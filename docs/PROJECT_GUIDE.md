# Apparat — Project Guide

How the app is built, the rules that keep it working, and what to build next and how.
Version covered: **1.1.0 (code 3)**, package `com.vedant.apparat`, minSdk 29, target/compile SDK 36.

Older plans and audits are in `docs/archive/` (see its README). This file replaces them as the
day-to-day reference.

---

## 1. What Apparat is

An Android toolkit for everyday document and media chores. Files are processed on the phone; the
app has no internet permission. 21 tools in 4 categories:

| Category | Tools |
|---|---|
| **PDF** | Remove PDF Password · Compress PDF · Split PDF · Merge PDFs · PDF to Images · Photos to PDF |
| **Video & audio** | Video Shrinker · Trim & Mute · Burn Subtitles · Video to GIF · Convert to MP4 · Extract Audio |
| **Photo** | Shrink Image (manual or **target size in KB**) · Document Scanner · Palette Extractor · Convert Image · EXIF Metadata Stripper |
| **Utilities** | QR Code Studio (text, Wi-Fi, UPI, vCard) · Checksum & Hash Verifier · JSON Formatter · Base64 Studio |

Ways in: the Home grid/list (search, categories, 4 quick shortcuts), Android's share menu, "Open
with" for PDFs, text-selection actions (Make QR, Format JSON, Base64, Hash), launcher long-press
shortcuts, and "Continue with…" chaining from one tool's result into another.

---

## 2. Repository layout

```
.
├── app/
│   ├── build.gradle.kts          versions, signing, R8, build types (debug / preview / release)
│   ├── proguard-rules.pro        R8 keep rules (PDFBox, ExifInterface tags)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml     activity, intent filters, text-action aliases, service, FileProvider
│       │   ├── res/                    launcher icon, notification icon, file_paths.xml, app name
│       │   └── java/com/vedant/apparat/
│       │       ├── ApparatApp.kt       PDFBox init, cache cleanup, launcher shortcuts
│       │       ├── MainActivity.kt     NavHost (all routes), incoming intents, shared-file hand-off
│       │       ├── core/
│       │       │   ├── jobs/           ToolJobs, ToolJobService, ExclusiveRunner (background work)
│       │       │   ├── model/          ToolItem list (ALL_TOOLS), ProcessState
│       │       │   ├── settings/       AppSettings (SharedPreferences "apparat_settings")
│       │       │   ├── theme/          Braun palette, ApparatTheme, onPrimaryForAccent()
│       │       │   ├── ui/             shared composables (ToolScaffold, ResultCard, pickers, notices, PdfPasswordGate)
│       │       │   └── util/           FileUtils (copy, save to Downloads, safe overwrite, share/open, cache cleanup)
│       │       ├── engine/             no UI; one object per area
│       │       │   ├── pdf/            PdfEngine (PDFBox-Android): unlock, compress, split, merge, images→PDF
│       │       │   ├── image/          BitmapDecoder, ImageEngine, TargetKbSearchEngine
│       │       │   ├── video/          VideoEngine (Media3 Transformer), GifEncoder, Subtitles
│       │       │   └── common/         UtilityEngine (QR, hashes, Base64, UPI/Wi-Fi/vCard), JsonFormatter
│       │       └── feature/            one screen per tool: home, pdf, image, video, utilities, settings
│       └── test/                       JVM unit tests (81) for engines and pure logic
├── docs/
│   ├── PROJECT_GUIDE.md          this file
│   ├── PLAY_STORE_GUIDE.md       Play Console + signing + CI steps
│   ├── PRIVACY_POLICY.md         host this for the Play listing (fill in the support email)
│   └── archive/                  past plans, audits and change logs
├── fastlane/                     optional Play upload lanes
├── .github/workflows/            "Deploy to Google Play Store" workflow (manual or on v* tags)
├── scripts/generate-keystore.sh  one-time release key creation
├── build-release.sh              builds the signed release bundle (.aab)
├── test-media/                   local test files (git-ignored), described in its README.txt
└── README.md
```

---

## 3. How it works

### 3.1 Screens, engines, state
- **A tool = a screen in `feature/` + engine calls.** Screens never touch PDFBox/Media3/Bitmap
  encoding directly (the one exception is PDF→Images, which renders with `PdfRenderer` in its screen
  file); they call `*Engine` functions that return `Result<File>` (or similar) and run on IO.
- **Routes:** each tool's nav route equals its `ToolItem.id` and its job key (`JOB_KEY`). Adding a
  tool means: a `ToolItem` in `ALL_TOOLS`, a `composable("route")` in `MainActivity`, the route in
  `MainActivity.TOOL_ROUTES`, and optionally an icon rule in `HomeScreen.getCategoryIcon`.
- **State:** screen state lives in `retained(JOB_KEY, "name") { … }` instead of `remember`. It
  survives rotation and leaving the screen while a job runs, and is dropped when the user leaves a
  tool with nothing running.
- **Process death:** if Android kills the app in the background, the finished result of the 10
  single-output tools (PDF ×4, Shrink Image, Video ×5) is restored when Android brings the screen
  back (`ToolJobs.restorableKeys`). A fresh launch forgets old results.

### 3.2 Background jobs
- `ToolJobs.launch(context, JOB_KEY, title) { appContext -> … }` runs work outside the screen, so
  pressing back or switching apps doesn't cancel it. One job per tool at a time.
- `ToolJobService` is a foreground service with a progress notification. On Android 15+ it asks for
  `mediaProcessing`, falls back to `dataSync`, and if both are refused jobs still run while the app
  is open. Android 17 (your S26) refuses `mediaProcessing`; `dataSync` works.
- Video encodes (`video_shrink/convert/caption/trim`) queue through `ExclusiveRunner`, one at a time
  (phones have 1–2 hardware encoders).
- When a job finishes and its screen isn't visible, a "finished" notification opens the tool.

### 3.3 Files
- Input: the system picker or share/open intents (content URIs). The app has no storage
  permissions. URIs with the `file` scheme or the app's own FileProvider are refused from outside.
- Work copies: `FileUtils.copyUriToCache` → `cache/input_<time><ext>`. For PDFs, the copy made by
  the password check is reused for the run (`PdfEngine.takeInputCopy`).
- Outputs: `cache/outputs/`. **Save** copies to `Downloads/Apparat/` through MediaStore.
  **Modify Original** backs the original up, overwrites, checks the size, and restores on failure.
- Cleanup on start: stale `input_`, `decrypted_`, `backup_` and PDFBox scratch files; outputs older
  than 24 h.

### 3.4 Engines in one line each
- **PdfEngine:** PDFBox with temp-file memory (100+ page scans don't run out of memory). Compress
  re-encodes photo images at ~150 DPI and skips 1-bit, masked and stencil images, and keeps any
  image whose JPEG isn't smaller. Merge decrypts locked inputs with their passwords.
- **BitmapDecoder:** the only place photos are decoded. It uses ImageDecoder: decodes at target size,
  applies EXIF rotation, reads HEIC. Pixel budget is 24 MP (12 MP on low-RAM phones).
- **ImageEngine:** shrink, convert, lossless JPEG EXIF strip (falls back to re-encode), scan
  enhance, palette, and `shrinkToTargetKb` (quality search first, then dimensions; result under
  N×1000 bytes).
- **VideoEngine:** Media3 Transformer. Stream-copies when no re-encode is needed, tone-maps HDR to
  SDR when it re-encodes, writes WAV itself, and does GIF via frame grabs plus `GifEncoder`.
- **UtilityEngine / JsonFormatter:** ZXing QR with UTF-8 (Hindi/₹ safe), single-pass multi-hash,
  Base64 with binary detection, and a lossless JSON pretty-printer (numbers and slashes untouched).

### 3.5 Look and feel
`ApparatTheme` (core/theme): Classic Ivory, Atelier Charcoal or System; accent from Settings. Text on
accent backgrounds must use `onPrimaryForAccent(accent)`, never a fixed colour. Red is for errors
only. Valid hints use `SuccessGreen`.

---

## 4. Rules that prevent regressions

1. Keep PDFBox on temp-file memory (`tempFileMemory`); never `PDDocument.load(file)` plain.
2. Decode photos only through `BitmapDecoder` (budget, rotation, HEIC).
3. Coil images in scrolling columns need an explicit height and `size(...)`.
4. Share/open files only via `FileUtils` (FileProvider URIs, `setDataAndType`).
5. Saves run in `withContext(Dispatchers.IO + NonCancellable)`.
6. Re-encoding video: keep the HDR→SDR tone-map in `runTransformer`.
7. Launcher shortcuts: max 4, built from `ALL_TOOLS` order; no static shortcuts XML.
8. Never catch `CancellationException` as an error inside a job.
9. R8 is on for release: anything found by reflection needs a keep rule. Test new libraries in the
   `preview` build, not only debug.
10. Before committing: `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Before a release:
    `./gradlew :app:lintRelease` (0 errors) and a smoke test of the `preview` build on a phone.

---

## 5. Build, test, release

```bash
export JAVA_HOME=$JAVA_HOME
./gradlew :app:testDebugUnitTest          # 81 unit tests
./gradlew assembleDebug                   # app/build/outputs/apk/debug/Apparat-debug.apk
./gradlew assemblePreview                 # release-speed, debug-signed: judge performance on the phone
./scripts/generate-keystore.sh            # once; back up the keystore + passwords
./build-release.sh                        # signed app/build/outputs/bundle/release/Apparat-release.aab
```
Release bundles refuse to build without the real key (no accidental debug-signed uploads).
Play steps, data-safety answers and CI secrets: `docs/PLAY_STORE_GUIDE.md`.

Version bumps: `versionCode` +1 every upload, `versionName` semantic (1.1.0 → 1.1.1 fixes, 1.2.0
features), both in `app/build.gradle.kts`.

---

## 6. Known limitations (as of 1.1.0)

- Photos over 24 MP are scaled down for Convert, non-JPEG EXIF strip and the scanner (a notice says
  so). JPEG EXIF stripping is lossless at any size. See 7.1.
- Process-death restore covers the 10 single-output tools; multi-output tools (Split, PDF→Images)
  and in-progress jobs start over.
- Video to GIF grabs frames one by one (slow on long 1080p clips); 12 fps plays slightly fast.
- JPEG 2000 images inside PDFs can't be decoded (PDFBox codec not bundled). Compress keeps them.
- Tested on a Pixel 7 emulator (Android 14) and partly on a Galaxy S26 (Android 17). Re-test the
  release build on the phone before uploading.

---

## 7. Roadmap — what to build next, and how

Ordered by value for the main use (PDFs and photos for Indian portals and business paperwork).
Effort: S ≈ hours, M ≈ a day, L ≈ several days. "Who" follows the cost plan: Gemini Flash for
mechanical work, Sonnet for normal features and reviews, Opus for risky engine work.

### 7.1 Full-resolution large photos  ·  M  ·  Sonnet (Opus for tiling)
**Recommendation: support up to 50 MP at full resolution; handle 108–200 MP with tiling later.**
Your S26 saves 12 MP by default (already full resolution today). Its 50 MP and 200 MP modes are
where the current 24 MP cap bites. A 50 MP photo needs ~200 MB of bitmap memory, which lives
in native memory (not the small Java heap), so it's fine on 8–12 GB phones but not on budget ones.
How:
1. Make the budget RAM-aware in `BitmapDecoder.effectivePixelBudget`: read
   `ActivityManager.MemoryInfo.availMem`; allow up to `min(50 MP, availMem / 4 bytes / 3)` (room for
   input + output bitmaps), never below 24 MP; keep 12 MP for low-RAM phones.
2. Wrap decode + encode in `try/catch OutOfMemoryError` → retry once at 24 MP and say so.
3. `LargePhotoNotice` already reads the same budget, so its message stays truthful automatically.
4. Later (L, Opus): for 108–200 MP and for Convert/Shrink at full size, process in horizontal strips
   with `BitmapRegionDecoder` and stream them into the encoder (PNG/WEBP need a tile-capable
   encoder; JPEG can use libjpeg-turbo via a small native lib). Only worth it if you actually keep
   200 MP photos.
Test: a 50 MP photo from the S26's high-res mode through Convert (PNG) and Shrink (Max Quality).

### 7.2 Portal photo presets ("Exam/KYC photo maker")  ·  M  ·  Sonnet — *my top idea*
Indian portals specify **dimensions and a KB range** (min and max), e.g. photo 200×230 px, 20–50 KB;
signature 140×60 px, 10–20 KB. Today you can hit the max KB but not exact pixels or a minimum.
How: a preset list (`PortalPreset(name, widthPx, heightPx, minKb, maxKb, background)`), a crop
step with a fixed-aspect frame (Compose `Canvas` + drag/pinch), then `TargetKbSearchEngine`
extended with `minBytes` (search for the highest quality that is ≤ max; if it's below min, raise
quality or add a few pixels). Let users save their own presets. Add Passport (35×45 mm @ 300 DPI),
PAN, Aadhaar update, SSC/UPSC photo & signature.

### 7.3 Compress PDF to a target size ("under 2 MB")  ·  M  ·  Opus
Portals cap uploads at 1–5 MB. Reuse the B5 machinery: loop over (DPI, quality) pairs — 150/0.6 →
120/0.5 → 100/0.4 → 72/0.35 — and stop at the first result under the limit; final fallback is
"rasterize pages" (render each page with `PdfRenderer` at the needed DPI and rebuild with
`imagesToPdf`). Add presets 500 KB / 1 MB / 2 MB / 5 MB to the Compress screen, like Photo Shrink's.

### 7.4 Organize PDF pages  ·  L  ·  Sonnet
Delete, rotate and reorder pages of one PDF visually. How: `PdfRenderer` thumbnails (~200 px wide)
in a `LazyVerticalGrid`, rendered on IO with an LRU cache; selection mode with Rotate/Delete;
drag-reorder (or up/down like Merge to start). Engine: `PdfEngine.organize(uri, password,
List<PageOp(sourceIndex, rotation)>)` that builds a new document via `importPage` and sets
`PDPage.rotation`. Reuse `PdfPasswordGate` and `takeInputCopy`.

### 7.5 Share many files at once  ·  S  ·  Gemini Flash
Accept `ACTION_SEND_MULTIPLE` (PDFs → Merge, images → Photos to PDF / batch shrink). Add intent
filters with the same MIME types, read `EXTRA_STREAM` as a list in `MainActivity`, and let
`SharedPayload` carry a list. Merge and Photos to PDF already accept lists.

### 7.6 Batch photo shrink  ·  M  ·  Sonnet
Pick 10 photos → each under N KB → "Save all". Loop `shrinkToTargetKb` in one job with progress
"3 of 10"; results list like Split's. Pairs well with 7.2.

### 7.7 Document scanner upgrade  ·  L  ·  Opus
Camera capture (`ActivityResultContracts.TakePicture` into a FileProvider URI), 4-corner crop with
a magnifier while dragging, perspective correction (`Matrix.setPolyToPoly` on a full-res canvas),
and real adaptive thresholding (Bradley/Sauvola on an integral image, done in pure Kotlin so it's
unit-testable) to remove shadows. Optional auto edge detection later.

### 7.8 Video trim with preview  ·  M  ·  Sonnet
Media3 ExoPlayer preview (`media3-exoplayer` + `media3-ui`), a two-handle `RangeSlider` mapped to
the duration, frame thumbnails along the track (`MediaMetadataRetriever` at ~10 points). Keep the
text fields for exact seconds.

### 7.9 Video shrink to a target size (MB)  ·  S  ·  Sonnet
`videoBitrate = (targetBytes×8 − audioBitrate×seconds) / seconds`, clamp to a sane minimum, pick the
resolution from the bitrate (≥2.5 Mbps → 720p, ≥1 Mbps → 480p, else 360p). Presets: WhatsApp
16 MB, Gmail 25 MB.

### 7.10 Smaller ideas worth doing
- **Protect PDF / add password** (S, Flash): the reverse of Unlock; PDFBox `StandardProtectionPolicy`.
- **Sign a PDF** (M, Sonnet): place a saved signature image on a page (drag to position), common
  for business paperwork.
- **Page numbers / watermark** (S–M, Sonnet).
- **Recent results** (S, Sonnet): a list of the last outputs (persisted like process-death restore)
  so a result isn't lost if you leave the screen.
- **Hindi UI** (M, Flash): move hardcoded strings to `strings.xml`, then add `values-hi`.
- **Performance** (S, Sonnet): add a Baseline Profile (`androidx.baselineprofile`) to cut first-
  scroll jank on Home; check Home in the `preview` build on the S26 first.
- **Local crash log** (S, Flash): no internet, so write uncaught exceptions to a file and add
  Settings → "Share crash log" so you can send it to yourself.
- **CI on every push** (S, Flash): run unit tests + `assembleDebug` in GitHub Actions, not only on
  release tags.
- **Instrumented smoke tests** (M, Sonnet): Compose UI tests that drive the main flows with the
  files in `test-media/`, so emulator checks don't have to be done by hand.

---

## 8. Working with several AI agents

- One agent per git worktree and branch (`git worktree add ../apparat-<agent> -b <agent>-batch`);
  never two agents in the same folder.
- Split work by files; engine files go to one agent at a time.
- Merge into `master` one branch at a time; after each merge run the tests and build.
- Give agents this file plus the specific section; write their task list with exact files.
- Review every Flash/DeepSeek batch with Sonnet before merging (`/code-review low` on the diff).
