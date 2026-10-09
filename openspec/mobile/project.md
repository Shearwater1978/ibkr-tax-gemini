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
- Personal data handling must consider Russian law 152-FZ (cross-border transfer).

## Tech Stack
- Android: Kotlin, Android Keystore, Tink, SQLCipher
- iOS: Swift, Keychain, Secure Enclave, CryptoKit, SQLCipher
- Cloud: Google Drive API v3 (scope: drive.appdata)
- Crypto: AES-256-GCM, Argon2id (or scrypt) for passphrase-derived keys

## Conventions
- Never log report contents, amounts, account numbers, or tax identifiers.
- Use vetted cryptographic libraries only. No custom primitives or formats.
