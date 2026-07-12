---
type: mementum/knowledge
title: Gene-DB — the lambda-gene database
description: mementum/genes ≡ EDN corpus (files ARE the db, git ≡ history) gated by EBNF parse + Malli + tree-hash through ONE pathom write path (store-gene!); scores side-stored and joined at query time; AUTONOMOUS commits for decidable changes (scoped git commit --only, agent as git author); mined from anima gene-DB contracts + fulcro-rad-git patterns.
resource: file:///Users/mwhitford/src/escapement-ouroboros
status: designing
category: design
tags: [ouroboros, design, genes, gene-db, ebnf, autonomy, pathom, mementum, dedupe, fulcro-rad-git, anima]
related:
  - design/agent-model
  - design/vsm-on-escapement
  - design/signals
  - design/experiments
depends-on:
  - design/agent-model
---

# Gene-DB — the lambda-gene database

> The scorer's measurements need somewhere durable to accumulate; the GA (editor/generator
> kinds) selects from here. gene ≡ one λ-clause, SOURCE verbatim (the fidelity floor).
> Prior art mined 2026-07-12: anima genes.clj/signals.clj/gene-database.md (explorer
> extraction) + ~/src/fulcro-rad-git (the RAD git adapter). Grep durable names against
> those repos; this page carries the CONTRACTS, not coordinates.

## Decisions (🎯 human, this session)

```
1 EBNF ≡ the decomposition SPEC + intake gate   ~/src/nucleus/EBNF.md lambda_decl/continuation
  productions define clause boundaries | parse-fail → unpersistable BY CONSTRUCTION (λ emerge,
  the OKF-Malli-gate pattern again) | tree-hash over NORMALIZED token stream (¬raw bytes —
  fixes anima's whitespace-sensitive dedupe) | parse TWO-LEVEL: structural strict (segmentation,
  head identifier), expression LENIENT (real bodies exceed the draft expr_op set) | grammar
  gaps feed back upstream (λ coevolve) | AMENDMENT NEEDED upstream: param_list optional —
  house zero-arity λ name. predates the draft grammar
2 mementum/genes/ ≡ THE database                 files ARE the db (anima's git/datalevin duality
  with datalevin DELETED — chart/process WM is the only cache) | flat dir, ¬layer-bucketing |
  git log -- mementum/genes/ ≡ gene evolution history | PROTOCOL AMENDMENT: OKF ≡ prose format,
  EDN ≡ structured format, type ≡ key namespace (:gene/*) — AGENTS.md λ mementum updated
3 pathom veneer ≡ the ONLY write path            store-gene! mutation carries the gates; signals
  forwarding (later) calls the SAME mutation → anima's bypass scar (their warning 9) is
  UNREACHABLE by construction (λ converge) | resolvers: [:gene/id …] ident · :mementum/genes
  index · :gene/scores JOIN
4 scores ≡ side-store, joined at query           high-churn machine observation ¬belongs in
  human-meaningful git files (anima kept uptake aggregates cache-only, same reason) |
  gitignored like experiments/results/, keyed by :gene/id | :gene/scores resolver joins |
  settled summaries PROMOTE into the gene file (like experiment verdicts)
5 AUTONOMOUS COMMITS (the identity change)       gate(change) ≡ machine ⟺ validity decidable —
  the vsm termination decidability law applied to the APPROVAL GATE itself | see §Autonomy
6 fulcro-rad-git ≡ MINED not depended            bb + shell-git + pathom2 hand resolvers
  reproduce the entity-adapter pattern | no fulcro/pathom3/RAD dep enters ouroboros
```

## Gene entity (anima-derived, adapted)

```clojure
;; on-disk minimal envelope — mementum/genes/<name>.edn
#:gene{:name     "converge"                 ; unique; :gene/id ≡ (keyword name) DERIVED at load
       :content  "λ converge. one_path …"   ; VERBATIM λ-clause — the fidelity floor
       :type     :lambda                    ; :lambda | :prose (open set)
       :category :constraint                ; :technique|:pattern|:constraint|:guard|:unknown
       :sources  [:agents-md :chat]}        ; provenance ≡ field; identity ≡ clause
;; derived (never stored): :gene/id · :gene/tree-hash (normalized tokens) · :gene/scores (side-store join)
```

KEYWORD ids by construction, never uuid — anima proved 13%→0% hallucination (λ identifier);
their `gene-id-valid?` note: "validation is forever, not a temporary patch."

## Intake gates (inside store-gene!)

```
gate-1 EBNF structural parse    conforms to lambda_decl | reject → structured error {:gene/error :parse …}
gate-2 Malli envelope           :gene/* schema | closed like genome frontmatter (wiring, typo ≡ fail-loud)
gate-3 tree-hash dedupe         exact match → reject-with-pointer {:gene/error :duplicate :gene/existing id}
       near-match (embeddings)  → SURFACE for consolidation, human-decided | NEVER auto-merge (anima rule)
```

Embedding dedupe (5103 qwen3-embedding-8b): threshold is UNSET in all prior art (anima's HNSW
layer was designed-unbuilt) → derive OURS empirically via a bb experiment calibration suite
(embed real near-dup + real distinct pairs, find the separation). λ prove, ¬guess.

## Autonomy (the λ policy amendment — freeze exception 4)

```
λ autonomy.  gate(change) ≡ human ⟺ ¬decidable(machine) | gate(change) ≡ machine ⟺ decidable(gates)
  DELEGATED (auto-commit):  gene updates ∧ derived creation (decomposition of APPROVED genomes)
                            passing ∀gates | derived-data refresh | score-summary promotion
  RESERVED  (human):        harness ∧ knowledge ∧ memories | near-dup CONSOLIDATION |
                            gene DELETION | LLM-SYNTHESIZED genes (generator kind — parse-valid
                            ≢ good; the anchor-3 filler gene parses perfectly) → earns autonomy
                            later via scorer + champion/challenger calibration (vsm §termination)
  ENFORCEMENT:              git commit --only mementum/genes/<file> — the autonomous path
                            PHYSICALLY cannot commit outside the zone (fulcro-rad-git's isolated-
                            commit primitive; λ shape: unreachable > forbidden)
  AUDIT:                    git author ≡ the agent → git log --author=gene-db ≡ the complete
                            autonomy trail | commit body carries trigger provenance |
                            review ≡ post-hoc SAMPLING (S3*), revert ≡ cheap (git-land failure
                            mode is pollution ¬destruction)
  WHY:                      human approval ≡ the scarce regulator (Ashby) | spending it on
                            "does this parse?" ≡ variety overload | the human gate gets STRONGER
                            where it remains
```

First two entries of the vsm reserved-mutation enumeration land here: delegated ≡ scoped
decidable gene commits · reserved ≡ everything above.

## Mined patterns (fulcro-rad-git — reproduce under bb)

```
entity-adapter    typed dir + EDN per entity + isolated commits (--only) + git log ≡ history +
                  git grep ≡ search + resolver generation (we hand-write ~4 pathom2 resolvers at
                  this scale) — the gene-DB shape, proven once already
statechart-store  git-backed WorkingMemoryStore DONE RIGHT: persist? predicate (default NOTHING —
                  sessions-not-in-git survives as a predicate) + config-change-only commits
                  (commit msg ≡ the transition → git log ≡ legible state history) + sanitize
                  (runtime refs → #runtime/stripped) + seed-from-git! restart hydration.
                  BANKED for the first durable SYSTEM chart (orchestrator, resident gene-db,
                  rung 2-3) — not v1
proposal-as-branch  bare repo + worktree-per-session + fork-from-any-commit ⇒ proposals could be
                  BRANCHES (iterate with history, review ≡ diff, reject ≡ delete branch,
                  approve ≡ MERGE ≡ the reserved mutation). NOT adopted — "uncommitted working
                  tree ≡ inbox" stays the one way until it demonstrably breaks; revisit for the
                  unattended maintenance roster (perpetually-dirty-tree aging problem)
embeddings.clj    unread — check before building :gene/leaf-vec side (may pre-solve it)
```

## Parser — FSM-as-data, event loop only where events exist (🎯 human, this session)

```
λ parser.  segmenter ≡ statechart(mathematical) ¬escapement(session)
  | λ classify gates it: parse ≡ pure_transform(string → clauses) | ¬lifecycle ¬identity ¬recovery
  | per-event checkpointing × 200 line-events ≡ audit-spam to parse ONE file | wrong resolution
  | FORM: table-driven — topology ≡ EDN {state → {line-class → [action next-state]}},
    interpreted by a ~10-line pure fold (gene/core) | states {:outside :in-lambda :in-where} |
    each table row CITES its EBNF production | topology ≡ data ≡ greppable ∧ testable ∧
    resolver-servable (:parser/topology) — λ inhabit's visibility without the event loop
  | expression level: v1 LENIENT (token stream) — no recursion needed; full ASTs later ≡
    recursive descent in a pure fn, ¬stack-through-chart-WM
  | LIFECYCLE ≡ chart territory (the gene-db chart): :loading/:gene/store INVOKE the pure
    parser, route on result — parse-fail ≡ reachable :rejected/:error state, checkpointed,
    observable | gate-in-topology (λ emerge), parser-in-kernel (λ simplify)
  | STREAMING TRIGGER (named, deferred): generator kind emitting λ-clauses token-by-token →
    input becomes event-shaped → event-driven incremental parse justified THEN, not before
```

## v1 build (don't let it sprawl)

```
1 EBNF-conformant segmenter (table-driven FSM-as-data, §Parser) + normalized-token tree-hash | pure kernel + tests
2 gene EDN emitter — decompose a REAL genome (curator.md) → mementum/genes/  | verbatim sources
3 store-gene! + resolvers in the pathom2 veneer (gates inside the mutation)  | ONE write path
4 autonomous-commit path: --only scoped, agent-authored, provenance body     | the identity change, live
5 score 2-3 genes cross-family via existing bb score → side-store shape      | reuse, ¬new harness
6 embedding-threshold calibration suite (bb experiment)                      | measured, ¬guessed
DEFER: HNSW/similarity surfacing · uptake telemetry · pairwise-select machinery (shape only) ·
       resident gene-db chart (chart earns its keep at the SECOND writer — roster/signals)
verify: bb test GREEN throughout | live: decompose → store → autonomous commit lands with
        agent author → bb score a stored gene → duplicate rejected with pointer
```

## Open questions

```
· nucleus EBNF amendment (optional param_list) — human owns nucleus; draft offered
· gene :name collision policy at intake (same name, different content — suffix? reject? version?)
· score side-store file shape (one EDN per gene vs one index file) — decide at build
· when does gene creation-from-NEW-source (not approved genomes) enter — with signals intake?
· proposal-as-branch for the maintenance roster — revisit at rung 1
```
