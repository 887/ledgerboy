# Persona — Mara, a Sparkasse saver (DE)

> A `docs/walks/` scenario persona. Drives a realistic end-to-end QA
> script against ledgerboy. Fictional person, fictional bank, fictional
> numbers — per the privacy posture, nothing here is real.

## Snapshot

- **Handle:** Mara (fictional)
- **Country / locale:** Germany, `de-DE`. Currency EUR, `,` decimal.
- **Tech comfort:** Everyday. Banks online, downloads a CSV export from
  her Sparkasse web banking each month, has never used a finance app
  that didn't want her login.
- **Device:** A mid-range Android phone (Android 16 / API 36).
- **Money shape (fictional):** one checking account at a savings bank,
  salary in, rent + groceries + fuel out. Round demo numbers only.

## What Mara wants

- To **import her monthly CSV** and see where the money went, without
  handing any bank credentials to an app. The disabled-by-default,
  read-a-file-locally model is exactly why she's trying ledgerboy.
- To **categorize** spending and have it **stick** — next month's
  "ARAL", "REWE", "Westvale Immobilien" rows should categorize
  themselves.
- A **monthly budget** on groceries so she notices when she overspends.

## What would make her bounce

- If "Import" asked for a bank login. (It must not — SAF file pick only.)
- If the German `;`-delimited, `,`-decimal CSV imported as garbage or
  zero rows.
- If she had to re-categorize the same merchant every month.
