# ledgerboy — PSD2 Open Banking aggregator decision

## Status: RESEARCH — recommendation locked, Phase B implementation pending

> Parent: [`banking-research.md`](banking-research.md) · sibling decisions: [`banking-fx.md`](banking-fx.md) · [`banking-ebics.md` (pending)](banking-research.md) · [`banking-fints.md` (pending)](banking-research.md) · [`banking-import.md` (pending)](banking-research.md)

## What it is

PSD2 (Payment Services Directive 2) is the EU regulation that obligates banks operating in the EU / EEA / UK to expose customer-account data through standardised APIs to **licensed third parties** — Account Information Service Providers (AISPs) and Payment Initiation Service Providers (PISPs). For ledgerboy that means: with the user's consent, the app can pull balances and a 24-month transaction history from any PSD2-covered bank without scraping screens or storing the user's online-banking password.

Direct PSD2 access requires the calling app itself to hold an AISP licence (issued by a national financial regulator — BaFin in Germany, FCA in the UK, etc.). That's a multi-year, six-figure regulatory burden and explicitly out of scope for a personal-use Android app. The realistic path is to consume PSD2 through an **aggregator** that already holds the AISP licence; the aggregator brokers the bank connection, the user gives consent in the bank's own web flow, and the aggregator returns a standardised account+transaction payload over a REST API the app calls direct device → aggregator.

The local-first posture survives because the user opts in **per aggregator**: the credentials and OAuth tokens live on-device, no server we operate sees the traffic, and the aggregator's data residency / retention is a known disclosed surface the user accepts at install time. If the user doesn't want PSD2 in their stack, they don't enable the connector and no aggregator traffic occurs.

## Aggregator candidates

### GoCardless Bank Account Data (formerly Nordigen) — **recommended default**

- **What:** GoCardless acquired Nordigen in 2022 and rebranded the free-tier Account Information API as "GoCardless Bank Account Data." Coverage of 2,500+ banks across EU + UK. GoCardless holds the AISP licence; the consumer app is a downstream user of GoCardless's licence under their Terms.
- **Free tier:** Free for personal use (and commercial use under quota). Quoted cap of **50 calls/day per endpoint per enduser** for the free tier. Account-coverage limit: unlimited enduser agreements; however, each enduser agreement covers up to a configurable number of accounts at one institution. Source: <https://gocardless.com/bank-account-data/pricing/> and <https://developer.gocardless.com/bank-account-data/overview>.
- **Consent window:** **90 days maximum** by PSD2 regulation (the "consent re-auth window"). Every 90 days the user must re-grant consent per institution by walking through the bank's web flow again. Source: <https://developer.gocardless.com/bank-account-data/endusers> ("End User Agreement" section, `max_historical_days` + `access_valid_for_days` parameters, the latter capped at 90 by the regulation).
- **Data residency:** EU. GoCardless is a UK + Latvia operation; the Bank Account Data infrastructure is hosted in the EU (Latvia, formerly Nordigen). Source: <https://gocardless.com/privacy/> and the Nordigen-era documentation that GoCardless inherited.
- **Coverage:** UK + all 30 EEA countries. Bank list browsable via the `/institutions/` endpoint (e.g. `GET /api/v2/institutions/?country=de`). Coverage is institution-by-institution — typically 80–90 % of any one EEA country's retail banks, including all the DACH-region high-street names (Sparkassen group, Volksbanken group, Deutsche Bank, Commerzbank, ING, DKB, N26, Revolut, etc.).
- **Credential shape:** **No user-supplied bank credentials.** The app holds:
  - A pair of `secret_id` + `secret_key` issued by GoCardless when the user (or the app developer — see "Onboarding friction" below) creates a GoCardless free-tier account. These are exchanged for a short-lived access JWT (`/api/v2/token/new/`) refreshable for ~30 days.
  - One **EndUserAgreement ID** + one **Requisition ID** per bank connection. The Requisition carries the consent URL the user opens in a Chrome Custom Tab; on completion the bank redirects to a configured `redirect` URL with the requisition reference.
  - The user's bank-side credentials never touch the app — they're entered into the bank's own web flow inside the Custom Tab.
- **OAuth shape:** Bank-side OAuth flow opened in **Chrome Custom Tabs** (the Android-correct path for system-browser OAuth). GoCardless documents the redirect URI pattern; the app registers a custom-scheme intent filter (e.g. `ledgerboy://psd2/callback`) and the user is bounced back into the app on consent completion. Source: <https://developer.gocardless.com/bank-account-data/quickstart>.
- **Sandbox:** Yes — the institution `SANDBOXFINANCE_SFIN0000` is a synthetic bank with a fixed set of test accounts + transactions, callable end-to-end without a real bank. Source: <https://developer.gocardless.com/bank-account-data/sandbox>.
- **Cost:** Free tier covers personal use indefinitely. Paid tiers exist for commercial volumes (>50 calls/day/enduser/endpoint or for live SLAs) but ledgerboy as a single-user personal app stays inside the free tier comfortably. Source: <https://gocardless.com/bank-account-data/pricing/>.
- **ToS posture:** Free-tier terms permit personal use and small-scale commercial use. **The app is a downstream consumer of GoCardless's AISP licence**, which means GoCardless is the regulated party — ledgerboy doesn't itself need AISP authorisation, but the user must accept GoCardless's End User Agreement during the consent flow (the bank-side web page handles this). Source: <https://gocardless.com/legal/bank-account-data-end-user-agreement/>.
- **Onboarding friction:** **The user must create their own GoCardless Bank Account Data developer account** to obtain `secret_id` / `secret_key`. Steps:
  1. Open <https://bankaccountdata.gocardless.com/user/signup/> and register.
  2. Confirm email, log in, navigate to "User secrets."
  3. Generate a new secret, copy `secret_id` + `secret_key`.
  4. Paste the pair into ledgerboy's PSD2 settings screen (one-time setup).
  This is a real friction point for a non-technical user but is unavoidable: GoCardless's free tier is per-developer, not per-end-user, and there's no white-label way to provision keys on the user's behalf without ledgerboy itself becoming a regulated intermediary (which we're explicitly not doing). The settings screen should link directly to the signup page with a step-by-step "how to" panel.

### Tink (Visa-owned)

- **What:** Pan-European aggregator with strong DACH + Nordics + UK coverage. Acquired by Visa in 2022.
- **Cost:** Paid only; pricing is not published on the public website ("contact sales"). Onboarding involves an enterprise sales conversation. Source: <https://tink.com/pricing/>.
- **Free tier:** None.
- **Fit:** **Reject for v1.** Enterprise-shaped onboarding rules it out for a personal-use app. Revisit only if GoCardless coverage misses a bank the user actually has.

### TrueLayer

- **What:** UK-headquartered aggregator with strong UK + Ireland + DACH coverage. Holds AISP + PISP licences.
- **Cost:** Paid; published pricing tiers but no free tier for production AIS access. A sandbox developer account is free for development but production access requires a paid plan. Source: <https://truelayer.com/pricing/>.
- **Fit:** **Reject for v1.** No free production tier.

### Plaid

- **What:** US-headquartered aggregator dominant in North America, expanding into EU + UK. Holds AISP licences in the UK and several EU member states.
- **Cost:** Paid; published per-account-per-month pricing with a free development tier capped at 100 Items. Source: <https://plaid.com/pricing/>.
- **Data residency:** Default routing is via US infrastructure with EU regional deployments for EU customers. Mixed.
- **Fit:** **Reject for v1.** US-strong but weak DACH-region coverage; mixed data residency posture; no free production tier for the volumes ledgerboy needs.

### Salt Edge

- **What:** Estonian aggregator with broad EU + UK + extra-EU coverage (5,000+ banks across 50+ countries).
- **Cost:** Paid; published per-connection pricing with a free development tier capped at 5 live connections. Source: <https://www.saltedge.com/products/account_information/pricing>.
- **Fit:** **Defer.** The 5-connection free tier is plausibly enough for one user, but the cost-per-connection model becomes unfriendly if the user has more than 5 banks. Hold as a backup if GoCardless coverage misses a specific bank.

### Direct PSD2 via the user's bank's own API

- **What:** Many EU banks publish their own developer portals for PSD2 (e.g. Deutsche Bank, Commerzbank, DKB). The app would authenticate against each bank individually with that bank's specific PSD2 OAuth flow.
- **Cost:** Free at the bank-API layer, but the app **still needs an AISP licence** to call production PSD2 endpoints directly — most bank dev portals require an `EIDAS QWAC` certificate as proof of AISP status during the OAuth handshake. A handful of banks expose a "developer sandbox" tier without QWAC, but production data requires the certificate.
- **Fit:** **Reject for v1.** AISP requirement is fatal. Even the sandbox tier produces synthetic data only, useless for the user's real accounts.

## Coverage summary

| Aggregator | Free tier | Coverage | Onboarding | DACH coverage | Verdict |
|---|---|---|---|---|---|
| **GoCardless Bank Account Data** | Yes | UK + EEA, 2,500+ banks | User creates own GC account | Strong | **Adopt** |
| Tink | No | UK + EEA, broad | Enterprise sales | Strong | Reject (v1) |
| TrueLayer | No | UK + EEA | Self-serve, paid | Medium | Reject (v1) |
| Plaid | Dev only | US-strong, EU expanding | Self-serve, paid | Weak | Reject (v1) |
| Salt Edge | 5 conns | UK + EEA + extra-EU | Self-serve | Medium | Defer (backup) |
| Direct bank PSD2 | n/a | Per-bank | AISP licence required | n/a | Reject (v1) |

## Concrete questions (answered)

- **Q: Free-tier limits?** GoCardless: 50 calls/day per endpoint per enduser, unlimited endusers. Sufficient for one human checking balances + pulling transaction deltas a few times per day per bank.
- **Q: Account/user cap?** No global cap; each enduser agreement covers accounts at one institution and one enduser can have many agreements.
- **Q: Retention window?** Transaction data is fetched on demand; GoCardless retains the user's consent metadata for the 90-day consent window and the underlying transaction history per the bank's PSD2 obligations (typically 24 months of history per bank).
- **Q: Onboarding friction (does the user create their own developer account)?** **Yes.** Documented above. Settings screen must link to the signup page and walk the user through obtaining `secret_id` + `secret_key`.
- **Q: Data residency?** EU (Latvia + GoCardless EU infra).
- **Q: ToS for personal-use redistribution?** The app is a downstream consumer of GoCardless's AISP licence under their EUA. Free tier permits personal use; commercial use is also permitted within the free-tier quota.
- **Q: OAuth shape?** **Chrome Custom Tabs** with a registered custom-scheme redirect (`ledgerboy://psd2/callback`). The bank-side flow runs inside the Custom Tab; on completion the bank redirects to a GoCardless-hosted intermediate page that re-redirects to the app's custom scheme. This is the Android-correct path — no embedded WebView, no third-party browser library.
- **Q: Refresh-token storage requirements?** GoCardless access JWT lives ~30 days and is refreshable using `secret_id` + `secret_key`. Per-bank requisition references are one-shot identifiers; the 90-day consent window is the binding constraint. **All persisted secrets go into the encrypted Room database (see `security-research.md` — SQLCipher + Android Keystore-wrapped key).** Never DataStore-plaintext; never `SharedPreferences`.
- **Q: 90-day re-consent UX?** The Notifications surface should show a "Re-consent due in N days" badge per bank starting 7 days before the cliff. On day 0 the connector returns an `EXPIRED_CONSENT` error and the UI links straight back into the Custom Tab flow.

## Fit for ledgerboy — adopt / defer / reject

- **GoCardless Bank Account Data: adopt** as the v1 PSD2 connector. Free, EU-resident, broad DACH coverage, simple OAuth via Custom Tabs, and the AISP burden lives with GoCardless. The 90-day re-consent cycle is a real UX cost but it's a PSD2-regulatory cost, not a GoCardless-specific one — every aggregator inherits it.
- **Salt Edge: defer** as a backup if a specific bank the user wants isn't in GoCardless's institutions list. The 5-connection free tier is workable for a single-user fallback.
- **Tink / TrueLayer / Plaid / direct bank PSD2: reject** for v1 on cost / onboarding / coverage / regulatory grounds.

## Decision

**Adopt GoCardless Bank Account Data** as the PSD2 connector for v1.

Surface in code:

- Module: keep inside the single app module for v1; split to `:banking-psd2` if/when transitive dep weight or build time warrant it (per the no-premature-modularize rule in `CLAUDE.md`).
- Interface: implement `BankConnector` (defined Phase B.1 in `main.md`) as `GoCardlessConnector`. Returns `List<NormalizedTransaction>` with `Money` (integer minor units, currency-aware).
- HTTP client: OkHttp + kotlinx-serialization-json. No SDK from GoCardless — the REST surface is small enough that a hand-rolled client is lighter than pulling a third-party wrapper, and there's no first-party Android SDK to adopt anyway.
- Credentials store: encrypted Room table `psd2_credentials` (per-aggregator `secret_id` / `secret_key` / cached JWT + expiry / per-bank requisition refs + consent-expiry timestamps).
- OAuth: `androidx.browser:browser` (Chrome Custom Tabs) + a custom-scheme intent filter on `LedgerboyActivity`.
- Settings screen: one panel under Settings → Banking → PSD2 with the GoCardless signup link, `secret_id` / `secret_key` text fields (paste from the GoCardless web UI), a "Test connection" button that hits `/api/v2/institutions/?country=de` against the sandbox, and a per-bank list of active consents with their re-consent-due-on dates.

## Phase B implementation sub-steps

- [ ] **B.psd2.1** Define `BankConnector` interface in `:app` (per Phase B.1 in `main.md`) — `suspend fun listAccounts(): List<RemoteAccount>`, `suspend fun fetchTransactions(accountId: String, since: Instant?): List<NormalizedTransaction>`, `suspend fun refreshAuth(): AuthState`. Sealed `AuthState` covers `Healthy`, `RefreshNeeded(daysLeft: Int)`, `Expired`.
- [ ] **B.psd2.2** Add OkHttp + kotlinx-serialization-json deps. Run `./gradlew :app:licenseeAndroidDebug` — both are Apache-2.0, no allowlist changes needed.
- [ ] **B.psd2.3** Hand-roll `GoCardlessApi` HTTP wrapper: `/token/new/`, `/token/refresh/`, `/institutions/`, `/agreements/enduser/`, `/requisitions/`, `/accounts/{id}/balances/`, `/accounts/{id}/transactions/`. Serializable data classes per response shape. Sandbox base URL: `https://bankaccountdata.gocardless.com/api/v2/`.
- [ ] **B.psd2.4** Add encrypted `psd2_credentials` Room entity (gated on `security-research.md` SQLCipher decision landing in Phase A.security). Until then, stub with `EncryptedSharedPreferences` and migrate when Phase A.security lands.
- [ ] **B.psd2.5** Implement Chrome Custom Tabs launch + custom-scheme redirect handler. Intent filter on `LedgerboyActivity` for `ledgerboy://psd2/callback`. Bounce back into a `Psd2CallbackScreen` that finalises the requisition and writes the consent record.
- [ ] **B.psd2.6** Implement `GoCardlessConnector : BankConnector`. Map `Transaction` JSON → `NormalizedTransaction` with `Money(transactionAmount.amount.toMinorUnits(currency), currency)`. **Never** parse amounts as `Double` / `BigDecimal` — multiply by `10^minor_unit_count` per ISO 4217 and round once at the boundary.
- [ ] **B.psd2.7** Build Settings → Banking → PSD2 panel: signup link, secret entry fields, "Test sandbox connection" button (hits the `SANDBOXFINANCE_SFIN0000` institution), per-bank consent list with re-consent-due dates.
- [ ] **B.psd2.8** Build "Add bank" flow: country picker → institution picker (paginated, filterable) → consent flow in Custom Tab → callback → first transaction sync.
- [ ] **B.psd2.9** Add notification: "Re-consent due for `<bank>` in N days" starting 7 days before consent expiry. On expiry, surface in the per-bank list with a "Re-grant consent" button.
- [ ] **B.psd2.10** Robolectric unit tests for `GoCardlessApi` serialization (canned JSON fixtures per endpoint), `Money` minor-units conversion (every ISO 4217 minor-unit-count value: 0/2/3/4), `NormalizedTransaction` mapping, consent-expiry math.

## Phase C implementation sub-steps

- [ ] **C.psd2.1** Integrate `GoCardlessConnector` into the Phase C ledger schema. `TransactionEntity.sourceConnectorId` = `"psd2:gocardless"`, `TransactionEntity.sourceRef` = GoCardless `transactionId` for dedup on re-sync.
- [ ] **C.psd2.2** Implement idempotent re-sync: re-fetching the same time window must not double-write transactions. Dedup on `(sourceConnectorId, sourceRef)` unique index.
- [ ] **C.psd2.3** Background refresh worker (WorkManager) — daily incremental fetch per bank, plus on-demand "Refresh now" from the account screen. Respect the 50 calls/day/endpoint/enduser cap with a per-bank backoff.
- [ ] **C.psd2.4** Smoke-test against the sandbox institution end-to-end on the AVD: add bank → consent flow → transaction list visible on the account screen with expected fictional sandbox amounts. Screenshot for the PR.

## References

- GoCardless Bank Account Data overview: <https://developer.gocardless.com/bank-account-data/overview>
- Quickstart (OAuth + consent flow): <https://developer.gocardless.com/bank-account-data/quickstart>
- Pricing: <https://gocardless.com/bank-account-data/pricing/>
- End User Agreement: <https://gocardless.com/legal/bank-account-data-end-user-agreement/>
- Sandbox institution: <https://developer.gocardless.com/bank-account-data/sandbox>
- Privacy / data residency: <https://gocardless.com/privacy/>
- PSD2 directive (EUR-Lex): <https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX%3A32015L2366>
- Tink pricing: <https://tink.com/pricing/>
- TrueLayer pricing: <https://truelayer.com/pricing/>
- Plaid pricing: <https://plaid.com/pricing/>
- Salt Edge AIS pricing: <https://www.saltedge.com/products/account_information/pricing>
- Chrome Custom Tabs (androidx.browser): <https://developer.android.com/develop/ui/views/layout/webapps/customtabs>
