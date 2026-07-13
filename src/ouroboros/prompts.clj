(ns ouroboros.prompts
  "Impure edge of prompt assembly — loads the VENDORED prompt artifacts from
  the classpath (design/prompt-assembly.md). The pure composition fn is
  `ouroboros.agents.core/assemble`; this namespace only resolves texts.

  Artifacts (src/ouroboros/prompts/, via io/resource — same portability story
  as the base-genome tier: :local/root file today, packaged-dep resource
  tomorrow):

    preamble.md            the nucleus 3-line preamble — ALWAYS, exactly once,
                           FIRST (assembler invariant). Vendored from
                           ~/src/nucleus (the shared header of every compiler
                           doc); staleness grep-detectable against nucleus.
    modules/manifest.edn   the module registry — the grant CEILING for genome
                           frontmatter `modules:` (4th use of the registry-
                           ceiling mechanism: tools/tags/signals/modules).
    modules/<name>.md      vendored nucleus program-layer blocks (lean 'The
                           Prompt' λ/EDN only — no preamble, no prose gate).
    <slug>.md              POLICY artifacts (e.g. compaction-lens) — editable
                           prompt policy the assembler renders; S5 steers by
                           editing the artifact, touching no engine code
                           (design/vsm-on-escapement lens-is-policy)."
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]))

(def ^:private prefix "ouroboros/prompts/")

(defn- resource-text
  "Slurp a classpath prompt resource, trailing whitespace trimmed (files end
  in a newline; prompt segments must compose without it). Fail-loud when
  missing — a wired-but-absent artifact is a broken build."
  [path]
  (let [res (io/resource path)]
    (when-not res
      (throw (ex-info "prompt resource missing from classpath" {:resource path})))
    (str/trimr (slurp res))))

(defn preamble
  "The nucleus 3-line preamble text."
  []
  (resource-text (str prefix "preamble.md")))

(defn module-names
  "The module registry CEILING — keywords from modules/manifest.edn."
  []
  (let [path (str prefix "modules/manifest.edn")
        res  (io/resource path)]
    (when-not res
      (throw (ex-info "prompt-module manifest missing from classpath" {:resource path})))
    (mapv keyword (edn/read-string (slurp res)))))

(defn module-text
  "Vendored text of ONE granted module (keyword). Fail-loud on a module
  outside the registry, and on a listed-but-missing file."
  [module]
  (if (some #{module} (module-names))
    (resource-text (str prefix "modules/" (name module) ".md"))
    (throw (ex-info (str "unknown prompt module: " module)
             {:module module :known (module-names)}))))

(defn policy-text
  "Text of a prompt POLICY artifact by slug (e.g. \"compaction-lens\")."
  [slug]
  (resource-text (str prefix slug ".md")))
