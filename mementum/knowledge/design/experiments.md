---
type: mementum/knowledge
title: Experiments — declarative suite-as-EDN, one runner, results feed the system
description: A self-improving agentic system must be able to CREATE experiments and GET results, so experiments are first-class artifacts — a suite is a declarative EDN file ({hypothesis, conditions, subjects, matrix, measure, verdict}) in experiments/ (TRACKED, the lab notebook), swept by ONE parameterized runner (ouroboros.experiment + pure core; bb experiment <slug>) where a new experiment type is a new EDN file not new code; measures are an open dispatch slot (edn-malli built; scorer/judge genomes route through the verdict topology later); raw results persist to experiments/results/ (gitignored — machine observation), while CONCLUSIONS promote through the human gate into the suite's verdict field and knowledge pages; the founding suite edn-signal-emission ports the 3-round scratch A/B and re-validates its conclusion in one command whenever models or prompts change — this is the measurement substrate the editor kind's champion/challenger termination protocol requires.
resource: file:///Users/mwhitford/src/escapement-ouroboros
status: active
category: design
tags: [ouroboros, design, experiments, empirical, ab-testing, suite, runner, measurement, anima, editor, fitness]
related:
  - design/signals
  - design/agent-model
  - design/vsm-on-escapement
depends-on:
  - design/agent-model
---

# Experiments — declarative suites, one runner

> BUILT (same session as designed): `ouroboros.experiment` (impure edges) +
> `ouroboros.experiment.core` (pure kernel) + `experiments/edn-signal-emission.edn`
> (founding suite) + `bb experiment <slug>`. Lineage: Anima `designs/experiments.md`
> (~/src/anima) + the ab_edn_signal scratch arc this machinery generalizes.

## Why

```
λ prove(x). hypothesis(x) → run(matrix) → measure(result) → decide | empirical > theoretical
  | self-improvement REQUIRES measurement: the editor kind's champion/challenger +
    pairwise + regression-guard termination protocol (vsm-on-escapement) IS experimentation
  | scratch harnesses answer once then rot; suites RE-RUN — new model on a port ⇒
    bb experiment <slug> re-validates every conclusion that model touches
  | an agent that can author a suite EDN + read results does EMPIRICAL self-improvement
```

## The shape (Anima's decisions, kept)

```
λ suite ≡ EDN file experiments/<slug>.edn — TRACKED (the lab notebook):
  {:experiment/id kw :experiment/type kw
   :experiment/hypothesis str          ; what this tests
   :experiment/verdict str?            ; the HUMAN-GATED banked conclusion (updated in place)
   :experiment/measure kw              ; dispatch into core/measures
   :measure/schema any? :measure/echo-substrings [str]?
   :matrix {:models [alias…] :thinking [bool…] :repeats n?}
   :conditions {kw {:system str}} :subjects {kw str}
   :prompt-template str}               ; %s ← subject text
  | envelope CLOSED (Malli, fail-loud) — a suite is wiring, unknown key ≈ typo
  | new experiment TYPE ⇒ new EDN (∧ maybe a new measure) | ¬new runner code (λ extend)

λ runner. bb experiment <slug> → load+validate → expand matrix (model×thinking×
  condition×subject×repeat) → run cells (ellm/ask; endpoints from ouroboros.models/table;
  no-think via :extra-body chat_template_kwargs) → assess (measure dispatch) →
  per-row lines + summary + hypothesis/verdict echo → persist rows

λ results. experiments/results/<slug>-<epoch>.edn — GITIGNORED (machine observation,
  sessions/ pattern) | conclusions promote through the HUMAN GATE → suite :verdict +
  knowledge pages | later: each run also emits an :experiment/result SIGNAL
  (design/signals) so agents can query past outcomes
```

## Measures — the open slot

```
:edn-malli        BUILT — strip fences → edn/read-string (FIRST form: dropped-brace
                  outputs parse as a keyword and fail validity, by design) → Malli
                  validate → humanized errors | + echo-substring detection
:scorer-genome    PLANNED — route output through the verdict topology (gene-scorer)
:judge-genome     PLANNED — pass/fail gate via llm-judge
:pairwise         PLANNED — A-vs-B selection (the GA/editor need; store absolute,
                  CHOOSE pairwise — agent-model §scorer-hazard)
```

## Anima's two scales (adopt at the gene era)

```
:gene-mutation  mutate ONE gene → test variants → score → fitness of THE GENE
:composition    assemble genes → test the whole prompt via the REAL assembly pipeline →
                fitness of THE ORGANISM (a gene can win alone and hurt assembled)
```

## Founding suite — edn-signal-emission

Ports scratch rounds 1-3 (ab_edn_signal{,2,3}.clj — kept, house convention). Verdict:
filled-exemplar + no-think sweeps prose instruction; prose fails STRUCTURALLY (JSON
drift, dropped braces); preamble is load-bearing; second confirmation of
prompt-topology-must-match-thinking. LIVE-PROVEN through the runner: re-run reproduced
the direction (template-ex 11/12 vs prose 9/12; parse 12/12 vs 10/12 — per-run noise
within tolerance, structural findings stable).

## Open questions

```
· statistical discipline — n per cell is small; repeats + a significance note in
  :verdict when suites start deciding CLOSE calls (current calls are 12/12-vs-9/12 obvious)
· suite authorship by agents — an experimenter grant (write experiments/, run bb
  experiment) is a capability like any other; human-gated verdict stays the invariant
· cost guard — a matrix bomb (models×conditions×subjects×repeats) needs a cell-count
  ceiling before agents author suites
· escapement-hosted trials — cells via lib/run (sessions, transcripts, artifacts) when
  an experiment needs TOOLS or multi-turn behavior; ellm/ask suffices for single-turn
```
