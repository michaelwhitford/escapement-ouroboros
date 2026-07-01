(ns ouroboros.mementum.store-test
  "Git-backed store core: file ops in a temp dir + recall against the live repo."
  (:require
    [babashka.fs :as fs]
    [clojure.test :refer [deftest is testing]]
    [ouroboros.mementum.store :as store]))

(def good "---\ntype: mementum/memory\ndescription: test memory\n---\n💡 body\n")
(def bad  "---\ntype: bogus\ndescription: x\n---\nbody")

(defn- temp-root [] (str (fs/create-temp-dir {:prefix "mementum-store"})))

(deftest store-read-list-delete-roundtrip
  (let [root (temp-root)]
    (is (:written (store/store! root :memory "m1" good)))
    (is (= "test memory" (:description (:frontmatter (store/read-doc root :memory "m1")))))
    (is (= ["m1"] (store/list-slugs root :memory)))
    (is (= "test memory" (:description (first (store/list-summaries root :memory)))))
    (is (:deleted (store/delete! root :memory "m1")))
    (is (nil? (store/read-doc root :memory "m1")))))

(deftest nested-slugs-preserve-subpath
  (let [root (temp-root)]
    (store/store! root :knowledge "upstream/foo" (str "---\ntype: mementum/knowledge\ndescription: d\nresource: r\n---\nb"))
    (is (= ["upstream/foo"] (store/list-slugs root :knowledge)))))

(deftest store-rejects-invalid-before-write
  (let [root (temp-root)]
    (is (thrown? clojure.lang.ExceptionInfo (store/store! root :memory "bad" bad)))
    (is (false? (fs/exists? (store/abs-path root :memory "bad"))))))

(deftest recall-against-live-repo
  (testing "the ouroboros repo is a git repo with committed mementum files"
    (is (seq (store/recall-grep "." "hermetic")) "grep finds a known term")
    (is (seq (store/recall-log "." 3)) "log returns recent commits")))
