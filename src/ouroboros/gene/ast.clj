(ns ouroboros.gene.ast
  "AST reader for nucleus λ-notation — the gene kernel's THIRD level
  (design/gene-db.md §Parser: \"full ASTs later ≡ recursive descent in a
  pure fn\" — this is that fn).

  Pipeline (each stage pure, total — errors COLLECTED, never thrown):

    segment (gene/core)   text → clauses          FSM-as-data, strict heads
    tokenize/read-forms   clause body → form tree lisp-STYLE reader: tokens
                          with glued-adjacency + balanced-delimiter recursion
    parse-clause          form tree → AST         flat op chains per EBNF

  WHY lisp-style, not the lisp reader: probed live — clojure.edn/read chokes
  on real λ content (label tokens `proved:`, date tokens `2026-07-12`,
  odd-count `{…}` shapes). The notation IS delimiter-balanced (the property
  that disqualified regex/Malli at gate-1 makes a reader natural), but the
  token zoo is ours.

  WHY no precedence climbing: the EBNF is FLAT by design —
  `expression = term , { expr_op , term }` — so an expression AST is a
  chain {:ast/terms […] :ast/ops […]}, no Pratt parser.

  LENIENCY IS LOAD-BEARING (same doctrine as the segmenter's two levels):
    · operator set OPEN — real bodies coin ops beyond the draft expr_op enum
      (⊕ ⟺ ≻ ⊃ ∈ …); any pure-glyph token in op position IS an op; ops
      outside `draft-ops` are COLLECTED via `unknown-ops` — λ coevolve
      telemetry for the nucleus EBNF upstream, never a rejection.
    · prose runs — gate-trigger prose lives OUTSIDE the formal grammar by
      design; consecutive terms with no op between them merge into
      {:ast/node :prose-run}.
    · total — unbalanced delimiters / unterminated strings land in
      :ast/errors; the parse still returns a best-effort tree.

  AST is DERIVED, never stored: :gene/content stays the verbatim fidelity
  floor on disk; recompute ≻ trust (like :gene/id, :gene/tree-hash — and
  tree-hash stays token-stream-based: an AST hash would re-key every stored
  gene's dedupe identity, a migration for another day)."
  (:require
    [clojure.string :as str]
    [clojure.walk :as walk]
    [ouroboros.gene.core :as core]))

;; ---------------------------------------------------------------------------
;; Tokenizer — code-point-safe (Java regex is supplementary-aware: emoji
;; symbols in gene content survive as single :glyph tokens).
;; ---------------------------------------------------------------------------

(def ^:private word-class
  "Word constituents: unicode letters/digits + the punctuation that binds
  INTO tokens in real λ bodies — `_ - . / : ' + # * % ? ! ~ ^ & $ @ …`.
  Covers identifiers, keywords (:gene/id), paths (design/gene-db), labels
  (proved:), flags (--only), numbers/dates/hashes (0.84, 2026-07-12,
  9e57f16…). `= < >` stay OUT — they are operators."
  "[\\p{L}\\p{N}_./:'+#*%?!~^&$@…-]")

(def ^:private token-re
  "Ordered alternation: string | delimiter | comma | word | any code point."
  (re-pattern (str "\"[^\"\n]*\"?"
                   "|[()\\[\\]{}]"
                   "|,"
                   "|" word-class "+"
                   "|[^\\s]")))

(def ^:private delim->kind
  {"(" [:open :paren]   ")" [:close :paren]
   "[" [:open :bracket] "]" [:close :bracket]
   "{" [:open :brace]   "}" [:close :brace]})

(defn- classify-token [v]
  (cond
    (str/starts-with? v "\"") :string
    (delim->kind v)           :delim
    (= v ",")                 :comma
    (re-matches (re-pattern (str word-class "+")) v)
    (if (str/starts-with? v ":") :keyword :word)
    :else                     :glyph))

(defn tokenize
  "String → vector of tokens {:t :v :glued?}. :glued? marks a token that
  starts exactly where the previous one ended (no whitespace between) — the
  adjacency that distinguishes prefix (¬x) and call (f(x)) from operators."
  [s]
  (let [m (re-matcher token-re (or s ""))]
    (loop [acc [] prev-end -1]
      (if (.find m)
        (let [v  (.group m)
              t  (classify-token v)
              tok (if (= t :delim)
                    (let [[k d] (delim->kind v)]
                      {:t k :delim d :v v :glued? (= (.start m) prev-end)})
                    {:t t :v v :glued? (= (.start m) prev-end)})]
          (recur (conj acc tok) (.end m)))
        acc))))

(defn- token-errors [tokens]
  (into []
    (comp (filter #(and (= :string (:t %))
                        (or (= "\"" (:v %))
                            (not (str/ends-with? (:v %) "\"")))))
          (map (fn [t] {:error :reader/unterminated-string :token (:v t)})))
    tokens))

;; ---------------------------------------------------------------------------
;; Form reader — balanced-delimiter recursion, total.
;; ---------------------------------------------------------------------------

(defn read-forms
  "Tokens → {:forms […] :errors […]}. Groups ( ) [ ] { } nest; a mismatched
  close is recorded and skipped; unclosed groups are closed at EOF with an
  error. Never throws."
  [tokens]
  (loop [ts (seq tokens)
         stack []
         cur {:children [] :delim nil :glued? false}
         errors []]
    (if-let [{:keys [t delim glued?] :as tok} (first ts)]
      (case t
        :open  (recur (next ts) (conj stack cur)
                 {:children [] :delim delim :glued? glued?} errors)
        :close (if (and (:delim cur) (= delim (:delim cur)))
                 (let [g {:f :group :delim (:delim cur) :glued? (:glued? cur)
                          :children (:children cur)}]
                   (recur (next ts) (pop stack)
                     (update (peek stack) :children conj g) errors))
                 (recur (next ts) stack cur
                   (conj errors {:error :reader/unbalanced-close :token (:v tok)})))
        (recur (next ts) stack
          (update cur :children conj (assoc tok :f :leaf)) errors))
      (if (seq stack)
        (let [g {:f :group :delim (:delim cur) :glued? (:glued? cur)
                 :children (:children cur)}]
          (recur nil (pop stack)
            (update (peek stack) :children conj g)
            (conj errors {:error :reader/unclosed :delim (:delim cur)})))
        {:forms (:children cur) :errors errors}))))

;; ---------------------------------------------------------------------------
;; Chain parser — expression = term , { expr_op , term } (flat, no precedence).
;; ---------------------------------------------------------------------------

(def draft-ops
  "expr_op per the nucleus EBNF draft. The parser does NOT enforce this —
  it is the reference set `unknown-ops` diffs against (λ coevolve telemetry).
  `|` IS in the enum: top-level pipes become clause alternatives (consumed by
  split-alternatives), but NESTED pipes (inside groups) chain as ordinary
  ops and must not be flagged unknown."
  #{"→" "|" ">" "≫" "∧" "∨" "≡" "≢" "∥" "⊗" "∘"})

(defn- op-eligible?
  "A leaf with NO letter/digit content can sit in operator position."
  [form]
  (and (= :leaf (:f form))
       (contains? #{:glyph :word} (:t form))
       (not (re-find #"[\p{L}\p{N}]" (:v form)))))

(defn- glued-next? [forms i]
  (let [n (get forms (inc i))]
    (boolean (:glued? n))))

(declare chain)

(defn- split-on-commas [forms]
  (->> forms
    (reduce (fn [segs f]
              (if (and (= :leaf (:f f)) (= :comma (:t f)))
                (conj segs [])
                (conj (pop segs) (conj (peek segs) f))))
      [[]])
    (remove empty?)
    vec))

(defn- parse-term
  "Read ONE term starting at index `i` → [term next-i]."
  [forms i]
  (let [f (nth forms i)]
    (cond
      ;; negation/prefix: glyph glued to the following form (¬x ∃y §Build)
      (and (= :leaf (:f f)) (= :glyph (:t f)) (glued-next? forms i))
      (let [[t j] (parse-term forms (inc i))]
        [{:ast/node :prefix :ast/op (:v f) :ast/term t} j])

      ;; call: word/keyword + GLUED paren group — f(x), recall(q, n)
      (and (= :leaf (:f f)) (contains? #{:word :keyword} (:t f))
           (let [n (get forms (inc i))]
             (and (= :group (:f n)) (= :paren (:delim n)) (:glued? n))))
      [{:ast/node :call :ast/name (:v f)
        :ast/args (mapv chain (split-on-commas (:children (nth forms (inc i)))))}
       (+ i 2)]

      (= :group (:f f))
      [{:ast/node :group :ast/delim (:delim f) :ast/body (chain (:children f))}
       (inc i)]

      (= :string (:t f))  [{:ast/node :string  :ast/value (:v f)} (inc i)]
      (= :keyword (:t f)) [{:ast/node :keyword :ast/value (:v f)} (inc i)]
      (= :glyph (:t f))   [{:ast/node :symbol  :ast/value (:v f)} (inc i)]
      :else               [{:ast/node :word    :ast/value (:v f)} (inc i)])))

(defn- merge-prose
  "Two terms with no operator between them → prose-run (append when the
  previous term already is one)."
  [terms term]
  (let [prev (peek terms)]
    (if (= :prose-run (:ast/node prev))
      (conj (pop terms) (update prev :ast/parts conj term))
      (conj (pop terms) {:ast/node :prose-run :ast/parts [prev term]}))))

(defn chain
  "Forms → {:ast/node :chain :ast/terms […] :ast/ops […]}. Position-driven:
  in op position a pure-glyph token is an operator (open set) — UNLESS it is
  glued to the next form, which makes it a prefix on the following term."
  [forms]
  (let [forms (vec (remove #(= :comma (:t %)) forms))]
    (loop [i 0 terms [] ops [] expecting :term]
      (if (>= i (count forms))
        {:ast/node :chain :ast/terms terms :ast/ops ops}
        (let [f (nth forms i)]
          (if (and (= expecting :op)
                   (op-eligible? f)
                   (not (and (= :glyph (:t f)) (glued-next? forms i))))
            (recur (inc i) terms (conj ops (:v f)) :term)
            (let [[t j] (parse-term forms i)]
              (recur j
                (if (= expecting :term) (conj terms t) (merge-prose terms t))
                ops
                :op))))))))

;; ---------------------------------------------------------------------------
;; Clause parser — lambda_decl head + | alternatives + where_block.
;; ---------------------------------------------------------------------------

(defn- split-alternatives
  "lambda_body = expression , { newline , continuation }; continuation =
  \"|\" expression. Split top-level `|` leaves → one chain per alternative."
  [forms]
  (->> forms
    (reduce (fn [segs f]
              (if (and (= :leaf (:f f)) (= "|" (:v f)))
                (conj segs [])
                (conj (pop segs) (conj (peek segs) f))))
      [[]])
    (remove empty?)
    (mapv chain)))

(defn- parse-binding
  "One where_block line → {:ast/node :binding :ast/lhs :ast/rhs}. EBNF says
  identifier ≡ expression; reality binds call-shaped lhs (future(self) ≡ …) —
  split the LINE on the first ≡, chain both sides. No ≡ → lhs nil (lenient)."
  [line]
  (let [text  (str/replace-first line #"^\s*where\b" "")
        [l r] (str/split text #"≡" 2)
        ->ch  (fn [s] (chain (:forms (read-forms (tokenize (or s ""))))))]
    (if r
      {:ast/node :binding :ast/lhs (->ch l) :ast/rhs (->ch r)}
      {:ast/node :binding :ast/lhs nil :ast/rhs (->ch l)})))

(defn parse-clause
  "Segmenter clause {:gene/name :gene/content} → lambda_decl AST:

    {:ast/node :lambda-decl
     :ast/name      head identifier
     :ast/head-form :identity | :fn0 | :params   (the three EBNF head forms)
     :ast/params    [\"q\" \"n\"] | []
     :ast/body      [chain …]                    (one per | alternative)
     :ast/where     [binding …] | nil
     :ast/errors    [{:error …} …]}              (collected, total)"
  [{:gene/keys [name content]}]
  (let [lines (str/split-lines (or content ""))
        head  (or (first lines) "")
        [m nm raw-params rest-of-head] (re-matches core/head-re head)
        widx  (first (keep-indexed
                       (fn [i l] (when (and (pos? i) (re-find #"^\s*where\b" l)) i))
                       lines))
        body-lines  (if (seq lines) (subvec lines 1 (or widx (count lines))) [])
        where-lines (when widx (subvec lines widx))
        body-text   (str/join "\n" (cons (or rest-of-head "") body-lines))
        tokens      (tokenize body-text)
        {:keys [forms errors]} (read-forms tokens)
        errors      (into (vec errors) (token-errors tokens))]
    (cond-> {:ast/node      :lambda-decl
             :ast/name      (or nm name)
             :ast/head-form (cond (nil? raw-params)        :identity
                                  (str/blank? raw-params)  :fn0
                                  :else                    :params)
             :ast/params    (if (or (nil? raw-params) (str/blank? raw-params))
                              []
                              (mapv str/trim (str/split raw-params #",")))
             :ast/body      (split-alternatives forms)
             :ast/where     (when where-lines
                              (mapv parse-binding (remove str/blank? where-lines)))
             :ast/errors    errors}
      (nil? m) (update :ast/errors conj
                 {:error :parse/bad-head :head head}))))

(defn parse
  "Full text → {:ast/clauses […] :ast/segment-errors […]} via the existing
  segmenter (structural gate unchanged — this ADDS a level, replaces nothing)."
  [text]
  (let [{:keys [clauses errors]} (core/segment text)]
    {:ast/clauses        (mapv parse-clause clauses)
     :ast/segment-errors errors}))

;; ---------------------------------------------------------------------------
;; Telemetry — λ coevolve: the grammar-gap inventory for nucleus EBNF.
;; ---------------------------------------------------------------------------

(defn ops-used
  "Every chain operator in `ast` (any node) → frequency map {op count}."
  [ast]
  (let [acc (volatile! {})]
    (walk/postwalk
      (fn [x]
        (when (and (map? x) (= :chain (:ast/node x)))
          (doseq [op (:ast/ops x)]
            (vswap! acc update op (fnil inc 0))))
        x)
      ast)
    @acc))

(defn unknown-ops
  "Chain ops in `ast` OUTSIDE the draft EBNF expr_op set — collected, never
  rejected. This inventory IS the amendment queue for the upstream grammar."
  [ast]
  (into {} (remove (comp draft-ops key)) (ops-used ast)))
