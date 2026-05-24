# ledgerboy — credential storage + threat model research

## Status: SEED — research-round agent picks up from here

Seed prompt for the next round of research on **credential storage, database encryption, and the app's threat model**. Produces a single decision file: `docs/plans/security-decisions.md`. Touches Phase A.3 (manifest permissions), Phase B (credential storage for connectors), Phase C (encrypted Room database).

This is the load-bearing-est research stream. Ledgerboy holds:

- Bank credentials (EBICS user/partner/bank IDs + RSA private keys, FinTS PINs, OAuth refresh tokens for PSD2 aggregators).
- Account numbers, IBANs, balances.
- Full transaction history (potentially years of it, fully attributed).
- Asset holdings (which by themselves can be high-value PII).

Get this wrong and the app is a high-value target on a lost phone. Get this right and a lost phone is a defeat-in-depth problem (lock screen → SCA on the bank's side → SQLCipher → Strongbox → biometric gate).

## Threat model — sketch (research agent refines)

Cards on the table:

1. **Lost / stolen phone, screen locked.** Default Android FBE encryption holds; attacker can't reach app data without the user's lock-screen creds. Ledgerboy's defence: rely on FBE, possibly add a separate biometric gate inside the app for fast-relock after foregrounding from a recents card.
2. **Lost / stolen phone, screen unlocked.** Attacker has the running app session. Ledgerboy's defence: re-lock on app background, biometric prompt on resume, optional auto-lock-on-screen-off.
3. **Malicious app on the same device.** Default Android sandboxing isolates app data dirs; ledgerboy isn't a content provider target. Defence is "don't be a content provider", don't `MODE_WORLD_READABLE`, don't accept untrusted broadcasts.
4. **Hostile OS image / rooted device / forensic ADB pull.** Defence: SQLCipher-encrypted Room database; the key wrapped in Android Keystore (Strongbox where available); the unwrap requires user presence (biometric or device credential). On a rooted device, all bets weaken; document that.
5. **MITM on banking traffic.** Defence: TLS pinning per bank (or per aggregator). EBICS already does end-to-end crypto at the application layer; layered TLS pinning is belt-and-braces.
6. **Backup leakage.** Defence: `android:allowBackup="false"` in the manifest. The user's bank ledger is not the kind of data ADB-backup should snapshot.

## Concrete questions for the research file

### Database encryption

- **SQLCipher** for Android (`net.zetetic:sqlcipher-android`). BSD-3-Clause. Mature. Integrates with Room via `SupportFactory`. Costs ~10-15% on cold-start query latency.
- **Alternative:** Room with no SQLCipher, but every PII-bearing column encrypted with a per-column key from Android Keystore. Heavier code, lighter perf cost, finer-grained.
- **Alternative:** No app-level encryption, rely on FBE alone. **Probably the wrong call for a banking app**, but document why.

Questions:

- Which approach?
- Where does the SQLCipher key live? (Keystore-wrapped; Strongbox if hardware-backed; software-backed otherwise.)
- Re-key strategy on biometric / lock-screen change?
- Migration path when SQLCipher itself bumps a major version?
- Backup posture: `allowBackup=false` is the simple call; alternatives?

### Credential storage (banking secrets)

- **EncryptedSharedPreferences** — deprecated as of androidx.security 1.1.0-alpha07. Don't.
- **DataStore Preferences + Android Keystore-encrypted blob per credential** — current recommendation.
- **Storing inside the SQLCipher Room database** — viable; locks credential lifecycle to ledger lifecycle.

Questions:

- Where do EBICS RSA private keys live? (Probably Keystore-generated, key handle stored in Room; never round-tripped to memory unwrapped.)
- Where do OAuth refresh tokens live? (Encrypted blob in DataStore or Room.)
- Where do FinTS PINs live? (Encrypted blob — only ever in-memory during a session; never persisted unencrypted.)
- Per-credential biometric gating? (Optional: re-prompt biometric before the first banking call of a session.)

### Biometric gate

- **AndroidX BiometricPrompt.** Class 3 (strong) biometrics only — fingerprint sensors, secure face unlock. Class 2 (camera-only face unlock) not strong enough to unwrap Keystore keys.
- Settings entry: "Require biometric on app open" (default on after first credential is stored; user can disable).
- Settings entry: "Re-lock when screen turns off" (default on).
- Fallback: device credential (PIN / pattern / password) when biometric not enrolled.

Questions:

- Is biometric mandatory once credentials are stored, or always opt-in?
- Grace window after unlock (5 minutes before re-prompting on each foreground)?
- Failure handling: how many biometric failures before falling back to device credential? What happens after N device-credential failures? (Probably: nothing punitive — the OS handles lock-out.)

### TLS pinning

- **OkHttp CertificatePinner** if the banking-API stack uses OkHttp.
- EBICS already authenticates the bank at the application layer (the bank's certificate is part of the EBICS handshake); TLS pinning is belt-and-braces.
- Per-aggregator pinning for PSD2 (Tink / GoCardless / etc. publish their cert SHA-256 publicly).

Questions:

- Pin per bank or per aggregator?
- How are pin lists distributed (compiled in? fetched on first launch? Both?)
- Pin rotation handling (when the bank's cert rotates, pinning blocks the app — need a grace mechanism).

### Backup / migration / wipe

- `android:allowBackup="false"` — locked.
- App-uninstall wipes everything (default Android behaviour).
- "Wipe all data" settings entry: deletes the Room database, revokes Keystore keys, forgets persisted URI permissions. Useful for the "I'm giving this phone to a kid" flow.

Questions:

- Manual export of the (encrypted) database for user-initiated backup to user-controlled storage? (Probably yes — backup as an encrypted blob with a user-provided passphrase, restorable later.)
- Multi-device sync via the same encrypted-export blob? (Stretch — not v1.)

### Memory hygiene

- Long-lived secrets (Keystore-unwrapped key material, FinTS PIN, EBICS passphrase) never hold beyond their immediate use. Zero out byte arrays. Avoid `String` for secrets (String interning means we can't reliably zero).
- Log scrubbing: never log balances, account numbers, PINs, keys. The `adb logcat -s ledgerboy:* ledgerboy.banking:*` filter must be safe to share publicly.

## Decision criteria

1. Belt-and-braces on credential storage (Keystore-wrapped, biometric-gated, encrypted database, no plaintext anywhere).
2. Minimal extra cognitive load on the user (biometric prompt happens once per session, not per call).
3. Backup posture is opt-in encrypted export, not "Google Drive auto-backup of an encrypted blob the user doesn't know about."
4. Logs are safe to share. (`adb logcat` should never leak a balance or an account number.)

## What "done" looks like for this research round

- One decision file: `docs/plans/security-decisions.md`.
- The decision file picks: encryption library, key-wrapping approach, biometric flow, TLS-pinning posture, backup posture, log-hygiene rule, and writes down the threat model in plain language.
- This file gets a "Decision: see security-decisions.md" pointer at the top.
- [`main.md`](main.md) Phase A.3 (manifest permissions), Phase B (credential storage in connectors), Phase C (encrypted Room) get sub-step checkboxes based on the decisions.
- `CLAUDE.md` "Privacy posture" gets a "Security posture" companion section reiterating the decisions for future agents.
