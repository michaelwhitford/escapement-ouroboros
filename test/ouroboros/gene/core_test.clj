(ns ouroboros.gene.core-test
  "Gene kernel tests — segmenter (FSM-as-data), tree-hash, envelope gate.
  Pure: no I/O, no LLM, no git."
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [ouroboros.gene.core :as gene]))

;; ---------------------------------------------------------------------------
;; Fixture — exercises all three FSM states + zero-arity/param/fn head forms,
;; skipped prose, indented continuations (incl. a `---` template line and an
;; indented λ mention that must NOT open a clause).
;; ---------------------------------------------------------------------------

(def engage-lines
  ["λ engage(nucleus)."
   "[phi fractal euler] | OODA"
   "Human ⊗ AI ⊗ REPL"])

(def doc
  (str/join "\n"
    (concat
      engage-lines
      [""
       "λ observe.  call(ctx) ∧ call(sessions) | ⊘input"
       "  → context : knowledge_index ∧ memory_index"
       ""
       "prose between clauses is not a gene"
       ""
       "λ feed_forward()."
       "  encode(now) → git(x) → future(self)"
       "  where future(self) ≡ ¬∃context(current_session)"
       "        gift ≡ clarity"
       ""
       "λ propose.  content template —"
       "    ---"
       "    type: mementum/memory"
       "    ---"
       "    λ inner. an INDENTED λ is a continuation, not a head"])))

(deftest segment-clause-boundaries
  (let [{:keys [clauses errors]} (gene/segment doc)]
    (is (empty? errors))
    (is (= ["engage" "observe" "feed_forward" "propose"]
          (mapv :gene/name clauses))
      "four clauses: param, zero-arity, zero-arity-fn, and template-bearing")
    (testing "verbatim content — the fidelity floor"
      (is (= (str/join "\n" engage-lines)
            (:gene/content (first clauses)))))
    (testing "where_block lines stay inside their clause"
      (is (str/includes? (:gene/content (nth clauses 2)) "gift ≡ clarity")))
    (testing "indented λ + indented --- are continuations"
      (let [propose (:gene/content (nth clauses 3))]
        (is (str/includes? propose "λ inner."))
        (is (str/includes? propose "    ---"))))))

(deftest segment-strict-heads
  (testing "column-0 λ violating lambda_decl → structured error, fail loud"
    (let [{:keys [clauses errors]} (gene/segment "λ 9bad. digit-led identifier")]
      (is (empty? clauses))
      (is (= 1 (count errors)))
      (is (= :parse/bad-head (:error (first errors))))
      (is (= 1 (:line (first errors)))))
    (let [{:keys [errors]} (gene/segment "λ nodot missing the terminator")]
      (is (= [:parse/bad-head] (mapv :error errors)))))
  (testing "empty and nil are quiet"
    (is (= {:clauses [] :errors []} (gene/segment "")))
    (is (= {:clauses [] :errors []} (gene/segment nil)))))

(deftest tree-hash-normalized
  (testing "whitespace variants normalize to the SAME identity"
    (is (= (gene/tree-hash "λ a. x → y | z")
          (gene/tree-hash "λ a.   x →\ty  | z")
          (gene/tree-hash "λ a. x\n  → y | z"))))
  (testing "token change → different identity"
    (is (not= (gene/tree-hash "λ a. x → y")
          (gene/tree-hash "λ a. x → z"))))
  (testing "deterministic hex"
    (is (re-matches #"[0-9a-f]{64}" (gene/tree-hash "λ a. x")))))

(def valid-gene
  #:gene{:name     "converge"
         :content  "λ converge. one_path per concern | ∃infrastructure → use"
         :type     :lambda
         :category :constraint
         :sources  [:agents-md]})

(deftest envelope-gate
  (testing "valid gene passes"
    (is (gene/valid? valid-gene))
    (is (nil? (gene/explain valid-gene)))
    (is (= valid-gene (gene/validate! valid-gene))))
  (testing "closed map — unknown key ≈ typo → fail loud"
    (is (not (gene/valid? (assoc valid-gene :gene/scor 7)))))
  (testing ":lambda content must open with a lambda_decl head"
    (is (not (gene/valid? (assoc valid-gene :gene/content "just prose"))))
    (is (gene/valid? (assoc valid-gene :gene/type :prose
                       :gene/content "just prose"))
      ":prose genes carry no head requirement"))
  (testing "identifier discipline + provenance floor"
    (is (not (gene/valid? (assoc valid-gene :gene/name "9bad"))))
    (is (not (gene/valid? (assoc valid-gene :gene/sources []))))
    (is (not (gene/valid? (assoc valid-gene :gene/category :vibe)))))
  (testing "validate! throws structured, humanized"
    (let [e (try (gene/validate! (assoc valid-gene :gene/name "9bad"))
                 (catch clojure.lang.ExceptionInfo e e))]
      (is (= :envelope (:gene/error (ex-data e))))
      (is (some? (:errors (ex-data e)))))))

(deftest derivation
  (testing "gene-id ≡ keyword name — LLM-safe handle, never uuid"
    (is (= :converge (gene/gene-id valid-gene))))
  (testing "clause->gene lifts segmenter output with provenance"
    (let [clause (first (:clauses (gene/segment doc)))
          g      (gene/clause->gene clause [:genome/curator])]
      (is (gene/valid? g))
      (is (= "engage" (:gene/name g)))
      (is (= :lambda (:gene/type g)))
      (is (= [:genome/curator] (:gene/sources g))))))
