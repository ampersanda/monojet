(ns imglaserprintopt.core
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [imglaserprintopt.imagemagick :as im]))

(def version "0.1.0")

(def modes #{"text" "threshold" "dither" "halftone" "gray"})

(def cli-opts
  [["-o" "--output PATH" "Output file (single input) or directory (multiple)"
    :default nil]
   ["-d" "--dir DIR" "Read all images from directory"]
   ["-m" "--mode MODE" (str "Conversion mode: " (str/join ", " (sort modes)))
    :default "text"
    :validate [#(contains? modes %)
               (str "Mode must be one of: " (str/join ", " (sort modes)))]]
   ["-t" "--threshold PCT" "Threshold % (text/threshold modes)"
    :default 50
    :parse-fn parse-long
    :validate [#(<= 0 % 100) "Threshold must be 0-100"]]
   ["-T" "--toner-saving LEVEL" "Toner-saving lightening: 0 (off) - 3 (aggressive)"
    :default 2
    :parse-fn parse-long
    :validate [#(<= 0 % 3) "Toner-saving must be 0-3"]]
   ["-b" "--brightness NUM" "Brightness adjustment (-100..100)"
    :default 0
    :parse-fn parse-long]
   ["-c" "--contrast NUM" "Contrast adjustment (-100..100)"
    :default 0
    :parse-fn parse-long]
   ["-i" "--auto-invert" "Invert if dark background detected (saves toner)"
    :default false]
   [nil "--invert" "Force invert"
    :default false]
   ["-v" "--verbose" "Print progress info"
    :default false]
   ["-V" "--version" "Show version"]
   ["-h" "--help" "Show help"]])

(def image-extensions
  #{"png" "jpg" "jpeg" "bmp" "tiff" "tif" "webp" "gif"})

(defn- image-file?
  [path]
  (let [ext (some-> (fs/extension path) str/lower-case)]
    (contains? image-extensions ext)))

(defn- natural-sort-key
  [path]
  (let [name (str (fs/file-name path))]
    (mapv (fn [part]
            (if (re-matches #"\d+" part)
              (parse-long part)
              part))
          (re-seq #"\d+|[^\d]+" name))))

(defn- resolve-inputs
  [{:keys [options arguments]}]
  (let [paths (if-let [dir (:dir options)]
                (let [dir-path (fs/path dir)]
                  (when-not (fs/directory? dir-path)
                    (binding [*out* *err*]
                      (println (str "Error: Not a directory: " dir)))
                    (System/exit 1))
                  (->> (fs/list-dir dir-path)
                       (filter image-file?)
                       (map str)))
                (map str arguments))]
    (sort-by natural-sort-key paths)))

(defn- validate-inputs!
  [image-paths]
  (when (zero? (count image-paths))
    (binding [*out* *err*]
      (println "Error: No input images. Pass files or use -d <dir>."))
    (System/exit 1))
  (doseq [path image-paths]
    (when-not (fs/exists? path)
      (binding [*out* *err*]
        (println (str "Error: File not found: " path)))
      (System/exit 1))
    (when-not (image-file? path)
      (binding [*out* *err*]
        (println (str "Error: Not a supported image: " path)))
      (System/exit 1))))

(defn- default-output-name
  [in]
  (str "bw-" (fs/file-name in)))

(defn- resolve-output
  "Decide the final output path for one input."
  [in user-output single?]
  (cond
    (and single? user-output)
    user-output

    (and (not single?) user-output)
    (do (fs/create-dirs user-output)
        (str (fs/path user-output (default-output-name in))))

    single?
    (default-output-name in)

    :else
    (do (fs/create-dirs "bw-output")
        (str (fs/path "bw-output" (default-output-name in))))))

(defn- human-size
  [bytes]
  (cond
    (< bytes 1024)            (str bytes " B")
    (< bytes (* 1024 1024))   (format "%.1f KB" (/ bytes 1024.0))
    :else                     (format "%.1f MB" (/ bytes 1024.0 1024.0))))

(defn- print-usage
  [summary]
  (println "imglaserprintopt - Convert images to laser-printer-optimized B&W")
  (println)
  (println "Usage: bb convert [options] image1 [image2 ...]")
  (println "       bb convert [options] -d <directory>")
  (println)
  (println "Options:")
  (println summary)
  (println)
  (println "Modes:")
  (println "  text      Local adaptive threshold; best for documents/screenshots")
  (println "  threshold Hard threshold at -t; smallest output, no midtones")
  (println "  dither    Floyd-Steinberg error-diffusion B&W; best for photos")
  (println "  halftone  Ordered halftone screen; newspaper-style photos")
  (println "  gray      Keep grayscale (with toner-saving lightening)"))

(defn- process-one!
  [in out opts verbose?]
  (let [pre-mean   (im/mean-luminance in)
        force?     (:invert opts)
        auto?      (:auto-invert opts)
        invert?    (or force? (and auto? (< pre-mean 0.5)))
        cvt-opts   {:mode         (:mode opts)
                    :threshold    (:threshold opts)
                    :toner-saving (:toner-saving opts)
                    :brightness   (:brightness opts)
                    :contrast     (:contrast opts)
                    :invert?      invert?}]
    (when verbose?
      (println (str "  source mean luminance: " (format "%.2f" pre-mean)
                    (cond
                      force? "  invert: forced"
                      invert? "  invert: auto (dark background)"
                      :else  ""))))
    (im/convert! in out cvt-opts)
    (let [cov-out (im/coverage out)
          cov-in  (- 1.0 pre-mean)
          ;; Approximate savings as drop in ink coverage.
          savings (max 0.0 (- cov-in cov-out))
          in-sz   (fs/size in)
          out-sz  (fs/size out)]
      {:input       in
       :output      out
       :coverage    cov-out
       :savings     savings
       :input-size  in-sz
       :output-size out-sz})))

(defn- print-report
  [results]
  (let [avg-cov  (/ (reduce + (map :coverage results)) (count results))
        avg-save (/ (reduce + (map :savings results)) (count results))]
    (println)
    (println "------------------------------------------------------------")
    (doseq [r results]
      (println (format "%-40s -> %-40s  ink=%.1f%%  saved=%.1f%%  (%s -> %s)"
                       (str (fs/file-name (:input r)))
                       (str (fs/file-name (:output r)))
                       (* 100.0 (:coverage r))
                       (* 100.0 (:savings r))
                       (human-size (:input-size r))
                       (human-size (:output-size r)))))
    (println "------------------------------------------------------------")
    (println (format "Average ink coverage: %.1f%%  (estimated toner saved: %.1f%%)"
                     (* 100.0 avg-cov)
                     (* 100.0 avg-save)))))

(defn -main
  [& args]
  (let [parsed  (cli/parse-opts args cli-opts)
        options (:options parsed)
        errors  (:errors parsed)]
    (when errors
      (binding [*out* *err*]
        (doseq [e errors] (println e)))
      (System/exit 1))
    (when (:version options)
      (println (str "imglaserprintopt " version))
      (System/exit 0))
    (when (:help options)
      (print-usage (:summary parsed))
      (System/exit 0))

    (im/check-magick!)

    (let [image-paths (resolve-inputs parsed)
          _           (validate-inputs! image-paths)
          n           (count image-paths)
          single?     (= n 1)
          verbose?    (:verbose options)
          _           (when verbose?
                        (println (str "Found " n " image" (when (> n 1) "s")
                                      "  mode=" (:mode options)
                                      "  threshold=" (:threshold options) "%"
                                      "  toner-saving=" (:toner-saving options))))
          results     (mapv (fn [i in]
                              (let [out (resolve-output in (:output options) single?)]
                                (when verbose?
                                  (println (str "Converting " (inc i) "/" n ": " in " -> " out)))
                                (process-one! in out options verbose?)))
                            (range)
                            image-paths)]
      (print-report results)
      (System/exit 0))))
