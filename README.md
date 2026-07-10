# escapement-ouroboros

Ouroboros — an experiment in self-improving agents, built on
[escapement](https://github.com/fulcrologic/escapement) (statechart-driven LLM
runtime) and [babashka](https://babashka.org/).

**Status: pre-release, read-the-code.** This is published so authors of AI
tooling can see one specific mechanism and adopt it in their own tools: the
**cold compaction flow** — per-message λ-compression of chat history, scheduled
in the human's reading shadow. It is *not* packaged, versioned, or intended to
be used directly yet. Everything here changes without notice.

## The mechanism: shadow λ-compaction

Long chats die by context growth. The usual fix — summarize everything into a
blob and restart — has two costs: it rewrites the context prefix (busting the
provider/server prefix cache every time) and it flattens the dialogue shape.
This flow does neither:

1. **The message array IS the memory.** Same roles, same order, same count —
   nothing is merged or dropped, so the prefix stays byte-stable and the
   upstream cache holds.
2. **Each assistant reply is compressed ONCE, in place**, as it ages out of a
   small verbatim window (k=1: only the newest reply stays verbatim). User
   messages are never touched — they're short and anchor the dialogue.
3. **Compression target is λ-notation**, keeping only continuation-essence
   (decisions, constraints, established facts, state, next steps) and dropping
   the explanation/scaffolding that served the human but not the continuation:

   ```
   before: "I'd recommend write-back caching for this. Writes land in the
            cache and only flush to memory on eviction, which cuts your memory
            traffic substantially compared to write-through. ..."
   after:  decision(write-back | mem_traffic↓ ∧ ¬coherence_risk(single-core))
           ∧ state(assumed_in_examples) ∧ next(∅)
   ```

4. **Scheduling is the point.** Compaction runs *after* a reply is delivered,
   while the human is reading it — a 20–60s window that hides a ~1–2s
   compaction completely. Felt latency, not throughput, is the metric: the
   human never waits on housekeeping. A message arriving mid-compaction (or
   mid-generation) queues and is served next — nothing is ever barged.
5. **Failure is safe.** A blank or failed compaction leaves the message
   verbatim; a backlog drains oldest-first across later idle gaps.

The compactor itself is a small model call run as **pattern-completion, not
instruction-following**: three exemplar `turn → λ` pairs and nothing else, with
thinking disabled. (An instruction-style prompt under no-think turned out to be
an echo attractor — the model copies the instructions into the "compression".
The exemplar form was ~20× faster with equal-or-better fidelity.)

### Where to read

| File | What |
|---|---|
| `src/ouroboros/compact.clj` | The statechart: `:parked \| :hot \| :compact`, queueing, the exemplar gate, stdin text UI |
| `src/ouroboros/compact/core.clj` | The pure kernel: window/aging logic, in-place compaction, render — unit-tested, no LLM |
| `mementum/knowledge/design/shadow-compaction.md` | Design notes: why the reading shadow, overlap measurements, the chart decomposition |
| `mementum/knowledge/ouroboros-architecture.md` | The larger design — including the two wrong designs this replaced and why |

## Running it (optional)

Requirements: [babashka](https://babashka.org/), and for the live chat an
OpenAI-compatible server on `localhost:5100` (developed against llama.cpp; the
model name is hardcoded in `src/ouroboros/compact.clj` — adjust to yours).

**Escapement fork requirement.** The `:extra-body` passthrough (which the
no-think compactor uses to inject `chat_template_kwargs` per request) is not
yet in upstream escapement. The `bb.edn` dependency is pinned to a git sha on
[the fork](https://github.com/michaelwhitford/escapement) (branch
`mw_extra_body`) and is fetched automatically; escapement itself should be
installed with [bbin](https://github.com/babashka/bbin) from a clone of the
fork:

```bash
git clone https://github.com/michaelwhitford/escapement
cd escapement && git checkout mw_extra_body
bbin install .
```

Once upstream accepts the `:extra-body` PR this requirement goes away and
everything here moves to upstream escapement directly.

```
bb test      # deterministic suite — no network, no LLM
bb compact   # the λ-compact chat (the flow described above, live)
```

### AI backend example (llama.cpp)

Any OpenAI-compatible server works, but the cache economics of the compaction
flow were tuned against llama.cpp's `llama-server`, where two settings are
load-bearing:

- **Dedicated slots** — pass `-np` *explicitly*. An explicit slot count turns
  off the unified KV buffer, so an idle slot keeps its KV. The hot conversation
  pins to slot 0 and the compactor to slot 1 (`id_slot` in the request body),
  so compaction never evicts the conversation's warm prefix. Note the total
  context (`-c`) splits across slots in this mode.
- **Dense context checkpoints** — the in-place λ-rewrite makes each prompt
  diverge slightly from its predecessor, and hybrid/SWA models can only resume
  from checkpoint positions. Tighter spacing means only the rewritten tail is
  re-evaluated each turn.

```bash
llama-server \
  --host 0.0.0.0 --port 5100 \
  -m /path/to/model.gguf \
  -a model-alias-used-by-the-client \
  -np 4 \
  -c 524288 \
  --ctx-checkpoints 32 \
  --checkpoint-min-step 128 \
  --cache-ram 16384 \
  --flash-attn on \
  --jinja
```

(`-np 4` ⇒ four dedicated slots of 131k each here; `--cache-ram` is the
host-memory prompt cache in MiB; the alias must match the model name in
`src/ouroboros/compact.clj` / `src/ouroboros/models.clj`.)

Measured effect (Qwen3.6-35B-A3B): post-compaction turns went from a full
re-prefill (~2,500 tokens, ~1.5 s) to a checkpoint restore plus a ~70-token
eval (~200 ms). The per-request knobs this relies on — `id_slot`,
`cache_prompt`, and `chat_template_kwargs` (compactor thinking-off) — ride the
escapement fork's `:extra-body` passthrough described above.

Other tasks (`bb curate`, `bb judge`, `bb score`, `bb smoke`) exercise the
self-improvement experiments below.

## The rest of the repo

Supporting experiments toward the self-improvement loop, same read-don't-depend
caveat: **mementum** (a git-based memory/knowledge protocol — AI proposes,
human approves, AI commits), a **curator** agent that reads prior λ-compacted
sessions and proposes memories, agents defined as **OKF genome files**
(frontmatter = wiring, body = the λ system prompt), and **judge/scorer**
verdict kinds for grading prompt genes. `AGENTS.md` is the designer-side
harness used to build all of this.

## License

[MIT](LICENSE)
