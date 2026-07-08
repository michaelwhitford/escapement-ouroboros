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
  λ leaves the message verbatim (safe under compactor lag/failure)."
  [messages k lambda]
  (let [messages (vec messages)]
    (if-let [i (and (not (str/blank? lambda)) (next-to-compact messages k))]
      (assoc messages i (assoc (nth messages i) :text lambda :compacted? true))
      messages)))
