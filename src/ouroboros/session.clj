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
  .gitignore). What actually gets committed is a per-session human choice."
  (:require
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
