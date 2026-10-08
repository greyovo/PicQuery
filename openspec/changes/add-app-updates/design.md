## Context
The Flutter app already has package_info_plus, open_filex, url_launcher, Hive and get_it/watch_it. Release CI publishes Android arm64 APKs, macOS DMGs, Windows x64 installers and Linux x64 archives. Startup occurs after privacy consent.

## Goals / Non-Goals
Goals: discover stable releases, show bottom sheets, persist per-version skips, verify/reuse downloads, and hand off installers. Non-goals: silent installation, bypassing platform permissions, or iOS sideloading.

## Decisions
- macOS releases currently exclude x86_64, so only arm64 receives the DMG. Use GitHub's releases API (the same data as the release page), excluding drafts/prereleases, and pub_semver for correct comparison. Use package_info_plus for the installed version.
- UpdateService owns HTTP, matching, cached files and platform handoff; UpdateManager owns observable UI state and preference decisions. Inject service/preferences for tests.
- Use application-support storage, a .part file and atomic rename. Compare byte count and SHA-256 against asset digest when supplied, and persist a local digest receipt to detect later damage. Without a completion receipt, redownload. Bound network requests and serialize operations.
- Match only published platform/architecture assets. Native OS architecture is obtained through a small platform channel on Android and OS process queries on desktop. Unsupported platforms can open release notes.
- Present all update states in one scrollable bottom sheet; only automatic checks stay quiet on network failure or no update. Manual checks always provide feedback.
- Installation requires the user's button press and uses OS installer handoff. macOS DMG and Linux archives require the final system-guided copy/extraction step; explain this in the sheet.

## Risks / Trade-offs
- GitHub unavailable/rate limited → timeouts, retry and manual feedback.
- Process interrupted or file corrupted → partial file never installable; verify size and digest before every handoff.
- Platform permissions/signing restrictions → surface failure, keep verified package for retry; Android requests unknown-source permission through a native installer channel and a restricted FileProvider.
- Asset naming changes → documented naming contract; unmatched assets fall back to release page without guessing.
