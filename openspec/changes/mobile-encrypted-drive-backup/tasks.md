## 1. Backup location
- [ ] 1.1 Let the user choose the backup folder with the system folder picker and persist the permission
- [ ] 1.2 Warn when the chosen folder is local-only
- [ ] 1.3 Detect lost folder access and ask the user to choose the folder again without losing local data
- [ ] 1.4 Keep old backups when the user changes the folder, unless deletion is requested
- [ ] 1.5 Implement the iOS equivalent (document picker with security-scoped bookmarks)

## 2. Key management
- [ ] 2.1 Implement device key in Keystore / Secure Enclave
- [ ] 2.2 Implement passphrase-derived KEK using Argon2id
- [ ] 2.3 Implement recovery code generation and display-once flow
- [ ] 2.4 Implement DK wrapping and unwrapping
- [ ] 2.5 Implement passphrase change with re-wrap
- [ ] 2.6 Store DK in Android Block Store when end-to-end encryption is available and use it to restore on a new device; fall back to passphrase or recovery code

## 3. Report ingestion
- [ ] 3.1 Implement file import via system document picker
- [ ] 3.2 Implement parser that runs without network access
- [ ] 3.3 Ensure no copies of source files are kept outside the managed location

## 4. Encrypted backup
- [ ] 4.1 Implement encrypted file format with versioned header
- [ ] 4.2 Implement writing to the backup folder with retries
- [ ] 4.3 Implement read-back verification (size and SHA-256)
- [ ] 4.4 Delete original only after verification succeeds
- [ ] 4.5 Implement restore flow on new device
- [ ] 4.6 Implement backup deletion in the backup folder

## 5. Local storage
- [ ] 5.1 Integrate SQLCipher with key from device key
- [ ] 5.2 Include encrypted database backup in the backup set
- [ ] 5.3 Exclude local data from OS cloud backups
- [ ] 5.4 Clear temporary files and caches on exit

## 6. Device hardening
- [ ] 6.1 Disable screenshots on Android and hide content in app switcher on iOS _Status: Android done (FLAG_SECURE, verified on an emulator); iOS postponed._
- [ ] 6.2 Auto-lock after timeout and on background _Status: Android done (locks on start and after 5 minutes in the background; biometric or device credential); iOS postponed._
- [ ] 6.3 Verify no sensitive data in logs in release builds
- [ ] 6.4 Review third-party SDKs for data collection
- [ ] 6.5 Integrate Play Integrity and App Attest

## 7. Data anonymization and pseudonymization
- [ ] 7.1 Define field classification (direct identifiers, quasi-identifiers, financial values)
- [ ] 7.2 Implement HMAC-SHA256 pseudonymization with device-bound key
- [ ] 7.3 Implement masking for account numbers and tax IDs in UI and notifications
- [ ] 7.4 Ensure logs, crash reports, and analytics receive only pseudonyms or masked values
- [ ] 7.5 Restrict full identity to tax output and its export, and document the exception
- [ ] 7.6 Implement erasure across local DB, backup folder, and keys (destroying keys makes leftover copies unreadable)
- [ ] 7.7 Implement export of tax output in CSV or PDF for portability
- [ ] 7.8 Write privacy notice and record processing activities (Art. 30 GDPR)
