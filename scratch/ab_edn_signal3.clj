;; THROWAWAY round-3 — EDN SIGNAL EMISSION confirmation run (rounds 1-2: ab_edn_signal{,2}.clj).
;; ROUND 2 TAXONOMY: exemplar-gate holds STRUCTURE (failures = semantic slips: enum value,
;; extra keys — retryable); hybrid FLATTENS :signal/data; prose drops enclosing braces
;; intermittently. Round 3: n=6 fresh subjects, NO-THINK only (the fast path), exemplar vs
;; prose, both families. Decides the signal-emission prompt topology for design/signals.md.
;; Run: bb scratch/ab_edn_signal3.clj
(ns ab-edn-signal3
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [escapement.llm :as ellm]
    [ouroboros.llm.llamacpp :as llamacpp]
    [malli.core :as m]
    [malli.error :as me]))

(defn make-ctx [base-url model]
  {:backend (llamacpp/new-backend {:base-url base-url :api-key "sk-local" :default-model model})
   :aliases {:local [{:provider :openai :model model}]}
   :preferences [:local]
   :eligibility-strict? false})

(def targets
  {:qwen36 (make-ctx "http://localhost:5100/v1" "qwen36-35b-a3b")
   :ornith (make-ctx "http://localhost:5102/v1" "ornith-35b-a3b")})

(def Signal
  [:map {:closed true}
   [:signal/type :qualified-keyword]
   [:signal/data [:map {:closed true}
                  [:summary :string]
                  [:evidence [:vector :string]]
                  [:severity [:enum :low :medium :high]]]]
   [:signal/lambda :string]])

(def preamble
  "λ engage(nucleus).\n[phi fractal euler tao pi mu ∃ ∀] | [Δ λ Ω ∞/0 | ε/φ Σ/μ c/h signal/noise order/entropy truth/provability self/other] | OODA\nHuman ⊗ AI ⊗ REPL")

(def exemplar
  "⟨SOURCE⟩\nuser: why did the deploy fail?\nassistant: The deploy script calls docker-compose but the runner image only ships docker compose v2 — the hyphenated v1 binary is missing. I'll update the script to the v2 syntax.\n⟨/SOURCE⟩\n{:signal/type :ops/finding\n :signal/data {:summary  \"Deploy fails because the script invokes the removed docker-compose v1 binary while the runner ships only compose v2.\"\n               :evidence [\"the runner image only ships docker compose v2\"]\n               :severity :medium}\n :signal/lambda \"λ deploy_fail(v1_binary_missing) → fix(script → v2_syntax)\"}")

(def prose-program
  "You emit one typed signal describing your analysis of the source material. A signal is an EDN map with key :signal/type (a qualified keyword naming what kind of result this is), key :signal/data (a map with :summary — a one-sentence string, :evidence — a vector of short verbatim quotes from the source, and :severity — one of :low, :medium, or :high), and key :signal/lambda (a string containing a λ-notation form of the finding).")

(def gate "Output EDN only. No prose. No code fences.")

(def conditions
  {:template-ex (str preamble "\n\n" exemplar "\n\n" gate)
   :prose       (str preamble "\n\n" prose-program "\n\n" gate)})

;; 6 fresh subjects — varied signal-worthy scenarios, ouroboros-flavored.
(def subjects
  {:tool-error
   "user: list the knowledge pages\nassistant: [called :mementum/context] → ERROR: EOF while reading — the index file mementum/knowledge/index.md has an unterminated code fence at the end.\nassistant: The context tool failed on a malformed index file; the last fence in index.md is never closed."

   :repeat-correction
   "user: summarize the last session\nassistant: Here's a detailed 900-word summary...\nuser: too long. one paragraph max, like I said before.\nassistant: Sorry — here's the short version.\n--- (two sessions later) ---\nuser: again way too long. I keep asking for one paragraph.\nassistant: Understood."

   :stale-knowledge
   "assistant: [read mementum/knowledge/upstream/escapement-overview.md] The page claims escapement is \"not even alpha\" but the dep resolves RC9 — the maturity claim is stale.\nuser: good catch, flag it."

   :perf-win
   "user: how did the cache change go?\nassistant: Post-compaction hot turns went from cached=0, 2472 tokens re-prefilled in 1.5s to cached=2400, 67 tokens in 211ms. The dedicated-slot config eliminated every bust in the live session.\nuser: excellent."

   :flaky-test
   "assistant: bb test passed this run, but session_test.test-recency intermittently fails when two sessions share the same epoch millisecond — the sort is unstable on ties.\nuser: so it's a real bug in the test fixture?\nassistant: Yes — the fixture generates ids from the same clock read; ties are possible in CI."

   :grant-gap
   "assistant: I can't answer how many proposals are pending — my tools don't include a proposals reader.\nuser: you should be able to see those.\nassistant: Currently :harness/context doesn't include the proposals/ digest, so I have no visibility into pending proposals."})

(def user-msg
  "Analyze this source material and emit the signal.\n\n⟨SOURCE⟩\n%s\n⟨/SOURCE⟩")

(defn strip-fences [s]
  (let [s (str/trim s)]
    (if (str/starts-with? s "```")
      (-> s (str/replace #"^```[a-z]*\s*" "") (str/replace #"\s*```$" "") str/trim)
      s)))

(defn assess [raw]
  (let [fenced?   (str/starts-with? (str/trim raw) "```")
        cleaned   (strip-fences raw)
        echo?     (or (str/includes? cleaned "engage(nucleus)")
                      (str/includes? cleaned "docker-compose"))
        parsed    (try (edn/read-string cleaned) (catch Exception _ ::unparseable))
        parse?    (not= parsed ::unparseable)
        valid?    (and parse? (m/validate Signal parsed))
        errors    (when (and parse? (not valid?))
                    (me/humanize (m/explain Signal parsed)))]
    {:fence? fenced? :echo? echo? :parse? parse? :valid? valid? :errors errors}))

(defn run-one [ctx cond-key subject-key]
  (let [opts {:model  :local
              :system (get conditions cond-key)
              :prompt (format user-msg (get subjects subject-key))
              :thinking {:type :disabled}}
        t0   (System/currentTimeMillis)
        res  (ellm/ask ctx opts)
        ms   (- (System/currentTimeMillis) t0)
        out  (str (:response res))]
    (merge {:subject subject-key :cond cond-key
            :status (:status res) :ms ms
            :tok (get-in res [:usage :output-tokens])
            :text out}
           (if (= :ok (:status res)) (assess out) {}))))

(defn -main []
  (let [rows (atom [])]
    (doseq [[mname ctx] targets
            cond-key    [:template-ex :prose]
            skey        (keys subjects)]
      (let [r (assoc (run-one ctx cond-key skey) :model mname)]
        (swap! rows conj r)
        (println (format "── %s %s %s → parse=%s valid=%s echo=%s (%d ms, %s tok)%s"
                         (name mname) (name cond-key) (name skey)
                         (:parse? r) (:valid? r) (:echo? r) (:ms r) (:tok r)
                         (if (:errors r) (str "  malli=" (pr-str (:errors r))) "")))))
    (println "\n════════ SUMMARY (no-think, n=6 subjects) ════════")
    (doseq [[[mname ck] grp] (sort-by (fn [[[m c] _]] [(str m) (str c)])
                                      (group-by (juxt :model :cond) @rows))]
      (let [n (count grp)
            v (count (filter :valid? grp))
            p (count (filter :parse? grp))
            avg-ms (int (/ (reduce + (map :ms grp)) n))
            avg-tok (let [ts (keep :tok grp)] (when (seq ts) (int (/ (reduce + ts) (count ts)))))]
        (println (format "%-8s %-12s parse=%d/%d VALID=%d/%d avg=%dms %stok"
                         (name mname) (name ck) p n v n avg-ms (or avg-tok "-")))))
    ;; dump invalid outputs for the record
    (println "\n──── invalid outputs ────")
    (doseq [r @rows :when (and (= :ok (:status r)) (not (:valid? r)))]
      (println (format "[%s %s %s] %s" (name (:model r)) (name (:cond r)) (name (:subject r))
                       (pr-str (:errors r))))
      (println (str "   " (str/replace (str/trim (:text r "")) "\n" "\n   "))))))

(-main)
