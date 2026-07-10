(ns ouroboros.cache-report-test
  "Deterministic tests for the cache-report pure kernel — no filesystem, no LLM."
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest testing is]]
    [ouroboros.cache-report :as cr]))

(defn- resp [region in cached out]
  {:event "llm/response"
   :data  {:invokeid region
           :usage    {:input-tokens in
                      :cache-read-input-tokens cached
                      :output-tokens out}}})

(deftest response-entries-filters-and-shapes
  (testing "only llm/response rows with an invokeid + usage survive"
    (let [rows [(resp "hot" 100 0 10)
                {:event "chart/event" :data {:invokeid "hot"}}   ; no usage
                {:event "llm/response" :data {:usage {:input-tokens 5}}} ; no invokeid
                (resp "compact" 200 0 20)]]
      (is (= [{:region "hot" :in 100 :cached 0 :out 10}
              {:region "compact" :in 200 :cached 0 :out 20}]
            (cr/response-entries rows)))))
  (testing "missing cached/out default to 0"
    (let [rows [{:event "llm/response"
                 :data {:invokeid "hot" :usage {:input-tokens 7}}}]]
      (is (= [{:region "hot" :in 7 :cached 0 :out 0}]
            (cr/response-entries rows))))))

(deftest analyze-classifies-turns
  (let [a (cr/analyze [{:region "hot" :in 2431 :cached 0 :out 56}      ; cold start
                       {:region "compact" :in 275 :cached 0 :out 18}
                       {:region "hot" :in 2467 :cached 2400 :out 199}  ; warm
                       {:region "hot" :in 2510 :cached 0 :out 31}])]   ; BUST
    (testing "first hot turn is cold-start, not a bust"
      (is (= :cold-start (:status (first (:hot a))))))
    (testing "cache reuse ⇒ warm; zero reuse after turn 1 ⇒ BUST"
      (is (= [:cold-start :warm :BUST] (mapv :status (:hot a))))
      (is (= [3] (get-in a [:summary :busts]))))
    (testing "paid = in - cached"
      (is (= [2431 67 2510] (mapv :paid (:hot a)))))
    (testing "reuse ratio over post-start turns only"
      ;; eligible: reused 2400, paid 67 + 2510 = 2577 → 2400/4977 ≈ 48%
      (is (= 48 (get-in a [:summary :reuse-pct]))))
    (testing "non-hot regions aggregate"
      (is (= {:calls 1 :in 275 :out 18} (get-in a [:other "compact"]))))))

(deftest analyze-empty-and-healthy
  (testing "no entries → empty report, no busts, nil ratio"
    (let [a (cr/analyze [])]
      (is (= [] (:hot a)))
      (is (= [] (get-in a [:summary :busts])))
      (is (nil? (get-in a [:summary :reuse-pct])))))
  (testing "healthy session: no busts, high reuse"
    (let [a (cr/analyze [{:region "hot" :in 2431 :cached 0 :out 56}
                         {:region "hot" :in 2467 :cached 2400 :out 31}
                         {:region "hot" :in 2532 :cached 2400 :out 36}])]
      (is (= [] (get-in a [:summary :busts])))
      (is (<= 90 (get-in a [:summary :reuse-pct]))))))

(deftest format-report-renders
  (let [a (cr/analyze [{:region "hot" :in 100 :cached 0 :out 5}
                       {:region "hot" :in 120 :cached 0 :out 6}])
        s (cr/format-report "s-1" a)]
    (is (str/includes? s "session: s-1"))
    (is (str/includes? s "cold-start"))
    (is (str/includes? s "BUST"))
    (is (str/includes? s "busts: 2"))))
