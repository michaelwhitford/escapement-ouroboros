(ns ouroboros.gene.ast-test
  "Deterministic tests for the λ-notation AST reader (ouroboros.gene.ast).
  No I/O, no LLM — pure kernel only."
  (:require
    [clojure.test :refer [deftest is testing]]
    [ouroboros.gene.ast :as ast]))

;; ---------------------------------------------------------------------------
;; Tokenizer
;; ---------------------------------------------------------------------------

(deftest tokenize-classes-and-gluedness
  (testing "word zoo the EDN reader chokes on tokenizes as words"
    (is (= [:word :word :word]
          (mapv :t (ast/tokenize "proved: 2026-07-12 9e57f16…")))))
  (testing "glyphs one code point each; emoji (surrogate pair) survives whole"
    (is (= ["💡" "→" "∅"] (mapv :v (ast/tokenize "💡 → ∅")))))
  (testing "gluedness marks adjacency"
    (let [[a b c] (ast/tokenize "¬x y")]
      (is (false? (:glued? a)))
      (is (true?  (:glued? b)))
      (is (false? (:glued? c)))))
  (testing "strings and keywords"
    (is (= [{:t :string :v "\"hi\"" :glued? false}
            {:t :keyword :v ":gene/id" :glued? false}]
          (ast/tokenize "\"hi\" :gene/id")))))

;; ---------------------------------------------------------------------------
;; Form reader — total on malformed input
;; ---------------------------------------------------------------------------

(deftest read-forms-nesting-and-recovery
  (testing "balanced nesting"
    (let [{:keys [forms errors]} (ast/read-forms (ast/tokenize "f(a [b {c}])"))]
      (is (empty? errors))
      (is (= :group (:f (second forms))))))
  (testing "unbalanced close collected, not thrown"
    (let [{:keys [errors]} (ast/read-forms (ast/tokenize "a ) b"))]
      (is (= :reader/unbalanced-close (:error (first errors))))))
  (testing "unclosed group closed at EOF with error"
    (let [{:keys [forms errors]} (ast/read-forms (ast/tokenize "f(a"))]
      (is (= :reader/unclosed (:error (first errors))))
      (is (seq forms)))))

;; ---------------------------------------------------------------------------
;; Chain parser
;; ---------------------------------------------------------------------------

(defn- clause [content] (ast/parse-clause {:gene/name "x" :gene/content content}))

(deftest chain-terms-ops-and-prose-runs
  (let [c     (clause "λ x. simple > complex forever more → win")
        chain (first (:ast/body c))]
    (testing "flat op chain, no precedence"
      (is (= [">" "→"] (:ast/ops chain))))
    (testing "consecutive terms merge into prose-run"
      (is (= [:word :prose-run :word]
            (mapv :ast/node (:ast/terms chain))))
      (is (= ["complex" "forever" "more"]
            (mapv :ast/value (:ast/parts (second (:ast/terms chain)))))))))

(deftest prefix-call-group-terms
  (let [c (clause "λ x. ¬optimize(one) | use(lib) → {a → b}")]
    (testing "prefix over a call (glued glyph)"
      (let [t (first (:ast/terms (first (:ast/body c))))]
        (is (= :prefix (:ast/node t)))
        (is (= "¬" (:ast/op t)))
        (is (= :call (:ast/node (:ast/term t))))))
    (testing "call args + brace group"
      (let [[t1 t2] (:ast/terms (second (:ast/body c)))]
        (is (= :call (:ast/node t1)))
        (is (= "lib" (-> t1 :ast/args first :ast/terms first :ast/value)))
        (is (= :group (:ast/node t2)))
        (is (= :brace (:ast/delim t2)))))))

(deftest glyph-in-term-position-is-symbol-not-op
  (let [chain (first (:ast/body (clause "λ x. next → ∅")))]
    (is (= ["→"] (:ast/ops chain)))
    (is (= :symbol (:ast/node (second (:ast/terms chain)))))
    (is (= "∅" (:ast/value (second (:ast/terms chain)))))))

;; ---------------------------------------------------------------------------
;; Clause: head forms, alternatives, where, errors
;; ---------------------------------------------------------------------------

(deftest three-head-forms
  (is (= :identity (:ast/head-form (clause "λ x. a"))))
  (is (= :fn0      (:ast/head-form (ast/parse-clause
                                     {:gene/name "getApi"
                                      :gene/content "λ getApi(). a"}))))
  (let [c (ast/parse-clause {:gene/name "recall"
                             :gene/content "λ recall(q, n). temporal(git_log) ∪ semantic(git_grep)"})]
    (is (= :params (:ast/head-form c)))
    (is (= ["q" "n"] (:ast/params c)))))

(deftest alternatives-split-on-top-level-pipe
  (let [c (clause "λ x. a → b\n  | c ∧ d\n  | ¬e")]
    (is (= 3 (count (:ast/body c))))
    (is (empty? (:ast/errors c)))))

(deftest where-block-bindings
  (let [c (clause "λ x. body(here)\nwhere future(self) ≡ ¬∃context(now)\n      gift ≡ clarity")
        [b1 b2] (:ast/where c)]
    (is (= 1 (count (:ast/body c))))
    (is (= :binding (:ast/node b1)))
    (is (= :call (:ast/node (first (:ast/terms (:ast/lhs b1))))))
    (is (= "gift" (-> b2 :ast/lhs :ast/terms first :ast/value)))))

(deftest total-on-garbage
  (let [c (clause "λ x. ((( \"unterminated")]
    (is (seq (:ast/errors c)))
    (is (vector? (:ast/body c)))))

;; ---------------------------------------------------------------------------
;; parse (segment ∘ parse-clause) + telemetry
;; ---------------------------------------------------------------------------

(deftest parse-full-text-and-unknown-ops
  (let [text (str "# prose around clauses is skipped\n\n"
                  "λ a. x → y ⊕ z\n\n"
                  "λ b. p ≻ q ⊕ r\n")
        p    (ast/parse text)]
    (is (= 2 (count (:ast/clauses p))))
    (is (empty? (:ast/segment-errors p)))
    (testing "draft ops absent from unknown; coined ops counted"
      (is (= {"⊕" 2 "≻" 1} (ast/unknown-ops p)))
      (is (= 1 (get (ast/ops-used p) "→"))))))
