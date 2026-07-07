---
type: mementum/knowledge
title: Escapement — Example & Demo Chart Pattern Catalog
description: Every example/demo chart mapped to the use-case it demonstrates, its topology, and its load-bearing techniques — the examples directory is upstream's INTENT documentation; consult this catalog before concluding what escapement is "for".
resource: https://github.com/fulcrologic/escapement
tags: [escapement, examples, patterns, use-cases, catalog, steering, multiplex, ouroboros]
status: active
category: upstream
related:
  - upstream/escapement-llm-conversation
  - upstream/escapement-statechart-model
  - upstream/escapement-multi-agent-and-services
  - upstream/escapement-library-embedding
depends-on:
  - upstream/escapement-llm-conversation
---

# Escapement — Example & Demo Chart Pattern Catalog

> Named units: everything under `escapement.examples.*` and `demos/{lib,matrix_team,unit_test}`.
> **WHY THIS PAGE EXISTS**: the examples directory is upstream's intent documentation. A
> session that reads engine internals but skips the examples WILL misjudge what the
> framework is for (proven: this project concluded "no chat support" twice before reading
> `steered_convo`). Pattern-match your use-case here FIRST; grep the named example against
> live `resource` for the working code.

## λ map (use-case → example, one line each)

```
minimal one-LLM-turn + event-tool exit            → hello         (wrapped-final trick)
modal human prompts (text/confirm/interrupt)      → ask           (human-input, ^:interactive?)
tool-using agent, fan-in events, re-drive loop    → scan          (tell-llm nudge after each finding)
coding-agent loop w/ chart-run tests + retry cap  → iterate       (all-:internal inside binding state)
per-dimension model eligibility gating            → clj_refactor  (:needs × :llm/ratings)
open-ended tool scan, LLM decides when done       → large_files   (:llm.idle termination, :output-ref)
two independent LLM tasks concurrently            → parallel_demo (done.state.* join)
multi-tool single turn characterization           → turn_loop     (Exp A)
INJECT USER MSG into live conversation (CHAT)     → steered_convo (Exp B — THE chat-shaped one)
mid-turn injection latency proof                  → steer_midturn (region-tool probe; same latency)
monitor + steer + capture from sidechart          → supervisor    (Task 005 — 3 jobs on one :llm.idle)
full transcript event spectrum + ordering rule    → inspectable   (Exp C — Task-001 canon)
two-phase convo, phase counter, capture+steer     → inspect_showcase (Task 006)
artifact-linked sequential pipeline               → artifacts_demo ({{writer.md}} template)
dynamic-N subagents, no LLM (minimal multiplex)   → n_subagents_demo (mux/reply, ^:multi-session?)
dynamic-N tournament, multi-phase multiplex       → haiku_tournament_dynamic (production form)
hosted embedding, hermetic creds, streaming       → demos/lib/embed_example (HOW OURO EMBEDS)
two-agent peer loop, typed verdicts, own nREPLs   → demos/matrix_team (verdict-schema routing)
multi-phase pipeline + parallel resource manager  → demos/unit_test (scripted-discovery-then-LLM)
transparent specialist-LLM-as-tool                → chart/consult declare-consultation (lib building block)
```

## Families (learn one member deeply, the siblings are variations)

```
STEERING     steered_convo → steer_midturn → supervisor
             shared: parallel(convo ⊗ monitor) · :llm.idle hook · h/tell-llm · send-after
             safety timer · one-shot :steer-sent? guard · :type :internal on region-root exits
INSPECTION   turn_loop → inspectable → inspect_showcase   (Exp A/B/C + Task 001/005/006/007 series)
             shared: capture on :llm.idle · event-tool transitions :type :internal (ordering rule)
MULTIPLEX    n_subagents_demo (baseline) → haiku_tournament_dynamic (production)
             shared: multiplex · mux/reply · mo/count(runtime fn) · ^:multi-session? · done.invoke.<id>
PEER-AGENTS  matrix_team · unit_test · consult.cljc(library extraction of the pattern)
             shared: parallel regions · typed coordination (:verdict-schema ∨ shared data-model)
BASELINE     hello · ask · large_files · artifacts_demo   (single-conversation intro tier)
MODEL-GATE   clj_refactor · iterate                        (:needs, ratings, real fs tools)
```

## Load-bearing techniques (cross-cutting; each names its proving example)

```
λ techniques. the non-obvious rules the examples exist to teach:
  ordering_rule (Task 001, THE canon — inspectable):
    turn ending via event-tool → chart event arrives BEFORE :llm.idle for that turn
    ⟹ event-tool transitions MUST be :type :internal (else :llm.idle handler torn down,
       capture never fires) | capture on :llm.idle, terminate on :llm.idle
  wrapped_final (hello): top-level final EMPTIES configuration → wrap final under a
    compound parent to keep data model + :final-config readable
  done_state_prefix_wedge (steered_convo): bare :done event prefix-matches reserved
    done.state.* → wedges macrostep | ALWAYS namespace chart events (:count/done)
  region_root_external_transition_conflict (steered_convo): external transition from a
    region root spans the whole parallel → conflicts with sibling region's transitions →
    silently dropped | use :type :internal on region-root exits
  send_after_not_send (steered_convo): raw delayed send outlives the chart (keeps runner
    alive minutes); send-after cancels on state exit — ALWAYS send-after for safety timers
  per_child_timer_hygiene (haiku_tournament): on-entry send + on-exit cancel per child
    state — a stale timer from a fast step must not fire during a later slow step
  re_drive_parked_worker (scan, supervisor): each event-tool call parks the worker
    :awaiting-user; it does NOT auto-resume — h/tell-llm "Continue." nudges the next turn
  plain_text_over_tools_for_small_models (haiku_tournament): small models ~50% reliable at
    tool calls vs 10/10 plain text — allowed-events [] + :llm.idle + defensive parsing
  autoforward_false_for_multiplex_children (haiku_tournament): children reply via mux/reply
    only; autoforward on N children ⇒ N× event churn
  belt_and_braces_termination (steering family): event-tool exit ∧ :safety/stop timer ∧
    :error.llm.max-turns terminator — three independent paths to final
  phase_counter_not_llm_heuristic (inspect_showcase): multi-phase discrimination via
    data-model :phase int + guarded :llm.idle branches — never "ask the LLM what phase"
  template_artifact_linking (artifacts_demo, embed_example): phase-2 :message =
    h/render-template "{{phase1-artifact.md}}" — inter-phase context without data-model writes
  chart_controlled_side_effects (iterate): tests run via tp/dispatch from a script, result
    self-posted via sp/send! — the LLM never sees the test runner
  scripted_cheap_path_before_llm (unit_test): try deterministic discovery (shell + parse)
    first, fire :need-llm event only on miss — LLM as fallback, not first resort
  reusable_chart_segment (unit_test refine-state): def a state element, drop it into a shim
    chart for isolated composition tests
  post_self_event (unit_test): sp/send! to own session-id from on-entry scripts — internal
    routing from scripts; also raise! (haiku_tournament) for same-macrostep routing
```

## Demos-only patterns (absent from src/examples/)

```
demos/lib/embed_example  escapement.lib/run · event-sink :text-delta streaming · hermetic
                         :credentials · :llm/aliases+preferences · READ FIRST for embedding
demos/matrix_team        :verdict-schema typed wrap-up inference (no event-tools at all) ·
                         h/tell-other-llm! (targeted cross-conversation steer by invokeid) ·
                         chart-owned external process lifecycle (nREPL via babashka.process,
                         spawn/watch-port/kill in service region) · pending-eval queue
                         (defer tool replies while resource boots) · idle-from? routing
demos/unit_test          shared-data-model region coordination (:nrepl-port, :repl-status) ·
                         on-entry self-post bypass (resource already available → skip wait) ·
                         prompts in external markdown via p/render-phase (fn [_ data])
```

## chart/consult.cljc — the reusable specialist-as-tool block

`declare-consultation` → a drop-in state element: asker LLM calls `region__<tool>`;
element `tell-other-llm!`s the request to an owned specialist conversation; on the
specialist's `:llm.idle` (verdict via `:verdict-schema`) it `service/post-reply`s the JSON
verdict as the asker's tool_result. Asker never knows another LLM exists. Wire via
`:chart-tools [{:owner <state-id>}]`. `:error.llm` while pending → error tool_result (asker
never hangs). This is `matrix_team`'s pattern, extracted; no current example uses it.

## Task/Exp numbering key

Exp A/B/C = turn_loop / steered_convo / inspectable (convo-turn-experiments workstream).
Task 001 = the ordering rule (cited as invariant across the steering+inspection families).
Task 005/006/007 = supervisor / inspect_showcase / the live validation runs. The numbering
marks charts that PROVE a specific LLM↔statechart interaction property — treat those
docstrings as spec text, not commentary.

## Stale-check markers

```
λ stale_check. grep-miss ⟹ stale:
  example set        : 16 files in src/escapement/examples/ + demos/{lib,matrix_team,tools,unit_test}
  consult unused     : declare-consultation referenced by no example (a first user may exist by now)
  ordering rule      : Task-001 comments in inspectable/steered_convo still assert
                       event-tool-event-BEFORE-:llm.idle
  small-model finding: haiku_tournament docstring still cites ~50% tool-call reliability
                       (llama3.2:3b era — may improve with newer small models)
```
