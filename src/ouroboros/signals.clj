(ns ouroboros.signals
  "Signal store — the impure edge over ouroboros.signals.core
  (design/signals.md). signals/ IS the store: one EDN file per signal,
  filesystem-side and GITIGNORED (the sessions/ pattern — machine
  observation, pre-approval; git stays approved-memory-only).

  `emit!` is THE write path (λ converge — the :signal/emit tool and the EQL
  mutation both route through it): validate (registry-typed Malli gate) →
  content-hash dedupe → persist. Core THROWS structured; edges catch →
  corrective tool_result / structured EQL rejection (the mementum precedent).

  On disk: the envelope ONLY (core/envelope-keys order). :signal/id is
  DERIVED — it IS the filename stem (gene precedent: recompute ≻ trust a
  stored field)."
  (:require
    [babashka.fs :as fs]
    [clojure.edn :as edn]
    [ouroboros.signals.core :as core]))

(def signals-dir "signals")

(defn signal-path
  "Repo-relative path for a signal id (signals/<id>.edn)."
  [id]
  (str signals-dir "/" (name id) ".edn"))

(defn- abs-path [root id]
  (str (fs/path root (signal-path id))))

;; ---------------------------------------------------------------------------
;; Reads — nil-safe on an absent dir (no signal ever emitted ≡ fine)
;; ---------------------------------------------------------------------------

(defn- read-file [f]
  (let [sig (edn/read-string (slurp (fs/file f)))]
    (assoc sig :signal/id (keyword (str (fs/strip-ext (fs/file-name f)))))))

(defn all-signals
  "Every stored signal under `root`, :signal/id derived from the filename,
  ordered oldest→newest (ids are time-ordered slugs — name order ≡ time
  order). Absent dir ⇒ []."
  [root]
  (let [dir (fs/path root signals-dir)]
    (if (fs/directory? dir)
      (->> (fs/glob dir "*.edn")
        (sort-by (comp str fs/file-name))
        (mapv read-file))
      [])))

(defn recent
  "The most recent `n` signals, oldest→newest (read toward the present)."
  [root n]
  (vec (take-last n (all-signals root))))

(defn by-type
  "All signals of `type` (qualified keyword), oldest→newest."
  [root type]
  (filterv #(= type (:signal/type %)) (all-signals root)))

(defn for-source
  "All signals emitted by `source` (agent/session id string), oldest→newest."
  [root source]
  (filterv #(= source (:signal/source %)) (all-signals root)))

;; ---------------------------------------------------------------------------
;; The write path — gate, dedupe, persist. Throws structured ex-info.
;; ---------------------------------------------------------------------------

(defn- emit-edn
  "Canonical on-disk rendering: fixed key order (core/envelope-keys), absent
  optional keys omitted — stable diffs, human-readable files."
  [signal]
  (binding [*print-namespace-maps* true]
    (pr-str (apply array-map
              (mapcat (fn [k]
                        (when (contains? signal k) [k (get signal k)]))
                core/envelope-keys)))))

(defn emit!
  "THE signal write path. `signal` is the envelope WITHOUT :signal/at
  (stamped here) — {:signal/type kw :signal/data map :signal/lambda str?
  :signal/source str}. Validates against the registry-typed gate, dedupes by
  content-hash (same {type data lambda} ⇒ :duplicate with pointer), persists
  one EDN file. Returns {:signal/id :signal/path :signal/written true}.
  Throws structured ex-info on gate failure — nothing persists."
  [root signal]
  (let [signal (merge {:signal/at (System/currentTimeMillis)} signal)]
    (when-let [err (core/validate signal)]
      (throw (ex-info "signal rejected at the emit gate" (assoc err :signal signal))))
    (let [hash (core/content-hash signal)]
      (when-let [dup (first (filter #(= hash (core/content-hash %)) (all-signals root)))]
        (throw (ex-info "duplicate signal (content identical) — not re-emitted"
                 {:signal/error    :duplicate
                  :signal/existing (:signal/id dup)
                  :signal          signal}))))
    ;; id collision (same ms + same type, DIFFERENT content — dedupe already
    ;; passed) → bump :signal/at until the slot is free. Epoch-ms ties are
    ;; real (the session recency-sort lesson); never overwrite a fact.
    (let [signal (loop [s signal]
                   (if (fs/exists? (abs-path root (core/signal-id s)))
                     (recur (update s :signal/at inc))
                     s))
          id     (core/signal-id signal)
          p      (abs-path root id)]
      (fs/create-dirs (fs/parent p))
      (spit p (str (emit-edn signal) "\n"))
      {:signal/id      id
       :signal/path    (signal-path id)
       :signal/written true})))
