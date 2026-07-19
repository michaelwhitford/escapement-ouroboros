(ns ouroboros.game.dashboard-test
  "Dashboard data-layer tests — the pure, HTTP-free core: list-games (newest-first
  index over the arena games/ side-store) and load-game (hit/miss). No server, no
  network; the composed Ring handler rides escapement's tested make-handler."
  (:require
    [babashka.fs :as fs]
    [clojure.test :refer [deftest is testing]]
    [ouroboros.game.dashboard :as dash]))

(defn- write-game!
  "Spit a minimal arena-shaped transcript into <root>/games/<id>.edn."
  [root id match]
  (let [dir (str root "/games")]
    (fs/create-dirs dir)
    (spit (str dir "/" id ".edn") (pr-str (assoc match :id id)))))

(def ^:private m-old
  {:game :poker-limit-holdem :mode :reset :seed 1
   :seats [{:genome :poker-tight} {:genome :poker-aggro}] :winner nil
   :totals {0 -25 1 25}
   :hands [{:hand 0 :result {:ending :fold :winners [1] :pot 30}
            :decisions [{:seat 0 :action {:action :fold :why "nit"} :ms 100 :error nil}]}]})

(def ^:private m-new
  {:game :poker-limit-holdem :mode :carry :seed 2
   :seats [{:genome :poker-aggro} {:genome :poker-tight}] :winner 0
   :totals {0 40 1 -40}
   :hands [{:hand 0 :result {:ending :showdown :winners [0] :pot 80} :decisions []}
           {:hand 1 :result {:ending :fold :winners [0] :pot 20} :decisions []}]})

(deftest list-games-newest-first-and-summarized
  (let [root (str (fs/create-temp-dir))]
    (try
      ;; ids embed the epoch-ms suffix the arena writes: game-seed-epochms
      (write-game! root "poker-limit-holdem-1-1000000000000" m-old)
      (write-game! root "poker-limit-holdem-2-2000000000000" m-new)
      (let [gs (dash/list-games root)]
        (is (= 2 (count gs)))
        (testing "newest ts first"
          (is (= "poker-limit-holdem-2-2000000000000" (:id (first gs))))
          (is (= 2000000000000 (:ts (first gs)))))
        (testing "summary projects seats→genomes + hand count, not bodies"
          (let [g (first gs)]
            (is (= [:poker-aggro :poker-tight] (:seats g)))
            (is (= 2 (:hands g)) "hands is a COUNT, not the vector")
            (is (= {0 40 1 -40} (:totals g)))
            (is (= 0 (:winner g)))
            (is (not (contains? g :decisions)) "index never carries decision bodies"))))
      (finally (fs/delete-tree root)))))

(deftest list-games-tolerates-missing-dir-and-corrupt-files
  (let [root (str (fs/create-temp-dir))]
    (try
      (testing "no games/ dir ⇒ empty, never throws"
        (is (= [] (dash/list-games root))))
      (fs/create-dirs (str root "/games"))
      (spit (str root "/games/garbage.edn") "{:this is not )( valid edn")
      (write-game! root "poker-limit-holdem-9-9000000000000" m-old)
      (testing "a corrupt side-store file is skipped, the rest still list"
        (let [gs (dash/list-games root)]
          (is (= 1 (count gs)))
          (is (= "poker-limit-holdem-9-9000000000000" (:id (first gs))))))
      (finally (fs/delete-tree root)))))

(deftest load-game-hit-and-miss
  (let [root (str (fs/create-temp-dir))]
    (try
      (write-game! root "poker-limit-holdem-2-2000000000000" m-new)
      (testing "hit ⇒ FULL transcript (decisions + result present)"
        (let [g (dash/load-game root "poker-limit-holdem-2-2000000000000")]
          (is (= :carry (:mode g)))
          (is (= 2 (count (:hands g))))
          (is (= {:ending :showdown :winners [0] :pot 80}
                 (:result (first (:hands g)))))))
      (testing "miss ⇒ nil (the handler turns this into a 404)"
        (is (nil? (dash/load-game root "no-such-id"))))
      (finally (fs/delete-tree root)))))
