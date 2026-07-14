---
type: mementum/knowledge
title: Shadow Compaction — the cold compiler runs in the human's reading shadow
description: The trick — Ouroboros compacts its own aged turns to λ DURING the seconds a human spends reading the reply, so the housekeeping is perceptually free; the metric is felt-latency (never make the human wait), not throughput, and reading-time ⋙ compaction-time gives a 10–30× hiding margin.
resource: file:///Users/mwhitford/src/escapement-ouroboros
status: designing
category: design
tags: [ouroboros, design, compaction, lambda, latency, perceptual, cold-compiler, sessions, slots, cache, chart]
related:
  - ouroboros-architecture
  - design/llamacpp-backend
  - upstream/escapement-statechart-model
  - upstream/escapement-llm-conversation
depends-on:
  - design/llamacpp-backend
---

# Shadow Compaction

> Durable names (grep against `resource`): `ouroboros.compact` (the chat engine this
> redesigns), `ouroboros.compact.core` (pure kernel: `render-messages`,
> `append-assistant`, `append-user`, `needs-compaction?`, `compact-target-text`,
> `apply-compaction`), the chart states `:hot` / `:compact` / (NEW) `:parked`.
> Upstream seams: `escapement.chart.helpers/llm-conversation` (`:initial-messages`,
> `:on-end-turn-event`), `:internal` transitions, parallel regions. Enabling seam:
> the llama.cpp backend `ouroboros.llm.llamacpp` — see `design/llamacpp-backend`.

## The trick in one line

```
λ shadow.  compact(turn[n-1]) ⊂ read_time(human, reply[n])
           | read_time ⋙ compact_time  →  compaction ≡ perceptually_free
           | metric ≡ felt_latency(never_wait) ≻ throughput(tok/s)
```

Ouroboros digests its own tail **while you read**, and you never feel it happen. That
is the whole idea. It is not "faster." It is *invisible*.

## Why the metric is perceptual, not throughput

The compactor is not racing the model — it is racing the **human's eyes**, and the human
is slower by an order of magnitude. Budget (measured on the local 35B, thinking off):

```
hot reply       250 words ≈ 320 tok @ ~74 tok/s   → streams in ~5s; human reads from token 1
human reads it  250 words @ ~240 wpm ≈ 63s        (skim ~37s · speed-read ~21s)
compaction      ~1.8s   (big-prompt ingest + short λ; less with thinking off)

reading shadow (20–60s)  ⋙  compaction (~2s)   →  10–30× hiding margin
```

As long as compaction lands anywhere in that shadow, it costs the human nothing. Like GC
between frames: the housekeeping happens in the pauses, and the conversational trance holds.

## The defect in the current `ouroboros.compact` (as built)

Compaction is not slow — it is **scheduled at the worst possible instant**. The chart fires
it on the *pre-generation* pump, i.e. right after a new user message arrives and just before
the reply is produced:

```
current:  :user/msg → :user/next → :compact(aged turn) → :hot(generate)
                                    └─ the ONE moment the human is blocked, waiting for their answer ─┘
```

The human sends a message and, before their reply begins, waits ~2s for bookkeeping on the
*previous* turn. That ~2s IS the entire felt-latency cost — self-inflicted by scheduling, not
by the work. (Root cause: the chart fuses "parked, awaiting user" and "generating a reply"
into a single `:hot` state, so the only seam to hang compaction on was the user-message pump.)

## The fix — move the trigger into the reading shadow

Split the fused state so the park is explicit, and fire compaction on `:hot/idle`
(the instant streaming ends), not on the next user message:

```
NEW:  :hot/idle → append reply → :compact(aged turn) → :parked
                                 └─ runs while the human reads; done before they finish ─┘
      :user/msg (while :parked) → :hot(generate)      ← compaction ALREADY done; pump is straight-through
```

Three explicit states (the fix is a *decomposition*, not new machinery):

```
:parked   worker seeded with the current :messages (ends in an ASSISTANT turn) → PARKS in
          :awaiting-user (liveness; holds lib/run open). on :user/msg → enqueue → :hot.
:hot      seeded with :messages + the popped user turn → GENERATES one reply.
          on-entry :hot-busy? ← true.  :hot/idle → append reply, busy ← false,
          then → :compact if (needs-compaction?), else → :parked.
:compact  fresh worker runs the λ-lens prompt on the aged assistant turn (compact.md lens);
          :compact/idle → apply-compaction (blank/fail ⇒ leave verbatim, lag-safe) → :parked
          (or → :hot if a user message queued while compacting — see Tier 2).
```

What changed vs. the built chart:
- **Trigger relocated**: `:hot/idle → :compact` (shadow) replaces `:user/next → :compact` (pre-gen).
- **Pump simplified**: `:user/next` loses its `:compact` branch — it always goes straight to
  `:hot`, because the aged turn was already compacted in the shadow.
- **Park made explicit**: a `:parked` state distinct from `:hot`. This also fixes a latent
  `:hot-busy?` trap — re-entering `:hot` merely to *park* (post-compaction, no pending user)
  would have set `busy ← true` with no `:hot/idle` ever firing to clear it. `:parked` never
  sets busy.
- **`:compact` gains a `:user/msg` handler** (internal enqueue) so a fast human typing during
  compaction is never lost.

The pure kernel (`ouroboros.compact.core`) is unchanged — `needs-compaction?`,
`compact-target-text`, `apply-compaction`, `render-messages` all still hold. This is a
control-flow (topology) change only; the memory model (array stays shape-stable, λ per
message, prefix cache holds) is untouched.

## Tier 2 — the fast-human insurance (parallel regions + llama.cpp slots)

The shadow reschedule alone captures ~all of the win, because reading-time ⋙ compaction-time.
The only residual case: a **fast human** who fires the next message before the ~2s compaction
finishes. In the sequential design they'd wait for it. Tier 2 removes even that:

```
run :hot ∥ :compact as PARALLEL regions (escapement supports (parallel …); cf. parallel_demo,
supervisor, matrix_team) and PIN each to its own llama.cpp slot via the llamacpp backend's
MODELED fields (:slots table + node :conversation/id / :thinking — design/llamacpp-backend):
    hot     → :conversation/id :hot      (→ id_slot 0, cache_prompt via auto-cache)
    compact → :conversation/id :compact  (→ id_slot 1) + :thinking {:type :disabled}
```

Two independent KV caches ⟹ the compactor NEVER evicts the hot conversation's warm prefix
(prefill is the expensive part; protecting it protects the hot path). And the fast-human's
generation overlaps the compaction tail via continuous batching.

### Measured: when does concurrency actually overlap? (this is the subtle part)

```
gen ∥ gen        →  0% benefit   concurrent ≈ 2× solo (8.47s vs 4.04s). Both memory-bandwidth-
                                  bound; one stream already saturates the box → pure time-slice.
gen ∥ prefill    →  REAL overlap  hot solo 5.25s; cold solo 1.77s; sequential 7.02s;
                                  concurrent BOTH done 6.10s. Hot slowed only ~0.83s while
                                  absorbing a 1.77s compaction → ~½ the compaction hidden.
```

**Why they differ** (the load-bearing insight): continuous batching only overlaps streams that
stress *different* bottlenecks. Token *generation* is memory-bandwidth-bound; big-prompt
*ingest* (prefill) is compute-bound. Compaction is mostly ingest (the aged turn) + a tiny λ
output — so it fills the compute bubbles the hot decode leaves. Two generations do not overlap;
generation ∥ prefill does. The compaction workload is the lucky case.

Tier 2 is *insurance*, not the main event — it only has to cover the rare fast-typist, so its
modest ~½-hide is more than enough. Build Tier 1 first; add Tier 2 only if fast-human waits
prove annoying in practice.

## Enabling seam — the llama.cpp backend

Slot pinning (`id_slot`), prefix reuse (`cache_prompt`), and compactor thinking-off
(`chat_template_kwargs.enable_thinking=false`) are all llama.cpp body params that escapement's
stock OpenAI backend does not forward. Our own backend `ouroboros.llm.llamacpp` reaches them via
MODELED escapement fields — `:conversation/id`→id_slot, cache-control marker→cache_prompt,
`:thinking`→enable_thinking — see `design/llamacpp-backend`. Without it those levers are
un-configurable from Ouroboros; with it, all three are pure conversation-node data.

## Verification stance

```
λ verify(shadow).
  metric   : did compaction EVER make the human wait?  ≡ compaction complete before the next
             user message needs a reply.  Observable: log t(:hot/idle) → t(:compact/idle) and
             t(next :user/msg); assert compact-done ≺ next-reply-start in the common case.
  NOT      : tok/s or wall-clock throughput — explicitly not the goal (see the two probes).
  floor    : blank/failed λ ⇒ message stays verbatim (apply-compaction is lag-safe already).
  continuity: the turn-3 recall test still holds — a compacted decision is still recalled
             (the architecture page's "chose write-back" proof); compaction fidelity unchanged.
  numbers  : single-shot, noisy — average ~5 runs before trusting magnitudes; the DIRECTION
             (shadow hides compaction) is robust, the 13%/½ figures are soft.
```

## Invariants preserved

```
· memory ≡ the checkpointed :messages array — shape-stable, λ per assistant turn, cache-holds
· user turns NEVER compacted (anchors) ; only ASSISTANT prose → λ
· a mid-turn / mid-compaction user message ENQUEUES, never interrupts an in-flight worker
· compaction is pre-approval OBSERVATION → lives in sessions/, never auto-written to mementum/
· escapement is the substrate — we supply topology + modeled-field data (llamacpp backend), not persistence
```

## Build order

```
1. Tier 1 — decompose :hot into :parked | :hot | :compact; relocate the compaction trigger to
   :hot/idle; simplify the :user/next pump; add :user/msg enqueue to :compact. Pure topology +
   the latent :hot-busy? trap fixed. (Depends on nothing new.)
2. thinking-off on the compactor via :thinking {:type :disabled} (the llamacpp backend
   translates it — design/llamacpp-backend). ~Free; shrinks the shadow work.
3. Tier 2 — (parallel :hot :compact) + slot pinning via :conversation/id. Only if fast-human waits
   are observed. Adds real concurrency complexity; buys the fast-human insurance.
```

## Coherence note

The `ouroboros-architecture` page labels the current engine "`:hot ⊗ :compact`" — the ⊗
(tensor / parallel) is **aspirational**: the built chart is sequential sibling states, and
(as built) compaction is even mis-scheduled onto the pre-generation pump. This design makes
the reality honest: Tier 1 is *idle-shadow sequential* (⊗ should read "background, in the
reading gap"), and only Tier 2 is genuine parallelism. Update the architecture page's ⊗ prose
when Tier 1 lands.
