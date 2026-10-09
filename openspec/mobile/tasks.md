## 1. Google Cloud setup
- [ ] 1.1 Create Google Cloud project and enable Drive API
- [ ] 1.2 Configure OAuth consent screen with privacy policy URL
- [ ] 1.3 Create Android OAuth client (package name, SHA-1 for debug and release)
- [ ] 1.4 Create iOS OAuth client (bundle ID) and configure URL scheme
- [ ] 1.5 Move OAuth app to production status

## 2. Key management
- [ ] 2.1 Implement device key in Keystore / Secure Enclave
- [ ] 2.2 Implement passphrase-derived KEK using Argon2id
- [ ] 2.3 Implement recovery code generation and display-once flow
- [ ] 2.4 Implement DK wrapping and unwrapping
- [ ] 2.5 Implement passphrase change with re-wrap

## 3. Report ingestion
- [ ] 3.1 Implement file import via system document picker
- [ ] 3.2 Implement parser that runs without network access
- [ ] 3.3 Ensure no copies of source files are kept outside the managed location

## 4. Encrypted backup
- [ ] 4.1 Implement encrypted file format with versioned header
- [ ] 4.2 Implement upload to appDataFolder with retries
- [ ] 4.3 Implement post-upload verification
- [ ] 4.4 Delete original only after verification succeeds
- [ ] 4.5 Implement restore flow on new device
- [ ] 4.6 Implement backup deletion using files.delete

## 5. Local storage
- [ ] 5.1 Integrate SQLCipher with key from device key
- [ ] 5.2 Include encrypted database backup in the backup set
- [ ] 5.3 Exclude local data from OS cloud backups
- [ ] 5.4 Clear temporary files and caches on exit

## 6. Device hardening
- [ ] 6.1 Disable screenshots on Android and hide content in app switcher on iOS
- [ ] 6.2 Auto-lock after timeout and on background
- [ ] 6.3 Verify no sensitive data in logs in release builds
- [ ] 6.4 Review third-party SDKs for data collection
- [ ] 6.5 Integrate Play Integrity and App Attest
