---
type: mementum/knowledge
title: Escapement — Tools (Protocol, Built-ins, Registration)
description: Real tools (Tool protocol) are dispatched in the worker and invisible to the chart; a registry maps tool-name keywords to tools; conversations whitelist via :real-tools.
resource: https://github.com/fulcrologic/escapement
tags: [escapement, tools, registry, protocol, builtins]
status: active
category: upstream
related:
  - upstream/escapement-statechart-model
  - upstream/escapement-llm-conversation
  - upstream/escapement-multi-agent-and-services
depends-on:
  - upstream/escapement-statechart-model
---

# Escapement — Tools (Protocol, Built-ins, Registration)

> Named units: `escapement.tools.protocol`, `escapement.tools.builtin`. These are **real
> tools** — dispatched inside the worker, invisible to the chart. (Event tools and region
> tools are covered elsewhere.)

## The `Tool` protocol

```clojure
(defprotocol Tool
  (tool-name    [t])    ; keyword, e.g. :fs/read
  (description  [t])    ; short string for the LLM
  (input-schema [t])    ; Malli schema
  (invoke       [t input]))  ; → {:result <string> :is-error <bool>}
```

```
λ tool_contract.
  invoke ALWAYS returns {:result string :is-error bool} | NEVER throws to caller
  | a registry ≡ atom of {tool-name → tool}
  | tp/dispatch validates input vs schema BEFORE invoke
    → validation failure → {:result <pretty error> :is-error true}  (LLM gets corrective tool_result)
  | tp/tool->anthropic-tool-def → JSON Schema via malli.json-schema/transform
    → qualified keywords flattened: :fs/read → "fs_read"
```

## The two registries

```
λ registries.
  builtin/default-registry      → defonce SINGLETON atom, seeded with built-ins on load
                                 | the CLI uses this
                                 | top-level register! in any required ns is visible (side-effect-on-require)
  builtin/new-builtin-registry  → FRESH atom each call, pre-seeded with built-ins
                                 | use for ISOLATION: tests, multi-tenant hosts, parallel runs w/ disjoint sets
  tp/new-registry               → empty registry (no built-ins)
```

## Built-in tools (bb-resident, Claude-Code-parity ergonomics)

| Tool | Input | Behavior |
|---|---|---|
| `:fs/read` | `{:path :string :offset? pos-int :limit? pos-int}` | `cat -n` style (6-digit line nums + tab). Default 2000-line window; `... [N more lines; read with :offset M]` overflow notice. Files >200 KiB byte-truncated first. |
| `:fs/write` | `{:path :string :content :string}` | Atomic UTF-8 (temp + `ATOMIC_MOVE`). Creates parent dirs. |
| `:fs/edit` | `{:path :old-string :new-string :replace-all? bool}` | `old-string` must occur **exactly once** by default (forces unambiguous context). `replace-all true` reports count. Identical old/new rejected. Indentation must match byte-for-byte. |
| `:fs/multi-edit` | `{:path :edits [{:old-string :new-string :replace-all?} …]}` | Atomic batch; applied in order in-memory; later edits see earlier output. Written only if ALL succeed; failure reports `edit #N of M`, file unchanged. |
| `:fs/glob` | `{:pattern :cwd? :limit? :by-mtime?}` | `PathMatcher` walk. Matches `**/*.foo` at root AND nested (also tries suffix w/o leading `**/`). mtime-sorted by default; cap `limit` (200). Absolute paths. |
| `:fs/grep` | `{:pattern :path? :glob? :output-mode? :ignore-case? :context? :limit?}` | `rg` if on PATH else `grep -rE`. Modes: `files-with-matches` (default), `content`, `count`. 20s timeout. `[no matches]` sentinel. |
| `:shell/run` | `{:command :string :timeout-ms? int}` | `bash -lc`, default 30s. Combined stdout+stderr + exit code; `is-error` on non-zero/timeout. |
| `:web/search` | — | Google search via Gemini `google_search` grounding. **Only registered when `GEMINI_API_KEY` set.** |
| `:web/fetch` | — | HTTP GET streamed to temp file under `$TMPDIR/escapement-fetch/`; returns metadata so LLM reads slices off disk. |

> Tool count: the fs/shell set is 7; `:web/*` are conditionally registered. The Guide
> alternately says "eight built-ins" / "five builtins" / lists nine — treat the table above
> as the working list and verify live with `(keys @registry)`.

## Exposing a tool to a conversation

```
λ visibility. by default a conversation's :llm-conversation exposes EVERY real tool in registry
  | :real-tools [:fs/read :http/get] (vector or set) → whitelist subset
  | tools the LLM never sees are STILL in registry, STILL dispatchable from chart-side scripts
    (e.g. the iterate example runs :shell/run from a script action, keeping test output out of the LLM loop)
```

## Registering a custom tool

```clojure
(ns my.app.tools
  (:require [escapement.tools.builtin :as builtin]
            [escapement.tools.protocol :as tp]))

(defrecord HttpGetTool []
  tp/Tool
  (tool-name    [_] :http/get)
  (description  [_] "GET a URL and return the body as a string.")
  (input-schema [_] [:map {:closed true} [:url :string]])
  (invoke [_ {:keys [url]}]
    (let [{:keys [status body]} (http/get url)]
      {:result body :is-error (not (<= 200 status 299))})))

;; Top-level: registers on ns load. Idempotent (registry keyed by tool-name).
(tp/register! builtin/default-registry (->HttpGetTool))
```

Then `(:require [my.app.tools])` from the chart ns → require triggers registration →
`:real-tools [:http/get]` works. `runner/load-chart` does `require` on the chart ns, so
transitive requires register tools before `runner/run!`.

### Three wiring strategies

```
λ wiring.
  A. require-from-chart  → self-register on load (CLI default pattern)
  B. --tools-ns sym      → CLI calls (your-fn registry-atom); explicit manifest at entry point
  C. drive-yourself      → own bb script: share singleton (A) OR fresh new-builtin-registry (isolation)
                         → pass :tool-registry to runner/run! (or escapement.lib/run)
```

> For `escapement.lib/run`: the facade wires the LLM processor **only when BOTH a backend
> AND a `:tool-registry` are present**. Pass `new-builtin-registry` for hermetic isolation.
