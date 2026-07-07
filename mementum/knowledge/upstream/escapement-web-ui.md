---
type: mementum/knowledge
title: Escapement — Web UI, WS Push, and the Event-Ingress Seams
description: The bundled web UI is a read-only inspector (Fulcro SPA, JVM-built) — but ui.server + ws-push are bb-safe and embeddable; NO route injects arbitrary chart events, so inbound reaches a chart only via the HumanRenderer promise path or a host-captured queue (:on-env-ready → sp/send!).
resource: https://github.com/fulcrologic/escapement
tags: [escapement, web, ui, websocket, http-kit, ingress, human-input, ouroboros]
status: active
category: upstream
related:
  - upstream/escapement-library-embedding
  - upstream/escapement-transcript-runner-cli-testing
  - upstream/escapement-statechart-model
depends-on:
  - upstream/escapement-library-embedding
---

# Escapement — Web UI, WS Push, and the Event-Ingress Seams

> Named units: `escapement.ui.server`, `escapement.ui.ws-push`, `escapement.ui.resolvers`,
> `escapement.ui.replay-source`, `escapement.ui.remote-renderer`,
> `escapement.invocation.human-input`, `escapement.debug.viz-server`.
> **RELEVANCE: the transport for Ouroboros's web chat channel** — reuse the bb-safe
> server/hub layers; the browser SPA itself is an inspector, not a chat surface.

## One-paragraph orientation

The web app is **observability, not conversation**: sessions report, transcript drill-in,
artifact browser, invocation timeline, live debugger (pause/step/continue), chart view.
There is **no chat input widget** in the browser SPA; interactive text prompts flow through
the OpenTUI (Bun) sidecar via `RemoteUiRenderer`, not the SPA. What a host wants from this
region is the **plumbing**: an http-kit server with a transit-EQL `/api` (pathom 2.4.0),
a WebSocket fan-out hub with catch-up + backpressure, and two narrow inbound seams.

## λ contracts (the embeddable core)

```
λ start!(opts). escapement.ui.server/start!
  opts: {:port :work-dir                              ; required
         :active-session-id :chart :controller :live  ; optional
         :ws-push hub :ws-handlers {:control f :answer f}}
  → {:stop fn :port n :ws-push hub} | teardown: stop! ∨ (:stop)
  routes: POST /api (transit-EQL, pathom2) | GET /ws (⟺ :ws-push supplied)
          GET / ∧ /js/main/* (SPA bundle) | GET * → classpath public/ fallback
          ; the public/-fallback serves ANY host classpath asset — a host chat.html rides free
  CORS: permissive "*" everywhere

λ hub. escapement.ui.ws-push
  new-hub({:cap :debug? :chart}) → hub-atom {:seq :clients :phase :recent :pending-prompt …}
  publish!(hub, transcript-row)  → wire {:kind "event" :seq :ts :event :data} → all clients
                                  | catch-up ring (≤256 replayed on attach) | non-blocking
  broadcast!(hub, frame)         → raw non-event frames ("prompt"|"progress"|"debug")
  dispatch-inbound!(frame)       → by :kind: "control"→:control-handler | "answer"→:answer-handler
                                  | ONLY these two kinds — the entire inbound vocabulary
  backpressure: per-client queue (cap 4096) | overflow coalesces llm/delta per
                [invokeid session-id type], else drops oldest | ordered single-in-flight CAS

λ ingress. THE LOAD-BEARING FACT: ∄ route(HTTP ∨ WS) → sp/send!(arbitrary-event)
  path-1: h/human-input invocation → HumanRenderer.ask! parks worker on promise(prompt-id)
          → deliver-answer!(prompt-id, value) → promise → sp/send!(:human.answer)
          triggers: WS {:kind "answer"} frame ∨ POST /api [(escapement.human/answer {…})]
  path-2: lib/run :on-env-ready(fn [env]) → capture ::sc/event-queue
          → sp/send!(queue, env, {:event e :target session-id :data d}) from any host handler
          ; sp ≡ com.fulcrologic.statecharts.protocols — send! is the injection primitive
  queue: escapement.engine.queue/InProcessQueue — IN-PROCESS ONLY, no network endpoint
```

## The missing seam (verified; drives host design)

`escapement.lib/Options` (closed schema) has **no `:human-renderer` key** — the CLI wires
renderers directly into `runner/run!`; the hermetic lib facade cannot. Consequence for an
embedding host wanting web chat:

```
λ host_chat_ingress. choose:
  (a) queue-injection  — :on-env-ready captures queue; host's :answer ws-handler (host-owned fn)
                          calls sp/send! {:event :user/msg …}; chart parks in wait-state.
                          works TODAY, zero escapement changes. honest topology: USER initiates.
  (b) human-input      — the framework primitive for AGENT-initiated prompts (modal ask).
                          blocked via lib facade (no :human-renderer seam) → patch escapement
                          ∨ bypass lib/run for runner/run!. right primitive for approval gates.
```

## Outbound composition (host → browser)

```
hub    ← ws-push/new-hub
server ← ui.server/start! {:ws-push hub :ws-handlers {…}}
lib/run {:transcript-tap (fn [row] (ws-push/publish! hub row)) :on-env-ready …}
```

Every transcript row streams to every WS client; `llm/delta` frames give live token
streaming (coalesced under backpressure). Phase snapshots auto-derive the active-state
breadcrumb from `runner/start-config` / `runner/event-processed`. Bonus: the `/api`
resolvers (`:escapement/all-sessions`, `:session/events`, `:session/artifacts`,
`:artifact/content`, …) work against any `:work-dir` sessions root — inspector for free.

## Wire envelopes (WS)

```
out: {:kind "event"  :seq :ts :event "ns/name" :data {…}}
     {:kind "phase"  :ts :config :breadcrumb :siblings}
     {:kind "debug"  :paused :step-budget :config}
     {:kind "prompt" :prompt-id :invokeid :type "text|select|multi|confirm" :opts}
     {:kind "progress" :phase "start|update|end" :invokeid :pct :label}
in:  {:kind "answer" :prompt-id :value}   | {:kind "answer" :prompt-id :cancelled true}
     {:kind "control" :op "pause|step|continue|arm"}
```

## bb-compatibility split (house rule: bb/SCI in source)

```
bb-SAFE   : ui.server ui.ws-push ui.replay-source ui.remote-renderer
            ui.resolvers(read side) debug.viz-server | http-kit ≡ bundled-with-bb
JVM-ONLY  : client.cljs ui.rendering/** ui.screens/** ui.model/** ui.control
            (Fulcro RAD + shadow-cljs :cljs alias; SPA bundle NOT in git — bb build-ui
             → release-jar/cache/GitHub-asset resolution chain)
CONFLICT  : bb pins guardrails 1.2.16 (pathom 2.4.0 × SCI); Fulcro RAD needs 1.3.2 —
            cannot coexist on one classpath (:ui-test alias overrides for JVM runs only)
```

→ A host builds its **own** thin chat page (static HTML+JS over `/ws`, served by the
classpath `public/` fallback) rather than adopting the Fulcro SPA.

## Naming traps

- `examples/turn-loop` is **not** a human turn loop — it demonstrates one multi-tool LLM
  turn ending in an `event__turn_done` event-tool. The human-turn primitive is
  `h/human-input` + a `HumanRenderer` (protocol: `prompt-text` `prompt-select`
  `prompt-multi` `prompt-confirm` `start-progress` `update-progress` `end-progress`
  `custom-render`; fallback impl: `stdin-renderer`).
- `debug.viz-server` is a separate SSE server (`GET /events`, shells out to `d2`) — not
  part of `ui.server`.

## Stale-check markers (re-verify against live `resource`)

```
λ stale_check. true at synthesis; grep-miss ⟹ stale:
  no-ingress-route     : no POST-/api resolver ∨ WS kind calls sp/send! with arbitrary events
                         (a future "event" inbound kind ∨ :human-renderer lib option would
                          OBSOLETE the λ host_chat_ingress fork above — check Options schema first)
  inbound kinds        : exactly {"control" "answer"} in ws-push dispatch-inbound!
  pathom on /api       : pathom 2.4.0, transit-clj 1.0.333
  hub defaults         : :cap 4096, catch-up ring 256
  renderer wiring      : RemoteUiRenderer only under CLI --tui=opentui
```
