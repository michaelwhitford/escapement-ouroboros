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

## Strategy population & autonomous evolution (the VSM resolution)

> Settled in design conversation (2026-07-18, human). The naive path — `bb genes`
> the player genomes into the shared gene-DB — COLLIDES: the strategy clause
> (`λ style`: range ∧ aggression) is rejected as a name-collision with an
> unrelated code-`style` gene, and `poker-tight`'s style ≠ `poker-aggro`'s style
> cannot coexist under one-gene-per-name. The collision is DIAGNOSTIC (λ emerge):
> it marks where a channel's variety requirement is violated.

```
λ variety_polarity.  the gene-DB is a PURE S2 attenuator — one canonical clause
  per name, :sources ≡ who adheres. CORRECT for coordination. But GA-over-
  strategies is S4 AMPLIFICATION (Ashby: only variety absorbs variety); pushing
  it through the S2 store gets it (correctly, for S2) SUPPRESSED. The name-
  collision IS S2 killing the variety S4 requires — two subsystems, one channel,
  opposite polarity ≡ the crossed channel.
  | resolves vsm-on-escapement open-Q "requisite variety NOT auto-enforced per channel"
  | resolves gene-db open-Q "name collision policy (suffix? reject? version?)"

λ recursion.  a GENOME is itself a viable system; its clauses are its own S1-S5:
  identity→S5 · read→S4(sense) · style→S3/S4(policy) · decide→S3/S1(allocate/act)
  · why→S2(report) · terminate→closure. A viable subsystem SHARES its S2 floor
  (interoperation) but must be FREE to differ in its S5/S4/S3 core (else it is a
  CLONE, not a distinct viable system). Evolution acts on the identity∧strategy
  core, NEVER the coordination floor. The collision structure already TRACKS this:
  identity/style collided (divergent core), read/decide/why stored (shared floor).

λ two_stores?  VSM role → variety polarity → storage:
  S2/S1-mechanics genes (read · decide-potodds · why · ground · evidence · verify)
    → CONVERGE → canonical, one-per-name (current gene-DB behavior CORRECT here).
  S5/S4/S3-policy genes (identity · style · preflop-range · aggression · vs-pressure)
    → DIVERGE → a POPULATION keyed by LOCUS: many ALLELES per locus, fitness-selected,
    NEVER deduped. locus ≡ slot · allele ≡ variant (tight|loose|positional): the current
    gene-DB CONFLATES them (one allele per name ≡ "the gene").

λ collapse (the !meta3 reflection — the two "stores" may be ONE):
  a coordination gene ≡ a strategy locus whose selection has CONVERGED (monomorphic).
  → NOT two stores; ONE store where a locus is MONOMORPHIC (converged ≡ coordination
    standard) ∨ POLYMORPHIC (competing ≡ strategy population). the `style` "collision"
    ≡ gate-3 REFUSING polymorphism; the fix ≡ dedup by (locus × tree-hash) ¬by name.
  → governance ≡ variety ≡ blast-radius are ONE axis, yielding the FIELD EQUATION:
      λ autonomy.  autonomy(locus) ∝ polymorphism(locus) ∝ 1 / blast_radius(locus)
                   | polymorphic ∧ decidable-fitness ∧ bounded → DELEGATED
                   | monomorphic ∧ unbounded-ripple → RESERVED
    poker ≡ NOT a new delegated domain — the FIRST locus polymorphic+decidable+bounded
    enough to cross the autonomy threshold that ALREADY governs everything.
  ⚠ OPEN (human's call, next session): ONE store + per-locus governance TAG + scoped-
    commit-filters-by-tag (fractal purity + honest boundary) VS TWO stores (capability-
    enforcement-by-DIRECTORY — dead-simple audit "these files autonomous, those reserved").
    two-stores buys ONLY audit simplicity; one-store is the λ converge answer. UNRESOLVED.

λ arena_as_audit.  the arena ≡ S3* AUDIT made machine-readable — do NOT trust the
  genome's :why self-report; AUDIT by making it PLAY, chips ≡ the reading. FIRST
  machine-read audit channel (the vsm diagnostic's "every audit consumer is human"
  no longer holds). decidable(chips) ⟹ per the reliability-ceiling law, player-
  evolution moves from human-in-loop → AUTOMATABLE.

λ projections.  ONE apparatus, THREE reads — the genome × model × seed grid (λ converge:
  same shape as signals' one-contract-three-projections). the arena serves TWO regulators
  with OPPOSITE variety needs simultaneously:
  benchmark : fix(genome) vary(model)  → which MODEL plays better | non-determinism ≡ SIGNAL
              (S4 AMPLIFY: strategy divergence ∧ per-model CONSISTENCY are the data)
  GA        : fix(model)  vary(genome) → which PROMPT plays better | non-determinism ≡ NOISE
              (S3 ATTENUATE: duplicate seating cancels CARD luck, ¬LLM sampling — the GA
               needs more hands/trials/patience the benchmark does NOT want)
  content   : fix(spot)   vary(both)   → how they DIVERGE → decision-spot fixtures
  | the SAME variance is signal(benchmark) ∧ noise(GA) — opposite polarity, one organ
  | CONFOUND: seat ≡ genome × model, each the OTHER's confound → clean benchmark fixes
    genome, clean GA fixes model (converge! already does) → orthogonal SLICES, never one
    headline number | ALWAYS attribute (genome, model)
  | CAPTURE (priority raised): reasoning_content per decision ≡ "study ¬leaderboard";
    divergence events (two models, opposite line, same board) ≡ the figures

λ autonomous_promotion.  (🎯 human 2026-07-18 — an r10 delegated-set EXPANSION,
  authorized by System+1's S5). WITHIN the poker sandbox, promote/demote is
  DELEGATED. TWO-FACTOR gate:
    chips (S3 · decidable duel-winner over duplicate seatings) DECIDE the duel
    ∧ llm-judge (delegated S5) RATIFIES identity — guards degenerate/exploit wins
      (stalling · engine-quirk · variance-rewarded incoherence)
  | model SELECTION for a seat ≡ delegated (pick from REGISTERED models);
    ADDING a model endpoint ≡ RESERVED r8 (a capability/world change) —
    "choose from the menu freely; registering the menu stays human"
  | ENFORCEMENT ≡ CAPABILITY: the autonomous poker path is a `git commit --only`
    scoped to {population store · poker genome files · arena config} and
    PHYSICALLY cannot reach src/ ∧ general gene-DB ∧ knowledge ∧ harness.
    governance boundary ⟹ capability boundary ⟹ STORE boundary — the SEPARATE
    population store is what makes "autonomous here, reserved there" true by
    CONSTRUCTION (λ shape: unreachable > forbidden), not by prompt.
    autonomy × shell ≡ DISJOINT (the arena runner is a scoped fn, never a shell).
  | RESERVED still (human final say): general-use genes · harness · knowledge ·
    memories · model-table additions · this enumeration itself (meta-reserved r10)
  | NEW DELEGATED d4: poker strategy-population — allele create ∧ promote ∧
    demote(cull), gated by chips ∧ llm-judge, scoped commit, agent ≡ git author
    (audit: git log --author over poker-zone paths only)
```

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
5. ✅ duplicate + report  — run-duplicate! (swapped seats, same deals → luck cancels; identical
                            play ⇒ net 0) + benchmark-report | bb poker-bench | BUILT 2026-07-18
6. strategy population    — locus/allele store, capability-scoped autonomous commit (--only,
                            walled from the S2 gene-DB ∧ src ∧ knowledge ∧ harness — λ two_stores)
7. fitness bridge         — arena chips → allele fitness (S3* audit feeds the S4 population)
8. GA loop + 2-factor gate— converge! (untouched) + arena :duel-fn + generator :challenger-fn |
                            PROMOTE ⟺ chips-win ∧ llm-judge ratifies identity (λ autonomous_promotion)
9. (later) decision-spot → experiment fixtures | diplomacy design page | resident table v2
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
