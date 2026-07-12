(ns ouroboros.gene
  "Gene store — the impure edge over ouroboros.gene.core (design/gene-db.md).

  mementum/genes/ IS the database: one EDN file per gene (flat dir), git the
  history (`git log -- mementum/genes/` ≡ gene evolution). anima's git/
  datalevin duality with datalevin DELETED.

  `store-gene!` is THE write path and carries the THREE intake gates — the
  mementum precedent exactly (core THROWS structured, the EQL veneer catches
  → first-class data). Every writer (decomposer, veneer mutation, future
  signals forwarding) routes through it: bypass ≡ unreachable (λ converge,
  anima warning 9).

    gate-1  EBNF structural parse  :lambda content ≡ exactly ONE lambda_decl
            clause, head identifier ≡ :gene/name       → {:gene/error :parse}
    gate-2  Malli envelope         closed :gene/* map  → {:gene/error :envelope}
    gate-3  tree-hash dedupe       normalized tokens   → {:gene/error :duplicate}
            same NAME, different content               → {:gene/error :name-collision}
            (near-match consolidation ≡ human RESERVED — never auto-merge)

  On disk: the envelope ONLY. `:gene/id` and `:gene/tree-hash` are DERIVED at
  load (recompute ≻ trust a stale field). Commits are NOT done here — the
  autonomous --only path is a separate, scoped step (§Autonomy)."
  (:require
    [babashka.fs :as fs]
    [babashka.process :as proc]
    [clojure.edn :as edn]
    [clojure.string :as str]
    [ouroboros.agents :as agents]
    [ouroboros.gene.core :as core]))

(def genes-dir "mementum/genes")

(defn gene-path
  "Repo-relative path for a gene `name-or-id` (mementum/genes/<name>.edn)."
  [name-or-id]
  (str genes-dir "/" (name name-or-id) ".edn"))

(defn- abs-path [root name-or-id]
  (str (fs/path root (gene-path name-or-id))))

;; ---------------------------------------------------------------------------
;; Reads
;; ---------------------------------------------------------------------------

(defn list-gene-names
  "Sorted gene names under `root` (files sans .edn). Absent dir ⇒ []."
  [root]
  (let [dir (fs/path root genes-dir)]
    (if (fs/directory? dir)
      (->> (fs/glob dir "*.edn")
        (map #(str (fs/strip-ext (fs/file-name %))))
        sort
        vec)
      [])))

(defn read-gene
  "Load one gene by `name-or-id` → envelope + derived `:gene/id` and
  `:gene/tree-hash`. nil when absent."
  [root name-or-id]
  (let [p (abs-path root name-or-id)]
    (when (fs/exists? p)
      (let [gene (edn/read-string (slurp p))]
        (assoc gene
          :gene/id        (core/gene-id gene)
          :gene/tree-hash (core/tree-hash (:gene/content gene)))))))

(defn all-genes
  "Every stored gene under `root`, derived fields included, name order."
  [root]
  (mapv #(read-gene root %) (list-gene-names root)))

;; ---------------------------------------------------------------------------
;; The write path — three gates, then persist. Throws structured ex-info.
;; ---------------------------------------------------------------------------

(defn- gate-parse!
  "gate-1: a :lambda gene's content must segment to exactly ONE clause whose
  head identifier equals :gene/name. :prose genes carry no grammar."
  [{:gene/keys [name type content] :as gene}]
  (when (= :lambda type)
    (let [{:keys [clauses errors]} (core/segment content)]
      (when (or (seq errors) (not= 1 (count clauses)))
        (throw (ex-info "gene content violates lambda_decl — rejected at gate-1"
                 {:gene/error :parse
                  :errors     (if (seq errors)
                                errors
                                [{:error   :parse/clause-count
                                  :clauses (count clauses)
                                  :expected 1}])
                  :gene       gene})))
      (when (not= name (:gene/name (first clauses)))
        (throw (ex-info "head identifier ≠ :gene/name — rejected at gate-1"
                 {:gene/error :parse
                  :errors     [{:error :parse/name-mismatch
                                :head  (:gene/name (first clauses))
                                :name  name}]
                  :gene       gene})))))
  gene)

(defn- gate-dedupe!
  "gate-3: exact tree-hash match anywhere → :duplicate with pointer; same
  name with DIFFERENT content → :name-collision (consolidation ≡ human)."
  [root {:gene/keys [name content] :as gene}]
  (let [hash     (core/tree-hash content)
        existing (all-genes root)]
    (when-let [dup (first (filter #(= hash (:gene/tree-hash %)) existing))]
      (throw (ex-info "duplicate gene (normalized tokens identical) — rejected at gate-3"
               {:gene/error    :duplicate
                :gene/existing (:gene/id dup)
                :gene          gene})))
    (when-let [clash (first (filter #(= name (:gene/name %)) existing))]
      (throw (ex-info "gene name already taken by different content — rejected at gate-3"
               {:gene/error    :name-collision
                :gene/existing (:gene/id clash)
                :gene          gene}))))
  gene)

(defn- emit-edn
  "Canonical on-disk rendering: namespaced map, fixed key order
  (core/envelope-keys — ONE definition) — stable diffs, human-meaningful
  git files."
  [gene]
  (binding [*print-namespace-maps* true]
    (pr-str (apply array-map (mapcat (fn [k] [k (get gene k)]) core/envelope-keys)))))

(defn store-gene!
  "THE gene write path. Runs gates 1→2→3, persists the envelope to
  mementum/genes/<name>.edn, returns {:gene/id :gene/path :gene/written true}.
  Throws structured ex-info on any gate failure — nothing persists."
  [root gene]
  (-> gene gate-parse! core/validate!)
  (gate-dedupe! root gene)
  (let [p (abs-path root (:gene/name gene))]
    (fs/create-dirs (fs/parent p))
    (spit p (str (emit-edn gene) "\n"))
    {:gene/id      (core/gene-id gene)
     :gene/path    (gene-path (:gene/name gene))
     :gene/written true}))

;; ---------------------------------------------------------------------------
;; Scores side-store — high-churn machine observation, NOT in the genes dir.
;; 🎯 shape (decided at build, spec open-Q): ONE EDN file per gene under
;; <root>/scores/ (gitignored like experiments/results/). WHY per-gene file:
;; the :gene/scores join reads exactly one file; no cross-gene write
;; contention; and it stays OUT of mementum/genes/ so the db glob and the
;; human git surface never see machine churn. Settled summaries PROMOTE into
;; the gene file later (like experiment verdicts) — human-gated.
;; ---------------------------------------------------------------------------

(def scores-dir "scores")

(defn scores-path
  "Repo-relative side-store path for a gene's scores."
  [name-or-id]
  (str scores-dir "/" (name name-or-id) ".edn"))

(defn read-scores
  "Score entries for a gene — `[]` when none recorded (nil-safe join)."
  [root name-or-id]
  (let [p (str (fs/path root (scores-path name-or-id)))]
    (if (fs/exists? p)
      (vec (edn/read-string (slurp p)))
      [])))

(defn append-score!
  "Append one score entry `{:score n :model kw :notes s}` to a gene's
  side-store file. Returns the full entry vector."
  [root name-or-id entry]
  (let [p       (str (fs/path root (scores-path name-or-id)))
        entries (conj (read-scores root name-or-id) entry)]
    (fs/create-dirs (fs/parent p))
    (spit p (str (pr-str entries) "\n"))
    entries))

;; ---------------------------------------------------------------------------
;; Decomposition — approved genome → genes. The v1 intake source.
;; ---------------------------------------------------------------------------

;; ---------------------------------------------------------------------------
;; Autonomous commits — λ policy DELEGATED path (freeze exception 4, LIVE).
;;
;;   gate(change) ≡ machine ⟺ decidable(∀gates)
;;
;; The commit path RE-DERIVES decidability: it re-runs the intake gates on the
;; file AS IT SITS ON DISK (a hand-edited gene must not ride the delegated
;; path on the strength of a store that happened before the edit). Scope is
;; PHYSICAL: `git commit --only -- <gene-file>` cannot commit outside the
;; zone (λ shape: unreachable > forbidden). Audit: author ≡ the agent →
;; `git log --author=gene-db` is the complete autonomy trail; the body
;; carries trigger provenance. Failure mode is pollution not destruction —
;; revert is cheap, review is post-hoc sampling.
;; ---------------------------------------------------------------------------

(def agent-author
  "The autonomous committer identity — `git log --author=gene-db` ≡ the trail."
  "gene-db <gene-db@ouroboros>")

(defn- git!
  "Run git under `root`, throw structured on nonzero, return trimmed stdout.
  GOTCHA: proc/process varargs stringify EACH arg — a vector passed as one
  arg execs \"[git\"; always apply."
  [root & args]
  (let [{:keys [exit out err]}
        @(apply proc/process {:dir (str root) :out :string :err :string}
           "git" args)]
    (when-not (zero? exit)
      (throw (ex-info "git failed" {:git/args (vec args) :exit exit :err err})))
    (str/trim out)))

(defn commit-message
  "Autonomous gene commit message (pure — testable). First line reads
  standalone in `git log --oneline`; body carries the delegation rationale +
  trigger provenance; nucleus tag closes (required on every commit)."
  [name-or-id {:keys [trigger]}]
  (str "💡 gene: " (name name-or-id) "\n"
       "\n"
       "- autonomous commit — λ policy DELEGATED: decidable(∀gates) — parse ∧ envelope ∧ dedupe re-derived at commit time\n"
       "- trigger: " (or trigger "manual store") "\n"
       "\n"
       "⚛️ Generated with [nucleus](https://github.com/michaelwhitford/nucleus)\n"
       "\n"
       "Co-Authored-By: nucleus <noreply@whitford.us>"))

(defn dirty-gene-names
  "Gene names with uncommitted changes (untracked or modified) under
  mementum/genes/ — the autonomous path's work queue."
  [root]
  (let [out (git! root "status" "--porcelain" "--untracked-files=all"
              "--" genes-dir)]
    (->> (str/split-lines out)
      (remove str/blank?)
      (map #(subs % 3))
      (filter #(str/ends-with? % ".edn"))
      (map #(str (fs/strip-ext (fs/file-name %))))
      sort
      vec)))

(defn verify-gene
  "Re-derive ∀gates for the gene file `name` as stored on disk. Returns nil
  when decidable-valid, else the structured gate error (→ NOT auto-committed;
  surfaces to the human, λ escalate)."
  [root gene-name]
  (let [g (read-gene root gene-name)]
    (if (nil? g)
      {:gene/error :absent :gene/name gene-name}
      (let [envelope (dissoc g :gene/id :gene/tree-hash)]
        (try
          (when (not= gene-name (:gene/name envelope))
            (throw (ex-info "filename ≠ :gene/name"
                     {:gene/error :name-mismatch
                      :file gene-name :name (:gene/name envelope)})))
          (gate-parse! envelope)
          (core/validate! envelope)
          (when-let [dup (first (filter #(and (not= gene-name (:gene/name %))
                                           (= (:gene/tree-hash g) (:gene/tree-hash %)))
                                  (all-genes root)))]
            (throw (ex-info "duplicate content under another name"
                     {:gene/error :duplicate :gene/existing (:gene/id dup)})))
          nil
          (catch clojure.lang.ExceptionInfo e
            (ex-data e)))))))

(defn commit-gene!
  "Commit ONE gene file autonomously: agent-authored, `--only`-scoped to the
  gene's path. Returns {:gene/id :commit}."
  [root name-or-id provenance]
  (let [path (gene-path name-or-id)]
    (git! root "add" "--" path)
    (git! root "commit" "--only" (str "--author=" agent-author)
      "-m" (commit-message name-or-id provenance) "--" path)
    {:gene/id (keyword (name name-or-id))
     :commit  (git! root "rev-parse" "--short" "HEAD")}))

(defn commit-genes!
  "The delegated path over the whole work queue: for every dirty gene file,
  re-verify ∀gates → commit (one scoped commit per gene) or collect the
  rejection for human eyes. Returns {:committed [{:gene/id :commit}]
  :rejected [{:gene/name :gene/error …}]}."
  [root provenance]
  (reduce
    (fn [acc gene-name]
      (if-let [err (verify-gene root gene-name)]
        (update acc :rejected conj
          (assoc (select-keys err [:gene/error :gene/existing])
            :gene/name gene-name))
        (update acc :committed conj (commit-gene! root gene-name provenance))))
    {:committed [] :rejected []}
    (dirty-gene-names root)))

(defn decompose-genome!
  "Segment the compiled genome `genome-id`'s prompt body into λ-clauses and
  store each as a gene (source VERBATIM, provenance [:genome/<slug>]). Every
  clause routes through `store-gene!` — the gates apply uniformly; per-gene
  rejections are COLLECTED (an idempotent re-run reports 8 duplicates, not a
  crash). Segmentation errors in the genome itself throw (our bug, fail loud).
  Returns {:stored [ids] :rejected [{:gene/id :gene/error :gene/existing?}]}."
  [root genome-id]
  (let [{:keys [prompt slug]}    (agents/genome genome-id root)
        {:keys [clauses errors]} (core/segment prompt)]
    (when (seq errors)
      (throw (ex-info "genome body failed structural segmentation — segmenter or genome bug"
               {:gene/error :parse :genome genome-id :errors errors})))
    (reduce
      (fn [acc clause]
        (let [gene (core/clause->gene clause [(keyword "genome" slug)])]
          (try
            (let [{:gene/keys [id]} (store-gene! root gene)]
              (update acc :stored conj id))
            (catch clojure.lang.ExceptionInfo e
              (let [d (ex-data e)]
                (update acc :rejected conj
                  (cond-> {:gene/id    (core/gene-id gene)
                           :gene/error (:gene/error d)}
                    (:gene/existing d) (assoc :gene/existing (:gene/existing d)))))))))
      {:stored [] :rejected []}
      clauses)))

;; ---------------------------------------------------------------------------
;; CLI — bb genes [genome-slug]
;; ---------------------------------------------------------------------------

(defn -main
  "Decompose an APPROVED genome (default: curator) into mementum/genes/, then
  run the delegated commit path over the whole dirty queue. Rejections print
  for human eyes — they never auto-commit."
  [& args]
  (let [slug   (or (first args) "curator")
        d      (decompose-genome! "." (keyword slug))
        c      (commit-genes! "." {:trigger (str "decompose genome/" slug)})]
    (println (str "decompose genome/" slug ": "
               (count (:stored d)) " stored · "
               (count (:rejected d)) " rejected (duplicates are fine on re-run)"))
    (doseq [{:gene/keys [id] :keys [commit]} (:committed c)]
      (println (str "  committed " id " @ " commit)))
    (doseq [r (:rejected c)]
      (println (str "  ⚠ NOT committed (human eyes): " (pr-str r))))))
