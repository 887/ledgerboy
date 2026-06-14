# Walk — Import a Sparkasse CSV, categorize, set a budget (DE)

> Persona: Mara (see `personality.md`). Goal: import a German bank CSV,
> see accounts + transactions, create categories + a rule, and set a
> monthly grocery budget — no sign-in, file read locally.
>
> Features exercised: Phase X Plugins screen + Configure flow + zero-calls
> guard; Phase B CSV import (Sparkasse dialect); Phase C/E/F ledger +
> accounts + transactions; Phase G categories + rules; Phase H budgets.
>
> Format: numbered steps, `taps X → expects Y`. **[QA RISK]** = confusion
> / likely-bug point. Use **fictional** CSV fixture data only.

## Preconditions

- Fresh install, never launched. Device may be offline (import is local).
- A fictional Sparkasse-shaped CSV on the device (`;`-delimited, `,`-decimal,
  `dd.MM.yy`), e.g. `acme-sparkasse-2026-01.csv` with rows for salary in,
  rent to "Westvale Immobilien", groceries at "REWE", fuel at "ARAL".

## Steps

1. Launch → expects the vertical rail (Overview / Accounts / Budgets /
   Assets / Charts / Settings) with **Overview** selected, showing the
   empty "Nothing here yet" state.
2. Tap **Settings** → tap **Plugins** → expects the Plugins screen with
   the **privacy banner** ("…makes no network calls until you enable…")
   and five categories; **Import → CSV import** shows **Disabled**.
   - **[QA RISK]** If any plugin shows as enabled/active out of the box,
     that violates the zero-calls posture — bug.
3. Tap the **CSV import** toggle → expects it to flip to **Enabled** and
   a **Configure** affordance to appear.
4. Tap **Configure** → expects the configure screen: a license/privacy
   header ("Nothing leaves your device…"), an **Account name** field, a
   **Format** chip row (Generic / Sparkasse (DE)), and **Pick CSV file**.
5. Type account name "Sparkasse Giro"; tap the **Sparkasse (DE)** format
   chip → expects both to persist.
6. Tap **Pick CSV file** → expects the **SAF document picker** (system
   file chooser), NOT a bank-login screen. Pick the fixture CSV.
   - **[QA RISK]** If a login/OAuth screen appears, hard bug.
7. Tap **Test connection** → expects a success snackbar reporting a row
   count (e.g. "OK · 5 rows") and the plugin to move to **Active**.
   - **[QA RISK]** German `,`-decimal must parse: a `-850,00` row must
     become −€850.00, not −85000 or 0. Verify amounts on the next screen.
8. Back out to the rail, tap **Accounts** → expects an account "Sparkasse
   Giro" with a balance that equals salary minus the outflows, formatted
   `de-DE` (e.g. `1.234,56 €`). Tap it.
9. On the transactions list → expects newest-first rows with payee +
   date + amount; expenses in the error colour, salary in the primary
   colour. Search "REWE" filters to grocery rows.
10. Tap a REWE row → detail sheet. There are no categories yet → expects
    the "No categories yet" hint.
11. Rail → **Settings → Categories & rules**. Add categories
    "Groceries", "Rent", "Fuel" → expects three chips.
12. Add a rule: payee contains **REWE** → **Groceries**; add **ARAL** →
    **Fuel**; **Westvale** → **Rent** → expects three rule rows.
13. Tap **Apply rules to uncategorized** → expects a snackbar
    "Categorized N transactions" and the Overview **uncategorized count**
    to drop.
    - **[QA RISK]** Case-insensitive match: "rewe" rule must catch a
      "REWE" payee.
14. Re-import the **same** CSV (repeat 6–7) → expects **no duplicates**
    (content-hash dedup); transaction count unchanged.
15. Rail → **Budgets** → add a **Groceries** budget of `300,00` →
    expects a progress bar showing spent-of-limit for the month, over the
    limit shown in the error colour if she overspent.
16. Rail → **Charts** → expects a **spending-by-category donut** with
    Groceries / Rent / Fuel slices + a 6-month cashflow bar chart.

## Expected end state

A Sparkasse account with categorized transactions, three rules that will
auto-categorize next month's import, one monthly budget, and charts — all
created from a single local CSV, zero network calls.
