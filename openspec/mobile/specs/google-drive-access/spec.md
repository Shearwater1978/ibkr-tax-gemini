## ADDED Requirements

### Requirement: OAuth sign-in with Google
The application SHALL authenticate users with Google using OAuth 2.0 with platform-specific client IDs.
The application SHALL NOT embed client secrets in the binary.

#### Scenario: First sign-in
- **WHEN** the user taps sign in with Google
- **THEN** the consent screen requests only the drive.appdata scope

#### Scenario: No secrets in binary
- **WHEN** the release build is inspected
- **THEN** no client secret or refresh token is present in the binary or resources

### Requirement: Token storage
Refresh tokens SHALL be stored in Android Keystore or Keychain and SHALL NOT be stored in plaintext files.

#### Scenario: Token persistence
- **WHEN** a refresh token is received
- **THEN** it is stored encrypted using the platform secure storage

### Requirement: Handling revoked access
The application SHALL detect revoked or expired access and prompt the user to sign in again without losing local data.

#### Scenario: Access revoked by user
- **WHEN** the Drive API returns an authorization error due to revoked access
- **THEN** the application clears the token, keeps local data, and prompts re-authentication

### Requirement: Sign-out
Signing out SHALL revoke or clear the local token and SHALL NOT delete existing backups in Drive unless the user requests it.

#### Scenario: Sign-out
- **WHEN** the user signs out
- **THEN** local tokens are removed and Drive backups remain intact
