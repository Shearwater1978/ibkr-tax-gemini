## 1. Backup location
- [x] 1.1 Offer "This phone" and "Google Drive" as backup locations, confirm the parent folder with the system folder picker, persist the permission, and create the app's "IBKR Tax Assistant backups" subfolder (or use a folder that already holds a manifest)
- [x] 1.2 Warn when the chosen folder is local-only
- [x] 1.3 Detect lost folder access and ask the user to choose the folder again without losing local data
- [x] 1.4 Keep old backups when the user changes the folder, unless deletion is requested
- [ ] 1.5 Implement the iOS equivalent (document picker with security-scoped bookmarks) _Status: iOS done: system folder picker in Settings, bookmark kept in the encrypted database, security-scoped and coordinated reads and writes, app subfolder, local-only warning (On My iPhone), lost access detected. To verify on a physical iPhone with iCloud Drive._

## 2. Key management
- [ ] 2.1 Implement device key in Keystore / Secure Enclave _Status: Android done (Keystore, StrongBox when available). iOS done (Secure Enclave P-256 key, this device only, wraps the database and pseudonym keys); verified on the simulator, physical-device check pending (mobile-app-core 5.2)._
- [x] 2.2 Implement passphrase-derived KEK using Argon2id _iOS: Argon2id through libsodium (swift-sodium), same parameters and manifest as Android; checked against `mobile/fixtures/keys/manifest_v1.json`._
- [x] 2.3 Implement recovery code generation and display-once flow _iOS: generation and confirm-before-store done; the display screen follows._
- [x] 2.4 Implement DK wrapping and unwrapping _iOS: same AES-256-GCM wraps and key check as Android._
- [x] 2.5 Implement passphrase change with re-wrap _iOS: done._
- [x] 2.6 Store DK in Android Block Store when end-to-end encryption is available and use it to restore on a new device; fall back to passphrase or recovery code _Status: Android done; used by the restore flow (4.5)._ _iOS: no Block Store equivalent yet (iCloud Keychain would be the candidate); a new iPhone restores with the passphrase or recovery code._
- [x] 2.7 Let the user replace the recovery code after entering the passphrase _iOS: done (keys); the Settings screens follow._

## 3. Report ingestion
- [x] 3.1 Implement file import via system document picker
- [x] 3.2 Implement parser that runs without network access _Status: Android done; `OfflineProcessingTest` keeps network code out of report handling, and an import was verified on an emulator with networking off._
- [x] 3.3 Ensure no copies of source files are kept outside the managed location

## 4. Encrypted backup
- [x] 4.1 Implement encrypted file format with versioned header _iOS: same `IBKB` v1 format; decrypts `mobile/fixtures/keys/valid_basic_v1.bin` built with pyca/cryptography._
- [x] 4.2 Implement writing to the backup folder with retries _Status: Android done; pending backups are retried automatically while the app runs (30 s, doubling up to 15 min, stopping on lost folder access), plus on opening Imports and "Retry backup". No background job when the app is closed._ _iOS: BackupService and BackupRetry ported with the same backoff._
- [x] 4.3 Implement read-back verification (size and SHA-256) _iOS: done._
- [x] 4.4 Delete original only after verification succeeds _iOS: done._
- [x] 4.5 Implement restore flow on new device _Status: Android done (import screen, Block Store key or passphrase/recovery code, verified on an emulator with a wiped app); iOS postponed. iOS done: pick the folder, passphrase or recovery code (no Block Store equivalent), restores Android backups too; tested back up → erase → restore._
- [ ] 4.6 Implement backup deletion in the backup folder

## 5. Local storage
- [ ] 5.1 Integrate SQLCipher with key from device key _Status: Android done; iOS done (SQLCipher 4.19.0, raw 256-bit key wrapped by the Secure Enclave key, refuses to open without SQLCipher)._
- [ ] 5.2 Include encrypted database backup in the backup set
- [x] 5.3 Exclude local data from OS cloud backups _Status: Android done (allowBackup and fullBackupContent off, data extraction rules exclude every domain from cloud backup and device transfer). iOS done: database and wrapped keys live in a folder excluded from backup, with complete file protection (not observable on the simulator)._
- [x] 5.4 Clear temporary files and caches on exit _Status: Android done; the app writes no plaintext files, and the cache folder is emptied at start and when the app goes to the background. iOS does the same for its temporary and Caches folders._

## 6. Device hardening
- [ ] 6.1 Disable screenshots on Android and hide content in app switcher on iOS _Status: Android done (FLAG_SECURE, verified on an emulator). iOS done: a cover hides the app whenever it is not active (app switcher, system prompts) and while the screen is recorded or mirrored; iOS cannot block screenshots. Verified on a simulator; app-switcher snapshot still to check on a physical iPhone._
- [ ] 6.2 Auto-lock after timeout and on background _Status: Android done (locks on start and after 5 minutes in the background; biometric or device credential). iOS done with the same rule (Face ID, Touch ID or passcode), verified on a simulator with simulated Face ID._
- [x] 6.3 Verify no sensitive data in logs in release builds _Status: Android done; the app does not log at all, enforced by `NoLoggingTest` (design Decision 9). iOS likewise, enforced by `NoLoggingTests`._
- [x] 6.4 Review third-party SDKs for data collection _Status: Android reviewed, no analytics or crash SDKs (design Decision 9)._
- [ ] 6.5 Integrate Play Integrity and App Attest

## 7. Data anonymization and pseudonymization
- [x] 7.1 Define field classification (direct identifiers, quasi-identifiers, financial values) _Status: Android done; `security/DataClassification.kt` classifies every report field and stored column, and `DataClassificationTest` fails on an unclassified column or a stored direct identifier. iOS has the same classification, checked against its live schema by `DataClassificationTests`._
- [x] 7.2 Implement HMAC-SHA256 pseudonymization with device-bound key _Status: Android done (account and report fingerprint); iOS pseudonymizer done with the same input format, used once import is ported. Key rotation is not offered; a restore re-imports reports, so pseudonyms are derived again with the new device key._
- [x] 7.3 Implement masking for account numbers and tax IDs in UI and notifications _Status: Android done; only the last four account characters are stored for display and shown, file names are masked; names and tax IDs are not read; the app sends no notifications and offers no reveal._
- [x] 7.4 Ensure logs, crash reports, and analytics receive only pseudonyms or masked values _Status: Android done; the app does not log (`NoLoggingTest`) and has no crash-reporting or analytics SDK (Decision 9)._
- [x] 7.5 Restrict full identity to tax output and its export, and document the exception _Status: no tax output on mobile (decision 2026-10-11), so the app never reads or keeps the full identity; spec requirement "No tax output in the mobile app"._
- [x] 7.6 Implement erasure across local DB, backup folder, and keys (destroying keys makes leftover copies unreadable) _Status: Android done ("Erase all data" in Settings, verified on an emulator). No retention period applies, so nothing is deleted automatically. iOS done: same order as Android; the app resets in place instead of restarting (iOS apps cannot relaunch themselves)._
- [x] 7.7 Implement export of tax output in CSV or PDF for portability _Status: not in the mobile app (decision 2026-10-11); tax output stays in the desktop tool._
- [x] 7.8 Write privacy notice and record processing activities (Art. 30 GDPR) _Status: drafts in `mobile/PRIVACY.md` and `mobile/PROCESSING_RECORD.md`, short version in Settings; publisher and contact are placeholders to fill and review before any public release._
