---
type: mementum/index
title: Ouroboros Design — Knowledge Index
description: Map of the design knowledge set — what Ouroboros IS BECOMING; one-line essence + build status per page, dependency-ordered reading paths, and the standing build queue.
resource: file:///Users/mwhitford/src/escapement-ouroboros
tags: [ouroboros, design, index, map]
status: active
category: design
related:
  - design/agent-model
  - design/vsm-on-escapement
  - design/shadow-compaction
  - design/extra-body-seam
  - design/agent-comms
  - design/signals
  - design/experiments
  - design/scheduled-maintenance
  - design/harness-coder
depends-on: []
---

# Ouroboros Design — Knowledge Index

These pages are the SPECS the designer holds (genesis S5 decision — "you are the designer
of Ouroboros"). Each carries its full essence in its `description`; this map is the
one-line projection + build status. `state.md` carries the live queue; git log carries
the when.

## The map (essence + status)

```
agent-model            OKF genome files; kind ≡ shape, tools ≡ capability grant,       BUILT (compiler,
                       registry ≡ ceiling; scorer ≡ the GA fitness function             4 genomes, judge+scorer)
vsm-on-escapement      charts ARE executable VSM; channel ≡ event+scope+transduction+   FRAME (guides all
                       variety; human ≡ System+1 S5; termination ≡ plateau-detection    other designs)
shadow-compaction      compact aged turns DURING the human's reading shadow —           BUILT (Tier 1 live;
                       felt-latency ≻ throughput                                        Tier 2 shelved)
extra-body-seam        the fork's raw-body passthrough (chat_template_kwargs,           BUILT (in the dep,
                       id_slot, cache_prompt) — 4 gates, caller-wins                    9e57f16)
agent-comms            escapement events ARE the bus but in-process only → residency    DEFERRED (control
                       + channels ≡ the live-push CONTROL plane                         plane; REVISED)
signals                typed durable EDN facts, pull ≡ subscription, NO residency —     DESIGNED (build
                       the DATA plane; ONE contract THREE projections (exemplar         after gene-DB)
                       primes · Malli gates · EQL serves)
experiments            suite-as-EDN, one runner, new experiment ≡ new EDN ¬new code;    BUILT (bb experiment
                       verdict ≡ human-gated conclusion; the editor's measurement       + founding suite)
                       substrate
scheduled-maintenance  2×2 proposer roster ({harness,app}×{coder,knowledge}),           DESIGNED (rung 1
                       role-as-tag (wired), 3-rung timer ladder, unattended discipline  after signals)
harness-coder          sessions → ONE evidence-cited harness recommendation →           DESIGNED (needs
                       proposals/ → human gate; Layer-2 only, Layer-1 flag              2 new tools)
                       bootstrap-scoped
```

## Reading paths

```
cold start        state.md → THIS → the page your task names
the agent system  agent-model → vsm-on-escapement → scheduled-maintenance → harness-coder
the comms story   signals → agent-comms (deferred half) → experiments (how emission was settled)
the chat engine   shadow-compaction → extra-body-seam (+ ouroboros-architecture, one level up)
```

## Standing build order (mirror of state.md λ tomorrow — state.md wins on conflict)

```
gene-DB substrate (anima resolvers/genes.clj ≡ prior art, read first) → signals substrate →
scheduled-maintenance rung 1 (bb maintain + 2×2 genomes + proposals inbox) → next-chat bootstrap
→ builder+author → editor → generator | channels/residency ⟸ only when interactive workflows exist
```
