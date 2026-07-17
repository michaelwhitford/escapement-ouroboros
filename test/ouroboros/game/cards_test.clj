(ns ouroboros.game.cards-test
  "Generic card substrate tests — deterministic, zero LLM."
  (:require
    [clojure.test :refer [deftest is testing]]
    [ouroboros.game.cards :as cards]))

(deftest deck-completeness
  (let [d (cards/deck)]
    (is (= 52 (count d)))
    (is (= 52 (count (set d))) "all cards distinct")
    (is (= 13 (count (filter #(= :spades (:suit %)) d))))
    (is (= 4 (count (filter #(= 14 (:rank %)) d))) "four aces")))

(deftest seeded-shuffle-deterministic
  (testing "same seed ⇒ same order"
    (is (= (cards/shuffled-deck 42) (cards/shuffled-deck 42))))
  (testing "different seed ⇒ different order (astronomically certain)"
    (is (not= (cards/shuffled-deck 42) (cards/shuffled-deck 43))))
  (testing "shuffle is a permutation — nothing lost, nothing invented"
    (is (= (set (cards/deck)) (set (cards/shuffled-deck 7))))
    (is (= 52 (count (cards/shuffled-deck 7)))))
  (testing "generic over any collection"
    (is (= (cards/shuffle-seeded (range 10) 1)
           (cards/shuffle-seeded (range 10) 1)))))

(deftest deal-arithmetic
  (let [d (cards/shuffled-deck 1)
        [taken rest] (cards/deal d 5)]
    (is (= 5 (count taken)))
    (is (= 47 (count rest)))
    (is (= d (into taken rest)) "deal preserves order and content"))
  (is (thrown? clojure.lang.ExceptionInfo (cards/deal [] 1)) "short deck throws"))

(deftest rendering
  (is (= "A♠" (cards/card->str {:rank 14 :suit :spades})))
  (is (= "T♥" (cards/card->str {:rank 10 :suit :hearts})))
  (is (= "7♦" (cards/card->str {:rank 7 :suit :diamonds})))
  (is (= "A♠ K♣" (cards/cards->str [{:rank 14 :suit :spades}
                                    {:rank 13 :suit :clubs}]))))
