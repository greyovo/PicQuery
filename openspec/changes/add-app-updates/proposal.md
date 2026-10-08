## Why
Users currently need to find releases and download updates themselves. Startup discovery and a manual check should make installing the correct release straightforward.

## What Changes
- Check GitHub stable releases at startup after privacy consent, compare semantic versions, and present updates in a bottom sheet.
- Offer manual checks and persist skipping a specific release (manual checks override skipping).
- Download matching platform installers with progress; reuse verified complete downloads and restart incomplete downloads.
- Hand packages to the platform installer, with explicit fallback for platforms without a published installer.

## Capabilities
### New Capabilities
- `app-updates`: Release discovery, version skipping, download verification and installer handoff.
### Modified Capabilities
None.

## Impact
App shell, settings, dependency registration, Hive preferences, localized strings, Android installation permissions, a new update service/manager and tests. Engine API is unchanged.
