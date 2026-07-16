(ns ouroboros.schedule
  "The maintenance schedule — RUNG 1 of the timer ladder
  (design/scheduled-maintenance): OS cron/launchd fires `bb maintain`, ONE
  sequential sweep of hermetic proposer runs. WHY sequential: one GPU at
  5100 (slot/bandwidth contention). WHY hermetic: per-run credentials
  sidestep the multi-model first-wins collision entirely.

  The TABLE is data (sibling to ouroboros.models): {:select {:tag …} ∨
  {:slug …}, :subject (the standing prompt), :budget-ms, :enabled,
  :cadence}. Tag selection is SET-VALUED — a new genome carrying the tag is
  swept automatically (role-as-tag, wired). :cadence is INTENT documentation
  at rung 1 — cron-side config is the truth; rungs 2/3 will interpret it.

  Discipline (design §Unattended-run):
    RATE   the genomes each propose ≤1 artifact per run (their λ terminate)
    DEDUP  :harness/context digests pending proposals; memory indexes show
           uncommitted candidates — ¬re-propose is a genome clause
    BUDGET :budget-ms per run — a wedged agent dies quietly and logs
    AUDIT  one summary line per run + an :s1/report signal per run
           (source \"bb-maintain\" — the roster EMITS signals)
    LOCK   .maintain.lock — overlapping sweeps refused; stale locks (>2h,
           yesterday's wedge) are broken"
  (:require
    [babashka.fs :as fs]
    [clojure.string :as str]
    [ouroboros.agents :as agents]
    [ouroboros.proposals :as proposals]
    [ouroboros.signals :as signals]))

;; ---------------------------------------------------------------------------
;; The table
;; ---------------------------------------------------------------------------

(def knowledge-subject
  "Standing prompt for the curator-tagged (knowledge) cells."
  "Begin your maintenance run: observe with your tools, metabolize what you read, then propose exactly ONE grounded memory per your instructions.")

(def assessor-subject
  "Standing prompt for the assessor-tagged (coder) cells."
  "Begin your maintenance run: observe with your tools, detect per your friction/drift table, then propose AT MOST one grounded recommendation — no finding is an honest outcome.")

(def table
  "The maintenance schedule. Extend by adding an entry; tag entries sweep
  every genome carrying the tag (new genomes join automatically)."
  [{:id        :knowledge-sweep
    :select    {:tag :curator}
    :cadence   "daily"
    :subject   knowledge-subject
    :budget-ms 240000
    :enabled   true}
   {:id        :assess-sweep
    :select    {:tag :assessor}
    :cadence   "daily"
    :subject   assessor-subject
    :budget-ms 240000
    :enabled   true}])

;; ---------------------------------------------------------------------------
;; Selection (pure over a compiled roster)
;; ---------------------------------------------------------------------------

(defn select-slugs
  "Resolve one entry's :select against a compiled `roster` → sorted agent
  ids. {:tag t} ⇒ every agent carrying t (set-valued); {:slug s} ⇒ exactly
  that agent, FAIL-LOUD when absent (a schedule pointing at a missing genome
  is a wiring error, λ emerge)."
  [roster {:keys [tag slug] :as select}]
  (cond
    tag  (vec (sort (keep (fn [[id a]] (when (some #{tag} (:tags a)) id)) roster)))
    slug (let [id (keyword slug)]
           (when-not (contains? roster id)
             (throw (ex-info (str "schedule selects unknown genome: " id)
                      {:select select :known (vec (sort (keys roster)))})))
           [id])
    :else (throw (ex-info "schedule entry :select needs :tag or :slug"
                   {:select select}))))

(defn sweep-plan
  "The full sweep as [{:agent :subject :budget-ms :entry}] — entries in table
  order, agents sorted within an entry, disabled entries skipped. An agent
  selected twice runs once (first entry wins — one run per agent per sweep)."
  [roster entries]
  (->> entries
    (filter :enabled)
    (mapcat (fn [{:keys [id subject budget-ms select]}]
              (map (fn [slug] {:agent slug :subject subject
                               :budget-ms budget-ms :entry id})
                (select-slugs roster select))))
    (reduce (fn [{:keys [seen] :as acc} {:keys [agent] :as run}]
              (if (seen agent)
                acc
                (-> acc (update :plan conj run) (update :seen conj agent))))
      {:plan [] :seen #{}})
    :plan))

;; ---------------------------------------------------------------------------
;; Lock — overlapping sweeps refused, stale locks broken
;; ---------------------------------------------------------------------------

(def lock-file ".maintain.lock")

(def stale-lock-ms
  "A lock older than this is yesterday's wedge — break it."
  (* 2 60 60 1000))

(defn- lock-path [root] (str (fs/path root lock-file)))

(defn lock-state
  "Pure decision over (existing lock timestamp, now): :free | :stale | :held."
  [lock-ms now-ms]
  (cond
    (nil? lock-ms)                        :free
    (> (- now-ms lock-ms) stale-lock-ms)  :stale
    :else                                 :held))

(defn acquire-lock!
  "Take the sweep lock or throw (:held). Stale locks are broken with a note."
  [root now-ms]
  (let [p        (lock-path root)
        existing (when (fs/exists? p) (parse-long (str/trim (slurp p))))]
    (case (lock-state existing now-ms)
      :held  (throw (ex-info "maintenance sweep already running — refusing overlap"
                      {:lock existing :now now-ms}))
      :stale (println (str "⚠ breaking stale maintenance lock (" existing ")"))
      :free  nil)
    (spit p (str now-ms))))

(defn release-lock! [root]
  (fs/delete-if-exists (lock-path root)))

;; ---------------------------------------------------------------------------
;; The sweep
;; ---------------------------------------------------------------------------

(defn summary-line
  "One audit line per run (pure)."
  [{:keys [agent status wall-ms new-proposals new-memories]}]
  (str "maintain " (name agent)
       " → " (name (or status :fail))
       " (" wall-ms "ms)"
       (when (pos? (+ (or new-proposals 0) (or new-memories 0)))
         (str " · +" (or new-proposals 0) " proposal(s), +"
              (or new-memories 0) " memory(ies)"))))

(defn- emit-report!
  "The roster EMITS signals: one :s1/report per agent run, source
  bb-maintain. Duplicate-damped: an identical re-emission is caught and
  noted, never fatal (dedupe IS the re-proposal damper working)."
  [root {:keys [agent status wall-ms] :as outcome}]
  (try
    (signals/emit! root
      {:signal/type   :s1/report
       :signal/data   {:summary (summary-line outcome)
                       :outcome (if (= :done status) :ok :fail)
                       :metrics {:agent (name agent) :wall-ms wall-ms}}
       :signal/lambda (str "λ maintain(" (name agent) ") → " (name (or status :fail)))
       :signal/source "bb-maintain"})
    (catch clojure.lang.ExceptionInfo e
      (when-not (= :duplicate (:signal/error (ex-data e))) (throw e))
      {:signal/written false :signal/duplicate true})))

(defn sweep!
  "Run the maintenance sweep against `root`: plan from the live roster ×
  `table`, sequential hermetic runs, per-run :s1/report signal + summary
  line; per-run exceptions become :fail outcomes (the sweep continues —
  one wedged agent must not starve the rest). `runner` is injectable for
  tests (default: the real proposer runner, resolved lazily).
  Returns the outcome vector."
  ([root] (sweep! root {}))
  ([root {:keys [runner entries quiet? screen-fn]
          :or   {entries table}}]
   (let [runner (or runner
                    (fn [slug opts]
                      ((requiring-resolve 'ouroboros.proposer/run!) slug opts)))
         plan   (sweep-plan (agents/compile-roster root) entries)]
     (acquire-lock! root (System/currentTimeMillis))
     (try
       (let [outcomes
             (vec
               (for [{:keys [agent subject budget-ms]} plan]
                 (let [t0     (System/currentTimeMillis)
                       p0     (count (proposals/pending root))
                       m0     (count (proposals/untracked-memories root))
                       status (try
                                (:status (runner agent {:root root :subject subject
                                                        :budget-ms budget-ms :quiet? quiet?}))
                                (catch Exception e
                                  (println (str "⚠ " (name agent) " run threw: " (ex-message e)))
                                  :fail))
                       outcome {:agent         agent
                                :status        status
                                :wall-ms       (- (System/currentTimeMillis) t0)
                                :new-proposals (- (count (proposals/pending root)) p0)
                                :new-memories  (- (count (proposals/untracked-memories root)) m0)}]
                   (emit-report! root outcome)
                   (println (summary-line outcome))
                   outcome)))]
         ;; The verifier IN THE LOOP (ouroboros.screen): after the sweep
         ;; produces its artifacts, `screen-fn` verdicts each one before the
         ;; human reads the inbox. Injectable, ABSENT default (λ extend:
         ;; option > detection — direct sweep! callers/tests never
         ;; surprise-run an LLM; bb maintain passes the real thing). A
         ;; screening failure must not fail the sweep — its artifacts are
         ;; already safe on disk (λ escalate: warn, continue).
         (when screen-fn
           (try (screen-fn root)
             (catch Exception e
               (println (str "⚠ screen pass threw: " (ex-message e))))))
         outcomes)
       (finally (release-lock! root))))))

;; ---------------------------------------------------------------------------
;; CLI — bb maintain [slug]
;; ---------------------------------------------------------------------------

(defn -main
  "bb maintain           → the full sweep (every enabled entry)
   bb maintain <slug>    → sweep exactly that genome (subject from the first
                           enabled entry whose selection includes it, else
                           the proposer default)."
  [& args]
  (let [screen-fn (fn [root]
                    ((requiring-resolve 'ouroboros.screen/screen!) root {}))
        outcomes
        (if-let [slug (first args)]
          (let [id      (keyword slug)
                roster  (agents/compile-roster ".")
                entry   (first (filter #(and (:enabled %)
                                          (some #{id} (select-slugs roster (:select %))))
                                 table))
                entries [(merge {:id :adhoc :select {:slug slug} :enabled true}
                           (select-keys entry [:subject :budget-ms]))]]
            (sweep! "." {:entries entries :screen-fn screen-fn}))
          (sweep! "." {:quiet? true :screen-fn screen-fn}))]
    (println)
    (println (proposals/render-inbox (proposals/pending ".")
               (proposals/untracked-memories ".")
               ((requiring-resolve 'ouroboros.screen/verdicts) ".")))
    (shutdown-agents)
    (System/exit (if (every? #(= :done (:status %)) outcomes) 0 1))))
