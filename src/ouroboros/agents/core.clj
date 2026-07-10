(ns ouroboros.agents.core
  "Pure kernel of the genome compiler (house convention: <engine>/core.clj is
  the pure, deterministic part; `ouroboros.agents` is the impure edge that
  scans sources).

  An Ouroboros agent is an OKF genome file with a HARD frontmatter/body
  boundary (mementum/knowledge/design/agent-model.md):

    frontmatter : body  ≡  loader : agent  ≡  metadata : context

  Frontmatter is agent-INVISIBLE wiring — {type, description, kind, tools?,
  model?} (+ optional title) — consumed HERE and stripped; the body is the
  agent's WHOLE λ system prompt, handed to the LLM untouched. Anything the
  agent must UNDERSTAND lives in the body (e.g. verdict SEMANTICS); anything
  the LOADER consumes lives in frontmatter (e.g. verdict SCHEMA → kind).

  KIND = SHAPE (topology + gate + verdict-behavior). TOOLS = CAPABILITY.
  Orthogonal axes: a kind never assigns tools; a grant never changes topology.

  Validation is FAIL-LOUD: an invalid genome throws with ALL problems
  aggregated — never half-run."
  (:require
    [clojure.string :as str]
    [malli.core :as m]
    [malli.error :as me]
    [ouroboros.mementum.okf :as okf]))

;; ---------------------------------------------------------------------------
;; Vocabulary
;; ---------------------------------------------------------------------------

(def agent-type
  "The OKF `type` every agent genome must declare."
  "ouroboros/agent")

(def kinds
  "The working kind list (agent-model spec, build order left→right):
  chat·proposer BUILT ; judge·scorer NEXT ; builder·author·editor ○ ;
  analyst·generator ◇ (blocked on unbuilt tools). A kind is a preset over a
  structural signature — new role with same tools+topology ⇒ new GENOME, not
  a new kind."
  #{:chat :proposer :judge :scorer :builder :author :editor :analyst :generator})

(def verdict-schemas
  "SCHEMA lives with the KIND (uniform per kind); SEMANTICS (when pass/fail,
  when 1 vs 10, what notes) live in the genome BODY — the agent must REASON
  about semantics, so they cannot hide in loader-only wiring. Frontmatter
  carries NO verdict field by design (agent-model spec §Judge & Scorer).

  A kind absent here idles free-text (escapement: nil :verdict-schema ⇒ no
  forced wrap-up). Keyword enums are safe: escapement decodes the model's
  JSON strings through Malli's json-transformer before validating
  (\"pass\" → :pass).

    judge  → GATES  — consumed by a :cond transition (pass→continue, fail→loop)
    scorer → MEASURES — consumed as a measurement (rank · accumulate · fitness)"
  {:judge  [:map
            [:status [:enum :pass :fail]]
            [:notes :string]]
   :scorer [:map
            [:score [:int {:min 1 :max 10}]]
            [:notes :string]]})

(defn verdict-schema
  "The forced-submit_verdict Malli schema for `kind`, or nil (free-text idle)."
  [kind]
  (get verdict-schemas kind))

(def ^:private non-blank-string
  [:and :string [:fn {:error/message "must be non-blank"} (complement str/blank?)]])

(def schema
  "Malli schema for genome frontmatter — the RAW parsed YAML (string values).
  CLOSED map, unlike mementum's open OKF envelope: genome frontmatter is
  WIRING, so an unknown key is almost certainly a typo → fail loud."
  [:map {:closed true}
   [:type [:= {:error/message (str "type must be " agent-type)} agent-type]]
   [:description non-blank-string]
   [:kind non-blank-string]
   [:title  {:optional true} :string]
   [:tools  {:optional true} [:sequential :string]]
   [:model  {:optional true} non-blank-string]])

;; ---------------------------------------------------------------------------
;; Normalization
;; ---------------------------------------------------------------------------

(defn- ->kw
  "YAML gives strings; wiring wants keywords. Accepts \"mementum/context\",
  \":local\" (a copy-pasted keyword literal), or an already-keyword."
  [x]
  (cond
    (keyword? x) x
    (string? x)  (keyword (str/replace-first x #"^:" ""))
    :else        x))

;; ---------------------------------------------------------------------------
;; Compile one genome
;; ---------------------------------------------------------------------------

(defn parse-genome
  "Parse + validate ONE genome document string into a compiled agent map:

    {:id :slug :tier :source :kind :description (:title) :tools
     :tools-explicit? :model :prompt}

  · :prompt is the body VERBATIM (frontmatter stripped — the agent never sees it)
  · :tools — absent `tools:` key ⇒ the read-only FLOOR; explicit list (even [])
    ⇒ exactly that list, validated ⊆ `registry-tools` (the ceiling)
  · :model defaults to :local
  Throws ex-info {:agent :tier :source :errors [...]} aggregating ALL problems."
  [{:keys [slug tier source doc registry-tools read-only-floor]}]
  (let [{:keys [frontmatter body]} (okf/parse doc)
        schema-errors (some-> (m/explain schema frontmatter) me/humanize)
        explicit?     (contains? frontmatter :tools)
        grant         (if explicit?
                        (mapv ->kw (:tools frontmatter))
                        (vec (sort read-only-floor)))
        kind          (->kw (:kind frontmatter))
        unknown-tools (vec (remove (set registry-tools) grant))
        errors        (cond-> []
                        schema-errors
                        (conj {:frontmatter schema-errors})
                        (and (not schema-errors) (not (contains? kinds kind)))
                        (conj {:kind {:got kind :allowed (vec (sort kinds))}})
                        (seq unknown-tools)
                        (conj {:tools {:unknown unknown-tools
                                       :registry (vec (sort registry-tools))}})
                        (str/blank? body)
                        (conj {:body "genome body (the λ system prompt) is blank"}))]
    (when (seq errors)
      (throw (ex-info (str "invalid agent genome: " slug " (" (name tier) ", " source ")")
               {:agent (keyword slug) :tier tier :source source :errors errors})))
    (cond-> {:id              (keyword slug)
             :slug            slug
             :tier            tier
             :source          source
             :kind            kind
             :description     (:description frontmatter)
             :tools           grant
             :tools-explicit? explicit?
             :model           (->kw (:model frontmatter "local"))
             :prompt          body}
      (:title frontmatter) (assoc :title (:title frontmatter)))))

;; ---------------------------------------------------------------------------
;; Merge — the fold over precedence-ordered tiers
;; ---------------------------------------------------------------------------

(defn merge-roster
  "Fold tier rosters ({id → agent}) in precedence order (base first, custom
  last). Later tiers WIN BY SLUG, REPLACE-WHOLE (the file on disk IS the
  agent — no field merge; `extends:` is deferred). A winner that shadowed an
  earlier tier records it under :overrides — provenance stays VISIBLE."
  [rosters]
  (reduce
    (fn [acc roster]
      (reduce-kv
        (fn [acc id agent]
          (assoc acc id
            (if-let [prev (get acc id)]
              (assoc agent :overrides (:tier prev))
              agent)))
        acc roster))
    {} rosters))

;; ---------------------------------------------------------------------------
;; Report — override + escalation must be VISIBLE (the human's audit surface)
;; ---------------------------------------------------------------------------

(defn report
  "Human-readable roster report: one line per agent with tier PROVENANCE
  (incl. overrides) and the tool GRANT, flagging escalations beyond the
  read-only floor."
  [roster read-only-floor]
  (let [floor (set read-only-floor)]
    (str "Compiled " (count roster) " agent" (when (not= 1 (count roster)) "s") ":\n"
      (str/join "\n"
        (for [[id agent] (sort-by key roster)
              :let [esc (vec (remove floor (:tools agent)))]]
          (str "  " (name id)
               "  " (name (:tier agent))
               (when (:overrides agent)
                 (str " (overrides " (name (:overrides agent)) ")"))
               "  kind:" (name (:kind agent))
               "  tools:" (pr-str (:tools agent))
               (when (seq esc)
                 (str "  ESCALATION:" (pr-str esc)))))))))
