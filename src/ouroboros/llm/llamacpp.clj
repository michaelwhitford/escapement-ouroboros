(ns ouroboros.llm.llamacpp
  "A pure-CONSUMER llama.cpp backend for escapement — NO fork required.

  WHY (feed-forward): escapement's typed `Request` is a CLOSED contract, and
  provider-specific llama.cpp body knobs (chat_template_kwargs to disable Qwen
  reasoning, id_slot to pin a KV-cache slot, cache_prompt to reuse a warm
  prefix) have no home in it. Rather than fork the wire layer (the `:extra-body`
  passthrough — PR pending upstream, may never merge), we OWN a backend and
  inject it via `lib/run`'s documented `:backend` escape hatch (wins verbatim
  over the `:credentials`-assembled backend).

  DESIGN — adapt escapement's CACHING pattern, don't passthrough. Escapement
  treats caching as a BACKEND concern driven by lightweight MODELED signals
  (cache-control markers + `:auto-cache?`), realized per-provider inside the
  backend, and normalized to a uniform `Usage` on the way out. We speak that
  same vocabulary — every input is a modeled escapement field, so there is NO
  `:metadata` overloading and NO opaque param map:

    :thinking {:type :disabled}   → chat_template_kwargs {enable_thinking false}
    cache-control marker present  → cache_prompt true   (escapement AUTO-stamps
                                    the marker under :auto-cache? default true;
                                    build-request CONSUMES :auto-cache? to
                                    produce the marker, so the MARKER — not the
                                    flag — is what reaches a backend)
    :conversation/id  ─(slots)→   id_slot N             (escapement's designated
                                    prompt-cache correlation key → a physical
                                    llama.cpp slot via the construction-time
                                    :slots table; slot POLICY lives with the
                                    endpoint, the chart just says WHICH convo)
    usage.cached_tokens           → :cache-read-input-tokens (FREE — we reuse
                                    escapement's `openai-json->response`, so the
                                    cache-report tool works unchanged)

  This is symmetric with escapement's Anthropic backend: it reads cache-control
  markers → emits `cache_control`; we read them → emit `cache_prompt`.

  REUSE BOUNDARY — everything load-bearing is escapement PUBLIC:
    `oai/request->openai-json` (translate) · `oai/openai-json->response` (parse) ·
    the SSE primitives (`stream-acc-init`/`process-sse-line!`/`stream-acc->openai-body`) ·
    `ht/request`/`request-streaming` · `proto/llm-error` · `types/validate-*`.
  Only the private HTTP orchestration (`post-chat!`/`stream-chat!` + 3 error
  helpers) is COPIED here — FROZEN glue insulated from escapement internal churn:
  copying > calling means Tony can rewrite the private originals freely without
  breaking us; we track only the STABLE public backend contract.

  Verify (runtime): mementum/knowledge/llama-cpp-prompt-cache.md — curl :5100/slots
  after a run shows the pinned id_slot's processed tokens; usage.cached_tokens →
  transcript :cache-read-input-tokens → `bb cache-report`."
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [malli.core :as m]
    [malli.error :as me]
    [escapement.llm.http-transport :as ht]
    [escapement.llm.openai :as oai]
    [escapement.llm.protocol :as proto]
    [escapement.llm.types :as types]
    [com.fulcrologic.statecharts.promise :as p]))

;;; ---------------------------------------------------------------------------
;;; The modeled-field → llama.cpp wire translation (pure — the WHOLE point).
;;; Each knob is a NAMED branch sourced from an escapement Request field; the
;;; llama.cpp string vocabulary is quarantined here and nowhere else.
;;; ---------------------------------------------------------------------------

(defn thinking-off?
  "Does this Request ask to SUPPRESS reasoning? True iff escapement's modeled
  `:thinking` is `{:type :disabled}`. (Absent ⇒ false ⇒ we emit nothing ⇒ the
  server/model default stands — behavior-preserving.)"
  [request]
  (= :disabled (:type (:thinking request))))

(defn caching-on?
  "Should llama.cpp reuse the prompt cache for this Request? True iff any
  escapement cache-control marker is present — the same signal escapement's
  Anthropic backend serializes to `cache_control`. Under `:auto-cache?` (default
  true) build-request stamps `:system-cache-control {:type :ephemeral}` on any
  request with a prefix, so this is on by default and opts out cleanly when a
  caller passes `:auto-cache? false`."
  [request]
  (boolean
    (or (:system-cache-control request)
      (some (fn [msg] (or (:cache-control msg)
                        (some :cache-control (:content msg))))
        (:messages request)))))

(defn slot-for
  "The llama.cpp KV slot this Request pins, per the backend's `slots` table
  keyed by escapement's modeled `:conversation/id`. nil ⇒ let the server pick
  (similarity→LRU). `slots` ≡ {conv-id → int}, conv-id a keyword/string/uuid."
  [slots request]
  (get slots (:conversation/id request)))

(defn llama-wire
  "The extra top-level OpenAI-body keys llama.cpp honors, derived from modeled
  Request fields. Pure; empty when nothing applies (no wire change ⇒ identical
  to the stock OpenAI backend)."
  [slots request]
  (let [slot (slot-for slots request)]
    (cond-> {}
      (thinking-off? request) (assoc "chat_template_kwargs" {"enable_thinking" false})
      (caching-on? request)   (assoc "cache_prompt" true)
      (some? slot)            (assoc "id_slot" slot))))

(defn wire-body
  "escapement's PUBLIC OpenAI translator, then MERGE our modeled llama knobs
  LAST (caller-wins). This one merge IS the entire escape hatch — at the right
  seam, with no fork and no opaque passthrough."
  [slots request]
  (merge (oai/request->openai-json request)
    (llama-wire slots request)))

;;; ---------------------------------------------------------------------------
;;; Copied HTTP orchestration — escapement's PRIVATE post-chat!/stream-chat! and
;;; error helpers, FROZEN here. They depend only on the public HttpTransport +
;;; proto/llm-error contract, so copying (not calling) insulates us from churn
;;; in escapement's internals.  (source: escapement.llm.openai, RC9)
;;; ---------------------------------------------------------------------------

(defn- mask-key [k]
  (when k
    (let [s (str k)]
      (if (> (count s) 8)
        (str (subs s 0 4) "..." (subs s (- (count s) 4)))
        "***"))))

(defn- status->category
  "HTTP status (+ best-effort body) → escapement error category, mirroring
  escapement.llm.openai so the retry/backoff/fallback machinery keys off the
  same categories."
  [status body]
  (let [b        (str/lower-case (str body))
        ctx-len? (some #(str/includes? b %)
                   ["prompt is too long" "context length" "context window"
                    "maximum context" "exceeds the maximum" "too many tokens"
                    "reduce the length"])]
    (cond
      (= status 429) :rate-limited
      (= status 529) :overloaded
      (= status 503) :overloaded
      (str/includes? b "overloaded") :overloaded
      (or (= status 401) (= status 403)) :auth
      (and (or (= status 400) (= status 422)) ctx-len?) :context-length
      (or (= status 400) (= status 422)) :invalid-request
      :else :transport)))

(defn- retry-after-ms
  "Parse a `Retry-After` header (seconds or HTTP-date) into ms; nil if absent."
  [headers]
  (when-let [raw (or (get headers "retry-after") (get headers "Retry-After"))]
    (try (some-> (Long/parseLong (str/trim (str raw))) (* 1000))
         (catch Throwable _ nil))))

(defn- with-categorized-timeout
  "Run `f`; rethrow transport failures as categorized `proto/llm-error`s."
  [url f]
  (try
    (f)
    (catch java.net.http.HttpTimeoutException t
      (throw (proto/llm-error :timeout (str "API request timed out: " url)
               {:cause t :data {:url url}})))
    (catch java.net.ConnectException t
      (throw (proto/llm-error :transport (str "API connection failed: " url)
               {:cause t :data {:url url}})))
    (catch java.io.IOException t
      (throw (proto/llm-error :transport (str "API transport error: " url)
               {:cause t :data {:url url}})))))

(defn- post-chat!
  [transport {:keys [base-url api-key extra-headers http-timeout-ms]} body-map]
  (let [url     (str base-url "/chat/completions")
        headers (merge {"Content-Type"  "application/json"
                        "Authorization" (str "Bearer " api-key)}
                  extra-headers)
        req     {:url        url
                 :method     :post
                 :headers    headers
                 :body       (json/generate-string body-map)
                 :timeout-ms (or http-timeout-ms 60000)}
        {:keys [status body headers]}
        (with-categorized-timeout url
          (fn [] (p/await! (ht/request transport req))))]
    (when-not (and (>= status 200) (< status 300))
      (throw (proto/llm-error (status->category status body)
               (str "llama.cpp API error: HTTP " status)
               {:status status
                :data   (cond-> {:status status :body body :url url}
                          (= status 429) (assoc :retry-after-ms (retry-after-ms headers)))})))
    (try
      (json/parse-string body)
      (catch Throwable t
        (throw (proto/llm-error :transport "Failed to parse llama.cpp API JSON response"
                 {:cause t :data {:body body}}))))))

(defn- stream-chat!
  [transport {:keys [base-url api-key extra-headers http-timeout-ms]}
   body-map request-model on-delta]
  (let [url     (str base-url "/chat/completions")
        headers (merge {"Content-Type"  "application/json"
                        "Accept"        "text/event-stream"
                        "Authorization" (str "Bearer " api-key)}
                  extra-headers)
        req     {:url        url
                 :method     :post
                 :headers    headers
                 :body       (json/generate-string
                               (assoc body-map
                                 "stream" true
                                 "stream_options" {"include_usage" true}))
                 :timeout-ms (or http-timeout-ms 60000)}
        acc     (atom (oai/stream-acc-init))
        on-line (fn [line] (oai/process-sse-line! acc line on-delta))
        {:keys [status body headers]}
        (with-categorized-timeout url
          (fn [] (p/await! (ht/request-streaming transport req on-line))))]
    (if (and (>= status 200) (< status 300))
      (oai/openai-json->response (oai/stream-acc->openai-body @acc) (str request-model))
      (throw (proto/llm-error (status->category status body)
               (str "llama.cpp API error: HTTP " status)
               {:status status
                :data   (cond-> {:status status :body body :url url}
                          (= status 429) (assoc :retry-after-ms (retry-after-ms headers)))})))))

;;; ---------------------------------------------------------------------------
;;; The backend record — mirrors escapement.llm.openai/OpenAIBackend, but builds
;;; the wire body through `wire-body` (modeled llama knobs merged last).
;;; ---------------------------------------------------------------------------

(defrecord LlamaCppBackend [opts]
  proto/LLMBackend
  (send-turn [_ request]
    (p/do!
      (let [request (cond-> request
                      (and (nil? (:model request)) (:default-model opts))
                      (assoc :model (:default-model opts)))]
        (when-let [err (types/validate-request request)]
          (throw (ex-info "Invalid LLM request" {:errors err :request request})))
        (let [transport     (or (:http-transport opts) (ht/default-transport))
              transcript-fn (:transcript-fn opts)
              body-map      (wire-body (:slots opts) request)
              _             (when transcript-fn
                              (transcript-fn {:event :llm/request :backend :llamacpp
                                              :base-url (:base-url opts)
                                              :api-key (mask-key (:api-key opts))
                                              :model (:model request) :body body-map}))
              parsed        (post-chat! transport opts body-map)
              response      (oai/openai-json->response parsed (:model request))]
          (when transcript-fn
            (transcript-fn {:event :llm/response :backend :llamacpp :response response}))
          (when-let [err (types/validate-response response)]
            (throw (ex-info "llama.cpp backend produced an invalid response"
                     {:errors err :response response :raw parsed})))
          response))))

  proto/StreamingLLMBackend
  (stream-turn [_ request on-delta]
    (p/do!
      (let [request (cond-> request
                      (and (nil? (:model request)) (:default-model opts))
                      (assoc :model (:default-model opts)))]
        (when-let [err (types/validate-request request)]
          (throw (ex-info "Invalid LLM request" {:errors err :request request})))
        (let [transport     (or (:http-transport opts) (ht/default-transport))
              transcript-fn (:transcript-fn opts)
              body-map      (wire-body (:slots opts) request)
              _             (when transcript-fn
                              (transcript-fn {:event :llm/request :backend :llamacpp
                                              :base-url (:base-url opts)
                                              :api-key (mask-key (:api-key opts))
                                              :model (:model request) :stream true :body body-map}))
              response      (stream-chat! transport opts body-map (:model request) on-delta)]
          (when transcript-fn
            (transcript-fn {:event :llm/response :backend :llamacpp :response response}))
          (when-let [err (types/validate-response response)]
            (throw (ex-info "llama.cpp backend produced an invalid response"
                     {:errors err :response response})))
          response)))))

(def ^:private opts-schema
  "Fail-loud construction gate. :slots is {conv-id → non-neg int} keyed by
  whatever the chart sets as :conversation/id."
  [:map {:closed false}
   [:base-url :string]
   [:api-key {:optional true} :string]
   [:default-model {:optional true} :string]
   [:slots {:optional true} [:map-of :any [:int {:min 0}]]]])

(defn new-backend
  "Construct a llama.cpp backend. Opts:
     :base-url       (required) e.g. \"http://localhost:5100/v1\"
     :api-key        bearer token (llama.cpp ignores it; send a placeholder)
     :default-model  fills a nil Request :model before validation
     :slots          {conv-id → slot-int} — id_slot pinning by :conversation/id
     :extra-headers :http-timeout-ms :http-transport :transcript-fn (optional)
  Inject via `lib/run {:backend (new-backend …)}` — wins verbatim; still pass a
  dummy `:credentials` (schema-required, closed contract)."
  [opts]
  (when-let [err (m/explain opts-schema opts)]
    (throw (ex-info "invalid llama.cpp backend opts" {:opts opts :errors (me/humanize err)})))
  (->LlamaCppBackend opts))
