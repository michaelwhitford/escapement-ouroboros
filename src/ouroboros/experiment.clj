(ns ouroboros.experiment
  "The experiment suite runner — impure edges around ouroboros.experiment.core.

  bb experiment <slug>   → loads experiments/<slug>.edn, sweeps the matrix
  (model × thinking × condition × subject × repeat), assesses every output via
  the suite's :experiment/measure, prints per-row + summary, and persists the
  full row set to experiments/results/<slug>-<epoch>.edn (gitignored —
  results are machine observation; CONCLUSIONS go to the suite's
  :experiment/verdict and to knowledge pages, human-gated).

  A self-improving system must be able to CREATE experiments and GET results:
  a new experiment is a new EDN file, not new code (λ extend). Model endpoints
  come from ouroboros.models/table — when a new model lands on a port,
  re-running a suite re-validates its conclusions."
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [escapement.llm :as ellm]
    [escapement.llm.providers :as providers]
    [ouroboros.experiment.core :as core]
    [ouroboros.models :as models]))

(def experiments-dir "experiments")

(defn- make-ctx [alias]
  (let [{:keys [base-url model]} (get models/table alias)]
    (when-not model
      (throw (ex-info (str "unknown model alias: " alias)
               {:alias alias :known (vec (sort (keys models/table)))})))
    {:backend (providers/build-injected-credentials-backend
                [{:provider :openai :api-key "sk-local" :base-url base-url}]
                [{:provider :openai :model model}])
     :aliases {:local [{:provider :openai :model model}]}
     :preferences [:local]
     :eligibility-strict? false}))

(defn load-suite
  "Read + validate experiments/<slug>.edn. Fail-loud on schema violations."
  [slug]
  (let [f (io/file experiments-dir (str slug ".edn"))]
    (when-not (.exists f)
      (throw (ex-info (str "no such suite: " (.getPath f)) {:slug slug})))
    (let [suite (edn/read-string (slurp f))]
      (when-let [errors (core/validate-suite suite)]
        (throw (ex-info (str "invalid suite " slug ": " (pr-str errors))
                 {:slug slug :errors errors})))
      suite)))

(defn run-cell
  "Execute ONE matrix cell → row map (cell ⊕ status/timing ⊕ assessment)."
  [suite ctx-cache {:keys [model thinking cond subject] :as cell}]
  (let [ctx     (get ctx-cache model)
        system  (get-in suite [:conditions cond :system])
        text    (get-in suite [:subjects subject])
        assess  (get core/measures (:experiment/measure suite))
        opts    (clojure.core/cond-> {:model  :local
                                      :system system
                                      :prompt (format (:prompt-template suite) text)}
                  (not thinking)
                  (assoc :extra-body {"chat_template_kwargs" {"enable_thinking" false}}))
        t0      (System/currentTimeMillis)
        res     (ellm/ask ctx opts)
        ms      (- (System/currentTimeMillis) t0)
        out     (str (:response res))]
    (merge cell
           {:status (:status res) :ms ms
            :tok (get-in res [:usage :output-tokens])
            :text out}
           (if (= :ok (:status res)) (assess suite cell out) {}))))

(defn- persist-results!
  [slug rows]
  (let [dir (io/file experiments-dir "results")
        f   (io/file dir (str slug "-" (System/currentTimeMillis) ".edn"))]
    (.mkdirs dir)
    (spit f (pr-str {:experiment/id slug :rows (mapv #(dissoc % :repeat) rows)}))
    (.getPath f)))

(defn run!
  "Sweep the whole suite. Returns {:rows :summary :results-path}."
  [slug]
  (let [suite     (load-suite slug)
        cells     (core/expand-matrix suite)
        ctx-cache (into {} (map (juxt identity make-ctx))
                        (distinct (map :model cells)))
        rows      (vec
                    (for [cell cells]
                      (let [r (run-cell suite ctx-cache cell)]
                        (println (format "── %s %s think=%s %s → status=%s parse=%s valid=%s (%d ms, %s tok)%s"
                                         (name (:model r)) (name (:cond r)) (:thinking r)
                                         (name (:subject r)) (some-> (:status r) name)
                                         (:parse? r) (:valid? r) (:ms r) (:tok r)
                                         (if (:errors r) (str "  malli=" (pr-str (:errors r))) "")))
                        r)))
        summary   (core/summarize rows)
        path      (persist-results! slug rows)]
    (println (str "\n════════ SUMMARY — " slug " ════════"))
    (println (core/format-summary summary))
    (println (str "\nhypothesis: " (:experiment/hypothesis suite)))
    (when-let [v (:experiment/verdict suite)]
      (println (str "prior verdict: " v)))
    (println (str "results → " path))
    {:rows rows :summary summary :results-path path}))

(defn -main [& args]
  (let [slug (or (first args) (throw (ex-info "usage: bb experiment <suite-slug>" {})))]
    (run! slug)))
