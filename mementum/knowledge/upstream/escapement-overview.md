---
type: mementum/knowledge
title: Escapement — Overview & Mental Model
description: Escapement makes control flow a statechart, not an LLM loop — the chart, not the model, decides next; an LLM conversation is bound to a chart state.
resource: https://github.com/fulcrologic/escapement
tags: [escapement, statecharts, agent-framework, architecture, babashka]
status: active
category: upstream
related:
  - upstream/escapement-statechart-model
  - upstream/escapement-llm-conversation
  - upstream/escapement-tools
  - upstream/escapement-multi-agent-and-services
  - upstream/escapement-backends
  - upstream/escapement-library-embedding
  - upstream/escapement-transcript-runner-cli-testing
depends-on: []
---

# Escapement — Overview & Mental Model

> Ground truth: the escapement repo (see `resource`). The project Guide is the canonical
> prose reference. This page is the cluster entry point; siblings drill into each subsystem.
> Locate any named thing by grepping its name against the live source — coordinates rot,
> names endure.

## The one-sentence thesis

```
λ escapement. control_flow ≡ statechart ∧ ¬free_form_LLM_loop
              | "regulates LLM agents the way a watch escapement regulates a mainspring"
              | the_chart_decides_what_happens_next ¬the_model
```

An LLM conversation is **bound to a chart state** via the statechart library's invocation
mechanism. While that state is active, a background worker thread holds a live LLM session,
dispatches tool calls, and posts named events back to the chart's queue. When the chart
leaves the state, the worker is interrupted and the conversation dies. The chart —
deterministic — decides what happens next; only the LLM's scoped choices are non-deterministic.

## Why this matters (the five properties the design buys)

```
λ design_payoff.
  visibility      → every LLM req/resp/tool-call/transition/event ≡ JSONL transcript row → full replay
  safety          → chart_author defines LLM vocabulary | LLM influences chart ONLY via declared event-tools
  reproducibility → chart logic deterministic | LLM choices tightly scoped
  async           → LLM on worker thread | each parallel region own worker | fan-out natural
  resumability    → working_memory checkpointed atomically after EVERY event | resume re-spawns workers idempotently
```

## The tool duality (the key idea — see escapement-statechart-model)

```
λ tools.
  real_tool   → side-effecting (fs/shell/repl) | dispatched INSIDE worker | chart NEVER sees it
  event_tool  → 1 synthetic tool per :allowed-events entry | LLM calls event__<name>(args)
              → worker Malli-validates args → posts chart event <name> with args as data
              → tool "result" to LLM is just an ack
  region_tool → subchart-as-tool | region__<name> | synchronous req/reply (see services page)
```

The chart only ever sees the high-level domain events its author exposed. It never sees
`tool_use` blocks for real tools. **This separation is the project's whole point.**

## Three usage modes (locked design)

```
λ usage_modes.
  1. CLI            → `escapement run ns/agent` | auto-detects backend from env | full tool
  2. hosted_library → `escapement.lib/run` | HERMETIC: ¬read(.escapement.edn) ∧ ¬sniff(env)
                    | host injects :credentials + :config as explicit data every call
                    | THIS is how Ouroboros embeds escapement → see escapement-library-embedding
  3. future_daemon  → long-lived multi-session HTTP/socket service | DEFERRED | ¬exists yet
```

## Architecture boundary — core vs presentation (ENFORCED BY TEST)

```
λ layering. dependency_direction ≡ add_on → core | NEVER reverse
            | enforced: an architecture-boundary test scans every ns form
            | core MUST NOT statically require web/Pathom/RAD/TUI
            | lazy requiring-resolve bridges are intentionally invisible to the scanner
```

| Layer | Namespaces | Notes |
|---|---|---|
| **Engine/library core** | `escapement.engine.*`, `escapement.runner`, `escapement.lib`, `escapement.protocols`, `escapement.invocation.*` (incl. the `HumanRenderer` protocol + dependency-free `StdinRenderer`), `escapement.llm.*`, `escapement.tools.*`, `escapement.storage.*`, `escapement.transcript`, `escapement.debug.{controller,control-handle,d2}`, `escapement.config`, `escapement.cli` | Embeddable entry point = `escapement.lib`. Must NOT require web/Pathom/RAD/TUI. |
| **Web/API add-on** | `escapement.ui.*` + Pathom/EQL/transit + RAD/CLJS bundle | Loaded **lazily** by `--api-server` via `requiring-resolve`. Only `escapement.ui.resolvers` requires Pathom (server-side, not in browser build). Deps kept OUT of the downstream-facing manifests; live in the `:api` alias. |
| **Terminal-UI add-ons** | (1) JLine TUI = `escapement.tui` (default, in-process); (2) OpenTUI sidecar = Bun+SolidJS (opt-in `--tui=opentui`) | Used by CLI front-end only; `escapement.lib` never pulls either. Reached from `escapement.cli` via `requiring-resolve`. |

**Why:** a consumer using only the core lib/CLI is not infected by the Pathom/Fulcro/RAD
tree. When adding code, keep heavy presentation deps behind the seam.

## The Babashka constraint (house rule — non-negotiable)

```
λ bb_everywhere. agent_process ∧ CLI ∧ test_suite (`bb test`) ALL run under bb (SCI) | ¬JVM_required
  | source ∧ demos MUST stay bb-compatible | ¬JVM_only_paths_in_source
  | SCI ¬expose: java.util.concurrent.LinkedBlockingDeque, ConcurrentLinkedDeque
  | SCI ✓: LinkedBlockingQueue, TimeUnit
  | AVOID com.fulcrologic.statecharts.integration.fulcro* (pulls non-bb Fulcro client machinery)
  | statecharts `simple`/`testing` ns transitively pull promesa → crashes under SCI
    → escapement assembles env manually + ships escapement.engine.testing harness
  | use com.fulcrologic.statecharts.promise directly (host-portable, no promesa shim needed)
```

The full async statecharts family is available without ceremony: `simple-async`,
`testing-async`, `invocation.statechart`, `execution-model.lambda-async`, the async
event-queue family, and `algorithms.v20150901-async`.

## Dependency stack (small)

`com.fulcrologic/statecharts`, `metosin/malli`, `cheshire`, `babashka.process`,
`org.babashka/http-client`, `com.fulcrologic/guardrails`.

## Subsystem map (by namespace, not path)

```
escapement.chart.helpers        authoring sugar (aliased h/ in charts)
escapement.chart.{service,consult,repl-service}   service regions + worked drop-ins
escapement.engine.{env,exec,queue,instrumented-queue,store,testing}   runtime + checkpointing + test harness
escapement.invocation.llm-conversation   THE core processor
escapement.invocation.human-input         human-in-the-loop processor
escapement.llm.{protocol,api,openai,openai-codex,multi,cache}   backends
escapement.llm.{needs,preferences,ratings,catalog,providers,types}   model selection
escapement.tools.{protocol,builtin}   real-tool protocol + 8–9 built-ins
escapement.examples.*           hello → scan → parallel-demo → iterate (read in order) + ~12 more
escapement.ui.*                 web/EQL/RAD add-on (lazy) + opentui sidecar glue
escapement.tui                  JLine TUI add-on
escapement.lib                  hermetic embedding entry point
escapement.lib.event-sink       normalized public event stream
escapement.{runner,transcript,cli,config,prompts}
```

## Cold-start reading order (the maintainers' recommendation)

1. `escapement.examples.hello` — minimal single-region chart, one event tool.
2. `escapement.examples.scan` — real tool (`:fs/read`) + fan-out of event-tools + `:type :internal`.
3. `escapement.examples.parallel-demo` — two parallel regions, independent conversations, join on compound final.
4. `escapement.examples.iterate` — multi-state coding loop, `tell-llm` mid-binding, retry/give-up.

These four exercise every substantive pattern. Then read the Guide for depth.

## Gotchas surfaced project-wide (see escapement-statechart-model for detail)

```
λ gotchas.
  :type :internal      → keeps LLM worker alive across state hops | external transition kills+respawns it
  tell-llm             → ONLY works inside the bound state | outside → event silently dropped
  top-level final      → empties configuration set | ALWAYS wrap final in compound parent
  resume               → restarts in-flight conversations from scratch (fresh history)
                       → at-most-once ¬guaranteed for side-effecting tools | track durably via data-model
  :_event              → (get-in data [:_event :data ...]) ≡ standard read of triggering event payload
  worker_threads       → virtual threads where supported else platform daemon | ¬prevent bb exit
```
