---
type: mementum/knowledge
title: escapement :extra-body — the OpenAI-wire provider escape hatch
description: A ~18-line escapement patch adding an :extra-body passthrough so callers can inject raw OpenAI/llama.cpp body params (chat_template_kwargs, id_slot, cache_prompt) escapement doesn't model; it survives four closed gates (Request schema → build-request → run-turn call site → openai wire), OpenAI-only, caller-wins-on-merge, not in the content-cache key.
resource: https://github.com/fulcrologic/escapement
status: designing
category: design
tags: [ouroboros, escapement, llm, openai, llama-cpp, passthrough, thinking, slots, cache, patch, upstream]
related:
  - design/shadow-compaction
  - upstream/escapement-backends
  - upstream/escapement-llm-conversation
depends-on:
  - upstream/escapement-backends
---

# escapement `:extra-body` — provider escape hatch

> Status: patch written on the escapement clone branch `mw_extra_body` (RC9 base),
> UNCOMMITTED, full test suite green (413 tests / 2260 assertions). Good upstream-PR
> candidate. Durable names (grep against `resource`): `escapement.llm.types/Request`,
> `escapement.llm/build-request`, `escapement.llm/run-turn`,
> `escapement.llm.openai/request->openai-json`, `escapement.llm.cache` (cache-key).

## Why it exists

escapement's OpenAI backend builds the wire body from a **closed** key set — even
`:thinking` and `:top-k` are dropped on the OpenAI path — with **no** arbitrary-body
passthrough. So provider-specific knobs that only exist on OpenAI-compatible servers
(notably llama.cpp) were **un-injectable** from a chart:

```
chat_template_kwargs {enable_thinking false}   disable Qwen/llama.cpp reasoning (thinking is ON by default!)
id_slot N                                       pin a request to a specific KV-cache slot
cache_prompt true                               reuse a slot's warm prefix across requests
```

These are exactly the levers `design/shadow-compaction` Tier 2 needs (slot pinning + compactor
thinking-off), and the finding that the local model runs with **thinking ON** (burning reasoning
tokens before every reply) has no other fix short of a global server flag.

## The four closed gates (the design constraint)

A new body param must survive an unbroken chain; miss any link and it is silently dropped:

```
h/llm-conversation node key
  → params (conversation node config)
    → run-turn  — EXPLICITLY rebuilds a map from params (does NOT forward verbatim)   ← easy-to-miss gate
      → build-request  — closed cond-> → Request map
        → types/Request schema  — closed :map (Malli validates before send)
          → request->openai-json — closed cond-> → wire JSON
```

## The patch (shape, not coordinates — grep the named vars)

```
types/Request            add  [:extra-body {:optional true} [:maybe [:map-of :string :any]]]
build-request            destructure extra-body ; (seq extra-body) (assoc :extra-body extra-body)
                         + one docstring bullet (caller contract)
run-turn call site       add  :extra-body (:extra-body params)   ← the gate most people forget
request->openai-json     destructure extra-body ; (seq extra-body) (merge extra-body)  — LAST branch
```

Design decisions:
- **Merge LAST ⇒ caller wins.** `extra-body` merges over the framework-produced body, so it can
  also override a modeled key (e.g. force `temperature`). Maximal-passthrough intent. Flip to
  `(merge extra-body base)` if you want it to only *add*, never clobber.
- **OpenAI-only, by design.** The Anthropic path (`escapement.llm.api`, near-passthrough) drops
  it. Fine for the llama.cpp target; extend `api.clj` later if Claude ever needs it.
- **Empty/absent ⇒ omitted.** `(seq extra-body)` guards both assoc and merge — no key pollution,
  no wire change when unused.

## Usage from Ouroboros (pure config — zero further code)

```clojure
;; disable local-model reasoning + pin the compactor to its own slot:
(h/llm-conversation
  {:id "compact" :model :local
   :extra-body {"chat_template_kwargs" {"enable_thinking" false}
                "id_slot" 1 "cache_prompt" true}})
```

## Gotchas / fail-modes

```
· CACHE COLLISION — escapement.llm.cache keys on SHA-256(pr-str {:model :system :messages :tools});
  :extra-body is NOT in the key. With ESCAPEMENT_LLM_CACHE=1, a thinking-ON vs thinking-OFF request
  (same messages) would COLLIDE on a stale entry. Fix = add :extra-body to the cache-key tuple
  (5th, optional edit). For local Ouroboros use the disk cache is off → left unpatched.
· llama.cpp ignores the request `model` field — it serves whatever is loaded. Verify behavior by
  probing the SERVER (/v1/models, /props, /slots), not by trusting the alias string.
· `/no_think` prompt token does NOT work on the qwen3.6 template — only chat_template_kwargs
  {enable_thinking false} disabled reasoning in testing. Prompt-side thinking-off is unreliable.
```

## Verify (runtime-checkable)

```
grep    escapement.llm.openai/request->openai-json → the (seq extra-body) (merge extra-body) branch
repl    (-> (build-request {:model "q" :messages [] :extra-body {"k" 1}})
            escapement.llm.openai/request->openai-json (get "k"))  ⇒ 1
schema  (malli.core/validate types/Request (build-request {... :extra-body {"k" 1}}))  ⇒ true
server  POST /v1/chat/completions {... "chat_template_kwargs":{"enable_thinking":false}}
        ⇒ response message.reasoning_content == ""   (thinking suppressed)
```

## Provenance

Discovered while verifying the local model config: the model is **Qwen3.6-35B-A3B**
(`qwen36-35b-a3b`, not the `qwen35-35b-a3b` string hardcoded in the Ouroboros charts) and it
runs with **thinking ON**. Tracing whether escapement could pass `chat_template_kwargs`
confirmed the closed-gate chain (RC9 source), which motivated this patch. Escapement is now
**1.0.0-RC9** (released) — the earlier "not even alpha" note in state.md/knowledge is stale.
