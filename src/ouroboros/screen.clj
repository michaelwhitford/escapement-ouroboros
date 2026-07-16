(ns ouroboros.screen
  "The verifier IN THE LOOP — evidence pre-screening of the human gate's
  inbox (design/scheduled-maintenance: the verifier stays OUTSIDE the 2×2
  matrix, serves all four cells). The sweep PRODUCES review artifacts
  (pending proposals + uncommitted memory candidates); the human REVIEWS
  them at bb proposals; this ns runs the verifier genome (verdict topology,
  judge kind — reads live src/sessions/mementum evidence) over each artifact
  IN BETWEEN, so every inbox line carries a stale/hallucinated/contradicted
  verdict before human attention is spent.

  Verdicts persist in proposals/.screen.edn — gitignored WITH its dir
  (pre-approval observation, the sessions/ pattern) — keyed by repo-relative
  artifact path and stamped with a CONTENT HASH: screening is IDEMPOTENT
  (the generate-scores precedent). Unchanged artifacts never re-run; an
  edited artifact re-plans, and its out-of-date verdict renders ⚠ stale
  rather than lying (λ coherence). A failed run persists NOTHING — it
  retries at the next screen (λ escalate: visible, never silently absorbed).

  One path each (λ converge): bb screen ≡ run + render · bb proposals stays
  a fast LLM-FREE read that merely DISPLAYS stored verdicts · the sweep
  composes via :screen-fn (injectable, ABSENT default — λ extend: option >
  detection; bb maintain passes the real thing)."
  (:require
    [babashka.fs :as fs]
    [clojure.edn :as edn]
    [clojure.string :as str]
    [ouroboros.proposals :as proposals]))

(def store-file
  "The verdict side-store, inside the gitignored proposals/ dir."
  "proposals/.screen.edn")

(defn- store-path [root] (str (fs/path root store-file)))

(defn load-store
  "Stored verdicts: {path {:hash :status :notes :model}}. Absent ⇒ {}."
  [root]
  (let [p (store-path root)]
    (if (fs/exists? p) (edn/read-string (slurp p)) {})))

(defn- save-store! [root store]
  (fs/create-dirs (fs/parent (store-path root)))
  (spit (store-path root) (pr-str store)))

(defn content-hash
  "SHA-1 hex of an artifact's full text — the idempotency key."
  [s]
  (let [d (.digest (java.security.MessageDigest/getInstance "SHA-1")
            (.getBytes (str s) "UTF-8"))]
    (apply str (map #(format "%02x" %) d))))

(defn- artifacts
  "Every inbox artifact as {:kind :path :content :hash} — pending proposals
  (unparseable ones are already surfaced by the inbox itself, skipped here)
  + uncommitted memory candidates. Reads the file RAW (frontmatter included:
  a verdict should cover the whole document the human will review)."
  [root]
  (->> (concat
         (for [{:keys [path error]} (proposals/pending root)
               :when (not error)]
           {:kind :proposal :path path})
         (for [path (proposals/untracked-memories root)]
           {:kind :memory :path path}))
    (keep (fn [{:keys [path] :as item}]
            (let [f (fs/path root path)]
              (when (fs/exists? f)
                (let [content (slurp (str f))]
                  (assoc item :content content :hash (content-hash content)))))))
    vec))

(defn plan
  "Artifacts needing a verifier run: never screened, or edited since their
  stored verdict (hash mismatch)."
  [root store]
  (vec (remove #(= (:hash %) (get-in store [(:path %) :hash])) (artifacts root))))

(defn- subject [{:keys [kind path content]}]
  (str (case kind
         :proposal "Pending proposal"
         :memory   "Proposed memory candidate")
       " for verification (" path "):\n\n" content))

(defn verdicts
  "Stored verdicts joined against CURRENT inbox content —
  {path {:status :notes :model :stale?}}. :stale? ⟺ the artifact was edited
  (or removed) after screening; a stale verdict renders as a warning, never
  as truth."
  [root]
  (let [current (into {} (map (juxt :path :hash)) (artifacts root))]
    (into {}
      (for [[path v] (load-store root)]
        [path (-> v (dissoc :hash)
                  (assoc :stale? (not= (:hash v) (get current path))))]))))

(defn screen!
  "Run the verifier over every planned artifact. `opts`:
    :run-fn  (fn [subject] → verdict-runner result) — injectable for tests;
             default: the REAL verifier via the verdict topology.
    :quiet?  suppress per-item lines.
  A run that yields no verdict (worker death, budget) persists nothing and
  is retried next screen. Returns [{:path :kind :status :notes}]."
  ([root] (screen! root {}))
  ([root {:keys [run-fn quiet?]}]
   (let [run-fn (or run-fn
                    (fn [subj]
                      ((requiring-resolve 'ouroboros.verdict/run!)
                       :verifier subj {:root root})))
         store  (load-store root)
         items  (plan root store)
         outcomes
         (vec
           (for [{:keys [path hash] :as item} items]
             (let [{:keys [verdict model]} (run-fn (subject item))
                   line (fn [s] (when-not quiet? (println s)))]
               (if verdict
                 (do (line (str "screen " path " → " (name (:status verdict))))
                     (assoc item :verdict verdict :model model))
                 (do (line (str "⚠ screen " path " → no verdict (run failed; will retry)"))
                     item)))))]
     (save-store! root
       (reduce (fn [st {:keys [path hash verdict model]}]
                 (if verdict
                   (assoc st path {:hash   hash
                                   :status (:status verdict)
                                   :notes  (:notes verdict)
                                   :model  model})
                   st))
         store outcomes))
     (mapv (fn [{:keys [path kind verdict]}]
             {:path path :kind kind
              :status (:status verdict) :notes (:notes verdict)})
       outcomes))))

;; ---------------------------------------------------------------------------
;; CLI — bb screen (run, then show the annotated inbox)
;; ---------------------------------------------------------------------------

(defn -main [& _]
  (let [outcomes (screen! ".")]
    (println)
    (println (proposals/render-inbox (proposals/pending ".")
               (proposals/untracked-memories ".")
               (verdicts ".")))
    (shutdown-agents)
    (System/exit (if (every? :status outcomes) 0 1))))
