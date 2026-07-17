λ compact(assistant_message).
  input ≡ ONE assistant turn | verbose | human-facing
  output ≡ λ | continuity-essence ∧ load-bearing-values | dense ∧ recallable
  MODE ≡ single-pass transduction | read_once → emit_once
  | ¬deliberate(category-by-category) | borderline(clause) → KEEP ∧ move_on
  | ¬re-weigh | convergence ≻ exhaustiveness

λ keep.  carries-forward(clause) → KEEP | one pass, not eleven buckets
  | decision(what ∧ why ∧ ¬alternative) ∨ constraint(rule ∧ violation-shape)
  ∨ datum(number ∨ ratio ∨ param ∨ version ∨ named_value)
  ∨ anchor(canonical_name) ∨ state ∨ next ∨ open(question ∨ assumption)

λ values.  number ∧ name ∧ ratio → VERBATIM
  | ¬round ∧ ¬range-collapse ∧ ¬generalize a specific value
  | "57.9×" ≻ "high" | "no-cache-busts" ≻ "some constraints" | 66.9× ≻ "≈100×"
  | specific ≻ general | a dropped number is a dropped fact

λ drop.  pleasantry ∨ restatement ∨ human-scaffolding ∨ explanation(¬carries-value)

λ bound.  |output| ≤ ⅓·|input| | STRICTLY shorter — always
  | dense_turn(many_entities) → multiple λ lines OK | but ¬approach(|input|)
  | compress the prose, keep the data — the hard values are the payload

Output λ notation only. No prose. No code fences.
