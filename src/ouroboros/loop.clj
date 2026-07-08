(ns ouroboros.loop
  "Loop B — the closed self-improvement loop (the improver).

  An escapement chart observes Ouroboros's own state on TWO axes:
    · `:mementum/context`  — the knowledge index, memory index, recent commits.
    · `:mementum/sessions` — prior conversations as λ-compacted message arrays
                             (the cross-session memory; see ouroboros.compact).
  It METABOLIZES what it reads (λ metabolize: recurring topic/decision/pattern;
  ≥3 on one topic ⇒ a knowledge-page candidate) and PROPOSES exactly one memory
  candidate (via `:mementum/propose-memory`), grounded in what it actually saw.

  This is PROPOSAL ONLY: the candidate lands in `mementum/memories/`,
  UNCOMMITTED. Commit is human-gated (AGENTS.md invariant — synthesis is AI,
  approval is human) — this namespace never touches git. Knowledge-page
  synthesis (the ≥3→page WRITE path) is a later increment; for now a ≥3 cluster
  is NOTED in the reflection, and the concrete gated artifact is one memory.

  Pure metabolize rendering lives in `ouroboros.loop.core`; the session readers
  in `ouroboros.session`.

  Run: bb loop"
  (:require
    [babashka.process :as proc]
    [clojure.string :as str]
    [com.fulcrologic.statecharts.chart :as chart]
    [com.fulcrologic.statecharts.elements :refer [state transition final]]
    [escapement.chart.helpers :as h]
    [escapement.lib :as lib]
    [escapement.lib.event-sink :as sink]
    [ouroboros.session :as session]
    [ouroboros.tools :as tools]))

;; Reuse the proven local llama.cpp wiring from ouroboros.smoke.
(def ^:private local-base-url "http://localhost:5100/v1")
(def ^:private local-model    "qwen35-35b-a3b")

(def system-prompt
  "λ engage(nucleus).
[phi fractal euler tao pi mu ∃ ∀] | [Δ λ Ω ∞/0 | ε/φ Σ/μ c/h signal/noise order/entropy truth/provability self/other] | OODA
Human ⊗ AI ⊗ REPL

λ identity(self). Ouroboros | observe(own_state ∧ prior_sessions) → metabolize → propose(ONE memory) | steps IN ORDER

λ observe.  call(mementum_context) ∧ call(mementum_sessions) | ⊘input
  → context  : knowledge_index ∧ memory_index ∧ recent_commits
  → sessions : prior λ-compacted conversations (assistant λ ≡ the essence, cross-session memory)

λ metabolize.  scan(sessions ∧ memories) → recurring(topic ∨ decision ∨ pattern)
  | ≥3(same_topic) → knowledge-page CANDIDATE — NAME it in your final reply (do NOT write it yet)
  | novel(insight) ∈ observed → memory CANDIDATE

λ select.  pick(ONE) : insight ∨ decision ∨ pattern | grounded(∃you_saw ∈ sessions ∨ context) | SPECIFIC
  | ¬fabricate ∧ ¬generic(software_advice) | ∃source ∈ observed

λ propose.  call(mementum_propose_memory {slug content})
  slug    : short kebab-case | ⊘\".md\" | ⊘path
  content : COMPLETE OKF document, EXACTLY this shape —
    ---
    type: mementum/memory
    description: <one crisp line>
    ---
    <symbol> <body | <200 words | ONE insight>
  symbol ∈ {💡 insight | 🔄 shift | 🎯 decision | 🌀 meta | ❌ mistake | ✅ win | 🔁 pattern}
    | match(what_happened)

λ repair.  error(tool) → fix(content) per_feedback → retry(mementum_propose_memory)

λ terminate.  success → reply(ONE sentence : what_you_proposed [+ note any ≥3 knowledge-page candidate]) → stop")

(def propose-chart
  (chart/statechart
    {:initial :observe}
    (state {:id :observe}
      (h/llm-conversation
        {:id         "propose"
         :system     system-prompt
         :model      :local
         :stream?    true
         :budget-ms  240000
         :real-tools [:mementum/context :mementum/sessions :mementum/propose-memory]
         :message    "Begin: call mementum_context AND mementum_sessions, metabolize what you read, then propose exactly one memory."})
      (transition {:event :llm.idle :target :done}
        (h/capture-llm-output {:as "reflection.md"})))
    (final {:id :done})))

(defn untracked-memories
  "Repo-relative paths under mementum/memories/ that git sees as untracked or
  modified — i.e. freshly proposed candidates awaiting human approval.
  `--untracked-files=all` forces PER-FILE listing — otherwise git collapses a
  wholly-new (never-before-tracked) directory to a single `?? dir/` line."
  [root]
  (let [{:keys [exit out]} (apply proc/shell
                             {:dir (str root) :out :string :err :string :continue true}
                             ["git" "status" "--porcelain" "--untracked-files=all"
                              "--" "mementum/memories/"])]
    (if (zero? exit)
      (->> (str/split-lines out)
        (remove str/blank?)
        (map #(str/trim (subs % 3)))
        (remove #(str/ends-with? % "/"))
        vec)
      [])))

(defn run!
  "Run the loop against `root` (default \".\"). Streams the assistant's tokens
  live to stdout. Returns `{:result <lib/run summary> :proposed <paths>}` — the
  proposed memory(ies), if any, are UNCOMMITTED working-tree files."
  ([] (run! "."))
  ([root]
   (let [adapter     (sink/make-adapter)
         session-id  (str "loop-" (System/currentTimeMillis))
         session-dir (session/session-dir root session-id)
         result  (lib/run
                   {:chart          propose-chart
                    :session-id     session-id
                    :session-dir    session-dir
                    :transcript-path (str session-dir "/transcript.jsonl")
                    :checkpoint-dir  (str session-dir "/checkpoints")
                    :tool-registry  (tools/new-registry root)
                    :credentials    [{:provider :openai :api-key "sk-local" :base-url local-base-url}]
                    :config         {:llm/aliases             {:local [{:provider :openai :model local-model}]}
                                     :llm/preferences         [:local]
                                     :llm/eligibility-strict? false}
                    :transcript-tap (fn [row]
                                      (doseq [e (sink/feed! adapter row)]
                                        (when (= :text-delta (:type e))
                                          (print (get-in e [:delta :text]))
                                          (flush))))})]
     {:result result :proposed (untracked-memories root)})))

(defn -main [& _]
  (let [{:keys [result proposed]} (run!)]
    (println)
    (println "status      :" (:status result))
    (println "proposed    :" (if (seq proposed) proposed "(none — the model proposed nothing)"))
    (when (seq proposed)
      (println "\n--- review before approving/committing ---")
      (doseq [p proposed] (println "\n#" p "\n" (slurp p))))
    (shutdown-agents)
    (System/exit (if (= :done (:status result)) 0 1))))
