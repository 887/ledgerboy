# ledgerboy — asset: cryptocurrencies

## Status: REWRITTEN 2026-05-24 — CoinGecko / CoinMarketCap / CryptoCompare / Messari all OUT (paid-SaaS pricing intermediaries rejected per the "direct-to-source-of-truth only" lock); pivot to **per-exchange plugins** + **on-chain RPC plugins** + **manual** + **CSV import**, all disabled by default per [`connector-plugins.md`](connector-plugins.md)

Per-class decision file spawned from [`asset-research.md`](asset-research.md).
Covers **crypto holdings** as a distinct asset class. Securities live in
[`asset-securities.md`](asset-securities.md); FX rates live in the
banking-research cluster.

## What it is

A *crypto holding* in ledgerboy is a quantity of a specific
cryptocurrency the user owns (in a hardware wallet, hot wallet, or on
an exchange) and wants to count in their net worth. The user enters a
holding (symbol + quantity + custody label); the source of price /
balance data depends on where the asset lives:

- **On an exchange:** a per-exchange plugin (if enabled and
  configured) reads the live balance + last-trade price directly from
  the exchange's API.
- **In a self-custodied wallet on a public chain:** an on-chain RPC
  plugin (if enabled and configured) reads the balance at the
  user-supplied address from a user-supplied node URL; price comes
  from the exchange plugin if the same asset is held on an exchange,
  or from manual entry otherwise.
- **Anywhere else (no plugin enabled):** manual entry.
- **Historical CSV from any exchange:** the per-exchange CSV-import
  plugin parses the user's downloaded statement.

The host writes one `ValuationEvent(date, value, source)` per fetch
or import. Crypto is treated as a **distinct asset class**, not as
"a security where the exchange is the venue" — different cadence
(intraday volatility matters more), different identifier (symbol +
network, not symbol + exchange), different custody model.

## Architectural framing

Every crypto source is a `ConnectorPlugin` per
[`connector-plugins.md`](connector-plugins.md):

- Category: `AssetPrice`.
- **Off by default.** A fresh install has zero exchange / RPC plugins
  enabled.
- One plugin per exchange (`kraken`, `coinbase`, `binance`,
  `bitstamp`, …). Each plugin uses the exchange's own REST/WebSocket
  API with the user's **read-only** API key.
- One plugin per chain (`eth-rpc`, `btc-rpc`, `sol-rpc`, …). Each
  plugin speaks JSON-RPC to a user-supplied node URL (the user's own
  node, or a trusted public endpoint they chose).
- Configure flow per plugin asks for the exchange API key + secret
  (read-only scope only) or the RPC endpoint URL + the list of
  watched addresses.
- Test connection performs one read-only balance call.
- Privacy statement names the exact endpoint and what data leaves the
  device.
- License gate: MIT / Apache-2.0 / BSD-2 / BSD-3 / MPL-2.0 only.

Universal fallbacks:

- **Manual entry.** Always available, no plugin needed.
- **CSV import.** Per-exchange CSV plugins under the import category
  (shape inherited from `banking-import.md`).

## Rejected SaaS-aggregator sources (documented so future agents don't re-grep)

The previous round picked CoinGecko Demo + CoinMarketCap Basic as v1
connectors. **Both are rejected** under the no-aggregator lock from
[`connector-plugins.md`](connector-plugins.md). Technical analysis
preserved for the record:

| Source | URL | Why rejected |
| --- | --- | --- |
| **CoinGecko** | <https://www.coingecko.com/en/api/pricing> | Third-party pricing aggregator. Demo plan now requires API key (`x-cg-demo-api-key`); free tier 100 calls/min, 10,000/month per <https://docs.coingecko.com/docs/common-errors-rate-limit>. Aggregates prices across exchanges into a single "spot" feed, but that's still aggregation. **OUT** — violates "direct-to-source-of-truth only." |
| **CoinMarketCap** | <https://coinmarketcap.com/api/pricing/> | Third-party pricing aggregator. Same shape. **OUT.** |
| **CryptoCompare** | <https://min-api.cryptocompare.com/> | Third-party pricing aggregator. **OUT.** |
| **Messari** | <https://messari.io/api> | Third-party pricing aggregator. **OUT.** |
| **Nomics** | (shut down 2023) | Dead. Was an aggregator anyway. **OUT.** |
| **Etherscan** | <https://etherscan.io/apis> | Third-party Ethereum explorer aggregator. Useful, but the on-chain RPC plugin pattern gives the user the same data direct from a node they trust. **OUT** as an aggregator path; on-chain RPC is the chosen substitute. |
| **Blockstream Esplora / Mempool.space** | <https://blockstream.info/api>, <https://mempool.space/docs/api/rest> | Third-party Bitcoin explorer aggregators. Same shape as Etherscan. **OUT** as aggregator path; on-chain RPC plugin (`btc-rpc` against the user's own Bitcoin Core or a chosen public endpoint) is the chosen substitute. |
| **CoinAPI** | <https://www.coinapi.io/> | Third-party pricing aggregator. **OUT.** |

The lock is firm: **no third-party crypto pricing aggregator ships as
a ledgerboy plugin, free or paid.** Prices come from exchanges where
the user actually holds the asset, from on-chain reads via the user's
own node, or from manual entry. That is the architecture.

## Per-exchange plugin candidates

### Kraken — **adopt as v1 reference plugin**

- **Plugin id:** `kraken`.
- **What:** US-licensed exchange with EU operations. Long-lived,
  conservative, well-documented public API.
- **API:** REST + WebSocket at `https://api.kraken.com/` and
  `wss://ws.kraken.com/` . Docs:
  <https://docs.kraken.com/rest/> .
- **Auth:** HMAC-SHA512 query signing with a user-issued API key +
  secret. **Read-only scope** is supported (the key permissions can
  be restricted to query-funds + query-trades, no order placement).
- **Configure flow:** the user enters their API key + secret. The
  plugin's Configure screen explicitly instructs the user to create
  the key with **only** the `Query Funds` + `Query Open Orders &
  Trades` + `Query Closed Orders & Trades` permissions enabled (no
  trading, no withdraw). Credentials stored encrypted via the host's
  Keystore-wrapping helper.
- **Test connection:** `POST /0/private/Balance` returns the
  account's asset balances; the plugin confirms a single read.
- **Privacy statement:** "Connects to `api.kraken.com` over HTTPS.
  Sends your API key (read-only-scoped) and a per-request signature.
  Receives your account balances and most-recent trade prices for
  assets you hold. No data goes anywhere except Kraken."
- **ToS posture:** clean. Kraken supports personal-use API clients.
- **Price source:** for any asset the user holds on Kraken, the
  exchange's own ticker (`GET /0/public/Ticker`) gives the
  last-trade price — no aggregator needed.

### Coinbase — **adopt as v1.x plugin**

- **Plugin id:** `coinbase`.
- **What:** US's largest exchange.
- **API:** the modern **Coinbase Advanced Trade API** at
  `https://api.coinbase.com/api/v3/brokerage/` plus the older
  retail Coinbase API at `https://api.coinbase.com/v2/` (for some
  account-summary endpoints). Docs:
  <https://docs.cdp.coinbase.com/advanced-trade/docs/welcome> .
- **Auth:** the Advanced Trade API uses Ed25519 JWT signing with a
  user-issued API key. **Read-only scope** is supported.
- **Configure flow:** the user pastes their API key (private key in
  PEM form) and the plugin parses + stores it encrypted. Instructions
  on the Configure screen point to Coinbase's API-key creation page
  and emphasise the "View" permission only (no Trade, no Transfer).
- **Test connection:** `GET /api/v3/brokerage/accounts` returns the
  account list; one successful read confirms.
- **ToS posture:** clean.
- **Price source:** Advanced Trade's `GET /api/v3/brokerage/products/{id}/ticker`.

### Binance — **adopt as v1.x plugin (regional ToS caveat)**

- **Plugin id:** `binance`.
- **What:** the largest exchange by volume.
- **API:** REST + WebSocket at `https://api.binance.com/` . Docs:
  <https://developers.binance.com/docs/binance-spot-api-docs> .
  Region-specific variants exist (`api.binance.us` for US users; some
  EU countries route through a regional subdomain).
- **Auth:** HMAC-SHA256 query signing with a user-issued API key +
  secret. **Read-only scope** is supported.
- **Configure flow:** API key + secret + endpoint-base picker
  (`api.binance.com` default; `api.binance.us` for US; per-region
  options for EU). Credentials encrypted.
- **Test connection:** `GET /api/v3/account` with signature returns
  the account's balances.
- **ToS posture:** **regional caveat.** Binance's ToS varies by
  jurisdiction; some regions explicitly restrict API automation for
  non-resident users. The plugin's privacy statement names the
  caveat and recommends the user verify their region's terms.
- **Price source:** `GET /api/v3/ticker/price`.

### Bitstamp — **adopt as Phase B+ plugin**

- **Plugin id:** `bitstamp`.
- **What:** long-running EU-based exchange.
- **API:** REST at `https://www.bitstamp.net/api/v2/` . Docs:
  <https://www.bitstamp.net/api/> .
- **Auth:** HMAC-SHA256 + nonce. Read-only key scope supported.
- **Recommendation:** ship as a Phase B+ plugin once Kraken +
  Coinbase + Binance are validated; the implementation pattern is the
  same. Not v1.

### Smaller / DEX exchanges — **on-chain RPC instead**

Uniswap, Curve, SushiSwap, etc. don't have account-level REST APIs
in the centralised-exchange sense — the user's positions live in
on-chain wallets and AMM LP tokens. The **on-chain RPC plugins** are
the correct path for DeFi positions; the plugin reads the wallet
balance + (optionally) the LP token holdings at user-supplied
addresses.

## On-chain RPC plugin candidates

### Ethereum RPC — **adopt as v1 reference plugin (chain side)**

- **Plugin id:** `eth-rpc`.
- **What:** direct JSON-RPC to a user-supplied Ethereum node URL.
- **Protocol:** standard Ethereum JSON-RPC over HTTPS. Spec:
  <https://ethereum.org/en/developers/docs/apis/json-rpc/> .
- **Configure flow:** the user enters one or more RPC endpoint URLs
  (their own Geth / Erigon / Reth node, or a trusted public endpoint
  they chose like `https://ethereum.publicnode.com/`,
  `https://eth.llamarpc.com/`, or an Infura / Alchemy endpoint they
  themselves registered for) plus the list of addresses to watch.
  **No aggregator (Etherscan) is in the path** — the plugin reads
  balances directly via `eth_getBalance` for ETH and `eth_call` for
  ERC-20 `balanceOf`.
- **Test connection:** `eth_blockNumber` against the RPC + one
  `eth_getBalance` against the first watched address.
- **Privacy statement:** "Connects over HTTPS to the RPC endpoint
  you configured. Sends your watched wallet addresses and balance
  queries; receives the on-chain state for those addresses. The RPC
  endpoint operator can see which addresses you're watching — choose
  an endpoint you trust, or run your own node."
- **ToS posture:** clean for any compliant Ethereum RPC.
- **Price source:** there is **no price feed in the RPC plugin.**
  Prices come from an exchange plugin if the same asset is held on an
  exchange, or from manual entry. (A future on-chain DEX-price helper
  is possible but explicitly **out of v1**: reading Uniswap pool
  ratios via `eth_call` is a non-trivial parser and a tail-tokens
  pricing model the v1 architecture deliberately avoids.)
- **Coverage:** ETH + every ERC-20 the user adds (per-asset config
  asks for the token contract address).

### Bitcoin RPC — **adopt as v1 plugin**

- **Plugin id:** `btc-rpc`.
- **What:** direct JSON-RPC to a user-supplied Bitcoin Core node.
- **Protocol:** Bitcoin Core JSON-RPC. Docs:
  <https://developer.bitcoin.org/reference/rpc/> .
- **Configure flow:** RPC endpoint URL + RPC auth (cookie file or
  user:password) + the list of watched addresses or descriptors. For
  users without their own node, document trusted public read-only
  endpoints with the privacy caveat (the operator sees the watched
  addresses).
- **Read path:** `scantxoutset` or — preferred — `importdescriptors`
  + `listunspent` if the node is configured to track the user's
  descriptors. The plugin assumes a watch-only descriptor wallet
  approach; spelling this out in the Configure screen is part of the
  plugin's UX.
- **Test connection:** `getblockcount` + one balance query.
- **Privacy statement:** same shape as `eth-rpc`.
- **Price source:** exchange plugin or manual entry; no on-chain
  price feed.

### Solana RPC — **adopt as Phase B+ plugin**

- **Plugin id:** `sol-rpc`.
- **Protocol:** Solana JSON-RPC. Docs:
  <https://solana.com/docs/rpc> .
- **Configure flow:** RPC endpoint URL + watched addresses.
- **Recommendation:** Phase B+. Same pattern as `eth-rpc`.

### Other chains — **per-chain Phase B+ plugins**

Each additional chain (Polygon, Arbitrum, Optimism, Avalanche, BSC,
Cardano, etc.) is its own plugin if and when the user needs it.
The pattern is uniform: one plugin per JSON-RPC protocol, user
supplies the endpoint, user supplies the watched addresses.

## Manual + CSV import — universal fallbacks

### Manual entry

The user enters:

1. **Symbol** (e.g. `BTC`, `ETH`, `SOL`)
2. **Quantity** (8-decimal precision long — BTC's smallest unit is
   the satoshi, 10⁻⁸ BTC; ETH's smallest unit is wei, 10⁻¹⁸ ETH, but
   8 decimals is the universal storage floor)
3. **Quote currency** (USD or EUR by default; user-configurable)
4. **Current price** (manual entry — the user types whatever they
   read off their exchange or block explorer)
5. **Custody label** (free text: "Ledger Nano X", "Trezor",
   "Coinbase", "Kraken" — informational only)

Each manual revaluation writes one `ValuationEvent(source="manual")`.
Available with **zero plugins enabled**.

### CSV import (per-exchange dialect plugins)

Every exchange exports transaction history as CSV. The CSV import
plugins live under the `Import` category from
[`connector-plugins.md`](connector-plugins.md), one plugin per
exchange dialect:

- `kraken-csv` — Kraken's "Ledgers" CSV.
- `coinbase-csv` — Coinbase's transaction CSV.
- `binance-csv` — Binance's account statement CSV.
- `bitstamp-csv` — Bitstamp transaction CSV.
- (Plus per-tax-software CSVs — Koinly / CoinTracker exports — as
  Phase B+ plugins for users who already aggregate elsewhere and want
  to import the aggregate.)

Each plugin is **disabled by default**. Configure flow is a one-time
column-mapping confirmation; import via SAF picker; each row writes
either a `ValuationEvent(source="csv:<exchange>")` (for price/balance
snapshots) or a transaction record (for tax-purpose imports).

Shares scaffolding with [`banking-import.md`](banking-import.md).

## Lookup shape

When a per-exchange plugin is `Active`, the plugin enumerates the
user's holdings on that exchange — no symbol search needed.

When a per-chain RPC plugin is `Active` for a wallet, the plugin
reads the balances at the watched addresses — for ERC-20 tokens, the
user supplies the contract address in the per-asset config.

When using manual entry without any plugin, the user supplies
(symbol, quantity, quote currency, price) directly. **No third-party
symbol-search service is called**, because every such service
(CoinGecko `/search`, CoinMarketCap, etc.) violates the no-aggregator
lock. The user types the symbol; if they need disambiguation between
two tokens with the same ticker, they distinguish by chain + contract
address (for ERC-20s) or by self-applied label.

The `AssetEntity` row stores `(symbol, chain_nullable,
contract_address_nullable, custody_label, source_plugin_id_nullable)`.

## Revaluation cadence

- **Automatic refresh:** **1 hour TTL** for exchange-plugin-held
  assets (crypto is volatile enough that daily is too coarse).
  Configurable per-plugin (1h / 6h / 24h). The `PluginScheduler`
  respects per-plugin background-sync opt-in.
- **For on-chain RPC plugins:** balance changes are event-driven by
  on-chain transactions, not by time. Recommended cadence: 6h or
  24h (the user's balance doesn't change unless they transact). The
  Configure screen documents this — the user is paying for RPC
  bandwidth (or wearing trust in a public endpoint), so frequent
  polling is wasteful.
- **Manual refresh:** "Refresh prices" forces a fetch on every
  `Active` plugin.
- **No automatic refresh for holdings with no `Active` plugin.** They
  stay at the last manually-entered value.
- **24/7 markets:** crypto markets never close. No holiday calendar.

## Fit recommendation

- **v1 reference plugin (exchange side):** `kraken`. Disabled by
  default. Configure asks for read-only API key + secret.
- **v1 reference plugin (chain side):** `eth-rpc` or `btc-rpc` (pick
  one for the user's primary holding). Disabled by default. Configure
  asks for RPC endpoint URL + watched addresses.
- **v1.x plugins:** `coinbase` (Advanced Trade), `binance` (with
  regional ToS caveat), the other on-chain RPC plugin from the
  v1-reference pair.
- **Universal v1 fallback:** **manual entry** (no plugin needed).
- **Universal v1 fallback:** **CSV import** via per-exchange dialect
  plugins, disabled by default.
- **Phase B+ plugins:** `bitstamp`, `sol-rpc`, `polygon-rpc`,
  `arbitrum-rpc`, `optimism-rpc`, additional exchanges — each small.
  Built on the Phase X host runtime.
- **Rejected (documented above):** CoinGecko, CoinMarketCap,
  CryptoCompare, Messari, Nomics, Etherscan, Blockstream / Mempool
  explorers, CoinAPI. No third-party crypto pricing aggregator ships,
  ever.

## Cross-cutting

### Plugin-internal `CryptoSource` shape

Each per-exchange and per-chain plugin implements `ConnectorPlugin`
(the host-visible surface from
[`connector-plugins.md`](connector-plugins.md)) and internally
exposes a narrow shape for its own work. The host only sees
`ConnectorPlugin`.

```kotlin
// Internal to exchange plugins.
interface ExchangeSource {
    val id: String                  // "kraken", "coinbase", "binance"
    val attribution: AttributionLine
    val ttl: Duration               // 1.hours default

    suspend fun readBalances(): Result<List<ExchangePosition>>
    suspend fun readPrices(symbols: List<String>, quote: Currency): Result<Map<String, Money>>
}

// Internal to on-chain RPC plugins.
interface ChainRpcSource {
    val id: String                  // "eth-rpc", "btc-rpc", "sol-rpc"
    val chain: Chain                // ETHEREUM, BITCOIN, SOLANA, ...
    val attribution: AttributionLine
    val ttl: Duration               // 6.hours default

    suspend fun readBalances(addresses: List<String>): Result<List<OnChainBalance>>
    // No fetchPrice — RPC plugins don't price.
}

data class ExchangePosition(val symbol: String, val quantity: Quantity, val lastPrice: Money?)
data class OnChainBalance(val address: String, val symbol: String, val quantity: Quantity, val contractAddress: String? = null)
```

Each plugin lives in `com.eight87.ledgerboy.plugins.<plugin-id>/`.
Adding an exchange or chain = new plugin module + new manifest entry;
host code unchanged (SOLID Open/Closed).

### Caching

Every read writes one or more `ValuationEvent` rows. Append-only.
For RPC plugins where a fetch reports the same balance as the
previous read (no on-chain activity), the plugin **still** writes a
`ValuationEvent` with the new timestamp so the history line stays
contiguous.

### Rate-limit discipline

Each plugin owns its own rate-limit policy (exchange APIs have
per-key quotas; RPC endpoints have either node-operator-set quotas or
no quota for self-hosted nodes). Spacing lives inside each plugin;
the host's `PluginNetworkGuard` tracks bytes-in/out / fetch count /
error count uniformly via the Phase X interceptor.

### Attribution surface

Per-plugin attribution rides on **Settings → Plugins → Asset prices**
from Phase X.6. No separate "Data sources" screen. Strings in
`strings.xml` under `plugin_<id>_attribution` /
`plugin_<id>_privacy_statement`.

### Credential storage

Exchange API key + secret pairs and (optional) RPC auth credentials
are stored encrypted via the host's Keystore-wrapping helper, per
[`security-research.md`](security-research.md). Each plugin owns a
private DataStore namespace from Phase X.3. **Read-only scoping is
enforced on the plugin author side**: every exchange plugin's
Configure screen prominently instructs the user to grant only
balance-read permissions when creating the API key; the plugin tests
on first use that the key cannot place an order (by attempting a
known-invalid order and confirming the exchange rejects it as
permission-denied, not as price-out-of-range — done once, cached).

### Money + Quantity shape

Prices arrive as exchange-API JSON numbers
(`{"price": "71234.56"}`). The plugin parses to `Money(long,
currency)` at the boundary. Quantity is a `Quantity` value class
(8-decimal precision long), shared with the securities cluster.
Position value = `unitPrice × quantity / 10⁸`, integer math throughout.
Display conversion to the user's net-worth base currency via the FX
cache.

**JPY edge case:** JPY-quoted exchanges return prices without
decimals. The `Money(_, JPY)` value class respects the per-currency
minor-unit count.

## Decision

- **v1 plugins (built on Phase X):** `kraken` (exchange reference) +
  one of `eth-rpc` / `btc-rpc` (chain reference). Both disabled by
  default.
- **v1.x plugins:** `coinbase` (Advanced Trade), `binance` (regional
  ToS caveat surfaced), the second on-chain RPC plugin from the
  reference pair.
- **Universal v1 fallbacks (no plugins required):** manual entry and
  per-exchange CSV import (CSV plugins disabled by default).
- **No third-party crypto pricing aggregator** — CoinGecko,
  CoinMarketCap, CryptoCompare, Messari, Etherscan, Blockstream
  / Mempool explorers all rejected.
- **Phase B+:** `bitstamp`, `sol-rpc`, additional EVM-chain RPCs,
  additional exchanges.
- **Out of v1, gated for later research:** on-chain price discovery
  from DEX pool ratios (Uniswap, Curve, etc.). Documented in a
  separate research stub when the user wants it.

### `ValuationEvent` write path (exchange plugin)

```
PluginScheduler tick (or user taps "Refresh prices"):
  for each Active exchange plugin:
    positions = plugin.readBalances()
    quote = userBaseCurrency
    prices = plugin.readPrices(positions.map { it.symbol }, quote)
    for each position:
      price = prices[position.symbol] ?: position.lastPrice
      if price != null:
        insert ValuationEvent(date = now(),
                              value = price * position.quantity / 10^8,
                              source = "plugin:${plugin.id}")
  done
```

### `ValuationEvent` write path (on-chain RPC plugin)

```
PluginScheduler tick (or user taps "Refresh prices"):
  for each Active chain RPC plugin:
    balances = plugin.readBalances(plugin.config.watchedAddresses)
    for each balance:
      // RPC plugins don't price; the host looks up the matching
      // exchange-plugin price, or falls back to the last manual price.
      price = priceFromExchangePluginOrManual(balance.symbol, userBaseCurrency)
      if price != null:
        insert ValuationEvent(date = now(),
                              value = price * balance.quantity / 10^8,
                              source = "plugin:${plugin.id}")
      else:
        // No price source; record a balance-only event for history.
        insert ValuationEvent(date = now(),
                              value = null,
                              source = "plugin:${plugin.id}",
                              note = "balance updated, no price source")
  done
```

### `ValuationEvent` write path (CSV import)

```
user invokes "Import exchange statement" via SAF picker:
  pick a CSV-import plugin (must be Active)
  parse rows, write per-position ValuationEvent(source = "csv:${plugin.id}")
```

### `ValuationEvent` write path (manual)

```
user taps "Update price" on a holding:
  type new price → insert ValuationEvent(source = "manual")
```

### Phase J implementation sub-steps

Builds on the **Phase X host runtime** from
[`connector-plugins.md`](connector-plugins.md). Each sub-step below
implements one plugin or one piece of the manual / CSV scaffolding.

- [ ] **J.21** Define the internal `ExchangeSource` interface,
  `ChainRpcSource` interface, `ExchangePosition` / `OnChainBalance`
  data classes, `Chain` sealed enum. All inside the plugins shared
  package. JVM unit tests for `Quantity` arithmetic at the 8-decimal
  boundary.
- [ ] **J.22** Wire crypto into the `AssetEntity` model: new asset
  class enum value `CRYPTO`, new fields for `symbol`, `chain_nullable`,
  `contract_address_nullable`, `quantity` (reuses the `Quantity`
  value class from the securities cluster), `quote_currency`,
  `custody_label`, `source_plugin_id_nullable`. Room additive
  migration.
- [ ] **J.23** Implement the **`kraken` plugin** (v1 exchange
  reference). Plugin id `kraken`, category `AssetPrice`, license
  `MIT`. `ConfigScreen()` asks for read-only API key + secret,
  encrypts them, runs the "key can't trade" check on first use. Test
  connection performs one `Balance` read. `fetch()` reads balances +
  ticker prices. **Disabled by default.**
- [ ] **J.24** Implement the **`eth-rpc` plugin** (v1 chain reference,
  unless the user's primary chain is Bitcoin — then `btc-rpc` ships
  in J.24 instead). Plugin id `eth-rpc`, category `AssetPrice`,
  license `MIT`. `ConfigScreen()` asks for RPC endpoint URL +
  watched addresses + (optional) per-ERC-20-token contract address
  list. Test connection performs `eth_blockNumber` + one
  `eth_getBalance`. `fetch()` reads ETH + ERC-20 balances at the
  watched addresses (no pricing). **Disabled by default.**
- [ ] **J.25** UI: Assets screen "Add crypto" entry. Form asks for
  symbol + quantity + quote currency + (optional) chain + (optional)
  contract address + custody label + (optional) source plugin to
  associate. If a source plugin is `Active`, the holding refreshes
  from the plugin; otherwise it's manual.
- [ ] **J.26** Manual-revaluation flow for crypto: "Update price"
  writes `ValuationEvent(source="manual")`. Same shape as the
  manual-class wizard from the real-estate / vehicles / generic files.
- [ ] **J.27** Implement the **`coinbase` plugin** (v1.x exchange).
  Plugin id `coinbase`, category `AssetPrice`, license `MIT`.
  `ConfigScreen()` asks for the Advanced Trade Ed25519 JWT key (PEM);
  Configure-screen text emphasises the View-only permission scope.
  Test connection reads accounts. **Disabled by default.**
- [ ] **J.28** Implement the **`btc-rpc` plugin** (v1.x chain).
  Plugin id `btc-rpc`, category `AssetPrice`, license `MIT`.
  `ConfigScreen()` asks for RPC endpoint URL + RPC auth + watched
  addresses or descriptors. Test connection performs `getblockcount`
  + one balance read. **Disabled by default.**
- [ ] **J.29** Implement the **`binance` plugin** (v1.x exchange,
  regional ToS caveat). Plugin id `binance`, category `AssetPrice`,
  license `MIT`. `ConfigScreen()` asks for API key + secret +
  endpoint-base (`api.binance.com` / `api.binance.us` / regional).
  Privacy statement names the regional caveat. **Disabled by default.**
- [ ] **J.30** CSV-import plugins for the v1 exchanges: `kraken-csv`,
  `coinbase-csv`, `binance-csv`. Each in category `Import`,
  disabled by default, with one-time column-mapping Configure.
  Shares the parser harness from `banking-import.md`.
- [ ] **J.31** Phase B+ plugin stubs (file-only, no impl): document
  `bitstamp`, `sol-rpc`, `polygon-rpc`, `arbitrum-rpc`,
  `optimism-rpc`. Each gets one paragraph in a new
  `docs/plans/asset-crypto-exchanges-and-chains.md` index file noting
  current API status + v1 deferral reason.
- [ ] **J.32** Cross-plugin price-lookup helper: when an on-chain RPC
  plugin reports a balance, the host looks up a price via any
  `Active` exchange plugin holding the same symbol, falling back to
  the most recent manual `ValuationEvent` for the holding. JVM unit
  tests for the fallback order. Documented as host-side code, not in
  any plugin — keeps the plugins narrow.

### References

- Plugin architecture authority: [`connector-plugins.md`](connector-plugins.md)
- Kraken REST API docs: <https://docs.kraken.com/rest/>
- Coinbase Advanced Trade API: <https://docs.cdp.coinbase.com/advanced-trade/docs/welcome>
- Binance Spot API: <https://developers.binance.com/docs/binance-spot-api-docs>
- Bitstamp API: <https://www.bitstamp.net/api/>
- Ethereum JSON-RPC spec: <https://ethereum.org/en/developers/docs/apis/json-rpc/>
- Bitcoin Core JSON-RPC: <https://developer.bitcoin.org/reference/rpc/>
- Solana JSON-RPC: <https://solana.com/docs/rpc>
- Public Ethereum RPC examples (for user-choice documentation): <https://ethereum.publicnode.com/>, <https://eth.llamarpc.com/>
- Rejected SaaS sources (for the record): <https://www.coingecko.com/en/api/pricing>, <https://coinmarketcap.com/api/pricing/>, <https://min-api.cryptocompare.com/>, <https://messari.io/api>, <https://etherscan.io/apis>, <https://blockstream.info/api>, <https://mempool.space/docs/api/rest>, <https://www.coinapi.io/>
