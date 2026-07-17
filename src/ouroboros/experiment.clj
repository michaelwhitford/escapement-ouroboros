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
    [babashka.http-client :as http]
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [escapement.llm :as ellm]
    [ouroboros.agents.core :as acore]
    [ouroboros.experiment.core :as core]
    [ouroboros.models :as models]
    [ouroboros.prompts :as prompts]))

(def experiments-dir "experiments")

(defn- make-ctx [alias]
  (let [{:keys [model]} (get models/table alias)]
    (when-not model
      (throw (ex-info (str "unknown model alias: " alias)
               {:alias alias :known (vec (sort (keys models/table)))})))
    ;; DE-FORKED backend: our llama.cpp backend (thinking-off rides the modeled
    ;; :thinking field, not the fork's :extra-body). Experiments don't pin slots
    ;; (throughput measurement, not a resident chat) ⇒ empty :slots.
    {:backend (models/llama-backend alias)
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

(defn condition-system
  "Resolve a condition's system prompt: literal :system, or :assemble through
  the REAL production assembler (ouroboros.agents.core/assemble — the ONE
  assembler; a suite that composed prompts its own way would validate
  nothing). :body-policy names a prompt POLICY artifact slug
  (ouroboros.prompts/policy-text)."
  [{:keys [system assemble]}]
  (or system
      (when assemble
        (acore/assemble
          {:preamble (prompts/preamble)
           :modules  (mapv prompts/module-text (:modules assemble))
           :body     (prompts/policy-text (:body-policy assemble))}))))

(defn run-cell
  "Execute ONE matrix cell → row map (cell ⊕ status/timing ⊕ assessment)."
  [suite ctx-cache {:keys [model thinking cond subject] :as cell}]
  (let [ctx      (get ctx-cache model)
        cond-cfg (get-in suite [:conditions cond])
        system   (condition-system cond-cfg)
        text     (get-in suite [:subjects subject])
        assess   (get core/measures (:experiment/measure suite))
        max-tok  (:max-tokens cond-cfg)
        opts    (clojure.core/cond-> {:model  :local
                                      :system system
                                      :prompt (format (:prompt-template suite) text)}
                  ;; thinking-off via escapement's MODELED field (our llama.cpp
                  ;; backend translates :disabled → chat_template_kwargs). thinking
                  ;; true ⇒ no :thinking ⇒ server default (on) — contrast preserved.
                  (not thinking)
                  (assoc :thinking {:type :disabled})
                  ;; the OPERATIONAL cap (λ extend): condition-set :max-tokens
                  ;; rides escapement's modeled field straight to llama.cpp
                  ;; max_tokens — bounds the total generation so a slow reasoning
                  ;; pass cannot blow the ask budget wall.
                  max-tok
                  (assoc :max-tokens max-tok))
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

;; ── kind :embedding — cosine-separation calibration ─────────────────────────

(defn- embed!
  "POST /embeddings on the suite's endpoint → vectors in input order."
  [{:embed/keys [endpoint model]} texts]
  (let [resp (http/post (str endpoint "/embeddings")
               {:headers {"Content-Type" "application/json"
                          "Authorization" "Bearer sk-local"}
                :body    (json/generate-string {:model model :input texts})})
        data (-> resp :body (json/parse-string true) :data)]
    (mapv :embedding (sort-by :index data))))

(defn- run-embedding!
  "Sweep every pair → cosine row → calibration. The derived :threshold (or
  the overlap verdict) is the suite's OUTPUT — promote it to the suite's
  :experiment/verdict + the gene-db knowledge page through the human gate."
  [slug suite]
  (let [rows (vec (for [[pid {:keys [a b expect]}] (sort-by key (:pairs suite))]
                    (let [[va vb] (embed! suite [a b])
                          cos     (core/cosine va vb)]
                      (println (format "── %-22s expect=%-9s cos=%.4f"
                                 (name pid) (name expect) cos))
                      {:pair pid :expect expect :cos cos})))
        cal  (core/calibrate rows)
        path (persist-results! slug rows)]
    (println (str "\n════════ CALIBRATION — " slug " ════════"))
    (println (format "near     n=%d  min=%.4f mean=%.4f max=%.4f"
               (:n (:near cal)) (:min (:near cal)) (:mean (:near cal)) (:max (:near cal))))
    (println (format "distinct n=%d  min=%.4f mean=%.4f max=%.4f"
               (:n (:distinct cal)) (:min (:distinct cal)) (:mean (:distinct cal)) (:max (:distinct cal))))
    (println (if (:separated? cal)
               (format "SEPARATED — gap=%.4f → threshold=%.4f (midpoint)"
                 (:gap cal) (:threshold cal))
               (format "OVERLAP — gap=%.4f → NO safe threshold; surface for consolidation review"
                 (or (:gap cal) 0.0))))
    (println (str "\nhypothesis: " (:experiment/hypothesis suite)))
    (println (str "results → " path))
    {:rows rows :calibration cal :results-path path}))

(defn- run-chat!
  "The original chat-shaped sweep (model × thinking × condition × subject)."
  [slug suite]
  (let [cells     (core/expand-matrix suite)
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

(defn run!
  "Sweep the whole suite (dispatch on :experiment/kind; absent ⇒ :chat).
  Returns {:rows :summary :results-path} (:chat) or
  {:rows :calibration :results-path} (:embedding)."
  [slug]
  (let [suite (load-suite slug)]
    (case (:experiment/kind suite :chat)
      :embedding (run-embedding! slug suite)
      (run-chat! slug suite))))

(defn -main [& args]
  (let [slug (or (first args) (throw (ex-info "usage: bb experiment <suite-slug>" {})))]
    (run! slug)))
