---
type: mementum/knowledge
title: Signals — typed EDN results as the inter-agent DATA plane
description: A signal is a typed, durable EDN FACT an agent emits ({:signal/type kw-from-registry, :signal/data nested-EDN, :signal/lambda optional-λ-string, :signal/source, :signal/at}) — consumers QUERY rather than receive, so signals give cross-process cross-TIME communication with NO residency (the geometry scheduled hermetic agents actually need; push cannot reach a process that does not exist between runs); the store is filesystem EDN under signals/ served by the existing mementum pathom2 veneer ("the parser is the bus" — Anima lineage), the type registry holds ONE contract per type projected THREE ways (filled exemplar primes generation, Malli schema gates the emit boundary, attributes serve EQL), and the emission prompt topology is empirically settled by experiments/edn-signal-emission.edn — nucleus preamble + filled exemplar + EDN-only gate, no-think, 12/12 first-pass valid cross-family vs 9/12 for prose instruction whose failures are structural (JSON drift, dropped braces).
resource: file:///Users/mwhitford/src/escapement-ouroboros
status: active
category: design
tags: [ouroboros, design, signals, comms, edn, data-plane, anima, registry, exemplar, no-think, pathom, eql]
related:
  - design/agent-comms
  - design/scheduled-maintenance
  - design/experiments
  - design/agent-model
depends-on:
  - design/agent-comms
---

# Signals — typed EDN results as the inter-agent DATA plane

> BUILT (2026-07-13). Durable names: `ouroboros.signals.core` (pure: registry ·
> validate · content-hash · signal-id · prompt-projection), `ouroboros.signals`
> (edge: emit! · all-signals/recent/by-type/for-source), tool `:signal/emit`,
> dir `signals/` (gitignored, sessions/ pattern), genome frontmatter key
> `signals:` (the 5th grant surface), EQL `:mementum/signals` + `signal/emit!`.
> Lineage: Anima `resolvers/signals.clj` + `designs/signals.md` (~/src/anima —
> source-verified at design time). Build facts in §Built below.

## What a signal is

```
λ signal ≡ typed durable EDN FACT | ¬message (no addressee, no delivery)
  {:signal/id uuid :signal/type kw(registry) :signal/data <nested EDN map>
   :signal/lambda str? :signal/source <agent/session id> :signal/at ms}
  emit    → :signal/emit tool (Malli-gated, corrective-retry — propose-memory pattern)
  consume → EQL queries: signals-recent · signals-by-type · signals-for-source
  split   : conversation(trace, ephemeral) ∥ signal(product, durable) — Anima's split,
            already true here: turn text serves the HUMAN; the typed result serves the SYSTEM
```

Anima's motivating problem is ours verbatim: agents produce prose, consumers re-parse
meaning, every agent invents its own output format. Signals type the output AT THE SOURCE.
One divergence from Anima: `:signal/data` nests native EDN (Anima string-encoded it —
a datalevin storage constraint we don't have).

## Why signals FIRST (the agent-comms revision)

The committed agent-comms design was push-centric — live events ⇒ residency ⇒ the
multi-model collision as prerequisite. But the first agents that need to communicate are
the MAINTENANCE roster: scheduled, hermetic, one-shot. **Push cannot reach a process that
doesn't exist between runs.** Pull + durable is the only geometry that fits:

```
λ two_planes.
  DATA plane     SIGNALS — durable typed EDN facts | pull | query ≡ subscription   BUILD FIRST
                 cross-process ∧ cross-time | NO residency | NO collision fix | NO liveness
  CONTROL plane  CHANNELS (agent-comms) — live push, scope-as-authority, residency  DEFERRED
                 until interactive multi-agent workflows exist
```

The VSM "bus ≠ channel" discipline survives, relocated: transduction ≡ the typed registry
(a signal type IS an encoder contract) · variety ≡ query-side attenuation (consumers pull
level-appropriate slices; the ≥3 damper is a query threshold) · authority ≡ EMIT grants
per type in genome frontmatter (the channel-grants design transfers verbatim, renamed).

## ONE contract, THREE projections (the load-bearing design)

```
λ signal_type ≡ registry entry {:schema <malli> :exemplar <filled source→signal pair>
                                :doc str :variety kw :reserved? bool} →
  1 PROMPT  the FILLED exemplar, auto-appended to emitting genomes' prompts
            → PRIMES generation (the model produces the shape natively)
  2 GATE    the Malli schema at the :signal/emit boundary
            → VALIDATES (malformed unrepresentable, λ emerge; residual slips → retry)
  3 QUERY   the same attributes as EQL vocabulary
            → SERVES consumers (query shape ≡ signal shape)
  | define once → never drifts | the genome compiler derives projection 1 (kind→verdict-
    schema precedent: a table lookup, per grant)
```

## The emission topology — EMPIRICALLY SETTLED

`experiments/edn-signal-emission.edn` (3-round arc; suite re-runnable via `bb experiment`):

```
WINNER  nucleus preamble + ONE FILLED EXEMPLAR + "Output EDN only…" gate + NO-THINK
        confirmation: 12/12 Malli-valid, both Qwen fine-tunes (same base, ¬true cross-family), ~1.3-1.6s, ~110 tok
LOSERS  prose instruction 9/12 — failures STRUCTURAL: JSON drift ×2 (prose describing
        EDN leaves format ambiguous; an exemplar SHOWING EDN pins it), dropped braces ×1
        bare :_fill template + comments — constraints-in-comments ≈ instructions, weak
        template WITHOUT preamble, no-think — ECHOES unfilled :_fill (preamble load-bearing)
THINKING unnecessary for the exemplar topology ∧ 10-20× slower
        → SECOND confirmation of prompt-topology-must-match-thinking (compactor was first)
POLICY  superseding note (🎯 universal thinking-ON, design/prompt-assembly): Ouroboros
        standardizes thinking-ON — the no-think constraint VANISHES; the exemplar
        projection is RETAINED as a first-pass-validity booster (think-compatible),
        and emitters simply run ON like everything else (unattended — nobody waits)
UNTESTED the self-executing EDN-statechart half of the nucleus intuition (behavioral
        routing via EDN in prompts) — separate experiment when a genome wants an EDN body
```

## Store & veneer

```
store   signals/ — one EDN file per signal (sessions/ pattern: filesystem-side,
        gitignored, pre-approval observation; git stays approved-memory-only)
veneer  the EXISTING mementum pathom2 parser grows signal resolvers + the emit mutation
        — "the pathom parser is the bus" (Anima) | we already own the parser
dedupe  content-hash (Anima tree-hash precedent) at emit; embed-dedupe (5103) later
gene-DB Anima auto-forwarded genome-eligible signals (:signal/lambda) to its gene DB
        with tree-hash dedupe — direct prior art for the queued gene-DB substrate;
        read anima resolvers/genes.clj before that build
```

## Seed type vocabulary (absorbs the agent-comms channel seeds)

```
:s4/proposal        maintenance-roster recommendation → human gate     reserved
:s1/report          operation result (sweep summaries, run outcomes)
:experiment/result  suite run outcome (design/experiments)
:ouro/algedonic     identity-threatening alarm                         reserved, RARE
:human/notice       surface-to-human, non-blocking
```

Grants: genome frontmatter `signals: [...]` (emit grants — mirrors tools/channels;
registry ≡ ceiling; absent ⇒ emit nothing; reserved ⇒ escalation in roster report).

## Built (the design as it landed)

```
λ built.
  ONE write path   emit! (validate → content-hash dedupe → persist) | tool ∧ EQL
                   mutation BOTH route through it (λ converge) — the open question
                   "Tool vs mutation" RESOLVED: both, over one edge fn
  tool seam        SOURCE-VERIFIED: escapement tool dispatch keywordizes TOP-LEVEL
                   arg keys only, NO json-transformer decode before m/validate →
                   nested keyword-keyed EDN cannot ride tool args as a map. The
                   tool therefore takes :data AS AN EDN STRING — which IS the
                   settled emission topology (the model natively writes EDN text
                   when the FILLED exemplar shows it). LIVE-PROVEN end-to-end
                   thinking-ON: grant → assembled projection → tool call →
                   EDN-in-JSON-string parsed clean → Malli-valid persisted fact
                   (the model derived :outcome :fail from prose unprompted); the
                   model touched the tool 3×, dedupe held the store to ONE fact
  grant            `signals:` frontmatter ≡ the 5th grant surface. Non-empty grant
                   AUTO-ADDS :signal/emit to :tools (ONE grant surface — a typeless
                   emit tool is inert; the TYPE grant is the capability). Absent ⇒
                   [] ⇒ emit nothing. Unknown types fail-loud aggregated. Roster
                   report: signals:[…] + RESERVED-SIGNALS:[…] escalation
  projection       loader appends prompt-projection AFTER the body (I/O gate last,
                   nucleus layer order); agent :body stays RAW — gene decomposition
                   reads the persona, never infrastructure
  identity         :signal/id ≡ time-ordered slug keyword :<at>-<ns>-<name>
                   (λ identifier — no uuid; id ≡ filename stem, derived at load,
                   gene precedent). content-hash ≡ sha256(canonical {type data
                   lambda}) — :at/:source EXCLUDED: same fact re-emitted later or
                   by another agent ≡ duplicate (re-proposal damping). Same-ms
                   id collision with DIFFERENT content → :at bumped, never overwrite
  trust boundary   tool `source` is INFRASTRUCTURE-set at registry construction
                   (never model-claimed); default registry construction is
                   inert-safe (no grants ⇒ every emit corrective-rejected). The
                   EQL mutation does NOT enforce grants — the veneer is inside the
                   trust boundary (charts, bb tasks, tests); grants gate the LLM
  chat genome note the ceiling grew but chat did NOT: :signal/emit rides signals:,
                   not the "full hands minus web/search" tools grant — chat has no
                   signal types, so no emit tool (flagged at review, human-visible)
```

## Open questions

```
· proposals/ (harness-coder design) vs :s4/proposal signals — proposal files are
  HUMAN-facing markdown, signals are machine EDN. Options: (a) proposal file stays the
  artifact + an :s4/proposal signal points at it; (b) proposals become signals and
  bb proposals RENDERS them. Lean (a) — don't break the committed harness-coder shape.
· retention/GC — unbounded signal accumulation; fine at current volume (sessions/ has
  the same property); revisit with a fold/archive when query latency notices.
  emit! dedupe is O(n) over all-signals per emit — same revisit trigger.
· Anima field lessons — the human reports signals were NOT battle-tested there (the
  idea came from the nucleus EDN-template behavior, now validated here); no operational
  retention/discipline lessons to import
```
