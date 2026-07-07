---
type: mementum/knowledge
title: Escapement — Web UI, WS Push, and the Event-Ingress Seams
description: The bundled web UI is a read-only inspector (Fulcro SPA, JVM-built) — but ui.server + ws-push are bb-safe and embeddable; web-chat ingress = external event → tell-llm into a RESIDENT llm-conversation (the parked worker holds the run open, no patches needed); human-input serves modal prompts only, and only IT is blocked by lib/run's missing :human-renderer passthrough.
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

λ ingress. ∄ route(HTTP ∨ WS) → sp/send!(arbitrary-event) — the host builds its own (thin).
  liveness precondition (runner contract — see transcript-runner page): external events are
  deliverable only into a run that live work holds open; a naked wait-state exits (:done).
  CHAT ingress (the primary use-case; zero escapement changes):
    resident h/llm-conversation (no :message → worker born PARKED :awaiting-user ≡ live
    invocation → runner resident) + sibling region:
      transition(:user/msg, :type :internal) → h/tell-llm(text) → worker's user-msg-queue
      → next turn. See llm-conversation page λ worker; reference: examples/steered_convo.
    host delivery: :on-env-ready captures ::sc/event-queue → web handler (EQL mutation ∨
    WS frame) calls sp/send!{:event :user/msg :target session-id :data {:text}}.
  MODAL ingress (approval gates, select/confirm — NOT chat):
    h/human-input → renderer parks promise(prompt-id) → deliver(promise,value) → worker does
    sp/send!(:human.answer). shipped delivery: WS {:kind "answer"} ∨ POST /api
    [(escapement.human/answer {:prompt-id :value})] → remote-renderer/deliver-answer!.
    THIS path (only) is blocked at the lib facade by the missing :human-renderer key.
  queue: escapement.engine.queue/InProcessQueue — IN-PROCESS ONLY, no network endpoint

λ human_in_loop. first-class inventory (all source-verified):
  h/human-input          kinds text|select|multi|confirm|progress|custom · Malli :answer-schema
                         · :on-answer-event(:human.answer) · canonical :error.human.* events
  HumanRenderer          protocol seam — promise-based; shipped impls: stdin-renderer (headless),
                         TUI modal renderer, RemoteUiRenderer (WS prompt frames). A host web
                         renderer ≈ 20 lines: park promise per prompt-id; HTTP handler delivers.
  ^{:interactive? true}  chart marker; examples/ask ≡ reference resident loop
                         (ask → :human.answer → confirm → loop|final; :ui.interrupt → cancelled)
  h/with-llm-questions   inverse flow: LLM asks HUMAN mid-conversation — injects
                         event__ask_choice/event__ask_text event-tools → human-input modal
                         → tell-llm answer into the LIVE conversation (parent-owned, survives detours)
  tell-llm               feed a message into a RUNNING conversation worker (resident convo possible)
```

## The missing seam (verified; ONE passthrough key — affects MODAL prompts only)

`runner/run!` accepts `:human-renderer` and forwards it to the human-input processor (the
CLI passes it). `escapement.lib/Options` (closed schema) never got the key — it belongs in
the schema's own "passthrough knobs (forwarded verbatim to runner/run!)" section; the patch
is two lines (Options entry + run-opts assembly entry). SCOPE: this blocks `h/human-input`
charts via the lib facade — i.e. modal prompts / approval gates. It does NOT block chat:
resident-conversation + tell-llm ingress needs no renderer and no patch. Defer the patch
until modal approval dialogs are actually wanted.

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
  lib seam gap         : escapement.lib/Options has NO :human-renderer key (runner/run! HAS it).
                         WATCH: if the key appears in Options, the "missing seam" section above
                         is OBSOLETE and web chat needs zero patches. Check FIRST.
  inbound kinds        : exactly {"control" "answer"} in ws-push dispatch-inbound!
  pathom on /api       : pathom 2.4.0, transit-clj 1.0.333
  hub defaults         : :cap 4096, catch-up ring 256
  renderer wiring      : RemoteUiRenderer only under CLI --tui=opentui
  liveness contract    : runner exits on (zero live)∧(zero deliverable) — re-verify the pump
                         cond table in escapement.runner before trusting resident-chart claims
```
