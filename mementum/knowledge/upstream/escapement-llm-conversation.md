---
type: mementum/knowledge
title: Escapement — The :llm-conversation Invocation
description: The :llm-conversation worker IS a resident chat session — it parks in :awaiting-user between turns (a live invocation that holds the runner open) and is fed user messages via tell-llm; flat authoring keys; model selection = :needs gate × :llm/preferences ranking × keyword aliases.
resource: https://github.com/fulcrologic/escapement
tags: [escapement, llm, invocation, model-selection, prompt-caching, helpers]
status: active
category: upstream
related:
  - upstream/escapement-statechart-model
  - upstream/escapement-backends
  - upstream/escapement-multi-agent-and-services
  - upstream/escapement-tools
depends-on:
  - upstream/escapement-statechart-model
---

# Escapement — The `:llm-conversation` Invocation

> Named units: `escapement.chart.helpers` (aliased `h/`),
> `escapement.invocation.llm-conversation`, and the model-selection namespaces
> `escapement.llm.{needs,preferences,ratings,catalog}`. Grep names against live `resource`.

## `h/llm-conversation` — the authoring sugar

```
λ h/llm-conversation(opts).
  → (invoke {:type :llm-conversation :id id :autoforward true
             :params (fn [env data] resolved-params)})
  | :autoforward? defaults TRUE — this is what makes tell-llm work
  | each opts value: literal OR (fn [env data]) resolved at invoke time
```

## Worker lifecycle — a conversation IS a resident chat session

```
λ worker(conversation). state ∈ {:running :awaiting-user :dying}
  start: (seq initial-msgs) → :running | ∅ initial message → :awaiting-user (born parked)
  after EVERY turn: post :on-end-turn-event(:llm.idle {:text|:output-ref :from}) to parent
                    → park :awaiting-user → poll user-msg-queue (ArrayBlockingQueue)
  tell-llm → :llm.user-message → autoforward → user-msg-queue → drained at :awaiting-user
             → applied to the VERY NEXT turn (between-turn; steer latency ≡ 1 turn, always —
             mid-turn injection paths buffer to the same queue, identical latency)
  PARKED WORKER ≡ LIVE INVOCATION → runner waits UNBOUNDEDLY (resident chart, no timer tricks)
  budgets are OPT-IN caps, not defaults: no :max-turns/:budget-ms → parks forever (chat-shaped)
  死: owning state exits externally → :dying (respawn on re-entry ⟺ re-invoked)

⟹ USE-CASE (the load-bearing one for Ouroboros): multi-turn human↔LLM chat is the PRIMITIVE.
  chart: resident conversation region + sibling region transitioning on external :user/msg
  (:type :internal) → tell-llm. External events are deliverable because the parked worker
  holds the run open. NO human-input, NO renderer, NO lib patch required for chat.
  Reference charts: examples/steered_convo (between-turn injection), supervisor
  (monitor+steer+capture), scan (tell-llm re-drive loop). human-input ≡ MODAL prompts
  (select/confirm/approval gates) — a different primitive for a different job.
```

## The full authoring-key catalog

| Key | Meaning / contract |
|---|---|
| `:id` | **REQUIRED**. Becomes `:invokeid`. Control key, not in params. |
| `:system` | System prompt string. |
| `:message` (alias `:initial-user-message`) | Opening user turn. Present → worker starts `:running`. Absent → `:awaiting-user` (parks until `tell-llm`). Canonical wins if both. |
| `:initial-messages` | Vector of pre-built message maps (multi-block first turn, image, prior exchange). Non-empty → takes precedence over `:message`, starts `:running`. |
| `:real-tools` | Selector. **Absent/`nil` = expose EVERY registry tool** (recommended). Vector/set of keywords = whitelist. Unknown kw throws at start. |
| `:allowed-events` | `[{:event kw :data-schema malli :description str}]` → synthesized `event__<encoded>` tools. |
| `:chart-tools` | `[{:owner state-id :as kw-prefix?}]` → region tools from service regions → `region__<encoded>`. Palette **snapshotted at conversation start**. |
| `:model` | **Alias keyword** (e.g. `:kimi2.6`). When set, `:llm/preferences` NOT consulted. String `:model` = `:error.llm.invalid-request :detail :string-model`. |
| `:models` | Ordered vector of **alias keywords**. Failover across flattened targets. Disables `:llm/preferences`. String element = error. |
| `:needs` | Eligibility GATE (only when no `:model`/`:models`). See below. |
| `:verdict-schema` | Malli schema → forced `submit_verdict` wrap-up inference at end-turn. See multi-agent page. |
| `:on-end-turn-event` | Default `:llm.idle`. Data: `{:text/:output-ref :from invokeid}`. |
| `:max-turns` | Positive int. Caps round-trips. Exceeded → `:error.llm.max-turns {:limit N :turns N}`, dies. |
| `:budget-ms` (alias `:max-conversation-duration-ms`) | Wall-clock budget start→clean `:end_turn`. Exceeded → `:error.llm.timeout`. NOT the same as backend `:http-timeout-ms`. |
| `:budget-extender` | **RAW** fn (not resolved). Called `(f {:messages :turn-count :max-turns :elapsed-ms :params :env})` at cap. |
| `:stream?` | Bool. SSE; publishes `:llm/delta {:type :text-delta\|:thinking-delta :text :model :invokeid}` mid-turn. Final Response byte-identical. No-op on non-streaming backends. |
| `:resilience` | `{:max-retries 3 :backoff-ms 500}`. Transient categories auto-retry w/ backoff before `:models` fallback. Terminal categories never retry. Opt-in sub-keys `:overrun`, `:latency {:first-token-ms :fallback}`. |
| `:temperature` / `:top-p` / `:top-k` | Sampling. `(0,1]` for temp. Don't combine temp with `:thinking`. |
| `:stop-sequences` | Vector of strings; model halts on any (excluded from result). |
| `:thinking` | `{:type :enabled :budget-tokens 4096}`. Anthropic extended thinking. Blocks recorded in history, chart never sees them. Need `max-tokens > budget-tokens`. |
| `:tool-choice` | `:auto` \| `:any` \| `:none` \| `{:type :tool :name "fs_read"}`. `:disable-parallel-tool-use true` optional. |
| `:metadata` | `{:user-id "..."}` — Anthropic audit. Use stable non-PII id. |
| `:auto-cache?` | **Default true**. Fills absent cache markers with `{:type :ephemeral}` + rolling message breakpoint. See caching below. |
| `:system-cache-control` / `:tools-cache-control` | Marker map `{:type :ephemeral [:ttl :1h]}` \| `false` \| absent. |
| `:message-cache-control` | Anthropic-only. Rolling breakpoint on last stable message. `false` disables. `{:strategy :last-stable\|{:tail N} :ttl :5m\|:1h}`. |
| `:conversation/id` | Opaque; openai-codex prompt-cache key. `api` backend ignores. |
| `:max-tokens` | **NOT a chart param.** Output cap resolved automatically from model catalog (`limit.output`). Setting it has no effect. Unknown models → backend default 8192. |

## Model selection — the full pipeline (most important)

```
λ model_selection. THREE inputs combine:
  :needs           (chart node)        → eligibility GATE that FILTERS
  :llm/preferences (host config)       → ordered alias-keyword vector that RANKS
  :llm/aliases     (host config)       → alias-kw → ordered [{:provider :model :params}]
  :llm/ratings     (host config)       → alias-kw → subjective opinion map (overlay)

STAGE 1 — needs → policy            (escapement.llm.needs/needs->policy)
  k v       → :require {k v}   (exact equality)
  k [:>= n] → :min {k n}       (inclusive floor)
  k [:<= n] → :max {k n}       (inclusive ceiling)
  | ONLY :>= and :<= accepted (no :> :< :=) | all clauses AND | empty admits all
  | malformed entry → ex-info naming offending key
  | legacy :intelligence N ≡ :needs {:intelligence [:>= N]}

STAGE 2 — alias expansion + ordering   (escapement.llm.preferences)
  node :models [kw…] | :model kw | else :llm/preferences (or default-preferences)
  → flatten-targets(prefs, aliases) → ordered, de-duped [{:provider :model :alias}]
  | order = AUTHOR order of candidate aliases' targets — NEVER reordered by ratings

STAGE 3 — needs GATE filtering (at TARGET granularity)   (escapement.llm.catalog)
  ∀ candidate: target-satisfies-policy?(model, policy, ratings[alias])
    objective facts ← catalog/info(model)   (models.dev dump + local overlay)
    subjective facts ← ratings[alias-kw]     (NOT model string — alias-keyed!)
    all :require/:min/:max clauses must hold
  | multi-provider alias: drop ineligible targets; alias survives iff ≥1 target eligible

STAGE 4 — fail-open vs fail-closed
  gate empties eligible list:
    DEFAULT fail-open → use unfiltered list + emit :llm/model-policy-empty warning
    :config :llm/eligibility-strict? true → fail-closed → :error.llm.invalid-request
                                            :detail :eligibility-empty-strict

STAGE 5 — model-status de-prioritization
  a model-status atom tracks :down models | skip them | all down → fall back to full list
  | a target erroring (429/network/parse) → marked :down for REST of process
```

### Objective facts (`escapement.llm.catalog/eligibility-facts`)

`:vision?` `:tool-call?` `:reasoning?` (booleans) · `:context-tokens` `:max-output-tokens`
(ints) · `:company` `:family` `:knowledge` (strings).

### Subjective ratings

Any host-defined key from `:llm/ratings` (`:intelligence` `:clojure` `:ux` `:tier` …),
keyed by **alias keyword**. With no ratings configured, a subjective clause matches nothing.

## `:llm/aliases` — single source of truth for targets

```clojure
{:llm/aliases
 {:kimi2.6 [{:provider :opencode-go :model "kimi-k2.6"}
            {:provider :deepinfra   :model "moonshotai/Kimi-K2.6" :temperature 0.3}
            {:provider :fireworks   :model "accounts/fireworks/models/kimi-k2p6"}]}}
```

```
λ aliases. alias-kw → non-empty ordered vector of CLOSED target maps
  | target: required :provider :model | optional :temperature :top-p :top-k :thinking :max-tokens
  | KEYWORD is the ONLY legal model reference anywhere
  | string :model / string :models element / {:provider :model} in prefs / string :llm/ratings key
    → ALL categorized errors (migration from old pair/string shapes)
  | preference/rating kw not in :llm/aliases → config validation error (referential integrity)
  | param precedence: node authoring-key OVERRIDES target value (merge target-under-node)
  | unknown alias named on node → :error.llm.invalid-request :detail :unknown-alias
```

Built-in defaults (the `escapement.llm.preferences` namespace's `default-aliases` /
`default-preferences`): aliases `:default-glm :default-sonnet :default-opus :default-gpt`;
preference order `[:default-glm :default-sonnet :default-opus :default-gpt]`.

## Conversation event vocabulary (what the chart catches)

```
:llm.idle                 → :on-end-turn-event default; {:text/:output-ref :from invokeid}
:llm.user-message         → tell-llm / tell-other-llm injection (has optional :target)
:error.llm.backend        → uncategorized throw; fires after EVERY candidate tried; :attempts [{:model :error}]
:error.llm.<category>     → rate-limited|overloaded|auth|invalid-request|context-length|timeout|transport
                          → categorized; transient ones fire only after retries exhausted; terminal fire immediately
:error.llm.tool-validation → tool input failed schema after one retry
:error.llm.unexpected-stop → :stop_sequence|:pause_turn|:refusal, or stuck :max_tokens (:detail :no-forward-progress)
:error.llm.max-turns       → :max-turns exceeded
:error.llm.timeout         → :budget-ms exceeded OR backend timeout category
:error.llm.worker-exception → uncaught worker throwable
:error.llm.verdict-validation → forced verdict failed schema (see multi-agent page)
```

Every `:error.llm.*` carries `:category` (matched keyword, or nil if uncategorized). Catch
the family with `(transition {:event :error.llm.* :target :recover})`. The old
`:on-error-event` option was removed; the canonical event always fires. Recovery (retry w/
backoff) happens *before* these fire.

## tell-llm helpers (drive a live worker)

```
λ h/tell-llm({:expr (fn [env data] text}).
  → script that send! {:event :llm.user-message :data {:text (expr …)}}
  | REQUIRES :autoforward? true on owning conversation
  | broadcasts to ALL live invocations (no :target)
  | MUST execute while owning state active — else event silently DROPPED

λ h/tell-other-llm({:target id-or-fn :expr fn}).   → targeted :llm.user-message
λ h/tell-other-llm!(env, target, text).            → imperative form (inside handler body)
```

See escapement-multi-agent-and-services for `:target` routing, verdicts, artifacts, and
`with-llm-questions`.

## Prompt caching (one-paragraph mental model)

`:auto-cache?` (default true) stamps `{:type :ephemeral}` (5-min TTL) on system + last tool
def, plus a rolling message-level breakpoint that advances each turn (last *stable*
message), so the cached prefix grows while the newest turn stays uncached. Anthropic's
4-breakpoint cap is shared system → tools → messages. Biggest wins: tool-heavy single-state
loops. `:auto-cache? false` emits no markers at all. Message caching is Anthropic-only
(no-op elsewhere).

## Output cap vs turns (continuation)

`:max-tokens` caps generation in ONE assistant HTTP response — not the conversation, not a
tool round-trip. A normal `:max_tokens` truncation is **continued transparently** (no knob);
only a continuation making *no forward progress* fires `:error.llm.unexpected-stop :detail
:no-forward-progress`. `:max-turns` / `:budget-ms` are the worst-case guards on the whole
conversation.

## Script-facing one-shot API (`escapement.llm`)

`escapement.llm` (a `.clj` namespace; blocks; `future`-based fan-out) is for a chart
`script` that needs a single answer now, or the same prompt over a collection in parallel.
Resolves models through the SAME engine (`run-turn`) — `:needs` gate + failover identical.
Every public call returns a uniform result envelope (status-tagged), never throws on backend
failure.
