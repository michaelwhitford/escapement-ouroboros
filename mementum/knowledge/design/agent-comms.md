---
type: mementum/knowledge
title: Agent Comms — channels on the escapement event substrate (no new bus)
description: Ouroboros builds NO message bus — escapement's statechart event system (target-routed :llm.user-message, service regions, consult, verdicts, artifacts, multiplex, ws-push hub) already IS one, but only in-process; REVISED (same session, see design/signals) — the DATA plane comes first as pull-based durable SIGNALS (no residency needed; the geometry scheduled hermetic agents require), while THIS page's live-push CHANNEL layer (residency, orchestrator chart, named registry, Malli-gated payloads, genome channel grants, scope=authority) is the DEFERRED control plane for interactive multi-agent workflows; the human membrane is ui.server + ws-push outbound (every message ≡ transcript row ≡ free audit) and a thin host ingress route inbound.
resource: file:///Users/mwhitford/src/escapement-ouroboros
status: designing
category: design
tags: [ouroboros, design, comms, channels, message-bus, residency, orchestrator, escapement, vsm, capability, grants]
related:
  - design/signals
  - design/vsm-on-escapement
  - design/agent-model
  - design/scheduled-maintenance
  - design/harness-coder
  - upstream/escapement-multi-agent-and-services
  - upstream/escapement-web-ui
depends-on:
  - design/vsm-on-escapement
  - design/agent-model
  - upstream/escapement-multi-agent-and-services
---

# Agent Comms — channels on the escapement event substrate

> Forward-looking durable names (this is DESIGN — grep once built): planned namespaces
> `ouroboros.channels` (the registry), `ouroboros.orchestrator` (the resident chart);
> planned tool `:bus/send`; planned genome frontmatter key `channels`.

## The problem

Ouroboros agents today are ISLANDS: each entrypoint (`bb compact`, `bb curate`, `bb judge`)
is its own hermetic `lib/run`. Agents cannot message each other; nothing can coordinate a
workflow; recommendations have no channel to ride to the human except stdout. The ask:
inter-agent communication that doubles as a message bus, with human observation built in.

## Finding: the bus EXISTS — escapement, in-process

```
λ inventory(escapement_comms).                          ouroboros_use
  1 directed/broadcast   :llm.user-message autoforwards to EVERY live
                         invocation; :target filter (nil ⇒ BROADCAST,
                         id ⇒ that agent only) — pub/sub free               agent→agent messaging
  2 request/reply        service regions: region__<name> tools; sync,
                         deferred (nil + post-reply), timeout, late-drop    agent-as-service
  3 LLM-as-tool          consult: specialist LLM behind a tool,
                         forced verdict-schema, JSON→JSON                   judge-inside-workflow
  4 typed results        verdicts: Malli-validated map at turn end          child→parent reports
  5 shared blackboard    artifacts: file-backed, {{template}} render        cross-phase context
  6 dynamic children     multiplex + :multi-session? (runner routes
                         cross-session — still ONE process)                 fan-out workers
  7 observation stream   transcript-tap / event-sink → ws-push hub
                         (fan-out, catch-up ring, backpressure)             human watches the bus
  8 the queue itself     sp/send! + transitions ≡ subscriptions;
                         transition SCOPE (LCCA) ≡ authority                THE substrate
  9 external ingress     host route → sp/send! into a live session
                         (proven: compact's stdin ingress)                  human/web → bus
```

What escapement does NOT have: **cross-process transport** (`InProcessQueue` only, no
network endpoint) and an **LLM-visible send tool** (`tell-other-llm!` is chart-side
plumbing, never exposed to the model).

## REVISION (same session) — two planes; signals first

The human's Anima prior art (signals: typed EDN results, pull-based, "the parser is the
bus") exposed a flaw in this page's push-centric framing: the first communicating agents
are the SCHEDULED maintenance roster — hermetic one-shots that don't exist between runs.
Push cannot reach a process that isn't there. **design/signals is the DATA plane, built
first, no residency.** This page's channels remain correct as the CONTROL plane —
live push for interactive workflows — and everything below is DEFERRED until those exist.
The channel registry's seed vocabulary and the grants mechanism migrate to the signal-type
registry (one contract, three projections — see design/signals).

## Decision 1 (deferred) — build NO new bus; the residency design

```
λ residency.
  ONE resident lib/run ≡ the ORCHESTRATOR chart (Ouroboros's own S5, per vsm-on-escapement)
  | agents ≡ parallel regions ∨ invocations ∨ multiplex children INSIDE it
  | the statechart event queue IS the bus | scope(LCCA) ≡ authority — free, and LOST
    the moment agents are peer processes (a socket has no LCCA)
  | today's bb tasks → clients of the resident system ∨ child runs it launches
  | hermetic one-shots (verdict runs) MAY stay subprocesses the orchestrator invokes —
    hermetic-per-run credentials sidestep the multi-model collision (see prerequisites)
```

Rejected: filesystem mailbox / WS peer bus between separate processes — re-implements
routing, liveness, and delivery that statecharts already law-govern, and forfeits
scope-as-authority (all peers equal ⇒ the VSM hierarchy becomes decorative again).

## Decision 2 — channels, not raw events (the VSM guard)

vsm-on-escapement's warning is the load-bearing constraint: *naively wiring events between
S-boxes gives a bus, not a VSM.* Three properties promote a message to a CHANNEL:

```
λ channel ≡ event ⊕ scope(authority) ⊕ transducer(encoder) ⊕ variety_class
  scope        the depth of the handling transition = who may preempt whom (escapement FREE)
  transduction crossing a level RE-ENCODES payload | raw S1 output NEVER rides up unencoded
               (the curator already obeys this: reads λ-compacted sessions, not raw)
  variety      algedonic ≈ 1 bit ∧ bypass | homeostat = high variety ∧ must settle
               | proposal = low variety down (approve/reject/constrain), higher up
```

### The channel registry (planned `ouroboros.channels`)

A named table, sibling to `ouroboros.models` — data, not code:

```
{channel-kw {:doc         "one line"
             :payload     <malli-schema>        ; gate at send ⇒ malformed UNREPRESENTABLE
             :variety     :algedonic | :report | :proposal | :homeostat
             :scope       :root | :region                    ; intended handler depth
             :reserved?   bool}}                             ; reserved ⇒ escalation in roster report

seed vocabulary (VSM named channels, from vsm-on-escapement):
  :s4/proposal     recommendation → S5/human gate   :proposal   reserved
  :s1/report       operation result upward          :report
  :ouro/algedonic  identity-threatening alarm       :algedonic  reserved, root-scoped, RARE
  :s3/allocate     resource-bargain downward        :report
  :human/notice    surface-to-human, non-blocking   :report
```

### Grants mirror tools (the elegant part — machinery already built)

```
λ grants. genome frontmatter += channels: [...]
  | registry ≡ CEILING (genome SELECTS, cannot invent) | absent key ⇒ NO send (floor = silence)
  | reserved channels ⇒ ESCALATION lines in the roster report (same audit surface as tools)
  | compiler change: agents.core schema += optional channels, validated against the registry
  | λ shape: an ungranted channel is UNREACHABLE, not forbidden
  | sibling schema growth: frontmatter also gains tags (role-as-tag, loader-consumed by the
    SCHEDULER not the bus — tags select WHO runs, grants select what-may; design/scheduled-maintenance)
```

### The one new tool: `:bus/send`

```
λ bus-send. agent-visible Tool {channel, to?, payload}
  | validates: channel ∈ genome grant ∧ payload ⊨ registry schema → else corrective
    {:is-error true} tool_result (curator's propose-memory retry pattern)
  | dispatches: sp/send! (chart events) ∨ tell-other-llm! (to a live conversation)
  | to absent ⇒ broadcast (subscribers ≡ charts with a matching transition)
  | request/reply does NOT ride this — service regions are native and better
```

## The membrane (human observation + ingress)

```
λ membrane.
  outbound  every message ≡ a transcript row → ui.server + ws-push hub streams ALL
            inter-agent traffic to any WS client | audit for FREE (cache-report pattern
            can grow a comms-report) | curator can metabolize traffic later
  inbound   thin host route (EQL mutation ∨ WS frame) → sp/send! {:event … :target sid}
            — the compact stdin ingress generalized | hub's own inbound vocab is only
            {"control","answer"} — the chat/bus ingress is OURS to add (by design)
  modal     h/human-input approval dialogs remain BLOCKED at the lib facade
            (missing :human-renderer passthrough — known 2-line seam, defer until wanted)
```

## Prerequisites (design them in, or residency bites)

```
1 MULTI-MODEL COLLISION (banked gotcha, now LIVE): escapement's provider-index is
  FIRST-WINS per provider tag — chat(:local@5100) + judge(:ornith@5102) as two :openai
  credentials in ONE lib/run send everything to the first. The resident system IS the
  "workflow that truly needs two models in one session". Options: descriptor :route regex ·
  provider-less alias targets · upstream/fork seam · keep multi-model work in child
  subprocesses (hermetic creds per run — works TODAY, zero patches).
2 S2 LAW: service-region substate transitions in parallel MUST be :type :internal
  (external LCCA restarts sibling agents — the anti-oscillation invariant).
3 SLOTS: llama.cpp slot convention (hot→2, compact→3) must extend per resident agent
  sharing 5100, or agents thrash each other's KV cache.
4 LIVENESS: the resident run stays open only while live work holds it (parked worker ≡
  live) — the orchestrator needs a parked anchor by construction.
```

## Migration path (incremental, gate-green at every step)

```
step 1  channel registry + :bus/send + genome channels grants (compiler + report)
        — testable WITHOUT residency: grant it to chat, message the compact worker
step 2  TWO agents, one run: chat region + a second region (scout ∨ curator) exchanging
        one channel message end-to-end | proves scope + grants + transcript audit
step 3  orchestrator chart (ouroboros.orchestrator): parked anchor + agent regions +
        ws membrane | bb tasks become clients | verdict one-shots stay child runs
step 4  the maintenance roster's :s4/proposal traffic (design/scheduled-maintenance)
        rides the channel to the human membrane
```

## Open questions

```
· multi-model in ONE run — which lever (route regex / provider-less aliases / fork seam /
  child-subprocess-only)? blocks nothing until step 2 hosts different-model agents
· message durability beyond transcript+checkpoint — probably NONE (sessions/ pattern:
  pre-approval observation is filesystem-local); revisit if replayable queues wanted
· does the orchestrator SUBSUME compact's chat chart or HOST it as a region? (hosting
  preserves the blessed ouroboros.compact names; subsuming is cleaner topology)
· channel registry as code table (models.clj pattern) vs EDN file — code table first
· backpressure/variety ENFORCEMENT: schema gates payload SHAPE, what damps FREQUENCY?
  (VSM: ≥3 damper + temporal separation are prompt/policy today, not mechanism)
```
