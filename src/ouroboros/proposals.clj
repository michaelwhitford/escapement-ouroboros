(ns ouroboros.proposals
  "Proposal artifacts — the human-gated recommendation channel of the
  maintenance roster (design/harness-coder §The proposal artifact,
  design/scheduled-maintenance §Unattended-run discipline).

  proposals/ is FILESYSTEM-SIDE and gitignored (the sessions/ pattern:
  pre-approval observation never rides git). A proposal is an OKF markdown
  document — HUMAN-facing prose-with-evidence, NOT a diff (diffs belong to
  the later editor kind). The human promotes a proposal by ACTING on it; the
  resulting change commit cites the proposal slug; stale proposals are
  deleted freely.

  `propose!` is THE write path (Malli-gated at the boundary, the mementum
  store! precedent: core THROWS structured → tool edge catches → corrective
  retry). Every proposal carries :severity from day one — :algedonic
  (identity-threatening) surfaces FIRST in the inbox: the seed of the S1↔S5
  bypass channel (costs a keyword now vs a channel redesign later).

  `bb proposals` ≡ the inbox: pending proposals (severity-first) + uncommitted
  memory candidates — the morning-coffee batch review surface."
  (:require
    [babashka.fs :as fs]
    [babashka.process :as proc]
    [clojure.string :as str]
    [malli.core :as m]
    [malli.error :as me]
    [ouroboros.mementum.okf :as okf]))

(def proposals-dir "proposals")

(defn proposal-path
  "Repo-relative path for a proposal slug (proposals/<slug>.md)."
  [slug]
  (str proposals-dir "/" (name slug) ".md"))

(defn- abs-path [root slug]
  (str (fs/path root (proposal-path slug))))

(def proposal-type "ouroboros/proposal")

(def schema
  "Malli gate for proposal frontmatter (raw parsed YAML — string values,
  except evidence which YAML gives as a vector). CLOSED like the genome
  schema: proposal frontmatter is inbox WIRING, an unknown key ≈ typo."
  [:map {:closed true}
   [:type [:= {:error/message (str "type must be " proposal-type)} proposal-type]]
   [:description [:and :string [:fn {:error/message "must be non-blank"}
                                (complement str/blank?)]]]
   [:target :string]                                  ; Layer-2 path (or a layer-1:… designer-attention flag)
   [:evidence [:sequential :string]]                  ; session ids / file paths / commit hashes
   [:severity [:enum {:error/message "severity must be ordinary or algedonic"}
               "ordinary" "algedonic"]]
   [:status {:optional true} :string]])               ; default "open"

;; ---------------------------------------------------------------------------
;; Reads — the inbox
;; ---------------------------------------------------------------------------

(defn pending
  "Parsed pending proposals under `root`, ALGEDONIC FIRST then slug order.
  Absent dir ⇒ []. A file that no longer parses is surfaced as an error
  entry rather than hidden (λ escalate — never silently drop a proposal)."
  [root]
  (let [dir (fs/path root proposals-dir)]
    (if-not (fs/directory? dir)
      []
      (->> (fs/glob dir "*.md")
        (map (fn [f]
               (let [slug (str (fs/strip-ext (fs/file-name f)))]
                 (try
                   (let [{:keys [frontmatter body]} (okf/parse (slurp (fs/file f)))]
                     {:slug        slug
                      :path        (proposal-path slug)
                      :description (:description frontmatter)
                      :target      (:target frontmatter)
                      :evidence    (vec (:evidence frontmatter))
                      :severity    (keyword (or (:severity frontmatter) "ordinary"))
                      :status      (or (:status frontmatter) "open")
                      :body        body})
                   (catch Exception e
                     {:slug slug :path (proposal-path slug)
                      :severity :ordinary :error (ex-message e)})))))
        (sort-by (juxt #(if (= :algedonic (:severity %)) 0 1) :slug))
        vec))))

(defn untracked-memories
  "Repo-relative paths under mementum/memories/ that git sees as untracked or
  modified — freshly proposed memory candidates awaiting human approval.
  (Moved here from the curator runner — this is INBOX vocabulary.)
  `--untracked-files=all` forces PER-FILE listing — plain porcelain collapses
  a wholly-new directory to one `?? dir/` line."
  [root]
  (let [{:keys [exit out]} (apply proc/shell
                             {:dir (str root) :out :string :err :string :continue true}
                             ["git" "status" "--porcelain" "--untracked-files=all"
                              "--" "mementum/memories/"])]
    (if (zero? exit)
      (->> (str/split-lines out)
        (remove str/blank?)
        (map #(str/trim (subs % 3)))
        (remove #(str/ends-with? % "/"))
        vec)
      [])))

(defn- verdict-note
  "One inbox line for a stored screen verdict (ouroboros.screen). A verdict
  whose artifact changed since screening renders ⚠ stale — never as truth."
  [{:keys [status notes stale?]}]
  (str "verifier: "
       (if stale?
         "⚠ stale (edited since screening — re-run bb screen)"
         (str (case status :pass "✓ pass" :fail "✗ fail" (pr-str status))
              (when-not (str/blank? (str notes))
                (let [first-line (first (str/split-lines (str notes)))]
                  (str " — " (subs first-line 0 (min 140 (count first-line))))))))))

(defn render-inbox
  "The bb proposals text: pending proposals (algedonic screams first) +
  uncommitted memory candidates (`untracked` — paths, caller supplies).
  `verdicts` (optional): {path screen-verdict} from ouroboros.screen — each
  screened artifact carries its evidence verdict on the line the human reads."
  ([proposals untracked-memories]
   (render-inbox proposals untracked-memories {}))
  ([proposals untracked-memories verdicts]
   (str
     "PROPOSALS (" (count proposals) " pending):\n"
     (if (seq proposals)
       (str/join "\n"
         (for [{:keys [slug path severity description target evidence error]} proposals]
           (if error
             (str "  ⚠ " slug " — UNPARSEABLE: " error)
             (str "  " (if (= :algedonic severity) "🚨 ALGEDONIC" "·") " " slug
                  "\n      " description
                  "\n      target: " target
                  "  evidence: " (pr-str evidence)
                  (when-let [v (get verdicts path)]
                    (str "\n      " (verdict-note v)))))))
       "  (none)")
     "\n\nUNCOMMITTED MEMORY CANDIDATES (" (count untracked-memories) "):\n"
     (if (seq untracked-memories)
       (str/join "\n"
         (map #(str "  · " %
                    (when-let [v (get verdicts %)]
                      (str "\n      " (verdict-note v))))
           untracked-memories))
       "  (none)"))))

;; ---------------------------------------------------------------------------
;; The write path — gate then persist. Throws structured ex-info.
;; ---------------------------------------------------------------------------

(defn propose!
  "THE proposal write path. `content` is a complete OKF document (frontmatter
  gated by `schema`, body required). Writes proposals/<slug>.md — WORKING
  TREE ONLY, never git. Returns {:proposal/slug :proposal/path
  :proposal/written true}. Throws structured ex-info on gate failure."
  [root slug content]
  (let [{:keys [frontmatter body]} (okf/parse content)
        errors (some-> (m/explain schema frontmatter) me/humanize)
        errors (cond-> errors
                 (str/blank? (str body))
                 (assoc :body ["proposal body is blank — problem → change-sketch → expected-effect"]))]
    (when errors
      (throw (ex-info "proposal rejected at the gate"
               {:proposal/error :invalid :errors errors})))
    (when (fs/exists? (abs-path root slug))
      (throw (ex-info "proposal slug already pending — do not re-propose"
               {:proposal/error :pending :proposal/existing (name slug)})))
    (let [p (abs-path root slug)]
      (fs/create-dirs (fs/parent p))
      (spit p content)
      {:proposal/slug    (name slug)
       :proposal/path    (proposal-path slug)
       :proposal/written true})))

;; ---------------------------------------------------------------------------
;; CLI — bb proposals (the morning-coffee inbox)
;; ---------------------------------------------------------------------------

(defn -main [& _]
  ;; requiring-resolve: screen depends on THIS ns — the inbox stays an
  ;; LLM-free read, merely DISPLAYING stored verdicts (never running any).
  (println (render-inbox (pending ".") (untracked-memories ".")
             ((requiring-resolve 'ouroboros.screen/verdicts) "."))))

