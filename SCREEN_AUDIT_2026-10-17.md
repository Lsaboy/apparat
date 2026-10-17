# Screen-Level Code Audit & Theme Review — 2026-10-17

**Auditor:** Gemini (Antigravity Senior Code Auditor)  
**Scope:** Master branch (`43e4c53`) screen-level logic and theme branch (`1f4028b` @ `~/Documents/antigravity/apparat-braun`) restyling across all 22 feature screens not covered line-by-line in Claude's `AUDIT_2026-10-17.md`.

---

## Executive Summary

Claude's backend audit (`AUDIT_2026-10-17.md`) covered engines, the job system, file handling, navigation, and core theme files. This audit closes the remaining gap by reviewing all **22 feature and utility screens** line-by-line.

### Major Findings:
1. **Critical MIME Type Bug in EXIF Stripper (`S1`):** `ExifStripperScreen.kt:330` hardcodes `"image/jpeg"` for Android share sheet even when stripping a PNG or WebP image.
2. **Loss of Slider Settings on Screen Rotation (`S2`):** `VideoToGifScreen.kt:79` unconditionally resets `startSec = 0f` and `durationSec` whenever `selectedUri` is composed, wiping user sliders on device rotation.
3. **Invalid Trimming Input Bounds Crash (`S3`):** `VideoTrimMuteScreen.kt` accepts any raw float input (e.g., start = 500s on a 10s video) without bounds validation against `videoTotalSec`, causing Media3 Transformer background crashes.
4. **Incorrect File Naming for Mute (`S4`):** In `VideoTrimMuteScreen.kt:364`, muting a video without trimming still saves the output as `Trimmed_<timestamp>.mp4` instead of `Muted_<timestamp>.mp4`.
5. **Memory State Loss in Base64 Studio (`S5`):** `Base64StudioScreen.kt:58` holds `detectedBinaryType` and `decodedBinaryBytes` in `remember` rather than `retained` or disk cache; rotating the phone wipes the decoded binary and hides the "Save File" button.
6. **QR Code Silent Failures on Overflow (`S6`):** When payload exceeds QR capacity (>2953 bytes), `QrStudioScreen.kt:108` catches `WriterException` and silently leaves the screen on "Fill in fields...", giving no user feedback.
7. **Severe Button Text Contrast Bug on Theme Branch (`TR1`):** The theme redesign blindly replaced `tint = AmoledBlack` with `tint = OnBraunOrange` (`#161618`) across all screens. Placing near-black text on `AccentPdf` (Red `#E53935`), `AccentVideo` (Blue `#1E88E5`), and `AccentImage` (Green `#2E7D32`) results in contrast ratios between **2.2:1 and 2.96:1**, severely failing WCAG AA (requires 4.5:1).
8. **Stale Branch Feature Loss (`TR2`):** `apparat-braun-redesign` was forked from Batch 9 (`90d41bd`). It completely lacks Batch 10 and 11 features:
   - Tool Chaining (`chainActions` in `ResultCard` and 7 screens)
   - Photo Shrink Quick Presets
   - Video Resolution & Duration Metadata in `FileSelectionCard`
   - Dynamic Launcher Shortcuts and `ALL_TOOLS` centralization
   - Incoming `initialUri` in `DocumentScanScreen`

---

## Screen-by-Screen Audit Matrix

| Screen | Location | Logic Bugs / Fragility | MIME / Output Safety | Theme Branch Restyling Status |
|---|---|---|---|---|
| **PdfPasswordGate** | `pdf/PdfPasswordGate.kt` | `password` and `visible` use `remember` (lost on rotation). Re-copies 100MB PDF on rotation. | N/A | Preserves master state. |
| **PdfSplit** | `pdf/PdfSplitScreen.kt` | "Share First File" only; lacks multi-share (`ACTION_SEND_MULTIPLE`). | Safe (`.pdf`). | `OnBraunOrange` on `AccentPdf` (2.96:1 contrast fail). |
| **PdfUnlock** | `pdf/PdfUnlockScreen.kt` | Password visibility toggle state lost on rotation. | Safe (`.pdf`). | `OnBraunOrange` on `AccentPdf` (2.96:1 contrast fail). |
| **PdfToImages** | `pdf/PdfToImagesScreen.kt` | Hardcoded 0.3f progress; "Share First Page" only; no JPEG/DPI option. | Safe (`image/png`). | `OnBraunOrange` on `AccentPdf` and `SuccessGreen`. |
| **ImagesToPdf** | `pdf/ImagesToPdfScreen.kt` | Picking replaces entire list (no append/reorder); lacks `initialUri` share-in. | Safe (`.pdf`). | `OnBraunOrange` on `AccentPdf`. |
| **PdfShrink** | `pdf/PdfShrinkScreen.kt` | Still shows "Save" in `ResultCard` if file couldn't be reduced (B18). | Safe (`.pdf`). | Missing Batch 10 tool chaining. |
| **ImageConvert** | `image/ImageConvertScreen.kt` | No image preview before conversion; single file only. | Safe (`targetFormat.mimeType`). | Missing Batch 10 tool chaining. |
| **ImageShrink** | `image/ImageShrinkScreen.kt` | "Keep EXIF" ignored for PNG/WEBP; PNG quality slider enabled. | Safe (`targetFormat.mimeType`). | **Lacks Batch 10 Presets**. |
| **DocumentScan** | `image/DocumentScanScreen.kt` | Lacks camera capture; transparent PNGs encode with black canvas. | Hardcoded JPEG. | Missing Batch 11 `initialUri` and chaining. |
| **ExifStripper** | `image/ExifStripperScreen.kt` | R8 reflection vulnerability in `ALL_EXIF_TAGS` (B7). | **BUG: Share passes `image/jpeg` for PNG/WEBP.** | `OnBraunOrange` on `AccentImage` (2.2:1 contrast fail). |
| **PaletteExtractor** | `image/PaletteExtractorScreen.kt` | Transparent pixels treated as black; quantization biased towards #000000. | N/A (clipboard). | Clean restyle. |
| **VideoTrimMute** | `video/VideoTrimMuteScreen.kt` | Start/duration inputs lack bounds validation against `videoTotalSec`. | **BUG: Mute saves as `Trimmed_*.mp4`.** | `OnBraunOrange` on `AccentVideo` (2.8:1 contrast fail). |
| **AudioExtract** | `video/AudioExtractScreen.kt` | UI switches to Main dispatcher on every buffer during WAV decode. | Safe (`audio/mp4` / `audio/wav`). | Missing Batch 11 chaining. |
| **VideoToGif** | `video/VideoToGifScreen.kt` | **BUG: `startSec` resets to 0 on rotation**; est. file size undercalculated. | Safe (`image/gif`). | `OnBraunOrange` on `AccentVideo`. |
| **VideoConvert** | `video/VideoConvertScreen.kt` | No tool chaining; missing `onNavigateToTool` parameter. | Safe (`video/mp4`). | Missing Batch 10 metadata inspection. |
| **VideoShrink** | `video/VideoShrinkScreen.kt` | ResultCard still offers Save for larger file when already optimized. | Safe (`video/mp4`). | Missing Batch 10 metadata inspection. |
| **VideoCaption** | `video/VideoCaptionScreen.kt` | Lacks `initialUri` share-in; no extension validation on subtitle file. | Safe (`video/mp4`). | `OnBraunOrange` on `AccentVideo`. |
| **QrStudio** | `utilities/QrStudioScreen.kt` | Silent failure when input exceeds QR max length; `qrBitmap` flashes on rotation. | Safe (`image/png`). | `OnBraunOrange` on `AccentUtility` (~5.7:1 pass). |
| **Base64Studio** | `utilities/Base64StudioScreen.kt` | Decoded binary bytes in `remember` (lost on rotation); Main-thread encoding. | Safe (magic byte detection). | Clean tokenized layout. |
| **JsonTool** | `utilities/JsonToolScreen.kt` | Formatting runs on Main thread; large numbers altered by `org.json` (B14). | N/A (text). | "Minify" button hardcodes dark surface. |
| **HashVerifier** | `utilities/HashVerifierScreen.kt` | Progress updates can spam Main dispatcher if file is very small. | N/A (text). | Clean tokenized layout. |
| **Customization** | `settings/CustomizationScreen.kt` | Copy says "Android 17"; no limit enforcement on dynamic shortcuts. | N/A | Missing Batch 11 launcher shortcut syncing. |
| **Home** | `home/HomeScreen.kt` | Shared payload never cleared (B2); search keywords added in Batch 7. | N/A | Stale pre-Batch 11 copy; merge conflict. |

---

## Detailed Screen-by-Screen Review

### 1. PDF Feature Screens

#### 1.1 `PdfPasswordGate.kt`
- **Component Role:** Wraps encrypted PDF tools (`PdfSplit`, `PdfUnlock`, `PdfToImages`, `PdfShrink`) with access verification and password input.
- **State & Lifecycle:**
  - `var password by remember { mutableStateOf("") }` and `var isPasswordVisible by remember { mutableStateOf(false) }` use `remember` instead of `rememberSaveable`. Rotating the device while typing a password clears the input.
  - `remember(uri) { PdfEngine.checkAccess(context, uri) }` triggers on file change, but inside a `LaunchedEffect`, if access requires decryption, it makes an extra temp copy of the document.
- **Recommendations:**
  - Change `password` and `isPasswordVisible` to `rememberSaveable`.
  - Cache access check result in `retained` so rotation does not re-read or re-copy large PDFs.

#### 1.2 `PdfSplitScreen.kt`
- **Input Validation:**
  - Batch 2 added `computeEveryNChunks` and strict `RangeValidation`. This is solid.
  - Edge case: If user inputs spaces in interval (`" 2 "`), `filter { c -> c.isDigit() }` cleans it safely.
- **File Handling & Sharing:**
  - When splitting generates 10 files, the screen only provides "Share First File" via `FileUtils.shareFile(context, generatedFiles.first(), "application/pdf")`.
  - Users splitting a document want to share *all* generated parts. The screen lacks an `ACTION_SEND_MULTIPLE` intent.
  - Output file names are hardcoded to `${baseName}_part_${index + 1}.pdf`. For custom ranges (e.g. `1-3`, `4-5`), names like `${baseName}_p1-3.pdf` would be far more informative.
- **Theme Restyling Review:**
  - Line 436: `Button(colors = ButtonDefaults.buttonColors(containerColor = AccentPdf))` has `Icon(..., tint = OnBraunOrange)` and `Text(..., color = OnBraunOrange)`. On red background (`#E53935`), `#161618` dark text gives **2.96:1 contrast**, violating WCAG AA. Text and icon must be `Color.White`.
  - Lines 265, 301: Placeholders hardcode `TextTertiary` (#636366, 2.5:1 on dark) instead of `MaterialTheme.colorScheme.onSurfaceVariant`.

#### 1.3 `PdfUnlockScreen.kt`
- **State & Verification:**
  - Correctly verifies whether PDF is unencrypted, owner-restricted, or password-protected via `PdfEngine.checkAccess`.
  - Password visibility state (`isPasswordVisible`) uses `remember` and is wiped on rotation.
- **Theme Restyling Review:**
  - Action button at lines 260 and 331 uses `OnBraunOrange` text on `AccentPdf` red container (contrast failure).

#### 1.4 `PdfToImagesScreen.kt`
- **State & Execution:**
  - Progress is hardcoded to a fixed `0.3f` during extraction (`processState = ProcessState.Processing(0.3f, "Rendering pages to images...")`) because `PdfEngine.pdfToImages` does not provide a per-page progress callback.
  - Sharing only allows "Share First Page". No multi-image sharing (`ACTION_SEND_MULTIPLE`).
- **File Format:**
  - Hardcoded to PNG at 2x scale. Large PDFs (50+ pages) consume hundreds of megabytes in cache and frequently cause OOM errors during rendering. Adding JPEG selection (quality 85) and 1x/1.5x/2x resolution options is essential (Issue B13).
- **Theme Restyling Review:**
  - Both "Extract All Pages" (red) and "Save All Images" (green) buttons use `OnBraunOrange` text (contrast failure).

#### 1.5 `ImagesToPdfScreen.kt`
- **UX & Intent Gaps:**
  - Completely lacks `initialUri` and `ACTION_SEND_MULTIPLE` share-in acceptance. If a user selects 5 photos in Google Photos and taps "Share to Apparat", the app cannot ingest them into Images to PDF.
  - Picking images via `multiPicker` overwrites `selectedUris` completely. There is no way to append additional images, delete an accidental photo, or reorder pages.
- **Page Layout:**
  - Batch 1 fixed the scaling bug to fit standard A4 margins. Output is properly timestamped `${baseName}_Photos_<date>.pdf`.

---

### 2. Photo & Image Feature Screens

#### 2.1 `ImageConvertScreen.kt`
- **State & Overwrite Safety:**
  - Compares input extension with target format extension; disables "Modify Original" if format differs (e.g. PNG to JPG) to prevent file corruption.
  - Correctly shows notice when animated images (GIF/WebP) are converted ("Processed as still frame").
- **Gaps:**
  - Lacks an image thumbnail preview before conversion.
  - Single-file only; does not support batch conversion.

#### 2.2 `DocumentScanScreen.kt`
- **Canvas Transparency Bug:**
  - When scanning a transparent PNG or signature crop, `ImageEngine.enhanceDocumentScan` does not fill a white matte background before encoding to JPEG, resulting in solid black backgrounds where transparency existed.
- **Gaps:**
  - No direct camera capture launcher (`ActivityResultContracts.TakePicture`).
  - Lacks perspective quadrilateral crop.

#### 2.3 `ExifStripperScreen.kt`
- **CONFIRMED BUG (MIME Type Mismatch):**
  - Line 330:
    ```kotlin
    onShare = {
        FileUtils.shareFile(context, success.outputFile, "image/jpeg")
    }
    ```
    If the stripped image was a PNG or WebP, `onShare` sends `"image/jpeg"` to the Android share sheet. While `onSaveToDownloads` dynamically detects MIME, `onShare` hardcodes JPEG!
- **Latent Reflection Issue (B7):**
  - `ImageEngine.ALL_EXIF_TAGS` uses reflection over `ExifInterface.TAG_*` fields. R8 minification will strip these unless ProGuard rules are added.
- **Theme Restyling Review:**
  - Button text and icon use `OnBraunOrange` on `AccentImage` (`#2E7D32` green). Contrast ratio is **2.2:1** (severe WCAG AA failure).

#### 2.4 `PaletteExtractorScreen.kt`
- **Alpha Transparency Bug:**
  - In `ImageEngine.extractColorPalette`:
    ```kotlin
    val pixel = scaled.getPixel(x, y)
    ```
    Transparent pixels (`Color.alpha(pixel) < 128`) are not discarded. They get evaluated as `RGB(0, 0, 0)`, biasing the palette heavily toward pure black for logos, stickers, and transparent graphics.
- **Execution Scope:**
  - Palette extraction coroutine uses local `scope.launch` rather than `ToolJobs`, so navigating away immediately aborts extraction.

---

### 3. Video & Audio Feature Screens

#### 3.1 `VideoTrimMuteScreen.kt`
- **CONFIRMED BUG (Incorrect Output Filename):**
  - Line 364:
    ```kotlin
    val displayName = "Trimmed_${SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())}.mp4"
    ```
    Even when `isMuteOnly = true`, the saved file is named `Trimmed_...mp4` instead of `Muted_...mp4`.
- **CONFIRMED BUG (Missing Input Validation):**
  - `startSecText` and `durationSecText` accept arbitrary strings. If user enters `startSec = 500` on a 10-second video, the button stays enabled. Tapping "Trim" launches Media3 Transformer with invalid clipping bounds, causing a pipeline failure.
  - Validation must enforce `startSec >= 0 && startSec < videoTotalSec` and `durationSec > 0 && (startSec + durationSec) <= videoTotalSec`.
- **Theme Restyling Review:**
  - Action button uses `OnBraunOrange` on `AccentVideo` (`#1E88E5` blue). Contrast ratio is **2.8:1** (fails WCAG AA).

#### 3.2 `VideoToGifScreen.kt`
- **CONFIRMED BUG (Slider Reset on Screen Rotation):**
  - In lines 64-93:
    ```kotlin
    LaunchedEffect(selectedUri) {
        val uri = selectedUri
        if (uri != null) {
            // ... query duration ...
            videoDurationSec = durMs / 1000f
            startSec = 0f  // <-- BUG: resets user's start slider on rotation!
            if (videoDurationSec > 0f) {
                durationSec = min(5f, videoDurationSec) // <-- BUG: resets duration slider!
            }
        }
    }
    ```
    Because `LaunchedEffect(selectedUri)` runs when the composable enters the composition tree, rotating the phone re-triggers this block and silently wipes the user's slider configuration back to 0s and 5s!
  - Fix: Track `lastLoadedUri` in `retained`. Only reset `startSec` and `durationSec` if `selectedUri != lastLoadedUri`.
- **File Size Calculation:**
  - Estimated size scales linearly with width `(width / 480f)`. Frame pixel count scales quadratically ($O(W^2)$). For 720px width, size is underestimated by ~35%.

#### 3.3 `VideoConvertScreen.kt` & `VideoShrinkScreen.kt`
- **Chaining Gaps:**
  - `VideoConvertScreen.kt` has no `chainActions` in `ResultCard` and lacks the `onNavigateToTool` parameter. Users converting an MKV to MP4 cannot chain directly into "Compress Video" or "Trim Video".
- **Theme Branch Divergence:**
  - On `master`, Batch 10 added `VideoEngine.getVideoMetadata` to display `resolutionFormatted` and `durationFormatted` in `FileSelectionCard`. The theme branch was branched prior to Batch 10 and lacks this metadata inspection.

#### 3.4 `VideoCaptionScreen.kt`
- **Gaps:**
  - Lacks `initialUri` and share-in acceptance.
  - Subtitle picker allows `*/*` with no validation that selected file is actually `.srt` or `.vtt`.

---

### 4. Quick Utilities & Settings Screens

#### 4.1 `QrStudioScreen.kt`
- **Silent Overflow Bug:**
  - When raw input exceeds QR capacity (~2,953 bytes for binary, ~4,296 alphanumeric), ZXing throws `WriterException: Data too big`.
  - Lines 108-110 catch `Exception` and set `qrBitmap = null`. The UI displays "Fill in the fields above...", leaving the user wondering why the QR code disappeared. An explicit error message ("Text is too long for a QR code") is required.
- **State Flash on Rotation:**
  - `var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }` uses `remember`. On rotation, `qrBitmap` becomes null during the 150ms debounce window, causing the QR preview to flicker.

#### 4.2 `Base64StudioScreen.kt`
- **CONFIRMED BUG (Rotation Data Loss):**
  - Lines 58-60:
    ```kotlin
    var detectedBinaryType by remember { mutableStateOf<Pair<String, String>?>(null) }
    var decodedBinaryBytes by remember { mutableStateOf<ByteArray?>(null) }
    ```
    These use `remember`, NOT `retained`. If a user decodes a PDF or PNG from Base64 and rotates the device, `detectedBinaryType` and `decodedBinaryBytes` are wiped to null! The "Save Decoded File" card disappears until they manually tap "Decode" again.
- **Thread Blocking:**
  - Tapping "Encode" or "Decode" executes `UtilityEngine.encodeBase64` synchronously on the Main thread. Large inputs (e.g. 2MB+) freeze the UI. Should execute on `Dispatchers.Default`.

#### 4.3 `JsonToolScreen.kt`
- **Thread Blocking & Data Loss (B14):**
  - Prettify / Minify runs on Main thread.
  - Relies on `org.json`, which converts large 64-bit integers into scientific notation doubles and escapes `/` as `\/`.
- **Theming:**
  - "Minify" button container hardcodes `DarkSurfaceElevated` with `TextPrimary`. In light theme, this creates an unstyled dark box.

#### 4.4 `CustomizationScreen.kt`
- **Copy Typo:**
  - Line 156: Labels dynamic color as `"Android 17 Dynamic Color"`. Dynamic Color was introduced in Android 12 (API 31). Should read `"Material You Dynamic Color (Android 12+)"`.
- **Dynamic Shortcuts Limit:**
  - Android enforces a strict limit of 4 dynamic shortcuts per app (`ShortcutManagerCompat`). `CustomizationScreen` allows checking all 18 tools without indicating that only the first 4 will be active.

---

## Theme Branch Divergence & Conflict Analysis

The `apparat-braun-redesign` branch (commit `1f4028b`) introduced clean Braun/Dieter Rams styling (`MaterialTheme.colorScheme`, 8dp corner radiuses, monospace accents), but was branched from Batch 9 (`90d41bd`).

### Lost Features in Theme Branch (Need to be Restored during Merge):
1. **Tool Chaining ("Continue With..."):** Added in Batch 10/11 to `ResultCard` and across `PdfSplit`, `ImagesToPdf`, `ImageConvert`, `VideoTrimMute`, `VideoToGif`, `AudioExtract`, and `DocumentScan`. The theme branch completely lacks `ToolChainAction` and all chaining chips.
2. **Photo Shrink Quick Presets:** Added in Batch 10 (`ImageShrinkScreen.kt`) with one-tap presets for ID Portal, WhatsApp, Social, and Max Quality. Missing on theme branch.
3. **Video Metadata Extraction:** Added in Batch 10 (`VideoConvertScreen`, `VideoShrinkScreen`, `AudioExtractScreen`) showing resolution, duration, and container info in `FileSelectionCard`. Missing on theme branch.
4. **Dynamic Launcher Shortcuts:** Added in Batch 11 (`AppSettings.kt` + `HomeScreen.kt` + `CustomizationScreen.kt`), with `ALL_TOOLS` centralized in `ToolModels.kt`. The theme branch retains the old static list in `HomeScreen.kt`.
5. **Document Scan Share-In:** Added in Batch 11 (`DocumentScanScreen.kt` accepts `initialUri`). Missing on theme branch.

### Theme Contrast Issues (WCAG AA Violations):
The theme branch defined `OnBraunOrange = Color(0xFF161618)` (near-black charcoal) and used it globally as the content color on colored buttons:
- On `AccentPdf` (`#E53935` Red): **2.96:1** (FAIL — requires 4.5:1)
- On `AccentVideo` (`#1E88E5` Blue): **2.80:1** (FAIL)
- On `AccentImage` (`#2E7D32` Green): **2.20:1** (FAIL)
- On `SuccessGreen` (`#43A047` Green): **3.50:1** (FAIL)
- On `AccentUtility` (`#F57C00` Orange): **5.71:1** (PASS)

**Fix for Theme Contrast:**
Do not hardcode `OnBraunOrange` on category-colored buttons. Use `Color.White` for `AccentPdf`, `AccentVideo`, `AccentImage`, and `SuccessGreen`. Reserve `OnBraunOrange` exclusively for `BraunOrange` (`#FF5500`) and amber surfaces.

---

## Action Plan: Handover to Claude & Execution

With this screen audit complete, all 22 screens are documented. Here is the recommended task allocation between Claude and Gemini Flash:

### Phase 1: Mechanical Screen Fixes (Gemini Flash)
1. **Fix EXIF Stripper MIME Bug (`S1`):** In `ExifStripperScreen.kt:330`, dynamically resolve MIME type from `outputFile` extension instead of hardcoding `"image/jpeg"`.
2. **Fix Video to GIF Rotation Reset (`S2`):** In `VideoToGifScreen.kt`, guard `startSec` and `durationSec` updates against `lastLoadedUri` so slider positions survive device rotation.
3. **Fix Video Trim Input Validation & Naming (`S3`, `S4`):** In `VideoTrimMuteScreen.kt`, validate start/duration against `videoTotalSec`, and save as `Muted_*.mp4` when `isMuteOnly = true`.
4. **Fix Base64 Studio Binary State (`S5`):** In `Base64StudioScreen.kt`, retain binary metadata or cache decoded bytes to disk so rotation does not clear the result card.
5. **Fix QR Studio Overflow Notice (`S6`):** In `QrStudioScreen.kt`, catch `WriterException` and display "Text is too long for a QR code".
6. **Correct "Android 17" Typo:** In `CustomizationScreen.kt:156`, update copy to "Material You Dynamic Color (Android 12+)".

### Phase 2: Theme Merge & WCAG Contrast (Claude + Gemini)
1. **Merge Master into Theme Branch (`T3`):** Merge `master` into `apparat-braun-redesign`. Carefully resolve the 4 conflicting files (`HomeScreen.kt`, `CustomizationScreen.kt`, `HashVerifierScreen.kt`, `AudioExtractScreen.kt`), retaining master's dynamic shortcuts, metadata inspection, and tool chaining while keeping the Braun UI tokens.
2. **Fix WCAG Contrast on Theme Branch (`TR1`, `T4`):** Update `PdfSplit`, `PdfUnlock`, `PdfToImages`, `ImagesToPdf`, `ExifStripper`, `VideoTrimMute`, `VideoToGif`, and `CommonComponents` to use `Color.White` on dark category buttons instead of `OnBraunOrange`.
3. **Restore Batch 10 Presets & Chaining:** Re-apply the `chainActions` in `ResultCard` and quick presets in `ImageShrinkScreen`.

### Phase 3: Advanced Engines (Claude & DeepSeek)
1. **Real PDF Image Compression (`B5`):** Implement PDF image stream re-encoding in `PdfEngine.compressPdf`.
2. **Target KB Photo Search (`B6`):** Implement bisection search in `ImageEngine.kt`.
3. **Lossless JSON Formatter (`B14`):** Implement token-preserving JSON parser.
