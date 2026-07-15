(ns ouroboros.workflow
  "The CODING-PIPELINE home (agent-model spec §topologies: `workflow` COMPOSES
  shot/verdict agents with loops + human gates — build-order step 5).

  TODAY the two shot kinds run STANDALONE, each a hermetic proposer/run!
  (λ converge: proposer.clj IS the one shot-topology runner; author and
  builder ride it unchanged — only the genome differs):

    author  (plan stage)  bb author  \"<task>\"          → plan document
            gate ≡ next-stage: the reply lands as the session artifact
            (artifacts/reflection.md); feed its PATH to the builder.
    builder (build stage) bb builder \"<task or plan>\"  → working-tree DIFF
            gate ≡ next-stage: the diff stays UNCOMMITTED — the human reviews
            (the report below prints status + diffstat) and commits.

  The composition (author→builder→judge with a bounded revise loop, k-capped)
  lands HERE once the standalone kinds are proven live.

  PROPOSAL ONLY: this namespace never WRITES git — it reads status/diffstat to
  REPORT what the builder changed (the human gate's audit surface)."
  (:require
    [babashka.process :as proc]
    [clojure.string :as str]
    [ouroboros.proposer :as proposer]))

(def author-budget-ms  300000)
(def builder-budget-ms 600000)

(defn- git-out
  "Read-only git query under `root` (never a write)."
  [root & args]
  (-> (apply proc/shell {:dir (str root) :out :string :err :string :continue true}
        "git" args)
      :out
      str/trimr))

(defn diff-report
  "The human gate's audit surface: what the builder left in the working tree.
  `git status --short` (includes untracked new files) + `git diff --stat`
  (magnitude per file). Read-only."
  [root]
  (let [status (git-out root "status" "--short")
        stat   (git-out root "diff" "--stat")]
    (str "WORKING TREE (uncommitted — review, then commit or revert):\n"
      (if (str/blank? status) "  (clean — the builder changed nothing)" status)
      (when-not (str/blank? stat) (str "\n\nDIFFSTAT:\n" stat)))))

(defn run-author!
  "Run the author genome on `task`. Returns proposer/run!'s map; the plan
  document is the session artifact (artifacts/reflection.md)."
  ([task] (run-author! task {}))
  ([task opts]
   (proposer/run! :author (merge {:subject task :budget-ms author-budget-ms} opts))))

(defn run-builder!
  "Run the builder genome on `task` (include a plan file path when one
  exists — the genome reads it first). Returns proposer/run!'s map."
  ([task] (run-builder! task {}))
  ([task opts]
   (proposer/run! :builder (merge {:subject task :budget-ms builder-budget-ms} opts))))

(defn -main
  "bb entry: (bb author|builder \"<task>\") → run the shot kind, print the
  next-stage handoff (author: the plan artifact path · builder: the diff
  report)."
  [kind & task-args]
  (let [task (str/join " " task-args)]
    (when (str/blank? task)
      (println "usage: bb author \"<task>\"  |  bb builder \"<task, incl. plan path if any>\"")
      (System/exit 2))
    (let [{:keys [status session-dir]}
          (case kind
            "author"  (run-author! task)
            "builder" (run-builder! task))]
      (println)
      (println "status  :" status)
      (case kind
        "author"  (println "plan    :" (str session-dir "/artifacts/reflection.md"))
        "builder" (println (diff-report ".")))
      (shutdown-agents)
      (System/exit (if (= :done status) 0 1)))))
