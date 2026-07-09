(ns ouroboros.session-test
  "Reading sessions back: checkpoint EDN → data-model → the λ :messages array.
  Writes a synthetic escapement-shaped checkpoint into a temp tree and asserts
  the readers extract it, and are nil-safe on absent sessions."
  (:require
    [babashka.fs :as fs]
    [clojure.test :refer [deftest is]]
    [ouroboros.session :as session]))

;; The exact keys escapement snapshots under: the working memory is wrapped in
;; wmem-key (1.0.0-RC+), and the data-model lives under dm-key inside it.
(def ^:private wmem-key :escapement.engine.store/wmem)
(def ^:private dm-key
  :com.fulcrologic.statecharts.data-model.working-memory-data-model/data-model)

(def ^:private sample-messages
  [{:role :user      :text "Pick ONE cache strategy."                  :compacted? false}
   {:role :assistant :text "decision(write-back ∧ perf↑) ∧ state(active)" :compacted? true}
   {:role :user      :text "Which did you pick?"                       :compacted? false}
   {:role :assistant :text "I chose write-back."                       :compacted? false}])

(defn- write-checkpoint! [root id messages]
  (let [dir (fs/path root "sessions" id "checkpoints")]
    (fs/create-dirs dir)
    (spit (str (fs/path dir (str id ".edn")))
      (pr-str {wmem-key {dm-key {:_sessionid id :messages messages}}}))))

(defn- temp-root [] (str (fs/create-temp-dir {:prefix "ouro-session"})))

(deftest session-messages-extracts-lambda-array
  (let [root (temp-root)
        id   "compact-1783525397252"]
    (write-checkpoint! root id sample-messages)
    (is (= sample-messages (session/session-messages root id)))
    (is (= [id] (session/list-session-ids root)))
    (is (some? (session/checkpoint-file root id)))
    (is (= sample-messages (:messages (session/read-data-model root id))))))

(deftest missing-session-is-nil-safe
  (let [root (temp-root)]
    (is (= [] (session/list-session-ids root)))
    (is (nil? (session/checkpoint-file root "nope")))
    (is (nil? (session/read-data-model root "nope")))
    (is (= [] (session/session-messages root "nope")))))
