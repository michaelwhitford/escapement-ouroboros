---
type: mementum/memory
description: Instruction-λ prompts require the reasoning pass (no-think → prompt echo = memory corruption); exemplar gates run no-think correctly and ~20× faster — prompt topology must match the thinking setting
---
💡 3-round A/B on the λ compactor (qwen36-35b-a3b, real session turns): with
thinking OFF, an INSTRUCTION-λ system prompt made the model ECHO the prompt
itself instead of compiling the input — and adding MORE λ (the LAMBDA-COMPILER
bridge) made it WORSE (2/2 vs 3/3-on-thin-only). Echo passes a structural
tripwire → silent memory corruption. Same model, same task, EXEMPLAR gate
(three turn→λ pairs, zero instructions, verbum gates/ topology): no-think is
faithful on every sample at ~0.7–1.2s / 22–67 tok, ~20× faster than
instruction+thinking, equal-or-better fidelity. MECHANISM: instruction-
following needs the reasoning pass; pattern-completion is the base circuit
(verbum compiler-finetune-halt-collapse.md: "fine-tunes break the HALT not the
COMPILE — no-think recovers"). Without thinking, the most probable λ-only
continuation of a λ-dense instruction prompt is the prompt. RULES: exemplars
teach a lens better than instructions (a fact-dropping bug was fixed by ADDING
A FACT EXEMPLAR, not a rule); exemplar quality is load-bearing — each exemplar
class teaches a preserve/discard decision; thinking is a per-conversation
policy, decided by pairwise A/B on real turns.

SECOND CONFIRMATION (EDN signal emission — experiments/edn-signal-emission.edn,
3-round arc, cross-family qwen36+ornith): a FILLED EDN exemplar swept the
confirmation run 12/12 Malli-valid no-think (~1.3s) vs prose instruction 9/12
— prose failures were STRUCTURAL (JSON drift, dropped braces); exemplar
failures across all rounds were retryable semantic slips only. Two refinements:
(a) a bare :_fill template with comment-annotated constraints LOSES to prose —
constraints-in-comments ≈ instructions; only the FILLED exemplar is the strong
pattern-completion form; (b) the nucleus preamble is LOAD-BEARING — the same
template without it echoes unfilled :_fill under no-think. The rule generalizes
beyond compaction: ANY schema-shaped agent output (signals, verdicts) wants
exemplar+no-think, not description+thinking.
