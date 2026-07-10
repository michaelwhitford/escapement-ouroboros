(ns ouroboros.tools
  "Custom escapement Tools exposing mementum to the LLM — the curator's hands.
  Dispatched inside the worker (invisible to the chart) per `escapement.tools.protocol`.
  `invoke` never throws; rejections become `{:is-error true}` corrective tool_results.

  Both tools call the pathom-FREE core (`ouroboros.mementum.store`) directly —
  no pathom on this path, per the composition decision in state.md.

  Wiring strategy C (\"drive-yourself\", escapement-tools.md): `new-registry`
  builds a FRESH, isolated registry per call — no global/singleton mutation."
  (:require
    [clojure.string :as str]
    [escapement.tools.protocol :as tp]
    [ouroboros.curator.core :as metabolize]
    [ouroboros.mementum.store :as store]
    [ouroboros.session :as session]))

;; ---------------------------------------------------------------------------
;; :mementum/context — read-only self-model digest. No input.
;; ---------------------------------------------------------------------------

(defn- summary-lines [summaries empty-label]
  (if (seq summaries)
    (str/join "\n" (map #(str "- " (:slug %) " :: " (:description %)) summaries))
    empty-label))

(defn digest
  "Plain-text self-model: knowledge index + memory index + recent commits.
  Pure, deterministic given `root`'s current state."
  [root]
  (let [knowledge (store/list-summaries root :knowledge)
        memories  (store/list-summaries root :memory)
        log       (store/recall-log root 5)]
    (str "KNOWLEDGE (" (count knowledge) " pages):\n"
      (summary-lines knowledge "(none)")
      "\n\nMEMORIES (" (count memories) " so far):\n"
      (summary-lines memories "(none yet)")
      "\n\nRECENT COMMITS:\n"
      (if (seq log)
        (str/join "\n" (map #(str (:hash %) " " (:subject %)) log))
        "(none)"))))

(defrecord ContextTool [root]
  tp/Tool
  (tool-name [_] :mementum/context)
  (description [_] "Read Ouroboros's current self-model: the knowledge page index, the memory index, and recent commits. No input. Call this FIRST, before proposing anything.")
  (input-schema [_] [:map {:closed true}])
  (invoke [_ _input]
    {:result (digest root) :is-error false}))

;; ---------------------------------------------------------------------------
;; :mementum/sessions — read prior conversations as λ-compacted arrays. No input.
;; The cross-session memory the curator metabolizes (≥3 recurrences → a
;; knowledge-page candidate). Reads the FILESYSTEM checkpoints directly, not git.
;; ---------------------------------------------------------------------------

;; How many most-recent conversation sessions to surface. λ bounds tokens per
;; message; this bounds sessions per digest.
(def ^:private sessions-limit 8)

(defn sessions-digest
  "Impure loader: the metabolize digest of the most recent `limit` CONVERSATION
  sessions under `root` — those carrying a compacted `:messages` array (chat /
  compact sessions). Sessions without messages (loop / smoke / … reflections)
  are excluded. Newest LAST (chronological, so the curator reads toward the
  present). Pure rendering is delegated to `ouroboros.curator.core`."
  [root limit]
  (->> (session/list-session-ids root)
    (map (fn [id] {:id id :messages (session/session-messages root id)}))
    (filter #(seq (:messages %)))
    (sort-by (comp metabolize/recency-key :id))
    (take-last limit)
    (metabolize/sessions-digest)))

(defrecord SessionsTool [root]
  tp/Tool
  (tool-name [_] :mementum/sessions)
  (description [_] "Read Ouroboros's most recent conversation sessions as λ-compacted message arrays — the cross-session memory. Each session lists its turns in order; assistant turns marked λ are the compacted essence. No input. Use this to spot RECURRING topics/decisions/patterns across sessions (≥3 on one topic ⇒ a knowledge-page candidate) and to ground any memory you propose in what actually happened.")
  (input-schema [_] [:map {:closed true}])
  (invoke [_ _input]
    {:result (sessions-digest root sessions-limit) :is-error false}))

;; ---------------------------------------------------------------------------
;; :mementum/propose-memory — writes to the working tree ONLY. Commit is
;; human-gated (AGENTS.md invariant) — this tool never touches git.
;; ---------------------------------------------------------------------------

(defrecord ProposeMemoryTool [root]
  tp/Tool
  (tool-name [_] :mementum/propose-memory)
  (description [_]
    (str "Propose ONE new mementum memory as a complete OKF document. Input: "
      "{:slug \"kebab-case-id\" :content \"<OKF doc>\"}. The content MUST be "
      "'---\\ntype: mementum/memory\\ndescription: <one line>\\n---\\n<symbol> <body>' "
      "where <symbol> is one of 💡🔄🎯🌀❌✅🔁. Writes ONLY the working tree — a human "
      "must approve before it is committed. On rejection, fix and retry."))
  (input-schema [_] [:map {:closed true} [:slug :string] [:content :string]])
  (invoke [_ {:keys [slug content]}]
    (try
      (let [{:keys [path]} (store/store! root :memory slug content)]
        {:result   (str "Proposed (NOT committed — awaiting human approval): " path)
         :is-error false})
      (catch clojure.lang.ExceptionInfo e
        (let [{:keys [errors]} (ex-data e)]
          {:result   (str "Rejected — fix and retry. OKF errors: " (pr-str errors))
           :is-error true})))))

;; ---------------------------------------------------------------------------
;; Registry assembly
;; ---------------------------------------------------------------------------

(defn all-tools
  "Every tool Ouroboros registers, rooted at `root`. This list IS the registry
  CEILING (agent-model spec): a genome SELECTS from it, it cannot INVENT. No
  commit/push/git-write tool exists here — the human-gate invariant is
  unreachable-by-absence."
  [root]
  [(->ContextTool root) (->SessionsTool root) (->ProposeMemoryTool root)])

(def read-only-tools
  "The READ-ONLY floor (agent-model spec): the flat, kind-independent grant an
  agent genome receives when its frontmatter carries NO `tools:` key at all.
  Forgetting a grant fails SAFE — the agent is inert, never dangerous. NOTE:
  an EXPLICIT `tools: []` means exactly no tools (not the floor)."
  #{:mementum/context :mementum/sessions})

(defn tool-names
  "The set of registered tool names — the universe `ouroboros.agents` validates
  genome grants against (tools ⊆ registry, else the compiler rejects)."
  []
  (set (map tp/tool-name (all-tools "."))))

(defn new-registry
  "A fresh, isolated tool registry (escapement wiring strategy C) exposing the
  mementum tools, rooted at `root` (default \".\"): context + sessions (read) and
  propose-memory (gated write)."
  ([] (new-registry "."))
  ([root]
   (tp/new-registry (all-tools root))))
