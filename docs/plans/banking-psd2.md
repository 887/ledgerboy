# ledgerboy — PSD2 direct-per-bank plugin research

## Status: RESEARCH — recommendation rewritten 2026-05-24 after the connector-plugin architecture lock. Phase X (plugin runtime) lands before any of these plugins.

> Parent: [`banking-research.md`](banking-research.md) · Architectural authority: [`connector-plugins.md`](connector-plugins.md) · sibling decisions: [`banking-fx.md`](banking-fx.md) · [`banking-fints.md`](banking-fints.md) · [`banking-ebics.md`](banking-ebics.md) · [`banking-import.md`](banking-import.md)

## Pivot summary

The earlier version of this file recommended GoCardless Bank Account Data (ex-Nordigen) as the v1 default. **That recommendation is withdrawn.** Per [`connector-plugins.md`](connector-plugins.md), ledgerboy ships **no aggregator middlemen** — no GoCardless, no Tink, no TrueLayer, no Plaid, no Salt Edge. Every banking source is a **per-bank plugin** the user explicitly enables and configures, talking direct-to-bank.

The PSD2 surface area is therefore implemented as **one plugin per bank**, each owning the bank's specific NextGenPSD2 endpoint URLs, OAuth flow, and dialect quirks. Each plugin is `Disabled` by default; the user enables the bank(s) they actually have; configuration walks the bank's own consent flow through Chrome Custom Tabs; `Active` only after `Test connection` succeeds.

## What PSD2 is (recap)

PSD2 (Payment Services Directive 2, [Directive (EU) 2015/2366](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX%3A32015L2366)) obligates banks operating in the EU/EEA/UK to expose customer-account data through standardised APIs to authorised third parties (AISPs / PISPs). Crucially for ledgerboy:

- **PSD2 Article 67** establishes the AISP "right of access to and use of account information services." The right is *the user's right* to direct a third party to access their own account data on their behalf — the bank can't refuse.
- For **personal access to one's own account**, the user is exercising their **subject-of-data right under GDPR Article 15** + the PSD2 Article 67 access right at the same time. A natural person accessing **their own** accounts via PSD2 endpoints is not "providing AISP services to third parties" and therefore — per the European Banking Authority's [EBA Opinion EBA-Op-2020-10](https://www.eba.europa.eu/publications-and-media/press-releases/eba-publishes-opinion-obstacles-provision-third-party) on PSD2 obstacles — does not itself require a PSD2 licence. The licence regime is about *commercial provision of AIS to others*, not self-access.
- In practice, banks gate their **production** NextGenPSD2 endpoints on an **eIDAS QWAC** (Qualified Website Authentication Certificate) presented during the TLS handshake. The QWAC is issued to a PSD2-licensed AISP. **This is the operational obstacle**, not the regulation itself, and it is precisely the obstacle the EBA Opinion flags as a non-conformity in several member states. **Per-bank research is required** — some banks expose a parallel "PSD2 for personal use / sandbox-with-real-data" tier that omits the QWAC requirement; others do not.
- Consent windows are capped at **90 days** by Regulatory Technical Standard ([Commission Delegated Regulation (EU) 2018/389, Article 10](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX%3A32018R0389)). Every plugin's consent flow re-prompts the user every 90 days. This is regulatory, not bank-specific.

The honest summary: **direct-to-bank PSD2 for personal use is legally clear, operationally per-bank-dependent**. Each plugin's research notes must record whether the target bank actually serves a personal-use customer without QWAC.

## The standard: Berlin Group NextGenPSD2

Most EU continental banks publish their PSD2 API as a dialect of the **Berlin Group NextGenPSD2** common standard ([berlin-group.org/nextgenpsd2-downloads](https://www.berlin-group.org/nextgenpsd2-downloads), current version **v1.3.13** as of 2026-05). The standard defines a REST/JSON surface for:

- Account Information Service (AIS): list accounts, balances, transaction history.
- Payment Initiation Service (PIS): initiate SEPA / instant / cross-border payments — **out of scope** for ledgerboy plugins.
- Confirmation of Funds (PIIS): yes/no balance check — **out of scope**.

Each bank publishes its NextGenPSD2 dialect: base URL, sandbox URL, supported scopes, supported SCA approaches (redirect / decoupled / embedded), supported balance types, transaction-pagination model, consent endpoint shape. A NextGenPSD2 plugin is, in essence, the bank's specific dialect of the common spec.

UK banks publish under the parallel **UK Open Banking** standard ([standards.openbanking.org.uk](https://standards.openbanking.org.uk/)) which is REST/JSON-shaped but uses different endpoint paths and a different consent model (Open Banking Account & Transaction API v3.1.10 as of 2026-05). UK plugins are siblings of NextGenPSD2 plugins in code but follow a different spec.

## Plugin shape

Every PSD2-direct plugin is one Kotlin module under
`app/src/main/java/com/eight87/ledgerboy/plugins/psd2-<bank-slug>/`
implementing `ConnectorPlugin` (per [`connector-plugins.md`](connector-plugins.md)). Per-plugin responsibilities:

- **Manifest entry**: `id = "psd2-sparkasse-de"` (or `psd2-dkb-de`, `psd2-monzo-uk`, etc.), `category = Banking`, `displayName` localised, `description` one paragraph naming the bank and noting it talks directly to the bank's own PSD2 API.
- **`ConfigScreen()`**:
  - Bank-specific intro paragraph + link to the bank's PSD2 developer-portal page.
  - "Begin consent" button → opens Chrome Custom Tabs to the bank's consent URL.
  - Per-bank fields if the bank requires them (some banks need a customer ID or IBAN to initiate consent).
  - "Test connection" button: after consent returns, perform one `GET /accounts` and report success/failure (account count, no balances).
- **State machine**:
  - `Disabled` → user toggles enable → `Enabled`.
  - `Enabled` → user runs consent flow + Test connection → `Configured`.
  - `Configured` + `Test connection` success → `Active`.
  - On consent expiry (90-day cliff) → drops back to `Configured` with a re-consent banner. No network calls in this state.
  - User disables → all of the above unwind to `Disabled` immediately (network guard rejects in-flight calls per [`connector-plugins.md`](connector-plugins.md) X.4).
- **Credentials**: consent reference token + bank-issued access/refresh tokens stored in encrypted Room via the Keystore-wrapping helper provided by the host (per [`security-research.md`](security-research.md)). Never DataStore-plaintext.
- **Transport**: shared OkHttp from the host, wrapped by `PluginNetworkGuard`. Per-bank TLS pinning where the bank publishes a pin.
- **Privacy statement**: each plugin declares "Data leaves the device to: `https://<bank>.example/psd2/v1/...`. No third party. Bank sees: device user agent, your account access token."
- **Network usage**: surfaces in Settings → Plugins → Network usage per [`connector-plugins.md`](connector-plugins.md) X.7.

## Per-bank coverage matrix (candidates, to be confirmed during per-bank research)

The current evidence base for "does this bank serve a personal NextGenPSD2 client without an AISP QWAC?" is mixed. The matrix below records the candidate set; each candidate becomes a per-plugin research sub-phase. Verdicts marked **verify** require hands-on investigation against the bank's PSD2 developer portal before the plugin's Phase B sub-phase opens.

### Germany (Berlin Group NextGenPSD2)

| Bank | Dev portal | NextGenPSD2 version | Production-without-QWAC? | Verdict |
|---|---|---|---|---|
| Sparkasse (DSGV — shared endpoint) | <https://api.sparkasse.de/> | 1.3.6+ | verify (DSGV historically required QWAC) | **candidate, v1 short list** |
| Volksbanken / Raiffeisenbanken (Atruvia — shared endpoint) | <https://developer.atruvia.de/> | 1.3.6+ | verify (Atruvia gates production) | **candidate** |
| DKB | <https://developer.dkb.de/> | 1.3 | verify | **candidate, v1 short list** |
| Commerzbank | <https://developer.commerzbank.com/> | 1.3 | verify (Commerzbank documented QWAC-only) | candidate |
| ING-DiBa | <https://api.ing.com/openbanking/> | 1.3 | verify (ING Group's portal, pan-EU) | candidate |
| Deutsche Bank | <https://developer.db.com/> | 1.3 | verify (db.com gates production) | candidate |
| N26 | <https://docs.n26.com/> | 1.3 | verify | candidate |
| Comdirect | (Commerzbank-Group portal) | 1.3 | verify | candidate |
| Postbank | (Deutsche-Bank-Group portal) | 1.3 | verify | candidate |

### Pan-EU neo-banks (variants of NextGenPSD2)

| Bank | Dev portal | Standard | Verdict |
|---|---|---|---|
| Revolut | <https://developer.revolut.com/docs/business/personal-api> | Open Banking (UK) + NextGenPSD2 (EU) | verify (Revolut Personal API exists for self-access) — **candidate** |
| Wise | <https://api-docs.transferwise.com/> | Wise's own REST API + NextGenPSD2 in the EU | verify | candidate |
| bunq | <https://doc.bunq.com/> | bunq's own REST + NextGenPSD2 | **likely fit** (bunq publishes a self-service developer API for own-account access) — candidate |

### UK (Open Banking Account & Transaction API v3.1)

| Bank | Dev portal | Standard | Verdict |
|---|---|---|---|
| HSBC UK | <https://develop.hsbc.com/> | Open Banking 3.1 | verify | candidate |
| Barclays | <https://developer.barclays.com/> | Open Banking 3.1 | verify | candidate |
| Lloyds Banking Group | <https://developer.lloydsbanking.com/> | Open Banking 3.1 | verify | candidate |
| Monzo | <https://docs.monzo.com/> | Monzo's own OAuth REST (also Open Banking) | **likely fit** (Monzo's own API supports own-account access) — **candidate, v1 short list** |
| Starling | <https://developer.starlingbank.com/> | Starling's own OAuth REST + Open Banking | **likely fit** (Starling Developer API exists) — candidate |

### Austria / DACH continental

| Bank | Dev portal | Verdict |
|---|---|---|
| Erste Group (George) | <https://developers.erstegroup.com/> | verify — candidate |
| Raiffeisen Bank International | <https://developer.rbinternational.com/> | verify — candidate |

### Notes on the matrix

- **Verify** is the dominant status because the **only** authoritative answer is "open the bank's developer portal and read their access policy." Several banks describe an "AISP licence required" obstacle that the EBA Opinion classifies as non-conformity; banks may have updated their stance since the agent-training-data cutoff. Each per-plugin sub-phase reads the portal afresh.
- The pivot does not commit ledgerboy to shipping every plugin in the matrix. It commits to the *plugin-per-bank model*; the actual v1 ship list is two or three candidates, picked after per-plugin verification.

## Realistic v1 scope

- **v1 ships at most two PSD2 plugins** chosen from the verified-fit set. Likely picks: the user's primary bank + one second candidate with a known-good personal-access posture (e.g. Monzo or bunq) for breadth.
- The per-bank plugin scaffold lands once. Each subsequent bank is a small Phase B sub-phase (~200–500 LOC + per-bank consent-URL + per-bank field shape).
- **No bank is bundled enabled by default.** A user with no PSD2 plugin enabled never makes a PSD2 network call.

## Consent + OAuth flow (per-bank, common shape)

Every plugin's consent path:

1. User taps "Begin consent" in `ConfigScreen()`.
2. Plugin builds a per-bank consent URL with `redirect_uri=ledgerboy://psd2/<plugin-id>/callback`.
3. Plugin opens Chrome Custom Tabs (`androidx.browser:browser`) to the consent URL.
4. User authenticates with bank credentials inside the bank's own web flow.
5. Bank redirects to `ledgerboy://psd2/<plugin-id>/callback?consent_ref=...`.
6. Custom-scheme intent filter on `LedgerboyActivity` routes the callback back into the plugin's `onConsentCallback(...)`.
7. Plugin exchanges `consent_ref` for access + refresh tokens via the bank's token endpoint.
8. Plugin stores tokens encrypted, transitions `Configured` → `Active` after the user runs `Test connection`.
9. Plugin records `consentExpiresAt = now + 90.days` (regulatory ceiling).

Per [`connector-plugins.md`](connector-plugins.md), all of this is gated by `PluginNetworkGuard` — no token-endpoint call happens unless the user is on the `ConfigScreen()` and has just returned from the consent Custom Tab.

## Library candidates

No third-party "PSD2 client" library to adopt. The two motivating reasons:

1. **Berlin Group NextGenPSD2 is a thin REST/JSON surface.** Hand-rolling per-bank clients with OkHttp + kotlinx-serialization-json is ~500 LOC per bank for read-only AIS. A library wrapper saves maybe 100 LOC of that and costs us a transitive dep.
2. **The licence gate disqualifies most candidates anyway.** [`xs2a-adapter`](https://github.com/adorsys/xs2a-adapter) (adorsys) is **Apache-2.0** but Spring-shaped and pulls a huge transitive set — wrong for an Android app. [`open-banking-gateway`](https://github.com/adorsys/open-banking-gateway) is the same problem. [Tesobe `OpenBankProject`](https://github.com/OpenBankProject/OBP-API) is **AGPL-3.0** — disqualifying.

**Decision: per-plugin hand-roll on shared OkHttp + kotlinx-serialization-json.** Both Apache-2.0, both already in the dep set per [`banking-import.md`](banking-import.md) and [`banking-fx.md`](banking-fx.md).

A shared `Psd2PluginToolkit` internal module under
`app/src/main/java/com/eight87/ledgerboy/plugins/psd2-common/`
factors out the parts every NextGenPSD2 plugin needs:

- `ConsentFlow` helper (build consent URL, launch Custom Tab, parse callback).
- `BerlinGroupAccount` / `BerlinGroupTransaction` / `BerlinGroupBalance` data classes mapping the v1.3 schema.
- `NormalizedTransactionMapper` from Berlin-Group `transactions[].*` → ledgerboy `NormalizedTransaction` (with `Money` integer minor units per CLAUDE.md).
- `ConsentExpiryTracker` (90-day cliff, re-consent banner trigger).

Per-bank plugins implement only the bank-specific bits: base URLs, dialect overrides, per-bank field-presence quirks.

## Sandbox

Most NextGenPSD2 banks publish a sandbox with synthetic accounts. Plugins should support a `useSandbox = true` config toggle (visible only in a Developer Options sub-screen) so the Test connection button can exercise the sandbox before the user commits to a real consent flow. UK Open Banking publishes a [Directory Sandbox](https://standards.openbanking.org.uk/our-standards/) with similar facilities.

## Sample bank coverage (recap)

Direct-PSD2 plugins reach **only the banks they implement**. There is no broad-coverage shortcut anymore — that was what aggregators offered, and we don't ship aggregators. Coverage grows one bank at a time, in Phase B sub-phases that the user (or a contributor) opens for a bank that matters to them.

## Credential shape

Per plugin, stored encrypted in Room (per `security-research.md`):

- Access token (per the bank's OAuth response, usually JWT, ~10 min to 24 h validity).
- Refresh token (per the bank's OAuth response, longer-lived, refreshable for the 90-day consent window).
- Consent reference (per-bank identifier for the active consent).
- `consentExpiresAt` (90-day cliff timestamp).
- Optional: per-bank account-ID list cached from the first `GET /accounts`.

User's bank-login password never touches the app — entered in the bank's own Custom-Tab consent flow only.

## Sandbox / test bank

Per-bank — every bank's sandbox is its own animal. Documented in the per-plugin sub-phase as it lands.

## Cost

**Free at every layer.** Banks must expose PSD2 for free under the regulation. No aggregator fees because there are no aggregators.

## ToS / legal posture

- **Per-bank developer portal Terms of Service** govern access. Most terms permit personal-use access by the account holder. Some terms restrict redistribution of the resulting data — ledgerboy doesn't redistribute (data stays on-device).
- **No "downstream AISP licence consumer" relationship** — the user is exercising their own access right; ledgerboy is the technical means.
- **GDPR is the user's right** (Article 15 + 20); PSD2 Article 67 + RTS Article 10 are the mechanism.

## Fit for ledgerboy — adopt / defer / reject

- **Per-bank NextGenPSD2 plugin pattern:** **adopt** as the v1 PSD2 architecture, per the connector-plugin lock.
- **Two-plugin v1 ship list** (user's primary bank + one breadth pick): **adopt** as the realistic v1 scope.
- **GoCardless / Tink / TrueLayer / Plaid / Salt Edge:** **reject** (aggregator middlemen — not shipped, not researched further).
- **xs2a-adapter / open-banking-gateway / OBP-API:** **reject** (architectural mismatch + AGPL in one case).

## Decision

**Adopt the per-bank PSD2-direct plugin pattern.** Each bank ships as its own `ConnectorPlugin`, off by default, with a shared `Psd2PluginToolkit` factoring the Berlin Group common surface. v1 ships two plugins picked after per-bank verification of the personal-access posture.

## Phase X (plugin runtime) — gating

Per [`connector-plugins.md`](connector-plugins.md), Phase X lands before any PSD2 plugin work. The runtime supplies:

- `ConnectorPlugin` interface + state machine (X.1).
- `PluginManifest` (X.2).
- Per-plugin DataStore + encrypted-Room hooks (X.3).
- `PluginNetworkGuard` OkHttp interceptor (X.4).
- `PluginScheduler` (X.5).
- Settings → Plugins UI (X.6 / X.7 / X.8).

PSD2 plugins are downstream consumers of every X.* facility. **Do not start any plugin sub-phase before Phase X is complete.**

## Phase B sub-step checklist — per-bank PSD2 plugins

> All sub-phases gated on Phase X (plugin runtime) being complete.

### B.psd2-common — shared toolkit (lands once, before any per-bank plugin)

- [ ] **B.psd2-common.1** `Psd2PluginToolkit` module under `app/src/main/java/com/eight87/ledgerboy/plugins/psd2-common/`. SPDX: ours.
- [ ] **B.psd2-common.2** Berlin Group v1.3 schema data classes (`BerlinGroupAccount`, `BerlinGroupTransaction`, `BerlinGroupBalance`, `BerlinGroupConsent`). Source the JSON schema from <https://www.berlin-group.org/nextgenpsd2-downloads>.
- [ ] **B.psd2-common.3** `NormalizedTransactionMapper` from Berlin-Group transactions → ledgerboy `NormalizedTransaction` with `Money` minor units per ISO 4217.
- [ ] **B.psd2-common.4** `ConsentFlow` helper: build consent URL, launch Chrome Custom Tab, parse `ledgerboy://psd2/<plugin-id>/callback` deep link, exchange consent ref → tokens.
- [ ] **B.psd2-common.5** `ConsentExpiryTracker` (90-day RTS cliff; re-consent notification 7 days before expiry per [`connector-plugins.md`](connector-plugins.md) X.5 scheduler hooks).
- [ ] **B.psd2-common.6** UK Open Banking v3.1 schema data classes + mapper sibling (used by Monzo / Starling / UK-bank plugins).
- [ ] **B.psd2-common.7** Robolectric tests: schema round-trip, mapper minor-unit conversion across every ISO 4217 currency, consent-expiry math, callback-URL parser.

### B.psd2-<bank> — per-bank plugin template

Each per-bank plugin is one sub-phase of this shape (template; pick concrete banks for v1 after per-bank verification):

- [ ] **B.psd2-<bank>.1** Read the bank's PSD2 developer portal end-to-end. Record: base URL, sandbox URL, NextGenPSD2 version, SCA approaches supported, whether personal-use access without QWAC is offered, any per-bank consent-URL fields needed.
- [ ] **B.psd2-<bank>.2** Implement `Psd2<Bank>Plugin : ConnectorPlugin` under `app/src/main/java/com/eight87/ledgerboy/plugins/psd2-<bank-slug>/`. Register in `PluginManifest`.
- [ ] **B.psd2-<bank>.3** Wire the bank-specific consent URL + per-bank fields into the shared `ConsentFlow`.
- [ ] **B.psd2-<bank>.4** Per-bank dialect overrides where the bank deviates from Berlin Group baseline (transaction-pagination model, balance-type enum quirks, optional-field-presence variations).
- [ ] **B.psd2-<bank>.5** Implement `fetch(window)`: `GET /accounts` → for each `accountId`, `GET /accounts/{id}/transactions?dateFrom=...`. Map to `List<NormalizedTransaction>` via `NormalizedTransactionMapper`.
- [ ] **B.psd2-<bank>.6** Plugin `ConfigScreen()`: bank intro + dev-portal link + "Begin consent" button + "Test connection" button. All strings in `values/strings.xml` per CLAUDE.md i18n discipline.
- [ ] **B.psd2-<bank>.7** Privacy statement string: enumerate every endpoint the plugin calls.
- [ ] **B.psd2-<bank>.8** Robolectric tests: per-bank fixture round-trip (synthetic Berlin-Group transactions JSON → `NormalizedTransaction`), consent URL builder, callback parser.
- [ ] **B.psd2-<bank>.9** Smoke test against the bank's sandbox via the AVD (per CLAUDE.md UI verification rule).

### B.psd2 — v1 ship list (concrete picks land here after per-bank verification)

- [ ] **B.psd2.v1a** First plugin: user's primary bank, picked after the per-bank-verification round.
- [ ] **B.psd2.v1b** Second plugin: breadth candidate (likely Monzo or bunq based on the likely-fit notes above; verify before opening the sub-phase).

## Phase C implementation sub-steps

- [ ] **C.psd2.1** Per-plugin `sourceConnectorId` registration: `"psd2:<bank-slug>"`. Dedup on `(sourceConnectorId, sourceRef)` unique index per [`banking-import.md`](banking-import.md) row-hash discipline.
- [ ] **C.psd2.2** Background refresh via `PluginScheduler` (per [`connector-plugins.md`](connector-plugins.md) X.5). Per-plugin background-sync opt-in defaults OFF — user must explicitly enable background fetch per plugin.
- [ ] **C.psd2.3** Re-consent notification surface: 7 days before `consentExpiresAt`, the plugin's card in Settings → Plugins shows a "Re-consent due in N days" badge; the Notifications surface gets a system notification.
- [ ] **C.psd2.4** Per-plugin Network usage rows appear in Settings → Plugins → Network usage (per [`connector-plugins.md`](connector-plugins.md) X.7).

## References

- Connector plugin architecture (authority): [`connector-plugins.md`](connector-plugins.md)
- Berlin Group NextGenPSD2 downloads: <https://www.berlin-group.org/nextgenpsd2-downloads>
- UK Open Banking standards: <https://standards.openbanking.org.uk/>
- PSD2 directive (EUR-Lex): <https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX%3A32015L2366>
- RTS on SCA + secure communication (90-day consent cap, Article 10): <https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX%3A32018R0389>
- EBA Opinion on PSD2 obstacles (EBA-Op-2020-10): <https://www.eba.europa.eu/publications-and-media/press-releases/eba-publishes-opinion-obstacles-provision-third-party>
- Chrome Custom Tabs (androidx.browser): <https://developer.android.com/develop/ui/views/layout/webapps/customtabs>
- Per-bank developer portals: linked in the coverage matrix above.
