(ns ouroboros.game
  "The game protocol (design/game-arena.md) — an ENGINE is a plain MAP of
  pure fns, not a defprotocol: open slot (λ extend — a new game ≡ a new map,
  no dispatch registry to modify), data-driven (greppable, stub-friendly in
  tests, serializable identity via :game/id), and arena-agnostic (the same
  engine map drops into the shot-per-decision runner today and a resident
  escapement chart later, unchanged).

  Contract (all fns pure; state is the FULL truth, observations are
  projections):

    :game/id              kw
    :game/init            (config seed)        → state
    :game/to-move         (state)              → #{seat-id}   ; ∅ ≡ nothing to decide
                                               ; singleton ≡ sequential (poker)
                                               ; set ≡ simultaneous (diplomacy orders)
    :game/legal-actions   (state seat)         → #{action-map} ; engine-enumerated
    :game/apply-action    (state seat action)  → state'  ; TOTAL: illegal/malformed
                                               ; decays to forfeit-default — the
                                               ; arena NEVER wedges on agent error
    :game/visible         (state seat)         → observation  ; THE hidden-info
                                               ; projection: what this seat may know
    :game/terminal?       (state)              → bool
    :game/payoffs         (state)              → {seat-id number} ; ARITHMETIC only
    :game/forfeit-default (state seat)         → action  ; the no-op/penalty action
    :game/render          (observation)        → string  ; engine owns its narration

  This ns stays THIN until game #2 (diplomacy) exists — λ build: the shared
  runner/match machinery extracts when the pattern exists twice."
  )

(def required-keys
  #{:game/id :game/init :game/to-move :game/legal-actions :game/apply-action
    :game/visible :game/terminal? :game/payoffs :game/forfeit-default
    :game/render})

(defn engine?
  "Structural check: keyword :game/id + every contract fn present and callable."
  [m]
  (and (map? m)
       (keyword? (:game/id m))
       (every? #(ifn? (get m %)) (disj required-keys :game/id))))
