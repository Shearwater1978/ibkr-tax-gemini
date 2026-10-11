# Design: Encrypted Backup to a User-Chosen Folder

## Decision 1: Client-side encryption before upload
Backups are encrypted on the device with a key the storage provider never sees.
Provider-side encryption at rest is not sufficient because the provider controls those keys.
This also supports GDPR Art. 32 (security of processing) and reduces the risk of
unauthorized access to personal data by the storage provider.

## Decision 2: Data key and key wrapping
- A random 256-bit data key (DK) encrypts all backup files and the database backup.
- DK is wrapped (encrypted) by a Key Encryption Key (KEK).
- KEK is derived from either:
  a) a user passphrase via Argon2id with per-user salt, or
  b) a recovery code (high-entropy random string shown once).
- Wrapped DK is stored in the backup manifest in the backup folder. The KEK is never written there.
- Changing the passphrase re-wraps DK without re-encrypting all backups.
- Parameters (Android): passphrases need at least 12 characters and are rejected when trivially simple (one repeated
  character or a plain sequence). Argon2id uses 64 MiB memory, 3 iterations, parallelism 1, a 16-byte random salt, and
  each wrapped key records its own parameters; lower them only if a phone needs more than about 2 seconds.
  Argon2id comes from Bouncy Castle (pure Java). The recovery code is 26 Crockford base32 characters (about
  130 bits) in six groups; its KEK is derived with HKDF-SHA256. On the device, DK is stored wrapped by a
  non-exportable Keystore key.
- Android Block Store holds a copy of DK only when it reports end-to-end encryption (Android 9+ with a screen lock),
  so a phone restored from the user's Android backup restores without the passphrase. A key check value in the
  manifest confirms that a retrieved DK belongs to the backup.

## Decision 3: Device-bound key for local data
A device key stored in Android Keystore or Secure Enclave protects the local
database key and the local cache. It is non-exportable and requires biometric
or device credential for use where supported.

## Decision 4: Ordering of operations
Import -> encrypt -> write to backup folder -> verify (read back; size and SHA-256) -> process
-> store results -> delete original. The original is never deleted before verification.

## Decision 5: User-chosen backup folder
The user picks a backup folder once with the system folder picker (Storage Access Framework,
`ACTION_OPEN_DOCUMENT_TREE`); the app persists permission for that folder only. The folder can live in
Google Drive, another cloud storage app, or on the device. This needs no Google Cloud project, OAuth
client, consent screen, or token storage, and the app itself makes no backup network requests: the
storage app syncs the files. Files are visible to the user, so they carry random, neutral names and
contain only ciphertext and the wrapped data key. A write is verified by reading it back. On a new
device the user picks the same folder to restore. Settings offers two locations, "This phone" and "Google Drive";
the picker opens in Documents for the phone option, the app creates its own "IBKR Tax Assistant backups"
subfolder in the confirmed folder, and it rejects a pick whose storage provider does not match the chosen option. (Replaces the earlier `drive.appdata` design after
Google Cloud access was unavailable; it also works with any provider.)

## Decision 6: Encrypted file format
Versioned header (format version, KDF parameters, wrapped DK, nonce), followed by
AES-256-GCM ciphertext. Associated data includes the format version and file ID.
Format is implemented using a vetted library (Tink or CryptoKit primitives).

## Decision 7: Pseudonymization and data minimization
- Direct identifiers (full name, tax ID, full account number) are replaced in non-essential
  contexts by pseudonyms or masked values.
- Pseudonyms are derived with HMAC-SHA256 using a secret held in the device keystore.
  Without that key, the pseudonym cannot be linked back to the identifier.
- The mobile app produces no tax output (decision 2026-10-11: PIT-38 stays in the desktop tool), so it never
  needs the full identity and never reads or keeps it.
- Pseudonymized data remains personal data under GDPR. True anonymization (no reasonable
  means of re-identification) is applied only for aggregated statistics, if any are produced.
- The Android app keeps the classification in code (`security/DataClassification.kt`); a unit test fails when a
  stored column is not classified or a direct identifier would be stored.
- Keys used for pseudonymization are never included in backups in plaintext, and the
  pseudonym mapping is rebuilt from the data key or re-derived on restore.

## Decision 8: Transfers and data subject rights
- The user chooses the storage provider; only end-to-end encrypted files reach it. The privacy notice
  explains this, including that providers may store data outside the EEA.
- Erasure request from the user: delete local data, delete the backup files and manifest in the backup
  folder, and destroy the keys (device key entries and the data key). Provider trash or version history
  may keep copies; without the keys they cannot be decrypted (crypto-shredding).
- Portability: the user's data stays on the phone and in the user's own backup folder; tax reports come from
  the desktop tool, which reads the same broker reports.

## Decision 9: No logging and a reviewed set of libraries (tasks 6.3, 6.4)

**Logs.** The Android app writes nothing to the system log: no `android.util.Log`, `println`, `print`, `printStackTrace` or `System.out/err` in any shipped source set (main, release, debug). `NoLoggingTest` scans the sources and fails the build if one appears. Exception messages carry only format version numbers. The Finnhub key is sent in the `X-Finnhub-Token` header, never in a URL, so an `HttpURLConnection` error message cannot contain it; network errors are caught. Release builds use a stub without the synthetic sample reports. Release builds are not minified; stripping library logging with R8 was not adopted because SQLCipher, Bouncy Castle and Play services need keep rules, and the libraries below do not log app data.

**Libraries (Android, as of 2026-10-11).** No analytics, crash-reporting, advertising or attribution SDK is included.

| Library | Version | Purpose | Network or data collection |
|---|---|---|---|
| AndroidX Compose, Activity, Navigation, Fragment, Material 3 | BOM 2024.12.01 and listed versions | UI | None |
| AndroidX Biometric | 1.1.0 | App lock with biometrics or device credential | None; the system prompt runs on the device |
| AndroidX DocumentFile | 1.0.1 | Access to the user-chosen backup folder (SAF) | None itself; the storage app (for example Google Drive) syncs the encrypted files |
| AndroidX Browser | 1.8.0 | Custom Tab for the Finnhub sign-up page | Opens a page the user asked for; nothing from the app is sent |
| AndroidX SQLite + SQLCipher for Android | 2.4.0, 4.6.1 | Encrypted local database | None |
| Bouncy Castle (bcprov) | 1.78.1 | Argon2id for the passphrase key | None |
| Play services Block Store (+ coroutines adapter) | 16.4.0 | End-to-end encrypted copy of the backup data key | Stored with the user's Android backup, only when end-to-end encryption is available; Google cannot read it. Play services' own usage reporting follows the user's Google settings and receives no app data |
| Kotlin coroutines | 1.9.0 | Concurrency | None |

The app's own network requests are listed in the market-prices spec: ticker symbols to Finnhub (with the user's key) and Yahoo Finance, currency codes to NBP.

## Risks
- Loss of passphrase and recovery code means backups are unrecoverable. Mitigation: explicit onboarding and warnings.
- Storage providers may keep deleted files in trash or version history. Mitigation: erasure destroys the keys,
  so leftover copies cannot be decrypted.
- The user may pick a folder that exists only on the device, so a lost phone also loses the backup.
  Mitigation: warn when the chosen folder is local-only and recommend a cloud folder.
- A storage app may sync late or be offline. Mitigation: verify by reading back, keep a visible pending state,
  and keep protected local data until verification succeeds.
- Pseudonymized logs and analytics can still be personal data. Mitigation: avoid collecting them
  at all in release builds where possible.
