(ns ouroboros.mementum.okf
  "OKF — Open Knowledge Format. ONE format for every mementum file (state,
  knowledge, index, memory); the namespaced `:type` discriminates. A document
  is YAML frontmatter fenced by `---`, followed by a markdown body.

  This namespace is the pathom-agnostic, bb-native CORE gate. It enforces the
  S1 invariant `type ~ ^mementum/ | ¬valid → reject@boundary` (AGENTS.md) via
  Malli, so malformed frontmatter can never be persisted by construction.

  No pathom, no git, no network — pure parse/emit/validate over strings."
  (:require
    [clj-yaml.core :as yaml]
    [clojure.string :as str]
    [malli.core :as m]
    [malli.error :as me]))

;; ---------------------------------------------------------------------------
;; Schema — the OKF envelope. OPEN map: producer-defined keys pass through
;; (AGENTS.md: {status, category, related, depends-on} are producer-defined and
;; extensible). The HARD invariants are only `:type` (namespaced) + `:description`.
;; ---------------------------------------------------------------------------

(def types
  "Known mementum types. The set is extensible per-domain; the enforced
  invariant is the `mementum/` namespace prefix, NOT membership here."
  #{"mementum/state" "mementum/knowledge" "mementum/index" "mementum/memory"})

(def ^:private non-blank-string
  [:and :string [:fn {:error/message "must be non-blank"} (complement str/blank?)]])

(def schema
  "Malli schema for OKF frontmatter. `:type` must be a `mementum/`-namespaced
  string; `:description` (the disclosure gate) is required and non-blank."
  [:map
   [:type [:and :string [:re {:error/message "type must be namespaced ^mementum/"}
                          #"^mementum/.+"]]]
   [:description non-blank-string]
   [:title      {:optional true} :string]
   [:tags       {:optional true} [:sequential :string]]
   [:status     {:optional true} :string]
   [:resource   {:optional true} :string]
   [:category   {:optional true} :string]
   [:related    {:optional true} [:sequential :string]]
   [:depends-on {:optional true} [:sequential :string]]])

(defn valid?
  "True iff `frontmatter` satisfies the OKF schema."
  [frontmatter]
  (m/validate schema frontmatter))

(defn explain
  "Human-readable error map for invalid `frontmatter`, or nil when valid."
  [frontmatter]
  (some-> (m/explain schema frontmatter) me/humanize))

(defn validate!
  "Return `frontmatter` when valid; else throw an ex-info carrying the humanized
  errors. This is the boundary gate — nothing malformed persists past it."
  [frontmatter]
  (if (valid? frontmatter)
    frontmatter
    (throw (ex-info "OKF frontmatter invalid — rejected at boundary"
             {:mementum/error :okf/invalid
              :errors         (explain frontmatter)
              :frontmatter    frontmatter}))))

;; ---------------------------------------------------------------------------
;; Parse / emit — document string <-> {:frontmatter <map> :body <string>}.
;; clj-yaml is a Babashka builtin; keys are keywordized, values stay scalar/seq.
;; ---------------------------------------------------------------------------

(def ^:private fence "---")

(defn parse
  "Split an OKF document string into `{:frontmatter <map> :body <string>}`.
  A document with no leading `---` fence yields `{:frontmatter {} :body doc}`.
  Frontmatter is a plain (unordered-comparable) map with keywordized keys."
  [doc]
  (let [lines (str/split-lines (or doc ""))]
    (if (= (first lines) fence)
      (let [rst     (rest lines)
            end-idx (first (keep-indexed (fn [i l] (when (= l fence) i)) rst))]
        (if end-idx
          {:frontmatter (into {} (yaml/parse-string (str/join "\n" (take end-idx rst))))
           :body        (str/triml (str/join "\n" (drop (inc end-idx) rst)))}
          {:frontmatter {} :body doc}))
      {:frontmatter {} :body doc})))

(defn emit
  "Render `{:frontmatter <map> :body <string>}` back to an OKF document string:
  `---\\n<yaml>---\\n\\n<body>\\n`."
  [{:keys [frontmatter body]}]
  (str fence "\n"
       (yaml/generate-string frontmatter :dumper-options {:flow-style :block})
       fence "\n"
       (when-not (str/blank? body) (str "\n" (str/trim body) "\n"))))

(defn parse-valid!
  "Parse `doc`, validate its frontmatter, and return `{:frontmatter :body}`.
  Throws (via `validate!`) if the frontmatter is malformed."
  [doc]
  (let [{:keys [frontmatter] :as parsed} (parse doc)]
    (validate! frontmatter)
    parsed))
