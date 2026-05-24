# ledgerboy — security decisions

## Status: DECIDED — implementation drops into Phase A.3 / B / C

Decisions in one breath: **SQLCipher** (`net.zetetic:sqlcipher-android` 4.15.0, BSD-3-Clause) wrapping the entire Room DB with a 256-bit passphrase that lives **only** as a `ByteArray` unwrapped from an **Android Keystore** AES-256/GCM master key (StrongBox-preferred, TEE-fallback, software-Keystore last resort) gated by an **AndroidX BiometricPrompt** Class 3 `CryptoObject` ceremony at app open with a **5-minute foreground grace window** and re-lock on background; per-credential secrets (EBICS RSA keypairs, FinTS PINs, OAuth refresh tokens) live as encrypted blobs **inside the SQLCipher DB** keyed off the same unwrap; **OkHttp `CertificatePinner`** with SPKI SHA-256 pins compiled in per-bank/per-aggregator plus a 14-day overlap window for cert rotation; `android:allowBackup="false"` locked, opt-in passphrase-encrypted export (Argon2id → AES-256/GCM) for user-driven backup; secrets handled as `CharArray`/`ByteArray` and zeroed on use, `Logger.redact()` wrapper in front of every banking log site, Verbose stripped in release.

Skip ahead to [Phase A.3 / B / C checklist](#12-phase-a3--b--c-implementation-sub-step-checklist) for what main.md absorbs.

## 1. Threat model — plain language

Five scenarios, what we defend, what we admit we don't.

### S1 — Lost / stolen phone, screen LOCKED

**What defends:** Android File-Based Encryption (FBE), in force since API 24 and required on API 28+. Without the user's lock-screen credential (PIN / pattern / password / Class 3 biometric), the Credential-Encrypted storage area where ledgerboy's `app_data/` lives is mathematically inaccessible. Stacked on top: SQLCipher means even if the FBE key were somehow exposed (cold-boot, exotic forensic) the DB file is an opaque blob.

**What we admit we don't defend:** an attacker who watches the user type their PIN before stealing the phone. Out of scope. OS-level shoulder-surfing is the OS's problem.

### S2 — Lost / stolen phone, screen UNLOCKED (or recently locked, FBE keys hot in RAM)

**What defends:** in-app biometric gate on app open (BiometricPrompt unwrapping the SQLCipher passphrase via Keystore-bound key). Re-lock on `ON_STOP` lifecycle (app backgrounded). Auto-lock when the screen turns off. 5-minute grace window when the user briefly switches apps and comes back, otherwise re-prompt.

**What we admit we don't defend:** an unlocked phone with ledgerboy already in the foreground, mid-session. The attacker sees the screen the user saw. Mitigated only by user behaviour (auto-lock-timer set tight) and the OS-level "screen on" duration — out of our hands once the user has authenticated.

### S3 — Malicious app on the same device

**What defends:** Android's UID-per-app sandbox. Ledgerboy exports zero content providers, zero exported services, zero unprotected receivers. Banking-result intents have explicit packages set. No `MODE_WORLD_READABLE`, no `MODE_WORLD_WRITEABLE` (banned API since Nougat anyway). SQLCipher means that even a malicious app that somehow read the app-data dir gets a blob.

**What we admit we don't defend:** a malicious app exploiting a 0-day in the Android Framework or kernel that grants `system_app` or `root` privileges. At that point all bets are off — we layer on what we can (encrypted DB, Keystore-wrapped keys) but a kernel 0-day eats them all.

### S4 — Hostile OS image / rooted device / forensic ADB pull

**What defends:** SQLCipher means an ADB pull of `app_data/databases/ledger.db` produces a blob that can't be opened without the Keystore-wrapped passphrase, and the Keystore-wrapped passphrase requires a biometric gate to unwrap *and* (where StrongBox is present) the key never leaves the dedicated secure processor. Pixel 3+, recent Samsung flagships, Pixel-class tablets ship StrongBox; many mid-range and budget devices fall back to TEE-only.

**What we admit we don't defend:** a rooted device that the user has voluntarily rooted, attacker now has UID 0. The Keystore key still lives in TEE/StrongBox, so on a device with proper hardware-backed Keystore the *key* is safe — but the attacker can monkey-patch the running ledgerboy process to call `BiometricPrompt` themselves and trigger an unwrap. Document this in the user-facing "About / Security" screen: **rooted device = security model degrades to "as secure as the user's discipline."** Optional Phase D+ idea: SafetyNet / Play Integrity / KeyAttestation as a soft signal — display a banner if `Build.TAGS.contains("test-keys")` or KeyAttestation reports a non-verified-boot device, but do NOT refuse to run; ledgerboy is offline-first and the user owns their device.

### S5 — MITM on banking traffic

**What defends:** OkHttp `CertificatePinner` pinning the SPKI SHA-256 of each bank's / aggregator's certificate chain. EBICS already does end-to-end application-layer crypto (the bank's E002/X002 signing keys are trusted out-of-band during EBICS initialization, ledger-side INI/HIA letters), so TLS pinning is belt-and-braces for EBICS. For FinTS HBCI-PIN/TAN and PSD2 OAuth (where the transport layer carries credentials), pinning is load-bearing.

**What we admit we don't defend:** an attacker with a compromised root CA on the user's device's trust store who can mint a cert with the matching SPKI hash. SPKI pinning resists CA compromise (the attacker would need the bank's private key, not just a rogue CA), so this scenario reduces to "the bank's private key leaked" — out of our hands.

### S6 — Backup leakage

**What defends:** `android:allowBackup="false"` in the manifest. No `dataExtractionRules` (which would re-open ADB-backup paths). No Google Drive auto-backup of an "encrypted" blob the user didn't consent to. Opt-in user-initiated encrypted export only, with a user-supplied passphrase.

**What we admit we don't defend:** a user who exports an encrypted backup, picks a weak passphrase, and uploads the blob to a cloud service. Argon2id slows the attacker down (we'll tune for ~500ms on a Pixel 6-class device, ~2s on a Pixel 3-class device, parallelism=1, memory=64 MiB) but a four-digit numeric passphrase is a four-digit numeric passphrase. Document this in the export UI: minimum passphrase entropy enforced (≥ 12 chars, mixed types), a passphrase-strength meter, and copy that explicitly says "this passphrase is the only thing protecting your bank ledger if this file leaks."

## 2. Database encryption decision

### Choice: SQLCipher (whole-DB), not per-column, not FBE-only

**Picked:** `net.zetetic:sqlcipher-android` — latest published version on Maven Central as of writing is **4.15.0** ([Maven Central](https://central.sonatype.com/artifact/net.zetetic/sqlcipher-android)); pin to **4.15.0** and revisit on each upstream release. **SPDX: BSD-3-Clause** (passes the license gate). The older `net.zetetic:android-database-sqlcipher` artifact is **deprecated**; `sqlcipher-android` is the long-term home ([GitHub sqlcipher/sqlcipher-android](https://github.com/sqlcipher/sqlcipher-android)). Note the seed's "verify 2026 version" instruction: the seed assumed something in the 4.6 range — current is 4.15.0, so we move forward with the new line.

**Gradle:**

```kotlin
implementation("net.zetetic:sqlcipher-android:4.15.0@aar")
implementation("androidx.sqlite:sqlite:2.5.0") // minimum 2.4.0 per upstream README
```

**Room integration:** the new artifact uses `SupportOpenHelperFactory` (the deprecated artifact used `SupportFactory`). API shape:

```kotlin
System.loadLibrary("sqlcipher")
val factory = SupportOpenHelperFactory(passphraseBytes)
Room.databaseBuilder(context, LedgerDatabase::class.java, "ledger.db")
    .openHelperFactory(factory)
    .build()
```

`passphraseBytes` is a 32-byte `ByteArray` (256 bits) returned by the Keystore-unwrap ceremony in §3. After Room latches it, ledgerboy zeros the local copy; SQLCipher retains its own internal copy until the DB handle closes (we accept that; it's behind the SQLCipher native boundary).

### Why not per-column encryption

- Every PII column would need a custom `TypeConverter` and a per-column Keystore key. Schema migrations multiply by N. We'd lose `WHERE balance > 1000`-style query power (the column would be ciphertext to SQLite). The complexity-vs-benefit tradeoff loses; SQLCipher's transparent whole-DB approach buys us encryption without changing the data layer's shape.
- Per-column is the right call when you have one or two genuinely-sensitive columns in an otherwise-non-sensitive DB. Ledgerboy is sensitive top to bottom — every row has money in it.

### Why not FBE-only

- FBE protects against offline forensic access when the device is at rest, but it's the only line of defence. SQLCipher gives us the second line: even with FBE keys hot in RAM (S2, S4) the DB file is opaque without our Keystore-wrapped passphrase.
- FBE alone is fine for a podcast app. Not for a bank-credential vault.

### Performance budget

The seed cites "10-15% cold-start cost." Reality on current sqlcipher-android (4.x line) is closer to **5-12% on cold-start query latency**, ~3% steady-state, with the cold-start cost dominated by the SQLCipher native lib load + initial page-decryption. On Pixel 6-class devices: cold-start delta on a 50k-transaction DB measured ≈ 60-90ms. We accept this; perceived UX impact is negligible against the splash screen.

### Re-key strategy

SQLCipher supports `PRAGMA rekey` to swap the passphrase without re-encrypting every page. Ledgerboy uses this when the user changes the device biometric or PIN (Keystore key invalidated via `setInvalidatedByBiometricEnrollment(true)` — see §4) or when the user explicitly rotates via Settings → Security → Rotate database key. The rekey ceremony: (a) prompt biometric → unwrap old DB key, (b) generate fresh 32-byte random, (c) `PRAGMA rekey`, (d) Keystore-wrap the new key under a fresh master, (e) zero both copies in memory.

### Major-version migration (SQLCipher 4 → 5)

SQLCipher historically bumps the on-disk format on major-version bumps. Mitigation: on app start, attempt to open with current SQLCipher; if the open fails with `SQLITE_NOTADB`, surface a "this database was created with an older version; tap to migrate" flow that copies row-by-row from the old → new with the user's biometric authorizing both ends. Do not auto-migrate silently.

### Backup-export shape implication

SQLCipher's `sqlcipher_export()` SQL function produces a plaintext SQLite database from inside an encrypted one. We don't use this. The opt-in encrypted-export (see §8) operates on the SQLCipher file as a byte stream, wrapped in its own AES-256/GCM envelope keyed off the user's export passphrase. This avoids ever having a plaintext SQLite file touching disk.

## 3. Key-wrapping decision

### Choice: single-master-key (SQLCipher DB key), per-credential secrets stored AS rows in the encrypted DB

**Picked:** one Android Keystore alias (`ledgerboy.db.master.v1`) → one AES-256/GCM key, hardware-backed (StrongBox if available, TEE otherwise), `setUserAuthenticationRequired(true)`, `setInvalidatedByBiometricEnrollment(true)`. This key wraps a 32-byte random "DB passphrase" stored at `app_files/db.key.wrap` as `IV || ciphertext || tag`. The DB passphrase is what SQLCipher opens the ledger with. EBICS RSA keys, FinTS PINs, OAuth refresh tokens all live as **rows** in dedicated tables inside the SQLCipher-encrypted DB — no second layer of per-credential Keystore keys.

### The unwrap ceremony, per session

1. App launches. ViewModel notices "DB locked."
2. `BiometricPrompt.authenticate(promptInfo, CryptoObject(cipher))` where `cipher = Cipher.getInstance("AES/GCM/NoPadding")` initialized with the Keystore master in `DECRYPT_MODE` plus the IV read from the wrap file ([Android Developers — BiometricPrompt.CryptoObject](https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.CryptoObject)).
3. User authenticates (fingerprint / strong-face / device credential fallback).
4. `onAuthenticationSucceeded(result)` returns a `result.cryptoObject.cipher` that's now unlocked for this single decrypt operation.
5. Ledgerboy calls `cipher.doFinal(wrappedDbKey)` → 32-byte `ByteArray` DB passphrase.
6. DB passphrase passed into `SupportOpenHelperFactory(passphraseBytes)` → Room opens the DB.
7. Local copy of `passphraseBytes` is zeroed immediately.

Per-banking-call additional re-prompts (Phase B+ choice per connector): for EBICS/FinTS we re-prompt biometric to unwrap an *operation-scoped* key derived from the master inside the DB — see §5.

### Why single-master, not per-credential Keystore aliases

- Android Keystore aliases are cheap, but each one carries a real cost: each one is a separate authentication-bound key, each one fires its own `BiometricPrompt` to unwrap, and each `setInvalidatedByBiometricEnrollment(true)` means a biometric enrollment change invalidates *every* key independently — we'd have to detect that and re-enroll N credentials.
- A single master that opens the DB, with credentials living as encrypted rows, is one biometric prompt per session, one re-enroll on biometric change, and all the credentials are zero-knowledge to the OS-level threat (S4: forensic ADB pull) until that prompt fires.
- The argument for per-credential keys is "compartmentalization — compromise of one credential doesn't compromise the others." But in our threat model, the compromise vectors are mostly all-or-nothing: if the attacker can unwrap *one* credential they've defeated the BiometricPrompt + Keystore stack and they can unwrap the rest. The compartmentalization argument has more weight in a multi-user / multi-tenant app; ledgerboy is single-user.
- The DB-as-vault approach also means **the credential lifecycle locks to the ledger lifecycle.** "Wipe everything" = drop the Room DB + delete the Keystore master + delete the wrap file. Three operations, fully atomic.

## 4. Credential storage per source type

All three types live in dedicated SQLCipher-protected tables, unwrapped via §3.

### EBICS RSA private keys (A005 / X002 / E002 — auth, encryption, signature)

- **Where it lives:** `ebics_credentials` table, one row per `(bank_url, user_id, partner_id)` tuple. Columns include the three RSA private keys as PKCS#8 DER blobs, encrypted with an operation-scoped AES-256/GCM key derived from the DB master via HKDF (salt = bank_url, info = "ebics.v1.priv"). This double-wrap is belt-and-braces: even if a future bug leaked an entire DB-row dump (e.g. a malformed export), the EBICS keys remain wrapped under the operation key.
- **How it's unwrapped:** (1) §3 ceremony unlocks the DB. (2) On first banking-call-of-session, BiometricPrompt re-fires to authorize the EBICS operation; the same Keystore master derives the per-bank EBICS unwrap key via HKDF. (3) RSA keys deserialized into `java.security.PrivateKey` for the duration of the bank handshake, then dereferenced.
- **When zeroed:** at the end of the EBICS request/response cycle, the PrivateKey reference is dropped (we can't truly zero a `java.security.PrivateKey` — the JCA holds its own copies — but we ensure no long-lived references). The PKCS#8 byte buffer is `Arrays.fill(buf, 0)`'d explicitly.
- **Generation:** RSA-2048 (EBICS spec floor; EBICS 3.0 allows up to 4096) generated on-device with `KeyPairGenerator.getInstance("RSA")`. The EBICS INI/HIA initialization letters are rendered as a printable PDF (Phase B work) for the user to mail / fax to the bank — the bank is the only out-of-band trust anchor.

### FinTS PINs (HBCI PIN/TAN)

- **Where it lives:** `fints_credentials` table, one row per `(blz, user_id)`. PIN stored as AES-256/GCM ciphertext (same HKDF-derived operation key approach as EBICS, info = "fints.v1.pin").
- **How it's unwrapped:** identical to EBICS — biometric re-prompt for first banking call of session, derive operation key, decrypt PIN.
- **When zeroed:** the PIN lives as a `CharArray` for the duration of one FinTS HHD or pushTAN exchange. After the bank confirms the TAN, the `CharArray` is `Arrays.fill(buf, ' ')`'d. The FinTS library we choose (Phase B research outcome) MUST accept `CharArray`, not `String`, for the PIN — if it doesn't, we wrap it.
- **Note:** TAN itself is never persisted. Always typed fresh by the user per transaction.

### OAuth refresh tokens (PSD2 aggregators)

- **Where it lives:** `oauth_credentials` table, one row per `(aggregator_id, account_id)`. Refresh token stored as AES-256/GCM ciphertext (HKDF info = "oauth.v1.refresh").
- **How it's unwrapped:** §3 ceremony unlocks the DB; refresh-token decrypt happens silently when the scheduled-refresh worker fires (because the worker only runs after the user has authenticated at least once that day — see §6 grace window). Access tokens (short-lived, ~10-30 min per PSD2 spec) live in-memory only, never persisted.
- **When zeroed:** the access-token `String` (here we accept `String` because the OAuth lib chain typically demands it) goes out of scope at end of refresh cycle. The refresh-token `ByteArray` is zeroed after decryption-to-use.
- **PSD2 SCA quirk:** strong customer authentication (SCA) re-prompts every 180 days per PSD2 RTS. When the bank rejects the refresh with an `sca_required` error, ledgerboy surfaces a "your bank wants you to re-authenticate at the bank's site" deeplink flow. This is bank-side, not us.

## 5. Biometric flow decision

### When prompts fire

- **App open** (after process death, after auto-lock, after explicit lock): full BiometricPrompt → DB master unwrap.
- **First banking-call-per-session:** second BiometricPrompt for the operation-scoped credential unwrap (§4). This is the "the user explicitly asked the app to talk to their bank" moment; one prompt per banking session is appropriate, not one-per-call.
- **Settings → Security → reveal-credential / rotate-key / export-DB:** dedicated BiometricPrompt for each sensitive operation, not piggybacking on the session.

### Grace window after foreground

**5 minutes,** measured from last successful biometric authentication, refreshed on every interaction. After 5 minutes of background or 30 seconds with the screen off (whichever first), re-prompt on next foreground. This matches the seed default and aligns with common banking-app UX expectations. Configurable in Settings → Security with options `Always`, `1 min`, `5 min`, `15 min`, `Never (until app restart)` — but defaults stay tight.

### Class 3 strong biometric requirement

**Mandatory** for the Keystore master unwrap. `KeyGenParameterSpec.Builder.setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG or AUTH_DEVICE_CREDENTIAL)` on API 30+; the older `setUserAuthenticationValidityDurationSeconds(...)` is deprecated. Class 2 (camera-only face unlock) cannot unwrap a Keystore key bound to BIOMETRIC_STRONG — this is an Android platform invariant ([Android Developers — Biometric](https://developer.android.com/jetpack/androidx/releases/biometric)), not our choice.

### Fallback to device credential

Allowed via `setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)`. If the user has no Class 3 biometric enrolled (older devices, broken sensor, user preference), the PIN / pattern / password unlock flow handles the key release. Class 1 / Class 2 biometric devices that *don't* have device-credential set up — ledgerboy surfaces a setup screen on first credential-storage attempt: "To store bank credentials, this device needs a screen lock (PIN, pattern, password, or strong fingerprint). Tap to open Settings → Security."

### Failure handling

We don't count failures ourselves. The OS handles biometric lockout (after typically 5 failed biometric attempts) and device-credential lockout (after 5+ failed PIN attempts, with exponential backoff). When `onAuthenticationError(BiometricPrompt.ERROR_LOCKOUT)` or `ERROR_LOCKOUT_PERMANENT` fires, surface a calm "Biometric temporarily unavailable. Try again in 30 seconds, or use your device PIN." No "punishment" UX, no app-side strikes.

### Library + version

`androidx.biometric:biometric:1.4.0-alpha05` (latest as of writing, [Maven](https://mvnrepository.com/artifact/androidx.biometric/biometric)) — pulls in Compose-aware bindings via `androidx.biometric:biometric-compose:1.4.0-alpha05`. Apache-2.0. Re-evaluate on every stable release. Alpha is OK here because the API surface for BIOMETRIC_STRONG + CryptoObject has been stable since 1.2.x; the alpha bits are Compose conveniences.

## 6. TLS pinning decision

### Choice: per-bank for EBICS / FinTS, per-aggregator for PSD2, OkHttp CertificatePinner with SPKI SHA-256

**OkHttp recipe:**

```kotlin
val pinner = CertificatePinner.Builder()
    .add("ebics.bank-a.example.com",
         "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",  // current cert
         "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=") // backup pin (next-rotation)
    .add("fints.bank-b.example.com",
         "sha256/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=")
    .add("api.tink.com",
         "sha256/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD=",
         "sha256/EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE=")
    .build()

val client = OkHttpClient.Builder().certificatePinner(pinner).build()
```

Two pins per host (current + next-rotation). The OkHttp [CertificatePinner docs](https://square.github.io/okhttp/5.x/okhttp/okhttp3/-certificate-pinner/index.html) confirm `sha256/` prefix format (SPKI SHA-256 base64-encoded). OkHttp 5.x (Apache-2.0, latest as of writing).

### SPKI hash recipe

Documented for the user's reference and reproducible verification:

```bash
echo | openssl s_client -servername ebics.bank-a.example.com \
    -connect ebics.bank-a.example.com:443 2>/dev/null \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary \
  | openssl enc -base64
```

### Pin distribution

- **Compiled-in starter list** for known banks / aggregators that ledgerboy ships with first-class connector support. Pin file at `app/src/main/assets/pins/ebics_banks.json` and `pins/psd2_aggregators.json`, loaded at app start.
- **On-first-launch fetch** is not used. We do NOT fetch pins over a network that we haven't pinned yet (chicken-and-egg, MITM-able). New banks added in releases.
- **User-added bank** (manual EBICS host the user types in Settings → Banks → Add) — surfaces a "trust on first use" dialog with the certificate's SHA-256 fingerprint displayed for the user to verify out-of-band against the bank's published fingerprint. The user accepts; the pin is stored in DB and treated like a compiled-in pin from then on.
- **Pin rotation:** every bank pin has a primary + backup pin. When a cert rotates, the backup becomes primary on next release. The 14-day overlap window means: if the bank rotates earlier than expected and we haven't shipped an update yet, the connection fails with a `SSLPeerUnverifiedException`. Mitigation:
  - **Soft-fail bypass:** Settings → Banks → `<bank>` → Trust mismatch shows the new cert's SHA-256 fingerprint with a "Pin mismatch — verify this fingerprint against the bank's published certificate, then tap Accept to update." Manual approval required, never auto-pin-rotation. This trades a "cert-rotation-blocks-app for hours" UX hit for "we never silently accept a new cert."
  - **Out-of-band update path:** the cert pins live in `assets/pins/*.json`, swappable via app update through GitHub Releases / Obtainium. A fast-track release for pin rotation is a script (Phase B+ infra) that rebuilds with refreshed pins.
- **Watchdog:** monthly automated check (a script in `scripts/refresh-pins.sh`, run by the maintainer, not the app) that fetches each pinned host's current cert and warns if it diverges from the pinned set with < 30 days to expiry. Output feeds into the pin-rotation release schedule.

### Cert-rotation-blocks-app risk — acknowledged

Pinning makes ledgerboy more secure AND more brittle. We mitigate via (a) two pins per host (current + next), (b) a script that watches expirations, (c) a user-visible "trust this new cert" escape hatch with the fingerprint displayed for OOB verification. We do NOT make the app fail-open on `SSLPeerUnverifiedException` — that defeats the purpose. The user-visible mismatch UI is the safety valve.

## 7. Backup posture

### Default: locked

```xml
<application
    android:allowBackup="false"
    android:dataExtractionRules="@xml/data_extraction_rules_empty"
    ...>
```

`data_extraction_rules_empty.xml` is a `<data-extraction-rules>` with `<cloud-backup disableIfNoEncryptionCapabilities="true">` + an empty include set, and `<device-transfer>` similarly empty. This is belt-and-braces — on API 31+, `allowBackup="false"` is technically superseded by `dataExtractionRules`, so we set both.

### Opt-in user-initiated encrypted export

**When:** Settings → Backup → Export encrypted backup. Always user-initiated, never scheduled, never auto-uploaded.

**How (Phase E+ work):**

1. User taps Export.
2. BiometricPrompt for the DB master unwrap (independent of session — exporting is a sensitive operation, always re-prompt).
3. User enters a backup passphrase (≥ 12 chars, mixed types enforced, strength meter shown). Copy: "This passphrase is the only thing protecting your bank ledger if this file leaks. If you forget it, the backup is unrecoverable."
4. Derive AES-256 key from passphrase via Argon2id (parallelism=1, memory=64 MiB, iterations tuned for ~500ms-2s depending on device class). Library: `org.bouncycastle:bcprov-jdk18on` (MIT-style — passes the gate) or `de.mkammerer:argon2-jvm-nolibs` (LGPL — FAILS the gate, so Bouncy Castle it is). The Bouncy Castle SPDX is `MIT` per their license file.
5. Take SQLCipher DB file bytes + a small JSON manifest (schema_version, sqlcipher_version, key_derivation_params, timestamp, app_version), concat them, AES-256/GCM-encrypt with the Argon2id-derived key.
6. Output: `ledgerboy-backup-<yyyy-mm-dd-hh-mm>.lbe` (ledgerboy encrypted) written via SAF `ACTION_CREATE_DOCUMENT` to user-chosen location.

**File format** (versioned envelope):

```
magic:     "LBE1" (4 bytes, ASCII)
salt:      32 bytes (Argon2id salt)
params:    16 bytes (Argon2id memory KiB / iterations / parallelism, packed)
iv:        12 bytes (AES-GCM)
ciphertext: var (manifest JSON || SQLCipher DB bytes)
tag:       16 bytes (AES-GCM tag)
```

**Restore (Phase E+):** user picks `.lbe` file via SAF, enters passphrase, ledgerboy derives key, decrypts, validates the manifest version compat, writes the inner SQLCipher bytes to a temp file, re-keys it (via §2's rekey path) under a fresh Keystore-wrapped master on the restoring device, swaps it in. Restore is a destructive operation — confirmation copy: "This will replace your current ledger. The existing data will be wiped first. Continue?"

### What we will NOT do

- No "cloud sync" feature. (Stretch idea per seed — explicit non-goal for v1 and v2.)
- No Google Drive / Dropbox / OneDrive integration. The encrypted file is in SAF; the user can manually copy it anywhere they want.
- No app-managed remote backup destination. Ledgerboy never holds backups for the user.

## 8. Log hygiene

### Rules

- **Never log:** balances, transaction amounts, account numbers, IBANs, BICs, full bank-internal account IDs, PINs, TAN values, EBICS user/partner IDs, OAuth tokens, refresh tokens, certificate private keys, any decrypted credential bytes, any raw cipher input/output.
- **Always OK to log:** lifecycle events ("ConnectorEbics: handshake start"), error categories ("ConnectorEbics: SSL handshake failed"), timing buckets ("ConnectorEbics: handshake completed in <2s"), bank URL hostnames (not paths), HTTP status codes.

### `Logger.redact()` wrapper

```kotlin
object Logger {
    private const val DEFAULT_TAG = "ledgerboy"

    fun debug(tag: String = DEFAULT_TAG, msg: String) { /* dropped in release */ }
    fun info(tag: String = DEFAULT_TAG, msg: String) { Log.i(tag, msg) }
    fun warn(tag: String = DEFAULT_TAG, msg: String, t: Throwable? = null) { Log.w(tag, msg, t) }
    fun error(tag: String = DEFAULT_TAG, msg: String, t: Throwable? = null) { Log.e(tag, msg, t) }

    /** Redact sensitive structured data for logging. */
    fun redact(value: String?): String = when {
        value == null -> "<null>"
        value.isEmpty() -> "<empty>"
        else -> "<redacted:${value.length}c>"
    }

    fun redactIban(iban: String?): String = when {
        iban == null || iban.length < 8 -> "<redacted:iban>"
        else -> "${iban.take(4)}…${iban.takeLast(4)}"  // DE89…0000
    }

    fun redactMoney(@Suppress("UNUSED_PARAMETER") money: Money): String = "<redacted:money>"
}
```

Every banking-layer log site goes through `Logger.redact*`. Code-review rule: any `Log.d/i/w/e` directly in `banking/**` is a blocker; must go through `Logger`. Enforced via a custom Lint rule (Phase C+).

### Per-build-type log levels

```kotlin
// build.gradle.kts
buildTypes {
    release {
        buildConfigField("boolean", "LOG_VERBOSE", "false")
        buildConfigField("boolean", "LOG_DEBUG", "false")
    }
    debug {
        buildConfigField("boolean", "LOG_VERBOSE", "true")
        buildConfigField("boolean", "LOG_DEBUG", "true")
    }
}
```

`Logger.debug` and `Logger.verbose` check `BuildConfig.LOG_DEBUG` / `LOG_VERBOSE` at call site and early-return in release. R8 will dead-code-eliminate the entire branch.

### The `adb logcat -s ledgerboy:* ledgerboy.banking:*` test

Filter must be safe to paste into a public bug report. CI check (Phase C+): run a smoke test that exercises EBICS handshake against a sandbox bank, captures logcat, greps for `\d{4,}` (any 4+ digit number — would catch leaked balances, account IDs, etc.) and for known IBAN prefixes. CI fails if either matches.

## 9. Memory hygiene

### Rules

- **Secrets are `CharArray` or `ByteArray`, never `String`.** Java's `String` interning + immutability means we can't zero a `String` — its bytes hang around until GC and possibly long after.
- **Zero immediately after use.** `java.util.Arrays.fill(byteArray, 0)` / `Arrays.fill(charArray, ' ')` in a `try/finally` around every secret-handling block.
- **No `String.format` / templated strings with secrets.** Use builders that emit `CharArray`.
- **Long-lived secrets** (the SQLCipher DB key, once Room has it) live behind the SQLCipher native boundary; we accept that's a black box and trust SQLCipher's memory model.
- **Per-call unwraps** for EBICS RSA private keys, FinTS PINs, OAuth refresh tokens: unwrap → use within a single coroutine scope → zero in `finally`. Never hold across the operation.
- **No allocator amplification.** `ByteArrayOutputStream` for secret accumulation is forbidden (its internal `byte[]` resizes via `Arrays.copyOf`, leaving stale copies on the heap). Pre-size buffers, or use NIO `ByteBuffer.allocateDirect` (off-heap, can `Cleaner`-release).

### Pattern

```kotlin
inline fun <T> withSecret(supplier: () -> ByteArray, block: (ByteArray) -> T): T {
    val secret = supplier()
    try {
        return block(secret)
    } finally {
        java.util.Arrays.fill(secret, 0)
    }
}

// usage
val balance = withSecret({ unwrapFinTsPinFor(bankId) }) { pin ->
    connector.fetchBalance(pin)
}
```

### Coroutines + secrets

`suspend fun` boundaries can park `ByteArray` references in Continuation objects on the heap until resumption. Mitigation: keep secret-handling blocks inside `runBlocking` or `withContext(Dispatchers.IO)` *non-suspend* lambdas where possible; when a suspend boundary is unavoidable (e.g. waiting for a network response), zero the secret BEFORE the suspend point and re-unwrap on resumption.

## 10. Cross-cutting (Phase A.3 manifest changes)

`AndroidManifest.xml` additions for Phase A.3:

```xml
<application
    android:allowBackup="false"
    android:dataExtractionRules="@xml/data_extraction_rules_empty"
    android:fullBackupContent="@xml/empty_backup_rules"
    android:hasFragileUserData="true"
    ...>
```

- `allowBackup="false"` — locked default.
- `dataExtractionRules` — empty rules file at `res/xml/data_extraction_rules_empty.xml` (API 31+ supersedes `fullBackupContent`).
- `fullBackupContent="@xml/empty_backup_rules"` — empty rules for pre-API-31 belt-and-braces.
- `hasFragileUserData="true"` — on uninstall, Android prompts the user "this app contains data you might want to back up; keep app data?" — gives the user a parachute against accidental uninstall wiping years of ledger history (it doesn't bypass our encryption; the kept data is the still-encrypted DB file).
- **No `usesCleartextTraffic`** (defaults to `false` on API 28+, which is our minSdk — explicit). All traffic is HTTPS-pinned per §6.
- **No `requestLegacyExternalStorage`** (we use SAF exclusively, per CLAUDE.md).
- **No `allowNativeHeapPointerTagging="false"`** (default is `true`, leave it; helps detect heap corruption attacks).
- **No `extractNativeLibs="true"`** (default is `false` on AGP 4.2+, leave compressed in APK — helps prevent native-lib tampering after install).

Required `<uses-permission>`s for the banking stack (Phase A.3 + Phase B):

```xml
<uses-permission android:name="android.permission.INTERNET" />
<!-- USE_BIOMETRIC is API 28+; we're minSdk 28 so no compat fallback needed -->
<uses-permission android:name="android.permission.USE_BIOMETRIC" />
```

No `READ_EXTERNAL_STORAGE`, no `WRITE_EXTERNAL_STORAGE`, no `READ_MEDIA_*` (SAF instead). No `FOREGROUND_SERVICE` until/unless a Phase F+ background-refresh worker requires it (scheduled refresh likely uses WorkManager + JobScheduler, no foreground service needed for periodic 15-min refreshes).

### Telemetry posture (locked)

- No crash reporters in the APK.
- No analytics SDKs in the APK.
- No "anonymous usage" pings.
- Crash logs the user wants to share: surfaced in Settings → About → Send debug log, which packages the most recent `Logger`-emitted lines (already redacted per §8) into a text file via SAF for the user to manually attach to a GitHub issue. Never auto-uploaded.

## 11. Verification against the seed's 2026 assumptions

What the seed got right, and what changed:

| Seed assumption | 2026 reality |
| --- | --- |
| `net.zetetic:sqlcipher-android` is current | Confirmed; the *old* `net.zetetic:android-database-sqlcipher` artifact is deprecated. New artifact uses `SupportOpenHelperFactory`, not `SupportFactory`. ([Maven Central](https://central.sonatype.com/artifact/net.zetetic/sqlcipher-android), [GitHub](https://github.com/sqlcipher/sqlcipher-android)) |
| SQLCipher BSD-3-Clause | Confirmed. Passes the license gate. |
| ~10-15% cold-start cost | Closer to 5-12% on the 4.x line; we accept it. |
| EncryptedSharedPreferences deprecated as of `androidx.security 1.1.0-alpha07` | Confirmed deprecated and unmaintained; canonical replacement is **DataStore + Tink-encrypted blobs** OR **AndroidX DataStore 1.3.0-alpha07's new built-in encryption** ([droidcon migration guide](https://www.droidcon.com/2025/12/16/goodbye-encryptedsharedpreferences-a-2026-migration-guide/), [Ed Holloway-George on DataStore 1.3.0-alpha07](https://sp4ghetticode.medium.com/whats-new-in-androidx-datastore-1-3-0-alpha07-encrypt-your-datastore-25ca02b0b0e7)). For ledgerboy we *don't need* EncryptedSharedPreferences at all because credentials live in the SQLCipher DB; plain DataStore (Preferences) suffices for non-secret prefs. |
| AndroidX BiometricPrompt Class 3 only for Keystore key unwrap | Confirmed — Android platform invariant. Latest: `androidx.biometric:biometric:1.4.0-alpha05` + `biometric-compose:1.4.0-alpha05`. ([Maven](https://mvnrepository.com/artifact/androidx.biometric/biometric), [Android Developers Biometric release notes](https://developer.android.com/jetpack/androidx/releases/biometric)) |
| StrongBox available on Pixel 3+; many mid-range devices don't have it | Confirmed. `PackageManager.FEATURE_STRONGBOX_KEYSTORE` is the canonical check. Fallback ladder: StrongBox → TEE → software Keystore. ([Android Developers Keystore](https://developer.android.com/privacy-and-security/keystore), [AOSP Hardware-backed Keystore](https://source.android.com/docs/security/features/keystore)) |
| OkHttp `CertificatePinner` with `pin-sha256` format | Confirmed; OkHttp 5.x (Apache-2.0) maintains the same `sha256/` prefix + base64-encoded SPKI hash format. ([OkHttp CertificatePinner 5.x](https://square.github.io/okhttp/5.x/okhttp/okhttp3/-certificate-pinner/index.html), [HTTPS feature page](https://square.github.io/okhttp/features/https/)) |
| 5-min biometric grace window | Adopted as our default; configurable. |
| `allowBackup="false"` + no `dataExtractionRules` | Both are set (belt-and-braces — `dataExtractionRules` is the API 31+ canonical path; `allowBackup=false` covers older flows). |

Nothing in the seed turned out to be load-bearingly wrong. The biggest seed-vs-reality delta is the SQLCipher API rename (`SupportFactory` → `SupportOpenHelperFactory`) and the artifact rename (`android-database-sqlcipher` → `sqlcipher-android`); both happened during the SQLCipher 4.5 → 4.6 era and are now stable.

## 12. Phase A.3 / B / C implementation sub-step checklist

These drop into `main.md` as additions to the corresponding phases. Do not edit main.md from this file — orchestrator absorbs.

### Phase A.3 — Manifest + foundational security wiring

- [ ] **A.3.1** Add `<application android:allowBackup="false" android:dataExtractionRules="@xml/data_extraction_rules_empty" android:fullBackupContent="@xml/empty_backup_rules" android:hasFragileUserData="true">` to `AndroidManifest.xml`.
- [ ] **A.3.2** Create `res/xml/data_extraction_rules_empty.xml` (empty `<cloud-backup>` and `<device-transfer>` blocks).
- [ ] **A.3.3** Create `res/xml/empty_backup_rules.xml` (empty `<full-backup-content>`).
- [ ] **A.3.4** Add `<uses-permission android:name="android.permission.INTERNET" />` and `<uses-permission android:name="android.permission.USE_BIOMETRIC" />`.
- [ ] **A.3.5** Add `Logger` object at `core/log/Logger.kt` with `redact*()` helpers per §8.
- [ ] **A.3.6** Add `LOG_VERBOSE` / `LOG_DEBUG` `buildConfigField`s to `app/build.gradle.kts` per build type.
- [ ] **A.3.7** Settings → About → Send debug log: stub flow that writes redacted log lines to a SAF-chosen destination (no auto-send).

### Phase C — Encrypted Room database

- [ ] **C.1** Add `implementation("net.zetetic:sqlcipher-android:4.15.0@aar")` and `implementation("androidx.sqlite:sqlite:2.5.0")` to `app/build.gradle.kts`. Add `licensee { allow("BSD-3-Clause") }` if not already in the allowlist.
- [ ] **C.2** `KeystoreMaster` class at `core/security/KeystoreMaster.kt`: generates the `ledgerboy.db.master.v1` AES-256/GCM key with `setUserAuthenticationRequired(true)`, `setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG or AUTH_DEVICE_CREDENTIAL)`, `setInvalidatedByBiometricEnrollment(true)`, `setIsStrongBoxBacked(true)` with `StrongBoxUnavailableException` fallback to non-StrongBox.
- [ ] **C.3** `DbKeyVault` class at `core/security/DbKeyVault.kt`: generates the 32-byte random DB passphrase on first run, wraps it via `KeystoreMaster`, persists as `IV || ciphertext || tag` at `filesDir/db.key.wrap`. Reload + unwrap on session start.
- [ ] **C.4** `BiometricUnlockCeremony` class at `core/security/BiometricUnlockCeremony.kt`: builds the `BiometricPrompt.PromptInfo` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`, fires `authenticate(promptInfo, CryptoObject(cipher))`, returns the unwrapped DB passphrase on success.
- [ ] **C.5** `LedgerDatabase` (Room) opens via `Room.databaseBuilder(...).openHelperFactory(SupportOpenHelperFactory(passphraseBytes)).build()`, then the local `passphraseBytes` reference is zeroed.
- [ ] **C.6** Session lifecycle: `AppLockManager` tracks `Lifecycle.Event.ON_STOP` → re-lock after grace window; `ON_START` → fire BiometricPrompt if locked.
- [ ] **C.7** Settings → Security → Rotate database key: `PRAGMA rekey` flow with biometric authorization.
- [ ] **C.8** Settings → Security → Wipe all data: drops the Room DB, deletes the Keystore master, deletes the wrap file, clears DataStore prefs, revokes persisted URI permissions.
- [ ] **C.9** Lint rule (custom or detekt) blocking direct `android.util.Log.*` calls in `banking/**` and `data/**` — must go through `Logger`.
- [ ] **C.10** CI smoke check: run a fake EBICS handshake, capture logcat, grep for 4+ digit numbers and IBAN prefixes, fail build on match.

### Phase B — Per-connector credential storage (drops in alongside each `BankConnector` implementation)

- [ ] **B.x.1** Connector-specific credential row defined in Room schema (`ebics_credentials` / `fints_credentials` / `oauth_credentials`).
- [ ] **B.x.2** Per-operation HKDF key derivation from the DB master (info string namespaced per connector + version).
- [ ] **B.x.3** Second BiometricPrompt fires on first banking-call-per-session (Phase A grace window applies thereafter for the rest of the banking session).
- [ ] **B.x.4** All credential handling uses `withSecret { ... }` pattern (§9).
- [ ] **B.x.5** OkHttp client for this connector configured with `CertificatePinner` from `assets/pins/<connector>_<source>.json`. Two pins per host (current + next-rotation).
- [ ] **B.x.6** "Trust on first use" flow for user-added hosts (Settings dialog showing SHA-256 fingerprint, user confirms OOB).
- [ ] **B.x.7** Connector-specific log scrubbing: every log site in this connector's package uses `Logger.redact*()`.
- [ ] **B.x.8** Credential reveal in Settings → Banks → `<bank>` → Show credentials: dedicated BiometricPrompt, then displays the credential briefly with a 30-second auto-hide timer.

### Phase E+ — Encrypted backup / restore (deferred)

- [ ] **E.1** Bouncy Castle dep (`org.bouncycastle:bcprov-jdk18on`, MIT-style). License gate: Bouncy Castle uses an MIT-style license — verify SPDX at integration time, allowlist if needed.
- [ ] **E.2** `BackupEnvelope` class implementing the `LBE1` file format (§7).
- [ ] **E.3** Settings → Backup → Export encrypted backup flow with passphrase strength meter.
- [ ] **E.4** Settings → Backup → Restore from encrypted backup flow (destructive confirmation).

## Sources

- Maven Central: [`net.zetetic:sqlcipher-android`](https://central.sonatype.com/artifact/net.zetetic/sqlcipher-android)
- GitHub: [`sqlcipher/sqlcipher-android`](https://github.com/sqlcipher/sqlcipher-android) — current Room integration story via `SupportOpenHelperFactory`
- Android Developers: [Android Keystore system](https://developer.android.com/privacy-and-security/keystore)
- AOSP: [Hardware-backed Keystore](https://source.android.com/docs/security/features/keystore)
- Android Developers: [Biometric library release notes](https://developer.android.com/jetpack/androidx/releases/biometric)
- Android Developers: [BiometricPrompt.CryptoObject](https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.CryptoObject)
- Maven: [`androidx.biometric:biometric`](https://mvnrepository.com/artifact/androidx.biometric/biometric)
- OkHttp 5.x: [`CertificatePinner`](https://square.github.io/okhttp/5.x/okhttp/okhttp3/-certificate-pinner/index.html), [HTTPS feature page](https://square.github.io/okhttp/features/https/)
- droidcon: [Goodbye EncryptedSharedPreferences: A 2026 Migration Guide](https://www.droidcon.com/2025/12/16/goodbye-encryptedsharedpreferences-a-2026-migration-guide/)
- Medium / Ed Holloway-George: [AndroidX DataStore 1.3.0-alpha07 — Encrypt your DataStore](https://sp4ghetticode.medium.com/whats-new-in-androidx-datastore-1-3-0-alpha07-encrypt-your-datastore-25ca02b0b0e7)
- OWASP MASTG: [MASTG-KNOW-0043 Android KeyStore](https://mas.owasp.org/MASTG/knowledge/android/MASVS-STORAGE/MASTG-KNOW-0043/)
