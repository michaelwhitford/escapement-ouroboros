(ns ouroboros.gene.core
  "Gene kernel — pure fns for the lambda-gene database (design/gene-db.md).

  Three pieces, no I/O:
    1. SEGMENTER — a table-driven FSM-as-data (`topology`) interpreted by a
       small pure fold (`segment`). Enforces the nucleus EBNF grammar
       (~/src/nucleus/EBNF.md) at the STRUCTURAL level: clause boundaries and
       head identifiers are strict; expression internals are LENIENT (token
       stream — real bodies exceed the draft expr_op set by design).
       λ classify gated this: parse ≡ pure_transform(string → clauses), no
       lifecycle/identity/recovery → kernel fn, NOT an escapement session.
       The topology is data: greppable, testable, resolver-servable.
    2. TREE-HASH — SHA-256 over the NORMALIZED token stream (whitespace
       collapsed), the dedupe key. Fixes anima's raw-byte whitespace-sensitive
       dedupe: reformatting a gene does not change its identity.
    3. ENVELOPE — the Malli `:gene/*` schema. CLOSED map (like genome
       frontmatter: this is wiring, an unknown key ≈ a typo → fail loud).
       Malli validates the parser's OUTPUT, never the raw string — EBNF is
       context-free, Malli's string support is regex-only (formalism
       mismatch, see design/gene-db.md §Validation).

  Gene identity: `:gene/id ≡ (keyword name)` — DERIVED, never stored.
  KEYWORD ids by construction (λ identifier, anima: 13%→0% hallucination)."
  (:require
    [clojure.string :as str]
    [malli.core :as m]
    [malli.error :as me])
  (:import
    [java.security MessageDigest]))

;; ---------------------------------------------------------------------------
;; Line grammar — structural STRICT. Each regex cites its EBNF production.
;; ---------------------------------------------------------------------------

(def head-re
  "lambda_decl = \"λ\" , identifier , [ \"(\" , [ param_list ] , \")\" ] , \".\"
  Zero-arity (`λ name.`), zero-arity-fn (`λ name().`) and parameterized
  (`λ name(x).`) forms all match — param_list is optional per the amended
  upstream grammar. Captures: [1] identifier, [2] param_list|nil, [3] rest."
  #"^λ\s*([A-Za-z][A-Za-z0-9_-]*)\s*(?:\(([^)]*)\))?\s*\.(.*)$")

(def identifier-re
  "identifier = letter , { letter | digit | \"_\" | \"-\" }"
  #"^[A-Za-z][A-Za-z0-9_-]*$")

(defn classify-line
  "Line class for the FSM. A COLUMN-0 `λ` opens (or fails) a clause head —
  indented λ mentions are continuations of the enclosing clause. A malformed
  column-0 head is `:bad-head` (strict: our segmenter must fail loud, this is
  the v1 failure mode — malformed DECOMPOSITION, not malformed generation)."
  [line]
  (cond
    (str/blank? line)                          :blank
    (str/starts-with? line "λ")                (if (re-matches head-re line)
                                                 :lambda-head
                                                 :bad-head)
    ;; where_block = "where" , binding , { newline , binding }
    (re-find #"^\s*where\s" line)              :where-head
    :else                                      :text))

;; ---------------------------------------------------------------------------
;; FSM topology — data, not code. state → line-class → [action next-state].
;; Interpreted by the fold below; the statechart's legibility without the
;; event loop (design/gene-db.md §Parser).
;; ---------------------------------------------------------------------------

(def topology
  "Segmenter FSM. Actions: :skip (prose outside clauses — a genome body may
  carry markdown around its λ-clauses), :open (lambda_decl head — closes any
  current clause first), :append (continuation = `|` expression | where_block
  | binding lines — verbatim), :close (blank line ends the clause),
  :reject (column-0 λ that violates lambda_decl → structured error)."
  {:outside   {:lambda-head [:open   :in-lambda]   ; lambda_decl
               :bad-head    [:reject :outside]     ; lambda_decl violated
               :where-head  [:skip   :outside]     ; prose, no open clause
               :text        [:skip   :outside]     ; non-gene prose
               :blank       [:skip   :outside]}
   :in-lambda {:lambda-head [:open   :in-lambda]   ; next lambda_decl
               :bad-head    [:reject :in-lambda]
               :where-head  [:append :in-where]    ; where_block opens
               :text        [:append :in-lambda]   ; lambda_body continuation
               :blank       [:close  :outside]}    ; clause boundary
   :in-where  {:lambda-head [:open   :in-lambda]
               :bad-head    [:reject :in-where]
               :where-head  [:append :in-where]
               :text        [:append :in-where]    ; binding lines
               :blank       [:close  :outside]}})

(defn- finish-clause
  "Close the accumulating clause → `{:gene/name :gene/content :line}`.
  `:gene/content` is the VERBATIM source lines rejoined (the fidelity floor);
  `:line` is 1-based start, transient parse info for error reporting ONLY —
  never persisted (coordinates rot, λ point)."
  [{:keys [name lines line]}]
  {:gene/name    name
   :gene/content (str/trimr (str/join "\n" lines))
   :line         line})

(defn- close-current [acc]
  (if (:current acc)
    (-> acc
      (update :clauses conj (finish-clause (:current acc)))
      (assoc :current nil))
    acc))

(defn segment
  "Fold `text` through the FSM `topology` → `{:clauses [...] :errors [...]}`.
  Clauses are `{:gene/name :gene/content :line}` in document order.
  Errors are `{:line :text :error :parse/bad-head}` — a column-0 λ line that
  violates lambda_decl. Empty/nil text → no clauses, no errors."
  [text]
  (let [step (fn [acc [i line]]
               (let [cls               (classify-line line)
                     [action to-state] (get-in topology [(:state acc) cls])]
                 (-> (case action
                       :skip   acc
                       :open   (-> acc close-current
                                 (assoc :current
                                   {:name  (second (re-matches head-re line))
                                    :lines [line]
                                    :line  (inc i)}))
                       :append (update-in acc [:current :lines] conj line)
                       :close  (close-current acc)
                       :reject (update acc :errors conj
                                 {:line (inc i) :text line :error :parse/bad-head}))
                   (assoc :state to-state))))]
    (-> (reduce step
          {:state :outside :current nil :clauses [] :errors []}
          (map-indexed vector (str/split-lines (or text ""))))
      close-current
      (select-keys [:clauses :errors]))))

;; ---------------------------------------------------------------------------
;; Tree-hash — dedupe identity over NORMALIZED tokens.
;; ---------------------------------------------------------------------------

(defn normalize-tokens
  "Whitespace-normalized token stream: split on any whitespace run, drop
  blanks. Two renderings of the same clause (indentation, alignment, trailing
  space) normalize identically; any TOKEN change does not."
  [content]
  (into [] (remove str/blank?) (str/split (or content "") #"\s+")))

(defn tree-hash
  "SHA-256 hex over the normalized token stream — the gate-3 dedupe key.
  DERIVED, never stored (recompute ≻ trust a stale field)."
  [content]
  (let [d (MessageDigest/getInstance "SHA-256")]
    (->> (.digest d (.getBytes (str/join " " (normalize-tokens content)) "UTF-8"))
      (map #(format "%02x" %))
      (apply str))))

;; ---------------------------------------------------------------------------
;; Envelope — Malli gate over the parser's OUTPUT (gate-2).
;; ---------------------------------------------------------------------------

(def ^:private non-blank-string
  [:and :string [:fn {:error/message "must be non-blank"} (complement str/blank?)]])

(def envelope-keys
  "The on-disk envelope key set — what the veneer strips mutation params to
  (anything else is transport noise, and the CLOSED schema would reject it)."
  [:gene/name :gene/content :gene/type :gene/category :gene/sources])

(def schema
  "The on-disk gene envelope (mementum/genes/<name>.edn). CLOSED: unknown key
  ≈ typo → fail loud. Derived fields (:gene/id :gene/tree-hash :gene/scores)
  are NOT in the envelope — they are computed at load/query time.
  The cross-field rule is the STRUCTURAL floor for :lambda genes: content must
  open with a lambda_decl head (expression internals stay lenient)."
  [:and
   [:map {:closed true}
    [:gene/name     [:and :string [:re {:error/message "must be an EBNF identifier (letter, then letters/digits/_/-)"}
                                   identifier-re]]]
    [:gene/content  non-blank-string]
    [:gene/type     [:enum :lambda :prose]]
    [:gene/category [:enum :technique :pattern :constraint :guard :unknown]]
    [:gene/sources  [:and [:vector :keyword]
                     [:fn {:error/message "at least one provenance source required"} seq]]]]
   [:fn {:error/message ":lambda content must open with a lambda_decl head (λ name[(params)]. …)"}
    (fn [{:gene/keys [type content]}]
      (or (not= type :lambda)
          (some? (re-matches head-re (first (str/split-lines (or content "")))))))]])

(defn valid?
  "True iff `gene` satisfies the envelope schema."
  [gene]
  (m/validate schema gene))

(defn explain
  "Humanized error data for an invalid `gene`, or nil when valid."
  [gene]
  (some-> (m/explain schema gene) me/humanize))

(defn validate!
  "Return `gene` when valid; else throw ex-info carrying humanized errors —
  the gate-2 boundary, mirroring okf/validate!."
  [gene]
  (if (valid? gene)
    gene
    (throw (ex-info "Gene envelope invalid — rejected at boundary"
             {:gene/error :envelope
              :errors     (explain gene)
              :gene       gene}))))

(defn gene-id
  "Derived LLM-safe handle: `(keyword name)` — λ identifier, never uuid."
  [gene]
  (keyword (:gene/name gene)))

(defn clause->gene
  "Lift a segmenter clause into a full (unvalidated) envelope with provenance.
  Segmented clauses are :lambda by construction; `sources` is the provenance
  vector (e.g. [:genome/curator])."
  [clause sources]
  {:gene/name     (:gene/name clause)
   :gene/content  (:gene/content clause)
   :gene/type     :lambda
   :gene/category :unknown
   :gene/sources  (vec sources)})
