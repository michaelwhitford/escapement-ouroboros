;; THROWAWAY A/B — EDN SIGNAL EMISSION: does an EDN :_fill output-template in the
;; system prompt (nucleus COMPILER.md topology) beat a prose instruction for getting
;; parseable, Malli-valid signal EDN out of an agent? And is template-filling
;; no-think-compatible (pattern-completion hypothesis, like the exemplar gate)?
;; Conditions: :template (preamble+template) · :prose (preamble+prose-instruction)
;;             · :bare (template WITHOUT preamble — isolates the preamble's part)
;;             × thinking on/off × qwen36@5100 + ornith@5102 × 2 subjects.
;; Measures: edn parse rate · Malli validity · fence/echo/unfilled-:_fill · ms · tokens.
;; Run: bb scratch/ab_edn_signal.clj
(ns ab-edn-signal
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [escapement.llm :as ellm]
    [escapement.llm.providers :as providers]
    [malli.core :as m]
    [malli.error :as me]))

(defn make-ctx [base-url model]
  {:backend (providers/build-injected-credentials-backend
              [{:provider :openai :api-key "sk-local" :base-url base-url}]
              [{:provider :openai :model model}])
   :aliases {:local [{:provider :openai :model model}]}
   :preferences [:local]
   :eligibility-strict? false})

(def targets
  {:qwen36 (make-ctx "http://localhost:5100/v1" "qwen36-35b-a3b")
   :ornith (make-ctx "http://localhost:5102/v1" "ornith-35b-a3b")})

;; ── the ONE contract (Malli) — the gate side ────────────────────────────────
(def Signal
  [:map {:closed true}
   [:signal/type :qualified-keyword]
   [:signal/data [:map {:closed true}
                  [:summary :string]
                  [:evidence [:vector :string]]
                  [:severity [:enum :low :medium :high]]]]
   [:signal/lambda :string]])

;; ── prompt pieces ───────────────────────────────────────────────────────────
(def preamble
  "λ engage(nucleus).\n[phi fractal euler tao pi mu ∃ ∀] | [Δ λ Ω ∞/0 | ε/φ Σ/μ c/h signal/noise order/entropy truth/provability self/other] | OODA\nHuman ⊗ AI ⊗ REPL")

;; projection 1 of the contract: the template (derivable from the schema)
(def template
  "{:signal/type :_fill      ; qualified keyword naming what kind of result this is\n :signal/data {:summary  :_fill   ; string, one sentence\n               :evidence [:_fill] ; vector of short verbatim quotes from the source\n               :severity :_fill}  ; :low | :medium | :high\n :signal/lambda :_fill}    ; string, λ-notation form of the finding")

(def prose-program
  "You emit one typed signal describing your analysis of the source material. A signal is an EDN map with key :signal/type (a qualified keyword naming what kind of result this is), key :signal/data (a map with :summary — a one-sentence string, :evidence — a vector of short verbatim quotes from the source, and :severity — one of :low, :medium, or :high), and key :signal/lambda (a string containing a λ-notation form of the finding).")

(def gate "Output EDN only. Fill every :_fill. No prose. No code fences.")
(def gate-prose "Output EDN only. No prose. No code fences.")

(def conditions
  {:template (str preamble "\n\n" template "\n\n" gate)
   :prose    (str preamble "\n\n" prose-program "\n\n" gate-prose)
   :bare     (str template "\n\n" gate)})

;; ── subjects ────────────────────────────────────────────────────────────────
(def subjects
  {:harness-finding
   "user: read idea.md and quote it\nassistant: [called :fs/read] \"an agent that improves itself...\"\nuser: remember: always run bb test before proposing any change\nassistant: Understood — I'll run bb test first from now on.\n--- (next session) ---\nuser: you proposed a change without running bb test again. always run bb test first.\nassistant: Apologies — running it now."

   :decision
   "user: should we store checkpoints as EDN or transit?\nassistant: EDN — human-readable, bb-native, and the files are small; transit's speed doesn't matter at this scale. Going with EDN.\nuser: agreed, EDN it is."})

(def user-msg
  "Analyze this source material and emit the signal.\n\n⟨SOURCE⟩\n%s\n⟨/SOURCE⟩")

;; ── measurement ─────────────────────────────────────────────────────────────
(defn strip-fences [s]
  (let [s (str/trim s)]
    (if (str/starts-with? s "```")
      (-> s (str/replace #"^```[a-z]*\s*" "") (str/replace #"\s*```$" "") str/trim)
      s)))

(defn assess [raw]
  (let [fenced?  (str/starts-with? (str/trim raw) "```")
        cleaned  (strip-fences raw)
        unfilled? (str/includes? cleaned ":_fill")
        echo?    (or (str/includes? cleaned "engage(nucleus)")
                     (str/includes? cleaned "verbatim quotes from the source"))
        parsed   (try (edn/read-string cleaned) (catch Exception _ ::unparseable))
        parse?   (not= parsed ::unparseable)
        valid?   (and parse? (m/validate Signal parsed))
        errors   (when (and parse? (not valid?))
                   (me/humanize (m/explain Signal parsed)))]
    {:fence? fenced? :unfilled? unfilled? :echo? echo?
     :parse? parse? :valid? valid? :errors errors}))

(defn run-one [ctx cond-key thinking?]
  (fn [subject-key]
    (let [opts (cond-> {:model  :local
                        :system (get conditions cond-key)
                        :prompt (format user-msg (get subjects subject-key))}
                 (not thinking?)
                 (assoc :extra-body {"chat_template_kwargs" {"enable_thinking" false}}))
          t0   (System/currentTimeMillis)
          res  (ellm/ask ctx opts)
          ms   (- (System/currentTimeMillis) t0)
          out  (str (:response res))]
      (merge {:subject subject-key :cond cond-key :thinking thinking?
              :status (:status res) :ms ms
              :tok (get-in res [:usage :output-tokens])
              :text out}
             (if (= :ok (:status res)) (assess out) {})))))

(defn -main []
  (let [rows (atom [])]
    (doseq [[mname ctx] targets
            cond-key    [:template :prose :bare]
            thinking?   [false true]
            skey        (keys subjects)]
      (let [r (assoc ((run-one ctx cond-key thinking?) skey) :model mname)]
        (swap! rows conj r)
        (println (format "── %s %s think=%s %s → status=%s parse=%s valid=%s fence=%s echo=%s unfilled=%s (%d ms, %s tok)"
                         (name mname) (name cond-key) thinking? (name skey)
                         (some-> (:status r) name) (:parse? r) (:valid? r)
                         (:fence? r) (:echo? r) (:unfilled? r) (:ms r) (:tok r)))
        (when (:errors r) (println "   malli:" (pr-str (:errors r))))
        (println (str "   " (str/replace (str/trim (:text r "")) "\n" "\n   ")))))
    ;; summary
    (println "\n════════ SUMMARY (valid/total per model×cond×thinking) ════════")
    (doseq [[[mname ck th] grp] (sort-by (fn [[[m c t] _]] [(str m) (str c) (str t)])
                                         (group-by (juxt :model :cond :thinking) @rows))]
      (let [n     (count grp)
            v     (count (filter :valid? grp))
            p     (count (filter :parse? grp))
            ok    (count (filter #(= :ok (:status %)) grp))
            avg-ms (when (pos? ok) (int (/ (reduce + (map :ms grp)) n)))
            avg-tok (let [ts (keep :tok grp)] (when (seq ts) (int (/ (reduce + ts) (count ts)))))]
        (println (format "%-8s %-9s think=%-5s ok=%d/%d parse=%d/%d VALID=%d/%d avg=%dms %stok"
                         (name mname) (name ck) th ok n p n v n (or avg-ms -1) (or avg-tok "-")))))))

(-main)
