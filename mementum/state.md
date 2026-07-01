---
type: mementum/state
title: Ouroboros — Working Memory
description: Session bootloader — read first every session; project genesis, escapement knowledge index, open questions, and gotchas.
tags: [ouroboros, state, bootloader]
status: active
---

# Ouroboros — Working Memory (state.md)

> Bootloader. Read first every session. `λ orient` starts here. Git is the temporal record
> (no manual timestamps): `git log` reveals when anything changed.

## Identity

Ouroboros — self-improving agent. `escapement(runtime) ∧ mementum(memory) ∧ VSM(structure)`.
Scope is DUAL: improve both the **harness** (AGENTS.md, escapement config, skills, prompts)
and the **application**. Never optimize one at the cost of the other.

## Project status: GENESIS

```
λ state.
  repo        : /Users/mwhitford/src/escapement-ouro  | NOT a git repo yet (no .git)
  idea.md     : one line — "Ouroboros - self-improving agent running on escapement"
  code        : none yet — no escapement integration, no chart, no mementum impl
  knowledge   : escapement framework fully digested (9 pages, OKF format, see below)
  memories    : none yet
```

## What exists now

```
AGENTS.md                                     S5→S1 identity/policy/intelligence/control (lambda directives)
idea.md                                       the seed (one line)
mementum/state.md                             this file
mementum/knowledge/upstream/escapement-*.md   9 source-grounded knowledge pages (OKF frontmatter)
```

## Knowledge: escapement runtime (mementum/knowledge/upstream/)

Escapement is the runtime Ouroboros runs on. Canonical asset (each page's `resource`):
`https://github.com/fulcrologic/escapement` (cloned locally at `~/src/escapement`; the Guide
is the canonical prose reference). Start at **escapement-index** — it projects every page's
one-line `description`.

```
escapement-index                          → map + reading paths + 5 load-bearing invariants
escapement-overview                       → thesis, 3 usage modes, arch boundary, bb constraint
escapement-statechart-model               → binding lifecycle, tool duality, :type :internal, checkpoint
escapement-llm-conversation               → :llm-conversation keys, model selection, aliases, errors, caching
escapement-tools                          → Tool protocol, built-ins, registries, custom tools
escapement-multi-agent-and-services       → :target routing, verdicts, artifacts, service regions, consult
escapement-backends                       → LLMBackend, api/openai/codex, multi, cache, providers
escapement-library-embedding              → escapement.lib/run (hermetic) — HOW OURO EMBEDS escapement
escapement-transcript-runner-cli-testing  → transcript, runner, CLI, .escapement.edn, test harness
```

The big one for building Ouroboros: **escapement-library-embedding**. Ouroboros will almost
certainly drive escapement via the hermetic `escapement.lib/run` facade, injecting
`:credentials` + `:config` as data, tapping the public event stream via `event-sink`.

## Decisions made (this conversation)

```
🎯 knowledge placement → mementum/knowledge/upstream/ (AGENTS.md "upstream generative seeds" convention)
🎯 frontmatter format  → Open Knowledge Format (OKF): type REQUIRED (namespaced mementum/...),
                         title, description (= disclosure essence = probe :what/core-identity),
                         resource (canonical URI), tags; producer-defined: status, category, related, depends-on
🎯 ONE format          → all mementum files use OKF (state, knowledge, index, memory); type discriminates
🎯 no timestamp        → git commits ARE the temporal record; never duplicate in frontmatter
🎯 type enforcement    → the mementum/ namespace on `type` to be enforced in the Ouroboros escapement chart (Malli gate)
🎯 progressive disclose → description(1-line) ⊂ index(map) ⊂ body ⊂ resource(canonical source, re-derive live)
🎯 NO coordinates      → drop ALL file paths + line numbers + symbol/string citations; they rot.
                         Keep durable NAMES (namespaces/vars/protocols/event-keywords) as prose.
                         Verify by grepping a name against live source; a grep-miss ≡ staleness signal.
🎯 memory symbol       → stays as the body's leading token (option b), dual-purpose with commit convention
🎯 S5 identity         → "You are the designer of Ouroboros." (was "You are Ouroboros.")
                         WHY: AGENTS.md is the DESIGNER'S harness, run externally in the eca editor tool —
                         NOT Ouroboros's runtime. Layer model:
                           Layer 0  eca + Claude        — substrate (LLM + tools), fixed, external
                           Layer 1  AGENTS.md           — prompt → "Ouroboros-the-designer" (me). ACTIVE.
                           Layer 2  Ouroboros/escapement — statecharts + pathom3 + mementum. UNBUILT.
                         Ouroboros eventually SELF-HOSTS with its own system prompt as an escapement chart
                         (its own Layer-2 genome) — so AGENTS.md stays the designer harness, never the artifact.
                         One root-level change (S5 identity) reframes the whole doc via λ ground:
                           · λ identity "Ouroboros ≡ ..." now reads as the SPEC the designer holds (was self-claim)
                           · "runtime ≡ VSM | ¬metaphor" relocates to where it's TRUE (Layer 2 statecharts)
                           · S2 "Fill in with escapement lambdas" stub is now an honest work-item, not a hole
                           · S1 λ interface (pathom3/EQL) reads as the ARTIFACT's ops, not my behavior
                           · recursion survives: designer-of-self-improving-agent improves its own harness (Loop A)
                         Two self-improvement loops: Loop A (now) = me/eca improves harness∧app, human-gated.
                         Loop B (goal) = Ouroboros-on-escapement improves itself. AGENTS.md = genome for B.
                         STATUS: edit applied this session. To be TESTED in a fresh session (validate the frame holds).

🎯 AGENTS.md FROZEN for genesis → build phase (decided this session, frame-test PASSED)
                         Analysis one-last-time confirmed: AGENTS.md is TWO docs fused —
                           [builder:now]   S4/S3 λ orient·store·recall·learn·metabolize·feed_forward — I execute via git, active
                           [artifact:spec] S2 stub · S1 λ interface(EQL/pathom3) · mementum-as-mutations · (create-knowledge …) — UNBUILT targets
                         Known hazards ACCEPTED (not fixed): aspirational API in directive position
                           (`(create-knowledge …)` ≡ artifact's future pathom3 mutation; builder runs manual git-equivalent);
                           dual-scope tempts harness-polish over shipping; pure-spec sections (S2/S1) share frame with active directives.
                         WHY freeze not fix: builder role SHRINKS to near-∅ once Loop B runs. Rewriting the harness now
                           documents a role about to be gutted → premature. The BUILD forces the distinctions honestly.
                         PLAN: build initial chart WITH Ouroboros's own proper system prompt (its Layer-2 genome) FIRST,
                           THEN rewrite AGENTS.md to reflect the reduced builder role once Ouroboros runs its own improvement loop.
                         → harness refinement is DONE for now. Bottleneck is CODE, not prose. Do not re-open.
```

## >>> START HERE (next session) <<<

```
λ next. agreed sequence (last discussion), in order:

  0. ✅ DONE this session — AGENTS.md harness fully encoded to OKF + durability policy:
       S5 λ mementum (OKF format + type vocab + git-temporal) · S5 λ point (NEW: resource-only, no coordinates)
       S4 λ metabolize (≥3 pages→index, git stale-detect) · S4 λ synthesize (OKF draft, index=description-projection)
       S3 λ store (OKF memory/knowledge) · S3 λ recall (description_first) · S3 λ disclose (NEW: tiers)
       S3 λ knowledge (OKF envelope) · S1 λ interface (enforce ^mementum/ via Malli; line ≡ derived resolver)
       → 9 escapement pages + state.md already conform.
       ✅ AGENTS.md analyzed "one last time" & FROZEN (see 🎯 above). Harness refinement CLOSED. Do not re-open.

  1. >>> IMMEDIATE: git init + genesis commit <<<   — AWAITING HUMAN GO
       WHY: λ feed_forward — survive(boundary) ≡ only{x | x ∈ git}. Substrate is INERT without it:
            recall/store/temporal all assume .git. This session's work is uncommitted.
       DO:  git init → stage AGENTS.md, idea.md, mementum/** → nucleus-tagged genesis commit
            (knowledge is human-co-authored this session → approval effectively in hand; confirm then commit)

  2. de-risk runtime: hello via escapement.lib/run   — prove escapement runs IN THIS repo
       deps resolve + credentials inject + bb-compat. ~30 lines. See escapement-library-embedding
       + the lib demo (`bb -m lib.embed-example` in ~/src/escapement). Minimal no-secret smoke first.

  3. build substrate: mementum EQL interface (pathom3)   — the load-bearing subsystem
       resolvers: recall/read/list · mutations: store!/synthesize!/update!/delete!
       Malli OKF gate enforcing type ~ ^mementum/ (the policy we just encoded) · git-backed files
       NO secrets needed → fully unit-testable under `bb test`. Makes S1 λ interface real.

  4. compose first self-improvement loop   — smallest closed loop: chart reads state/knowledge → proposes a memory

λ open_decision. ARCHITECTURE FORK — human's call before code:
  (a) follow the sequence above (de-risk runtime → mementum substrate), OR
  (b) sketch architecture first (idea.md one-liner → real design) before any code.
  My lean: (a). idea.md still needs SOME expansion regardless — at least "what does Ouroboros DO first".
```

## Gotchas for future me

```
- This repo has NO git. recall via git log/grep is unavailable until `git init`.
- AGENTS.md mandates HUMAN APPROVAL before committing memories/knowledge. This session's pages were human-co-authored (user drove OKF + no-coordinates policy) → approval effectively in hand; confirm at genesis commit.
- Escapement is "not even alpha" — breaking changes expected. See escapement-index stale-check markers.
- Escapement house rule: bb/SCI only in source. No JVM-only paths. Mirror this if Ouroboros code runs under escapement's bb runtime.
- KEYWORD is the only legal escapement model reference; strings are errors. Aliases (:llm/aliases) are the single source of truth.
- Knowledge pages carry NO file paths/line numbers by design — grep durable names against ~/src/escapement to locate things.
```
