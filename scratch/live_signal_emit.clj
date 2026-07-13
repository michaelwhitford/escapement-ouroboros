(ns live-signal-emit
  "LIVE smoke for the signals substrate END-TO-END through the REAL paths:
  custom-tier genome with a `signals:` grant → loader (auto :signal/emit
  grant + exemplar projection assembled into the prompt) → chart-hosted LLM
  (thinking-ON, localhost:5100) → :signal/emit tool (EDN-in-JSON-string
  seam) → gated emit! → signals/ file → all-signals read-back.

  The one seam the edn-signal-emission experiment did NOT cover: the model
  must place EDN inside a JSON tool-argument string. This proves (or
  disproves) that escaping surface.

  Run: bb --config bb.edn scratch/live_signal_emit.clj"
  (:require
    [babashka.fs :as fs]
    [com.fulcrologic.statecharts.chart :as chart]
    [com.fulcrologic.statecharts.elements :refer [state transition final]]
    [escapement.chart.helpers :as h]
    [escapement.lib :as lib]
    [escapement.lib.event-sink :as sink]
    [ouroboros.agents :as agents]
    [ouroboros.signals :as signals]
    [ouroboros.signals.core :as signals.core]
    [ouroboros.tools :as tools]))

(def genome-doc
  (str "---\n"
       "type: ouroboros/agent\n"
       "description: live-smoke signal emitter\n"
       "kind: proposer\n"
       "tools: []\n"
       "signals: [s1/report]\n"
       "---\n"
       "λ emitter. read(⟨SOURCE⟩) → emit(ONE :s1/report via signal_emit) → reply(\"done\")\n"
       "  | facts_from_source_only | ¬invent"))

(def message
  (str "⟨SOURCE⟩\n"
       "The maintenance sweep just finished: 3 agents ran, 2 proposals were "
       "filed, one agent failed with a timeout. Total wall time 142 seconds.\n"
       "⟨/SOURCE⟩\n\n"
       "Emit exactly ONE :s1/report signal capturing this outcome, then say done."))

(defn -main [& _]
  (let [root (str (fs/create-temp-dir {:prefix "live-signal-"}))]
    (try
      (fs/create-dirs (fs/path root "agents"))
      (spit (str (fs/path root "agents" "emitter.md")) genome-doc)
      (let [g       (agents/genome :emitter root)
            _       (println "grant  :" (pr-str (:tools g)) (pr-str (:signals g)))
            adapter (sink/make-adapter)
            emit-chart
            (chart/statechart {:initial :emit}
              (state {:id :emit}
                (h/llm-conversation
                  {:id         "emit"
                   :system     (:prompt g)
                   :model      (:model g)
                   :stream?    true
                   :budget-ms  240000
                   :max-turns  3
                   :real-tools (:tools g)
                   :message    message})
                (transition {:event :llm.idle :target :done})
                (transition {:event :error.llm :target :failed}))
              (final {:id :done})
              (final {:id :failed}))
            result (lib/run
                     {:chart          emit-chart
                      :session-id     (str "signal-smoke-" (System/currentTimeMillis))
                      :session-dir    (str (fs/path root "sessions" "smoke"))
                      :tool-registry  (tools/new-registry root
                                        {:source       "live-smoke"
                                         :signal-types (set (:signals g))})
                      :credentials    [{:provider :openai :api-key "sk-local"
                                        :base-url "http://localhost:5100/v1"}]
                      :config         {:llm/aliases {:local [{:provider :openai
                                                              :model "qwen36-35b-a3b"}]}
                                       :llm/preferences [:local]
                                       :llm/eligibility-strict? false}
                      :transcript-tap (fn [row]
                                        (doseq [e (sink/feed! adapter row)]
                                          (case (:type e)
                                            :text-delta (do (print (get-in e [:delta :text])) (flush))
                                            :tool-call  (println "\n[tool]" (:name e) (pr-str (:params e)))
                                            nil)))})
            sigs   (signals/all-signals root)]
        (println "\n\nstatus :" (:status result))
        (println "signals:" (count sigs))
        (doseq [s sigs]
          (println "  " (pr-str s))
          (println "  valid?" (nil? (signals.core/validate (dissoc s :signal/id)))))
        (shutdown-agents)
        (System/exit (if (and (= :done (:status result))
                              (= 1 (count sigs))
                              (nil? (signals.core/validate (dissoc (first sigs) :signal/id))))
                       0 1)))
      (finally (fs/delete-tree root)))))

(-main)
