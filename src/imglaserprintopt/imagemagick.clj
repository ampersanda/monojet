(ns imglaserprintopt.imagemagick
  (:require [babashka.process :as p]
            [clojure.string :as str]))

(defn check-magick!
  "Verify ImageMagick is installed. Throws on failure."
  []
  (try
    (p/shell {:out :string :err :string} "magick" "--version")
    (catch Exception _
      (binding [*out* *err*]
        (println "Error: ImageMagick (magick) not found. Install with: brew install imagemagick"))
      (System/exit 1))))

(defn identify
  "Get image dimensions and format. Returns {:width :height :format}."
  [path]
  (let [result (p/shell {:out :string :err :string}
                        "magick" "identify" "-format" "%w %h %m" (str path))
        parts  (str/split (str/trim (:out result)) #"\s+")]
    {:width  (parse-long (nth parts 0))
     :height (parse-long (nth parts 1))
     :format (nth parts 2)}))

(defn mean-luminance
  "Mean grayscale luminance of the image as 0.0..1.0 (0=black, 1=white).
   Flattens alpha against white so fully-transparent pixels read as paper, not ink."
  [path]
  (let [result (p/shell {:out :string :err :string}
                        "magick" (str path)
                        "-background" "white" "-alpha" "remove" "-alpha" "off"
                        "-colorspace" "Gray"
                        "-format" "%[fx:mean]" "info:")]
    (Double/parseDouble (str/trim (:out result)))))

(defn coverage
  "Fraction of 'ink' on a B&W output, 0.0..1.0.
   Computed as (1 - mean luminance) on a grayscale view."
  [path]
  (- 1.0 (mean-luminance path)))

(defn- mode-args
  "Mode-specific ImageMagick operators.
   `threshold` is a percentage used by the `threshold` mode only.
   `text` uses LAT with a negative offset (bias toward white in flat regions).
   `dither` uses Floyd-Steinberg via `-colors 2`; `-monochrome` is unreliable
   here because it doesn't preserve mean luminance on real-world photos."
  [mode threshold]
  (case mode
    "text"      ["-lat" "20x20-10%"]
    "threshold" ["-threshold" (str threshold "%")]
    "dither"    ["-dither" "FloydSteinberg" "-colors" "2" "-depth" "1"]
    "halftone"  ["-ordered-dither" "h6x6o"]
    "gray"      []))

(defn- gamma-for-toner-saving
  "Gamma value applied via `-gamma`. Higher gamma lightens midtones."
  [level]
  (case (int level)
    0 1.0
    1 1.3
    2 1.6
    3 2.0
    1.6))

(defn convert!
  "Run the ImageMagick pipeline on `in` writing to `out`.
   opts: {:mode :threshold :toner-saving :brightness :contrast :invert?}"
  [in out {:keys [mode threshold toner-saving brightness contrast invert?]}]
  (let [gamma (gamma-for-toner-saving toner-saving)
        bc    (str brightness "x" contrast)
        args  (concat ["magick" (str in)
                       "-background" "white" "-alpha" "remove" "-alpha" "off"]
                      (when invert? ["-negate"])
                      ["-colorspace" "Gray"]
                      (when (not= gamma 1.0)
                        ["-gamma" (str gamma)])
                      (when (or (not (zero? brightness)) (not (zero? contrast)))
                        ["-brightness-contrast" bc])
                      (mode-args mode threshold)
                      [(str out)])]
    (apply p/shell {:out :string :err :string} args)))
