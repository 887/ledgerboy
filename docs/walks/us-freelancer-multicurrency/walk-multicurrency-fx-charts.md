# Walk — Two currencies, ECB rates, cashflow charts (US)

> Persona: Theo (see `personality.md`). Goal: import a USD and a EUR
> statement, enable the ECB FX plugin directly, and read per-currency
> balances + cashflow charts.
>
> Features exercised: Phase B CSV import (Generic dialect, two accounts);
> Phase D ECB FX plugin + guarded network call + FX cache; Phase E
> per-currency Overview; Phase K charts.
>
> Format: numbered steps, `taps X → expects Y`. **[QA RISK]** flags.
> Fictional fixture data only.

## Preconditions

- Fresh install. Two fictional RFC-4180 CSVs: `acme-usd-2026-q1.csv`
  (USD) and `acme-eur-2026-q1.csv` (EUR), each `Date,Payee,Amount,Memo`,
  ISO dates, `.` decimal. Device online for the FX step.

## Steps

1. Launch → Overview empty state.
2. Settings → Plugins → enable **CSV import** → Configure. Set account
   "USD Checking", **Generic** format, pick the USD CSV, **Test
   connection** → expects "OK · N rows", plugin **Active**.
3. Configure CSV import again (same plugin): change account to "EUR
   Clients", pick the EUR CSV, **Test connection** → expects the EUR rows
   imported into a second account.
   - **[QA RISK]** The two imports must land in **two distinct accounts**
     (keyed by account name), not merge.
4. Rail → **Accounts** → expects two accounts: "USD Checking" with a
   `$`-formatted balance and "EUR Clients" with a `€`-formatted balance.
   - **[QA RISK]** Each balance must format in its **own** currency
     (`$1,200.00` vs `1.200,00 €`-style per locale), never both in one.
5. Rail → **Overview** → expects a **Balances by currency** card with a
   **separate USD line and EUR line** — NOT a single summed number.
   - **[QA RISK]** If USD and EUR are added together, hard bug (mixed
     currencies must never be summed without conversion).
6. Settings → Plugins → **Exchange rates → ECB exchange rates** → toggle
   **Enabled** → **Configure** → expects an informational note ("No
   configuration needed…"), the source URL pointing at `ecb.europa.eu`,
   and a privacy statement.
7. Tap **Test connection** (device online) → expects a real fetch of the
   ECB daily file and a success snackbar with a row count (~30 rates);
   plugin → **Active**.
   - **[QA RISK]** The request must go directly to `ecb.europa.eu` (no
     proxy). Settings → Plugins → **Network usage** should now list the
     ECB endpoint and non-zero bytes for `ecb-fx` only.
8. Open **Network usage** → expects per-plugin accounting: `ecb-fx` shows
   fetches ≥ 1 + the `ecb.europa.eu` endpoint; `csv-import` shows nothing
   (import is local).
9. Rail → **Charts** → expects a **cashflow bar chart** (income vs
   expense per month) and a spending-by-category donut for the base
   currency (EUR v0).
   - **[QA RISK]** Charts aggregate a single currency in v0 — confirm the
     copy/legend doesn't imply a blended USD+EUR total.

## Expected end state

Two accounts in their native currencies, per-currency Overview totals,
ECB rates cached via a direct, audited fetch, and cashflow charts — with
no third-party FX broker and no fabricated cross-currency sum.
