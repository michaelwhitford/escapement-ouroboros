(ns ouroboros.workflow-test
  "The coding-pipeline home: diff-report (the human gate's audit surface) over
  a real temp git repo. Read-only git — the runner itself is live-only."
  (:require
    [babashka.fs :as fs]
    [babashka.process :as proc]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [ouroboros.workflow :as workflow]))

(defn- temp-git-repo []
  (let [root (str (fs/create-temp-dir {:prefix "ouro-workflow"}))]
    (doseq [args [["init" "-q"]
                  ["config" "user.email" "test@test"]
                  ["config" "user.name" "test"]]]
      (apply proc/shell {:dir root :out :string :err :string} "git" args))
    (spit (str (fs/path root "committed.txt")) "original\n")
    (proc/shell {:dir root :out :string :err :string} "git" "add" ".")
    (proc/shell {:dir root :out :string :err :string} "git" "commit" "-q" "-m" "seed")
    root))

(deftest diff-report-clean-tree
  (let [report (workflow/diff-report (temp-git-repo))]
    (is (str/includes? report "uncommitted") "frames the human gate")
    (is (str/includes? report "changed nothing"))))

;; ---------------------------------------------------------------------------
;; next-action — the editor loop's pure gate decision (vsm §adaptive loop)
;; ---------------------------------------------------------------------------

(deftest next-action-gates-the-editor-loop
  (testing "judge pass ⇒ accept, at any revision count"
    (is (= :accept (workflow/next-action {:verdict {:status :pass :notes "ok"} :revisions 0})))
    (is (= :accept (workflow/next-action {:verdict {:status :pass :notes "ok"} :revisions 2}))))
  (testing "judge fail with revisions left ⇒ revise"
    (is (= :revise (workflow/next-action {:verdict {:status :fail :notes "x"} :revisions 0})))
    (is (= :revise (workflow/next-action {:verdict {:status :fail :notes "x"} :revisions 1}))))
  (testing "revisions exhausted ⇒ give up (bounded — an LLM always finds an edit)"
    (is (= :give-up (workflow/next-action {:verdict {:status :fail :notes "x"} :revisions 2}))))
  (testing "judge itself failed (nil verdict) ⇒ give up — fail SAFE, never masquerade as accepted"
    (is (= :give-up (workflow/next-action {:verdict nil :revisions 0})))))

(deftest judge-subject-carries-criteria-recommendation-and-diff
  (let [s (workflow/judge-subject "tighten λ paths" "--- a/x\n+++ b/x")]
    (is (str/starts-with? s "CRITERIA:"))
    (is (str/includes? s "RECOMMENDATION:\ntighten λ paths"))
    (is (str/includes? s "DIFF:\n--- a/x"))))

(deftest run-editor-refuses-a-dirty-tree
  ;; THE incident regression (2026-07-15): launched over a dirty tree, the
  ;; judged diff carried pre-existing uncommitted work → the judge ordered it
  ;; stripped → the revise pass DESTROYED it. The guard fires before any LLM
  ;; run — this test is deterministic.
  (let [root (temp-git-repo)]
    (spit (str (fs/path root "committed.txt")) "uncommitted dirt\n")
    (is (= {:outcome :dirty-tree :verdicts [] :revisions 0}
           (workflow/run-editor! "any recommendation" {:root root})))))

(deftest diff-report-surfaces-edits-and-new-files
  (let [root (temp-git-repo)]
    (spit (str (fs/path root "committed.txt")) "modified\n")
    (spit (str (fs/path root "new-file.txt")) "brand new\n")
    (let [report (workflow/diff-report root)]
      (testing "tracked edit appears in status + diffstat"
        (is (str/includes? report "M committed.txt"))
        (is (str/includes? report "DIFFSTAT")))
      (testing "untracked NEW file appears (status --short covers what diff misses)"
        (is (str/includes? report "?? new-file.txt"))))))
