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
    [ouroboros.analysis-test]
    [ouroboros.proposer.core-test]
    [ouroboros.tools-test]
    [ouroboros.compact.core-test]
    [ouroboros.cache-report-test]
    [ouroboros.agents-test]
    [ouroboros.models-test]
    [ouroboros.llm.llamacpp-test]
    [ouroboros.verdict-test]
    [ouroboros.experiment.core-test]
    [ouroboros.gene.core-test]
    [ouroboros.gene.ast-test]
    [ouroboros.gene-test]
    [ouroboros.signals.core-test]
    [ouroboros.signals-test]
    [ouroboros.proposals-test]
    [ouroboros.schedule-test]
    [ouroboros.screen-test]
    [ouroboros.workflow-test]
    [ouroboros.generator-core-test]
    [ouroboros.game.cards-test]
    [ouroboros.game.poker.eval-test]
    [ouroboros.game.poker-test]
    [ouroboros.game.arena-test]
    [ouroboros.game.llm-test]
    [ouroboros.game.dashboard-test]))

(defn run! [& _]
  (let [{:keys [fail error]}
        (t/run-tests
          'ouroboros.smoke-test
          'ouroboros.mementum.okf-test
          'ouroboros.mementum.store-test
          'ouroboros.mementum.eql-test
          'ouroboros.session-test
          'ouroboros.analysis-test
          'ouroboros.proposer.core-test
          'ouroboros.tools-test
          'ouroboros.compact.core-test
          'ouroboros.cache-report-test
          'ouroboros.agents-test
          'ouroboros.models-test
          'ouroboros.llm.llamacpp-test
          'ouroboros.verdict-test
          'ouroboros.experiment.core-test
          'ouroboros.gene.core-test
          'ouroboros.gene.ast-test
          'ouroboros.gene-test
          'ouroboros.signals.core-test
          'ouroboros.signals-test
          'ouroboros.proposals-test
          'ouroboros.schedule-test
          'ouroboros.screen-test
          'ouroboros.workflow-test
          'ouroboros.generator-core-test
          'ouroboros.game.cards-test
          'ouroboros.game.poker.eval-test
          'ouroboros.game.poker-test
          'ouroboros.game.arena-test
          'ouroboros.game.llm-test
          'ouroboros.game.dashboard-test)]
    (System/exit (if (pos? (+ fail error)) 1 0))))
