(ns ouroboros.game.arena
  "The ARENA — game-agnostic match runner (design/game-arena.md, build step 3).

  Drives any ouroboros.game engine map through hands and matches with an
  INJECTABLE decide-fn:

      (decide-fn seat-spec observation) → action-map

  Tests stub it with bots (the RunTestsTool precedent — deterministic, no
  LLM); step 4 supplies the real one (a hermetic verdict-topology shot per
  decision, the engine's :game/action-schema as the forced :verdict-schema).
  A decide-fn that THROWS or returns garbage decays through the engine's
  TOTAL apply-action to forfeit-default — the arena never wedges; forfeits
  are counted per seat (a benchmark metric, not a crash).

  Seat model: `seats` ≡ vector of SPEC maps (arena ids ≡ index; later
  {:genome :model} — the benchmark axes). Per hand the arena maps ALIVE
  arena seats onto engine seats 0..k-1 and translates payoffs back.

  Bankroll accounting is ARENA-generic (bankroll ≡ start + Σ payoffs):
    :reset       — every hand starts fresh; engine never sees bankrolls
                   (benchmark mode: variance-controlled decision quality)
    :carry       — bankrolls ride into the engine config as :stacks
    :elimination — :carry + busted seats sit out + play stops at one
                   survivor (or :max-hands); :level-fn hand-idx → config
                   overrides (escalating blinds live HERE, not in the engine)

  Transcripts persist to games/ (root-anchored gitignored side-store — the
  sessions/scores pattern): EDN, replayable from :seed + :config, decisions
  and endings included, full states (decks, live holes) EXCLUDED — showdown
  :result reveals only what a table would."
  (:require
    [clojure.java.io :as io]))

(defn- forfeit-counts
  "Engine-idx → forfeit count, read from the engine's history (engine truth,
  not arena bookkeeping)."
  [state]
  (frequencies (keep #(when (:forfeit? %) (:seat %)) (:history state))))

(defn run-hand!
  "One hand of `engine` with `hand-seats` (spec per ENGINE seat idx).
  Returns {:state <terminal> :decisions [{:seat :action :error :ms} ...]}.
  Decide errors are recorded and decay via apply-action; :max-steps guards
  against a non-terminating engine (a bug, not a condition)."
  [engine {:keys [config seed decide-fn hand-seats max-steps]
           :or   {max-steps 1000}}]
  (let [{:game/keys [init terminal? to-move visible apply-action]} engine]
    (loop [st (init config seed) decisions []]
      (cond
        (terminal? st)
        {:state st :decisions decisions}

        (>= (count decisions) max-steps)
        (throw (ex-info "hand exceeded max-steps" {:steps max-steps}))

        :else
        (let [eidx  (first (to-move st))
              spec  (nth hand-seats eidx)
              ;; the ACTION CONTRACT rides the observation: seat-scoped
              ;; decision context (legality-narrowed schema + filled
              ;; exemplar) for decide-fns that force verdicts (game.llm);
              ;; bots ignore it
              obs   (cond-> (visible st eidx)
                      (:game/action-schema engine)
                      (assoc :action-schema ((:game/action-schema engine) st eidx))
                      (:game/action-exemplar engine)
                      (assoc :action-exemplar ((:game/action-exemplar engine) st eidx)))
              t0    (System/currentTimeMillis)
              [action err] (try [(decide-fn spec obs) nil]
                                (catch Exception e [nil (ex-message e)]))
              act   (if (map? action) action {:action ::invalid})
              ;; chip context for the transcript — betting games expose
              ;; :pot/:to-call/:stack via visible, and the legal menu carries
              ;; the ENGINE-decided amount for the chosen action (agents don't
              ;; size bets in limit play; fold/check commit nothing). Captured
              ;; opportunistically: absent for games that don't surface :pot.
              committed (some (fn [la] (when (= (:action la) (:action action))
                                         (:amount la)))
                              (:legal obs))
              decision  (cond-> {:seat eidx :action action
                                 :error err
                                 :ms (- (System/currentTimeMillis) t0)}
                          (contains? obs :pot)
                          (assoc :chips (cond-> {:pot     (:pot obs)
                                                 :to-call (:to-call obs)
                                                 :stack   (:stack obs)}
                                          committed (assoc :committed committed))))]
          (recur (apply-action st eidx act)
                 (conj decisions decision)))))))

(defn- persist!
  [root id match]
  (let [f (io/file root "games" (str id ".edn"))]
    (io/make-parents f)
    (spit f (pr-str match))
    (str f)))

(defn run-match!
  "A match: `hands` hands (or :elimination until one survivor). Deterministic
  from :seed — hand h shuffles with (+ seed h); the engine button rotates
  through the alive seats by hand index (v1 simplification).

  opts: :seats [spec…] (REQUIRED) · :decide-fn (REQUIRED) · :seed · :hands ·
        :mode :reset|:carry|:elimination · :max-hands (elimination guard) ·
        :starting-bankroll (default 1000) · :config (engine extras) ·
        :level-fn (hand-idx → config overrides) · :root · :persist? · :id

  Returns {:id :game :mode :seed :seats :hands [hand-records] :totals
           :bankrolls :forfeits :winner :file}."
  [engine {:keys [seats decide-fn seed hands mode max-hands starting-bankroll
                  config level-fn root persist? id]
           :or   {seed 0 hands 10 mode :reset max-hands 200
                  starting-bankroll 1000 root "." persist? true}}]
  (let [n  (count seats)
        id (or id (str (name (:game/id engine)) "-" seed "-"
                       (System/currentTimeMillis)))]
    (loop [hand-idx 0 bankrolls (vec (repeat n starting-bankroll)) acc []]
      (let [alive (if (= mode :reset)
                    (vec (range n))
                    (filterv #(pos? (bankrolls %)) (range n)))
            done? (or (if (= mode :elimination)
                        (or (<= (count alive) 1) (>= hand-idx max-hands))
                        (>= hand-idx hands)))]
        (if done?
          (let [match {:id        id
                       :game      (:game/id engine)
                       :mode      mode
                       :seed      seed
                       :seats     seats
                       :hands     acc
                       :totals    (into {} (map (fn [i] [i (- (bankrolls i)
                                                              starting-bankroll)])
                                                (range n)))
                       :bankrolls bankrolls
                       :forfeits  (apply merge-with + {}
                                         (map :forfeits acc))
                       :winner    (when (and (= mode :elimination)
                                             (= 1 (count alive)))
                                    (first alive))}]
            (cond-> match
              persist? (assoc :file (persist! root id match))))
          (let [level      (when level-fn (level-fn hand-idx))
                hand-seed  (+ seed hand-idx)
                hand-cfg   (cond-> (merge {:seats (count alive)} config level
                                          {:button (mod hand-idx (count alive))})
                             (not= mode :reset)
                             (assoc :stacks (mapv bankrolls alive)))
                hand-seats (mapv seats alive)
                {:keys [state decisions]}
                (run-hand! engine {:config     hand-cfg
                                   :seed       hand-seed
                                   :decide-fn  decide-fn
                                   :hand-seats hand-seats})
                ;; engine idx → arena id translation
                ->arena    (fn [m] (into {} (map (fn [[ei v]] [(alive ei) v]) m)))
                payoffs    (->arena ((:game/payoffs engine) state))
                record     {:hand      hand-idx
                            :seed      hand-seed
                            :alive     alive
                            :level     level
                            :payoffs   payoffs
                            :ending    (get-in state [:result :ending])
                            :result    (:result state)
                            ;; FULL-TRUTH study record (design/game-arena §content —
                            ;; "study ¬leaderboard"): the transcript is a post-hoc
                            ;; human/analysis artifact AGENTS never read, so it captures
                            ;; the WHOLE deal — every seat's hole cards (even mucked
                            ;; folds ≡ the nit-leak evidence) + the community board.
                            ;; visible/:result STILL enforce table-view for agents; the
                            ;; :deck alone stays unpersisted (replay rides :seed).
                            :cards     {:board (:board state)
                                        :holes (into (sorted-map)
                                                     (map-indexed
                                                       (fn [ei s] [ei (:hole s)])
                                                       (:seats state)))}
                            :forfeits  (->arena (forfeit-counts state))
                            :decisions decisions}]
            (recur (inc hand-idx)
                   (reduce (fn [b [i p]] (update b i + p)) bankrolls payoffs)
                   (conj acc record))))))))

(defn run-duplicate!
  "Heads-up DUPLICATE-SEATED match (design/game-arena.md λ duplicate, step 5):
  two seat-specs play the SAME seed/deals TWICE with the seats SWAPPED, so
  card luck cancels the way the comparator's both-seatings cancels position
  bias. Each spec plays both sides of every deal; the aggregate is pure skill.

  INVARIANT: seat-a ≡ seat-b (identical play) ⇒ :a = :b = 0 — any nonzero
  aggregate is a decidable skill difference, not variance. This is the fitness
  primitive the benchmark report and the GA duel both stand on.

  opts: :seat-a · :seat-b (REQUIRED specs) · :decide-fn (REQUIRED) · :seed ·
        :hands · :mode · :starting-bankroll · :config · :level-fn · :root ·
        :persist? (default false — duplicates are ephemeral fitness probes).
  Returns {:a <spec-a net chips over both seatings> :b <spec-b net>
           :hands-played (* 2 hands) :seatings [m1 m2]}."
  [engine {:keys [seat-a seat-b decide-fn seed hands mode starting-bankroll
                  config level-fn root persist?]
           :or   {seed 0 hands 10 mode :reset starting-bankroll 1000
                  root "." persist? false}}]
  (let [run  (fn [seats]
               (run-match! engine {:seats             seats
                                   :decide-fn         decide-fn
                                   :seed              seed
                                   :hands             hands
                                   :mode              mode
                                   :starting-bankroll starting-bankroll
                                   :config            config
                                   :level-fn          level-fn
                                   :root              root
                                   :persist?          persist?}))
        m1   (run [seat-a seat-b])          ; a≡idx0, b≡idx1
        m2   (run [seat-b seat-a])          ; b≡idx0, a≡idx1 — swapped, same deals
        t1   (:totals m1)
        t2   (:totals m2)
        a    (+ (get t1 0 0) (get t2 1 0))  ; a's chips as idx0 then as idx1
        b    (+ (get t1 1 0) (get t2 0 0))]
    {:a a :b b :hands-played (* 2 hands) :seatings [m1 m2]}))

(defn benchmark-report
  "A duplicate result → a per-genome summary (design/game-arena.md λ benchmark:
  payoff alone lies at small n, so we surface the decidable spread AND the
  failure signal). Forfeits translate the same seat-swap way as chips.

  opts: :label-a · :label-b (display keys, default :a/:b).
  Returns {:hands-played n :label-a {…} :label-b {…} :verdict :a|:b|:tie}
  where each side is {:net :chips-per-hand :forfeits}."
  [{:keys [a b hands-played seatings]} & [{:keys [label-a label-b]
                                           :or {label-a :a label-b :b}}]]
  (let [[m1 m2] seatings
        f1 (:forfeits m1) f2 (:forfeits m2)
        fa (+ (get f1 0 0) (get f2 1 0))          ; a's forfeits (idx0 then idx1)
        fb (+ (get f1 1 0) (get f2 0 0))
        per (fn [net] (double (/ net (max 1 hands-played))))]
    {:hands-played hands-played
     label-a       {:net a :chips-per-hand (per a) :forfeits fa}
     label-b       {:net b :chips-per-hand (per b) :forfeits fb}
     :verdict      (cond (> a b) label-a (< a b) label-b :else :tie)}))
