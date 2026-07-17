(ns ouroboros.game.llm-test
  "LLM decide-seam tests — REAL roster genomes, stubbed run-fn (no LLM).
  Proves: :player kind registered (schema deliberately dynamic), player
  genomes compile floor-less, the legality-narrowed schema + exemplar +
  rendered narration reach the decide seam, verdict actions flow through
  a full arena match, and nil verdicts decay to counted forfeits."
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [malli.core :as m]
    [ouroboros.agents :as agents]
    [ouroboros.agents.core :as core]
    [ouroboros.game.arena :as arena]
    [ouroboros.game.llm :as game.llm]
    [ouroboros.game.poker :as poker]))

(deftest player-kind-registered
  (is (contains? core/kinds :player) "the 11th kind")
  (is (nil? (core/verdict-schema :player))
      "NO static schema row — per-decision, engine-supplied (the semantic
      difference that made :player a kind)"))

(deftest player-genomes-compile
  (doseq [[id model] {:poker-tight :local :poker-aggro :gemma4}]
    (let [g (agents/genome id ".")]
      (is (= :player (:kind g)) (str id))
      (is (= [] (:tools g))
          "explicit empty grant — a player needs NOTHING but the observation;
          git/fs unreachable by absence")
      (is (= model (:model g)) "cross-family table by default")
      (is (str/includes? (:prompt g) "table-talk")))))

(defn- schema-actions
  "[:map [:action [:enum & as]] …] → as"
  [schema]
  (-> schema (nth 1) (nth 1) rest vec))

(deftest stubbed-match-end-to-end
  (let [calls (atom [])
        stub  (fn [genome {:keys [message schema]}]
                (swap! calls conj {:genome (:id genome)
                                  :message message
                                  :schema  schema})
                (let [acts   (set (schema-actions schema))
                      action {:action (or (some acts [:check :call]) (first acts))
                              :why    "stub talk"}]
                  (is (m/validate schema action) "stub obeys the narrowed schema")
                  action))
        df    (game.llm/decide-fn poker/engine {:run-fn stub})
        m     (arena/run-match! poker/engine
                                {:seats     [{:genome :poker-tight}
                                             {:genome :poker-aggro}]
                                 :decide-fn df
                                 :seed      13 :hands 2 :persist? false})]
    (is (= 2 (count (:hands m))))
    (is (zero? (reduce + (vals (:totals m)))))
    (is (= #{} (set (keys (:forfeits m)))) "valid verdicts → zero forfeits")
    (testing "both genomes decided; contract reached the seam every time"
      (is (= #{:poker-tight :poker-aggro} (set (map :genome @calls))))
      (is (every? :schema @calls))
      (is (every? #(str/includes? (:message %) "hole cards") @calls)
          "the engine's narration is the message")
      (is (every? #(str/includes? (:message %) ":why") @calls)
          "the filled exemplar primes the shape"))
    (testing "table-talk rides every decision record"
      (is (every? (fn [h] (every? #(= "stub talk" (get-in % [:action :why]))
                                  (:decisions h)))
                  (:hands m))))
    (is (pos? (count @calls)))))

(deftest nil-verdict-decays-to-forfeit
  (let [df (game.llm/decide-fn poker/engine {:run-fn (constantly nil)})
        {:keys [state decisions]}
        (arena/run-hand! poker/engine
                         {:config     {:seats 2}
                          :seed       5
                          :decide-fn  df
                          :hand-seats [{:genome :poker-tight}
                                       {:genome :poker-aggro}]})]
    (is (poker/terminal? state) "dead models cannot wedge the table")
    (is (some :forfeit? (:history state)) "forfeits recorded by the engine")
    (is (every? (comp nil? :action) decisions))))

(deftest non-player-genome-rejected
  (let [df (game.llm/decide-fn poker/engine {:run-fn (constantly {:action :fold})})]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"not kind:player"
          (df {:genome :llm-judge} {})))))

(deftest decision-message-shape
  (let [msg (game.llm/decision-message "NARRATION" {:action :call :why "w"})]
    (is (str/starts-with? msg "NARRATION"))
    (is (str/includes? msg "{:action :call"))))
