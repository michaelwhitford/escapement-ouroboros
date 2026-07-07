---
type: mementum/index
title: Escapement — Knowledge Index
description: Map of the escapement knowledge set — Ouroboros's runtime; start here, then drill into one page, then re-derive against live source.
resource: https://github.com/fulcrologic/escapement
tags: [escapement, index, map]
status: active
category: upstream
related:
  - upstream/escapement-overview
  - upstream/escapement-statechart-model
  - upstream/escapement-llm-conversation
  - upstream/escapement-tools
  - upstream/escapement-multi-agent-and-services
  - upstream/escapement-backends
  - upstream/escapement-library-embedding
  - upstream/escapement-transcript-runner-cli-testing
  - upstream/escapement-web-ui
depends-on: []
---

# Escapement — Knowledge Index

Escapement is **Ouroboros's runtime** (`runtime ≡ VSM`). These pages are durable
understanding artifacts: they name concepts as prose and point only at the canonical
`resource` (the escapement repo). To reach ground truth, grep a named thing against the
**live** source — never trust a stored coordinate, because coordinates rot and names endure.

## The map (each entry is its page's `description`)

```
λ map. (projection of every page's frontmatter description)

escapement-overview
  → Escapement makes control flow a statechart, not an LLM loop — the chart, not the
    model, decides next; an LLM conversation is bound to a chart state.

escapement-statechart-model
  → A worker thread holds the LLM while its chart state is active; the chart sees only
    declared event-tools, never real tools; :type :internal keeps the worker alive across hops.

escapement-llm-conversation
  → The :llm-conversation invocation and its flat authoring keys; model selection =
    :needs eligibility gate × :llm/preferences ranking × keyword aliases.

escapement-tools
  → Real tools (Tool protocol) are dispatched in the worker and invisible to the chart;
    a registry maps tool-name keywords to tools; conversations whitelist via :real-tools.

escapement-multi-agent-and-services
  → :target-routed messages, typed verdicts, file-backed artifacts, and service regions
    that expose subcharts as synchronous region-tools.

escapement-backends
  → The LLMBackend protocol and its implementations (Anthropic, OpenAI, codex OAuth),
    the multi-backend dispatcher, content-addressed cache, and provider auto-detection.

escapement-library-embedding
  → escapement.lib/run is the hermetic embedding entry point — the host injects
    :credentials and :config as data; no disk or env reads on the lib path. HOW OURO EMBEDS.

escapement-transcript-runner-cli-testing
  → JSONL single-writer transcript, the runner pump loop, CLI flags, .escapement.edn
    config, and the bb-friendly synchronous test harness.

escapement-web-ui
  → The bundled web UI is a read-only inspector (Fulcro SPA, JVM-built) — but ui.server +
    ws-push are bb-safe and embeddable; NO route injects arbitrary chart events, so inbound
    reaches a chart only via the HumanRenderer promise path or a host-captured queue
    (:on-env-ready → sp/send!).
```

## Reading paths

```
λ path(goal).
  understand_whole      → overview → statechart-model → llm-conversation → (siblings as needed)
  author_a_chart        → statechart-model → llm-conversation → tools → examples hello/scan/iterate
  embed_in_ouroboros    → library-embedding → backends → overview(arch boundary) → the lib demo
  multi_agent_team      → multi-agent-and-services → llm-conversation(tell-llm, verdicts)
  debug_a_run           → transcript-runner-cli-testing (jq recipes, event vocab) → statechart-model
  pick_a_model          → llm-conversation(model selection) → backends(providers, catalog)
  web_chat_channel      → web-ui(ingress seams) → library-embedding(:on-env-ready, :transcript-tap)
                          → statechart-model(wait-state authoring)
```

## The five load-bearing invariants (memorize)

```
1. :type :internal keeps the LLM worker alive across state hops. External transition kills+respawns it.
2. The chart sees ONLY declared event-tools; real tools are invisible (dispatched in worker).
3. Read assistant output via h/deref-output — never :text (replaced by :output-ref under ArtifactStore).
4. KEYWORD is the only legal model reference — strings are categorized errors. Aliases are the SoT.
5. escapement.lib/run is HERMETIC: no disk, no env on the lib path; host injects :credentials + :config.
```

## Stale-check markers (facts that drift — re-verify against live `resource`)

```
λ stale_check. these were true when synthesized; git history of resource reveals drift:
  statecharts version : com.fulcrologic/statecharts 1.4.0-RC13
  default aliases     : :default-glm :default-sonnet :default-opus :default-gpt
                        (the escapement.llm.preferences default-aliases / default-preferences)
  default models seen : glm-5.1/4.7, claude-sonnet-4-7, claude-opus-4-7, gpt-5
  built-in tool count : Guide says variously 5/8/9 — verify live with (keys @registry)
  status              : "prototype / not even alpha" — breaking changes expected
SIGNAL of staleness: a named concept greps to nothing in live source ⟹ this page may be stale.
```
