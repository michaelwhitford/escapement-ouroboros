(ns ouroboros.game.poker-test
  "Limit hold'em engine tests — scripted deterministic hands via :deck
  injection (deal order: 2 consecutive cards per seat in seat order, then
  board with no burns), plus seeded random playouts asserting the
  conservation laws. Zero LLM.

  Invariants under test: chip conservation · payoffs sum to zero ·
  final stack ≡ initial + payoff · hidden info never leaks pre-terminal ·
  illegal actions decay to forfeit-default (the arena never wedges)."
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [ouroboros.game :as game]
    [ouroboros.game.poker :as poker]))

(defn- c [s]
  (let [r  (get {\A 14 \K 13 \Q 12 \J 11 \T 10} (first s)
                (- (int (first s)) (int \0)))
        su ({\s :spades \h :hearts \d :diamonds \c :clubs} (last s))]
    {:rank r :suit su}))

(defn- cs [s] (mapv c (str/split s #" ")))

(defn- steps
  "Apply scripted [seat action] steps."
  [state actions]
  (reduce (fn [st [seat act]] (poker/apply-action st seat act)) state actions))

;; hole-hole-...-board deck layout helpers
(def ^:private hu-deck   ;; s0: AsAh (trips on this board) · s1: KdKh
  (cs "As Ah Kd Kh Ac 7d 2c 3h 9s"))

(deftest protocol-instance
  (is (game/engine? poker/engine) "poker satisfies the game protocol"))

(deftest init-heads-up
  (let [st (poker/init {:seats 2} 42)]
    (testing "button is the small blind heads-up, acts first preflop"
      (is (= {:sb 0 :bb 1} (:positions st)))
      (is (= #{0} (poker/to-move st))))
    (testing "blinds posted"
      (is (= 995 (get-in st [:seats 0 :stack])))
      (is (= 990 (get-in st [:seats 1 :stack])))
      (is (= 15 (reduce + (map :total-committed (:seats st))))))
    (testing "everyone gets preflop action (BB option included)"
      (is (= #{0 1} (:pending st))))
    (testing "legal actions for the mover; empty for anyone else"
      (is (= #{{:action :fold} {:action :call :amount 5} {:action :raise :amount 15}}
             (poker/legal-actions st 0)))
      (is (= #{} (poker/legal-actions st 1))))
    (testing "button 1 flips the positions"
      (let [st2 (poker/init {:seats 2 :button 1} 42)]
        (is (= {:sb 1 :bb 0} (:positions st2)))
        (is (= #{1} (poker/to-move st2)))))))

(deftest fold-ending
  (let [st (-> (poker/init {:seats 2} 42)
               (poker/apply-action 0 {:action :fold}))]
    (is (poker/terminal? st))
    (is (= {0 -5, 1 5} (poker/payoffs st)))
    (is (= 995 (get-in st [:seats 0 :stack])))
    (is (= 1005 (get-in st [:seats 1 :stack])))
    (is (= :fold (get-in st [:result :ending])))))

(deftest checked-down-showdown
  (let [st (-> (poker/init {:seats 2 :deck hu-deck} 0)
               (steps [[0 {:action :call}]   ; sb completes
                       [1 {:action :check}]  ; bb option → flop
                       [1 {:action :check}] [0 {:action :check}]   ; flop (bb first postflop)
                       [1 {:action :check}] [0 {:action :check}]   ; turn
                       [1 {:action :check}] [0 {:action :check}]]))] ; river
    (is (poker/terminal? st))
    (is (= :showdown (get-in st [:result :ending])))
    (testing "trip aces beat kings; pot 20 moves 10"
      (is (= {0 10, 1 -10} (poker/payoffs st)))
      (is (= 1010 (get-in st [:seats 0 :stack])))
      (is (= 990 (get-in st [:seats 1 :stack])))
      (is (= :three-of-a-kind (get-in st [:result :evals 0])))
      (is (= :pair (get-in st [:result :evals 1]))))
    (testing "showdown reveals both live hands"
      (is (= (cs "As Ah") (get-in st [:result :revealed 0])))
      (is (= (cs "Kd Kh") (get-in st [:result :revealed 1]))))))

(deftest chopped-pot
  (let [deck (cs "Ah Kh As Ks Qd Jc Th 2s 3d") ; both play board straight AKQJT
        st (-> (poker/init {:seats 2 :deck deck} 0)
               (steps [[0 {:action :call}] [1 {:action :check}]
                       [1 {:action :check}] [0 {:action :check}]
                       [1 {:action :check}] [0 {:action :check}]
                       [1 {:action :check}] [0 {:action :check}]]))]
    (is (= {0 0, 1 0} (poker/payoffs st)) "identical straights chop")
    (is (= 1000 (get-in st [:seats 0 :stack])))
    (is (= 1000 (get-in st [:seats 1 :stack])))))

(deftest raise-cap
  (let [st (-> (poker/init {:seats 2} 7)
               (steps [[0 {:action :raise}] [1 {:action :raise}]
                       [0 {:action :raise}] [1 {:action :raise}]]))]
    (is (= 4 (:raises st)))
    (testing "raise number five is unrepresentable"
      (is (= #{:fold :call} (set (map :action (poker/legal-actions st 0))))))))

(deftest street-bet-sizes
  (let [st (-> (poker/init {:seats 2 :deck hu-deck} 0)
               (steps [[0 {:action :call}] [1 {:action :check}]]))]
    (is (= :flop (:street st)))
    (testing "flop raise is the small bet"
      (is (= 10 (:amount (first (filter #(= :raise (:action %))
                                        (poker/legal-actions st 1)))))))
    (let [st2 (steps st [[1 {:action :check}] [0 {:action :check}]])]
      (is (= :turn (:street st2)))
      (testing "turn raise is the big bet"
        (is (= 20 (:amount (first (filter #(= :raise (:action %))
                                          (poker/legal-actions st2 1))))))))))

(deftest forfeit-decay
  (testing "illegal check (chips owed, no check available) decays to fold"
    (let [st (-> (poker/init {:seats 2} 42)
                 (poker/apply-action 0 {:action :check}))]
      (is (poker/terminal? st))
      (is (= :fold (get-in st [:result :ending])))
      (is (some :forfeit? (:history st)) "forfeit is recorded, not hidden")))
  (testing "unknown action decays too"
    (let [st (-> (poker/init {:seats 2} 42)
                 (poker/apply-action 0 {:action :all-in-baby}))]
      (is (poker/terminal? st))))
  (testing "illegal call (nothing owed) decays to the free check"
    (let [st (-> (poker/init {:seats 2 :deck hu-deck} 0)
                 (steps [[0 {:action :call}]
                         [1 {:action :call}]]))] ; bb owes 0 → check
      (is (= :flop (:street st)) "hand advanced, nobody folded")))
  (testing "out-of-turn action is a no-op"
    (let [st (poker/init {:seats 2} 42)]
      (is (= st (poker/apply-action st 1 {:action :fold}))))))

(deftest visibility-law
  (let [st   (poker/init {:seats 2 :deck hu-deck} 0)
        obs0 (poker/visible st 0)
        obs1 (poker/visible st 1)]
    (testing "each seat sees its OWN hole"
      (is (= (cs "As Ah") (:hole obs0)))
      (is (= (cs "Kd Kh") (:hole obs1))))
    (testing "no hole cards anywhere else in the projection"
      (is (every? #(not (contains? % :hole)) (:seats obs0)))
      (is (nil? (:result obs0)) "no result pre-terminal")
      (is (not (str/includes? (pr-str (dissoc obs0 :hole)) ":rank"))
          "the only cards in a live preflop observation are the seat's own"))
    (testing "render narrates own cards, never the opponent's"
      (let [text (poker/render obs0)]
        (is (str/includes? text "A♠ A♥"))
        (is (not (str/includes? text "K♦")))
        (is (str/includes? text "small blind"))))))

(deftest side-pots-three-way
  ;; stacks [15 100 100] · button 0 · sb 1 · bb 2 · s0 short-stacked with aces
  (let [deck (cs "As Ah Kd Kh Qc Qd Ac 7d 2h 3s 9c")
        st (-> (poker/init {:seats 3 :stacks [15 100 100] :deck deck} 0)
               (steps [[0 {:action :call}]    ; UTG calls 10 (5 behind)
                       [1 {:action :call}]    ; sb completes
                       [2 {:action :check}]   ; bb option → flop
                       [1 {:action :raise}]   ; flop bet 10 (sb first after button)
                       [2 {:action :call}]
                       [0 {:action :call}]    ; all-in for 5
                       [1 {:action :check}] [2 {:action :check}]     ; turn
                       [1 {:action :check}] [2 {:action :check}]]))] ; river
    (is (poker/terminal? st))
    (testing "main pot 45 to the short stack's aces, side pot 10 to the kings"
      (is (= [{:amount 45 :eligible [0 1 2]}
              {:amount 10 :eligible [1 2]}]
             (get-in st [:result :pots])))
      (is (= {0 30, 1 -10, 2 -20} (poker/payoffs st))))
    (testing "conservation"
      (is (zero? (reduce + (vals (poker/payoffs st)))))
      (is (= 215 (reduce + (map :stack (:seats st))))))
    (testing "stacks settle: 45 · 90 · 80"
      (is (= [45 90 80] (mapv :stack (:seats st)))))))

(deftest uncalled-bet-refund
  ;; heads-up · bb covers only 12 total · sb's raise is partly uncalled
  (let [deck (cs "As Ah Kd Kh Ac 7d 2h 3s 9c")
        st (-> (poker/init {:seats 2 :stacks [100 12] :deck deck} 0)
               (steps [[0 {:action :raise}]    ; to 20
                       [1 {:action :call}]]))] ; all-in for 2 more (12 total)
    (is (poker/terminal? st) "run-out to showdown, nobody left to act")
    (testing "8 uncalled chips return to the raiser before pots form"
      (is (= {0 12, 1 -12} (poker/payoffs st)))
      (is (= [112 0] (mapv :stack (:seats st)))))
    (is (= 112 (reduce + (map :stack (:seats st)))) "chips conserve")))

(deftest lone-active-seat-still-owes
  ;; bb is all-in from the blind; sb must still decide — the hand may NOT
  ;; auto-run-out past a live decision
  (let [st (poker/init {:seats 2 :stacks [100 8] :deck hu-deck} 0)]
    (is (= #{0} (poker/to-move st)) "sb still owes a decision")
    (testing "sb folds → bb wins without showdown"
      (let [st' (poker/apply-action st 0 {:action :fold})]
        (is (= {0 -5, 1 5} (poker/payoffs st')))))
    (testing "sb calls → run-out, aces hold"
      (let [st' (poker/apply-action st 0 {:action :call})]
        (is (poker/terminal? st'))
        (is (= :showdown (get-in st' [:result :ending])))
        (is (= {0 8, 1 -8} (poker/payoffs st')))))))

(deftest table-talk-rides-history
  (let [st (-> (poker/init {:seats 2} 42)
               (poker/apply-action 0 {:action :call :why "pot odds"}))]
    (is (some #(= "pot odds" (:why %)) (:history st)))))

(deftest random-playout-conservation
  (testing "20 seeded random 3-handed playouts: terminal, zero-sum, conserved"
    (doseq [seed (range 20)]
      (let [rng   (java.util.Random. (long seed))
            final (loop [st (poker/init {:seats 3} seed) guard 0]
                    (cond
                      (poker/terminal? st) st
                      (> guard 300) (throw (ex-info "playout did not terminate"
                                                    {:seed seed}))
                      :else
                      (let [mover (first (poker/to-move st))
                            legal (vec (sort-by :action (poker/legal-actions st mover)))
                            act   (legal (.nextInt rng (count legal)))]
                        (recur (poker/apply-action st mover act) (inc guard)))))]
        (is (poker/terminal? final))
        (is (zero? (reduce + (vals (poker/payoffs final))))
            (str "zero-sum, seed " seed))
        (is (= 3000 (reduce + (map :stack (:seats final))))
            (str "conservation, seed " seed))
        (is (every? (fn [{:keys [id stack]}]
                      (= stack (+ 1000 (get (poker/payoffs final) id))))
                    (:seats final))
            (str "stack ≡ initial + payoff, seed " seed))))))
