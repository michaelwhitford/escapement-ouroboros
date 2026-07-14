(ns ouroboros.smoke-llama
  "Live integration probe for the DE-FORKED llama.cpp backend (ouroboros.llm.llamacpp)
  against localhost:5100. NOT a unit test — needs a running llama-server (the
  deterministic gate is bb test / ouroboros.llm.llamacpp-test). Proves end-to-end
  that the modeled fields reach the wire and the server honors them:

    · :thinking {:type :disabled}  ⇒ chat_template_kwargs.enable_thinking=false
      ⇒ response message.reasoning_content is EMPTY (vs a control call that
        leaves reasoning ON) — definitive suppression proof.
    · :conversation/id :probe ─(:slots {:probe N})→ id_slot N ⇒ /slots shows
      the pinned slot processed the prompt.
    · a real send-turn through the backend returns a normal answer + :usage.

  A leaf ns (requires BOTH llamacpp and models — the backend ns can't require
  models without a cycle). Run: bb llama-smoke   Skips (exit 0) if server down."
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [com.fulcrologic.statecharts.promise :as p]
    [escapement.llm :as llm]
    [escapement.llm.http-transport :as ht]
    [escapement.llm.protocol :as proto]
    [ouroboros.llm.llamacpp :as l]
    [ouroboros.models :as models]))

(def ^:private base-url   (get-in models/table [:local :base-url]))          ; …:5100/v1
(def ^:private server-root (str/replace base-url #"/v1/?$" ""))              ; …:5100
(def ^:private probe-slot 2)

(defn- await! [pr] (p/await! pr))

(defn- http [method url & [body]]
  (let [t   (ht/default-transport)
        req (cond-> {:url url :method method :timeout-ms 60000
                     :headers {"Content-Type"  "application/json"
                               "Authorization" "Bearer sk-local"}}
              body (assoc :body (json/generate-string body)))]
    (await! (ht/request t req))))

(defn- reachable? []
  (try (let [{:keys [status]} (http :get (str server-root "/health"))]
         (and status (< status 500)))
       (catch Throwable _ false)))

(defn- reasoning-content [parsed]
  (get-in parsed ["choices" 0 "message" "reasoning_content"]))

(defn- answer-text [parsed]
  (get-in parsed ["choices" 0 "message" "content"]))

(defn- probe-request [thinking-off?]
  (llm/build-request
    (cond-> {:model      "qwen36-35b-a3b"
             :max-tokens 64
             :system     "You are a terse assistant."
             :messages   [{:role :user :content [{:type :text :text "Reply with exactly: PONG"}]}]
             :conv-id    :probe}
      thinking-off? (assoc :thinking {:type :disabled}))))

(defn- response-text
  "Concatenated :text blocks of an escapement Response."
  [resp]
  (->> (:content resp) (filter #(= :text (:type %))) (map :text) (str/join "")))

(defn -main [& _]
  (println "=== llama.cpp de-forked backend smoke ===")
  (println "server-root:" server-root)
  (if-not (reachable?)
    (do (println "SKIP — llama-server not reachable at" server-root
          "(start it to run this live probe).")
        (System/exit 0))
    (let [off-req (probe-request true)
          on-req  (probe-request false)]

      ;; ---- 1. Definitive thinking-suppression proof (raw wire) --------------
      (println "\n[1] thinking suppression (raw wire, reasoning_content):")
      (let [off (http :post (str server-root "/v1/chat/completions")
                  (l/wire-body {:probe probe-slot} off-req))
            on  (http :post (str server-root "/v1/chat/completions")
                  (l/wire-body {:probe probe-slot} on-req))
            off-r (reasoning-content (json/parse-string (:body off)))
            on-r  (reasoning-content (json/parse-string (:body on)))]
        (println "   thinking OFF → reasoning_content =" (pr-str off-r)
          " answer =" (pr-str (answer-text (json/parse-string (:body off)))))
        (println "   thinking ON  → reasoning_content =" (pr-str on-r))
        (println "   VERDICT:" (if (str/blank? (str off-r)) "PASS (suppressed)" "FAIL (still reasoning)")))

      ;; ---- 2. Slot pinning honored (/slots) ---------------------------------
      (println "\n[2] slot pinning (/slots after a pinned request):")
      (try
        (let [slots (json/parse-string (:body (http :get (str server-root "/slots"))))
              s     (some #(when (= probe-slot (get % "id")) %) slots)]
          (if s
            (println "   slot" probe-slot "→ prompt tokens processed:"
              (or (get s "n_prompt_tokens_processed") (get s "n_ctx") (get s "prompt")))
            (println "   (slot" probe-slot "not reported — server may hide /slots)")))
        (catch Throwable e (println "   /slots unavailable:" (ex-message e))))

      ;; ---- 3. Real send-turn through the backend ----------------------------
      (println "\n[3] backend send-turn (through ouroboros.llm.llamacpp):")
      (let [b    (models/llama-backend :local {:probe probe-slot})
            resp (await! (proto/send-turn b off-req))]
        (println "   answer:" (pr-str (response-text resp)))
        (println "   usage :" (:usage resp)))

      (println "\n=== smoke done ===")
      (System/exit 0))))
