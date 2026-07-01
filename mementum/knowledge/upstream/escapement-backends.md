---
type: mementum/knowledge
title: Escapement — LLM Backends
description: The LLMBackend protocol and its implementations (Anthropic, OpenAI, codex OAuth), the multi-backend dispatcher, content-addressed cache, and provider auto-detection.
resource: https://github.com/fulcrologic/escapement
tags: [escapement, llm, backends, anthropic, openai, codex, cache, providers]
status: active
category: upstream
related:
  - upstream/escapement-llm-conversation
  - upstream/escapement-library-embedding
depends-on:
  - upstream/escapement-llm-conversation
---

# Escapement — LLM Backends

> Named units: `escapement.llm.{protocol,api,openai,openai-codex,multi,cache,providers,types,catalog}`.
> Grep names against live `resource`.

## `LLMBackend` protocol

```clojure
(defprotocol LLMBackend
  (send-turn [this request] …))

(defprotocol StreamingLLMBackend
  (stream-turn [this request on-delta] …))
```

```
λ send-turn(this, request).  → Promise<Response> | reject via (proto/llm-error category msg)
  | request  ≡ escapement.llm.types/Request map
  | Response ≡ {:stop-reason :content :usage :model :backend-metadata?}
  | sync throw tolerated (back-compat) but promise rejection is canonical
λ stream-turn(this, request, on-delta).  → same Promise<Response>
  | invokes (on-delta {:type :text-delta :text "…"}) 0+ times
  | unknown :type (e.g. :thinking-delta) ignored by consumers | on-delta throws must NOT abort turn
λ streaming?(backend) → satisfies? StreamingLLMBackend
λ send-turn*(backend, request, on-delta) → streams iff (and on-delta (streaming? backend))
```

### Error taxonomy

```
error-categories = #{:rate-limited :overloaded :auth :invalid-request
                     :context-length :timeout :transport}
λ llm-error(category, message, opts) → ex-info {:llm/category :llm/status?}
λ error-category(throwable) → walks ex-cause chain → :llm/category | nil
```

## Backend implementations

### `escapement.llm.api` — Anthropic Messages API (Anthropic / z.ai / OpenAI-compat-Anthropic)

```
λ AnthropicAPIBackend [opts].
  auth auto-sniff: base-url contains "z.ai" → :bearer (Authorization: Bearer)
                   else → :x-api-key (x-api-key + anthropic-version: 2023-06-01)
  request→anthropic-json: near-passthrough; cache_control markers verbatim
    | tool-schema property keys munged to ^[a-zA-Z0-9_.-]{1,64}$ (?→_QMARK_ etc, reversible)
  HTTP: POST {base-url}/v1/messages
  status→category: 429→rate-limited | 529/503/"overloaded"→overloaded | 401/403→auth
                   | 400/422(+ctx-len phrase)→context-length | else 400/422→invalid-request
                   | timeout→timeout | conn/IO→transport
  streaming: SSE accumulator; result STRUCTURALLY IDENTICAL to buffered
  new-backend opts: required :api-key :base-url | optional :default-model :auth-mode
                    :anthropic-version :extra-headers :http-timeout-ms :http-transport :transcript-fn
```

### `escapement.llm.openai` — OpenAI Chat Completions (OpenRouter / vLLM / llama.cpp / Ollama)

```
λ OpenAIBackend [opts]. implements BOTH LLMBackend + StreamingLLMBackend
  request→openai-json: Anthropic-only inputs silently dropped (:cache-control :top-k :thinking …)
    | system → [{"role":"system"}] | tool_result → role:tool (is_error → inline "[error] ")
    | max_tokens key switch: older families (gpt-3.5/4/4o, glm-, kimi-, deepseek-, minimax-, mimo-)
      use max_tokens | newer (o-series, gpt-4.1+, gpt-5) use max_completion_tokens
  HTTP: POST {base-url}/chat/completions | Authorization: Bearer | honors Retry-After → :retry-after-ms
  response: cached_tokens (prompt_tokens_details) → :cache-read-input-tokens
  streaming: "stream":true + stream_options include_usage; assembles → identical translator
  new-backend opts: required :api-key :base-url | optional :default-model :extra-headers
                    :http-timeout-ms :http-transport :transcript-fn
```

### `escapement.llm.openai-codex` — ChatGPT Plus/Pro via OAuth (Responses API)

```
λ OpenAICodexBackend [default-model http-transport]. LLMBackend only (no streaming)
  auth: OAuth token at ~/.escapement/openai-auth.json (escapement login codex)
        | auto-refresh when within 60s of expiry | HTTP 401 → refresh once, retry
  send-turn: validate → get-auth! → build Responses-API body → post-responses-stream! (timeout 180s)
             → openai-response->anthropic-response (canonical Response)
  new-backend opts: optional :default-model (default "gpt-5.1-codex") :http-transport
```

## `escapement.llm.multi` — multi-backend dispatcher

```
λ MultiBackend [routes default-backend provider-index]  (StreamingMultiBackend if any sub streams)
  routes = vector of [matcher sub-backend] OR [matcher sub-backend provider-kw]
  matcher: Pattern (re-find vs model) | #{strings} (membership) | (fn [model] bool)
  select-backend priority:
    1. request :provider kw AND provider-index has it → direct dispatch
    2. else first route whose matcher matches model string
    3. else :default-backend
    4. else throw ex-info {:model :route-matchers}
  | 3-tuple routes build provider-index {provider-kw → sub-backend} (first wins)
  | stream dispatch: per-call; one non-streaming sub does NOT disable streaming globally
  | new-backend → StreamingMultiBackend iff (some streaming? subs)
```

## `escapement.llm.cache` — content-addressed disk cache

```
λ CachingBackend [inner dir].
  cache-key(request) = SHA-256(pr-str {:model :system :messages :tools})
    | sampling params (:temperature :top-p :max-tokens) NOT in key
  store: {dir}/{sha256}.edn | atomic write (.tmp → ATOMIC_MOVE + REPLACE)
  send-turn: hit → resolved cached | miss → then(inner.send-turn, write!-and-return)
  activation: enabled-by-env? ← ESCAPEMENT_LLM_CACHE ∈ {"1" "true" "yes"}
  caching-backend(inner, cache-dir) → CachingBackend
```

## `escapement.llm.providers` — auto-detection / precedence & hermetic injection

```
λ detect-available-credentials → [descriptor …] ordered by precedence:
```

| Env var | provider :kind | base-url | route regex | default model |
|---|---|---|---|---|
| `ANTHROPIC_API_KEY` | `:anthropic` | api.anthropic.com | `^claude-` | claude-sonnet-4-6 |
| saved OAuth | `:codex` | (internal) | `^gpt-5` | gpt-5.1-codex |
| `OPENAI_API_KEY` | `:openai` | api.openai.com/v1 | `^gpt-` | gpt-4o-mini (`$OPENAI_MODEL`) |
| `OPENROUTER_API_KEY` | `:openrouter` | openrouter.ai/api/v1 | `.+/.+` | openai/gpt-4o-mini (`$OPENROUTER_MODEL`) |
| `ZAI_API_KEY` | `:zai` | api.z.ai/api/anthropic | `^glm-` | glm-4.6, `:http-timeout-ms 300000` |
| `OPENCODE_GO_API_KEY` | `:opencode-go-*` | opencode.ai/zen/go(/v1) | `^(glm-\|kimi-\|mimo-)` / `^minimax-` | glm-5 / minimax-m2.7 |
| `OLLAMA_API_KEY` | `:ollama` | ollama.com/v1 | `^(kimi-\|deepseek-\|glm-\|minimax-\|gpt-oss)` | kimi-k2.5 (`$OLLAMA_MODEL`) |

> z.ai sets a 5-min HTTP timeout because glm-5.x buffers non-streaming turns past the 60s default.

```
λ build-credential-backend(descriptor) → LLMBackend  (constructors resolved lazily via require+resolve)
  :anthropic/:zai/:opencode-go-anthropic → api backend
  :openai/:openrouter/:ollama/:opencode-go-openai → openai backend
  :codex → codex backend

λ build-injected-credentials-backend(descriptors, pref-targets) → MultiBackend | nil   [HERMETIC]
  | NEVER reads env or disk | merges caller {:provider :api-key …} vs provider-templates
  | route order by preference-rank (providers first in flattened pref targets → lower-index routes)
  | routes are 3-tuples [regex backend provider-kw] enabling :provider-keyed dispatch
  | first resolvable descriptor → :default-backend | nil when none resolve
  | THIS is the path escapement.lib/run uses
```

## Model catalog (`escapement.llm.catalog` + the bundled models-api.json)

```
λ catalog. models-api.json ≡ per-provider model registry (~140 providers) — LARGE
  | ALWAYS query with jq (e.g. jq -r '.anthropic.models|keys[]'), NEVER read whole
  | catalog/info(model) → objective facts (deep-merged with local-models/local-providers overlay)
  | feeds the :needs eligibility gate (see escapement-llm-conversation, model selection)
  | catalog/max-output-tokens(model) → the per-turn output cap (limit.output); fallback 8192
```

## Request/Response types (`escapement.llm.types`)

```
Request  : {:model :messages :system? :tools? :max-tokens? :temperature? :thinking? :tool-choice? :metadata?}
Message  : {:role :user|:assistant :content [ContentBlock]}
Block    : dispatch :type ∈ {:text :image :tool_use :tool_result :thinking :redacted_thinking}
Response : {:stop-reason :content :usage :model :backend-metadata?}
Usage    : {:input-tokens? :output-tokens? :cache-creation-input-tokens? :cache-read-input-tokens?}
StopReason: :end_turn :max_tokens :tool_use :stop_sequence :pause_turn :refusal
```
