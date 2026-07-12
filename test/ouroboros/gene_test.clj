(ns ouroboros.gene-test
  "Gene store tests — the three intake gates + real-genome decomposition.
  Deterministic: temp dirs, no LLM, no git commits. The decompose test runs
  against the REAL base-tier curator genome (classpath resource)."
  (:require
    [babashka.fs :as fs]
    [babashka.process :as proc]
    [clojure.edn :as edn]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing]]
    [ouroboros.gene :as gene]
    [ouroboros.gene.core :as core]
    [ouroboros.mementum.eql :as eql]))

(defn- with-root [f]
  (let [root (str (fs/create-temp-dir))]
    (try (f root) (finally (fs/delete-tree root)))))

(def valid-gene
  #:gene{:name     "converge"
         :content  "λ converge. one_path per concern | ∃infrastructure → use"
         :type     :lambda
         :category :constraint
         :sources  [:agents-md]})

(deftest store-and-read
  (with-root
    (fn [root]
      (let [r (gene/store-gene! root valid-gene)]
        (is (= :converge (:gene/id r)))
        (is (= "mementum/genes/converge.edn" (:gene/path r)))
        (is (true? (:gene/written r))))
      (testing "round-trip + derived fields at load"
        (let [g (gene/read-gene root :converge)]
          (is (= (:gene/content valid-gene) (:gene/content g)) "content verbatim")
          (is (= :converge (:gene/id g)))
          (is (= (core/tree-hash (:gene/content valid-gene)) (:gene/tree-hash g)))))
      (testing "on-disk EDN carries the envelope ONLY — no derived fields"
        (let [raw (edn/read-string (slurp (str (fs/path root "mementum/genes/converge.edn"))))]
          (is (nil? (:gene/id raw)))
          (is (nil? (:gene/tree-hash raw)))))
      (is (= ["converge"] (gene/list-gene-names root))))))

(deftest gate-1-parse
  (with-root
    (fn [root]
      (testing "non-parsing :lambda content rejected, nothing persists"
        (let [e (try (gene/store-gene! root (assoc valid-gene :gene/content "λ 9bad. nope"))
                     (catch clojure.lang.ExceptionInfo e e))]
          (is (= :parse (:gene/error (ex-data e))))
          (is (= [] (gene/list-gene-names root)))))
      (testing "two clauses in one gene rejected"
        (let [e (try (gene/store-gene! root
                       (assoc valid-gene :gene/content "λ converge. a\nλ other. b"))
                     (catch clojure.lang.ExceptionInfo e e))]
          (is (= :parse (:gene/error (ex-data e))))))
      (testing "head identifier must equal :gene/name"
        (let [e (try (gene/store-gene! root
                       (assoc valid-gene :gene/content "λ diverge. a | b"))
                     (catch clojure.lang.ExceptionInfo e e))]
          (is (= :parse (:gene/error (ex-data e))))
          (is (= :parse/name-mismatch (-> (ex-data e) :errors first :error)))))
      (testing ":prose genes carry no grammar gate"
        (is (:gene/written
              (gene/store-gene! root
                #:gene{:name "note" :content "plain prose" :type :prose
                       :category :unknown :sources [:chat]})))))))

(deftest gate-2-envelope
  (with-root
    (fn [root]
      (let [e (try (gene/store-gene! root (assoc valid-gene :gene/category :vibe))
                   (catch clojure.lang.ExceptionInfo e e))]
        (is (= :envelope (:gene/error (ex-data e))))
        (is (some? (:errors (ex-data e))) "humanized errors present")
        (is (= [] (gene/list-gene-names root)) "nothing persisted")))))

(deftest gate-3-dedupe
  (with-root
    (fn [root]
      (gene/store-gene! root valid-gene)
      (testing "same tokens, different whitespace → :duplicate with pointer"
        (let [e (try (gene/store-gene! root
                       (assoc valid-gene :gene/content
                         "λ converge.   one_path per concern |\n  ∃infrastructure → use"))
                     (catch clojure.lang.ExceptionInfo e e))]
          (is (= :duplicate (:gene/error (ex-data e))))
          (is (= :converge (:gene/existing (ex-data e))))))
      (testing "same name, different content → :name-collision (human consolidates)"
        (let [e (try (gene/store-gene! root
                       (assoc valid-gene :gene/content "λ converge. a different body"))
                     (catch clojure.lang.ExceptionInfo e e))]
          (is (= :name-collision (:gene/error (ex-data e))))
          (is (= :converge (:gene/existing (ex-data e)))))))))

(deftest eql-veneer
  (with-root
    (fn [root]
      (let [ctx {:mementum/root root}]
        (testing "gene/store! mutation — valid gene persists, structured ok"
          (let [r (get (eql/process ctx [(list 'gene/store! valid-gene)])
                    'gene/store!)]
            (is (true? (:gene/written r)))
            (is (= :converge (:gene/id r)))
            (is (nil? (:gene/error r)))))
        (testing "ident read joins envelope + derived tree-hash + scores"
          (let [q [{[:gene/id :converge]
                    [:gene/exists? :gene/content :gene/tree-hash :gene/scores]}]
                g (get (eql/process ctx q) [:gene/id :converge])]
            (is (true? (:gene/exists? g)))
            (is (= (:gene/content valid-gene) (:gene/content g)))
            (is (some? (:gene/tree-hash g)))
            (is (= [] (:gene/scores g)) "side-store absent ⇒ empty, nil-safe")))
        (testing "scores side-store joins once written"
          (gene/append-score! root :converge {:score 10 :model :local :notes "load-bearing"})
          (let [g (get (eql/process ctx [{[:gene/id :converge] [:gene/scores]}])
                    [:gene/id :converge])]
            (is (= [{:score 10 :model :local :notes "load-bearing"}]
                  (:gene/scores g)))))
        (testing "index lists the corpus"
          (let [idx (:mementum/genes (eql/process ctx [{:mementum/genes
                                                        [:gene/id :gene/type]}]))]
            (is (= [{:gene/id :converge :gene/type :lambda}] idx))))
        (testing "duplicate via mutation → structured rejection with pointer"
          (let [r (get (eql/process ctx [(list 'gene/store!
                                           (assoc valid-gene :gene/content
                                             "λ converge.  one_path per concern  | ∃infrastructure → use"))])
                    'gene/store!)]
            (is (false? (:gene/written r)))
            (is (= :duplicate (:gene/error r)))
            (is (= :converge (:gene/existing r)))))
        (testing "parse-fail via mutation → structured :parse"
          (let [r (get (eql/process ctx [(list 'gene/store!
                                           (assoc valid-gene
                                             :gene/name "broken"
                                             :gene/content "λ 9bad. nope"))])
                    'gene/store!)]
            (is (false? (:gene/written r)))
            (is (= :parse (:gene/error r)))))
        (testing "absent gene reads as :gene/exists? false"
          (let [g (get (eql/process ctx [{[:gene/id :ghost] [:gene/exists?]}])
                    [:gene/id :ghost])]
            (is (false? (:gene/exists? g)))))
        (testing "parser topology served as data"
          (is (= core/topology
                (:parser/topology (eql/process ctx [:parser/topology])))))))))

;; ---------------------------------------------------------------------------
;; Autonomous commit path — deterministic git in a temp repo (no network).
;; ---------------------------------------------------------------------------

(defn- sh! [dir & args]
  ;; GOTCHA: proc/process varargs stringify EACH arg — a vector passed as one
  ;; arg execs "[git" ... → apply, never pass the vector itself.
  (let [{:keys [exit err]} @(apply proc/process
                              {:dir (str dir) :out :string :err :string} args)]
    (assert (zero? exit) (str args " → " err))))

(defn- git-out [dir & args]
  (let [{:keys [out]} @(apply proc/process
                         {:dir (str dir) :out :string :err :string}
                         "git" args)]
    (str/trim out)))

(defn- with-git-root [f]
  (with-root
    (fn [root]
      (sh! root "git" "init" "-q")
      (sh! root "git" "config" "user.name" "test")
      (sh! root "git" "config" "user.email" "test@test")
      (f root))))

(deftest commit-message-shape
  (let [msg (gene/commit-message :select {:trigger "decompose genome/curator"})]
    (is (str/starts-with? msg "💡 gene: select\n")
      "first line reads standalone in git log --oneline")
    (is (str/includes? msg "trigger: decompose genome/curator"))
    (is (str/includes? msg "decidable(∀gates)"))
    (is (str/includes? msg
          "⚛️ Generated with [nucleus](https://github.com/michaelwhitford/nucleus)"))
    (is (str/includes? msg "Co-Authored-By: nucleus <noreply@whitford.us>"))))

(deftest autonomous-commit-path
  (with-git-root
    (fn [root]
      (gene/store-gene! root valid-gene)
      (testing "gate-passing dirty gene → scoped agent-authored commit"
        (let [{:keys [committed rejected]}
              (gene/commit-genes! root {:trigger "test decompose"})]
          (is (= [] rejected))
          (is (= [:converge] (mapv :gene/id committed)))
          (is (some? (:commit (first committed)))))
        (is (= "gene-db" (git-out root "log" "-1" "--format=%an"))
          "agent IS the git author — the autonomy audit trail")
        (is (str/starts-with?
              (git-out root "log" "-1" "--format=%s") "💡 gene: converge"))
        (is (= "mementum/genes/converge.edn"
              (git-out root "log" "-1" "--name-only" "--format="))
          "--only scope: exactly the one gene file in the commit"))
      (testing "clean queue → no-op"
        (is (= {:committed [] :rejected []}
              (gene/commit-genes! root {:trigger "noop"}))))
      (testing "hand-edited INVALID gene → verified at commit time, NOT committed"
        (let [before (git-out root "rev-parse" "HEAD")]
          (spit (str (fs/path root "mementum/genes/converge.edn"))
            (pr-str (assoc valid-gene :gene/category :vibe)))
          (let [{:keys [committed rejected]}
                (gene/commit-genes! root {:trigger "tamper"})]
            (is (= [] committed))
            (is (= [:envelope] (mapv :gene/error rejected))))
          (is (= before (git-out root "rev-parse" "HEAD")) "HEAD untouched"))))))

(deftest decompose-curator
  (with-root
    (fn [root]
      (let [{:keys [stored rejected]} (gene/decompose-genome! root :curator)]
        (is (= [] rejected))
        (is (= #{:engage :identity :observe :metabolize :select :propose
                 :repair :terminate}
              (set stored))
          "the 8 λ-clauses of the real curator genome")
        (testing "sources carry genome provenance"
          (is (= [:genome/curator] (:gene/sources (gene/read-gene root :observe)))))
        (testing "verbatim: stored content re-segments to itself (round-trip)"
          (let [g (gene/read-gene root :select)
                {:keys [clauses errors]} (core/segment (:gene/content g))]
            (is (empty? errors))
            (is (= (:gene/content g) (:gene/content (first clauses))))))
        (testing "idempotent re-run → all duplicates, pointers, no crash"
          (let [{:keys [stored rejected]} (gene/decompose-genome! root :curator)]
            (is (= [] stored))
            (is (= 8 (count rejected)))
            (is (every? #(= :duplicate (:gene/error %)) rejected))
            (is (every? :gene/existing rejected))))))))
