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
   [:experiment/kind {:optional true} [:= :chat]]
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
   [:conditions [:map-of :keyword
                 [:and
                  [:map
                   [:system   {:optional true} :string]
                   ;; :assemble — the condition's system prompt is built by the
                   ;; REAL production pipeline (ouroboros.agents.core/assemble,
                   ;; resolved at the impure edge): granted modules ⊕ a policy
                   ;; artifact body. The Anima rule made mechanical: a suite
                   ;; that assembles differently than production validates
                   ;; nothing — this is how `bb experiment` A/Bs module
                   ;; inclusion HONESTLY.
                   [:assemble {:optional true}
                    [:map {:closed true}
                     [:modules [:vector :keyword]]
                     [:body-policy :string]]]]
                  [:fn {:error/message "condition needs :system or :assemble"}
                   (fn [c] (boolean (or (:system c) (:assemble c))))]]]]
   [:subjects [:map-of :keyword :string]]
   [:prompt-template :string]])

(def embedding-suite-schema
  "The :embedding suite variant (λ extend: a new experiment KIND is a new
  schema + cell executor, same runner). Pairs of REAL texts with an :expect
  label; the measure is cosine separation between the :near and :distinct
  populations — the dedupe threshold is DERIVED from the gap, not guessed
  (design/gene-db.md: anima's HNSW threshold was UNSET everywhere)."
  [:map {:closed true}
   [:experiment/id :keyword]
   [:experiment/kind [:= :embedding]]
   [:experiment/type :keyword]
   [:experiment/hypothesis :string]
   [:experiment/verdict {:optional true} :string]
   [:embed/endpoint :string]
   [:embed/model :string]
   [:pairs [:map-of :keyword
            [:map {:closed true}
             [:a :string]
             [:b :string]
             [:expect [:enum :near :distinct]]]]]])

(defn validate-suite
  "nil when valid, humanized errors otherwise. Dispatches on
  :experiment/kind (absent ⇒ :chat, the original suite shape)."
  [suite]
  (let [schema (case (:experiment/kind suite :chat)
                 :embedding embedding-suite-schema
                 suite-schema)]
    (when-not (m/validate schema suite)
      (me/humanize (m/explain schema suite)))))

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

(defn assess-lambda-compaction
  "Decidable fidelity FLOOR for a λ-compaction output (semantic fidelity is
  unverifiable without a judge — this measures what a string fn can):

    echo?    — output contains any :measure/echo-substrings (lens/bridge
               text leaking = the round-1/2 failure mode)
    shorter? — STRICTLY shorter than the subject turn (the compression
               contract, same law as compact.core/apply-compaction — a
               'compaction' that answers/derails instead expands)
    keeps?   — contains every (:keeps expected) substring, case-insensitive
               (load-bearing content names must SURVIVE the compression)

  :valid? ⟺ ¬echo ∧ shorter ∧ keeps. :parse? = non-blank output (so the
  summary's parse column reads 'produced anything')."
  [suite {:keys [subject]} raw]
  (let [out      (str/trim (str raw))
        out-lc   (str/lower-case out)
        text     (str (get-in suite [:subjects subject]))
        echo?    (boolean (some #(str/includes? out %)
                                (or (:measure/echo-substrings suite) [])))
        shorter? (and (not (str/blank? out)) (< (count out) (count text)))
        keeps    (get-in suite [:measure/expected subject :keeps])
        missing  (vec (remove #(str/includes? out-lc (str/lower-case %)) keeps))]
    {:parse?   (not (str/blank? out))
     :echo?    echo?
     :shorter? shorter?
     :keeps?   (empty? missing)
     :missing  missing
     :valid?   (boolean (and (not echo?) shorter? (empty? missing)))}))

(def measures
  "measure keyword → assess fn (suite, cell, raw-text → assessment map).
  Open slot (λ extend): future measures dispatch here — e.g. :scorer-genome /
  :judge-genome route the output through the verdict topology instead."
  {:edn-malli         (fn [suite _cell raw]
                        (assess-edn-malli (:measure/schema suite)
                                          (:measure/echo-substrings suite)
                                          raw))
   :edn-expected      (fn [suite cell raw]
                        (assess-edn-expected suite cell raw))
   :lambda-compaction (fn [suite cell raw]
                        (assess-lambda-compaction suite cell raw))})

;; ── embedding calibration (kind :embedding) ─────────────────────────────────

(defn cosine
  "Cosine similarity of two equal-length vectors (doubles)."
  [a b]
  (let [dot   (reduce + (map * a b))
        norm  (fn [v] (Math/sqrt (reduce + (map * v v))))
        denom (* (norm a) (norm b))]
    (if (zero? denom) 0.0 (double (/ dot denom)))))

(defn- stats [xs]
  (when (seq xs)
    {:n (count xs) :min (apply min xs) :max (apply max xs)
     :mean (double (/ (reduce + xs) (count xs)))}))

(defn calibrate
  "Rows [{:pair :expect :cos}] → threshold calibration:
  {:near <stats> :distinct <stats> :gap <min-near − max-distinct>
   :threshold <midpoint when populations separate> :separated? <bool>}.
  :near cosines should sit HIGH, :distinct LOW; a positive gap means ANY
  threshold inside (max-distinct, min-near) cleanly splits — we take the
  midpoint. Overlap (gap ≤ 0) ⇒ :threshold nil: SURFACE, don't guess."
  [rows]
  (let [near (mapv :cos (filter #(= :near (:expect %)) rows))
        dist (mapv :cos (filter #(= :distinct (:expect %)) rows))
        gap  (when (and (seq near) (seq dist))
               (- (apply min near) (apply max dist)))]
    {:near       (stats near)
     :distinct   (stats dist)
     :gap        gap
     :separated? (boolean (and gap (pos? gap)))
     :threshold  (when (and gap (pos? gap))
                   (/ (+ (apply min near) (apply max dist)) 2.0))}))

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
