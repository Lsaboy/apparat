# OmniForge 🛠️

**OmniForge** is an open-source, 100% offline Swiss-Army knife Android app designed to replace bloated, subscription-gated, and privacy-invasive web utility tools (like Smallpdf, CloudConvert, Ezgif, etc.).

All media and document operations run directly on your device using hardware acceleration, native image APIs, **PDFBox-Android**, and **FFmpeg-Kit**.

---

## ✨ Full Feature Suite

### 📄 PDF Tools
- **Remove PDF Password**: Enter the password once to decrypt the document and export a permanent, DRM-free, unlocked PDF copy.
- **Shrink & Compress PDF**: Downsample embedded raster images and clean redundant streams to reduce PDF file size.
- **Photos to PDF**: Combine one or multiple images into a clean multi-page PDF document.
- **PDF to Images**: Render every page of a PDF into high-res PNG images using Android's native hardware `PdfRenderer`.
- **Split PDF / Extract Pages**: Extract specific page ranges (e.g. pages 2–5) into a separate, clean PDF.

### 🎬 Video & Audio Tools
- **Video Shrinker**: Compress videos with targeted presets:
  - *Discord / Social* (Compact 720p)
  - *WhatsApp / Messaging* (480p)
  - *Light Compress* (1080p)
  - *Extreme Shrink* (360p)
- **Instant Lossless Trim & Mute**: Stream-copy trimming and audio muting in **under 1 second** with zero quality loss (`-c copy`).
- **Burn Subtitles & Captions**: Hardcode `.srt` or `.vtt` subtitle tracks directly onto the video stream with customizable font sizing and crisp contrast outlines.
- **Video to Smooth GIF**: Generate crystal-clear animated GIFs using FFmpeg's two-pass color palette generation (`palettegen` + `paletteuse`).
- **Format Exchange**: Transcode between **MP4**, **MKV**, **WEBM**, and animated **GIF**.
- **Extract Audio**: Rip audio tracks directly to **MP3**, **M4A**, **WAV**, or **FLAC**.

### 🖼️ Photo & Graphics Tools
- **Photo Shrinker & Optimizer**: Compress images with granular quality sliders (1–100%), max dimension scaling (e.g. 1920p, 1080p, 720p), and optional EXIF stripping.
- **Document & Receipt Enhancer**: Turn camera shots of notes or receipts into crisp, high-contrast monochrome scans.
- **Palette & Color Extractor**: Sample dominant colors and HEX codes directly from any image.
- **Format Exchange**: Convert between **JPEG**, **PNG**, and **WEBP** (Lossy & Lossless).
- **EXIF Privacy Stripper**: Inspect camera make, model, exposure settings, timestamps, and GPS coordinates; strip all tracking data with a single tap.

### ⚡ Quick Utilities
- **QR Code Studio**: Generate high-res QR codes for URLs, Wi-Fi, and text 100% offline, with one-tap PNG save & share.
- **Checksum & Hash Verifier**: Calculate **MD5** and **SHA-256** hashes of any file, with instant clipboard matching to verify file integrity.
- **JSON Formatter & Validator**: Prettify or minify raw JSON data completely offline.
- **Base64 Studio**: Fast offline encoder and decoder for text and strings.

---

## 🎨 Design & Theming
- **AMOLED Dark Mode by Default**: Built from the ground up for pitch-black (`#000000`) surfaces, deep obsidian cards (`#161616`), and subtle contrast borders.
- **Material 3 / Jetpack Compose**: Modern cards, fluid animations, filter chips, and instant search across all tools.
- **Category Accents**:
  - *Electric Cyan* (`#00E5FF`) for Video & Audio
  - *Royal Lavender* (`#B388FF`) for PDF Tools
  - *Vibrant Emerald* (`#00E676`) for Photos & Graphics
  - *Amber Gold* (`#FFD54F`) for Quick Utilities

---

## 🚀 Building & Installing

### Prerequisites
- JDK 17+
- Android SDK (API Level 35)

### Build Debug APK
```bash
./gradlew assembleDebug
```
The compiled APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Install directly via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔒 Privacy & Permissions
- **Zero Internet Permissions**: OmniForge does not require or declare `android.permission.INTERNET`. No telemetry, no ads, no external servers.
- Uses Android System File Picker (`Storage Access Framework`) and `MediaStore` for safe, scoped file access without requiring invasive device-wide storage permissions.
