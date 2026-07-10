(ns ouroboros.compact.core
  "Pure message-list logic for the λ-compacted conversation — no LLM, no
  escapement, fully unit-testable.

  THE IDEA (grounded in ~/src/nucleus/LAMBDA-COMPILER.md + eca/prompts/compact.md):
  a chat's assistant turns generate many tokens the HUMAN needs (explanation,
  scaffolding) but the CONTINUATION does not. So we keep the message array shape
  intact — same roles, order, count — and compress each ASSISTANT message's prose
  into λ ONCE, as it ages out of a small verbatim window (k). User messages stay
  verbatim (short; they anchor the dialogue).

  WHY per-message (not one summary blob): a growing summary in the system prompt
  rewrites the shared prefix every turn → busts the upstream prefix cache every
  turn. Compacting each assistant message in place, ONCE, keeps the prefix STABLE
  → cache holds. The conversation is still there, just λ-dense.

  Canonical message:  {:role :user|:assistant :text <prose-or-λ> :compacted? bool}
  The data-model holds a vector of these; the chart renders it into escapement's
  `:initial-messages` shape each turn and seeds a FRESH worker with it."
  (:require
    [clojure.string :as str]))

(defn message
  "A fresh (verbatim, not-yet-compacted) canonical message."
  [role text]
  {:role role :text text :compacted? false})

(defn append-user      [messages text] (conj (vec messages) (message :user text)))
(defn append-assistant [messages text] (conj (vec messages) (message :assistant text)))

(defn render-messages
  "Canonical :messages → escapement `:initial-messages` shape: a vector of
  `{:role .. :content [{:type :text :text s}]}`. Old assistant turns carry λ
  text, recent ones + all user turns carry verbatim text — but the SHAPE is
  identical, which is what keeps the upstream prefix stable/cacheable."
  [messages]
  (mapv (fn [{:keys [role text]}]
          {:role role :content [{:type :text :text text}]})
    messages))

(defn- assistant-indices
  "Indices of assistant messages, ascending."
  [messages]
  (vec (keep-indexed (fn [i m] (when (= :assistant (:role m)) i)) messages)))

(defn next-to-compact
  "Index of the assistant message due for λ-compaction, or nil.

  DUE ≡ an assistant message that is (a) NOT yet compacted AND (b) NOT among the
  last `k` assistant messages (it has aged out of the verbatim window). With
  k=1 the single most-recent assistant reply stays verbatim in context; the one
  that just aged behind it is due. Oldest-eligible first, so a lagging backlog
  drains in order."
  [messages k]
  (let [a-idxs (assistant-indices messages)
        window (set (take-last k a-idxs))]
    (->> a-idxs
      (remove window)
      (remove #(:compacted? (nth messages %)))
      first)))

(defn needs-compaction?
  "True iff some assistant message has aged out of the k-window and is still verbatim."
  [messages k]
  (some? (next-to-compact messages k)))

(defn compact-target-text
  "Verbatim text of the assistant message due for compaction, or nil. This is
  the input the compactor compresses into λ."
  [messages k]
  (when-let [i (next-to-compact messages k)]
    (:text (nth messages i))))

(defn apply-compaction
  "Replace the due assistant message's text with `lambda` and mark it compacted.
  No-op (returns the vector) if nothing is due or `lambda` is blank — a blank
  λ leaves the message verbatim (safe under compactor lag/failure). A leading
  \"λ:\" label (the exemplar gate's answer marker, which the model sometimes
  repeats) is stripped so stored λ text is uniform; if stripping leaves nothing,
  that too is a failed compaction → verbatim."
  [messages k lambda]
  (let [messages (vec messages)
        lambda   (some-> lambda str/trim (str/replace-first #"^λ:\s*" "") str/trim)]
    (if-let [i (and (not (str/blank? lambda)) (next-to-compact messages k))]
      (assoc messages i (assoc (nth messages i) :text lambda :compacted? true))
      messages)))

;; ---------------------------------------------------------------------------
;; Echo line discipline — the text-UI rendering kernel (pure).
;;
;; The tap streams token deltas; models pad prose with blank lines and trailing
;; whitespace, and tool round-trips interleave. This kernel makes the terminal
;; UI uniform: every emitted LINE begins with a role prefix, newline RUNS
;; collapse to one, whitespace-only lines vanish, leading blanks are stripped,
;; and trailing whitespace/newlines are never emitted (newlines are DEFERRED —
;; only realized when more content actually arrives).
;;
;; Pure fold: state × chunk → {:state s' :out str}. The impure edge (compact.clj
;; transcript-tap) holds the state in an atom and prints :out.
;; ---------------------------------------------------------------------------

(def echo-init
  "Fresh echo state: no content emitted yet, no deferred newline, no pending
  intra-line whitespace."
  {:begun? false :pending-nl? false :ws ""})

(defn echo-text
  "Fold `chunk` (a streamed text delta, possibly empty/nil) through the line
  discipline. Returns {:state s' :out str} where :out is exactly what should be
  printed now. `prefix` starts every emitted line (e.g. \"assistant: \").

  Rules: newline runs → ONE deferred newline (emitted only before the next
  content char, so trailing newlines never print); whitespace at line
  boundaries is dropped (whitespace-only lines vanish, trailing spaces
  stripped) but INTRA-line whitespace — including code indentation following
  content on the same line — is preserved via the :ws buffer."
  [state prefix chunk]
  (loop [st  state
         cs  (seq (str chunk))
         out (StringBuilder.)]
    (if-let [[c & more] cs]
      (cond
        (= c \newline)
        (recur (assoc st :pending-nl? (:begun? st) :ws "") more out)

        (or (= c \space) (= c \tab) (= c \return))
        (recur (update st :ws str c) more out)

        :else
        (do
          (when (:pending-nl? st) (.append out "\n"))
          (when (or (:pending-nl? st) (not (:begun? st))) (.append out prefix))
          (.append out (:ws st))
          (.append out c)
          (recur (assoc st :begun? true :pending-nl? false :ws "") more out)))
      {:state st :out (str out)})))

(defn echo-break
  "Close any open output line (before a tool line, or at end of turn): emits a
  single newline iff content was emitted this segment, and resets the state so
  the next text starts a fresh prefixed line. Returns {:state echo-init :out}."
  [state]
  {:state echo-init :out (if (:begun? state) "\n" "")})

(defn- truncate-str
  [s max-len]
  (if (> (count s) max-len) (str (subs s 0 max-len) "…") s))

(defn tool-line
  "One-line rendering of a tool CALL for the text UI (the result is
  deliberately not shown): \"tool: :fs/read {:path \\\"idea.md\\\"}\".
  `input` is pr-str'd and truncated to `max-len` chars (default 160) so a
  large :fs/write payload cannot flood the terminal."
  ([tool input] (tool-line tool input 160))
  ([tool input max-len]
   (str "tool: " tool
     (when (some? input)
       (str " " (truncate-str (pr-str input) max-len))))))
