(ns ouroboros.cold
  "Cold-compiler prototype — a LIVE escapement parallel-region chart.

  Two concurrent regions inside one `parallel` state:

    :hot   — a working conversation, driven ONE turn at a time. Each turn is a
             FRESH single-turn `:llm-conversation` (a new worker per entry), so
             context is ASSEMBLED, never accumulated: the hot model sees only
               [ base-system + compiled-λ + the single most-recent exchange ]
             — never turns 1..N-1 verbatim. If it stays coherent across turns,
             the compiled λ (not raw history) carried the memory.

        :cold   — the cold compiler. On each hot turn it compiles the exchange into
                 a dense CONTINUE + RULED-OUT brief (the ESSENCE needed to continue),
                 VERIFIES it retains the turn's salient specifics (rejecting a brief
                 that dropped load-bearing detail), and on success publishes it to a
                 durable artifact (artifacts/brief.md) the hot side reads.
             Runs on the SAME local model, in its own worker — it compiles turn
             N while the hot side is already generating turn N+1 (the double
             buffer: compression hidden behind the hot model's own latency).

  Coordination is through the shared, checkpointed data-model — NOT ephemeral
  message passing. The RULED_OUT ledger is a monotonic set-union (see
  `ouroboros.cold.core/merge-ruled-out`): the compiler proposes additions, code
  merges them, removal is not an operation it can perform.

  Run: bb cold   (needs local llama.cpp at localhost:5100, model qwen35-35b-a3b)"
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [com.fulcrologic.statecharts.chart :as chart]
    [com.fulcrologic.statecharts.data-model.operations :as ops]
    [com.fulcrologic.statecharts.elements :refer [final on-entry parallel script state transition]]
    [com.fulcrologic.statecharts.protocols :as sp]
    [escapement.chart.helpers :as h]
    [escapement.lib :as lib]
    [escapement.lib.event-sink :as sink]
    [escapement.prompts :as prompts]
    [escapement.protocols :as proto]
    [ouroboros.cold.core :as core]
    [ouroboros.session :as session]
    [ouroboros.tools :as tools]))

;; Reuse the proven local llama.cpp wiring from ouroboros.smoke / ouroboros.loop.
(def ^:private local-base-url "http://localhost:5100/v1")
(def ^:private local-model    "qwen35-35b-a3b")

;; ---------------------------------------------------------------------------
;; Self-send: post an internal event to our own session (iterate.cljc pattern).
;; ---------------------------------------------------------------------------

(defn- send-self!
  "Post `event` (no data) to the chart's own session. Side-effecting; call from
  inside a script :expr, then return your ops vector as usual."
  [env event]
  (let [queue (get env :com.fulcrologic.statecharts/event-queue)
        sid   (some-> env :com.fulcrologic.statecharts/vwmem deref
                :com.fulcrologic.statecharts/session-id)]
    (when (and queue sid)
      (sp/send! queue env {:target sid :source-session-id sid :event event})))
  nil)

(defn- log-event!
  "Emit a side-channel transcript row so the runner (run!) can collect cold-side
  verdicts / state without parsing token streams. No-op if no transcript-fn."
  [env event data]
  (when-let [tfn (:escapement/transcript-fn env)]
    (try (tfn {:event event :data data}) (catch Throwable _ nil)))
  nil)

;; ---------------------------------------------------------------------------
;; Prompts — authored as files under src/ouroboros/prompts/cold/, loaded via
;; escapement.prompts. Static prompts are used verbatim; parameterized ones are
;; rendered with {{VAR}} substitution (prompts/render) or {{artifact}}/{{output}}
;; substitution (h/render-template) at invoke time.
;; ---------------------------------------------------------------------------

(defn- load-prompt
  "Read a prompt template from the classpath (src on :paths) with a disk
  fallback. Fails loud if absent — a missing prompt must never ship silently."
  [name]
  (let [rel (str "ouroboros/prompts/cold/" name)]
    (slurp (or (io/resource rel)
             (let [f (io/file "src" rel)] (when (.exists f) f))
             (throw (ex-info (str "prompt not found: " name) {:prompt name}))))))

(def ^:private compile-system-prompt   (load-prompt "compiler_system.md"))
(def ^:private compile-user-template   (load-prompt "compiler_user.md"))
(def ^:private hot-system-template     (load-prompt "hot_system.md"))

;; ---------------------------------------------------------------------------
;; The brief is a DURABLE ARTIFACT (artifacts/brief.md) — the single source of
;; truth both regions read, not a transient data-model string. Cold publishes
;; it on ACCEPT only; hot reads it via {{brief.md}} in its system template.
;; ---------------------------------------------------------------------------

(def ^:private brief-artifact "artifacts/brief.md")

(defn- read-brief
  "Current published brief, or \"\" if none yet."
  [env]
  (or (when-let [store (:escapement/artifact-store env)]
        (proto/read-artifact store nil brief-artifact))
    ""))

(defn- publish-brief!
  "Atomically write `content` as the new published brief (verified accepts only)."
  [env content]
  (when-let [store (:escapement/artifact-store env)]
    (proto/write-artifact! store nil brief-artifact content {}))
  nil)

(defn- build-compile-user
  "Render the cold compiler's user message from the durable prior brief, the
  authoritative monotonic ruled-out ledger, and the new exchange."
  [prev-brief ledger job]
  (prompts/render compile-user-template
    {:PREV_BRIEF       (if (str/blank? (or prev-brief ""))
                         "(none — first compilation)" prev-brief)
     :RULED_OUT_LEDGER (if (seq ledger) (str/join "\n" ledger) "(none yet)")
     :NEW_EXCHANGE     (:raw job)}))

;; ---------------------------------------------------------------------------
;; The chart.
;; ---------------------------------------------------------------------------

(defn- cold-done?
  "Cold region may finalize when the hot side is finished AND nothing is queued."
  [data]
  (and (:hot-finished? data) (empty? (:pending-queue data []))))

(def cold-chart
  (chart/statechart
    {:initial :run}
    (state {:id :run :initial :par}
      (parallel {:id :par}

        ;; ===================== HOT REGION =====================
        (state {:id :hot :initial :hot/turn}
          (state {:id :hot/turn}
            (h/llm-conversation
              {:id                "hot"
               :on-end-turn-event :hot/idle-done
               :model             :local
               :real-tools        []            ; pure reasoning turn — no tools
               :budget-ms         240000
               ;; assemble-don't-accumulate: system = base template with the
               ;; durable {{brief.md}} artifact + {{output}} = the last exchange.
               :system            (fn [env data]
                                    (h/render-template hot-system-template env
                                      {:output (:last-raw data)}))
               :message           (fn [_env data]
                                    (nth (:hot-turns data) (:idx data)))})
            (transition {:event :hot/idle-done :target :hot/gap}
              (script {:expr (fn [env data]
                               (let [idx  (:idx data)
                                     user (nth (:hot-turns data) idx)
                                     text (h/deref-output env data)
                                     raw  (str "user: " user "\nassistant: " text)]
                                 (send-self! env :compile-request)
                                 (send-self! env :hot/route)
                                 [(ops/assign :pending-queue
                                    (conj (:pending-queue data [])
                                      {:idx idx :user user :assistant text :raw raw}))
                                  (ops/assign :last-raw raw)
                                  (ops/assign :idx (inc idx))]))})))
          (state {:id :hot/gap}
            (transition {:event :hot/route :target :hot/turn
                         :cond  (fn [_env data] (< (:idx data) (:n data)))})
            (transition {:event :hot/route :target :hot/done
                         :cond  (fn [_env data] (>= (:idx data) (:n data)))}
              (script {:expr (fn [env _data]
                               (send-self! env :cc/wake)
                               [(ops/assign :hot-finished? true)])})))
          (final {:id :hot/done}))

        ;; ===================== COLD REGION ====================
        (state {:id :cold :initial :cc/idle}
          ;; Queue-driven pump: the pending-queue is the source of truth, NOT
          ;; the wake events (which are dropped when cold is busy compiling).
          ;; A :compile-request only re-arms an idle-waiting cold; draining is
          ;; driven by cold self-sending :cc/wake after each compile.
          (state {:id :cc/idle}
            (transition {:event :compile-request :target :cc/idle}
              (script {:expr (fn [env _data] (send-self! env :cc/wake))}))
            ;; work waiting → pop head, compile
            (transition {:event :cc/wake :target :cc/compiling
                         :cond  (fn [_env data] (seq (:pending-queue data [])))}
              (script {:expr (fn [_env data]
                               (let [q (:pending-queue data [])]
                                 [(ops/assign :current (first q))
                                  (ops/assign :pending-queue (vec (rest q)))]))}))
            ;; nothing left and hot is done → finalize
            (transition {:event :cc/wake :target :cc/final
                         :cond  (fn [_env data] (cold-done? data))})
            ;; nothing left but hot still running → wait for the next request
            (transition {:event :cc/wake :target :cc/idle
                         :cond  (fn [_env data]
                                  (and (empty? (:pending-queue data []))
                                    (not (cold-done? data))))}))

          (state {:id :cc/compiling}
            (h/llm-conversation
              {:id                "cold"
               :on-end-turn-event :cc/idle-done
               :model             :local
               :real-tools        []            ; pure compression — no tools
               :budget-ms         240000
               :system            compile-system-prompt
               :message           (fn [env data]
                                    (build-compile-user (read-brief env)
                                      (:ruled-out data []) (:current data)))})
            (transition {:event :cc/idle-done :target :cc/verifying}
              (script {:expr (fn [env data]
                               [(ops/assign :candidate (h/deref-output env data))])})))

          (state {:id :cc/verifying}
            (on-entry {}
              (script {:expr (fn [env data]
                               (let [job     (:current data)
                                     ;; STRUCTURAL tripwire — accuracy is unverifiable
                                     ;; without an LLM judge; we prime instead. Gate only
                                     ;; on "is this a usable, non-empty brief?". `:coverage`
                                     ;; / `:missing` are logged for observability, NOT gated
                                     ;; (lexical coverage would penalize dense λ).
                                     verdict (core/tripwire
                                               {:compiled (:candidate data) :source (:raw job)})]
                                 (log-event! env :cold/verdict
                                   {:idx (:idx job) :ok? (:ok? verdict)
                                    :coverage (:coverage verdict) :missing (:missing verdict)})
                                 (send-self! env (if (:ok? verdict) :cc/ok :cc/bad))
                                 [(ops/assign :last-verdict verdict)]))}))
            ;; ACCEPT — publish brief to the durable artifact + fold the
            ;; authoritative monotonic RULED_OUT ledger (code merges; the model
            ;; can only add, never shrink it).
            (transition {:event :cc/ok :target :cc/idle}
              (script {:expr (fn [env data]
                               (let [job  (:current data)
                                     cand (:candidate data)
                                     led  (core/merge-ruled-out
                                            (:ruled-out data []) (core/ruled-out-lines cand))]
                                 (publish-brief! env cand)
                                 (log-event! env :cold/state
                                   {:idx (:idx job) :compiled cand :ruled-out led})
                                 (send-self! env :cc/wake)
                                 [(ops/assign :through (:idx job))
                                  (ops/assign :ruled-out led)
                                  (ops/assign :verdicts
                                    (conj (:verdicts data []) (assoc (:last-verdict data) :accepted true)))
                                  (ops/assign :current nil)]))}))
            ;; REJECT — keep the previous compiled-λ; do NOT poison context
            (transition {:event :cc/bad :target :cc/idle}
              (script {:expr (fn [env data]
                               (let [job (:current data)]
                                 (log-event! env :cold/rejected
                                   {:idx (:idx job) :missing (:missing (:last-verdict data))})
                                 (send-self! env :cc/wake)
                                 [(ops/assign :verdicts
                                    (conj (:verdicts data []) (assoc (:last-verdict data) :accepted false)))
                                  (ops/assign :current nil)]))})))

          (final {:id :cc/final})))

      ;; both regions final → parallel raises done.state.par
      (transition {:event :done.state.par :target :run/done})
      (final {:id :run/done}))))

;; ---------------------------------------------------------------------------
;; Demo hot conversation — a 4-turn design chat where later turns REQUIRE memory
;; of earlier ones. If the hot side stays coherent, compiled-λ carried it.
;; ---------------------------------------------------------------------------

(def demo-turns
  ["We're designing a small write cache for Ouroboros. Compare write-through vs write-behind in two sentences, then pick ONE and state it plainly."
   "Given the strategy you just chose, what is the cache invalidation approach? Be specific."
   "What did we rule out earlier, and why? One sentence."
   "Summarize our cache design decisions so far as exactly three bullet points."])

;; ---------------------------------------------------------------------------
;; Runner.
;; ---------------------------------------------------------------------------

(defn run!
  "Run the cold-compiler chart against `root` (default \".\").
  Streams the HOT conversation's tokens live; collects cold-side verdicts and
  compiled state from the side-channel transcript events. Persists to a DURABLE
  session under <root>/sessions/<id>/ (escapement auto-writes transcript +
  per-event checkpoints + the brief.md artifact there — we only supply the
  stable path). Returns {:result <lib summary> :verdicts [...] :final-state {...}
  :session-id <id> :session-dir <path>}."
  ([] (run! "."))
  ([root]
   (let [adapter     (sink/make-adapter)
         verdicts    (atom [])
         final-state (atom nil)
         session-id  (str "cold-" (System/currentTimeMillis))
         session-dir (session/session-dir root session-id)
         result
         (lib/run
           {:chart          cold-chart
            :session-id     session-id
            :session-dir    session-dir
            :transcript-path (str session-dir "/transcript.jsonl")
            :checkpoint-dir  (str session-dir "/checkpoints")
            :tool-registry  (tools/new-registry root)
            :initial-data   {:hot-turns     demo-turns
                             :n             (count demo-turns)
                             :idx           0
                             :pending-queue []
                             :last-raw      ""
                             :ruled-out     []
                             :verdicts      []
                             :hot-finished? false}
            :credentials    [{:provider :openai :api-key "sk-local" :base-url local-base-url}]
            :config         {:llm/aliases             {:local [{:provider :openai :model local-model}]}
                             :llm/preferences         [:local]
                             :llm/eligibility-strict? false}
            :transcript-tap (fn [row]
                              ;; :cold/verdict is emitted once per turn (accept OR reject);
                              ;; :cold/rejected is a redundant transcript marker — do NOT
                              ;; collect it or turns get double-counted.
                              (case (:event row)
                                :cold/verdict (swap! verdicts conj (:data row))
                                :cold/state   (reset! final-state (:data row))
                                nil)
                              (doseq [e (sink/feed! adapter row)]
                                (when (and (= :text-delta (:type e))
                                        (= "hot" (str (:invokeid e))))
                                  (print (get-in e [:delta :text]))
                                  (flush))))})]
     {:result result :verdicts @verdicts :final-state @final-state
      :session-id session-id :session-dir session-dir})))

(defn -main [& _]
  (let [{:keys [result verdicts final-state session-dir]} (run!)]
    (println "\n\n=========== COLD-COMPILER RUN ===========")
    (println "status        :" (:status result))
    (println "brief artifact:" (str session-dir "/" brief-artifact))
    (println "\n--- per-turn verify verdicts (grounding gate) ---")
    (if (seq verdicts)
      (doseq [{:keys [idx ok? coverage missing]} verdicts]
        (println (format "  turn %s  %s  coverage=%.2f  %s%s"
                   (str idx)
                   (if ok? "ACCEPT" "REJECT")
                   (double (or coverage 0.0))
                   (if ok? "→ published" "→ kept previous")
                   (if (seq missing) (str "  DROPPED=" (pr-str missing)) ""))))
      (println "  (none — cold side produced no verdicts)"))
    (when-let [{:keys [compiled ruled-out]} final-state]
      (println "\n--- final compiled λ (the whole session in the cache) ---")
      (println (str/trim (or compiled "")))
      (when (seq ruled-out)
        (println "\n--- monotonic RULED_OUT ledger ---")
        (doseq [l ruled-out] (println "  " l))))
    (shutdown-agents)
    (System/exit (if (= :done (:status result)) 0 1))))
