(ns ouroboros.curator
  "The CURATOR — Ouroboros's memory/knowledge curation agent.

  Ouroboros is ONE system of MANY self-improving agents (curator built; a
  harness-improver, an app-improver, verifier(s), and a documenter are planned).
  The curator's job is CURATION of the mementum store: notice what is worth
  keeping and propose it — memory now, knowledge (the ≥3→page synthesize! path)
  next.

  It observes Ouroboros on TWO axes:
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

  Pure metabolize rendering lives in `ouroboros.curator.core`; the session
  readers in `ouroboros.session`.

  Run: bb curate"
  (:require
    [babashka.process :as proc]
    [clojure.string :as str]
    [com.fulcrologic.statecharts.chart :as chart]
    [com.fulcrologic.statecharts.elements :refer [state transition final]]
    [escapement.chart.helpers :as h]
    [escapement.lib :as lib]
    [escapement.lib.event-sink :as sink]
    [ouroboros.agents :as agents]
    [ouroboros.session :as session]
    [ouroboros.tools :as tools]))

;; Reuse the proven local llama.cpp wiring from ouroboros.smoke.
(def ^:private local-base-url "http://localhost:5100/v1")
(def ^:private local-model    "qwen36-35b-a3b")

(def genome
  "The curator's compiled genome — src/ouroboros/agents/curator.md via the
  ouroboros.agents loader (agent-model BUILD STEP 1). :prompt = the λ system
  prompt body (byte-identical extraction of the former inline def); :tools =
  the grant (context + sessions read, propose-memory ESCALATION); :model =
  the routing alias. Frontmatter is loader-only wiring — the LLM never sees it."
  (agents/genome :curator))

(def propose-chart
  (chart/statechart
    {:initial :observe}
    (state {:id :observe}
      (h/llm-conversation
        {:id         "propose"
         :system     (:prompt genome)
         :model      (:model genome)
         :stream?    true
         :budget-ms  240000
         :real-tools (:tools genome)
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
  "Run the curator against `root` (default \".\"). Streams the assistant's tokens
  live to stdout. Returns `{:result <lib/run summary> :proposed <paths>}` — the
  proposed memory(ies), if any, are UNCOMMITTED working-tree files."
  ([] (run! "."))
  ([root]
   (let [adapter     (sink/make-adapter)
         session-id  (str "curator-" (System/currentTimeMillis))
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
