(ns ouroboros.models-test
  "Deterministic tests for the model routing table — per-run hermetic
  credential injection (see ouroboros.models for the provider-index
  first-wins hazard this design avoids)."
  (:require
    [clojure.test :refer [deftest is]]
    [ouroboros.models :as models]))

(deftest llm-config-routes-by-alias
  (let [local  (models/llm-config :local)
        gemma4 (models/llm-config :gemma4)]
    (is (models/valid-config? local))
    (is (models/valid-config? gemma4))
    (is (= "http://localhost:5100/v1" (get-in local [:credentials 0 :base-url])))
    (is (= "http://localhost:5102/v1" (get-in gemma4 [:credentials 0 :base-url])))
    (is (= "qwen36-35b-a3b" (get-in local [:config :llm/aliases :local 0 :model])))
    (is (= "gemma-4-31b-it" (get-in gemma4 [:config :llm/aliases :gemma4 0 :model])))
    (is (= [:gemma4] (get-in gemma4 [:config :llm/preferences]))
      "each run carries ONLY its own alias — hermetic per-run routing")))

(deftest llm-config-single-credential-per-run
  (is (= 1 (count (:credentials (models/llm-config :gemma4))))
    "ONE credential per run — two same-provider creds collide on provider-index"))

(deftest llm-config-fails-loud-on-unknown-alias
  (is (thrown? clojure.lang.ExceptionInfo (models/llm-config :nonexistent))))
