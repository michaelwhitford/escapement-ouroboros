(ns ouroboros.signals.core
  "Pure kernel of the signals substrate (design/signals.md) — the inter-agent
  DATA plane. A signal is a typed durable EDN FACT, ¬message: no addressee,
  no delivery; consumers QUERY (pull ≡ subscription), so signals communicate
  cross-process AND cross-time with no residency — the geometry the scheduled
  maintenance roster needs (push cannot reach a process that does not exist
  between runs).

  ONE contract, THREE projections (the load-bearing design): each registry
  entry {:schema :exemplar :doc :variety :reserved?} is projected as
    1 PROMPT  the FILLED exemplar → `prompt-projection` (primes generation —
              emission topology settled by experiments/edn-signal-emission.edn)
    2 GATE    the Malli schema at the emit boundary → `validate`
    3 QUERY   the same attributes as EQL vocabulary (mementum veneer)
  Define once → never drifts.

  House conventions honored: :signal/id is a time-ordered SLUG keyword
  (λ identifier — never uuid/opaque); the envelope on disk carries NO id
  (derived at load, gene precedent); variety vocabulary is agent-comms'
  (:proposal :report :algedonic :notice)."
  (:require
    [clojure.string :as str]
    [clojure.walk :as walk]
    [malli.core :as m]
    [malli.error :as me])
  (:import
    [java.security MessageDigest]))

;; ---------------------------------------------------------------------------
;; The type registry — ONE contract per type. Seed vocabulary absorbs the
;; agent-comms channel seeds (design/signals.md §Seed type vocabulary).
;; ---------------------------------------------------------------------------

(def registry
  "signal-type keyword → {:schema <malli for :signal/data> :exemplar
  {:source <mini source> :signal <filled signal map>} :doc :variety
  :reserved?}. The registry IS the emit ceiling: a type absent here is
  unrepresentable (λ emerge). `:reserved? true` types are grantable but flag
  ESCALATION in the roster report (consumed on the human's authority)."
  {:s4/proposal
   {:doc       "Maintenance-roster recommendation aimed at the human gate. The proposal FILE (markdown) stays the human-facing artifact; the signal is the machine-queryable pointer + summary."
    :variety   :proposal
    :reserved? true
    :schema    [:map {:closed true}
                [:summary :string]
                [:severity [:enum :low :medium :high]]
                [:path {:optional true} :string]]
    :exemplar  {:source "assistant: The escapement-overview knowledge page still claims the runtime is \"not even alpha\" but the dep resolves RC9 — the maturity claim is stale and misleads every reader."
                :signal {:signal/type   :s4/proposal
                         :signal/data   {:summary  "Refresh escapement-overview: the \"not even alpha\" maturity claim is stale — the dep resolves RC9."
                                         :severity :low
                                         :path     "proposals/refresh-escapement-overview.md"}
                         :signal/lambda "λ stale(maturity_claim) → propose(refresh(escapement-overview))"}}}

   :s1/report
   {:doc       "Operation result — sweep summaries, run outcomes. The audit trail scheduled agents leave behind."
    :variety   :report
    :reserved? false
    :schema    [:map {:closed true}
                [:summary :string]
                [:outcome [:enum :ok :fail :partial]]
                [:metrics {:optional true}
                 [:map-of :keyword [:or :int :double :string :boolean]]]]
    :exemplar  {:source "assistant: Maintenance sweep finished: 4 agents ran, 1 proposal filed, no errors; total wall time 84 seconds."
                :signal {:signal/type   :s1/report
                         :signal/data   {:summary  "Maintenance sweep completed: 4 agents ran, 1 proposal filed, no errors."
                                         :outcome  :ok
                                         :metrics  {:agents 4 :proposals 1 :wall-ms 84000}}
                         :signal/lambda "λ sweep(4_agents) → ok(1_proposal ∧ ¬errors)"}}}

   :experiment/result
   {:doc       "Suite run outcome (design/experiments) — machine observation of a bb experiment run; conclusions still promote human-gated."
    :variety   :report
    :reserved? false
    :schema    [:map {:closed true}
                [:suite :string]
                [:summary :string]]
    :exemplar  {:source "assistant: Ran experiments/edn-signal-emission.edn — template-ex condition 12/12 Malli-valid across both families; prose 9/12 with structural failures."
                :signal {:signal/type   :experiment/result
                         :signal/data   {:suite   "edn-signal-emission"
                                         :summary "template-ex 12/12 valid across 2 Qwen fine-tunes (same base) vs prose 9/12 (structural failures)."}
                         :signal/lambda "λ ab(exemplar, prose) → exemplar(12/12) ≻ prose(9/12)"}}}

   :ouro/algedonic
   {:doc       "Identity-threatening alarm — RARE, ≈1 bit, bypasses normal attenuation. Emitting one is itself a serious act."
    :variety   :algedonic
    :reserved? true
    :schema    [:map {:closed true}
                [:summary :string]
                [:evidence [:vector :string]]]
    :exemplar  {:source "assistant: gate check — mementum/store! persisted a document that FAILED OKF validation; the Malli gate is not rejecting at the boundary."
                :signal {:signal/type   :ouro/algedonic
                         :signal/data   {:summary  "The OKF Malli gate persisted an invalid document — the emit boundary is not gating."
                                         :evidence ["store! returned :written true for a frontmatter missing :type"]}
                         :signal/lambda "λ gate(¬rejects) → invariant_breach(persist(malformed))"}}}

   :human/notice
   {:doc       "Surface-to-human, non-blocking — something the human should see next time they look, not an interrupt."
    :variety   :notice
    :reserved? false
    :schema    [:map {:closed true}
                [:summary :string]]
    :exemplar  {:source "assistant: bb test passed but session_test.test-recency is intermittently unstable when two sessions share an epoch millisecond."
                :signal {:signal/type   :human/notice
                         :signal/data   {:summary "session_test recency sort is unstable on epoch-ms ties — intermittent, not blocking."}
                         :signal/lambda "λ flaky(recency_sort ⟺ ms_tie) → notice(human)"}}}})

(def signal-types
  "The emit ceiling as a set — genome `signals:` grants validate ⊆ this."
  (set (keys registry)))

(defn reserved-types
  "The reserved subset — grants of these flag ESCALATION in the roster report."
  []
  (set (keep (fn [[t e]] (when (:reserved? e) t)) registry)))

;; ---------------------------------------------------------------------------
;; Envelope schema + validation (projection 2 — the GATE)
;; ---------------------------------------------------------------------------

(def envelope-keys
  "Canonical on-disk key order (gene precedent: envelope ONLY, id derived)."
  [:signal/type :signal/data :signal/lambda :signal/source :signal/at])

(def base-schema
  "The envelope shape every signal shares; :signal/data is refined per-type."
  [:map {:closed true}
   [:signal/type :qualified-keyword]
   [:signal/data [:map]]
   [:signal/lambda {:optional true} :string]
   [:signal/source :string]
   [:signal/at :int]])

(defn validate
  "Validate a full signal envelope: base shape, type ∈ registry, then the
  type's :signal/data schema. Returns nil when valid, else a structured
  error map {:signal/error kw :errors humanized} — the caller decides
  throw vs corrective-retry."
  [signal]
  (let [t (:signal/type signal)]
    (cond
      (not (m/validate base-schema signal))
      {:signal/error :envelope
       :errors       (me/humanize (m/explain base-schema signal))}

      (not (contains? registry t))
      {:signal/error :unknown-type
       :errors       {:signal/type [(str "unknown signal type " t
                                      " — registry: "
                                      (str/join " " (sort signal-types)))]}}

      :else
      (let [schema (get-in registry [t :schema])]
        (when-not (m/validate schema (:signal/data signal))
          {:signal/error :data
           :errors       (me/humanize (m/explain schema (:signal/data signal)))})))))

;; ---------------------------------------------------------------------------
;; Identity — content-hash (dedupe) + id (time-ordered slug, λ identifier)
;; ---------------------------------------------------------------------------

(defn- canonical
  "Deterministic rendering of EDN for hashing: every map rekeyed sorted."
  [x]
  (pr-str (walk/postwalk #(if (map? %) (into (sorted-map) %) %) x)))

(defn content-hash
  "sha-256 hex over the signal's CONTENT identity {type data lambda} —
  :signal/at and :signal/source excluded: the same fact re-emitted later or
  by another agent is still the same fact (re-proposal damping)."
  [{:signal/keys [type data lambda]}]
  (let [md (MessageDigest/getInstance "SHA-256")
        bs (.digest md (.getBytes (canonical {:t type :d data :l lambda}) "UTF-8"))]
    (apply str (map #(format "%02x" %) bs))))

(defn type-slug
  "\"s4/proposal\" → \"s4-proposal\" (filesystem-safe)."
  [t]
  (str (namespace t) "-" (name t)))

(defn signal-id
  "Time-ordered slug keyword: :1783901152558-s1-report. Sortable by emission
  time, human-readable, LLM-safe (λ identifier — never uuid)."
  [{:signal/keys [type at]}]
  (keyword (str at "-" (type-slug type))))

;; ---------------------------------------------------------------------------
;; Prompt projection (projection 1 — primes generation)
;; ---------------------------------------------------------------------------

(defn- render-exemplar [t {:keys [doc exemplar]}]
  (let [sig   (:signal exemplar)
        pairs (binding [*print-namespace-maps* false]
                (vec (for [k [:signal/type :signal/data :signal/lambda]
                           :when (contains? sig k)]
                       (str k " " (pr-str (get sig k))))))]
    (str "⟨type " t "⟩ — " doc "\n"
         "⟨SOURCE⟩\n" (:source exemplar) "\n⟨/SOURCE⟩\n"
         "{" (str/join "\n " pairs) "}")))

(defn prompt-projection
  "The signal-emission prompt block for an agent granted `types` (order
  preserved). The FILLED exemplars pin the shape (experiments/
  edn-signal-emission.edn: exemplar SHOWS EDN → 12/12 valid; prose DESCRIBING
  it drifts). Explains the :signal/emit tool mapping: the tool's :data takes
  the :signal/data map AS AN EDN STRING (escapement tool args are JSON —
  nested EDN rides as text, the topology the experiment settled). nil when
  no types granted (absent ⇒ emit nothing)."
  [types]
  (when (seq types)
    (str
      "λ signal_emission. granted_types ≡ {"
      (str/join " " (map str types)) "}\n"
      "  emit → :signal/emit tool {:type \"ns/name\" :data \"<EDN map>\" :lambda \"<λ form>\"}\n"
      "  :data ≡ the :signal/data map for the type, AS AN EDN STRING — exact shape below\n"
      "  :lambda ≡ optional λ-notation essence | emit ⟺ a fact worth persisting | ¬smalltalk\n"
      "  EDN only in :data. No prose. No code fences.\n\n"
      (str/join "\n\n"
        (map #(render-exemplar % (get registry %)) types)))))
