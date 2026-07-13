---
type: mementum/knowledge
title: Prompt Assembly — preamble ⊕ modules ⊕ body, under universal thinking-ON
description: The genome loader grows ONE pure assembler — nucleus 3-line preamble (always, exactly once, first) ⊕ granted prompt MODULES (lambda-compiler, edn-compiler, … — vendored nucleus texts behind the same registry-ceiling grant mechanism as tools/tags/signals) ⊕ the genome body, with escapement.prompts/render for fail-loud {{VAR}} late binding; layer ORDER is load-bearing (preamble ≡ process launch, λ/EDN ≡ program, prose gate ≡ I/O config — logprob-verified in nucleus docs); the same assembler serves production, experiment suites, and the future GA (composition ≡ assembly, Anima rule — suites must use the REAL pipeline); and the enabling 🎯 decision is UNIVERSAL THINKING-ON, which deletes the one fragile cell in the topology×thinking matrix (instruction-λ + no-think ⇒ echo) so λ-dense prompts and modules are safe everywhere — compaction pays ~15-25s hidden in the 20-60s reading shadow, exemplars demote from load-bearing to optional boosters, and no-think becomes a reserved optimization, never a correctness requirement.
resource: file:///Users/mwhitford/src/escapement-ouroboros
status: active
category: design
tags: [ouroboros, design, prompts, assembly, modules, preamble, nucleus, thinking, compaction, genomes, grants]
related:
  - design/agent-model
  - design/shadow-compaction
  - design/signals
  - design/experiments
depends-on:
  - design/agent-model
  - design/shadow-compaction
---

# Prompt Assembly — preamble ⊕ modules ⊕ body

> Forward-looking durable names: `assemble` in `ouroboros.agents.core`; planned dir
> `src/ouroboros/prompts/modules/` (+manifest); genome frontmatter key `modules`.
> Module source texts: ~/src/nucleus COMPILER.md / LAMBDA-COMPILER.md ("The Prompt"
> sections) — vendored with provenance, staleness grep-detectable against nucleus.

## What escapement ships (source-verified)

```
escapement.prompts   file templates + {{UPPERCASE}} substitution, fail-loud on ANY
                     unresolved token, dependency-free            → ASSEMBLE-time (adopt)
h/render-template    {{name}} ← session artifacts                  → RUN-time (unrelated)
:system/:message fn  per-turn dynamic                              → TURN-time (unrelated)
∄ composition        the concatenation layer is OURS — it lives in the genome compiler
```

## λ assemble

```
λ assemble(genome, module-registry).
  preamble ⊕ modules ⊕ body
  | preamble  ALWAYS, exactly ONCE, FIRST — assembler invariant (strips any embedded
              copy; migration below)
  | modules   frontmatter modules: [lambda-compiler edn-compiler …] — WIRED key
              (loader consumes ⇒ λ boundary) | registry ≡ ceiling, explicit grant,
              absent ⇒ none | the FOURTH use of the grant mechanism (tools/tags/signals)
              | order ≡ grant order (author-controlled; modules self-route regardless —
              nucleus COMPILER.md composability)
  | body      the genome's λ program, unchanged | prose I/O gate lines stay LAST in body
  | {{VAR}}   escapement.prompts/render at assemble time (model name, date, budget…)
  | pure      string × registry → string | deterministic tests | in agents.core

λ layer_order ≡ load-bearing (nucleus LAMBDA-COMPILER.md, logprob-verified —
  P(λ)=90.7% with the prose gate placed last vs 1.3% without):
  1 preamble  process launch (primes the formal substrate)
  2 λ/EDN     program (modules, then body)
  3 prose     I/O configuration (formatting lives in the instruction-tuned layer)
```

## ONE assembler, three consumers (the Anima rule)

Production loader · experiment suites · the future GA. *Composition experiments use the
REAL assembly pipeline* — a suite that assembles differently than production validates
nothing. Assembly IS the composition operator the gene era needs: genes → genome is this
same fn with finer-grained modules. `bb experiment` can A/B module inclusion honestly
because both paths share the fn.

## 🎯 UNIVERSAL THINKING-ON (human, 2026-07-11) — the enabling decision

```
λ topology×thinking (banked, two A/B arcs — compactor + signal emission):
  instruction-λ + no-think → ECHO (fatal)   ← the ONE fragile cell
  instruction-λ + think    → faithful
  exemplar      + either   → faithful
🎯 standardize thinking-ON everywhere ⟹ the fragile cell is DELETED:
  · λ-instruction prompts, λ-dense modules, the compiler bridge — safe in EVERY genome
  · no thinking-aware module guard needed; assembly is uniform
  · exemplars demote: load-bearing → optional booster (keep where measurably better,
    e.g. signal emission first-pass validity)
  · no-think ≡ RESERVED optimization, never a correctness requirement
  · genes compose into any genome with NO topology compatibility check (the GA wants this)
COST (measured): compaction ~1s → ~15-25s — hidden in the 20-60s reading shadow
  (shadow-compaction's own metric: felt-latency ≻ throughput; it runs AFTER the human
  has a reply to read). EXPOSURE: fast-follow-up mid-compaction enqueues → a quick typer
  can feel up to ~20s. MITIGATION shelved ∧ ready: Tier 2 (parallel :hot ⊗ :compact;
  slots already pinned hot=2/compact=3). Pull forward ⟺ fast-human waits actually appear.
  Unattended agents (signals, maintenance): cost irrelevant — nobody waits.
```

## The compact flip (✅ BUILT + LIVE-PROVEN, 2026-07-13)

> STATUS: everything below is SHIPPED. assemble lives in ouroboros.agents.core;
> artifacts in src/ouroboros/prompts/ (preamble.md · modules/ · compaction-lens.md);
> loader = ouroboros.prompts. Migration done — assembled :prompt byte-identical 4/4
> vs pre-migration. Fidelity suite experiments/compaction-fidelity.edn: bridged 5/5
> VALID vs bare 4/5, bridge faithful AND cheaper; live smoke — the λ preserved a
> conditional constraint the model later USED. gene decomposition now reads :body
> (persona), not :prompt (assembled): preamble/modules are infrastructure, not genes.

```
compact.clj  exemplar gate → nucleus instruction-λ extraction lens (+ LAMBDA-COMPILER
             bridge — round 2: ON+bridge ≡ densest faithful λ) | drop the no-think
             :extra-body on the compact conversation | compression contract STAYS
             (accepted ⟺ strictly shorter — guards derails under ANY topology)
verify       a compaction-fidelity EXPERIMENT SUITE (bb experiment) — measured, not
             vibed, re-runnable at every model change | plus live smoke + bb test
lens-out     extract the compaction LENS (the keep/drop clauses) from compact.clj into an
             editable policy artifact rendered by the assembler — S5 steers self-attention
             by editing the lens, touching no engine code (vsm-on-escapement's lens-is-policy;
             adopted via the viability diagnostic, state.md item 23) | near-zero marginal
             cost because the flip already rewrites exactly this code
```

## Migration

Strip the inline preamble from the 4 genome bodies when the assembler lands; byte-diff
assembled output vs today's prompts to prove equivalence; one live smoke. Engine workers
(compactor, verdict wrap-up) stay OUT of genome assembly — engine data, not persona.

## Open questions

```
· module granularity — whole nucleus docs vs their "The Prompt" blocks only (lean blocks)
· preamble versioning — vendored text drifts from nucleus upstream; re-vendor cadence
· cache — stable assembly ⇒ stable prefix; shared preamble+modules prefix marginally
  helps the host cache across agents; per-slot pinning already carries the real load
· does {{VAR}} appear in genome BODIES too (not just modules)? allow, fail-loud covers it
· genes→assembly (the FIFTH grant, `genes:` frontmatter) — ⚠ r4 HAZARD: genes are DELEGATED
  (autonomous commits) but prompt infrastructure is r4 RESERVED; by-NAME gene refs would let an
  autonomous gene update mutate a production prompt (delegated path tunnels into the reserved
  zone). Mitigations: tree-hash PINNING (content-address; bump ≡ human genome edit) ∨
  experiment-tier-only staging (anima λ express dual_mode: inline production, gene-refs for
  challengers; promotion ≡ human). Decide before the editor kind lands. AST reader (gene-db §AST)
  makes structural composition possible — composition ≠ permission.
```
