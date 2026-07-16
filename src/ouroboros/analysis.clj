(ns ouroboros.analysis
  "clj-kondo-backed code analysis — the ANALYST kind's lens (agent-model spec:
  analyst · shot · gate ≡ INFORMS · output ≡ map/graph/report).

  Pure digest fns: kondo result maps → BOUNDED text (λ context: sip → dribble;
  raw analysis EDN for this repo is thousands of entries — the LLM gets a
  capped, formatted projection). Deterministic — tested with canned maps.

  ONE impure edge: `kondo-run!` via the REGISTRY-PINNED pod (🎯 human decision:
  pin ≻ PATH binary — hermetic, machine-portable). clj-kondo CANNOT load as a
  bb library (empirical, 2026-07-15: its vendored tools.reader deftype
  implements java.io.Closeable — SCI rejects interface impls; the pod IS the
  bb-library mechanism and mirrors the clj-kondo.core/run! API 1:1). The pod
  loads LAZILY on first use — genome compile / tool-names / bb test never
  spawn it (first live use downloads it once).

  Coordinates (file:line) are FINE here despite λ point: analyst output is an
  EPHEMERAL session report, re-derived live each run — `line ≡ derived`, never
  stored."
  (:require
    [clojure.string :as str]))

(def pod-version
  "The pinned clj-kondo pod (registry: clj-kondo/clj-kondo)."
  "2024.08.01")

(def ^:private kondo!
  ;; delay ⇒ the pod process spawns on FIRST analysis, never at require/compile.
  (delay
    ((requiring-resolve 'babashka.pods/load-pod) 'clj-kondo/clj-kondo pod-version)
    (requiring-resolve 'pod.borkdude.clj-kondo/run!)))

(defn kondo-run!
  "Run clj-kondo (pod) over `paths` with analysis on (incl. arglists).
  Returns the standard {:findings [...] :analysis {...}} result map."
  [paths]
  (@kondo! {:lint (vec paths)
            :config {:analysis {:arglists true}}}))

;; ---------------------------------------------------------------------------
;; Bounding — every digest is capped; the tail names what was elided
;; ---------------------------------------------------------------------------

(def max-lines
  "Per-digest line cap — the analyst drills with narrower ops, never pages."
  60)

(defn- bounded [lines]
  (let [lines (vec lines)]
    (if (> (count lines) max-lines)
      (conj (subvec lines 0 max-lines)
        (str "… " (- (count lines) max-lines) " more (narrow the path/query)"))
      lines)))

(defn- render [header lines empty-msg]
  (str header "\n"
    (if (seq lines) (str/join "\n" (bounded lines)) empty-msg)))

;; ---------------------------------------------------------------------------
;; Digests — pure projections of the kondo result map
;; ---------------------------------------------------------------------------

(defn- finding-line [{:keys [filename row col level type message]}]
  (str (name level) " " filename ":" row ":" col " [" (name type) "] " message))

(defn lint-digest
  "Findings, worst-first (errors before warnings), with a level census."
  [{:keys [findings]}]
  (render (str "FINDINGS (" (count findings) ")"
            (when (seq findings) (str " " (pr-str (frequencies (map :level findings))))) ":")
    (map finding-line (sort-by #(if (= :error (:level %)) 0 1) findings))
    "(clean — no findings)"))

(defn ns-graph-digest
  "Namespace dependency edges: one line per FROM ns, its deps sorted."
  [{:keys [analysis]}]
  (let [edges (group-by :from (:namespace-usages analysis))]
    (render (str "NAMESPACE DEPS (" (count edges) " namespaces):")
      (for [[from usages] (sort-by (comp str key) edges)]
        (str from " → " (str/join " " (sort (map str (distinct (map :to usages)))))))
      "(no namespace dependencies found)")))

(defn var-defs-digest
  "Var definitions, optionally filtered to `ns-str`: name, arglists, privacy,
  definition site."
  [{:keys [analysis]} ns-str]
  (let [target (when-not (str/blank? (str ns-str)) (symbol ns-str))
        defs   (cond->> (:var-definitions analysis)
                 target (filter #(= target (:ns %))))]
    (render (str "VAR DEFINITIONS" (when target (str " in " target)) " (" (count defs) "):")
      (for [{:keys [ns name arglist-strs private filename row]}
            (sort-by (juxt (comp str :ns) (comp str :name)) defs)]
        (str ns "/" name
          (when (seq arglist-strs) (str "  " (str/join " " arglist-strs)))
          (when private "  [private]")
          "  — " filename ":" row))
      "(none — check the ns name against ns-graph)")))

(defn usages-digest
  "Call sites of `symbol-str` (bare name matches any ns; qualified ns/name
  pins the target ns)."
  [{:keys [analysis]} symbol-str]
  (let [sym       (symbol symbol-str)
        want-ns   (some-> (namespace sym) symbol)
        want-name (symbol (name sym))
        usages    (filter (fn [{:keys [to name]}]
                            (and (= want-name name)
                                 (or (nil? want-ns) (= want-ns to))))
                    (:var-usages analysis))]
    (render (str "USAGES of " symbol-str " (" (count usages) "):")
      (for [{:keys [from to filename row]} (sort-by (juxt :filename :row) usages)]
        (str filename ":" row "  " to "/" want-name "  (from " from ")"))
      "(no usages found — wrong symbol, or a dead-code candidate: cross-check op unused)")))

(def unused-finding-types
  "The kondo linters that flag dead/unused code."
  #{:unused-binding :unused-private-var :unused-namespace
    :unused-referred-var :unused-import :unused-value :unresolved-var})

(defn unused-digest
  "Only the unused/dead-code findings."
  [{:keys [findings]}]
  (let [dead (filterv (comp unused-finding-types :type) findings)]
    (render (str "UNUSED/DEAD (" (count dead) " of " (count findings) " findings):")
      (map finding-line dead)
      "(nothing flagged unused)")))
