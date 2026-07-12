(ns ouroboros.experiment.core
  "Pure kernel of the experiment suite (house <engine>/core convention).

  An EXPERIMENT is a declarative EDN suite: {hypothesis, conditions, subjects,
  matrix, measure}. This namespace holds everything deterministic — matrix
  expansion, output assessment, aggregation. The impure edge (ouroboros.experiment)
  loads suites, calls models, persists results.

  Lineage: anima designs/experiments.md (suite-as-EDN, ONE parameterized runner,
  new experiment type ⇒ new EDN ¬new runner) + the ab_edn_signal scratch arc
  (rounds 1-3) this suite machinery generalizes."
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [malli.core :as m]
    [malli.error :as me]))

;; ── suite validation ─────────────────────────────────────────────────────────

(def suite-schema
  "The suite envelope. CLOSED like genome frontmatter — a suite is WIRING;
  an unknown key ≈ typo, fail loud."
  [:map {:closed true}
   [:experiment/id :keyword]
   [:experiment/type :keyword]
   [:experiment/hypothesis :string]
   [:experiment/verdict {:optional true} :string]
   [:experiment/measure :keyword]
   [:measure/schema {:optional true} :any]
   [:measure/echo-substrings {:optional true} [:vector :string]]
   [:measure/expected {:optional true} [:map-of :keyword [:map-of :keyword :any]]]
   [:matrix [:map
             [:models [:vector :keyword]]
             [:thinking [:vector :boolean]]
             [:repeats {:optional true} pos-int?]]]
   [:conditions [:map-of :keyword [:map [:system :string]]]]
   [:subjects [:map-of :keyword :string]]
   [:prompt-template :string]])

(defn validate-suite
  "nil when valid, humanized errors otherwise."
  [suite]
  (when-not (m/validate suite-schema suite)
    (me/humanize (m/explain suite-schema suite))))

;; ── matrix expansion ─────────────────────────────────────────────────────────

(defn expand-matrix
  "Suite → seq of cells {:model :thinking :cond :subject :repeat}."
  [{:keys [matrix conditions subjects]}]
  (let [repeats (get matrix :repeats 1)]
    (for [model    (:models matrix)
          thinking (:thinking matrix)
          cnd      (sort (keys conditions))
          subject  (sort (keys subjects))
          r        (range repeats)]
      {:model model :thinking thinking :cond cnd :subject subject :repeat r})))

;; ── assessment (measure :edn-malli) ─────────────────────────────────────────

(defn strip-fences
  "Remove a single surrounding markdown code fence, if present."
  [s]
  (let [s (str/trim (str s))]
    (if (str/starts-with? s "```")
      (-> s (str/replace #"^```[a-zA-Z]*\s*" "") (str/replace #"\s*```$" "") str/trim)
      s)))

(defn assess-edn-malli
  "Assess raw model output against a Malli schema.
  Returns {:fence? :echo? :parse? :valid? :errors}."
  [schema echo-substrings raw]
  (let [raw      (str raw)
        fenced?  (str/starts-with? (str/trim raw) "```")
        cleaned  (strip-fences raw)
        echo?    (boolean (some #(str/includes? cleaned %) (or echo-substrings [])))
        parsed   (try (edn/read-string cleaned) (catch Exception _ ::unparseable))
        parse?   (not= parsed ::unparseable)
        valid?   (boolean (and parse? schema (m/validate schema parsed)))
        errors   (when (and parse? schema (not valid?))
                   (me/humanize (m/explain schema parsed)))]
    {:fence? fenced? :echo? echo? :parse? parse? :valid? valid? :errors errors}))

(defn assess-edn-expected
  "assess-edn-malli PLUS a per-subject correctness oracle: the suite's
  :measure/expected maps subject → kv pairs the parsed EDN must contain
  (schema stays answer-neutral — wrong options live in the enum so the shape
  doesn't leak the answer; expectation checks correctness).
  :valid? ⟺ schema-valid ∧ expected-satisfied — summarize's VALID column
  then reads as CORRECT with no aggregation changes."
  [suite {:keys [subject]} raw]
  (let [base     (assess-edn-malli (:measure/schema suite)
                                   (:measure/echo-substrings suite) raw)
        expected (get-in suite [:measure/expected subject])
        parsed   (when (:parse? base)
                   (try (edn/read-string (strip-fences (str raw)))
                        (catch Exception _ nil)))
        correct? (boolean (and (map? parsed) expected
                               (= (select-keys parsed (keys expected)) expected)))]
    (assoc base
           :correct? correct?
           :valid? (boolean (and (:valid? base) (or (nil? expected) correct?))))))

(def measures
  "measure keyword → assess fn (suite, cell, raw-text → assessment map).
  Open slot (λ extend): future measures dispatch here — e.g. :scorer-genome /
  :judge-genome route the output through the verdict topology instead."
  {:edn-malli    (fn [suite _cell raw]
                   (assess-edn-malli (:measure/schema suite)
                                     (:measure/echo-substrings suite)
                                     raw))
   :edn-expected (fn [suite cell raw]
                   (assess-edn-expected suite cell raw))})

;; ── aggregation ──────────────────────────────────────────────────────────────

(defn summarize
  "Rows → per-(model, cond, thinking) aggregate rows, sorted."
  [rows]
  (->> (group-by (juxt :model :cond :thinking) rows)
       (sort-by (fn [[[m c t] _]] [(str m) (str c) (str t)]))
       (map (fn [[[model cnd thinking] grp]]
              (let [n    (count grp)
                    toks (keep :tok grp)]
                {:model model :cond cnd :thinking thinking :n n
                 :ok    (count (filter #(= :ok (:status %)) grp))
                 :parse (count (filter :parse? grp))
                 :valid (count (filter :valid? grp))
                 :avg-ms  (when (pos? n) (int (/ (reduce + (map #(:ms % 0) grp)) n)))
                 :avg-tok (when (seq toks) (int (/ (reduce + toks) (count toks))))})))))

(defn format-summary
  [summaries]
  (str/join "\n"
    (for [{:keys [model cond thinking n ok parse valid avg-ms avg-tok]} summaries]
      (format "%-8s %-14s think=%-5s ok=%d/%d parse=%d/%d VALID=%d/%d avg=%dms %stok"
              (name model) (name cond) thinking ok n parse n valid n
              (or avg-ms -1) (or avg-tok "-")))))
