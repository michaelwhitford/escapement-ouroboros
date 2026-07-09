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
  repo        : /Users/mwhitford/src/escapement-ouro  | ✅ git LIVE (root 79ac142) — recall/store/temporal real
  idea.md     : one line — "Ouroboros - self-improving agent running on escapement"
  code        : none yet — no escapement integration, no chart, no mementum impl
  knowledge   : escapement framework fully digested (11 pages, OKF format, see below)
  memories    : none yet
```

## What exists now

```
AGENTS.md                                     S5→S1 identity/policy/intelligence/control (lambda directives)
idea.md                                       the seed (one line)
mementum/state.md                             this file
mementum/knowledge/upstream/escapement-*.md   11 source-grounded knowledge pages (OKF frontmatter)
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
           >>> BUILD STEP 1 (when moving design→code): ouroboros.agents (the compiler — fold over sources →
           validated, reported roster; io/resource for base) + EXTRACT the two inline prompts →
           src/ouroboros/agents/{curator.md, chat.md} as the first genomes (proves the seam; bb test stays green).
           THEN: judge kind (escapement :verdict-schema + agents/llm-judge.md) → scorer → builder+author → editor.
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
           appear). CODE UNCOMMITTED pending approval. Model still thinking-ON (extra-body patch not in dep yet).
       (0b) 🎯 escapement :extra-body PATCH (written this session on the escapement clone, branch mw_extra_body,
           RC9 base, UNCOMMITTED, full suite GREEN 413/2260). Adds an OpenAI-wire passthrough so charts can inject
           chat_template_kwargs / id_slot / cache_prompt — the levers shadow-compaction Tier 2 + thinking-off need.
           4 gates patched: Request schema → build-request → run-turn call site → request->openai-json (merge LAST,
           caller wins). See design/extra-body-seam.md. Good upstream-PR candidate.
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
```

## Gotchas for future me

```
- Git is LIVE (root 79ac142). recall via git log/grep works. Genesis knowledge was human-co-authored → approval was in hand.
- AGENTS.md mandates HUMAN APPROVAL before committing memories/knowledge. This session's pages were human-co-authored (user drove OKF + no-coordinates policy) → approval effectively in hand; confirm at genesis commit.
- Escapement is "not even alpha" — breaking changes expected. See escapement-index stale-check markers.
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
  work on the qwen3.6 template; only chat_template_kwargs {enable_thinking false} disables it → needs the :extra-body patch.
- ESCAPEMENT IS RC9 (released), NOT "not even alpha" — that maturity claim is STALE wherever it appears (state/knowledge).
- RC-ERA CHECKPOINT SHAPE: working memory is wrapped under :escapement.engine.store/wmem → data-model → :messages.
  ouroboros.session/read-data-model + both test fixtures (session_test, tools_test) FIXED this session to read that
  path (no pre-RC compat — escapement was alpha, RC is the solidified baseline). Missing this ⇒ session-messages
  silently returns 0 → curator/bootstrap see EMPTY sessions. Verify readers after escapement bumps.
```
