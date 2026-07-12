(ns ouroboros.mementum.eql
  "The mementum EQL interface — S1 λ interface made real. A Pathom 2 parser
  (mirroring escapement's proven bb wiring) wraps the pathom-agnostic core
  (`store` + `okf`) into ONE uniform channel: resolvers read, mutations write,
  the OKF Malli gate rejects malformed writes at the boundary.

  Env carries `:mementum/root` (the repo dir) so the same parser serves any
  working tree — the curator, tests, or a live escapement region.

  Reads  (resolvers):
    ident  [{[:mementum/ref {:kind :knowledge :slug \"upstream/escapement-index\"}]
             [:mementum/type :mementum/description :mementum/body ...]}]
    global [{:mementum/knowledge [:slug :description]}]  · [{:mementum/memories [...]}]
    recall [(:mementum/recall {:query \"hermetic\" :n 3})]
  Writes (mutations):
    [(mementum/store!     {:kind :memory :slug \"s\" :content \"<okf>\"})]
    [(mementum/synthesize! {:slug \"topic\" :content \"<okf>\"})]      ; knowledge
    [(mementum/update!    {:kind :knowledge :slug \"s\" :content \"<okf>\"})]
    [(mementum/delete!    {:kind :memory :slug \"s\"})]"
  (:require
    [com.wsscode.pathom.core :as p]
    [com.wsscode.pathom.connect :as pc]
    [ouroboros.gene :as gene]
    [ouroboros.gene.core :as gene.core]
    [ouroboros.mementum.store :as store]))

(defn- root [env] (get env :mementum/root "."))

;; ---------------------------------------------------------------------------
;; Resolvers (reads)
;; ---------------------------------------------------------------------------

(pc/defresolver page-resolver
  "Resolve one page by ident `[:mementum/ref {:kind :slug}]` → its OKF facets.
  `:mementum/description` is the disclosure gate; `:mementum/body` the content."
  [env {:mementum/keys [ref]}]
  {::pc/input  #{:mementum/ref}
   ::pc/output [:mementum/exists? :mementum/type :mementum/description
                :mementum/status :mementum/body :mementum/path :mementum/frontmatter]}
  (let [{:keys [kind slug]} ref
        {:keys [frontmatter body path]} (store/read-doc (root env) kind slug)]
    (if frontmatter
      {:mementum/exists?     true
       :mementum/type        (:type frontmatter)
       :mementum/description (:description frontmatter)
       :mementum/status      (:status frontmatter)
       :mementum/body        body
       :mementum/path        path
       :mementum/frontmatter frontmatter}
      {:mementum/exists?     false
       :mementum/type        nil :mementum/description nil :mementum/status nil
       :mementum/body        nil :mementum/path nil :mementum/frontmatter nil})))

(pc/defresolver knowledge-index
  "Global: knowledge pages as disclosure summaries (description_first)."
  [env _]
  {::pc/output [{:mementum/knowledge [:slug :type :description]}]}
  {:mementum/knowledge (store/list-summaries (root env) :knowledge)})

(pc/defresolver memories-index
  "Global: memory pages as disclosure summaries."
  [env _]
  {::pc/output [{:mementum/memories [:slug :type :description]}]}
  {:mementum/memories (store/list-summaries (root env) :memory)})

(pc/defresolver recall-resolver
  "Global, parameterized recall: `[(:mementum/recall {:query \"q\" :n 5})]` →
  `{:grep [paths] :log [{:hash :subject}]}` (semantic + temporal)."
  [env _]
  {::pc/output [:mementum/recall]}
  (let [{:keys [query n]} (-> env :ast :params)]
    {:mementum/recall {:grep (when query (store/recall-grep (root env) query))
                       :log  (store/recall-log (root env) (or n 5))}}))

;; ---------------------------------------------------------------------------
;; Gene resolvers (design/gene-db.md §3 — the veneer over the ONE write path).
;; Idents are :gene/id KEYWORDS (λ identifier — LLM-safe handles, never uuid).
;; ---------------------------------------------------------------------------

(pc/defresolver gene-resolver
  "Resolve one gene by ident `[:gene/id :converge]` → envelope + derived
  tree-hash. `:gene/exists?` false when absent."
  [env {:gene/keys [id]}]
  {::pc/input  #{:gene/id}
   ::pc/output [:gene/exists? :gene/name :gene/content :gene/type
                :gene/category :gene/sources :gene/tree-hash]}
  (if-let [g (gene/read-gene (root env) id)]
    (assoc g :gene/exists? true)
    {:gene/exists? false
     :gene/name nil :gene/content nil :gene/type nil
     :gene/category nil :gene/sources nil :gene/tree-hash nil}))

(pc/defresolver genes-index
  "Global: the gene corpus as id-keyed summaries (content via ident read —
  disclosure discipline, load bodies on demand)."
  [env _]
  {::pc/output [{:mementum/genes [:gene/id :gene/name :gene/type :gene/category]}]}
  {:mementum/genes
   (mapv #(select-keys % [:gene/id :gene/name :gene/type :gene/category])
     (gene/all-genes (root env)))})

(pc/defresolver gene-scores
  "Side-store join: `:gene/scores` for a gene id — [] when never scored
  (the side-store is gitignored machine observation, absent ⇒ empty)."
  [env {:gene/keys [id]}]
  {::pc/input  #{:gene/id}
   ::pc/output [:gene/scores]}
  {:gene/scores (gene/read-scores (root env) id)})

(pc/defresolver parser-topology
  "The segmenter FSM served as data (design/gene-db.md §Parser:
  topology ≡ greppable ∧ testable ∧ resolver-servable)."
  [_ _]
  {::pc/output [:parser/topology]}
  {:parser/topology gene.core/topology})

;; ---------------------------------------------------------------------------
;; Mutations (writes) — all funnel through the OKF-gated core.
;; ---------------------------------------------------------------------------

;; The core `store!` THROWS on invalid OKF (the hard gate — nothing persists).
;; The veneer translates that throw into a STRUCTURED EQL rejection so callers
;; (the curator, tests) see first-class data, not pathom's opaque error string.
(def ^:private write-output
  [:mementum/path :mementum/written :mementum/error :mementum/errors])

(defn- write!
  "Run the OKF-gated core write, returning a structured result:
  ok  → {:mementum/written true  :mementum/path <p>}
  bad → {:mementum/written false :mementum/error <kw> :mementum/errors <humanized>}"
  [env kind slug content]
  (try
    (let [{:keys [path written]} (store/store! (root env) kind slug content)]
      {:mementum/path path :mementum/written written :mementum/error nil})
    (catch clojure.lang.ExceptionInfo e
      (let [d (ex-data e)]
        {:mementum/written false
         :mementum/error   (or (:mementum/error d) :error)
         :mementum/errors  (:errors d)}))))

;; NOTE: pc/defmutation takes NO docstring (arglist is [sym [env params] config
;; & body]); descriptions live in these comments (per escapement's convention).

;; store! — write a memory (or any kind). OKF-validated before persist.
(pc/defmutation store!-mut [env {:keys [kind slug content]}]
  {::pc/sym 'mementum/store! ::pc/output write-output}
  (write! env (or kind :memory) slug content))

;; synthesize! — write a knowledge page (kind :knowledge). OKF-validated first.
(pc/defmutation synthesize!-mut [env {:keys [slug content]}]
  {::pc/sym 'mementum/synthesize! ::pc/output write-output}
  (write! env :knowledge slug content))

;; update! — overwrite an existing page in place. OKF-validated before persist.
(pc/defmutation update!-mut [env {:keys [kind slug content]}]
  {::pc/sym 'mementum/update! ::pc/output write-output}
  (write! env (or kind :knowledge) slug content))

;; delete! — remove a page from the working tree.
(pc/defmutation delete!-mut [env {:keys [kind slug]}]
  {::pc/sym    'mementum/delete!
   ::pc/output [:mementum/deleted]}
  {:mementum/deleted (:deleted (store/delete! (root env) (or kind :memory) slug))})

;; gene/store! — THE gene write path via EQL. Params ARE the gene envelope:
;;   [(gene/store! #:gene{:name "n" :content "λ n. …" :type :lambda
;;                        :category :constraint :sources [:chat]})]
;; The three intake gates run INSIDE ouroboros.gene/store-gene! (core throws
;; → veneer catches → structured rejection, the mementum precedent):
;;   gate-1 parse → :gene/error :parse   · gate-2 envelope → :envelope
;;   gate-3 dedupe → :duplicate | :name-collision (+ :gene/existing pointer)
(pc/defmutation gene-store!-mut [env params]
  {::pc/sym    'gene/store!
   ::pc/output [:gene/id :gene/path :gene/written
                :gene/error :gene/errors :gene/existing]}
  (try
    (assoc (gene/store-gene! (root env) (select-keys params gene.core/envelope-keys))
      :gene/error nil)
    (catch clojure.lang.ExceptionInfo e
      (let [d (ex-data e)]
        (cond-> {:gene/written false
                 :gene/error   (or (:gene/error d) :error)}
          (:errors d)        (assoc :gene/errors (:errors d))
          (:gene/existing d) (assoc :gene/existing (:gene/existing d)))))))

;; ---------------------------------------------------------------------------
;; Parser — mirrors escapement.ui.resolvers (proven under bb).
;; ---------------------------------------------------------------------------

(def registry
  [page-resolver knowledge-index memories-index recall-resolver
   gene-resolver genes-index gene-scores parser-topology
   store!-mut synthesize!-mut update!-mut delete!-mut gene-store!-mut])

(def parser
  (delay
    (p/parser
      {::p/mutate  pc/mutate
       ::p/env     {::p/reader [p/map-reader pc/reader2 pc/ident-reader pc/index-reader]}
       ::p/plugins [(pc/connect-plugin {::pc/register registry})
                    (p/post-process-parser-plugin p/elide-not-found)
                    p/error-handler-plugin]})))

(defn process
  "Run an EQL `query` (reads and/or mutations) against mementum. `ctx` is merged
  onto the env — supply `:mementum/root` to target a working tree (default \".\")."
  ([query] (process {} query))
  ([ctx query] (@parser (merge {:mementum/root "."} ctx) query)))
