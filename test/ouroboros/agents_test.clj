(ns ouroboros.agents-test
  "Deterministic tests for the genome compiler (agent-model BUILD STEP 1):
  parse/validate/normalize (ouroboros.agents.core), the fold over precedence
  sources with custom-wins-by-slug (ouroboros.agents), and the roster report.
  No LLM, no network; the custom tier uses throwaway temp dirs."
  (:require
    [babashka.fs :as fs]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [ouroboros.agents :as agents]
    [ouroboros.agents.core :as core]
    [ouroboros.tools :as tools]))

(def ^:private ctx
  {:registry-tools  (tools/tool-names)
   :read-only-floor tools/read-only-tools})

(defn- doc [frontmatter body]
  (str "---\n" frontmatter "\n---\n" body))

(defn- parse [slug d]
  (core/parse-genome (merge ctx {:slug slug :tier :base :source "test" :doc d})))

(defn- errors-of [slug d]
  (try (parse slug d) nil
       (catch clojure.lang.ExceptionInfo e (:errors (ex-data e)))))

(defn- with-temp-repo
  "Create a temp repo-root with an agents/ dir holding `genomes`
  ({filename → content}), call (f root), always clean up."
  [genomes f]
  (let [dir (fs/create-temp-dir)]
    (try
      (let [agents-dir (fs/create-dirs (fs/path dir "agents"))]
        (doseq [[fname content] genomes]
          (spit (str (fs/path agents-dir fname)) content))
        (f (str dir)))
      (finally (fs/delete-tree dir)))))

;; ---------------------------------------------------------------------------
;; parse-genome — normalize + the frontmatter/body boundary
;; ---------------------------------------------------------------------------

(deftest parse-genome-normalizes
  (let [a (parse "t" (doc (str "type: ouroboros/agent\n"
                               "title: T\n"
                               "description: a test agent\n"
                               "kind: proposer\n"
                               "tools: [mementum/context, mementum/sessions]\n"
                               "model: \":ornith\"")
                          "λ test. the whole prompt"))]
    (is (= :t (:id a)))
    (is (= :base (:tier a)))
    (is (= :proposer (:kind a)) "kind string → keyword")
    (is (= [:mementum/context :mementum/sessions] (:tools a)) "tool strings → keywords")
    (is (true? (:tools-explicit? a)))
    (is (= :ornith (:model a)) "a copy-pasted ':ornith' literal normalizes")
    (is (= "λ test. the whole prompt" (:prompt a)) "body verbatim")
    (is (not (str/includes? (:prompt a) "ouroboros/agent"))
      "frontmatter STRIPPED — the agent never sees its wiring")))

(deftest parse-genome-model-defaults-to-local
  (let [a (parse "t" (doc "type: ouroboros/agent\ndescription: d\nkind: chat\ntools: []" "body"))]
    (is (= :local (:model a)))))

(deftest tools-absent-means-read-only-floor
  (let [a (parse "t" (doc "type: ouroboros/agent\ndescription: d\nkind: author" "body"))]
    (is (= (vec (sort tools/read-only-tools)) (:tools a))
      "no tools: key ⇒ the read-only floor (fails SAFE, never dangerous)")
    (is (false? (:tools-explicit? a)))))

(deftest tools-explicit-empty-means-none
  (let [a (parse "t" (doc "type: ouroboros/agent\ndescription: d\nkind: chat\ntools: []" "body"))]
    (is (= [] (:tools a)) "explicit [] ⇒ exactly no tools, NOT the floor")))

;; ---------------------------------------------------------------------------
;; parse-genome — fail-loud validation
;; ---------------------------------------------------------------------------

(deftest rejects-wrong-type
  (let [errs (errors-of "t" (doc "type: mementum/memory\ndescription: d\nkind: chat" "body"))]
    (is (some :frontmatter errs))))

(deftest rejects-blank-description
  (is (some :frontmatter
        (errors-of "t" (doc "type: ouroboros/agent\ndescription: \"\"\nkind: chat" "body")))))

(deftest rejects-unknown-kind
  (let [errs (errors-of "t" (doc "type: ouroboros/agent\ndescription: d\nkind: wizard" "body"))]
    (is (= :wizard (get-in (first (filter :kind errs)) [:kind :got]))
      "kind ∉ kinds is named in the error, with the allowed list")))

(deftest rejects-tool-outside-registry
  (let [errs (errors-of "t" (doc "type: ouroboros/agent\ndescription: d\nkind: builder\ntools: [git/commit]" "body"))]
    (is (= [:git/commit] (get-in (first (filter :tools errs)) [:tools :unknown]))
      "the registry is the CEILING — no commit tool exists to grant")))

(deftest rejects-blank-body
  (is (some :body (errors-of "t" (doc "type: ouroboros/agent\ndescription: d\nkind: chat" "")))
    "a genome with no prompt is invalid — never half-run"))

(deftest rejects-unknown-frontmatter-key
  (is (some :frontmatter
        (errors-of "t" (doc "type: ouroboros/agent\ndescription: d\nkind: chat\ntool: [x]" "body")))
    "closed map: a typo'd wiring key (tool: for tools:) fails loud"))

(deftest aggregates-all-errors
  (let [errs (errors-of "t" (doc "type: ouroboros/agent\ndescription: d\nkind: wizard\ntools: [nope/nope]" ""))]
    (is (<= 3 (count errs)) "kind + tools + body problems reported TOGETHER")))

;; ---------------------------------------------------------------------------
;; merge-roster — custom wins by slug, replace-whole, visible provenance
;; ---------------------------------------------------------------------------

(deftest merge-custom-wins-by-slug
  (let [base   {:a {:id :a :tier :base :tools [:mementum/context]}
                :b {:id :b :tier :base}}
        custom {:a {:id :a :tier :custom :tools []}}
        merged (core/merge-roster [base custom])]
    (is (= :custom (get-in merged [:a :tier])))
    (is (= [] (get-in merged [:a :tools])) "REPLACE-WHOLE — no field merge")
    (is (= :base (get-in merged [:a :overrides])) "shadowing is recorded")
    (is (= :base (get-in merged [:b :tier])) "unshadowed base survives")
    (is (nil? (get-in merged [:b :overrides])))))

;; ---------------------------------------------------------------------------
;; compile-roster — the real base tier (io/resource) + custom temp dirs
;; ---------------------------------------------------------------------------

(deftest base-roster-compiles
  (let [roster (agents/compile-roster ".")]
    (is (contains? roster :curator))
    (is (contains? roster :chat))
    (testing "curator genome"
      (let [c (:curator roster)]
        (is (= :proposer (:kind c)))
        (is (= [:mementum/context :mementum/sessions :mementum/propose-memory] (:tools c)))
        (is (str/starts-with? (:prompt c) "λ engage(nucleus)."))
        (is (str/includes? (:prompt c) "λ terminate.") "full body extracted")))
    (testing "chat genome"
      (let [c (:chat roster)]
        (is (= :chat (:kind c)))
        (is (= [] (:tools c)) "explicit empty grant — the resident chatbot holds no tools")
        (is (str/starts-with? (:prompt c) "λ engage(nucleus)."))))))

(deftest custom-tier-adds-and-overrides
  (with-temp-repo
    {"fizzbuzz.md" (doc "type: ouroboros/agent\ndescription: custom specialist\nkind: author" "λ fizzbuzz. body")
     "curator.md"  (doc "type: ouroboros/agent\ndescription: shadowed curator\nkind: proposer\ntools: []" "λ custom-curator. body")}
    (fn [root]
      (let [roster (agents/compile-roster root)]
        (is (= :custom (get-in roster [:fizzbuzz :tier])) "plop a file ⇒ new agent")
        (is (= :custom (get-in roster [:curator :tier])) "custom shadows base by slug")
        (is (= :base (get-in roster [:curator :overrides])))
        (is (= [] (get-in roster [:curator :tools])) "replace-whole: base grant GONE")
        (is (= :base (get-in roster [:chat :tier])) "unshadowed base intact")))))

(deftest invalid-custom-fails-loud
  (with-temp-repo
    {"broken.md" (doc "type: ouroboros/agent\ndescription: d\nkind: wizard" "body")}
    (fn [root]
      (is (thrown? clojure.lang.ExceptionInfo (agents/compile-roster root))
        "one invalid genome ⇒ the whole compile throws — never half-run"))))

(deftest genome-accessor
  (is (= :curator (:id (agents/genome :curator "."))))
  (is (thrown? clojure.lang.ExceptionInfo (agents/genome :nonexistent "."))))

;; ---------------------------------------------------------------------------
;; report — provenance + grants + escalations visible
;; ---------------------------------------------------------------------------

(deftest report-surfaces-provenance-and-escalation
  (let [r (agents/report ".")]
    (is (str/includes? r "curator"))
    (is (str/includes? r "ESCALATION:[:mementum/propose-memory]")
      "the write grant beyond the read-only floor is the human's audit surface"))
  (with-temp-repo
    {"curator.md" (doc "type: ouroboros/agent\ndescription: shadowed\nkind: proposer\ntools: []" "body")}
    (fn [root]
      (is (str/includes? (agents/report root) "custom (overrides base)")))))
