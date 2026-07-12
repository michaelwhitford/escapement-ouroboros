(ns ouroboros.experiment.core-test
  "Deterministic tests for the experiment kernel — no LLM, no network."
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
