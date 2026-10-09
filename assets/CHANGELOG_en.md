**Changelog**

## v2.1.0

**Added**

- Drag a folder into the desktop app to add an album and index its images.
- Added app update checks, with update package downloads and installer handoff.
- Added support for opening images in the Android system gallery (beta).
- Added problem reporting and optional automatic error reporting, with file paths redacted from diagnostic logs.
- Added in-app update log page.

**Improved**

- Show local model preparation progress at startup and allow retrying if preparation fails.
- Keep the search bar above the results, improve thumbnail clarity, and fix distorted aspect ratios in album covers and search thumbnails.

**Fixed**

- Fixed indexing pause states, speed calculations after resuming, and stale update prompts after indexing completes.
- Fixed incorrect image ownership when indexing multiple albums and shared image indexes being removed when deleting an album.
- Fixed the file picker failing to open on macOS.

---

## v2.0.0

1. A completely redesigned interface, rebuilt with Flutter.
2. Upgraded to the unquantized MobileCLIP 2 model for improved search accuracy.
3. Added a built-in lightweight Chinese-to-English translation model—Google services are no longer required.
4. Added desktop support for Windows, macOS, and Linux (Linux is currently untested).
5. Added automatic index update detection. Incomplete album indexing tasks now resume automatically at startup.

