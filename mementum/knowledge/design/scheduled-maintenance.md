---
type: mementum/knowledge
title: Scheduled Maintenance — the 2×2 proposer roster, role-as-tag, and the timer ladder
description: The system waits for human input by default, so maintenance agents run on a schedule — a 2×2 roster ({harness,app} × {coder,knowledge}, matrix slugs, ALL kind=proposer at stage 1) swept once or twice daily; "curator" is a ROLE not an agent and lives as an open-vocabulary genome TAG the loader genuinely consumes (schedule selects by tag, report groups by tag — tags pick WHO runs, never what-may); the schedule is data outside the genomes ({select-by, cadence, subject, budget-ms}); and the mechanism is a three-rung ladder, each rung source-verified — cron→bb maintain (sequential hermetic sweep, works today) → resident orchestrator + send-after (zero patches) → resident + a timer InvocationProcessor (Tony's statecharts custom-invocation example; lifecycle-scoped so pausing an agent ≡ exiting its state), whose only obstacle is a 2-line lib-facade seam (Options lacks invocation-processors — same gap class as the human-renderer gap, extra-body precedent).
resource: file:///Users/mwhitford/src/escapement-ouroboros
status: designing
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

> Forward-looking durable names: planned genomes `harness-coder` `app-coder`
> `harness-knowledge` `app-knowledge`; planned table `ouroboros.schedule`; planned tasks
> `bb maintain`, `bb proposals`; planned dir `proposals/`. Timer reference: the
> `custom-invocation` example in the statecharts repo (`~/src/statecharts`, grep
> `InvocationProcessor` / `:timer`).

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
          links | morning-coffee batch review: approve / refine / discard
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

## Open questions

```
· curator namespace rename timing — at bb maintain (forces the generalization) ∨ earlier
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
