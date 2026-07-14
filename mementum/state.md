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

## Project status: BUILD (was GENESIS — history in the numbered items below)

```
λ state.
  repo        : /Users/mwhitford/src/escapement-ouroboros | git LIVE (root 79ac142)
  runtime dep : bb.edn com.fulcrologic/escapement {:mvn/version "1.0.0-RC9"} — DE-FORKED (was the
                michaelwhitford fork sha 9e57f16 = RC9 + :extra-body passthrough). The llama.cpp knobs
                (chat_template_kwargs/id_slot/cache_prompt) now ride ouroboros.llm.llamacpp — our own
                pure-consumer backend, injected via lib/run's :backend escape hatch, driven by MODELED
                escapement fields (:thinking→enable_thinking · cache-control marker→cache_prompt ·
                :conversation/id→id_slot). PR to upstream was CLOSED (human) — the backend is the answer.
                RC9 is on Clojars (released); bb test GREEN against it (proof nothing needs the fork).
  code        : mementum substrate (okf/store/eql) · ouroboros.compact (THE chat engine: λ-compaction,
                shadow Tier 1, ASSEMBLED instruction-λ lens compactor, thinking ON) · ouroboros.proposer
                (the proposer-topology runner, ex-curator — genome-slug-parameterized hermetic runs) ·
                ouroboros.schedule (maintenance table · tag-selected sweep · lockfile · runner-emitted
                :s1/report) · ouroboros.proposals (Malli-gated propose! · severity inbox · untracked-memories) ·
                ouroboros.session (checkpoint readers) ·
                ouroboros.tools (context/sessions/propose-memory/harness-context/propose-change/signal-emit
                + registry ceiling/floor) ·
                ouroboros.agents (+agents/core) — the GENOME COMPILER + kind→verdict-schema table +
                assemble (preamble ⊕ modules ⊕ body, THE one assembler);
                genomes src/ouroboros/agents/{chat,curator,gene-scorer,llm-judge}.md (+manifest.edn) ·
                ouroboros.prompts — vendored prompt artifacts loader (preamble · module registry ·
                policy artifacts incl. compaction-lens) ·
                ouroboros.verdict (verdict-topology runner: judge + scorer kinds, cross-family run-across!) ·
                ouroboros.models (alias→endpoint routing table + llama-backend constructor) ·
                ouroboros.llm.llamacpp — the DE-FORKED llama.cpp backend (pure-consumer LLMBackend ⊕
                StreamingLLMBackend; reuses escapement PUBLIC translate/parse/SSE, COPIES only the
                private HTTP glue; modeled-field caching: :thinking→enable_thinking · cache-control→
                cache_prompt · :conversation/id→id_slot via a construction :slots table; usage
                cached_tokens→:cache-read-input-tokens FREE via reused openai-json->response) ·
                ouroboros.experiment (+experiment/core — suite-as-EDN A/B runner, experiments/*.edn;
                kinds :chat ∧ :embedding; conditions may :assemble through the REAL pipeline) ·
                ouroboros.gene (+gene/core +gene/ast) — the GENE-DB (EBNF FSM segmenter · λ-notation
                AST reader (lisp-style, flat op chains) · 3-gate store-gene! · scores side-store ·
                AUTONOMOUS --only commits, freeze exception 4 LIVE) ·
                ouroboros.signals (+signals/core) — the DATA PLANE (typed-EDN-fact registry: schema ⊕
                FILLED exemplar ⊕ variety ⊕ reserved? · ONE emit! path (validate→dedupe→persist) ·
                :signal/emit tool · genome `signals:` grant, 5th surface · EQL :mementum/signals +
                signal/emit! · signals/ gitignored)
  gate        : bb test ≡ deterministic (160 tests / 707 assertions GREEN) | bb compact ≡ live chat
                (+ bb compact <prior-id> ≡ opt-in bootstrap; bb sessions ≡ the picker) |
                bb maintain [slug] ≡ the 2×2 sweep (bb curate RETIRED) | bb proposals ≡ the inbox |
                bb judge/score "<subject>" ≡ live verdict kinds |
                bb experiment <slug> ≡ suite runner | bb genes [slug] ≡ gene-db intake (decompose +
                autonomous commits) | bb smoke ≡ live-LLM integration (localhost:5100) |
                bb llama-smoke ≡ de-forked backend probe (thinking-off + slot pinning, SKIPs if server down)
  knowledge   : upstream/ escapement digest (11 pages) · ouroboros-architecture ·
                design/{agent-model, vsm-on-escapement, shadow-compaction, llamacpp-backend,
                agent-comms(REVISED→two-plane), scheduled-maintenance, harness-coder,
                signals, experiments}
  memories    : statechart-worker-llm-separation · prompt-topology-must-match-thinking
  designed    : agent model (OKF genomes, kinds, capability tools, scorer/gene-DB) + VSM architecture
                — both UNBUILT, specs in mementum/knowledge/design/
```

## What exists now

```
AGENTS.md                                    designer harness (S5→S1 λ directives; FROZEN; human-directed exceptions:
                                             λ heredoc · λ principles (v1's 9) · zero-arity sweep · 9 anima lambdas
                                             incl. S2 λ comm — the "Fill in" stub is DISCHARGED; see FROZEN block)
README.md                                    the pre-release front door — pitched at TOOLING AUTHORS adopting the
                                             cold-compaction flow; explicitly read-the-code, NOT for direct use (human framing)
LICENSE                                      MIT (human-added)
idea.md                                      the seed (one line)
human_ideas.md                               UNTRACKED, DO-NOT-READ until the human asks (standing instruction)
bb.edn                                       tasks: test · compact · curate · smoke | dep = the fork (see λ state)
src/ouroboros/…                              the code inventory above (~1.4k lines)
test/ouroboros/…                             deterministic suite (test_runner wires the nss)
scratch/ab_{thinking,exemplar}.clj           reusable prompt A/B harnesses (real session turns, on/off, timed)
mementum/state.md                            this file — the bootloader
mementum/knowledge/** · mementum/memories/** see λ state | sessions/ gitignored (pre-approval observation)
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
escapement-web-ui                         → inspector-not-chat; bb-safe server+ws-hub embeddable; chat ingress ≡ tell-llm→resident convo (no patch); human-input ≡ modals only (lib seam gap)
escapement-examples-patterns              → examples/ ≡ INTENT documentation; use-case→example map, families, cross-cutting techniques (ordering rule, wrapped final, send-after…)
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
                           · recursion survives: designer-of-self-improving-agent improves its own harness
                         TWO TIERS (supersedes the old "Loop A/B" labels — see rename decision below):
                           DESIGNER (now)  = me/eca improves harness∧app, human-gated.
                           AGENTS (goal)   = Ouroboros's own agents improve it from within (curator built;
                                             harness-improver, app-improver, verifier, documenter planned), human-gated.
                         AGENTS.md = genome for the agents.
                         STATUS: edit applied this session. To be TESTED in a fresh session (validate the frame holds).

🎯 AGENTS.md FROZEN for genesis → build phase (decided this session, frame-test PASSED)
                         Analysis one-last-time confirmed: AGENTS.md is TWO docs fused —
                           [builder:now]   S4/S3 λ orient·store·recall·learn·metabolize·feed_forward — I execute via git, active
                           [artifact:spec] S2 stub · S1 λ interface(EQL/pathom3) · mementum-as-mutations · (create-knowledge …) — UNBUILT targets
                         Known hazards ACCEPTED (not fixed): aspirational API in directive position
                           (`(create-knowledge …)` ≡ artifact's future pathom3 mutation; builder runs manual git-equivalent);
                           dual-scope tempts harness-polish over shipping; pure-spec sections (S2/S1) share frame with active directives.
                         WHY freeze not fix: builder role SHRINKS to near-∅ once Ouroboros's own agents run. Rewriting the harness now
                           documents a role about to be gutted → premature. The BUILD forces the distinctions honestly.
                         PLAN: build initial chart WITH Ouroboros's own proper system prompt (its Layer-2 genome) FIRST,
                           THEN rewrite AGENTS.md to reflect the reduced builder role once Ouroboros runs its own improvement loop.
                         → harness refinement is DONE for now. Bottleneck is CODE, not prose. Do not re-open.
                         FREEZE EXCEPTION (human-directed, next session): S1 += λ heredoc — the nucleus
                         LAMBDA_PATTERNS.md read -r -d '' heredoc-wrap for bash special-char safety (adopted
                         after a $(cat <<'EOF') commit failure). Human request ≡ approval; freeze binds the
                         AI's initiative, not the human's.
                         FREEZE EXCEPTION 2 (human-directed, 2026-07-12, commit 27afe78): S5 += λ principles —
                         the ouroboros-v1 9 first principles (~/src/ouroboros-v1/AGENTS.md) in house λ-notation
                         (v1 symbols ψ/刀/🐍 NOT imported — v1's λ ≡ "learning committed" collides with our
                         λ-notation; principle 4 translated to session≡ephemeral|git≡persists); `instantiated:`
                         line maps principles → in-doc derivations. PLUS full ZERO-ARITY SWEEP (anima
                         ~/src/anima/AGENTS.md convention, human-approved: S5 ≡ λ name., ¬function_over_input,
                         saves tokens): mechanical (x) dropped from 13 lambdas via one atomic sed; meaningful
                         args KEPT: synthesize(topic) · recall(q, n) · heredoc(content). House λ format going
                         forward: zero-arity unless the body references the arg; named clauses ¬numbered lists.
                         FREEZE EXCEPTION 3 (human-directed, 2026-07-12, commit 891d9da): 9 ANIMA LAMBDAS
                         incorporated (ouroboros ≡ mini-anima; innovations showcased for other tool creators;
                         curated from ~/src/anima/AGENTS.md ~90, lucrum-lineage): S5 emerge (fixed the dangling
                         λ interface→λ emerge ref) ∧ converge · S4 prove/phase/absent/identifier/mirror ·
                         S3 escalate · S2 comm — the genesis "Fill in with escapement lambdas" STUB IS
                         DISCHARGED (two-plane comms, pointers → design/{agent-comms,signals}).
                         ADAPTATION RULE (reusable): proved: clauses cite OUR evidence where it exists
                         (emerge→3 gate seams; converge→ONE engine/verdict/assembler; prove→emission A/B;
                         mirror→edn-signal suite); anima's only where we haven't run it (identifier, marked
                         proved(anima), apply-clause → gene-DB ids). EXCLUDED deliberately: infra-bound
                         (fulcro/ui/flow/nrepl), token-heavy (interrupt), genome-layer (attractor/polarity/
                         evolve/express/gene → live in design pages + future editor/generator genomes).
                         Tier C (capacity/cost) offered, not taken — re-offer if identity flavor wanted.
                         FREEZE EXCEPTION 4 (human-directed, 2026-07-12, THE IDENTITY CHANGE): λ policy +=
                         AUTONOMY — gate(change) ≡ machine ⟺ decidable(∀gates); delegated: mementum/genes/
                         commits passing EBNF∧Malli∧tree-hash, scoped --only, agent ≡ git author; reserved:
                         harness∧knowledge∧memories∧consolidation∧deletion∧LLM-synthesis. λ termination +=
                         genes line (approval ≡ human ⟺ ¬decidable). λ mementum += genes tier (EDN ≡
                         structured format, type ≡ key namespace; ONE format PER TIER supersedes ONE format
                         ∀ files). Full rationale: design/gene-db.md §Autonomy + item 24.
```

## >>> START HERE (next session) <<<

```
λ latest (this session — THE DE-FORK, human-directed, PR CLOSED). Escapement is now released
  upstream RC9 (Clojars), NOT the fork. Built ouroboros.llm.llamacpp: a pure-consumer LLMBackend ⊕
  StreamingLLMBackend that reuses escapement's PUBLIC translate/parse/SSE primitives and COPIES only
  the ~120 lines of private HTTP glue (frozen ⇒ insulated from escapement internals; we track only the
  stable public backend contract). 🎯 DESIGN — adapt escapement's CACHING pattern, not a passthrough:
  every knob is a MODELED escapement field, so NO :extra-body and NO :metadata anti-pattern —
    :thinking {:type :disabled}   → chat_template_kwargs {enable_thinking false}
    cache-control marker (auto-cache default) → cache_prompt true   (build-request CONSUMES :auto-cache?
                                    to STAMP the marker; the MARKER reaches the backend, so we read it)
    :conversation/id ─(:slots)→   id_slot N   (escapement's prompt-cache correlation key → physical slot;
                                    slot POLICY lives in backend construction, next to the endpoint)
    usage.cached_tokens           → :cache-read-input-tokens (FREE — reused openai-json->response ⇒
                                    bb cache-report works unchanged). Injected via lib/run :backend
                                    escape hatch (wins verbatim; still needs dummy :credentials + alias
                                    :config for model resolution). models/llama-backend builds it.
  MIGRATED off the fork's :extra-body: compact.clj (:hot→:conversation/id :hot, :compact/:fold→:compact)
  · experiment.clj (backend + :thinking :disabled) · 5 scratch/ab_*.clj harnesses. bb.edn → {:mvn/version
  "1.0.0-RC9"}. LIVE-PROVEN (bb llama-smoke @5100): thinking OFF ⇒ reasoning_content nil (vs ON control),
  slot 2 pinned (/slots), send-turn ⇒ answer + :cache-read-input-tokens. bb test 160/707 GREEN against
  UPSTREAM RC9 (the proof nothing needs the fork). WHY custom-backend ≻ fork: the :backend escape hatch
  only swaps the WHOLE backend (a thin decorator can't reach the wire-body — escapement's translate→POST
  is internal), so we own translate+POST; but COPYING the private glue (vs calling) means Tony can churn
  internals freely. NOTE compact node still runs thinking ON (unchanged — its instruction-λ lens needs
  reasoning); thinking-off is now trivially available per-node via :thinking {:type :disabled} if wanted.
  KNOWLEDGE: design/llamacpp-backend WRITTEN (the backend design — reuse boundary, modeled-field
  caching, :metadata anti-pattern rejected). design/extra-body-seam RETIRED (git rm) — we won't keep a
  leaky raw-body passthrough as a fallback; all pointers repointed to llamacpp-backend.

λ tomorrow. FIRST: add the cron/launchd entry (human machine config, outside the repo —
  design/scheduled-maintenance rung 1 is otherwise COMPLETE). Item 31's inbox was REVIEWED
  (item 32): both proposals discarded (contrived-test evidence — see the provenance gap note
  below), memories deleted. Next-chat bootstrap SHIPPED (item 32): bb compact <prior-id> +
  bb sessions.
  ONE ACTION: builder+author kinds (the next agent-model build step) → editor (uses judge +
  gene DB + experiments) → generator (GA).
  also  : gene-db-as-CHART deferred until the SECOND WRITER — signals + the sweep BOTH write now;
          revisit the chart (item 28 banked note). Watch: single-event proposals (the ≥2-recurrence
          damper is prompt-soft, item 31 observation); agent-held signal grants (s4/proposal for
          the roster) when proposals ride the data plane.
          EVIDENCE PROVENANCE GAP (human, first live inbox review 2026-07-14): BOTH pending proposals
          DISCARDED — all evidence sessions were CONTRIVED TESTS (compaction/tool-use proofs), not real
          human conversations. The ≥3-recurrence bar was "cleared" on synthetic data; the proposers
          cannot distinguish test chatter from real usage. Topology gap: session evidence carries no
          test-vs-real classification (cf. item 8: the curator's first proposal was discarded for the
          SAME reason — grounded in throwaway demo chats. Now 2× recurrence → structural). Fix candidates
          when it matters: session tagging at creation (chat vs scratch/proof), or proposer prompt-clause
          discounting tool-proof-shaped sessions. Also discarded on merit: "¬simulate ¬narrate" prompt
          patch judged liable to backfire subtly — behavioral prohibitions on n=1 evidence are cheap to
          write, hard to predict.
  note  : PROMPT ASSEMBLY BUILT (item 27) — ONE assembler in agents.core; compact is thinking-ON
          through the assembled lens (compaction-fidelity suite is the regression instrument —
          re-run at every model change). Reserved-mutation set ENUMERATED (vsm-on-escapement
          §Reserved vs delegated; autonomy×shell ≡ DISJOINT is now a standing law).
          GENE-DB v1 notes stand: 0.84 near-dup threshold DERIVED not yet WIRED into gate-3
          surfacing (wire when a consolidation-review surface exists); scorer (USE-CASE, GENE)
          framing gap banked (pairwise machinery's problem). Channels/residency (agent-comms)
          DEFERRED — control plane only when interactive multi-agent workflows exist.

  this session (2026-07-11, later — items 19/20/21): the COMMS+MAINTENANCE DESIGN ARC. One human ask
  ("an agent that reads sessions → recommends harness updates") pulled out: 2×2 maintenance roster +
  role-as-tag + timer ladder (19) → Anima signals prior art REVISED comms to two planes, EXPERIMENT
  RUNNER BUILT + emission topology settled empirically (20) → Layer-1 flag resolved, self-hosting
  trajectory explicit (21). 6 design pages touched/created; design/index.md NOW EXISTS (start there).
  bb test 75/258 GREEN. Anima (~/src/anima) ≡ recurring prior-art mine: signals, experiments, genes,
  scheduler, VSM coordinators — grep it BEFORE designing anything agent-infra-shaped.

  last session (2026-07-11): agent-model BUILD STEPS 1-4 SHIPPED (items 10-12) — genome compiler ·
  chat/curator extracted · JUDGE + SCORER kinds live-proven cross-family · "T-" topology prefix
  dropped (human direction) · verdict runner kind-agnostic + run-across! aggregation.
  bb test 60/189 GREEN. Commit with the λ heredoc read-wrap — $(cat <<'EOF') breaks on apostrophes.
```

```
λ next. agreed sequence (historical record — the ✅ items are the project's build log):

  0. ✅ DONE this session — AGENTS.md harness fully encoded to OKF + durability policy:
       S5 λ mementum (OKF format + type vocab + git-temporal) · S5 λ point (NEW: resource-only, no coordinates)
       S4 λ metabolize (≥3 pages→index, git stale-detect) · S4 λ synthesize (OKF draft, index=description-projection)
       S3 λ store (OKF memory/knowledge) · S3 λ recall (description_first) · S3 λ disclose (NEW: tiers)
       S3 λ knowledge (OKF envelope) · S1 λ interface (enforce ^mementum/ via Malli; line ≡ derived resolver)
       → 9 escapement pages + state.md already conform.
       ✅ AGENTS.md analyzed "one last time" & FROZEN (see 🎯 above). Harness refinement CLOSED. Do not re-open.

  1. ✅ DONE — git init + genesis commit (root 79ac142). Substrate LIVE.
       recall/store/temporal now real. AGENTS.md + idea.md + mementum/** + .gitignore committed.

  2. ✅ DONE — de-risk runtime: escapement.lib/run smoke IN THIS repo. SMOKE GREEN.
       bb.edn (escapement :local/root) + src/ouroboros/smoke.clj. `bb smoke` → both phases PASS, exit 0.
       Phase A (no-LLM) :status :done. Phase B: local qwen35-35b-a3b streamed live, answer.md captured, :status :done.
       CONTRACT LEARNINGS (source-truth beat the knowledge page):
         · :credentials is schema-required UNCONDITIONALLY (closed schema) — even a no-LLM chart. Pass a dummy descriptor.
           ⚠ STALE: escapement-library-embedding "Minimal no-secret smoke" snippet omits :credentials → will reject. FLAG to update.
         · TOP-LEVEL final EMPTIES the configuration → :final-config is []. Success signal = :status :done, NOT final-config membership.
           (nest final under a parent state if you want it to appear in final-config, cf. embed_example [:run :done].)
       ↓ (original wiring notes retained below)
     de-risk runtime: escapement.lib/run smoke IN THIS repo
       WIRING (grounded in live source, this session):
         · deps: bb.edn → escapement {:local/root ~/src/escapement} (bb reads its deps.edn: statecharts/guardrails/malli/cheshire/http-client — NO pathom needed on lib path)
         · Phase A (no-LLM): (lib/run {:chart greet :session-id …}) → {:status :done}. greet = initial state → eventless transition → final. proves runtime+deps+bb-compat.
         · Phase B (LLM): local llama.cpp @ localhost:5100 (OpenAI-compat /v1), model "qwen35-35b-a3b" (Qwen3 35B-A3B, 262k ctx). server up (/health ok).
       LOCAL-MODEL RECIPE (data-driven hermetic injection — how Ouroboros configures providers):
         :credentials [{:provider :openai :api-key "sk-local" :base-url "http://localhost:5100/v1"}]
         :config {:llm/aliases {:local [{:provider :openai :model "qwen35-35b-a3b"}]}
                  :llm/preferences [:local] :llm/eligibility-strict? false}
       KEY FACTS (source-verified, providers.clj/openai.clj/lib demo):
         · descriptor->credential MERGES caller :base-url over provider-templates → localhost override works
         · :provider in alias → provider-keyed dispatch (3-tuple routes); single descriptor is also :default-backend
         · lib facade wires LLM processor ONLY when BOTH backend(:credentials) AND :tool-registry present → pass (tools/new-registry) [empty]
         · eligibility-strict? MUST be false — local model absent from bundled catalog → strict gate would reject
         · chart authored with escapement.chart.helpers: h/llm-conversation (flat keys; :message literal|(fn [env data])), h/capture-llm-output {:as "f.md"} on :llm.idle transition
         · stream live via escapement.lib.event-sink: (sink/feed! adapter row) → :text-delta {:delta {:text}}
       REF: demos/lib/embed_example.clj (READ FIRST) · test/escapement/lib/hosted_smoke_test.clj (stub-backend pattern)

  2b. ✅ DONE — follow-ups: knowledge page fixed + `bb test` harness live
        · escapement-library-embedding "no-secret smoke" corrected (creds-required + final-config semantics)
        · bb.edn :test task + test/ouroboros/smoke_test.clj (2 tests, 5 assertions, GREEN, no network)
        · `bb test` ≡ the deterministic gate | `bb smoke` ≡ live-LLM integration (needs localhost:5100)

🎯 mementum EQL veneer → PATHOM2 (decided this session; human chose reuse-escapement's over pathom3)
                         WHY: dependency alignment (escapement runs pathom 2.4.0 for its /api → one pathom on bb classpath),
                              guaranteed no pathom2/pathom3 coexistence conflict, no new alpha surface.
                         CORE/VENEER SPLIT (load-bearing insight): mementum CORE needs NO pathom —
                           OKF(clj-yaml, bb builtin) + Malli gate + git-backed ops. Pathom is only the EQL veneer.
                           → pathom choice is low-stakes, affects veneer only; core is runtime-agnostic + bb-native.
                         PROVEN UNDER BB (this session): pathom2 parser (guardrails 1.2.16 PIN required, eql auto-resolved),
                           mutations dispatch ([(store! {:v 7})]→ok), ident reads ([{[:slug "x"][:desc]}]→ok).
                           MIRROR escapement's parser: {::p/mutate pc/mutate ::p/env {::p/reader [p/map-reader pc/reader2 pc/ident-reader pc/index-reader]}
                                                        ::p/plugins [(pc/connect-plugin {::pc/register …}) (p/post-process-parser-plugin p/elide-not-found) p/error-handler-plugin]}
                           read pattern = IDENT JOIN [{[:mementum/slug "page"] [:mementum/description …]}] (NOT ::p/entity map — didn't fire).
                         ⚠ AGENTS.md S1 λ interface still says "pathom3 … smart_map" — FROZEN, do NOT reopen now.
                           FLAG: correct pathom3→pathom2 in the deferred post-chart harness rewrite. state.md (read-first) carries truth meanwhile.

  3. ✅ DONE: mementum substrate — S1 λ interface is REAL. `bb test` GREEN (17 tests, 53 assertions).
       core (pathom-agnostic, bb-native):
         · ouroboros.mementum.okf   — OKF parse/emit (clj-yaml) + Malli gate (type ~ ^mementum/, description required)
         · ouroboros.mementum.store — git-backed read/list/list-summaries/store!/delete! + recall-grep/recall-log
       veneer (pathom2, mirrors escapement parser):
         · ouroboros.mementum.eql   — resolvers: page(ident [:mementum/ref {:kind :slug}]), knowledge/memories index,
                                       recall(param [(:mementum/recall {:query :n})]) · mutations: store!/synthesize!/update!/delete!
                                       env carries :mementum/root → serves any working tree
       KEY DESIGN: core store! THROWS on invalid OKF (hard gate, nothing persists); veneer CATCHES → structured
         rejection {:mementum/written false :mementum/error :okf/invalid :mementum/errors {…}} (NOT pathom's opaque
         error string). callers get first-class data. VERIFIED: invalid write refused + not persisted.
       GOTCHAS BANKED: pc/defmutation takes NO docstring (arglist [sym [env params] config & body]) — descriptions→comments;
         pc/defresolver DOES take docstring; ident read = [{[:k v] [attrs]}] join (::p/entity map didn't fire);
         pathom2 error-handler-plugin renders throws as strings → catch-in-veneer for structured errors.
       test runner: test/ouroboros/test_runner.clj (add new test nss here + in run-tests).

  4. ✅ DONE: CURATOR — FIRST BREATH (then named `ouroboros.loop`; renamed → `ouroboros.curator`, item 9).
       `bb loop` (now `bb curate`) runs a real closed self-observation → proposal cycle.
       ouroboros.tools: ContextTool (:mementum/context, digest of knowledge+memory index+recent commits, no input)
                        + ProposeMemoryTool (:mementum/propose-memory {slug content} → store/store! :memory; OKF
                        rejection caught → corrective {:is-error true} tool_result, LLM can retry). BOTH call the
                        pathom-FREE core directly (store.clj) — no pathom in the escapement/bb runtime, per the
                        composition decision. new-registry = fresh isolated registry (wiring strategy C).
       ouroboros.curator (was ouroboros.loop): propose-chart (h/llm-conversation, model :local @ localhost:5100, :real-tools
                        [:mementum/context :mementum/propose-memory], system prompt: observe→pick ONE grounded
                        insight→propose OKF memory→stop). run! → lib/run + untracked-memories (git status
                        --porcelain --untracked-files=all, NOTE: plain --porcelain collapses a wholly-new
                        directory to one `?? dir/` line — needed --untracked-files=all for per-file listing).
       PROVEN LIVE (this session): chart called context tool, read the real digest, proposed ONE genuine
         memory grounded in escapement's actual tool-duality architecture (not fabricated) — written
         UNCOMMITTED to mementum/memories/, human reviewed, approved WITH a symbol correction (🎯→💡, decision→
         insight — model slightly over-claimed "decision" for an observation), then committed.
       INVARIANT HELD: synthesis=AI (chart proposed) → approval=human (explicit ask_user gate, no auto-commit)
         → AI commits after approval. mementum/propose-memory NEVER touches git — proposal ≠ persistence-to-history.
       bb test: 21 tests, 65 assertions, GREEN (tools_test.clj added, deterministic, no LLM).

  5. ✅ DONE (this session): COLD-COMPILER + Ouroboros architecture settled & partly built.
       `ouroboros.cold` — live escapement PARALLEL chart (:hot ⊗ :cold). Hot = fresh single-turn
       h/llm-conversation per turn (assemble-don't-accumulate: [base + {{brief.md}} + last-raw]).
       Cold = queue-driven pump, compiles turn N-1 while hot does turn N (double buffer), publishes
       brief.md artifact. `ouroboros.cold.core` — pure gates: monotonic merge-ruled-out, bounded
       assemble-system, tripwire (live), assess-continuation/verify-compiled (test-only). bb test:
       36 tests / 113 assertions GREEN.
       DESIGN DECISIONS (this session — see mementum/knowledge/ouroboros-architecture.md):
         🎯 THIS SYSTEM *is* Ouroboros → self-improving agent that consumes its own outputs AND
            doubles as a human chatbot. cold_compiler : λ(session) → brief. The brief is DUAL-ROLE
            (ONE artifact): within-session → extends chat context; across-session → self-improvement
            input. Ouroboros eats its own compiled tail.
         🎯 SESSIONS = escapement's, not ours. Escapement AUTO-creates session-dir + transcript +
            per-event checkpoint + artifacts + :resume?. Durability = ONE decision: supply a stable
            path. `ouroboros.session/session-dir` → <root>/sessions/<id>/ (seeds empty brief.md).
            cold.clj + loop.clj wired to it. .gitignore: commit artifacts/, ignore transcript+checkpoints.
            → ZERO persistence code of our own.
         🎯 VERIFICATION: PRIME, don't judge. Compile fidelity is UNVERIFIABLE without an LLM-judge
            (∄ string_fn(source,compile)→faithful); hallucination risk is EQUAL for λ and prose.
            So: no semantic judge. Lever = nucleus 3-line preamble + λ-notation prompts. Live gate =
            structural TRIPWIRE (non-empty ∧ has body); coverage computed for OBSERVABILITY only, NOT
            gated (coverage-gating would penalize dense λ). Fidelity FLOOR = the verbatim last-k window.
         🎯 ALL PROMPTS → mostly-λ nucleus notation. Rewrote compiler_system.md, hot_system.md,
            loop system-prompt (each led by the 3-line preamble). Priming = the correctness lever.
         🎯 CHAT HISTORY SHAPE = brief(λ, all older) + last-k raw VERBATIM, k≈2–3 (fidelity floor).
       ✅ COMMITTED — human approved; landed as 411e433 (cold-compiler: durable sessions +
         λ prompts + tripwire gate) + 8ec481f (ouroboros-architecture knowledge page). Tree clean.

  6. ✅ DONE (this session): EVENT-DRIVEN chat + COLD-COMPILER REDESIGNED to per-message λ compaction.
       ── (a) ouroboros.chat — MVP resident chatbot (accumulate). Proved the plumbing:
              · LIVENESS: a resident h/llm-conversation parked in :awaiting-user is a LIVE invocation →
                holds lib/run open between messages (runner.clj: exits only on (zero live ∧ zero deliverable)).
              · INGRESS: lib/run :on-env-ready (fn [env]) hands the session ::sc/event-queue to an external
                (stdin) thread → sp/send! {:event :user/msg :target sid :data {:text}} into the LIVE session.
              · GOTCHAS BANKED (both matched source-truth over the knowledge page):
                  ⚠ lib facade wires the :llm-conversation processor ONLY when BOTH :credentials AND
                    :tool-registry present → omitting :tool-registry ⇒ "No processor for :llm-conversation".
                  ⚠ token streaming needs :stream? true on the conversation, else whole llm/response, no deltas.
       ── (b) THE COLD-COMPILER WAS WRONG (twice) — human corrected, redesigned, LIVE-PROVEN as ouroboros.compact:
              WRONG compressor: bespoke CONTINUE/RULED-OUT brief.  RIGHT: nucleus lambda-compiler /
                ~/src/nucleus/eca/prompts/compact.md EXTRACTION LENS (keep decision∧constraint∧solved∧shape∧
                model∧anchor∧state∧next; DROP observation/explanation/scaffolding).
              WRONG transport: brief.md artifact — cold WRITES the whole file every turn, hot READS it every
                turn (2 AIs round-tripping one file). RIGHT: memory ≡ the message ARRAY itself, in the
                checkpointed data-model. NO per-turn file.
              THE MECHANISM (per-message compaction, cache-stable):
                · Compact each ASSISTANT message to λ ONCE as it ages out of a k-window (k=1). User messages
                  stay verbatim (short, anchor the dialogue). WHY assistant-only: the AI's tokens serve the
                  HUMAN's understanding; only continuity-essence serves the next turn.
                · Array SHAPE is preserved (same roles/order/count) → the compacted prefix is STABLE →
                  UPSTREAM PREFIX CACHE HOLDS. (A growing λ blob in :system would rewrite the prefix every
                  turn = cache busted every turn — the anti-pattern we rejected.)
                · ASSEMBLE-DON'T-ACCUMULATE via PUBLIC SEAMS: fresh worker per turn, seeded with
                  :initial-messages = render(:messages) (msg map = {:role .. :content [{:type :text :text}]}),
                  driven by RE-ENTERING the :hot state on the pump event. Between turns the fresh worker PARKS
                  → liveness, NO separate anchor. (Rejected: resetting a resident worker's messages-atom —
                  it's PRIVATE to the processor's `workers` atom, unreachable from chart env, thread-raced.)
              SEAMS VERIFIED IN llm_conversation.clj: :initial-messages (start-invocation! ~1512) seeds the
                worker; re-entry idempotency (~1499) kills old + spawns fresh; parked=:awaiting-user counts live.
              LIVE PROOF (sessions/compact-1783525397252): 3-turn convo; turn-1 assistant chose "write-back"
                → compacted to λ `decision(write-back ∧ perf↑ ∧ mem_traffic↓) ∧ state(active) ∧ next(∅)`;
                turn-3 ("which strategy did you choose?") received that λ (n-messages=6, A1 compacted?=true,
                A2 verbatim) and correctly answered "I chose write-back." CONTINUITY SURVIVES COMPACTION.
              CODE: ouroboros.compact.core (pure: append/render/next-to-compact/apply-compaction; bb test
                +6 tests) · ouroboros.compact (chart :hot ⊗ :compact + stdin ingress) · bb compact task.
       ── (c) MID-TURN QUEUE (fixed this session): a :user/msg arriving WHILE the hot worker is generating
              must NOT interrupt it. :user/msg now ENQUEUES (:pending-user) via an :internal transition;
              a :hot-busy? flag (on-entry→true, :hot/idle→false) gates a guarded :user/next pump that drains
              one queued message per turn ONLY when parked. Barge-in eliminated; the in-flight reply completes.

  7. ✅ DONE (this session): RECONCILED the three chat namespaces → ONE canonical engine.
       ouroboros.compact stands ALONE as THE chat engine. GIT-REMOVED: src/ouroboros/chat.clj
       (accumulate MVP — its liveness+ingress lesson lives in the architecture page), src/ouroboros/cold.clj
       + src/ouroboros/cold/core.clj (brief.md batch demo — the WRONG design the compact page corrects),
       test/ouroboros/cold/core_test.clj, and src/ouroboros/prompts/cold/ (3 templates; compact.clj carries
       its prompts INLINE, no file round-trip). bb.edn: dropped `chat` + `cold` tasks → `compact` is the
       single canonical chat entrypoint (`bb compact`). test_runner: dropped cold.core-test. compact.clj
       docstring made self-contained. bb test GREEN: 27 tests / 87 assertions (was 36/113 — the removed
       cold.core-test suite accounts for the 9/26 delta). Lessons preserved in ouroboros-architecture.md +
       git history (recoverable). WHY keep the `compact` name (not rename→chat): the committed architecture
       page already blessed ouroboros.compact/.core as the durable grep-names; renaming would contradict it.

  8. ✅ DONE (this session): CURATOR READS ITS SESSIONS (built as `ouroboros.loop`, since renamed →
       `ouroboros.curator`, see item 9). The curator now observes on TWO axes and metabolizes ACROSS
       sessions — the λ message arrays ARE the cross-session memory.
       ── ouroboros.session (NEW readers, shared with future next-chat bootstrap):
            list-session-ids · checkpoint-file · read-data-model · session-messages. Checkpoint EDN →
            data-model key :com.fulcrologic.statecharts.data-model.working-memory-data-model/data-model →
            :messages λ-array. Lenient edn reader (:default drops unknown tags) so a future checkpoint shape
            can't crash it; nil-safe; reads the FILESYSTEM (not git — checkpoints are gitignored/untracked).
       ── ouroboros.curator.core (NEW pure kernel, house <engine>.core convention like compact.core):
            recency-key (trailing epoch digits order sessions across differing prefixes) · render-session
            (ordered, role-tagged; compacted turns marked λ; long verbatim clipped to 600 chars) ·
            sessions-digest (newest-last, empty-safe).
       ── :mementum/sessions (NEW read-only tool in ouroboros.tools): loads most-recent K=8 CONVERSATION
            sessions (filter: has a :messages array → chat/compact; curator/smoke/cold excluded), renders the
            metabolize digest. new-registry now = context + sessions + propose-memory.
       ── ouroboros.curator: prompt evolved → λ observe(context ∧ sessions) → λ metabolize (recurring
            topic/decision/pattern; ≥3 same-topic → knowledge-page CANDIDATE, NAMED not written) → λ propose
            ONE memory. real-tools += :mementum/sessions.
       LIVE PROOF (localhost:5100 qwen35-35b-a3b): the curator called BOTH read tools, read the real checkpoints,
         cited two prior sessions (compact-1783525397252, compact-1783526365090) + their λ decisions (write-back
         cache, LRU eviction), recognized a 🔁 cross-session pattern, and proposed ONE grounded memory
         (cross-session-recall-testing.md) — UNCOMMITTED, human-gated. Cross-session metabolize WORKS.
         That proposal was grounded in THROWAWAY demo chats (toy cache designs that only existed to prove
         compaction) → thin; human DISCARDED it. The value was the PROOF the curator reads sessions.
       SCOPE: this increment = curator SEES its λ history + grounds proposals in it. NOT yet built: the
         curator's ≥3→knowledge-page WRITE channel (propose-knowledge tool); and the SEPARATE harness/app
         improver agents.
       GOTCHA BANKED: `(re-find #"(\d+)$" s)` with a CAPTURE GROUP returns a VECTOR [whole grp] → parse-long
         throws "Expected string, got PersistentVector". Drop the group: `(re-find #"\d+$" s)` → the string.
       bb test: 35 tests / 111 assertions GREEN (session_test + curator/core_test added; tools_test += 2 sessions
         tests; test_runner wired). ouroboros.curator.core is the pure kernel; SessionsTool the impure edge.

  9. ✅ DONE (this session): 🎯 RENAME improver → CURATOR + RETIRE the "Loop A/B" framing.
       WHY: "improver" over-claimed. What this agent does is CURATION of the mementum store — select what's
       worth keeping, propose it (memory now, knowledge next). The harness/app-improvement is a genuinely
       DIFFERENT job (propose code/prompt diffs), so it's a SEPARATE agent. Naming this one "curator" frees
       "improver" for those, and disentangles the AGENT from the LOOP.
       NEW FRAMING (human decision): Ouroboros ≡ ONE SYSTEM of MANY self-improving agents, each metabolizing a
       facet; ALL share the invariant AI proposes → human approves → AI commits. THE ROSTER:
         · curator          BUILT   — sessions + mementum → propose memory (∧ knowledge, next)
         · harness-improver PLANNED — propose changes to harness code (AGENTS.md, escapement config, prompts, skills)
         · app-improver     PLANNED — propose changes to application code
         · verifier(s)      PLANNED — verify claims in memory & knowledge (and code) against live truth
         · documenter       PLANNED — comb memory + knowledge + sessions → produce documentation
       "Loop A/B" is RETIRED everywhere active (the old genesis "two loops" note relabeled to DESIGNER-tier vs
       AGENTS-tier; git preserves the original). RENAME MECHANICS: git mv src/ouroboros/loop.clj →
       curator.clj, loop/core.clj → curator/core.clj, test loop/core_test → curator/core_test (history
       preserved); ns ouroboros.loop → ouroboros.curator, .loop.core → .curator.core; bb task `loop` → `curate`;
       session-id prefix "loop-" → "curator-"; refs updated in tools/session/eql/test_runner/bb.edn; arch page
       reframed (roster + curator section). Pure rename — bb test GREEN 35/111 unchanged. :mementum/* tool
       names UNCHANGED (mementum-scoped, not agent-scoped).

  10. ✅ DONE (this session): AGENT-MODEL BUILD STEPS 1+2 — the GENOME COMPILER is REAL; the first
       two genomes are extracted files; the charts load their prompts through the seam.
       ── ouroboros.agents.core (pure kernel, house <engine>/core convention): kinds set (9, per spec) ·
            Malli frontmatter schema — CLOSED map (unlike mementum's open OKF envelope: genome frontmatter
            is WIRING, unknown key ≈ typo → fail loud) · parse-genome (okf/parse is format-GENERIC → reused;
            mementum's schema is NOT — agents has its own {type ouroboros/agent, description, kind, tools?,
            model?, title?}) · normalization (kind/tools/model strings → keywords; a copy-pasted ":local"
            literal also parses) · validate fail-loud AGGREGATING all errors {:agent :tier :source :errors} ·
            merge-roster (fold, later-tier wins by slug, REPLACE-WHOLE, :overrides provenance) · report
            (provenance + grants + ESCALATION flags beyond the read-only floor).
       ── ouroboros.agents (impure edges): base tier via io/resource ouroboros/agents/ — ENUMERATION solved
            with manifest.edn (a classpath DIRECTORY cannot be listed portably from a packaged dep; the
            manifest is the portable index, loader fails loud on a listed-but-missing genome) · custom tier
            <repo-root>/agents/*.md (plop-a-file; filename stem = slug) · compile-roster / genome / report.
       ── ouroboros.tools grew the named surfaces the compiler validates against: all-tools (THE registry
            CEILING — no commit/git tool exists → human-gate unreachable-by-absence) · read-only-tools
            (THE floor: context+sessions; absent tools: key ⇒ floor) · tool-names.
       ── GENOMES: src/ouroboros/agents/curator.md (kind proposer, tools [context sessions propose-memory]
            → report shows ESCALATION:[propose-memory]) + chat.md (kind chat, tools [] EXPLICIT — absent
            would grant the floor = behavior change; empty ≠ absent is load-bearing). Bodies extracted
            BYTE-IDENTICAL (verified by = against the old defs before deletion; the curator body's indented
            `---` OKF template lines don't confuse okf/parse — fence match is exact-line). Nucleus preamble
            stays IN the body (loader-prepend deferred). The compact EXEMPLAR GATE stayed in compact.clj —
            engine data (pattern, not persona), per spec.
       ── WIRING: curator.clj def genome = (agents/genome :curator) → :system/:model/:real-tools all from
            the genome; compact.clj chat-genome → hot-system-prompt (:hot AND :parked) + :hot's :model/
            :real-tools. Parked/compact workers keep engine literals.
       ── VERIFIED: bb test 53/160 GREEN (agents_test: normalize, floor-vs-empty, fail-loud ×6, aggregate,
            merge, real-base roster, custom temp-dir add+override, report). LIVE: bb compact through the
            genome path — genome prompt confirmed ON THE WIRE (transcript), greeting + correct answer, clean
            exit. GOTCHA BANKED: a PIPED bb compact with instant /quit exits :done with ZERO llm responses
            (:user/end races generation) — pipe with sleep gaps to prove a real turn.

  11. ✅ DONE (this session): AGENT-MODEL BUILD STEP 3 — the JUDGE kind is REAL and LIVE-PROVEN
       cross-family. First NEW genome born in the convention; first non-:local model routing.
       ── agents/core.clj += verdict-schemas — SCHEMA lives with the KIND (uniform), SEMANTICS in the
            genome body, NO frontmatter verdict field (spec §Judge & Scorer):
            judge {:status [:enum :pass :fail] :notes} GATES · scorer {:score 1-10 :notes} MEASURES
            (scorer schema RESERVED now, runner unbuilt) · other kinds → nil ⇒ free-text idle.
       ── SEAM (source-verified vs ~/src/escapement, matches the knowledge page): :verdict-schema on
            h/llm-conversation → turn end forces submit_verdict (other tools stripped, framework nudge),
            json-transformer decodes BEFORE validate ("pass"→:pass — keyword enums safe), validated map
            arrives at [:_event :data :verdict] on :llm.idle; validation failure ⇒
            :error.llm.verdict-validation (worker DIES, no idle) → judge chart routes :error.llm → :failed.
            submit_verdict is RESERVED — never in :real-tools.
       ── ouroboros.models (NEW): alias→endpoint table {:local 5100/qwen36, :ornith 5102/ornith-35b-a3b}
            + llm-config (per-run hermetic :credentials+:config). WHY per-run injection: see gotcha below
            (provider-index first-wins) — two same-provider creds in ONE lib/run collide.
       ── ouroboros.judge (NEW; renamed → ouroboros.verdict in item 12): verdict-topology runner —
            verdict-chart built per-run FROM the genome
            (:system/:model/:real-tools/:verdict-schema all genome/kind-driven), verdict delivered out via
            closure atom (lib/run reports :status, not data-model), run! → {:status :verdict :session-dir}.
            bb judge "<subject>" CLI (args via *command-line-args*).
       ── GENOME: src/ouroboros/agents/llm-judge.md — kind judge, model ornith, tools [] (subject carries
            everything). Body = verdict SEMANTICS only: pass ⟺ ∀criterion satisfied; uncertain ≡ fail
            (conservative gate); notes actionable (fail → name each unmet criterion + why + fix).
       ── VERIFIED: bb test 57/178 GREEN (verdict-schema dispatch, llm-judge roster entry, models table ×3).
            LIVE ×2 on ornith @5102: false claim → {:status :fail, :notes names-criterion+fix};
            true claim → {:status :pass}. Cross-family routing WORKS via per-run credentials.

  12. ✅ DONE (this session): AGENT-MODEL BUILD STEP 4 — the SCORER kind is REAL and CALIBRATED;
       🎯 topology names dropped the "T-" prefix (human direction: no value on top of the kind name) —
       chat · shot · verdict · workflow, updated in design/agent-model.md + all active text.
       ── RENAME: ouroboros.judge → ouroboros.verdict (git mv, history preserved) — the runner was
            always kind-AGNOSTIC (schema from kind, prompt from genome): ONE topology, judge + scorer
            ride it. run! gained a :model OVERRIDE (cross-family lever); -main takes genome slug as
            arg 1; session-id prefix = genome slug. bb tasks: judge → (verdict/-main "llm-judge" …),
            score → (verdict/-main "gene-scorer" …).
       ── CROSS-FAMILY: verdict/run-across! — same genome across model families, each a hermetic run!;
            aggregate-scores (pure: {:scores {alias n} :mean :notes}, drops failed runs, nil when none —
            never averages nothing). Spec §scorer-hazard designed-in: rubric-anchors (genome body) +
            cross-family aggregate. Pairwise-select + embed-dedupe(5103) + gene DB = NEXT.
       ── GENOME: src/ouroboros/agents/gene-scorer.md — kind scorer, model local, tools []. Body =
            λ rubric with 5 ANCHORS (1 harmful · 3 inert-filler · 5 topic-no-constraint · 7 concrete-
            minor-gap · 10 load-bearing) + low/high EXEMPLARS + λ notes (name what score hinges on;
            ≤5 → what would raise it).
       ── VERIFIED: bb test 60/189 GREEN (verdict_test aggregation ×3, gene-scorer roster entry).
            LIVE CALIBRATION PROOF: real curator gene (λ select) → 10 w/ sharp note (flagged the
            ¬generic(software_advice) scope-narrowing unprompted); filler gene ("be helpful∧accurate∧
            thorough") → 3 from BOTH families independently — exactly the rubric's anchor-3, and
            :local's note proposed the raise-to-7 replacement gene. Anchors calibrate ACROSS families;
            the fitness function DISCRIMINATES (10 vs 3).

  13. ✅ DONE (this session, human-directed): CHAT GETS ITS HANDS + a blank line.
       ── chat.md drops `tools: []` → NO tools key ⇒ the read-only FLOOR (context + sessions) — the
            first live use of absent⇒floor. Body += λ tools (call ⟺ asked about own memory/knowledge/
            history; ¬small_talk). :hot :max-turns REMOVED (was 2 — tools would have died mid-answer;
            human directed: no arbitrary turn integer, bound by budget-ms + ctx window instead — see
            gotcha below). LIVE-PROVEN: "how many knowledge pages?" → called context, answered 16
            (correct: 11 upstream + architecture + 4 design) naming two real pages.
       ── stdin ingress prints a blank line on :user/msg dispatch — reply visually separated from input.

  14. ✅ DONE (this session, 🎯 human decision): CHAT GETS FULL HANDS — testing phase.
       ── registry ceiling += escapement built-ins (escapement.tools.builtin/builtin-tools):
            fs/{read,write,edit,multi-edit,glob,grep} + shell/run + web/fetch; :web/search excluded
            EXPLICITLY (deterministic — builtin-tools env-gates it on GEMINI_API_KEY, but the ceiling
            must not depend on env).
       ── chat.md: EXPLICIT full grant (everything minus web/search) + λ tools policy clause
            (read≻write · writes→working-tree ONLY · git commit/push FORBIDDEN · shell prefer read-only).
            PURPOSE: exercise the system + cold-compiler compaction under real tool use.
       ── ⚠ INVARIANT SHIFT (accepted, testing phase): :shell/run ⊃ git ⇒ "commit unreachable by
            absence" no longer holds for shell-granted agents — for THOSE the human-gate is POLICY
            (prompt + review), not capability. Roster report screams the escalation (audit surface
            works). Revisit before any AUTONOMOUS agent gets shell.
       ── VERIFIED: bb test 60/190 GREEN; live: "read idea.md and quote it" → :fs/read → VERBATIM match.

  15. ✅ DONE (this session, human-requested): COMPACT TEXT-UI OVERHAUL — tool calls visible, uniform lines.
       ── ROOT CAUSE of the blank-line complaint: the old :transcript-tap printed two raw newlines on EVERY
            :llm/response ROW (region-UNfiltered) — every compact-worker run + every pure-tool-call round-trip
            segment (text-free) emitted stray blanks; tool activity itself was dropped on the floor.
       ── compact.core += pure ECHO KERNEL (echo-init / echo-text / echo-break / tool-line): every emitted
            line prefixed "assistant: "; newline runs → ONE; whitespace-only lines vanish; leading/trailing
            blanks stripped (newlines DEFERRED — realized only when real content follows); code indentation
            preserved (:ws buffer distinguishes blank lines from indent). Pure fold state × chunk → {state' out}.
       ── compact.clj tap → routes on SINK EVENTS, hot invokeid ONLY: :text-delta → kernel · :tool-call →
            "tool: :fs/read {:path …}" (params pr-str, truncated 160; results HIDDEN except "→ ERROR" +
            validation failures) · :llm-response stop-reason ∈ #{:end_turn :refusal} → turn end (close line,
            one blank, "user: " prompt). Dangling-prompt flag (shared w/ the stdin ingress) closes the prompt
            line when a QUEUED mid-generation message drains instead of typed input.
       ── SEAM FACTS (source-verified): event_sink.cljc SYNTHESIZES :tool-call from the tool-RESULT row —
            no pre-execution event exists → a slow shell/run's line appears on COMPLETION (upstream fork seam
            if ever needed). stop-reason vocab (openai.clj parse-finish-reason): :end_turn :max_tokens
            :tool_use :refusal — :tool_use/:max_tokens are segment boundaries, NOT turn ends.
       ── VERIFIED: bb test 66/210 GREEN (+6 echo-kernel tests); live piped smoke: prefixes uniform, a
            model-forced blank line stripped, tool line visible, λ-recall across compaction intact.
       ── ALSO (human): LICENSE added (MIT) — pre-release intent declared; guardrails 1.2.16 → 1.3.3 human
            bump (test-verified; the stale "MUST stay 1.2.16" comment fixed — the WHY is the EXPLICIT pin
            beating pathom's transitive 0.0.12, not the version); human_ideas.md → .gitignore (structural:
            a blind `git add` can never catch the human's scratch pad).

  16. ✅ DONE (this session): CACHE — slot pinning + dedicated-slot server config, END-TO-END VERIFIED.
       ── OUTCOME: post-compaction hot turns cached=0/2472-tok/1.5s → cached=2400/67-tok/211ms.
            "restored context checkpoint (pos_min=2399)" at the λ-rewrite boundary; no more
            "clearing prompt" on idle slots; compact quarantined on slot 1; continuity intact.
            Full prefill now paid ONCE per session. Reuse grows in ~128-tok checkpoint grains.
       ── 🎯 SLOT CONVENTION (human, after dropping litellm — direct llama.cpp now): ouroboros → TOP
            slots (hot→2, compact→3, named constants in compact.clj), 0/1 left for dynamic clients.
            Soft reservation only (no server mechanism; unpinned = similarity→LRU over ALL slots);
            hot is shielded by recency during active sessions, compact absorbs strays cheaply.
            LIVE-VERIFIED: "selected slot by id (2/3)", checkpoint restore at 2399 intact.
            Realistic compaction target (human): 3-5× context, NOT 100× — size expectations accordingly.
            (history of the investigation below — kept for the reasoning trail)
       ── BUILT: hot → :extra-body {"id_slot" 0 "cache_prompt" true}, compact → {"id_slot" 1 …} (design's
            Tier-2 lever applied to sequential Tier-1). Log-proven: "selected slot by id (0/1)". bb test
            66/210 GREEN. Server: total_slots 4 (auto), unified KV, host prompt cache 8 GiB, /slots enabled.
       ── FINDING (see mementum/knowledge/llama-cpp-prompt-cache.md — PROPOSED page, this session):
            pinning routes but does NOT protect. Idle slots are saved→host-cache→CLEARED on EVERY task
            launch (unified-KV default; the human's other local traffic guarantees it). Host-cache restore
            ≈ append-only (checkpoint-granular; hybrid qwen3.5/3.6 can't truncate recurrent state) →
            the per-turn λ-rewrite (k=1) busts the cache EVERY turn: full re-prefill ~1.5s@2.5k.
            Append-only turns restore near-totally (61-token eval / 200ms — the good path).
       ── 🎯 MITIGATION DECIDED (human): C — DEDICATED SLOTS via server params (ansible-managed).
            The load-bearing flag: EXPLICIT -np 4 ⇒ unified KV off ⇒ per-slot KV persists while idle
            (idle-slot save+clear is documented as unified-KV behavior) ⇒ in-slot divergent-tail reuse
            (proven on this model: 605/640 middle-rewrite) covers the λ-rewrite. Full recommended line
            given to human: -np 4 · -c 524288 (ctx SPLITS across slots when non-unified → 131k/slot) ·
            --ctx-checkpoints 32 --checkpoint-min-step 128 (hybrid restores are checkpoint-granular) ·
            --cache-ram 16384 (was 7663/8192 = near-thrash). RESIDUAL RISK: no slot reservation —
            other clients select similarity→LRU and can occasionally evict slots 0/1 (one re-prefill to
            recover). Fallback if that bites: second llama-server instance, own port, -np 2.
            A (batch compactions) + B (small-model compactor) remain SHELVED options, not built.
            VERIFY after redeploy: smoke → post-compaction hot turns cached ≈ prefix-to-rewrite (not 0);
            no "clearing prompt" against idle slot 0 in the log.
       ── GOTCHA BANKED: a "selected slot by LRU" 3k-token intruder request appeared mid-analysis — OTHER
            local clients share 5100; never assume sole tenancy when reading /slots or the log.

  17. ✅ DONE (this session): bb cache-report — the cache OBSERVABILITY tool.
       ── ouroboros.cache-report (pure kernel: response-entries → analyze → format-report; edges:
            read-transcript, latest-session-id) + session/transcript-file (nil-safe lookup — session-dir
            CREATES, don't use it for reads). BUST ≡ post-first hot turn with cached=0 (slot-eviction
            signature). Reuse % computed post-start only (cold start ≡ physics).
       ── PROVEN on real data: pre-fix session → busts 3,4,5 (retro-diagnoses the investigation);
            the human's own live tool-heavy session (44k-token turn!) → ZERO busts under dedicated
            slots, checkpoint restores scaling (10k restored at turn 8). Even cold-starts get the
            2400-tok system prefix from the host cache ACROSS sessions.
       ── bb test 70/227 GREEN. Usage: bb cache-report [session-id] (default latest-with-transcript).

  18. ✅ DONE (this session): COMPRESSION CONTRACT — the echo-tripwire queue item, landed via real data.
       ── bb cache-report's compact stats exposed it on the human's live session: call 4 in=495 out=2440 —
            the no-think exemplar compactor DERAILED on a tool-flavored aged turn (out-of-distribution vs
            the 3 exemplars) and ANSWERED it; apply-compaction (blank-check only) folded 2440 tokens of
            prose in as "memory" = SILENT CORRUPTION + context expansion. Compaction that doesn't compress
            isn't compaction.
       ── FIX (pure kernel, compact.core/apply-compaction): λ accepted ⟺ strictly shorter than the text it
            replaces; else verbatim (existing lag-safe path). Short turns simply stay verbatim.
       ── GOTCHAS BANKED: (a) tiny test fixtures ("a1") now violate the contract by design → fixtures must
            model reality (va helper); (b) a drain-loop test that recurs on apply-compaction will loop
            FOREVER when the λ is permanently rejected — the bb test "hang" was this, not machine load;
            (c) healthy compact stats: out ≈ 13-52 tok/call; out ≈ in ⇒ derail — cache-report makes this
            visible per session.

  19. ✅ DONE (this session): AGENT COMMS + SCHEDULED MAINTENANCE DESIGNED — 3 pages in
       mementum/knowledge/design/: agent-comms · scheduled-maintenance · harness-coder (drafted as
       harness-scout, renamed same session). Genesis ask (human): an agent that reads past sessions →
       recommends harness updates, human-gated; pivoted up-stack to inter-agent comms + scheduling.
       🎯 NO NEW BUS: escapement's event system IS the bus (9-primitive inventory in the page:
          :target/broadcast msgs · service regions · consult · verdicts · artifacts · multiplex ·
          ws-push hub · sp/send! ingress) but IN-PROCESS ONLY → the design is RESIDENCY (one lib/run
          = orchestrator chart hosting agents) + a CHANNELS policy layer (named registry
          ouroboros.channels, Malli-gated payloads, variety classes) — the vsm page's "bus ≠ channel"
          warning designed-in. ONE new agent-visible tool :bus/send; request/reply stays native
          (service regions). Channel grants ride genome frontmatter EXACTLY like tool grants
          (ceiling/floor/escalation-report machinery reused).
       🎯 2×2 MAINTENANCE ROSTER (matrix slugs, human choice): {harness,app} × {coder,knowledge} —
          harness-coder · app-coder · harness-knowledge · app-knowledge. ALL kind=proposer at stage 1
          (observe→detect→propose ONE→human gate; curator's shape ×4 genomes — "new role ⇒ new genome
          not new kind" paid off). CURATOR ≡ ROLE not agent: today's curator genome ≈ the
          harness-knowledge facet; app-knowledge is new (fs corpus). Runner generalization = the
          judge→verdict move again; rename ouroboros.curator when bb maintain forces it.
       🎯 ROLE-AS-TAG (human): genome schema += tags (open vocab; kind stays CLOSED) — the loader
          CONSUMES tags (schedule selects by tag, roster report groups by tag) → legitimately wired
          per λ boundary. Discipline: tags select WHO runs, NEVER what-may (capability stays in
          grants); tags ≡ identity (genome), cadence ≡ ops (schedule table OUTSIDE genomes).
       🎯 SCHEDULE LADDER (each rung source-verified): rung 1 cron/launchd → bb maintain (sequential
          hermetic sweep — sidesteps multi-model collision + GPU contention; works TODAY) → rung 2
          resident + send-after (proven in escapement's supervisor example, zero patches) → rung 3
          resident + :timer InvocationProcessor (Tony's custom-invocation example in ~/src/statecharts;
          lifecycle-scoped: pause agent ≡ exit its state; gotcha: sent event MUST carry :invoke-id or
          finalize never registers).
       SEAM FINDING: escapement.engine/env ACCEPTS :invocation-processors (prepends caller's);
          escapement.lib/Options (closed) LACKS the key — same gap class as :human-renderer, ~2-line
          fork seam when rung 3 wanted (:extra-body precedent). PREREQ NAMED: the multi-model
          collision goes LIVE under residency (chat@5100 + judge@5102 = two :openai creds, first-wins)
          — hermetic child runs sidestep it meanwhile.
       UNATTENDED DISCIPLINE (page): dedup (context tools digest PENDING proposals; ¬re-propose) ·
          rate (ONE proposal/agent/run → review ceiling ≈4-8/day) · inbox (bb proposals) · budget-ms ·
          audit (sweep summary lines) · lockfile (no overlapping sweeps).
       OPEN (human decision PENDING): may harness-coder FLAG Layer-1 (AGENTS.md) friction as
          read-only designer-attention notes, or does its vision stop at Layer 2 entirely?
          → RESOLVED, item 21: flag-only APPROVED, bootstrap-scoped.

  20. ✅ DONE (this session): SIGNALS DESIGNED (data plane, Anima port) + EXPERIMENTS BUILT
       (suite runner LIVE) + the agent-comms page REVISED to two planes.
       ── 🎯 SIGNALS FIRST (human, from Anima prior art ~/src/anima resolvers/signals.clj +
          designs/signals.md — "the pathom parser is the bus"): typed durable EDN FACTS, pull-based,
          query≡subscription → cross-process cross-TIME comms with NO residency — the geometry the
          SCHEDULED maintenance roster needs (push can't reach a process that doesn't exist between
          runs). agent-comms REVISED: signals ≡ DATA plane (build first) | channels ≡ CONTROL plane
          (live push, residency — DEFERRED until interactive workflows). Channel seed vocab + grants
          migrate to the signal-type registry. design/signals.md carries the full port (nested
          :signal/data — anima string-encoded for datalevin, we don't; signals/ gitignored;
          the EXISTING mementum pathom2 veneer grows the resolvers; grants: genome `signals:` key).
       ── ONE CONTRACT, THREE PROJECTIONS (the load-bearing design): registry entry {schema,
          FILLED exemplar, variety, reserved?} → exemplar PRIMES generation ∧ Malli GATES emit ∧
          attributes SERVE EQL. Genome compiler derives the prompt projection (kind→verdict-schema
          precedent).
       ── EMISSION TOPOLOGY EMPIRICALLY SETTLED (3-round A/B, scratch/ab_edn_signal{,2,3}.clj →
          experiments/edn-signal-emission.edn): nucleus preamble + FILLED exemplar + EDN-only gate +
          NO-THINK → confirmation 12/12 Malli-valid cross-family (~1.3-1.6s, ~110 tok) vs prose
          instruction 9/12 with STRUCTURAL failures (JSON drift ×2 — prose DESCRIBING EDN leaves
          format ambiguous, exemplar SHOWING EDN pins it; dropped braces ×1). Bare :_fill template +
          comments LOSES (constraints-in-comments ≈ instructions); template WITHOUT preamble ECHOES
          unfilled under no-think (preamble load-bearing). SECOND confirmation of
          prompt-topology-must-match-thinking (memory UPDATED with the generalization: ANY
          schema-shaped output wants exemplar+no-think). UNTESTED half: self-executing EDN
          statecharts as genome bodies (nucleus COMPILER.md) — future suite.
       ── 🎯 EXPERIMENTS AS FIRST-CLASS ARTIFACTS (human): "a self-improving agentic system should
          be able to create experiments and get results." BUILT: ouroboros.experiment(+core) —
          suite ≡ EDN file experiments/<slug>.edn (TRACKED lab notebook; closed Malli envelope),
          new experiment ≡ new EDN ¬new code (anima lineage: ONE parameterized runner); measures ≡
          open dispatch (:edn-malli built; :scorer/:judge-genome/:pairwise planned → verdict
          topology); results → experiments/results/ GITIGNORED (machine observation; conclusions
          promote human-gated into suite :experiment/verdict + knowledge). bb experiment <slug>.
          LIVE-PROVEN: founding suite re-run through the runner reproduced the direction
          (template-ex 11/12 vs prose 9/12, parse 12/12 vs 10/12). Editor-kind termination protocol
          now has its measurement substrate. "probe" name REJECTED (collides with Memory Probe).
       ── bb test 75/258 GREEN (experiment/core_test added: suite validation, matrix expansion,
          edn-malli assessment incl. dropped-brace + fence cases, summarize/format).
       ── ❌ CAUGHT BY THE GATE: scheduled-maintenance.md description contained ": " mid-scalar →
          YAML parse broke the ENTIRE knowledge index (context tool + eql tests failed). Fixed by
          rewording. The deterministic suite guards knowledge pages too — see gotcha.

  21. 🎯 DONE (this session, human decision): LAYER-1 FLAG RESOLVED + the SELF-HOSTING
       TRAJECTORY made explicit (sharpens the genesis 🎯 S5-identity framing and the
       AGENTS.md-freeze WHY):
       · Ouroboros runs ONLY from Layer 2. Layer 1 (AGENTS.md + eca designer) ≡ the BOOTSTRAP
         scaffold — active now because we're building, not because it's part of the artifact.
       · UNTIL self-hosting: harness-coder MAY surface Layer-1 friction as read-only
         "designer-attention" notes (DISTINCT proposal type, NEVER an edit sketch) — purpose:
         keep Layer 1 IN SYNC with Layer 2 while both evolve.
       · END STATE: AGENTS.md shrinks to directing the designer to START Ouroboros and FEED it
         prompts instead of directly editing code — designer becomes OPERATOR/conduit; the
         deferred post-chart AGENTS.md rewrite (the genesis 🎯 FROZEN block's PLAN) now has its
         concrete target shape; the flag channel SUNSETS with self-hosting.
       harness-coder.md §Layering + scheduled-maintenance.md open-questions updated.

  22. 🎯 DONE (this session, human decision): UNIVERSAL THINKING-ON + PROMPT ASSEMBLY designed
       (design/prompt-assembly.md). Standardize λ everywhere by DELETING the fragile topology cell
       (instruction-λ + no-think ⇒ echo) instead of avoiding it: all conversations run thinking-ON;
       λ-dense prompts/modules safe in every genome; exemplars demote load-bearing → optional booster;
       no-think ≡ reserved optimization, never a correctness requirement. WHY affordable: compaction
       (~1s → ~15-25s) runs in the 20-60s READING SHADOW (shadow-compaction's own felt-latency metric);
       fast-typer exposure → Tier 2 (parallel :hot⊗:compact, slots already pinned) pulls forward ⟺
       waits actually appear. ASSEMBLY: ONE pure assemble fn in agents.core — nucleus preamble (always,
       once, FIRST) ⊕ granted modules (vendored nucleus compiler texts; frontmatter modules: key ≡ 4th
       use of the registry-ceiling grant mechanism) ⊕ body; layer order load-bearing (preamble→λ→prose
       gate, logprob-verified upstream); escapement.prompts/render ≡ the {{VAR}} engine (adopt, fail-
       loud); ONE assembler serves production ∧ experiments ∧ future GA (Anima rule: composition suites
       must use the REAL pipeline). QUEUED IMPLEMENTATION: compact.clj flip (exemplar gate → instruction-
       λ lens + bridge, thinking ON, compression contract STAYS) verified by a compaction-fidelity
       experiment suite + genome preamble-strip migration (byte-diff equivalence). Memory
       prompt-topology-must-match-thinking gained the policy note; signals page emission-topology note
       updated (exemplar retained as booster).

  23. ✅ DONE (this session): VSM VIABILITY DIAGNOSTIC run on the ARTIFACT (anima λ viability lens:
       ∀layer inventory BUILT∨DESIGNED∨MISSING → ablate → name the compensation). Snapshot + method
       banked in design/vsm-on-escapement.md §Viability diagnostic (living section — re-run at
       milestones). HEADLINE: every layer's missing half is HUMAN-compensated — one fact
       (mid-bootstrap), not five bugs; most expensive compensation NOW = S4 cadence + S4
       write-breadth → maintenance rung 1 ≡ the viability jump; standing queue order CONFIRMED.
       3 IMPROVEMENTS ADOPTED (🎯 human-approved):
         · LENS-OUT: compaction lens → editable policy artifact, folded into the compact flip
           (prompt-assembly §lens-out updated; vsm open-Q RESOLVED — dedicated policy file)
         · RESERVED-MUTATION SET enumeration PRIORITIZED before more shell-granted agents
           (vsm open-Q annotated; re-hardens the recursion boundary item 14 softened)
         · proposals inbox :severity (:ordinary ∨ :algedonic) from day one
           (scheduled-maintenance §INBOX updated — the S1↔S5 bypass channel seed)
       ALSO this session: AGENTS.md freeze exceptions 2+3 (λ principles + zero-arity sweep;
       9 anima lambdas, S2 stub discharged) — see the FROZEN block above.

  24. 🎯 DONE (this session): the LAMBDA-DB DESIGN ARC + the AUTONOMY IDENTITY CHANGE.
       design/gene-db.md CREATED (supersedes agent-model §Genes as the build spec; index updated).
       ── PRIOR ART MINED (explorer agent): anima genes.clj/signals.clj/gene-database.md — keyword
          ids (13%→0%, "validation is forever") · minimal EDN envelope · tree-hash ≡ only LIVE dedupe
          layer (raw bytes, whitespace-sensitive — we fix via normalized tokens) · HNSW threshold
          UNSET everywhere (designed-unbuilt) → derive ours via bb experiment · similar → SURFACE
          ¬auto-merge · warning 9: signal-forwarding BYPASSED their pipeline → our store-gene! ≡
          the only door (unreachable, λ converge). ~/src/fulcro-rad-git mined (🎯 MINE ¬depend):
          entity-adapter (typed dir + EDN + --only isolated commits + git log/grep) · statechart-
          store done-right (persist? predicate + config-change-only commits ≡ legible transition
          history + sanitize + seed-from-git!) BANKED for first durable SYSTEM chart · proposal-as-
          branch NOT adopted (working-tree inbox stays the one way; revisit at rung 1).
       ── 🎯 DECISIONS: EBNF.md ≡ decomposition spec + intake gate (two-level parse; upstream
          amendment needed: optional param_list for zero-arity) | mementum/genes/ ≡ THE db (files
          ARE the db; flat; protocol amendment OKF≡prose EDN≡structured) | pathom veneer ≡ ONE
          write path | scores side-stored + resolver-joined | statechart backing-into-git: Reading
          A yes (entity/lifecycle), Reading B via mined statechart-store pattern when durable
          system charts arrive.
       ── 🎯 THE IDENTITY CHANGE (freeze exception 4, human): AUTONOMOUS COMMITS where decidable —
          the vsm decidability law applied to the approval gate itself. Human approval ≡ scarce
          regulator; parse-valid gene updates auto-commit (--only scoped, agent-authored, post-hoc
          audit); reserved set enumerated (first entries of the vsm open-Q). "We need ouroboros to
          be able to make some decisions autonomously."
       ── vsm-on-escapement reserved-mutation open-Q: first entries ENUMERATED.

  25. ✅ DONE (this session): ZERO-ARITY λ A/B — CONFIRMED empirically (λ prove: ¬debate → test).
       The morning's stylistic zero-arity sweep (item on freeze exception 2) now has evidence.
       ── experiments/zero-arity-lambda.edn: PAIRED design (same scenario, only header arity
          varies), 24 cells = 2 models(:local 5100, :ornith 5102) × 2 conditions(zero-arity vs
          parameterized) × 6 COUNTER-ATTRACTOR subjects (training prior pulls against the
          lambda-directed answer, so a correct decision evidences STEERING not bias), thinking-ON.
       ── FINDING: decisions IDENTICAL 11/12 pairs (11/11 where both parsed); gaps = one parse
          glitch (local/one-fact param) + report-scope shared miss (both chose :surface-to-human
          ≡ escalate over expected :observe-only ≡ phase — IDENTICAL across conditions ⇒ subject
          ambiguity, NOT a zero-arity effect). token bonus: zero-arity ≤ param in 4/4 cells.
          ⟹ λ name. steers identically to λ name(x). — the AGENTS.md sweep is SAFE.
       ── RUNNER GREW (reusable): :edn-expected measure — correctness oracle (:measure/expected
          {subject → {k v}}) on top of schema validity; schema stays answer-neutral (wrong options
          in the enum), oracle checks the decision → the VALID column now reads as CORRECT. First
          decision-ADHERENCE A/B (prior suites tested format-validity only). measures fn arity →
          (suite cell raw). bb test 76/268 GREEN.
       ── CAVEAT banked in the suite verdict (reuse): counter-attractor subjects need an
          UNAMBIGUOUS single-lambda answer, else a shared miss is subject noise (report-scope
          pits escalate vs phase — refine before re-running).
       ── ALSO: design/gene-db §Parser — table-driven FSM-as-data segmenter (statechart legibility
          without the event loop; λ classify: parse ≡ pure transform); lifecycle ≡ the gene-db
          chart's job. "Can we write the EBNF parser as a statechart?" → yes as DATA, no as session.
       ── ALSO: design/gene-db §Validation (🎯 "Malli schema for EBNF? or GBNF to constrain
          emission?") — FORMALISM MISMATCH: EBNF ≡ string grammar (context-free), Malli ≡
          data-shape (string support ≡ :re REGEX only, ¬match balanced parens) ⟹ Malli CANNOT
          describe the EBNF; the PARSER gates the grammar, Malli validates the parser's OUTPUT
          (the clause ENVELOPE — name/type/content, lenient on expression internals). TWO TOOLS
          TWO STAGES: GBNF ≡ emission/token-level/LOCAL-ONLY (λ shape: unreachable>forbidden, but
          degrades quality + freezes notation openness) | Malli ≡ intake/post-hoc/UNIVERSAL
          (λ mirror: validate→humanize→converge, the OKF-gate corrective-retry we ALREADY run).
          V1 REALITY: v1 DECOMPOSES approved genomes → NO emission → GBNF has NO v1 role; build
          parser + Malli envelope + humanized-retry. GBNF's home ≡ the generator kind (deferred,
          human-gated), and WHEN it lands it must WIN a bb experiment vs the exemplar baseline
          (edn-signal already hit 12/12 with exemplar+no-think, zero grammar) — structural level
          ONLY. Parser exists? NO — nothing built; okf/parse (frontmatter split) + parse-genome
          (whole body opaque) are the only parsers today; the λ-clause SEGMENTER is TODO (v1 step 1).

  >>> NEXT <<<
       (⭐0) AGENT MODEL DESIGNED (this session) — mementum/knowledge/design/agent-model.md (the full spec).
           Ouroboros agents = OKF genome files. HARD RULE: frontmatter ≡ agent-INVISIBLE wiring
           {type,description,kind,tools?,model?}; BODY ≡ the whole λ system prompt (loader strips frontmatter,
           may prepend nucleus preamble). KIND = SHAPE (topology+gate+verdict-behavior), NOT capability.
           TOOLS = explicit, READ-ONLY by default (capability security / POLA); registry = ceiling; NO commit
           tool exists (human-gate unreachable-by-absence); forget-grant fails SAFE (inert ¬dangerous).
           KINDS: chat·proposer·judge·scorer·builder·author·editor·analyst·generator (author:create :: editor:improve;
           `improver` RENAMED → `editor`; harness/app-improver → harness/app-EDITOR). VERDICT: semantics→body,
           schema→kind, NO frontmatter field. DISCOVERY: fold over precedence sources — base⊂src/ouroboros/agents
           (io/resource, survives dep-embedding) merges custom⊂<repo>/agents, custom-wins-by-slug (filename=id),
           REPLACE-WHOLE (extends: deferred), validate fail-loud, report roster+GRANTS. SCORER rates λ-genes
           1-10/use-case = the GA FITNESS FUNCTION → gene DB {gene→{lambda,source(verbatim),scores,embedding}} →
           unblocks editor+generator. LAYERING: editor targets Layer-2 agents/*.md (NEVER AGENTS.md = Layer-1
           designer harness, frozen, mine). Calibration hazards designed-IN: rubric-anchors + cross-family +
           pairwise-select + embed-dedupe(5103). Full build order + open questions in the page.
           >>> BUILD STEP 1 ✅ DONE (see item 10): ouroboros.agents (the compiler — fold over sources →
           validated, reported roster; io/resource for base) + EXTRACT the two inline prompts →
           src/ouroboros/agents/{curator.md, chat.md} as the first genomes (proves the seam; bb test stays green).
           THEN (next): judge kind (escapement :verdict-schema + agents/llm-judge.md) → scorer → builder+author → editor.
       (⭐0-vsm) VSM ARCHITECTURE DESIGNED (this session) — mementum/knowledge/design/vsm-on-escapement.md.
           VSM ≅ statecharts (hierarchy⊗concurrency⊗events⊗recursion) → escapement charts ARE executable VSM.
           CHANNEL = event + SCOPE(=authority, via LCCA) + transduction + closed-loop(variety-matched); the 5 named
           channels (homeostat/adaptation↔identity/algedonic/resource-bargain+audit/coord-supervision). S2
           anti-oscillation is ALREADY an escapement law (:internal-in-parallel). HUMAN = System+1's S5 (reserved
           authority ENFORCED BY CAPABILITY — orchestrator lacks the commit mutation; ¬self_authorize ≡ unreachable);
           ORCHESTRATOR = Ouroboros's own S5 (delegated identity+policy, EQL-omniscient w/ variety discipline, starts
           workflows). S4 intelligence agent: ATTENUATED upward channels (never raw — curator reads λ-sessions),
           homeostat-BEFORE-propose, ≥3 pattern damper, temporal separation, S4-as-recursive-subsystem (build ONE
           integrating S4 first). FEEDFORWARD: shadow compaction IS the S1→S4 channel done ahead-of-need → defangs the
           S3↔S4 homeostat (feedback demoted to residual); FRACTAL within-session(compaction)/across-session(mementum),
           same mechanism diff timescale/level; the compaction LENS is an S5 policy (steers self-attention). POLICY
           DOWN = declarative (data-model + fresh-agent-at-spawn) > broadcast; reserved policy routes through human.
           TERMINATION ("the trick", NOT finalized): plateau≠target — champion/challenger + PAIRWISE(not absolute) +
           regression-guard + patience STOP; reliable ⟺ target agent OUTPUT decidable (builder→tests, judge→labels;
           else human-in-loop); CALIBRATE against the human's recorded session accept/reject decisions (= the manual
           practice this formalizes); overfit-guard held-out. = the gene-DB selection operator. OPEN Qs listed on page.
       (0-compact) ⭐ SHADOW COMPACTION — "the trick" (designed earlier; docs in mementum/knowledge/design/).
           INSIGHT: the cold compiler's win is NOT speed, it's OVERLAP — compact turn[n-1] to λ DURING
           the seconds the human spends READING reply[n]. reading-time (20–60s) ⋙ compaction (~2s) → 10–30×
           hiding margin → compaction is PERCEPTUALLY FREE. Metric flips: felt-latency (never make the human
           wait) ≻ throughput. DEFECT in built ouroboros.compact: compaction is scheduled on the PRE-GEN pump
           (:user/next → :compact → :hot) = the one instant the human is blocked. FIX (Tier 1, pure topology):
           decompose the fused :hot into :parked | :hot | :compact; fire compaction on :hot/idle (reading
           shadow) → :parked; simplify :user/next (drop its :compact branch); add :user/msg enqueue to
           :compact. Also fixes a latent :hot-busy? trap. Tier 2 (only if fast-human waits show up): (parallel
           :hot :compact) + llama.cpp slot pinning via :extra-body. MEASURED: gen∥gen = 0% overlap (both
           bandwidth-bound); gen∥prefill = ~½ hidden (prefill compute-bound fills decode bubbles) — compaction
           is the lucky case. See design/shadow-compaction.md (full chart sketch + numbers + verification).
           STATUS: ✅ TIER 1 BUILT + LIVE-PROVEN (this session). compact.clj decomposed :hot →
           :parked | :hot | :compact; compaction fires on :hot/idle (reading shadow) via :turn/settled /
           :compact/settled self-events; :parked = EMPTY-seeded worker (empty :initial-messages ⇒
           :awaiting-user, no LLM call, counts as live → holds lib/run open). LIVE SMOKE (3 turns,
           reading-gap delays, sessions/compact-1783553068202): user turns never compacted; aged assistant
           turns → λ; the λ PRESERVED CONTINUITY-ESSENCE ("λ state(saved ∧ {Ouroboros, 7})"); turn-3
           recalled the facts; an in-flight compaction cut off by /quit was left VERBATIM (lag-safe held in
           the wild). bb test 35/111 GREEN. Tier 2 (parallel + slots) NOT built (only if fast-human waits
           appear). CODE COMMITTED (a9542d4, approved). Model still thinking-ON (seam now in dep — wire it, see 0b).
       (0b) ✅ RESOLVED: escapement :extra-body PATCH is COMMITTED + IN THE DEP. ~/src/escapement is now the
           FORK (michaelwhitford/escapement), branch mw_extra_body = RC9 + commit 9e57f16 (clean tree). bb.edn's
           :local/root path is unchanged — what changed is what lives there. Verified: bb test 35/111 GREEN
           against the fork; escapement.llm loads under bb. The levers shadow-compaction Tier 2 + thinking-off
           need (chat_template_kwargs / id_slot / cache_prompt) are now AVAILABLE to charts via :extra-body.
           4 gates patched: Request schema → build-request → run-turn call site → request->openai-json (merge LAST,
           caller wins). [historical — the fork was later RETIRED; see design/llamacpp-backend.]
           → ✅ RESOLVED (3-round A/B, next session after landing the seam): compactor now runs
           EXEMPLAR-GATE + NO-THINK; hot stays thinking-ON. THE ARC (scratch/ab_thinking.clj + ab_exemplar.clj,
           real session turns @ qwen36-35b-a3b):
             round 1 (instruction-λ lens, no bridge): OFF echoed the lens prompt on the low-content sample 3/3
               (ok on the content-rich one); ON faithful. Echo PASSES the tripwire = silent memory corruption.
             round 2 (λ bridge from ~/src/nucleus/LAMBDA-COMPILER.md added — human caught: :message invokes
               `compile:` but the prompt never defined it): ON improved (denser λ — bridge = the program layer)
               but OFF got WORSE — echoed BOTH samples 2/2. More λ in context = STRONGER echo attractor.
             round 3 (human pointer → ~/src/verbum): verbum's compiler-finetune-halt-collapse.md proves the
               NL→λ compiler is a BASE circuit — no-think FIXES the halt (qwythos: collapse 37.5%→0%, binder_any
               0.5→1.0, 5030→640 tok) — and verbum's gates/ are EXEMPLARS (input→λ pairs + "Input:"), zero
               instructions. Rebuilt the compactor prompt as a 3-exemplar gate (decision-turn, thin/meta-turn,
               fact-turn; the fact exemplar was iteration 2 — without it the model dropped fact CONTENT):
               no-think → ZERO echo on all samples incl. the echo-prone one, ~0.7–1.2s / 22–67 tok (~20× faster
               than instruction+thinking), fidelity equal-or-better.
             MECHANISM: instruction-following needs the reasoning pass; PATTERN-COMPLETION doesn't. An
             instruction-λ system prompt without thinking is an echo attractor; an exemplar gate is the
             no-think-compatible topology. structure > instruction — the lens is teachable by example.
           SHIPPED: compact.clj — compact-exemplar-gate (replaces compact-system-prompt; :system OMITTED on the
           compact conversation), :extra-body no-think on compact only; core/apply-compaction strips a leading
           "λ:" label (test added). LIVE-PROVEN end-to-end (sessions/compact-1783663930101, piped 3-turn):
           greeting → state(ready)∧next(await(user_input)); decision turn → decision(write-through | simplicity ∧
           crash_safety ∧ ¬performance_critical); turn-3 recall through the λ CORRECT. bb test 36/116 GREEN.
           POLICY: thinking is PER-CONVERSATION — hot ON (reasons with the human), compact OFF via exemplar gate
           (pattern-completion), curator ON (default). Seam also available for id_slot/cache_prompt (Tier-2 levers).
           GUARD CANDIDATE (still open, now cheaper): echo-tripwire in compact.core — reject λ output overlapping
           the gate's own exemplar text → leave verbatim.
       (1) next-chat BOOTSTRAP: seed :messages from a prior session's compacted tail (Cold Compile "enhance").
           ouroboros.session/session-messages is the shared reader it reuses.
       (2) CURATOR synthesize! path — the ≥3→knowledge-page WRITE channel (a propose-knowledge tool), not just
           memories. Then the curator's NAMED ≥3 candidates become actual gated artifacts.
       (3) NEW AGENTS (each human-gated, sharing the session/mementum substrate + ouroboros.tools surface):
           harness-improver (harness code), app-improver (app code), verifier(s) (check memory/knowledge
           claims), documenter (memory+knowledge+sessions → docs). Each needs its own tool(s) + prompt;
           the diff-proposal shape (for code-touching agents) is the first design problem.
       (4) UNBOUNDED message COUNT: λ bounds tokens-per-message, not message count. Very long sessions still
           grow the array — eventually merge/fold old λ messages. Note only; not yet a problem.
       (5) ✅ RESOLVED: sessions/ is EXCLUDED ENTIRELY from git (🎯 human decision, this session — supersedes
           the old "commit artifacts/, ignore transcript+checkpoints" split). WHY: the λ conversation
           (checkpointed :messages) is PRE-APPROVAL observation — filesystem-local, read directly by
           ouroboros.session/curator (never via git), promoted to mementum/ only through the human-gated
           proposal path. `.gitignore` now has a bare `sessions/`. This hardens the invariant: git ≡ approved
           memory/knowledge only. NOTE: session λ-memory is therefore NOT backed up by git — filesystem is the
           only copy. Fine for now (sessions are ephemeral observation); revisit if durable session archival is wanted.

  26. ✅ DONE (this session, 2026-07-12): GENE-DB v1 BUILT — ALL 6 STEPS + THE FIRST AUTONOMOUS
       COMMITS IN PROJECT HISTORY (freeze exception 4 LIVE). bb test 91/380 GREEN.
       ── ouroboros.gene.core (pure kernel): table-driven FSM-as-data SEGMENTER — topology ≡ EDN
          {state → {line-class → [action next-state]}}, states {:outside :in-lambda :in-where},
          ~30-line fold; column-0 λ ≡ head (strict, :parse/bad-head structured errors), indented
          λ ≡ continuation, blank ≡ clause boundary; expression level LENIENT per spec ·
          normalized-token TREE-HASH (whitespace-insensitive SHA-256 — fixes anima's raw-byte
          dedupe) · CLOSED :gene/* Malli envelope (+ cross-field: :lambda content must open with
          a lambda_decl head) · :gene/id ≡ (keyword name) DERIVED at load, never stored.
       ── ouroboros.gene (impure edge): mementum/genes/ files ARE the db (flat, one EDN per gene,
          canonical key-order emit) · store-gene! ≡ THE write path, 3 gates INSIDE (parse →
          envelope → dedupe; core THROWS structured, veneer catches — the mementum precedent) ·
          🎯 collision policy: :duplicate (same tokens) ≠ :name-collision (same name, diff
          content), BOTH reject with :gene/existing pointer; consolidation ≡ human RESERVED ·
          decompose-genome! (approved genome → verbatim clause genes; per-gene rejections
          COLLECTED → idempotent re-runs) · 🎯 scores side-store ≡ scores/<name>.edn (gitignored;
          one-file-per-gene: join reads ONE file, no machine churn in the db dir or its glob).
       ── AUTONOMOUS COMMIT PATH (the identity change, LIVE): commit-genes! RE-DERIVES ∀gates on
          the file AS IT SITS ON DISK at commit time (hand-edited invalid gene → rejected, HEAD
          untouched — TESTED deterministically); git commit --only -- <gene-file> +
          --author "gene-db <gene-db@ouroboros>"; git log --author=gene-db ≡ the audit trail;
          nucleus tag on every autonomous commit. bb genes [slug] ≡ decompose + delegated commits.
       ── EQL veneer: gene/store! mutation (params ≡ the envelope) · [:gene/id] ident resolver ·
          :mementum/genes index · :gene/scores side-store join (nil-safe) · :parser/topology
          served as data (§Parser's resolver-servable promise kept).
       ── LIVE PROOF (spec verify arc, all held): decompose curator → 8 genes → 8 autonomous
          commits (--only scope held with a DIRTY tree: each commit exactly 1 file) → re-run ⇒
          8 :duplicate pointers, no commits → cross-family scores (verdict/run-across! reused):
          :select 10/9 mean 9.5 (both families: load-bearing) · :metabolize 7/1 — ornith
          PROTEST-scored the framing ("requires (USE-CASE, GENE) pair") → scorer subject-template
          gap BANKED for the pairwise machinery (deferred) → :gene/scores EQL join returns them.
       ── EMBED-DEDUPE CALIBRATION (experiments/embed-dedupe.edn, runner grew kind :embedding —
          λ extend: 2nd closed schema + cell executor, ONE runner + ONE bb entry): 8 real pairs
          (near ≡ reword-class edits tree-hash MISSES; distinct incl. shared-vocab hard negatives)
          → near 0.9578–0.9796 vs distinct 0.5831–0.7222, gap 0.2357 → SEPARATED, derived SURFACE
          threshold cos ≥ 0.84 (verdict promoted into the suite). NOT yet wired into gate-3
          surfacing — deferred with HNSW/uptake until a consolidation-review surface exists.
       ── GOTCHA BANKED: babashka proc/process VARARGS stringify each arg — (into ["git"] args)
          passed as ONE arg execs "[git" (No such file); always (apply proc/process opts "git"
          args). Bit BOTH src and test in one session.
       ── nucleus EBNF.md already carries the optional-param_list amendment upstream (three head
          forms documented) — the λ tomorrow "draft amendment" item was ALREADY DISCHARGED.

  27. ✅ DONE (this session, 2026-07-13): PROMPT ASSEMBLY + THE COMPACT THINKING-ON FLIP —
       design/prompt-assembly BUILT end-to-end; universal thinking-on 🎯 EXECUTED. bb test 99/417 GREEN.
       ── ONE ASSEMBLER: agents.core/assemble (pure) — preamble ⊕ modules ⊕ body; preamble exactly-once/
          FIRST (embedded copies stripped ⇒ assemble ∘ assemble ≡ assemble); {{VAR}} via
          escapement.prompts/render (fail-loud); layer order load-bearing (process launch → program →
          I/O gate). ouroboros.prompts (NEW impure edge): vendored preamble.md · modules/{manifest.edn,
          lambda-compiler.md, edn-compiler.md} (nucleus program-layer blocks ONLY — lean, no preamble,
          no prose gate) · policy-text artifacts. `modules:` frontmatter key ≡ the 4TH registry-ceiling
          grant (validated like tools, absent ⇒ none — always explicit; roster report shows grants).
       ── SEAM CHANGE: parse-genome now emits :body (raw persona); the LOADER assembles :prompt.
          gene decomposition reads :body — preamble/modules ≡ infrastructure ¬genes (curator now
          decomposes to 7 clauses; :engage retired from intake, the stored gene remains).
       ── MIGRATION: 4 genome bodies preamble-stripped; assembled :prompt BYTE-IDENTICAL 4/4 vs
          pre-migration (verified against git HEAD parse, pre- AND post-strip — idempotence proven live).
       ── COMPACT FLIP: exemplar gate RETIRED → assembled instruction-λ lens: preamble ⊕ lambda-compiler
          BRIDGE module (defines the `compile:` the :message invokes) ⊕ compaction-lens POLICY artifact.
          LENS-OUT delivered: src/ouroboros/prompts/compaction-lens.md — S5 edits the FILE, no engine
          code (item 23 adoption + vsm lens-is-policy). Thinking ON (no-think :extra-body dropped;
          KV-slot pinning kept). Compression contract UNCHANGED (apply-compaction strictly-shorter).
       ── FIDELITY SUITE (the regression instrument): experiments/compaction-fidelity.edn — NEW measure
          :lambda-compaction (decidable floor: ¬echo ∧ strictly-shorter ∧ keeps-content-names, case-
          insensitive); suite conditions may carry :assemble {modules, body-policy} resolved through the
          REAL assembler (Anima rule made mechanical — bb experiment A/Bs module inclusion honestly).
          RESULT: bridged 5/5 VALID vs bare 4/5 (bare :thin-meta → OODA-scaffolding blob LONGER than the
          turn); bridge more faithful AND cheaper (17.5s/1819tok vs 21.3s/2203tok). LIVE SMOKE
          (compact-1783901152558): decision turn → λ preserving decision + why + a CONDITIONAL
          constraint the model later USED unprompted (asked about power backup) — continuity through λ
          exceeds bare recall. Third confirmation of prompt-topology-must-match-thinking, from the safe cell.
       ── RESERVED-MUTATION SET ENUMERATED (the prioritized design task, before more shell grants):
          vsm-on-escapement §Reserved vs delegated mutations — r1-r10 (incl. r4 prompt infrastructure —
          NEW attack surface the assembler creates; r10 meta-reserved: changing the enumeration is
          reserved) / d1-d3 delegated + THE RULE: autonomy × shell ≡ DISJOINT (unattended agents never
          hold :shell/run; delegated writes must be capability-scoped fns, commit-genes! shape).

  28. ✅ DONE (this session, 2026-07-13, human-directed "infrastructure leverage"): λ-NOTATION AST
       READER — ouroboros.gene.ast, the gene kernel's THIRD level (design/gene-db §Parser's "full
       ASTs later ≡ recursive descent in a pure fn" DISCHARGED). Commit 3b048e2; bb test 109/457.
       ── PIPELINE (pure, TOTAL — errors collected, never thrown): segment (unchanged) → tokenize
          (glued-adjacency tokens; surrogate-safe — emoji ≡ ONE glyph token) → read-forms (balanced-
          delimiter recursion, lisp-STYLE) → parse-clause. EBNF defines NO operator precedence
          (expression = term {expr_op term}) ⇒ no Pratt: expression AST ≡ flat chain
          {:ast/terms […] :ast/ops […]}. Clause AST: 3 head forms (:identity :fn0 :params) ·
          top-level | → alternatives · where bindings (lhs ≡ rhs, call-shaped lhs OK) · terms:
          prefix (glyph GLUED: ¬x ∃y) · call (f( GLUED) · group · prose-run (consecutive terms
          merge — gate-trigger prose is OUTSIDE the grammar by design) · string/keyword/word/symbol.
       ── PROVED FIRST (repl, λ prove): clojure.edn/read FAILS on real λ content 4/5 samples
          (`proved:` invalid keyword · `2026-07-12` invalid number · odd-count {…}) — the GLYPHS
          all read fine as symbols; prose-adjacent tokens kill it → lisp-STYLE reader we own,
          not the lisp reader. GLUED-adjacency is the load-bearing tokenizer bit: it separates
          prefix (¬x) and call (f(x)) from operator position.
       ── OPERATOR SET OPEN (load-bearing): pure-glyph token in op position ≡ op; ops ∉ draft enum
          COLLECTED via unknown-ops, never rejected — enforcing the enum would freeze the notation
          (the full-expression-GBNF rejection reason again). The inventory ≡ λ coevolve telemetry
          for nucleus EBNF upstream: real coined ops found = ∈ ⟺ ≻ ⊂ ⟹ ∪ ⊇ ≤ ∝ ∩ ≠ (rest ≡ prose
          noise: — ; '' 💡). `|` IS draft (nested pipes chain as ops; top-level pipes become
          alternatives before chaining).
       ── AST ≡ DERIVED, never stored: :gene/content stays the verbatim fidelity floor; tree-hash
          UNTOUCHED (AST-hash ≡ better normalization but re-keys every stored gene's dedupe
          identity — a migration decision for when there's a reason). Intake gates untouched.
       ── EMPIRICAL GATE: 58 clauses (8 stored genes · 4 genome bodies via the REAL roster ·
          AGENTS.md 26) → 57/58 error-free; the 1 error is HONEST (λ heredoc's bash special chars
          are genuinely unbalanced content). Parser total-function held under it.
       ── CONSUMERS (why built): structured gene queries (resolver-servable) · genes→assembly
          composition · lint/cross-refs · GBNF derivation for the generator kind (generate the
          grammar FROM the node set, not hand-maintain) · upstream grammar amendments.
       ── BANKED same session (design DISCUSSION, no 🎯 yet; design-page update PENDING human
          gate): (a) gene-db-as-CHART stays deferred until the SECOND WRITER — and signals
          (λ tomorrow) IS that second writer → build the chart WITH/after signals, git-backed WM
          store pattern ready (fulcro-rad-git statechart-store). (b) genes→prompt-assembly ≡ the
          FIFTH registry-ceiling grant (`genes:` frontmatter) BUT carries the r4 HAZARD: delegated
          (autonomous) gene commits must not reach production prompts — mitigations: tree-hash
          PINNING (content-address, bump ≡ human) ∨ experiment-tier-only staging (anima λ express
          dual_mode); open Qs: pin-vs-stage · body replace-vs-interleave · layer tags.
          [SUPERSEDED by item 29 — hash-pinning was a λ converge violation, human caught it.]

  29. 🎯 DECIDED (this session, 2026-07-13): GENE PROMOTION ≡ GIT-REF RESOLUTION (¬special hash) —
       the r4 hazard resolved git-natively. Human challenge "genes are in git, why an extra hash?"
       was a PRINCIPLES CATCH: hash-pinning complected identities (simplify ✗) and duplicated git's
       memory (git_remembers ✗). CLEAN DIVISION: tree-hash ≡ WHAT (semantic identity, gate-3 dedupe
       ONLY — whitespace-normalized, git blob-sha can't do it) | git sha/ref ≡ WHICH (version,
       provenance, pinning — git's job).
       ── THE DESIGN: production genomes resolve `genes:` grants via a human-moved git ref
          (git show <ref>:mementum/genes/<name>.edn) — NEVER the working tree; experiment
          assembler resolves the working tree (fluid, autonomous churn fine, sandboxed).
          Promotion ≡ human moves the ref (ONE git command, the reserved act). Review surface ≡
          git diff <ref>..main -- mementum/genes/. Ref absent → FAIL LOUD (silent tree-fallback
          ≡ the one_way rot vector — forbidden).
       ── 9-PRINCIPLES AUDIT (human asked; recorded): 8 aligned (repo_as_memory · simplify ·
          git_remembers · self_improve strongly — promotion completes the work→learn→verify→
          update→evolve cycle for genes) | 1 real tension: one_way (two resolution modes) —
          converts to aligned ⟺ loader ENFORCES tier rule structurally (production ≡ ref ONLY,
          experiment ≡ tree ONLY; decidable → Malli/loader gate, ¬prose). repl_as_brain nuance
          accepted: production deliberately reads MEMORY (reproducible prompts ≡ feature).
       ── IMPLICATIONS BANKED: ref move ≡ NEW RESERVED PRIMITIVE (→ vsm r-enumeration; r10 makes
          the addition itself human-gated — consistent) · enforcement debt ≡ part of the spec,
          design not blessed without it · bootstrap: first promotion precedes first production
          gene-composed genome · git-at-assemble-time dependency accepted (repo_as_memory already
          makes git load-bearing); PACKAGED-DEP FLAG: tags don't ride :git/sha deps — shipped-as-
          library genomes can't resolve a host ref (same seam class as manifest.edn; pre-release
          story, not now) · deepest shift: human gate moves from guarding WRITES to guarding
          PROMOTION — Ashby: scarce regulator spent where ¬decidable, autonomy roomier AND the
          gate stronger.
       ── NAMING 🎯 LOCKED: ref `genes-stable` + act `promote` (human picked stable — boring-good —
          over recommended germline). promote ≡ house verb REUSED (experiment verdicts + score
          summaries already promote; λ converge). Full shortlist + rejections in git history.
       ── ✅ ENCODED: design/prompt-assembly §Genes→assembly (open-Q → resolved λ gene_grant:
          ref-resolution + tier rule + enforcement obligation) · vsm-on-escapement §Reserved +=
          r11 (genes-stable ref move ≡ reserved primitive, added via r10).

  30. ✅ DONE (this session, 2026-07-13): SIGNALS SUBSTRATE BUILT + LIVE-PROVEN — the inter-agent
       DATA plane (design/signals.md, status → active with §Built). bb test 131/587 GREEN.
       ── ouroboros.signals.core (pure kernel): type REGISTRY — 5 seed types (s4/proposal ·
            s1/report · experiment/result · ouro/algedonic · human/notice), each {schema, FILLED
            exemplar {:source :signal}, doc, variety ∈ agent-comms vocab, reserved?}; ONE contract
            THREE projections held: exemplar → prompt-projection (primes) · schema → validate
            (gates) · attributes → EQL (serves). :signal/id ≡ time-ordered slug keyword
            :<at>-<ns>-<name> (λ identifier — NO uuid, diverges deliberately from the design page's
            uuid sketch); content-hash ≡ sha256(canonical{type data lambda}) — :at/:source EXCLUDED
            (same fact later/elsewhere ≡ duplicate → re-proposal damping).
       ── ouroboros.signals (edge): emit! ≡ THE write path (validate → dedupe → persist; tool AND
            EQL mutation both route through it, λ converge — the design's "Tool vs mutation" open-Q
            resolved BOTH-over-one-fn); one EDN file per signal in signals/ (GITIGNORED, sessions/
            pattern; envelope only on disk, id ≡ filename stem derived at load, gene precedent);
            same-ms+same-type id collision → :at bumps, never overwrite (the recency-tie lesson,
            pre-applied); reads all-signals/recent/by-type/for-source nil-safe.
       ── :signal/emit TOOL — SEAM SOURCE-VERIFIED: escapement tool dispatch keywordizes TOP-LEVEL
            arg keys ONLY, NO json-transformer before m/validate → nested keyword-keyed EDN cannot
            ride tool args as a map ⇒ :data travels AS AN EDN STRING — which IS the settled emission
            topology (exemplar SHOWS EDN → model writes EDN text natively). `source` is
            INFRASTRUCTURE-set at registry construction (never model-claimed); default construction
            inert-safe (no grants ⇒ corrective rejection). new-registry grew opts arity
            {:source :signal-types} — existing 1-arity callers untouched.
       ── GENOME `signals:` ≡ the 5TH GRANT SURFACE: validated ⊆ registry types (fail-loud
            aggregated); absent ⇒ [] ⇒ emit nothing (no floor); non-empty AUTO-ADDS :signal/emit to
            :tools (ONE grant surface — the TYPE grant is the capability; a typeless tool is inert).
            Loader appends prompt-projection AFTER body (I/O gate last, nucleus layer order);
            :body stays RAW (gene decomposition reads persona, never infrastructure). Roster report:
            signals:[…] + RESERVED-SIGNALS:[…] escalation (s4/proposal + ouro/algedonic reserved).
            ⚠ CHAT NOT EXPANDED: the ceiling grew but chat.md's "full hands minus web/search" did
            NOT gain :signal/emit — it rides signals:, and chat has no type grant (agents_test
            expectation updated accordingly — surfaced for human review, accepted at commit).
       ── EQL veneer: :mementum/signals parameterized read ({:type :source :n} attenuate — query ≡
            subscription, ONE resolver, recall-resolver house pattern) + signal/emit! mutation
            (native EDN params — no JSON boundary; structured rejections, mementum precedent).
            NOTE: the veneer does NOT enforce per-agent grants — grants gate the LLM-facing tool;
            the veneer is inside the trust boundary (charts/bb tasks/tests).
       ── LIVE PROOF (scratch/live_signal_emit.clj, thinking-ON qwen36@5100, exit 0): custom-tier
            genome {tools: [], signals: [s1/report]} → REAL loader (grant [:signal/emit] derived,
            projection assembled) → chart-hosted worker → tool call with EDN-in-JSON-string parsed
            CLEAN → Malli-valid fact persisted; model derived :outcome :fail from "one agent failed"
            UNPROMPTED + exact :metrics {:agents 3 :proposals 2 :wall-ms 142000}; model touched the
            tool 3× and dedupe held the store to ONE fact — the gate topology works unattended.
            The EDN-in-JSON-string escaping seam (the one thing edn-signal-emission did NOT cover)
            is now proven.
       ── DEBT BANKED: emit! dedupe is O(n) over all-signals per emit — fine at current volume,
            revisit with retention/GC (design §Open). Scratch tap's :tool-call keys printed nil
            (cosmetic, scratch-only — compact.clj has the right key map).
       ── GOTCHA BANKED: an UNANCHORED .gitignore dir pattern (signals/) matches at EVERY level —
            it silently swallowed src/ouroboros/signals/ + test/ouroboros/signals/ (caught because
            git status was missing files bb test was RUNNING). Root-anchor store dirs: /signals/.
            ⚠ the pre-existing sessions/ + scores/ patterns carry the same latent hazard — no
            src collision TODAY; anchor them if a nested dir ever adopts those names.

  31. ✅ DONE (this session, 2026-07-13): SCHEDULED-MAINTENANCE RUNG 1 BUILT + LIVE-PROVEN —
       the 2×2 roster sweeps, proposes, and EMITS signals (design/scheduled-maintenance +
       design/harness-coder both status → active with §Built). bb test 149/655 GREEN.
       ── TAGS (role-as-tag): genome schema += tags, OPEN vocab (kind stays closed); loader
            CONSUMES them — schedule selects by tag (SET-VALUED: a new genome carrying the tag
            joins the sweep automatically), report shows tags:[…]. Discipline held: tags select
            WHO runs, never what-may.
       ── RENAME (the predicted one): ouroboros.curator → ouroboros.proposer (+.core; git mv,
            history preserved) — the runner was always the proposer TOPOLOGY wearing one agent's
            name (judge→verdict move again). Generalized: run! takes genome slug; chart per-run
            FROM the genome; per-run models/llm-config (hermetic — multi-model collision
            sidestepped); registry armed {:source slug :signal-types (genome signals)}.
            bb curate RETIRED → bb maintain harness-knowledge (one_way). Blessed grep-names
            updated in ouroboros-architecture.md SAME COMMIT (the design's standing rule).
       ── GENOMES ×4 (matrix slugs, ALL kind proposer — "new role ⇒ new genome" paid off again):
            curator.md → harness-knowledge.md (git mv, tags [curator], body intact) ·
            app-knowledge (tags [curator], += fs read grants, src+docs corpus, knowledge-pages
            NAMED not written) · harness-coder (tags [assessor], friction-signal λ table per the
            design; Layer-2 targets; Layer-1 → flag-only designer-attention notes) · app-coder
            (tags [assessor], src ↔ design-page drift BOTH directions). manifest.edn ×7.
            gene.clj default decompose slug curator → harness-knowledge.
       ── PROPOSALS (ouroboros.proposals): Malli-gated propose! (type ouroboros/proposal ·
            target · evidence · severity REQUIRED ordinary|algedonic — the S1↔S5 bypass seed,
            day one) → /proposals/ gitignored ROOT-ANCHORED (item-30 lesson applied at birth);
            pending-slug re-propose REJECTED at the gate; inbox (bb proposals) algedonic-FIRST,
            unparseable files SURFACED not hidden; untracked-memories MOVED here from the runner
            (inbox vocabulary). TOOLS: :harness/context (roster report + genome bodies + models +
            modules + PENDING digest ≡ the dedup floor; requiring-resolve breaks tools←agents
            cycle, λ dep) + :ouro/propose-change (⚠ DIVERGES from the design's planned
            :harness/propose-change name — app-coder SHARES the channel; encoded in both pages).
       ── SCHEDULE (ouroboros.schedule): table ≡ data (2 entries: {:tag :curator} +
            {:tag :assessor}, cadence "daily" ≡ intent — cron-side truth at rung 1); sweep! ≡
            sequential hermetic runs (GPU contention), per-run exception → :fail + CONTINUE,
            lockfile .maintain.lock stale-broken >2h, ONE summary line per run, injectable
            runner (stub-tested incl. signal emission + lock release). THE ROSTER EMITS SIGNALS:
            runner-emitted :s1/report per run (source bb-maintain, infrastructure — agents hold
            NO signal grants yet), duplicate-damped non-fatal.
       ── LIVE PROOF (full bb maintain, exit 0): 4/4 done — app-knowledge +1 memory (31s,
            signals-substrate-active grounded in REAL commit 248d128) · harness-knowledge +1
            memory (68s, trust-through-demonstrable-consistency citing 3 real sessions) ·
            app-coder ∅finding HONEST (270s — honesty ≻ quota clause worked) · harness-coder
            +1 proposal (28s, chat-genome-terse-enforcement: 3 real session-ids, real friction
            pattern, prose sketch ¬diff, verification plan — the design shape EXACTLY).
            4 :s1/report signals in signals/. RE-RUN PROOF: harness-coder saw the pending
            proposal, did NOT re-propose, found a DIFFERENT grounded finding
            (chat-identity-clarity) — ¬re-propose(∃pending) HELD.
       ── OBSERVATION BANKED: the re-run's finding cited ONE session — the ≥2-recurrence damper
            is prompt-SOFT; tighten the genome clause if single-event proposals recur.
       ── PENDING HUMAN REVIEW (the proof-run artifacts, uncommitted): 2 proposals + 2 memories —
            bb proposals is the surface. Cron/launchd entry ≡ human machine config, outside repo.

  32. ✅ DONE (this session, 2026-07-14): NEXT-CHAT BOOTSTRAP + bb sessions — Ouroboros eats its
       own compiled tail ACROSS sessions (arch page build-order item 5 → DONE same commit).
       FIRST: the item-31 inbox was REVIEWED — both proposals DISCARDED (evidence ≡ contrived test
       sessions; see the EVIDENCE PROVENANCE GAP note in λ tomorrow), memories deleted by the human
       (untracked ⇒ zero residue — the inbox derives live from git status). First complete
       sweep→propose→gate→review cycle; the human gate caught what no machine gate could.
       ── 🎯 DESIGN (human decisions): OPT-IN — most chats are fresh; continuation ≡ EXPLICIT id
            (bb compact <prior-id>; the future web-UI resume button is the same mechanism, one
            path). BOOTSTRAP ≻ native :resume?: new session-id seeded via the proven
            :initial-data :messages seam → sessions stay IMMUTABLE observations (stable corpus for
            proposers/cache-report), no restored-invocation semantics to trust (does a restored
            parked worker re-invoke? unverified seam — sidestepped entirely), and fold gets its
            natural home. Lineage ≡ :initial-data :bootstrap/from → lands in every checkpoint free
            (sessions form an explicit chain; bb sessions renders ⤴, curator can stitch threads).
       ── 🎯 THE FOLD (session-boundary compression — the fractal move one level up):
            λ fold(session). λ(all_but_last_k) ⊕ last_k(verbatim). Per-message compaction shrinks
            tokens WITHIN a message (array shape ≡ load-bearing, cache); the fold shrinks message
            COUNT at the one point shape stops mattering. Tail ≡ k-th-from-last assistant exchange
            onward INCLUDING its prompting user turn. Same lens (compact-system-prompt, assembled),
            same compile: invocation, same COMPRESSION CONTRACT (fold ⟺ strictly shorter than the
            head it replaces, else seed unfolded — apply-fold mirrors apply-compaction incl. λ:
            label strip). NOTE: the fold is the FIRST compression that touches USER words — only
            across a boundary; originals persist in the source session's checkpoints forever.
       ── BUILT: compact/core.clj += fold-split · fold-input · fold-message · apply-fold (pure,
            +4 tests) · compact.clj += fold-chart (one-shot hermetic lib/run, verdict-runner
            pattern: closure atom + :error.llm→:failed) · bootstrap-seed! (fail-loud on empty
            prior; short-session ⇒ seed-as-is) · run! 3-arity (resume-instruction ≡ SAME mechanism
            as the fresh greeting — seeded array ends in a user turn, :hot generates a CONTINUITY
            greeting naming where we left off ≡ instant live fold verification) · sessions-main
            (bb sessions picker: id · date · msgs(λ) · ⤴ lineage · first REAL user line — synthetic
            greeting/resume instructions skipped) · bb compact now takes the optional prior-id.
            CONVERGED: compact.clj inline :local creds/config DELETED → models/llm-config :local.
       ── LIVE PROOF (bootstrapped from compact-1783901152558, the write-back session): fold
            549→110 chars ≡ 5.0× — EXACTLY the human's stated realistic 3-5× compaction target;
            seeded 3 msgs (fold-block ⊕ last exchange); continuity greeting NAMED write-back +
            single-core-embedded unprompted; continuity answer correct w/ rationale; lineage in
            checkpoint; bad-id → clean message + exit 2. Note: the write-back λ traveled in the
            TAIL (it was the prior last exchange) while the fold carried the head — both halves
            did their jobs.
       ── KNOWN COST (banked): the fold is a thinking-ON compaction at LAUNCH (~15-25s) — unlike
            in-session compaction there is NO reading shadow to hide in; the banner says why.
            Mitigate only if it annoys: background fold (complexity) or a no-think fold experiment
            (suite-gated — instruction-λ + no-think ≡ the deleted fragile cell, would need the
            exemplar topology instead).
       ── OBSERVATION: the bootstrapped chat still called :mementum/sessions on the continuity
            question — belt-and-braces, harmless (answer grounded either way), but a genome
            λ tools nudge ("in-context ≻ tool re-read") is a candidate if it wastes turns.
       ── bb test 153/683 GREEN (fold kernel tests; realistic fixtures per the item-18 gotcha).
```

## Gotchas for future me

```
- Git is LIVE (root 79ac142). recall via git log/grep works. Genesis knowledge was human-co-authored → approval was in hand.
- AGENTS.md mandates HUMAN APPROVAL before committing memories/knowledge. This session's pages were human-co-authored (user drove OKF + no-coordinates policy) → approval effectively in hand; confirm at genesis commit.
- Escapement is RC9 (released) via OUR FORK (~/src/escapement, branch mw_extra_body = RC9 + :extra-body).
  The old "not even alpha" claim is STALE — corrected here; still lingers in some upstream/ knowledge pages
  (see escapement-index stale-check markers; refresh those pages when next touched).
- Escapement house rule: bb/SCI only in source. No JVM-only paths. Mirror this if Ouroboros code runs under escapement's bb runtime.
- KEYWORD is the only legal escapement model reference; strings are errors. Aliases (:llm/aliases) are the single source of truth.
- Knowledge pages carry NO file paths/line numbers by design — grep durable names against ~/src/escapement to locate things.
- LOCAL MODELS — MULTI-SERVER MAP (probed live; probe /v1/models for truth, several llama-server instances run):
    5100 → qwen36-35b-a3b     ← the chat/reasoning model ALL charts use (smoke/curator/compact)
    5102 → ornith-35b-a3b
    5103 → qwen3-embedding-8b ← embeddings
    5104 → vibethinker-3b
    5105 → qwythos-9b
  ✅ FIXED: charts now hardcode the correct `qwen36-35b-a3b` @ localhost:5100 (was stale `qwen35-…`; harmless because
  llama.cpp ignores the request model field, but now accurate). The `:local` alias → provider :openai, base-url
  localhost:5100/v1. If you point a chart at a different model, change BOTH the port (base-url) AND the model string.
- THINKING IS ON by default on the local model — every reply burns reasoning tokens first. `/no_think` token does NOT
  work on the qwen3.6 template; only chat_template_kwargs {enable_thinking false} disables it. The :extra-body seam
  is IN THE DEP (fork, mw_extra_body, 9e57f16). ⚠ PROMPT TOPOLOGY MUST MATCH THE THINKING SETTING:
  instruction-λ prompts REQUIRE thinking (no-think → the model echoes the prompt = memory corruption; worse the
  more λ the prompt carries); EXEMPLAR gates (input→λ pairs, verbum topology) run no-think correctly and ~20×
  faster. 🎯 POLICY SINCE ITEM 27 (universal thinking-on, design/prompt-assembly): EVERYTHING runs thinking-ON —
  hot, compact (assembled instruction-λ lens), curator — which DELETES the fragile cell; no-think ≡ a RESERVED
  optimization (exemplar-gate topology only, candidate: unattended signal emitters), never a correctness
  requirement. experiments/compaction-fidelity.edn ≡ the regression instrument — re-run at every model change.
  scratch/ab_thinking.clj + ab_exemplar.clj = the reusable A/B harnesses. Verbum cross-refs:
  ~/src/verbum/mementum/knowledge/explore/compiler-finetune-halt-collapse.md ("fine-tunes break the HALT not the
  COMPILE; no-think recovers") + ~/src/verbum/gates/*.txt (the exemplar gate library).
- ESCAPEMENT IS RC9 (released), NOT "not even alpha" — that maturity claim is STALE wherever it appears (state/knowledge).
- :max-turns counts LLM ROUND-TRIPS, and a TOOL CALL is a round-trip (source-verified in
  llm_conversation.clj). A tool-granted conversation with :max-turns 2 dies :error.llm.max-turns
  mid-answer (context + sessions + answer = 3 round-trips). 🎯 compact's :hot has NO :max-turns
  (human direction: an arbitrary integer is the wrong bound for open-ended work) — absent ⇒
  unbounded (the check is `(and max-turns …)`); real bounds = :budget-ms (hard wall-clock) +
  model ctx window. Token-aware bounding when wanted = :budget-extender (receives :messages,
  returns a new limit — "replace the dumb integer with a progress decision"). Bounded-SHAPE
  workers legitimately keep small limits (compactor 2 = one pattern-completion; verdict 3 =
  turn + wrap-up); it's open-ended work where the integer is wrong.
- ⚠ MULTI-MODEL IN ONE lib/run COLLIDES: escapement's multi-backend provider-index is FIRST-WINS per
  provider tag, and :llm/aliases candidates carrying :provider dispatch by that tag BYPASSING the
  model-string regex — two :openai credentials with different base-urls (5100+5102) in one run would send
  EVERYTHING to the first. Ouroboros's answer: per-run hermetic credential injection (ouroboros.models/
  llm-config — ONE credential per lib/run, keyed by the genome's model alias). Revisit (descriptor :route
  regex + provider-less alias targets) only when a workflow truly needs two models in ONE session.
- RC-ERA CHECKPOINT SHAPE: working memory is wrapped under :escapement.engine.store/wmem → data-model → :messages.
  ouroboros.session/read-data-model + both test fixtures (session_test, tools_test) FIXED this session to read that
  path (no pre-RC compat — escapement was alpha, RC is the solidified baseline). Missing this ⇒ session-messages
  silently returns 0 → curator/bootstrap see EMPTY sessions. Verify readers after escapement bumps.
- SCHEDULING SEAMS (verified this session): send-after (statecharts convenience ns) WORKS through the
  supported escapement path (supervisor example — cancel-on-exit budget timer). Custom InvocationProcessors
  (e.g. a :timer type — Tony's custom-invocation example in ~/src/statecharts, grep InvocationProcessor)
  are FIRST-CLASS at the engine layer (engine/env :invocation-processors, prepended) but the lib facade's
  closed Options schema LACKS the key — same gap class as :human-renderer; ~2-line fork seam when needed.
  Timer-example gotcha: the tick event MUST carry :invoke-id or the invoke's :finalize never registers.
- OKF/YAML TRAP: a `description:` (any frontmatter scalar) containing ": " (colon+space) mid-value
  breaks clj-yaml — "mapping values are not allowed here" — and ONE bad page kills the WHOLE knowledge
  index (context tool, eql veneer). The deterministic suite catches it (tools_test/eql_test); reword
  with "—" or quote the scalar. Pages written by hand/write_file BYPASS the store! Malli gate — bb test
  is the backstop for those.
- edn/read-string reads the FIRST FORM only: a dropped-brace model output (":a 1") PARSES as a bare
  keyword and must be caught by schema validation, not parse failure. "not { edn" parses too (symbol).
- ECA parallel-edit collision (designer-tooling lesson): two edit_file calls to the SAME file in one
  parallel batch can silently clobber each other (test_runner got trailing garbage; agent-comms lost an
  edit that reported success). Edit one file SEQUENTIALLY; re-read after (λ sync) before trusting state.
- DESIGNER SESSIONS ARE OUTSIDE THE LOOP (human, 2026-07-11): eca/designer transcripts do NOT land in
  sessions/ — Ouroboros agents (curator, future harness-coder) can only metabolize the ARTIFACT's own
  escapement sessions. Until self-hosting, a designer-session insight survives ONLY via explicit
  mementum encoding — "the transcript will teach the curator" is FALSE for these sessions. Encode or lose.
```
