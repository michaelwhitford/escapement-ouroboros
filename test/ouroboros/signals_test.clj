(ns ouroboros.signals-test
  "Temp-dir round-trip tests for the signal store edge — emit gate, dedupe,
  id-collision bump, reads. No network, no LLM."
  (:require
    [babashka.fs :as fs]
    [clojure.test :refer [deftest is testing]]
    [ouroboros.signals :as signals]))

(defn- with-root [f]
  (let [root (str (fs/create-temp-dir {:prefix "ouro-signals-"}))]
    (try (f root) (finally (fs/delete-tree root)))))

(def report
  {:signal/type   :s1/report
   :signal/data   {:summary "sweep ok" :outcome :ok}
   :signal/lambda "λ sweep → ok"
   :signal/source "test-agent"})

(deftest emit-and-read-round-trip
  (with-root
    (fn [root]
      (let [{:signal/keys [id path written]} (signals/emit! root report)]
        (is (true? written))
        (is (fs/exists? (fs/path root path)))
        (let [[sig] (signals/all-signals root)]
          (is (= id (:signal/id sig)))
          (is (= :s1/report (:signal/type sig)))
          (is (= (:signal/data report) (:signal/data sig)))
          (is (int? (:signal/at sig)) ":at stamped by emit!"))))))

(deftest gate-rejects-nothing-persists
  (with-root
    (fn [root]
      (testing "unknown type"
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"emit gate"
              (signals/emit! root (assoc report :signal/type :nope/nope)))))
      (testing "bad data shape"
        (let [e (try (signals/emit! root (assoc report :signal/data {:summary "x"}))
                     (catch clojure.lang.ExceptionInfo e (ex-data e)))]
          (is (= :data (:signal/error e)))))
      (is (empty? (signals/all-signals root)) "nothing persisted"))))

(deftest dedupe-same-fact
  (with-root
    (fn [root]
      (let [{:signal/keys [id]} (signals/emit! root report)
            e (try (signals/emit! root report)
                   (catch clojure.lang.ExceptionInfo e (ex-data e)))]
        (is (= :duplicate (:signal/error e)))
        (is (= id (:signal/existing e)) "pointer to the existing signal")
        (is (= 1 (count (signals/all-signals root))))))))

(deftest id-collision-bumps-not-overwrites
  (with-root
    (fn [root]
      ;; same ms + same type, DIFFERENT content — both facts must survive
      (let [a (assoc report :signal/at 5000)
            b (assoc report :signal/at 5000
                :signal/data {:summary "different fact" :outcome :fail})]
        (signals/emit! root a)
        (signals/emit! root b)
        (is (= 2 (count (signals/all-signals root))))))))

(deftest filtered-reads
  (with-root
    (fn [root]
      (signals/emit! root (assoc report :signal/at 1))
      (signals/emit! root {:signal/type :human/notice
                           :signal/data {:summary "look"}
                           :signal/source "other-agent"
                           :signal/at 2})
      (signals/emit! root (assoc report :signal/at 3
                            :signal/data {:summary "second sweep" :outcome :ok}))
      (testing "recent — oldest→newest, bounded"
        (let [r (signals/recent root 2)]
          (is (= 2 (count r)))
          (is (= [:human/notice :s1/report] (mapv :signal/type r)))))
      (testing "by-type"
        (is (= 2 (count (signals/by-type root :s1/report))))
        (is (= 1 (count (signals/by-type root :human/notice)))))
      (testing "for-source"
        (is (= 1 (count (signals/for-source root "other-agent"))))
        (is (= 2 (count (signals/for-source root "test-agent")))))
      (testing "absent dir is []"
        (is (= [] (signals/all-signals (str (fs/path root "nowhere")))))))))
