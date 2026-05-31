# Changelog

## [0.1.0] - 2026-05-31

### Added

- Initial imglaserprintopt CLI implementation
- Modes: `text` (adaptive threshold), `threshold`, `dither` (Floyd-Steinberg), `halftone` (ordered dither), `gray`
- Toner-saving gamma lightening (`-T` 0..3)
- Auto-invert for dark-background images (`-i`)
- Coverage report: estimated toner usage vs. original
- Batch directory processing (`-d`) with per-file output
- `--version` flag and install script with auto-update support
- CI/CD release workflow via GitHub Actions
