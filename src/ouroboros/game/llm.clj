(ns ouroboros.game.llm
  "The LLM decide seam — game-agnostic (design/game-arena.md, build step 4).

  One DECISION ≡ one hermetic verdict-topology shot (the verdict.clj pattern,
  λ converge): a single llm-conversation whose system prompt is the PLAYER
  genome, whose message is the engine's `render`ed observation ⊕ the FILLED
  action exemplar (λ mirror), and whose forced :verdict-schema is the
  engine's PER-DECISION :game/action-schema (legality-narrowed enum — an
  illegal action cannot even validate; this is why :player has no row in
  agents.core/verdict-schemas).

  Turn-based games don't need residency: decide ≡ pure_fn(observation) at
  the LLM boundary, so the arena's decide-fn seam is just this shot. Failure
  geometry: worker death / validation failure / nil verdict → nil action →
  the engine's forfeit-default (the arena never wedges; the forfeit is the
  agent's score, not our crash).

  `run-fn` is injectable (RunTestsTool precedent) — tests never surprise-LLM;
  `decide-fn` builds an arena-shaped (fn [seat-spec obs] → action) closing
  over a genome cache (one roster read per match, not per decision)."
  (:refer-clojure :exclude [run!])
  (:require
    [com.fulcrologic.statecharts.chart :as chart]
    [com.fulcrologic.statecharts.elements :refer [final script state transition]]
    [escapement.chart.helpers :as h]
    [escapement.lib :as lib]
    [ouroboros.agents :as agents]
    [ouroboros.models :as models]
    [ouroboros.session :as session]
    [ouroboros.tools :as tools]))

(defn decision-message
  "The user-message for one decision: engine narration ⊕ exemplar-primed
  format gate. The exemplar SHOWS the shape (λ mirror); the schema enforces
  it; the engine already narrated the legal menu."
  [rendered exemplar]
  (str rendered
       "\n\nDecide now. Submit your verdict with exactly this shape"
       " (choose YOUR action from the legal menu above):\n"
       (pr-str exemplar)))

(def default-budget-ms
  "Per-decision wall-clock budget. GENEROUS by design (🎯 human, 2026-07-17):
  thinking stays ON and models get room to finish — reasoning QUALITY is the
  benchmark axis that separates model families; an arena that clips thought
  measures patience, not poker. (The first live match's lone gemma4 forfeit
  was a 120s budget artifact, not a model failure.) The budget remains the
  runaway guard, set where no honest deliberation should ever hit it."
  600000)

(defn- decision-chart
  [genome model message schema verdict-atom budget-ms]
  (chart/statechart
    {:initial :deciding}
    (state {:id :deciding}
      (h/llm-conversation
        {:id             (:slug genome)
         :system         (:prompt genome)
         :model          model
         :stream?        false
         :real-tools     (:tools genome)   ; players carry the [] floor-less grant
         :verdict-schema schema
         :max-turns      3
         :budget-ms      budget-ms
         :message        message})
      (transition {:event :llm.idle :target :done}
        (script {:expr (fn [_env data]
                         (reset! verdict-atom (get-in data [:_event :data :verdict]))
                         nil)}))
      (transition {:event :error.llm :target :failed}))
    (final {:id :done})
    (final {:id :failed})))

(defn run!
  "One live decision shot. Returns the validated action map or nil (worker
  death / validation failure — the caller's forfeit path)."
  [genome {:keys [model message schema root budget-ms]
           :or {root "." budget-ms default-budget-ms}}]
  (let [model       (or model (:model genome))
        verdict     (atom nil)
        session-id  (str (:slug genome) "-" (System/currentTimeMillis))
        session-dir (session/session-dir root session-id)]
    (lib/run
      (merge
        {:chart           (decision-chart genome model message schema verdict budget-ms)
         :session-id      session-id
         :session-dir     session-dir
         :transcript-path (str session-dir "/transcript.jsonl")
         :checkpoint-dir  (str session-dir "/checkpoints")
         :tool-registry   (tools/new-registry root)}
        (models/llm-config model)))
    @verdict))

(defn decide-fn
  "Arena decide-fn over PLAYER genomes for `engine`: seat-spec
  {:genome <slug-kw> :model <alias override?>} → (fn [spec obs] → action|nil).
  The engine's own :game/render narrates; the arena's obs carries the
  action contract (:action-schema / :action-exemplar).

  opts: :root · :run-fn (fn [genome {:model :message :schema :root :budget-ms}]
  → action) — injectable for tests; defaults to the live `run!` ·
  :budget-ms (default `default-budget-ms` — generous, thinking is the
  benchmark axis) · :on-decision (fn [spec obs action]) side-tap for CLI
  narration."
  [engine {:keys [root run-fn on-decision budget-ms]
           :or {root "." run-fn run! budget-ms default-budget-ms}}]
  (let [render     (:game/render engine)
        cache      (atom {})
        genome-for (fn [id]
                     (or (get @cache id)
                         (let [g (agents/genome id root)]
                           (when-not (= :player (:kind g))
                             (throw (ex-info "seat genome is not kind:player"
                                             {:genome id :kind (:kind g)})))
                           (swap! cache assoc id g)
                           g)))]
    (fn [spec obs]
      (let [genome (genome-for (:genome spec))
            schema (:action-schema obs)
            action (when schema
                     (run-fn genome {:model     (:model spec)
                                     :message   (decision-message
                                                  (render obs)
                                                  (:action-exemplar obs))
                                     :schema    schema
                                     :root      root
                                     :budget-ms budget-ms}))]
        (when on-decision (on-decision spec obs action))
        action))))
