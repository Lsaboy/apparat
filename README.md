# ⬛ APPARAT — Industrial Precision Pocket Instruments

> *"Weniger, aber besser — Less, but better"*  
> — **Dieter Rams**

An open-source, local-first Android media and document toolkit engineered with the functionalist industrial design principles of Braun (Dieter Rams, Dietrich Lubs). Built with modern Android APIs, Jetpack Compose, **PDFBox-Android**, and **AndroidX Media3 Transformer** (hardware-accelerated codecs).

Designed for absolute utility, zero distractions, high contrast, and tactile mechanical clarity.

**Developer docs:** [`docs/PROJECT_GUIDE.md`](docs/PROJECT_GUIDE.md) (structure, rules, roadmap) · [`docs/PLAY_STORE_GUIDE.md`](docs/PLAY_STORE_GUIDE.md) · [`docs/PRIVACY_POLICY.md`](docs/PRIVACY_POLICY.md) · past plans and audits in [`docs/archive/`](docs/archive/)

---

## 🛠️ Instrument Inventory (21 Instruments)

Categorized into clean technical divisions: `DOCS`, `VIDEO`, `GRAPHICS`, and `UTILITY`.

### 📄 Documents (DOCS)
- **Remove PDF Password**: Enter password once to decrypt and export a permanent, DRM-free unlocked PDF.
- **Compress PDF**: Re-encodes embedded photos and colour scans at ~150 DPI. Black-and-white (1-bit) scans and transparent images (logos, signatures) are left untouched, and any image whose re-encode isn't smaller keeps its original.
- **Split PDF**: Split PDFs by custom page ranges (e.g. `1-3, 4-6`), fixed intervals (every $N$ pages), or single ranges with full validation.
- **Merge PDFs**: Combine several PDFs in any order (add more, reorder, remove). Password-protected files ask for their password; the result is unprotected.
- **PDF to Images**: Extract pages as JPEG (default) or PNG at 150 or 300 DPI, all pages or a page range.
- **Photos to PDF**: Combine photos into a multi-page A4 PDF (add more, reorder, remove), with orientation detection.

### 🎬 Video & Audio (VIDEO)
- **Video Shrinker**: Compress videos to target presets (Discord, WhatsApp, Light Compress, Extreme Shrink) using hardware-accelerated H.264 MP4 export.
- **Trim & Mute**: Keyframe-accurate trimming with Media3 trim optimization and instant stream-copy audio removal without re-encoding.
- **Burn Subtitles**: Hardcode `.srt` or `.vtt` subtitle tracks directly onto video streams with responsive text sizing.
- **Video to GIF**: Animated GIF creation with a built-in encoder (median-cut colour palette per frame).
- **Convert to MP4**: Hardware-accelerated transcoding of MKV, WEBM, MOV, and 3GP videos into universal MP4.
- **Extract Audio**: Rip audio tracks directly to clean M4A (AAC) or uncompressed WAV (16-bit PCM).

### 🖼️ Photos & Graphics (GRAPHICS)
- **Shrink Photo**: Two modes. *Target size (KB)* shrinks a photo under a limit (20/50/100/200/500 KB or custom) for portal uploads, keeping full resolution when quality alone gets there; the limit is met under both 1000- and 1024-byte KB. *Manual* offers quality and max-dimension sliders, JPEG/PNG/WEBP output and presets. Copy or safe in-place overwrite.
- **Document Scanner**: Greyscale and adjustable contrast boost to turn photos of papers and receipts into clean scans.
- **Palette Extractor**: Extracts dominant color palettes and HEX codes directly from images.
- **Convert Image**: Bi-directional conversion between JPEG, PNG, and WEBP with alpha-channel transparency preservation.
- **EXIF Cleaner**: Inspect camera and GPS metadata with 100% lossless EXIF tag stripping for JPEG (preserving orientation) and native format preservation.

### ⚡ Quick Utilities (UTILITY)
- **QR Code Studio**: Offline QR code generation for text, URLs, UPI payments, and Wi-Fi credentials.
- **File Checksum**: Calculate MD5, SHA-1, SHA-256, and SHA-512 checksums with clipboard comparison.
- **JSON Formatter**: Offline JSON pretty-printing, validation, and minification with line/column error diagnostics.
- **Base64 Studio**: Fast offline encoder and decoder for strings, data URIs, and raw binary formats.

---

## 🎨 Braun Industrial Design System

Apparat replaces decorative ornamentation with tactile, functionalist precision:

### Dual-Mode Theming Engine
- **Classic Ivory (Light Mode)**: Warm matte ivory chassis (`#F5F3EC` background, `#EBE7DD` surfaces) with high-legibility charcoal typography (`#191919`). Inspired by the Braun SK4 audio console and T3 pocket radio.
- **Atelier Charcoal (Dark Mode)**: Anodized studio dark chassis (`#161618` background, `#1E1E22` surfaces) engineered for glare-free low-light operation.
- **Follow System Default**: Automatically synchronizes with system light/dark theme.
- **First-Boot Appearance Onboarding**: Polished selection modal on first launch, persisted in preferences with orientation-change survival.

### Precision Accents & Accessibility
- **Braun Signal Orange (`#FF5500`)**: Tactile focal point inspired by the ET66 calculator and TP1 power switch.
- **Readable accent buttons**: Button text colour follows the accent (`onPrimaryForAccent`): dark charcoal on orange/amber (≈5.7:1 on Braun Orange), white on red, blue and green.
- **Tactile Rocker Switch (`BraunSegmentedControl`)**: Machined 3-way rocker switch with active indicator line and click feedback.
- **8dp Chassis Geometry**: Uniform 8dp chassis cards with 1dp machined hairline borders (`MaterialTheme.colorScheme.outline`).

### Customization & Workflow
- **Storage Subfolder**: All exports and results are cleanly saved to `Downloads/Apparat/`.
- **Default Save Behavior**: Choose between "Create New Copy" (with unique filename) or "Modify Original" (in-place overwrite when the format matches; the original is backed up first and restored if the write fails).
- **View Mode Switcher**: Instant toggle between a **2-column Grid** (compact instrument tiles) and a **1-column List**.
- **System Share Sheet Integration**: Sharing files from other Android apps immediately launches Apparat and filters the dashboard to compatible instruments.
- **Foreground Background Processing**: Long-running jobs run reliably in an Android Foreground Service (`ToolJobService`), allowing full background multitasking with progress notifications.

---

## 🔒 Security & Privacy by Architecture

- **100% Offline**: `android.permission.INTERNET` is completely omitted. Zero network requests, zero telemetry, zero analytics.
- **Scoped Storage**: Uses Android Storage Access Framework (SAF) and MediaStore for secure, permission-scoped file access without broad storage permissions.
- **Automatic Cache Management**: Stale temporary scratch and render files are safely cleaned up automatically in the background on startup.

---

## 🚀 Building & Running

### Requirements
- JDK 17 (`export JAVA_HOME=/path/to/jdk17`)
- Android SDK (compile/target API 36, minimum Android 10 / API 29)

### Build Debug APK
```bash
export JAVA_HOME=/path/to/jdk17
./gradlew assembleDebug
```

The APK will be generated at:
```
app/build/outputs/apk/debug/Apparat-debug.apk
```

### Install via ADB
```bash
adb install -r app/build/outputs/apk/debug/Apparat-debug.apk
```

### Test a release-speed build on a phone
Debug builds run Compose without optimisations and feel slow on 120 Hz screens. The `preview`
build is minified like release but signed with the debug key, so it installs over a debug build:
```bash
./gradlew assemblePreview
adb install -r app/build/outputs/apk/preview/Apparat-preview.apk
```
Never upload the preview build to Play.

### Release bundle for Google Play
Release builds are minified with R8 (`app/proguard-rules.pro`) and refuse to build without a real
signing key. Create one once with `./scripts/generate-keystore.sh`, keep the keystore and passwords
safe (losing them means you can't update the app), then:
```bash
./build-release.sh        # -> app/build/outputs/bundle/release/Apparat-release.aab
```
See `docs/PLAY_STORE_GUIDE.md` for the Play Console steps and the GitHub Actions workflow.
