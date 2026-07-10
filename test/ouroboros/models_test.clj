(ns ouroboros.models-test
  "Deterministic tests for the model routing table — per-run hermetic
  credential injection (see ouroboros.models for the provider-index
  first-wins hazard this design avoids)."
  (:require
    [clojure.test :refer [deftest is]]
    [ouroboros.models :as models]))

(deftest llm-config-routes-by-alias
  (let [local  (models/llm-config :local)
        ornith (models/llm-config :ornith)]
    (is (models/valid-config? local))
    (is (models/valid-config? ornith))
    (is (= "http://localhost:5100/v1" (get-in local [:credentials 0 :base-url])))
    (is (= "http://localhost:5102/v1" (get-in ornith [:credentials 0 :base-url])))
    (is (= "qwen36-35b-a3b" (get-in local [:config :llm/aliases :local 0 :model])))
    (is (= "ornith-35b-a3b" (get-in ornith [:config :llm/aliases :ornith 0 :model])))
    (is (= [:ornith] (get-in ornith [:config :llm/preferences]))
      "each run carries ONLY its own alias — hermetic per-run routing")))

(deftest llm-config-single-credential-per-run
  (is (= 1 (count (:credentials (models/llm-config :ornith))))
    "ONE credential per run — two same-provider creds collide on provider-index"))

(deftest llm-config-fails-loud-on-unknown-alias
  (is (thrown? clojure.lang.ExceptionInfo (models/llm-config :nonexistent))))
