(ns ouroboros.experiment.core-test
  "Deterministic tests for the experiment kernel — no LLM, no network.
  Includes the :embedding kind (cosine, calibrate, schema dispatch)."
  (:require
    [clojure.edn :as edn]
    [clojure.test :refer [deftest is testing]]
    [ouroboros.experiment.core :as core]))

(def tiny-suite
  {:experiment/id :t
   :experiment/type :prompt-ab
   :experiment/hypothesis "h"
   :experiment/measure :edn-malli
   :measure/schema [:map {:closed true} [:a :int]]
   :matrix {:models [:local :ornith] :thinking [false true] :repeats 2}
   :conditions {:c1 {:system "s1"} :c2 {:system "s2"}}
   :subjects {:s1 "one" :s2 "two" :s3 "three"}
   :prompt-template "⟨SOURCE⟩%s⟨/SOURCE⟩"})

(deftest suite-validation
  (testing "the founding suite file is valid"
    (let [suite (edn/read-string (slurp "experiments/edn-signal-emission.edn"))]
      (is (nil? (core/validate-suite suite)))))
  (testing "tiny suite valid; unknown key fails loud (closed envelope)"
    (is (nil? (core/validate-suite tiny-suite)))
    (is (some? (core/validate-suite (assoc tiny-suite :experiment/oops 1))))
    (is (some? (core/validate-suite (dissoc tiny-suite :experiment/hypothesis))))))

(deftest matrix-expansion
  (let [cells (core/expand-matrix tiny-suite)]
    (testing "full cartesian product × repeats"
      (is (= (* 2 2 2 3 2) (count cells))))
    (testing "cells carry all coordinates"
      (is (every? #(and (:model %) (contains? % :thinking) (:cond %) (:subject %)) cells)))
    (testing "repeats default to 1"
      (is (= (* 2 2 2 3)
             (count (core/expand-matrix (update tiny-suite :matrix dissoc :repeats))))))))

(deftest edn-malli-assessment
  (let [schema [:map {:closed true} [:a :int]]
        assess #(core/assess-edn-malli schema ["SECRET"] %)]
    (testing "valid EDN validates"
      (is (= {:fence? false :echo? false :parse? true :valid? true :errors nil}
             (assess "{:a 1}"))))
    (testing "fenced EDN is stripped and validates, fence flagged"
      (let [r (assess "```edn\n{:a 1}\n```")]
        (is (:fence? r))
        (is (:valid? r))))
    (testing "schema violation parses but fails with humanized errors"
      (let [r (assess "{:a \"x\"}")]
        (is (:parse? r))
        (is (not (:valid? r)))
        (is (some? (:errors r)))))
    (testing "unparseable output fails parse (unclosed map = EOF)"
      (let [r (assess "{:a 1")]
        (is (not (:parse? r)))
        (is (not (:valid? r)))))
    (testing "bare key-value stream (dropped braces) parses as first form, fails validity"
      (let [r (assess ":a 1")]
        (is (:parse? r))
        (is (not (:valid? r)))))
    (testing "echo substring detection"
      (is (:echo? (assess "{:a 1} SECRET"))))))

(deftest summarize-and-format
  (let [rows [{:model :m :cond :c :thinking false :status :ok :parse? true :valid? true :ms 100 :tok 10}
              {:model :m :cond :c :thinking false :status :ok :parse? true :valid? false :ms 300 :tok 30}
              {:model :m :cond :d :thinking false :status :error :ms 50}]
        summary (core/summarize rows)]
    (testing "aggregates per (model, cond, thinking)"
      (is (= 2 (count summary)))
      (let [c (first (filter #(= :c (:cond %)) summary))]
        (is (= 2 (:n c)))
        (is (= 2 (:ok c)))
        (is (= 1 (:valid c)))
        (is (= 200 (:avg-ms c)))
        (is (= 20 (:avg-tok c)))))
    (testing "format renders one line per aggregate"
      (is (= 2 (count (clojure.string/split-lines (core/format-summary summary))))))))

(deftest edn-expected-assessment
  (let [suite {:measure/schema [:map {:closed true}
                                [:decision [:enum :extend-existing :create-new]]
                                [:rationale :string]]
               :measure/expected {:s1 {:decision :extend-existing}}}
        assess (get core/measures :edn-expected)]
    (testing "correct decision → valid+correct"
      (let [r (assess suite {:subject :s1}
                      "{:decision :extend-existing :rationale \"one path\"}")]
        (is (:parse? r)) (is (:correct? r)) (is (:valid? r))))
    (testing "schema-valid but WRONG decision → ¬valid (oracle bites)"
      (let [r (assess suite {:subject :s1}
                      "{:decision :create-new :rationale \"shiny\"}")]
        (is (:parse? r)) (is (not (:correct? r))) (is (not (:valid? r)))))
    (testing "no expectation for subject → falls back to schema validity"
      (let [r (assess suite {:subject :s2}
                      "{:decision :create-new :rationale \"ok\"}")]
        (is (:valid? r)) (is (not (:correct? r)))))
    (testing "unparseable → nothing passes"
      (let [r (assess suite {:subject :s1} "not { edn")]
        (is (not (:valid? r))) (is (not (:correct? r)))))))

;; ── kind :embedding ──────────────────────────────────────────────────────────

(deftest lambda-compaction-assessment
  (let [suite {:measure/echo-substrings ["continuity-essence" "structural_equivalence"]
               :measure/expected {:decision {:keeps ["redis"]}}
               :subjects {:decision "I'd pick Redis over Postgres for the session store because TTL expiry is free and reads are sub-millisecond."
                          :thin     "Sure — paste the logs whenever you're ready."}}
        assess (fn [subject raw]
                 (core/assess-lambda-compaction suite {:subject subject} raw))]
    (testing "faithful λ: shorter, keeps content name (case-insensitive), no echo"
      (let [r (assess :decision "decision(Redis > postgres | ttl_free ∧ reads(sub_ms)) ∧ next(∅)")]
        (is (:valid? r))
        (is (:shorter? r))
        (is (:keeps? r))
        (is (not (:echo? r)))))
    (testing "lens echo fails even when shorter"
      (let [r (assess :decision "redis | continuity-essence ONLY")]
        (is (:echo? r))
        (is (not (:valid? r)))))
    (testing "derail (answer longer than the turn) fails the compression contract"
      (let [r (assess :decision (apply str (repeat 40 "let me explain in detail ")))]
        (is (not (:shorter? r)))
        (is (not (:valid? r)))))
    (testing "dropped load-bearing name fails :keeps? and reports :missing"
      (let [r (assess :decision "decision(db_choice | fast) ∧ next(∅)")]
        (is (not (:keeps? r)))
        (is (= ["redis"] (:missing r)))
        (is (not (:valid? r)))))
    (testing "no expectation ⇒ compression is the only gate (thin/meta turns)"
      (is (:valid? (assess :thin "state(await(logs))")))
      (is (not (:valid? (assess :thin "")))
        "blank output is a failed compaction, never valid"))))

(deftest assemble-condition-suite-validation
  (testing "the compaction-fidelity suite file is valid (assemble-shaped conditions)"
    (let [suite (edn/read-string (slurp "experiments/compaction-fidelity.edn"))]
      (is (nil? (core/validate-suite suite)))))
  (testing "a condition with neither :system nor :assemble fails loud"
    (is (some? (core/validate-suite
                 (assoc-in tiny-suite [:conditions :c3] {})))))
  (testing "an :assemble condition validates without :system"
    (is (nil? (core/validate-suite
                (assoc-in tiny-suite [:conditions :c3]
                  {:assemble {:modules [:lambda-compiler]
                              :body-policy "compaction-lens"}}))))))

(deftest embedding-suite-validation
  (testing "the embed-dedupe suite file is valid"
    (let [suite (edn/read-string (slurp "experiments/embed-dedupe.edn"))]
      (is (nil? (core/validate-suite suite)))
      (is (= 8 (count (:pairs suite))))))
  (testing "kind dispatch: embedding shape rejected under chat schema rules"
    (let [suite {:experiment/id :e :experiment/kind :embedding
                 :experiment/type :calibration :experiment/hypothesis "h"
                 :embed/endpoint "http://x/v1" :embed/model "m"
                 :pairs {:p {:a "a" :b "b" :expect :near}}}]
      (is (nil? (core/validate-suite suite)))
      (is (some? (core/validate-suite (assoc suite :experiment/oops 1)))
        "closed envelope")
      (is (some? (core/validate-suite
                   (assoc-in suite [:pairs :p :expect] :maybe)))
        ":expect ∈ {near, distinct} only"))))

(deftest cosine-and-calibrate
  (testing "cosine basics"
    (is (= 1.0 (core/cosine [1.0 0.0] [1.0 0.0])))
    (is (= 0.0 (core/cosine [1.0 0.0] [0.0 1.0])))
    (is (> 0.001 (Math/abs (- 1.0 (core/cosine [1.0 2.0 3.0] [2.0 4.0 6.0])))
      ) "scale-invariant"))
  (testing "separated populations → positive gap + midpoint threshold"
    (let [cal (core/calibrate [{:pair :n1 :expect :near :cos 0.95}
                               {:pair :n2 :expect :near :cos 0.90}
                               {:pair :d1 :expect :distinct :cos 0.60}
                               {:pair :d2 :expect :distinct :cos 0.70}])]
      (is (:separated? cal))
      (is (< 0.19 (:gap cal) 0.21))
      (is (< 0.79 (:threshold cal) 0.81))
      (is (= 2 (:n (:near cal))))
      (is (= 2 (:n (:distinct cal))))))
  (testing "overlap → no threshold, surface don't guess"
    (let [cal (core/calibrate [{:pair :n :expect :near :cos 0.7}
                               {:pair :d :expect :distinct :cos 0.8}])]
      (is (not (:separated? cal)))
      (is (nil? (:threshold cal)))))
  (testing "empty/one-sided rows are quiet"
    (let [cal (core/calibrate [])]
      (is (nil? (:gap cal)))
      (is (not (:separated? cal))))))
