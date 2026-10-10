# Change: Add encrypted backup for broker reports to a user-chosen folder

## Why
Users need a recoverable copy of imported broker reports after the original files are
removed from the device, without giving up on-device-only processing or
exposing plaintext data to the storage provider. Personal data must be minimized and pseudonymized
wherever the full identity is not required.

## What Changes
- Import broker reports from user-selected files.
- Encrypt each report on-device before writing it to a backup folder the user chooses once
  (Google Drive, another cloud storage app, or the device), via the Android Storage Access Framework.
- Verify the written backup before deleting the original file from the device.
- Add key management with a user passphrase or recovery code.
- Encrypt the local database and include an encrypted database backup.
- Add restore flow on new devices.
- Add device hardening measures (screenshots, app switcher, backups, logs, SDK).
- Add data anonymization and pseudonymization for non-essential processing paths
  (logs, diagnostics, analytics, UI masking, statistics).

## Impact
- New capabilities: report-ingestion, key-management, encrypted-backup,
  local-storage, backup-location, device-hardening, data-anonymization.
- Needs no Google Cloud project, OAuth client, or consent screen; the app makes no backup network requests itself.
- Requires user-facing onboarding step for passphrase or recovery code creation.
- Requires privacy notice and documented legal basis; the privacy notice explains that encrypted backups go
  to the storage provider the user chooses (GDPR).
