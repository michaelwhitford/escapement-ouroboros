(ns ouroboros.screen-test
  "The verifier in the loop: content-hashed idempotent screening, failed-run
  retry, stale-verdict detection, inbox annotation, and sweep composition —
  all against a STUBBED verdict runner (no LLM). Temp roots are git-inited:
  untracked-memory listing is a real `git status` walk."
  (:require
    [babashka.fs :as fs]
    [babashka.process :as proc]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [ouroboros.proposals :as proposals]
    [ouroboros.schedule :as schedule]
    [ouroboros.screen :as screen]))

(defn- with-root [f]
  (let [root (str (fs/create-temp-dir {:prefix "ouro-screen-"}))]
    (try
      (proc/shell {:dir root :out :string :err :string} "git init -q")
      (f root)
      (finally (fs/delete-tree root)))))

(defn- proposal-doc []
  (str "---\n"
       "type: ouroboros/proposal\n"
       "description: tighten the chat genome tool clause\n"
       "target: src/ouroboros/agents/chat.md\n"
       "evidence: [compact-111]\n"
       "severity: ordinary\n"
       "---\n"
       "🔁 problem → change-sketch → expected-effect\n"))

(defn- memory-doc []
  (str "---\n"
       "type: mementum/memory\n"
       "description: a candidate insight\n"
       "---\n"
       "💡 the registry ceiling excludes :web/search\n"))

(defn- seed! [root]
  (proposals/propose! root "chat-tools" (proposal-doc))
  (fs/create-dirs (fs/path root "mementum/memories"))
  (spit (str (fs/path root "mementum/memories/candidate.md")) (memory-doc)))

(defn- stub [result & [ran]]
  (fn [subject]
    (when ran (swap! ran conj subject))
    result))

(def pass-run {:status :done :verdict {:status :pass :notes "all claims verified"} :model :stub})
(def fail-run {:status :done :verdict {:status :fail :notes "claim 2 contradicted\ndetail"} :model :stub})
(def dead-run {:status :done :verdict nil :model :stub})

(deftest screen-covers-inbox-and-is-idempotent
  (with-root
    (fn [root]
      (seed! root)
      (let [ran  (atom [])
            outs (screen/screen! root {:run-fn (stub pass-run ran) :quiet? true})]
        (is (= 2 (count outs)) "one run per artifact: proposal + memory candidate")
        (is (every? #(= :pass (:status %)) outs))
        (is (= #{"proposals/chat-tools.md" "mementum/memories/candidate.md"}
               (set (keys (screen/load-store root)))) "verdicts persisted by path")
        (testing "subjects name the artifact kind and path"
          (is (some #(str/starts-with? % "Pending proposal for verification (proposals/chat-tools.md)") @ran))
          (is (some #(str/starts-with? % "Proposed memory candidate for verification (mementum/memories/candidate.md)") @ran)))
        (testing "idempotent — unchanged artifacts never re-run"
          (let [ran2 (atom [])]
            (is (= [] (screen/screen! root {:run-fn (stub pass-run ran2) :quiet? true})))
            (is (= [] @ran2))))
        (testing "an edited artifact re-plans; its stored verdict reads stale"
          (spit (str (fs/path root "mementum/memories/candidate.md"))
            (str (memory-doc) "edited\n"))
          (is (true? (get-in (screen/verdicts root)
                       ["mementum/memories/candidate.md" :stale?])))
          (is (= ["mementum/memories/candidate.md"]
                 (mapv :path (screen/plan root (screen/load-store root)))))
          (screen/screen! root {:run-fn (stub fail-run) :quiet? true})
          (let [v (get (screen/verdicts root) "mementum/memories/candidate.md")]
            (is (false? (:stale? v)))
            (is (= :fail (:status v)))))))))

(deftest failed-run-persists-nothing-and-retries
  (with-root
    (fn [root]
      (seed! root)
      (let [outs (screen/screen! root {:run-fn (stub dead-run) :quiet? true})]
        (is (= 2 (count outs)))
        (is (every? #(nil? (:status %)) outs) "no verdict surfaced, not invented")
        (is (= {} (screen/load-store root)) "nothing persisted")
        (is (= 2 (count (screen/plan root (screen/load-store root))))
          "still planned — retried next screen")))))

(deftest inbox-renders-verdicts
  (with-root
    (fn [root]
      (seed! root)
      (screen/screen! root {:run-fn (stub fail-run) :quiet? true})
      (let [text (proposals/render-inbox (proposals/pending root)
                   (proposals/untracked-memories root)
                   (screen/verdicts root))]
        (is (str/includes? text "verifier: ✗ fail — claim 2 contradicted")
          "first notes line on the artifact's inbox entry")
        (is (not (str/includes? text "detail")) "multi-line notes truncated to the first line"))
      (testing "stale verdict warns instead of lying"
        (spit (str (fs/path root "mementum/memories/candidate.md"))
          (str (memory-doc) "edited\n"))
        (is (str/includes?
              (proposals/render-inbox (proposals/pending root)
                (proposals/untracked-memories root) (screen/verdicts root))
              "⚠ stale (edited since screening")))
      (testing "two-arity render (no verdicts) unchanged"
        (is (not (str/includes?
                   (proposals/render-inbox (proposals/pending root)
                     (proposals/untracked-memories root))
                   "verifier:")))))))

(deftest sweep-composes-screening
  (with-root
    (fn [root]
      (fs/create-dirs (fs/path root "agents"))
      (let [screened (atom 0)
            entries  [{:id :t :select {:tag :assessor} :subject "go" :enabled true}]]
        (schedule/sweep! root {:runner    (fn [_ _] {:status :done})
                               :entries   entries
                               :screen-fn (fn [r] (is (= root r)) (swap! screened inc))})
        (is (= 1 @screened) "ONE screen pass, after all runs")
        (testing "absent screen-fn ⇒ no screening (option > detection)"
          (schedule/sweep! root {:runner (fn [_ _] {:status :done}) :entries entries})
          (is (= 1 @screened)))
        (testing "a throwing screen pass never fails the sweep"
          (let [outs (schedule/sweep! root {:runner    (fn [_ _] {:status :done})
                                            :entries   entries
                                            :screen-fn (fn [_] (throw (ex-info "gpu down" {})))})]
            (is (every? #(= :done (:status %)) outs))))))))
