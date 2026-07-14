;; THROWAWAY round-2 — EDN SIGNAL EMISSION (see ab_edn_signal.clj for round 1).
;; ROUND 1 RESULT: parse 24/24 (EDN emission solved); VALIDITY split — prose won
;; (qwen36 no-think 2/2, ornith think 2/2), comment-annotated template lost (the
;; recurring nit: unqualified :signal/type), bare-template no-think ECHOED (preamble
;; is load-bearing). Exemplar-gate lesson applied: round 2 tests a FILLED EXEMPLAR
;; (pattern completion) and a HYBRID (template + terse λ constraints) vs prose champion.
;; Run: bb scratch/ab_edn_signal2.clj
(ns ab-edn-signal2
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

(def template
  "{:signal/type :_fill\n :signal/data {:summary  :_fill\n               :evidence [:_fill]\n               :severity :_fill}\n :signal/lambda :_fill}")

;; ONE worked exemplar (third scenario, disjoint from the subjects) — the pattern gate.
(def exemplar
  "⟨SOURCE⟩\nuser: why did the deploy fail?\nassistant: The deploy script calls docker-compose but the runner image only ships docker compose v2 — the hyphenated v1 binary is missing. I'll update the script to the v2 syntax.\n⟨/SOURCE⟩\n{:signal/type :ops/finding\n :signal/data {:summary  \"Deploy fails because the script invokes the removed docker-compose v1 binary while the runner ships only compose v2.\"\n               :evidence [\"the runner image only ships docker compose v2\"]\n               :severity :medium}\n :signal/lambda \"λ deploy_fail(v1_binary_missing) → fix(script → v2_syntax)\"}")

(def constraints
  "constraints: :signal/type ≡ qualified keyword (:domain/kind) | :summary ≡ one-sentence string | :evidence ≡ vector of verbatim source quotes | :severity ∈ {:low :medium :high} | :signal/lambda ≡ string(λ-form)")

(def prose-program
  "You emit one typed signal describing your analysis of the source material. A signal is an EDN map with key :signal/type (a qualified keyword naming what kind of result this is), key :signal/data (a map with :summary — a one-sentence string, :evidence — a vector of short verbatim quotes from the source, and :severity — one of :low, :medium, or :high), and key :signal/lambda (a string containing a λ-notation form of the finding).")

(def gate "Output EDN only. No prose. No code fences.")

(def conditions
  {:prose       (str preamble "\n\n" prose-program "\n\n" gate)
   :template-ex (str preamble "\n\n" exemplar "\n\n" gate)
   :hybrid      (str preamble "\n\n" template "\n\n" constraints "\n\n" gate)})

(def subjects
  {:harness-finding
   "user: read idea.md and quote it\nassistant: [called :fs/read] \"an agent that improves itself...\"\nuser: remember: always run bb test before proposing any change\nassistant: Understood — I'll run bb test first from now on.\n--- (next session) ---\nuser: you proposed a change without running bb test again. always run bb test first.\nassistant: Apologies — running it now."

   :decision
   "user: should we store checkpoints as EDN or transit?\nassistant: EDN — human-readable, bb-native, and the files are small; transit's speed doesn't matter at this scale. Going with EDN.\nuser: agreed, EDN it is."})

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
        unfilled? (str/includes? cleaned ":_fill")
        echo?     (or (str/includes? cleaned "engage(nucleus)")
                      (str/includes? cleaned "docker-compose"))   ; exemplar leak
        parsed    (try (edn/read-string cleaned) (catch Exception _ ::unparseable))
        parse?    (not= parsed ::unparseable)
        valid?    (and parse? (m/validate Signal parsed))
        errors    (when (and parse? (not valid?))
                    (me/humanize (m/explain Signal parsed)))]
    {:fence? fenced? :unfilled? unfilled? :echo? echo?
     :parse? parse? :valid? valid? :errors errors}))

(defn run-one [ctx cond-key thinking? subject-key]
  (let [opts (cond-> {:model  :local
                      :system (get conditions cond-key)
                      :prompt (format user-msg (get subjects subject-key))}
               (not thinking?)
               (assoc :thinking {:type :disabled}))
        t0   (System/currentTimeMillis)
        res  (ellm/ask ctx opts)
        ms   (- (System/currentTimeMillis) t0)
        out  (str (:response res))]
    (merge {:subject subject-key :cond cond-key :thinking thinking?
            :status (:status res) :ms ms
            :tok (get-in res [:usage :output-tokens])
            :text out}
           (if (= :ok (:status res)) (assess out) {}))))

(defn -main []
  (let [rows (atom [])]
    (doseq [[mname ctx] targets
            cond-key    [:prose :template-ex :hybrid]
            thinking?   [false true]
            skey        (keys subjects)]
      (let [r (assoc (run-one ctx cond-key thinking? skey) :model mname)]
        (swap! rows conj r)
        (println (format "── %s %s think=%s %s → status=%s parse=%s valid=%s fence=%s echo=%s (%d ms, %s tok)"
                         (name mname) (name cond-key) thinking? (name skey)
                         (some-> (:status r) name) (:parse? r) (:valid? r)
                         (:fence? r) (:echo? r) (:ms r) (:tok r)))
        (when (:errors r) (println "   malli:" (pr-str (:errors r))))
        (println (str "   " (str/replace (str/trim (:text r "")) "\n" "\n   ")))))
    (println "\n════════ SUMMARY (valid/total per model×cond×thinking) ════════")
    (doseq [[[mname ck th] grp] (sort-by (fn [[[m c t] _]] [(str m) (str c) (str t)])
                                         (group-by (juxt :model :cond :thinking) @rows))]
      (let [n (count grp)
            v (count (filter :valid? grp))
            p (count (filter :parse? grp))
            ok (count (filter #(= :ok (:status %)) grp))
            avg-ms (when (pos? n) (int (/ (reduce + (map :ms grp)) n)))
            avg-tok (let [ts (keep :tok grp)] (when (seq ts) (int (/ (reduce + ts) (count ts)))))]
        (println (format "%-8s %-12s think=%-5s ok=%d/%d parse=%d/%d VALID=%d/%d avg=%dms %stok"
                         (name mname) (name ck) th ok n p n v n (or avg-ms -1) (or avg-tok "-")))))))

(-main)
