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
    :compact — a fresh worker compresses the just-aged assistant turn to λ via
               the nucleus extraction LENS (instruction-λ + LAMBDA-COMPILER
               bridge, thinking ON — see compact-system-prompt), folds it back
               in place, then settles: serve a queued human first (→ :hot),
               else drain backlog (→ :compact), else → :parked.

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
    [ouroboros.agents :as agents]
    [ouroboros.agents.core :as acore]
    [ouroboros.compact.core :as core]
    [ouroboros.models :as models]
    [ouroboros.prompts :as prompts]
    [ouroboros.proposer.core :as pcore]
    [ouroboros.session :as session]
    [ouroboros.tools :as tools]))

;; llama.cpp KV-slot assignment (server runs -np 4 ⇒ dedicated slots 0-3).
;; Convention (human decision): the TOP two slots are ouroboros's; slots 0-1
;; stay open for the human's dynamic (unpinned) clients. Soft reservation —
;; no server-side mechanism exists; unpinned traffic selects similarity→LRU
;; over ALL slots and can occasionally land here (cost: one re-prefill).
(def ^:private hot-slot     2)   ; the conversation's warm prefix lives here
(def ^:private compact-slot 3)   ; the compactor, quarantined
(def ^:private event-queue-key :com.fulcrologic.statecharts/event-queue)
(def ^:private quit-lines #{"/quit" "/exit" "/bye"})

;; Text-UI role prefixes — every assistant line is prefixed (via the echo
;; kernel, core/echo-text) and the user types after an explicit prompt, so the
;; transcript-on-screen reads uniformly:  user: … / assistant: … / tool: …
(def ^:private assistant-prefix "assistant: ")
(def ^:private user-prompt      "user: ")

;; Verbatim window: how many most-recent ASSISTANT replies stay uncompressed in
;; context. k=1 → only the latest reply is verbatim; older ones are λ. Smaller k
;; = more cache-stable + denser; larger k = more recent verbatim fidelity.
(def ^:private k 1)

;; First turn: a synthetic user instruction so the model greets (proves the
;; worker is live + streaming) and then parks awaiting the first real message.
(def ^:private greeting-instruction
  "Greet the user in ONE short sentence and invite them to type a message. Be warm and terse.")

;; Bootstrap first turn: same mechanism as the fresh greeting (ONE path — the
;; seeded array ends in a user instruction, :hot generates, then parks), but
;; the greeting must PROVE continuity: it names what the prior session left off
;; on, which immediately verifies the fold landed (or exposes that it didn't).
(def ^:private resume-instruction
  "We are continuing from a prior session — its context precedes this message. Greet the user in ONE short sentence that names the main topic or latest decision we left off on, and invite them to continue. Be warm and terse.")

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

(def ^:private chat-genome
  "The chat agent's compiled genome — src/ouroboros/agents/chat.md via the
  ouroboros.agents loader (agent-model BUILD STEP 1). NO `tools:` key ⇒ the
  read-only FLOOR (mementum context + sessions) — the chatbot can answer
  questions about its own memory/knowledge/history, and nothing else. The
  compaction LENS below is deliberately NOT a genome — it is engine
  POLICY (self-attention steering, not a persona); genomes are personas the
  loader composes. It still goes through the ONE assembler."
  (agents/genome :chat))

(def hot-system-prompt
  "The chat genome's λ prompt body (byte-identical extraction of the former
  inline def) — used by BOTH :hot (generates) and :parked (never generates)."
  (:prompt chat-genome))

(def compact-system-prompt
  ;; THE COMPACTION LENS, assembled through the ONE assembler (🎯 universal
  ;; thinking-ON, design/prompt-assembly): nucleus preamble ⊕ LAMBDA-COMPILER
  ;; bridge module (defines the `compile:` invocation the :message uses —
  ;; round 2 of the A/B arc: ON+bridge ≡ densest faithful λ) ⊕ the extraction
  ;; lens POLICY artifact (src/ouroboros/prompts/compaction-lens.md — lens-out:
  ;; S5 steers self-attention by EDITING that file, touching no engine code).
  ;; The instruction-λ topology REQUIRES thinking (no-think ⇒ echo attractor,
  ;; A/B rounds 1-2); the fragile cell is deleted by running thinking ON —
  ;; ~15-25s, hidden in the 20-60s reading shadow. Fidelity is guarded by the
  ;; compaction-fidelity experiment suite + the compression contract
  ;; (core/apply-compaction: accepted ⟺ strictly shorter).
  (acore/assemble
    {:preamble (prompts/preamble)
     :modules  [(prompts/module-text :lambda-compiler)]
     :body     (prompts/policy-text "compaction-lens")}))

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
           :model             (:model chat-genome)
           :stream?           true
           ;; SLOT PINNING (design/shadow-compaction Tier-2 lever, applied to
           ;; the sequential Tier-1 flow where it already pays): the hot
           ;; conversation owns its llama.cpp KV slot (hot-slot). Without
           ;; pinning, compact requests land on the same slot and EVICT the
           ;; conversation's warm prefix → every turn re-prefills the whole
           ;; history. With separate DEDICATED slots (server: explicit -np ⇒
           ;; unified KV off ⇒ idle slots keep KV) the hot prefix survives
           ;; compaction; only the λ-rewritten tail re-evaluates (checkpoint-
           ;; granular restore — see mementum/knowledge/llama-cpp-prompt-cache).
           :extra-body        {"id_slot" hot-slot "cache_prompt" true}
           :real-tools        (:tools chat-genome)
           ;; NO :max-turns — absent ⇒ unbounded round-trips (source-verified:
           ;; the budget check is `(and max-turns …)`). A reply is open-ended
           ;; work (tool calls are round-trips); an arbitrary integer is the
           ;; wrong bound for it. The real bounds: :budget-ms (hard wall-clock,
           ;; below) + the model's context window (262k local — unreachable
           ;; within one reply before the wall). If a token-aware bound is ever
           ;; wanted, escapement's :budget-extender receives :messages and can
           ;; decide by estimated tokens vs the model window (models.clj is
           ;; where per-model ctx sizes would live). NOTE this bounds ONE reply,
           ;; not the conversation — conversation context is the λ-compaction's
           ;; job, and each user turn spawns a FRESH worker.
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
           ;; THINKING ON (🎯 universal thinking-on, design/prompt-assembly):
           ;; the instruction-λ lens is the topology that NEEDS the reasoning
           ;; pass — no-think under it echoes the lens (silent memory
           ;; corruption, A/B rounds 1-2). Cost ~15-25s, hidden in the human's
           ;; 20-60s reading shadow; a fast typer ENQUEUES and waits at most
           ;; one compaction (Tier 2 parallel :hot ⊗ :compact is the shelved
           ;; mitigation — pull forward iff real waits appear).
           ;; compact-slot: the compactor is quarantined in its own KV slot so
           ;; its prompts never evict the hot conversation's warm prefix in
           ;; hot-slot (see the :hot node comment).
           :extra-body        {"id_slot" compact-slot "cache_prompt" true}
           :real-tools        []
           :max-turns         2
           :budget-ms         240000
           ;; `compile:` is DEFINED by the bridge module in the system prompt —
           ;; the message invokes the program the modules loaded.
           :message           (fn [_env data]
                                (str "compile:\n\n"
                                  (core/compact-target-text (:messages data) k)))})

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
  "`at-prompt?` is the shared UI flag: the tap sets it when it prints the
  `user: ` prompt; real typed input CONSUMES the prompt line, so dispatching
  here clears it. (If instead a QUEUED message drains into the next turn, the
  tap sees the flag still set and closes the dangling prompt line first.)"
  [queue env sid at-prompt?]
  (doto (Thread.
          (fn []
            (loop []
              (let [line (read-line)]
                (cond
                  (nil? line)                        (send-event! queue env sid :user/end)
                  (contains? quit-lines (str/trim line)) (send-event! queue env sid :user/end)
                  (str/blank? line)                  (recur)
                  :else (do (reset! at-prompt? false)
                            (send-event! queue env sid :user/msg {:text line})
                            ;; Blank line after the user's enter — the reply
                            ;; block starts visually separated from the input.
                            (println)
                            (recur)))))))
    (.setDaemon true)
    (.setName "ouroboros-compact-stdin")
    (.start)))

;; ---------------------------------------------------------------------------
;; Bootstrap — next-chat continuation (OPT-IN: bb compact <prior-session-id>).
;; A NEW session seeded from a prior session's array, FOLDED at the boundary
;; (core/fold-split: λ(all_but_last_k) ⊕ last_k verbatim). Bootstrap ≻ native
;; :resume?: sessions stay immutable observations, the fold has a natural home,
;; and every launch is a fresh proven lib/run (no restored-invocation semantics
;; to trust). Lineage rides :initial-data :bootstrap/from → checkpointed free.
;; ---------------------------------------------------------------------------

(defn- fold-chart
  "One-shot chart compressing a prior session's head dialogue into λ through
  THE compaction lens (verdict-runner pattern: hermetic run, output via a
  closure atom, :error.llm → :failed so a dead worker cannot hang the launch)."
  [fold-text out]
  (chart/statechart
    {:initial :folding}
    (state {:id :folding}
      (h/llm-conversation
        {:id                "fold"
         :on-end-turn-event :fold/idle
         :system            compact-system-prompt
         :model             :local
         :stream?           false
         ;; The fold is compactor work — quarantine it in the compactor's slot
         ;; so launch never evicts a warm hot-slot prefix (another client's or,
         ;; later, a resident session's).
         :extra-body        {"id_slot" compact-slot "cache_prompt" true}
         :real-tools        []
         :max-turns         2
         :budget-ms         240000
         ;; `compile:` is defined by the lens's bridge module (cf. :compact).
         :message           (str "compile:\n\n" fold-text)})
      (transition {:event :fold/idle :target :done}
        (script {:expr (fn [env data] (reset! out (h/deref-output env data)) nil)}))
      (transition {:event :error.llm :target :failed}))
    (final {:id :done})
    (final {:id :failed})))

(defn- bootstrap-seed!
  "Load prior session `prior-id`'s λ-array and fold it at the boundary: ONE
  hermetic compaction call over the pre-tail head, gated by the pure
  compression contract (core/apply-fold — accepted ⟺ strictly shorter, else
  the array seeds UNFOLDED; a failed/budget-dead fold run leaves the atom nil
  and rejects the same way). Returns {:messages [...] :folded? bool}.
  Fail-loud when the prior session has no messages: wrong id, a non-chat
  session, or checkpoint-shape drift (see session/read-data-model)."
  [root prior-id]
  (let [prior (session/session-messages root prior-id)]
    (when (empty? prior)
      (throw (ex-info (str "no messages in session '" prior-id
                        "' — not a chat/compact session? (bb sessions lists candidates)")
               {:prior-id prior-id})))
    (let [{:keys [head]} (core/fold-split prior k)]
      (if (empty? head)
        {:messages (vec prior) :folded? false}   ; too short to fold — seed as-is
        (let [out      (atom nil)
              fold-sid (str "fold-" (System/currentTimeMillis))
              fold-dir (session/session-dir root fold-sid)]
          (lib/run
            (merge
              {:chart           (fold-chart (core/fold-input head) out)
               :session-id      fold-sid
               :session-dir     fold-dir
               :transcript-path (str fold-dir "/transcript.jsonl")
               :checkpoint-dir  (str fold-dir "/checkpoints")
               :tool-registry   (tools/new-registry root)}
              (models/llm-config :local)))
          (core/apply-fold prior k prior-id @out))))))

;; ---------------------------------------------------------------------------
;; Session listing — the picker (bb sessions): the CLI projection of what the
;; web UI's resume button will render later (same reads, second projection).
;; ---------------------------------------------------------------------------

(def ^:private synthetic-instructions
  "Engine-injected user turns — skipped when deriving a session's human label."
  #{greeting-instruction resume-instruction})

(defn- first-real-user-line
  [messages]
  (some (fn [{:keys [role text]}]
          (when (and (= :user role) (not (contains? synthetic-instructions text)))
            text))
    messages))

(defn- fmt-epoch [ms]
  (if (pos? ms)
    (-> (java.time.Instant/ofEpochMilli ms)
      (.atZone (java.time.ZoneId/systemDefault))
      (.format (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm")))
    "----------------"))

(defn- clip-label [s n]
  (let [s (str (or s ""))]
    (if (> (count s) n) (str (subs s 0 n) "…") s)))

(defn sessions-main
  "bb sessions — conversation sessions (those with a :messages array), newest
  first: id · date · msgs(λ) · lineage (⤴ bootstrapped-from) · first human line."
  [& _]
  (let [rows (->> (session/list-session-ids ".")
               (keep (fn [id]
                       (let [dm   (session/read-data-model "." id)
                             msgs (:messages dm)]
                         (when (seq msgs)
                           {:id    id
                            :epoch (pcore/recency-key id)
                            :n     (count msgs)
                            :nc    (count (filter :compacted? msgs))
                            :from  (:bootstrap/from dm)
                            :line  (first-real-user-line msgs)}))))
               (sort-by :epoch)
               reverse)]
    (if (empty? rows)
      (println "(no conversation sessions yet)")
      (doseq [{:keys [id epoch n nc from line]} rows]
        (println (str id "  " (fmt-epoch epoch)
                   "  " n " msgs (" nc " λ)"
                   (when from (str "  ⤴ " from))
                   "  | " (clip-label line 60)))))))

;; ---------------------------------------------------------------------------
;; Runner.
;; ---------------------------------------------------------------------------

(defn run!
  "Run the λ-compacted chat against `root` (default \".\"). Blocks until the
  conversation ends. Streams the assistant's tokens live (hot region only);
  reads user turns from stdin via `:on-env-ready` ingress. Returns the `lib/run`
  summary plus the durable session-dir (its transcript records the compact
  worker's λ outputs for inspection).

  With `prior-id` (opt-in — bb compact <id>): a NEW session bootstrapped from
  that session's folded tail (see bootstrap-seed!); the first turn is a
  continuity greeting naming where the prior session left off. NOTE the fold is
  a thinking-ON compaction call at LAUNCH (~15-25s) — unlike in-session
  compaction it has no reading shadow to hide in; the launch banner says why."
  ([] (run! "."))
  ([root] (run! root nil))
  ([root prior-id]
   (when prior-id
     (println (str "bootstrapping from " prior-id " — folding prior session (λ, ~15-25s)…")))
   (let [seed        (when prior-id (bootstrap-seed! root prior-id))
         _           (when seed
                       (println (str "  seeded " (count (:messages seed)) " msgs (folded: "
                                  (:folded? seed) ")")))
         messages-0  (if seed
                       (core/append-user (:messages seed) resume-instruction)
                       [(core/message :user greeting-instruction)])
         adapter     (sink/make-adapter)
         session-id  (str "compact-" (System/currentTimeMillis))
         session-dir (session/session-dir root session-id)
         ;; Echo line-discipline state (core/echo-text) — the impure shell
         ;; around the pure kernel. Serialized: escapement invokes the tap
         ;; sequentially per transcript row.
         echo        (atom core/echo-init)
         ;; Dangling-prompt flag, shared with the stdin ingress (see
         ;; start-stdin-ingress! docstring for the queued-message case).
         at-prompt?  (atom false)
         emit!       (fn [s] (when (seq s) (print s) (flush)))
         ;; Called before any tap output: if the `user: ` prompt is dangling
         ;; (a queued message drained — nothing was typed on it), close it.
         pre!        (fn [] (when @at-prompt?
                              (reset! at-prompt? false)
                              (emit! "\n")))
         break!      (fn [] (let [{:keys [state out]} (core/echo-break @echo)]
                              (reset! echo state)
                              (emit! out)))]
     (println "Ouroboros λ-compact chat —  type a message, Enter.  /quit (or Ctrl-D) to end.\n")
     (let [result
           (lib/run
             (merge
              ;; :local routing (credentials + aliases) from THE table —
              ;; ouroboros.models/llm-config (was inline; converged).
              (models/llm-config :local)
              {:chart           compact-chart
              :session-id      session-id
              :session-dir     session-dir
              :transcript-path (str session-dir "/transcript.jsonl")
              :checkpoint-dir  (str session-dir "/checkpoints")
              :tool-registry   (tools/new-registry root)   ; REQUIRED to wire the llm processor
              ;; :bootstrap/from = the lineage marker: it lands in every
              ;; checkpoint, so sessions form an explicit chain (bb sessions
              ;; renders it; the curator can stitch threads).
              :initial-data    (cond-> {:messages     messages-0
                                        :pending-user []}
                                 prior-id (assoc :bootstrap/from prior-id))
              :on-env-ready    (fn [env]
                                 (start-stdin-ingress! (get env event-queue-key) env session-id at-prompt?))
              ;; The text UI rides the SINK EVENTS, hot region ONLY — the
              ;; compact/parked workers stay silent (the old tap printed two
              ;; raw newlines on EVERY :llm/response row, region-unfiltered:
              ;; each compact run + each pure-tool-call round-trip segment
              ;; showed up as stray blank lines).
              :transcript-tap
              (fn [row]
                (doseq [e (sink/feed! adapter row)]
                  (when (= "hot" (str (:invokeid e)))
                    (case (:type e)
                      ;; Streamed reply text → uniform lines: newline runs
                      ;; collapsed, blank lines dropped, "assistant: " prefix.
                      :text-delta
                      (let [{:keys [state out]}
                            (core/echo-text @echo assistant-prefix
                              (get-in e [:delta :text]))]
                        (reset! echo state)
                        (when (seq out) (pre!))
                        (emit! out))

                      ;; Tool CALL made visible (name + truncated params); the
                      ;; result is deliberately NOT shown. NOTE (source-truth):
                      ;; the sink synthesizes :tool-call from the tool-RESULT
                      ;; row, so this line appears when the tool completes.
                      :tool-call
                      (do (pre!)
                          (break!)
                          (emit! (str (core/tool-line (:tool e) (:input e)) "\n")))

                      ;; Results are hidden — except failures, which the human
                      ;; should see (the model will react to them anyway).
                      :tool-result
                      (when (:is-error e)
                        (pre!)
                        (break!)
                        (emit! (str "tool: " (:tool e) " → ERROR\n")))

                      :tool-validation-failure
                      (do (pre!)
                          (break!)
                          (emit! (str "tool: " (:tool e) " → invalid ("
                                   (or (:message e) (:reason e)) ")\n")))

                      ;; End of the assistant's TURN (not a :tool_use /
                      ;; :max_tokens segment boundary): close the line, one
                      ;; blank line, then the prompt the human types after.
                      :llm-response
                      (when (contains? #{:end_turn :refusal} (:stop-reason e))
                        (break!)
                        (emit! (str "\n" user-prompt))
                        (reset! at-prompt? true))

                      nil))))}))]
       (assoc result :session-dir session-dir)))))

(defn -main [& [prior-id]]
  (let [{:keys [status session-dir]}
        (try
          (run! "." prior-id)
          (catch clojure.lang.ExceptionInfo e
            (println (str "bootstrap failed: " (ex-message e)))
            (System/exit 2)))]
    (println "\n--- λ-compact chat ended ---")
    (println "status      :" status)
    (println "session-dir :" session-dir)
    (shutdown-agents)
    (System/exit (if (contains? #{:done :aborted} status) 0 1))))
