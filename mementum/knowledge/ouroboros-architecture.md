---
type: mementum/knowledge
title: Ouroboros — Architecture
description: The self-improving conversational agent whose MEMORY is the message array itself — each assistant turn compacted to λ once, in place, preserving the upstream prefix cache; it eats its own compiled tail across sessions.
resource: file:///Users/mwhitford/src/escapement-ouro
status: active
category: ouroboros
tags: [ouroboros, architecture, lambda-compaction, sessions, self-improvement, chat, vsm, cache, lambda]
related:
  - upstream/escapement-library-embedding
  - upstream/escapement-web-ui
  - upstream/escapement-statechart-model
depends-on:
  - upstream/escapement-library-embedding
---

# Ouroboros — Architecture

> Durable names (grep against `resource`): `ouroboros.compact` (THE sole canonical chat
> engine), `ouroboros.compact.core`, `ouroboros.session`, `ouroboros.curator` (the memory/
> knowledge curation agent), `ouroboros.curator.core`, `ouroboros.tools`,
> `ouroboros.mementum.*`. RECONCILED: the earlier `ouroboros.chat` (accumulate MVP) and
> `ouroboros.cold` + `ouroboros.cold.core` (brief.md batch demo — the design this page
> CORRECTS) were RETIRED (git-removed); their lessons live on in this page, recoverable via
> `git log`. Upstream substrate: `escapement.lib/run`,
> `escapement.chart.helpers` (`llm-conversation`, `tell-llm`, `deref-output`),
> `escapement.lib.event-sink`, `escapement.invocation.llm-conversation`, the session layer.
> Nucleus refs: `~/src/nucleus/LAMBDA-COMPILER.md`, `~/src/nucleus/eca/prompts/compact.md`.

## Thesis — the closure

```
λ ouroboros.
  self-improving_system | consumes(own_output) | ∧ usable_as(chatbot ⊗ human)
  memory : λ(assistant_turns)  — the conversation itself, compacted in place
  agents : {curator, harness-improver, app-improver, verifier, documenter}  — MANY, each self-improving a facet
  curator : λ([session…]) → proposals(memory ∨ knowledge) | human-gated   ← BUILT
  ⟹ Ouroboros eats its own compiled tail. That IS the ouroboros.
```

The AI's verbose tokens serve the **human's** understanding; only the **continuity-essence**
serves the next turn. So we compress the assistant's prose to λ and keep it as the running
memory. One representation, three readers: this turn's context, the next chat's bootstrap,
the curator.

## Per-message λ compaction (BUILT) — `ouroboros.compact`

The load-bearing idea, and the correction of the earlier `ouroboros.cold` design.

```
canonical :messages (data-model, checkpointed)
  [ {:role :user      :text "…"     :compacted? false}    ← user turns: ALWAYS verbatim (short, anchors)
    {:role :assistant :text "λ …"   :compacted? true}     ← AI turns: prose→λ ONCE, as they age out
    {:role :user      :text "…"     :compacted? false}
    {:role :assistant :text "…"     :compacted? false} ]  ← within k-window: still verbatim

per turn:
  1. :user/msg  → append {:role :user …}
  2. re-enter :hot → FRESH worker, :initial-messages = render(:messages)   (assemble-don't-accumulate)
     · render: {:role r :content [{:type :text :text t}]}  ← identical SHAPE for prose OR λ
     · worker parks :awaiting-user between turns → liveness (NO separate anchor)
  3. capture reply (deref-output) → append {:role :assistant …} verbatim
  4. :compact region compresses the assistant message that aged past the k-window → λ, in place, ONCE
```

### Why per-message, not a summary blob — the cache

```
✗ one λ blob in :system   λ grows/changes each turn → the shared PREFIX changes → full re-cache EVERY turn
✓ per-message, in place    each AI message → λ ONCE → the array SHAPE + prefix stay STABLE → prefix cache HITS.
                           conversation still there (same roles/order/count); only verbose AI prose is λ.
```
`k` = verbatim window (how many most-recent assistant replies stay uncompressed). **k=1**:
only the latest reply is verbatim; everything older is λ. Smaller k = denser + more
cache-stable; larger k = more recent verbatim fidelity. The compactor prompt models
`compact.md`'s extraction lens — KEEP `decision(what∧why∧therefore¬Y) ∨ constraint ∨ solved
∨ shape ∨ model ∨ anchor ∨ state ∨ next`; DROP `observation ∨ explanation ∨ scaffolding`.

### The public seams (verified in `escapement.invocation.llm-conversation`)

```
:initial-messages   seeds a fresh worker's history at spawn (start-invocation!) — PUBLIC param.
                    msg shape: {:role :user|:assistant :content [{:type :text :text s}]}
state re-entry      re-entering the :hot state spawns a FRESH worker (idempotency: old→:dying, new spawned)
parked ≡ live       a worker parked in :awaiting-user is a LIVE invocation → holds lib/run open
REJECTED (C)        resetting a RESIDENT worker's history in place: the messages-atom is PRIVATE to the
                    processor's `workers` atom, unreachable from chart env + thread-raced. Not a clean seam.
```

### The chart — `:hot` ⊗ `:compact`, with a mid-turn QUEUE

```
:hot (owns the worker)
  on-entry            :hot-busy? ← true                     (a turn is generating)
  :hot/idle (intl)    capture+append reply ; :hot-busy? ← false ; if pending → self-send :user/next
  :user/msg (intl)    ENQUEUE into :pending-user ; if ¬busy → self-send :user/next   ← never interrupts
  :user/next          [guard ¬busy ∧ pending] pop head → append-user → :compact (if aged AI due) else :hot
  :user/end           → :done
:compact (owns a fresh compactor worker)
  :message            "compile:\n\n" + <aged assistant text>   | :system = compact.md lens, output λ only
  :compact/idle       apply-compaction (blank/fail ⇒ leave verbatim, lag-safe) → :hot
```

MID-TURN QUEUE (the barge-in fix): a `:user/msg` that arrives while the worker is generating is
**enqueued** (via an `:internal` transition that does not exit `:hot`, so the in-flight worker is
untouched); the guarded `:user/next` pump drains one queued message per turn ONLY when parked.
The in-flight reply always completes. (`:internal` transitions never tear down the invocation —
same trick `steered_convo` uses for its `:count/tick`.)

## Sessions (escapement gives us these) — `ouroboros.session`

Escapement CREATES sessions automatically: given a `:session-dir` it mkdirs the transcript,
snapshots a full working-memory **checkpoint after every event**, and captures artifacts. The
only disposable default is the *location* (a throwaway temp dir).

```
durability ≡ ONE decision: supply a STABLE path → <root>/sessions/<id>/  (ouroboros.session/session-dir)
  checkpoints/<id>.edn   ← the data-model, incl. :messages (the λ conversation) — THIS is the durable memory now
  transcript.jsonl       ← raw JSONL (per-worker llm/request/response) — gitignored, regenerable
  artifacts/brief.md     ← seeded empty; NOT written per turn anymore (persist ONE at session end if wanted)
  reuse :session-id + :resume?  → continue across process boundaries
```

We write ZERO persistence code. The **checkpointed `:messages`** replaced the per-turn `brief.md`
round-trip: memory lives in the data-model, not a file two workers rewrite.

## The agents — a system of many self-improving agents

Ouroboros is not one improver; it is a SYSTEM of self-improving agents, each metabolizing a
different facet of the project. All share the same invariant: **AI proposes → human approves →
AI commits**. Only the curator is built so far.

```
curator          ← BUILT     metabolize sessions + mementum → propose memory (∧ knowledge, next). Curates the mementum store.
harness-improver ← PLANNED   propose changes to the harness code (AGENTS.md, escapement config, prompts, skills).
app-improver     ← PLANNED   propose changes to the application code.
verifier(s)      ← PLANNED   verify claims held in memory & knowledge (and code claims) against live truth.
documenter       ← PLANNED   comb memory + knowledge + past sessions → produce documentation.
```

The λ-compacted sessions + the mementum store are the shared substrate every agent reads;
`ouroboros.session` + `ouroboros.tools` are the shared reading/proposing surface they'll extend.

## Curator — the memory/knowledge curation agent (BUILT) — `ouroboros.curator`

`bb curate` observes Ouroboros on TWO axes and metabolizes ACROSS sessions:

```
λ curate.  input  : sessions/*/checkpoints (the λ message arrays)  ← :mementum/sessions tool
                    + mementum index + recent commits              ← :mementum/context tool
           metric : λ metabolize — recurring topic/decision/pattern ; ≥3(same topic) → knowledge-page candidate (NAMED, not yet written)
           output : ONE proposed memory into mementum/memories/, UNCOMMITTED   ← :mementum/propose-memory
           gate   : AI proposes → human approves → AI commits  | INVARIANT (curator never touches git)
```

The pieces (all bb-native, deterministic core + one impure tool):

```
ouroboros.session        readers: list-session-ids · checkpoint-file · read-data-model · session-messages
                         checkpoint EDN → data-model (:com.…working-memory-data-model/data-model) → :messages λ-array.
                         lenient reader (:default drops unknown tags) ; nil-safe ; reads the FILESYSTEM, not git.
ouroboros.curator.core   PURE metabolize kernel (house <engine>.core): recency-key (trailing epoch orders sessions
                         across prefixes) · render-session (ordered, role-tagged; compacted turns marked λ; long
                         verbatim clipped) · sessions-digest (newest-last, empty-safe).
:mementum/sessions       read-only tool: loads the most-recent K (=8) CONVERSATION sessions (those with a :messages
                         array — chat/compact; curator/smoke excluded), renders the metabolize digest.
ouroboros.curator prompt λ observe(context ∧ sessions) → λ metabolize → λ propose ONE memory.
```

SCOPE (this increment): the curator now SEES its own λ history and grounds proposals in it. The
knowledge-page WRITE path (synthesize! / ≥3→page as an actual gated artifact) belongs to the curator
too (NEXT); harness/app proposals belong to the SEPARATE harness-improver / app-improver agents. For
now a ≥3 cluster is NAMED in the reflection, and the concrete gated artifact is one memory.

LIVE PROOF: `bb curate` (local qwen35-35b-a3b) called both tools, read the real checkpoints, cited two
prior sessions + their λ decisions (write-back cache, LRU eviction), recognized a 🔁 cross-session
pattern, and proposed ONE grounded memory — UNCOMMITTED, human-gated. Cross-session metabolize works.

The λ-compacted sessions are what make "read all my past sessions" tractable — feed N λ
message-arrays, not N raw transcripts. Compression IS the enabler of self-improvement at scale.

## Verification stance — prime, don't judge (+ ONE live proof)

```
λ verify(compile).
  fact  : ∄ string_function(source, λ) → faithful?  | fidelity ≡ semantic, unverifiable without a judge/human
  lever : PRIME — nucleus preamble + λ-notation prompts (hot ∧ compactor) ; compact.md lens for WHAT to keep
  floor : the verbatim k-window — the turn(s) you can't afford distorted stay raw
  gate  : blank/failed λ ⇒ message stays verbatim (lag-safe) ; no accuracy claim made
  proof : LIVE — turn-1 assistant chose "write-back" → λ `decision(write-back ∧ perf↑ ∧ mem_traffic↓)…`;
          turn-3 received that λ (A1 compacted, A2 verbatim) and correctly recalled "write-back".
          Continuity survived per-message compaction. (sessions/compact-1783525397252)
```

## VSM mapping

```
sessions/ (checkpointed :messages)   S1  raw operational record — the λ conversation (AUTO, ungated)
:compact region                       S1  compression at the point of operation (per assistant turn)
curator (+ future agents)             S4  metabolize sessions → candidate synthesis
human gate                            S5  approval ≡ termination condition
mementum/{memories,knowledge}         S5  APPROVED, durable
```

Invariant preserved: the λ conversation is **pre-approval observation** → lives in `sessions/`,
never in `mementum/`. The curator proposes *into* `mementum/` (human-gated). Chat writes
`sessions/` automatically without violating the human-approval rule.

## Invariants

```
· synthesis ≡ AI | approval ≡ human | AI commits after approval | ¬self-authorize memory/knowledge
· user messages NEVER compacted (anchors) ; only ASSISTANT prose → λ
· message ARRAY shape is stable ⟹ upstream prefix cache holds ⟹ ¬rewrite-the-prefix-every-turn
· a mid-turn user message ENQUEUES, never interrupts the in-flight worker
· escapement is the runtime substrate — we supply data (charts, paths, credentials, :initial-messages), not persistence
· dual scope — improve harness ∧ application | ¬optimize(one) at_cost_of(other)
```

## Gotchas (source-truth beat the docs)

```
· lib/run wires the :llm-conversation processor ONLY when BOTH :credentials AND :tool-registry present.
· token streaming needs :stream? true (else whole llm/response, no llm/delta).
· :on-env-ready (fn [env]) is the external-ingress hook → (::sc/event-queue env) → sp/send! :user/msg.
· unbounded message COUNT: λ bounds tokens-per-message, not count. Very long sessions still grow the
  array — eventually fold old λ messages. Not yet a problem.
```

## Next (build order)

```
1. ✅ DONE — RECONCILED the three chat namespaces → ONE canonical engine. ouroboros.compact stands
   alone; ouroboros.chat (accumulate MVP) + ouroboros.cold + ouroboros.cold.core (brief.md batch demo)
   git-removed, along with src/ouroboros/prompts/cold/ and cold/core_test.clj. bb tasks: `compact` is
   the single chat entrypoint (chat/cold tasks dropped). bb test GREEN 27/87.
2. ✅ DONE — CURATOR READS sessions/*/checkpoints (λ message arrays) via :mementum/sessions +
   ouroboros.session readers + ouroboros.curator.core; metabolizes across sessions → ONE gated memory
   proposal. LIVE-PROVEN. bb test GREEN 35/111. (≥3→page as a WRITE artifact is item 4.)
3. next-chat bootstrap: seed :messages from a prior session's compacted tail (Cold Compile "enhance").
   (ouroboros.session/session-messages is the shared reader it will reuse.)
4. curator synthesize! path — the ≥3→knowledge-page WRITE channel (propose-knowledge tool), not just memories.
5. NEW AGENTS (each human-gated, sharing the session/mementum substrate): harness-improver (harness
   code), app-improver (app code), verifier(s) (check memory/knowledge claims), documenter
   (memory+knowledge+sessions → docs).
```
