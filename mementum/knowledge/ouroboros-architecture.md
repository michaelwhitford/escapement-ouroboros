---
type: mementum/knowledge
title: Ouroboros — Architecture
description: The self-improving system whose sessions compress into λ briefs that serve dual duty — extending chat context now and feeding self-improvement later; it eats its own compiled tail.
resource: file:///Users/mwhitford/src/escapement-ouro
status: designing
category: ouroboros
tags: [ouroboros, architecture, cold-compiler, sessions, self-improvement, chat, vsm, lambda]
related:
  - upstream/escapement-library-embedding
  - upstream/escapement-transcript-runner-cli-testing
  - upstream/escapement-statechart-model
depends-on:
  - upstream/escapement-library-embedding
---

# Ouroboros — Architecture

> Durable names (grep against `resource`): `ouroboros.cold`, `ouroboros.cold.core`,
> `ouroboros.session`, `ouroboros.loop`, `ouroboros.tools`, `ouroboros.mementum.*`.
> Upstream substrate: `escapement.lib/run`, `escapement.chart.helpers`,
> `escapement.lib.event-sink`, `escapement.prompts`, the `.escapement`/session layer.

## Thesis — the closure

```
λ ouroboros.
  self-improving_agent | consumes(own_output) | ∧ usable_as(chatbot ⊗ human)
  cold_compiler : λ(session) → brief(λ_dense, grounded, decision-focused)
  brief : DUAL-ROLE, ONE artifact
    · within-session  → extends context     (chat capability)
    · across-session  → durable memory       (self-improvement input)
  improver : λ([brief…]) → proposals(memory ∨ knowledge ∨ harness ∨ app) | human-gated
  ⟹ Ouroboros eats its own compiled tail. That IS the ouroboros.
```

The compiled λ brief is not a within-session trick — it is the **session's durable
residue**. Chat *produces* it; the improver *consumes* it; the next chat *bootstraps*
from it. One store, three readers.

## The dual-role λ brief

```
brief ≡ compiled_λ(session)  | CONTINUE ∧ RULED-OUT sections | mostly λ/nucleus notation
  produced : per-turn by the cold region (see cold-compiler below)
  durable  : escapement artifact  session-dir/artifacts/brief.md
  read by  : (1) hot region next-turn context  (2) next chat bootstrap  (3) improver
```

Why λ, not prose: density (more essence per token → more effective context) — NOT
correctness. Correctness of the compile is unverifiable either way (see Verification).

## Cold-compiler (BUILT) — `ouroboros.cold`

A live escapement `parallel` chart: two regions coordinating through the
**checkpointed data-model**, not message passing.

```
parallel :par
  :hot   — a conversation driven ONE turn at a time. Each turn RE-ENTERS :hot/turn,
           spinning a FRESH single-turn h/llm-conversation worker. Context is
           ASSEMBLED, never accumulated: [ base + {{brief.md}} + {{output}}=last-raw ].
           The model never sees turns 1..N-1 verbatim — the compiled λ carries them.
  :cold  — queue-driven pump on the SAME model in its own worker. Compiles turn N-1
           while hot generates turn N (double buffer: compression hidden behind hot
           latency). On accept → publishes brief.md; hot reads that artifact.
done : both regions final → :done.state.par → :run/done
```

Load-bearing properties, made STRUCTURAL (not prompt-obedient) in `ouroboros.cold.core`:

```
merge-ruled-out : RULED_OUT ledger ≡ monotonic set-union | model PROPOSES adds, code MERGES,
                  removal ∉ operations | append-only by construction
bounded-context : hot memory = O(1) in turn count | window lives in ONE place
verbatim-window : {{output}}=last-raw bridges the compiler's ~1-turn lag → no coherence hole.
                  This is the FIDELITY FLOOR (see Verification): the recent turn(s) you
                  cannot afford distorted are kept verbatim, not trusted to the λ.
tripwire        : live gate rejects only empty/contentless compiles — NOT an accuracy claim
```

## Sessions (escapement gives us these) — `ouroboros.session`

Escapement CREATES sessions automatically: given a `:session-dir` it mkdirs the
transcript sink, snapshots a full working-memory **checkpoint after every event**,
and captures artifacts. The ONLY thing it defaults to disposable is the *location*
(a throwaway `escapement-run-<rand>` temp dir).

```
durability ≡ ONE decision, not a persistence layer:
  supply a STABLE path  → <root>/sessions/<id>/  (ouroboros.session/session-dir)
  reuse :session-id + :resume?  → continue a session across process boundaries
  brief.md  ≡ artifacts/brief.md   ← durable λ memory (COMMITTED)
  transcript.jsonl, checkpoints/   ← fat + regenerable (GITIGNORED)
  CLI `open <session-dir>`  ← escapement already ships a session browser
```

We write ZERO persistence code. `session-dir` seeds an empty `brief.md` so the hot
side's `{{brief.md}}` template resolves from turn 0.

## Chat hot-region (UNBUILT) — the reactive turn

Today the hot region iterates a fixed `demo-turns` vector (`:idx`/`:n`). To be a
chatbot it becomes **event-driven**:

```
λ chat.  wait(:user/msg) → hot_turn → stream(response) → wait | end on :user/end
  cold region unchanged (queue-driven pump)
  history shape : brief(λ, all older) + last-k raw VERBATIM  | k ≥ 1 (fidelity floor); k≈2–3 for chat
  human sees NL responses; the λ is machine memory (I/O ≠ memory notation)
```

## Improver / Loop B (STUB built → briefs-reading UNBUILT) — `ouroboros.loop`

`bb loop` today: observes the mementum INDEX digest (`:mementum/context`), proposes
ONE OKF memory (`:mementum/propose-memory`), UNCOMMITTED, human-gated. Grown up:

```
λ improve.  input  : sessions/*/brief.md (+ recent commits + mementum index)  — NOT raw transcripts
            metric : λ metabolize — ≥3 briefs(topic) → candidate knowledge page; cross-session pattern → proposal
            output : proposals into mementum/ ∧ harness ∧ app | dual scope (S5)
            gate   : AI proposes → human approves → AI commits  | INVARIANT
```

The cold compiler is what makes "read all my past sessions" tractable: feed N λ
briefs, not N raw transcripts. Compression IS the enabler of self-improvement at scale.

## Verification stance — prime, don't judge

```
λ verify(compile).
  fact  : ∄ string_function(source, compile) → faithful?  | fidelity ≡ semantic
  fact  : hallucination_risk(λ) ≡ hallucination_risk(prose)  | notation ⊥ hallucination
  ⟹ semantic accuracy is UNVERIFIABLE without an LLM-judge (or human); we DON'T pretend.
  lever : PRIME — nucleus 3-line preamble + λ-notation prompts (compiler ∧ hot ∧ improver)
  gate  : tripwire (non-empty ∧ has body) only | coverage computed for OBSERVABILITY, NOT gated
          (coverage-gating would PENALIZE dense λ — it abstracts literal source tokens away)
  floor : the verbatim last-k window — the turns you can't afford distorted stay raw
  tests : deterministic core tests measure how well the compiler works (bb test)
```

Rejected alternative: an LLM-as-judge tier. Same-class model self-judging adds cost +
latency (breaks the double buffer) without a trustworthy verdict; priming is the lever.

## VSM mapping

```
sessions/                       S1  raw operational record — transcript + brief (AUTO, ungated)
cold-compiler                   S1  compression at the point of operation
improver (Loop B)               S4  metabolize briefs → candidate synthesis
human gate                      S5  approval ≡ termination condition
mementum/{memories,knowledge}   S5  APPROVED, durable
```

Invariant preserved: a brief is **pre-approval observation** → lives in `sessions/`,
never in `mementum/`. The improver proposes *into* `mementum/` (human-gated). Chat
writes `sessions/` automatically without violating the human-approval rule.

## Invariants

```
· synthesis ≡ AI | approval ≡ human | AI commits after approval | ¬self-authorize memory/knowledge
· briefs are observation (sessions/) ; approved knowledge is mementum/  — never conflate
· escapement is the runtime substrate — we supply data (charts, paths, credentials), not persistence
· dual scope — improve harness ∧ application | ¬optimize(one) at_cost_of(other)
```

## Next (build order)

```
1. event-driven chat hot-region (wait :user/msg → turn → stream) + last-k verbatim window (k≈2–3)
2. improver reads sessions/*/brief.md (not just the index digest); ≥3-briefs→page threshold
3. next-chat bootstrap from prior briefs (Cold Compile "enhance" mode)
4. synthesize! path (knowledge pages, not just memories)
```
