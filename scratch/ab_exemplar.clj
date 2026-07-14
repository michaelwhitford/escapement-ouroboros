;; THROWAWAY round-3 harness — EXEMPLAR-GATE compactor (verbum topology:
;; pattern-completion, zero instructions) vs the instruction-λ lens, no-think.
;; Verbum finding (compiler-finetune-halt-collapse.md): the NL→λ compile
;; circuit is a BASE circuit; no-think FIXES the halt; exemplar gates are the
;; no-think-compatible prompt topology. Round 1/2 failed no-think with an
;; INSTRUCTION-λ system prompt (echo attractor). Round 3 tests the exemplar form.
;; Run: bb scratch/ab_exemplar.clj   (llama.cpp @ localhost:5100)
(ns ab-exemplar
  (:require
    [clojure.string :as str]
    [escapement.llm :as ellm]
    [ouroboros.llm.llamacpp :as llamacpp]))

(def base-url "http://localhost:5100/v1")
(def model    "qwen36-35b-a3b")

(def ctx
  {:backend (llamacpp/new-backend {:base-url base-url :api-key "sk-local" :default-model model})
   :aliases {:local [{:provider :openai :model model}]}
   :preferences [:local]
   :eligibility-strict? false})

;; ── The exemplar gate: 2 turn→λ pairs, then the input. NO instructions.
;; Exemplar 1 = content-rich decision turn. Exemplar 2 = thin/meta turn (the
;; echo-prone class) compacting to almost nothing — the discard lens BY EXAMPLE.
(def exemplar-gate
  "turn: I'd recommend write-back caching for this. Writes land in the cache and only flush to memory on eviction, which cuts your memory traffic substantially compared to write-through. The usual trade-off is coherence complexity, but since you said this is a single-core embedded target, that risk doesn't apply. Let's go with write-back — I'll assume that in the code examples from here on.
λ: decision(write-back | mem_traffic↓ ∧ ¬coherence_risk(single-core)) ∧ state(assumed_in_examples) ∧ next(∅)

turn: I don't have access to your filesystem or a live debugger, but I'm happy to help — paste the error message and the relevant function and I'll walk through it with you. What are you seeing?
λ: constraint(¬fs_access ∧ ¬live_debug) ∧ next(await(error_msg ∧ code))

turn: TCP slow start doubles the congestion window every round trip until it reaches the slow-start threshold, and after that point growth switches to linear congestion avoidance.
λ: fact(tcp_slow_start | cwnd×2/RTT → ssthresh → linear(congestion_avoidance)) ∧ next(∅)

turn: %s
λ:")

(def samples
  [;; the echo-prone thin/meta turn (round 1: OFF echoed 3/3; round 2: 2/2)
   "I don’t have direct access to debugging tools or environments, but I can help you debug by analyzing your code, walking through errors, suggesting fixes, and recommending the right tools for your setup. What are you working on?"
   ;; the content-rich factual turn
   "The first medieval mechanical clocks only had hour hands, so exact minutes weren’t measurable until the second hand was invented centuries later."
   ;; a longer multi-point turn (length stress; realistic chat reply)
   "Good question — there are three things going on. First, your handler is registered twice because the module is imported both directly and via the barrel file, so every event fires two callbacks; dedupe the import path and the double-fire disappears. Second, the debounce wrapper you added is created inside the render function, so each render makes a fresh timer and the debounce never actually holds — hoist it out or memoize it. Third, and this one is subtle: the cleanup function returned by your effect closes over a stale reference, which is why the listener leaks after unmount. Fix the first two and I'd bet the memory growth you're seeing mostly vanishes; the third is a correctness fix worth doing regardless. Want me to sketch the corrected effect?"])

(defn run-one [text thinking?]
  (let [opts (cond-> {:model  :local
                      :prompt (format exemplar-gate text)}
               (not thinking?)
               (assoc :thinking {:type :disabled}))
        t0   (System/currentTimeMillis)
        res  (ellm/ask ctx opts)
        ms   (- (System/currentTimeMillis) t0)
        out  (str/trim (str (:response res)))]
    {:ms ms :status (:status res) :text out
     :echo? (or (str/includes? out "engage(nucleus)")
                (str/includes? out "write-back caching for this"))
     :out-tokens (get-in res [:usage :output-tokens])}))

(defn -main []
  (doseq [[i text] (map-indexed vector samples)]
    (println (str "\n════════ SAMPLE " (inc i) " (" (count text) " chars) ════════"))
    (let [off (run-one text false)
          on  (run-one text true)]
      (println (str "--- EXEMPLAR + no-think  (" (:ms off) " ms, " (:out-tokens off)
                    " tok, echo? " (:echo? off) ") ---"))
      (println (:text off))
      (println (str "\n--- EXEMPLAR + thinking  (" (:ms on) " ms, " (:out-tokens on)
                    " tok, echo? " (:echo? on) ") ---"))
      (println (:text on))))
  (println "\nJudge: fidelity · density · λ-discipline · does no-think hold on the thin turn?"))

(-main)
