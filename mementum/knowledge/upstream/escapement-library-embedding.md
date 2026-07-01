---
type: mementum/knowledge
title: Escapement — Embedding as a Library (escapement.lib/run)
description: escapement.lib/run is the hermetic embedding entry point — the host injects :credentials and :config as data; no disk or env reads on the lib path.
resource: https://github.com/fulcrologic/escapement
tags: [escapement, lib, embedding, hermetic, event-sink, ouroboros]
status: active
category: upstream
related:
  - upstream/escapement-overview
  - upstream/escapement-backends
  - upstream/escapement-transcript-runner-cli-testing
depends-on:
  - upstream/escapement-overview
  - upstream/escapement-backends
---

# Escapement — Embedding as a Library

> Named units: `escapement.lib`, `escapement.lib.event-sink`. **HIGHEST RELEVANCE: this is
> how Ouroboros runs escapement as its runtime.** `escapement.lib` is the embeddable core
> entry point and never pulls web/Pathom/RAD/TUI.

## `escapement.lib/run` — the hermetic entry point

```
λ run(opts :: Options).  → summary-map
  validate-options(opts)        ; CLOSED schema; unknown keys → ex-info {:errors :provided-keys}
  run-id ← (str (random-uuid))
  cfg ← (or :config {})
  resolved-prefs    ← preferences/preferences(cfg)        ; NEVER disk
  llm-aliases       ← aliases-from-config(cfg)            ; else default-aliases
  default-models    ← model-order(prefs, aliases)         ; flattened targets
  catalog-ratings   ← ratings/ratings(cfg)                ; {} when cfg absent
  strict?           ← :llm/eligibility-strict? from cfg
  pref-targets      ← flatten-targets(prefs, aliases)
  backend           ← (or :backend (providers/build-injected-credentials-backend
                                      credentials pref-targets))
  tmp-dir created when :transcript-path or :checkpoint-dir absent
  result ← runner/run!(assembled-run-opts)   [quiet? → with-quiet-logging]
  → (assoc result :run-id :transcript :checkpoint-dir :session-dir)
```

### Options (closed `:map`)

**Required:**

| Key | Type | Notes |
|---|---|---|
| `:chart` | any | statechart value |
| `:session-id` | any | keyword/string/uuid |
| `:credentials` | `[:vector [:map [:provider :keyword] …]]` | ordered provider descriptors; drives hermetic backend assembly. **Required for any chart with an `:llm-conversation`** — but facade wires the LLM processor only when BOTH a backend AND `:tool-registry` are present. |

**Optional (defaults sensibly):**

| Key | Default / Notes |
|---|---|
| `:config` | `.escapement.edn`-shaped map. Absent → `{}` ratings + built-in prefs, **never disk**. Keys: `:llm/preferences` `:llm/aliases` `:llm/ratings` `:llm/eligibility-strict?`. |
| `:backend` | Explicit `LLMBackend` escape hatch; **wins verbatim** over `:credentials`. |
| `:tool-registry` | Tool registry atom. Use `new-builtin-registry` for isolation. |
| `:initial-data` | Seed data-model map. |
| `:transcript-tap` | `(fn [raw-row])` — receives every transcript row in-process. |
| `:on-env-ready` | `(fn [env])` — called once after env built, before chart start. |
| `:transcript-path` | absent → `<tmp>/transcript.jsonl`. |
| `:checkpoint-dir` | absent → `<tmp>/checkpoints`. |
| `:session-dir` | absent → `<tmp>` (artifact root). |
| `:store` | working-memory store override. |
| `:quiet?` | default **true** — suppress Timbre DEBUG/INFO to stderr (thread-local binding, not global). |
| `:cancel` | atom/promise/delay; truthy → abort at safe pump boundary → `:status :aborted`. Omitting → `:status :done` on normal run. |
| `:chart-id` | forwarded verbatim. |
| `:resume?` | skip `start!` if checkpoint exists. |
| `:trace?` | emit per-tick `:runner/tick`. |
| `:max-iterations` | **deprecated / no-op**. |
| `:quiescent-sleep-ms` | forwarded to runner. |

### Summary/result map

```
{:run-id <uuid-string>          ; lib-generated per call
 :transcript <path>             ; actual path used (temp if defaulted)
 :checkpoint-dir <path>
 :session-dir <path>
 :final-config (vec configuration)
 :status :done | :aborted | :frozen-config
 :session-id <echoed>
 :env <live env map>}
```

## Hermetic invariants (locked design)

```
λ hermetic.
  ZERO disk reads on lib path  → config/load-config + load-project-config NEVER called
  ZERO env var reads           → build-injected-credentials-backend consumes only :credentials
  NO process globals           → with-quiet-logging uses thread-local binding (concurrent-safe)
  per-call config resolution   → ratings/prefs/strict? derived freshly from injected :config each run
  unknown keys rejected        → unconditionally (not just under guardrails)
  correlation stays in HOST closure → capture :run-id/:session-id yourself; no host ids in payloads
```

Two `run` calls in one process with different `:config` ratings resolve **independently** —
there is no process global.

## Credentials shape

```clojure
:credentials [{:provider :z-ai-plan :subscription true}
              {:provider :anthropic :api-key (System/getenv "ANTHROPIC_API_KEY")}]
:config      {:llm/preferences [:default-glm :default-opus]
              :llm/aliases     {:default-glm [{:provider :z-ai-plan :model "glm-4.6"}]
                                :default-opus [{:provider :anthropic :model "claude-opus-4-7"}]}
              :llm/ratings     {:default-opus {:clojure 9}}
              :llm/eligibility-strict? true}
```

Each descriptor names a `:provider` keyword + the env-free secret/override it needs
(`:api-key`, `:base-url`, `:model`, or `:subscription true`). The host pulls the secret;
escapement never sniffs env on this path.

## `escapement.lib.event-sink` — normalized public event stream

```
λ make-adapter() → {:feed (fn [raw-row] → [PublicEvent…]) :ctx (fn [] → ctx)}
  | closure-local state atom; no globals, no producer mutation
λ feed!(adapter, raw-row) → [PublicEvent …]  (possibly empty)
λ normalize(ctx, row) → [0|1|2 PublicEvent]  (pure; internal rows → [])
```

Every public event carries the correlation triple `:session-id`, `:run-id`, `:invokeid?`
(captured from `:runner/started`). `PublicEvent` is a closed multi-schema dispatched on
`:type`:

```
run     : :run-started{:chart-id :resume?} :run-resumed{:config} :run-done{:final-config}
          :run-aborted{:reason} :run-error{:message}
chart   : :chart-event{:event-name :config-before :config-after :event-data}
          :chart-config{:config} :chart-checkpoint{:checkpoint-session-id}
llm     : :llm-request{:model :n-messages} :text-delta{:delta :model}
          :llm-response{:model :stop-reason :n-blocks :usage} :llm-retry{:model :category :attempt}
          :llm-fallback{:from-model :category} :llm-error{:reason :category :message}
          :llm-continuation{:segment :usage}
tool    : :tool-call{:tool-use-id :tool :input}  (synthesized from :llm/tool-result pair)
          :tool-result{:tool-use-id :tool :is-error :content-preview}
          :tool-validation-failure{:tool :category :reason :message}
```

### Host tap pattern

```clojure
(let [adapter (sink/make-adapter)
      events  (atom [])]
  (lib/run {:chart greet
            :session-id "demo"
            :credentials [...]
            :transcript-tap (fn [row]
                              (doseq [e (sink/feed! adapter row)]
                                (swap! events conj e)))}))
;; every event carries :run-id matching (:run-id result) — correlate on :session-id + :run-id
```

`:text-delta` is the **supported** way to stream assistant tokens live (the alternative to
hand-matching raw `:llm/delta` rows).

## Cancellation

Pass `:cancel` an atom (or delivered promise/future/delay). When truthy, the run aborts
promptly at a safe boundary; result `:status` is `:aborted`. Omitting `:cancel` always yields
`:status :done` for a normal run.

## Runnable reference

The `lib` demo (`bb -m lib.embed-example`) — a two-phase LLM chart driven via `lib/run` with
injected credentials/config/initial-data, artifact-shared context, and live `:text-delta`
streaming via `event-sink`. **Read this first when wiring Ouroboros onto escapement.**

## Minimal no-secret smoke (no LLM)

```clojure
(require '[escapement.lib :as lib])
(import '[com.fulcrologic.statecharts.elements])
(refer 'com.fulcrologic.statecharts.elements :only '[state transition final script on-entry])
;; assign into data model, transition to final — runs with zero secrets
(lib/run {:chart greet-chart :session-id "demo"})
;; → {:status :done :final-config [:run :done] :run-id … :transcript /tmp/…}
```
