# Digital Pocket Knife 🔪

An open-source, local-first Android media and document toolkit powered by modern Android APIs, Jetpack Compose, **PDFBox-Android**, and **FFmpeg-Kit**.

Built to handle everyday tasks locally on-device without needing web converters.

---

## 🛠️ Feature Suite (20 Local Utilities)

### 📄 PDF Tools
- **Remove PDF Password**: Enter password once to decrypt and export a permanent, DRM-free unlocked PDF.
- **Shrink & Compress PDF**: Downsample embedded raster images and clean redundant streams with copy or overwrite options.
- **Split PDF / Multi-Split**: Split PDFs by custom page ranges (e.g. `1-3, 4-6`), fixed intervals (every $N$ pages), or single ranges.
- **PDF to Images**: Hardware-accelerated page extraction to high-resolution PNGs.
- **Photos to PDF**: Combine multiple photos into a clean multi-page document.

### 🎬 Video & Audio Tools
- **Video Shrinker**: Compress videos to target sizes (Discord, WhatsApp, Email, or custom MB) and resolutions (1080p, 720p, 480p) with output format selection (MP4, MKV, WEBM).
- **Lossless Trim & Mute**: Sub-second stream copy trimming (`-c copy`) and instant audio removal without re-encoding.
- **Burn Subtitles & Captions**: Hardcode `.srt` or `.vtt` subtitle tracks directly onto video streams with custom font scaling.
- **Video to Smooth GIF**: Two-pass palette generation (`palettegen` + `paletteuse`) for clean, high-framerate animated GIFs.
- **Video Format Exchange**: Transcode between MP4, MKV, WEBM, MOV, and animated GIF.
- **Extract Audio**: Rip audio tracks directly to MP3, M4A, WAV, or lossless FLAC.

### 🖼️ Photo & Graphics Tools
- **Photo Shrinker & Optimizer**: Dimension downsampling, quality sliders (1–100%), output format selection (JPEG, PNG, WEBP), and copy/overwrite modes.
- **Document & Receipt Enhancer**: Adaptive thresholding to convert photos of papers/receipts into crisp high-contrast scans.
- **Palette & Color Extractor**: Extracts dominant color palettes and HEX codes directly from images.
- **Photo Format Exchange**: Bi-directional conversion between JPEG, PNG, WEBP, and BMP.
- **EXIF Privacy Stripper**: Inspect camera/GPS metadata and strip EXIF tags before sharing.

### ⚡ Quick Utilities
- **QR Code Studio**: Offline QR code generation for text, URLs, and Wi-Fi credentials.
- **Checksum & Hash Verifier**: Calculate MD5, SHA-1, SHA-256, and SHA-512 hashes with clipboard matching.
- **JSON Formatter & Validator**: Offline JSON pretty-printing, validation, and minification.
- **Base64 Studio**: Fast offline encoder and decoder for strings and text.

---

## 🎨 UI & Customization (Lumen-Inspired)

- **Dual Theming Engine**:
  - **AMOLED True Dark**: Deep `#000000` pitch black background with `#121215` cards and `#222228` borders.
  - **Material You (Android 17 / Material 3 Expressive)**: Dynamic wallpaper color extraction on Android 12+.
- **Customization Panel**:
  - Theme style switcher: AMOLED, Material You, System Dark, Light.
  - Dynamic Color toggle.
  - 9 accent color swatches: Cyan, Emerald, Lavender, Amber, Electric Blue, Hot Pink, Orange, Teal, Ruby.
  - Default Save Behavior: "Create New Copy" (to `Downloads/DigitalPocketKnife/`) or "Modify Original" (in-place scoped storage overwrite when formats match).
  - App Shortcuts toggle.
- **View Mode Switcher**: Instant toggle between a **2-column Grid** (compact squircle tiles) and a **1-column List**.
- **Quick Shortcuts Carousel**: 1-tap launcher bar on the dashboard for Decrypt PDF, Lossless Trim, Shrink Photo, and Shrink Video.
- **Share Sheet Filtering**: When sharing a file from Android's system share sheet, the app automatically filters the dashboard to show only tools compatible with that MIME type.

---

## 🔒 Security & Privacy

- **100% Offline**: `android.permission.INTERNET` is completely omitted. Zero network requests, zero telemetry, zero cloud uploads.
- **Scoped Storage**: Uses Android Storage Access Framework (SAF) and MediaStore for secure, permission-scoped file access.

---

## 🚀 Building & Running

### Requirements
- JDK 17
- Android SDK (API 35)

### Build Debug APK
```bash
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
