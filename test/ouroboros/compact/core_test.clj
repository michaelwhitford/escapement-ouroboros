(ns ouroboros.compact.core-test
  "Deterministic tests for the λ-compaction message-list logic. No LLM, no
  network — pure functions only."
  (:require
    [clojure.test :refer [deftest testing is]]
    [ouroboros.compact.core :as core]))

(deftest render-shape
  (testing "canonical messages render into escapement :initial-messages shape"
    (let [msgs [(core/message :user "hi")
                (assoc (core/message :assistant "λ x.") :compacted? true)]]
      (is (= [{:role :user      :content [{:type :text :text "hi"}]}
              {:role :assistant :content [{:type :text :text "λ x."}]}]
            (core/render-messages msgs)))
      (testing "shape is identical for verbatim and λ text — the cache-stability property"
        (is (every? #(and (:role %) (vector? (:content %))) (core/render-messages msgs)))))))

(deftest append-helpers
  (let [m0 []
        m1 (core/append-user m0 "u1")
        m2 (core/append-assistant m1 "a1")]
    (is (= [{:role :user :text "u1" :compacted? false}] m1))
    (is (= :assistant (:role (last m2))))
    (is (false? (:compacted? (last m2))))))

(deftest k1-eviction-sequence
  (testing "k=1 keeps the single most-recent assistant verbatim; the one behind it is due"
    ;; greeting turn: [greetU, A0]
    (let [g   [(core/message :user "greet") (core/message :assistant "A0")]]
      (testing "A0 is the only/latest assistant → nothing due"
        (is (nil? (core/next-to-compact g 1)))
        (is (false? (core/needs-compaction? g 1))))

      ;; turn 1: user U1 then reply A1 → [greetU, A0, U1, A1]
      (let [t1 (-> g (core/append-user "U1") (core/append-assistant "A1"))]
        (testing "now A0 has aged out behind A1 → A0 (index 1) is due"
          (is (= 1 (core/next-to-compact t1 1)))
          (is (= "A0" (core/compact-target-text t1 1))))

        ;; compact A0 → λA0
        (let [t1c (core/apply-compaction t1 1 "λ A0.")]
          (is (true? (:compacted? (nth t1c 1))))
          (is (= "λ A0." (:text (nth t1c 1))))
          (testing "after compacting A0, A1 is the latest → nothing else due"
            (is (nil? (core/next-to-compact t1c 1))))

          ;; turn 2: U2 then A2 → A1 ages out
          (let [t2 (-> t1c (core/append-user "U2") (core/append-assistant "A2"))]
            (testing "A1 (index 3) is now due; A0 already compacted, A2 in window"
              (is (= 3 (core/next-to-compact t2 1)))
              (is (= "A1" (core/compact-target-text t2 1))))))))))

(deftest user-messages-never-compacted
  (testing "no user message is ever selected for compaction, at any k"
    (let [msgs (-> []
                 (core/append-user "u1") (core/append-assistant "a1")
                 (core/append-user "u2") (core/append-assistant "a2")
                 (core/append-user "u3") (core/append-assistant "a3"))]
      (doseq [k [1 2 3]]
        (loop [m msgs]
          (when-let [i (core/next-to-compact m k)]
            (is (= :assistant (:role (nth m i))) "only assistant messages are compaction targets")
            (recur (core/apply-compaction m k (str "λ" i)))))))))

(deftest blank-lambda-is-noop
  (testing "a blank/failed compaction leaves the message verbatim (lag-safe)"
    (let [msgs (-> [] (core/append-user "u1") (core/append-assistant "a1")
                 (core/append-user "u2") (core/append-assistant "a2"))]
      (is (= msgs (core/apply-compaction msgs 1 "")))
      (is (= msgs (core/apply-compaction msgs 1 "   ")))
      (is (false? (:compacted? (nth (core/apply-compaction msgs 1 "") 1)))))))

(deftest lambda-label-stripped
  (testing "a leading \"λ:\" answer-marker from the exemplar gate is normalized away"
    (let [msgs (-> [] (core/append-user "u1") (core/append-assistant "a1")
                 (core/append-user "u2") (core/append-assistant "a2"))]
      (is (= "decision(x) ∧ next(∅)"
            (:text (nth (core/apply-compaction msgs 1 "λ: decision(x) ∧ next(∅)") 1))))
      (is (= "decision(y)"
            (:text (nth (core/apply-compaction msgs 1 "  λ:  decision(y)  ") 1))))
      (testing "λ used as NOTATION (not a label) is untouched"
        (is (= "λ f. f(x)"
              (:text (nth (core/apply-compaction msgs 1 "λ f. f(x)") 1)))))
      (testing "a bare label with no body is a failed compaction → verbatim"
        (is (= msgs (core/apply-compaction msgs 1 "λ:")))
        (is (= msgs (core/apply-compaction msgs 1 " λ: ")))))))

(deftest backlog-drains-oldest-first
  (testing "if compaction lagged, multiple assistants are due → oldest goes first"
    (let [msgs (-> [] (core/append-user "u1") (core/append-assistant "a1")
                 (core/append-user "u2") (core/append-assistant "a2")
                 (core/append-user "u3") (core/append-assistant "a3"))]
      ;; a1(1) and a2(3) are aged out (a3 in window); a1 is oldest → first
      (is (= 1 (core/next-to-compact msgs 1)))
      (let [after (core/apply-compaction msgs 1 "λa1")]
        (is (= 3 (core/next-to-compact after 1)))))))

;; ---------------------------------------------------------------------------
;; Echo line discipline (the text-UI kernel)
;; ---------------------------------------------------------------------------

(defn- echo-all
  "Fold a seq of streamed chunks through core/echo-text; return the full
  printed string (as the terminal would show it, before any echo-break)."
  [chunks]
  (:out (reduce (fn [{:keys [state out]} chunk]
                  (let [{:keys [state] :as r} (core/echo-text state "assistant: " chunk)]
                    {:state state :out (str out (:out r))}))
          {:state core/echo-init :out ""}
          chunks)))

(deftest echo-prefixes-every-line
  (testing "each emitted line begins with the role prefix"
    (is (= "assistant: hello" (echo-all ["hello"])))
    (is (= "assistant: one\nassistant: two" (echo-all ["one\ntwo"])))
    (testing "prefix is applied once per line even when tokens arrive split"
      (is (= "assistant: hel lo" (echo-all ["hel" " " "lo"])))
      (is (= "assistant: one\nassistant: two" (echo-all ["one\n" "two"]))))))

(deftest echo-collapses-blank-lines
  (testing "newline runs collapse to ONE; whitespace-only lines vanish"
    (is (= "assistant: a\nassistant: b" (echo-all ["a\n\n\nb"])))
    (is (= "assistant: a\nassistant: b" (echo-all ["a\n  \n\t\nb"])))
    (testing "even split across chunks"
      (is (= "assistant: a\nassistant: b" (echo-all ["a\n" "\n" "  \n" "b"]))))))

(deftest echo-strips-leading-and-trailing-blanks
  (testing "leading newlines/whitespace-lines before any content are dropped"
    (is (= "assistant: hi" (echo-all ["\n\n  \nhi"]))))
  (testing "trailing newlines/whitespace are deferred and never printed"
    (is (= "assistant: hi" (echo-all ["hi\n\n  "])))
    (is (= "" (echo-all ["\n\n   \n"])))))

(deftest echo-preserves-intra-line-content
  (testing "line-leading indentation with content is preserved (code blocks)"
    (is (= "assistant: code:\nassistant:   (defn f [x]"
          (echo-all ["code:\n  (defn f [x]"]))))
  (testing "intra-line spacing survives token splits"
    (is (= "assistant: a  b" (echo-all ["a" "  " "b"])))))

(deftest echo-break-closes-open-lines
  (testing "break emits a newline only when content was emitted, and resets"
    (let [{:keys [state]} (core/echo-text core/echo-init "assistant: " "hi")]
      (is (= "\n" (:out (core/echo-break state))))
      (is (= core/echo-init (:state (core/echo-break state)))))
    (testing "no content → no newline"
      (is (= "" (:out (core/echo-break core/echo-init))))
      (let [{:keys [state]} (core/echo-text core/echo-init "assistant: " "\n \n")]
        (is (= "" (:out (core/echo-break state))))))))

(deftest tool-line-renders-and-truncates
  (testing "tool + params on one line, params pr-str'd"
    (is (= "tool: :fs/read {:path \"idea.md\"}"
          (core/tool-line :fs/read {:path "idea.md"}))))
  (testing "nil input → name only"
    (is (= "tool: :mementum/context" (core/tool-line :mementum/context nil))))
  (testing "oversized params are truncated with an ellipsis"
    (let [line (core/tool-line :fs/write {:path "f" :content (apply str (repeat 500 "x"))} 40)]
      (is (<= (count line) (+ (count "tool: :fs/write ") 41)))
      (is (clojure.string/ends-with? line "…")))))
