(ns ouroboros.poker
  "bb poker — LLM agents at the limit hold'em table (design/game-arena.md,
  build step 4's CLI seam).

    bb poker                          — poker-tight vs poker-aggro, 3 hands, reset
    bb poker <a> <b>                  — two player-genome slugs
    bb poker <a> <b> <hands>          — hand count
    bb poker <a> <b> <hands> elim     — elimination (carried stacks, until felt)

  Cross-family by DEFAULT: poker-tight rides :local (qwen36 @5100),
  poker-aggro rides :gemma4 (@5102) — two model families, one table, chips
  as the decorrelated scoreboard. Narrates each decision live (the :why
  table-talk included); the full match record persists to games/."
  (:require
    [clojure.string :as str]
    [ouroboros.game.arena :as arena]
    [ouroboros.game.cards :as cards]
    [ouroboros.game.llm :as game.llm]
    [ouroboros.game.poker :as poker]))

(defn- narrate [spec obs action]
  (println (format "  seat %s [%s] %s%s  (hole %s | board %s | pot %d)"
                   (:seat obs)
                   (name (:genome spec))
                   (if action (name (:action action)) "— no verdict → forfeit")
                   (if-let [why (:why action)] (str " — \"" why "\"") "")
                   (cards/cards->str (:hole obs))
                   (if (seq (:board obs)) (cards/cards->str (:board obs)) "—")
                   (:pot obs))))

(defn -main [& [a b hands-s mode-s]]
  (let [a     (keyword (or a "poker-tight"))
        b     (keyword (or b "poker-aggro"))
        hands (parse-long (or hands-s "3"))
        mode  (if (= mode-s "elim") :elimination :reset)
        _     (println (str "♠♥♦♣ " (name a) " vs " (name b)
                            " — limit hold'em, " (name mode)
                            (when (= mode :reset) (str ", " hands " hands"))))
        m     (arena/run-match!
                poker/engine
                {:seats     [{:genome a} {:genome b}]
                 :decide-fn (game.llm/decide-fn poker/engine {:on-decision narrate})
                 :seed      (rand-int 1000000)
                 :hands     hands
                 :mode      mode
                 :max-hands 100
                 :starting-bankroll 200})]
    (println)
    (doseq [h (:hands m)]
      (println (format "hand %d: %s — payoffs %s"
                       (:hand h) (name (or (:ending h) :?)) (pr-str (:payoffs h)))))
    (println)
    (println "totals   :" (pr-str (:totals m)))
    (println "forfeits :" (pr-str (:forfeits m)))
    (when-some [w (:winner m)]
      (println "WINNER   :" (name (nth [a b] w)) "— takes the table"))
    (println "record   :" (:file m))
    (shutdown-agents)
    (System/exit 0)))
