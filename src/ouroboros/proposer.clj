(ns ouroboros.proposer
  "The PROPOSER-topology runner — kind-shaped, genome-parameterized (the
  judge→verdict generalization applied to the curator: the runner was always
  the proposer topology wearing one agent's name; `bb maintain` forced the
  rename — design/scheduled-maintenance §Naming).

  A proposer run is ONE hermetic lib/run: observe(corpus via granted tools) →
  detect(pattern) → propose(ONE artifact) → stop. The chart is built per-run
  FROM the genome (the verdict precedent): :system/:model/:real-tools all
  genome-driven; per-run credential injection via ouroboros.models/llm-config
  (sidesteps the multi-model first-wins collision); the tool registry is
  armed with the genome's signal grant + the agent slug as :signal/source.

  PROPOSAL ONLY: artifacts land in the working tree (mementum/memories/ via
  :mementum/propose-memory, proposals/ via :ouro/propose-change) —
  UNCOMMITTED. The human gate is the lifecycle (AGENTS.md invariant). This
  namespace never touches git.

  Run: bb maintain <slug>   (single agent)  ·  bb maintain  (the full sweep)"
  (:require
    [com.fulcrologic.statecharts.chart :as chart]
    [com.fulcrologic.statecharts.elements :refer [state transition final]]
    [escapement.chart.helpers :as h]
    [escapement.lib :as lib]
    [escapement.lib.event-sink :as sink]
    [ouroboros.agents :as agents]
    [ouroboros.models :as models]
    [ouroboros.session :as session]
    [ouroboros.tools :as tools]))

(def default-subject
  "The standing kick-off when the schedule entry carries no :subject —
  the genome body is the program; this just starts it."
  "Begin your maintenance run: observe with your tools, then propose exactly ONE artifact per your instructions.")

(def default-budget-ms 240000)

(defn propose-chart
  "Build the proposer chart for a compiled `genome`: one llm-conversation,
  parked→done on :llm.idle, failed on :error.llm."
  [genome subject budget-ms]
  (chart/statechart
    {:initial :observe}
    (state {:id :observe}
      (h/llm-conversation
        {:id         "propose"
         :system     (:prompt genome)
         :model      (:model genome)
         :stream?    true
         :budget-ms  (or budget-ms default-budget-ms)
         :real-tools (:tools genome)
         :message    (or subject default-subject)})
      (transition {:event :llm.idle :target :done}
        (h/capture-llm-output {:as "reflection.md"}))
      (transition {:event :error.llm :target :failed}))
    (final {:id :done})
    (final {:id :failed})))

(defn run!
  "Run the proposer genome `slug` (keyword or string) against `root`.
  `opts` {:subject :budget-ms :quiet?} — subject is the schedule's standing
  prompt. Streams tokens to stdout unless :quiet?. Returns
  {:status <lib/run status> :session-dir <dir> :agent <kw>}."
  ([slug] (run! slug {}))
  ([slug {:keys [root subject budget-ms quiet?] :or {root "."}}]
   (let [id          (keyword slug)
         genome      (agents/genome id root)
         adapter     (sink/make-adapter)
         session-id  (str (name id) "-" (System/currentTimeMillis))
         session-dir (session/session-dir root session-id)
         llm         (models/llm-config (:model genome))
         result      (lib/run
                       {:chart           (propose-chart genome subject budget-ms)
                        :session-id      session-id
                        :session-dir     session-dir
                        :transcript-path (str session-dir "/transcript.jsonl")
                        :checkpoint-dir  (str session-dir "/checkpoints")
                        :tool-registry   (tools/new-registry root
                                           {:source       (name id)
                                            :signal-types (set (:signals genome))})
                        :credentials     (:credentials llm)
                        :config          (:config llm)
                        :transcript-tap  (fn [row]
                                           (doseq [e (sink/feed! adapter row)]
                                             (when (and (not quiet?) (= :text-delta (:type e)))
                                               (print (get-in e [:delta :text]))
                                               (flush))))})]
     {:status      (:status result)
      :session-dir session-dir
      :agent       id})))

(defn -main
  "bb entry: run ONE proposer genome (arg 1, e.g. harness-knowledge) and
  print the resulting inbox. The full sweep lives in ouroboros.schedule."
  [& args]
  (let [slug (or (first args) "harness-knowledge")
        {:keys [status]} (run! slug {})
        pending    ((requiring-resolve 'ouroboros.proposals/pending) ".")
        untracked  ((requiring-resolve 'ouroboros.proposals/untracked-memories) ".")]
    (println)
    (println "status :" status)
    (println ((requiring-resolve 'ouroboros.proposals/render-inbox) pending untracked))
    (shutdown-agents)
    (System/exit (if (= :done status) 0 1))))
