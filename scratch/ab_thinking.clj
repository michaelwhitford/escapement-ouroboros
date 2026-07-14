;; THROWAWAY A/B harness — compactor thinking ON vs OFF.
;; Pairwise comparison on REAL verbatim assistant turns pulled from prior
;; session checkpoints. Same source, same lens prompt, both settings, timed.
;; Run: bb scratch/ab_thinking.clj   (needs llama.cpp @ localhost:5100)
;; NOT for commit — human judges the pairs; decision goes to state.md.
(ns ab-thinking
  (:require
    [clojure.string :as str]
    [escapement.llm :as ellm]
    [ouroboros.llm.llamacpp :as llamacpp]
    [ouroboros.compact :as compact]
    [ouroboros.session :as session]))

(def base-url "http://localhost:5100/v1")
(def model    "qwen36-35b-a3b")

(def ctx
  {:backend (llamacpp/new-backend {:base-url base-url :api-key "sk-local" :default-model model})
   :aliases {:local [{:provider :openai :model model}]}
   :preferences [:local]
   :eligibility-strict? false})

(defn samples
  "Distinct verbatim assistant texts (>120 chars) from the most recent sessions."
  [root n]
  (->> (session/list-session-ids root)
       (sort-by identity)                     ; recency approximated by trailing epoch
       reverse
       (mapcat #(session/session-messages root %))
       (filter #(and (= :assistant (:role %))
                     (not (:compacted? %))
                     (> (count (str (:text %))) 120)))
       (map :text)
       distinct
       (take n)
       vec))

(defn compact-once [text thinking?]
  (let [opts (cond-> {:system  compact/compact-system-prompt
                      :model   :local
                      :prompt  (str "compile:\n\n" text)}
               (not thinking?)
               (assoc :thinking {:type :disabled}))
        t0   (System/currentTimeMillis)
        res  (ellm/ask ctx opts)
        ms   (- (System/currentTimeMillis) t0)]
    {:ms ms :status (:status res) :text (str/trim (str (:response res)))
     :usage (:usage res)}))

(defn -main []
  (let [texts (samples "." 3)]
    (when (empty? texts)
      (println "No verbatim assistant samples found in sessions/.") (System/exit 1))
    (doseq [[i text] (map-indexed vector texts)]
      (println (str "\n════════ SAMPLE " (inc i) " (" (count text) " chars) ════════"))
      (println (subs text 0 (min 400 (count text))))
      (when (> (count text) 400) (println "…"))
      (let [on  (compact-once text true)
            off (compact-once text false)]
        (println (str "\n--- THINKING ON   (" (:ms on) " ms, status " (:status on)
                      ", usage " (:usage on) ") ---"))
        (println (:text on))
        (println (str "\n--- THINKING OFF  (" (:ms off) " ms, status " (:status off)
                      ", usage " (:usage off) ") ---"))
        (println (:text off))))
    (println "\nDone — judge pairwise: fidelity (essence kept?) · density · λ-format discipline · latency vs reading shadow.")))

(-main)
