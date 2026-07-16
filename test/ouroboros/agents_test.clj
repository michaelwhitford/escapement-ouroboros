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
    [ouroboros.prompts :as prompts]
    [ouroboros.signals.core :as signals.core]
    [ouroboros.tools :as tools]))

(def ^:private ctx
  {:registry-tools   (tools/tool-names)
   :read-only-floor  tools/read-only-tools
   :registry-modules (prompts/module-names)
   :registry-signals signals.core/signal-types})

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
    (is (= "λ test. the whole prompt" (:body a)) "body verbatim")
    (is (not (str/includes? (:body a) "ouroboros/agent"))
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
;; verdict schemas — SCHEMA lives with the KIND, semantics with the body
;; ---------------------------------------------------------------------------

(deftest verdict-schema-dispatches-by-kind
  (is (= [:map [:status [:enum :pass :fail]] [:notes :string]]
        (core/verdict-schema :judge))
    "judge GATES: {:status pass|fail :notes}")
  (is (= [:map [:score [:int {:min 1 :max 10}]] [:notes :string]]
        (core/verdict-schema :scorer))
    "scorer MEASURES: {:score 1-10 :notes}")
  (is (nil? (core/verdict-schema :chat)) "non-verdict kinds idle free-text")
  (is (nil? (core/verdict-schema :proposer)))
  (is (some? (core/verdict-schema :comparator)) "comparator has a verdict schema"))

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
    (is (contains? roster :harness-knowledge))
    (is (contains? roster :chat))
    (testing "the 2×2 maintenance roster is present with role tags"
      (is (= [:curator] (:tags (:harness-knowledge roster))))
      (is (= [:curator] (:tags (:app-knowledge roster))))
      (is (= [:assessor] (:tags (:harness-coder roster))))
      (is (= [:assessor] (:tags (:app-coder roster))))
      (is (every? #(= :proposer (:kind (% roster)))
            [:harness-knowledge :app-knowledge :harness-coder :app-coder])
        "ALL four cells ride the proposer kind — new role ⇒ new genome, not new kind"))
    (testing "harness-knowledge genome (the former curator)"
      (let [c (:harness-knowledge roster)]
        (is (= :proposer (:kind c)))
        (is (= [:mementum/context :mementum/sessions :mementum/propose-memory] (:tools c)))
        (is (str/starts-with? (:prompt c) "λ engage(nucleus)."))
        (is (str/includes? (:prompt c) "λ terminate.") "full body extracted")))
    (testing "chat genome — FULL grant minus web/search (testing phase)"
      (let [c (:chat roster)]
        (is (= :chat (:kind c)))
        (is (true? (:tools-explicit? c)))
        ;; :signal/emit rides the `signals:` grant, not `tools:` (design/
        ;; signals.md) — chat has no signal types, so no emit tool.
        ;; :harness/context + :ouro/propose-change are the maintenance
        ;; roster's hands (design/scheduled-maintenance) — not chat's.
        ;; This assertion is a deliberate TRIPWIRE: ceiling growth must be
        ;; consciously granted-or-excluded here.
        (is (= (disj (tools/tool-names)
                 :web/search :signal/emit :harness/context :ouro/propose-change)
               (set (:tools c)))
          "everything in the ceiling except web/search — 🎯 human decision")
        (is (str/starts-with? (:prompt c) "λ engage(nucleus)."))))
    (testing "gene-scorer genome — the GA fitness function"
      (let [s (:gene-scorer roster)]
        (is (= :scorer (:kind s)))
        (is (= :local (:model s)) "primary family; cross-family via verdict/run-across!")
        (is (= [] (:tools s)))
        (is (str/includes? (:prompt s) "λ rubric.")
          "rubric anchors live in the BODY — the calibration lever")))
    (testing "author genome — the coding workflow's PLAN stage (build-order step 5)"
      (let [a (:author roster)]
        (is (= :author (:kind a)))
        (is (= [:coder] (:tags a)))
        (is (= [:mementum/context :fs/read :fs/glob :fs/grep] (:tools a))
          "read-only — the plan stage never writes")
        (is (str/includes? (:prompt a) "λ plan.") "the document shape lives in the body")))
    (testing "builder genome — the coding workflow's BUILD stage (first write escalation)"
      (let [b (:builder roster)]
        (is (= :builder (:kind b)))
        (is (= [:coder] (:tags b)))
        (is (= [:fs/read :fs/glob :fs/grep :fs/edit :fs/multi-edit :fs/write :dev/run-tests]
               (:tools b))
          "🎯 raw fs grants (human decision) + the dedicated test gate — NO :shell/run, git stays unreachable")
        (is (str/includes? (:prompt b) "λ verify.") "the tests-GREEN gate lives in the body")))
    (testing "analyst genome — the analyst kind's first genome (code-nav)"
      (let [a (:analyst roster)]
        (is (= :analyst (:kind a)))
        (is (= [:analyst] (:tags a)))
        (is (= [:code/analyze :fs/read :fs/glob :fs/grep] (:tools a))
          "read-only + the kondo lens — INFORMS gate, no writes anywhere")
        (is (str/includes? (:prompt a) "map ≻ read")
          "the map-first discipline lives in the body")))
    (testing "harness-editor genome — the editor kind's first genome (v1)"
      (let [e (:harness-editor roster)]
        (is (= :editor (:kind e)))
        (is (= [:editor] (:tags e)))
        (is (= [:harness/context :fs/read :fs/glob :fs/grep :fs/edit :fs/multi-edit :dev/run-tests]
               (:tools e))
          "edit-only escalation — NO fs/write (cannot CREATE genomes, v1 scope by capability) + NO shell")
        (is (str/includes? (:prompt e) "SCOPE ≻ judge notes")
          "precedence is explicit — the incident fix: notes must never override scope")))
    (testing "llm-judge genome — first genome born in the convention"
      (let [j (:llm-judge roster)]
        (is (= :judge (:kind j)))
        (is (= :ornith (:model j)) "first non-:local genome — cross-family routing")
        (is (= [] (:tools j)) "the subject carries everything; no grant")
        (is (str/starts-with? (:prompt j) "λ engage(nucleus)."))
        (is (not (str/includes? (:prompt j) "submit_verdict"))
          "verdict SCHEMA is kind-owned wiring — the body carries only semantics")))
    (testing "comparator genome — pairwise tournament selector"
      (let [c (:comparator roster)]
        (is (= :comparator (:kind c)))
        (is (= :local (:model c)))
        (is (= [] (:tools c)))
        (is (str/starts-with? (:prompt c) "λ engage(nucleus)."))
        (is (str/includes? (:prompt c) "λ comparator."))
        (is (not (str/includes? (:prompt c) "submit_verdict"))
          "verdict SCHEMA is kind-owned wiring — the body carries only semantics")))))

(deftest custom-tier-adds-and-overrides
  (with-temp-repo
    {"fizzbuzz.md" (doc "type: ouroboros/agent\ndescription: custom specialist\nkind: author" "λ fizzbuzz. body")
     "harness-knowledge.md"  (doc "type: ouroboros/agent\ndescription: shadowed curator\nkind: proposer\ntools: []" "λ custom-curator. body")}
    (fn [root]
      (let [roster (agents/compile-roster root)]
        (is (= :custom (get-in roster [:fizzbuzz :tier])) "plop a file ⇒ new agent")
        (is (= :custom (get-in roster [:harness-knowledge :tier])) "custom shadows base by slug")
        (is (= :base (get-in roster [:harness-knowledge :overrides])))
        (is (= [] (get-in roster [:harness-knowledge :tools])) "replace-whole: base grant GONE")
        (is (= :base (get-in roster [:chat :tier])) "unshadowed base intact")))))

(deftest invalid-custom-fails-loud
  (with-temp-repo
    {"broken.md" (doc "type: ouroboros/agent\ndescription: d\nkind: wizard" "body")}
    (fn [root]
      (is (thrown? clojure.lang.ExceptionInfo (agents/compile-roster root))
        "one invalid genome ⇒ the whole compile throws — never half-run"))))

(deftest genome-accessor
  (is (= :harness-knowledge (:id (agents/genome :harness-knowledge "."))))
  (is (thrown? clojure.lang.ExceptionInfo (agents/genome :nonexistent "."))))

;; ---------------------------------------------------------------------------
;; assemble — preamble ⊕ modules ⊕ body (design/prompt-assembly)
;; ---------------------------------------------------------------------------

(deftest assemble-preamble-exactly-once-first
  (is (= "P\n\nbody" (core/assemble {:preamble "P" :body "body"}))
    "preamble ALWAYS, first, blank-line joined")
  (is (= "P\n\nbody" (core/assemble {:preamble "P" :body "P\n\nbody"}))
    "an embedded preamble copy is STRIPPED — exactly-once invariant, idempotent")
  (is (= (core/assemble {:preamble "P" :body "body"})
         (core/assemble {:preamble "P"
                         :body (core/assemble {:preamble "P" :body "body"})}))
    "assemble ∘ assemble ≡ assemble"))

(deftest assemble-modules-in-grant-order
  (is (= "P\n\nM1\n\nM2\n\nbody"
        (core/assemble {:preamble "P" :modules ["M1" "M2"] :body "body"}))
    "module texts sit between preamble and body, in grant order (layer order
     is load-bearing: process launch → program → I/O gate)"))

(deftest assemble-renders-vars-fail-loud
  (is (= "P\n\nmodel is local"
        (core/assemble {:preamble "P" :body "model is {{MODEL}}"
                        :subs {:MODEL "local"}}))
    "{{VAR}} late binding via escapement.prompts/render")
  (is (thrown? clojure.lang.ExceptionInfo
        (core/assemble {:preamble "P" :body "{{UNRESOLVED_TOKEN}}"}))
    "an unresolved token fails loud — prompts never ship literal {{...}}"))

(deftest modules-grant-validated-against-registry
  (let [a (parse "t" (doc (str "type: ouroboros/agent\ndescription: d\nkind: chat\n"
                               "tools: []\nmodules: [lambda-compiler]")
                          "body"))]
    (is (= [:lambda-compiler] (:modules a)) "module strings → keywords"))
  (let [a (parse "t" (doc "type: ouroboros/agent\ndescription: d\nkind: chat\ntools: []" "body"))]
    (is (= [] (:modules a)) "absent modules: ⇒ NONE — always an explicit grant"))
  (is (some :modules
        (errors-of "t" (doc (str "type: ouroboros/agent\ndescription: d\nkind: chat\n"
                                 "tools: []\nmodules: [warp-drive]")
                            "body")))
    "a module outside the registry ceiling fails loud"))

(deftest loader-assembles-granted-modules
  (with-temp-repo
    {"bridged.md" (doc (str "type: ouroboros/agent\ndescription: d\nkind: author\n"
                            "tools: []\nmodules: [lambda-compiler]")
                       "λ own. body")}
    (fn [root]
      (let [a (get (agents/compile-roster root) :bridged)]
        (is (str/starts-with? (:prompt a) "λ engage(nucleus).")
          "assembled prompt leads with the vendored preamble")
        (is (str/includes? (:prompt a) "λ bridge(x).")
          "the granted module text is IN the wire prompt")
        (is (str/ends-with? (:prompt a) "λ own. body")
          "the genome body stays LAST (prose I/O gates live at its tail)")
        (is (= "λ own. body" (:body a))
          "raw body preserved for body-level consumers (gene decomposition)")))))

(deftest base-genomes-carry-no-inline-preamble
  (doseq [id [:chat :harness-knowledge :app-knowledge :harness-coder :app-coder :gene-scorer :llm-judge :comparator]]
    (let [a (agents/genome id ".")]
      (is (not (str/includes? (:body a) "engage(nucleus)"))
        (str id " body migrated — preamble is the assembler's job"))
      (is (str/starts-with? (:prompt a) "λ engage(nucleus).")
        (str id " assembled prompt still leads with the preamble")))))

;; ---------------------------------------------------------------------------
;; report — provenance + grants + escalations visible
;; ---------------------------------------------------------------------------

(deftest report-surfaces-provenance-and-escalation
  (let [r (agents/report ".")]
    (is (str/includes? r "harness-knowledge"))
    (is (str/includes? r "tags:[:curator]") "role-as-tag visible in the audit surface")
    (is (str/includes? r "ESCALATION:[:mementum/propose-memory]")
      "the write grant beyond the read-only floor is the human's audit surface"))
  (with-temp-repo
    {"harness-knowledge.md" (doc "type: ouroboros/agent\ndescription: shadowed\nkind: proposer\ntools: []" "body")}
    (fn [root]
      (is (str/includes? (agents/report root) "custom (overrides base)")))))
;; ---------------------------------------------------------------------------
;; signals: grant — the 5th grant surface (design/signals.md)
;; ---------------------------------------------------------------------------

(deftest signals-grant-normalizes-and-arms-the-tool
  (let [a (parse "t" (doc (str "type: ouroboros/agent\n"
                               "description: d\n"
                               "kind: proposer\n"
                               "tools: [mementum/context]\n"
                               "signals: [s1/report, \":human/notice\"]")
                          "body"))]
    (is (= [:s1/report :human/notice] (:signals a)) "strings → keywords")
    (is (= [:mementum/context :signal/emit] (:tools a))
      "non-empty signals grant auto-adds :signal/emit — ONE grant surface")))

(deftest signals-grant-no-double-emit-tool
  (let [a (parse "t" (doc (str "type: ouroboros/agent\n"
                               "description: d\n"
                               "kind: proposer\n"
                               "tools: [signal/emit]\n"
                               "signals: [s1/report]")
                          "body"))]
    (is (= [:signal/emit] (:tools a)) "already-listed emit tool not duplicated")))

(deftest signals-absent-means-emit-nothing
  (let [a (parse "t" (doc "type: ouroboros/agent\ndescription: d\nkind: chat\ntools: []" "body"))]
    (is (= [] (:signals a)) "absent ⇒ [] — no floor, emit nothing")
    (is (= [] (:tools a)) "no signals ⇒ no emit tool")))

(deftest signals-unknown-type-fails-loud
  (let [errs (errors-of "t" (doc (str "type: ouroboros/agent\n"
                                      "description: d\n"
                                      "kind: proposer\n"
                                      "signals: [nope/nope, s1/report]")
                                 "body"))]
    (is (= [:nope/nope] (get-in (first (filter :signals errs)) [:signals :unknown]))
      "unknown signal types aggregated, registry named")))

(deftest signals-grant-appends-projection-to-prompt-not-body
  (with-temp-repo
    {"emitter.md" (doc (str "type: ouroboros/agent\n"
                            "description: an emitting agent\n"
                            "kind: proposer\n"
                            "signals: [s1/report]")
                       "λ emitter. observe → emit")}
    (fn [root]
      (let [a (agents/genome :emitter root)]
        (is (str/includes? (:prompt a) "λ signal_emission.")
          "projection assembled into the prompt")
        (is (str/includes? (:prompt a) "⟨type :s1/report⟩")
          "the granted type's FILLED exemplar is present")
        (is (not (str/includes? (:prompt a) "⟨type :s4/proposal⟩"))
          "ungranted types are absent")
        (is (= "λ emitter. observe → emit" (:body a))
          ":body stays RAW — gene decomposition reads the persona only")))))

(deftest report-shows-signals-and-reserved-escalation
  (with-temp-repo
    {"alarmer.md" (doc (str "type: ouroboros/agent\n"
                            "description: reserved-signal agent\n"
                            "kind: proposer\n"
                            "signals: [ouro/algedonic, s1/report]")
                       "body")}
    (fn [root]
      (let [r (agents/report root)]
        (is (str/includes? r "signals:[:ouro/algedonic :s1/report]"))
        (is (str/includes? r "RESERVED-SIGNALS:[:ouro/algedonic]")
          "reserved grants scream in the audit surface")))))
;; ---------------------------------------------------------------------------
;; tags: — role-as-tag (design/scheduled-maintenance)
;; ---------------------------------------------------------------------------

(deftest tags-normalize-open-vocabulary
  (let [a (parse "t" (doc (str "type: ouroboros/agent\n"
                               "description: d\n"
                               "kind: proposer\n"
                               "tags: [curator, \":assessor\", brand-new-role]")
                          "body"))]
    (is (= [:curator :assessor :brand-new-role] (:tags a))
      "strings → keywords; vocabulary OPEN — unknown roles are legal by design")))

(deftest tags-absent-means-none
  (let [a (parse "t" (doc "type: ouroboros/agent\ndescription: d\nkind: chat\ntools: []" "body"))]
    (is (= [] (:tags a)))))

(deftest tags-show-in-report
  (let [roster {:x {:id :x :tier :base :kind :proposer :tags [:curator]
                    :tools [] :signals [] :modules []}}]
    (is (str/includes? (core/report roster #{}) "tags:[:curator]"))))
