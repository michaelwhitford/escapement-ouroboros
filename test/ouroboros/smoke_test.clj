(ns ouroboros.smoke-test
  "Deterministic, no-network unit tests for the runtime smoke path.

  Phase B (live LLM against localhost:5100) is an INTEGRATION check owned by
  `bb smoke` — it needs the llama.cpp server up and is intentionally NOT part
  of `bb test`. Here we assert only the hermetic, secret-free contract:
  `escapement.lib/run` drives the no-LLM greet chart to quiescence."
  (:require
    [clojure.test :refer [deftest is testing]]
    [escapement.lib :as lib]
    [ouroboros.smoke :as smoke]))

(deftest no-llm-chart-runs-to-done
  (testing "lib/run drives the no-LLM greet chart to :status :done"
    (let [result (lib/run {:chart       smoke/greet
                           :session-id  "test-a"
                           ;; schema-required unconditionally, even with no LLM
                           :credentials [{:provider :openai :api-key "sk-unused"}]})]
      (is (= :done (:status result))
        ":status :done is the success signal for a top-level final")
      (is (string? (:run-id result))
        "a run-id string is generated per call")
      (is (= 36 (count (:run-id result)))
        "run-id is a uuid string")
      (is (= [] (:final-config result))
        "top-level final empties the configuration"))))

(deftest credentials-required-unconditionally
  (testing "the closed options schema rejects a run with no :credentials"
    (is (thrown? clojure.lang.ExceptionInfo
          (lib/run {:chart smoke/greet :session-id "test-missing-creds"}))
      "omitting :credentials → closed schema rejection")))
