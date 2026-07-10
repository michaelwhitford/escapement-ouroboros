(ns ouroboros.models
  "The model routing table — ONE place mapping a genome's `model:` alias to a
  live llama-server endpoint (state.md carries the multi-server port map;
  probe /v1/models for truth).

  WHY per-run injection instead of one multi-credential backend: escapement's
  multi-backend `provider-index` is FIRST-WINS per provider tag, and
  `:llm/aliases` candidates carrying `:provider` dispatch by that tag,
  BYPASSING the model-string regex — so two `:openai` credentials with
  different base-urls (5100 + 5102) in ONE `lib/run` would collide (every
  request hits the first). Each `lib/run` is HERMETIC, so a runner simply
  injects the ONE credential its genome's model needs (`llm-config`).
  Multi-model-in-one-chart is deferred until a workflow actually composes
  cross-family agents in a single session."
  (:require
    [malli.core :as m]))

(def table
  "alias → live endpoint. Extend when a genome routes to a new server."
  {:local  {:base-url "http://localhost:5100/v1" :model "qwen36-35b-a3b"}
   :ornith {:base-url "http://localhost:5102/v1" :model "ornith-35b-a3b"}})

(defn llm-config
  "The `:credentials` + `:config` pair a `lib/run` needs to route `alias`
  (a genome's :model). Fail-loud on an unknown alias — a chart wired to a
  missing endpoint must not half-run."
  [alias]
  (let [{:keys [base-url model]} (get table alias)]
    (when-not model
      (throw (ex-info (str "unknown model alias: " alias)
               {:alias alias :known (vec (sort (keys table)))})))
    {:credentials [{:provider :openai :api-key "sk-local" :base-url base-url}]
     :config      {:llm/aliases             {alias [{:provider :openai :model model}]}
                   :llm/preferences         [alias]
                   :llm/eligibility-strict? false}}))

(def ^:private config-schema
  [:map
   [:credentials [:vector [:map [:provider :keyword] [:api-key :string] [:base-url :string]]]]
   [:config :map]])

(defn valid-config?
  "Sanity gate for tests: llm-config output matches the lib/run contract shape."
  [cfg]
  (m/validate config-schema cfg))
