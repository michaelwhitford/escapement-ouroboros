(ns ouroboros.game.poker.eval
  "Poker hand evaluator — pure, zero I/O (design/game-arena.md).

  The comparable unit is the VALUE VECTOR: `[category t1 t2 t3 t4 t5]`,
  always length 6 (zero-padded). Clojure's `compare` on vectors is
  length-FIRST, so equal length is load-bearing — two hands compare
  correctly by plain `(compare (:value a) (:value b))`, no custom
  comparator, no bugs hiding in ragged vectors.

  Categories (int, ascending strength):
    0 high-card · 1 pair · 2 two-pair · 3 three-of-a-kind · 4 straight ·
    5 flush · 6 full-house · 7 four-of-a-kind · 8 straight-flush

  7-card evaluation is best-of-C(7,5)=21 five-card combos — brute force,
  correct by construction, microseconds at arena scale (λ build:
  simple > complex; a lookup-table evaluator is an optimization we have
  no benchmark asking for)."
  )

(def categories
  "Category keyword by strength index (position ≡ the value-vector head)."
  [:high-card :pair :two-pair :three-of-a-kind :straight
   :flush :full-house :four-of-a-kind :straight-flush])

(defn- pad5 [xs]
  (vec (take 5 (concat xs (repeat 0)))))

(defn- straight-high
  "Top rank of the best straight makeable from rank-set, else nil.
  Wheel (A-2-3-4-5) counts with high card 5 — ace demotes to 1 here,
  the ONLY place ace-low exists in the codebase."
  [rank-set]
  (let [rs (if (contains? rank-set 14) (conj rank-set 1) rank-set)]
    (->> (range 14 4 -1)
         (filter (fn [hi] (every? rs (range (- hi 4) (inc hi)))))
         first)))

(defn eval-5
  "Evaluate exactly 5 cards → {:category kw :value [c t1..t5]}."
  [cards]
  (let [rks      (map :rank cards)
        flush?   (apply = (map :suit cards))
        rank-set (set rks)
        s-high   (when (= 5 (count rank-set)) (straight-high rank-set))
        ;; groups: [[rank count] ...] desc by count then rank
        groups   (sort-by (fn [[r c]] [(- c) (- r)]) (frequencies rks))
        counts   (mapv second groups)
        granks   (mapv first groups)
        desc     (vec (sort > rks))
        [ci tie] (cond
                   (and flush? s-high)  [8 [s-high]]
                   (= counts [4 1])     [7 granks]
                   (= counts [3 2])     [6 granks]
                   flush?               [5 desc]
                   s-high               [4 [s-high]]
                   (= counts [3 1 1])   [3 granks]
                   (= counts [2 2 1])   [2 granks]
                   (= counts [2 1 1 1]) [1 granks]
                   :else                [0 desc])]
    {:category (categories ci)
     :value    (into [ci] (pad5 tie))}))

(defn- combinations
  "All k-subsets of xs (order-preserving). Small n only — arena needs C(7,5)."
  [xs k]
  (cond
    (zero? k)    [[]]
    (empty? xs)  []
    :else        (concat (map #(cons (first xs) %)
                              (combinations (rest xs) (dec k)))
                         (combinations (rest xs) k))))

(defn eval-best
  "Best 5-card evaluation from 5..7 cards (hole ⊕ board)."
  [cards]
  (let [n (count cards)]
    (when (or (< n 5) (> n 7))
      (throw (ex-info "eval-best wants 5..7 cards" {:count n})))
    (reduce (fn [best c5]
              (let [e (eval-5 c5)]
                (if (or (nil? best) (pos? (compare (:value e) (:value best))))
                  e
                  best)))
            nil
            (combinations (seq cards) 5))))

(defn compare-hands
  "Standard comparator over evaluations: neg ≡ a loses, 0 ≡ chop, pos ≡ a wins."
  [a b]
  (compare (:value a) (:value b)))
