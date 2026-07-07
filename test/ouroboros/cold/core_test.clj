(ns ouroboros.cold.core-test
  "Deterministic proofs of the cold-compiler's structural invariants.
  No LLM, no chart, no network — the properties hold by construction."
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [ouroboros.cold.core :as core]))

(def ^:private grounded-lambda
  (str "STATE:\n"
    "λ session(). topic(caching) | decided(write_through) | exploring(invalidation)\n"
    "STEER:\n"
    "λ steer(). next(invalidation) | ¬revisit(write_through)\n"
    "RULED_OUT:\n"
    "λ ruled_out(). write_behind() | disproven_by(consistency)"))

;; A turn where every domain specific in the λ above actually appears.
(def ^:private grounded-raw
  (str "user: We're designing a caching layer. Should we use write-through or "
    "write-behind?\n"
    "assistant: Go with write-through for consistency; write-behind is ruled out. "
    "Next we tackle invalidation."))

;; A λ that invents specifics the turn never mentioned (redis, sharding, ttl).
(def ^:private hollow-lambda
  (str "STATE:\n"
    "λ session(). backend(redis) | strategy(sharding) | policy(ttl_eviction)\n"
    "STEER:\n"
    "λ steer(). next(sharding)"))

(deftest parse-sections-splits-three-blocks
  (let [secs (core/parse-sections grounded-lambda)]
    (is (contains? secs :state))
    (is (contains? secs :steer))
    (is (contains? secs :ruled-out))
    (is (str/includes? (:state secs) "caching"))
    (is (not (str/includes? (:state secs) "STATE:")) "header line dropped")))

(deftest extract-refs-pulls-paren-arguments
  (let [refs (set (core/extract-refs grounded-lambda))]
    (is (contains? refs "caching"))
    (is (contains? refs "write_through"))
    (is (contains? refs "invalidation"))
    (is (not (contains? refs "session")) "≥4 but a paren-head with empty args → not captured as arg")))

(deftest verify-accepts-grounded-lambda
  (let [{:keys [ok? ratio hollow]} (core/verify-compiled
                                     {:compiled grounded-lambda :raw grounded-raw})]
    (is ok? "a λ whose refs all appear in the turn passes")
    (is (= 1.0 ratio) "every reference grounded")
    (is (empty? hollow))))

(deftest verify-rejects-hollow-lambda
  (let [{:keys [ok? ratio hollow]} (core/verify-compiled
                                     {:compiled hollow-lambda :raw grounded-raw})]
    (is (not ok?) "invented specifics sink the verdict")
    (is (< ratio 0.5))
    (is (seq hollow) "the hallucinated refs are named")
    (is (some #{"redis"} hollow))))

(deftest verify-rejects-missing-state-section
  (let [no-state "STEER:\nλ steer(). next(x)"
        {:keys [ok?]} (core/verify-compiled {:compiled no-state :raw grounded-raw})]
    (is (not ok?) "no STATE section → not a usable compilation")))

;; --- ESSENCE gate: recall of continuation-critical detail ---

(def ^:private turn-source
  (str "user: We're designing a write cache. Compare write-through vs write-behind, pick one.\n"
    "assistant: Go with write-through and LRU eviction; invalidate on version-bump. "
    "Write-behind is ruled out for durability."))

(deftest salient-terms-extracts-specifics-not-filler
  (let [s (set (map clojure.string/lower-case (core/salient-terms turn-source)))]
    (is (contains? s "write-through") "hyphenated compound")
    (is (contains? s "lru") "ALLCAPS acronym")
    (is (contains? s "eviction") "content word ≥5")
    (is (contains? s "durability"))
    (is (not (contains? s "compare")) "generic filler dropped")
    (is (not (contains? s "pick")) "short filler dropped")))

(deftest assess-passes-when-essence-retained
  (let [brief (str "CONTINUE:\nDecided write-through with LRU eviction; invalidate on "
                "version-bump. Ruled out write-behind (durability).\nRULED-OUT:\nwrite-behind")
        {:keys [ok? coverage missing]} (core/assess-continuation
                                         {:compiled brief :source turn-source})]
    (is ok? "a brief that keeps the specifics passes")
    (is (>= coverage 0.6))
    (is (not (some #{"write-through"} (map clojure.string/lower-case missing))))))

(deftest assess-catches-dropped-essence
  (testing "a brief that compresses away the actual decision FAILS (silent loss)"
    (let [lossy (str "CONTINUE:\nThe team settled the consistency model and an "
                  "eviction policy. Direction is clear.\nRULED-OUT:\none option")
          {:keys [ok? coverage missing]} (core/assess-continuation
                                           {:compiled lossy :source turn-source})]
      (is (not ok?) "vague summary that dropped the specifics is rejected")
      (is (< coverage 0.6))
      (is (some #{"write-through"} (map clojure.string/lower-case missing))
        "the dropped decision is named as missing")
      (is (some #{"lru"} (map clojure.string/lower-case missing))))))

;; --- LIVE tripwire: structural gate, NOT accuracy; must not reject dense λ ---

(deftest tripwire-accepts-usable-brief
  (let [brief (str "CONTINUE:\nλ session(). decided(write-through) | next(invalidation)\n"
                "RULED-OUT:\nλ ruled_out(). write_behind() | disproven(durability)")
        {:keys [ok?]} (core/tripwire {:compiled brief :source turn-source})]
    (is ok? "a non-empty brief with a CONTINUE body passes the tripwire")))

(deftest tripwire-does-not-gate-on-lexical-coverage
  (testing "dense λ that abstracts away literal source tokens still passes (coverage would have rejected it)"
    (let [source "user: pick a cache write policy\nassistant: write-through with LRU eviction."
          ;; brief carries the MEANING in λ but shares almost no literal tokens
          ;; with the source — a coverage gate (θ=0.6) would reject this.
          lambda "CONTINUE:\nλ decision(). ✓(synchronous-persist) | evict(recency) | ¬defer"
          cov    (core/assess-continuation {:compiled lambda :source source})
          {:keys [ok? coverage]} (core/tripwire {:compiled lambda :source source})]
      (is (< (:coverage cov) 0.6) "lexical coverage IS low for dense λ")
      (is ok? "…but the tripwire still accepts it — we prime, we don't coverage-gate")
      (is (= coverage (:coverage cov)) "coverage is surfaced for observability, not gating"))))

(deftest tripwire-rejects-empty-or-contentless
  (is (not (:ok? (core/tripwire {:compiled "" :source turn-source}))) "empty → reject")
  (is (not (:ok? (core/tripwire {:compiled "   \n  " :source turn-source}))) "blank → reject")
  (is (not (:ok? (core/tripwire {:compiled "CONTINUE:\n" :source turn-source})))
    "a CONTINUE header with no body and no substance → reject"))

(deftest merge-ruled-out-is-monotonic
  (testing "result is always a superset of the existing ledger"
    (let [a   ["λ ruled_out(). write_behind()"]
          b   ["λ ruled_out(). polling()" "λ ruled_out(). write_behind()"]
          ab  (core/merge-ruled-out a b)
          abc (core/merge-ruled-out ab ["λ ruled_out(). global_lock()"])]
      (is (every? (set ab) a) "existing entries survive the merge")
      (is (= 2 (count ab)) "dedup: write_behind not duplicated")
      (is (every? (set abc) ab) "monotonic across a second merge")
      (is (= 3 (count abc))))))

(deftest merge-ruled-out-never-shrinks-over-a-sequence
  (let [merges [["a"] ["b" "a"] [] ["c" "b"] ["a"]]
        sizes  (->> merges
                 (reductions core/merge-ruled-out [])
                 (map count))]
    (is (apply <= sizes) (str "ledger size is non-decreasing: " (vec sizes)))))

(deftest assemble-system-is-bounded-in-turn-count
  (testing "adding more compiled λ / a fixed last-raw does not scale with turn count"
    (let [base     "You are Ouroboros."
          compiled "STATE:\nλ session(). big(state)"
          last-raw "user: q\nassistant: a"
          s1       (core/assemble-system base compiled last-raw)
          ;; Simulate 100 turns having elapsed: the compiled λ is still ONE block,
          ;; last-raw is still ONE exchange. Nothing accumulates.
          s100     (core/assemble-system base compiled last-raw)]
      (is (= (count s1) (count s100)) "size independent of elapsed turns")
      (is (str/includes? s1 base))
      (is (str/includes? s1 compiled))
      (is (str/includes? s1 last-raw))
      (is (not (str/includes? s1 "turn 1")) "no verbatim history accumulated"))))

(deftest assemble-system-tolerates-empty-memory
  (let [s (core/assemble-system "Base." nil nil)]
    (is (= "Base." s) "first turn: no compiled λ, no prior exchange")))
