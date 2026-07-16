---
type: mementum/knowledge
title: VSM on Escapement — the cybernetic architecture & its self-improvement loop
description: VSM and statecharts are near-isomorphic (hierarchy⊗concurrency⊗events⊗recursion), so escapement charts are a natural executable form of the Viable System Model; each VSM channel = a statechart event made real by scope-as-authority + transduction + loop/variety-match; the human is System+1's S5 (reserved authority enforced by capability, not rule); the orchestrator is Ouroboros's own S5; S4 intelligence gets ATTENUATED upward signals and the S1→S4 channel is realized as FEEDFORWARD (shadow compaction, pre-computed in the reading shadow) which defangs the S3↔S4 homeostat; and the harness self-improvement loop TERMINATES by plateau-detection (champion/challenger, pairwise, regression-guarded, calibrated against the human's recorded session decisions), not by ever judging a prompt "good enough".
resource: file:///Users/mwhitford/src/escapement-ouroboros
status: designing
category: design
tags: [ouroboros, design, vsm, cybernetics, statecharts, escapement, channels, feedforward, homeostat, recursion, self-improvement, termination, harness, editor, judge, scorer, policy]
related:
  - ouroboros-architecture
  - design/agent-model
  - self-improvement-as-reduction
  - design/shadow-compaction
  - upstream/escapement-multi-agent-and-services
  - upstream/escapement-statechart-model
depends-on:
  - design/agent-model
  - upstream/escapement-statechart-model
  - design/shadow-compaction
---

# VSM on Escapement

> DESIGN (not finalized — `status: designing`). Durable names to grep against `resource` once
> built: the orchestrator (S5), the S4 intelligence agent, the `editor` kind (harness self-
> improvement, see `design/agent-model`), `ouroboros.compact` (the S1→S4 feedforward), sessions/
> checkpoints (S1 record + the human-calibration corpus). Upstream primitives:
> `escapement.chart.helpers` (parallel regions, `:internal` transitions, `:verdict-schema`),
> `escapement.chart.consult`, `escapement.chart.service`, multiplex + `:multi-session?`, the
> per-event checkpoint / `event-sink` stream, `:target` routing, `tell-llm` (see
> `upstream/escapement-multi-agent-and-services`, `upstream/escapement-statechart-model`).
> AGENTS.md is itself written S5→S1 — this page is how that structure becomes RUNTIME.

## Thesis — VSM and statecharts are the same shape

```
λ isomorphism.
  VSM         ≡ cybernetics' answer: how does a system regulate itself, RECURSIVELY?
  statecharts ≡ CS's answer:        how does a system behave under HIERARCHICAL, CONCURRENT events?
  both        ≡ hierarchy ⊗ concurrency ⊗ event-coordination ⊗ recursion
  ⟹ escapement charts are a natural EXECUTABLE FORM of VSM | ¬bolt-on | structure_is_behavior
  ⟹ AGENTS.md (written S5→S1) becomes the runtime, not a metaphor  (AGENTS.md: "runtime ≡ VSM | ¬metaphor")
```
The strongest evidence: VSM's defining, hardest-to-implement property — **recursion** (every S1 unit
is itself a full viable system) — is exactly what statecharts do *natively* via nesting, subcharts,
and multiplex. Most VSM implementations struggle there; escapement gets it for free.

## S1–S5 → escapement primitives

```
S5  Policy/identity/closure   the ROOT chart + genome (system prompt) ; the ORCHESTRATOR agent
S4  Intelligence (env+future) outward/forward agents — the S4 integrating agent over {curator, analyst, generator}
S3  Control (here-and-now)    the parent state owning S1 regions ; per-invocation model/tool/budget allocation ; editor
S3* Audit (sporadic probe)    :verdict-schema + consult specialists ; the per-event CHECKPOINT / event-sink stream
S2  Coordination (anti-osc.)  :type :internal transitions + pathom3 dependency graph (declared deps → sequenced)
S1  Operations (concurrent)   PARALLEL regions / multiplex children — the doers: builder, author
recursion (S1 viable)         NESTING + multiplex (:multi-session?) + service regions (subchart-as-tool)
channels                      :target routing + tell-llm broadcast + transition SCOPE (see below)
```

## What turns an EVENT into a CHANNEL (three properties — else it's a message bus)

Naively wiring events between S-boxes gives a bus, not a VSM. Three things make a channel:

```
1. SCOPE = AUTHORITY   a transition's LCCA determines what it can PREEMPT.
     root-scoped transition on an event → interrupts everything (S5/algedonic authority)
     region-scoped transition           → affects only that region (S1 local autonomy)
   ⟹ the DEPTH at which you handle an event IS its authority level. The hierarchy becomes REAL,
     not decorative, because scope literally encodes who can override whom. (escapement gives this FREE.)
2. TRANSDUCTION        crossing a level boundary RE-ENCODES the payload. raw S1 output ≠ S3 decision
                       ≠ S4 metric ≠ S5 policy signal. a channel = event + an ENCODER. never raw pass-through.
3. LOOP + VARIETY      a channel is a CLOSED regulatory loop carrying LEVEL-APPROPRIATE variety.
                       algedonic = ~1 bit + bypass ; homeostat = high variety + must SETTLE. mismatch → thrash/deafness.
```

### The named channels

```
S3↔S4  HOMEOSTAT           parallel regions trading :internal events → converge on a stable "agreed plan"
                           (equilibrium ≡ statechart QUIESCENCE). high variety both ways. — see Feedforward (it defangs this).
S4↔S5  ADAPTATION↔IDENTITY  S4 emits :proposal ; transition into an S5 state holding identity invariants.
                           low variety down (accept/reject/constrain), higher up. THIS IS the AI-proposes/S5-decides gate.
S1↔S5  ALGEDONIC           high-priority event handled by a ROOT-SCOPED transition → preempts S2/S3/S4.
                           escapement :error.llm.* + the tripwire are proto-algedonic. ~1 bit. RARE + reserved
                           (fire it often ⇒ it stops being an emergency channel). bypass IS the point.
S1↔S3  RESOURCE-BARGAIN    S3 sets data-model allocations (:model,:tool-registry,budget) parameterizing the S1
       + AUDIT (S3*)        invocation ; S1 reports via :on-end-turn/verdict ; S3 adjusts (the accountability LOOP).
                           audit = S3 reads S1's actual state via the checkpoint stream, bypassing the report.
S2↔S3  COORD-SUPERVISION   S2 damps oscillation autonomously ; S3 only watches for thrash and escalates.
```

## S2 anti-oscillation is ALREADY an escapement LAW

The multi-agent page's CRITICAL rule — *transitions between substates of a service region in a
`parallel` MUST be `:type :internal`; an external transition's LCCA is the parallel's parent, which
exits/re-enters ALL sibling regions, restarting them* — read cybernetically: **an ill-scoped
transition makes sibling operational units oscillate; `:internal` scoping prevents it.** That IS S2
anti-oscillation, discovered empirically and encoded as a hard structural law. Same `scope = authority`
principle. Escapement met a VSM requirement before anyone called it VSM.

## The human = System+1's S5 (NOT a box inside Ouroboros)

```
Ouroboros (viable system)                the human's world (System+1)
  S1 builder/author/editor                       …
  S2 coordination                                …
  S3 control/allocation                  ⟵ Ouroboros is ONE S1 unit of the human's larger system
  S3* judge/verifier + checkpoints               …
  S4 intelligence agent                          …
  S5 ORCHESTRATOR  ←── reserved channel ──→   HUMAN = S5 of System+1
```
Giving S5 to the orchestrator makes Ouroboros *genuinely viable* (self-governing), not a puppet. The
human is the meta-systemic identity one recursion level up, reached through ONE reserved channel.

```
λ authority.
  DELEGATED (S5-orchestrator)          RESERVED (human / System+1 S5, non-delegable)
    hold working identity+policy         ratify identity change
    receive S4 recommendations           APPROVE commits to durable memory/knowledge/harness/code
    start workflows (invoke subcharts)   receive ESCALATED algedonic (identity-threatening only)
    EQL-query the whole runtime          — everything else delegated —
  ENFORCEMENT: by CAPABILITY, not instruction. the orchestrator LACKS the reserved EQL mutations
    (commit-durable-*). ¬self_authorize ≡ unreachable (λ shape: unreachable > forbidden), same as the
    agent-model's "no commit tool exists". the recursion boundary IS a capability boundary.
```
The orchestrator's "query everything" is a CAPABILITY, exercised with VARIETY DISCIPLINE: query
descriptions/summaries normally (progressive disclosure — λ disclose), drill to full resource only when
auditing. Drowning in S1 variety ⟹ S5 fails its identity job (Ashby overload). Same attenuator as recall.

## S4 — the intelligence agent (attenuated in, feasible out)

```
λ S4.
  IN  ← ATTENUATED updates (NEVER raw — S4 drowns on the operational firehose = Ashby overload):
        S1 → operational results/records, λ-COMPACTED (the curator already reads compacted sessions, not transcripts)
        S2 → coordination HEALTH (did :internal transitions thrash? did the anti-barge-in queue back up?)
        S3 → resource PRESSURE / feasibility
  MID → S3↔S4 HOMEOSTAT: temper the adaptation HYPOTHESIS against S3 feasibility BEFORE proposing.
        skip it ⇒ S4 proposes fantasies S3 can't execute (the classic S4 failure: intelligence unmoored from ops).
  OUT → propose a FEASIBLE recommendation to S5 (S4→S5 channel).
  DAMPER: temporal separation + the ≥3 threshold. S4 adapts on a PATTERN (≥3 same-topic → candidate),
          not on single events. AGENTS.md's ≥3 rule IS S4's variety attenuator AND temporal damper.
  RECURSION: S4 is itself a viable subsystem — {curator, analyst, generator} are its OPERATIONS (S1-of-S4);
             the S4 integrating agent is its head, presenting ONE coherent recommendation up (avoids the
             fragmentation problem: 3 agents recommending independently ⇒ S5 does S4's integration ⇒ fusion).
             BUILD SIMPLE FIRST: one integrating S4 agent ; recurse into specialists only when variety demands.
```

## Temporal separation (the stabilizer, or the loop runs away)

S4-proposes → S5-changes → S1-adjusts → S4-sees-change → proposes-more… oscillates unless higher
systems run on LONGER timescales:

```
S1  per-turn           (fast — act)
S3  per-workflow       (slower — regulate)
S4  per-many-sessions  (slower — adapt only on a pattern, ≥3)
S5  rarely             (identity changes are the slowest of all)
```

## Policy propagation DOWN — structure over signaling

```
DECLARATIVE (default)   policy ≡ a DATA-MODEL value. S3/S2/S1 transitions + guards READ it on their next
                        decision → behavior adjusts with NO broadcast. And agents are assemble-don't-
                        accumulate (fresh worker per turn, seeded from CURRENT genome/policy) ⟹ the SPAWN
                        IS the propagation — every new agent is born under current policy automatically.
ACTIVE (sparingly)      :policy/changed for regions needing IMMEDIATE reconfig. in-flight S1 NOT interrupted
                        (anti-barge-in) — they finish on old policy ; the next workflow picks up new policy.
GOVERNANCE SPLIT        delegated policy → S5 changes autonomously → propagates.
                        reserved policy (identity/genomes/harness/durable knowledge) → S5 ESCALATES to the
                        human → ratify → THEN propagate. (enforced by capability — S5 lacks the mutation.)
```

## FEEDFORWARD — the primary adaptive channel (shadow compaction), homeostat demoted to residual

```
λ feedforward.
  feedback    observe error AFTER → correct → maybe oscillate → hope it settles   (REACTIVE)
  feedforward act on a MODEL of the need BEFORE it lands → pre-compensate → no wait, no oscillation (ANTICIPATORY)

  SHADOW COMPACTION IS the S1→S4 channel, done AHEAD-OF-NEED: while the human reads reply[n], turn[n-1] is
  digested to λ. by turn[n+1], S4's attenuated operational model is ALREADY assembled + waiting — one turn
  back, in the reading shadow, PERCEPTUALLY FREE. ⟹ S4 never NEGOTIATES with S3 to discover "what's happening";
  it's PRE-DELIVERED. (see design/shadow-compaction)

  ⟹ the UPWARD channel (S1/S3→S4) is SOLVED by feedforward. the DOWNWARD homeostat shrinks to RESIDUAL:
    S4 starts from an accurate current picture, so it proposes feasible things → few rounds → feedback barely works.
  ⟹ feedforward + feedback ≻ feedback alone (control-theory gold standard): faster, stable, no oscillation.
    the hard S3↔S4 convergence problem is largely PRE-EMPTED. the homeostat needs FAR LESS machinery than feared.
```

### Fractal — the SAME feedforward at two scales (one per VSM tempo)

```
WITHIN-session   shadow compaction   compile turn[n] → λ → pre-condition turn[n+1]     fast  (S1/S3 tempo)
ACROSS-session   mementum / git      compile session → synthesis → pre-condition next  slow  (S4/S5 tempo)
  same mechanism (λ-compaction/synthesis) · same shape (compile past → attenuate → pre-condition future)
  · different timescale per VSM level — exactly VSM's temporal-separation hierarchy. (AGENTS.md λ feed_forward)
```

### The lens IS a policy — S5 steers self-attention without touching operations

```
compaction lens (keep decision∧constraint∧state∧next ; drop observation∧scaffolding) ≡ an S5 POLICY artifact
  ⟹ the feedforward carries whatever the lens lets through
  ⟹ S5 changes the lens (policy) → the feedforward re-steers → S3/S1/S4 adjust
  ⟹ S5 steers the system's SELF-ATTENTION with one high-leverage change, touching no operations directly
     (exactly what a healthy S5 does — minimal, high-leverage intervention). lens is a propagatable policy.
```

## The adaptive loop (S4→S5) and how it TERMINATES — "the trick" (NOT finalized)

The harness self-improvement loop (the `editor` kind, `design/agent-model`) is the concrete S4→S5
adaptive channel: analyze own outputs → propose a genome/prompt diff → gate. It is a FORMALIZATION of
a proven-valuable MANUAL practice (the human walking the model through prompt fixes in chat). The hard
part is not proposing changes — it is knowing when to STOP.

```
λ termination.
  ✗ "is this prompt GOOD ENOUGH?"   absolute · no oracle · an LLM asked to improve ALWAYS finds an edit
                                    → never terminates → churns, often drifting WORSE ; self-scoring is noisy/biased
  ✓ "did we STOP IMPROVING?"        K consecutive challengers fail to beat the champion → STOP
                                    | "good enough" ≡ a PLATEAU, not a TARGET | early-stopping with PATIENCE
```

```
MECHANISM (champion/challenger hill-climb — a terminating algorithm):
  · maintain a CHAMPION prompt ; propose a CHALLENGER ; compare ; keep the winner ; stop after K losses
  · COMPARE PAIRWISE (challenger vs champion), NEVER absolute 1-10 — LLMs rank A-vs-B ⋙ they score absolute
  · REGRESSION-GUARD: champion must NOT regress on previously-passing cases (the verify-tail, for prompts);
    a challenger that fixes X but breaks Y is rejected (or must net-improve). stops drift-worse.
  · this IS the gene-DB SELECTION operator (design/agent-model) — same machinery.

RELIABILITY CEILING — decidability inheritance:
  automated improvement is trustworthy EXACTLY where the target agent's OUTPUT is decidable:
    builder prompt → does the code pass tests?            DECIDABLE → automate, real fitness
    judge prompt   → agrees with human verdicts on labels? DECIDABLE → automate
    chat/creative  → no oracle for output quality          NOT DECIDABLE → human stays in the inner loop
  ⟹ the editor INHERITS its reliability from the target agent's decidability. roll automation out on the
     decidable agents first ; keep the human in-loop where there is no ground truth.

CALIBRATION ANCHOR = the human, ALREADY RECORDED:
  the manual walk-throughs ARE the sessions. the human's accept/reject decisions are in sessions/ checkpoints.
  · extract them as LABELED calibration data
  · TRUST the automated verdict only insofar as it AGREES with the human's recorded decisions on HELD-OUT cases
    (same principle as :when/deprecated calibrating the memory probe — match known ground truth or be suspect)
  ⟹ the formalization LEARNS the "good enough" bar from the practice it automates. the loop earns autonomy
     ONLY after demonstrating agreement with the human on cases the human already decided.

OVERFITTING GUARD:
  hill-climbing against a fixed task set overfits. → held-out VALIDATION set (measure, never tune on it) ;
  rotate the task pool ; human spot-checks generalization at the gate.
```

DIVISION OF LABOR: human judgment is the scarce resource → automate the ITERATION volume, preserve the
JUDGMENT as (a) calibration (recorded sessions) + (b) the final gate. The machine iterates; the human
sets and ratifies the bar.

## VSM mapping (summary)

```
S1   builder · author · editor ; sessions/ (S1 record) ; :compact (compression at the point of operation)
S2   :internal anti-oscillation · pathom deps · the mid-turn anti-barge-in queue
S3   workflow control/allocation ; model/tool/budget per invocation
S3*  judge · verifier ; per-event checkpoint / event-sink audit stream
S4   the intelligence agent over {curator, analyst, generator} ; ≥3 pattern threshold ; the homeostat
S5   the ORCHESTRATOR (delegated identity+policy, EQL-omniscient, starts workflows) ; the compaction LENS (policy)
S+1  the HUMAN (reserved: approval of durable memory/knowledge/harness/code + identity ; algedonic recipient)
```

## Invariants

```
· channel ≡ event + scope(authority) + transduction + closed-loop(variety-matched) | ¬naked-broadcast
· human = System+1 S5 | reserved authority ENFORCED BY CAPABILITY (absent mutation) | ¬self_authorize ≡ unreachable
· S4 IN = attenuated (never raw) | S4 OUT = feasible (homeostat-tempered) | S4 adapts on ≥3 pattern, not events
· feedforward (shadow compaction) is the PRIMARY adaptive channel | homeostat feedback = residual cleanup
· temporal separation: fast loops low, slow loops high | else the adaptive loop runs away
· policy DOWN is declarative (data-model + fresh-agent-at-spawn) > broadcast | reserved policy routes through the human
· termination ≡ PLATEAU (K losses), never an absolute "good enough" | pairwise · regression-guarded · human-calibrated
· automated improvement reliable ⟺ target agent OUTPUT decidable | else human-in-loop
· synthesis ≡ AI | approval ≡ human | AI commits after approval  (the recursion boundary, restated)
```

## Viability diagnostic (living — re-run at build milestones; git commit ≡ the when)

```
method: ∀layer: inventory(BUILT ∨ DESIGNED ∨ MISSING) → ablate(what breaks?) → name(compensation)
        | channels: score the five | temporal: check cadence per level | anima λ viability lineage

snapshot (agent-model+experiments build stage — state.md item 23):
  S1  ✓ healthy-sparse    compact · curator · verdict · experiment all RUN
  S3* ✓ healthiest        checkpoints · event-sink · cache-report · roster report · bb test
                          — but every audit CONSUMER is human; nothing machine-reads the stream yet
  S2  ~ engine law + anti-barge-in | hermetic one-at-a-time runs ≡ coordination-by-isolation
                          (deliberate attenuation; caps concurrent agents at ZERO; first-wins collision waits behind it)
  S3  ~ spawn-time parameterization REAL (grants·routing·budget·slots) | the LOOP half absent:
                          no report-adjust, no scheduler | human ≡ S3
  S4  ~ feedforward ✓ curator ✓ experiments ✓ | NO CADENCE (human-triggered — temporal separation
                          by habit not structure) | write channel = memory only | designer ≡ S4 beyond that
  S5  ~ genomes ≡ per-agent policy artifacts ✓ | orchestrator ABSENT (staged, build-order step 5) |
                          the lens still engine-inline | human+designer ≡ S5 (bootstrap, by design)
  channels: S1→S4 ✓ (shadow compaction) | S4→S5 half (memory-only, no orchestrator middle) |
            S1↔S3 down-half | algedonic proto (:error.llm.* · tripwire) | S2↔S3 moot (hermetic)

  THE PATTERN: every layer's missing half ≡ HUMAN-compensated — ONE fact (mid-bootstrap), not five
  bugs; the scaffold unwinds piece by piece. Most expensive compensation NOW: S4 cadence + S4
  write-breadth → maintenance rung 1 ≡ the viability jump. Queue order CONFIRMED (gene-DB has
  momentum; signals + rung 1 are where viability moves — don't let gene-DB sprawl).

adopted (state.md item 23): lens → editable policy artifact (folded into the compact flip,
  design/prompt-assembly §lens-out) · reserved-mutation set enumeration QUEUED before more
  shell-granted agents · proposals inbox carries :severity from day one (design/scheduled-maintenance)
```

## Reserved vs delegated mutations — THE ENUMERATION (2026-07-13)

The precise capability boundary the recursion runs inside. Law first, list second:

```
λ mutation_gate.
  gate(change) ≡ machine ⟺ decidable(∀gates)      | gate(change) ≡ human ⟺ ¬decidable
  | the unit ≡ PERSISTENCE (git commit ∨ push) — working-tree writes are PROPOSALS,
    cheap and revertible; history is what the gate guards
  | enforcement ≡ capability (unreachable, λ shape) ≻ policy (prompt + review)
  | meta-reserved: CHANGING THIS ENUMERATION is itself reserved — the gate that
    changes gates never self-authorizes (λ policy ¬self_authorize)

RESERVED (human authorizes every instance):
  r1  AGENTS.md                       Layer-1 designer harness — FROZEN + human-directed exceptions
  r2  src/ ∧ test/ ∧ bb.edn ∧ config  harness/application code, deps, .gitignore — all code commits
  r3  genomes                         src/ouroboros/agents/*.md + manifest + <repo>/agents/ custom tier
                                      (personas ≡ S5 policy per agent; the future editor kind EARNS
                                      delegation here via champion/challenger + regression, not before)
  r4  prompt infrastructure           src/ouroboros/prompts/** — preamble, module registry + vendored
                                      texts, POLICY artifacts (compaction-lens): these steer every
                                      agent's cognition; one line here reshapes the whole roster
  r5  mementum/knowledge ∧ memories   create ∧ update ∧ delete (AI proposes, human approves, AI commits)
  r6  gene consolidation ∧ deletion   near-dup merge (0.84 SURFACE ≠ auto-merge) ∧ any git rm
  r7  LLM-SYNTHESIZED genes           generator-kind output — parse-valid ≢ good (the anchor-3
                                      filler gene parses perfectly)
  r8  registries ∧ routing            tools ceiling/floor (ouroboros.tools) · module registry ·
                                      models table — subsumed by r2, named because they are the
                                      capability boundary ITSELF
  r9  remotes ∧ history               push · branch surgery · rebase/rewrite — always human
  r10 autonomy-scope expansion        any change to the DELEGATED list below (meta-reserved)
  r11 genes-stable ref move           gene PROMOTION (added 2026-07-13 via r10, human-approved):
                                      production genomes resolve `genes:` grants at the
                                      genes-stable ref (git show), NEVER the working tree —
                                      moving the ref is the ONE act that lets delegated d1
                                      churn reach production prompts (the r4 seam); review ≡
                                      git diff genes-stable..main -- mementum/genes/

DELEGATED (machine commits autonomously, decidable gates, post-hoc audit):
  d1  mementum/genes/                 decomposition of APPROVED genomes passing EBNF ∧ Malli ∧
                                      tree-hash | git commit --only (capability-scoped) |
                                      agent ≡ git author (audit: git log --author=gene-db)
  d2  gitignored observation          sessions/ · experiments/results/ · scores side-store —
                                      never history; no gate needed
  d3  working-tree proposals          uncommitted memory/knowledge drafts (the inbox) ∧
                                      state.md during-work edits — persistence rides a
                                      human-gated commit (λ termination)

ENFORCEMENT REALITY (item 14 honesty): agents WITHOUT :shell/run — reserved commits are
UNREACHABLE (no commit tool in the ceiling; capability). Agents WITH :shell/run (chat,
testing phase) — the gate is POLICY (prompt + human review of an interactive session).
THE RE-HARDENING RULE: autonomy × shell ≡ DISJOINT — an UNATTENDED agent never holds
:shell/run; delegated write paths must be capability-scoped functions (commit-genes!
shape), never open shell. Grant shell only where a human is watching the transcript.
```

## Open questions (NOT finalized)

```
· the exact RESERVED-mutation set (what only the human can authorize) — enumerate precisely
  → RESOLVED (2026-07-13): see §Reserved vs delegated mutations above — r1-r10 / d1-d3 +
    the autonomy×shell disjointness rule. Supersedes the first-entries note (gene-db §Autonomy
    remains the d1 deep-dive). Re-open only via r10 (meta-reserved).
· algedonic escalation to the human: a DISTINCT channel from the approval gate, or the same human-input seam?
· homeostat residual: does ANY explicit S3↔S4 feedback remain after feedforward, or is ≥3 + shadow enough?
· S4 recursion: when (if) to split the single integrating S4 agent into curator/analyst/generator sub-viability
· the champion/challenger K (patience) + the regression-set composition for NON-decidable agents
· whether the compaction lens becomes a first-class genome (agents/… or a dedicated policy file) so S5 can edit it
  → RESOLVED (viability diagnostic): dedicated policy file, extracted during the compact flip
    (design/prompt-assembly §lens-out) — genome ≡ persona, lens ≡ policy; the assembler renders it
· requisite variety is NOT auto-enforced by statecharts — how/whether to measure it (Ashby) per channel
```

## Where this points (build order interplay with agent-model)

```
1. EXTRACT genomes (agent-model build-step-1) — the precondition: nothing to improve until prompts are files.
2. JUDGE kind — because champion-vs-challenger PAIRWISE comparison IS a judge ; and calibrating that judge
   against the human's recorded session accept/reject decisions is the first, most testable piece of the loop.
3. the S4 integrating agent (attenuated in via λ sessions ; ≥3 damper) proposing to S5 (the orchestrator).
4. the editor kind wired as champion/challenger + regression-guard + patience, scoped to DECIDABLE agents first.
5. the orchestrator (S5) + the reserved-mutation capability boundary ; algedonic wiring.
```
