(ns ouroboros.proposals-test
  "The proposal channel: Malli-gated propose!, pending reads (algedonic
  first), inbox rendering. Temp dirs, no git dependence in the gated path."
  (:require
    [babashka.fs :as fs]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [ouroboros.proposals :as proposals]))

(defn- with-root [f]
  (let [root (str (fs/create-temp-dir {:prefix "ouro-proposals-"}))]
    (try (f root) (finally (fs/delete-tree root)))))

(defn- doc [severity]
  (str "---\n"
       "type: ouroboros/proposal\n"
       "description: tighten the chat genome tool clause\n"
       "target: src/ouroboros/agents/chat.md\n"
       "evidence: [compact-111, compact-222]\n"
       "severity: " severity "\n"
       "---\n"
       "🔁 problem → change-sketch → expected-effect → confidence\n"))

(deftest propose-round-trip
  (with-root
    (fn [root]
      (let [r (proposals/propose! root "chat-tools" (doc "ordinary"))]
        (is (true? (:proposal/written r)))
        (is (fs/exists? (fs/path root (:proposal/path r))))
        (let [[p] (proposals/pending root)]
          (is (= "chat-tools" (:slug p)))
          (is (= :ordinary (:severity p)))
          (is (= ["compact-111" "compact-222"] (:evidence p)))
          (is (= "open" (:status p)) "status defaults open"))))))

(deftest gate-rejections-structured
  (with-root
    (fn [root]
      (testing "wrong type"
        (let [e (try (proposals/propose! root "x"
                       (str/replace (doc "ordinary") "ouroboros/proposal" "nope"))
                     (catch clojure.lang.ExceptionInfo e (ex-data e)))]
          (is (= :invalid (:proposal/error e)))))
      (testing "bad severity"
        (let [e (try (proposals/propose! root "x" (doc "urgent"))
                     (catch clojure.lang.ExceptionInfo e (ex-data e)))]
          (is (= :invalid (:proposal/error e)))
          (is (some? (get-in e [:errors :severity])))))
      (testing "blank body"
        (let [d (str (first (str/split (doc "ordinary") #"🔁")))
              e (try (proposals/propose! root "x" d)
                     (catch clojure.lang.ExceptionInfo e (ex-data e)))]
          (is (some? (get-in e [:errors :body])))))
      (is (empty? (proposals/pending root)) "nothing persisted"))))

(deftest re-propose-pending-rejected
  (with-root
    (fn [root]
      (proposals/propose! root "dup" (doc "ordinary"))
      (let [e (try (proposals/propose! root "dup" (doc "ordinary"))
                   (catch clojure.lang.ExceptionInfo e (ex-data e)))]
        (is (= :pending (:proposal/error e)))
        (is (= "dup" (:proposal/existing e)))))))

(deftest algedonic-surfaces-first
  (with-root
    (fn [root]
      (proposals/propose! root "aaa-ordinary" (doc "ordinary"))
      (proposals/propose! root "zzz-alarm" (doc "algedonic"))
      (let [ps (proposals/pending root)]
        (is (= ["zzz-alarm" "aaa-ordinary"] (mapv :slug ps))
          "algedonic screams first regardless of name order"))
      (let [inbox (proposals/render-inbox (proposals/pending root) ["mementum/memories/m.md"])]
        (is (str/includes? inbox "🚨 ALGEDONIC"))
        (is (str/includes? inbox "UNCOMMITTED MEMORY CANDIDATES (1)"))
        (is (< (str/index-of inbox "zzz-alarm") (str/index-of inbox "aaa-ordinary")))))))

(deftest empty-inbox-safe
  (with-root
    (fn [root]
      (is (= [] (proposals/pending root)))
      (let [inbox (proposals/render-inbox [] [])]
        (is (str/includes? inbox "PROPOSALS (0"))
        (is (str/includes? inbox "(none)"))))))
