(ns ouroboros.verdict
  "The VERDICT topology runner — kind-agnostic (agent-model spec: many kinds
  ride few base topologies). A verdict agent is a one-shot conversation whose
  turn ends in a FORCED, schema-validated verdict; the schema comes from the
  genome's KIND (ouroboros.agents.core/verdict-schemas), the semantics from
  the genome BODY. Two kinds ride this topology today:

    judge  → {:status :pass|:fail :notes}  GATES   (a :cond transition consumes it)
    scorer → {:score 1-10 :notes}          MEASURES (rank · accumulate · fitness)

  Mechanics (source-verified): escapement sees `:verdict-schema`, forces a
  `submit_verdict` tool call at turn end (other tools stripped, framework
  nudge appended), decodes the JSON input through Malli's json-transformer
  (\"pass\" → :pass) and validates. The validated map arrives on the idle
  event at [:_event :data :verdict]; validation failure raises
  :error.llm.verdict-validation (the worker dies) → this chart routes ANY
  :error.llm.* to :failed rather than hanging. `submit_verdict` is RESERVED —
  never granted via :real-tools.

  Cross-family routing: the genome's `model:` alias selects the endpoint via
  ouroboros.models/llm-config — each run! is a hermetic lib/run carrying ONLY
  the credential its model needs (see ouroboros.models for why not one
  multi-credential backend). `run!` accepts a `:model` override so the SAME
  genome can be run across model families (`run-across!`) — uncorrelated
  scoring noise cancels in the aggregate (spec §scorer-hazard).

  Run: bb judge \"<subject>\"   (llm-judge genome, ornith @5102)
       bb score \"<subject>\"   (gene-scorer genome, rubric-anchored)"
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
  "The verdict topology: one llm-conversation seeded with `subject`, forced
  submit_verdict per the genome's KIND schema. The validated verdict is
  delivered OUT via `verdict-atom` (the chart is built per-run; the closure is
  the simplest seam — lib/run reports :status, not data-model contents).
  `model` is the (possibly overridden) routing alias."
  [genome model subject verdict-atom]
  (chart/statechart
    {:initial :working}
    (state {:id :working}
      (h/llm-conversation
        {:id             (:slug genome)
         :system         (:prompt genome)
         :model          model
         :stream?        false
         :real-tools     (:tools genome)
         :verdict-schema (core/verdict-schema (:kind genome))
         ;; Tool-less verdicts (llm-judge, gene-scorer) decide from the
         ;; subject alone — 3 turns is plenty. An evidence-READING verdict
         ;; agent (verifier: fs/read·grep·glob) spends turns gathering before
         ;; the forced submit — 3 clips it mid-read (live-proven: max-turns
         ;; death, first verifier run). Turns follow the GRANT; budget-ms
         ;; stays the runaway guard.
         :max-turns      (if (seq (:tools genome)) 12 3)
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
  "Run the verdict-kind agent `genome-id` on `subject`. Blocking. Returns
  {:status :verdict :model :session-dir} — :verdict is the validated,
  kind-shaped map, or nil when the run failed.
  `opts`: {:root \".\" :model <alias override — cross-family runs>}."
  ([genome-id subject] (run! genome-id subject {}))
  ([genome-id subject {:keys [root model] :or {root "."}}]
   (let [genome      (agents/genome genome-id root)
         model       (or model (:model genome))
         verdict     (atom nil)
         session-id  (str (:slug genome) "-" (System/currentTimeMillis))
         session-dir (session/session-dir root session-id)
         result      (lib/run
                       (merge
                         {:chart           (verdict-chart genome model subject verdict)
                          :session-id      session-id
                          :session-dir     session-dir
                          :transcript-path (str session-dir "/transcript.jsonl")
                          :checkpoint-dir  (str session-dir "/checkpoints")
                          :tool-registry   (tools/new-registry root)}
                         (models/llm-config model)))]
     {:status      (:status result)
      :verdict     @verdict
      :model       model
      :session-dir session-dir})))

;; ---------------------------------------------------------------------------
;; Cross-family aggregation (spec §scorer-hazard: LLM absolute scores are
;; noisy + uncalibrated — score with different-family models and aggregate;
;; uncorrelated noise cancels).
;; ---------------------------------------------------------------------------

(defn aggregate-scores
  "Pure. Fold scorer results ({:model :verdict {:score :notes}}) into
  {:scores {alias score} :mean :notes {alias notes}}. Results without a
  verdict are dropped; nil when NONE scored (fail loud at the caller)."
  [results]
  (let [scored (filterv #(some-> % :verdict :score) results)]
    (when (seq scored)
      {:scores (into {} (map (juxt :model #(get-in % [:verdict :score]))) scored)
       :mean   (double (/ (reduce + (map #(get-in % [:verdict :score]) scored))
                         (count scored)))
       :notes  (into {} (map (juxt :model #(get-in % [:verdict :notes]))) scored)})))

(defn run-across!
  "Run ONE verdict genome on `subject` across `model-aliases` (each a hermetic
  run!), returning {:results [...] :aggregate <aggregate-scores>}. The
  cross-family lever: same genome, different model families."
  [genome-id subject model-aliases]
  (let [results (mapv #(run! genome-id subject {:model %}) model-aliases)]
    {:results results :aggregate (aggregate-scores results)}))

(defn -main [& [genome-slug & subject-args]]
  (let [subject (str/join " " subject-args)]
    (when (or (str/blank? (or genome-slug "")) (str/blank? subject))
      (println "usage: bb judge|score \"<subject text>\"")
      (System/exit 2))
    (let [{:keys [status verdict model session-dir]} (run! (keyword genome-slug) subject)]
      (println "status      :" status)
      (println "model       :" model)
      (println "verdict     :" (pr-str verdict))
      (println "session-dir :" session-dir)
      (shutdown-agents)
      (System/exit (if (and (= :done status) (some? verdict)) 0 1)))))
