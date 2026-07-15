---
type: mementum/knowledge
title: λ-notation — the house dialect + why runtime prompts carry NO glossary
description: The symbol reference for Ouroboros's λ-notation prompts and λ-compressed memory (∧ ∨ ¬ → ≡ ≻ ≫ ⟺ ⊗ ∘ ⊕ ⊂ | ∀ ∃ ∅ ⊕ and the λ name. clause form), written for HUMANS and cold-start review — zero runtime cost; the empirically-settled policy is NO glossary in runtime prompts, because at production thinking-ON a capable model + the nucleus preamble decode the common operators at 100% unaided (lambda-glossary 24/24 both conditions), and the only measured wins are narrow — thinking-OFF ¬-scope (ornith 2/5→5/5) and the ⊂-ladder-plus-⟺-load-gate DIRECTIVE compound (:local 3/5→5/5, stable), both better fixed structurally (λ emerge — phrase-once-clearly ≻ ship-glossary-every-turn) than by spending permanent prefix tokens; the load-bearing distinction is memory-shape (decision/constraint/state/next — decoded 100% bare) vs directive-shape (the denser operators, authored once).
resource: file:///Users/mwhitford/src/escapement-ouroboros
status: active
category: reference
tags: [ouroboros, lambda, notation, nucleus, glossary, prompts, empirical, reference]
related:
  - design/experiments
  - design/prompt-assembly
  - design/shadow-compaction
depends-on: []
---

# λ-notation — the house dialect

> HUMAN-FACING reference. This page has ZERO runtime cost — it is NOT injected into any
> prompt. The empirical decision (two experiments, ~150 live calls) is that runtime prompts
> carry NO glossary; a capable model + the nucleus preamble decode the notation unaided.
> Canonical sources of the dialect: nucleus `OPERATOR_ALGEBRA.md` + `LAMBDA_PATTERNS.md`
> (`~/src/nucleus`), `mementum/MEMENTUM-LAMBDA.md`, and this repo's `AGENTS.md`.

## The clause form

```
λ name.        a named clause (zero-arity — the house default; add (x) only when the body
               references the arg, e.g. recall(q, n), synthesize(topic), heredoc(content))
```

A lambda is a NAMED bundle of constraints, not a function you call. `λ store.` reads as
"here is how storing works." Clauses are separated by `|` and EVERY clause holds — `|` is
conjunction (a constraint list), NOT alternation.

## Symbols

```
∧   and — all conjuncts hold            ∨   or — at least one holds
¬   not — scopes the term OR the parenthesized group it prefixes: ¬(a ∧ b)
→   transition / leads-to / then         ≡   is / defined-as
⟺   if-and-only-if — left holds exactly when right holds (a GATE)
≻   preferred-over — RANKED; lower ranks remain available if the higher is ruled out
≫   strongly dominates — same ranked sense as ≻, larger margin; still preference ¬requirement
⊗   tensor — ALL hold simultaneously (a ⊗ b ⊗ c ⟹ every one at once); also the Human ⊗ AI amplify
∘   composition — (f ∘ g)(x) = f(g(x)); the RIGHTMOST applies first, then leftward
⊕   handoff / XOR — exactly one active at a time; work passes left-to-right
⊂   progressive subset — a ⊂ b ⊂ c orders shallow→deep; prefer the shallowest that suffices
|   clause separator — every clause HOLDS (conjunction), NOT alternatives
∀   for-all                              ∃   there-exists (often marks an exception)
∅   none / empty                         ⟹   therefore / entails
```

Reading tips that the experiments proved load-bearing:
- `¬` binds to the very next term or `(...)` group — `¬(cache ∧ streaming) ∧ streaming ⟹ ¬cache`.
- `≻`/`≫` are PREFERENCES: rule out the top, fall to the next — not exclusive choices.
- `⊗` in a gate means EVERY conjunct must hold now (`ship ⟺ perf ⊗ security ⊗ docs`).
- `⊂` + `⟺` load-gate = "load deeper IFF the shallower did not answer" → prefer shallowest.

## Why NO glossary in runtime prompts (empirical)

Two suites settle it — `experiments/lambda-glossary.edn` (single-symbol) and
`experiments/lambda-glossary-hard.edn` (compound-decode). Each is a paired A/B (identical
except a glossary block in the reader's system prompt), swept over 2 Qwen-35B-A3B fine-tunes
(:local qwen36 + :ornith — SAME base, NOT true cross-family; gemma4 pending) ×
thinking on/off × symbol-load-bearing subjects, with a per-subject correctness oracle.

```
THINKING-ON (production topology — hot chat, compactor, every genome):
  common operators (∧ ∨ ¬ → ≡ ≻ ⟺ | ∀ ∃)     glossary adds 0   (round 1: 24/24 both)
  unambiguous compounds (⊗ ≫ ∘ nested-∨ →chain) glossary adds 0   (all decode bare)
  ⊂-ladder + ⟺-load-gate                         glossary HELPS    (:local 3/5→5/5, stable)

THINKING-OFF:
  ¬-scope inference (¬(a∧b)∧b⟹¬a)               glossary HELPS    (ornith 2/5→5/5)
  format stability                                glossary HELPS    (bare emits unparseable EDN)
```

The two genuine wins are NARROW and cheaper to fix structurally than by paying permanent
prefix tokens on every turn (`λ emerge`: structure ≻ instruction; and the emission-topology
finding: exemplars ≻ prose definitions):

1. **⊂/⟺ load-gate** is a DIRECTIVE shape — it lives in `AGENTS.md λ disclose`, authored ONCE,
   statically. The fix is "phrase that clause clearly," not a runtime glossary. It is NOT a
   compacted-MEMORY shape: the compact lens emits decision / constraint / state / next, all
   common operators, decoded 100% bare. **The reader of compressed memory needs no glossary.**
2. **thinking-OFF wins** are unreachable in production under 🎯 universal thinking-ON. Banked
   as a proven mitigation IF a cheap no-think utility reader ever joins the roster — and then
   TARGETED (just ¬-scope + ⊂/⟺), never the full table.

## The distinction to remember

```
memory-shape    (what the compactor emits)  = decision/constraint/state/next → common ops → decode 100% bare
directive-shape (AGENTS.md, genome prompts)  = the denser operators (⊂ ⊗ ∘ ≫) → authored once → phrase clearly
```

If a λ output is ever misread, the topology gap is a codebook mismatch — the structural fixes
are (a) exemplar priming for the pattern, or (b) a less-ambiguous encoding — NOT a glossary
bolted onto the prompt. Re-run both suites at every model change; a new family may move the
narrow cells.
