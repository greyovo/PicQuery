# CI workflows

- `flutter.yml`: skips pushes and PR updates that only change `website/` or
  `deploy.yml`. Otherwise, prepares models once, runs analysis and unit tests,
  then runs platform jobs in parallel. Android and macOS each run correctness
  integration tests before packaging on the same runner, reusing their setup and
  native dependency state. PRs run Android tests without the signing environment
  or signed builds; other events run Android tests and builds in `android-build`.
  Windows and Linux have build jobs. `release.yml` waits for the entire reusable
  workflow to succeed before publishing, including all integration tests.
- `release.yml`: runs when `v*` tags are pushed, or via manual dispatch; only
  `greyovo` can publish. Pushes use the tag as the version and the current
  Asia/Shanghai date in `YYMMDD` format as the build number. Manual runs accept
  a required `tag` and optional `build-number` (defaulting to the same date).
  Both localized changelogs must contain exactly one nonempty section matching
  the tag; validation errors stop the workflow before building or publishing.
  Push publication requires the remote tag to exist; manual publication can
  create the tag at the selected workflow commit.
- `models.yml`: checks out the default branch of `greyovo/picquery-models` and
  caches the two MobileCLIP ONNX outputs under `picquery-onnx-<full HEAD SHA>`.
  Any commit on that branch invalidates the cache. There is no fallback key for
  exported models. Cache hits skip Python setup and export; translation and
  tokenizer assets always come from the current PicQuery checkout.
- `benchmarks.yml`: runs performance suites only when manually dispatched. Each
  suite still runs in its own application process.
- `deploy.yml`: deploys Pages only for changes under `website/` or to the deploy
  workflow itself, or when manually dispatched.

`.github/actions/setup-flutter` downloads and imports the complete model bundle,
restores Flutter/pub caches, and resolves dependencies. Consumer checkouts do not
fetch LFS again. `.github/actions/test-android` additionally sets up Java,
restores Gradle state, and runs the selected suites on an emulator. The Android
release build runs after this action in the same job; signing is configured only
after tests pass.

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
