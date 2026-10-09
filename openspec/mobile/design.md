# Design: Encrypted Drive Backup

## Decision 1: Client-side encryption before upload
Backups are encrypted on the device with a key the Google Drive provider never sees.
Google-side encryption at rest is not sufficient because Google controls those keys.

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

## Risks
- Loss of passphrase and recovery code means backups are unrecoverable. Mitigation: explicit onboarding and warnings.
- Refresh token expiry in OAuth Testing mode. Mitigation: publish the OAuth app before production.
- Drive version history retains old file versions. Mitigation: delete via files.delete, not trash only.
