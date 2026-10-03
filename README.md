# PicQuery

English | [中文](README_zh.md)

![PicQuery](README_assets/cover_en.jpg)

PicQuery is an offline image search app. It indexes local folders or photo albums and lets you search them with text or a similar image. Photos, indexes, and model inference stay on your device.

Features include folder and album indexing, Chinese-to-English query translation, image similarity search, folder-scoped search, incremental index updates, and dark mode.

> ✨ PicQuery V2 has been completely rewritten in Flutter and adds desktop support for Windows, macOS, and Linux. Its database is not compatible with the previous version, so you must rebuild your image indexes after upgrading.
>
> The Play Store version has not yet been updated.

## Install

Download the package for your platform from [GitHub Releases](https://github.com/greyovo/PicQuery/releases).

### macOS

Because the app is not signed or notarized with a paid Apple Developer account, macOS may prevent it from opening. To allow it, open Terminal and run:

```bash
sudo xattr -rd com.apple.quarantine "/Applications/PicQuery.app"
```

## Build from source

Install Git, [Git LFS](https://git-lfs.com/), [FVM](https://fvm.app/), and the Flutter toolchain required by your target platform. The repository's `.fvmrc` selects Flutter stable. You may replace `fvm flutter` with `flutter` below if you do not use FVM.

```bash
git lfs install
git clone https://github.com/greyovo/PicQuery.git
cd PicQuery
git lfs pull
fvm use
fvm flutter pub get
```

Before running or building the app, `assets/models/` must contain:

- `mobileclip2_s0_visual.onnx`
- `mobileclip2_s0_text.onnx`
- `mt_zho-eng.fp32.quantized.onnx`

The translation model is stored with Git LFS and is downloaded by a normal LFS-enabled clone. Export the two MobileCLIP models with the separate [`greyovo/ml-mobileclip`](https://github.com/greyovo/ml-mobileclip) repository, then copy them to `assets/models/` under the filenames above.

Run the app on a connected device or desktop:

```bash
fvm flutter run
```

Common checks and builds:

```bash
fvm flutter analyze lib/
fvm flutter test
fvm flutter build apk --release --target-platform=android-arm64
fvm flutter build macos
fvm flutter build windows
```

Only run a platform build on a supported host with that platform's Flutter dependencies installed. The core engine lives in `lib/src/engine/`; it uses `flutter_onnxruntime` for on-device ONNX inference and SQLite for image metadata and vector search.

## Contributing and acknowledgments

Issues and pull requests are welcome. Please include reproduction steps, the affected platform, and relevant check output. Model or preprocessing changes should include numerical and retrieval validation.

PicQuery builds on Apple [MobileCLIP](https://github.com/apple/ml-mobileclip). Thanks to [@mazzzystar](https://github.com/mazzzystar) and [@Young-Flash](https://github.com/Young-Flash) for their help; see the [original discussion](https://github.com/mazzzystar/Queryable/issues/12).

- [mazzzystar/Queryable](https://github.com/mazzzystar/Queryable), the inspiration for this project and an [iOS app](https://apps.apple.com/us/app/queryable-find-photo-by-text/id1661598353)
- [IacobIonut01/Gallery](https://github.com/IacobIonut01/Gallery)

## License

The project source is available under the [MIT License](LICENSE). Third-party dependencies and model assets retain their respective licenses, including the [Apple MobileCLIP model license](https://github.com/apple/ml-mobileclip/blob/main/LICENSE_MODELS).
