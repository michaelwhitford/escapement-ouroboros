(ns ouroboros.game.arena-test
  "Arena runner tests — real poker engine, stubbed decide-fns (bots), zero
  LLM. Invariants: zero-sum per hand and match, bankroll conservation across
  hands, elimination terminates with the chips in one stack, forfeit
  accounting, transcript persistence + seed replayability."
  (:require
    [babashka.fs :as fs]
    [clojure.edn :as edn]
    [clojure.test :refer [deftest is testing]]
    [ouroboros.game.arena :as arena]
    [ouroboros.game.poker :as poker]))

;; ── bots (decide-fn stubs) ────────────────────────────────────────────────

(defn- legal-set [obs] (set (map :action (:legal obs))))

(def bots
  {:passive (fn [_ obs] (let [ls (legal-set obs)]
                          (cond (ls :check) {:action :check}
                                (ls :call)  {:action :call}
                                :else       {:action :fold})))
   :raiser  (fn [_ obs] (let [ls (legal-set obs)]
                          (cond (ls :raise) {:action :raise :why "pressure"}
                                (ls :call)  {:action :call}
                                :else       {:action :check})))
   :naive   (fn [_ _obs] {:action :call})          ; often illegal → forfeit decay
   :thrower (fn [_ _obs] (throw (ex-info "llm exploded" {})))})

(defn- bot-decide [spec obs] ((bots (:bot spec)) spec obs))

(defn- run [opts]
  (arena/run-match! poker/engine
                    (merge {:decide-fn bot-decide :persist? false} opts)))

;; ── hands ─────────────────────────────────────────────────────────────────

(deftest single-hand
  (let [{:keys [state decisions]}
        (arena/run-hand! poker/engine
                         {:config     {:seats 2}
                          :seed       1
                          :decide-fn  bot-decide
                          :hand-seats [{:bot :raiser} {:bot :passive}]})]
    (is (poker/terminal? state))
    (is (seq decisions))
    (is (zero? (reduce + (vals (poker/payoffs state)))))))

;; ── match modes ───────────────────────────────────────────────────────────

(deftest reset-match-zero-sum-and-replayable
  (let [opts {:seats [{:bot :raiser} {:bot :passive}] :seed 7 :hands 10}
        m1   (run opts)
        m2   (run opts)]
    (is (= 10 (count (:hands m1))))
    (is (zero? (reduce + (vals (:totals m1)))) "match totals are zero-sum")
    (is (every? #(zero? (reduce + (vals (:payoffs %)))) (:hands m1))
        "every hand is zero-sum")
    (is (= (:totals m1) (:totals m2)) "same seed ⇒ same match")
    (is (nil? (:winner m1)) "reset mode has no elimination winner")))

(deftest carry-match-conserves-bankrolls
  (let [m (run {:seats [{:bot :raiser} {:bot :passive} {:bot :passive}]
                :seed 3 :hands 8 :mode :carry :starting-bankroll 200})]
    (is (= 600 (reduce + (:bankrolls m))) "chips conserve across carried hands")
    (is (= (:totals m)
           (into {} (map (fn [[i b]] [i (- b 200)])
                         (map-indexed vector (:bankrolls m))))))))

(deftest elimination-terminates-with-one-stack
  (let [m (run {:seats [{:bot :raiser} {:bot :raiser} {:bot :raiser}]
                :seed 11 :mode :elimination :starting-bankroll 60
                :max-hands 300})]
    (is (some? (:winner m)) "an all-raiser table with 6BB stacks busts down")
    (is (= 180 ((:bankrolls m) (:winner m))) "winner holds ALL the chips")
    (is (every? zero? (map (:bankrolls m)
                           (remove #{(:winner m)} (range 3))))
        "everyone else is felted")
    (is (= 180 (reduce + (:bankrolls m))) "conservation")))

(deftest elimination-respects-level-fn
  (let [m (run {:seats [{:bot :passive} {:bot :passive}]
                :seed 5 :mode :elimination :starting-bankroll 100
                :max-hands 4
                :level-fn (fn [h] {:small-blind (* 5 (inc h))
                                   :big-blind   (* 10 (inc h))})})]
    (is (= {:small-blind 5 :big-blind 10} (:level (first (:hands m)))))
    (is (= {:small-blind 10 :big-blind 20} (:level (second (:hands m))))
        "blind escalation is arena policy, not engine surgery")))

;; ── failure decay ─────────────────────────────────────────────────────────

(deftest thrower-forfeits-but-match-completes
  (let [m (run {:seats [{:bot :thrower} {:bot :passive}] :seed 2 :hands 5})]
    (is (= 5 (count (:hands m))) "the arena never wedges on a dead decide-fn")
    (is (pos? (get (:forfeits m) 0 0)) "thrower's forfeits are counted")
    (is (zero? (reduce + (vals (:totals m)))))
    (is (every? #(some :error (:decisions %)) (:hands m))
        "decide errors are recorded, not swallowed")))

(deftest naive-bot-decays-not-crashes
  (let [m (run {:seats [{:bot :naive} {:bot :passive}] :seed 4 :hands 5})]
    (is (= 5 (count (:hands m))))
    (is (zero? (reduce + (vals (:totals m)))))))

;; ── duplicate seating (step 5) ────────────────────────────────────────────

(defn- dup [opts]
  (arena/run-duplicate! poker/engine (merge {:decide-fn bot-decide} opts)))

(deftest duplicate-aggregate-is-zero-sum
  (let [d (dup {:seat-a {:bot :raiser} :seat-b {:bot :passive}
                :seed 7 :hands 10})]
    (is (= 20 (:hands-played d)) "both seatings played")
    (is (zero? (+ (:a d) (:b d))) "duplicate aggregate is zero-sum")
    (is (= 2 (count (:seatings d))))))

(deftest duplicate-cancels-luck-for-identical-play
  (testing "identical genomes ⇒ each nets EXACTLY 0 — card luck cancels, only
            skill remains (the fitness invariant the GA duel stands on)"
    (doseq [[bot seed] [[:passive 3] [:passive 42] [:raiser 11] [:raiser 99]]]
      (let [d (dup {:seat-a {:bot bot} :seat-b {:bot bot}
                    :seed seed :hands 12})]
        (is (zero? (:a d)) (str bot "/" seed " — a nets zero"))
        (is (zero? (:b d)) (str bot "/" seed " — b nets zero"))))))

(deftest duplicate-is-deterministic
  (let [opts {:seat-a {:bot :raiser} :seat-b {:bot :passive} :seed 5 :hands 8}]
    (is (= (select-keys (dup opts) [:a :b])
           (select-keys (dup opts) [:a :b]))
        "same seed ⇒ same duplicate result")))

(deftest benchmark-report-summarizes-the-duplicate
  (let [d (dup {:seat-a {:bot :raiser} :seat-b {:bot :passive}
                :seed 7 :hands 10})
        r (arena/benchmark-report d {:label-a :aggro :label-b :nit})]
    (is (= 20 (:hands-played r)))
    (is (= (:a d) (get-in r [:aggro :net])))
    (is (= (:b d) (get-in r [:nit :net])))
    (is (= (double (/ (:a d) 20)) (get-in r [:aggro :chips-per-hand])))
    (is (contains? #{:aggro :nit :tie} (:verdict r)))
    (is (= (:verdict r) (cond (> (:a d) (:b d)) :aggro
                              (< (:a d) (:b d)) :nit
                              :else :tie))
        "verdict tracks the chip winner")))

(deftest benchmark-report-translates-forfeits
  (let [d (dup {:seat-a {:bot :thrower} :seat-b {:bot :passive}
                :seed 2 :hands 6})
        r (arena/benchmark-report d)]
    (is (pos? (get-in r [:a :forfeits])) "the thrower's forfeits attribute to :a")
    (is (zero? (get-in r [:b :forfeits])) "the passive bot never forfeits")))

;; ── persistence ───────────────────────────────────────────────────────────

(deftest transcript-persists-and-parses
  (let [root (str (fs/create-temp-dir))]
    (try
      (let [m (arena/run-match! poker/engine
                                {:seats [{:bot :raiser} {:bot :passive}]
                                 :decide-fn bot-decide
                                 :seed 9 :hands 3
                                 :root root :id "test-match"})
            f (str root "/games/test-match.edn")
            r (edn/read-string (slurp f))]
        (is (= f (:file m)))
        (is (= 3 (count (:hands r))))
        (is (= (:totals m) (:totals r)) "the transcript IS the match record")
        (is (= 9 (:seed r)) "seed rides the transcript — replay ≡ re-run")
        (is (not (re-find #":deck" (slurp f)))
            "full states (decks) never persist — only what a table shows"))
      (finally (fs/delete-tree root)))))
