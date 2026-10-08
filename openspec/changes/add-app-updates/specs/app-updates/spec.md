## ADDED Requirements

### Requirement: Discover stable updates
The app SHALL compare the installed semantic version with the latest stable GitHub release after privacy consent on each launch, and SHALL provide a manual check in settings.
#### Scenario: New version
- **WHEN** a newer stable release exists
- **THEN** a bottom sheet shows the version, release notes and available actions.
#### Scenario: Manual check feedback
- **WHEN** a manual check finds no update or fails
- **THEN** the bottom sheet reports the result and allows retry on failure.

### Requirement: Skip a release
The app SHALL persist the exact skipped version for automatic checks and SHALL ignore skipping for manual checks.
#### Scenario: Skipped release at startup
- **WHEN** the latest version equals the skipped version
- **THEN** startup does not prompt, but manual checking can show it.
#### Scenario: Subsequent release
- **WHEN** a newer release than the skipped version is published
- **THEN** startup prompts for the new release.

### Requirement: Verified platform downloads
The app SHALL select a compatible platform asset, download with progress, and validate completion before installation. Interrupted, size-mismatched or corrupt cached files SHALL be downloaded again.
#### Scenario: Complete cached package
- **WHEN** a package has a valid completion receipt, expected size and matching SHA-256
- **THEN** the install action reuses it without another download.
#### Scenario: Incomplete download
- **WHEN** only a partial file or invalid completed file exists
- **THEN** a fresh download starts and no partial package is handed to the installer.

### Requirement: System installation
The app SHALL hand verified installers to the OS only after an explicit user action, report handoff failures, and explain any required manual installation steps.
#### Scenario: Supported package
- **WHEN** the user chooses to update with a valid package
- **THEN** the OS installer or package opens and installation guidance is displayed.
#### Scenario: Unsupported platform
- **WHEN** no compatible package is published
- **THEN** the app offers the release page and does not download an incompatible asset.
