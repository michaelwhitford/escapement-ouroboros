---
type: mementum/knowledge
title: Escapement — Multi-Agent Patterns & Service Regions
description: :target-routed messages, typed verdicts, file-backed artifacts, and service regions that expose subcharts as synchronous region-tools.
resource: https://github.com/fulcrologic/escapement
tags: [escapement, multi-agent, services, verdicts, artifacts, consult, multiplex]
status: active
category: upstream
related:
  - upstream/escapement-llm-conversation
  - upstream/escapement-statechart-model
  - upstream/escapement-transcript-runner-cli-testing
depends-on:
  - upstream/escapement-llm-conversation
---

# Escapement — Multi-Agent Patterns & Service Regions

> Named units: `escapement.chart.service`, `escapement.chart.consult`,
> `escapement.chart.repl-service`, `escapement.chart.helpers`,
> `escapement.invocation.llm-conversation`. Grep names against live `resource`.

## `:target` routing on `:llm.user-message`

```
λ routing. tell-llm posts :llm.user-message → autoforward delivers to EVERY live invocation
  | filter inside forward-event!:
    target  = (:target ev-data)
    accept? = (or (nil? target) (= (->id-str target) (->id-str invokeid)))
  | no :target → broadcast to all | :target "<invokeid>" → only matching invocation enqueues
  | ->id-str normalizes: keyword→(name kw), else (str x) → :researcher ≡ "researcher"
```

```
λ h/tell-other-llm({:target id-or-fn :expr (fn [env data] text)}).
  → script that runtime-resolves target (if fn) then calls tell-other-llm!
λ h/tell-other-llm!(env, target, text).
  → send! {:event :llm.user-message :data {:text text :target (->id-str target)}}
  | imperative; use inside handle-tool body / script :expr | NEVER expose to LLM
```

## `:on-end-turn-event` payload & typed Verdicts

```
λ on-end-turn payload (base, no verdict).
  {:text "<final text>"        ; ABSENT when ArtifactStore present
   :from "<invokeid>"
   :output-ref <blob-locator>  ; present when ArtifactStore present
   :io/snippet "<≤80-char>"}   ; present when ArtifactStore present
  | ALWAYS read via h/deref-output — never :text directly
  | fires once per logical turn (:end_turn OR a :tool_use turn that batched an event-tool — glm case)
```

### Verdicts (`:verdict-schema`)

```
λ verdict.
  set :verdict-schema <malli> on the conversation →
  at turn end: run-verdict-inference!
    → forces tool-choice {:type :tool :name "submit_verdict"}  (strips all other tools)
    → appends framework nudge user-message (chart never sees)
    → input decoded (json-transformer) then validated vs schema
  happy   → :on-end-turn-event data carries :verdict <validated-map>
  failure → :error.llm.verdict-validation (worker dies); NOT :on-end-turn-event
  INVARIANT: "submit_verdict" is RESERVED — never declare in :allowed-events / :real-tools
           | :verdict-schema nil → free-text idle, no wrap-up
           | region-tool replies mid-turn do NOT trigger wrap-up
```

Chart consumption:
```clojure
(transition {:event :llm.idle
             :cond (fn [_ data] (= :done (get-in data [:_event :data :verdict :status])))} …)
```

## File-backed artifact helpers (cross-phase context sharing)

Root: `<session-dir>/artifacts/<name>`. CLJ/bb only (`java.nio.file`).

```
λ h/deref-output(env, data).
  → read :output-ref via ArtifactStore → :text | fallback inline :text | "" if neither
λ h/capture-llm-output({:as name?}).
  → script: atomic-write (deref-output) to artifacts/<name> | default name = :from invokeid
  → emits :artifact/captured {:name :bytes}
λ h/render-template(template, env, extras?).
  → {{name}} → reads artifacts/<name> | {{output}} → extras :output (in-flight text)
  → THROWS ex-info {:reason :missing-artifact} on any unresolved token (fail-fast)
λ h/forward-llm-output({:to id :template str :as name? :capture? bool}).
  → one step: capture output → render template (with {{output}}) → post targeted :llm.user-message to :to
  | :capture? default true
```

Pattern: state A `(h/capture-llm-output {:as "research.md"})`; state B uses
`(fn [env _] (h/render-template "Prior: {{research}}\n…" env))` as its `:message` lambda.
Artifacts are files; latest write wins; version with filenames for history.

## Service regions — subcharts as tools (THE pattern)

A service region is a chart region that registers a tool; another conversation calls it as
`region__<name>` and gets a synchronous reply. The registry is a chart-scoped atom holding
`{tool-kw {owner-state-id {:owner :description :input-schema}}}`.

```
λ service/register-tool!({:tool kw :description str :input-schema malli}).
  → script for on-entry | reads :owner from the runtime context-element-id (never passed)
  | input-schema MUST be OPEN (no {:closed true}) → else ex-info :closed-region-tool-schema
  | same-owner+same-decl idempotent | same-owner+different-decl → hard error

λ service/unregister-tool!(tool-kw).
  → script for on-exit | removes (tool-kw, current-owner)

λ service/handle(event-kw, handler-fn)  ≡  h/handle-tool.
  → transition element (:internal, no :target) | :cond walks ancestor chain to match owner
  handler-fn(env, request) → reply | nil | (throw)
    request = {:data <user-payload> :reply-id <s> :reply-to <caller-invokeid> :timeout-ms <int>}
    {:result str :is-error bool} → synchronous reply (fires :escapement.tool/reply now)
    nil                          → DEFERRED reply (caller parks until post-reply or timeout)
    throw                        → {:result "handler threw: …" :is-error true}

λ service/post-reply(env, {:reply-id :reply-to :result :is-error?}).
  → send! {:event :escapement.tool/reply …} → delivered to caller's tool-reply-queue
  | late reply (after timeout) → logged :llm/region-tool-late-reply, dropped
```

### Consumer side: `:chart-tools`

```clojure
:chart-tools [{:owner :repl-A}]                          ; → region__repl_eval
:chart-tools [{:owner :repl-A :as :py}                   ; → region__py__repl_eval
              {:owner :repl-B :as :clj}]
```

```
λ chart-tools. palette SNAPSHOTTED at conversation start (late registrations invisible)
  | each tool → region__<encoded> (or region__<as>__<encoded>)
  | framework auto-merges optional :timeout-ms into JSON schema (LLM may override default 30s)
```

### Wire protocol (one call)

```
caller LLM calls region__repl_eval
 → worker posts chart event {:event :repl/eval :data {<input>
     :escapement.tool/{reply-id reply-to owner timeout-ms}}}
 → engine routes :repl/eval to owner region (or active substate)
 → service/handle transition fires (owner ancestor cond passes) → handler returns {:result …}
 → service/post-reply → :escapement.tool/reply
 → forward-event! matches reply-to vs invokeid → offer to tool-reply-queue
 → poll-reply-queue! unblocks → emits tool_result → LLM continues
```

### Substate routing (`:idle`/`:running`/`:busy`)

SCXML transition precedence: a substate's `handle-tool` overrides the parent's — no dispatch
table. **CRITICAL**: transitions between substates of a service region in a `parallel` MUST
be `:type :internal` — an external transition's LCCA is the parallel's parent, which
exits/re-enters ALL sibling regions (including the consumer conversation, restarting it).

### Slow / async work

```clojure
(h/handle-tool :repl/eval
  (fn [env {:keys [reply-id reply-to data]}]
    (future (service/post-reply env {:reply-id reply-id :reply-to reply-to
                                     :result (str (slow-eval (:expr data))) :is-error false}))
    nil))   ; nil = "reply is in the mail"
```

Consumer parks in `poll-reply-queue!` with `deadline = now + timeout-ms`; post-deadline
replies dropped + `:llm/region-tool-late-reply`.

## Worked drop-ins

```
λ repl-service/repl-service-region({:id :project-dir :eval-fn? :status-fn?}).
  → state element for a parallel region | registers :repl/eval (always), :repl/status (if :status-fn)
  | default eval = :repl/eval builtin (SCI in-process, bb-compat)
  | :project-dir → on-entry best-effort nREPL port auto-discovery → stamps :nrepl-port
  | consumer: {:chart-tools [{:owner :repl-mgr}]}

λ consult/declare-consultation({:state-id :tool-name :specialist-invokeid
                                :input-schema :verdict-schema :system …}).
  → a consultation tool whose IMPLEMENTATION is another LLM
  | on-entry register-tool! / on-exit unregister | owns an llm-conversation w/ forced :verdict-schema
  | handle-tool: enqueue {:reply-id :reply-to} on pending atom, tell-other-llm! input to specialist, return nil
  | on :llm.idle from specialist: pop pending → post-reply JSON verdict
  | on :error.llm.* w/ pending: pop → error tool_result so asker doesn't hang
  | EXACTLY one in-flight per consult state; extras FIFO-queued
  | asker LLM sees: JSON in → JSON out, unaware of another LLM / chart / region
```

## Dynamic child sessions: multiplex + `:multi-session?`

```
λ multiplex (com.fulcrologic.statecharts.invocation.multiplex).
  {:id :workers
   mo/child-type   ::sc/chart
   mo/count        (fn [env data] N)              ; dynamic
   mo/child-params (fn [env data idx] {:src worker-chart-id :params {…}})}
  | child reports via (mux/reply env :event {…}) carrying mo/from = child idx
  | library fires :done.invoke.<multiplex-id> when ALL count children reach final
  | child chart MUST be registered: (sp/register-statechart! registry worker-chart-id worker-chart)

λ :multi-session? (var metadata on the chart).
  (def ^{:multi-session? true} agent (chart/statechart {…}))
  | REQUIRED when chart spawns multiplex children
  | runner drain-once! then calls receive-events! WITHOUT :session-id filter,
    routes each event to (:target event) (else parent sid)
  | without it: children wedge, :done.invoke.* never reaches parent
```

## Cross-cutting invariants

| Concern | Rule |
|---|---|
| Parallel + service-region transitions | `:type :internal` REQUIRED |
| Region-tool schema | must be OPEN (no `{:closed true}`) |
| Palette | snapshot at conversation start; late registrations invisible |
| Reading idle text | always `h/deref-output`, never `:text` |
| `submit_verdict` | reserved name; never declare |
| invokeid compare | `->id-str` normalizes kw vs string |
| Late `post-reply` | logged `:llm/region-tool-late-reply`, dropped |
