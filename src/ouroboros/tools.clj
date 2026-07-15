(ns ouroboros.tools
  "Custom escapement Tools exposing mementum to the LLM — the curator's hands.
  Dispatched inside the worker (invisible to the chart) per `escapement.tools.protocol`.
  `invoke` never throws; rejections become `{:is-error true}` corrective tool_results.

  Both tools call the pathom-FREE core (`ouroboros.mementum.store`) directly —
  no pathom on this path, per the composition decision in state.md.

  Wiring strategy C (\"drive-yourself\", escapement-tools.md): `new-registry`
  builds a FRESH, isolated registry per call — no global/singleton mutation."
  (:require
    [babashka.process :as proc]
    [clojure.edn :as edn]
    [clojure.string :as str]
    [escapement.tools.builtin :as builtin]
    [escapement.tools.protocol :as tp]
    [ouroboros.proposer.core :as metabolize]
    [ouroboros.mementum.store :as store]
    [ouroboros.models :as models]
    [ouroboros.proposals :as proposals]
    [ouroboros.prompts :as prompts]
    [ouroboros.session :as session]
    [ouroboros.signals :as signals]
    [ouroboros.signals.core :as signals.core]))

;; ---------------------------------------------------------------------------
;; :mementum/context — read-only self-model digest. No input.
;; ---------------------------------------------------------------------------

(defn- summary-lines [summaries empty-label]
  (if (seq summaries)
    ;; Lead with the repo-relative PATH (the exact fs-read argument), not the
    ;; bare slug — feed forward: attention reads the address it will open with,
    ;; never re-deriving mementum/<dir>/…/<slug>.md from a slug and guessing.
    (str/join "\n" (map #(str "- " (:path %) " :: " (:description %)) summaries))
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
  present). Pure rendering is delegated to `ouroboros.proposer.core`."
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
;; :harness/context — read-only Layer-2 harness digest (design/harness-coder).
;; What the maintenance assessors observe: the roster report (provenance +
;; grants + escalations), every genome's λ body, the model routing table, the
;; prompt-module inventory, and — THE DEDUP FLOOR (scheduled-maintenance
;; discipline 1) — pending proposals + uncommitted memory candidates, so an
;; unattended agent can honor ¬re-propose(∃pending).
;;
;; ouroboros.agents is resolved LAZILY (λ dep: requiring-resolve) — the loader
;; requires ouroboros.tools for the registry ceiling; a direct require back
;; would cycle.
;; ---------------------------------------------------------------------------

(defn harness-digest
  "Plain-text digest of the Layer-2 harness under `root`."
  [root]
  (let [report  ((requiring-resolve 'ouroboros.agents/report) root)
        roster  ((requiring-resolve 'ouroboros.agents/compile-roster) root)
        pending (proposals/pending root)]
    (str "ROSTER:\n" report
      "\n\nMODELS: " (pr-str models/table)
      "\nPROMPT MODULES: " (pr-str (vec (sort (prompts/module-names))))
      "\n\nPENDING PROPOSALS (" (count pending) ") — do NOT re-propose these:\n"
      (if (seq pending)
        (str/join "\n" (map #(str "  · " (:slug %) " — " (:description %)) pending))
        "  (none)")
      "\n\nUNCOMMITTED MEMORY CANDIDATES:\n"
      (let [um (proposals/untracked-memories root)]
        (if (seq um) (str/join "\n" (map #(str "  · " %) um)) "  (none)"))
      "\n\nGENOME BODIES (the Layer-2 λ programs):\n"
      (str/join "\n\n"
        (for [[id agent] (sort-by key roster)]
          (str "⟨genome " (name id) "⟩\n" (:body agent)))))))

(defrecord HarnessContextTool [root]
  tp/Tool
  (tool-name [_] :harness/context)
  (description [_] "Read the Layer-2 harness: the compiled agent roster (kinds, tags, tool/signal grants, escalations), every genome's λ body, the model routing table, prompt modules, and the PENDING proposals + uncommitted memories you must NOT re-propose. No input. Call this before proposing any harness change.")
  (input-schema [_] [:map {:closed true}])
  (invoke [_ _input]
    {:result (harness-digest root) :is-error false}))

;; ---------------------------------------------------------------------------
;; :ouro/propose-change — the 2×2 roster's recommendation writer
;; (design/harness-coder stage 1; named ouro/ not harness/ because app-coder
;; shares it — proposals are one channel regardless of target). Writes to
;; proposals/ WORKING TREE ONLY; the human gate is the lifecycle.
;; ---------------------------------------------------------------------------

(defrecord ProposeChangeTool [root]
  tp/Tool
  (tool-name [_] :ouro/propose-change)
  (description [_]
    (str "Propose ONE change recommendation as a complete OKF document. Input: "
      "{:slug \"kebab-case-id\" :content \"<OKF doc>\"}. The content MUST be "
      "'---\\ntype: ouroboros/proposal\\ndescription: <one line>\\ntarget: <Layer-2 path>\\n"
      "evidence: [<session-ids or file paths>]\\nseverity: ordinary|algedonic\\n---\\n"
      "<symbol> problem → change-sketch (PROSE, not a diff) → expected-effect → confidence'. "
      "severity algedonic ONLY for identity-threatening findings. Writes ONLY the "
      "working tree — a human explores/refines/approves. On rejection, fix and retry."))
  (input-schema [_] [:map {:closed true} [:slug :string] [:content :string]])
  (invoke [_ {:keys [slug content]}]
    (try
      (let [{:proposal/keys [path]} (proposals/propose! root slug content)]
        {:result   (str "Proposed (NOT committed — awaiting human review): " path)
         :is-error false})
      (catch clojure.lang.ExceptionInfo e
        (let [{:proposal/keys [error existing] :keys [errors]} (ex-data e)]
          (if (= :pending error)
            {:result   (str "Rejected — '" existing "' is already pending review. "
                         "Do not re-propose; pick a DIFFERENT finding or stop.")
             :is-error true}
            {:result   (str "Rejected — fix and retry. Errors: " (pr-str errors))
             :is-error true}))))))

;; ---------------------------------------------------------------------------
;; :signal/emit — the DATA-plane write (design/signals.md). Emits ONE typed
;; durable EDN fact to signals/ (gitignored machine observation — consumers
;; QUERY it; this is not a message and never blocks).
;;
;; SEAM (source-verified, escapement tools/protocol.cljc + llm/openai.clj):
;; tool arguments arrive as JSON with only TOP-LEVEL keys keywordized and NO
;; json-transformer decode before m/validate — so nested keyword-keyed EDN
;; cannot ride the args as a map. :data therefore travels AS AN EDN STRING,
;; which is exactly the empirically-settled emission topology (experiments/
;; edn-signal-emission.edn: the model natively produces EDN text when a
;; FILLED exemplar shows it — the prompt projection provides that exemplar).
;;
;; `allowed-types` is the per-agent grant (genome `signals:` frontmatter);
;; `source` is INFRASTRUCTURE-set at construction (never model-claimed).
;; Default construction is inert-safe: no grants ⇒ every emit is rejected
;; with a corrective result naming the (empty) grant.
;; ---------------------------------------------------------------------------

(defrecord SignalEmitTool [root source allowed-types]
  tp/Tool
  (tool-name [_] :signal/emit)
  (description [_]
    (str "Emit ONE typed signal — a durable EDN fact other agents and the "
      "human query later. Input: {:type \"ns/name\" :data \"<EDN map>\" "
      ":lambda \"<optional λ-notation essence>\"}. :type must be one of YOUR "
      "granted signal types; :data is the :signal/data map for that type AS "
      "AN EDN STRING, matching the type's shape exactly (see the exemplars "
      "in your instructions). Emit facts worth persisting, not conversation. "
      "On rejection, fix the shape and retry."))
  (input-schema [_]
    [:map {:closed true}
     [:type :string]
     [:data :string]
     [:lambda {:optional true} :string]])
  (invoke [_ {:keys [type data lambda]}]
    (let [t       (keyword (str/replace-first type #"^:" ""))
          granted (set allowed-types)]
      (cond
        (not (contains? signals.core/signal-types t))
        {:result   (str "Rejected — unknown signal type " t ". Registry: "
                     (str/join " " (sort signals.core/signal-types)))
         :is-error true}

        (not (contains? granted t))
        {:result   (str "Rejected — type " t " is not in your signal grant "
                     (pr-str (vec (sort granted))) ".")
         :is-error true}

        :else
        (let [parsed (try {:ok (edn/read-string data)}
                          (catch Exception e {:err (ex-message e)}))]
          (if-let [err (:err parsed)]
            {:result   (str "Rejected — :data is not readable EDN: " err
                         ". Send the :signal/data map as an EDN string.")
             :is-error true}
            (try
              (let [{:signal/keys [id path]}
                    (signals/emit! root
                      (cond-> {:signal/type   t
                               :signal/data   (:ok parsed)
                               :signal/source (str (or source "unattributed"))}
                        lambda (assoc :signal/lambda lambda)))]
                {:result   (str "Emitted " id " → " path)
                 :is-error false})
              (catch clojure.lang.ExceptionInfo e
                (let [{:signal/keys [error existing] :keys [errors]} (ex-data e)]
                  (if (= :duplicate error)
                    {:result   (str "Rejected — duplicate of " existing
                                 " (same fact already emitted). Do not re-emit.")
                     :is-error true}
                    {:result   (str "Rejected — fix and retry. Signal errors ("
                                 error "): " (pr-str errors))
                     :is-error true}))))))))))

;; ---------------------------------------------------------------------------
;; :dev/run-tests — the builder's VERIFICATION gate. Runs EXACTLY the
;; deterministic suite (`bb test`): no arguments reach the shell, so the grant
;; carries NO general execution capability — :shell/run stays a separate,
;; deliberate escalation and git stays unreachable by absence (agent-model
;; invariant). Failing tests are DOMAIN feedback the agent must react to, so
;; they ride a corrective {:is-error true} result (the repair nudge), same as
;; OKF rejections.
;;
;; `cmd` is injectable at construction (tests substitute a stub command —
;; running bb test inside bb test would recurse); the registry default is the
;; real suite.
;; ---------------------------------------------------------------------------

(def ^:private run-tests-timeout-ms 300000)

(def ^:private run-tests-tail-chars
  "Suite output can be long; the model needs the TAIL (counts + failures)."
  4000)

(defn- tail-str [s n]
  (let [s (str s)]
    (if (> (count s) n) (subs s (- (count s) n)) s)))

(defrecord RunTestsTool [root cmd]
  tp/Tool
  (tool-name [_] :dev/run-tests)
  (description [_]
    (str "Run the project's deterministic test suite (bb test). No input. "
      "Returns the suite tail (test/assertion counts + any failures). Run it "
      "after EVERY batch of edits; a change is not done until the suite is "
      "GREEN. On failure, read the output, fix, and re-run."))
  (input-schema [_] [:map {:closed true}])
  (invoke [_ _input]
    (try
      (let [ps  (apply proc/process {:dir (str root) :out :string :err :string} cmd)
            res (deref ps run-tests-timeout-ms ::timeout)]
        (if (= ::timeout res)
          (do (proc/destroy-tree ps)
              {:result   (str "Test run TIMED OUT after " (/ run-tests-timeout-ms 1000)
                           "s — likely a hang. Do not retry blindly; report this.")
               :is-error true})
          (let [{:keys [exit out err]} res
                text (str/trim (str out (when (seq (str err)) (str "\n" err))))]
            {:result   (str "exit " exit "\n" (tail-str text run-tests-tail-chars))
             :is-error (not (zero? exit))})))
      (catch Exception e
        {:result (str "Test runner failed to launch: " (ex-message e)) :is-error true}))))

;; ---------------------------------------------------------------------------
;; Registry assembly
;; ---------------------------------------------------------------------------

(defn all-tools
  "Every tool Ouroboros registers, rooted at `root`. This list IS the registry
  CEILING (agent-model spec): a genome SELECTS from it, it cannot INVENT.

  mementum tools + escapement's built-ins (fs read/write/edit/multi-edit/
  glob/grep, shell/run, web/fetch). :web/search is excluded explicitly
  (deterministic — builtin-tools also env-gates it on GEMINI_API_KEY, but the
  ceiling should not depend on the environment).

  ⚠ :shell/run ⊃ git: with it in the ceiling, 'commit unreachable by absence'
  no longer holds for shell-granted agents — for THOSE the human-gate is
  POLICY (prompt + review), not capability (🎯 human decision, chat-testing
  phase). Grants stay explicit + visible in the roster report.

  :signal/emit here is the INERT default construction (no source, no type
  grants — every emit rejected corrective). Charts wiring an EMITTING agent
  build their registry via `new-registry` with {:source :signal-types} from
  the genome."
  ([root] (all-tools root {}))
  ([root {:keys [source signal-types]}]
   (into [(->ContextTool root) (->SessionsTool root) (->ProposeMemoryTool root)
          (->HarnessContextTool root) (->ProposeChangeTool root)
          (->RunTestsTool root ["bb" "test"])
          (->SignalEmitTool root source (set signal-types))]
     (remove #(= :web/search (tp/tool-name %)) (builtin/builtin-tools)))))

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
  mementum tools, rooted at `root` (default \".\"): context + sessions (read),
  propose-memory (gated write), signal/emit (data plane — pass `opts`
  {:source <agent/session id> :signal-types <the genome's signals grant>} to
  arm it; without opts it is inert-safe)."
  ([] (new-registry "."))
  ([root] (new-registry root {}))
  ([root opts]
   (tp/new-registry (all-tools root opts))))
