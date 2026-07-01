---
type: mementum/knowledge
title: Escapement — Transcript, Runner, CLI, Config & Testing
description: JSONL single-writer transcript, the runner pump loop, CLI flags, .escapement.edn config, and the bb-friendly synchronous test harness.
resource: https://github.com/fulcrologic/escapement
tags: [escapement, transcript, runner, cli, config, testing, checkpoint, resume]
status: active
category: upstream
related:
  - upstream/escapement-overview
  - upstream/escapement-library-embedding
  - upstream/escapement-statechart-model
depends-on:
  - upstream/escapement-overview
---

# Escapement — Transcript, Runner, CLI, Config & Testing

> Named units: `escapement.{transcript,runner,cli,config}`, `escapement.engine.testing`.
> Grep names against live `resource`.

## Transcript — JSONL single-writer sink

```
λ transcript.
  single-writer daemon thread owns BufferedWriter | callers enqueue via LinkedBlockingQueue
  | FIFO | monotonic :seq owned by writer thread
  Record: TranscriptSink [queue thread seq-counter path closed?]
  row shape: {:event <kw> :ts <epoch-ms> :seq <long> :data {…}}
  API:
    open-transcript({:path :append?(t) :fsync?(f)}) → TranscriptSink (spawns daemon, mkdirs)
    write!(sink, event) → bool  (async, non-blocking, any thread; false if closed)
    close!(sink) → nil          (end-sentinel, join 5s, idempotent)
    make-transcript-fn(sink) → (fn [event] → bool)   (for env's :transcript-fn slot)
  fault tolerance: serialize failure → :transcript/serialize-error row + sanitized retry; never crashes
```

### Event vocabulary

```
runner   : :runner/started :runner/start-config :runner/resumed :runner/tick :runner/done
           :runner/error :runner/aborted :runner/event-processed :runner/event-dropped
llm      : :llm/start :llm/request :llm/response :llm/delta :llm/retry :llm/continuation
           :llm/error :llm/user-message :llm/worker-exit :llm/context-warning
           :llm/event-posted :llm/latency-switch :llm/tool-result
checkpoint: :checkpoint/written
human    : :human-input/{start answer cancelled error interrupted validation-failed}
artifact : :artifact/captured
cli      : :cli/config-loaded :cli/deps-added
error    : :transcript/serialize-error
```

Key shapes: `:runner/event-processed` → `{:event-name :config-before :config-after :entered
:exited :event-data :session-id}`; `:llm/response` → `{:stop-reason :n-blocks :usage :content
:model :context-window :context-used-frac :invokeid}`.

### `jq` recipes

```bash
# all transitions
jq -c 'select(.event=="runner/event-processed") | {ev:.data.event-name, to:.data.config-after}' transcript.jsonl
# model usage per response
jq -c 'select(.event=="llm/response") | {model:.data.model, usage:.data.usage}' transcript.jsonl
```

## Runner — run lifecycle

```
λ runner/run!(opts) → summary-map
  REQUIRED: :chart :session-id :transcript-path :checkpoint-dir
  OPTIONAL (additive): :chart-id(::chart) :backend :tool-registry :initial-data :store
    :session-dir :run-id :cancel :catalog-ratings :eligibility-strict? :llm-aliases
    :llm-preferences :backend-default-models :resume?(f) :trace?(f) :max-iterations(no-op)
    :max-frozen-cycles(4000) :quiescent-sleep-ms(50) :debug-controller :human-input-active?
    :multi-session? :on-env-ready :transcript-tap :prelude-events
```

### Pump loop (safe-boundary cancel, frozen-config guard)

```
loop [no-progress]:
  1. cancel-requested?(cancel) → :aborted              ; checked FIRST
  2. drain-once! → progressed? → recur(0) if progressed
  3. live=count-live-invocations | pending/deliverable from queue
     (zero live)∧(pos pending)∧(zero deliverable) → sleep; recur(0)   ; future-dated timer
     (zero live)∧(zero deliverable)                → :done            ; quiescent
     (pos live)                                     → sleep; recur(0)  ; LLM in-flight
     (>= inc no-progress max-frozen-cycles)         → :runner/error :frozen-config
     else                                           → sleep; recur(inc)
```

```
λ cancel-requested?(cancel).
  nil → never | IPending(promise/future/delay) → realized? ∧ truthy | IDeref(atom) → truthy | else truthy

λ :resume?.  (and resume? existing (seq configuration)) → emit :runner/resumed, SKIP start!
             else → sp/start! with :initial-data
λ :multi-session?.  drain-once! calls receive-events! WITHOUT session filter,
                    routes each event to (or (:target event) session-id)   ; multiplex charts
```

Return map: `{:final-config :status(:done|:aborted|:frozen-config) :session-id :transcript
:checkpoint-dir :env}`.

## CLI

```
subcommands: run <chart-sym> | info | open <session-dir> | login codex | logout codex
```

### `run` flags

```
--param key=value (multi, EDN-parsed, merges over --input) | --input <edn-file>
--session <id>(uuid) | --work-dir <path>(.escapement) | --transcript <path> | --checkpoint-dir <dir>
--base-dir <path>(session-dir; fs/shell tool root) | --resume
--backend api|codex|openai|ollama|opencode-go | --model <name> | --api-base-url <url> | --api-key-env <name>
--tools-ns <sym> (multi; called with registry atom) | --source-paths <p:p> | --deps <edn>
--trace | --log-level debug|info|warn|error | --no-tui | --tui=opentui|jline
--debug (force TUI on, pause before first event, inspector) | --api-server <port> | --dump-d2
```

```
λ backend auto-detect (build-default-multi-backend!).  detect-available-credentials scans env
  | single credential → bare backend | multiple → escapement.llm.multi dispatcher
  | returns {:backend B :default-models [id…]}
λ info.  prints version/java/os/bb/cwd/classpath, .escapement.edn discovery, env var + codex OAuth status, auto-detect summary
λ login codex.  browser OAuth → ~/.escapement/openai-auth.json (mode 0600)
λ effective-opts.  layering: CLI flag > .escapement.edn > built-in default
```

## `.escapement.edn` project config

```
λ config.
  load-config → ~/.escapement.edn deep-merged UNDER cwd/.escapement.edn (project wins)
  load-project-config(start-dir) → git-style WALK-UP for .escapement.edn
    | Malli-validates + referential integrity | :tools-ns normalized to vector
    | nil if none found | ex-info (humanized) on malformed
  config root = parent dir of found file; relative :source-paths/:work-dir resolve against it
```

### Schema keys (closed map)

```
:source-paths [string]        classpath roots (relative to root)
:deps {symbol any}            Maven/git coords for add-deps
:tools-ns symbol|[symbol]     registration fns (normalized to vector)
:work-dir string              default work dir
:default-chart symbol         used when no chart arg given
:llm/preferences [keyword]    ordered alias keywords (default candidate set)
:llm/ratings {kw {kw any}}    alias-keyed subjective overlay (any inner key filterable via :needs)
:llm/aliases {kw [target]}    THE target definition (closed target maps)
:llm/credential-sources {kw string}   named external JSON key stores (~ expanded)
:llm/credentials [{:provider kw …}]   ordered descriptors → hermetic multi-dispatch backend
:debug {:auto-pause? bool} | :viewers {ext cmd} | :d2 {:command :layout}
```

Referential integrity: every `:llm/preferences` and `:llm/ratings` keyword MUST exist in
`:llm/aliases` (or builtin defaults `#{:default-glm :default-sonnet :default-opus
:default-gpt}`), else rejected at config load. Secrets stay out via
`:llm/credential-sources` + `:key-from [<source> <path…>]`. `.escapement.edn` is gitignored.

## Testing harness (`escapement.engine.testing`)

The library's `com.fulcrologic.statecharts.testing` crashes under bb/SCI (pulls promesa).
`escapement.engine.testing` is a synchronous pump driving the SAME real engine, processor,
and tool dispatch.

```
λ engine.testing.
  new-testing-env({:statechart :session-id? :checkpoint-dir? :tool-registry? :session-dir?
                   :artifact-store?} & invocation-processors) → {:env :session-id :chart-id}
    | registers chart under :dcch.test/chart | session default :dcch.test/session | tmp checkpoint
  start!(t, initial-data) → t        ; sp/start! w/ initial-data as invocation-data; saves w0
  drain!(t, max-iters=1000) → t      ; pump until quiescent; throws on exceed
  drain-multi!(t, max-iters) → t     ; multi-session variant (multiplex charts)
  run-events!(t & events) → t        ; send! each (kw → {:name kw}) then drain!
  wmem(t) | configuration(t)→#{ids} | in?(t, id)→bool | data(t)→map
  goto-configuration!(t, data-ops, states) → t   ; brute force; does NOT run entry actions
  re-exported: assign, delete  (data-model ops)
```

### Mocking a backend / stubbing a tool

```clojure
(defrecord MockBackend [responses call-log]
  llm/LLMBackend
  (send-turn [_ request]
    (swap! call-log conj request)
    (or (ts/pop-first! responses) (throw (ex-info "out of canned responses" {})))))
;; canned shapes:
;;  tool-use : {:stop-reason :tool_use  :content [{:type :tool_use :id … :name … :input …}] :usage {} :model "mock"}
;;  end-turn : {:stop-reason :end_turn  :content [{:type :text :text "ok"}]                :usage {} :model "mock"}

(defrecord StubShellTool [responses calls]
  tp/Tool
  (tool-name [_] :shell/run) (description [_] "stub") (input-schema [_] [:map [:command :string]])
  (invoke [_ in] (swap! calls conj in) (or (ts/pop-first! responses) {:result "" :is-error true})))
(let [reg (builtin/new-builtin-registry)] (tp/register! reg (StubShellTool. (atom q) (atom []))) …)
```

> Test conventions: mock queues = atom-of-vector via `escapement.test-support` (`ts/queue`,
> `ts/pop-first!`). Do NOT import `LinkedBlockingDeque`/`ConcurrentLinkedDeque` (SCI lacks
> them); `LinkedBlockingQueue`/`TimeUnit` are fine. Live-API tests gate on env vars and skip
> cleanly when unset. Run with `bb test` (NOT kaocha — removed deliberately).

## Work-dir layout

```
.escapement/<session>/
  transcript.jsonl              every req/resp/tool-call/transition/checkpoint, one JSON/line
  checkpoints/<session>.edn      atomic working-memory snapshot after every event
  artifacts/<name>               file-backed LLM outputs (capture-llm-output)
```
