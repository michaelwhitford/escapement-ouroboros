(ns ouroboros.generator
  "The GENERATOR kind's runner — the LAST kind (agent-model §Genes): recombine
  scored λ-genes into NOVEL candidate genomes for a target use-case.

    fitness   score-pool! — gene × use-case via the gene-scorer verdict →
              scores/ side-store entries {:score :model :notes :use-case}
    fanout    generate! — n hermetic generator-genome runs; each reply is ONE
              complete OKF genome doc → extract → parse-genome VALIDATES
              (fail-loud per candidate: a hallucinated tool, bad kind, or
              broken frontmatter DROPS that candidate with reasons — the same
              compiler gate the roster runs) → candidates/<slug>-<i>.md
    tourney   tournament! — comparator verdicts over all ordered pairs
              (both seatings; position bias cancels in the tally)
    gate      candidates/ is UNCOMMITTED (gitignored-adjacent working set is
              deliberate: candidates are PROPOSALS) — the human reviews the
              ranking + files; landing one ≡ a normal agents/*.md commit.

  ⚠ decorrelation caveat (state.md λ tomorrow): until gemma4 lands, scorer +
  comparator noise is single-family CORRELATED — rankings are directional,
  not statistical truth. Re-run the suites when the second family arrives.

  Run: bb generate-scores \"<use-case>\"     (fills fitness — background-friendly)
       bb generate <slug> \"<use-case>\" [n] (fanout + tournament + report)"
  (:require
    [babashka.fs :as fs]
    [clojure.string :as str]
    [ouroboros.agents.core :as agents.core]
    [ouroboros.gene :as gene]
    [ouroboros.generator.core :as core]
    [ouroboros.prompts :as prompts]
    [ouroboros.proposer :as proposer]
    [ouroboros.signals.core :as signals.core]
    [ouroboros.tools :as tools]
    [ouroboros.verdict :as verdict]))

(def gene-count
  "Top-k genes handed to each generator run."
  12)

(def generate-budget-ms 300000)

(def candidates-dir "candidates")

;; ---------------------------------------------------------------------------
;; Pool + fitness
;; ---------------------------------------------------------------------------

(defn pool
  "Every gene joined with its side-store scores."
  [root]
  (mapv (fn [g] {:gene g :scores (gene/read-scores root (:gene/name g))})
    (gene/all-genes root)))

(defn score-pool!
  "Score every gene not yet scored for `use-case` (idempotent — re-runs skip
  scored genes) via the gene-scorer verdict, appending
  {:score :model :notes :use-case} to the side-store. Prints progress.
  Returns {:scored [{:gene :score}] :failed [names]}."
  [root use-case]
  (let [todo (->> (pool root)
               (remove (fn [{:keys [scores]}]
                         (some #(= use-case (:use-case %)) scores)))
               (map :gene))]
    (println (str "scoring " (count todo) " unscored gene(s) for use-case: " use-case))
    (reduce
      (fn [acc {:gene/keys [name content]}]
        (let [subject (str "USE-CASE: " use-case " ∧ GENE: " content)
              {:keys [verdict model]} (verdict/run! :gene-scorer subject {:root root})]
          (if-let [s (:score verdict)]
            (do (gene/append-score! root name
                  {:score s :model model :notes (:notes verdict) :use-case use-case})
                (println (str "  " name " → " s))
                (update acc :scored conj {:gene name :score s}))
            (do (println (str "  " name " → FAILED (no verdict)"))
                (update acc :failed conj name)))))
      {:scored [] :failed []}
      todo)))

;; ---------------------------------------------------------------------------
;; Fanout — n candidates, each validated through THE genome compiler gate
;; ---------------------------------------------------------------------------

(defn- validation-ctx []
  {:registry-tools   (tools/tool-names)
   :read-only-floor  tools/read-only-tools
   :registry-modules (prompts/module-names)
   :registry-signals signals.core/signal-types})

(defn- gene-block [selected]
  (str/join "\n"
    (map (fn [{:keys [gene fitness]}]
           (str "· [fitness " fitness "] " (:gene/content gene)))
      selected)))

(defn- validate-candidate
  "Run ONE extracted doc through agents.core/parse-genome. Returns
  {:ok agent} or {:error <humanized>} — never throws (a bad candidate is a
  DROP, not a crash)."
  [slug doc]
  (try
    {:ok (agents.core/parse-genome
           (merge {:slug slug :tier :candidate :source (str candidates-dir "/" slug ".md")
                   :doc doc}
             (validation-ctx)))}
    (catch clojure.lang.ExceptionInfo e
      {:error (:errors (ex-data e))})))

(defn generate!
  "FANOUT: `n` hermetic generator runs for `use-case`; write the validated
  candidates to candidates/<slug>-<i>.md (UNCOMMITTED — proposals). Returns
  {:written [paths] :dropped [{:i :error}] :selected [gene-names]}."
  [root slug use-case n]
  (let [selected (core/select-genes (pool root) use-case gene-count)]
    (when (empty? selected)
      (throw (ex-info "no scored genes for this use-case — run bb generate-scores first"
               {:use-case use-case})))
    (let [subject (str "TARGET USE-CASE: " use-case
                    "\n\nGENE POOL (fitness-ranked, verbatim λ clauses):\n"
                    (gene-block selected)
                    "\n\nCANDIDATE SLUG: " slug)]
      (fs/create-dirs (fs/path root candidates-dir))
      (reduce
        (fn [acc i]
          (let [cslug (str slug "-" i)
                {:keys [session-dir]}
                (proposer/run! :generator {:root root :subject subject
                                           :budget-ms generate-budget-ms})
                reply (let [f (str session-dir "/artifacts/reflection.md")]
                        (when (fs/exists? f) (slurp f)))
                doc   (core/extract-okf reply)
                v     (when doc (validate-candidate cslug doc))]
            (cond
              (nil? doc)
              (update acc :dropped conj {:i i :error :no-okf-document})

              (:error v)
              (update acc :dropped conj {:i i :error (:error v)})

              :else
              (let [p (str (fs/path root candidates-dir (str cslug ".md")))]
                (spit p doc)
                (update acc :written conj p)))))
        {:written [] :dropped []
         :selected (mapv (comp :gene/name :gene) selected)}
        (range 1 (inc n))))))

;; ---------------------------------------------------------------------------
;; Tournament — comparator over candidate files
;; ---------------------------------------------------------------------------

(defn tournament!
  "Pairwise comparator runs over `candidate-paths` (all ordered pairs).
  Returns {:results [{:pair :winner :notes}] :ranking [[path wins]...]}."
  [root use-case candidate-paths]
  (let [results
        (vec
          (for [[a b] (core/round-robin-pairs (vec candidate-paths))]
            (let [subject (str "USE-CASE: " use-case
                            "\n\nCANDIDATE A:\n" (slurp a)
                            "\nCANDIDATE B:\n" (slurp b))
                  {:keys [verdict]} (verdict/run! :comparator subject {:root root})]
              {:pair [a b] :winner (:winner verdict) :notes (:notes verdict)})))]
    {:results results :ranking (core/tally results)}))

;; ---------------------------------------------------------------------------
;; CLI
;; ---------------------------------------------------------------------------

(defn score-main [& args]
  (let [use-case (str/join " " args)]
    (when (str/blank? use-case)
      (println "usage: bb generate-scores \"<use-case>\"")
      (System/exit 2))
    (let [{:keys [scored failed]} (score-pool! "." use-case)]
      (println (str "scored " (count scored) " · failed " (count failed)))
      (shutdown-agents)
      (System/exit (if (seq failed) 1 0)))))

(defn -main [& [slug & rest-args]]
  (let [[n-str & _] (filter #(re-matches #"\d+" %) (take-last 1 rest-args))
        n           (if n-str (parse-long n-str) 3)
        use-case    (str/join " " (if n-str (butlast rest-args) rest-args))]
    (when (or (str/blank? (or slug "")) (str/blank? use-case))
      (println "usage: bb generate <slug> \"<use-case>\" [n]")
      (System/exit 2))
    (let [{:keys [written dropped selected]} (generate! "." slug use-case n)]
      (println)
      (println "selected genes :" (str/join " " selected))
      (doseq [{:keys [i error]} dropped]
        (println (str "candidate " i " DROPPED: " (pr-str error))))
      (doseq [p written] (println "candidate      :" p))
      (if (< (count written) 2)
        (println "(<2 candidates — no tournament; review directly)")
        (let [{:keys [ranking results]} (tournament! "." use-case written)]
          (println)
          (println "TOURNAMENT (" (count results) "pairwise verdicts, both seatings):")
          (doseq [[p wins] ranking]
            (println (str "  " wins " wins — " p)))))
      (println)
      (println "candidates are UNCOMMITTED proposals — review, refine, land as agents/*.md (human gate)")
      (shutdown-agents)
      (System/exit 0))))
