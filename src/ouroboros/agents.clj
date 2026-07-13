(ns ouroboros.agents
  "The genome compiler/loader — agent-model BUILD STEP 1
  (mementum/knowledge/design/agent-model.md).

  Discovery is a FOLD over precedence-ordered sources:

    base   ⊂ src/ouroboros/agents/   via io/resource — ships WITH Ouroboros;
             survives the dep-embedding future (:local/root file today,
             classpath resource under a packaged dep tomorrow). Enumeration
             uses manifest.edn (a resource DIRECTORY cannot be listed
             portably from a jar — the manifest is the portable index).
    custom ⊂ <repo-root>/agents/     filesystem — the HOST repo's specialists.
             Ergonomics: plop a file. Identity = FILENAME STEM (slug); the
             filesystem IS the dispatch table.

  merge = union, CUSTOM-WINS-BY-SLUG, REPLACE-WHOLE. validate = fail-loud
  (ouroboros.agents.core). The roster REPORT surfaces provenance + grants —
  override and escalation are the human's audit surface.

  Pure kernel (parse/validate/merge/report): ouroboros.agents.core.
  Registry ceiling + read-only floor: ouroboros.tools."
  (:require
    [babashka.fs :as fs]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [ouroboros.agents.core :as core]
    [ouroboros.prompts :as prompts]
    [ouroboros.tools :as tools]))

(def ^:private base-prefix "ouroboros/agents/")

(defn- base-genomes
  "The base tier: slugs from manifest.edn, each genome slurped via io/resource.
  A manifest entry whose .md is missing is a broken build → fail loud."
  []
  (let [manifest-path (str base-prefix "manifest.edn")
        manifest      (io/resource manifest-path)]
    (when-not manifest
      (throw (ex-info "base agent manifest missing from classpath"
               {:resource manifest-path})))
    (vec
      (for [slug (edn/read-string (slurp manifest))
            :let [path (str base-prefix slug ".md")
                  res  (io/resource path)]]
        (do
          (when-not res
            (throw (ex-info (str "manifest lists " slug " but genome resource is missing")
                     {:resource path})))
          {:slug slug :tier :base :source path :doc (slurp res)})))))

(defn- custom-genomes
  "The custom tier: every *.md under <repo-root>/agents/ (absent dir ⇒ none)."
  [repo-root]
  (let [dir (fs/path repo-root "agents")]
    (when (fs/directory? dir)
      (vec
        (for [f (sort-by str (fs/glob dir "*.md"))]
          {:slug   (str (fs/strip-ext (fs/file-name f)))
           :tier   :custom
           :source (str f)
           :doc    (slurp (fs/file f))})))))

(defn- assemble-agent
  "Compiled agent → agent with the FINAL :prompt: preamble ⊕ granted module
  texts ⊕ body (ouroboros.agents.core/assemble — the ONE assembler). Module
  texts resolve here (the impure edge); grants were already validated against
  the registry ceiling in parse-genome. :subs today: {{MODEL}} (fail-loud
  covers any token a body carries that we don't provide)."
  [agent]
  (assoc agent :prompt
    (core/assemble {:preamble (prompts/preamble)
                    :modules  (mapv prompts/module-text (:modules agent))
                    :body     (:body agent)
                    :subs     {:MODEL (name (:model agent))}})))

(defn- compile-tier [genomes ctx]
  (into {}
    (map (fn [g] (let [agent (assemble-agent (core/parse-genome (merge g ctx)))]
                   [(:id agent) agent])))
    genomes))

(defn compile-roster
  "Compile the full agent roster for `repo-root` (default \".\"): fold base
  then custom through parse+validate+assemble, custom-wins-by-slug. Returns
  {id → compiled-agent}. Throws on ANY invalid genome (never half-run)."
  ([] (compile-roster "."))
  ([repo-root]
   (let [ctx {:registry-tools   (tools/tool-names)
              :read-only-floor  tools/read-only-tools
              :registry-modules (prompts/module-names)}]
     (core/merge-roster
       [(compile-tier (base-genomes) ctx)
        (compile-tier (custom-genomes repo-root) ctx)]))))

(defn report
  "The startup roster report (provenance + grants) for `repo-root`."
  ([] (report "."))
  ([repo-root]
   (core/report (compile-roster repo-root) tools/read-only-tools)))

(defn genome
  "Fetch ONE compiled agent by id (e.g. :curator). Fail-loud when absent —
  a chart wired to a missing genome must not half-run."
  ([id] (genome id "."))
  ([id repo-root]
   (or (get (compile-roster repo-root) id)
       (throw (ex-info (str "unknown agent genome: " id)
                {:agent id :known (vec (sort (keys (compile-roster repo-root))))})))))
