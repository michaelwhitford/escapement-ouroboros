(ns ouroboros.test-runner
  "Aggregate bb test runner. Requires every test namespace and runs them under
  clojure.test, exiting nonzero on any failure/error. Add new test namespaces
  to both the :require and the run-tests call."
  (:require
    [clojure.test :as t]
    [ouroboros.smoke-test]
    [ouroboros.mementum.okf-test]
    [ouroboros.mementum.store-test]
    [ouroboros.mementum.eql-test]
    [ouroboros.session-test]
    [ouroboros.curator.core-test]
    [ouroboros.tools-test]
    [ouroboros.compact.core-test]
    [ouroboros.agents-test]
    [ouroboros.models-test]))

(defn run! [& _]
  (let [{:keys [fail error]}
        (t/run-tests
          'ouroboros.smoke-test
          'ouroboros.mementum.okf-test
          'ouroboros.mementum.store-test
          'ouroboros.mementum.eql-test
          'ouroboros.session-test
          'ouroboros.curator.core-test
          'ouroboros.tools-test
          'ouroboros.compact.core-test
          'ouroboros.agents-test
          'ouroboros.models-test)]
    (System/exit (if (pos? (+ fail error)) 1 0))))
