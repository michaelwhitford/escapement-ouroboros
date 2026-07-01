(ns ouroboros.mementum.okf-test
  "OKF core: parse/emit roundtrip + the Malli boundary gate."
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [ouroboros.mementum.okf :as okf]))

(def sample
  (str "---\n"
       "type: mementum/memory\n"
       "description: a crisp one-line essence\n"
       "tags: [a, b]\n"
       "status: open\n"
       "---\n\n"
       "💡 the body\n"))

(deftest parse-splits-frontmatter-and-body
  (let [{:keys [frontmatter body]} (okf/parse sample)]
    (is (= "mementum/memory" (:type frontmatter)))
    (is (= "a crisp one-line essence" (:description frontmatter)))
    (is (= ["a" "b"] (vec (:tags frontmatter))))
    (is (str/includes? body "the body"))))

(deftest parse-without-fence-is-all-body
  (let [{:keys [frontmatter body]} (okf/parse "no frontmatter here")]
    (is (= {} frontmatter))
    (is (= "no frontmatter here" body))))

(deftest roundtrip-is-semantically-stable
  (let [p1 (okf/parse sample)
        p2 (okf/parse (okf/emit p1))]
    (is (= (:frontmatter p1) (:frontmatter p2)))
    (is (= (:body p1) (:body p2)))))

(deftest validate-enforces-invariants
  (testing "valid frontmatter passes"
    (is (okf/valid? {:type "mementum/knowledge" :description "d"})))
  (testing "type must be ^mementum/ namespaced"
    (is (not (okf/valid? {:type "bogus" :description "d"})))
    (is (contains? (okf/explain {:type "bogus" :description "d"}) :type)))
  (testing "description is required + non-blank"
    (is (not (okf/valid? {:type "mementum/memory"})))
    (is (not (okf/valid? {:type "mementum/memory" :description "  "}))))
  (testing "validate! throws :okf/invalid on bad input"
    (is (thrown? clojure.lang.ExceptionInfo (okf/validate! {:type "x" :description "d"})))
    (is (= :okf/invalid
          (try (okf/validate! {:type "x" :description "d"})
               (catch clojure.lang.ExceptionInfo e (:mementum/error (ex-data e))))))))

(deftest known-types-all-validate
  (doseq [t okf/types]
    (is (okf/valid? {:type t :description "d"}) (str t " should validate"))))
