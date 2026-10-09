# Project Context

## Purpose
Mobile application (Android and iOS) that imports broker reports, processes them
on-device for tax reporting, stores results in a local encrypted database, and
keeps an encrypted backup of raw reports in the user's Google Drive app data folder.

## Constraints
- All report processing happens on the device. No server-side processing.
- Raw reports are deleted from the device after successful processing and verified backup.
- Plaintext financial data must never leave the device unencrypted.
- Secrets must never be embedded in the application binary.
- Personal data is processed in accordance with GDPR (Regulation (EU) 2016/679).
  - Data minimization and data protection by design and by default (Art. 5 and Art. 25).
  - Legal basis for processing and transparent privacy notice are required before release.
  - Transfers outside the EEA (including Google Drive storage) must rely on an approved mechanism
    such as the EU-US Data Privacy Framework or Standard Contractual Clauses.
  - Data subject rights (access, erasure, portability) must be supported, including Drive backups.
- Pseudonymized data is still personal data under GDPR. Only properly anonymized data falls outside its scope.

## Tech Stack
- Android: Kotlin, Android Keystore, Tink, SQLCipher
- iOS: Swift, Keychain, Secure Enclave, CryptoKit, SQLCipher
- Cloud: Google Drive API v3 (scope: drive.appdata)
- Crypto: AES-256-GCM, Argon2id (or scrypt) for passphrase-derived keys, HMAC-SHA256 for pseudonymization

## Conventions
- Never log report contents, amounts, account numbers, or tax identifiers.
- Use vetted cryptographic libraries only. No custom primitives or formats.
- Direct identifiers are pseudonymized or masked by default outside the tax output screen and export.
