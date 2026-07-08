(ns ouroboros.curator.core-test
  "Pure metabolize kernel: recency ordering, λ-marked rendering, empty-safe digest."
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is]]
    [ouroboros.curator.core :as core]))

(deftest recency-key-orders-by-trailing-epoch
  (is (= 1783525397252 (core/recency-key "compact-1783525397252")))
  (is (= 0 (core/recency-key "no-digits")))
  (is (< (core/recency-key "chat-1783486440175")
        (core/recency-key "compact-1783525397252"))
    "across differing prefixes, the epoch suffix orders sessions"))

(deftest render-session-marks-compacted-lambda
  (let [msgs [{:role :user :text "q" :compacted? false}
              {:role :assistant :text "λessence" :compacted? true}]
        out  (core/render-session "compact-1" msgs)]
    (is (str/includes? out "SESSION compact-1 (2 msgs, 1 λ-compacted)"))
    (is (re-find #"λ\s+\|\s+λessence" out) "compacted assistant turn marked λ")
    (is (str/includes? out "user | q"))))

(deftest render-session-clips-long-verbatim
  (let [long (apply str (repeat 2000 "x"))
        out  (core/render-session "s" [{:role :assistant :text long :compacted? false}])]
    (is (str/includes? out "…[clipped]"))
    (is (< (count out) 1000) "a long verbatim reply is bounded")))

(deftest sessions-digest-empty-safe-and-joins
  (is (str/includes? (core/sessions-digest []) "no prior conversation"))
  (let [d (core/sessions-digest
            [{:id "s1" :messages [{:role :assistant :text "A" :compacted? true}]}
             {:id "s2" :messages [{:role :user :text "B" :compacted? false}]}])]
    (is (str/includes? d "SESSION s1"))
    (is (str/includes? d "SESSION s2"))))
