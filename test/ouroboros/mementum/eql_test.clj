(ns ouroboros.mementum.eql-test
  "The pathom2 EQL veneer: resolvers read, mutations write through the OKF gate."
  (:require
    [babashka.fs :as fs]
    [clojure.test :refer [deftest is testing]]
    [ouroboros.mementum.eql :as eql]
    [ouroboros.mementum.store :as store]))

(def good "---\ntype: mementum/memory\ndescription: eql memory\n---\n💡 body\n")
(def bad  "---\ntype: bogus\ndescription: x\n---\nbody")

(defn- temp-root [] (str (fs/create-temp-dir {:prefix "mementum-eql"})))

(deftest knowledge-index-resolves-summaries
  (let [ks (:mementum/knowledge (eql/process [{:mementum/knowledge [:slug :type :description]}]))]
    (is (seq ks) "at least one knowledge page")
    (is (every? :slug ks))
    (is (every? :description ks))))

(deftest ident-read-resolves-a-known-page
  (let [ident [:mementum/ref {:kind :knowledge :slug "upstream/escapement-index"}]
        page  (get (eql/process [{ident [:mementum/exists? :mementum/type :mementum/description]}]) ident)]
    (is (true? (:mementum/exists? page)))
    (is (= "mementum/index" (:mementum/type page)))
    (is (string? (:mementum/description page)))))

(deftest recall-resolver-is-parameterized
  (let [r (:mementum/recall (eql/process [(list :mementum/recall {:query "hermetic" :n 2})]))]
    (is (seq (:grep r)))
    (is (seq (:log r)))))

(deftest mutations-write-read-delete-through-gate
  (let [root (temp-root)
        s    (get (eql/process {:mementum/root root}
                    [(list 'mementum/store! {:kind :memory :slug "m1" :content good})])
                'mementum/store!)]
    (is (true? (:mementum/written s)))
    (let [ident [:mementum/ref {:kind :memory :slug "m1"}]
          rb    (get (eql/process {:mementum/root root} [{ident [:mementum/description :mementum/exists?]}]) ident)]
      (is (true? (:mementum/exists? rb)))
      (is (= "eql memory" (:mementum/description rb))))
    (let [d (get (eql/process {:mementum/root root}
                   [(list 'mementum/delete! {:kind :memory :slug "m1"})])
                'mementum/delete!)]
      (is (true? (:mementum/deleted d))))))

(deftest synthesize-writes-knowledge
  (let [root (temp-root)
        s    (get (eql/process {:mementum/root root}
                    [(list 'mementum/synthesize!
                       {:slug "topic1"
                        :content "---\ntype: mementum/knowledge\ndescription: k desc\nresource: r\n---\nbody"})])
                'mementum/synthesize!)]
    (is (true? (:mementum/written s)))
    (is (= "mementum/knowledge/topic1.md" (:mementum/path s)))))

(deftest mutation-rejects-invalid-and-does-not-persist
  (let [root (temp-root)
        v    (get (eql/process {:mementum/root root}
                    [(list 'mementum/store! {:kind :memory :slug "bad" :content bad})])
                'mementum/store!)]
    (is (false? (:mementum/written v)) "the write is refused")
    (is (= :okf/invalid (:mementum/error v)) "structured rejection, not an opaque string")
    (is (contains? (:mementum/errors v) :type) "humanized errors point at the bad :type")
    (is (false? (fs/exists? (store/abs-path root :memory "bad"))) "nothing persisted")))
