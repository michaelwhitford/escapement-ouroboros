(ns ouroboros.tools
  "Custom escapement Tools exposing mementum to the LLM — the loop's hands.
  Dispatched inside the worker (invisible to the chart) per `escapement.tools.protocol`.
  `invoke` never throws; rejections become `{:is-error true}` corrective tool_results.

  Both tools call the pathom-FREE core (`ouroboros.mementum.store`) directly —
  no pathom on this path, per the composition decision in state.md.

  Wiring strategy C (\"drive-yourself\", escapement-tools.md): `new-registry`
  builds a FRESH, isolated registry per call — no global/singleton mutation."
  (:require
    [clojure.string :as str]
    [escapement.tools.protocol :as tp]
    [ouroboros.mementum.store :as store]))

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

(defn new-registry
  "A fresh, isolated tool registry (escapement wiring strategy C) exposing only
  the two mementum tools, rooted at `root` (default \".\")."
  ([] (new-registry "."))
  ([root]
   (tp/new-registry [(->ContextTool root) (->ProposeMemoryTool root)])))
