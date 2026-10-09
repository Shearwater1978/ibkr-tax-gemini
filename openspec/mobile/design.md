# Design: Encrypted Drive Backup

## Decision 1: Client-side encryption before upload
Backups are encrypted on the device with a key the Google Drive provider never sees.
Google-side encryption at rest is not sufficient because Google controls those keys.
This also supports GDPR Art. 32 (security of processing) and reduces the risk of
unauthorized access to personal data by the storage provider.

## Decision 2: Data key and key wrapping
- A random 256-bit data key (DK) encrypts all backup files and the database backup.
- DK is wrapped (encrypted) by a Key Encryption Key (KEK).
- KEK is derived from either:
  a) a user passphrase via Argon2id with per-user salt, or
  b) a recovery code (high-entropy random string shown once).
- Wrapped DK is stored in the backup manifest on Google Drive. The KEK is never uploaded.
- Changing the passphrase re-wraps DK without re-encrypting all backups.

## Decision 3: Device-bound key for local data
A device key stored in Android Keystore or Secure Enclave protects the local
database key and the local cache. It is non-exportable and requires biometric
or device credential for use where supported.

## Decision 4: Ordering of operations
Import -> encrypt -> upload -> verify (checksum from server metadata) -> process
-> store results -> delete original. The original is never deleted before verification.

## Decision 5: Drive scope
Use `drive.appdata` only. Files are not visible to the user in Drive UI and are
inaccessible to other apps.

## Decision 6: Encrypted file format
Versioned header (format version, KDF parameters, wrapped DK, nonce), followed by
AES-256-GCM ciphertext. Associated data includes the format version and file ID.
Format is implemented using a vetted library (Tink or CryptoKit primitives).

## Decision 7: Pseudonymization and data minimization
- Direct identifiers (full name, tax ID, full account number) are replaced in non-essential
  contexts by pseudonyms or masked values.
- Pseudonyms are derived with HMAC-SHA256 using a secret held in the device keystore.
  Without that key, the pseudonym cannot be linked back to the identifier.
- The full identity is kept only where the tax output requires it (generated tax report
  and its export). This is a documented exception.
- Pseudonymized data remains personal data under GDPR. True anonymization (no reasonable
  means of re-identification) is applied only for aggregated statistics, if any are produced.
- Keys used for pseudonymization are never included in backups in plaintext, and the
  pseudonym mapping is rebuilt from the data key or re-derived on restore.

## Decision 8: Transfers and data subject rights
- Google Drive is a transfer outside the EEA. Rely on Google's DPA with SCCs and/or
  the EU-US Data Privacy Framework (verify certification status before release).
- Erasure request from the user: delete local data, delete Drive backups via files.delete,
  and remove the local device key entry. The backup manifest is deleted with them.
- Portability: the user can export the tax output in a standard format (CSV or PDF).

## Risks
- Loss of passphrase and recovery code means backups are unrecoverable. Mitigation: explicit onboarding and warnings.
- Refresh token expiry in OAuth Testing mode. Mitigation: publish the OAuth app before production.
- Drive version history retains old file versions. Mitigation: delete via files.delete, not trash only.
- Pseudonymized logs and analytics can still be personal data. Mitigation: avoid collecting them
  at all in release builds where possible.
