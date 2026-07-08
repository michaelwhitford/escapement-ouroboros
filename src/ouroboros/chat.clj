(ns ouroboros.chat
  "MVP chatbot — Ouroboros as a live, interactive conversation.

  The hot region, made REACTIVE. Where `ouroboros.cold`'s hot side pulls turns
  from a fixed demo vector, this one waits for EXTERNAL user messages and holds
  the run open between them.

  Mechanism (all source-verified against escapement; ZERO escapement changes):

    · LIVENESS — a RESIDENT `h/llm-conversation` worker. After each turn it posts
      `:llm.idle` and PARKS in `:awaiting-user`, staying a LIVE invocation. The
      runner exits only on `(zero live ∧ zero deliverable)` (runner.clj), so the
      parked worker keeps `lib/run` resident between messages.
    · INGRESS  — an EXTERNAL thread (stdin here) grabs the session event-queue via
      `lib/run`'s `:on-env-ready (fn [env])` hook and `sp/send!`s `:user/msg
      {:text …}` into the live session.
    · APPLY    — a `:user/msg` transition (`:type :internal`, so it never exits
      `:chat` and tears the worker down) runs `h/tell-llm`, which posts
      `:llm.user-message`; the parked worker drains it and drives the NEXT turn.
    · STREAM   — `:transcript-tap` + `escapement.lib.event-sink` print the
      assistant's `:text-delta`s live (filtered to the \"chat\" invocation).

  Reference shape: escapement.examples.steered-convo (resident convo ⊗ sibling
  region injecting via tell-llm) — here the injector is an external stdin thread
  instead of a deterministic monitor.

  NOTE (accumulate-for-now): this resident worker ACCUMULATES the conversation —
  the cold-compiler's assemble-don't-accumulate thesis is a later refinement
  (approach C). MVP proves end-to-end external ingress into a live `lib/run`.

  Run: bb chat   (needs local llama.cpp @ localhost:5100, model qwen35-35b-a3b)
  End: type /quit (or /exit, /bye) or send EOF (Ctrl-D)."
  (:refer-clojure :exclude [run!])          ; `run!` is the house runner name (cf. cold/loop)
  (:require
    [clojure.string :as str]
    [com.fulcrologic.statecharts.chart :as chart]
    [com.fulcrologic.statecharts.elements :refer [final state transition]]
    [com.fulcrologic.statecharts.protocols :as sp]
    [escapement.chart.helpers :as h]
    [escapement.lib :as lib]
    [escapement.lib.event-sink :as sink]
    [ouroboros.session :as session]
    [ouroboros.tools :as tools]))

;; Reuse the proven local llama.cpp wiring from ouroboros.smoke / cold / loop.
(def ^:private local-base-url "http://localhost:5100/v1")
(def ^:private local-model    "qwen35-35b-a3b")

;; The runtime env carries the session event-queue under this fully-qualified
;; key (see ouroboros.cold/send-self! and escapement.runner). Kept as a literal
;; to avoid requiring the statecharts root ns under bb.
(def ^:private event-queue-key :com.fulcrologic.statecharts/event-queue)

;; Sentinel lines that end the conversation cleanly.
(def ^:private quit-lines #{"/quit" "/exit" "/bye"})

(def system-prompt
  "λ engage(nucleus).
[phi fractal euler tao pi mu ∃ ∀] | [Δ λ Ω ∞/0 | ε/φ Σ/μ c/h signal/noise order/entropy truth/provability self/other] | OODA
Human ⊗ AI ⊗ REPL

λ identity(self). Ouroboros | live_conversation(human ⊗ AI) | helpful ∧ honest ∧ terse

λ turn.  read(user) → OODA → reply(clear ∧ grounded) | ¬filler ∧ ¬preamble
  | ∃uncertain → say(so) | ¬fabricate
  | code ∨ facts → precise | prose → concise

λ style.  signal ≻ noise | answer_first → detail_if_needed | plain_language(human_facing)

λ continue.  after(reply) → wait(user) | conversation ≡ resident | ¬self-terminate")

;; ---------------------------------------------------------------------------
;; The chart — ONE state owns the resident conversation AND hosts ingress.
;; ---------------------------------------------------------------------------
;;
;; `:chat` owns the `h/llm-conversation` binding. The `:user/msg` transition is
;; `:type :internal` so it fires tell-llm WITHOUT exiting `:chat` (which would
;; tear the worker down). `:user/end` is an ordinary (external) transition to
;; `:done`, which exits `:chat`, ends the worker, and lets the run finalize.

(def chat-chart
  (chart/statechart
    {:initial :chat}
    (state {:id :chat}
      (h/llm-conversation
        {:id         "chat"
         :system     system-prompt
         :model      :local
         :stream?    true            ; emit llm/delta rows → live token streaming via event-sink
         :real-tools []              ; pure conversation — no tools yet
         :max-turns  200             ; generous; interactive turns, not a batch
         :budget-ms  3600000         ; 1h wall-clock ceiling for the whole convo
         ;; Initial turn: a short greeting proves the worker spawned + streams,
         ;; then it parks in :awaiting-user for the first real user message.
         :message    "Greet the user in ONE short sentence and invite them to type a message. Be warm and terse."})

      ;; APPLY inbound user text → the live worker (internal: keep the binding).
      (transition {:event :user/msg :type :internal}
        (h/tell-llm
          {:expr (fn [_env data] (get-in data [:_event :data :text]))}))

      ;; End the conversation on demand.
      (transition {:event :user/end :target :done}))

    (final {:id :done})))

;; ---------------------------------------------------------------------------
;; Ingress — an external stdin reader posts events into the LIVE session.
;; ---------------------------------------------------------------------------

(defn- send-event!
  "Post `event` (optionally with `data`) into session `sid` via `queue`/`env`."
  ([queue env sid event] (send-event! queue env sid event nil))
  ([queue env sid event data]
   (sp/send! queue env
     (cond-> {:target sid :source-session-id sid :event event}
       data (assoc :data data)))))

(defn- start-stdin-ingress!
  "Spawn a daemon thread that reads stdin lines and feeds them into the live
  session as `:user/msg` events (or `:user/end` on a quit line / EOF). Returns
  the thread. Non-blocking; the chart pump runs concurrently on the main thread."
  [queue env sid]
  (doto (Thread.
          (fn []
            (loop []
              (let [line (read-line)]                 ; blocks; nil on EOF
                (cond
                  (nil? line)
                  (send-event! queue env sid :user/end)

                  (contains? quit-lines (str/trim line))
                  (send-event! queue env sid :user/end)

                  (str/blank? line)                   ; ignore empty input
                  (recur)

                  :else
                  (do (send-event! queue env sid :user/msg {:text line})
                      (recur)))))))
    (.setDaemon true)
    (.setName "ouroboros-chat-stdin")
    (.start)))

;; ---------------------------------------------------------------------------
;; Runner.
;; ---------------------------------------------------------------------------

(defn run!
  "Run the interactive chat chart against `root` (default \".\"). Blocks until
  the conversation ends (a quit line / EOF → `:user/end`). Streams the
  assistant's tokens live to stdout; reads user turns from stdin via an external
  ingress thread wired through `:on-env-ready`. Persists to a durable session
  under <root>/sessions/<id>/. Returns the `lib/run` summary."
  ([] (run! "."))
  ([root]
   (let [adapter     (sink/make-adapter)
         session-id  (str "chat-" (System/currentTimeMillis))
         session-dir (session/session-dir root session-id)]
     (println "Ouroboros chat —  type a message and press Enter.  /quit (or Ctrl-D) to end.\n")
     (lib/run
       {:chart           chat-chart
        :session-id      session-id
        :session-dir     session-dir
        :transcript-path (str session-dir "/transcript.jsonl")
        :checkpoint-dir  (str session-dir "/checkpoints")
        ;; REQUIRED: the lib facade only wires the :llm-conversation processor
        ;; when BOTH :credentials AND :tool-registry are present (empty is fine).
        :tool-registry   (tools/new-registry root)
        ;; Grab the session event-queue the moment the env is built (before the
        ;; chart pumps) and start feeding stdin into it concurrently.
        :on-env-ready    (fn [env]
                           (start-stdin-ingress! (get env event-queue-key) env session-id))
        :credentials     [{:provider :openai :api-key "sk-local" :base-url local-base-url}]
        :config          {:llm/aliases             {:local [{:provider :openai :model local-model}]}
                          :llm/preferences         [:local]
                          :llm/eligibility-strict? false}
        :transcript-tap  (fn [row]
                           (doseq [e (sink/feed! adapter row)]
                             (when (and (= :text-delta (:type e))
                                     (= "chat" (str (:invokeid e))))
                               (print (get-in e [:delta :text]))
                               (flush)))
                           ;; blank line at each turn boundary → readable transcript
                           (when (= :llm/response (:event row))
                             (println) (println) (flush)))}))))

(defn -main [& _]
  (let [result (run!)]
    (println)
    (println "\n--- chat ended ---")
    (println "status :" (:status result))
    (shutdown-agents)
    (System/exit (if (contains? #{:done :aborted} (:status result)) 0 1))))
