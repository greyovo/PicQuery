# CODEBUDDY.md

## Project Overview

PicQuery — offline image query app. **Features**: index local image folders/albums, search by text (with Chinese→English translation), search by image similarity, folder-scoped search, incremental index updates, dark mode.

## Commands

```bash
flutter analyze lib/ # Dart lint / check dart errors
flutter test # Dart unit tests (db, tokenizers)
flutter build macos # Build / check compile errors
```

如果可以，使用 FVM。将上述命令的 `flutter` 替换为 `fvm flutter` 即可使用 FVM 管理的 Flutter 版本。

## Architecture

```
Flutter UI (lib/)  ←→  Dart Engine (lib/src/engine/)
```

Pure Dart/Flutter stack: all compute runs in Dart — ONNX inference via `flutter_onnxruntime`, image preprocessing via `image` (+`Isolate.run`), CLIP BPE & Marian SentencePiece tokenizers (pure Dart ports), vector DB via `sqlite3` + `sqlite_vector`.

### Dart Engine Modules (`lib/src/engine/`)

- `api.dart` — facade with the same function names/signatures the UI consumes
- `db.dart` — sqlite3 + sqlite_vector (`picquery_v2.db`, WAL, `vector_init`/`vector_full_scan` KNN)
- `ort_engine.dart` — ONNX session management (CLIP visual/text + MarianMT), EP per platform (Apple→CoreML, Android→NNAPI, Windows→DirectML, fallback CPU/XNNPACK)
- `image_preprocess.dart` — decode → shortest-side 256 resize (linear) → center crop 256² → CHW [0,1] float tensor (NO ImageNet norm), runs in `Isolate.run`
- `clip_tokenizer.dart` — CLIP BPE tokenizer (GPT-2 byte-encoder, SOT=49406/EOT=49407, 77 fixed length)
- `sp_tokenizer.dart` — Marian SentencePiece Unigram tokenizer (Viterbi, Metaspace), verified against HF `tokenizers` reference outputs
- `translator.dart` — autoregressive greedy decoding (decoder_start=32000, EOS=0, ≤255 steps)
- `indexer.dart` — folder/album scanning, per-image encode, batch=10 inserts, `Stream<IndexProgress>` with cancel support
- `models.dart` — IndexProgress / IndexStatus / Folder / SearchResult / FolderUpdateInfo

### Flutter State Management

- **Dependency Injection**: `get_it` + `watch_it` (reactive widgets via `WatchingWidget` / `WatchingStatefulWidget`)
- **Managers** (singletons registered in `locator.dart`):
  - `ThemeManager` — theme mode (light/dark/system), persisted via `SettingsStore`
  - `FolderManager` — loads and manages indexed folder list from the DB
  - `IndexingManager` — handles indexing progress, album updates, cancellation; consumes the engine's `Stream<IndexProgress>` (cancel = `subscription.cancel()`)
  - `SearchManager` — manages selected folder IDs for scoped search
- **Stores**: `SettingsStore` — Hive-based persistence for theme mode

### Data Flow

- **Indexing (Desktop)**: folder → `file_picker` → scan recursively → preprocess (resize 256→center crop 256², [0,1] tensor, NO ImageNet norm) → visual ONNX → 512-dim L2-norm embedding → batch (10) transaction insert `images` + `vector_images` → stream `IndexProgress`
- **Indexing (Mobile)**: `photo_manager` album → list image paths → same pipeline as desktop
- **Text Search**: text → (optional) `translator.dart` Chinese→English via MarianMT ONNX → CLIP BPE tokenize (len-77 padded) → text ONNX → L2-norm → KNN → distance→cosine (**measured**: sqlite_vector `distance` is plain L2, so cos_sim = 1 − d²/2; verified by known-vector unit tests in `test/db_test.dart`) → JOIN images
- **Image Search**: query image → same preprocessing → visual ONNX → KNN
- **Incremental Updates**: `checkForUpdates()` compares disk files with DB → `indexPendingUpdates()` adds/removes deltas

### Database Schema (`picquery_v2.db`, SQLite WAL + foreign_keys, sqlite_vector extension)

| Table           | Key Columns                                                                                | Notes                           |
| --------------- | ------------------------------------------------------------------------------------------ | ------------------------------- |
| `folders`       | id, folder_path UNIQUE, indexed_at, image_count                                            |                                 |
| `images`        | id, folder_id FK CASCADE, file_path UNIQUE, file_name, file_size, modified_time, width, height, format, indexed_at, ocr_text | CASCADE delete from folders |
| `vector_images` | rowid = images.id, embedding BLOB (float32 LE, 512-dim) + `vector_init('vector_images','embedding','type=FLOAT32,dimension=512')` | Written via `vector_as_f32(?)` with blob input; delete by rowid separately |

Additional indexes: `idx_images_modified(file_path, modified_time)`, `idx_images_folder(folder_id)`.

Note: the old `picquery.db` (sqlite-vec vec0 format) is intentionally not read; upgrading users re-index.

## Rules

### Desktop Icons

- 如果需要更换桌面端 App 的图标，确保图标文件 `assets/icon-picquery.png` 更新后，运行脚本 `scripts/generate_desktop_icons.dart` 会自动生成对应平台的圆角图标。

### Reference Data

- Tokenizer/model fixtures in `assets/models/` and `test/fixtures/` (HF `tokenizers` reference outputs) must stay in sync with `test/sp_tokenizer_test.dart`.

### Engine Workflow

- All engine changes go through `lib/src/engine/`; keep the `api.dart` facade signatures stable — UI files import only `package:picquery_app/src/engine/api.dart`.
- After editing Dart: `flutter analyze lib/` and `flutter test`, fix errors.
- Tokenizer changes must keep the HF fixture 对拍 (`test/sp_tokenizer_test.dart`) green.

### Packages with Unclear APIs

Flutter packages (`flutter_onnxruntime`, `sqlite_vector`, `sqlite3`, `image`) with unclear APIs → consult docs via MCP context7

### Model Assets

- MobileCLIP export tooling lives in the separate `greyovo/ml-mobileclip` repository; use `uv` as its Python environment manager.
- Translation model: MarianMT Chinese→English (`mt_zho-eng.fp32.quantized.onnx`) + SentencePiece tokenizers (`source_tokenizer.json`, `target_tokenizer.json`)
