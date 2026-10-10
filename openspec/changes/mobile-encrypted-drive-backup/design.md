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
device the user picks the same folder to restore. (Replaces the earlier `drive.appdata` design after
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
- The full identity is kept only where the tax output requires it (generated tax report
  and its export). This is a documented exception.
- Pseudonymized data remains personal data under GDPR. True anonymization (no reasonable
  means of re-identification) is applied only for aggregated statistics, if any are produced.
- Keys used for pseudonymization are never included in backups in plaintext, and the
  pseudonym mapping is rebuilt from the data key or re-derived on restore.

## Decision 8: Transfers and data subject rights
- The user chooses the storage provider; only end-to-end encrypted files reach it. The privacy notice
  explains this, including that providers may store data outside the EEA.
- Erasure request from the user: delete local data, delete the backup files and manifest in the backup
  folder, and destroy the keys (device key entries and the data key). Provider trash or version history
  may keep copies; without the keys they cannot be decrypted (crypto-shredding).
- Portability: the user can export the tax output in a standard format (CSV or PDF).

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
