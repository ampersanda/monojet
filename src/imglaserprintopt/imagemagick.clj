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

(defn- pdf?
  [path]
  (some-> path str str/lower-case (str/ends-with? ".pdf")))

(defn- density-args
  [density]
  (when density ["-density" (str density)]))

(defn identify
  "Get image dimensions and format. Returns {:width :height :format :pages}.
   For multi-page inputs (PDFs), reports the first page's dimensions."
  [path & {:keys [density]}]
  (let [pages-r (p/shell {:out :string :err :string}
                         "magick" "identify" "-format" "%p\n" (str path))
        n       (count (str/split-lines (str/trim (:out pages-r))))
        args    (concat ["magick" "identify"]
                        (when (pdf? path) (density-args (or density 150)))
                        ["-format" "%w %h %m" (str (str path "[0]"))])
        result  (apply p/shell {:out :string :err :string} args)
        parts   (str/split (str/trim (:out result)) #"\s+")]
    {:width  (parse-long (nth parts 0))
     :height (parse-long (nth parts 1))
     :format (nth parts 2)
     :pages  n}))

(defn mean-luminance
  "Mean grayscale luminance of the image as 0.0..1.0 (0=black, 1=white).
   For multi-page PDFs, the mean is averaged across all pages.
   Flattens alpha against white so fully-transparent pixels read as paper, not ink."
  [path & {:keys [density]}]
  (let [args   (concat ["magick"]
                       (when (pdf? path) (density-args (or density 150)))
                       [(str path)
                        "-background" "white" "-alpha" "remove" "-alpha" "off"
                        "-colorspace" "Gray"
                        "-format" "%[fx:mean]\n" "info:"])
        result (apply p/shell {:out :string :err :string} args)
        means  (->> (str/split-lines (str/trim (:out result)))
                    (map #(Double/parseDouble (str/trim %))))]
    (/ (reduce + means) (count means))))

(defn- lat-window
  "Scale the LAT window with density so it doesn't collapse the inside of
   large solid letters at higher DPI. Calibrated for 150 DPI -> 20px."
  [density]
  (max 8 (int (Math/round (* 20.0 (/ (or density 150) 150.0))))))

(defn- mode-args
  "Mode-specific ImageMagick operators.
   `threshold` is a percentage used by the `threshold` mode only.
   `text` uses LAT with a negative offset (bias toward white in flat regions).
   `dither` uses Floyd-Steinberg via `-colors 2`; `-monochrome` is unreliable
   here because it doesn't preserve mean luminance on real-world photos."
  [mode threshold density]
  (case mode
    "text"      (let [w (lat-window density)] ["-lat" (str w "x" w "-10%")])
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
   opts: {:mode :threshold :toner-saving :brightness :contrast :invert? :density}
   When `in` is a PDF, `density` is used to rasterize each page (default 150 DPI)."
  [in out {:keys [mode threshold toner-saving brightness contrast invert? density]}]
  (let [gamma (gamma-for-toner-saving toner-saving)
        bc    (str brightness "x" contrast)
        args  (concat ["magick"]
                      (when (pdf? in) (density-args (or density 150)))
                      [(str in)
                       "-background" "white" "-alpha" "remove" "-alpha" "off"]
                      (when invert? ["-negate"])
                      ["-colorspace" "Gray"]
                      (when (not= gamma 1.0)
                        ["-gamma" (str gamma)])
                      (when (or (not (zero? brightness)) (not (zero? contrast)))
                        ["-brightness-contrast" bc])
                      (mode-args mode threshold density)
                      [(str out)])]
    (apply p/shell {:out :string :err :string} args)))
