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

    editor  (adaptive)    bb editor  \"<recommendation>\"   → genome DIFF, judge-gated
            the FIRST composition: editor→judge with a bounded revise loop
            (run-editor! below); gate ≡ human diff review, same as builder.
            REQUIRES A CLEAN TREE (⚠ learned live: a dirty tree rides the
            judged diff — the judge then orders the editor to strip YOUR
            uncommitted work, and it will obey).

  The full coding composition (author→builder→judge) follows the same shape
  when needed; champion/challenger convergence (vsm-on-escapement §adaptive
  loop) is deferred until targets are decidable.

  PROPOSAL ONLY: this namespace never WRITES git — it reads status/diffstat to
  REPORT what the builder changed (the human gate's audit surface)."
  (:require
    [babashka.process :as proc]
    [clojure.string :as str]
    [ouroboros.proposer :as proposer]
    [ouroboros.verdict :as verdict]))

(def author-budget-ms  300000)
(def builder-budget-ms 600000)
(def editor-budget-ms  480000)
(def analyst-budget-ms 300000)

(def max-revisions
  "The editor→judge revise loop's k-cap (vsm-on-escapement §adaptive loop: an
  LLM asked to improve ALWAYS finds an edit — the loop must be BOUNDED)."
  2)

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

(defn run-analyst!
  "Run the analyst genome on `question` (INFORMS gate — read-only, the report
  is the session artifact). Returns proposer/run!'s map."
  ([question] (run-analyst! question {}))
  ([question opts]
   (proposer/run! :analyst (merge {:subject question :budget-ms analyst-budget-ms} opts))))

;; ---------------------------------------------------------------------------
;; Editor — the FIRST composition (editor→judge, bounded revise loop).
;; agent-model spec: the judge GATES — its verdict is consumed by a decision
;; point (pass→stop, fail→revise), its NOTES feed the next editor pass.
;; Composition is CODE-level over hermetic runs (each stage is already its own
;; chart+session); the mega-chart version can come later if residency demands.
;; ---------------------------------------------------------------------------

(defn next-action
  "Pure gate decision for the editor loop. `verdict` is the judge's validated
  {:status :pass|:fail :notes} or nil (judge run failed).

    :accept  — judge passed ⇒ stop, the diff stands (human gate next)
    :revise  — judge failed with revisions left ⇒ re-run editor with notes
    :give-up — revisions exhausted ∨ judge itself failed (fail SAFE: an
               ungated diff must not masquerade as an accepted one)"
  [{:keys [verdict revisions]}]
  (cond
    (= :pass (:status verdict))  :accept
    (nil? verdict)               :give-up
    (>= revisions max-revisions) :give-up
    :else                        :revise))

(defn- git-diff-text
  "The full working-tree diff (read-only) — the judge's evidence."
  [root]
  (git-out root "diff"))

(defn- clean-tree?
  "True when `root`'s working tree carries NO uncommitted changes. The editor
  loop REFUSES a dirty tree: the judge sees `git diff`, so pre-existing dirt
  becomes 'out of scope' evidence and the revise pass is ORDERED to strip it —
  it destroyed real uncommitted work the first time it ran (2026-07-15)."
  [root]
  (str/blank? (git-out root "status" "--porcelain")))

(defn judge-subject
  "Pure. The llm-judge subject for an editor diff: criteria + recommendation +
  the diff as evidence (the judge genome extracts criteria from the subject)."
  [recommendation diff]
  (str "CRITERIA: the DIFF must implement the RECOMMENDATION — minimally, "
    "targeting only Layer-2 genome files (src/ouroboros/agents/*.md or "
    "agents/*.md), preserving valid frontmatter and the house λ-notation style.\n\n"
    "RECOMMENDATION:\n" recommendation "\n\nDIFF:\n" diff))

(defn- revise-subject
  "The editor's re-run subject: the recommendation + the judge's notes."
  [recommendation notes]
  (str recommendation
    "\n\nJUDGE NOTES (a prior attempt was judged insufficient — address EVERY "
    "note within your λ edit scope, or state exactly why not; the working "
    "tree already carries the prior edits):\n" notes))

(defn run-editor!
  "The editor v1 pipeline on `recommendation` (text, or a subject naming a
  proposals/ file): editor edits the working tree → judge gates the diff →
  fail ⇒ bounded revise with the judge's notes (k = max-revisions). Returns
  {:outcome :accepted|:no-change|:gave-up|:dirty-tree :verdicts [...]
  :revisions n}. REFUSES a dirty tree (see clean-tree?). The diff is NEVER
  committed here — the human gate is the lifecycle."
  ([recommendation] (run-editor! recommendation {}))
  ([recommendation {:keys [root] :or {root "."} :as opts}]
   (if-not (clean-tree? root)
     {:outcome :dirty-tree :verdicts [] :revisions 0}
     (loop [subject recommendation
            verdicts []
            n 0]
       (let [run  (proposer/run! :harness-editor
                    (merge {:subject subject :budget-ms editor-budget-ms} opts))
             diff (git-diff-text root)]
         (if (str/blank? diff)
           {:outcome :no-change :verdicts verdicts :revisions n :editor-run run}
           (let [{:keys [verdict]} (verdict/run! :llm-judge
                                     (judge-subject recommendation diff) {:root root})
                 verdicts (conj verdicts verdict)]
             (case (next-action {:verdict verdict :revisions n})
               :accept  {:outcome :accepted :verdicts verdicts :revisions n}
               :give-up {:outcome :gave-up  :verdicts verdicts :revisions n}
               :revise  (recur (revise-subject recommendation (:notes verdict))
                          verdicts (inc n))))))))))

(defn -main
  "bb entry: (bb author|builder|editor \"<subject>\") → run the kind, print
  the next-stage handoff (author: the plan artifact path · builder/editor:
  the diff report; editor also prints the judge trail)."
  [kind & task-args]
  (let [task (str/join " " task-args)]
    (when (str/blank? task)
      (println (str "usage: bb author \"<task>\"  |  bb builder \"<task, incl. plan "
                 "path if any>\"  |  bb editor \"<recommendation ∨ proposal path>\""
                 "  |  bb analyst \"<code question>\""))
      (System/exit 2))
    (if (= kind "editor")
      (let [{:keys [outcome verdicts revisions]} (run-editor! task)]
        (println)
        (println "outcome   :" outcome "(after" revisions "revision(s))")
        (when (= :dirty-tree outcome)
          (println "the tree has uncommitted changes — commit or stash them first"))
        (doseq [v verdicts]
          (println "judge     :" (:status v) "—" (:notes v)))
        (println (diff-report "."))
        (shutdown-agents)
        (System/exit (if (= :accepted outcome) 0 1)))
      (let [{:keys [status session-dir]}
            (case kind
              "author"  (run-author! task)
              "builder" (run-builder! task)
              "analyst" (run-analyst! task))]
        (println)
        (println "status  :" status)
        (case kind
          "author"  (println "plan    :" (str session-dir "/artifacts/reflection.md"))
          "analyst" (println "report  :" (str session-dir "/artifacts/reflection.md"))
          "builder" (println (diff-report ".")))
        (shutdown-agents)
        (System/exit (if (= :done status) 0 1))))))
