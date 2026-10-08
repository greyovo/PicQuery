# Sentry configuration

The SDK is wired for Flutter/Dart exceptions and native crashes on supported platforms. Without a DSN, the app stays offline and manual reports show “not configured”.

## Local builds

Pass the public project DSN when compiling:

```sh
fvm flutter run --dart-define=SENTRY_DSN=https://PUBLIC_KEY@HOST/PROJECT_ID --dart-define=SENTRY_ENVIRONMENT=development
```

`ci/build.sh` accepts the same values through `SENTRY_DSN` and `SENTRY_ENVIRONMENT` environment variables. GitHub Actions reads the optional `SENTRY_DSN` repository secret; release builds use `production`. Empty values disable transmission. Release metadata comes from the app version and build number.

The DSN is the runtime ingestion address, not a Sentry auth token. Do not embed `SENTRY_AUTH_TOKEN` in Dart defines, app assets or source files. An auth token and project/organization details are only needed for a separate build-time debug-symbol upload setup. Symbol upload is not configured yet; use that setup to symbolicate production native crash stacks and any future obfuscated Dart builds.

## Collection behavior

- Automatic reporting defaults to off. The revised privacy notice requires a fresh choice from existing users. Rejecting reporting permits normal app usage.
- Settings can change the saved preference. The native Flutter SDK is initialized only after opt-in, and closed on opt-out.
- Framework/platform errors use the SDK handlers. The app's guarded async zone and caught errors emitted through `Logger` are reported through the logging listener. Keep caught errors logged with both error and stack trace; avoid logging the same failure at several layers.
- Ordinary empty results offer a manual report button. Index stream errors and partial per-image failures show a report prompt in album management. Report buttons prevent duplicate submission while sending or after success.
- Clicking a report sends that report via an isolated Dart hub and does not turn on automatic/native collection. Manual submission times out after 15 seconds and can be retried on failure.
- Dart events remove user/request/server-name fields and recursively redact paths. The latest flushed application log is attached with a 256 KiB bound. Images, embeddings, raw query text, screenshots, widget hierarchies, session recording and performance tracing are not deliberately collected.
- App log files and console records redact paths independently of Sentry, including exception text and stack traces. Existing local logs are sanitized at startup. Native crash metadata is supplied by the platform SDK and does not pass through the Dart `beforeSend` hook.

The full bilingual privacy policy is bundled for offline viewing and is maintained in `website/docs/privacy-policy.md`.

## Production project setup

When the real project is available, record its hosting region and retention period in the privacy policy, configure server-side data scrubbing (including native diagnostic fields and IP addresses), and verify a handled exception, an uncaught error, a native crash and both manual report flows against that project. Client-side opt-out stops future automatic reporting; previously received events must be removed through Sentry if deletion is requested.

Relevant primary references: [Flutter SDK](https://docs.sentry.io/platforms/dart/guides/flutter/), [Sentry privacy](https://sentry.io/privacy/), [project data scrubbing settings](https://docs.sentry.io/api/projects/update-a-project/).

## Verification

```sh
fvm flutter analyze lib/
fvm flutter test
# Dummy DSN + mocked HTTP transport; no external requests are sent:
fvm flutter test test/error_reporting_test.dart --dart-define=SENTRY_DSN=https://public@example.com/1
fvm flutter build macos --debug
```
