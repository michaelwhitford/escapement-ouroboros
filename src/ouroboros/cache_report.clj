(ns ouroboros.cache-report
  "λ cache-report(session) → per-turn KV-cache reuse table + bust detection.

  Reads a session's transcript.jsonl (escapement records :usage on every
  :llm/response row; escapement.llm.openai maps llama.cpp's
  usage.prompt_tokens_details.cached_tokens → :cache-read-input-tokens) and
  renders the cache story of the conversation:

    turn  region   in  cached  paid  out  status
       1  hot    2431       0  2431   56  cold-start   ← session start, expected
       2  hot    2467    2400    67  199  warm         ← the good path
       3  hot    2510       0  2510   31  BUST         ← slot evicted mid-session

  BUST ≡ a hot turn after the first with zero cache reuse — the signature of
  an unpinned client landing on our dedicated slot (similarity→LRU has no
  reservation; see mementum/knowledge/llama-cpp-prompt-cache.md). Cost of a
  bust = one full re-prefill, auto-recovered next turn.

  Run: bb cache-report              (latest session with a transcript)
       bb cache-report <session-id> (explicit)"
  (:require
    [cheshire.core :as json]
    [clojure.string :as str]
    [ouroboros.curator.core :as ccore]
    [ouroboros.session :as session]))

;; ---------------------------------------------------------------------------
;; Pure kernel.
;; ---------------------------------------------------------------------------

(defn response-entries
  "Parsed transcript rows → usage entries [{:region :in :cached :out}] in
  order. Rows without an invokeid or usage (errors, chart events) are skipped."
  [rows]
  (into []
    (keep (fn [row]
            (when (= "llm/response" (:event row))
              (let [{:keys [invokeid usage]} (:data row)]
                (when (and invokeid (:input-tokens usage))
                  {:region (str invokeid)
                   :in     (:input-tokens usage)
                   :cached (or (:cache-read-input-tokens usage) 0)
                   :out    (or (:output-tokens usage) 0)})))))
    rows))

(defn analyze
  "Usage entries → {:hot [turn…] :other {region {:calls :in :out}} :summary}.
  Hot turns are numbered and classified: first ⇒ :cold-start (expected full
  prefill), cached > 0 ⇒ :warm, else ⇒ :BUST. Summary reuse ratio is computed
  over WARM-ELIGIBLE turns only (2..n) — the cold start is physics, not a bug."
  [entries]
  (let [hot   (->> entries
                (filter #(= "hot" (:region %)))
                (map-indexed
                  (fn [i {:keys [in cached] :as e}]
                    (assoc e
                      :n      (inc i)
                      :paid   (- in cached)
                      :status (cond
                                (zero? i)     :cold-start
                                (pos? cached) :warm
                                :else         :BUST))))
                vec)
        other (reduce (fn [m {:keys [region in out]}]
                        (-> m
                          (update-in [region :calls] (fnil inc 0))
                          (update-in [region :in] (fnil + 0) in)
                          (update-in [region :out] (fnil + 0) out)))
                {}
                (remove #(= "hot" (:region %)) entries))
        eligible (rest hot)
        reused   (reduce + 0 (map :cached eligible))
        paid     (reduce + 0 (map :paid eligible))
        denom    (+ reused paid)]
    {:hot     hot
     :other   other
     :summary {:hot-turns   (count hot)
               :reused      reused
               :paid        paid
               :reuse-pct   (when (pos? denom) (Math/round (* 100.0 (/ reused denom))))
               :busts       (mapv :n (filter #(= :BUST (:status %)) hot))}}))

(defn format-report
  "Analysis → printable string."
  [session-id {:keys [hot other summary]}]
  (let [{:keys [hot-turns reused paid reuse-pct busts]} summary
        line (fn [& cols] (apply format "%4s  %-8s %6s  %6s  %6s  %5s  %s" cols))]
    (str/join "\n"
      (concat
        [(str "session: " session-id)
         ""
         (line "turn" "region" "in" "cached" "paid" "out" "status")]
        (for [{:keys [n in cached paid out status]} hot]
          (line n "hot" in cached paid out (name status)))
        [""]
        (for [[region {:keys [calls in out]}] (sort other)]
          (format "%s: %d call(s), %d tokens in, %d out" region calls in out))
        [""
         (str "summary: " hot-turns " hot turn(s)"
           (when reuse-pct
             (str " | post-start reuse " reuse-pct "% (" reused " reused / " paid " paid)"))
           " | busts: " (if (seq busts) (str/join "," busts) "none"))]))))

;; ---------------------------------------------------------------------------
;; Impure edges.
;; ---------------------------------------------------------------------------

(defn read-transcript
  "Transcript File → parsed rows (keywordized). Unparseable lines skipped."
  [file]
  (into []
    (keep #(try (json/parse-string % true) (catch Exception _ nil)))
    (str/split-lines (slurp file))))

(defn latest-session-id
  "Most recent session under `root` that HAS a transcript, or nil."
  [root]
  (->> (session/list-session-ids root)
    (filter #(session/transcript-file root %))
    (sort-by ccore/recency-key)
    last))

(defn report
  "Cache report string for `session-id` under `root`, or nil when the session
  has no transcript."
  [root session-id]
  (when-let [f (session/transcript-file root session-id)]
    (format-report session-id (analyze (response-entries (read-transcript f))))))

(defn -main [& [session-id]]
  (let [root "."
        id   (or session-id (latest-session-id root))]
    (cond
      (nil? id)
      (do (println "no sessions with a transcript under" (str root "/sessions/"))
          (System/exit 1))

      :else
      (if-let [r (report root id)]
        (println r)
        (do (println "no transcript for session:" id)
            (System/exit 1))))))
