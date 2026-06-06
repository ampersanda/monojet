# Changelog

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
