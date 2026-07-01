---
type: mementum/knowledge
title: Escapement — Statechart Model, Binding & Tool Duality
description: A worker thread holds the LLM while its chart state is active; the chart sees only declared event-tools, never real tools; :type :internal keeps the worker alive across hops.
resource: https://github.com/fulcrologic/escapement
tags: [escapement, statecharts, invocation, tools, lifecycle, checkpoint]
status: active
category: upstream
related:
  - upstream/escapement-overview
  - upstream/escapement-llm-conversation
  - upstream/escapement-tools
  - upstream/escapement-multi-agent-and-services
depends-on:
  - upstream/escapement-overview
---

# Escapement — Statechart Model, Binding & Tool Duality

> Core named units: `escapement.invocation.llm-conversation` (the engine),
> `escapement.chart.helpers` (authoring sugar, aliased `h/`),
> `escapement.engine.store` (checkpointing). Grep these names against the live `resource`.

## Vocabulary

```
Chart           ≡ Fulcrologic statechart value | (com.fulcrologic.statecharts.chart/statechart)
                | elements: state · parallel · final · transition · script · on-entry · invoke
LLM-bound state ≡ compound state whose body has (invoke {:type :llm-conversation ...})
                | authoring sugar = h/llm-conversation
Real tool       ≡ LLM acts on world (:fs/read :shell/run) | dispatched in worker | chart never sees
Event tool      ≡ 1 synthetic tool per :allowed-events entry | call → validated chart event
Region tool     ≡ subchart-as-tool | region__<name> | synchronous req/reply
Transcript      ≡ JSONL, single-writer daemon thread, FIFO, per-row :seq
Checkpoint      ≡ EDN of working-memory, temp-file + atomic rename, after EVERY event
Resume          ≡ reload latest checkpoint + continue | re-spawns invocations idempotently
```

## Conversation binding lifecycle

`LlmConversationProcessor` implements the statecharts `InvocationProcessor` protocol.
Authors never construct it directly — they use `h/llm-conversation`.

```
λ start-invocation!(env, {:invokeid id :params params}).
  → build tool defs (real ⊕ event ⊕ region)
  → spawn daemon Thread "llm-conv-<session-id>-<invokeid>"
  → worker-state atom ← :running (if initial messages) | :awaiting-user (else)
  → store thread in workers atom @ key [parent-session-id invokeid]
  | invocation LIVES while owning state ∈ configuration set
  | IDEMPOTENT on re-entry: existing entry same key → mark old :dying → respawn

λ stop-invocation!(env, {:invokeid id}).                        [called on state EXIT]
  → mark worker-state :dying → interrupt thread → drop from workers atom

worker-state ∈ {:running :awaiting-user :dying}                 [run-worker!]
  :running       → handle-running-turn! → :continue | :idle | error-and-die
  :awaiting-user → poll user-msg-queue (200ms slices) | message → :running
  :dying         → emit :llm/worker-exit | loop exits
  INVARIANT: transition-state! is a CAS that REFUSES to overwrite :dying
             → once engine calls stop-invocation!, worker can't re-enter :running
```

### End-of-turn boundary (`:end_turn`)

```
λ on :end_turn.                                                 [handle-running-turn!]
  1. collect final text from content blocks
  2. IF :verdict-schema set → run-verdict-inference! (forced submit_verdict tool)
  3. finalize-idle-data → externalize full text to ArtifactStore
     → REPLACE :text with :output-ref + :io/snippet in event payload
  4. post :llm.idle (default :on-end-turn-event) to parent:
     {:text … :from invokeid :output-ref …}     ; :text ABSENT when artifact store present
  5. transition worker-state → :awaiting-user                  ; PARKS — does not die
```

> Read assistant output with `h/deref-output` — never `(get-in data [:_event :data :text])`
> directly, because `:text` is replaced by `:output-ref` when an ArtifactStore is present.

## Tool duality — how an event tool becomes a chart event

```
λ event-tool-defs(allowed-events).
  ∀ {:event :description :data-schema} →
    tname ← "event__" + kw->anthropic-name(event)
    def   ← {:name tname :description … :input-schema (malli->json-schema (or data-schema [:map]))}

:allowed-events entry shape:
  {:event       :hello/done                       ; REQUIRED keyword → tool name
   :description "…"                               ; optional
   :data-schema [:map [:greeting :string]]}       ; Malli → JSON Schema for LLM

λ on tool_use(event__*).                                        [handle-tool-use-block]
  → m/decode schema input tool-input-transformer   ; coerces stringified-JSON (small-model compat)
  → m/validate schema decoded
    valid          → post-event-to-parent! decoded as event data; tool_result "ok"
    invalid (1st)  → error tool_result; bump retry-counts[id]
    invalid (2nd)  → :error.llm.tool-validation to chart; worker dies

λ post-event-to-parent!(ctx, event, decoded).
  → send! {:target parent-session-id :invokeid invokeid :event event :data decoded}
  | engine matches it against (transition {:event :hello/done …}) next cycle
```

`tool-input-transformer` = `json-string-transformer` + Malli's string-transformer —
decodes stringified-JSON values for `:vector`/`:sequential`/`:set`/`:map` fields so small
models that emit JSON-as-string still validate.

Real tools, by contrast, are looked up in the tool-registry and dispatched via
`tp/dispatch` **inside the worker**; the chart never observes them.

## Internal vs external transitions — THE survival rule

```
λ transition_semantics.
  external (DEFAULT) → exiting source state tears down its children + ALL active invocations,
                       then re-enters from scratch
  internal {:type :internal} → target is descendant → source NOT exited
                       → configuration updated in place → invocations PRESERVED

INVARIANT (the iterate pattern):
  whenever tell-llm drives a multi-step loop, ALL transitions between the child
  states MUST be :type :internal — else the bound llm-conversation is destroyed
  and the accumulated message history is lost.
```

Worked shape (the `iterate` example): the `llm-conversation` is bound at `:work`;
transitions `:read-spec → :propose-patch → :run-tests → :reflect` are ALL `:type :internal`,
so `:work` is never exited and the conversation lives for the whole loop. The `scan` example
uses an internal transition to accumulate many `:scan/found-bug` events while one terminal
`:scan/complete` uses a normal external transition to leave.

## Parallel regions

```
λ parallel. (parallel {:id :work} (state regionA …) (state regionB …))
  | each region gets its OWN worker thread → concurrent conversations
  | event-tool names encode the event keyword → NO tool-name collisions across regions
  | when ALL children reach a final substate → library raises :done.state.<parallel-id>
    → parent transitions to its own final
```

## Checkpointing & the store

```
λ FileBackedStore.
  save-working-memory!(store, env, sid, wmem)
    → update in-memory cache
    → atomic-write-edn! file wmem                 ; .tmp → ATOMIC_MOVE + REPLACE_EXISTING
    | crash mid-write CANNOT corrupt canonical .edn | file: <dir>/<sid>.edn
  get-working-memory!(store, env, sid)
    → cache hit → return (NO disk read)           ; hot reads never hit disk
    → miss → read-edn-file → populate cache
  reload-from-disk!(store, sid) → drop cache entry   ; sim restart (tests)
  new-store(dir) → ->FileBackedStore | injected as the working-memory-store in env

WHEN: the v20150901 algorithm calls save-working-memory! after each event's macrostep.
      Queue delivers one event at a time → working memory is durable at single-event
      granularity. Resume = re-enter saved configuration on next event.
```

## Reading the triggering event inside a transition

```clojure
(transition {:event :hello/done :target :finished}
  (script {:expr (fn [_env data]
                   [(ops/assign :greeting (get-in data [:_event :data :greeting]))])}))
```

The library exposes the triggering event under `:_event` in the data-model snapshot passed
to `script :expr`. `(get-in data [:_event :data ...])` is the standard payload read.

## Authoring keys are FLAT (no :params escape hatch)

```
λ authoring_keys. each value ≡ literal OR (fn [env data]) resolved at invoke time
  | fn? ≡ the "compute me" signal | wrap ONLY data-dependent slots (:message :system)
  | leave static config as literals
  | to compute several keys from data → write one lambda per key
  | RAW pass-through (NOT resolved as fn): :budget-extender (llm-conversation), :render (human-input)
  | control keys dropped from params: :id, :autoforward?
  | alias resolution: if both alias + canonical present → canonical wins, alias dropped
```

See escapement-llm-conversation for the full key catalog and escapement-tools for the
real-tool registry mechanics.
