(ns ouroboros.tools-test
  "The loop's hands: mementum/context (read) + mementum/propose-memory (write),
  dispatched through the real escapement tool-registry contract."
  (:require
    [babashka.fs :as fs]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [escapement.tools.protocol :as tp]
    [ouroboros.mementum.store :as store]
    [ouroboros.tools :as tools]))

(def good "---\ntype: mementum/memory\ndescription: test via tool\n---\n💡 body\n")
(def bad  "---\ntype: nope\ndescription: x\n---\nbody")

(defn- temp-root [] (str (fs/create-temp-dir {:prefix "ouro-tools"})))

(deftest context-digests-the-live-repo
  (let [reg (tools/new-registry ".")
        r   (tp/dispatch reg :mementum/context {})]
    (is (false? (:is-error r)))
    (is (str/includes? (:result r) "KNOWLEDGE ("))
    (is (str/includes? (:result r) "escapement-index") "names a known knowledge page")
    (is (str/includes? (:result r)
          "mementum/knowledge/upstream/escapement-index.md")
      "emits the repo-relative path (the fs-read argument), not a bare slug")
    (is (str/includes? (:result r) "RECENT COMMITS"))))

(defn- write-checkpoint! [root id messages]
  (let [dir (fs/path root "sessions" id "checkpoints")]
    (fs/create-dirs dir)
    (spit (str (fs/path dir (str id ".edn")))
      (pr-str {:escapement.engine.store/wmem
               {:com.fulcrologic.statecharts.data-model.working-memory-data-model/data-model
                {:messages messages}}}))))

(deftest sessions-tool-digests-conversation-sessions
  (let [root (temp-root)
        id   "compact-1783525397252"]
    (write-checkpoint! root id
      [{:role :user :text "pick a cache" :compacted? false}
       {:role :assistant :text "decision(write-back)" :compacted? true}])
    (let [r (tp/dispatch (tools/new-registry root) :mementum/sessions {})]
      (is (false? (:is-error r)))
      (is (str/includes? (:result r) (str "SESSION " id)))
      (is (str/includes? (:result r) "decision(write-back)") "the λ essence is surfaced"))))

(deftest sessions-tool-empty-safe
  (let [r (tp/dispatch (tools/new-registry (temp-root)) :mementum/sessions {})]
    (is (false? (:is-error r)))
    (is (str/includes? (:result r) "no prior conversation"))))

(deftest propose-memory-writes-valid-and-does-not-commit
  (let [root (temp-root)
        reg  (tools/new-registry root)
        r    (tp/dispatch reg :mementum/propose-memory {:slug "t1" :content good})]
    (is (false? (:is-error r)))
    (is (str/includes? (:result r) "NOT committed"))
    (is (= "test via tool" (:description (:frontmatter (store/read-doc root :memory "t1")))))))

(deftest propose-memory-rejects-invalid-and-persists-nothing
  (let [root (temp-root)
        reg  (tools/new-registry root)
        r    (tp/dispatch reg :mementum/propose-memory {:slug "t2" :content bad})]
    (is (true? (:is-error r)))
    (is (str/includes? (:result r) "Rejected"))
    (is (nil? (store/read-doc root :memory "t2")))))

(deftest unknown-tool-and-bad-input-are-corrective-not-thrown
  (testing "unknown tool name"
    (is (true? (:is-error (tp/dispatch (tools/new-registry ".") :nope/nope {})))))
  (testing "malformed input against closed schema"
    (is (true? (:is-error (tp/dispatch (tools/new-registry ".") :mementum/propose-memory {:slug 5}))))))
