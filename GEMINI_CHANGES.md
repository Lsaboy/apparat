# Gemini Changes - Digital Pocket Knife (OmniForge)
**Date:** October 17, 2026  
**Commit Range:** `8ebf590` -> `HEAD` (11 batches)  
**Total Impact:** 44 files changed, +2,250 lines, -460 lines  
**Build & Test Status:** All 52 JUnit tests passing, `./gradlew assembleDebug` passing (0 errors).

---

## 1. Executive Summary

This document details all modifications made to the Digital Pocket Knife repository for peer review and architectural verification by Claude.

All changes were implemented to resolve safety, data corruption, memory leak, and UX issues outlined in `FIX_PLAN.md` without introducing regressions into Media3, PDFBox, or Kotlin coroutines. Every batch underwent peer review by a Pro-tier code auditor before implementation.

### Commit History
1. `2af848f` - **Batch 1:** Save folder consistency, image transparency compositing, photo shrink format, and A4 PDF fit (`1B.5`, `1B.8`, `1B.10`, `1C.4`, `1D.2`, `1D.3`).
2. `10357f7` - **Batch 2:** PDF split chunking & range validation, compress overwrite protection, notification permission persistence, and standardized screen titles (`1B.1`, `1B.2`, `1B.3`, `1D.2`, `1D.3`).
3. `feb5b2d` - **Batch 3:** Theme recomposition, default save mode setting, overwrite safety & format matching, and lossless JPEG EXIF stripping (`1C.1`, `1C.2`, `1B.7`, `1B.11`).
4. `35c4f6d` - **Batch 4:** ToolJobs Activity leak prevention, singleTop backstack preservation, safe startup cache cleanup, float PCM WAV downsampling, and Media3 README (`1C.5`, `1D.2`, `1D.3`).
5. `aeb768a` - **Batch 5:** Save completion resilience, share-in rotation safety, animated image notice, and audio/video tooling (`1D.2`, `1D.3`, `3B`).
6. `3d66375` - **Batch 6:** Hash Verifier multi-digest & FGS, QR Studio 50x speedup, Base64 URL-safe, Media3 HDR tone-mapping, and encryption honesty (`1D.2`, `3D`).
7. `85e8fa0` - **Batch 7:** Search keywords for 20 tools, PDF unlock encryption detection, JSON line/col reporting, timestamped PDF output, and APK naming (`2.5`, `2.7`, `3B`, `3D`).
8. `964f437` - **Batch 8:** Comprehensive share sheet & open-with intent routing, initial URI rotation safety, QR Studio templates, and Base64 binary magic-byte detection (`2.8`, `3B`, `3D`, `3F`).
9. `78b2521` & `5bb9f4d` - **Batch 9:** ResultCard direct file opening & image preview, lossless WebP, palette extractor polish, and video share-in routing (`3B` lines 460, 479, 484-486, 497, 502-504).
10. `bde6adc` & `a5f97be` - **Batch 10:** ACTION_PROCESS_TEXT with 4 activity-aliases, Photo Shrink quick presets & continuous slider, video metadata inspection in `fileDetails`, ResultCard `ToolChainAction` assist chips, `UtilityEngine.calculateTextChecksums`, version bump to 1.0.1, 49 passing unit tests (`3B`, `3C`, `3D`, `3E`).
11. `HEAD` - **Batch 11:** Dynamic Quick Actions & Android launcher shortcuts, manifest shortcut conflict removal, deep link scheme safety, HashVerifier chaining fix, AudioExtract metadata & M4A notice, DocumentScan preview & chaining, VideoToGif frame/size estimate, and LauncherShortcutsTest (`1C.3`, `3B`, `3C`, `3D`).

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

### Batch 5: Save Resilience, Share-In Safety, Animated Image Warnings & Video Tooling

#### 1. Save Completion Resilience (`1D.2`)
- **Root Cause:** In `ResultCard` and the multi-file screens (`PdfSplitScreen`, `PdfToImagesScreen`), file export was tied to the composable's coroutine scope. Navigating away or rotating the screen mid-save cancelled the IO operation, leaving partial/corrupt files or silent failures. Furthermore, displaying Toasts with `LocalContext.current` (Activity) after `NonCancellable` blocks caused crashes or memory leaks when the Activity had already finished.
- **Solution:**
  - In `ResultCard` ([`CommonComponents.kt`](./app/src/main/java/com/omniforge/app/core/ui/CommonComponents.kt)), wrapped `onSaveToDownloads()` inside `withContext(NonCancellable)`.
  - In `PdfSplitScreen.kt` and `PdfToImagesScreen.kt` ("Save All"), wrapped the entire file copying loop inside `withContext(NonCancellable)` and reported honest progress ("Saved X of Y files to Downloads/${FileUtils.DOWNLOAD_SUBFOLDER}").
  - In all 13 screens calling `ResultCard`, updated `onSaveToDownloads` to extract `val appContext = context.applicationContext`, passing `appContext` to both `FileUtils.saveToDownloads` and `Toast.makeText`.
  - Renamed `SaveModeSelector` label from "Modify OG" to "Replace Original" with single-line maxLines and ellipsis.

#### 2. Stale State on Share-In (`1D.2`)
- **Root Cause:** When sharing a file via Android's share sheet into an active tool screen (`PdfShrink`, `VideoShrink`, `VideoConvert`, `ImageShrink`, `ImageConvert`, `ExifStripper`), `retained(...)` ignored the new `initialUri` while a job was running, or unconditionally overwriting state would clobber in-progress operations. Device rotation also re-passed `initialUri`, clobbering user-picked files.
- **Solution:**
  - Added retained states `lastHandledInitialUri` and `pendingShareUri`.
  - In `LaunchedEffect(initialUri)`: if `initialUri` arrives while a background job is running on another file, it sets `pendingShareUri` and surfaces a prominent card: *"Incoming shared file: [filename]. A job is currently running on another file. Switch to this new file or dismiss it."*
  - If the tool is idle, it immediately adopts the new file and clears previous processing state.
  - On device rotation, `initialUri == lastHandledInitialUri` evaluates to true, preventing any state wipe or reversion to previously shared files.

#### 3. Animated Image Detection & Info Notice (`1D.2`)
- **Root Cause:** `BitmapDecoder` and Android's `ImageDecoder.decodeBitmap` decode only the first frame of animated GIF/WebP files without notifying the user, causing silent conversion to static frames. Calling full `ImageDecoder.decodeDrawable` on every image risked memory spikes (~200MB allocation for 48MP photos).
- **Solution:**
  - In `BitmapDecoder.kt`, implemented `isAnimated(context: Context, uri: Uri): Boolean` using zero-allocation fast paths:
    - MIME / extension check for `.gif` (returns `true`) and `.jpg`/`.bmp`/`.heic` (returns `false`).
    - WebP 21-byte header parsing (`isAnimatedWebpHeader`): reads 21 bytes, verifies `RIFF...WEBP`, and inspects byte 20 of chunk `VP8X` where bit 1 (`0x02`) indicates animation.
    - Fallback: minimal 1x1 downscaled decode via `ImageDecoder.decodeDrawable { decoder.setTargetSize(1, 1) }` checking `drawable is AnimatedImageDrawable`.
  - Added pure unit tests in `BitmapDecoderTest.kt` covering valid animated WebP headers, static WebP headers, and truncated headers.
  - In `ImageConvertScreen.kt` and `ImageShrinkScreen.kt`, display an info notice card when an animated image is loaded: *"Note: Animated images are processed as a still frame."*

#### 4. Audio Extraction & Video-to-GIF UI/Safety (`1D.3` / `3B`)
- **Root Cause:**
  - `AudioExtractScreen.kt` picker was restricted to `video/*` and placeholder read "Select Video to Rip Audio", preventing users from converting audio formats (e.g. AAC to WAV). `VideoEngine.kt` threw "This video has no audio track" even when an audio file was selected.
  - `VideoToGifScreen.kt` hardcoded `startSec = 0f` and lacked duration bounds, causing `IllegalArgumentException` ("Start time is past the end of the video") on short clips.
- **Solution:**
  - In `AudioExtractScreen.kt`: updated picker to `arrayOf("video/*", "audio/*")` and placeholder to "Select Video or Audio to Convert".
  - In `VideoEngine.kt`: updated error to "Selected file contains no audio track".
  - In `VideoToGifScreen.kt`: extracted video duration via `MediaMetadataRetriever`, added a Start Offset slider clamped to `(videoDuration - 1s).coerceIn(0s, 60s)`, and clamped the duration slider to prevent overshooting clip length.

---

### Batch 6: Hash Verifier Multi-Digest & FGS, QR Studio 50x Speedup, Base64 URL-Safe & Strict Decoding, Media3 HDR Tone-Mapping, and Encryption Honesty

#### 1. Hash Verifier Single-Pass Multi-Digest & Background Execution (`1D.2` / `3D`)
- **Root Cause:**
  - `HashVerifierScreen.kt` ran hashing directly on local coroutine scope without `ToolJobs`, meaning navigation or app backgrounding cancelled long hashes on multi-GB files.
  - State used `remember`, wiped on device rotation.
  - Files were read sequentially twice (once for MD5, once for SHA-256) with small 8KB buffers, doubling disk IO and battery drain. SHA-1 (advertised on home screen) and SHA-512 were missing.
  - Hash comparison was limited to SHA-256 and MD5, and had no clipboard auto-check or paste action.
- **Solution:**
  - In `UtilityEngine.kt`, implemented `calculateChecksums(context, uri, algorithms, onProgress)`: reads the file in a single streaming pass using a 64KB buffer, updating `MessageDigest` instances concurrently. Emits throttled progress updates ($\ge 100\text{ms}$) to avoid main thread UI jank, safeguards against unreadable or zero-byte file sizes (`fileSize <= 0`), and ensures coroutine cooperative cancellation via `coroutineContext.ensureActive()`.
  - In `HashVerifierScreen.kt`:
    - Migrated all screen state to `retained(JOB_KEY, ...)` and integrated `ToolJobs.launch(context, JOB_KEY, "Hash Verifier") { appContext -> ... }` with `ProcessStateView` and `ToolScaffold(jobKey = JOB_KEY)`.
    - Render cards for all 4 computed hashes (SHA-256, SHA-512, SHA-1, MD5) with individual copy buttons.
    - Added hash comparison field checking against all 4 computed hashes case-insensitively, with visual match/mismatch cues and an Android 12+ compliant "Paste from Clipboard" button that avoids unsolicited clipboard access toasts.

#### 2. QR Studio 50x Optimization, Keystroke Debounce & Background Generation (`3D`)
- **Root Cause:**
  - `UtilityEngine.generateQrCode` executed 262,144 JNI `setPixel(x, y)` calls in a nested loop for every keystroke directly on the main thread, causing severe UI freezes and frame drops.
  - Saving and sharing ran directly in the UI scope without `NonCancellable` or `appContext`.
- **Solution:**
  - In `UtilityEngine.kt`, refactored `generateQrCode` to populate an in-memory `IntArray(size * size)` with row-major stride `[y * size + x]` and dispatch a single bulk JNI `bitmap.setPixels(pixels, 0, size, 0, 0, size, size)` call using `Bitmap.Config.ARGB_8888`.
  - In `QrStudioScreen.kt`:
    - Added 150ms debounce (`delay(150)`) and offloaded QR generation to `Dispatchers.Default`.
    - Retained user input text across device rotations via `retained(JOB_KEY, "inputText")`.
    - Protected file save and share actions with `withContext(NonCancellable)` and `appContext`.

#### 3. Base64 URL-Safe Toggle & Resilient Strict UTF-8 Decoding (`3D`)
- **Root Cause:**
  - `UtilityEngine.decodeBase64` used `Base64.DEFAULT`. When decoding URL-safe Base64 strings (containing `-` or `_`), it crashed with `IllegalArgumentException: bad base-64`.
  - Decoding binary data (e.g. image/PDF Base64) with `String(bytes, UTF_8)` silently injected Unicode replacement characters (`\uFFFD`) and corrupted data.
- **Solution:**
  - In `UtilityEngine.kt`:
    - Added `urlSafe: Boolean` parameter to `encodeBase64` and `decodeBase64`.
    - Made `decodeBase64` resilient by attempting primary flags (`DEFAULT` vs `URL_SAFE`) and automatically falling back to alternate flags before failing.
    - Replaced naive string conversion with a strict `CharsetDecoder` configured with `CodingErrorAction.REPORT`. If binary data is passed, it throws a descriptive exception: `"Decoded N bytes of binary data (not valid UTF-8 text)."`.
  - In `Base64StudioScreen.kt`:
    - Added a URL-Safe FilterChip toggle allowing users to switch between standard (`+`, `/`) and URL-safe (`-`, `_`) modes.
    - Retained mode, input, and output across screen rotations.

#### 4. Media3 OpenGL HDR Input Tone-Mapping to SDR (`1D.2`)
- **Root Cause:**
  - Phone cameras (Pixel, Samsung, iPhone) record 10-bit HDR (HDR10/HLG). Media3 Transformer with H.264 SDR output failed with `ENCODER_INIT_FAILED` / `DECODING_FORMAT_UNSUPPORTED` or produced washed-out grey footage.
- **Solution:**
  - In `VideoEngine.kt:runTransformer`, wrapped the media item in `EditedMediaItemSequence(item)` and built a `Composition` with `setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)` whenever video re-encoding is active (`forceVideoEncode || videoMimeType != null || item.effects.videoEffects.isNotEmpty()`) and video is not being removed.
  - Pure transmuxing operations (`losslessTrim`, `stripAudio`, `extractAudio`) omit tone-mapping to preserve instantaneous lossless stream copying.

#### 5. Encryption Honesty in Output Messages (`1D.2`)
- **Root Cause:**
  - When processing encrypted PDFs, `PdfSplitScreen` stripped the encryption dictionary (output files have no password), while `PdfShrinkScreen` re-saved the document preserving the original encryption dictionary. Users were not told what happened to their document passwords.
- **Solution:**
  - In `PdfSplitScreen.kt`: if `!pdfPassword.isNullOrEmpty()`, appended `(Output files have no password)` to the success message.
  - In `PdfShrinkScreen.kt`: if `!pdfPassword.isNullOrEmpty()`, appended `(Original password kept)` across all completion scenarios (standard compress, in-place overwrite, and already-optimized).

---

### Batch 7: Search Keywords for 20 Tools, PDF Unlock Encryption Detection, JSON Line/Column Reporting, Timestamped PDF Output, and APK Naming

#### 1. Home Screen Multi-Word Search & Tool Keywords (`3B` / line 507)
- **Root Cause:**
  - Searching for common actions or formats like "resize", "merge", "combine", "jpg", "reduce size", "cut", "audio", "subtitles" yielded 0 results because search only matched substrings of the title and short description.
  - Substring search failed on multi-word queries (e.g. "merge pdf") and trailing spaces typed by mobile keyboards ("jpg ").
- **Solution:**
  - Added `val keywords: List<String> = emptyList()` to `ToolItem` in `ToolModels.kt`.
  - Populated comprehensive keyword sets for all 20 tools in `HomeScreen.kt` covering verbs, synonyms, and file extensions.
  - Implemented tokenized search in `HomeScreen.kt`: splits queries by whitespace (`query.trim().lowercase().split(Regex("\\s+"))`) and requires that all tokens match across the tool's title, description, or keyword list.
  - Created unit test suite [`HomeSearchTest.kt`](./app/src/test/java/com/omniforge/app/feature/home/HomeSearchTest.kt) with 5 test cases validating multi-word matching, trailing spaces, verb aliases, and empty queries.

#### 2. PDF Unlock Encryption Detection & Copy Cleanup (`2.7` / lines 418, 424)
- **Root Cause:**
  - In `PdfUnlockScreen.kt`, selecting an unencrypted PDF allowed users to enter dummy text and click "Unlock", which blindly re-saved the document without informing the user that the file had no password.
  - Misleading terminology "DRM-free" was used throughout labels and buttons (decryption removes passwords/permissions, not DRM).
  - Failed password attempts wrapped in generic `IllegalArgumentException("Incorrect password or corrupt PDF: ...")`.
  - In `PdfEngine.kt:removePassword`, failure left an orphan temp file in the cache directory.
- **Solution:**
  - In `PdfUnlockScreen.kt`:
    - Integrated `LaunchedEffect(selectedUri)` checking access via `PdfEngine.checkAccess(appContext, uri)`.
    - Retained `accessStatus` and `isCheckingAccess` across device rotations.
    - If `PdfAccess.Open` with `!isEncrypted`: displays an informative card (*"This PDF has no password. It is already unlocked and does not require decryption."*) and disables the Unlock button.
    - If `PdfAccess.Open` with `isEncrypted` (owner password restriction): explains permissions removal.
    - Replaced all "DRM-free" copy with clear "unrestricted, unlocked PDF" terminology.
  - In `PdfEngine.kt:removePassword`: explicitly catches `PdfPasswordException` and deletes the temp output file in a `finally` block if processing does not complete successfully.

#### 3. JSON Tool Retained State, Error Line/Col Clarification & Branding (`3D` / line 559)
- **Root Cause:**
  - `JsonToolScreen.kt` held input, output, and errors in `remember`, which wiped user work on screen rotation.
  - Sample JSON template hardcoded `"name": "OmniForge"`.
  - Syntax errors reported cryptic messages without line or column context.
- **Solution:**
  - In `UtilityEngine.kt:formatJson`: parsed character offset indices from `JSONException` (e.g. `at character 45`) to calculate exact 1-based `Line X, Col Y: <description>`.
  - In `JsonToolScreen.kt`: migrated all state to `retained(JOB_KEY, ...)`, updated sample branding to "Digital Pocket Knife", and added "Clear" and "Paste" actions.

#### 4. Photos to PDF Dynamic Timestamped Output Naming (`2.5` / line 399)
- **Root Cause:**
  - `ImagesToPdfScreen.kt` hardcoded the output filename to `combined_photos.pdf`. Multiple exports caused filename collisions in `Downloads/DigitalPocketKnife`.
- **Solution:**
  - Replaced static naming with timestamped format: `Photos_${SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())}.pdf`. Using explicit `Locale.US` prevents issues with Eastern Arabic / Devanagari numerals on regional device locales.

#### 5. Output APK Naming & Root Project Name (`3B` / line 517)
- **Root Cause:**
  - Output APKs defaulted to `app-debug.apk` and `settings.gradle.kts` still identified the root project as `"OmniForge"`.
- **Solution:**
  - In `app/build.gradle.kts`: added `base { archivesName.set("DigitalPocketKnife") }`, generating `DigitalPocketKnife-debug.apk` and `DigitalPocketKnife-release.apk`.
  - In `settings.gradle.kts`: updated `rootProject.name = "DigitalPocketKnife"`.
  - In `app/proguard-rules.pro`: updated comment reference to Digital Pocket Knife.

---

### Batch 8: Comprehensive Share-In & Open-With Intent Routing, Rotation Safety, QR Studio Templates, and Base64 Binary Detection
**Commit:** `964f437`

#### 1. Comprehensive Share-In & Open-With Intent Routing (`2.8` / `3F` / lines 427, 428, 564)
- **Root Cause:**
  - `AndroidManifest.xml` lacked intent-filters for `ACTION_VIEW` for PDFs and `ACTION_SEND` for `text/plain` and generic files `*/*`. Digital Pocket Knife never appeared in Android's "Open with" chooser or system share sheets.
  - In `MainActivity.kt`, incoming intent handling only ran in `onCreate` and checked `ACTION_SEND` with `EXTRA_STREAM`. When the activity had `launchMode="singleTop"`, receiving an intent while in the background triggered `onNewIntent`, which was ignored, deadlocking incoming shares.
  - Text payloads (e.g. shared JSON or URLs) were not routed to appropriate utility tools.
- **Solution:**
  - In `AndroidManifest.xml`:
    - Added `ACTION_VIEW` intent-filter for MIME type `application/pdf` with schemes `content` and `file` under `DEFAULT` category (omitting `BROWSABLE` to adhere to Android 12+ strict web-intent verification requirements).
    - Added `ACTION_SEND` intent-filter for MIME type `text/plain` under `DEFAULT`.
    - Added `ACTION_SEND` intent-filter for MIME type `*/*` under `DEFAULT` with `android:priority="-1"` so it handles generic file sharing into Hash Verifier without hijacking specialized viewers.
  - In `MainActivity.kt`:
    - Defined immutable `SharedPayload(uri: Uri?, mimeType: String?, text: String?, token: Long = System.currentTimeMillis())`.
    - Managed active payload with `var currentSharedPayload by mutableStateOf<SharedPayload?>(null)`.
    - Consolidated intent parsing in `handleIncomingIntent(intent)` invoked by both `onCreate` and `onNewIntent`.
    - Handled text routing: URLs route directly to `qr_studio`, valid JSON strings route to `json_tool`.
    - For PDF and media files, displayed contextual share banner on `HomeScreen` offering quick action buttons to route into any compatible tool, and forwarded `initialUri` and `initialText` to all destinations in `NavHost`.

#### 2. Initial URI Acceptance & Rotation Safety Across PDF & Utility Tools (`2.8` / `1D.2`)
- **Root Cause:**
  - `PdfSplitScreen.kt`, `PdfToImagesScreen.kt`, `PdfUnlockScreen.kt`, and `HashVerifierScreen.kt` lacked `initialUri` parameters and could not accept shared files.
  - In existing share-in screens, file pickers were updating `lastHandledInitialUri = it`. When the device was rotated, Compose evaluated `initialUri != lastHandledInitialUri` as true because `lastHandledInitialUri` had been overwritten with the user-picked file, reverting the screen back to the stale share URI.
- **Solution:**
  - Added `initialUri: Uri? = null` parameter, `lastHandledInitialUri`, and `pendingShareUri` to `PdfSplitScreen.kt`, `PdfToImagesScreen.kt`, `PdfUnlockScreen.kt`, and `HashVerifierScreen.kt`.
  - Removed `lastHandledInitialUri = it` from file pickers across all screens so external URIs are strictly decoupled from manual file picker selections.
  - If a job is currently running when a new share arrives, displays a pending share card offering "Switch to New File" or "Dismiss" without interrupting the active background job.
  - `HashVerifierScreen.kt` automatically triggers single-pass multi-digest calculation immediately upon receiving an initial URI.

#### 3. QR Studio Templates (Wi-Fi, UPI Pay, Contact vCard) with ZXing & NPCI Spec Compliance (`3D` / line 549)
- **Root Cause:**
  - `QrStudioScreen.kt` only supported raw text input, requiring users to manually format complex Wi-Fi, UPI, or vCard strings.
- **Solution:**
  - In `UtilityEngine.kt`:
    - Added `escapeWifiField` implementing strict ZXing spec escaping: backslash `\` is escaped first, followed by `;`, `,`, `:`, and `"`.
    - Added `formatWifiQr(ssid, password, securityType, isHidden)` supporting `WPA`, `WEP`, and `nopass`.
    - Added `formatUpiQr(vpa, name, amount, note)` following the NPCI UPI QR specification, URL-encoding query parameters (`pa`, `pn`, `am`, `cu=INR`, `tn`) using `URLEncoder.encode(..., "UTF-8")`.
    - Added `formatVCardQr(name, phone, email, company, title)` conforming to standard vCard 3.0 format.
  - In `QrStudioScreen.kt`:
    - Added FilterChips for Text/URL, Wi-Fi, UPI Payment, and Contact.
    - Rendered dedicated input fields per template with retained state across rotations and live debounced generation on `Dispatchers.Default`.

#### 4. Base64 Binary Magic-Byte Detection, Data URL Stripping & Downloads Export (`3D` / lines 557-558)
- **Root Cause:**
  - `Base64StudioScreen.kt` only supported text decoding. Pasting Base64-encoded files (images, PDFs) caused decoding failures or rendered raw binary replacement characters (`\uFFFD`).
  - Base64 data copied from browsers often includes `data:[<mediatype>][;base64],` prefixes which caused decode errors.
- **Solution:**
  - In `UtilityEngine.kt`:
    - Added `stripDataUrlPrefix` regex stripping `^data:[^,]+;base64,` case-insensitively.
    - Added `decodeBase64ToBytes` supporting both standard and URL-safe Base64.
    - Added `detectBinaryType(bytes: ByteArray)` with header guards requiring $\ge 12$ bytes: checks PNG (`89 50 4E 47 0D 0A 1A 0A`), JPEG (`FF D8 FF`), WebP (`RIFF` at 0..3 and `WEBP` at 8..11), GIF (`GIF87a`/`GIF89a`), and PDF (`%PDF-`).
  - In `Base64StudioScreen.kt`:
    - Added Clear and Paste buttons.
    - In Decode mode, if binary data is detected, displays a dedicated card showing the detected file format, MIME type, and byte size.
    - Added "Save Decoded File to Downloads" button running on `Dispatchers.IO + NonCancellable` with timestamped filename (`Decoded_yyyyMMdd_HHmmss.<ext>`).

### Batch 9: ResultCard Direct Open & Image Preview, Lossless WebP, Palette Extractor Polish, and Video Share-In Routing
**Commit:** `78b2521` & `5bb9f4d`

#### 1. ResultCard Upgrade with Direct "Open File" via FileProvider & Image Thumbnail Preview (`3B` / line 497)
- **Problem:**
  - After processing, users only had "Save" and "Share". To view the result, they had to exit the app and open an external file manager.
  - No visual confirmation was shown for generated images in the result card.
- **Solution:**
  - In `FileUtils.kt`:
    - Added `openFile(context: Context, file: File, mimeType: String? = null)`.
    - Uses `FileProvider.getUriForFile` (`${context.packageName}.fileprovider`).
    - Uses `intent.setDataAndType(contentUri, resolvedMime)` (avoiding the separate `intent.data = ...` + `intent.type = ...` bug where setting type clears data).
    - Sets `intent.clipData = ClipData.newRawUri("Output File", contentUri)` and `FLAG_GRANT_READ_URI_PERMISSION` so read permissions survive across `Intent.createChooser()`.
    - Handles `ActivityNotFoundException` and `SecurityException` gracefully with user-facing toasts.
  - In `CommonComponents.kt`:
    - Upgraded `ResultCard` to a 2-tier ergonomic action layout: Tier 1 full-width "Save to Downloads" button (with progress indicator and "Saving to Downloads…" text); Tier 2 side-by-side "Open" (using `Icons.AutoMirrored.Filled.OpenInNew`) and "Share" buttons (`weight(1f)` each).
    - If `outputFile` has an image extension (`.jpg`, `.jpeg`, `.png`, `.webp`, `.gif`), renders an `AsyncImage` (Coil) preview with bounded dimensions (`size(512, 512)`), `ContentScale.Fit`, and `150.dp` height inside a clipped rounded surface.

#### 2. Lossless WEBP Compression Support (`3B` / line 460)
- **Problem:**
  - WebP encoding in `ImageEngine.kt` previously used deprecated `Bitmap.CompressFormat.WEBP` or lossy encoding regardless of quality setting.
- **Solution:**
  - In `ImageEngine.kt` (`shrinkImage` and `convertFormat`):
    - When `targetFormat == ImageOutputFormat.WEBP`: on API 30+ (Android R+), uses `Bitmap.CompressFormat.WEBP_LOSSLESS` when `quality >= 100`, and `Bitmap.CompressFormat.WEBP_LOSSY` when `quality < 100`. On API < 30, falls back to `Bitmap.CompressFormat.WEBP`.
  - In `ImageShrinkScreen.kt`:
    - Expanded slider range from `10f..95f` to `10f..100f` (`steps = 17`).
    - Displays `(Lossless WEBP)` badge in the quality label when 100% quality is selected.
  - In `ImageConvertScreen.kt`:
    - Displays `(Lossless WEBP)` badge when 100% quality is selected.

#### 3. EXIF Stripper "No Metadata Found" Card & Photo Preview (`3B` / line 479)
- **Problem:**
  - In `ExifStripperScreen.kt`, if an image had no EXIF tags (already clean or screenshot), the screen rendered an empty metadata section without reassuring the user.
  - The screen did not show a photo preview of the selected image.
- **Solution:**
  - In `ImageEngine.kt`:
    - Added `ExifInfo.hasAnyMetadata(): Boolean` which inspects `cameraMake`, `cameraModel`, `dateTime`, `iso`, `fNumber`, `exposureTime`, and `(latitude != null && longitude != null)`.
  - In `ExifStripperScreen.kt`:
    - Added Coil `AsyncImage` preview of the selected image with bounded size (`size(512, 512)`).
    - When `!info.hasAnyMetadata()`, renders a dedicated reassurance card with `Icons.Default.VerifiedUser` in `SuccessGreen` informing the user that the photo is already free of private metadata tags.

#### 4. Palette Extractor Polish, Image Preview & State Retain (`3B` / lines 484-486)
- **Problem:**
  - `PaletteExtractorScreen.kt` naively used `remember`, losing state on screen rotation.
  - It did not accept shared images (`initialUri`), lacked an image preview, and did not offer a quick way to copy all palette colors.
- **Solution:**
  - Migrated state to `retained(JOB_KEY, ...)` (`selectedUri`, `fileName`, `fileSize`, `paletteColors`, `isExtracting`, `lastHandledInitialUri`, `pendingShareUri`).
  - Added `initialUri: Uri? = null` parameter, incoming share banner, and share-in protection matching the rest of the app.
  - Added Coil `AsyncImage` preview of the selected image before and above the palette swatches (`size(512, 512)`).
  - Added "Copy All" button that copies all extracted HEX color codes comma-separated to the clipboard.
  - Displays both HEX (`#RRGGBB`) and RGB (`rgb(r, g, b)`) strings with a 1dp border for swatch contrast.

#### 5. Share-In URI Support & Duration Clamping for Remaining Video Tools (`3B` / lines 502-504)
- **Problem:**
  - Video tools (`VideoTrimMuteScreen`, `VideoToGifScreen`, `AudioExtractScreen`) did not support `initialUri`, missing incoming shared video/audio files.
  - `VideoTrimMuteScreen` and `VideoToGifScreen` did not dynamically query video duration when shared from other apps.
- **Solution:**
  - In `VideoTrimMuteScreen.kt`:
    - Added `initialUri: Uri? = null`, `lastHandledInitialUri`, `pendingShareUri`, incoming share banner.
    - Added dynamic duration extraction via `MediaMetadataRetriever` on `Dispatchers.IO` with file descriptor fallback, clamping trim bounds to actual video length.
  - In `VideoToGifScreen.kt`:
    - Added `initialUri: Uri? = null`, `lastHandledInitialUri`, `pendingShareUri`, incoming share banner.
    - Dynamically queries duration, clamps GIF duration to `min(5f, videoDurationSec)`, and resets start offset when a new video is loaded.
  - In `AudioExtractScreen.kt`:
    - Added `initialUri: Uri? = null`, `lastHandledInitialUri`, `pendingShareUri`, incoming share banner, and `appContext` for sharing.
  - In `MainActivity.kt`:
    - Wired `initialUri` to `video_trim`, `video_to_gif`, `audio_extract`, and `palette_extractor`.
  - In `HomeScreen.kt`:
    - Added direct quick-action `AssistChip`s inside the shared file banner for all compatible tools.

---

### Batch 10: ACTION_PROCESS_TEXT System Integration, Photo Shrink Quick Presets, Video Metadata Inspection, Tool Chaining & Version 1.0.1
**Commit:** `bde6adc`

#### 1. Android System `ACTION_PROCESS_TEXT` Integration (`3F` / lines 557-558)
- **Problem:**
  - When users select text in any Android application (browser, chat, email, notes), Digital Pocket Knife did not appear in the floating system text selection context menu.
- **Solution:**
  - In `AndroidManifest.xml`, declared 4 `<activity-alias>` elements immediately after `</activity>` targeting `MainActivity`:
    - `ProcessTextQrActivity` (`android:label="Make QR Code"`)
    - `ProcessTextJsonActivity` (`android:label="Format JSON"`)
    - `ProcessTextBase64Activity` (`android:label="Base64 Studio"`)
    - `ProcessTextHashActivity` (`android:label="Calculate Hash"`)
  - Each alias registers an `<intent-filter>` for `android.intent.action.PROCESS_TEXT` with `android.intent.category.DEFAULT` and MIME type `text/plain`.
  - In `MainActivity.kt:handleIncomingIntent`:
    - Handles `Intent.ACTION_PROCESS_TEXT`.
    - Extracts text via `intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()`.
    - Inspects `intent.component?.className` matching `.endsWith("ProcessText...")` to map to `qr_studio`, `json_tool`, `base64_studio`, or `hash_verifier`.
    - Populates `currentSharedPayload = SharedPayload(text = processText, mimeType = "text/plain")` and dispatches route event through `navEvents`.
  - In `Base64StudioScreen.kt`:
    - Added `initialText: String? = null` parameter and `LaunchedEffect(initialText)` auto-encoding/decoding support.
  - In `HashVerifierScreen.kt`:
    - Added `initialText: String? = null` parameter and `HashInputMode` enum (`FILE`, `TEXT`).
    - Added `FilterChip` toggle between "Verify File" and "Hash Text".
    - In "Hash Text" mode, computes checksums in-memory via `UtilityEngine.calculateTextChecksums(text)` for `SHA-256`, `MD5`, `SHA-1`, and `SHA-512`.
    - Added character and byte counter (`X characters • Y UTF-8 bytes`), Clear & Paste actions, and live hash comparison.
  - In `UtilityEngine.kt`:
    - Added pure helper `calculateTextChecksums(text: String, algorithms: List<String>): Map<String, String>` computing hex digests from UTF-8 string bytes.

#### 2. Photo Shrink Quick Presets & Continuous Dimension Slider (`3B` / line 453)
- **Problem:**
  - `ImageShrinkScreen.kt` had a stepped `maxDimension` slider with `steps = 4` across `720f..3840f` (only snapping to 720, 1344, 1968, 2592, 3216, 3840). Setting common preset resolutions like 1000px or 1600px caused slider desynchronization and position jumping.
  - Users had to manually guess dimensions and quality percentages for common upload scenarios.
- **Solution:**
  - In `ImageShrinkScreen.kt`:
    - Set `steps = 0` on `maxDimension` slider for smooth, continuous resolution adjustment.
    - Added horizontal scrolling `Quick Presets` FilterChip row above the format selector:
      - **Portal / ID (<100KB)**: 1000px, 70% quality, JPEG
      - **WhatsApp / Chat**: 1600px, 80% quality, WEBP
      - **Email / Social**: 1920px, 85% quality, JPEG
      - **Max Quality**: 3840px, 95% quality, JPEG
    - Tapping a chip updates dimension, quality, and format simultaneously; the active preset highlights whenever current settings match.

#### 3. Video Metadata Inspection (Resolution, Duration & File Size) (`3C` / line 533)
- **Problem:**
  - `VideoConvertScreen` and `VideoShrinkScreen` only displayed filename and file size, giving users no visibility into video resolution, orientation, or playback duration before processing.
- **Solution:**
  - In `VideoEngine.kt`:
    - Created `VideoMetadata(width, height, durationMs, rotation, mimeType)` with formatted helpers `durationFormatted` (`M:SS`) and `resolutionFormatted` (`${width}x${height}`).
    - Added `suspend fun getVideoMetadata(context: Context, uri: Uri): VideoMetadata?` on `Dispatchers.IO`:
      - Content resolver with file-descriptor fallback (`openFileDescriptor(uri, "r")`).
      - Swaps width and height when `rotation == 90 || rotation == 270` so portrait smartphone videos report accurate vertical resolutions.
      - Uses `MediaMetadataRetriever.METADATA_KEY_MIMETYPE`.
      - Reliably releases retriever in a `finally` block.
  - In `VideoConvertScreen.kt` and `VideoShrinkScreen.kt`:
    - Added `videoMetadata` retained state populated via `LaunchedEffect(selectedUri)`.
    - Formatted `fileDetails` in `FileSelectionCard` as `"${resolutionFormatted} • ${durationFormatted} • ${formatFileSize(fileSize)}"`.

#### 4. "Continue with…" Tool Chaining (`3B` / line 496)
- **Problem:**
  - Common multi-step workflows (e.g. Combine Photos to PDF -> Compress PDF; Convert Image -> Shrink Image; Trim Video -> Make GIF) forced users to return to Home, find the next tool, and pick the newly generated file.
- **Solution:**
  - In `CommonComponents.kt`:
    - Defined `data class ToolChainAction(val label: String, val icon: ImageVector? = null, val onClick: () -> Unit)`.
    - Upgraded `ResultCard` to support `chainActions: List<ToolChainAction> = emptyList()`.
    - Renders a horizontal scrolling row of `AssistChip`s below Tier 2 action buttons under a clean "Continue with…" divider.
  - In `FileUtils.kt`:
    - Added `getContentUri(context: Context, file: File): Uri` providing secure `content://com.omniforge.app.fileprovider/...` URIs.
  - In `MainActivity.kt`:
    - Created `navigateToToolWithFile(route: String, uri: Uri)` callback updating `currentSharedPayload` and navigating to the next tool.
  - Wired Chains:
    - `ImagesToPdfScreen` -> "Compress PDF" (`pdf_shrink`)
    - `ImageConvertScreen` -> "Shrink Photo" (`image_shrink`)
    - `VideoTrimMuteScreen` -> "Make GIF" (`video_to_gif`)

#### 5. Version Bump to 1.0.1 (`3E` / line 579)
- **Solution:**
  - In `app/build.gradle.kts`: bumped `versionCode = 2`, `versionName = "1.0.1"`.
  - Output APK builds cleanly as `app/build/outputs/apk/debug/DigitalPocketKnife-debug.apk`.

---

### Batch 11: Dynamic Quick Actions, Launcher Shortcuts Sync, Intent Scheme Isolation & Tool Chaining Expansion
**Commit:** `3673b6e`

#### 1. Manifest Static Shortcuts Conflict & Dynamic Launcher Shortcuts (`1C.3` / line 176 & `3B` / line 503)
- **Problem:**
  - `AndroidManifest.xml` had `<meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts" />` declaring 4 static shortcuts. In Android, static shortcuts count towards the launcher's strict 4-shortcut limit and block dynamic shortcut customization via `ShortcutManagerCompat.setDynamicShortcuts`.
  - Furthermore, `CustomizationScreen` had a "Share Sheet Shortcuts" toggle that updated SharedPreferences `enabled_shortcuts`, but it was never read by the system or launcher.
  - On `HomeScreen`, the top "QUICK SHORTCUTS" row was hardcoded to a static 4-tool list (`QUICK_ACTIONS`), ignoring user customization.
- **Root Cause & Solution:**
  - In `AndroidManifest.xml`: Removed the static `<meta-data android:name="android.app.shortcuts" ...>` tag.
  - In `ToolModels.kt`: Centralized `ALL_TOOLS` so models, settings, and home screen share a single source of truth.
  - In `AppSettings.kt`: Added `updateLauncherShortcuts(context: Context)` which derives up to 4 shortcuts using `ShortcutManagerCompat.setDynamicShortcuts`, preserving deterministic order from `ALL_TOOLS`, setting launcher icon `R.mipmap.ic_launcher`, and deep link URIs (`dpk://${tool.id}`).
  - In `OmniForgeApp.kt`: Initialized dynamic shortcuts on app start via `AppSettings(applicationContext).updateLauncherShortcuts(applicationContext)`.
  - In `CustomizationScreen.kt`: Renamed section to "Quick Actions & Launcher Shortcuts" with clear copy explaining dual effect on the Home top bar and launcher long-press menu. On toggle, calls `appSettings.updateLauncherShortcuts(context)` and `onSettingsChanged()`.
  - In `HomeScreen.kt`: Dynamically derived `quickActions` from `appSettings.enabledShortcuts` mapped to `ALL_TOOLS` and `getCategoryIcon`, falling back to the 4 default tools if no shortcuts are toggled.
- **Tests Added:**
  - `LauncherShortcutsTest.kt` with 3 unit tests:
    - `testDefaultShortcutsExistInAllTools`: checks default shortcuts exist and follow deterministic `ALL_TOOLS` order.
    - `testDynamicShortcutCappingAtFour`: checks that selecting >4 shortcuts caps at exactly 4 in `ALL_TOOLS` order.
    - `testEmptyShortcutFallback`: checks fallback behavior when set is empty.

#### 2. Deep Link Scheme Safety & Hash Verifier Chaining Execution (`3B`, `3D`)
- **Problem:**
  - In `MainActivity.kt:handleIncomingIntent`, `Intent.ACTION_VIEW` was treating all URIs as incoming file shares, including internal deep links (`dpk://<route>`). This polluted `currentSharedPayload` with a pseudo-file URI `dpk://...` and triggered extraneous navigation to "home".
  - In `HashVerifierScreen.kt:52`, `lastHandledInitialUri` was initialized to `initialUri`. When a file was chained into `HashVerifierScreen` via `ToolChainAction` (`navigateToToolWithFile("hash_verifier", uri)`), `LaunchedEffect(initialUri)` checked `initialUri != lastHandledInitialUri`, which evaluated to `false`, failing to trigger automatic hash calculation.
- **Solution:**
  - In `MainActivity.kt`: Guarded `Intent.ACTION_VIEW` with `if (uri != null && uri.scheme != "dpk")`.
  - In `MainActivity.kt`: In `navigateToToolWithFile`, added fallback `"hash_verifier" -> "*/*"` MIME mapping.
  - In `HashVerifierScreen.kt`: Changed `lastHandledInitialUri` initial state to `null`. Now, when chained or shared into `hash_verifier`, `startCalculation(initialUri)` triggers automatically and reliably on entry.

#### 3. AudioExtractScreen Polish: Metadata Inspection, M4A Lossless Notice & Tool Chaining (`3C` / line 528, 533)
- **Problem:**
  - `AudioExtractScreen` only displayed file name and raw file size without audio duration or resolution.
  - For MP4/AAC sources, users were not informed that M4A extraction performs lossless stream copy without re-encoding.
  - After extracting audio, users had no quick way to compute cryptographic checksums on the output.
- **Solution:**
  - Added duration and resolution inspection: invokes `VideoEngine.getVideoMetadata(context, uri)` on file pick. If null (for pure audio files like MP3/WAV/AAC where video dimensions are 0), falls back to a background duration query via `MediaMetadataRetriever` on `Dispatchers.IO`. Formats into `fileDetails` of `FileSelectionCard`.
  - Updated format description text: explicitly explains that M4A extraction copies the AAC stream losslessly without re-encoding when possible.
  - Added `chainActions` in `ResultCard`: offers "Calculate Hash" (`hash_verifier`) using FileProvider content URIs.
  - Wired `onNavigateToTool = navigateToToolWithFile` in `MainActivity.kt`.

#### 4. DocumentScanScreen Polish: Photo Preview, Share-In & Tool Chaining (`3B`)
- **Problem:**
  - `DocumentScanScreen` did not accept shared images (`initialUri`), lacked image preview, and had no tool chaining.
- **Solution:**
  - Added `initialUri: Uri? = null` parameter, `lastHandledInitialUri`, and incoming shared photo banner with Switch / Dismiss options.
  - Added bounded Coil `AsyncImage` preview (`size(512, 512)`, height 160dp, `ContentScale.Fit`) inside the scrollable column.
  - Added `chainActions` to `ResultCard`: offers "Shrink Photo" (`image_shrink`) via `navigateToToolWithFile`.
  - Wired in `MainActivity.kt`: passes `initialUri` and `navigateToToolWithFile` to `composable("document_scan")`.

#### 5. VideoToGifScreen Polish: Output Frame & Size Estimate & Tool Chaining (`3C` / line 523)
- **Problem:**
  - Users had no indication of how many frames would be generated or how large the resulting animated GIF would be before generating.
  - After generating a GIF, users had no quick way to verify checksums.
- **Solution:**
  - Added real-time output estimate below the duration slider: calculates `val totalFrames = (effectiveDur * fps).toInt()` and estimates file size based on width and frame count (`~${FileUtils.formatFileSize(estBytes)}`).
  - Added `chainActions` in `ResultCard`: offers "Calculate Hash" (`hash_verifier`).
  - Wired `onNavigateToTool = navigateToToolWithFile` in `MainActivity.kt`.

---

## 3. Comprehensive File Audit

| File Path | Changes Made |
|---|---|
| `app/build.gradle.kts` | Added `base { archivesName.set("DigitalPocketKnife") }`; bumped `versionCode = 2`, `versionName = "1.0.1"`. |
| `settings.gradle.kts` | Updated `rootProject.name = "DigitalPocketKnife"`. |
| `app/proguard-rules.pro` | Updated comment reference to Digital Pocket Knife. |
| `app/src/main/AndroidManifest.xml` | Added `android:launchMode="singleTop"` to `MainActivity`; registered `ACTION_VIEW` for `application/pdf`, `ACTION_SEND` for `text/plain`, and `ACTION_SEND` for `*/*`; registered 4 `<activity-alias>` tags for `ACTION_PROCESS_TEXT` targeting `MainActivity`; removed static `android.app.shortcuts` meta-data to enable dynamic launcher shortcuts. |
| `app/src/main/java/com/omniforge/app/MainActivity.kt` | Added `themeRecomposeTrigger` link to `OmniForgeTheme`; added `navEvents` Channel and `onNewIntent` route navigation; added `SharedPayload` Compose state, `handleIncomingIntent`, and text/file share routing to all screens; added `ACTION_PROCESS_TEXT` alias matching and routing; passed `initialText` to `qr_studio`, `json_tool`, `base64_studio`, `hash_verifier`; added `navigateToToolWithFile` callback; guarded `ACTION_VIEW` from `dpk://` deep links; wired `onNavigateToTool` to `video_to_gif`, `audio_extract`, `document_scan`; passed `initialUri` to `document_scan`. |
| `app/src/main/java/com/omniforge/app/OmniForgeApp.kt` | Launched `FileUtils.cleanStaleCache` on `Dispatchers.IO` with `appStartTime`; initialized dynamic launcher shortcuts via `AppSettings.updateLauncherShortcuts` on app start. |
| `app/src/main/java/com/omniforge/app/core/jobs/ToolJobs.kt` | Updated `launch` to pass `appContext: Context` into the coroutine block. |
| `app/src/main/java/com/omniforge/app/core/jobs/ToolJobService.kt` | Implemented `onTimeout` for Android 15 FGS; switched to `ic_stat_notify`; updated `openToolIntent` flags to `singleTop`. |
| `app/src/main/java/com/omniforge/app/core/model/ToolModels.kt` | Added `keywords: List<String>` to `ToolItem`; centralized `ALL_TOOLS` list declaration. |
| `app/src/main/java/com/omniforge/app/core/settings/AppSettings.kt` | Added `hasAskedNotificationPermission` SharedPreferences persistence; added `updateLauncherShortcuts(context)` syncing up to 4 dynamic launcher shortcuts via `ShortcutManagerCompat`. |
| `app/src/main/java/com/omniforge/app/core/theme/Theme.kt` | Added `recomposeTrigger: Int = 0` to trigger theme recomposition. |
| `app/src/main/java/com/omniforge/app/core/ui/CommonComponents.kt` | Added `allowOverwrite` and `disallowedReason` to `SaveModeSelector`; persistent notification permission check; `withContext(NonCancellable)` in `ResultCard`; "Replace Original" label; 2-tier ergonomic action layout with full-width Save, side-by-side Open (`AutoMirrored.OpenInNew`) and Share buttons, and Coil `AsyncImage` output thumbnail preview; added `ToolChainAction` and `chainActions` horizontal AssistChips row in `ResultCard`. |
| `app/src/main/java/com/omniforge/app/core/ui/PdfPasswordGate.kt` | Added `pageCount` to `onReady` callback. |
| `app/src/main/java/com/omniforge/app/core/util/FileUtils.kt` | Added `DOWNLOAD_SUBFOLDER`; `isImageFormatMatching`; `isVideoMp4`; `cleanStaleCacheDir`; `cleanStaleCache`; added `openFile(context, file, mimeType)` with FileProvider, `setDataAndType`, `ClipData`, and `FLAG_GRANT_READ_URI_PERMISSION`; added `getContentUri(context, file)`. |
| `app/src/main/java/com/omniforge/app/engine/common/UtilityEngine.kt` | Single-pass `calculateChecksums` (MD5/SHA-1/SHA-256/SHA-512); bulk `setPixels` for QR; URL-safe Base64; JSON line/col reporting; Wi-Fi/UPI/vCard QR formatters; Base64 data URL stripping; magic-byte binary type detector; added `calculateTextChecksums(text, algorithms)` for in-memory string hashing. |
| `app/src/main/java/com/omniforge/app/engine/image/BitmapDecoder.kt` | Added `isAnimated` and `isAnimatedWebpHeader` zero-allocation animation detection. |
| `app/src/main/java/com/omniforge/app/engine/image/ImageEngine.kt` | White canvas compositing for JPEG; `targetFormat` in `shrinkImage`; lossless EXIF stripping preserving orientation; lossless WebP (`WEBP_LOSSLESS` on API 30+ when quality >= 100); added `ExifInfo.hasAnyMetadata()`. |
| `app/src/main/java/com/omniforge/app/engine/pdf/PdfEngine.kt` | Added `calculateA4Fit`; `computeEveryNChunks`; filtered out-of-bounds ranges in `multiSplitPdf`; explicit `PdfPasswordException` handling and temp file cleanup in `removePassword`. |
| `app/src/main/java/com/omniforge/app/engine/video/VideoEngine.kt` | Handled `ENCODING_PCM_FLOAT` in `decodeToWav` by downsampling to 16-bit PCM; agnostic audio track missing message; configured `Composition` with OpenGL HDR-to-SDR tone-mapping for video re-encoding; added `VideoMetadata` and `getVideoMetadata(context, uri)` with rotation dimension swapping and file descriptor fallback. |
| `app/src/main/java/com/omniforge/app/feature/home/HomeScreen.kt` | Standardized screen titles; comprehensive search keywords for all 20 tools; whitespace tokenized multi-word search; contextual share banner for incoming files with direct quick-action `AssistChip`s; dynamic `quickActions` pinned bar derived from `appSettings.enabledShortcuts`. |
| `app/src/main/java/com/omniforge/app/feature/image/ExifStripperScreen.kt` | Injected `appContext` into `ToolJobs.launch`; `AppSettings.defaultSaveMode`; share-in protection banner; appContext Toast/Save; Coil `AsyncImage` photo preview; reassurance card with `VerifiedUser` when clean. |
| `app/src/main/java/com/omniforge/app/feature/image/ImageConvertScreen.kt` | Injected `appContext` into `ToolJobs.launch`; `AppSettings.defaultSaveMode`; overwrite safety; share-in protection banner; animated image warning; appContext Toast/Save; `(Lossless WEBP)` badge when 100% selected; wired `chainActions` to "Shrink Photo" via `navigateToToolWithFile`. |
| `app/src/main/java/com/omniforge/app/feature/image/ImageShrinkScreen.kt` | Injected `appContext` into `ToolJobs.launch`; `AppSettings.defaultSaveMode`; pass `outputFormat`; overwrite safety; share-in protection banner; animated image warning; appContext Toast/Save; expanded slider to 100% with `(Lossless WEBP)` badge; continuous `maxDimension` slider (`steps = 0`) & Quick Presets row. |
| `app/src/main/java/com/omniforge/app/feature/image/DocumentScanScreen.kt` | Injected `appContext` into `ToolJobs.launch`; appContext Toast/Save; `initialUri` and incoming share banner support; bounded Coil `AsyncImage` preview; wired `chainActions` to "Shrink Photo" via `navigateToToolWithFile`. |
| `app/src/main/java/com/omniforge/app/feature/image/PaletteExtractorScreen.kt` | Retained state across rotation; `initialUri` and incoming share banner; `AsyncImage` preview; "Copy All" button; hex + rgb display. |
| `app/src/main/java/com/omniforge/app/feature/pdf/ImagesToPdfScreen.kt` | Injected `appContext` into `ToolJobs.launch`; timestamped `Photos_yyyy-MM-dd_HHmmss.pdf` output naming; appContext Toast/Save; wired `chainActions` to "Compress PDF" via `navigateToToolWithFile`. |
| `app/src/main/java/com/omniforge/app/feature/pdf/PdfShrinkScreen.kt` | Injected `appContext` into `ToolJobs.launch`; `AppSettings.defaultSaveMode`; overwrite protection for $\ge$ size; share-in protection banner; password retention honesty messages; appContext Toast/Save. |
| `app/src/main/java/com/omniforge/app/feature/pdf/PdfSplitScreen.kt` | Injected `appContext` into `ToolJobs.launch`; strict range validation UI; dynamic chunk count; `NonCancellable` Save All with honest progress count; password removal honesty message; initialUri support & share protection banner. |
| `app/src/main/java/com/omniforge/app/feature/pdf/PdfToImagesScreen.kt` | Injected `appContext` into `ToolJobs.launch`; displays `pageCount`; `NonCancellable` Save All with honest progress count; initialUri support & share protection banner. |
| `app/src/main/java/com/omniforge/app/feature/pdf/PdfUnlockScreen.kt` | Injected `appContext` into `ToolJobs.launch`; background PDF access check; unencrypted PDF detection & disable button; copy cleanup ("DRM-free" removed); initialUri support & share protection banner; appContext Toast/Save. |
| `app/src/main/java/com/omniforge/app/feature/settings/CustomizationScreen.kt` | Renamed section to "Quick Actions & Launcher Shortcuts" with clear copy explaining dual effect; calls `appSettings.updateLauncherShortcuts(context)` and `onSettingsChanged()` on toggle. |
| `app/src/main/java/com/omniforge/app/feature/utilities/Base64StudioScreen.kt` | Retained state; URL-Safe toggle FilterChip; descriptive decode error reporting; Clear & Paste actions; binary file magic-byte detection card & Downloads export; `initialText` auto-encoding/decoding support. |
| `app/src/main/java/com/omniforge/app/feature/utilities/HashVerifierScreen.kt` | Retained state; `ToolJobs.launch` background hashing with progress; SHA-256/SHA-512/SHA-1/MD5 cards with copy buttons; 4-algorithm case-insensitive hash comparison; Android 12+ compliant clipboard paste; initialUri auto-hash support & share protection banner; `initialText` routing, `HashInputMode` toggle ("Verify File" / "Hash Text"), direct text hash computation, and byte/char counter; initialized `lastHandledInitialUri` to null to fix tool chaining automatic hash computation. |
| `app/src/main/java/com/omniforge/app/feature/utilities/JsonToolScreen.kt` | Retained state; Clear and Paste actions; line/column error display; template branding update; initialText support. |
| `app/src/main/java/com/omniforge/app/feature/utilities/QrStudioScreen.kt` | Retained state; 150ms keystroke debounce; `Dispatchers.Default` background QR generation; `NonCancellable` save/share with `appContext`; Wi-Fi, UPI Pay, and Contact vCard templates; initialText support. |
| `app/src/main/java/com/omniforge/app/feature/video/AudioExtractScreen.kt` | Injected `appContext` into `ToolJobs.launch`; audio + video input picker; initialUri and pendingShareUri support; appContext Toast/Save; duration & resolution inspection in `fileDetails` with pure-audio duration fallback via `MediaMetadataRetriever`; M4A lossless stream copy notice; wired `chainActions` to "Calculate Hash". |
| `app/src/main/java/com/omniforge/app/feature/video/VideoCaptionScreen.kt` | Injected `appContext` into `ToolJobs.launch`; font size label fix; appContext Toast/Save. |
| `app/src/main/java/com/omniforge/app/feature/video/VideoConvertScreen.kt` | Injected `appContext` into `ToolJobs.launch`; removed invalid SaveModeSelector; share-in protection banner; appContext Toast/Save; video metadata extraction (resolution, duration, size in `fileDetails`). |
| `app/src/main/java/com/omniforge/app/feature/video/VideoShrinkScreen.kt` | Injected `appContext` into `ToolJobs.launch`; `AppSettings.defaultSaveMode`; overwrite protection for $\ge$ size and non-MP4; share-in protection banner; appContext Toast/Save; video metadata extraction (resolution, duration, size in `fileDetails`). |
| `app/src/main/java/com/omniforge/app/feature/video/VideoToGifScreen.kt` | Injected `appContext` into `ToolJobs.launch`; initialUri and pendingShareUri support; dynamic duration query; Start Offset slider; clamped duration; appContext Toast/Save; total frame count and rough file size estimate; wired `chainActions` to "Calculate Hash". |
| `app/src/main/java/com/omniforge/app/feature/video/VideoTrimMuteScreen.kt` | Injected `appContext` into `ToolJobs.launch`; initialUri and pendingShareUri support; dynamic duration query on `Dispatchers.IO`; appContext Toast/Save; wired `chainActions` to "Make GIF" via `navigateToToolWithFile`. |
| `app/src/main/res/drawable/ic_stat_notify.xml` | Created 24dp monochrome notification icon. |
| `app/src/test/java/com/omniforge/app/core/settings/LauncherShortcutsTest.kt` | Added 3 unit tests for default shortcut presence, dynamic shortcut capping at 4, and empty fallback handling. |
| `app/src/test/java/com/omniforge/app/engine/common/UtilityEngineTest.kt` | Unit tests for MessageDigest verification, string casing, JSON line/col errors, data URL stripping, binary magic-byte detection, Wi-Fi/UPI/vCard formatting, and `calculateTextChecksums`. |
| `app/src/test/java/com/omniforge/app/engine/image/BitmapDecoderTest.kt` | Added unit tests for WebP header animation flag detection. |
| `app/src/test/java/com/omniforge/app/engine/image/ExifInfoTest.kt` | Added unit tests for `ExifInfo.hasAnyMetadata()`. |
| `app/src/test/java/com/omniforge/app/engine/pdf/PdfA4FitTest.kt` | Added unit tests for A4 image scaling and margins. |
| `app/src/test/java/com/omniforge/app/engine/pdf/PdfSplitTest.kt` | Added unit tests for PDF chunking and bounds. |
| `app/src/test/java/com/omniforge/app/core/util/FileUtilsTest.kt` | Added unit tests for MIME format matching and cache cleanup. |
| `app/src/test/java/com/omniforge/app/feature/home/HomeSearchTest.kt` | Added 5 unit tests for tool keywords, multi-word matching, trailing spaces, and verb aliases; updated to import centralized `ALL_TOOLS`. |
| `app/src/test/java/com/omniforge/app/engine/video/VideoMetadataTest.kt` | Added unit tests for video duration and resolution formatting. |
| `README.md` | Updated for Media3 architecture and current features. |
| `FIX_PLAN.md` | Checked off all completed items across Batches 1–11. |

---

## 4. Suggested Audit Points for Claude

When reviewing these changes, please inspect:
1. **Activity Context Leak Elimination:** Verify that no lambda passed to `ToolJobs.launch` or `onSaveToDownloads` references `LocalContext.current` (outer `context`). All 15 screens should only reference `appContext`.
2. **Channel vs. SharedFlow Navigation:** Check [`MainActivity.kt`](./app/src/main/java/com/omniforge/app/MainActivity.kt) to confirm that `navEvents: Channel<String>` correctly guards against duplicate navigations on activity recreation/rotation and handles `onNewIntent`.
3. **Float to 16-Bit PCM Downsampling:** Check [`VideoEngine.kt:decodeToWav`](./app/src/main/java/com/omniforge/app/engine/video/VideoEngine.kt) to confirm that the sample conversion `(sample.coerceIn(-1.0f, 1.0f) * 32767.0f).roundToInt().toShort()` preserves byte alignment with the 44-byte WAV header.
4. **Cache Cleanup Race Conditions:** Inspect `FileUtils.cleanStaleCacheDir` to verify that `appStartTime` properly protects freshly created input files from being pruned by the background startup cleanup.
5. **Lossless EXIF Tag Stripping:** Inspect `ImageEngine.stripExif` to confirm that `TAG_ORIENTATION` is preserved on JPEG while clearing privacy tags (`GPS`, `Make`, `Model`, `DateTime`, etc.).
6. **NonCancellable Save Scopes:** Check `CommonComponents.kt:ResultCard` and `PdfSplitScreen.kt` / `PdfToImagesScreen.kt` to ensure `NonCancellable` properly finishes writing files to disk even if the user navigates away or rotates during export.
7. **Share-In State Retain Safety:** Verify that `lastHandledInitialUri` correctly prevents screen rotations from reverting user-picked files back to initial share URIs across all screens, and that manual file picker updates do not overwrite `lastHandledInitialUri`.
8. **WebP Header Bit Parsing:** Inspect `BitmapDecoder.isAnimatedWebpHeader` to confirm RFC 6386 bit compliance (byte 20, bit 1 = 0x02 for animation).
9. **Single-Pass Multi-Digest Streaming:** Inspect `UtilityEngine.calculateChecksums` to verify that all 4 algorithms (`SHA-256`, `SHA-512`, `SHA-1`, `MD5`) read from the stream in one pass, throttle progress to $\ge 100\text{ms}$, handle `fileSize <= 0` gracefully, and check `coroutineContext.ensureActive()`.
10. **Bulk JNI setPixels Stride:** Verify that `UtilityEngine.generateQrCode` correctly maps indices as `[y * size + x]` to avoid matrix transposition when calling native `setPixels` on `ARGB_8888`.
11. **Strict Base64 UTF-8 Decoding:** Inspect `UtilityEngine.decodeBase64` to verify that binary data triggers `CharacterCodingException` via `CharsetDecoder` rather than corrupting strings with `\uFFFD`.
12. **Media3 OpenGL Tone-Mapping Scope:** Check `VideoEngine.kt:runTransformer` to confirm that `setHdrMode(HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)` is only enabled when video re-encoding is active and video is not being removed, avoiding regressions on lossless transmuxing operations.
13. **Search Tokenization Efficiency:** Inspect `HomeScreen.kt` search filtering logic to verify that `tokens.all { ... }` runs efficiently with zero frame drops, handles uppercase/lowercase queries, and tolerates leading/trailing/multiple consecutive spaces.
14. **PDF Unlock Pre-check & Cleanup:** Inspect `PdfUnlockScreen.kt` and `PdfEngine.kt:removePassword` to verify that unencrypted PDFs prevent redundant unlocking operations, and that temp files in cache are reliably cleaned up if password removal throws.
15. **JSON Line/Column Offset Math:** Inspect `UtilityEngine.formatJson` to confirm that converting `org.json.JSONException` character offset into 1-based line and column handles newlines (`\n`, `\r\n`) accurately.
16. **SharedPayload Reactive Observation:** In `MainActivity.kt`, inspect `currentSharedPayload` Compose state and verify that `onNewIntent` updates trigger immediate recomposition and route navigation without activity restart.
17. **Wi-Fi QR Escaping Order:** Inspect `UtilityEngine.escapeWifiField` to ensure that backslash `\` is replaced before `;`, `,`, `:`, and `"` to prevent double-escaping.
18. **UPI QR Spec Compliance:** Verify `UtilityEngine.formatUpiQr` includes mandatory `pa`, `pn`, and `cu=INR` parameters with UTF-8 URL encoding conforming to NPCI UPI specifications.
19. **Magic-Byte False Positive Prevention:** In `UtilityEngine.detectBinaryType`, inspect the length guard (`bytes.size < 12`) and dual-offset WebP verification (`RIFF` at 0..3 and `WEBP` at 8..11) to ensure WAV/AVI files are not misidentified as WebP.
20. **Base64 Decoded File Export:** Check `Base64StudioScreen.kt` binary export to verify `withContext(Dispatchers.IO + NonCancellable)` ensures safe writing to `Downloads/DigitalPocketKnife` with unique timestamped filenames.
21. **Intent setDataAndType in FileUtils.openFile:** Verify that `intent.setDataAndType(contentUri, resolvedMime)` is called as a single atomic invocation (rather than `intent.data = ...` followed by `intent.type = ...`, which Android clears).
22. **FileProvider Chooser ClipData:** In `FileUtils.openFile`, verify that `clipData = ClipData.newRawUri("Output File", contentUri)` and `FLAG_GRANT_READ_URI_PERMISSION` ensure read access propagates correctly when wrapped in `Intent.createChooser()`.
23. **Coil ImageRequest Bounding:** In `ResultCard`, `ExifStripperScreen`, `PaletteExtractorScreen`, and `DocumentScanScreen`, inspect `ImageRequest.Builder.size(coil.size.Size(512, 512))` to ensure full-resolution multi-megapixel photos are never decoded into memory at 100% resolution inside scrollable columns.
24. **WebP Lossless API Level Branching:** In `ImageEngine.kt`, verify that `Bitmap.CompressFormat.WEBP_LOSSLESS` is only accessed on `Build.VERSION.SDK_INT >= Build.VERSION_CODES.R` (API 30+), avoiding `ClassNotFoundException` / NoSuchFieldError on API 29.
25. **MediaMetadataRetriever Resource Cleanup:** Check `VideoTrimMuteScreen.kt`, `VideoToGifScreen.kt`, and `AudioExtractScreen.kt` to ensure `retriever.release()` runs inside a `finally` block and catches any platform release exceptions.
26. **`ACTION_PROCESS_TEXT` Canonical Activity-Alias Class Names:** In `MainActivity.kt:handleIncomingIntent`, verify that `intent.component?.className` matches using `.endsWith(...)` rather than `.equals()` or manifest-relative strings, since Android runtime always returns canonical fully-qualified names.
27. **Continuous Slider Snapping Prevention:** In `ImageShrinkScreen.kt`, verify that `maxDimension` slider uses `steps = 0` so arbitrary preset resolutions (1000px, 1600px, 1920px, 3840px) set the slider smoothly without jumping to quantized step positions.
28. **Video Rotation Swapping Logic:** In `VideoEngine.kt:getVideoMetadata`, verify that portrait smartphone videos with rotation 90° or 270° correctly swap width and height to return vertical dimensions.
29. **Tool Chaining FileProvider Content URIs:** In `FileUtils.kt:getContentUri`, verify that output files are converted to `content://` URIs via FileProvider so recipient screens can query content resolvers and open streams without `file://` exposure exceptions.
30. **Text Checksum Test Vectors:** In `UtilityEngineTest.kt:testCalculateTextChecksums`, verify test vectors for standard strings and empty inputs across all 4 cryptographic hash algorithms.
31. **Launcher Dynamic Shortcuts Max Limit:** Verify that `AppSettings.updateLauncherShortcuts` limits shortcuts to `.take(4)` to adhere to Android's hard per-app launcher shortcut quota, avoiding `IllegalArgumentException: Max shortcuts limit exceeded`.
32. **Deterministic Shortcut Ordering:** Inspect `ALL_TOOLS.filter { it.id in targetIds }` to confirm that dynamic shortcuts and Home quick actions maintain a deterministic display order regardless of the non-deterministic order of SharedPreferences `Set<String>`.
33. **Deep Link Scheme Filtering in ACTION_VIEW:** Verify in `MainActivity.kt:handleIncomingIntent` that `uri.scheme != "dpk"` prevents launcher shortcuts or notification deep links from overwriting `currentSharedPayload` with non-file URIs.
34. **AudioExtract Pure Audio Fallback:** In `AudioExtractScreen.kt`, verify that selecting a pure audio file (where `VideoEngine.getVideoMetadata` returns null due to width/height $\le 0$) safely queries duration via `MediaMetadataRetriever` on `Dispatchers.IO`.
35. **HashVerifier Chaining State Reset:** Verify that initializing `lastHandledInitialUri` to `null` allows chained tool invocations to trigger `startCalculation(initialUri)` immediately upon screen entry while retaining state across configuration changes.
37. **ExifInterface Keep Rules:** Verified `-keepclassmembers class androidx.exifinterface.media.ExifInterface { public static final java.lang.String TAG_*; }` in `proguard-rules.pro` to ensure R8 shrinking does not strip EXIF tag constants.
38. **EXIF GPS Verification:** In `ImageEngine.kt:stripExif`, added post-strip verification using `ExifInterface.latLong` with fallback to clean re-encoding if metadata remains.
39. **Shared Payload Consumption:** In `MainActivity.kt` and `HomeScreen.kt`, verified that `currentSharedPayload` is cleared via `ConsumeSharedPayloadEffect` when entering destination tools and can be dismissed explicitly via the close button on the home screen banner.
40. **QR UTF-8 Encoding:** In `UtilityEngine.kt:generateQrCode`, verified `EncodeHintType.CHARACTER_SET to "UTF-8"` correctly preserves non-Latin-1 scripts, Hindi characters, and the ₹ currency symbol.
41. **FileUtils URI Safety & Error Handling:** In `FileUtils.kt`, verified `runCatching` on `getFileName`/`getFileSize` and timestamped cache filenames `input_${System.currentTimeMillis()}$ext` with cleanup on failure.
42. **PDF Partial Output Cleanup:** In `PdfEngine.kt`, verified `try-catch` deletion of `outputFile` on failure and `try-finally` cleanup of `page.close()` and `bitmap.recycle()` in `pdfToImages`.
43. **Apparat Rebranding & Theme Contrast:** Renamed application to Apparat in `strings.xml`, `build.gradle.kts`, and `settings.gradle.kts`. In `apparat-braun` worktree, restored `dpk_settings` prefs name and updated `onPrimary` to `OnBraunOrange` (`#161618`) on orange/amber accents for WCAG AA compliance.

---

## 3. Post-Audit Fixes & Apparat Rebranding
**Commit Range:** `43e4c53` -> `HEAD`

1. **Rebranding:** App name updated to **Apparat** (`strings.xml`, `build.gradle.kts`, `settings.gradle.kts`).
2. **Issue B2:** Shared-file payload auto-clears on destination tool consumption (`ConsumeSharedPayloadEffect`) and includes an explicit dismiss (X) button on `HomeScreen`. Added `sharedUri` to `remember` keys in `filteredTools`.
3. **Issue B3:** UTF-8 character encoding hint passed to ZXing in `UtilityEngine.generateQrCode`, fixing Hindi/Devanagari, ₹, and emoji corruption.
4. **Issue B4:** Fixed Play Store release workflow environment variable scoping and made release builds require a valid keystore rather than silently falling back to the debug key.
5. **Issue B7:** Added ProGuard keep rule for `ExifInterface.TAG_*` and added post-strip GPS verification check with fallback re-encoding.
6. **Issue B9:** Wrapped `getFileName` and `getFileSize` in `runCatching` to prevent `SecurityException` crashes from invalid shared URIs.
7. **Issue B10:** Replaced unbounded cache filenames with `input_<timestamp><ext>` in `FileUtils.copyUriToCache` to prevent `ENAMETOOLONG`, and added failure deletion.
8. **Issue B15:** Added deletion of partial outputs in `PdfEngine` (`compressPdf`, `imagesToPdf`, `mergePdfs`, `pdfToImages`) and wrapped page rendering in `try-finally`.
9. **Issue B16:** Rejected `file://` scheme and internal FileProvider authorities in `MainActivity.handleIncomingIntent` to prevent local file disclosure.
10. **Themes Issue 1 & 4 (in `apparat-braun`):** Restored `dpk_settings` SharedPreferences name to prevent user preference loss on upgrade. Updated `onPrimary` to use `OnBraunOrange` (`#161618`) for Braun Orange and Amber accents, ensuring WCAG AA contrast compliance.
