(ns ouroboros.game.poker
  "Limit Texas hold'em — the FIRST ouroboros.game protocol instance
  (design/game-arena.md). Pure, seeded, zero LLM: the engine is code,
  agents only choose from `legal-actions`.

  State ≡ the FULL truth (all holes, the deck). Agents NEVER receive state —
  only `visible` projections (λ emerge: hole cards are unreachable, not
  forbidden). Transcripts store the seed; replay ≡ re-init.

  Structure:
    · limit betting — fixed sizes: small-bet on preflop/flop, big-bet on
      turn/river; raises per street capped at :max-raises (the blind counts
      as the initial bet; a raise ≡ one more unit on top of the call).
    · heads-up rule — button ≡ small blind, acts FIRST preflop, SECOND
      postflop (falls out of the same position arithmetic as ring play).
    · stacks live IN state from day 1 (design 🎯): all-in + side pots work,
      so both bankroll modes (benchmark reset · elimination carry) are
      match-loop POLICY, not engine surgery. The match loop lives in the
      arena (build step 3), not here.
    · v1 simplifications (documented, revisit if a benchmark cares):
      raise requires the FULL raise amount (short stacks may call all-in but
      not under-raise — no bet-reopening ambiguity); odd chops award the
      remainder chip(s) to winners in seat order.

  Action maps (the verdict-schema surface): {:action :fold|:check|:call|:raise}
  — amounts are ENGINE-decided in limit play; an agent's :amount is ignored,
  extra keys (:why table-talk) pass through to history untouched."
  (:require
    [clojure.string :as str]
    [ouroboros.game.cards :as cards]
    [ouroboros.game.poker.eval :as eval]))

(def default-config
  {:seats          2
   :starting-stack 1000
   :small-blind    5
   :big-blind      10
   :small-bet      10
   :big-bet        20
   :max-raises     4})

;; ── position + seat arithmetic ─────────────────────────────────────────────

(defn- positions
  "Heads-up: button IS the small blind. Ring: blinds left of the button."
  [n button]
  (if (= n 2)
    {:sb button :bb (mod (inc button) n)}
    {:sb (mod (inc button) n) :bb (mod (+ button 2) n)}))

(defn- next-idx
  "First seat index after `from` (clockwise, wrapping) satisfying pred."
  [state from pred]
  (let [n (count (:seats state))]
    (->> (range 1 (inc n))
         (map #(mod (+ from %) n))
         (filter pred)
         first)))

(defn- active-idxs
  "Seats that can still ACT: in the hand, not all-in."
  [state]
  (->> (:seats state)
       (filter #(and (not (:folded? %)) (not (:all-in? %))))
       (mapv :id)))

(defn- in-hand-idxs
  "Seats still contesting the pot (may be all-in)."
  [state]
  (->> (:seats state) (remove :folded?) (mapv :id)))

(defn- current-bet [state]
  (apply max (map :street-committed (:seats state))))

(defn- street-bet [state]
  (let [{:keys [small-bet big-bet]} (:config state)]
    (if (#{:preflop :flop} (:street state)) small-bet big-bet)))

(defn- pot-total [state]
  (reduce + (map :total-committed (:seats state))))

(defn- commit
  "Move up to `amount` chips from a seat's stack into the pot (clamped to
  stack — calling all-in for less is legal). Stamps :all-in? at zero."
  [state idx amount]
  (let [stack (get-in state [:seats idx :stack])
        amt   (min amount stack)]
    (-> state
        (update-in [:seats idx :stack] - amt)
        (update-in [:seats idx :street-committed] + amt)
        (update-in [:seats idx :total-committed] + amt)
        (cond-> (= amt stack) (assoc-in [:seats idx :all-in?] true)))))

;; ── protocol basics ────────────────────────────────────────────────────────

(defn terminal? [state]
  (= :done (:street state)))

(defn payoffs [state]
  (:payoffs state))

(defn to-move [state]
  (if-some [i (:to-act state)] #{i} #{}))

(defn legal-actions
  "Engine-enumerated actions for the seat to move (∅ for anyone else).
  Limit sizes: :call amount ≡ what is owed (clamped by apply), :raise
  amount ≡ call + one street bet unit."
  [state seat-id]
  (if (or (terminal? state) (not= seat-id (:to-act state)))
    #{}
    (let [seat  (get-in state [:seats seat-id])
          owe   (- (current-bet state) (:street-committed seat))
          bet   (street-bet state)
          stack (:stack seat)]
      (cond-> #{{:action :fold}}
        (zero? owe) (conj {:action :check})
        (pos? owe)  (conj {:action :call :amount (min owe stack)})
        (and (< (:raises state) (get-in state [:config :max-raises]))
             (>= stack (+ owe bet)))
        (conj {:action :raise :amount (+ owe bet)})))))

(defn forfeit-default
  "The decay action for invalid/illegal/absent decisions: check when free,
  else fold. The arena never wedges on agent error."
  [state seat-id]
  (if (some #(= :check (:action %)) (legal-actions state seat-id))
    {:action :check}
    {:action :fold}))

;; ── hand endings ───────────────────────────────────────────────────────────

(defn- finish-uncontested
  "Everyone else folded — winner takes the whole pot (their own uncalled
  chips return inside it)."
  [state widx]
  (let [street (:street state)
        pot    (pot-total state)
        payoff (into {}
                     (map (fn [{:keys [id total-committed]}]
                            [id (if (= id widx)
                                  (- pot total-committed)
                                  (- total-committed))])
                          (:seats state)))]
    (-> state
        (update-in [:seats widx :stack] + pot)
        (update :history conj {:street street :seat widx :action :win :amount pot})
        (assoc :street :done :to-act nil :pending #{}
               :payoffs payoff
               :result {:ending :fold :winners [widx] :pot pot}))))

(defn- best-value
  "Best evaluation :value among eligible seat ids."
  [evals eligible]
  (reduce (fn [b i]
            (let [v (:value (evals i))]
              (if (or (nil? b) (pos? (compare v b))) v b)))
          nil eligible))

(defn- showdown
  "River betting complete (or run-out): refund any uncalled excess, layer
  side pots by contribution level, award each pot to the best eval among
  its eligible seats (chops split; odd chips to winners in seat order)."
  [state]
  (let [street0  (:street state)
        seats    (:seats state)
        in-hand  (vec (in-hand-idxs state))
        contribs (mapv :total-committed seats)
        ;; refund: unique top contributor among in-hand gets the uncalled part back
        in-cs    (sort > (map contribs in-hand))
        top      (first in-cs)
        second-c (second in-cs)
        ridx     (when (and second-c (> top second-c))
                   (first (filter #(= top (contribs %)) in-hand)))
        refund   (if ridx (- top second-c) 0)
        contribs (if ridx (update contribs ridx - refund) contribs)
        state    (if ridx
                   (-> state
                       (update-in [:seats ridx :stack] + refund)
                       (update-in [:seats ridx :total-committed] - refund))
                   state)
        board    (:board state)
        evals    (into {}
                       (map (fn [i]
                              [i (eval/eval-best
                                   (into (get-in state [:seats i :hole]) board))])
                            in-hand))
        levels   (sort (distinct (map contribs in-hand)))
        pots     (loop [prev 0 [lv & more] levels acc []]
                   (if (nil? lv)
                     acc
                     (recur lv more
                            (conj acc
                                  {:amount   (reduce + (map #(- (min % lv) (min % prev))
                                                            contribs))
                                   :eligible (filterv #(>= (contribs %) lv) in-hand)}))))
        awards   (reduce
                   (fn [aw {:keys [amount eligible]}]
                     (let [bv    (best-value evals eligible)
                           ws    (filterv #(= bv (:value (evals %))) eligible)
                           share (quot amount (count ws))
                           extra (rem amount (count ws))]
                       (reduce (fn [aw [k w]]
                                 (update aw w (fnil + 0)
                                         (+ share (if (< k extra) 1 0))))
                               aw (map-indexed vector ws))))
                   {} pots)
        state    (reduce (fn [st [i amt]] (update-in st [:seats i :stack] + amt))
                         state awards)
        payoff   (into {}
                       (map (fn [{:keys [id]}]
                              [id (- (get awards id 0) (contribs id))])
                            seats))]
    (-> state
        (update :history conj {:street street0 :action :showdown
                               :awards awards
                               :revealed (into {} (map (fn [i] [i (get-in state [:seats i :hole])])
                                                       in-hand))})
        (assoc :street :done :to-act nil :pending #{}
               :payoffs payoff
               :result {:ending   :showdown
                        :pots     pots
                        :awards   awards
                        :revealed (into {} (map (fn [i] [i (get-in state [:seats i :hole])])
                                                in-hand))
                        :evals    (into {} (map (fn [[i e]] [i (:category e)]) evals))}))))

;; ── street flow ────────────────────────────────────────────────────────────

(declare advance)

(defn- begin-betting
  "Set up a street's betting round: pending ≡ seats owing action, first to
  act ≡ next pending seat after `from`. Betting is impossible (all-in
  run-out, or a lone active seat owing nothing) → advance past the street.
  A LONE active seat still OWING chips (facing an all-in blind/bet) must
  still act — that is the one single-active case that stays live."
  [state from]
  (let [active  (active-idxs state)
        pending (cond
                  (>= (count active) 2) (set active)
                  (= 1 (count active))
                  (let [a   (first active)
                        owe (- (current-bet state)
                               (get-in state [:seats a :street-committed]))]
                    (if (pos? owe) #{a} #{}))
                  :else #{})]
    (if (empty? pending)
      (advance state)
      (assoc state :pending pending :to-act (next-idx state from pending)))))

(def ^:private next-street {:preflop :flop, :flop :turn, :turn :river})
(def ^:private board-count {:flop 3, :turn 1, :river 1})

(defn- deal-board [state street]
  (let [[cs deck] (cards/deal (:deck state) (board-count street))]
    (-> state
        (assoc :deck deck)
        (update :board into cs)
        (update :history conj {:street street :action :board :cards cs}))))

(defn- advance
  "Betting round done: fold-out → uncontested; river → showdown; else deal
  the next street and open its betting (recursing through run-out streets
  when nobody can act)."
  [state]
  (let [in-hand (in-hand-idxs state)]
    (cond
      (= 1 (count in-hand)) (finish-uncontested state (first in-hand))
      (= :river (:street state)) (showdown state)
      :else
      (let [street (next-street (:street state))]
        (-> state
            (deal-board street)
            (assoc :street street :raises 0)
            (update :seats (fn [ss] (mapv #(assoc % :street-committed 0) ss)))
            (begin-betting (:button state)))))))

(defn- after-action
  "Post-action bookkeeping: hand over on fold-out, street over when nobody
  is pending, else pass the action clockwise."
  [state idx]
  (let [in-hand (in-hand-idxs state)]
    (cond
      (= 1 (count in-hand))      (finish-uncontested state (first in-hand))
      (empty? (:pending state))  (advance state)
      :else                      (assoc state :to-act
                                        (next-idx state idx (:pending state))))))

;; ── protocol entry points ──────────────────────────────────────────────────

(defn init
  "Deal a hand: blinds posted, holes dealt (2 consecutive cards per seat in
  seat order — fair under a seeded shuffle), preflop betting open. Config
  may carry :stacks (vector — elimination mode carry), :button, and :deck
  (test injection). Seed drives the shuffle; the transcript needs only it."
  [config seed]
  (let [cfg    (merge default-config config)
        n      (:seats cfg)
        _      (when (< n 2)
                 (throw (ex-info "poker needs ≥2 seats" {:seats n})))
        stacks (or (:stacks cfg) (vec (repeat n (:starting-stack cfg))))
        button (or (:button cfg) 0)
        deck   (or (:deck cfg) (cards/shuffled-deck seed))
        [hcs deck] (cards/deal deck (* 2 n))
        holes  (mapv vec (partition 2 hcs))
        seats  (vec (map-indexed
                      (fn [i st]
                        {:id i :stack st :hole (holes i)
                         :folded? false :all-in? false
                         :street-committed 0 :total-committed 0})
                      stacks))
        {:keys [sb bb]} (positions n button)
        cfgv   (fn [k] (get cfg k))]
    (-> {:config cfg :seats seats :button button :positions {:sb sb :bb bb}
         :deck deck :board [] :street :preflop :history [] :raises 0
         :seed seed}
        (commit sb (cfgv :small-blind))
        (update :history conj {:street :preflop :seat sb :action :small-blind
                               :amount (min (cfgv :small-blind) (stacks sb))})
        (commit bb (cfgv :big-blind))
        (update :history conj {:street :preflop :seat bb :action :big-blind
                               :amount (min (cfgv :big-blind) (stacks bb))})
        (begin-betting bb))))

(defn apply-action
  "TOTAL transition. Out-of-turn ≡ no-op (the arena drives from to-move;
  defensive). Unknown/illegal action ≡ forfeit-default. Amounts are engine
  law: the matching legal action's amount is used, the agent's ignored.
  Agent :why (table-talk) rides into history."
  [state seat-id action]
  (if (or (terminal? state) (not= seat-id (:to-act state)))
    state
    (let [idx    (:to-act state)
          legal  (legal-actions state idx)
          match  (first (filter #(= (:action %) (:action action)) legal))
          act    (or match (forfeit-default state idx))
          forfeited? (nil? match)
          street (:street state)
          state' (case (:action act)
                   :fold  (-> state
                              (assoc-in [:seats idx :folded?] true)
                              (update :pending disj idx))
                   :check (update state :pending disj idx)
                   :call  (-> state
                              (commit idx (:amount act))
                              (update :pending disj idx))
                   :raise (as-> state s
                            (commit s idx (:amount act))
                            (update s :raises inc)
                            (assoc s :pending (disj (set (active-idxs s)) idx))))]
      (-> state'
          (update :history conj
                  (cond-> {:street street :seat idx :action (:action act)
                           :amount (:amount act 0)}
                    forfeited?     (assoc :forfeit? true)
                    (:why action)  (assoc :why (:why action))))
          (after-action idx)))))

(defn visible
  "The seat's projection — its OWN hole, the public board/pots/stacks/history,
  and its legal actions. Opponents' holes appear ONLY in :result at terminal
  (showdown reveals; folded cards stay forever hidden)."
  [state seat-id]
  (let [seat (get-in state [:seats seat-id])]
    {:game      :poker-limit-holdem
     :seat      seat-id
     :hole      (:hole seat)
     :board     (:board state)
     :street    (:street state)
     :button    (:button state)
     :positions (:positions state)
     :pot       (pot-total state)
     :to-call   (max 0 (- (current-bet state) (:street-committed seat)))
     :stack     (:stack seat)
     :seats     (mapv #(select-keys % [:id :stack :street-committed :folded? :all-in?])
                      (:seats state))
     :history   (:history state)
     :legal     (legal-actions state seat-id)
     :config    (select-keys (:config state)
                             [:small-blind :big-blind :small-bet :big-bet :max-raises])
     :result    (when (terminal? state) (:result state))}))

(defn- event->str [{:keys [seat action amount cards]}]
  (case action
    :board       (str "board: " (cards/cards->str cards))
    :showdown    "showdown"
    (str "seat " seat " " (name action)
         (when (pos? (or amount 0)) (str " " amount)))))

(defn render
  "Observation → prompt text. The engine owns its narration; genomes add
  style, not facts."
  [{:keys [seat hole board street pot to-call stack seats legal history
           positions result] :as _obs}]
  (let [pos (cond (= seat (:sb positions)) "small blind"
                  (= seat (:bb positions)) "big blind"
                  :else                    (str "seat " seat))]
    (str "Limit hold'em. You are seat " seat " (" pos ").\n"
         "Street: " (name street)
         " | Board: " (if (seq board) (cards/cards->str board) "—") "\n"
         "Your hole cards: " (cards/cards->str hole) "\n"
         "Pot: " pot " | To call: " to-call " | Your stack: " stack "\n"
         "Seats: "
         (str/join " | "
                   (map (fn [{:keys [id stack folded? all-in?]}]
                          (str "seat " id ": " stack
                               (cond folded? " (folded)" all-in? " (all-in)" :else "")))
                        seats))
         "\n"
         "Action so far: "
         (if (seq history) (str/join ", " (map event->str history)) "—")
         "\n"
         (if (seq legal)
           (str "Your legal actions: "
                (str/join ", "
                          (map (fn [{:keys [action amount]}]
                                 (str (name action) (when amount (str " " amount))))
                               (sort-by :action legal))))
           (if result (str "Hand over: " (name (:ending result))) "Waiting.")))))

(def engine
  "The ouroboros.game protocol instance (see ouroboros.game for the contract)."
  {:game/id              :poker-limit-holdem
   :game/init            init
   :game/to-move         to-move
   :game/legal-actions   legal-actions
   :game/apply-action    apply-action
   :game/visible         visible
   :game/terminal?       terminal?
   :game/payoffs         payoffs
   :game/forfeit-default forfeit-default
   :game/render          render})
