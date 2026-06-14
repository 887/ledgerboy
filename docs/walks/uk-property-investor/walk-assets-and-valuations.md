# Walk — Add assets and track valuations (UK)

> Persona: Priya (see `personality.md`). Goal: add a flat and a car as
> assets, record valuations over time, and see current value roll up
> from the latest valuation while keeping the history.
>
> Features exercised: Phase I asset registry (AssetEntity + valuation
> history), Assets screen + Asset detail, manual revaluation.
>
> Format: numbered steps, `taps X → expects Y`. **[QA RISK]** flags.
> Fictional values only.

## Preconditions

- Fresh install (or any state). No connectors needed — this walk is
  entirely manual / local.

## Steps

1. Launch → rail → tap **Assets** → expects the empty "No assets yet"
   state with guidance.
2. In the **New asset** card: type name "Flat — 12 Westvale Road", tap
   the **Real estate** class chip, type acquisition cost "180000" →
   expects the Add button to enable. Tap **Add asset**.
   - **[QA RISK]** Cost parses to minor units: £180,000 → 18,000,000
     minor, displayed `£180,000.00`.
3. Add a second asset: name "Car", class **Vehicle**, cost "14000" →
   tap **Add asset** → expects two assets now grouped by class
   (Real estate, Vehicle).
4. Expects each asset row to show a **current value** equal to its
   acquisition cost (no valuation recorded yet — fallback).
5. Tap the **Flat** row → expects the Asset detail screen with the
   valuation history (empty → "No valuations yet" hint) and a **Record
   valuation** form.
6. Record a valuation "184000" → expects a new history row with today's
   date + source **manual** + `£184,000.00`.
7. Record a later valuation "190000" → expects a **second** history row
   (newest first), the first one **still present** (history preserved,
   not overwritten).
   - **[QA RISK]** Recording a valuation must **append**, never replace
     the previous event.
8. Back to **Assets** → expects the Flat's current value now shows
   **£190,000.00** (the latest valuation), not the acquisition cost.
   - **[QA RISK]** Current value must follow the **latest by date**, not
     insertion order.
9. (Future, Phase J) When an asset-price connector is enabled, automated
   valuations would appear in this same history with `source =
   "<connector>"` — note that the screen already renders the `source`
   column for that.

## Expected end state

Two assets (one real estate, one vehicle) with acquisition costs and a
preserved valuation trail; the Assets list reflects the most recent
valuation per asset.
