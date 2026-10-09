# Change: Add encrypted Google Drive backup for broker reports

## Why
Users need a recoverable copy of imported broker reports after the original files are
removed from the device, without giving up on-device-only processing or
exposing plaintext data to Google.

## What Changes
- Import broker reports from user-selected files.
- Encrypt each report on-device before upload to Google Drive app data folder.
- Verify the upload before deleting the original file from the device.
- Add key management with a user passphrase or recovery code.
- Encrypt the local database and include an encrypted database backup.
- Add restore flow on new devices.
- Add device hardening measures (screenshots, app switcher, backups, logs, SDK).

## Impact
- New capabilities: report-ingestion, key-management, encrypted-backup,
  local-storage, google-drive-access, device-hardening.
- Requires Google Cloud OAuth clients for Android and iOS.
- Requires user-facing onboarding step for passphrase or recovery code creation.
