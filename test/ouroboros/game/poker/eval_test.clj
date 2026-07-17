(ns ouroboros.game.poker.eval-test
  "Hand evaluator tests — every category, kickers, the wheel, 7-card
  selection, comparison law. Deterministic, zero LLM."
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [ouroboros.game.poker.eval :as eval]))

(defn- c
  "\"As\" → {:rank 14 :suit :spades} — readable test fixtures."
  [s]
  (let [r  (get {\A 14 \K 13 \Q 12 \J 11 \T 10} (first s)
                (- (int (first s)) (int \0)))
        su ({\s :spades \h :hearts \d :diamonds \c :clubs} (last s))]
    {:rank r :suit su}))

(defn- cs [s] (mapv c (str/split s #" ")))

(deftest category-recognition
  (testing "all nine categories recognized"
    (is (= :straight-flush  (:category (eval/eval-5 (cs "9s 8s 7s 6s 5s")))))
    (is (= :four-of-a-kind  (:category (eval/eval-5 (cs "9s 9h 9d 9c 2s")))))
    (is (= :full-house      (:category (eval/eval-5 (cs "9s 9h 9d 2c 2s")))))
    (is (= :flush           (:category (eval/eval-5 (cs "As Js 9s 6s 3s")))))
    (is (= :straight        (:category (eval/eval-5 (cs "9s 8h 7d 6c 5s")))))
    (is (= :three-of-a-kind (:category (eval/eval-5 (cs "9s 9h 9d Kc 2s")))))
    (is (= :two-pair        (:category (eval/eval-5 (cs "9s 9h 5d 5c 2s")))))
    (is (= :pair            (:category (eval/eval-5 (cs "9s 9h Kd 5c 2s")))))
    (is (= :high-card       (:category (eval/eval-5 (cs "Ks Jh 9d 5c 2s")))))))

(deftest the-wheel
  (testing "A-5 straight, high card 5 — the only ace-low in the codebase"
    (let [wheel (eval/eval-5 (cs "As 2h 3d 4c 5s"))]
      (is (= :straight (:category wheel)))
      (is (neg? (eval/compare-hands wheel (eval/eval-5 (cs "2s 3h 4d 5c 6s"))))
          "wheel loses to a six-high straight")))
  (testing "steel wheel (A-5 straight flush)"
    (is (= :straight-flush (:category (eval/eval-5 (cs "As 2s 3s 4s 5s")))))))

(deftest broadway-and-ordering
  (testing "category order is total"
    (let [hands [(eval/eval-5 (cs "Ks Jh 9d 5c 2s"))   ; high card
                 (eval/eval-5 (cs "9s 9h Kd 5c 2s"))   ; pair
                 (eval/eval-5 (cs "9s 9h 5d 5c 2s"))   ; two pair
                 (eval/eval-5 (cs "9s 9h 9d Kc 2s"))   ; trips
                 (eval/eval-5 (cs "Ts Jh Qd Kc As"))   ; broadway straight
                 (eval/eval-5 (cs "As Js 9s 6s 3s"))   ; flush
                 (eval/eval-5 (cs "9s 9h 9d 2c 2s"))   ; boat
                 (eval/eval-5 (cs "9s 9h 9d 9c 2s"))   ; quads
                 (eval/eval-5 (cs "9s 8s 7s 6s 5s"))]] ; straight flush
      (is (every? neg? (map eval/compare-hands hands (rest hands)))
          "each hand strictly loses to the next"))))

(deftest kicker-discipline
  (testing "pair kickers break ties"
    (is (pos? (eval/compare-hands (eval/eval-5 (cs "As Ah Kd 5c 2s"))
                                  (eval/eval-5 (cs "Ac Ad Qd 5h 2h"))))))
  (testing "two-pair: high pair first, then low pair, then kicker"
    (is (pos? (eval/compare-hands (eval/eval-5 (cs "As Ah 3d 3c Ks"))
                                  (eval/eval-5 (cs "Kc Kd Qd Qh As")))))
    (is (pos? (eval/compare-hands (eval/eval-5 (cs "As Ah 3d 3c Ks"))
                                  (eval/eval-5 (cs "Ac Ad 3h 3s Qs"))))))
  (testing "identical values chop regardless of suits"
    (is (zero? (eval/compare-hands (eval/eval-5 (cs "As Kh Qd Jc 9s"))
                                   (eval/eval-5 (cs "Ah Ks Qc Jd 9h")))))))

(deftest seven-card-selection
  (testing "picks the flush hiding behind a straight"
    (let [e (eval/eval-best (cs "As Ks 7s 4s 2s 6h 5d"))]
      (is (= :flush (:category e)))))
  (testing "best two pair from three pairs, best kicker"
    (let [e (eval/eval-best (cs "As Ah Kd Kc 5s 5h Qd"))]
      (is (= :two-pair (:category e)))
      (is (= [2 14 13 12 0 0] (:value e)) "aces and kings, queen kicker")))
  (testing "6-card evaluation works"
    (is (= :straight (:category (eval/eval-best (cs "9s 8h 7d 6c 5s Kd"))))))
  (testing "board plays: 7 cards can still be a straight flush"
    (is (= :straight-flush
           (:category (eval/eval-best (cs "9s 8s 7s 6s 5s Ah Ad"))))))
  (testing "arity guard"
    (is (thrown? clojure.lang.ExceptionInfo (eval/eval-best (cs "As Kh"))))))

(deftest value-vectors-are-uniform
  (testing "every value vector has length 6 — compare is element-wise, never length-first"
    (doseq [h ["9s 8s 7s 6s 5s" "9s 9h 9d 9c 2s" "9s 9h 9d 2c 2s"
               "As Js 9s 6s 3s" "9s 8h 7d 6c 5s" "9s 9h 9d Kc 2s"
               "9s 9h 5d 5c 2s" "9s 9h Kd 5c 2s" "Ks Jh 9d 5c 2s"]]
      (is (= 6 (count (:value (eval/eval-5 (cs h))))) h))))
