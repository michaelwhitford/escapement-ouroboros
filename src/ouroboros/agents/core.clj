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
    [escapement.prompts :as eprompts]
    [malli.core :as m]
    [malli.error :as me]
    [ouroboros.mementum.okf :as okf]
    [ouroboros.signals.core :as signals.core]))

;; ---------------------------------------------------------------------------
;; Vocabulary
;; ---------------------------------------------------------------------------

(def agent-type
  "The OKF `type` every agent genome must declare."
  "ouroboros/agent")

(def kinds
  "The working kind list (agent-model spec, build order left→right): ALL
  BUILT — chat·proposer·judge·scorer·builder·author·editor·analyst·generator,
  plus comparator (pairwise tournament selector, the generator's selection
  operator), plus player (design/game-arena.md — a game-arena decision agent
  whose forced verdict schema is DYNAMIC, supplied per decision by the game
  engine's :game/action-schema rather than this table; the honest 11th kind:
  a real semantic difference in where the schema comes from, not a new role
  on an old shape). A kind is a preset over a structural signature — new role
  with same tools+topology ⇒ new GENOME, not a new kind."
  #{:chat :proposer :judge :scorer :builder :author :editor :analyst :generator :comparator
    :player})

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
    scorer → MEASURES — consumed as a measurement (rank · accumulate · fitness)

  :player is DELIBERATELY absent — its schema is per-DECISION (the game
  engine's :game/action-schema, legality-narrowed each turn); the arena's
  decide seam passes it explicitly (ouroboros.game.llm). A static row here
  would be wrong twice: wrong game, wrong turn."
  {:judge  [:map
            [:status [:enum :pass :fail]]
            [:notes :string]]
   :scorer [:map
            [:score [:int {:min 1 :max 10}]]
            [:notes :string]]
   ;; comparator → CHOOSES between two seated candidates (A vs B); the
   ;; tournament runner seats each pair BOTH ways — position bias cancels.
   :comparator [:map
                [:winner [:enum :a :b]]
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
   [:title   {:optional true} :string]
   [:tools   {:optional true} [:sequential :string]]
   [:modules {:optional true} [:sequential :string]]
   [:signals {:optional true} [:sequential :string]]
   ;; tags — ROLE-AS-TAG (design/scheduled-maintenance): OPEN vocabulary by
   ;; design (roles EMERGE — curator, assessor, verifier — no schema change
   ;; per new role; kind stays CLOSED). The loader genuinely CONSUMES tags:
   ;; the schedule selects by tag, the roster report shows them. Discipline:
   ;; tags select WHO runs — NEVER what-may (capability stays in grants).
   [:tags    {:optional true} [:sequential :string]]
   [:model   {:optional true} non-blank-string]])

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
     :tools-explicit? :modules :model :body}

  · :body is the genome body VERBATIM (frontmatter stripped — the agent never
    sees it); the loader ASSEMBLES it into the final :prompt (see `assemble`)
  · :tools — absent `tools:` key ⇒ the read-only FLOOR; explicit list (even [])
    ⇒ exactly that list, validated ⊆ `registry-tools` (the ceiling)
  · :modules — prompt-module grants, validated ⊆ `registry-modules` (the
    module CEILING — 4th use of the grant mechanism); absent ⇒ [] (no floor:
    a module is always an explicit grant)
  · :signals — emit-type grants (design/signals.md — the 5th grant surface),
    validated ⊆ `registry-signals` (the signal-type registry ≡ the emit
    ceiling); absent ⇒ [] (emit NOTHING — no floor). A non-empty grant
    auto-adds :signal/emit to :tools (ONE grant surface: the TYPE grant is
    the capability; a typeless emit tool would be inert)
  · :model defaults to :local
  Throws ex-info {:agent :tier :source :errors [...]} aggregating ALL problems."
  [{:keys [slug tier source doc registry-tools read-only-floor registry-modules
           registry-signals]}]
  (let [{:keys [frontmatter body]} (okf/parse doc)
        schema-errors (some-> (m/explain schema frontmatter) me/humanize)
        explicit?     (contains? frontmatter :tools)
        signals       (mapv ->kw (:signals frontmatter))
        grant         (as-> (if explicit?
                               (mapv ->kw (:tools frontmatter))
                               (vec (sort read-only-floor))) g
                        (if (and (seq signals) (not-any? #{:signal/emit} g))
                          (conj g :signal/emit)
                          g))
        kind          (->kw (:kind frontmatter))
        modules       (mapv ->kw (:modules frontmatter))
        unknown-tools (vec (remove (set registry-tools) grant))
        unknown-mods  (vec (remove (set registry-modules) modules))
        unknown-sigs  (vec (remove (set registry-signals) signals))
        errors        (cond-> []
                        schema-errors
                        (conj {:frontmatter schema-errors})
                        (and (not schema-errors) (not (contains? kinds kind)))
                        (conj {:kind {:got kind :allowed (vec (sort kinds))}})
                        (seq unknown-tools)
                        (conj {:tools {:unknown unknown-tools
                                       :registry (vec (sort registry-tools))}})
                        (seq unknown-mods)
                        (conj {:modules {:unknown unknown-mods
                                         :registry (vec (sort registry-modules))}})
                        (seq unknown-sigs)
                        (conj {:signals {:unknown unknown-sigs
                                         :registry (vec (sort registry-signals))}})
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
             :modules         modules
             :signals         signals
             :tags            (mapv ->kw (:tags frontmatter))
             :model           (->kw (:model frontmatter "local"))
             :body            body}
      (:title frontmatter) (assoc :title (:title frontmatter)))))

;; ---------------------------------------------------------------------------
;; Assembly — preamble ⊕ modules ⊕ body (design/prompt-assembly.md)
;; ---------------------------------------------------------------------------

(defn strip-preamble
  "Remove a leading copy of `preamble` (plus following blank lines) from
  `body`. Makes `assemble` IDEMPOTENT over genomes that still embed the
  preamble inline — the assembler's exactly-once invariant holds either way."
  [preamble body]
  (let [body (str body)]
    (if (str/starts-with? body preamble)
      (str/triml (subs body (count preamble)))
      body)))

(defn assemble
  "The ONE prompt-composition fn (production loader · experiment suites · the
  future GA — Anima rule: all consumers share this exact pipeline):

    preamble ⊕ modules ⊕ body   (joined by blank lines)

  · preamble — ALWAYS, exactly once, FIRST (embedded copies stripped)
  · modules  — RESOLVED module texts, in grant order (author-controlled)
  · body     — the genome's λ program; prose I/O gate lines stay LAST in it
  Layer order is LOAD-BEARING (nucleus LAMBDA-COMPILER.md, logprob-verified:
  process launch → program → I/O configuration).

  `subs` feeds escapement.prompts/render — {{VAR}} late binding, fail-loud on
  any unresolved token (a prompt must never ship a literal {{...}}). Pure:
  strings in, string out."
  [{:keys [preamble modules body subs] :or {modules [] subs {}}}]
  (-> (str/join "\n\n"
        (remove str/blank?
          (concat [(str/trimr (str preamble))]
                  (map (comp str/trimr str) modules)
                  [(str/trimr (strip-preamble preamble body))])))
      (eprompts/render subs)))

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
  (let [floor    (set read-only-floor)
        reserved (signals.core/reserved-types)]
    (str "Compiled " (count roster) " agent" (when (not= 1 (count roster)) "s") ":\n"
      (str/join "\n"
        (for [[id agent] (sort-by key roster)
              :let [esc      (vec (remove floor (:tools agent)))
                    res-sigs (vec (filter reserved (:signals agent)))]]
          (str "  " (name id)
               "  " (name (:tier agent))
               (when (:overrides agent)
                 (str " (overrides " (name (:overrides agent)) ")"))
               "  kind:" (name (:kind agent))
               (when (seq (:tags agent))
                 (str "  tags:" (pr-str (:tags agent))))
               "  tools:" (pr-str (:tools agent))
               (when (seq (:modules agent))
                 (str "  modules:" (pr-str (:modules agent))))
               (when (seq (:signals agent))
                 (str "  signals:" (pr-str (:signals agent))))
               (when (seq esc)
                 (str "  ESCALATION:" (pr-str esc)))
               (when (seq res-sigs)
                 (str "  RESERVED-SIGNALS:" (pr-str res-sigs)))))))))
