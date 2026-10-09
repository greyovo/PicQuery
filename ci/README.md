# CI workflows

- `flutter.yml`: prepares models once, runs unit and correctness integration tests,
  and builds all platforms. Builds wait for unit tests but overlap integration
  tests. `release.yml` waits for the entire reusable workflow to succeed before
  publishing, including both integration test jobs.
- `models.yml`: checks out the default branch of `greyovo/picquery-models` and
  caches the two MobileCLIP ONNX outputs under `picquery-onnx-<full HEAD SHA>`.
  Any commit on that branch invalidates the cache. There is no fallback key for
  exported models. Cache hits skip Python setup and export; translation and
  tokenizer assets always come from the current PicQuery checkout.
- `benchmarks.yml`: runs performance suites manually or weekly on Monday at
  10:00 Asia/Shanghai. Each suite still runs in its own application process.
- `deploy.yml`: deploys Pages only for changes under `website/` or to the deploy
  workflow itself, or when manually dispatched.

`.github/actions/setup-flutter` downloads and imports the complete model bundle,
restores Flutter/pub caches, and resolves dependencies. Consumer checkouts do not
fetch LFS again. Android jobs also restore Gradle state.

`resolve_version.sh` resolves `VERSION_INPUT` (defaults to `pubspec.yaml`) and
`BUILD_NUMBER_INPUT` (defaults to the date in Asia/Shanghai), validates them, and
outputs `version`, `build_number`, and `build_date`. Every platform uses the same
metadata. `build.sh` can still export models locally; CI sets
`PICQUERY_MODELS_BUNDLE_DIR=assets/models` to reuse the imported assets.

Local integration commands:

```bash
bash ci/test_integration.sh macos                         # correctness only
bash ci/test_integration.sh macos macos benchmarks        # performance only
bash ci/test_integration.sh android emulator-5554 all     # all Android suites
```
