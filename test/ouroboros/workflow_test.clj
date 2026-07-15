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
