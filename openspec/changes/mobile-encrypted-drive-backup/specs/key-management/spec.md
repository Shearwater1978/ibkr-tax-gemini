## ADDED Requirements

### Requirement: Device-bound key protection
The application SHALL protect the device key using Android Keystore or Apple Secure Enclave.
The device key SHALL be non-exportable.

#### Scenario: Key generation on first launch
- **WHEN** the application is launched for the first time
- **THEN** a device key is generated inside the hardware-backed keystore and marked non-exportable

#### Scenario: Key unavailable
- **WHEN** the hardware-backed keystore is unavailable on the device
- **THEN** the application SHALL NOT fall back to storing the key in plaintext files

### Requirement: Passphrase-derived key encryption key
The application SHALL derive a Key Encryption Key (KEK) from a user passphrase using Argon2id
with a unique random salt per user. The KEK SHALL NOT be stored or uploaded.

#### Scenario: Passphrase setup
- **WHEN** the user creates a backup passphrase
- **THEN** the application derives the KEK, wraps the data key, and stores only the wrapped key and salt

#### Scenario: Weak passphrase
- **WHEN** the user enters a passphrase shorter than the minimum length or below the strength threshold
- **THEN** the application rejects the passphrase and explains the requirement

### Requirement: Recovery code
The application SHALL generate a high-entropy recovery code during onboarding and show it to the user once.
The recovery code SHALL be able to unwrap the data key independently of the passphrase.

#### Scenario: Recovery code display
- **WHEN** the recovery code is generated
- **THEN** it is displayed once with instructions to store it securely and the user must confirm it was saved

#### Scenario: Restore with recovery code
- **WHEN** the user provides a valid recovery code on a new device
- **THEN** the data key is unwrapped and backups become readable

### Requirement: Passphrase change without re-encryption
Changing the backup passphrase SHALL re-wrap the data key only and SHALL NOT require re-encrypting all backup files.

#### Scenario: Passphrase change
- **WHEN** the user changes the passphrase
- **THEN** the application re-wraps the data key with the new KEK and updates the manifest

### Requirement: Data key is never stored in plaintext
The data key SHALL NOT be written to disk, logs, or the backup folder in plaintext.

#### Scenario: Manifest inspection
- **WHEN** the backup manifest is inspected
- **THEN** it contains only the wrapped data key, KDF parameters, and salt

### Requirement: Automatic key restore with Android Block Store
The application SHALL store the data key in Android Block Store with cloud backup only when Block Store reports that
end-to-end encryption is available. On a new device restored from the user's Android backup, the application SHALL use
that key to restore without asking for the passphrase. The passphrase and recovery code SHALL remain available as fallback.

#### Scenario: New phone restored from the Android backup
- **WHEN** the user restores a new phone from their Android backup and opens the application
- **THEN** the data key is retrieved from Block Store, checked against the backup manifest, and used without a passphrase

#### Scenario: End-to-end encryption unavailable
- **WHEN** Block Store reports that end-to-end encryption is not available, for example because the device has no screen lock
- **THEN** the data key is not stored in Block Store and restore requires the passphrase or recovery code

#### Scenario: Key not found
- **WHEN** no data key is found in Block Store, for example on a phone set up as new or on another platform
- **THEN** the application asks for the passphrase or recovery code

#### Scenario: Erasure
- **WHEN** the user erases all data
- **THEN** the data key is also deleted from Block Store
