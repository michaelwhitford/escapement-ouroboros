(ns ouroboros.tools-test
  "The loop's hands: mementum/context (read) + mementum/propose-memory (write),
  dispatched through the real escapement tool-registry contract."
  (:require
    [babashka.fs :as fs]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [escapement.tools.protocol :as tp]
    [ouroboros.mementum.store :as store]
    [ouroboros.signals :as signals]
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
;; ---------------------------------------------------------------------------
;; :signal/emit — the data-plane write, through the real dispatch contract
;; ---------------------------------------------------------------------------

(deftest signal-emit-armed-happy-path
  (let [root (temp-root)
        reg  (tools/new-registry root {:source "test-agent"
                                       :signal-types #{:s1/report}})
        r    (tp/dispatch reg :signal/emit
               {:type "s1/report"
                :data "{:summary \"sweep ok\" :outcome :ok}"
                :lambda "λ sweep → ok"})]
    (is (false? (:is-error r)))
    (is (str/includes? (:result r) "Emitted"))
    (let [[sig] (signals/all-signals root)]
      (is (= :s1/report (:signal/type sig)))
      (is (= "test-agent" (:signal/source sig)) "source is infrastructure-set")
      (is (= {:summary "sweep ok" :outcome :ok} (:signal/data sig))))))

(deftest signal-emit-default-registry-is-inert-safe
  (let [root (temp-root)
        r    (tp/dispatch (tools/new-registry root) :signal/emit
               {:type "s1/report" :data "{:summary \"x\" :outcome :ok}"})]
    (is (true? (:is-error r)))
    (is (str/includes? (:result r) "not in your signal grant"))
    (is (empty? (signals/all-signals root)) "nothing persisted")))

(deftest signal-emit-corrective-rejections
  (let [root (temp-root)
        reg  (tools/new-registry root {:source "t" :signal-types #{:s1/report}})]
    (testing "unknown type names the registry"
      (let [r (tp/dispatch reg :signal/emit {:type "nope/nope" :data "{}"})]
        (is (true? (:is-error r)))
        (is (str/includes? (:result r) "unknown signal type"))))
    (testing "granted-elsewhere type names YOUR grant"
      (let [r (tp/dispatch reg :signal/emit
                {:type "human/notice" :data "{:summary \"x\"}"})]
        (is (true? (:is-error r)))
        (is (str/includes? (:result r) "grant"))))
    (testing "unreadable EDN is corrective"
      (let [r (tp/dispatch reg :signal/emit {:type "s1/report" :data "{:a"})]
        (is (true? (:is-error r)))
        (is (str/includes? (:result r) "not readable EDN"))))
    (testing "schema-invalid data is corrective with humanized errors"
      (let [r (tp/dispatch reg :signal/emit
                {:type "s1/report" :data "{:summary \"x\"}"})]
        (is (true? (:is-error r)))
        (is (str/includes? (:result r) "fix and retry"))))
    (testing "duplicate is corrective with pointer"
      (let [args {:type "s1/report" :data "{:summary \"once\" :outcome :ok}"}]
        (tp/dispatch reg :signal/emit args)
        (let [r (tp/dispatch reg :signal/emit args)]
          (is (true? (:is-error r)))
          (is (str/includes? (:result r) "duplicate")))))
    (is (= 1 (count (signals/all-signals root)))
      "only the ONE valid emission persisted")))

(deftest signal-emit-in-the-ceiling
  (is (contains? (tools/tool-names) :signal/emit)))
;; ---------------------------------------------------------------------------
;; :harness/context + :ouro/propose-change — the maintenance roster's hands
;; ---------------------------------------------------------------------------

(deftest harness-context-digests-the-live-harness
  (let [r (tp/dispatch (tools/new-registry ".") :harness/context {})]
    (is (false? (:is-error r)))
    (is (str/includes? (:result r) "ROSTER:"))
    (is (str/includes? (:result r) "PENDING PROPOSALS"))
    (is (str/includes? (:result r) "⟨genome chat⟩") "genome bodies present")
    (is (str/includes? (:result r) "MODELS:"))))

(deftest propose-change-gated-round-trip
  (let [root (temp-root)
        reg  (tools/new-registry root)
        good (str "---\ntype: ouroboros/proposal\n"
                  "description: test finding\ntarget: src/ouroboros/agents/chat.md\n"
                  "evidence: [compact-1]\nseverity: ordinary\n---\n🔁 problem → sketch\n")]
    (testing "valid proposal writes working tree only"
      (let [r (tp/dispatch reg :ouro/propose-change {:slug "t1" :content good})]
        (is (false? (:is-error r)))
        (is (str/includes? (:result r) "NOT committed"))))
    (testing "re-propose pending is corrective"
      (let [r (tp/dispatch reg :ouro/propose-change {:slug "t1" :content good})]
        (is (true? (:is-error r)))
        (is (str/includes? (:result r) "already pending"))))
    (testing "invalid severity is corrective with errors"
      (let [r (tp/dispatch reg :ouro/propose-change
                {:slug "t2" :content (str/replace good "ordinary" "urgent")})]
        (is (true? (:is-error r)))
        (is (str/includes? (:result r) "severity"))))))

(deftest maintenance-tools-in-the-ceiling
  (is (contains? (tools/tool-names) :harness/context))
  (is (contains? (tools/tool-names) :ouro/propose-change))
  (is (not (contains? tools/read-only-tools :harness/context))
    "harness context is an explicit grant, not the floor"))
