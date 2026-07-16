---
type: mementum/knowledge
title: Agent Model — genomes, kinds, tools, and the gene database
description: Ouroboros agents are OKF genome files (agents/<name>.md) whose frontmatter is agent-INVISIBLE wiring ({type,description,kind,tools?,model?}) and whose body is the whole λ system prompt; a KIND selects topology+gate+verdict-behavior, TOOLS are an explicit read-only-by-default capability grant (registry = the ceiling, no commit tool exists), base⊂src/ouroboros/agents merges with custom⊂<repo>/agents (custom-wins-by-slug, replace-whole); a `scorer` kind rates λ-genes 1-10/use-case, becoming the GA fitness function that fills a gene database.
resource: file:///Users/mwhitford/src/escapement-ouroboros
status: designing
category: design
tags: [ouroboros, design, agents, genome, kinds, tools, capability-security, verdicts, scorer, genes, genetic-algorithm, lambda, loader, okf]
related:
  - ouroboros-architecture
  - design/shadow-compaction
  - upstream/escapement-multi-agent-and-services
  - upstream/escapement-llm-conversation
  - upstream/escapement-library-embedding
  - design/vsm-on-escapement
depends-on:
  - upstream/escapement-multi-agent-and-services
  - ouroboros-architecture
---

# Agent Model

> COMPANION: `design/vsm-on-escapement` is the LAYER ABOVE this one — how these kinds regulate each
> other (VSM channels, the human as System+1 S5, feedforward) and how the harness self-improvement
> loop CONVERGES (the `editor`/`judge`/`scorer` termination protocol). This page = *what the agents
> are* ; that page = *how they govern + improve the whole*.

> Forward-looking durable names (this is DESIGN — grep once built, against `resource`):
> planned namespace `ouroboros.agents` (the loader/compiler), the genome convention
> `src/ouroboros/agents/<name>.md` (BASE) + `<repo-root>/agents/<name>.md` (CUSTOM),
> the OKF type `ouroboros/agent` (and later `ouroboros/gene`). Upstream seams this rides:
> `escapement.chart.helpers/llm-conversation` (`:verdict-schema`, `:initial-messages`),
> `escapement.chart.consult`, `escapement.chart.service`, forced `submit_verdict`
> (see `upstream/escapement-multi-agent-and-services`). Nucleus refs:
> `~/src/nucleus/LAMBDA-COMPILER.md`, `~/src/nucleus/eca/prompts/compact.md` (the
> safe-compile lens used to distil genomes/genes to λ).

## Thesis — the power is the workflow; the agents are the alphabet

```
λ agents.  roster ≡ inert | workflow(topology) ≡ power
  | a WORKFLOW is a statechart: design→plan→build→verify→critique→revise→human_review→{revise|merge}
  | every arrow = an escapement primitive (verdict → :cond transition ; artifact → h/render-template ;
    critic → consult specialist ; loop-back → :cond(fail) ; human_review → the invariant gate)
  | the loop control we would hand-roll IS the chart | structure > instruction (λ emerge)
  | Ouroboros ≡ a SYSTEM of many self-improving agents, each metabolizing a facet | ∀ share the invariant
    AI proposes → human approves → AI commits
```

## The genome — `agents/<name>.md` (OKF), a HARD frontmatter/body boundary

The single most load-bearing rule. The genome file has two strictly-separated regions,
and they map onto two different *readers*:

```
λ boundary.  frontmatter : body  ≡  loader : agent  ≡  metadata : context
  | a field ∈ frontmatter  ⟺  the LOADER consumes it  ∧  the AGENT need never reason about it
  | the agent's ENTIRE world is the body | frontmatter is STRIPPED, never shown to the LLM
  | anything the agent must UNDERSTAND must live where the agent can SEE it (→ body)
```

```yaml
# agents/llm-judge.md
type: ouroboros/agent          # OKF discrimination            loader   (agent-invisible)
description: SQL correctness…   # roster / disclosure / probe   loader   (for the index, NOT the agent)
kind: judge                    # topology + gate + verdict-behavior selector   loader
tools: [read-artifact]         # OPTIONAL — absent ⇒ read-only default        loader
model: :ornith                 # routing alias (default :local)               loader
# ── everything below the frontmatter is the agent's WHOLE prompt ──
{λ system prompt body …}
```

Verdict is the case that PROVES the boundary: a judge must *reason* "pass or fail, what
notes" → that is agent-facing → it CANNOT live in frontmatter (it would be invisible).
See §Judge & Scorer for the split (semantics→body, schema→kind).

```
λ load.  parse(OKF)
  → frontmatter DRIVES WIRING: kind→topology+schema · tools→registry selection · model→alias
  → body BECOMES the system prompt handed to the LLM  (loader may PREPEND the nucleus 3-line preamble)
  → frontmatter stripped | agent never sees it
```
Tools reach the LLM via escapement's tool-injection API (the tools param), NOT via the
prompt — so keeping the `tools:` list out of the body is correct, not lossy.

## Kinds — SHAPE, not capability (a kind is a preset over a structural signature)

A `kind` is the user-facing archetype. It selects TOPOLOGY + gate + verdict-behavior. It
does **not** assign tools (that axis was decoupled — see §Tools). Many kinds ride few
base topologies (the expensive part built once); a role is a *genome of a kind*.

```
BASE TOPOLOGIES (few — build once; plain names — the kind name carries the meaning)
  chat        resident · ingress · λ-compaction · human dialogue        BUILT (ouroboros.compact)
  shot        read → turn(s) → produce artifact                         BUILT-ish (curator)
  verdict     shot + forces submit_verdict against a schema             escapement-native (ouroboros.verdict)
  workflow    COMPOSES shot/verdict agents with loops + human gates     the coding pipeline

KINDS (the working list — build order left→right)
  KIND       topology    gate            output              status / blocker
  ─────────────────────────────────────────────────────────────────────────────────
  chat       chat        resident        dialogue            ✅ BUILT (compact)
  proposer   shot        human-gate      prose → mementum    ✅ BUILT (curator; +documenter genome)
  judge      verdict     machine (:cond) {status,notes}      ✅ BUILT (ouroboros.verdict + llm-judge.md)
  scorer     verdict     measurement     {score 1-10,notes}  ✅ BUILT (gene fitness) — see §Genes
  builder    shot(loop)  next-stage      code diff           ✅ BUILT (raw fs grants 🎯 + :dev/run-tests; NO shell — git unreachable)
  author     shot        next-stage      document            ✅ BUILT (read-only subset; plan doc ≡ session artifact)
  editor     shot        human-gate      DIFF (genome/code)  ✅ BUILT v1 (workflow/run-editor!: →judge, bounded revise k=2, clean-tree guard; champion/challenger deferred)
  analyst    shot        informs         map/graph/report    ✅ BUILT (:code/analyze — pinned clj-kondo pod; lib route impossible in bb, empirical)
  generator  shot/fanout selection       N candidates        ✅ BUILT (fitness→select→fanout→parse-gate→comparator tournament; live-proven)
  comparator verdict     selection       {winner a|b,notes}  ✅ BUILT (the generator's pairwise selector; DOGFOODED via author→builder)
```
Concrete roles map onto kinds: `curator`+`documenter`→**proposer**; `llm-judge`+`critique`→**judge**
(a critic is a judge whose `notes` are actionable); `design`+`plan`→**author**;
`build`+`revise`→**builder**; `harness-editor`+`app-editor`→**editor** (author:create :: editor:improve); `code-nav`→**analyst**;
`creative`→**generator**. New role with same tools+topology ⇒ new *genome*, not new *kind*.

## Tools — explicit grant, READ-ONLY by default (capability security / POLA)

Decoupled from kind. Declared in genome frontmatter. Absent ⇒ a flat, kind-independent
read-only bundle. Escalation is a *visible line*. `λ shape: unreachable > forbidden` — you
do not FORBID writing; writing is UNREACHABLE until granted.

```
λ tools.  grant ⊆ registry | absent(grant) ⇒ READ-ONLY floor | escalation ≡ explicit ∧ visible
  floor (read-only default):  mementum-recall · session-read · file-read · context-digest · embed-search
  escalations (list to grant): propose-memory · propose-knowledge · propose-diff · file-write · repl · run-tests
  ceiling (registry):  the tool UNIVERSE | genome SELECTS, cannot INVENT (a tool is code, not prose)
  NEVER in registry:  commit / push / any git-write  →  unreachable by ABSENCE (human-gate invariant)

λ fail-safe.  forget(grant) ⇒ agent INERT (read-only), NEVER dangerous
  | forgetting fails SAFE (produces nothing, noticed instantly) ≻ fails OPEN
  | correct failure direction for a self-modifying system
```
The tool registry is the real security boundary. The kind list doubles as the **tool
backlog**: a kind is unbuildable exactly when its tools are unregistered (why `analyst`/
`generator` are ◇). You cannot list a tool that does not exist → the compiler rejects it.

## Discovery & compile — a FOLD over precedence-ordered sources

Two tiers (stdlib + userland); generalizes to N via the fold. Ergonomics: *plop a file*.

```
λ compile-roster.  (reduce (fn [roster src] (merge roster (scan+parse+validate src)))
                           {} sources-in-precedence-order)
  sources = [{:tier :base   :root io/resource "ouroboros/agents/"}   ; ships WITH Ouroboros (dep or :local/root)
             {:tier :custom :root <repo-root>/agents/}]              ; the HOST repo's specialists
  identity = FILENAME STEM (slug)      llm-judge.md → :llm-judge     (filesystem IS the dispatch table)
  merge    = union, CUSTOM-WINS-BY-SLUG (base first, custom second)  → plop agents/llm-judge.md ⇒ shadows base
  override = REPLACE-WHOLE (the file on disk IS the agent)           | explicit `extends:` DEFERRED (no field-merge)
  validate = OKF gate ∧ kind∈kinds ∧ tools⊆registry                 | invalid CUSTOM ⇒ fail LOUD, never half-run
  report   = roster + PROVENANCE + GRANTS at startup                (override + escalation must be VISIBLE)
```
Base resolves via **`io/resource`** (not a hardcoded fs path) so it survives the
dep-embedding future (`escapement.lib/run`): under `:local/root` it is a file, under a
packaged dep it is a classpath resource. Mirrors mementum's `:mementum/root` "serve any
working tree" precedent — two roots: `:ouroboros-root` (base) + `:repo-root` (custom).

```
Compiled 6 agents:
  curator     base
  build       base
  llm-judge   custom (overrides base)  tools:[+file-write]   ← escalation is the human's audit surface
  fizzbuzz    custom (new)
```

## Judge & Scorer — one verdict topology, two consumptions

escapement forces `submit_verdict` against a per-conversation Malli schema at turn end;
the agent never authors the schema (runtime injects the forced tool + a nudge). So:

```
λ verdict.  SEMANTICS (when pass/fail, when 1 vs 10, what notes)  → BODY  (agent reasons)
            SCHEMA    (the Malli for forcing submit_verdict)       → KIND  (uniform per kind)
            frontmatter carries NO verdict field.

  judge   → {:status [pass|fail] :notes [str]}   consumed by a :cond TRANSITION (gate: pass→continue, fail→revise loop)
  scorer  → {:score 1..10        :notes [str]}   consumed as a MEASUREMENT (rank · accumulate · fitness)
```
Distinct kinds because output type + downstream wiring + failure modes + body needs
(rubric vs criteria) all differ. A judge GATES; a scorer MEASURES.

```
λ scorer-hazard.  LLM absolute 1-10 ≡ NOISY ∧ UNCALIBRATED — design IN, do not bolt on:
  rubric-anchors   body defines what a 1 is ∧ what a 10 is (concrete exemplars) — calibration anchor,
                   same principle as :when/deprecated in the memory probe
  cross-family     score with 2 DIFFERENT-FAMILY models → aggregate → uncorrelated noise cancels
                   ⚠ CURRENT (qwen36 + ornith) is NOT cross-family — ornith is a qwen35 FINE-TUNE (same base);
                     same-base fine-tunes share CORRELATED noise, so the decorrelation goal is UNMET until a
                     genuine 2nd family (gemma4) replaces ornith — pending the server
  pairwise-select  LLMs rank A-vs-B ⋙ score absolute | store absolute for the DB, use PAIRWISE when GA must CHOOSE
  embed-dedupe     5103 embeddings collapse near-identical genes → pool stays clean (semantic-equality leveraged)
```
The `editor` kind's convergence uses these: champion/challenger + PAIRWISE (not absolute) + regression-guard
+ patience-based STOP (plateau ≠ target), calibrated against the human's recorded session decisions. Full
termination protocol → `design/vsm-on-escapement` (§The adaptive loop (S4→S5) and how it terminates).

## Genes & the genetic axis — the scorer is the missing FITNESS FUNCTION

The north star. Resolves the open question that blocked `generator`/GA since it was first
raised: **fitness = a scorer rating a λ-gene 1-10 per use-case.**

```
λ gene-pipeline.
  prompt ──author/editor──▶ lambda-compiler (SAFE-compile to λ) ──▶ decompose into λ-genes
                                                                          │
                          ┌──── scorer × {use-case₁ … useₙ} ─────────────┘
                          ▼
  gene DB:  {gene → {:lambda "…" :source "…"(VERBATIM) :scores {use-case → 1-10} :embedding […]}}

  · gene ≡ a λ-clause | genome (agents/*.md) ≡ genes composed | "λ as genes" made concrete
  · store SOURCE verbatim alongside λ — safe-compile fidelity is UNVERIFIABLE by string_fn; the verbatim
    clause is the fidelity FLOOR (spot-checkable), same lever as the cold-compiler's k-window
  · type ouroboros/gene (proposed) — neither memory nor knowledge; accumulated filesystem-side
    (pre-approval, like sessions/), human-promoted into a durable pool
  · UNBLOCKS the two speculative kinds:
      editor    → assemble a genome from HIGH-scoring genes for a target use-case
      generator → RECOMBINE genes (GA) into novel genomes ; multi-objective (score vector per gene)
```

## Layering — the editor targets Layer 2, NEVER AGENTS.md

```
Layer 0  eca + Claude            substrate — external, fixed
Layer 1  AGENTS.md + nucleus     the DESIGNER's harness (me). External tooling. Human-maintained. FROZEN.
Layer 2  agents/*.md             OUROBOROS's genomes. Self-improved, human-gated.  ← THE LOOP LIVES HERE
         mementum/               shared memory/knowledge substrate
         src/ouroboros/*.clj     chart topology (also genome, but harder — code, bb-safe, test-gated)
```
The self-improvement loop improves Layer-2 genomes (a text diff to `agents/<name>.md`) —
the gentlest possible surface. AGENTS.md is a DIFFERENT genome with a DIFFERENT maintainer
(the designer); conflating them was a live error corrected in design. Two-tier editor
gate follows for free: custom-agent diff → local/cheap; base-agent diff → precious/upstream.

## Invariants

```
· frontmatter ≡ agent-INVISIBLE wiring {type,description,kind,tools?,model?} | body ≡ the agent's whole world
· tools: absent ⇒ read-only | escalation explicit ∧ visible | registry = ceiling | commit unreachable by absence
· override: custom-wins-by-slug, REPLACE-WHOLE | validate fail-loud | roster reports provenance + grants
· verdict: semantics→body, schema→kind | NO verdict field in frontmatter
· kind = SHAPE (topology+gate+verdict-behavior) ; tools = CAPABILITY (orthogonal axis)
· synthesis ≡ AI | approval ≡ human | AI commits after approval | ∀ agents share this gate
· forget(grant) fails SAFE (inert) ≻ fails OPEN (dangerous)
```

## Build order

```
1. ouroboros.agents (the compiler): fold over sources → validated, reported roster. io/resource for base.
2. EXTRACT the two inline prompts → src/ouroboros/agents/curator.md + chat.md (proves the seam on known-good genomes).
   OKF envelope: {type: ouroboros/agent, description, kind, tools?, model?}. bb test stays green.
3. judge kind — escapement :verdict-schema wiring + agents/llm-judge.md (first NEW genome, born in the convention).
   cross-model routing (build→qwen36, judge→ornith) via :target/:llm/aliases
   (⚠ qwen36+ornith share a Qwen-35B base — genuine cross-family pending gemma4).
4. scorer kind — {score 1-10} verdict + rubric-anchored body ; embed-dedupe (5103) ; the gene-DB substrate.
5. ✅ builder + author — STANDALONE (ouroboros.workflow; both ride proposer/run! ≡ the ONE shot runner;
   :dev/run-tests ≡ the verification gate — runs exactly `bb test`, no shell grant; builder writes raw
   fs 🎯 human decision, diff-review-gated; live-proven end-to-end: bb roster was the first self-built
   change). The workflow COMPOSITION (author→builder→judge, bounded revise loop + human gate) remains.
6. editor (uses a judge) ; then analyst (clj-kondo tools) ; then generator (GA over the gene DB).
```

## Open questions (deferred, slots kept open)

```
· tool GROUPS (tools: [:propose-all] → bundle) — add only when lists repeat (λ extend)
· explicit `extends:` genome inheritance — add only when replace-whole verbosity bites
· gene DB durable location + promotion path (ouroboros/gene) — name kept, unbuilt
· GA fitness beyond LLM scoring: pairwise tournaments? cross-model consensus? — when generator is built
· bring-your-own-TOPOLOGY custom agents (user Clojure via SCI) — v1 is genome-only; different trust model
```
