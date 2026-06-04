# monojet desktop

macOS desktop UI for [monojet](../README.md) — drop an image or PDF, get a laser-printer-ready B&W version. Built with [ClojureDart](https://github.com/Tensegritics/ClojureDart) + Flutter + [flutter95](https://pub.dev/packages/flutter95).

## Features

- Drag-and-drop or click the **Original** pane to load a file. Conversion runs automatically, no Convert button.
- **Automatic** mode (default) runs every CLI mode in parallel, picks the highest `saved=` percentage, and shows which mode won as a sub-caption under the toner-saved % stat.
- After a non-inverted run, a silent inverted run is launched in the background. If it would save ≥ 5 pp more, a modal pops with the comparison and a one-click "Use invert" action.
- Threshold / brightness / contrast are integer sliders; mode and toner-saving are dropdowns; the path/script/PATH fields hide behind a **Show advanced options** toggle inside the sidebar.
- **Save as…** writes the converted file anywhere. **Print** opens a confirmation modal before shelling out to `lp`.
- Every settings change re-runs conversion after a 400 ms debounce.
- a11y: buttons wrap in `Semantics(button: true, label, enabled)` + `Tooltip`, the preview image gets a `semanticLabel`, and disabled buttons render in `disabledTextStyle` so the state is visible.

## Run in dev

```bash
clj -M:cljd flutter -d macos
```

Compiles `src/monojet_desktop/**.cljd` → `lib/cljd-out/...` and launches the .app. Hot-reload picks up most widget edits; capital `R` (hot-restart) is needed for top-level `def`s, `defonce` atoms, or Swift changes.

## Build a release .app

```bash
flutter build macos
open build/macos/Build/Products/Release/monojet.app
```

Or trigger the GitHub Actions workflow at `.github/workflows/desktop.yml` (manual `workflow_dispatch` only — no auto-run on push/PR yet).

## Runtime requirements

The app shells out to `bb monojet.bb …`. The Xcode build phase **Bundle monojet runtime** (in `macos/Runner.xcodeproj`) copies the parent repo's `bb`, `monojet.bb`, `bb.edn`, and `src/` into `Contents/Resources/monojet/` at build time, so users don't need Babashka installed. They DO still need:

- `magick` (ImageMagick) — `brew install imagemagick`
- `gs` (Ghostscript) — `brew install ghostscript`, only required for PDF input/output

The subprocess `PATH` defaults to `/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin` so a Homebrew install resolves. Override in **Show advanced options** if needed.

macOS deployment target is **11.0**; minimum window size is **880 × 540** (set in `MainFlutterWindow.swift`).

## Project layout

```
deps.edn                              # ClojureDart deps + :cljd/opts {:main monojet-desktop.core}
cljd.edn                              # (vestigial, kept for compatibility)
pubspec.yaml                          # Flutter deps
src/monojet_desktop/
  core.cljd                           # Top-level UI, subprocess plumbing, modals
  state.cljd                          # Constants, default-config, defonce app-state
  widgets/
    form.cljd                         # labeled-field, num-field, slider-field, checkbox-row, enum-popup
    buttons.cljd                      # icon-button, chevron-toggle (a11y-wrapped)
    preview.cljd                      # tappable image/PDF preview tile
    stat.cljd                         # big-number stat card with optional sub-caption
    modal.cljd                        # reusable centered modal-card
lib/
  main.dart                           # 1-line re-export of the compiled entry
macos/
  Runner.xcodeproj/                   # Xcode project (with Bundle monojet runtime phase)
  Runner/
    MainFlutterWindow.swift           # contentMinSize 880×540
    Configs/AppInfo.xcconfig          # PRODUCT_NAME=monojet, bundle id dev.ampersanda.monojet
    DebugProfile.entitlements         # sandbox disabled (subprocess + arbitrary file reads)
    Release.entitlements              # same
    Assets.xcassets/AppIcon.appiconset/  # two-dark-circles icon (transparent canvas)
```

## State & UI

`state.cljd` defines a single `defonce app-state` atom. Local UI state (e.g. the sidebar's advanced-options toggle) is held inside the relevant widget via `cljd.flutter/widget :managed`.

```
MaterialApp                          (flutter95 needs a Material root)
  Scaffold95                         (Win95 chrome: title bar + toolbar + body)
    toolbar: "Open file"
    body: Stack
      ├── Column
      │   ├── Row
      │   │   ├── Stack
      │   │   │   ├── Row [previews]                  (Original / Converted tiles)
      │   │   │   └── Positioned [chevron + big-number stat]
      │   │   └── AnimatedContainer (right sidebar 0↔320 px)
      │   │       └── OverflowBox  → config-panel  (sliders, dropdowns, advanced toggle)
      │   └── bottom-bar [status, Print, Save as…]
      └── render-modal                                 (invert suggestion or print confirm)
```

Config changes route through `update-cfg!`, which schedules a 400 ms debounced re-convert via `schedule-convert!`. Drop and Pick fire `run-convert!` immediately and reset all conversion-derived state.

## Conversion subprocess

`run-convert!` dispatches on `:mode`:

- `"auto"` → `run-convert-auto!` runs `run-convert-once!` for every CLI mode in parallel via `Future.wait`, then picks the run with the highest `saved=` percentage. Sets `:auto-chosen-mode` so the stat card shows e.g. `auto · dither`.
- Any other value → `run-convert-once!` once, then `maybe-check-invert!` runs a silent comparison with `:invert? true`. If that beats the normal run by ≥ 5 pp it stages an `:invert` modal.

The argv looks like:

```
<bundled-bb> <bundled-monojet.bb> <input> -o <output> -m <mode> -t <threshold> -T <toner-saving> -b <brightness> -c <contrast> -D <density> [-i] [--invert]
```

…with `workingDirectory` set to the bundled resources dir (so `bb` finds `bb.edn`) and `environment {"PATH" extra-path}` overlaid on the inherited env. Output goes to `~/Library/Caches/monojet-desktop/bw-<input>-<ms>.<ext>`; a timestamped filename prevents Flutter's `Image.file` cache from hanging onto stale bitmaps.

## License

MIT — same as the parent CLI.
