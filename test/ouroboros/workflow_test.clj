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

;; ---------------------------------------------------------------------------
;; converge! — champion/challenger + patience (the SECOND composition).
;; All stubbed: no LLM runs, real temp git repo (the dirty-guard needs git).
;; ---------------------------------------------------------------------------

(def ^:private champ-slug "stub-genome")
(def ^:private champ-text "---\nkind: proposer\n---\nλ champion. incumbent\n")
(def ^:private chall-text "---\nkind: proposer\n---\nλ challenger. novel\n")

(defn- repo-with-genome []
  (let [root (temp-git-repo)
        rel  (workflow/genome-path champ-slug)]
    (fs/create-dirs (fs/parent (fs/path root rel)))
    (spit (str (fs/path root rel)) champ-text)
    (proc/shell {:dir root :out :string :err :string} "git" "add" ".")
    (proc/shell {:dir root :out :string :err :string} "git" "commit" "-q" "-m" "genome")
    root))

(defn- throwing-fn [tag]
  (fn [& _] (throw (ex-info (str tag " must not be called") {}))))

(deftest converge-refuses-a-dirty-champion
  ;; The editor-incident law again: the promotion spit's absent companion is
  ;; the BASELINE — uncommitted work on the champion path must never ride.
  (let [root (repo-with-genome)]
    (spit (str (fs/path root (workflow/genome-path champ-slug))) "uncommitted tweak\n")
    (is (= {:outcome :dirty-champion :rounds 0 :history []}
           (workflow/converge! champ-slug "any use"
             {:root root
              :challenger-fn (throwing-fn "challenger")
              :duel-fn       (throwing-fn "duel")}))
      "guard fires before any LLM run")))

(deftest converge-fails-loud-on-a-missing-genome
  (is (thrown? clojure.lang.ExceptionInfo
        (workflow/converge! "no-such-genome" "use"
          {:root (temp-git-repo)}))))

(deftest converge-plateaus-when-challengers-keep-losing
  (let [root (repo-with-genome)
        champion-sweep [{:pair [:champion :challenger] :winner :a}
                        {:pair [:challenger :champion] :winner :b}]
        {:keys [outcome rounds history]}
        (workflow/converge! champ-slug "use"
          {:root root :patience 2 :max-rounds 5
           :challenger-fn (fn [_ _ _ _] chall-text)
           :duel-fn       (fn [_ _ _ _] champion-sweep)})]
    (is (= :plateau outcome) "K consecutive losses ⇒ stop, no write")
    (is (= 2 rounds) "patience bounds the loop below max-rounds")
    (is (= [:champion :champion] (mapv :winner history)))
    (is (= champ-text (slurp (str (fs/path root (workflow/genome-path champ-slug)))))
      "incumbent text untouched")
    (is (str/includes? (workflow/diff-report root) "changed nothing"))))

(deftest converge-promotes-a-winning-challenger-as-uncommitted-diff
  (let [root (repo-with-genome)
        challenger-sweep [{:pair [:champion :challenger] :winner :b}
                          {:pair [:challenger :champion] :winner :a}]
        champion-sweep   [{:pair [:champion :challenger] :winner :a}
                          {:pair [:challenger :champion] :winner :b}]
        duels (atom [challenger-sweep champion-sweep champion-sweep])
        {:keys [outcome rounds history]}
        (workflow/converge! champ-slug "use"
          {:root root :patience 2 :max-rounds 5
           :challenger-fn (fn [_ _ _ round] (str chall-text "round: " round "\n"))
           :duel-fn       (fn [& _] (let [[d & more] @duels] (reset! duels more) d))})]
    (is (= :promoted outcome))
    (is (= 3 rounds) "win resets the streak; 2 losses after it ⇒ plateau reached")
    (is (= [:challenger :champion :champion] (mapv :winner history)))
    (is (= (str chall-text "round: 1\n")
           (slurp (str (fs/path root (workflow/genome-path champ-slug)))))
      "round-1 winner survives the later losing rounds")
    (testing "promotion ≡ UNCOMMITTED worktree diff — the human gate"
      (is (str/includes? (workflow/diff-report root)
            (str "M " (workflow/genome-path champ-slug)))))))

(deftest converge-counts-a-dropped-challenger-as-a-loss
  ;; regression guard: a candidate that failed the compiler gate never duels —
  ;; nil ⇒ streak++, and the duel-fn must not run.
  (let [root (repo-with-genome)
        {:keys [outcome rounds history]}
        (workflow/converge! champ-slug "use"
          {:root root :patience 2 :max-rounds 5
           :challenger-fn (fn [& _] nil)
           :duel-fn       (throwing-fn "duel")})]
    (is (= :plateau outcome))
    (is (= 2 rounds))
    (is (= [:challenger-dropped :challenger-dropped] (mapv :note history)))))

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
