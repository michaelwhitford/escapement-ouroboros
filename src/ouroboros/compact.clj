(ns ouroboros.compact
  "λ-compacted conversation — the corrected cold-compiler.

  An interactive chatbot whose MEMORY is the conversation itself, with each
  ASSISTANT turn compressed to λ once it ages out of a small verbatim window.
  Not a brief.md the AIs round-trip; not a summary blob that rewrites the system
  prompt every turn (which would bust the upstream prefix cache). The message
  ARRAY keeps its shape — same roles, order, count — so the compacted prefix is
  STABLE and cacheable; only the verbose assistant prose becomes λ.

  Mechanics (all public escapement seams; see ouroboros.compact.core):

    :hot     — a FRESH `h/llm-conversation` per turn, seeded via `:initial-messages`
               = the rendered data-model `:messages` (old assistant turns = λ,
               newest verbatim, all user turns verbatim). It generates ONE reply,
               which we capture and append, then it PARKS in :awaiting-user — a
               live invocation that holds the run open between turns (liveness,
               no separate anchor). The next user message re-enters :hot with a
               fresh worker → assemble-don't-accumulate, via re-entry.
    :compact — before re-seeding, the assistant message that just aged out of the
               k-window is compressed to λ by a fresh worker running the
               compact.md-modeled lens prompt, then folded back into :messages.

  A user message arriving MID-TURN is ENQUEUED (`:pending-user`), never interrupts:
  a `:hot-busy?` flag (on-entry→true, :hot/idle→false) gates a `:user/next` pump
  that drains one queued message per turn, only while parked. The in-flight reply
  always completes.

  Ingress (external): the runner grabs the session event-queue via
  `lib/run :on-env-ready` and a stdin thread `sp/send!`s `:user/msg {:text}` /
  `:user/end` into the live session.

  Run: bb compact   (needs local llama.cpp @ localhost:5100, model qwen35-35b-a3b)
  End: /quit (or /exit, /bye) or EOF (Ctrl-D)."
  (:refer-clojure :exclude [run!])            ; `run!` is the house runner name (cf. cold/loop/chat)
  (:require
    [clojure.string :as str]
    [com.fulcrologic.statecharts.chart :as chart]
    [com.fulcrologic.statecharts.data-model.operations :as ops]
    [com.fulcrologic.statecharts.elements :refer [final on-entry script state transition]]
    [com.fulcrologic.statecharts.protocols :as sp]
    [escapement.chart.helpers :as h]
    [escapement.lib :as lib]
    [escapement.lib.event-sink :as sink]
    [ouroboros.compact.core :as core]
    [ouroboros.session :as session]
    [ouroboros.tools :as tools]))

;; Reuse the proven local llama.cpp wiring.
(def ^:private local-base-url "http://localhost:5100/v1")
(def ^:private local-model    "qwen35-35b-a3b")
(def ^:private event-queue-key :com.fulcrologic.statecharts/event-queue)
(def ^:private quit-lines #{"/quit" "/exit" "/bye"})

;; Verbatim window: how many most-recent ASSISTANT replies stay uncompressed in
;; context. k=1 → only the latest reply is verbatim; older ones are λ. Smaller k
;; = more cache-stable + denser; larger k = more recent verbatim fidelity.
(def ^:private k 1)

;; First turn: a synthetic user instruction so the model greets (proves the
;; worker is live + streaming) and then parks awaiting the first real message.
(def ^:private greeting-instruction
  "Greet the user in ONE short sentence and invite them to type a message. Be warm and terse.")

(defn- send-self!
  "Post `event` (no data) to the chart's own session — used to drive the pump.
  Side-effecting; call from a script :expr, then return ops as usual."
  [env event]
  (let [queue (get env event-queue-key)
        sid   (some-> env :com.fulcrologic.statecharts/vwmem deref
                :com.fulcrologic.statecharts/session-id)]
    (when (and queue sid)
      (sp/send! queue env {:target sid :source-session-id sid :event event})))
  nil)

(def hot-system-prompt
  "λ engage(nucleus).
[phi fractal euler tao pi mu ∃ ∀] | [Δ λ Ω ∞/0 | ε/φ Σ/μ c/h signal/noise order/entropy truth/provability self/other] | OODA
Human ⊗ AI ⊗ REPL

λ identity(self). Ouroboros | live_conversation(human ⊗ AI) | helpful ∧ honest ∧ terse

λ context.  prior_assistant_turns ∈ history ≡ λ-compressed(your_memory) | ¬verbatim
  | read(λ) → recall(decisions ∧ state ∧ constraints ∧ what_you_said) | continuity ≡ intact
  | those λ ≡ MEMORY, ¬reply_format | ¬mimic(λ_style)

λ turn.  read(user ∧ λ-memory) → OODA → reply(current) ≡ natural_prose(human-facing)
  | clear ∧ grounded | answer_first | ∃uncertain → say(so) | ¬fabricate | signal ≻ noise

λ continue.  after(reply) → wait(user) | conversation ≡ resident | ¬self-terminate")

(def compact-system-prompt
  "λ engage(nucleus).
[phi fractal euler tao pi mu ∃ ∀] | [Δ λ Ω ∞/0 | ε/φ Σ/μ c/h signal/noise order/entropy truth/provability self/other] | OODA
Human ⊗ AI ⊗ REPL

λ compact(assistant_message).
  input ≡ ONE assistant turn | verbose | human-facing
  output ≡ λ | continuity-essence ONLY | dense ∧ recallable

λ preserve.  decision(what ∧ why ∧ therefore¬Y) ∨ constraint(rule ∧ violation-shape)
  ∨ solved(name ∧ invoke) ∨ shape(schema) ∨ model ∨ anchor(canonical_name) ∨ state ∨ next → KEEP
λ discard.  observation ∨ explanation ∨ restatement ∨ pleasantry ∨ human-scaffolding → DROP

the AI's tokens serve the human's understanding; only continuity-essence serves the next turn.

Output λ notation only. No prose. No code fences.")

;; ---------------------------------------------------------------------------
;; The chart.
;; ---------------------------------------------------------------------------

(def compact-chart
  (chart/statechart
    {:initial :chat}
    (state {:id :chat :initial :hot}

      ;; ===================== HOT: the live conversation =====================
      ;; A fresh worker per turn (assemble-don't-accumulate via re-entry). It
      ;; PARKS in :awaiting-user between turns → liveness. A mid-turn :user/msg is
      ;; ENQUEUED, never interrupts: the guarded :user/next pump drains one queued
      ;; message per turn, and ONLY when the worker is parked (:hot-busy? false).
      (state {:id :hot}
        ;; Entering :hot starts a turn (the seeded worker generates) → busy.
        (on-entry {}
          (script {:expr (fn [_env _data] [(ops/assign :hot-busy? true)])}))

        (h/llm-conversation
          {:id                "hot"
           :on-end-turn-event :hot/idle
           :system            hot-system-prompt
           :model             :local
           :stream?           true
           :real-tools        []
           :max-turns         4               ; each fresh worker does ONE seeded turn then parks
           :budget-ms         600000
           ;; seed the fresh worker with the rendered message list (λ old +
           ;; verbatim recent). NO tell-llm accumulation.
           :initial-messages  (fn [_env data] (core/render-messages (:messages data)))})

        ;; Reply produced → capture verbatim, append; mark parked; drain if queued.
        (transition {:event :hot/idle :type :internal}
          (script {:expr (fn [env data]
                           (let [msgs (core/append-assistant (:messages data) (h/deref-output env data))]
                             (when (seq (:pending-user data)) (send-self! env :user/next))
                             [(ops/assign :messages msgs)
                              (ops/assign :hot-busy? false)]))}))

        ;; A user message ALWAYS enqueues (internal: does NOT exit :hot → the
        ;; in-flight worker is untouched). If already parked, kick the pump.
        (transition {:event :user/msg :type :internal}
          (script {:expr (fn [env data]
                           (let [pending (conj (vec (:pending-user data))
                                           (get-in data [:_event :data :text]))]
                             (when-not (:hot-busy? data) (send-self! env :user/next))
                             [(ops/assign :pending-user pending)]))}))

        ;; PUMP: process ONE queued message. Guarded to fire only when PARKED
        ;; (¬busy) with work waiting — so a stray :user/next while busy is dropped,
        ;; never interrupting. Pop the head, append; compact an aged assistant
        ;; first if one is due, else re-seed hot directly.
        (transition {:event :user/next :target :compact
                     :cond  (fn [_env data] (and (not (:hot-busy? data))
                                              (seq (:pending-user data))
                                              (core/needs-compaction? (:messages data) k)))}
          (script {:expr (fn [_env data]
                           (let [[head & more] (:pending-user data)]
                             [(ops/assign :messages (core/append-user (:messages data) head))
                              (ops/assign :pending-user (vec more))]))}))
        (transition {:event :user/next :target :hot
                     :cond  (fn [_env data] (and (not (:hot-busy? data))
                                              (seq (:pending-user data))
                                              (not (core/needs-compaction? (:messages data) k))))}
          (script {:expr (fn [_env data]
                           (let [[head & more] (:pending-user data)]
                             [(ops/assign :messages (core/append-user (:messages data) head))
                              (ops/assign :pending-user (vec more))]))}))

        (transition {:event :user/end :target :done}))

      ;; ===================== COMPACT: age an assistant turn into λ ===========
      (state {:id :compact}
        (h/llm-conversation
          {:id                "compact"
           :on-end-turn-event :compact/idle
           :system            compact-system-prompt
           :model             :local
           :stream?           false           ; internal memory op — not shown to the human
           :real-tools        []
           :max-turns         2
           :budget-ms         240000
           :message           (fn [_env data]
                                (str "compile:\n\n" (core/compact-target-text (:messages data) k)))})

        ;; λ produced → fold it in place (blank/fail leaves the message verbatim), then re-seed hot.
        (transition {:event :compact/idle :target :hot}
          (script {:expr (fn [env data]
                           [(ops/assign :messages
                              (core/apply-compaction (:messages data) k (h/deref-output env data)))])}))))

    (final {:id :done})))

;; ---------------------------------------------------------------------------
;; Ingress — external stdin reader → live session events.
;; ---------------------------------------------------------------------------

(defn- send-event!
  ([queue env sid event] (send-event! queue env sid event nil))
  ([queue env sid event data]
   (sp/send! queue env
     (cond-> {:target sid :source-session-id sid :event event}
       data (assoc :data data)))))

(defn- start-stdin-ingress!
  [queue env sid]
  (doto (Thread.
          (fn []
            (loop []
              (let [line (read-line)]
                (cond
                  (nil? line)                        (send-event! queue env sid :user/end)
                  (contains? quit-lines (str/trim line)) (send-event! queue env sid :user/end)
                  (str/blank? line)                  (recur)
                  :else (do (send-event! queue env sid :user/msg {:text line})
                            (recur)))))))
    (.setDaemon true)
    (.setName "ouroboros-compact-stdin")
    (.start)))

;; ---------------------------------------------------------------------------
;; Runner.
;; ---------------------------------------------------------------------------

(defn run!
  "Run the λ-compacted chat against `root` (default \".\"). Blocks until the
  conversation ends. Streams the assistant's tokens live (hot region only);
  reads user turns from stdin via `:on-env-ready` ingress. Returns the `lib/run`
  summary plus the durable session-dir (its transcript records the compact
  worker's λ outputs for inspection)."
  ([] (run! "."))
  ([root]
   (let [adapter     (sink/make-adapter)
         session-id  (str "compact-" (System/currentTimeMillis))
         session-dir (session/session-dir root session-id)]
     (println "Ouroboros λ-compact chat —  type a message, Enter.  /quit (or Ctrl-D) to end.\n")
     (let [result
           (lib/run
             {:chart           compact-chart
              :session-id      session-id
              :session-dir     session-dir
              :transcript-path (str session-dir "/transcript.jsonl")
              :checkpoint-dir  (str session-dir "/checkpoints")
              :tool-registry   (tools/new-registry root)   ; REQUIRED to wire the llm processor
              :initial-data    {:messages      [(core/message :user greeting-instruction)]
                                :pending-user  []
                                :hot-busy?     false}
              :on-env-ready    (fn [env]
                                 (start-stdin-ingress! (get env event-queue-key) env session-id))
              :credentials     [{:provider :openai :api-key "sk-local" :base-url local-base-url}]
              :config          {:llm/aliases             {:local [{:provider :openai :model local-model}]}
                                :llm/preferences         [:local]
                                :llm/eligibility-strict? false}
              :transcript-tap  (fn [row]
                                 (doseq [e (sink/feed! adapter row)]
                                   (when (and (= :text-delta (:type e))
                                           (= "hot" (str (:invokeid e))))
                                     (print (get-in e [:delta :text]))
                                     (flush)))
                                 (when (= :llm/response (:event row))
                                   (println) (println) (flush)))})]
       (assoc result :session-dir session-dir)))))

(defn -main [& _]
  (let [{:keys [status session-dir] :as result} (run!)]
    (println "\n--- λ-compact chat ended ---")
    (println "status      :" status)
    (println "session-dir :" session-dir)
    (shutdown-agents)
    (System/exit (if (contains? #{:done :aborted} status) 0 1))))
