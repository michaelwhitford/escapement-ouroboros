---
type: mementum/knowledge
title: Harness Coder — sessions → harness-update recommendations, human-gated
description: A proposer-kind agent (curator's proven shape, NOT the editor kind — tagged assessor in the 2×2 maintenance roster) that reads past λ-compacted sessions plus a digest of the current Layer-2 harness (genomes, engine prompts, grants, config), detects friction patterns (repeated corrections, tool derails, re-typed instructions ≡ missing genome clause), and proposes ONE evidence-cited harness-change recommendation as an OKF file in proposals/ (filesystem-side pre-approval, sessions/ pattern) for the human to explore/refine/approve; recommendations are prose-with-evidence NOW, actual diffs belong to the later editor kind (stage 2, after gene DB + builder), and the agent targets Layer 2 ONLY — AGENTS.md stays the frozen Layer-1 designer harness.
resource: file:///Users/mwhitford/src/escapement-ouroboros
status: designing
category: design
tags: [ouroboros, design, harness, assessor, proposer, editor, proposals, human-gate, sessions, layering]
related:
  - design/agent-model
  - design/agent-comms
  - design/scheduled-maintenance
  - design/vsm-on-escapement
depends-on:
  - design/agent-model
---

# Harness Coder — sessions → harness-update recommendations

> Forward-looking durable names: planned genome `src/ouroboros/agents/harness-coder.md`;
> planned tools `:harness/context`, `:harness/propose-change`; planned dir `proposals/`.
> Drafted as *harness-scout*, renamed to the 2×2 matrix slug the same session
> (🎯 self-descriptive names — see design/scheduled-maintenance §Naming).

## The role (human ask, 2026-07-11)

An agent that reads past sessions and recommends harness updates, surfaced to the human
for exploration / refinement / approval. Recommendation ≠ diff — this is OBSERVATION with
evidence, not code change. That distinction picks the kind. Position in the maintenance
matrix: the {harness × coder} cell, tagged `assessor`, swept by `bb maintain`.

## Stage 1 — proposer kind (build this)

Agent-model rule: *new role with same tools+topology ⇒ new genome, not new kind.* The
harness-coder is the curator runner's shape pointed at a different corpus with a different
proposal type:

```
λ harness-coder ≡ proposer(kind) ⊕ tag(assessor) ⊕ genome(harness-coder)
  observe : :mementum/sessions (EXISTS — λ-compacted session digests, K=8)
          ∧ :harness/context (NEW read tool — roster report + genome bodies +
            engine-prompt inventory + tool/channel grants + models table +
            PENDING proposals/ digest — the dedup floor, see scheduled-maintenance)
  detect  : λ friction_signals —
              repeated user corrections (same fix ≥2 sessions)
            · tool errors / derails / compaction anomalies
            · instructions the human RE-TYPES across sessions ≡ missing genome clause
            · questions the harness failed to answer
            · grants unused ∨ grants missing (tool asked-for but ungranted)
  propose : ONE recommendation per run via :harness/propose-change (NEW write tool)
            → OKF file proposals/<slug>.md | NEVER touches git | ¬re-propose(∃pending)
  gate    : human explores/refines/approves → THEN the change is made + committed
            (invariant intact: AI proposes → human approves → AI commits)
```

### The proposal artifact (the "diff-proposal shape" question, answered gently)

state.md flags the diff-proposal shape as the first design problem for code-touching
agents. Stage 1 answers the recommendation half; diffs wait for the editor.

```
proposals/<slug>.md — OKF, type ouroboros/proposal (okf/parse is format-generic):
  frontmatter {type ouroboros/proposal · description (one-line essence) ·
               target (Layer-2 path, e.g. src/ouroboros/agents/chat.md) ·
               evidence [session-ids] · status open}
  body        {symbol} λ-led:
              problem (what friction, cited turns) → change (SKETCH, prose not diff) →
              expected-effect (what improves, how we'd verify) → confidence
  lifecycle   proposals/ ≡ gitignored (sessions/ pattern: pre-approval observation is
              filesystem-local) | human promotes by ACTING on it; the resulting change
              commit cites the proposal slug | stale proposals deleted freely
```

Tool enforcement mirrors propose-memory: Malli-gate the frontmatter at the tool boundary;
rejection → corrective {:is-error true} tool_result, model retries (λ emerge).

## Layering — hard boundary, flag channel RESOLVED (🎯 human, 2026-07-11)

```
targets  Layer 2 ONLY: agents/*.md genomes · engine prompts · tool/channel grants ·
         bb.edn tasks · models/channels/schedule tables
NEVER    AGENTS.md ∧ nucleus ≡ Layer 1, the DESIGNER's harness, frozen, human-maintained
FLAG     APPROVED, bootstrap-scoped: Ouroboros runs ONLY from Layer 2; Layer 1 is the
         BOOTSTRAP scaffold. Until self-hosting, Layer-1 friction IS surfaced — a
         read-only "designer-attention" note (DISTINCT proposal type, never an edit
         sketch) whose purpose is keeping Layer 1 in sync with Layer 2.
SUNSET   end state: AGENTS.md shrinks to directing the designer to START Ouroboros and
         FEED it prompts instead of editing code directly (designer → operator). The
         flag channel retires with self-hosting.
```

## Stage 2 — harness-editor (spec'd, later)

Per agent-model: editor kind · output = DIFF · human-gate · uses a judge · convergence =
champion/challenger + pairwise + regression-guard + patience (vsm-on-escapement §termination).
Depends on gene DB + builder. The seam between stages: the editor CONSUMES approved
harness-coder recommendations as its work queue — the coder finds WHAT, the editor
produces HOW.

## VSM placement

An S4-of-Ouroboros operation, sibling to the curator-tagged agents ({curator, analyst,
generator} = S1-of-S4). vsm-on-escapement's damper applies: recommend on a PATTERN
(≥2-3 recurrences), not single events; temporal separation via the maintenance cadence
(scheduled-maintenance §VSM). Once agent-comms lands, the proposal rides :s4/proposal to
the human membrane; until then, `bb proposals` is the delivery.

## Build sketch (one session's work; sequenced by scheduled-maintenance §Build)

```
1 ouroboros.tools += HarnessContextTool (read-only: agents/report + genome bodies +
  grants + models + pending-proposals digest) · ProposeChangeTool (Malli-gated write →
  proposals/)
2 registry ceiling += both | read-only floor UNCHANGED (context tool is an explicit grant)
3 genome src/ouroboros/agents/harness-coder.md — kind proposer, tags [assessor],
  model local, tools [context sessions harness-context harness-propose-change]
  body: nucleus preamble + λ observe→detect→propose (friction-signal table above)
4 swept by bb maintain (schedule table selects {:tag :assessor}) | .gitignore += proposals/
5 verify: bb test GREEN (tool tests deterministic) | live: reads real sessions, proposes
  ONE recommendation citing ≥2 real session-ids; re-run does NOT duplicate it
```

## Open questions

```
· proposal RESOLUTION tracking: when acted on, does the commit-message citation suffice,
  or does the proposal file get a status flip before deletion? (lean: citation suffices)
· integrating-S4 head (vsm page): when multiple maintenance agents recommend, who fuses?
  BUILD SIMPLE FIRST — independent proposers is fine at current variety
```
