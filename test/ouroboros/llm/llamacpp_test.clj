(ns ouroboros.llm.llamacpp-test
  "Deterministic tests for the pure modeled-field → llama.cpp wire translation
  (no network). The backend record's HTTP path is exercised by the live
  llama-smoke, not here."
  (:require
    [clojure.test :refer [deftest is testing]]
    [escapement.llm :as llm]
    [ouroboros.llm.llamacpp :as l]
    [ouroboros.models :as models]))

(defn- req
  "Build a real escapement Request through build-request so the auto-cache
  defaulting (→ :system-cache-control marker) fires exactly as in production."
  [& {:as extra}]
  (llm/build-request
    (merge {:model    "qwen36-35b-a3b"
            :system   "you are a test"
            :messages [{:role :user :content [{:type :text :text "hi"}]}]}
      extra)))

(deftest thinking-off-only-on-disabled
  (is (l/thinking-off? (req :thinking {:type :disabled})))
  (is (not (l/thinking-off? (req :thinking {:type :enabled :budget-tokens 1024}))))
  (is (not (l/thinking-off? (req))) "absent :thinking ⇒ false ⇒ server default preserved"))

(deftest caching-on-tracks-cache-control-marker
  (testing "auto-cache default stamps :system-cache-control ⇒ caching-on?"
    (is (l/caching-on? (req))))
  (testing "explicit :auto-cache? false drops the marker ⇒ caching-off"
    (is (not (l/caching-on? (req :auto-cache? false))))))

(deftest slot-for-reads-conversation-id
  (let [slots {:hot 2 :compact 3}]
    (is (= 2 (l/slot-for slots (req :conv-id :hot))))
    (is (= 3 (l/slot-for slots (req :conv-id :compact))))
    (is (nil? (l/slot-for slots (req :conv-id :unmapped))) "unknown convo ⇒ server picks")
    (is (nil? (l/slot-for slots (req))) "no :conversation/id ⇒ nil")))

(deftest llama-wire-maps-each-knob
  (let [w (l/llama-wire {:hot 2} (req :thinking {:type :disabled} :conv-id :hot))]
    (is (= {"enable_thinking" false} (get w "chat_template_kwargs")))
    (is (= true (get w "cache_prompt")))
    (is (= 2 (get w "id_slot"))))
  (testing "nothing applies ⇒ empty ⇒ identical to the stock OpenAI wire"
    (is (= {} (l/llama-wire {} (req :auto-cache? false))))))

(deftest wire-body-merges-llama-knobs-last-over-base-translator
  (let [w (l/wire-body {:hot 2} (req :thinking {:type :disabled} :conv-id :hot))]
    (testing "base translator output is preserved"
      (is (= "qwen36-35b-a3b" (get w "model")))
      (is (some? (get w "messages"))))
    (testing "our modeled knobs are merged on top"
      (is (= {"enable_thinking" false} (get w "chat_template_kwargs")))
      (is (= true (get w "cache_prompt")))
      (is (= 2 (get w "id_slot"))))))

(deftest models-llama-backend-builds-from-alias
  (let [b (models/llama-backend :local {:hot 2 :compact 3})]
    (is (= "http://localhost:5100/v1" (get-in b [:opts :base-url])))
    (is (= "qwen36-35b-a3b" (get-in b [:opts :default-model])))
    (is (= {:hot 2 :compact 3} (get-in b [:opts :slots]))))
  (is (thrown? clojure.lang.ExceptionInfo (models/llama-backend :nonexistent))
    "fail-loud on unknown alias"))

(deftest new-backend-fails-loud-on-bad-opts
  (is (thrown? clojure.lang.ExceptionInfo (l/new-backend {}))
    "missing :base-url ⇒ reject at construction")
  (is (thrown? clojure.lang.ExceptionInfo (l/new-backend {:base-url "x" :slots {:hot -1}}))
    "negative slot ⇒ reject"))
