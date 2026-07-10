(ns ouroboros.judge
  "T-verdict runner — the JUDGE kind (agent-model BUILD STEP 3).

  A judge is a one-shot agent whose turn ends in a FORCED, schema-validated
  verdict: escapement sees `:verdict-schema` on the conversation and, at turn
  end, forces a `submit_verdict` tool call (all other tools stripped, framework
  nudge appended), decodes the JSON input through Malli's json-transformer
  (\"pass\" → :pass) and validates it. The validated map arrives on the idle
  event at [:_event :data :verdict]. Validation failure raises
  :error.llm.verdict-validation instead (the worker dies) → this chart routes
  ANY :error.llm.* to :failed rather than hanging.

  Division of labor (agent-model spec §Judge & Scorer):
    SCHEMA    → the KIND (ouroboros.agents.core/verdict-schemas — uniform)
    SEMANTICS → the genome BODY (when pass/fail, what notes — the agent reasons)
    frontmatter carries NO verdict field.

  A judge GATES: the returned {:status :pass|:fail :notes} is machine-consumed
  (a :cond transition in a workflow, or the caller of run!). `submit_verdict`
  is a RESERVED name — never granted via :real-tools.

  Cross-family routing: the genome's `model:` alias selects the endpoint via
  ouroboros.models/llm-config — each run! is a hermetic lib/run carrying ONLY
  the credential its model needs (see ouroboros.models for why not one
  multi-credential backend).

  Run: bb judge \"<subject text>\"   (routes per the llm-judge genome: ornith @5102)"
  (:refer-clojure :exclude [run!])
  (:require
    [clojure.string :as str]
    [com.fulcrologic.statecharts.chart :as chart]
    [com.fulcrologic.statecharts.elements :refer [final script state transition]]
    [escapement.chart.helpers :as h]
    [escapement.lib :as lib]
    [ouroboros.agents :as agents]
    [ouroboros.agents.core :as core]
    [ouroboros.models :as models]
    [ouroboros.session :as session]
    [ouroboros.tools :as tools]))

(defn- verdict-chart
  "T-verdict: one llm-conversation seeded with `subject`, forced submit_verdict
  per the genome's KIND schema. The validated verdict is delivered OUT via
  `verdict-atom` (the chart is built per-run; the closure is the simplest
  seam — lib/run reports :status, not data-model contents)."
  [genome subject verdict-atom]
  (chart/statechart
    {:initial :judging}
    (state {:id :judging}
      (h/llm-conversation
        {:id             "judge"
         :system         (:prompt genome)
         :model          (:model genome)
         :stream?        false
         :real-tools     (:tools genome)
         :verdict-schema (core/verdict-schema (:kind genome))
         :max-turns      3
         :budget-ms      240000
         :message        subject})
      (transition {:event :llm.idle :target :done}
        (script {:expr (fn [_env data]
                         (reset! verdict-atom (get-in data [:_event :data :verdict]))
                         nil)}))
      ;; :error.llm.* (verdict-validation, model errors, budget) — the worker
      ;; died without an idle; terminate instead of hanging the run.
      (transition {:event :error.llm :target :failed}))
    (final {:id :done})
    (final {:id :failed})))

(defn run!
  "Judge `subject` with the `genome-id` agent (default :llm-judge) against
  `root`. Blocking. Returns {:status :verdict :session-dir} — :verdict is the
  validated {:status :pass|:fail :notes} map, or nil when the run failed."
  ([subject] (run! :llm-judge subject "."))
  ([genome-id subject] (run! genome-id subject "."))
  ([genome-id subject root]
   (let [genome      (agents/genome genome-id root)
         verdict     (atom nil)
         session-id  (str "judge-" (System/currentTimeMillis))
         session-dir (session/session-dir root session-id)
         result      (lib/run
                       (merge
                         {:chart           (verdict-chart genome subject verdict)
                          :session-id      session-id
                          :session-dir     session-dir
                          :transcript-path (str session-dir "/transcript.jsonl")
                          :checkpoint-dir  (str session-dir "/checkpoints")
                          :tool-registry   (tools/new-registry root)}
                         (models/llm-config (:model genome))))]
     {:status      (:status result)
      :verdict     @verdict
      :session-dir session-dir})))

(defn -main [& args]
  (let [subject (str/join " " args)]
    (when (str/blank? subject)
      (println "usage: bb judge \"<subject text to judge>\"")
      (System/exit 2))
    (let [{:keys [status verdict session-dir]} (run! subject)]
      (println "status      :" status)
      (println "verdict     :" (pr-str verdict))
      (println "session-dir :" session-dir)
      (shutdown-agents)
      (System/exit (if (and (= :done status) (some? verdict)) 0 1)))))
