(ns ouroboros.schedule-test
  "The maintenance schedule: selection (tag set-valued, slug fail-loud),
  sweep planning (dedupe, disabled skipped), lock lifecycle, and a full
  sweep! against a STUBBED runner (no LLM)."
  (:require
    [babashka.fs :as fs]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [ouroboros.agents :as agents]
    [ouroboros.schedule :as schedule]
    [ouroboros.signals :as signals]))

(def roster
  {:harness-knowledge {:id :harness-knowledge :tags [:curator]}
   :app-knowledge     {:id :app-knowledge :tags [:curator]}
   :harness-coder     {:id :harness-coder :tags [:assessor]}
   :app-coder         {:id :app-coder :tags [:assessor]}
   :chat              {:id :chat :tags []}})

(deftest select-by-tag-is-set-valued
  (is (= [:app-knowledge :harness-knowledge]
         (schedule/select-slugs roster {:tag :curator}))
    "every carrier of the tag, sorted — new genomes join automatically")
  (is (= [] (schedule/select-slugs roster {:tag :nobody-has-this}))))

(deftest select-by-slug-fails-loud-when-absent
  (is (= [:chat] (schedule/select-slugs roster {:slug "chat"})))
  (is (thrown? clojure.lang.ExceptionInfo
        (schedule/select-slugs roster {:slug "ghost"}))))

(deftest sweep-plan-dedupes-and-skips-disabled
  (let [entries [{:id :a :select {:tag :curator} :subject "s1" :enabled true}
                 {:id :b :select {:slug "app-knowledge"} :subject "s2" :enabled true}
                 {:id :c :select {:tag :assessor} :subject "s3" :enabled false}]
        plan    (schedule/sweep-plan roster entries)]
    (is (= [:app-knowledge :harness-knowledge] (mapv :agent plan))
      "duplicate selection runs ONCE (first entry wins); disabled entry skipped")
    (is (= "s1" (:subject (first plan))) "first entry's subject wins")))

(deftest live-roster-covers-the-2x2
  (let [plan (schedule/sweep-plan (agents/compile-roster ".") schedule/table)]
    (is (= [:app-knowledge :harness-knowledge :app-coder :harness-coder]
           (mapv :agent plan))
      "the real table × real roster sweeps exactly the four matrix cells")))

(deftest lock-lifecycle
  (testing "pure state decisions"
    (is (= :free  (schedule/lock-state nil 1000)))
    (is (= :held  (schedule/lock-state 1000 2000)))
    (is (= :stale (schedule/lock-state 1000 (+ 1000 schedule/stale-lock-ms 1)))))
  (let [root (str (fs/create-temp-dir {:prefix "ouro-sched-"}))]
    (try
      (schedule/acquire-lock! root 5000)
      (is (thrown? clojure.lang.ExceptionInfo (schedule/acquire-lock! root 6000))
        "overlap refused")
      (schedule/acquire-lock! root (+ 5000 schedule/stale-lock-ms 1))
      (schedule/release-lock! root)
      (schedule/acquire-lock! root 7000)
      (schedule/release-lock! root)
      (finally (fs/delete-tree root)))))

(deftest sweep-with-stub-runner
  (let [root (str (fs/create-temp-dir {:prefix "ouro-sweep-"}))]
    (try
      ;; minimal custom roster so compile-roster works against the temp root
      (fs/create-dirs (fs/path root "agents"))
      (let [ran     (atom [])
            runner  (fn [slug {:keys [subject]}]
                      (swap! ran conj [slug subject])
                      (if (= :app-coder slug) (throw (ex-info "boom" {})) {:status :done}))
            entries [{:id :t :select {:tag :assessor} :subject "go" :enabled true}]
            outs    (schedule/sweep! root {:runner runner :entries entries})]
        (is (= [[:app-coder "go"] [:harness-coder "go"]] @ran)
          "sequential, table-ordered, subject passed")
        (is (= [:fail :done] (mapv :status outs))
          "a throwing run becomes :fail and the sweep CONTINUES")
        (testing "the roster emits signals — one :s1/report per run"
          (let [sigs (signals/by-type root :s1/report)]
            (is (= 2 (count sigs)))
            (is (every? #(= "bb-maintain" (:signal/source %)) sigs))
            (is (= #{:ok :fail} (set (map #(get-in % [:signal/data :outcome]) sigs))))))
        (testing "lock released after sweep"
          (is (not (fs/exists? (fs/path root schedule/lock-file))))))
      (finally (fs/delete-tree root)))))

(deftest summary-line-format
  (is (= "maintain app-coder → done (1200ms) · +1 proposal(s), +0 memory(ies)"
         (schedule/summary-line {:agent :app-coder :status :done :wall-ms 1200
                                 :new-proposals 1 :new-memories 0})))
  (is (= "maintain chat → fail (5ms)"
         (schedule/summary-line {:agent :chat :status nil :wall-ms 5}))
    "nil status renders fail; zero artifacts renders bare"))
