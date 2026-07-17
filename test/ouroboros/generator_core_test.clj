(ns ouroboros.generator-core-test
  "Pure generator kernel: fitness, selection, candidate extraction, tournament
  math. Deterministic — the LLM/fs edges are live-only."
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [ouroboros.generator.core :as core]))

(def pool
  [{:gene   {:gene/name "verify" :gene/content "λ verify. tests GREEN"}
    :scores [{:score 9 :use-case "coder"} {:score 7 :use-case "coder"}
             {:score 3 :use-case "poet"}]}
   {:gene   {:gene/name "ground" :gene/content "λ ground. read before edit"}
    :scores [{:score 8 :use-case "coder"}]}
   {:gene   {:gene/name "rhyme" :gene/content "λ rhyme. couplets"}
    :scores [{:score 10 :use-case "poet"}]}
   {:gene   {:gene/name "unscored" :gene/content "λ mystery. ?"}
    :scores []}])

(deftest fitness-is-per-use-case-mean
  (is (= 8.0 (core/fitness-for (:scores (first pool)) "coder")))
  (is (= 3.0 (core/fitness-for (:scores (first pool)) "poet")))
  (testing "unscored ⇒ nil, never a default"
    (is (nil? (core/fitness-for [] "coder")))
    (is (nil? (core/fitness-for (:scores (second pool)) "poet")))))

(deftest selection-ranks-excludes-unscored-and-caps
  (let [sel (core/select-genes pool "coder" 10)]
    (is (= ["ground" "verify"] (mapv (comp :gene/name :gene) sel))
      "fitness desc: ground 8.0 > verify 8.0? no — verify (9+7)/2=8.0 ties ground 8.0, name breaks tie")
    (is (= [8.0 8.0] (mapv :fitness sel))))
  (testing "k caps"
    (is (= 1 (count (core/select-genes pool "coder" 1)))))
  (testing "unscored gene never selected"
    (is (not-any? #(= "unscored" (get-in % [:gene :gene/name]))
          (core/select-genes pool "coder" 10))))
  (testing "different use-case, different ranking"
    (is (= ["rhyme"] (mapv (comp :gene/name :gene) (core/select-genes pool "poet" 1))))))

(deftest extract-okf-handles-prose-and-fences
  (testing "clean document passes through"
    (let [doc "---\ntype: ouroboros/agent\n---\nλ x. y\n"]
      (is (= doc (core/extract-okf doc)))))
  (testing "leading prose, wrapping fence, AND trailing prose all stripped"
    (is (= "---\ntype: ouroboros/agent\n---\nλ x. y\n"
           (core/extract-okf
             "Here is the genome:\n```markdown\n---\ntype: ouroboros/agent\n---\nλ x. y\n```\nDone."))))
  (testing "no fence ⇒ nil (candidate dropped)"
    (is (nil? (core/extract-okf "I could not compose a genome.")))))

(deftest round-robin-runs-both-seatings
  (let [pairs (core/round-robin-pairs [:x :y :z])]
    (is (= 6 (count pairs)))
    (is (some #{[:x :y]} pairs))
    (is (some #{[:y :x]} pairs))))

(deftest tally-counts-wins-and-drops-failures
  (let [ranking (core/tally [{:pair [:x :y] :winner :a}   ; x
                             {:pair [:y :x] :winner :b}   ; x (bias canceled)
                             {:pair [:x :z] :winner :b}   ; z
                             {:pair [:z :x] :winner nil}  ; dropped
                             {:pair [:y :z] :winner :a}])] ; y
    (is (= [[:x 2] [:y 1] [:z 1]] ranking))))

;; ---------------------------------------------------------------------------
;; Convergence kernel — champion/challenger + patience
;; ---------------------------------------------------------------------------

(deftest duel-winner-needs-strict-majority
  (testing "challenger sweeps both seatings ⇒ promoted"
    (is (= :challenger
          (core/duel-winner [{:pair [:champion :challenger] :winner :b}
                             {:pair [:challenger :champion] :winner :a}]))))
  (testing "split seatings ≡ tie ⇒ incumbent holds"
    (is (= :champion
          (core/duel-winner [{:pair [:champion :challenger] :winner :a}
                             {:pair [:challenger :champion] :winner :a}]))))
  (testing "zero decided verdicts (both runs failed) ⇒ incumbent holds"
    (is (= :champion
          (core/duel-winner [{:pair [:champion :challenger] :winner nil}
                             {:pair [:challenger :champion] :winner nil}]))))
  (testing "one decided verdict is a strict majority"
    (is (= :challenger
          (core/duel-winner [{:pair [:champion :challenger] :winner :b}
                             {:pair [:challenger :champion] :winner nil}])))))

(deftest converge-step-tracks-streak-and-rounds
  (let [s0 {:streak 0 :rounds 0}]
    (testing "champion win extends the loss streak"
      (is (= {:streak 2 :rounds 2}
            (-> s0 (core/converge-step :champion) (core/converge-step :champion)))))
    (testing "challenger win RESETS the streak"
      (is (= {:streak 0 :rounds 3}
            (-> s0
              (core/converge-step :champion)
              (core/converge-step :champion)
              (core/converge-step :challenger)))))))

(deftest converged-stops-on-plateau-or-budget
  (let [opts {:patience 2 :max-rounds 4}]
    (is (not (core/converged? {:streak 1 :rounds 3} opts)) "still climbing")
    (is (core/converged? {:streak 2 :rounds 2} opts) "plateau: K consecutive losses")
    (is (core/converged? {:streak 0 :rounds 4} opts) "budget cap regardless of streak")))
