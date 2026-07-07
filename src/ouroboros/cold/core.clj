(ns ouroboros.cold.core
  "Cold-compiler pure core — no chart, no LLM, no IO. Deterministic and testable.

  These functions encode the robustness properties that escapement's substrate
  lets us make STRUCTURAL rather than prompt-obedient:

    * `assemble-system`  — bounded context: [base + compiled-λ + last raw turn].
                           The model never sees turns 1..N-1 verbatim; the
                           compiled λ carries them. Size is O(1) in turn count,
                           enforced HERE (one place), not by any caller.
    * `merge-ruled-out`  — the RULED_OUT ledger is a monotonic set-union.
                           Callers can only ADD; removal is not an operation
                           this function performs. Append-only by construction,
                           not by asking the LLM nicely.
    * `verify-compiled`  — the gate. A compiled λ is trusted only if its STATE
                           section is present AND its domain references are
                           GROUNDED in the raw turn it claims to compress
                           (resolve(reference) ≡ ¬∃session_context). Hollow
                           references — specifics the compiler invented that
                           never appeared in the turn — sink the verdict.

  The raw turn text is the ground truth every check runs against; in the live
  chart it comes from the escapement transcript / captured artifact."
  (:require
    [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Section parsing — the compiler emits three labelled λ blocks.
;; ---------------------------------------------------------------------------

(def ^:private section-order [:state :steer :ruled-out])

(def ^:private section-headers
  {"CONTINUE" :continue "ESSENCE" :continue "CARRY-FORWARD" :continue
   "STATE" :state "STEER" :steer
   "RULED_OUT" :ruled-out "RULED-OUT" :ruled-out})

(defn parse-sections
  "Split a compiled λ string into {:state :steer :ruled-out} by the STATE:/
  STEER:/RULED_OUT: markers. Missing sections are absent from the map. Body
  strings are trimmed; header lines are dropped."
  [compiled]
  (if (str/blank? (or compiled ""))
    {}
    (let [lines (str/split-lines compiled)]
      (loop [ls lines, cur nil, acc {}]
        (if-let [line (first ls)]
          (let [header (some (fn [[h k]]
                               (when (re-find (re-pattern (str "(?i)^\\s*" h "\\s*:")) line) k))
                         section-headers)]
            (if header
              (recur (rest ls) header acc)
              (recur (rest ls) cur
                (if cur (update acc cur (fnil conj []) line) acc))))
          (into {} (for [[k v] acc] [k (str/trim (str/join "\n" v))])))))))

;; ---------------------------------------------------------------------------
;; Reference extraction + grounding — the anti-hallucination check.
;;
;; Domain references live INSIDE the λ parens: `decided(write_through)` cites
;; `write_through`; `topic(caching)` cites `caching`. The function HEADS
;; (decided, topic, exploring…) are nucleus/λ vocabulary — not claims about the
;; turn. So we extract the ARGUMENTS and require those to be grounded.
;; ---------------------------------------------------------------------------

(def ^:private paren-group #"\(([^()]*)\)")

(defn- normalize
  "Lowercase and strip ALL non-alphanumerics so `write_through`, `write-through`,
  `write through`, and `invalidation.` all compare equal as substrings."
  [s]
  (-> (or s "") str/lower-case (str/replace #"[^a-z0-9]+" "")))

(def ^:private compiler-vocab
  "λ/nucleus filler the compiler emits as function heads or connectors — NOT
  claims about the turn, so excluded from grounding. Domain terms remain."
  #{"session" "state" "trajectory" "momentum" "drift" "next" "none" "ready"
    "phase" "recap" "decision" "decisions" "decided" "chose" "choose" "chosen"
    "select" "selected" "exploring" "comparing" "designing" "design" "awaiting"
    "avoids" "keeps" "keep" "implementation" "disproven" "hypotheses" "explicit"
    "defined" "converging" "converge" "arc" "steer" "guide" "revisit"})

(defn extract-refs
  "Domain references a compiled λ asserts: the tokens appearing inside its
  parentheses, split on separators, length ≥ 4, deduped, original casing kept.
  These are the specifics the compiler claims the turn contained."
  [compiled]
  (->> (re-seq paren-group (or compiled ""))
    (map second)
    ;; Split only on STRUCTURAL separators between distinct refs — NOT on
    ;; _ or - (intra-token: write_through stays whole; `normalize` handles the
    ;; write_through vs write-through grounding comparison).
    (mapcat #(str/split % #"[\s,|→/]+"))
    (map str/trim)
    (remove str/blank?)
    (filter #(>= (count %) 4))
    (remove #(contains? compiler-vocab (str/lower-case %)))
    distinct
    vec))

(defn- grounded?
  "Is `ref` present in the normalized raw turn text?"
  [normalized-raw ref]
  (str/includes? normalized-raw (normalize ref)))

(defn verify-compiled
  "The gate. Given a compiled λ and the RAW turn text it claims to compress,
  return a verdict map:

    {:ok?       bool     — sections present AND grounding ratio ≥ threshold
     :ratio     double   — |grounded| / |refs|  (1.0 when no refs asserted)
     :grounded  [...]    — references found in the raw turn
     :hollow    [...]    — references INVENTED (absent from the raw turn)
     :sections  {...}}   — parsed sections, for inspection

  `threshold` defaults to 0.5. A λ that cites specifics the turn never
  contained is rejected — that is the difference between a cache and a
  hallucination poured into every future turn."
  [{:keys [compiled raw threshold] :or {threshold 0.5}}]
  (let [secs      (parse-sections compiled)
        has-state (contains? secs :state)
        ;; Ground the STATE section ONLY. STATE asserts what happened (must be
        ;; grounded in the turn); STEER is forward-looking trajectory and
        ;; RULED_OUT references earlier turns — neither belongs to THIS raw turn.
        refs      (extract-refs (get secs :state ""))
        rawn      (normalize raw)
        grouped   (group-by (partial grounded? rawn) refs)
        grounded  (vec (get grouped true))
        hollow    (vec (get grouped false))
        n         (count refs)
        ratio     (if (zero? n) 1.0 (/ (double (count grounded)) n))]
    {:ok?      (and has-state (>= ratio threshold))
     :ratio    ratio
     :grounded grounded
     :hollow   hollow
     :sections secs}))

;; ---------------------------------------------------------------------------
;; Continuation coverage — the ESSENCE gate.
;;
;; The cold compiler's job is to preserve what a future turn NEEDS to continue,
;; not to emit pretty lambda. So the gate measures RECALL of the source turn's
;; salient specifics in the compiled brief — the opposite direction from the
;; fabrication check. `missing` is the dropped essence: the scary failure.
;; ---------------------------------------------------------------------------

(def ^:private stopwords
  "Generic conversational/filler words that are not continuation-critical."
  #{"user" "assistant" "one" "two" "small" "number" "added" "current"
    "about" "above" "after" "again" "against" "along" "already" "also" "another"
    "approach" "around" "based" "because" "been" "before" "being" "below"
    "between" "both" "brief" "bullet" "bullets" "call" "called" "cannot" "chose"
    "choose" "chosen" "compare" "comparison" "continue" "could" "decide" "decided"
    "decision" "decisions" "describe" "design" "designing" "different" "does"
    "doing" "done" "during" "each" "either" "else" "ensure" "ensuring" "every"
    "exactly" "explain" "first" "focus" "focused" "following" "from" "further"
    "genuine" "given" "goes" "going" "have" "having" "here" "here's" "however"
    "into" "just" "keep" "keeps" "known" "later" "like" "make" "makes" "many"
    "maybe" "might" "more" "most" "must" "need" "needs" "never" "next" "nothing"
    "only" "other" "over" "pick" "plainly" "please" "point" "points" "prefer"
    "propose" "quick" "rather" "really" "recap" "remember" "reply" "sentence"
    "sentences" "shall" "short" "should" "since" "some" "something" "specific"
    "state" "still" "such" "summarize" "summary" "take" "than" "that" "their"
    "them" "then" "there" "these" "they" "thing" "things" "this" "those"
    "three" "through" "thus" "together" "under" "until" "using" "very" "want"
    "were" "what" "when" "where" "which" "while" "will" "with" "within"
    "without" "would" "your"})

(defn salient-terms
  "Continuation-critical specifics in a piece of prose: hyphenated compounds
  (write-through, content-addressed), ALLCAPS acronyms (LRU), numbers, and
  content words of length ≥ 5 that are not generic filler. Deduped
  case-insensitively; original casing kept for reporting."
  [text]
  (let [toks (re-seq #"[A-Za-z][A-Za-z0-9]*(?:-[A-Za-z0-9]+)+|[A-Z]{2,}[A-Za-z0-9]*|\d+(?:\.\d+)?|[A-Za-z]{5,}"
               (or text ""))]
    (->> toks
      (remove #(contains? stopwords (str/lower-case %)))
      (reduce (fn [{:keys [seen out]} t]
                (let [k (str/lower-case t)]
                  (if (contains? seen k)
                    {:seen seen :out out}
                    {:seen (conj seen k) :out (conj out t)})))
        {:seen #{} :out []})
      :out)))

(defn assess-continuation
  "The ESSENCE gate. Does `compiled` retain the salient specifics of `source`
  (the raw turn it must let a future turn continue from)?

    {:ok?      bool     — content present AND coverage ≥ threshold
     :coverage double   — |retained| / |salient source terms|
     :covered  [...]    — salient terms carried into the brief
     :missing  [...]}   — DROPPED essence — details a future turn would lack

  `threshold` defaults to 0.6. This measures RECALL: a brief that forgot the
  decision fails even if everything it kept is accurate."
  [{:keys [compiled source threshold] :or {threshold 0.6}}]
  (let [salient   (salient-terms source)
        comp-norm (normalize compiled)
        grouped   (group-by #(str/includes? comp-norm (normalize %)) salient)
        covered   (vec (get grouped true))
        missing   (vec (get grouped false))
        n         (count salient)
        coverage  (if (zero? n) 1.0 (/ (double (count covered)) n))]
    {:ok?      (and (not (str/blank? (str/trim (or compiled ""))))
                 (>= coverage threshold))
     :coverage coverage
     :covered  covered
     :missing  missing}))

;; ---------------------------------------------------------------------------
;; Live gate — a STRUCTURAL tripwire, NOT an accuracy verifier.
;;
;; Compile fidelity is UNVERIFIABLE without an LLM-as-judge (there is no string
;; function from (source, compile) → faithful?), and the hallucination risk is
;; identical for prose and λ. So we do not pretend to verify accuracy — we PRIME
;; the compiler (nucleus preamble + λ prompts) and gate only on the crude failure
;; a deterministic check CAN catch: an empty / contentless compile.
;;
;; Coverage (`assess-continuation`) is still computed, but for OBSERVABILITY only
;; (logged, non-gating). Gating on lexical coverage would PENALIZE exactly the
;; dense λ we want — the compiler abstracts literal source tokens away, so a
;; faithful λ brief can score low coverage. Tripwire ≻ coverage-gate here.
;; ---------------------------------------------------------------------------

(defn tripwire
  "The LIVE gate. Given a compiled brief and the raw turn, return:

    {:ok?      bool     — compile is non-empty AND has usable continuation body
     :coverage double   — lexical recall of source specifics (OBSERVABILITY only)
     :covered  [...]    — salient source terms present in the brief
     :missing  [...]}   — salient source terms absent (a SIGNAL to watch, not a reject)

  `:ok?` is a structural check: the brief has a non-blank CONTINUE/STATE body, or
  (tolerating header-format drift) at least ~40 non-space chars of content. It is
  deliberately NOT an accuracy claim."
  [{:keys [compiled source]}]
  (let [txt      (str/trim (or compiled ""))
        secs     (parse-sections compiled)
        continue (str/trim (or (get secs :continue) (get secs :state) ""))
        usable?  (or (not (str/blank? continue))
                   (>= (count (str/replace txt #"\s+" "")) 40))
        cov      (assess-continuation {:compiled compiled :source source})]
    {:ok?      (and (not (str/blank? txt)) usable?)
     :coverage (:coverage cov)
     :covered  (:covered cov)
     :missing  (:missing cov)}))

;; ---------------------------------------------------------------------------
;; RULED_OUT ledger — monotonic by construction.
;; ---------------------------------------------------------------------------

(defn ruled-out-lines
  "Extract RULED_OUT body lines from a compiled λ as a vector of non-blank
  trimmed strings. Empty when the section is absent."
  [compiled]
  (->> (get (parse-sections compiled) :ruled-out "")
    str/split-lines
    (map str/trim)
    (remove str/blank?)
    vec))

(defn merge-ruled-out
  "Fold `new-lines` into the `existing` ledger as a de-duplicated union,
  preserving first-seen order. This is the ONLY mutation offered: the result
  is always a superset of `existing`. There is no remove — the compiler can
  propose additions, never deletions."
  [existing new-lines]
  (let [seen (volatile! (set existing))]
    (reduce (fn [acc line]
              (if (contains? @seen line)
                acc
                (do (vswap! seen conj line)
                    (conj acc line))))
      (vec existing)
      (remove str/blank? (map str/trim new-lines)))))

;; ---------------------------------------------------------------------------
;; Context assembly — assemble, don't accumulate.
;; ---------------------------------------------------------------------------

(defn assemble-system
  "Build the hot turn's system prompt from durable, bounded inputs:

    base       — the invariant persona/task system prompt
    compiled   — the current verified compiled λ (the session's whole memory)
    last-raw   — the single most-recent exchange, verbatim (the hot window)

  The result never contains turns 1..N-1 in full — only the λ that compresses
  them plus the last exchange. Length is bounded by (|base| + |compiled| +
  |last-raw|), independent of how many turns have elapsed. THIS is where the
  window lives: one function, not a caller's `take-last`."
  [base compiled last-raw]
  (str base
    (when-not (str/blank? (or compiled ""))
      (str "\n\n## Compiled session memory (λ) — this is your whole memory of earlier turns\n"
        compiled))
    (when-not (str/blank? (or last-raw ""))
      (str "\n\n## Most recent exchange (verbatim)\n" last-raw))))
