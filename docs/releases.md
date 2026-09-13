# Publishing APKs on GitHub

Users download installable APKs from the **Assets** section of a [GitHub Release](https://github.com/greyovo/PicQuery/releases). GitHub's automatic source archives do not contain APKs. The `Android CI` workflow uploads verified, signed APKs when a release is published, and supports manual backfills of existing releases.

## Repository configuration

Under **Settings → Secrets and variables → Actions**, configure these repository secrets using the **original release keystore**:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded keystore file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing key alias |
| `ANDROID_KEY_PASSWORD` | Signing key password |

Keep credentials out of source control, issues and workflow inputs. A replacement key cannot update an installation signed with the original key. A Google Play upload key may differ from the app signing key; use the certificate that matches the APK distribution being updated.

Models are Git-ignored. Create a ZIP with the following files **at its root**, host it at an HTTPS URL accessible to GitHub runners, and configure repository variables `MODELS_URL` and `MODELS_SHA256` (the ZIP's SHA-256 digest). Both regular CI and releases restore these models. Keep the ZIP immutable and retain a checksum for every release.

| Source version | Required ZIP entries |
|---|---|
| Current MobileCLIP2 flavors | `mobileclip2_s0_image.onnx`, `mobileclip2_s0_text_int8.onnx`, `image_model.tflite`, `text_model_dynamic_wi8.tflite` |
| `v1.1.3` / `v1.1.4` (CLIP) | `clip-image-int8.ort`, `clip-text-int8.ort` |

Use the matching model exports, not renamed substitutes. The workflow copies only the required files and preserves the tracked vocabulary and ML Kit assets. See the [model guide](../script/model-MobileCLIP2/README.md) for current exports.

After these prerequisites are configured, publish a release against its version tag. The workflow builds that exact tag, verifies each APK signature and its packaged models, and uploads APKs plus `SHA256SUMS`. Current builds provide separate ONNX and TFLite APKs; historical builds provide a single APK.

## Backfill v1.1.3 and v1.1.4

If the original signed APKs are available, verify their signatures and inspect their package/version metadata with Android SDK tools, then upload each APK to its matching release:

```bash
apksigner verify --verbose --print-certs /path/to/original-v1.1.3.apk
aapt dump badging /path/to/original-v1.1.3.apk
gh release upload v1.1.3 /path/to/original-v1.1.3.apk --repo greyovo/PicQuery

apksigner verify --verbose --print-certs /path/to/original-v1.1.4.apk
aapt dump badging /path/to/original-v1.1.4.apk
gh release upload v1.1.4 /path/to/original-v1.1.4.apk --repo greyovo/PicQuery
```

Otherwise, once the workflow fix is on `master`, open **Actions → Android CI → Run workflow**, select `master`, enter the existing `release_tag`, and provide that tag's CLIP ZIP URL and checksum via `models_url` and `models_sha256`. Repeat for each tag. CLI equivalent:

```bash
gh workflow run android.yml --repo greyovo/PicQuery --ref master \
  -f release_tag=v1.1.3 \
  -f models_url=https://YOUR_HOST/clip-models.zip \
  -f models_sha256=YOUR_ZIP_SHA256
```

The workflow revision and tagged application source are checked out separately, so backfills can use the fixed tooling even when the old tag has no release automation. Both historical tags select CLIP. **The `v1.1.4` source still declares `versionName = 1.1.3` and `versionCode = 5`, the same as `v1.1.3`.** Document this in its release notes when publishing a rebuild; the workflow does not rewrite historical source or version metadata.

Existing assets are never overwritten automatically. If a run stops because an asset already exists, inspect that release before deciding which missing files to build and upload locally. Releases created using a workflow's `GITHUB_TOKEN` may not trigger another workflow; use the manual dispatch in that case.

Without the original signed APKs or original signing material, historical APK publication remains blocked. An unsigned APK or a development-key build is not a substitute. Verify the public release Assets before marking [issue #41](https://github.com/greyovo/PicQuery/issues/41) resolved.

## Validate changes to the publishing tools

Use Python 3.11 or newer. The release helper and its tests use only the standard library.

```bash
python3 -m unittest discover -s scripts/tests -v
actionlint .github/workflows/android.yml
```

The unit tests exercise model ZIP validation and APK selection with fixture archives. A real release additionally needs the SDK build, signature verification, and upload checks; unit tests alone do not establish that an APK has been published.
