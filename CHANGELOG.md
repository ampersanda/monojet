# Changelog

## Mobile [0.1.0] - unreleased

### Added

- Initial iOS / iPadOS Flutter target under `flutter-mobile/`. Same flutter95 chrome as the desktop, with a responsive 600 dp breakpoint: stacked previews + bottom-sheet settings on iPhone, side-by-side previews + persistent sidebar on iPad.
- Pure-Dart conversion engine in `flutter-mobile/src/monojet_mobile/engine.cljd` — replaces the desktop's `bb + magick + gs` subprocess. Implements alpha-flatten over white, gamma (toner-saving), brightness/contrast, LAT (via `gaussianBlur`), Floyd–Steinberg dither, 8×8 Bayer halftone, threshold, gray. **Image only — PDF is intentionally unsupported on mobile.**
- iOS document picker (`file_picker` → `UIDocumentPickerViewController`) for Open, Share Sheet (`share_plus` → `UIActivityViewController`) for Save, AirPrint via `printing` + `pdf` for Print.
- "Auto" mode picks the highest-saved result across a narrowed mobile pool (`text` + `dither` + `halftone`).
- Sibling cljd package `flutter-shared/` consumed by both apps via `:local/root` — `form`, `buttons`, `modal`, `preview`, `stat` widgets are now reused across platforms.

### Not yet shipped

- App Store release. Build-from-source instructions in the README under "Mobile app (iOS / iPadOS) → Build from source".
- CI for mobile (deliberately omitted to keep CI cost down).

## Repo [chore]

- Renamed `flutter/` → `flutter-desktop/` to make room for the sibling mobile target. The Xcode "Bundle monojet runtime" build phase reaches the repo root via `${SRCROOT}/../..`, so the rename doesn't disturb it.

## Desktop [1.2.0]

### Added

- Win95-style chunky marquee progress bar in the Converted preview tile while a conversion is running.

### Changed

- Form/preview/modal/buttons/stat widgets extracted into a sibling `flutter-shared/` cljd package, consumed via `:local/root`. No functional change — just enables reuse with the upcoming mobile app.
- `Container` instances that only set `.color` swapped to `ColoredBox` (cheaper, single-purpose).

## Desktop [1.1.1]

### Changed

- Documentation refresh: README desktop section now leads with feature highlights (previously buried under "Build from source"), `updater.cljd` added to the project tree, CHANGELOG split into CLI/Desktop tracks.

## Desktop [1.1.0]

### Added

- In-app updater: title bar shows the current version, **Check for updates** in the toolbar downloads the latest GitHub release, swaps `monojet.app` in place via a detached shell script, and keeps the previous bundle as `monojet.app.bak`.
- Launch-time update check.

### Changed

- Release artifacts split by architecture (`monojet-desktop-mac-arm64.zip`); Intel matrix leg dropped — `macos-13` runners are too queued in CI.
- PDF DPI slider is hidden when the input is not a PDF.

## Desktop [1.0.0]

### Added

- macOS Flutter desktop app (ClojureDart + flutter95) with drag-and-drop, automatic mode picker, slider-based controls, auto-invert prompt, **Save as…**, and **Print**.
- GitHub Actions release workflow producing signed-by-default zips.

## CLI [0.2.0]

### Added

- PDF input and output (multi-page) via Ghostscript delegate.
- `-D, --density` flag for PDF rasterization DPI (default 150).
- LAT window in `text` mode scales with `-D` so large titles render solid at high DPI.

### Changed

- Renamed binary from `imglaserprintopt` → `monojet`.

## CLI [0.1.0] - 2026-05-31

### Added

- Initial monojet CLI implementation (originally imglaserprintopt)
- Modes: `text` (adaptive threshold), `threshold`, `dither` (Floyd-Steinberg), `halftone` (ordered dither), `gray`
- Toner-saving gamma lightening (`-T` 0..3)
- Auto-invert for dark-background images (`-i`)
- Coverage report: estimated toner usage vs. original
- Batch directory processing (`-d`) with per-file output
- `--version` flag and install script with auto-update support
- CI/CD release workflow via GitHub Actions
