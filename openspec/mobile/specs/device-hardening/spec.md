## ADDED Requirements

### Requirement: Prevent screenshots of sensitive screens
Screens displaying report data SHALL prevent screenshots and screen recording where the platform allows it.

#### Scenario: Android
- **WHEN** a sensitive screen is displayed on Android
- **THEN** the window is marked with FLAG_SECURE

#### Scenario: iOS app switcher
- **WHEN** the application moves to background
- **THEN** sensitive content is hidden in the app switcher snapshot

### Requirement: Auto-lock
The application SHALL require re-authentication after a configurable inactivity timeout and when moved to background.

#### Scenario: Timeout
- **WHEN** the inactivity timeout expires
- **THEN** the application locks and requires biometric or device credential authentication

### Requirement: No sensitive data in logs
Release builds SHALL NOT log report contents, amounts, account numbers, tax identifiers, keys, or tokens.

#### Scenario: Release logging
- **WHEN** the application runs in release mode
- **THEN** no sensitive values appear in logcat or system logs

### Requirement: Third-party SDK review
Third-party SDKs SHALL NOT collect or transmit report contents or key material. Crash reporting SHALL be configured to exclude user data.

#### Scenario: Crash report
- **WHEN** a crash occurs
- **THEN** the crash report does not include report contents or key material

### Requirement: Integrity verification
The application SHALL verify device and application integrity using Play Integrity (Android) and App Attest (iOS) before enabling backup operations.

#### Scenario: Integrity failure
- **WHEN** integrity verification fails
- **THEN** backup operations are disabled and the user is informed, while local read access follows the documented policy
