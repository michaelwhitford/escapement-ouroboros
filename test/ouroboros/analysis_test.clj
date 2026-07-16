(ns ouroboros.analysis-test
  "Pure digest fns over CANNED kondo result maps — no pod, deterministic.
  (The impure kondo-run! edge is exercised only by live analyst runs.)"
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [ouroboros.analysis :as analysis]))

(def canned
  {:findings
   [{:filename "src/a/core.clj" :row 5 :col 3 :level :warning
     :type :unused-binding :message "unused binding y"}
    {:filename "src/a/util.clj" :row 9 :col 1 :level :error
     :type :unresolved-symbol :message "Unresolved symbol: foo"}
    {:filename "src/a/util.clj" :row 12 :col 1 :level :warning
     :type :unused-private-var :message "Unused private var a.util/helper2"}]
   :analysis
   {:namespace-usages
    [{:from 'a.core :to 'a.util :filename "src/a/core.clj" :row 3}
     {:from 'a.core :to 'clojure.string :filename "src/a/core.clj" :row 4}
     {:from 'a.util :to 'clojure.string :filename "src/a/util.clj" :row 2}]
    :var-definitions
    [{:ns 'a.core :name 'run! :arglist-strs ["[x]" "[x opts]"]
      :filename "src/a/core.clj" :row 10}
     {:ns 'a.util :name 'helper :private true :arglist-strs ["[s]"]
      :filename "src/a/util.clj" :row 7}]
    :var-usages
    [{:from 'a.core :to 'a.util :name 'helper :filename "src/a/core.clj" :row 12}
     {:from 'a.other :to 'a.util :name 'helper :filename "src/a/other.clj" :row 3}
     {:from 'a.core :to 'b.thing :name 'helper :filename "src/a/core.clj" :row 20}]}})

(deftest lint-digest-counts-and-orders-errors-first
  (let [d (analysis/lint-digest canned)]
    (is (str/starts-with? d "FINDINGS (3)"))
    (is (str/includes? d "{:warning 2, :error 1}"))
    (testing "errors sort before warnings"
      (is (< (str/index-of d "unresolved-symbol") (str/index-of d "unused-binding"))))
    (is (str/includes? d "error src/a/util.clj:9:1 [unresolved-symbol] Unresolved symbol: foo"))))

(deftest lint-digest-clean
  (is (str/includes? (analysis/lint-digest {:findings []}) "(clean — no findings)")))

(deftest ns-graph-digest-one-line-per-from-ns
  (let [d (analysis/ns-graph-digest canned)]
    (is (str/includes? d "NAMESPACE DEPS (2 namespaces):"))
    (is (str/includes? d "a.core → a.util clojure.string"))
    (is (str/includes? d "a.util → clojure.string"))))

(deftest var-defs-digest-filters-by-ns-and-marks-private
  (let [all (analysis/var-defs-digest canned nil)
        one (analysis/var-defs-digest canned "a.util")]
    (is (str/includes? all "VAR DEFINITIONS (2):"))
    (is (str/includes? all "a.core/run!  [x] [x opts]  — src/a/core.clj:10"))
    (is (str/includes? one "VAR DEFINITIONS in a.util (1):"))
    (is (str/includes? one "a.util/helper  [s]  [private]"))
    (is (not (str/includes? one "a.core/run!")))
    (testing "unknown ns points back at ns-graph"
      (is (str/includes? (analysis/var-defs-digest canned "no.such")
            "(none — check the ns name against ns-graph)")))))

(deftest usages-digest-bare-vs-qualified
  (testing "bare name matches ANY target ns"
    (let [d (analysis/usages-digest canned "helper")]
      (is (str/includes? d "USAGES of helper (3):"))))
  (testing "qualified ns/name pins the target ns"
    (let [d (analysis/usages-digest canned "a.util/helper")]
      (is (str/includes? d "USAGES of a.util/helper (2):"))
      (is (str/includes? d "src/a/core.clj:12"))
      (is (str/includes? d "src/a/other.clj:3"))
      (is (not (str/includes? d "src/a/core.clj:20")))))
  (testing "no hits names the dead-code cross-check"
    (is (str/includes? (analysis/usages-digest canned "a.wrong/helper")
          "dead-code candidate"))))

(deftest unused-digest-filters-to-dead-code-linters
  (let [d (analysis/unused-digest canned)]
    (is (str/includes? d "UNUSED/DEAD (2 of 3 findings):"))
    (is (str/includes? d "unused-binding"))
    (is (str/includes? d "unused-private-var"))
    (is (not (str/includes? d "unresolved-symbol")))))

(deftest digests-are-bounded
  (let [many {:findings (vec (for [i (range 100)]
                               {:filename "src/x.clj" :row i :col 1 :level :warning
                                :type :unused-binding :message (str "b" i)}))}
        d    (analysis/lint-digest many)]
    (is (= (+ 2 analysis/max-lines) (count (str/split-lines d)))
      "header + cap + elision tail")
    (is (str/includes? d "… 40 more (narrow the path/query)"))))
