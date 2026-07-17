---
type: mementum/knowledge
title: Game Arena — adversarial games as decidable benchmark + content generator
description: A game-agnostic arena where genome-compiled agents play adversarial games (poker first, diplomacy later) — the GAME PROTOCOL is a pure-Clojure engine contract (init · legal-actions · apply-action · visible · to-move · terminal? · payoffs) whose `visible` projection makes hidden information UNREACHABLE (structure > instruction — an agent cannot see hole cards that never enter its context); decisions ride the EXISTING shot-per-decision hermetic runner (λ converge — no residency needed for turn-based play) with per-game Malli action schemas served registry-style (ONE contract ≡ filled exemplar primes generation ⊕ schema gates the verdict boundary ⊕ renderer serves observation text), illegal/invalid actions decay to the game's forfeit-default (fold/hold) so cheating is unrepresentable; payoffs are ARITHMETIC (chips/points) — the first fully DECIDABLE fitness signal for the gene-DB GA and champion/challenger convergence, a cross-family model benchmark (qwen vs gemma4 at the same table, duplicate seating cancels luck like comparator both-seatings cancels position bias), and a content generator (game transcripts ≡ EDN decision-spot fixtures replayable through the experiment harness).
resource: file:///Users/mwhitford/src/escapement-ouroboros
status: designing
category: design
tags: [ouroboros, design, games, arena, poker, diplomacy, benchmark, fitness, decidable, hidden-information, verdict, duplicate-seating]
related:
  - design/agent-model
  - design/experiments
  - design/gene-db
  - design/agent-comms
  - design/signals
depends-on:
  - design/agent-model
  - design/experiments
---

# Game Arena — adversarial games as decidable benchmark + content generator

> DESIGNING (2026-07-17); steps 1-3 BUILT. Durable names: `ouroboros.game` (protocol) ·
> `ouroboros.game.cards` (generic substrate) · `ouroboros.game.poker` (+ `.eval`) — carries the
> action contract as engine keys `:game/action-schema` (legality-narrowed enum) +
> `:game/action-exemplar` (no separate registry ns — λ converge, the engine map IS the registry
> entry) · `ouroboros.game.arena` (hand/match runner, bankroll modes, games/ transcripts).
> Still planned: genome kind `:player` (step 4). Verify by grepping names against src/.

## Why (three payoffs, one build)

```
λ arena_value.
  fitness   : payoffs ≡ ARITHMETIC (chips ∧ points) | decidable(∀gates) | ¬LLM_judge ¬rubric
              | gene-DB GA + converge! get REAL selection pressure | first non-opinion fitness
              | judge/scorer verdicts stay for prose subjects; games self-score
  benchmark : seat ≡ genome × model | fix(genome) vary(model) → MODEL benchmark
              | fix(model) vary(genome) → GENOME benchmark (GA substrate)
              | cross-family EARNED: qwen36 vs gemma4 at one table — decorrelation as gameplay
              | metrics beyond payoff: illegal-action rate (λ mirror under pressure) ·
                verdict-validation failures · latency · token cost · cache-hit rate
  content   : transcript ≡ EDN facts (seed ∧ observations ∧ actions ∧ payoffs)
              | decision-spot → experiment fixture (same spot × lens/thinking/model matrix —
                the compaction-detail pattern, but spots are ADVERSARIAL and ground-truthed)
              | research narrative: "two model families played N hands; here is what diverged"
```

## The game protocol (pure engine contract — the extraction target)

```
λ game(engine).
  init(config, seed)          → state                 | seeded ≡ replayable | state ≡ FULL truth
  to-move(state)              → #{seat}               | singleton ≡ sequential (poker)
                                                      | set ≡ simultaneous (diplomacy orders)
  legal-actions(state, seat)  → actions ∨ generator   | engine-enumerated, never LLM-trusted
  apply-action(state, seat→action) → state'           | total fn: illegal ≡ forfeit-default(state, seat)
  visible(state, seat)        → observation           | THE hidden-info projection (see λ hidden)
  terminal?(state)            → bool
  payoffs(state)              → {seat → number}       | zero-sum not required | ARITHMETIC only
  forfeit-default(state, seat) → action               | poker: fold∨check | diplomacy: hold
  render(observation)         → prompt-text           | engine owns its own narration format
```

Engine ≡ pure Clojure ≡ deterministic ≡ `bb test`-able with zero LLM. The LLM's ONLY
surface is `render(visible(state, seat)) → verdict(action)`. Everything else is code.

```
λ hidden.  agent_context ← visible(state, seat) ONLY | full_state NEVER serialized to a prompt
           | cannot_leak(hole_cards) ≡ unreachable ¬forbidden (λ emerge: topology > instruction)
           | public actions fold into EVERY seat's observation | private info stays seat-scoped
           | diplomacy press (later): message channel IS part of visible — same projection law
```

## Decision topology — shot-per-decision (λ converge: rides existing infra)

Turn-based games do not need residency. Each decision is a pure function of
`(visible-history, seat-private-info)` — exactly the hermetic-shot geometry:

```
λ decide(seat, state).
  genome(seat) → assemble (THE one assembler)
  → messages: render(visible(state, seat)) ⊕ filled action-exemplar (registry, λ mirror)
  → :verdict-schema ≡ action-schema(game, state-phase)     | escapement forces submit_verdict
  → validate: Malli ∧ legal-actions(state, seat)           | two gates: SHAPE then LEGALITY
  → invalid ∨ illegal ∨ worker-death → forfeit-default     | game continues, failure RECORDED
                                                           | (illegal-rate ≡ benchmark metric,
                                                              not a crash — the arena never wedges)
  → :conversation/id ≡ seat-key → id_slot pin              | per-seat prompt-cache across streets
                                                           | (llamacpp backend, modeled fields)
λ loop(match).
  init(seed) → while ¬terminal?: ∀seat ∈ to-move → decide ∥? → apply-action → fold(transcript)
  | sequential game: one decide per step | simultaneous: collect all seats THEN one apply
  | thinking: per-genome choice (player genomes may differ — itself a benchmark axis)
```

WHY not a resident table chart (escapement `:target` routing, service regions, multiplex):
that is the v2 geometry, EARNED when a game needs mid-turn interaction — diplomacy
negotiation press is the forcing use-case (λ comm: channels/residency DEFERRED ⟺
interactive multi-agent workflows exist; a poker turn is not interactive, an auction of
promises is). The protocol above is deliberately runner-agnostic: the same engine drops
into a resident chart later without change.

## Action contracts — registry-style (ONE contract, three projections)

Signals precedent (design/signals): per game(-phase), a registry entry holds

```
{:game/action-schema  <malli>       ; gates the verdict boundary (escapement :verdict-schema)
 :game/exemplar       <filled-EDN>  ; primes generation (λ mirror: exemplar ≻ prose, qwen-proven;
                                    ;  gemma4 tolerant either way — exemplar costs nothing)
 :game/render-hints   …}            ; observation narration knobs
```

Poker: `{:action (enum :fold :check :call :raise) :amount int :why str}` — `:why` is
CONTENT (one-line table-talk / reasoning trace for the transcript), never parsed by the
engine. Diplomacy: orders map, per-unit enum — same registry shape, richer schema.

## Seats, matches, tournaments — variance discipline

```
λ seat.        {:seat/genome slug  :seat/model alias  :seat/thinking? bool}
λ match.       game × seats × seed × hands-n → transcript ⊕ {seat → payoff}
λ duplicate.   SAME seed/deals × seat-permutations → luck cancels
               | comparator both-seatings precedent (position bias cancellation) —
               |   same kernel idea, generalized to N seats
λ tournament.  TWO formats, different questions:
               | benchmark ≡ round-robin(seat-pool) × duplicate → aggregate table
               |   (variance-controlled, per-decision quality — the RESEARCH instrument)
               | elimination ≡ starting_stack(∀seat) → play until one survivor (🎯 human-wanted)
               |   stacks CARRY across hands · busted ≡ out · escalating blinds force action
               |   (tournament-structure FSM: level ≡ {blinds, duration}) · winner ≡ THE story
               |   (survival skill ⊃ decision skill: bankroll pressure, shove/fold, ICM-ish)
               | 5×-repeat discipline applies (experiments lineage): confirm before conclude
               | elimination is high-variance BY DESIGN — content format, benchmark stays duplicate
λ benchmark.   report ≡ {payoff/hand · illegal-rate · validation-failures · latency ·
                         tokens · cache-hits} per seat | payoff alone lies at small n
```

## Storage & content pipeline

```
λ store(arena).
  transcripts → games/          | gitignored (sessions/ ∧ scores/ ∧ candidates/ pattern)
                                | EDN per match: seed · per-decision {seat obs-hash action why ms tokens}
  fitness     → scores/ side-store (EXISTING shape) | per-(genome, game) mean payoff
                | generator/converge consume unchanged — arena is just a new scorer
  research    → decision-spots: transcript → experiment fixture (experiments/*.edn conditions
                may :assemble through the REAL pipeline — infra already exists)
              | findings → knowledge pages (human-gated, normal λ metabolize path)
  signals     → match-complete summary ≡ candidate signal type (registry entry) — the sweep
                can then schedule tournaments (bb maintain tag) WITHOUT new plumbing
```

Autonomy note (λ policy): transcripts/fitness are machine-generated decidable facts in
gitignored side-stores — no commit gate needed at all. Genome PROMOTION on game fitness
stays human-gated (diff-report lifecycle) until the decidability rollout says otherwise.

## Kind question — `:player` (11th kind, honest addition)

The verdict runner's kind→verdict-schema table is static per kind; a player's schema is
per-GAME(-phase), resolved at decide-time from the game registry. That is a real semantic
difference (dynamic schema injection), so: **new kind `:player`**, one row, schema-fn not
schema-literal. Player genomes carry style/aggression/table-image as PROMPT — which makes
them gene-DB decomposable and GA-evolvable with arena fitness. tools: [] (floor only) —
a player needs NOTHING but the observation; git/fs unreachable by absence.

## Games roadmap

```
poker (v1)   : heads-up limit hold'em → full ring → no-limit → ELIMINATION tournament
               | (starting cash · stacks carry · escalating blinds · last agent standing —
               |  the content flagship: "N models sat down, one walked away")
               | tests DECISION quality under hidden info + decidable payoff
               | engine: deck · 7-card eval · betting FSM · pot/side-pots · showdown
diplomacy    : tests COMMUNICATION — negotiation press ≡ private targeted messages,
               simultaneous orders ≡ multi-seat verdict collection, adjudication ≡ pure engine
               | THE forcing case for resident-table v2 (escapement :target routing ∧
                 service regions ∧ multiplex — upstream/escapement-multi-agent-and-services)
               | press plane ≡ λ comm channels EARNED, not speculative
extraction   : ouroboros.game protocol stays THIN until game #2 exists (λ build:
               pattern ∃ twice → extract shape) — poker builds CONCRETE, diplomacy extracts
```

## v1 build order (each step lands green before the next)

```
1. ouroboros.game.poker   — pure engine + deterministic tests (zero LLM, zero new deps)
2. action registry entry  — schema ⊕ exemplar (poker betting phase)
3. arena runner           — decide/loop/forfeit + stubbed decide-fn tests (RunTestsTool precedent)
4. poker-player genome(s) — :player kind row + 2 styles (tight/aggressive) | bb poker ≡ watch a match
5. duplicate + report     — seat-permutation matches, benchmark table, transcripts → games/
6. fitness bridge         — arena payoffs → scores/ | bb generate/converge on a poker use-case
7. (later) decision-spot → experiment fixtures | diplomacy design page | resident table v2
```

## Open questions

```
? thinking traces in transcripts — research gold vs cost/latency; capture reasoning_content
  per decision or only :why? (start: :why only, add capture flag when a study needs it —
  RAISED PRIORITY now that thinking is the declared benchmark axis, see below)
🎯 thinking stays ON for players (human, 2026-07-17) — reasoning quality IS what separates
  model families at the table; the arena extends per-decision :budget-ms (default 600s,
  ouroboros.game.llm/default-budget-ms) rather than disabling thought. An arena that clips
  thinking measures patience, not poker. (First live match's lone gemma4 forfeit ≡ 120s
  budget artifact — reclassified, not a model failure. Contrast the COMPACTION think-OFF
  decision: that is an infrastructure path where speed ≻ deliberation; the table is the
  opposite geometry.)
? bankroll semantics — SETTLED by format (2026-07-17, human): benchmark mode resets per
  hand (decision quality, variance-controlled); elimination mode carries stacks until one
  survivor (starting cash → busts → winner). Engine must support BOTH from the start:
  stack ∈ state, reset ≡ a match-loop policy not an engine concern | blinds schedule ≡
  elimination-mode config
? seat count > families — self-play (same model both seats) contaminates decorrelation
  claims but is fine for genome-vs-genome; report must ATTRIBUTE (genome, model) always
? diplomacy press protocol — free text vs structured proposals; and does press ride
  signals (durable, pull) or targeted messages (live, push)? deferred with v2
```
