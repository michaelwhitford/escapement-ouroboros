(ns ouroboros.signals.core-test
  "Deterministic tests for the signals pure kernel — registry projections,
  the validate gate, content-hash identity, id slugs, prompt projection."
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [ouroboros.signals.core :as core]))

(def valid-report
  {:signal/type   :s1/report
   :signal/data   {:summary "sweep ok" :outcome :ok}
   :signal/lambda "λ sweep → ok"
   :signal/source "test"
   :signal/at     1000})

(deftest registry-shape
  (testing "every entry carries all three projections + variety + reserved flag"
    (doseq [[t e] core/registry]
      (is (qualified-keyword? t))
      (is (some? (:schema e)) (str t " schema"))
      (is (string? (:doc e)) (str t " doc"))
      (is (contains? #{:proposal :report :algedonic :notice} (:variety e))
        (str t " variety ∈ agent-comms vocabulary"))
      (is (boolean? (:reserved? e)) (str t " reserved?"))
      (testing (str t " exemplar is FILLED and self-valid")
        (let [{:keys [source signal]} (:exemplar e)]
          (is (string? source))
          (is (= t (:signal/type signal)))
          (is (nil? (core/validate (merge signal {:signal/source "exemplar"
                                                  :signal/at     1}))))))))
  (testing "reserved set matches the flags"
    (is (= #{:s4/proposal :ouro/algedonic} (core/reserved-types)))))

(deftest validate-gate
  (testing "valid signal → nil"
    (is (nil? (core/validate valid-report))))
  (testing "optional lambda may be absent"
    (is (nil? (core/validate (dissoc valid-report :signal/lambda)))))
  (testing "unknown type → :unknown-type naming the registry"
    (let [err (core/validate (assoc valid-report :signal/type :nope/nope))]
      (is (= :unknown-type (:signal/error err)))
      (is (str/includes? (first (get-in err [:errors :signal/type])) "s1/report"))))
  (testing "envelope violations → :envelope"
    (is (= :envelope (:signal/error (core/validate (dissoc valid-report :signal/source)))))
    (is (= :envelope (:signal/error (core/validate (assoc valid-report :extra 1))))))
  (testing "per-type data schema gates → :data"
    (let [err (core/validate (assoc-in valid-report [:signal/data :outcome] :maybe))]
      (is (= :data (:signal/error err))))
    (is (= :data (:signal/error
                   (core/validate (assoc valid-report :signal/data {:summary "x"})))))))

(deftest content-identity
  (testing "hash ignores :at and :source (same fact ≡ same hash)"
    (is (= (core/content-hash valid-report)
           (core/content-hash (assoc valid-report :signal/at 9999
                                                  :signal/source "other")))))
  (testing "hash sees data changes"
    (is (not= (core/content-hash valid-report)
              (core/content-hash (assoc-in valid-report [:signal/data :summary] "y")))))
  (testing "hash is key-order independent (canonical sorted-map walk)"
    (is (= (core/content-hash (assoc valid-report :signal/data
                                (array-map :summary "s" :outcome :ok)))
           (core/content-hash (assoc valid-report :signal/data
                                (array-map :outcome :ok :summary "s")))))))

(deftest id-slugs
  (testing "time-ordered, human-readable, keyword (λ identifier — no uuid)"
    (is (= :1000-s1-report (core/signal-id valid-report)))
    (is (= "s4-proposal" (core/type-slug :s4/proposal)))))

(deftest prompt-projection
  (testing "no grants → nil (absent ⇒ emit nothing)"
    (is (nil? (core/prompt-projection [])))
    (is (nil? (core/prompt-projection nil))))
  (testing "granted types render tool mapping + their FILLED exemplars only"
    (let [p (core/prompt-projection [:s1/report :human/notice])]
      (is (str/includes? p ":signal/emit"))
      (is (str/includes? p "⟨type :s1/report⟩"))
      (is (str/includes? p "⟨type :human/notice⟩"))
      (is (not (str/includes? p "⟨type :s4/proposal⟩")))
      (is (str/includes? p "⟨SOURCE⟩"))
      (testing "exemplar shows the literal signal keys (shape ≡ pinned)"
        (is (str/includes? p "{:signal/type :s1/report"))
        (is (str/includes? p ":signal/lambda"))))))
