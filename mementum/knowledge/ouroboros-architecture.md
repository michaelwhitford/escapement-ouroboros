---
type: mementum/knowledge
title: Ouroboros — Architecture
description: The self-improving conversational agent whose MEMORY is the message array itself — each assistant turn compacted to λ once, in place, preserving the upstream prefix cache; it eats its own compiled tail across sessions.
resource: file:///Users/mwhitford/src/escapement-ouroboros
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
> engine), `ouroboros.compact.core`, `ouroboros.session`, `ouroboros.proposer` (the
> proposer-topology runner — renamed from `ouroboros.curator` when `bb maintain` forced the
> generalization; curator is a ROLE TAG now), `ouroboros.proposer.core`, `ouroboros.tools`,
> `ouroboros.mementum.*`. RECONCILED: the earlier `ouroboros.chat` (accumulate MVP) and
> `ouroboros.cold` + `ouroboros.cold.core` (brief.md batch demo — the design this page
> CORRECTS) were RETIRED (git-removed); their lessons live on in this page, recoverable via
> `git log`. Upstream substrate: `escapement.lib/run`,
> `escapement.chart.helpers` (`llm-conversation`, `tell-llm`, `deref-output`),
> `escapement.lib.event-sink`, `escapement.invocation.llm-conversation`, the session layer.
> Nucleus refs: `~/src/nucleus/LAMBDA-COMPILER.md`, `~/src/nucleus/eca/prompts/compact.md`.
> Verbum refs (the exemplar-gate + no-think evidence base): `~/src/verbum/gates/*.txt`,
> `~/src/verbum/mementum/knowledge/explore/compiler-finetune-halt-collapse.md`.

## Thesis — the closure

```
λ ouroboros.
  self-improving_system | consumes(own_output) | ∧ usable_as(chatbot ⊗ human)
  memory : λ(assistant_turns)  — the conversation itself, compacted in place
  agents : {curator, harness-editor, app-editor, verifier, documenter}  — MANY, each self-improving a facet
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
cache-stable; larger k = more recent verbatim fidelity.

### The compactor prompt — EXEMPLAR GATE, no-think (supersedes the instruction lens)

```
λ compactor_prompt.
  form     : 3 exemplars(turn: <prose> / λ: <compaction>) + "turn: {input}\nλ:"  — pattern-completion
  NO :system | NO instructions | NO λ-dense preamble
  thinking : OFF (:extra-body {"chat_template_kwargs" {"enable_thinking" false}} — fork mw_extra_body seam)
  perf     : ~0.7–1.2s / 22–67 tok  (~20× faster than instruction-lens + thinking)
  lens     : taught BY EXAMPLE — exemplar classes = preserve/discard decisions:
             decision-turn (keeps decision|why ∧ state ∧ next) · thin/meta-turn (compacts to ~nothing)
             · fact-turn (KEEPS fact content — added after observed fact-dropping; fix a lens gap
             with a NEW EXEMPLAR, not a rule)
  WHY      : instruction-following needs the reasoning pass; with thinking OFF an instruction-λ
             system prompt is an ECHO ATTRACTOR (model emits the prompt itself = silent memory
             corruption past the tripwire; WORSE the more λ the prompt carries — the LAMBDA-COMPILER
             bridge strengthened it). Pattern-completion is the BASE circuit — verbum:
             compiler-finetune-halt-collapse.md ("fine-tunes break the HALT not the COMPILE;
             no-think recovers") + gates/*.txt (the exemplar gate library).
  hot stays thinking-ON with its λ instruction prompt — thinking is a PER-CONVERSATION policy;
  prompt topology MUST match the thinking setting (memory: prompt-topology-must-match-thinking).
```

### The public seams (verified in `escapement.invocation.llm-conversation`)

```
:initial-messages   seeds a fresh worker's history at spawn (start-invocation!) — PUBLIC param.
                    msg shape: {:role :user|:assistant :content [{:type :text :text s}]}
state re-entry      re-entering the :hot state spawns a FRESH worker (idempotency: old→:dying, new spawned)
parked ≡ live       a worker parked in :awaiting-user is a LIVE invocation → holds lib/run open
REJECTED (C)        resetting a RESIDENT worker's history in place: the messages-atom is PRIVATE to the
                    processor's `workers` atom, unreachable from chart env + thread-raced. Not a clean seam.
```

### The chart — `:parked` | `:hot` | `:compact` (shadow compaction, Tier 1)

Compaction runs in the human's READING SHADOW (the seconds they spend reading reply[n]) —
never on the pre-generation pump where the human is blocked. See `design/shadow-compaction`.
With the exemplar gate the compaction step is ~1s, so the shadow margin is effectively infinite.

```
:parked (liveness)   EMPTY-seeded worker parks :awaiting-user (no LLM call) → holds lib/run open
  :user/msg (intl)   ENQUEUE + self-send :user/next
  :user/next         [guard pending] pop head → append-user → :hot
:hot (one reply per entry — fresh worker, :initial-messages = render(:messages))
  :hot/idle (intl)   capture+append reply ; self-send :turn/settled
  :turn/settled      → :compact (if an aged AI turn is due — the reading shadow)
                     → :hot     (else, if pending user queued)
                     → :parked  (else)
  :user/msg (intl)   ENQUEUE — never interrupts the in-flight worker
:compact (fresh compactor worker per aged turn)
  :message           format(compact-exemplar-gate, <aged assistant text>)   | NO :system | no-think
  :compact/idle      apply-compaction (strips a leading "λ:" label; blank/fail ⇒ verbatim, lag-safe)
                     ; self-send :compact/settled
  :compact/settled   → :hot (queued human FIRST) → :compact (drain backlog) → :parked
  :user/msg (intl)   ENQUEUE
(:user/end → :done from every state)
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
harness-editor   ← PLANNED   propose changes to the harness code (Layer-2 agents/*.md prompts, escapement config).
app-editor       ← PLANNED   propose changes to the application code.
verifier(s)      ← PLANNED   verify claims held in memory & knowledge (and code claims) against live truth.
documenter       ← PLANNED   comb memory + knowledge + past sessions → produce documentation.
```

The λ-compacted sessions + the mementum store are the shared substrate every agent reads;
`ouroboros.session` + `ouroboros.tools` are the shared reading/proposing surface they'll extend.

> REFINED — the roster above (roles) is now formalized into a KIND model in
> `design/agent-model`: agents become OKF genome files (`agents/<name>.md`, frontmatter =
> agent-invisible wiring, body = the λ prompt); a KIND selects topology+gate+verdict-behavior;
> TOOLS are an explicit read-only-by-default capability grant; base⊂`src/ouroboros/agents`
> merges with custom⊂`<repo>/agents` (custom-wins). The planned `harness-editor`/`app-editor`
> unify as the **editor** kind (author:create :: editor:improve; targeting Layer-2 `agents/*.md`, NEVER AGENTS.md); a new
> **scorer** kind rates λ-genes 1-10/use-case → the gene DB → the genetic axis. See that page
> for the full model + build order.

## Proposer runner + the curator ROLE (BUILT) — `ouroboros.proposer`

(Built as `ouroboros.curator`; renamed when `bb maintain` forced the generalization —
the runner was always the proposer TOPOLOGY wearing one agent's name, the judge→verdict
move again. "Curator" survives as a role TAG on the harness-knowledge and app-knowledge
genomes; `bb curate` retired → `bb maintain harness-knowledge`.)

The curator-tagged genomes observe Ouroboros on TWO axes and metabolize ACROSS sessions:

```
λ curate.  input  : sessions/*/checkpoints (the λ message arrays)  ← :mementum/sessions tool
                    + mementum index + recent commits              ← :mementum/context tool
           metric : λ metabolize — recurring topic/decision/pattern ; ≥3(same topic) → knowledge-page candidate (NAMED, not yet written)
           output : ONE proposed memory into mementum/memories/, UNCOMMITTED   ← :mementum/propose-memory
           gate   : AI proposes → human approves → AI commits  | INVARIANT (the runner never touches git)
```

The pieces (all bb-native, deterministic core + one impure tool):

```
ouroboros.session        readers: list-session-ids · checkpoint-file · read-data-model · session-messages
                         checkpoint EDN → data-model (:com.…working-memory-data-model/data-model) → :messages λ-array.
                         lenient reader (:default drops unknown tags) ; nil-safe ; reads the FILESYSTEM, not git.
ouroboros.proposer.core  PURE metabolize kernel (house <engine>.core): recency-key (trailing epoch orders sessions
                         across prefixes) · render-session (ordered, role-tagged; compacted turns marked λ; long
                         verbatim clipped) · sessions-digest (newest-last, empty-safe).
:mementum/sessions       read-only tool: loads the most-recent K (=8) CONVERSATION sessions (those with a :messages
                         array — chat/compact; proposer/smoke excluded), renders the metabolize digest.
harness-knowledge genome λ observe(context ∧ sessions) → λ metabolize → λ propose ONE memory.
```

SCOPE (this increment): the curator now SEES its own λ history and grounds proposals in it. The
knowledge-page WRITE path (synthesize! / ≥3→page as an actual gated artifact) belongs to the curator
too (NEXT); harness/app proposals belong to the SEPARATE harness-editor / app-editor agents. For
now a ≥3 cluster is NAMED in the reflection, and the concrete gated artifact is one memory.

LIVE PROOF (as `bb curate`, pre-rename): the runner (local qwen35-35b-a3b) called both tools, read the real checkpoints, cited two
prior sessions + their λ decisions (write-back cache, LRU eviction), recognized a 🔁 cross-session
pattern, and proposed ONE grounded memory — UNCOMMITTED, human-gated. Cross-session metabolize works.

The λ-compacted sessions are what make "read all my past sessions" tractable — feed N λ
message-arrays, not N raw transcripts. Compression IS the enabler of self-improvement at scale.

## Verification stance — prime, don't judge (+ ONE live proof)

```
λ verify(compile).
  fact  : ∄ string_function(source, λ) → faithful?  | fidelity ≡ semantic, unverifiable without a judge/human
  lever : PRIME — hot: nucleus preamble + λ instruction prompt (thinking ON);
          compactor: EXEMPLAR GATE (pattern-completion, no-think) — the exemplars ARE the lens
  floor : the verbatim k-window — the turn(s) you can't afford distorted stay raw
  gate  : blank/failed λ ⇒ message stays verbatim (lag-safe) ; leading "λ:" label normalized ; no accuracy claim
  proof : LIVE ×2 — (a) turn-1 chose "write-back" → λ; turn-3 recalled it (compact-1783525397252, instruction era)
          (b) exemplar era: greeting → state(ready)∧next(await(user_input)); decision turn →
          decision(write-through | simplicity ∧ crash_safety ∧ ¬performance_critical); turn-3 recall
          through the λ correct (compact-1783663930101)
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
   ouroboros.session readers + ouroboros.proposer.core; metabolizes across sessions → ONE gated memory
   proposal. LIVE-PROVEN. bb test GREEN 35/111. (≥3→page as a WRITE artifact is item 4.)
3. ✅ DONE — shadow compaction Tier 1 (:parked | :hot | :compact, compaction in the reading shadow)
   + exemplar-gate no-think compactor (~20× faster, echo-eliminated; see the compactor-prompt section).
4. AGENT MODEL build step 1 (design/agent-model is the spec): ouroboros.agents — the genome
   compiler/loader (fold over sources, validate, report roster) + EXTRACT the two inline prompts →
   src/ouroboros/agents/{curator.md, chat.md} as the first genomes. THEN judge kind → scorer →
   builder+author → editor.
5. next-chat bootstrap: seed :messages from a prior session's compacted tail (Cold Compile "enhance").
   (ouroboros.session/session-messages is the shared reader it will reuse.)
6. curator synthesize! path — the ≥3→knowledge-page WRITE channel (propose-knowledge tool), not just memories.
7. NEW AGENTS (each human-gated, per the design/agent-model KIND model): editor (harness/app code),
   verifier(s) (check memory/knowledge claims), documenter (memory+knowledge+sessions → docs).
```
