(ns ouroboros.mementum.store
  "Git-backed persistence — the CORE storage layer beneath the EQL veneer.
  Pure-ish fns over the repo's `mementum/` tree; git provides temporal
  (`log`) and semantic (`grep`) recall. bb-native (babashka.fs + .process).
  No pathom.

  Writes go through the OKF Malli gate (`okf/parse-valid!`) so malformed
  frontmatter never reaches disk. Commits are NOT done here — they are
  human-gated (AGENTS.md invariant); `store!`/`delete!` mutate only the
  working tree, leaving the commit as a separate approved step.

  All fns take an explicit `root` (repo dir) so they are trivially testable
  against a temp dir."
  (:require
    [babashka.fs :as fs]
    [babashka.process :as proc]
    [clojure.string :as str]
    [ouroboros.mementum.okf :as okf]))

;; ---------------------------------------------------------------------------
;; Path model. `:state` is a singleton; `:memory`/`:knowledge` are slug-keyed
;; (slugs may be nested, e.g. "upstream/escapement-index").
;; ---------------------------------------------------------------------------

(def kind->dir
  {:memory    "mementum/memories"
   :knowledge "mementum/knowledge"})

(defn rel-path
  "Repo-relative path for [kind slug]. `:state` ignores slug (singleton)."
  [kind slug]
  (case kind
    :state "mementum/state.md"
    (str (kind->dir kind) "/" slug ".md")))

(defn abs-path
  "Absolute filesystem path string for [kind slug] under `root`."
  [root kind slug]
  (str (fs/path root (rel-path kind slug))))

;; ---------------------------------------------------------------------------
;; Reads
;; ---------------------------------------------------------------------------

(defn read-doc
  "Read + parse the OKF doc for [kind slug] under `root`. Returns
  `{:frontmatter :body :slug :kind :path}` or nil when absent."
  [root kind slug]
  (let [p (abs-path root kind slug)]
    (when (fs/exists? p)
      (assoc (okf/parse (slurp p))
        :slug slug :kind kind :path (rel-path kind slug)))))

(defn list-slugs
  "Slugs under a `kind` dir (recursive), sans `.md`, relative to the kind dir
  so nested pages keep their subpath (e.g. \"upstream/escapement-index\")."
  [root kind]
  (let [dir (str (fs/path root (kind->dir kind)))]
    (when (fs/exists? dir)
      (->> (file-seq (fs/file dir))
        (filter #(and (.isFile ^java.io.File %)
                   (str/ends-with? (.getName ^java.io.File %) ".md")))
        (map #(.getPath ^java.io.File %))
        (map #(-> % (str/replace (str dir "/") "") (str/replace #"\.md$" "")))
        sort
        vec))))

(defn list-summaries
  "Disclosure map for a `kind` (AGENTS.md λ disclose: description_first):
  `[{:slug :type :description}]`. Reads frontmatter only — no bodies loaded."
  [root kind]
  (mapv (fn [slug]
          (let [{:keys [frontmatter]} (read-doc root kind slug)]
            {:slug slug :type (:type frontmatter) :description (:description frontmatter)}))
    (list-slugs root kind)))

;; ---------------------------------------------------------------------------
;; Mutations (working tree only — commit is human-gated)
;; ---------------------------------------------------------------------------

(defn store!
  "Validate `content` (full OKF doc string) and write it to [kind slug] under
  `root`. The Malli gate rejects malformed frontmatter BEFORE any write, so
  nothing bad persists. Returns `{:slug :kind :path :written true}`."
  [root kind slug content]
  (okf/parse-valid! content)                 ; throws on invalid → no write
  (let [p (abs-path root kind slug)]
    (fs/create-dirs (fs/parent p))
    (spit p content)
    {:slug slug :kind kind :path (rel-path kind slug) :written true}))

(defn delete!
  "Delete [kind slug] from the working tree. Returns `{:slug :kind :deleted bool}`."
  [root kind slug]
  (let [p (abs-path root kind slug)]
    (if (fs/exists? p)
      (do (fs/delete p) {:slug slug :kind kind :deleted true})
      {:slug slug :kind kind :deleted false})))

;; ---------------------------------------------------------------------------
;; Recall — temporal (git log) + semantic (git grep)
;; ---------------------------------------------------------------------------

(defn- git
  "Run git in `root`, capturing output; never throws on nonzero exit."
  [root args]
  (apply proc/shell {:dir (str root) :out :string :err :string :continue true}
    "git" args))

(defn recall-grep
  "Semantic recall: `git grep -il <query>` over `mementum/`. Returns matching
  repo-relative paths, or [] on no match. (Searches tracked files — commit to
  make new memories findable.)"
  [root query]
  (let [{:keys [exit out]} (git root ["grep" "-il" query "--" "mementum/"])]
    (if (zero? exit)
      (vec (remove str/blank? (str/split-lines (str/trim out))))
      [])))

(defn recall-log
  "Temporal recall: last `n` commits touching mementum memories + knowledge.
  Returns `[{:hash :subject}]`, newest first, or [] when unavailable."
  [root n]
  (let [{:keys [exit out]} (git root ["log" (str "-n" n) "--oneline" "--"
                                      "mementum/memories" "mementum/knowledge"])]
    (if (zero? exit)
      (->> (str/split-lines (str/trim out))
        (remove str/blank?)
        (mapv (fn [line]
                (let [[h & r] (str/split line #"\s+")]
                  {:hash h :subject (str/join " " r)}))))
      [])))
