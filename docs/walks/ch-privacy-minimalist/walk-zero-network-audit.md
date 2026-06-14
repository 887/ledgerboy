# Walk — Prove zero network, then use it offline (CH)

> Persona: Luca (see `personality.md`). Goal: confirm ledgerboy phones
> home to nobody on first launch, audit the Network-usage screen, and use
> the app fully offline via a local CSV.
>
> Features exercised: Phase X zero-calls posture + PluginNetworkGuard +
> Network-usage audit; the disabled-by-default manifest; Phase B offline
> CSV import; Phase E/F offline ledger.
>
> Format: numbered steps, `taps X → expects Y`. **[QA RISK]** flags.
> Fictional data only.

## Preconditions

- Fresh install, never launched. Have a way to observe traffic
  (`adb logcat`, a proxy, or airplane mode as a blunt instrument).
- A fictional CHF CSV on the device for the offline step.

## Steps

1. **Before launching**, start watching the wire (proxy / `tcpdump` /
   airplane mode). Launch ledgerboy for the first time → expects the app
   to open to Overview with **zero outbound connections**.
   - **[QA RISK]** Any request on first launch (telemetry, update check,
     remote manifest, crash reporter, analytics) is a hard fail of the
     network posture.
2. Rail → **Settings → Plugins** → expects the privacy banner and **every
   plugin Disabled** — no "recommended", no pre-enabled connector.
   - **[QA RISK]** If any plugin is enabled/active by default, hard fail.
3. Tap **Network usage** (top-right or the row) → expects "No connector
   has made a network call. ledgerboy has sent nothing."
   - **[QA RISK]** This text must be truthful — it should match what the
     wire shows (nothing).
4. With the network still **off**, enable **CSV import** → Configure →
   pick the local CHF CSV → **Test connection** → expects the import to
   succeed **offline** (import is local; the network guard is a no-op for
   it) and rows to appear.
   - **[QA RISK]** CSV import must never require the network; it must work
     in airplane mode.
5. Rail → **Accounts / Transactions** → expects the imported CHF data
   visible and usable entirely offline, formatted `de-CH`.
6. (Negative check) Re-open **Network usage** → expects `csv-import` to
   still show **zero** bytes / fetches (a local file read is not network).
   - **[QA RISK]** If the import registered network bytes, the accounting
     is wrong.
7. (Trust-building) Enable **ECB exchange rates** but **do not** tap Test
   connection → expects it to sit at **Enabled/Configured** and make **no
   call** until he explicitly triggers one (configure-before-fetch).
   - **[QA RISK]** Merely enabling a plugin must not fetch. Only an
     explicit Test / scheduled-and-opted-in fetch may call out.

## Expected end state

Luca has watched ledgerboy launch and run silently, confirmed the
Network-usage audit matches the wire, imported a statement offline, and
verified that enabling a network plugin still doesn't call out until he
says so. He trusts it enough to keep using it.
