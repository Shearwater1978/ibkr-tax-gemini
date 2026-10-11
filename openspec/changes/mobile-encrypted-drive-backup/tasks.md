## 1. Backup location
- [x] 1.1 Offer "This phone" and "Google Drive" as backup locations, confirm the parent folder with the system folder picker, persist the permission, and create the app's "IBKR Tax Assistant backups" subfolder (or use a folder that already holds a manifest)
- [x] 1.2 Warn when the chosen folder is local-only
- [x] 1.3 Detect lost folder access and ask the user to choose the folder again without losing local data
- [x] 1.4 Keep old backups when the user changes the folder, unless deletion is requested
- [ ] 1.5 Implement the iOS equivalent (document picker with security-scoped bookmarks)

## 2. Key management
- [ ] 2.1 Implement device key in Keystore / Secure Enclave _Status: Android done (Keystore, StrongBox when available); iOS postponed._
- [x] 2.2 Implement passphrase-derived KEK using Argon2id
- [x] 2.3 Implement recovery code generation and display-once flow
- [x] 2.4 Implement DK wrapping and unwrapping
- [x] 2.5 Implement passphrase change with re-wrap
- [x] 2.6 Store DK in Android Block Store when end-to-end encryption is available and use it to restore on a new device; fall back to passphrase or recovery code _Status: Android done; used by the restore flow (4.5)._
- [x] 2.7 Let the user replace the recovery code after entering the passphrase

## 3. Report ingestion
- [x] 3.1 Implement file import via system document picker
- [x] 3.2 Implement parser that runs without network access _Status: Android done; `OfflineProcessingTest` keeps network code out of report handling, and an import was verified on an emulator with networking off._
- [x] 3.3 Ensure no copies of source files are kept outside the managed location

## 4. Encrypted backup
- [x] 4.1 Implement encrypted file format with versioned header
- [x] 4.2 Implement writing to the backup folder with retries _Status: Android done; pending backups are retried automatically while the app runs (30 s, doubling up to 15 min, stopping on lost folder access), plus on opening Imports and "Retry backup". No background job when the app is closed._
- [x] 4.3 Implement read-back verification (size and SHA-256)
- [x] 4.4 Delete original only after verification succeeds
- [x] 4.5 Implement restore flow on new device _Status: Android done (import screen, Block Store key or passphrase/recovery code, verified on an emulator with a wiped app); iOS postponed._
- [ ] 4.6 Implement backup deletion in the backup folder

## 5. Local storage
- [ ] 5.1 Integrate SQLCipher with key from device key
- [ ] 5.2 Include encrypted database backup in the backup set
- [x] 5.3 Exclude local data from OS cloud backups _Status: Android done (allowBackup and fullBackupContent off, data extraction rules exclude every domain from cloud backup and device transfer); iOS postponed._
- [x] 5.4 Clear temporary files and caches on exit _Status: Android done; the app writes no plaintext files, and the cache folder is emptied at start and when the app goes to the background._

## 6. Device hardening
- [ ] 6.1 Disable screenshots on Android and hide content in app switcher on iOS _Status: Android done (FLAG_SECURE, verified on an emulator); iOS postponed._
- [ ] 6.2 Auto-lock after timeout and on background _Status: Android done (locks on start and after 5 minutes in the background; biometric or device credential); iOS postponed._
- [x] 6.3 Verify no sensitive data in logs in release builds _Status: Android done; the app does not log at all, enforced by `NoLoggingTest` (design Decision 9)._
- [x] 6.4 Review third-party SDKs for data collection _Status: Android reviewed, no analytics or crash SDKs (design Decision 9)._
- [ ] 6.5 Integrate Play Integrity and App Attest

## 7. Data anonymization and pseudonymization
- [x] 7.1 Define field classification (direct identifiers, quasi-identifiers, financial values) _Status: Android done; `security/DataClassification.kt` classifies every report field and stored column, and `DataClassificationTest` fails on an unclassified column or a stored direct identifier._
- [x] 7.2 Implement HMAC-SHA256 pseudonymization with device-bound key _Status: Android done (account and report fingerprint). Key rotation is not offered; a restore re-imports reports, so pseudonyms are derived again with the new device key._
- [x] 7.3 Implement masking for account numbers and tax IDs in UI and notifications _Status: Android done; only the last four account characters are stored for display and shown, file names are masked; names and tax IDs are not read; the app sends no notifications and offers no reveal._
- [x] 7.4 Ensure logs, crash reports, and analytics receive only pseudonyms or masked values _Status: Android done; the app does not log (`NoLoggingTest`) and has no crash-reporting or analytics SDK (Decision 9)._
- [ ] 7.5 Restrict full identity to tax output and its export, and document the exception
- [x] 7.6 Implement erasure across local DB, backup folder, and keys (destroying keys makes leftover copies unreadable) _Status: Android done ("Erase all data" in Settings, verified on an emulator). No retention period applies, so nothing is deleted automatically._
- [ ] 7.7 Implement export of tax output in CSV or PDF for portability
- [x] 7.8 Write privacy notice and record processing activities (Art. 30 GDPR) _Status: drafts in `mobile/PRIVACY.md` and `mobile/PROCESSING_RECORD.md`, short version in Settings; publisher and contact are placeholders to fill and review before any public release._
