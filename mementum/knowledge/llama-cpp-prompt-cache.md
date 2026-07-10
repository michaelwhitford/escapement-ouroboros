---
type: mementum/knowledge
title: llama.cpp prompt-cache semantics vs λ-compaction
description: Empirically-proven (b9860, qwen3.6 hybrid) llama.cpp server cache semantics — id_slot pinning works but idle slots are saved+CLEARED on every task launch (unified KV default); host-cache restore is append-only/checkpoint-granular and fails on hybrid models for divergent prompts, so the per-turn λ-rewrite forces a full re-prefill; append-only turns reuse near-totally.
resource: https://github.com/ggml-org/llama.cpp
status: open
category: runtime
related:
  - design/shadow-compaction
  - design/extra-body-seam
tags: [ouroboros, llama-cpp, cache, kv, slots, prefill, compaction, latency]
---

# llama.cpp prompt cache vs λ-compaction (b9860, Qwen3.6-35B-A3B hybrid, port 5100)

💡 Slot pinning routes but does not protect. The cache lives in the HOST prompt
cache, restore is effectively append-only for hybrid models, and the per-turn
λ-rewrite therefore busts it every turn.

## Empirically proven (this session; method: /slots + usage.cached_tokens + server log)

```
id_slot on /v1/chat/completions      HONORED — log: "selected slot by id (N)". Works via the
                                     fork's :extra-body seam for BOTH hot and compact workers.
idle-slot clearing                   ON EVERY task launch, ALL idle slots are saved to the host
                                     prompt cache and CLEARED ("saving idle slot to prompt cache" /
                                     "clearing prompt with N tokens"). Unified-KV default behavior
                                     (--cache-idle-slots, kv-unified auto-on when -np absent).
                                     ⟹ in-slot KV NEVER survives another request launching —
                                     pinning cannot preserve a warm slot on a shared server.
append-only prompt (same convo +1)   RESTORES near-totally: hot turn = 61-token eval / 200ms
                                     (usage cached=2400+). The good path.
divergent prompt (λ-rewrite mid-array) cached=0, FULL re-prefill (2472 tokens / 1.5s at 2.5k ctx).
                                     Host-cache restore is checkpoint-granular; hybrid/recurrent
                                     models (qwen3.5/3.6 family) cannot truncate recurrent state at
                                     arbitrary positions → restore refuses divergent tails
                                     (upstream issues 20225 / 21831 / 19794 match our log lines
                                     "erased invalidated context checkpoint" exactly).
in-slot divergent reuse              WORKS when the slot was never cleared (controlled experiment:
                                     middle-rewrite reused 605/640) — reachable only on a server
                                     with no interleaving traffic at all.
```

## Consequence for the compact chat (k=1)

Every turn rewrites exactly one aged assistant message → every hot prompt diverges
from its stored predecessor → full re-prefill every turn. Cost is bounded BY the
compaction itself (λ keeps context small): ~1.5s at 2.5k, scaling ~0.5ms/token.
The greeting/system prefix (2400 tokens) DOES restore when the turn is append-only.

## Mitigation chosen + VERIFIED: dedicated slots (server params)

🎯 Human decision, ansible-deployed, live-verified same session:

```
-np 4                      EXPLICIT slot count ⇒ unified KV OFF ⇒ per-slot dedicated KV that
                           PERSISTS while idle ("save idle → clear" is unified-KV-only behavior;
                           verified: saves continue, "clearing prompt" GONE from the log)
-c 524288                  non-unified ctx SPLITS across slots → 131072/slot (verified /slots)
--ctx-checkpoints 32       hybrid restore is checkpoint-granular; denser checkpoints put a
--checkpoint-min-step 128  restore point near the λ-rewrite divergence
--cache-ram 16384          host cache headroom (was 7663/8192 = near-thrash)
```

VERIFIED RESULT (bb compact smoke, transcript usage + log): post-compaction hot turns went
cached=0 / 2472-token re-prefill / ~1.5s → cached=2400 / 67-token eval / 211ms. Log:
"restored context checkpoint (pos_min = 2399)" at the divergence boundary; stale checkpoint
past the rewrite correctly invalidated. Reuse advances in ~checkpoint-step (128-token) grains.
Full prefill is paid once per session, at start.

SLOT CONVENTION (🎯 human): ouroboros takes the TOP slots — hot → 2, compact → 3 (named
constants hot-slot/compact-slot in ouroboros.compact) — leaving 0/1 for the human's dynamic
clients. RESIDUAL RISK: no server-side slot reservation exists — unpinned traffic selects
similarity→LRU over ALL slots and can land on ours; recovery = one re-prefill, and the
expensive slot (hot) is shielded by being recently-used during active sessions while the
cheap one (compact, ~275-tok prompts) absorbs strays. Fallback: second llama-server instance
on its own port. SHELVED alternatives (not built): A batch compactions (backlog ≥ B);
B compactor → small-model server (@5105/@5104, needs scratch/ab_exemplar.clj A/B first).

## Verify (runtime-checkable)

```
curl :5100/props                     → total_slots, build
curl :5100/slots                     → per-slot n_prompt_tokens / processed after a run
usage.prompt_tokens_details.cached_tokens  → per-request reuse (escapement maps it to
                                     :cache-read-input-tokens in transcript :llm/response rows)
server log (--log-verbose)           → "selected slot by id/LRU" · "saving idle slot" ·
                                     "cached n_tokens =" · "restored/erased context checkpoint"
```
