(ns ouroboros.game.cards
  "Generic card-game substrate (design/game-arena.md) — NO game-specific
  knowledge lives here. Poker owns hand ranking and betting; hearts owns
  tricks; blackjack owns 21. They all draw from THIS deck.

  A card ≡ {:rank int :suit kw} — 2..14 (J=11 Q=12 K=13 A=14), suit ∈ suits.
  Plain EDN maps: greppable, transcript-serializable, Malli-friendly.

  Determinism law: `shuffle-seeded` is Fisher-Yates over java.util.Random —
  same seed ⇒ same order, on any host (bb ∧ JVM). A seeded deck makes every
  match REPLAYABLE (the transcript stores only the seed) and makes duplicate
  seating possible (same deals × seat permutations — luck cancellation)."
  (:require
    [clojure.string :as str]))

(def suits
  "The four suit keywords, canonical order."
  [:clubs :diamonds :hearts :spades])

(def ranks
  "Rank ints 2..14. Ace ≡ 14 (games that treat ace low handle it themselves —
  e.g. poker's wheel straight lives in the poker evaluator, not here)."
  (vec (range 2 15)))

(defn deck
  "The standard 52-card deck, unshuffled (suits × ranks)."
  []
  (vec (for [s suits r ranks] {:rank r :suit s})))

(defn shuffle-seeded
  "Deterministic Fisher-Yates shuffle of any collection. Same seed ⇒ same
  order. Pure from the caller's view (fresh Random per call)."
  [coll seed]
  (let [rng (java.util.Random. (long seed))]
    (loop [v (vec coll) i (dec (count v))]
      (if (pos? i)
        (let [j (.nextInt rng (inc i))]
          (recur (assoc v i (v j) j (v i)) (dec i)))
        v))))

(defn shuffled-deck
  "A seeded-shuffled standard deck."
  [seed]
  (shuffle-seeded (deck) seed))

(defn deal
  "Deal n cards off the top: [dealt remaining]. Throws when the deck is short
  (a game asking for cards that do not exist is a bug, not a condition)."
  [cards n]
  (when (< (count cards) n)
    (throw (ex-info "deck exhausted" {:want n :have (count cards)})))
  [(vec (take n cards)) (vec (drop n cards))])

(def ^:private rank-strs {10 "T" 11 "J" 12 "Q" 13 "K" 14 "A"})
(def ^:private suit-strs {:clubs "♣" :diamonds "♦" :hearts "♥" :spades "♠"})

(defn rank->str
  "2..9 as digits, then T J Q K A."
  [r]
  (get rank-strs r (str r)))

(defn card->str
  "\"A♠\", \"T♥\", \"7♦\" — the human/LLM-facing rendering."
  [{:keys [rank suit]}]
  (str (rank->str rank) (suit-strs suit)))

(defn cards->str
  "Space-joined card->str: \"A♠ K♦ 7♣\"."
  [cards]
  (str/join " " (map card->str cards)))
