(ns ouroboros.game.dashboard
  "bb dashboard — the arena REPLAY EXPLORER, converged onto escapement's OWN web
  layer (design/game-arena.md, the CONTENT projection made human-readable).

    bb dashboard            — serve on :5080, browse ./games (+ ./sessions inspector)
    bb dashboard <port>     — pick the port
    bb dashboard <port> <root> — games/sessions root (default \".\")

  λ converge — ONE server, not two. escapement.ui.server/make-handler is PUBLIC:
  it returns a Ring handler over the inspector ctx (POST /api EQL · GET /ws push ·
  SPA + public/ statics). We DELEGATE to it for everything that isn't an arena
  route, so this single server also carries:
    · GET /ws        — the ws-push fan-out hub, ALREADY wired for the LIVE layer
    · POST /api      — escapement's session inspector, for free
    · public/ statics
  and we own only the arena grain escapement can't model:
    · GET /          — the arena replay page
    · GET /api/games         — newest-first game index
    · GET /api/games/{id}    — one full transcript

  feed_forward — the LIVE table rides the SAME server + SAME page: pass a `:ws-push`
  hub here and a `lib/run` :transcript-tap → ws-push/publish! at the match seam.
  Replay and live are ONE surface; nothing forks. escapement's ui.server + ws-push
  are the substrate, exactly as the self-hosting future will use them.

  VSM framing: the arena already made the S3* audit MACHINE-readable (chips ≡ the
  reading); this dashboard makes that same audit HUMAN-readable — a window on the
  existing content projection, read-only by construction."
  (:require
    [cheshire.core :as json]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [escapement.storage.disk-read :as disk-read]
    [escapement.ui.server :as ui]
    [escapement.ui.ws-push :as ws-push]
    [org.httpkit.server :as http]))

;; ── data layer (pure, HTTP-free — the testable core) ───────────────────────

(defn games-dir
  "The arena's transcript directory under `root` (arena persists to
  (io/file root \"games\" ...); root default \".\")."
  [root]
  (io/file (or root ".") "games"))

(defn- ts-of
  "Epoch-millis suffix embedded in an arena id like
  \"poker-limit-holdem-663634-1784354335235\" (game-seed-epochms), or nil."
  [id]
  (some-> id (str/split #"-") last parse-long))

(defn- summarize
  "Project a full transcript to a list-view summary (never load bodies into the
  index — λ disclose)."
  [{:keys [id game mode seed seats totals winner hands] :as _match}]
  {:id     id
   :game   game
   :mode   mode
   :seed   seed
   :winner winner
   :seats  (mapv :genome seats)
   :hands  (count hands)
   :totals totals
   :ts     (ts-of id)})

(defn read-game
  "Read+parse one transcript File, or nil on any read/parse failure (a corrupt
  side-store file must not sink the whole listing)."
  [^java.io.File f]
  (try
    (edn/read-string (slurp f))
    (catch Exception _ nil)))

(defn list-games
  "Newest-first summaries of every games/*.edn under `root`. Missing dir ⇒ []."
  [root]
  (let [dir (games-dir root)]
    (if (.isDirectory dir)
      (->> (.listFiles dir)
           (filter #(str/ends-with? (.getName ^java.io.File %) ".edn"))
           (keep read-game)
           (map summarize)
           (sort-by (fn [g] (or (:ts g) 0)) >)
           vec)
      [])))

(defn load-game
  "Full transcript for `id` under `root`, or nil if absent."
  [root id]
  (let [f (io/file (games-dir root) (str id ".edn"))]
    (when (.isFile f) (read-game f))))

;; ── http layer (composed onto escapement's Ring handler) ───────────────────

(defn- json-response [status data]
  {:status  status
   :headers {"Content-Type"                "application/json; charset=utf-8"
             "Access-Control-Allow-Origin" "*"}
   :body    (json/generate-string data)})

(defn- page []
  (if-let [res (io/resource "ouroboros/game/dashboard.html")]
    {:status 200 :headers {"Content-Type" "text/html; charset=utf-8"} :body (slurp res)}
    {:status 500 :body "dashboard.html resource missing"}))

(defn- arena-route
  "Handle an arena-owned route, or nil to let the request fall through to
  escapement's handler (λ converge — one server, delegated tail)."
  [root {:keys [request-method uri]}]
  (when (= :get request-method)
    (cond
      (= uri "/")
      (page)

      (= uri "/api/games")
      (json-response 200 (list-games root))

      (str/starts-with? uri "/api/games/")
      (let [id (subs uri (count "/api/games/"))]
        (if-let [g (load-game root id)]
          (json-response 200 g)
          (json-response 404 {:error "no such game" :id id}))))))

(defn make-handler
  "Ring handler: arena routes first, then DELEGATE to escapement's make-handler
  (POST /api inspector · GET /ws push · public/ statics). `ctx` is the escapement
  inspector ctx (see escapement.ui.server/start! keys)."
  [root ctx]
  (let [escapement (ui/make-handler ctx)]
    (fn [req]
      (or (arena-route root req)
          (escapement req)))))

(defn start!
  "Start the converged dashboard server. opts:
     :port      — TCP port (default 5080)
     :root      — arena games/ + escapement sessions root (default \".\")
     :ws-push   — optional fan-out hub (escapement.ui.ws-push/new-hub) powering
                  GET /ws; created here if absent so the LIVE seam is live from
                  day one (feed_forward — the match tap just calls publish! on it)
     :chart :active-session-id :live :controller — passed through to the inspector
  Returns {:stop :port :ws-push} (mirrors escapement.ui.server/start!)."
  [{:keys [port root ws-push chart active-session-id live controller]
    :or   {port 5080 root "."}}]
  (let [hub (or ws-push (ws-push/new-hub {:chart chart}))
        ctx {:escapement/store              (disk-read/new-store root)
             :escapement/active-session-id  active-session-id
             :escapement/chart              chart
             :escapement/controller         controller
             :escapement/live               live
             :escapement/ws-push            hub}
        stop (http/run-server (make-handler root ctx) {:port port})]
    {:stop stop :port port :ws-push hub}))

(defn -main [& [port root]]
  (let [port (or (some-> port parse-long) 5080)
        root (or root ".")
        n    (count (list-games root))]
    (start! {:port port :root root})
    (println (str "▶ arena replay explorer — http://localhost:" port
                  "  (" n " game" (when (not= 1 n) "s") " under " (games-dir root) ")"))
    (println (str "  live seam: GET /ws · inspector: POST /api · Ctrl-C to stop."))
    @(promise)))
