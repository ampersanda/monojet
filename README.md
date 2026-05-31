# imglaserprintopt

CLI tool to convert images (and PDFs) to black-and-white optimized for **cheap laserjet printing**. Built with [Babashka](https://babashka.org/) (Clojure) and [ImageMagick](https://imagemagick.org/).

Unlike a generic grayscale conversion, this tool is aimed at minimizing toner usage: it lightens midtones with gamma correction, optionally inverts dark backgrounds (so a black page becomes a white page), and picks a B&W rendering that matches the content type — local adaptive threshold for text, error-diffusion or halftone for photos. Multi-page PDFs are supported in and out. Each run prints a coverage report so you can see roughly how much toner you'll save.

## Install

The install script checks that all requirements are present before downloading.

```bash
curl -fsSL https://raw.githubusercontent.com/ampersanda/image-laser-print/main/install.sh | bash
```

### Requirements

The following must be installed **before** running the install script:

- [Babashka](https://github.com/babashka/babashka) (bb)
- [ImageMagick](https://imagemagick.org/) (magick)
- [Ghostscript](https://www.ghostscript.com/) (gs) — required only if you want to convert PDFs

```bash
brew install borkdude/brew/babashka imagemagick ghostscript
```

The script installs `imglaserprintopt` to `~/.local/bin`. Make sure it is in your `PATH`:

```bash
export PATH="${HOME}/.local/bin:${PATH}"
```

### Update

Re-run the install command. It detects existing installs and updates in place:

```bash
curl -fsSL https://raw.githubusercontent.com/ampersanda/image-laser-print/main/install.sh | bash
```

### Uninstall

```bash
rm ~/.local/bin/imglaserprintopt
```

## Usage

If installed via the install script:

```bash
# Convert a single image (default mode: text)
imglaserprintopt screenshot.png -o printable.png

# Convert all images in a directory; outputs go to ./bw-output/
imglaserprintopt -d screenshots/

# Photo-friendly Floyd-Steinberg dithering, with aggressive toner saving
imglaserprintopt photo.jpg -m dither -T 3 -o photo-bw.png

# Auto-invert if the background is dark (e.g. dark-mode terminal screenshots)
imglaserprintopt -i terminal.png -o terminal-bw.png

# Verbose output (per-image stats)
imglaserprintopt -v -d screenshots/ -o output/

# PDF in -> B&W PDF out (multi-page preserved)
imglaserprintopt zine.pdf -m text -o zine-bw.pdf

# Higher DPI rasterization for sharper PDF output
imglaserprintopt zine.pdf -m text -D 300 -o zine-bw.pdf
```

Or run directly with Babashka:

```bash
bb convert -- image.png -o out.png
bb convert -- -d screenshots/ -o output/
bb -m imglaserprintopt.core image.png -o out.png
```

## Options

| Flag | Description | Default |
|---|---|---|
| `-o, --output` | Output file (single input) or directory (multiple) | `bw-<input>` / `./bw-output/` |
| `-d, --dir` | Read all images from directory | |
| `-m, --mode` | `text`, `threshold`, `dither`, `halftone`, `gray` | `text` |
| `-t, --threshold` | Threshold % (text/threshold modes, 0-100) | 50 |
| `-T, --toner-saving` | Toner-saving lightening: 0 (off) - 3 (aggressive) | 2 |
| `-b, --brightness` | Brightness adjustment (-100..100) | 0 |
| `-c, --contrast` | Contrast adjustment (-100..100) | 0 |
| `-i, --auto-invert` | Invert if dark background detected | off |
| `--invert` | Force invert | off |
| `-D, --density` | Rasterization DPI for PDF input (30-600) | 150 |
| `-v, --verbose` | Print progress info | |
| `-V, --version` | Show version | |
| `-h, --help` | Show help | |

## Modes

| Mode | When to use | What it does |
|---|---|---|
| `text` | Documents, screenshots, code | ImageMagick `-lat 20x20` local adaptive threshold; keeps text crisp on uneven backgrounds |
| `threshold` | High-contrast B&W source | Hard global threshold; smallest output, no midtones |
| `dither` | Photos | Floyd-Steinberg error diffusion (`-colors 2`); fine detail, smooth gradients |
| `halftone` | Photos for newspaper-like look | Ordered dither (`h6x6o`); regular dot pattern |
| `gray` | Charts/diagrams | Lightened grayscale (no B&W conversion); use when toner-saving alone is enough |

> **Avoid `threshold` mode on images with large solid color regions** (e.g. ID cards with a coloured photo background, brand banners). A flat mid-tone block will collapse to solid black, which is the opposite of toner saving. Use `dither` or `halftone` instead — both reproduce solid colours as sparse dot patterns.

## How it works

1. Reads the image with ImageMagick and computes its mean luminance.
2. If `-i` is set and the image is mostly dark, the pipeline starts with `-negate` so the background becomes white (massive toner savings on dark-mode UIs).
3. Converts to grayscale, then applies a gamma curve (`-evaluate Pow`) tuned by `-T` to lighten midtones — gray boxes, light shadows and anti-aliased edges become lighter or disappear entirely.
4. Applies the chosen mode-specific operator (adaptive threshold, ordered dither, etc.).
5. Re-measures luminance of the output to estimate ink coverage and report toner savings vs. the original.

A typical UI screenshot drops from ~40% ink coverage to ~5–10% after `text` mode with `-T 2`.

## PDF support

Both input and output PDFs are handled when Ghostscript is available. Multi-page input PDFs are rasterized page-by-page at `-D` DPI (default 150). If your output extension is `.pdf`, all pages are written into a single multi-page B&W PDF. The `text` mode's local-window size scales with `-D` so titles in large fonts don't render as outlines at higher DPI.

For text-heavy documents start with `-m text -T 1`. For zines or comics with photos, `-m halftone -T 2` keeps photos legible while staying toner-cheap.

## Project structure

```
bb.edn                            # Project config (no external deps)
imglaserprintopt.bb               # Entry point for uberscript bundling
src/imglaserprintopt/
  core.clj                        # CLI parsing, orchestration, reporting
  imagemagick.clj                 # ImageMagick interop (identify, analyze, convert)
```

## References

- [ImageMagick: Local Adaptive Thresholding](https://imagemagick.org/script/command-line-options.php#lat) - used for the `text` mode
- [ImageMagick: Quantization & Dithering](https://imagemagick.org/Usage/quantize/) - background on `-monochrome` and `-ordered-dither`
- [HP toner-saving guidance](https://support.hp.com/) - inspiration for the toner-saving gamma defaults

## License

MIT
