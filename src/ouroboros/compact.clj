(ns ouroboros.compact
  "λ-compacted conversation — the corrected cold-compiler.

  An interactive chatbot whose MEMORY is the conversation itself, with each
  ASSISTANT turn compressed to λ once it ages out of a small verbatim window.
  Not a brief.md the AIs round-trip; not a summary blob that rewrites the system
  prompt every turn (which would bust the upstream prefix cache). The message
  ARRAY keeps its shape — same roles, order, count — so the compacted prefix is
  STABLE and cacheable; only the verbose assistant prose becomes λ.

  SHADOW COMPACTION (see mementum/knowledge/design/shadow-compaction.md): the
  compactor runs in the human's READING SHADOW — the seconds they spend reading
  reply[n] — so the λ-housekeeping is perceptually free. The metric is felt-latency
  (never make the human wait), not throughput; reading-time (20–60s) ⋙ compaction
  (~2s) gives a 10–30× hiding margin.

  Three states (decomposed — the fused :hot of the earlier build mis-scheduled
  compaction onto the PRE-GEN pump = the one instant the human is blocked):

    :parked  — an EMPTY-seeded worker parks in :awaiting-user (NO LLM call) purely
               to hold lib/run open between turns (liveness). Its content is unused:
               assemble-don't-accumulate kills it and spawns a FRESH :hot worker on
               the next user message. (empty :initial-messages ⇒ worker-state
               :awaiting-user; a parked worker counts as live for the runner.)
    :hot     — a FRESH `h/llm-conversation` seeded via `:initial-messages` = the
               rendered `:messages` (old assistant turns = λ, newest verbatim, all
               user turns verbatim). Generates ONE reply; we capture + append, then
               settle: → :compact if a turn aged out, else serve a queued human,
               else → :parked.
    :compact — a fresh worker compresses the just-aged assistant turn to λ (the
               compact.md-modeled lens), folds it back in place, then settles:
               serve a queued human first (→ :hot), else drain backlog (→ :compact),
               else → :parked.

  A user message arriving mid-turn / mid-compaction ENQUEUES onto `:pending-user`
  (an `:internal` transition that never exits the generating/compacting state) and
  is drained one-per-settle — the in-flight worker always completes, never barged.

  Ingress (external): the runner grabs the session event-queue via
  `lib/run :on-env-ready` and a stdin thread `sp/send!`s `:user/msg {:text}` /
  `:user/end` into the live session.

  Run: bb compact   (needs local llama.cpp @ localhost:5100, model qwen36-35b-a3b)
  End: /quit (or /exit, /bye) or EOF (Ctrl-D)."
  (:refer-clojure :exclude [run!])            ; `run!` is the house runner name (cf. cold/loop/chat)
  (:require
    [clojure.string :as str]
    [com.fulcrologic.statecharts.chart :as chart]
    [com.fulcrologic.statecharts.data-model.operations :as ops]
    [com.fulcrologic.statecharts.elements :refer [final script state transition]]
    [com.fulcrologic.statecharts.protocols :as sp]
    [escapement.chart.helpers :as h]
    [escapement.lib :as lib]
    [escapement.lib.event-sink :as sink]
    [ouroboros.compact.core :as core]
    [ouroboros.session :as session]
    [ouroboros.tools :as tools]))

;; Reuse the proven local llama.cpp wiring.
(def ^:private local-base-url "http://localhost:5100/v1")
(def ^:private local-model    "qwen36-35b-a3b")
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

(defn- enqueue-user
  "Ops appending the incoming :user/msg text onto the :pending-user queue.
  Used by the `:internal` :user/msg handler in every state — enqueue never
  interrupts an in-flight worker."
  [data]
  [(ops/assign :pending-user
     (conj (vec (:pending-user data)) (get-in data [:_event :data :text])))])

(defn- serve-pending
  "Ops popping the head of :pending-user into :messages as a user turn — the
  assemble-don't-accumulate seed for the next :hot generation. Caller guards on
  (seq (:pending-user data))."
  [data]
  (let [[head & more] (:pending-user data)]
    [(ops/assign :messages (core/append-user (:messages data) head))
     (ops/assign :pending-user (vec more))]))

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
    (state {:id :chat :initial :hot}      ; boot straight into :hot to generate the greeting

      ;; ===================== PARKED: liveness between turns =================
      ;; An EMPTY-seeded worker parks in :awaiting-user (no LLM call) and holds
      ;; lib/run open while the human reads/thinks. Its content is unused —
      ;; the next user message kills it and spawns a FRESH :hot worker.
      (state {:id :parked}
        (h/llm-conversation
          {:id                "parked"
           :on-end-turn-event :parked/idle          ; never fires — the worker only parks
           :system            hot-system-prompt
           :model             :local
           :stream?           false
           :real-tools        []
           :max-turns         1
           :budget-ms         3600000               ; hold liveness across long idle gaps
           :initial-messages  (fn [_env _data] [])}) ; empty ⇒ :awaiting-user, no generation

        ;; Idle → a user message enqueues and kicks the pump immediately.
        (transition {:event :user/msg :type :internal}
          (script {:expr (fn [env data]
                           (send-self! env :user/next)
                           (enqueue-user data))}))
        (transition {:event :user/next :target :hot
                     :cond  (fn [_env data] (seq (:pending-user data)))}
          (script {:expr (fn [_env data] (serve-pending data))}))
        (transition {:event :user/end :target :done}))

      ;; ===================== HOT: generate ONE reply ========================
      ;; A fresh worker per turn (assemble-don't-accumulate via re-entry), seeded
      ;; with the rendered message list (λ old + verbatim recent). NO tell-llm
      ;; accumulation. Entered with :messages ending in a USER turn ⇒ it generates.
      (state {:id :hot}
        (h/llm-conversation
          {:id                "hot"
           :on-end-turn-event :hot/idle
           :system            hot-system-prompt
           :model             :local
           :stream?           true
           :real-tools        []
           :max-turns         2               ; one seeded turn; killed on the settle transition
           :budget-ms         600000
           :initial-messages  (fn [_env data] (core/render-messages (:messages data)))})

        ;; Reply produced → capture verbatim + append, then settle (via a self-
        ;; event so the routing conds see the APPENDED array).
        (transition {:event :hot/idle :type :internal}
          (script {:expr (fn [env data]
                           (send-self! env :turn/settled)
                           [(ops/assign :messages
                              (core/append-assistant (:messages data) (h/deref-output env data)))])}))

        ;; SETTLE: compact the just-aged turn FIRST (the reading shadow), else
        ;; serve a queued human, else park. Compaction is prioritized because it
        ;; is cheap and hides under the human's read of the reply just produced.
        (transition {:event :turn/settled :target :compact
                     :cond  (fn [_env data] (core/needs-compaction? (:messages data) k))})
        (transition {:event :turn/settled :target :hot
                     :cond  (fn [_env data] (and (not (core/needs-compaction? (:messages data) k))
                                              (seq (:pending-user data))))}
          (script {:expr (fn [_env data] (serve-pending data))}))
        (transition {:event :turn/settled :target :parked
                     :cond  (fn [_env data] (and (not (core/needs-compaction? (:messages data) k))
                                              (empty? (:pending-user data))))})

        ;; Mid-turn user message ENQUEUES (internal: does NOT exit :hot → the
        ;; in-flight worker is untouched); drained at the next settle.
        (transition {:event :user/msg :type :internal}
          (script {:expr (fn [_env data] (enqueue-user data))}))
        (transition {:event :user/end :target :done}))

      ;; ===================== COMPACT: age ONE turn into λ ===================
      ;; Runs in the reading shadow. A fresh worker compresses the aged assistant
      ;; turn; blank/failed λ leaves it verbatim (lag-safe).
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

        ;; λ produced → fold in place + settle (self-event so conds see the fold).
        (transition {:event :compact/idle :type :internal}
          (script {:expr (fn [env data]
                           (send-self! env :compact/settled)
                           [(ops/assign :messages
                              (core/apply-compaction (:messages data) k (h/deref-output env data)))])}))

        ;; SETTLE: serve a human who queued during compaction FIRST (never make
        ;; them wait for backlog); else drain more backlog; else park.
        (transition {:event :compact/settled :target :hot
                     :cond  (fn [_env data] (seq (:pending-user data)))}
          (script {:expr (fn [_env data] (serve-pending data))}))
        (transition {:event :compact/settled :target :compact
                     :cond  (fn [_env data] (and (empty? (:pending-user data))
                                              (core/needs-compaction? (:messages data) k)))})
        (transition {:event :compact/settled :target :parked
                     :cond  (fn [_env data] (and (empty? (:pending-user data))
                                              (not (core/needs-compaction? (:messages data) k))))})

        ;; Fast human typing during compaction ENQUEUES, never interrupts.
        (transition {:event :user/msg :type :internal}
          (script {:expr (fn [_env data] (enqueue-user data))}))
        (transition {:event :user/end :target :done})))

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
                                :pending-user  []}
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
