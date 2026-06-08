# monojet mobile

iOS / iPadOS UI for [monojet](../README.md). Same flutter95 chrome as the [macOS desktop app](../flutter-desktop/README.md), but with a pure-Dart conversion engine (no `bb` / `magick` / `gs` subprocess — those can't ship on iOS) and a layout that adapts between phone and iPad.

**Not on the App Store yet.** Build it yourself to try it — see below.

## Features

- Drop a file via the iOS document picker (Files / iCloud Drive / Photos via Files app integration). Conversion runs automatically.
- **Automatic** mode (default) races a small mode pool (`text` + `dither` + `halftone`) and shows the result of whichever saved the most toner. A small "auto · dither" caption under the toner-saved % reveals which mode won.
- After a non-inverted run, a silent inverted run kicks off. If it would save ≥ 5 pp more, a modal pops with the comparison and a one-click "Use invert" action.
- Threshold / brightness / contrast are integer sliders; mode and toner-saving are dropdowns.
- **Save** opens the iOS Share Sheet (Save to Files, AirDrop, Photos, Mail, …).
- **Print** uses AirPrint — the raster output is wrapped in a single-page PDF on the fly.
- Every settings change re-runs conversion after a 400 ms debounce.

## Layout

LayoutBuilder switches on the constraints' max width:

- **< 600 dp (phone)**: stacked previews (Original on top, Converted below), a status row, and a toolbar of icon buttons (Settings, Save, Print). Settings opens a flutter95-styled bottom sheet via `family_bottom_sheet`.
- **≥ 600 dp (iPad)**: side-by-side previews + persistent right sidebar (`config-panel`). Mirrors the desktop layout.

## Run

```bash
flutter pub get
clojure -M:cljd compile

# Run on the first available booted iOS simulator:
flutter run

# Build a release .ipa (unsigned):
flutter build ios --release --no-codesign
```

Hot-reload picks up most widget edits. Capital `R` (hot-restart) is needed for top-level `def`s, `defonce` atoms, or new ns-level requires.

For App Store / device install you still need to open `ios/Runner.xcworkspace` in Xcode and sign with your Apple Developer team.

## Constraints

- **Image only.** PDF is not supported on mobile. iOS doesn't ship Ghostscript and bundling a static build would balloon the IPA without enough payoff.
- **Pure-Dart engine.** Conversion runs in `Future.microtask` on the UI isolate. Typical phone screenshots (2-3 MP) take a few hundred ms; larger inputs can hit a second or more. A future optimisation is to swap the microtask for `compute()` (isolate offload). Mobile auto-mode pool is intentionally narrower than desktop's full pool for the same reason.
- **Not on the App Store.** Build-from-source only for now.
- **No CI.** Each build is a manual local run, by design.

## Project layout

```
deps.edn                              # ClojureDart deps + :cljd/opts {:main monojet-mobile.core}
                                      # + :local/root → ../flutter-shared
pubspec.yaml                          # flutter95, image, file_picker, printing, pdf,
                                      # share_plus, navigation_utils, family_bottom_sheet, …
src/monojet_mobile/
  core.cljd                           # Top-level UI: LayoutBuilder phone/iPad split,
                                      # picker, share, AirPrint, modals
  state.cljd                          # Constants, default-config, app-state atom
                                      # (no subprocess fields, no PDF, no updater)
  engine.cljd                         # Pure-Dart conversion pipeline. Mirrors
                                      # monojet.imagemagick.convert! from desktop.
lib/
  main.dart                           # 1-line re-export of the compiled entry
ios/                                  # Flutter iOS scaffold (Xcode project, Info.plist)
```

The `../flutter-shared/` package provides `monojet-shared.widgets.{form,buttons,modal,preview,stat}` — these are shared with the desktop app verbatim.

## Engine notes

`monojet_mobile.engine/convert!` mirrors `monojet.imagemagick.convert!` on desktop, but using the [`image`](https://pub.dev/packages/image) package instead of an ImageMagick subprocess. The mapping:

| CLI step                          | `image` package equivalent                                      |
| ---                               | ---                                                             |
| `-background white -alpha remove` | Per-pixel alpha-over-white loop                                  |
| `-negate`                         | `invert(image)`                                                  |
| `-colorspace Gray`                | `grayscale(image)`                                               |
| `-gamma G`                        | `adjustColor(image, gamma: G)`                                   |
| `-brightness-contrast B×C`        | `adjustColor(image, brightness: 1+B/100, contrast: 1+C/100)`     |
| `text` (`-lat 20x20-10%`)         | `gaussianBlur(radius: 10)` + per-pixel compare with 25-unit bias |
| `threshold`                       | Per-pixel binarize against `2.55 × pct`                          |
| `dither` (FS, `-colors 2`)        | Hand-rolled Floyd–Steinberg (7/3/5/1 over 16)                    |
| `halftone` (`h6x6o`)              | Hand-rolled 8×8 Bayer ordered dither                             |
| `gray`                            | Passthrough after grayscale + gamma                              |

PDF input/output is intentionally absent. Density / DPI fields are absent.

## License

MIT — same as the parent CLI.
