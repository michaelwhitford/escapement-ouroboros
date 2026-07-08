(ns ouroboros.session
  "Durable session location — λ path(session-id) → <root>/sessions/<id>/.

  Escapement already CREATES the session automatically: given a session-dir it
  mkdirs the transcript sink, snapshots a full working-memory checkpoint after
  EVERY event, and captures artifacts (capture-llm-output). The ONLY thing it
  defaults to disposable is the LOCATION — absent explicit paths it drops
  everything in a throwaway `escapement-run-<rand>` temp dir.

  So durability is one decision, not a persistence layer: point escapement at a
  STABLE path and reuse the same :session-id (+ :resume?) to continue it.

    λ session(id).  dir      = <root>/sessions/<id>/
                    brief    = <dir>/artifacts/brief.md   ← the compiled λ (durable memory)
                    transcript = <dir>/transcript.jsonl   ← raw JSONL (gitignored; regenerable)
                    checkpoints/ = <dir>/checkpoints/     ← per-event WM snapshots (gitignored)

  The BRIEF is the durable residue both readers want (next chat bootstrap ∧ the
  improver). The transcript/checkpoints are fat and regenerable → gitignored (see
  .gitignore). What actually gets committed is a per-session human choice.

  READING BACK (the improver + next-chat bootstrap): escapement snapshots the
  whole working-memory data-model to `checkpoints/<id>.edn` after every event.
  The compact chat's λ conversation lives there as the data-model `:messages`
  vector. `session-messages` re-derives it — this layout knowledge lives here so
  both the improver (metabolize across sessions) and the bootstrap (seed a fresh
  chat from a prior tail) share ONE reader."
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]))

(def ^:private brief-rel "artifacts/brief.md")

(defn session-dir
  "Absolute path to session `id`'s durable dir under `root` (default \".\").
  Idempotent side effect: ensures artifacts/ exists and seeds an EMPTY
  artifacts/brief.md when absent, so the hot side's {{brief.md}} template
  resolves from turn 0 (render-template fails on a missing artifact). An
  EXISTING brief is left untouched — reusing an id + :resume? continues it."
  ([id] (session-dir "." id))
  ([root id]
   (let [dir   (.getPath (io/file root "sessions" (str id)))
         brief (io/file dir brief-rel)]
     (io/make-parents brief)
     (when-not (.exists brief) (spit brief ""))
     dir)))

(defn brief-path
  "Absolute path to session `id`'s compiled-λ brief artifact."
  ([id] (brief-path "." id))
  ([root id] (.getPath (io/file (session-dir root id) brief-rel))))

;; ---------------------------------------------------------------------------
;; Reading sessions back — checkpoint → data-model → :messages.
;; The improver metabolizes these; the next-chat bootstrap re-seeds from them.
;; ---------------------------------------------------------------------------

;; escapement snapshots the whole working memory here. Kept as a literal FQ
;; keyword (matches the codebase pattern — cf. compact/event-queue-key) so we
;; needn't require the statecharts data-model root ns under bb.
(def ^:private data-model-key
  :com.fulcrologic.statecharts.data-model.working-memory-data-model/data-model)

(defn list-session-ids
  "Session ids (dir names) under `<root>/sessions/`, sorted ascending. `[]` when
  the sessions dir is absent. Includes ALL sessions (chat/compact/loop/…) — the
  caller filters (e.g. the improver keeps only those with a `:messages` array)."
  ([] (list-session-ids "."))
  ([root]
   (let [d (io/file root "sessions")]
     (if (.isDirectory d)
       (->> (.listFiles d)
         (filter #(.isDirectory ^java.io.File %))
         (mapv #(.getName ^java.io.File %))
         sort vec)
       []))))

(defn checkpoint-file
  "The checkpoint EDN `File` for session `id` (`<dir>/checkpoints/<id>.edn`), or
  nil when absent. Escapement names the checkpoint after the session id."
  ([id] (checkpoint-file "." id))
  ([root id]
   (let [f (io/file root "sessions" (str id) "checkpoints" (str id ".edn"))]
     (when (.exists f) f))))

(defn read-data-model
  "Parse session `id`'s checkpoint and return the statechart working-memory
  data-model map, or nil when the checkpoint is absent/unreadable. Unknown
  tagged literals are read leniently (tag dropped → value) so a future
  escapement checkpoint shape can't crash the reader."
  ([id] (read-data-model "." id))
  ([root id]
   (when-let [f (checkpoint-file root id)]
     (try
       (get (edn/read-string {:default (fn [_tag v] v)} (slurp f)) data-model-key)
       (catch Exception _ nil)))))

(defn session-messages
  "The λ-compacted `:messages` vector from session `id`'s checkpoint, or `[]`
  when absent. Each msg: `{:role :user|:assistant :text <prose-or-λ> :compacted? bool}`.
  This is the cross-session memory the improver reads."
  ([id] (session-messages "." id))
  ([root id] (or (:messages (read-data-model root id)) [])))
