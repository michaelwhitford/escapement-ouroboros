---
type: mementum/knowledge
title: The llama.cpp backend — a pure-consumer LLMBackend that adapts escapement's caching pattern
description: ouroboros.llm.llamacpp is our own escapement LLMBackend ⊕ StreamingLLMBackend, injected via lib/run's :backend escape hatch — it reaches llama.cpp's non-standard body knobs (chat_template_kwargs/id_slot/cache_prompt) WITHOUT forking escapement's closed typed Request, by reusing escapement's PUBLIC translate/parse/SSE and copying only the private HTTP glue; every knob rides a MODELED escapement field (:thinking→enable_thinking, cache-control marker→cache_prompt, :conversation/id→id_slot) so there is no :extra-body and no :metadata anti-pattern, and usage.cached_tokens flows to cache-report for free.
resource: file:///Users/mwhitford/src/escapement-ouroboros
status: active
category: design
tags: [ouroboros, escapement, llm, backend, llama-cpp, openai, caching, slots, thinking, cache-prompt, de-fork, escape-hatch]
related:
  - design/shadow-compaction
  - llama-cpp-prompt-cache
  - upstream/escapement-backends
depends-on:
  - upstream/escapement-backends
---

# The llama.cpp backend (`ouroboros.llm.llamacpp`)

> Durable names (grep against live source): `ouroboros.llm.llamacpp` — `wire-body`,
> `llama-wire`, `thinking-off?`, `caching-on?`, `slot-for`, `new-backend`, `LlamaCppBackend`;
> `ouroboros.models/llama-backend`; `ouroboros.smoke-llama` (`bb llama-smoke`). Escapement
> public seams reused: `escapement.llm.openai/request->openai-json` · `.../openai-json->response`
> · `.../stream-acc-init` · `.../process-sse-line!` · `.../stream-acc->openai-body`;
> `escapement.llm.http-transport/request`, `.../request-streaming`, `.../default-transport`;
> `escapement.llm.protocol/llm-error`, `.../LLMBackend`, `.../StreamingLLMBackend`;
> `escapement.llm.types/validate-request`, `.../validate-response`.

## Why it exists

escapement's typed `Request` (`escapement.llm.types`) is a **closed contract**: its OpenAI
translator emits a fixed key set and silently drops anything else. llama.cpp servers accept
non-standard body fields with no home in that shape:

```
chat_template_kwargs {enable_thinking false}   disable Qwen/llama.cpp reasoning (ON by default!)
id_slot N                                       pin a request to a dedicated KV-cache slot
cache_prompt true                               reuse a slot's warm prefix across requests
```

The earlier answer was a one-commit **fork** adding an `:extra-body` passthrough to the wire
layer. It worked but pinned the runtime dep to a personal sha and tracked escapement's
*internals*; the upstream PR was closed and the fork retired. The durable answer is to **own a
backend**: escapement is a library whose backend layer is meant to be extended, and a leaky
raw-body passthrough is not something to keep as a fallback.

## The seam that makes it fork-free

`escapement.lib/run` accepts an explicit **`:backend` escape hatch that wins verbatim** over
the `:credentials`-assembled backend. That swaps the *whole* backend, not the wire translator —
so a thin *decorator* around the stock `OpenAIBackend` **cannot** work (its translate→POST path
is internal; unmodeled keys are dropped before the POST). We therefore own translate→POST. The
crucial mitigation: almost everything is **public**, so we reuse it and copy only the minimum.

```
lib :backend  →  whole backend swapped  (this is where we inject)
run-turn → build-request → Request → backend.send-turn → request->openai-json → POST
                                          ↑ our one merge (llama knobs) lives HERE, in OUR backend
```

## Reuse boundary — public reused, private copied

- **Reused (public):** `request->openai-json` (translate), `openai-json->response` (parse), the
  SSE accumulator primitives, `http-transport/request(-streaming)`, `protocol/llm-error`,
  `types/validate-*`.
- **Copied (private, ~120 lines):** `post-chat!`, `stream-chat!`, and the error helpers
  `status->category` / `retry-after-ms` / `with-categorized-timeout` / `mask-key`.

**Copying > calling** is deliberate: the copied HTTP glue is FROZEN, so escapement can rewrite
its private `post-chat!`/`stream-chat!` freely without breaking us. We track only the *stable
public backend contract*.

## The design — adapt escapement's caching pattern, don't passthrough

escapement treats caching as a **backend concern driven by lightweight MODELED signals**
(cache-control markers + `:auto-cache?`), realized per-provider inside the backend, normalized
to a uniform `Usage` on the way out. We speak that same vocabulary. Every knob is a modeled
escapement field, so there is **no `:extra-body` and no `:metadata` overloading**:

| llama.cpp wire | ← modeled escapement field | notes |
|---|---|---|
| `chat_template_kwargs {enable_thinking false}` | `:thinking {:type :disabled}` | `:thinking` reaches our backend intact (only escapement's OpenAI translator drops it — the fn we replace) |
| `cache_prompt true` | any cache-control marker present | `build-request` CONSUMES `:auto-cache?` (default true) to STAMP `:system-cache-control` — the MARKER, not the flag, reaches a backend, so we read the marker |
| `id_slot N` | `:conversation/id` → `:slots` table | `:conversation/id` is escapement's designated prompt-cache correlation key; the backend maps it to a physical slot |
| `:cache-read-input-tokens` | `usage.cached_tokens` | FREE — reused `openai-json->response` already maps it, so `bb cache-report` works unchanged |

This is symmetric with escapement's Anthropic backend: it reads cache-control markers → emits
`cache_control`; we read them → emit `cache_prompt`.

## Slot policy lives in the backend, not the chart

Slot *numbers* are backend construction, next to the endpoint they depend on (the server's
`-np` slot layout — see `llama-cpp-prompt-cache`). The chart only declares WHICH conversation it
is; `models/llama-backend` builds the backend with a `{conv-id → slot-int}` table:

```clojure
;; runner:
(models/llama-backend :local {:hot 2 :compact 3})   ; the compact engine's slot convention
;; chart node just says which convo it is:
(h/llm-conversation {:id "hot" :conversation/id :hot ...})   ; → id_slot 2
```

This is why `compact.clj` migrated off `:extra-body {"id_slot" N …}` to `:conversation/id` +
an injected backend: the slot numbers left the chart and moved to backend construction.

## Why NOT `:metadata` (the anti-pattern we avoided)

An earlier candidate carried per-node knobs in escapement's open `:metadata` map. Rejected:
`:metadata`'s intent is provider *audit* info, and — worse than the semantic smell — it
**silently drops** if the wrong backend is installed (node-declared intent with no enforcement),
the exact silent-failure this whole effort escapes. Sourcing every knob from a modeled field
(`:thinking`, cache-control, `:conversation/id`) dissolves that: nothing is orphaned, and a
stock backend degrades sanely (thinking stays on, cache still works, no crash).

## Injection contract

Inject via `lib/run {:backend (models/llama-backend alias slots)}`. Still pass a dummy
`:credentials` (schema-required, closed) and the alias `:config` — escapement's `run-turn` still
resolves the model string via `:aliases`/`:preferences` before calling our backend (llama.cpp
ignores the model field, but resolution must not fail; keep `:eligibility-strict? false`).

## Gotchas / fail-modes

```
· `:thinking {:type :disabled}` reaches the wire ONLY through THIS backend. escapement's stock
  OpenAI translator drops :thinking — so on the released upstream, thinking-off is impossible
  without our backend (this is the whole point).
· `/no_think` prompt token does NOT disable qwen3.6 reasoning; only chat_template_kwargs
  {enable_thinking false} does (verified).
· llama.cpp ignores the request `model` field — it serves whatever is loaded. Verify by probing
  the SERVER (/v1/models, /props, /slots), not the alias string.
· Slot reservation is soft — no server-side mechanism; unpinned traffic can land on a pinned
  slot (cost: one re-prefill). See llama-cpp-prompt-cache for the -np server config that makes
  idle slots persist.
· cache_prompt rides the cache-control marker, which `build-request` only stamps when a prefix
  exists under :auto-cache?. A caller passing :auto-cache? false ⇒ no marker ⇒ no cache_prompt.
```

## Verify (runtime-checkable)

```
bb test                        deterministic — pure translation (thinking-off?, caching-on?,
                               slot-for, llama-wire, wire-body) — no network
bb llama-smoke                 live @ localhost:5100 (SKIPs if down): thinking OFF ⇒ response
                               message.reasoning_content == "" (vs a control call); id_slot
                               honored in /slots; a real send-turn returns answer + :usage
repl  (l/wire-body {:hot 2} (llm/build-request {:model "m" :messages [] :system "s"
        :thinking {:type :disabled} :conv-id :hot}))
      ⇒ {"chat_template_kwargs" {"enable_thinking" false} "cache_prompt" true "id_slot" 2 …}
bb cache-report                usage.cached_tokens → :cache-read-input-tokens per turn
```

## Provenance

Built after the upstream `:extra-body` PR was closed. Design settled through a discussion that
rejected two weaker carriers (a bare `:extra-body` map on our side; a `:metadata` sub-map) in
favor of modeled-field sourcing + backend-owned slot policy — adapting escapement's existing
caching abstraction rather than bolting on a passthrough. Live-proven against
Qwen3.6-35B-A3B (`qwen36-35b-a3b`) on port 5100; `bb test` green against released escapement
1.0.0-RC9 (Clojars), which is the proof no code depends on the retired fork.
