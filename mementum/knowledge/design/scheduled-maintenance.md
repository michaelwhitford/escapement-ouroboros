---
type: mementum/knowledge
title: Scheduled Maintenance — the 2×2 proposer roster, role-as-tag, and the timer ladder
description: The system waits for human input by default, so maintenance agents run on a schedule — a 2×2 roster ({harness,app} × {coder,knowledge}, matrix slugs, ALL kind=proposer at stage 1) swept once or twice daily; "curator" is a ROLE not an agent and lives as an open-vocabulary genome TAG the loader genuinely consumes (schedule selects by tag, report groups by tag — tags pick WHO runs, never what-may); the schedule is data outside the genomes ({select-by, cadence, subject, budget-ms}); and the mechanism is a three-rung ladder, each rung source-verified — cron→bb maintain (sequential hermetic sweep, works today) → resident orchestrator + send-after (zero patches) → resident + a timer InvocationProcessor (Tony's statecharts custom-invocation example; lifecycle-scoped so pausing an agent ≡ exiting its state), whose only obstacle is a 2-line lib-facade seam (Options lacks invocation-processors — same gap class as the human-renderer gap, extra-body precedent).
resource: file:///Users/mwhitford/src/escapement-ouroboros
status: active
category: design
tags: [ouroboros, design, maintenance, schedule, roster, proposer, curator, tags, timer, invocation-processor, cron, residency]
related:
  - design/agent-model
  - design/agent-comms
  - design/harness-coder
  - design/vsm-on-escapement
depends-on:
  - design/agent-model
  - design/agent-comms
---

# Scheduled Maintenance — the 2×2 roster and the timer ladder

> RUNG 1 BUILT (2026-07-13, state item 31). Durable names: genomes `harness-coder`
> `app-coder` `harness-knowledge` `app-knowledge` (base tier); `ouroboros.schedule`
> (table · select-slugs · sweep-plan · sweep! · lock); `ouroboros.proposer` (the
> generalized runner, ex-`ouroboros.curator`); `ouroboros.proposals` (gate · pending ·
> inbox); tools `:harness/context` + `:ouro/propose-change` (NOT `:harness/propose-change`
> — app-coder shares the channel); tasks `bb maintain [slug]`, `bb proposals`; dirs
> `/proposals/` + `/.maintain.lock` (gitignored, root-anchored). Rungs 2/3 remain
> design-only; timer reference: the `custom-invocation` example in the statecharts repo
> (`~/src/statecharts`, grep `InvocationProcessor` / `:timer`). §Built (rung 1) below.

## The ask (human, 2026-07-11)

The system runs waiting for human input by default. Maintenance agents should run once or
twice a day unattended: assess code against specs, mine sessions for harness improvements,
metabolize memories/knowledge, improve docs — each producing human-gated proposals.

## The 2×2 roster — four genomes, ONE kind

```
                CODE (assess vs spec → findings)      KNOWLEDGE (metabolize → memory/docs)
  HARNESS       harness-coder                          harness-knowledge
                sessions+genomes → friction findings   sessions+mementum → memory/knowledge
                (detail: design/harness-coder)         proposals (≈ today's curator genome)
  APP           app-coder                              app-knowledge
                src/ vs design pages → flaw/drift      sessions+src+docs → doc improvements
                findings (specs ≡ the design pages —   + app knowledge pages (NEW corpus:
                detects drift BOTH directions)         needs fs-read grants)
```

```
λ roster.
  ∀four : kind proposer (stage 1) — observe(corpus) → detect(pattern) → propose(ONE) → human-gate
  | the agent-model rule "new role ⇒ new genome, not new kind" pays off: 4 .md files, 0 new kinds
  | stage 2: the coder column graduates to editor kind (diffs, judge-gated, gene-DB era)
  | verifier stays OUTSIDE the matrix — different role (claims vs live truth), serves all four
    (BUILT: ouroboros.screen — the sweep's :screen-fn verdicts every inbox artifact post-run;
     content-hashed idempotent side-store proposals/.screen.edn; bb screen ∨ bb maintain)
```

## Naming decisions (🎯 human, this session)

```
🎯 matrix slugs   genome names are self-descriptive {target}-{artifact}. They age well:
                  a fifth concern (app-tests? deps?) extends the matrix without convention change.
🎯 curator ≡ ROLE harness-knowledge ∧ app-knowledge ARE curator-type agents (same kind, same
                  role, different corpus). "curator" survives as a TAG + prose, not an agent name.
                  Today's curator genome ≈ the harness-knowledge facet; app-knowledge is new.
   runner         ouroboros.curator (the namespace) is really the proposer-topology runner wearing
                  one agent's name — the judge→verdict generalization again. Rename when bb maintain
                  forces it (runner takes genome slug), updating blessed grep-names in the same commit.
```

## Role-as-tag (🎯 human): tags become WIRED frontmatter

```
λ role-as-tag.
  genome schema += tags (optional, vector, normalized → keywords) | closed map grows ONE key
  | λ boundary satisfied: the loader CONSUMES tags — schedule selects by tag, roster report
    groups by tag → role is load-bearing, not decoration
  | kind stays a CLOSED set (topology finite, unknown ⇒ typo) ; tags stay OPEN (roles EMERGE —
    curator, assessor, verifier — no schema change per new role)
  | OKF symmetry: mementum files already carry tags; ONE format everywhere, type discriminates

λ tag_discipline.
  tags select WHO runs — NEVER what-may | capability stays in tools/channels grants
    (a :trusted tag implying grants ≡ capability-by-prose, the anti-pattern the ceiling exists to kill)
  tags ≡ identity (in the genome) | cadence ≡ deployment ops (in the schedule table, OUTSIDE genomes)
    — an agent doesn't know when it runs; the schedule names who runs
  seed vocabulary: curator (harness-knowledge, app-knowledge) · assessor (harness-coder, app-coder)
```

## The schedule table (data, sibling to models/channels)

```
{entry {:select   {:tag :curator} ∨ {:slug :app-coder}     ; tag ⇒ set-valued, auto-includes new genomes
        :cadence  …                                         ; rung-dependent encoding (cron expr ∨ interval)
        :subject  "standing prompt, e.g. assess src/ouroboros vs design/agent-model.md; find drift"
        :budget-ms …
        :enabled  true}}
```

## The mechanism — a three-rung ladder (each rung source-verified this session)

```
rung 1  CRON → bb maintain                          BUILD FIRST — works today, zero escapement changes
        OS launchd/cron fires ONE sequential sweep; each selected agent runs as a hermetic
        lib/run (the curator runner pattern). WHY sequential: one GPU at 5100 (slot/bandwidth
        contention); WHY hermetic: per-run credentials sidestep the multi-model collision entirely.

rung 2  RESIDENT + send-after                       zero patches — supported path
        com.fulcrologic.statecharts.convenience/send-after, proven inside escapement (the
        supervisor example uses it as a cancel-on-exit budget timer). Orchestrator tick-state
        re-entry loop ≡ periodic schedule reading the same table.

rung 3  RESIDENT + :timer InvocationProcessor       the declarative form — Tony's pattern
        statecharts custom-invocation example: deftype implementing sp/InvocationProcessor,
        supports-invocation-type? :timer; chart declares (invoke {:type :timer :params
        {:interval ms} :finalize …}) and receives tick events while the owning state is active.
        | LIFECYCLE ≡ SCOPE: stop-invocation! fires on state exit → the schedule DIES with its
          owning state → pausing an agent ≡ exiting its state (structure as behavior, λ emerge)
        | :finalize updates data-model per tick (run counters, last-run)
        | GOTCHA (documented in the example): the sent event MUST carry :invoke-id or finalize
          never registers
```

### The rung-3 seam finding

```
engine  escapement.engine/env ACCEPTS :invocation-processors and PREPENDS caller processors
        to its own (llm · human-input · chart · multiplex) — custom processors are first-class
lib     escapement.lib/Options (closed schema) LACKS the key — the same gap class as the
        known :human-renderer seam | fix ≡ ~2 lines (Options entry + run-opts passthrough),
        the :extra-body fork precedent proves the pattern | cut ONLY when rung 3 is wanted |
        upstream-PR candidate alongside the other two seam keys
```

## Unattended-run discipline (what daily runs force)

```
1 DEDUP   a daily agent re-finds yesterday's finding → context tools MUST digest PENDING
          proposals/ + uncommitted memories | genome clause: ¬re-propose(∃pending) |
          embed-dedupe (5103) when volume demands
2 RATE    ONE proposal per agent per run (curator's proven discipline) → human review
          ceiling ≈ 4-8 items/day | the gate must never become a silently-overflowing queue
3 INBOX   bb proposals — pending proposals/ + uncommitted memories, per-agent, with evidence
          links | morning-coffee batch review: approve / refine / discard | every proposal
          carries :severity (:ordinary ∨ :algedonic ≡ identity-threatening, surface FIRST) —
          the seed of the S1↔S5 bypass channel; costs a keyword now vs a channel redesign
          later (viability diagnostic, state.md item 23)
4 BUDGET  :budget-ms per scheduled run (the house bound) — a wedged unattended agent dies
          quietly and logs
5 AUDIT   unattended sessions land in sessions/ + transcripts as usual → curator-type agents
          can read them | bb maintain emits one summary line per agent run
6 LOCK    two overlapping sweeps must not run (cron fires while yesterday's sweep wedged) —
          a lockfile in the sweep runner, stale-broken by age
```

## VSM notes

- The daily cadence IS the temporal-separation damper vsm-on-escapement requires (S4 on
  longer timescales than S1) — scheduling is the stabilizer, not a convenience.
- Rung 3's timer-per-agent-region makes cadence part of chart TOPOLOGY — authority over an
  agent's schedule is scope over its state.
- Proposals ride `:s4/proposal` to the human membrane once agent-comms lands; until then
  the inbox is `bb proposals`.

## Build sketch (rung 1; slots after gene-DB per the standing queue)

```
1 agents.core schema += tags (normalize like kind/tools) | roster report += tag column
2 generalize the proposer runner (curator.clj pattern → genome-slug arg, verdict/-main style)
3 genomes ×4: split curator → harness-knowledge · add app-knowledge (fs-read grants) ·
  harness-coder (see design/harness-coder) · app-coder (subject: assess vs design pages)
4 ouroboros.schedule table + bb maintain (sequential, budget-per-run, summary lines, lockfile)
5 bb proposals inbox | .gitignore += proposals/
6 launchd/cron entry (human machine config, outside the repo)
verify: bb test GREEN (schema/tags/table deterministic) | live: one full sweep produces
  ≤4 grounded proposals citing real evidence; re-sweep does NOT duplicate them
```

## Built (rung 1, as it landed)

```
λ built(rung-1).
  runner        ouroboros.proposer — genome-slug-parameterized (judge→verdict move):
                chart per-run FROM the genome; per-run models/llm-config (hermetic);
                registry armed {:source slug :signal-types (genome signals)} |
                bb curate RETIRED → bb maintain harness-knowledge (one_way)
  tags          genome schema += tags, OPEN vocab (roles emerge; kind stays closed);
                loader consumes: schedule selects by tag (set-valued — new genomes
                join a sweep automatically), report shows tags
  sweep         sequential hermetic (GPU + multi-model collision sidestepped);
                per-run exception → :fail outcome, sweep CONTINUES; lockfile
                .maintain.lock stale-broken >2h; ONE summary line per run
  signals       the ROSTER EMITS: runner-emitted :s1/report per agent run (source
                bb-maintain, infrastructure-set — agents hold NO signal grants yet);
                duplicate-damped (dedupe rejection ≡ the damper working, non-fatal)
  proposals     ouroboros.proposals: Malli-gated propose! (severity REQUIRED,
                ordinary|algedonic); pending re-propose REJECTED at the gate (slug
                collision → corrective); inbox algedonic-FIRST; unparseable files
                SURFACED not hidden (λ escalate); untracked-memories moved here
                (inbox vocabulary, ex-curator-runner)
  dedup floor   :harness/context digests PENDING proposals + uncommitted memories +
                roster report + genome bodies + models + modules; requiring-resolve
                breaks the tools←agents cycle (λ dep)
  LIVE PROOF    full sweep exit 0: 4/4 done — app-knowledge +1 memory (31s) ·
                harness-knowledge +1 memory (68s) · app-coder honest ∅finding (270s) ·
                harness-coder +1 proposal citing 3 REAL session-ids (28s); 4 :s1/report
                signals; re-run harness-coder → did NOT re-propose (found a DIFFERENT
                grounded finding) — the ¬re-propose(∃pending) discipline held
  observation   re-run's second finding cited ONE session (the ≥2-recurrence damper is
                prompt-soft) — watch; tighten the genome clause if single-event
                proposals recur
```

## Open questions

```
· ~~curator namespace rename timing~~ RESOLVED — renamed at bb maintain as predicted
  (ouroboros.proposer; blessed grep-names updated in the same commit)
· app-knowledge emits TWO artifact types (doc edits vs knowledge pages) — different gates?
  (doc edit ≈ working-tree change proposal; knowledge page ≈ mementum proposal) — may split
  the genome later; start with knowledge-pages-only, docs as a named candidate
· tag vocabulary governance — none for now (open by design); roster report lists distinct
  tags as the drift-visibility surface
· cadence encoding — rung 1 wants cron-side truth (table stores intent only); rung 2/3 want
  intervals in the table. Keep the table authoritative and let rungs interpret.
· ~~Layer-1 flag-only channel~~ RESOLVED (🎯 human) — approved, bootstrap-scoped; sunsets
  with self-hosting (see design/harness-coder §Layering)
```
