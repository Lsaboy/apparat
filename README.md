# Digital Pocket Knife 🔪

An open-source, local-first Android media and document toolkit powered by modern Android APIs, Jetpack Compose, **PDFBox-Android**, and **AndroidX Media3 Transformer** (hardware-accelerated codecs).

Built to handle everyday tasks locally on-device without needing web converters.

---

## 🛠️ Feature Suite (20 Local Utilities)

### 📄 PDF Tools
- **Remove PDF Password**: Enter password once to decrypt and export a permanent, DRM-free unlocked PDF.
- **Compress PDF**: Downsample embedded raster images and clean redundant streams with overwrite protection.
- **Split PDF**: Split PDFs by custom page ranges (e.g. `1-3, 4-6`), fixed intervals (every $N$ pages), or single ranges with full validation.
- **PDF to Images**: High-resolution page extraction to PNG images with A4 fit margins.
- **Photos to PDF**: Combine multiple photos into a clean multi-page document with standardized A4 layout and orientation detection.

### 🎬 Video & Audio Tools
- **Video Shrinker**: Compress videos to target presets (Discord, WhatsApp, Light Compress, Extreme Shrink) using hardware-accelerated H.264 MP4 export.
- **Trim & Mute**: Keyframe-accurate trimming with Media3 trim optimization and instant stream-copy audio removal without re-encoding.
- **Burn Subtitles**: Hardcode `.srt` or `.vtt` subtitle tracks directly onto video streams with responsive text sizing.
- **Video to GIF**: High-quality animated GIF creation via local NeuQuant color quantization.
- **Convert to MP4**: Hardware-accelerated transcoding of MKV, WEBM, MOV, and 3GP videos into universal MP4.
- **Extract Audio**: Rip audio tracks directly to clean M4A (AAC) or uncompressed WAV (16-bit PCM).

### 🖼️ Photo & Graphics Tools
- **Shrink Image**: Dimension downsampling, quality sliders (1–100%), output format selection (JPEG, PNG, WEBP), and copy/overwrite modes with transparent-to-white canvas compositing.
- **Document Enhancer**: Adaptive thresholding to convert photos of papers and receipts into crisp high-contrast scans.
- **Palette Extractor**: Extracts dominant color palettes and HEX codes directly from images.
- **Convert Image**: Bi-directional conversion between JPEG, PNG, and WEBP with alpha-channel transparency preservation.
- **EXIF Stripper**: Inspect camera and GPS metadata with 100% lossless EXIF tag stripping for JPEG (preserving orientation) and native format preservation.

### ⚡ Quick Utilities
- **QR Code Studio**: Offline QR code generation for text, URLs, and Wi-Fi credentials.
- **Hash Verifier**: Calculate MD5, SHA-1, SHA-256, and SHA-512 checksums with clipboard comparison.
- **JSON Formatter**: Offline JSON pretty-printing, validation, and minification.
- **Base64 Studio**: Fast offline encoder and decoder for strings and text.

---

## 🎨 UI & Customization (Lumen-Inspired)

- **Dual Theming Engine**:
  - **AMOLED True Dark**: Deep `#000000` pitch black background with `#121215` cards and `#222228` borders.
  - **Material You (Material 3 Expressive)**: Dynamic wallpaper color extraction on Android 12+.
- **Customization Panel**:
  - Theme style switcher: AMOLED, Material You, System Dark, Light (with live recomposition).
  - Dynamic Color toggle.
  - 9 accent color swatches: Cyan, Emerald, Lavender, Amber, Electric Blue, Hot Pink, Orange, Teal, Ruby.
  - Default Save Behavior: "Create New Copy" (to `Downloads/DigitalPocketKnife/`) or "Modify Original" (in-place scoped storage overwrite when formats match).
- **View Mode Switcher**: Instant toggle between a **2-column Grid** (compact squircle tiles) and a **1-column List**.
- **Quick Shortcuts**: 1-tap launcher bar on the dashboard for quick access to primary tools.
- **Share Sheet Integration**: When sharing a file from Android's system share sheet, the app automatically filters the dashboard to show only tools compatible with that MIME type.
- **Background Processing & Notifications**: Long-running jobs run reliably in a foreground service (`dataSync` and `mediaProcessing`), allowing users to multitask freely while tracking progress.

---

## 🔒 Security & Privacy

- **100% Offline**: `android.permission.INTERNET` is completely omitted. Zero network requests, zero telemetry, zero cloud uploads.
- **Scoped Storage**: Uses Android Storage Access Framework (SAF) and MediaStore for secure, permission-scoped file access without broad storage permissions.
- **Automatic Cache Management**: Stale temporary scratch and render files are safely cleaned up automatically in the background on startup.

---

## 🚀 Building & Running

### Requirements
- JDK 17
- Android SDK (API 35, Minimum Android 10 / API 29)

### Build Debug APK
```bash
export JAVA_HOME=/path/to/jdk17
./gradlew assembleDebug
```

Output APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Install via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
