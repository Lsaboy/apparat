# Gemini Changes - Digital Pocket Knife (OmniForge)
**Date:** October 16, 2026  
**Commit Range:** `8ebf590` -> `35c4f6d` (4 commits)  
**Total Impact:** 37 files changed, +1,204 lines, -322 lines  
**Build & Test Status:** All JUnit tests passing, `./gradlew assembleDebug` passing, `./gradlew lintDebug` passing (0 errors).

---

## 1. Executive Summary

This document details all modifications made to the Digital Pocket Knife repository for peer review and architectural verification by Claude.

All changes were implemented to resolve safety, data corruption, memory leak, and UX issues outlined in `FIX_PLAN.md` without introducing regressions into Media3, PDFBox, or Kotlin coroutines. Every batch underwent peer review by a Pro-tier code auditor before implementation.

### Commit History
1. `2af848f` - **Batch 1:** Save folder consistency, image transparency compositing, photo shrink format, and A4 PDF fit (`1B.5`, `1B.8`, `1B.10`, `1C.4`, `1D.2`, `1D.3`).
2. `10357f7` - **Batch 2:** PDF split chunking & range validation, compress overwrite protection, notification permission persistence, and standardized screen titles (`1B.1`, `1B.2`, `1B.3`, `1D.2`, `1D.3`).
3. `feb5b2d` - **Batch 3:** Theme recomposition, default save mode setting, overwrite safety & format matching, and lossless JPEG EXIF stripping (`1C.1`, `1C.2`, `1B.7`, `1B.11`).
4. `35c4f6d` - **Batch 4:** ToolJobs Activity leak prevention, singleTop backstack preservation, safe startup cache cleanup, float PCM WAV downsampling, and Media3 README (`1C.5`, `1D.2`, `1D.3`).

---

## 2. Batch-by-Batch Technical Breakdown

### Batch 1: Baseline Fixes & Image/PDF Correctness
**Commit:** `2af848f`

#### 1. Centralized Download Folder Name (`1C.4`)
- **Problem:** Files were saved to `Downloads/DigitalPocketKnife`, but 11+ UI toasts reported `Downloads/OmniForge`.
- **Fix:** Added `const val DOWNLOAD_SUBFOLDER = "DigitalPocketKnife"` in [`FileUtils.kt`](./app/src/main/java/com/omniforge/app/core/util/FileUtils.kt) and updated all screen toasts to reference `Downloads/${FileUtils.DOWNLOAD_SUBFOLDER}`.

#### 2. JPEG Compositing on Transparent Images (`1B.8`)
- **Problem:** Converting transparent PNG or WebP images to JPEG resulted in solid black backgrounds because JPEG has no alpha channel.
- **Fix:** In [`ImageEngine.kt`](./app/src/main/java/com/omniforge/app/engine/image/ImageEngine.kt) (`convertFormat` and `shrinkImage`), if the target is JPEG and `bitmap.hasAlpha()`, the bitmap is composited onto a white canvas (`Bitmap.createBitmap(w, h, ARGB_8888)` + `Canvas.drawColor(Color.WHITE)` + `Canvas.drawBitmap(...)`). The intermediate bitmap is recycled immediately.

#### 3. Photo Shrinker Output Format (`1B.5`)
- **Problem:** `ImageEngine.shrinkImage` hardcoded compression to JPEG, ignoring the user's selected format (PNG/WebP) and saving with mismatched MIME extensions.
- **Fix:** Added `targetFormat: ImageOutputFormat = ImageOutputFormat.JPEG` to `shrinkImage`. JPEG quality slider applies to JPEG and WebP lossy, while PNG compression is lossless. Guarded EXIF attribute copying to JPEG only.

#### 4. Android 15 FGS Timeout & Notification Icon (`1D.2`)
- **Problem:** On Android 15 (API 35), foreground services with `dataSync` and `mediaProcessing` types crash the app if `Service.onTimeout(startId, fgsType)` is not implemented. Also, `ToolJobService` used the full-color adaptive icon `ic_launcher_foreground` for notification smallIcon, displaying as a white square.
- **Fix:** Added `@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM) override fun onTimeout(...)` in [`ToolJobService.kt`](./app/src/main/java/com/omniforge/app/core/jobs/ToolJobService.kt) to call `stopSelfNow()`. Created a 24dp monochrome vector drawable `ic_stat_notify.xml` (white fill on transparent) for notification small icons.

#### 5. Photos to PDF Page Size Normalization (`1B.10`)
- **Problem:** `PdfEngine.imagesToPdf` set PDF page points equal to image pixel dimensions. A 4000x3000 photo produced a ~1.4 meter wide PDF page.
- **Fix:** Extracted pure calculation function `calculateA4Fit(imageWidth, imageHeight, margin, a4Short, a4Long): A4FitResult`. Automatically chooses Portrait vs. Landscape A4 orientation based on image aspect ratio, scales image to fit within margins (20pt default), and centers it on the page.
- **Tests Added:** [`PdfA4FitTest.kt`](./app/src/test/java/com/omniforge/app/engine/pdf/PdfA4FitTest.kt) covering landscape, portrait, square, and margin constraints.

#### 6. Tool UI Copy Fixes (`1D.3`)
- Removed misleading "Save Mode" selector from `VideoConvertScreen` (since output is always MP4).
- Updated font-size slider label in `VideoCaptionScreen` from "pt" to "Text size" (relative to 720p height).

---

### Batch 2: PDF Split Chunking, Validation & Overwrite Safety
**Commit:** `10357f7`

#### 1. PDF Split Chunking & Page Count (`1B.1`)
- **Problem:** `PdfSplitScreen` hardcoded `(1..100 step n)` for "Every N Pages", generating 90 duplicate pages for a 10-page doc with $n=1$, and truncating docs $>100$ pages.
- **Fix:**
  - Extended [`PdfPasswordGate.kt`](./app/src/main/java/com/omniforge/app/core/ui/PdfPasswordGate.kt) callback to `onReady: (password: String, pageCount: Int) -> Unit`.
  - Added pure chunk calculation `PdfEngine.computeEveryNChunks(totalPages: Int, n: Int): List<IntRange>`.
  - Displayed real page count in file info headers across `PdfSplitScreen`, `PdfShrinkScreen`, and `PdfToImagesScreen`.

#### 2. Strict Range Validation in PDF Split (`1B.2`)
- **Problem:** `parseCustomRanges` silently clamped invalid ranges (e.g. "12-15" on 10-page doc -> page 10) and ignored tokens like "5-3" or non-numeric input.
- **Fix:** Implemented strict range validation for `CUSTOM_RANGES`, `EVERY_N_PAGES`, and `SINGLE_RANGE`. Displays inline descriptive error text (e.g., `"Range '12-15' exceeds document length (10 pages)"`, `"Range '5-3' has start > end"`). The Split button is disabled until all inputs are valid.
- **Tests Added:** [`PdfSplitTest.kt`](./app/src/test/java/com/omniforge/app/engine/pdf/PdfSplitTest.kt) testing `computeEveryNChunks`, single page documents, exact multiples, remainders, out-of-bounds, and edge cases.

#### 3. PDF Compress Overwrite Protection (`1B.3`)
- **Problem:** If PDF compression did not reduce the file size (or made it larger), "Modify Original" overwrote the user's file anyway.
- **Fix:** Compare `outputFile.length()` with original `fileSize`. If `output >= original`, refuse overwrite and show an honest message: `"PDF is already optimized (${formatFileSize(newBytes)}) — could not reduce size further."`

#### 4. Notification Permission Persistence (`1D.2`)
- **Problem:** Permission request nagged the user on every app launch because `askedForNotifications` was an in-memory boolean.
- **Fix:** Added `var hasAskedNotificationPermission: Boolean` to [`AppSettings.kt`](./app/src/main/java/com/omniforge/app/core/settings/AppSettings.kt) backed by SharedPreferences (`asked_notification_permission`).

#### 5. Screen Titles Standardization (`1D.3`)
- Standardized tool titles across `HomeScreen`, `ToolScaffold`, `ToolJobs`, and notifications:
  - "Trim & Mute" (was "Lossless Trim & Mute")
  - "Video to GIF" (was "Video to Smooth GIF Maker")
  - "Split PDF" (was "Split PDF / Multi-File Split")
  - "Compress PDF" (was "Shrink & Compress PDF")
  - "Convert Image" (was "Photo Format Exchange")
  - "Shrink Image" (was "Photo Shrinker & Optimizer")

---

### Batch 3: Theme Live Recomposition, Overwrite Safety & Lossless EXIF
**Commit:** `feb5b2d`

#### 1. Theme Change Applies Immediately (`1C.1`)
- **Problem:** Selecting a new theme or accent color in `CustomizationScreen` did not apply until an app restart because `themeRecomposeTrigger` was never read inside `OmniForgeTheme`.
- **Fix:** Added `recomposeTrigger: Int = 0` parameter to `OmniForgeTheme` in [`Theme.kt`](./app/src/main/java/com/omniforge/app/core/theme/Theme.kt) and passed `recomposeTrigger = themeRecomposeTrigger` in `MainActivity.kt`.

#### 2. Default Save Mode Setting Honored (`1C.2`)
- **Problem:** `AppSettings.defaultSaveMode` was ignored because screens hardcoded `mutableStateOf(SaveMode.NEW_COPY)`.
- **Fix:** Initialized `saveMode` with `AppSettings(context).defaultSaveMode` in all relevant screens.

#### 3. "Modify Original" Format Mismatch Guard (`1B.7`)
- **Problem:** Selecting "Modify Original" when converting formats (e.g. PNG to JPG or MKV to MP4) wrote new container bytes into the old URI, corrupting the file extension. Furthermore, if `overwriteOriginal` failed (e.g. read-only SAF URI), the toast claimed "Saved as copy in Downloads" when nothing was saved.
- **Fix:**
  - Added `allowOverwrite: Boolean = true` and `disallowedReason: String? = null` to `SaveModeSelector`.
  - Added pure helpers `isImageFormatMatching` and `isVideoMp4` in [`FileUtils.kt`](./app/src/main/java/com/omniforge/app/core/util/FileUtils.kt) with tests in [`FileUtilsTest.kt`](./app/src/test/java/com/omniforge/app/core/util/FileUtilsTest.kt).
  - Disabled "Modify Original" with explanation when formats do not match.
  - Corrected failure message to: `"Processed! (Original is read-only — tap Save below to save copy)"`.

#### 4. Lossless EXIF Stripping (`1B.11`)
- **Problem:** `ImageEngine.stripExif` unconditionally decoded images into bitmaps and recompressed to JPEG q95, which re-encoded lossy JPEG, converted PNG/WebP to JPEG, and rotated portrait phone photos sideways.
- **Fix:**
  - For JPEG: Copies file stream directly, reads attributes with `ExifInterface`, and clears metadata tags while explicitly **preserving `TAG_ORIENTATION`** (since raw JPEG DCT pixel bytes are unrotated). Calls `exif.saveAttributes()` for 100% lossless, fast stripping.
  - Added fallback for non-consecutive JPEG thumbnail strip `IOException`.
  - For PNG/WebP: Encodes back to native format (`Bitmap.CompressFormat.PNG` or `WEBP_LOSSY`).

---

### Batch 4: Lifecycle Leaks, Backstack Navigation, Cache Cleanup & WAV Engine
**Commit:** `35c4f6d`

#### 1. ToolJobs Activity Leak Prevention (`1D.2`)
- **Problem:** `ToolJobs.launch(context, key, title) { ... }` captured `LocalContext.current` (`MainActivity`). Because jobs live in `ToolJobs`' static supervisor scope, a background job kept the destroyed Activity alive in memory during orientation change or back navigation.
- **Fix:**
  - Changed `ToolJobs.launch` signature to `block: suspend CoroutineScope.(appContext: Context) -> Unit`.
  - Provided `context.applicationContext` into the block execution.
  - Updated all 15 tool screens to use the passed `appContext` parameter for all backend operations (ContentResolver, engines, FileUtils), eliminating Activity references from background coroutine closures.

#### 2. Backstack Preservation on Notification Click (`1D.2`)
- **Problem:** In `ToolJobService.openToolIntent`, `Intent.FLAG_ACTIVITY_CLEAR_TASK` wiped the backstack and destroyed in-memory state of other open tools whenever the user tapped a job notification.
- **Fix:**
  - Set `android:launchMode="singleTop"` on `MainActivity` in [`AndroidManifest.xml`](./app/src/main/AndroidManifest.xml).
  - Replaced `FLAG_ACTIVITY_CLEAR_TASK` with `FLAG_ACTIVITY_SINGLE_TOP or FLAG_ACTIVITY_NEW_TASK` in `ToolJobService.kt`.
  - In `MainActivity.kt`, used a buffered `Channel<String>(Channel.BUFFERED)` to deliver route navigation events consumed via `LaunchedEffect(navController)`. This avoids:
    - SharedFlow/StateFlow replay issues (which cause duplicate navigations on device rotation).
    - Dropping cold-start deep links.
    - Pushing duplicate destinations if already on that screen.

#### 3. Safe Startup Temp Cache Cleanup (`1C.5`)
- **Problem:** `cache/outputs/` grew indefinitely, and aborted jobs left scratch files in `cache/pdfbox/`, `cache/input_*`, and `cache/decrypted_*`.
- **Fix:**
  - Added pure method `cleanStaleCacheDir(cacheDir: File, maxAgeMs: Long, appStartTime: Long)` and `cleanStaleCache(context: Context, ...)` to [`FileUtils.kt`](./app/src/main/java/com/omniforge/app/core/util/FileUtils.kt).
  - Executed in a background coroutine on `OmniForgeApp.onCreate()`.
  - **Safety Guard:** Scratch files (`pdfbox/`, `input_*`, `decrypted_*`) are only deleted if `lastModified < appStartTime`, preventing race conditions with active jobs. Outputs older than 24 hours are deleted.
  - **Tests Added:** Added `testCleanStaleCacheDir` to [`FileUtilsTest.kt`](./app/src/test/java/com/omniforge/app/core/util/FileUtilsTest.kt).

#### 4. WAV Audio Extractor Float PCM Downsampling (`1D.2`)
- **Problem:** In `VideoEngine.decodeToWav`, the WAV header placeholder was 44 bytes written before format discovery. If MediaCodec outputted `AudioFormat.ENCODING_PCM_FLOAT` (which modern Android decoders do for certain AAC/Opus streams), writing a 16-bit header caused screeching static. Furthermore, writing a 56-byte Format 3 IEEE float header at the end would overwrite the first 12 bytes of raw audio data.
- **Fix:**
  - Detect `AudioFormat.ENCODING_PCM_FLOAT` from `codec.outputFormat`.
  - Downsample float samples to 16-bit signed integer PCM on the fly:
    `val s16 = (sample.coerceIn(-1.0f, 1.0f) * 32767.0f).roundToInt().toShort()`
  - Write standard 16-bit PCM bytes to disk.
  - Keeps the 44-byte format 1 header intact with zero audio byte overwrites, universal compatibility with all media players, and 50% smaller file size.

#### 5. Documentation Alignment (`1D.3`)
- Updated [`README.md`](./README.md) to replace all legacy FFmpeg-Kit references with AndroidX Media3 Transformer, clarify hardware-accelerated local transcoding, align tool titles, and document Android 10+ and notification permissions.

---

## 3. Comprehensive File Audit

| File Path | Changes Made |
|---|---|
| `app/src/main/AndroidManifest.xml` | Added `android:launchMode="singleTop"` to `MainActivity`. |
| `app/src/main/java/com/omniforge/app/MainActivity.kt` | Added `themeRecomposeTrigger` link to `OmniForgeTheme`; added `navEvents` Channel and `onNewIntent` route navigation. |
| `app/src/main/java/com/omniforge/app/OmniForgeApp.kt` | Launched `FileUtils.cleanStaleCache` on `Dispatchers.IO` with `appStartTime`. |
| `app/src/main/java/com/omniforge/app/core/jobs/ToolJobs.kt` | Updated `launch` to pass `appContext: Context` into the coroutine block. |
| `app/src/main/java/com/omniforge/app/core/jobs/ToolJobService.kt` | Implemented `onTimeout` for Android 15 FGS; switched to `ic_stat_notify`; updated `openToolIntent` flags to `singleTop`. |
| `app/src/main/java/com/omniforge/app/core/settings/AppSettings.kt` | Added `hasAskedNotificationPermission` SharedPreferences persistence. |
| `app/src/main/java/com/omniforge/app/core/theme/Theme.kt` | Added `recomposeTrigger: Int = 0` to trigger theme recomposition. |
| `app/src/main/java/com/omniforge/app/core/ui/CommonComponents.kt` | Added `allowOverwrite` and `disallowedReason` to `SaveModeSelector`; persistent notification permission check. |
| `app/src/main/java/com/omniforge/app/core/ui/PdfPasswordGate.kt` | Added `pageCount` to `onReady` callback. |
| `app/src/main/java/com/omniforge/app/core/util/FileUtils.kt` | Added `DOWNLOAD_SUBFOLDER`; `isImageFormatMatching`; `isVideoMp4`; `cleanStaleCacheDir`; `cleanStaleCache`. |
| `app/src/main/java/com/omniforge/app/engine/image/ImageEngine.kt` | White canvas compositing for JPEG; `targetFormat` in `shrinkImage`; lossless EXIF stripping preserving orientation. |
| `app/src/main/java/com/omniforge/app/engine/pdf/PdfEngine.kt` | Added `calculateA4Fit`; `computeEveryNChunks`; filtered out-of-bounds ranges in `multiSplitPdf`. |
| `app/src/main/java/com/omniforge/app/engine/video/VideoEngine.kt` | Handled `ENCODING_PCM_FLOAT` in `decodeToWav` by downsampling to 16-bit PCM. |
| `app/src/main/java/com/omniforge/app/feature/home/HomeScreen.kt` | Standardized screen titles. |
| `app/src/main/java/com/omniforge/app/feature/image/ExifStripperScreen.kt` | Injected `appContext` into `ToolJobs.launch`; `AppSettings.defaultSaveMode`. |
| `app/src/main/java/com/omniforge/app/feature/image/ImageConvertScreen.kt` | Injected `appContext` into `ToolJobs.launch`; `AppSettings.defaultSaveMode`; overwrite safety. |
| `app/src/main/java/com/omniforge/app/feature/image/ImageShrinkScreen.kt` | Injected `appContext` into `ToolJobs.launch`; `AppSettings.defaultSaveMode`; pass `outputFormat`; overwrite safety. |
| `app/src/main/java/com/omniforge/app/feature/image/DocumentScanScreen.kt` | Injected `appContext` into `ToolJobs.launch`. |
| `app/src/main/java/com/omniforge/app/feature/pdf/ImagesToPdfScreen.kt` | Injected `appContext` into `ToolJobs.launch`. |
| `app/src/main/java/com/omniforge/app/feature/pdf/PdfShrinkScreen.kt` | Injected `appContext` into `ToolJobs.launch`; `AppSettings.defaultSaveMode`; overwrite protection for $\ge$ size. |
| `app/src/main/java/com/omniforge/app/feature/pdf/PdfSplitScreen.kt` | Injected `appContext` into `ToolJobs.launch`; strict range validation UI; dynamic chunk count. |
| `app/src/main/java/com/omniforge/app/feature/pdf/PdfToImagesScreen.kt` | Injected `appContext` into `ToolJobs.launch`; displays `pageCount`. |
| `app/src/main/java/com/omniforge/app/feature/pdf/PdfUnlockScreen.kt` | Injected `appContext` into `ToolJobs.launch`. |
| `app/src/main/java/com/omniforge/app/feature/video/AudioExtractScreen.kt` | Injected `appContext` into `ToolJobs.launch`. |
| `app/src/main/java/com/omniforge/app/feature/video/VideoCaptionScreen.kt` | Injected `appContext` into `ToolJobs.launch`; font size label fix. |
| `app/src/main/java/com/omniforge/app/feature/video/VideoConvertScreen.kt` | Injected `appContext` into `ToolJobs.launch`; removed invalid SaveModeSelector. |
| `app/src/main/java/com/omniforge/app/feature/video/VideoShrinkScreen.kt` | Injected `appContext` into `ToolJobs.launch`; `AppSettings.defaultSaveMode`; overwrite protection for $\ge$ size and non-MP4. |
| `app/src/main/java/com/omniforge/app/feature/video/VideoToGifScreen.kt` | Injected `appContext` into `ToolJobs.launch`. |
| `app/src/main/java/com/omniforge/app/feature/video/VideoTrimMuteScreen.kt` | Injected `appContext` into `ToolJobs.launch`. |
| `app/src/main/res/drawable/ic_stat_notify.xml` | Created 24dp monochrome notification icon. |
| `app/src/test/java/com/omniforge/app/engine/pdf/PdfA4FitTest.kt` | Added unit tests for A4 image scaling and margins. |
| `app/src/test/java/com/omniforge/app/engine/pdf/PdfSplitTest.kt` | Added unit tests for PDF chunking and bounds. |
| `app/src/test/java/com/omniforge/app/core/util/FileUtilsTest.kt` | Added unit tests for MIME format matching and cache cleanup. |
| `README.md` | Updated for Media3 architecture and current features. |
| `FIX_PLAN.md` | Checked off all completed items across Batches 1–4. |

---

## 4. Suggested Audit Points for Claude

When reviewing these changes, please inspect:
1. **Activity Context Leak Elimination:** Verify that no lambda passed to `ToolJobs.launch` references `LocalContext.current` (outer `context`). All 15 screens should only reference `appContext`.
2. **Channel vs. SharedFlow Navigation:** Check [`MainActivity.kt`](./app/src/main/java/com/omniforge/app/MainActivity.kt) to confirm that `navEvents: Channel<String>` correctly guards against duplicate navigations on activity recreation/rotation and handles `onNewIntent`.
3. **Float to 16-Bit PCM Downsampling:** Check [`VideoEngine.kt:decodeToWav`](./app/src/main/java/com/omniforge/app/engine/video/VideoEngine.kt) to confirm that the sample conversion `(sample.coerceIn(-1.0f, 1.0f) * 32767.0f).roundToInt().toShort()` preserves byte alignment with the 44-byte WAV header.
4. **Cache Cleanup Race Conditions:** Inspect `FileUtils.cleanStaleCacheDir` to verify that `appStartTime` properly protects freshly created input files from being pruned by the background startup cleanup.
5. **Lossless EXIF Tag Stripping:** Inspect `ImageEngine.stripExif` to confirm that `TAG_ORIENTATION` is preserved on JPEG while clearing privacy tags (`GPS`, `Make`, `Model`, `DateTime`, etc.).
