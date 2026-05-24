# ledgerboy — PSD2 direct-per-bank plugin research

## Status: RESEARCH — recommendation rewritten 2026-05-24 after the connector-plugin architecture lock + per-bank QWAC verification round 2026-05-24. Phase X (plugin runtime) lands before any of these plugins.

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

## QWAC verification matrix (2026-05-24 round)

Verdict legend:

- **OPEN** — bank publishes a personal/developer API path the account holder can use against their own account without AISP licensing or eIDAS QWAC. Adoptable for a ledgerboy plugin.
- **CONDITIONAL** — sandbox is reachable with self-signed / test QWAC; production access requires AISP + eIDAS QWAC. Useful only for development/smoke tests; not shippable.
- **CLOSED** — production AND meaningful sandbox both gated on eIDAS QWAC and BaFin / NCA AISP registration. Cannot ship a plugin without the user being a licensed AISP.

The "Berlin Group NextGenPSD2" path is **CLOSED across the board** for personal access — every German bank surveyed enforces the eIDAS QWAC requirement at the TLS layer because the XS2A PSD2 endpoint family is, by ASPSP policy, AISP-only. The personal-access path exists exclusively on banks that publish a **parallel, non-PSD2 developer API** of their own (the neo-banks: Monzo, Starling, bunq).

### Germany — Berlin Group NextGenPSD2 (XS2A)

| Bank | QWAC required for prod? | Personal-access w/o QWAC? | Sandbox w/o QWAC? | URL evidence | Verdict |
|---|---|---|---|---|---|
| Sparkasse (DSGV / Finanz Informatik) | Yes | No | No — sandbox accepts only PSD2-format QWACs | <https://www.openbanking.exchange/europe/resources/insights/psd2-xs2a-granting-access-certificates-and-registers/> | **CLOSED** |
| Volksbanken / Raiffeisen (Atruvia AG) | Yes | No | No — Atruvia's PSD2 1.3 swagger requires eIDAS QWAC | <https://p1.xs2a-doc.atruvia.de/swagger-ui/index.html>, <https://documentation.finapi.io/access/registering-with-an-aspsp-to-gain-tpp-api-access> | **CLOSED** |
| DKB (via finAPI XS2A) | Yes | No | No — "Third-party providers with a valid QWAC certificate can access the interface" | <https://www.dkb.de/fragen-antworten/wie-funktioniert-der-drittanbieter-zugang-gemaess-psd2>, <https://www.finapi.io/en/dkb-integrates-the-finapi-xs2a-server/> | **CLOSED** |
| Commerzbank | Yes | No | No — "A valid QWAC Certificate for PSD2 is required to access the Sandbox and Berlin Group API" | <https://psd2.developer.commerzbank.com/howto> | **CLOSED** |
| ING-DiBa | Yes | No | No — "eIDAS certificates can only be obtained by organizations, not by individuals"; ING devs explicitly confirm "Open Banking for personal use" is not possible | <https://developer.ing.com/openbanking/resources/get-started/psd2>, <https://villoro.com/post/ing_api> | **CLOSED** |
| Deutsche Bank | Yes | No | No — db.com gates all XS2A endpoints on QWAC | <https://developer.db.com/products/psd2> | **CLOSED** |
| N26 (PSD2 dedicated interface) | Yes | No | No — "QWAC Certificate must be issued from a production certificate authority"; AIS cert required for AIS endpoints | <https://support.n26.com/en-eu/security/open-banking-psd2/psd2-open-banking-for-third-party-providers>, <https://github.com/n26/psd2-tpp-docs> | **CLOSED** |
| Comdirect (Commerzbank Group / XS2A) | Yes | No | Conditional — sandbox accepts ETSI-format test QWAC per June-2019 EBA clarification, but the certificate must still follow eIDAS QWAC shape | <https://xs2a-developer.comdirect.de/content/howto/connection> | **CLOSED** (sandbox-only test-cert tier doesn't reach prod) |
| Postbank (Deutsche-Bank Group) | Yes | No | No | (Deutsche-Bank-Group portal, same posture as Deutsche Bank) | **CLOSED** |

### Pan-EU neo-banks (non-PSD2 own-API paths exist alongside their PSD2 endpoints)

| Bank | QWAC required for prod? | Personal-access w/o QWAC? | Sandbox w/o QWAC? | URL evidence | Verdict |
|---|---|---|---|---|---|
| bunq (own REST API, NOT the PSD2 path) | No, for own-account use | **Yes** — "You can use the bunq API to manage your own accounts without a license—a license is only needed if you use the API to provide services to third parties"; PSD2 path is separate | <https://doc.bunq.com/basics/authentication/api-keys>, <https://doc.bunq.com/basics/authentication/oauth>, <https://help.bunq.com/articles/bunq-developer-create-and-share-your-apps> | Yes — public sandbox + production with personal API key / OAuth | **OPEN** (requires bunq Pro or Elite paid plan — not free, but no AISP/QWAC gate) |
| Revolut Personal | n/a — there is no developer Personal API for individuals | No | No — Revolut's developer portal only serves Revolut Business (with cert-based JWT auth) | <https://developer.revolut.com/docs/business/business-api>, <https://developer.revolut.com/docs/guides/build-banking-apps/get-started/get-access-token> | **CLOSED** (no personal-customer developer tier exists) |
| Wise (personal API token) | No, for the limited endpoint set | Partial — personal token works for quotes/recipients/transfers; **balance & statement endpoints not available outside US/CA/AU/NZ/SG/MY** | n/a — production token | <https://docs.wise.com/guides/developer/auth-and-security/personal-api-token> | **CONDITIONAL** — token issuance is OPEN; the AIS surface ledgerboy needs (balance + transactions) is closed for EU/UK accounts |

### UK — Open Banking 3.1 vs. each bank's own developer API

| Bank | QWAC required for prod? | Personal-access w/o QWAC? | Sandbox w/o QWAC? | URL evidence | Verdict |
|---|---|---|---|---|---|
| HSBC UK (Open Banking 3.1) | Yes — full Open Banking Directory enrollment | No | Test-cert sandbox only | <https://develop.hsbc.com/knowledge-article/get-started-open-banking-apis> | **CLOSED** |
| Barclays (Open Banking 3.1) | Yes — "Open Banking only if you have the required regulatory permission from the FCA" (AISP/PISP) | No | Test sandbox after Barclays-side identity & validation checks | <https://developer.barclays.com/open-banking> | **CLOSED** |
| Lloyds Banking Group (Open Banking 3.1) | Yes — Read-Write APIs require Open Banking Directory registration (company-level) | No | Yes for Open-Data only (no personal account access) | <https://developer.lloydsbanking.com/prod01/lbg/get-started> | **CLOSED** |
| Monzo (own OAuth API) | No | **Yes** — "You may only connect to your own account or those of a small set of users you explicitly allow"; explicitly designed for personal access. No AISP licensing required. | Yes — production OAuth client at developers.monzo.com | <https://docs.monzo.com/>, <https://developers.monzo.com/> | **OPEN** |
| Starling (own OAuth API + Personal Access Token tier) | No | **Yes** — Personal Access Tokens are issued from the developer dashboard to account holders, linked to the user's own account via QR-code scan in the Starling app. Scopes include `account:read`, `balance:read`, `transaction:read`, `space:read`. | n/a — tokens hit production | <https://developer.starlingbank.com/get-started>, <https://developer.starlingbank.com/permissions>, <https://starlingbank.github.io/starling-developer-sdk/examples/authorization> | **OPEN** |

### Austria / DACH continental

| Bank | QWAC required for prod? | Personal-access w/o QWAC? | URL evidence | Verdict |
|---|---|---|---|---|
| Erste Group (George) | Yes — Berlin Group XS2A, AISP-gated | No | <https://developers.erstegroup.com/> | **CLOSED** |
| Raiffeisen Bank International | Yes — Berlin Group XS2A, AISP-gated | No | <https://developer.rbinternational.com/> | **CLOSED** |

### Notes on the matrix

- The verdict pattern is consistent: **every ASPSP that exposes only the regulatory PSD2/XS2A path is CLOSED for personal access**. The eIDAS QWAC is issued only to legal entities (per [Reg. (EU) 910/2014 eIDAS](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX%3A32014R0910)), so a natural person cannot satisfy the transport-layer requirement, regardless of whether they are accessing their own account.
- The [EBA Opinion EBA-Op-2020-10](https://www.eba.europa.eu/publications-and-media/press-releases/eba-publishes-opinion-obstacles-provision-third-party) flags the QWAC barrier in the context of *commercial TPPs* and clarifies that ASPSPs may not impose *additional* AISP-style checks; it does **not** carve out a "natural-person own-account direct access" path that bypasses the eIDAS QWAC at the TLS layer. The PSD2 Article 67 right of access remains a right *to direct a licensed TPP*, not a right to bring your own TLS client to the XS2A endpoint.
- GDPR Article 15 / 20 layered on top does not change the transport requirement — it gives the data subject the right to receive a copy of their data, which most banks satisfy via their netbanking export (CAMT.053 / MT940 / CSV downloads) — i.e., the **file-import plugins**, not PSD2-direct.
- The OPEN verdicts are **non-PSD2 paths**: bunq, Monzo, and Starling each publish their own developer API (not Berlin Group / not UK Open Banking 3.1) that explicitly contemplates account-holder use without AISP licensing. Those APIs exist in addition to the bank's PSD2 endpoints, not on top of them.
- Wise's personal-token tier is genuinely OPEN at the auth layer, but the endpoints ledgerboy needs (balances + transactions) are scoped out for EU/UK accounts. Useful only if the user has a US / CA / AU / NZ / SG / MY Wise account.

## Realistic v1 scope (post-verification)

The 2026-05-24 QWAC verification round eliminated **every German PSD2 candidate** from the v1 ship list — Sparkasse, DKB, Commerzbank, ING-DiBa, Deutsche Bank, N26, Comdirect, Postbank, Atruvia/Volksbanken all gate XS2A access on an eIDAS QWAC, which is issued only to AISP-licensed legal entities. The same is true of every UK bank that exposes only the Open Banking Account & Transaction API (HSBC, Barclays, Lloyds).

The **OPEN set** is three banks, none German:

1. **bunq** (NL-licensed, pan-EU) — own-account API via personal API key or OAuth, no AISP gate. Requires a paid bunq Pro or Elite subscription.
2. **Monzo** (UK) — own-account OAuth API explicitly designed for personal developer access; the user must be a Monzo account holder.
3. **Starling** (UK) — Personal Access Tokens, scoped (`account:read`, `balance:read`, `transaction:read`, `space:read`), bound to the user's own account via QR-code linking in the Starling app.

Given the user's primary geography (DE) is unrepresented in the OPEN set, the v1 ship-list options are:

- **Option A — ship one or two OPEN-set plugins anyway for breadth.** Picks: bunq (broadest EU footprint, real EUR IBAN) + Monzo or Starling (UK breadth). Useful for users who happen to bank with these institutions, useless for the user's own primary banking.
- **Option B — defer the entire `psd2-direct` plugin category from v1.** Document that direct-to-bank for DE users runs exclusively via the **FinTS plugin** (per [`banking-fints.md`](banking-fints.md)) plus the **file-import plugins** (per [`banking-import.md`](banking-import.md)), and revisit PSD2-direct only when one of:
  - A regulatory change (PSD3 / FIDA — the [Financial Data Access framework currently in EU trilogue](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX%3A52023PC0360)) introduces an explicit natural-person own-account direct-access tier without QWAC.
  - A new DE neobank (or an existing one) publishes a non-PSD2 personal developer API in the bunq/Monzo/Starling shape.
  - The user moves accounts to bunq or a similar OPEN-set bank.

**Recommendation: Option A, narrow.** Ship bunq and Monzo as the v1 PSD2-direct reference plugins (priority order: bunq first because of EUR/SEPA-EU footprint, Monzo second for UK-side breadth and proof-of-shape that the `Psd2PluginToolkit` works against a second, very different API dialect). Starling slots in as v1.5 once the bunq + Monzo plugins are stable — same OPEN posture, just lower priority because Monzo already covers the UK breadth case. **Skip every German bank in v1.** German users live on FinTS + file-import until the regulatory landscape changes.

- The per-bank plugin scaffold lands once. Each subsequent bank is a small Phase B sub-phase (~200–500 LOC + per-bank consent-URL + per-bank field shape).
- **No bank is bundled enabled by default.** A user with no PSD2 plugin enabled never makes a PSD2 network call.
- **Architectural note:** the `Psd2PluginToolkit` name is now a slight misnomer — the OPEN-set plugins (bunq, Monzo, Starling) talk each bank's own non-PSD2 OAuth API rather than Berlin Group NextGenPSD2. Keep the toolkit name (Phase X-era code does not change), but `B.psd2-common.2` (Berlin Group schema data classes) and `B.psd2-common.6` (UK Open Banking 3.1 schema) are now **deferred indefinitely** — the OPEN-set plugins each carry their own data classes. Berlin Group / OB 3.1 schema work re-opens only when an OPEN-set bank emerges that actually uses one of those specs for personal access.

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

**Adopt the per-bank direct-API plugin pattern.** Each bank ships as its own `ConnectorPlugin`, off by default. The shared `Psd2PluginToolkit` factors the OAuth-redirect / Custom-Tab / token-store mechanics; the Berlin Group + UK Open Banking 3.1 schema sub-modules are deferred indefinitely because the OPEN-set banks don't use those specs.

**v1 ships two plugins, neither German, neither using Berlin Group NextGenPSD2:**

1. **bunq** — own-account API, requires bunq Pro/Elite subscription, no AISP/QWAC gate.
2. **Monzo** — own-account OAuth API, UK-licensed, explicitly intended for personal developer use.

**v1.5 adds Starling.** No further PSD2-direct plugins are planned until a regulatory shift (PSD3 / FIDA) opens a personal-account-holder direct-access tier on the XS2A path, or until a DE neobank publishes a non-PSD2 personal developer API.

**For DE users on traditional banks (Sparkasse / Volksbanken / DKB / Commerzbank / ING / DB / N26 / Comdirect / Postbank): direct-to-bank runs via the FinTS plugin** (see [`banking-fints.md`](banking-fints.md)) — FinTS is unaffected by the eIDAS QWAC gate because it predates PSD2 and uses HBCI's own auth model. The **file-import plugins** (CAMT.053 / MT940 / OFX / QIF / CSV per [`banking-import.md`](banking-import.md)) cover the universal fallback: every bank's netbanking export route is GDPR-Article-15-mandated and works without any developer-API access at all.

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
>
> **Verification round complete (2026-05-24)** — every candidate bank's `.1` sub-step is now ticked with verdict + URL evidence, per the QWAC verification matrix above. The OPEN set narrowed to three non-DE neo-banks (bunq, Monzo, Starling). All German PSD2 candidates are CLOSED until the regulatory landscape shifts.

### B.psd2-common — shared toolkit (lands once, before any per-bank plugin)

- [ ] **B.psd2-common.1** `Psd2PluginToolkit` module under `app/src/main/java/com/eight87/ledgerboy/plugins/psd2-common/`. SPDX: ours.
- [ ] **B.psd2-common.2** Berlin Group v1.3 schema data classes (`BerlinGroupAccount`, `BerlinGroupTransaction`, `BerlinGroupBalance`, `BerlinGroupConsent`). Source the JSON schema from <https://www.berlin-group.org/nextgenpsd2-downloads>. **DEFERRED** — no OPEN-set bank uses Berlin Group for personal access; revisit only when an OPEN-set Berlin-Group bank emerges.
- [ ] **B.psd2-common.3** `NormalizedTransactionMapper` — keep as a target interface (`fun <T> map(raw: T): NormalizedTransaction`); concrete mappers live in each per-bank plugin since the OPEN-set banks share no common schema.
- [ ] **B.psd2-common.4** `ConsentFlow` helper: build consent URL, launch Chrome Custom Tab, parse `ledgerboy://psd2/<plugin-id>/callback` deep link, exchange consent ref → tokens. Still needed — bunq, Monzo, and Starling all use OAuth 2.0 + redirect.
- [ ] **B.psd2-common.5** `ConsentExpiryTracker` — repurpose for OAuth refresh-token expiry per bank (Monzo: ~hours, refresh-token several months; bunq: refresh per session; Starling: Personal Access Tokens are long-lived but revocable). RTS 90-day cliff is **N/A for the OPEN-set** because none of them are PSD2 endpoints.
- [ ] **B.psd2-common.6** UK Open Banking v3.1 schema data classes + mapper sibling. **DEFERRED** — Monzo and Starling use their own APIs, not OB 3.1. Revisit only if a UK OB-3.1 bank ever publishes a personal-access tier.
- [ ] **B.psd2-common.7** Robolectric tests: `ConsentFlow` callback parser, OAuth state-param round-trip, mapper interface tests with synthetic fixtures.

### B.psd2-<bank> — per-bank plugin template

Each per-bank plugin is one sub-phase of this shape:

- [ ] **B.psd2-<bank>.1** Read the bank's developer portal end-to-end. Record: base URL, sandbox URL, API version, auth model, whether personal-use access without QWAC is offered, any per-bank consent-URL fields needed.
- [ ] **B.psd2-<bank>.2** Implement `Psd2<Bank>Plugin : ConnectorPlugin` under `app/src/main/java/com/eight87/ledgerboy/plugins/psd2-<bank-slug>/`. Register in `PluginManifest`.
- [ ] **B.psd2-<bank>.3** Wire the bank-specific consent / token-issuance URL + per-bank fields into the shared `ConsentFlow` (or per-bank flow where the bank's auth model differs — Starling uses a Personal Access Token paste rather than OAuth redirect).
- [ ] **B.psd2-<bank>.4** Per-bank data classes (account, transaction, balance) — each OPEN-set bank has its own schema.
- [ ] **B.psd2-<bank>.5** Implement `fetch(window)`: per the bank's account-list + transaction-list endpoints. Map to `List<NormalizedTransaction>` via the per-bank mapper.
- [ ] **B.psd2-<bank>.6** Plugin `ConfigScreen()`: bank intro + dev-portal link + "Begin consent" (or "Paste access token") button + "Test connection" button. All strings in `values/strings.xml` per CLAUDE.md i18n discipline.
- [ ] **B.psd2-<bank>.7** Privacy statement string: enumerate every endpoint the plugin calls.
- [ ] **B.psd2-<bank>.8** Robolectric tests: per-bank fixture round-trip (synthetic API JSON → `NormalizedTransaction`), consent URL builder, callback parser.
- [ ] **B.psd2-<bank>.9** Smoke test against the bank's sandbox / live developer account via the AVD (per CLAUDE.md UI verification rule).

### Per-bank verification verdicts (B.psd2-<bank>.1)

DE / CONTINENTAL EU (Berlin Group XS2A):

- [x] **B.psd2-sparkasse.1** Verdict: **CLOSED** — eIDAS QWAC required for both sandbox and prod. <https://www.openbanking.exchange/europe/resources/insights/psd2-xs2a-granting-access-certificates-and-registers/>. Plugin not viable for v1.
- [x] **B.psd2-atruvia.1** (Volksbanken/Raiffeisen) Verdict: **CLOSED** — Atruvia PSD2 1.3 swagger requires eIDAS QWAC. <https://p1.xs2a-doc.atruvia.de/swagger-ui/index.html>. Plugin not viable for v1.
- [x] **B.psd2-dkb.1** Verdict: **CLOSED** — DKB's finAPI-operated XS2A interface requires QWAC. <https://www.dkb.de/fragen-antworten/wie-funktioniert-der-drittanbieter-zugang-gemaess-psd2>. Plugin not viable for v1.
- [x] **B.psd2-commerzbank.1** Verdict: **CLOSED** — "A valid QWAC Certificate for PSD2 is required to access the Sandbox and Berlin Group API." <https://psd2.developer.commerzbank.com/howto>. Plugin not viable for v1.
- [x] **B.psd2-ing.1** Verdict: **CLOSED** — eIDAS certs issued only to organisations; ING confirms Open Banking not available for personal use. <https://developer.ing.com/openbanking/resources/get-started/psd2>. Plugin not viable for v1.
- [x] **B.psd2-db.1** (Deutsche Bank) Verdict: **CLOSED** — db.com gates all XS2A endpoints on QWAC. <https://developer.db.com/products/psd2>. Plugin not viable for v1.
- [x] **B.psd2-n26.1** Verdict: **CLOSED** — N26 PSD2 dedicated interface requires production-CA QWAC + AIS-role certificate. <https://support.n26.com/en-eu/security/open-banking-psd2/psd2-open-banking-for-third-party-providers>. Plugin not viable for v1.
- [x] **B.psd2-comdirect.1** Verdict: **CLOSED** — sandbox accepts ETSI-format test QWAC but prod requires real eIDAS QWAC. <https://xs2a-developer.comdirect.de/content/howto/connection>. Plugin not viable for v1.
- [x] **B.psd2-postbank.1** Verdict: **CLOSED** — Deutsche-Bank-Group portal posture, same as Deutsche Bank. Plugin not viable for v1.
- [x] **B.psd2-erste.1** Verdict: **CLOSED** — Berlin Group XS2A, AISP-gated. <https://developers.erstegroup.com/>. Plugin not viable for v1.
- [x] **B.psd2-rbi.1** Verdict: **CLOSED** — Berlin Group XS2A, AISP-gated. <https://developer.rbinternational.com/>. Plugin not viable for v1.

UK (Open Banking 3.1):

- [x] **B.psd2-hsbc.1** Verdict: **CLOSED** — Open Banking Directory enrollment required; test sandbox only. <https://develop.hsbc.com/knowledge-article/get-started-open-banking-apis>. Plugin not viable for v1.
- [x] **B.psd2-barclays.1** Verdict: **CLOSED** — FCA AISP/PISP permission required. <https://developer.barclays.com/open-banking>. Plugin not viable for v1.
- [x] **B.psd2-lloyds.1** Verdict: **CLOSED** — Open Banking Directory company-level registration required for Read-Write APIs. <https://developer.lloydsbanking.com/prod01/lbg/get-started>. Plugin not viable for v1.

Pan-EU / Neo-banks (non-PSD2 own-API paths):

- [x] **B.psd2-bunq.1** Verdict: **OPEN** — "You can use the bunq API to manage your own accounts without a license—a license is only needed if you use the API to provide services to third parties." Requires bunq Pro or Elite paid plan. API key + OAuth flows both available. <https://doc.bunq.com/basics/authentication/api-keys>, <https://doc.bunq.com/basics/authentication/oauth>, <https://help.bunq.com/articles/bunq-developer-create-and-share-your-apps>. **v1 candidate #1.**
- [x] **B.psd2-monzo.1** Verdict: **OPEN** — "The Monzo Developer API ... You may only connect to your own account or those of a small set of users you explicitly allow." OAuth 2.0, no AISP requirement. <https://docs.monzo.com/>, <https://developers.monzo.com/>. **v1 candidate #2.**
- [x] **B.psd2-starling.1** Verdict: **OPEN** — Personal Access Tokens scoped `account:read`, `balance:read`, `transaction:read`, `space:read`; account linked via QR-code scan in the Starling app. <https://developer.starlingbank.com/get-started>, <https://developer.starlingbank.com/permissions>, <https://starlingbank.github.io/starling-developer-sdk/examples/authorization>. **v1.5 candidate.**
- [x] **B.psd2-revolut.1** Verdict: **CLOSED** — no developer Personal API for individuals; developer.revolut.com only serves Revolut Business with certificate-based JWT auth. <https://developer.revolut.com/docs/business/business-api>. Plugin not viable for v1.
- [x] **B.psd2-wise.1** Verdict: **CONDITIONAL** — personal tokens exist but balance / statement endpoints are restricted to US / CA / AU / NZ / SG / MY accounts; the AIS surface ledgerboy needs is closed for EU/UK Wise customers. <https://docs.wise.com/guides/developer/auth-and-security/personal-api-token>. Plugin viable only for users with non-EU/UK Wise accounts; deprioritise indefinitely.

### B.psd2 — v1 ship list (concrete picks, post-verification)

- [ ] **B.psd2.v1a** First plugin: **bunq** (broadest EUR/SEPA footprint in the OPEN set; covers DE-resident users who maintain a bunq account; paid-tier paywall acknowledged in plugin `ConfigScreen()` description).
- [ ] **B.psd2.v1b** Second plugin: **Monzo** (UK breadth; proves the toolkit works across two divergent OAuth API shapes).
- [ ] **B.psd2.v1c** Third plugin (post-v1, "v1.5"): **Starling** (second UK option; Personal Access Token model exercises a non-OAuth auth path).

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
