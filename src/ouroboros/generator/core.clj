(ns ouroboros.generator.core
  "Pure kernel of the GENERATOR kind (agent-model spec §Genes — the last kind):
  fitness aggregation over the scores side-store, selection for a target
  use-case, candidate-document extraction, and pairwise-tournament math.
  Deterministic; the LLM/fs edges live in ouroboros.generator."
  (:require
    [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Fitness — mean per (gene, use-case) over the side-store entries
;; ---------------------------------------------------------------------------

(defn mean [xs]
  (when (seq xs) (double (/ (reduce + xs) (count xs)))))

(defn fitness-for
  "Mean score of side-store `entries` for `use-case` — nil when unscored
  (unscored ≡ NO fitness ≡ excluded from selection, never assumed average)."
  [entries use-case]
  (mean (keep #(when (= use-case (:use-case %)) (:score %)) entries)))

(defn select-genes
  "Rank `pool` ([{:gene <envelope> :scores [entries]}]) for `use-case`:
  [{:gene .. :fitness n}] fitness-desc, unscored excluded, top `k`.
  Ties break by gene name (deterministic)."
  [pool use-case k]
  (->> pool
    (keep (fn [{:keys [gene scores]}]
            (when-let [f (fitness-for scores use-case)]
              {:gene gene :fitness f})))
    (sort-by (juxt (comp - :fitness) (comp :gene/name :gene)))
    (take k)
    vec))

;; ---------------------------------------------------------------------------
;; Candidate extraction — the generator's reply IS the genome document
;; ---------------------------------------------------------------------------

(defn extract-okf
  "Pull the OKF document from a generator reply: from the FIRST line that is
  exactly `---`, cut at the first code-fence line AFTER it (models wrap the
  doc in ```markdown — anything past the closing fence is prose, dropped).
  nil when the reply carries no `---` — the caller drops the candidate."
  [reply]
  (let [lines (mapv str/trimr (str/split-lines (str reply)))
        start (first (keep-indexed (fn [i l] (when (= "---" l) i)) lines))]
    (when start
      (let [tail (subvec lines start)
            stop (first (keep-indexed
                          (fn [i l] (when (str/starts-with? l "```") i)) tail))]
        (str (str/trim (str/join "\n" (if stop (subvec tail 0 stop) tail)))
          "\n")))))

;; ---------------------------------------------------------------------------
;; Tournament — pairwise, BOTH orders (position bias cancels in the tally)
;; ---------------------------------------------------------------------------

(defn round-robin-pairs
  "Every ORDERED pair (a≠b) — each unordered matchup runs twice, once per
  seating, so a comparator's first-position bias self-cancels."
  [ids]
  (vec (for [a ids, b ids :when (not= a b)] [a b])))

(defn tally
  "Fold comparator results [{:pair [a b] :winner :a|:b|nil}] into a ranking
  [[id wins] ...] wins-desc (ties by id). nil winners (failed runs) drop out."
  [results]
  (->> results
    (keep (fn [{:keys [pair winner]}]
            (case winner :a (first pair) :b (second pair) nil)))
    frequencies
    (sort-by (juxt (comp - val) key))
    vec))
