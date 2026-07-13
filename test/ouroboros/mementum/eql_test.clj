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
;; ---------------------------------------------------------------------------
;; Signals — the data plane through the veneer (design/signals.md)
;; ---------------------------------------------------------------------------

(deftest signal-emit-and-parameterized-read
  (let [root (temp-root)
        e    (get (eql/process {:mementum/root root}
                    [(list 'signal/emit! {:type   :s1/report
                                          :data   {:summary "sweep ok" :outcome :ok}
                                          :lambda "λ sweep → ok"
                                          :source "bb-test"})])
                'signal/emit!)]
    (is (true? (:signal/written e)))
    (is (nil? (:signal/error e)))
    (eql/process {:mementum/root root}
      [(list 'signal/emit! {:type :human/notice
                            :data {:summary "look here"}
                            :source "other"})])
    (testing "bare read returns everything oldest→newest"
      (let [all (:mementum/signals (eql/process {:mementum/root root}
                                     [:mementum/signals]))]
        (is (= 2 (count all)))
        (is (= [:s1/report :human/notice] (mapv :signal/type all)))))
    (testing "params attenuate — query ≡ subscription"
      (let [by-type (:mementum/signals
                      (eql/process {:mementum/root root}
                        [(list :mementum/signals {:type :s1/report})]))
            by-src  (:mementum/signals
                      (eql/process {:mementum/root root}
                        [(list :mementum/signals {:source "other"})]))
            recent1 (:mementum/signals
                      (eql/process {:mementum/root root}
                        [(list :mementum/signals {:n 1})]))]
        (is (= 1 (count by-type)))
        (is (= "bb-test" (:signal/source (first by-type))))
        (is (= [:human/notice] (mapv :signal/type by-src)))
        (is (= 1 (count recent1)))
        (is (= :human/notice (:signal/type (first recent1))) "recent ≡ take-last")))))

(deftest signal-emit-structured-rejections
  (let [root (temp-root)]
    (testing "gate failure is first-class data, not a pathom error string"
      (let [e (get (eql/process {:mementum/root root}
                     [(list 'signal/emit! {:type :s1/report
                                           :data {:summary "no outcome"}
                                           :source "t"})])
                'signal/emit!)]
        (is (false? (:signal/written e)))
        (is (= :data (:signal/error e)))
        (is (some? (:signal/errors e)) "humanized malli errors surface")))
    (testing "duplicate carries the existing pointer"
      (let [emit #(get (eql/process {:mementum/root root}
                         [(list 'signal/emit! {:type :human/notice
                                               :data {:summary "once"}
                                               :source "t"})])
                    'signal/emit!)
            first-e (emit)
            dup-e   (emit)]
        (is (true? (:signal/written first-e)))
        (is (= :duplicate (:signal/error dup-e)))
        (is (= (:signal/id first-e) (:signal/existing dup-e)))))))
