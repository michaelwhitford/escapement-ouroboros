(ns ouroboros.smoke
  "First light — prove `escapement.lib/run` runs IN the Ouroboros repo.

  Two phases, escalating trust:

    Phase A  no-secret, no-LLM greet chart  → proves runtime + deps + bb-compat
    Phase B  real :llm-conversation         → proves the LLM path against a local
                                              llama.cpp server (OpenAI-compat /v1)

  Everything is injected as data (hermetic contract): the lib path never reads
  env or disk. The local-model wiring here is the same shape Ouroboros will use
  to configure any provider.

  Run:  bb smoke"
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [com.fulcrologic.statecharts.chart :as chart]
    [com.fulcrologic.statecharts.elements :refer [state transition final]]
    [escapement.chart.helpers :as h]
    [escapement.lib :as lib]
    [escapement.lib.event-sink :as sink]
    [escapement.tools.protocol :as tools]))

;; ---------------------------------------------------------------------------
;; Local llama.cpp server (OpenAI-compatible). Injected as data below.
;; ---------------------------------------------------------------------------

(def ^:private local-base-url "http://localhost:5100/v1")
(def ^:private local-model    "qwen35-35b-a3b")

;; ---------------------------------------------------------------------------
;; Phase A — no-LLM greet chart.
;; initial state → eventless transition → final. Zero secrets, zero network.
;; ---------------------------------------------------------------------------

(def greet
  (chart/statechart
    {:initial :hello}
    (state {:id :hello}
      (transition {:target :done}))
    (final {:id :done})))

(defn phase-a []
  (println "\n=== Phase A: no-LLM lib/run smoke ===")
  ;; :credentials is schema-required unconditionally (closed contract), even
  ;; though this chart has no :llm-conversation — it is never consulted here.
  (let [result (lib/run {:chart       greet
                         :session-id  "smoke-a"
                         :credentials [{:provider :openai :api-key "sk-unused"}]})
        ;; :status :done is the success signal. A TOP-LEVEL final empties the
        ;; configuration, so :final-config is [] here — expected, not a failure.
        ok?    (= :done (:status result))]
    (println "status      :" (:status result))
    (println "final-config:" (:final-config result) "(empty = root reached final; OK)")
    (println "run-id      :" (:run-id result))
    (println (if ok? "✅ Phase A PASS" "❌ Phase A FAIL"))
    ok?))

;; ---------------------------------------------------------------------------
;; Phase B — real :llm-conversation against localhost:5100.
;; Chart authored with escapement.chart.helpers; one turn, no tools, then idle.
;; ---------------------------------------------------------------------------

(def llm-chart
  (chart/statechart
    {:initial :ask}
    (state {:id :ask}
      (h/llm-conversation
        {:id        "ask"
         :system    "You are terse. Answer in ONE short sentence, no preamble."
         :model     :local
         :stream?   true
         :budget-ms 180000
         :message   "In one sentence: what is an ouroboros?"})
      (transition {:event :llm.idle :target :done}
        (h/capture-llm-output {:as "answer.md"})))
    (final {:id :done})))

(defn phase-b []
  (println "\n=== Phase B: LLM lib/run against" local-base-url "(" local-model ") ===")
  (let [adapter (sink/make-adapter)
        result  (lib/run
                  {:chart          llm-chart
                   :session-id     "smoke-b"
                   ;; LLM processor wires only when BOTH backend + tool-registry present.
                   :tool-registry  (tools/new-registry)
                   ;; Hermetic, data-driven credential injection. :base-url override
                   ;; merges over the :openai provider template → points at localhost.
                   :credentials    [{:provider :openai
                                     :api-key  "sk-local"
                                     :base-url local-base-url}]
                   :config         {:llm/aliases             {:local [{:provider :openai :model local-model}]}
                                    :llm/preferences         [:local]
                                    ;; local model isn't in the bundled catalog → strict gate would reject.
                                    :llm/eligibility-strict? false}
                   ;; Stream assistant tokens live via the normalized public event.
                   :transcript-tap (fn [row]
                                     (doseq [e (sink/feed! adapter row)]
                                       (when (= :text-delta (:type e))
                                         (print (get-in e [:delta :text]))
                                         (flush))))})
        answer  (let [f (io/file (:session-dir result) "artifacts" "answer.md")]
                  (when (.exists f) (str/trim (slurp f))))
        ok?     (and (= :done (:status result)) (boolean (seq answer)))]
    (println)
    (println "status      :" (:status result))
    (println "final-config:" (:final-config result))
    (println "answer.md   :" answer)
    (println (if ok? "✅ Phase B PASS" "❌ Phase B FAIL"))
    ok?))

(defn -main [& _]
  (let [a (phase-a)
        b (try
            (phase-b)
            (catch Exception e
              (println "❌ Phase B error:" (ex-message e))
              false))]
    (println "\n=== SMOKE" (if (and a b) "GREEN ✅" "RED ❌") "===")
    (shutdown-agents)
    (System/exit (if (and a b) 0 1))))
