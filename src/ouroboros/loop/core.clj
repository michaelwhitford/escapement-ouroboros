(ns ouroboros.loop.core
  "Pure metabolize kernel for the improver (Loop B). Turns prior sessions'
  λ-compacted message arrays into a digest the improver LLM pattern-matches over
  to propose memories (and, later, knowledge pages).

  No LLM, no escapement, no IO — callers pass already-loaded session data
  (`{:id :messages}`, loaded via `ouroboros.session/session-messages`). House
  convention `<engine>.core` (mirrors `ouroboros.compact.core`): the impure
  chart/tool layers sit above this fully-unit-testable core.

  λ metabolize.  the assistant turns marked λ ARE the compacted essence — the
  cross-session memory. Rendering them together lets the improver spot recurring
  topics/decisions (≥3 → a knowledge-page candidate) grounded in what actually
  happened, not fabricated."
  (:require
    [clojure.string :as str]))

(defn recency-key
  "Sort key for a session id: its trailing epoch-millis digits (0 when absent).
  Session ids look like \"compact-1783525397252\" / \"chat-1783486440175\" — the
  numeric suffix orders them across differing prefixes."
  [id]
  (or (some-> (re-find #"\d+$" (str id)) parse-long) 0))

;; Bound a single message's contribution — λ lines + user anchors are short, but
;; a still-verbatim (in-window) assistant reply can be long; clip so the digest
;; stays bounded across many sessions.
(def ^:private max-msg-chars 600)

(defn- clip [s]
  (let [s (str/trim (or s ""))]
    (if (> (count s) max-msg-chars)
      (str (subs s 0 max-msg-chars) " …[clipped]")
      s)))

(defn- render-msg
  [{:keys [role text compacted?]}]
  (let [tag (cond (= :user role) "user"
                  compacted?     "λ   "        ; the compacted essence — metabolize target
                  :else          "asst")]      ; still-verbatim (in the k-window)
    (str "  " tag " | " (clip text))))

(defn render-session
  "Pure: a session's `id` + `:messages` → an ordered, role-tagged text block.
  Compacted assistant turns are marked `λ` (the essence to metabolize)."
  [id messages]
  (let [n  (count messages)
        nc (count (filter :compacted? messages))]
    (str "SESSION " id " (" n " msgs, " nc " λ-compacted):\n"
      (str/join "\n" (map render-msg messages)))))

(defn sessions-digest
  "Pure: seq of `{:id :messages}` (already loaded, newest LAST) → a metabolize
  digest across sessions. Empty-safe."
  [sessions]
  (if (seq sessions)
    (str/join "\n\n" (map (fn [{:keys [id messages]}] (render-session id messages)) sessions))
    "(no prior conversation sessions yet)"))
