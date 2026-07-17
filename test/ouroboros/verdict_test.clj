(ns ouroboros.verdict-test
  "Deterministic tests for the verdict topology's pure parts — cross-family
  score aggregation (spec §scorer-hazard: different-family models, aggregate,
  uncorrelated noise cancels). No LLM, no network."
  (:require
    [clojure.test :refer [deftest is]]
    [ouroboros.verdict :as verdict]))

(deftest aggregate-scores-folds-families
  (let [agg (verdict/aggregate-scores
              [{:model :local  :verdict {:score 7 :notes "concrete, minor gap"}}
               {:model :gemma4 :verdict {:score 9 :notes "load-bearing"}}])]
    (is (= {:local 7 :gemma4 9} (:scores agg)))
    (is (= 8.0 (:mean agg)))
    (is (= "load-bearing" (get-in agg [:notes :gemma4])))))

(deftest aggregate-scores-drops-failed-runs
  (let [agg (verdict/aggregate-scores
              [{:model :local :verdict {:score 4 :notes "n"}}
               {:model :dead  :verdict nil}])]
    (is (= {:local 4} (:scores agg)) "a failed family run is dropped, not zeroed")
    (is (= 4.0 (:mean agg)))))

(deftest aggregate-scores-nil-when-none-scored
  (is (nil? (verdict/aggregate-scores [{:model :x :verdict nil}]))
    "no scores ⇒ nil — the caller fails loud, never averages nothing")
  (is (nil? (verdict/aggregate-scores []))))
