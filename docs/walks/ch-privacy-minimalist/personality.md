# Persona — Luca, a privacy-first minimalist (CH)

> A `docs/walks/` scenario persona. Fictional person, fictional data.
> Money is private — nothing here is real.

## Snapshot

- **Handle:** Luca (fictional)
- **Country / locale:** Switzerland, `de-CH`. Currency CHF.
- **Tech comfort:** High and **suspicious by default**. Reads
  permissions, watches network traffic, assumes finance apps exfiltrate
  until proven otherwise.
- **Device:** A privacy-hardened Android phone (Android 16 / API 36),
  often with the network off.
- **Goal shape:** wants a ledger that is **provably silent on the wire**
  out of the box, and is happy to enter everything by hand / from a local
  file. He will not enable a single connector until he's convinced.

## What Luca wants

- To install ledgerboy and **verify it makes zero network calls** before
  trusting it with anything.
- A **user-visible audit** of what (if anything) has called out.
- To use the app fully **offline** via local CSV import only.

## What would make him uninstall on the spot

- Any outbound request on first launch (telemetry, update check, remote
  manifest, crash reporter).
- A connector that's enabled by default, or a "recommended set".
- A Network-usage screen that doesn't match what he sees on the wire.
