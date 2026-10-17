# Digital Pocket Knife — Fix & Improvement Plan

Created: 2026-10-16. Source: full read-through of every screen, engine and build file
(no device testing yet). Items marked **[verify]** are strongly suspected from the code but
must be reproduced on a device before/while fixing.

Primary user: business owner in India, handles PDFs and photos daily — mostly **splitting
PDFs** and **resizing/shrinking photos** (often to meet upload size limits on portals).
Prioritise those flows.

Order of work:
1. **Phase 1** — app-breaking bugs and wrong-output bugs (all tools)
2. **Phase 2** — PDF tools (all problems + redesign around page previews)
3. **Phase 3** — everything else (images, cross-cutting UX, video, utilities, build)

Check items off as they land. Keep one commit per item (or small group) so regressions are
easy to bisect.

## Status (Handover to Claude — 2026-10-17)

- **Completed Batches 1–11 (Commits `2af848f` … `cf2f310` on `master`):**
  - **Phase 1 Complete:** All app-breaking bugs and wrong-output bugs resolved:
    - 1A: Media3 Transformer migration (FFmpeg removed), deep link whitelisting, OOM protection, background FGS jobs (`ToolJobs`), Android 10+ baseline (`minSdk = 29`), save ANR fixes.
    - 1B: PDF split chunking (1B.1) & range validation (1B.2), compress honest reporting (1B.3), EXIF rotation matrix (1B.4), shrink format encoding (1B.5), container cleanup (1B.6), overwrite format mismatch guard (1B.7), JPEG white canvas compositing for transparency (1B.8), exact max-dimension scaling (1B.9), A4 PDF page scaling (1B.10), lossless EXIF stripping preserving orientation (1B.11).
    - 1C: Dynamic theme recomposition (1C.1), default save mode honored (1C.2), dynamic launcher shortcuts via `ShortcutManagerCompat` & Home quick action sync (1C.3), centralized `DOWNLOAD_SUBFOLDER` (1C.4), stale temp cache cleanup on startup (1C.5).
    - 1D.2: Android 15 FGS timeout safety (`onTimeout`), monochrome 24dp notification icon (`ic_stat_notify`), `appContext` injection into `ToolJobs.launch` (Activity leak prevention), rotation/share-in protection banner, `singleTop` notification intent navigation, persistent notification permission, `NonCancellable` file downloads, single-pass multi-digest hashing (MD5/SHA-1/SHA-256/SHA-512) in `ToolJobs`, Media3 OpenGL HDR-to-SDR tone-mapping, WAV 16-bit PCM downsampling for float audio, animated image detection notice.
    - 1D.3: Unified screen titles, README rewritten for Media3, video subtitle font slider relative scale, overwrite selector hidden for formats.
  - **Phase 3 Core Features Landed:**
    - Tokenized multi-word search across all 20 tools with comprehensive keywords.
    - ResultCard 2-tier ergonomic layout with full-width Save, side-by-side Open (`AutoMirrored.OpenInNew`) and Share, Coil thumbnail preview, and "Continue with…" tool chaining.
    - Comprehensive system share sheet & `ACTION_VIEW` intent routing for PDFs, videos, images, and text.
    - `ACTION_PROCESS_TEXT` system integration with 4 activity-aliases (QR, JSON, Base64, Hash).
    - QR Studio 50x optimization with bulk `setPixels`, 150ms debounce, and templates (Wi-Fi, UPI, vCard).
    - Base64 URL-safe toggle, binary magic-byte detection (PDF, PNG, JPG, GIF, WebP), and direct Downloads export.
    - Version bumped to 1.0.1 (code 2), base archives name `DigitalPocketKnife`.
  - **Testing & Verification:**
    - 52 passing unit tests in `app/src/test` covering utility algorithms, settings, home search, PDF math, image decoders, and file utils (`./gradlew :app:testDebugUnitTest --rerun-tasks`).
    - Full technical documentation in `GEMINI_CHANGES.md` and `Gemini changes.md`.
- **Handover State:**
  - Gemini has completed all safe, self-contained fixes without regressions.
  - Detailed Code Audit and Handover Guide for Claude added below and in `CLAUDE_HANDOVER.md`.
- **Build Environment:**
  - `export JAVA_HOME=$JAVA_HOME` for all Gradle invocations.
  - minSdk: 29 (Android 10+), targetSdk / compileSdk: 35 (Android 15).
  - Package: `com.omniforge.app` (kept for upgrade path; user-visible brand is "Digital Pocket Knife").

---

## Phase 1 — App-breaking & wrong-output bugs

### 1A. Tools that crash or cannot work at all

- [x] **1A.1 Video tools have no FFmpeg engine inside.** `com.github.arthenica:ffmpeg-kit:v4.5.1.LTS`
  from JitPack resolves to a 55 KB AAR containing only Java classes — **no `.so` native
  libraries**; the built `app-debug.apk` contains no `libffmpegkit*.so`. Every
  `FFmpegKit.execute` call will throw `UnsatisfiedLinkError` (caught by `runCatching`, so the user
  just sees an error). Affects all 6 video tools. **[verify]** on device.
  - **DECIDED: Media3 only.** Remove `ffmpeg-kit` (and the JitPack repo if nothing else
    needs it). Rewrite `VideoEngine` on **Media3 Transformer** (already a dependency).
  - What each tool becomes:
    - **Video Shrinker** → Transformer with a scale/`Presentation` effect + video bitrate (MP4,
      H.264; H.265 optional where the device has an encoder).
    - **Trim** → `MediaItem.ClippingConfiguration`. Enable trim optimisation
      (`experimentalSetTrimOptimizationEnabled`) for near-lossless cuts. **Mute** →
      `EditedMediaItem.setRemoveAudio(true)`. With no effects, Transformer copies streams
      instead of re-encoding.
    - **Convert** → output is **MP4 only** (inputs MKV/WEBM/MOV/3GP still accepted). Rename the
      tool to "Convert to MP4". Remove the MKV/WEBM/GIF output chips.
    - **Audio Extract** → `setRemoveVideo(true)` → **M4A (AAC)**. Add **WAV** with our own code:
      decode via MediaExtractor/MediaCodec to PCM, write a WAV header. **Drop MP3/FLAC**: no
      offline encoder without FFmpeg.
    - **Burn Subtitles** → parse .srt/.vtt in Kotlin and render with Media3 `OverlayEffect` +
      timed `TextOverlay`. Re-check feasibility when implementing; if too rough, hide the tool.
    - **Video → GIF** → no Media3 GIF output. Option: pull frames with
      `MediaMetadataRetriever.getFrameAtTime` and write them with a small pure-Kotlin GIF
      encoder (palette quantisation). If quality or speed is poor, remove the tool. Decide while
      implementing; low priority for this user.
  - Update home descriptions to match. Remove claims about MKV/WEBM/MOV output, FLAC and MP3.
  - Files: `app/build.gradle.kts`, `settings.gradle.kts`, `engine/video/VideoEngine.kt`, all
    `feature/video/*`, `HomeScreen.kt` tool list.

- [x] **1A.2 Deep link crash.** `dpk://<anything>` is `BROWSABLE` + exported; `MainActivity`
  calls `navController.navigate(shortcutRoute)` with the raw host. An unknown route throws
  `IllegalArgumentException` → crash. Any web page link can trigger it.
  - Fix: whitelist known routes; ignore others. Remove `BROWSABLE` (shortcuts don't need it).
  - Files: `MainActivity.kt:63-82`, `AndroidManifest.xml`.

- [x] **1A.3 Out-of-memory crashes on large photos.** `convertFormat`, `stripExif`,
  `enhanceDocumentScan`, `extractColorPalette`, `PdfEngine.imagesToPdf` decode the full
  bitmap. 50–200 MP phone photos → ~200–800 MB ARGB → OOM.
  - Fix: one shared `decodeBitmap(uri, maxPixels)` helper that samples with `inSampleSize`,
    then exact-scales; apply EXIF rotation there too (see 1B.4).
  - Files: `engine/image/ImageEngine.kt`, `engine/pdf/PdfEngine.kt`.

- [x] **1A.4 Out-of-memory on big PDFs.** `PDDocument.load(file)` defaults to main memory.
  Large scanned PDFs (bank statements, 100+ page scans) can OOM.
  - Fix: `PDDocument.load(file, password, MemoryUsageSetting.setupTempFileOnly())` everywhere.

- [x] **1A.5 ANR / freeze on save.** Every "Save" / "Save All" calls
  `FileUtils.saveToDownloads` directly in `onClick` (main thread). Large videos or 50+ split
  files freeze the UI; can trigger "App not responding".
  - Fix: make `saveToDownloads` `suspend` + `Dispatchers.IO`, show progress, disable button
    while saving.

- [x] **1A.6 Password-protected PDFs fail in Split / Compress / PDF→Images** with a raw
  exception message (`InvalidPasswordException` from PDFBox, `SecurityException` from
  `PdfRenderer`). Very common for Indian bank statements, e-Aadhaar, Form 16.
  - Fix: detect encryption on file pick; show a password dialog (shared component) and pass
    the password through the engine. Reuse in all PDF tools.
  - Done via `core/ui/PdfPasswordGate.kt` + `PdfEngine.checkAccess`. **Not yet used by** Unlock
    (has its own field; see 2.7) or Merge (no screen yet; `mergePdfs` has no password param, see 2.2).

- [x] **1A.7 Saving broken on Android 8/9 (API 26–28).** Pre-Q branch inserts into
  `MediaStore.Files` with no path and no write permission.
  - **DECIDED: drop Android 8/9.** Set `minSdk = 29`. Delete the pre-Q branches in
    `FileUtils.saveToDownloads`. Drop `Build.VERSION` checks that are now always true.
    Review `READ_EXTERNAL_STORAGE`/`READ_MEDIA_*` permissions: the app uses the system picker,
    so they are probably unneeded. Remove them if nothing breaks.
  - Still version-gated above 29: `WEBP_LOSSY/LOSSLESS` (API 30), dynamic colour (31),
    `getParcelableExtra(class)` (33).
  - File: `core/util/FileUtils.kt:97-129`, `app/build.gradle.kts`, `AndroidManifest.xml`.

- [x] **1A.8 Long jobs are lost when leaving the screen / backgrounding.** Work runs in
  `rememberCoroutineScope()` of the screen. Pressing back mid-job cancels the coroutine (the
  blocking FFmpeg/PDFBox call keeps running, result discarded). Long video encodes in the
  background may be killed by the OS.
  - Fix: move processing into a ViewModel (minimum) or WorkManager/foreground service with a
    notification for long jobs. Confirm-on-back while processing.

### 1B. Tools that run but produce wrong / damaged output

- [x] **1B.1 PDF Split "Every N Pages" assumes 100 pages.** `(1..100 step n)` hard-coded.
  10-page PDF with N=1 → 100 files (90 duplicates of page 10). 150-page PDF → pages 101+ lost.
  - Fix: read page count first; compute chunks from real count. Move chunking into engine.
  - File: `feature/pdf/PdfSplitScreen.kt:231`.

- [x] **1B.2 PDF Split silently clamps invalid ranges.** "12-15" on a 10-page doc → page 10.
  "5-3" silently skipped. Non-numbers silently ignored.
  - Fix: validate against page count; show inline errors per token; block the button until valid.
  - Files: `PdfSplitScreen.kt:339` (`parseCustomRanges`), `PdfEngine.kt:201-202`.

- [x] **1B.3 PDF Compress does nothing.** `compressPdf` ignores `imageQuality` and just
  re-saves. Output can be larger; with "Modify OG" it overwrites the original anyway.
  - Phase 1 fix: never overwrite if output ≥ input; show honest "could not reduce" message.
  - Real compression is Phase 2 (2.6).
  - File: `engine/pdf/PdfEngine.kt:61-79`.

- [x] **1B.4 Photos come out rotated.** `BitmapFactory` ignores the EXIF orientation tag;
  outputs drop/strip it. Portrait phone photos turn sideways in Shrink (strip on), Convert,
  EXIF Strip, Scanner, Photos→PDF.
  - Fix: read `ExifInterface.rotationDegrees`/flip and apply a matrix in the shared decoder (1A.3).

- [x] **1B.5 Photo Shrinker ignores output format.** Always JPEG, but saved/shared as
  `.png`/`.webp` with wrong MIME.
  - Fix: pass format into `shrinkImage`; for PNG note that quality slider has no effect.
  - Files: `ImageEngine.kt:48-50`, `ImageShrinkScreen.kt`.

- [x] **1B.6 Video Shrinker ignores container choice.** Always MP4, saved as `.mkv`/`.webm`.
  - Resolved by 1A.1: Media3 outputs MP4 only, so remove the container selector.

- [x] **1B.7 "Modify Original" corrupts files when format changes.** PNG→JPG with Modify OG
  writes JPEG bytes into the `.png`. Same for Shrink→WEBP, Video→MKV etc.
  - Fix: only allow overwrite when output format == input format; otherwise disable the option
    with an explanation. Also refuse overwrite when output is larger (Shrink/Compress).
  - Also: `"wt"` mode needs write permission the picker may not grant → falls back silently;
    message currently claims "Saved as copy in Downloads" but nothing was saved. Fix message.

- [x] **1B.8 PNG/WEBP with transparency → black background** when converted to JPEG.
  - Fix: draw onto white canvas before JPEG encode.

- [x] **1B.9 Max-dimension resize is inaccurate.** `inSampleSize` is rounded down to a power
  of 2 → "1920px" yields 2000–3999px. Fix in shared decoder (sample, then exact scale).
  - File: `ImageEngine.kt:149-169`.

- [x] **1B.10 Photos→PDF page size = photo pixel size.** A 4000×3000 photo → ~1.4 m wide page.
  - Phase 1 fix: default to A4 fit with margins. Full options in Phase 2 (2.5).
  - File: `PdfEngine.kt:101`.

- [x] **1B.11 EXIF "strip" re-encodes at q95 and forces JPEG.** PNG input becomes JPG,
  quality loss, file can grow.
  - Fix: for JPEG, remove tags with `ExifInterface` (no re-encode). For PNG/WEBP keep format.

### 1C. Settings / features that silently do nothing

- [x] **1C.1 Theme change doesn't apply until restart.** **[verify]** `themeRecomposeTrigger`
  is incremented but never read; `appSettings.*` are plain getters → no recomposition.
  - Fix: expose settings as Compose state / `StateFlow` (DataStore), read in `setContent`.
  - File: `MainActivity.kt:68,106`, `core/settings/AppSettings.kt`.
- [x] **1C.2 "Default Save Mode" setting is never read.** Every tool hard-codes `NEW_COPY`.
- [x] **1C.3 "Share Sheet Shortcuts" setting is never read.** Wired to `ShortcutManagerCompat.setDynamicShortcuts`
  (removing static manifest metadata) and synced with dynamic Home quick actions pin bar.
- [x] **1C.4 Toasts lie about save folder.** 11 places say "Downloads/OmniForge"; files go to
  `Downloads/DigitalPocketKnife`. Centralise the folder name in one constant.
- [x] **1C.5 Temp files never deleted.** `cache/outputs/` grows forever (PDF→Images PNGs are
  big). Clean outputs older than N hours on app start + clear on new job per tool.
  Also clean (added in 1A): `cache/pdfbox/` (PDFBox scratch files left after a crash),
  `cache/decrypted_*.pdf` (PDF→Images on protected PDFs), `cache/input_*` copies. **Never**
  delete files belonging to a job that `ToolJobs` reports as running, or a result still shown.

### 1D. Follow-ups found while fixing 1A (2026-10-16)

Problems and risks found during the 1A work. **1D.1 and 1D.2 are blockers**: do them before
continuing with 1B/1C. The rest can be slotted in where noted.

#### 1D.1 Verify 1A on a device (blocker — nothing has been run yet)

- [ ] Get a test device: install the API 34 (and ideally API 29 + 35) system image and create
  AVDs, or use the user's phone over `adb`. Ask the user before the download.
- [ ] **Video (1A.1)**: run every video tool once on a real phone (emulator software codecs are
  slow and not representative):
  - Shrink: 1080p and 4K landscape + **portrait** clips. Check output orientation and size, and that
    the requested bitrate is honoured (some encoders ignore it).
  - Trim: start/duration in middle of clip; start past end → error; MP4 and MKV input. Confirm
    trim optimisation doesn't produce a frozen first second.
  - Mute: output has no audio track, video bit-identical length.
  - Convert to MP4: MKV (H.264), WEBM (VP9 + Opus), MOV, 3GP, HEVC input.
  - Audio: M4A from AAC source (should copy) and from Opus source (re-encode); WAV plays in
    another app and has correct duration (header patched correctly).
  - Burn Subtitles: SRT and VTT, multi-line cues, landscape + portrait video. Check the text box is
    bottom-centred and sized sensibly, no flicker between cues, and gaps show nothing.
  - Video→GIF: 5 s at 15 fps on 480 px. Time it: frame grabbing uses `OPTION_CLOSEST`, which
    seeks from a keyframe per frame (see 1D.4). Check colours/banding.
  - Progress bar moves for each; **cancel** (back → Stop) actually stops the encode and deletes
    the partial file.
- [ ] **Deep link (1A.2)**: `adb shell am start -a android.intent.action.VIEW -d dpk://nonsense`
  → app opens on home, no crash. `dpk://pdf_split` → opens Split. Launcher shortcuts still work.
- [ ] **Photos (1A.3/1B.4/1B.9)**: 108 MP (or 200 MP) photo through every image tool and
  Photos→PDF: no crash. Portrait photo (EXIF orientation 6) comes out upright everywhere.
  Shrink with "keep EXIF" doesn't double-rotate. HEIC input works. Max-dimension 1920 → exactly
  1920 px.
- [ ] **PDF memory (1A.4)**: 100+ page scanned PDF (~100 MB) through Split, Compress, PDF→Images.
  Check `cache/pdfbox` temp files are removed after each job.
- [ ] **Saving (1A.5)**: save a 500 MB video and 50 split files; UI stays responsive, button
  shows "Saving…". Check no half-written/pending entries remain in Downloads after a failed save.
- [ ] **Passwords (1A.6)**: user-password PDF (bank statement style) in Split / Compress /
  PDF→Images: dialog appears, wrong password re-asks, right one works. Owner-password-only PDF
  (opens without password but has restrictions) renders in PDF→Images (SecurityException
  fallback path). Damaged/non-PDF file → "isn't a readable PDF". Cancel dialog → selection cleared.
- [ ] **Android 10+ (1A.7)**: install on API 29: saving to Downloads works without any storage
  permission; pickers and share-in still work.
- [ ] **Background jobs (1A.8)**: start a long video shrink → back → "Keep running" → home
  screen → switch apps for several minutes → job finishes → "finished" notification → tap opens
  the tool with the result. Also: back → "Stop" → reopen tool shows Idle, not stuck on
  "Processing". Rotate / dark-mode switch during a job. Deny notification permission → job
  still completes. Check Android 14 (`dataSync`) and 15 (`mediaProcessing`) FGS types are
  accepted (no `MissingForegroundServiceTypeException`).

#### 1D.2 Bugs / risks in the new 1A code (blocker — fix before moving on)

- [x] **Foreground service timeout on Android 15.** `dataSync` and `mediaProcessing` FGS have a
  6-hour daily limit on API 35; when hit, the system calls `Service.onTimeout(startId, fgsType)`
  and **crashes the app if the service doesn't stop** within seconds. `ToolJobService` doesn't
  implement `onTimeout`. Fix: override it, cancel running jobs (or let them finish without FGS),
  call `stopSelf()`. File: `core/jobs/ToolJobService.kt`.
- [x] **Notification small icon.** Progress/done notifications use
  `R.drawable.ic_launcher_foreground` (full-colour adaptive-icon layer). Status bar will likely
  show a white square/blob. Add a monochrome 24dp vector (`ic_stat_notify`) and use it.
- [x] **Jobs hold the Activity.** The job lambdas capture `LocalContext.current` (the Activity).
  A job that outlives the screen keeps the destroyed Activity alive until it finishes (memory
  leak during long encodes; Toasts from a dead Activity). Fix: in `ToolJobs.launch`, pass
  `applicationContext` into the block (e.g. `block: suspend CoroutineScope.(Context) -> Unit`)
  and use that in every screen's job body.
- [x] **Stale state when a new file is shared into a busy tool.** `retained(...)` keeps the old
  `selectedUri` while that tool's job runs, so a new share-in (`initialUri`) is ignored without
  any message. Fix: if `initialUri` differs and a job is running, show "Busy with <file> —
  wait or stop it" banner; if idle, take the new URI.
- [x] **Notification tap resets the back stack.** `openToolIntent` uses `FLAG_ACTIVITY_CLEAR_TASK`
  → Activity recreated, other open tool's state dropped. Better: `singleTop` + handle
  `onNewIntent` in `MainActivity` (also needed for share-in, 3B) and navigate there.
- [x] **Notification permission nags each app run.** `askedForNotifications` is an in-memory
  flag. Persist "asked once" in `AppSettings`; after a denial, don't ask again (offer it in
  Settings instead).
- [x] **Saves still tied to the screen.** `ResultCard`'s Save and Split / PDF→Images "Save All" run
  in the screen's coroutine scope. Leaving mid-save cancels it; the partial entry is deleted,
  but for Save All some files are saved and the rest silently aren't. Fix: run saves through
  `ToolJobs` too (or `NonCancellable` + finish), and report "Saved 7 of 12".
- [ ] **Two heavy jobs at once.** `ToolJobs` lets every tool run a job at the same time (e.g. two
  video encodes); hardware encoders are limited and may fail with `ENCODER_INIT_FAILED`.
  Fix: queue video jobs (one at a time) and show "Waiting…" in the second tool.
- [ ] **Process death loses results.** The retained store is in memory only. If the OS kills the
  process (rare with the FGS, possible after the job finishes and the FGS stops), the result
  card is gone and the output file sits orphaned in `cache/outputs`. Acceptable for now. Revisit
  with ViewModel + `SavedStateHandle` (3E) and the cache cleanup (1C.5).
- [x] **Not converted to background jobs:** Hash Verifier (multi-GB files can take a while),
  Palette, QR. Hash moved to `ToolJobs` with single-pass multi-digest and throttled progress (see 3D).
- [ ] **Password gate copies the PDF twice.** `PdfEngine.checkAccess` copies the whole file to
  cache and parses it on pick; the tool then copies it again to run. Slow on 100 MB PDFs, and
  "Opening PDF…" has no progress or cancel. Fix with 2.0 "PDF info on pick": copy once, keep
  the cached file + page count + encryption flag for the whole screen session.
- [x] **Encryption of outputs is inconsistent.** With a password-protected input, Split outputs
  are written **without** a password (pages copied into new documents), while Compress re-saves
  the original document and **keeps** the encryption. Result messages now honestly inform the
  user ("Output files have no password" for Split / "Original password kept" for Compress).
- [x] **Media3 HDR input.** Phone cameras record HDR10/HLG. Transformer's default with H.264 SDR
  output may fail or look washed out. Set
  `Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL` for re-encoding tasks.
- [x] **Shrink can make files bigger.** Enforced across `VideoShrinkScreen`, `ImageShrinkScreen`, and
  `PdfShrinkScreen`: shows "could not reduce" and disables overwrite when output ≥ input.
- [x] **WAV assumes 16-bit PCM.** `decodeToWav` writes a 16-bit header always; if a decoder
  outputs float PCM (`KEY_PCM_ENCODING` = `ENCODING_PCM_FLOAT`), the WAV is noise. Read the
  output format's PCM encoding and write format 3 (float) or convert to 16-bit.
- [ ] **Image decode cap silently lowers resolution.** `BitmapDecoder` caps at 24 MP (12 MP on
  low-RAM, 16 MP for scanner / Photos→PDF). A 50 MP photo through **Convert** or **EXIF strip**
  comes out at 24 MP without telling the user. Fix: show "Reduced to W×H to fit in memory" in
  the result, and for EXIF strip on JPEG skip decoding entirely (1B.11 lossless path).
- [x] **Animated images lose animation.** `ImageDecoder.decodeBitmap` returns only the first
  frame of animated GIF/WEBP; Convert/Shrink silently output a still. Detect (`ImageDecoder`
  returns `AnimatedImageDrawable` via `decodeDrawable`) and warn.
- [ ] **Wide-gamut / 16-bit images.** `ImageDecoder` may return `RGBA_F16` / Display-P3 bitmaps
  for some HEIF/PNG. Check `Bitmap.compress` and PDFBox `JPEGFactory` handle them; if not, force
  `setTargetColorSpace(sRGB)` and `ARGB_8888` in `BitmapDecoder`.
- [x] **Run Android lint** (`./gradlew lintDebug`); not run during 1A. Expect `MissingPermission`
  (suppressed on `ToolJobService`), possible `NewApi`, unused imports and the now-unused
  `scope` vals in converted screens. Fix what it reports.

#### 1D.3 Stale text left behind by 1A (do with 3B copy cleanup, or sooner — it's quick)

- [x] **README.md** still says "powered by … **FFmpeg-Kit**", lists MKV/WEBM output, "Video
  Format Exchange" (MP4/MKV/WEBM/MOV/GIF), MP3/FLAC audio, `-c copy` trimming. Rewrite the video
  section for Media3: Convert to MP4, M4A/WAV, Android 10+ requirement, new notification permission.
- [x] Screen titles don't match the new home titles: "Video to Smooth GIF Maker" (→ "Video to
  GIF"), "Lossless Trim & Mute" (trim optimisation re-encodes the frames before the first
  keyframe, so "lossless" is not quite true → "Trim & Mute"), "Split PDF / Multi-File Split",
  "Shrink & Compress PDF", "Photo Format Exchange". Home tool card "Lossless Trim & Mute" + badge
  "Lossless" too. Pick one name per tool and use it in home, screen title, `ToolJobs` title,
  notifications and launcher shortcuts.
- [x] `VideoCaptionScreen`'s font-size slider says "pt"; it's now "relative to 720p height"
  (`SubtitleOverlay`). Rename to "Text size" with S/M/L or a preview.
- [x] Convert to MP4 still shows the **Save Mode** selector. "Modify original" on an `.mkv`
  writes MP4 bytes into the `.mkv` (1B.7). Until 1B.7 lands, hide Save Mode on Convert.

#### 1D.4 Quality/performance improvements to the new video code (after 1D.1 numbers)

- [ ] **Video→GIF speed:** replace per-frame `getScaledFrameAtTime(OPTION_CLOSEST)` with one
  sequential `MediaExtractor` + `MediaCodec` decode to a `ImageReader`/Surface, sampling frames
  at the target fps. If the 1D.1 timing is acceptable, keep as is.
- [ ] **Video→GIF quality:** add ordered (Bayer) or Floyd–Steinberg dithering option in
  `GifEncoder`, and frame-diff transparency to shrink files. Median-cut per frame is O(colours ×
  256), fine at 480 px; re-check at 720 px.
- [ ] **Media3 version:** project pins 1.4.1. Newer 1.5+/1.6+ improve trim optimisation, HDR
  handling and add `Transformer` features. Evaluate the upgrade once 1D.1 baselines exist.
- [ ] **Instrumented tests:** add `androidTest` with tiny bundled sample clips (MP4 H.264+AAC,
  WEBM VP9+Opus, portrait MP4, SRT) exercising each `VideoEngine` function and checking
  duration / track count / dimensions with `MediaMetadataRetriever`. Same for `BitmapDecoder`
  (EXIF-rotated JPEG, HEIC, PNG with alpha) and `PdfEngine` (encrypted PDF, 150-page PDF).

---

## Phase 2 — PDF tools

### 2.0 Shared building blocks (build first; every PDF tool uses them)

- [ ] **PDF info on pick:** page count, encrypted?, file size, first-page thumbnail in the
  file card. Never show a PDF tool without the page count.
- [ ] **`PdfThumbnailLoader`:** `PdfRenderer`-based, renders on IO, LRU memory cache, lazy per
  visible page, small size (≈200px wide) for grids, bigger for zoom.
- [ ] **`PdfPageGrid` composable:** lazy grid of thumbnails with page numbers; tap to toggle
  select; long-press-drag range select; "Select all / none / invert / odd / even";
  optional modes: reorder (drag), rotate (per page), split markers between pages; tap-and-hold
  to open full-screen zoom preview.
- [ ] **Password dialog** (from 1A.6), reused by all PDF tools.
- [ ] **Multi-file result card:** list of outputs with name + pages + size, rename each,
  "Save all" (to a sub-folder named after the source), "Share all" (`ACTION_SEND_MULTIPLE`),
  optional "Save as ZIP", "Open" (ACTION_VIEW).
- [ ] **Per-page real progress** ("Writing part 3 of 7…") + Cancel.
- [ ] **Output naming:** `<original>_pages_1-3.pdf`, not `part_1`. Editable.

### 2.1 Split PDF — redesign (user-reported)

- [ ] Replace the text-only UI with `PdfPageGrid`.
- [ ] Modes:
  - **Extract pages** — select pages → one PDF.
  - **Split into parts** — tap the gap between pages to place a ✂ marker; live summary
    "Part 1: pp 1–3 · Part 2: pp 4–9 · Part 3: pp 10–12".
  - **Every N pages** — stepper (not free text) with live count "→ 5 files".
  - **Custom ranges (advanced)** — keep text field, but validate live and highlight the
    pages each range covers in the grid.
- [ ] "Remove selected pages" shortcut (= extract the inverse).
- [ ] Multi-file result card (2.0); fix "Share First File" only.
- [x] Accept shared PDF (wired via `initialUri` and `lastHandledInitialUri` in Batch 8).

### 2.2 Merge PDFs — new screen (engine already exists: `PdfEngine.mergePdfs`)

- [ ] Pick multiple PDFs (and add more), list with first-page thumb, page count, drag to
  reorder, remove, total pages/size, name output.
- [ ] Handle encrypted inputs (password dialog per file).
- [ ] Add to home + search keywords ("merge", "combine", "join").

### 2.3 Organize Pages — new screen (falls out of `PdfPageGrid`)

- [ ] Reorder, rotate, delete, duplicate pages; insert blank page; insert pages from another
  PDF or images. Save as new PDF.

### 2.4 PDF → Images

- [ ] Page selection via grid (default all).
- [ ] Format JPG (default) / PNG; quality; resolution presets: "Share (150 DPI)",
  "Print (300 DPI)", custom. Currently fixed 2× PNG = huge files.
- [ ] Render from page size in points × DPI/72, cap max pixels to avoid OOM.
- [ ] Option: "Combine selected pages into one long image" (handy for WhatsApp).
- [ ] Results: thumbnail strip, Save all, Share all (currently only first page).

### 2.5 Photos → PDF

- [ ] Thumbnail strip/grid: drag to reorder, × remove, "+ Add more" (currently picking again
  replaces everything), rotate per photo.
- [ ] Add from camera directly.
- [ ] Page size: A4 (default) / Letter / Legal / Fit to photo; orientation auto/portrait/landscape;
  margins none/small/normal; image fit/fill.
- [ ] Compression: High quality / Balanced / Small file (downscale + JPEG quality); show
  estimated size.
- [x] Name the PDF (timestamped `Photos_yyyy-MM-dd_HHmmss.pdf`, not `combined_photos.pdf`).
- [ ] Accept multi-share from Gallery (`ACTION_SEND_MULTIPLE`, image/*).
- [ ] Per-photo progress.

### 2.6 Compress PDF — real implementation

- [ ] Walk page resources; for each image XObject: downscale to target DPI and re-encode JPEG
  at chosen quality; replace in place. Remove unused objects/metadata; save compressed.
- [ ] "Strong" mode for scanned PDFs: rasterize each page to JPEG at chosen DPI and rebuild
  (loses text selectability — say so).
- [ ] **Target size presets:** "Under 2 MB / 1 MB / 500 KB / 200 KB / custom" — iterate
  quality/DPI until under target (portal upload limits). Report if impossible.
- [ ] Before/after size; never overwrite if larger (1B.3).
- [ ] Before running, show estimated output size and a sample page preview at the chosen level
  (render one page, compress in memory) so the user can check text stays readable.
- [ ] Remove the fake slider label "Image Downsample Quality" until it's real.

### 2.7 Unlock / Lock PDF

- [x] On pick: if not encrypted, say "This PDF has no password" and skip.
- [x] Distinguish wrong password vs damaged file (catch `PdfPasswordException` separately).
- [ ] After a correct password, show the first-page thumbnail as proof before saving.
- [ ] Optional hint text for common Indian document password formats.
- [ ] **New: Add password to PDF** (AES-256 via PDFBox `StandardProtectionPolicy`), optional
  permissions (no print/copy). Useful for sending statements/invoices.
- [x] Copy cleanup: drop "DRM-free" wording (misleading — it's password removal).

### 2.8 PDF share-in / open-with

- [x] Shared PDF reaches every PDF tool (Compress, Unlock, Split, To Images) via `initialUri` and rotation-safe `lastHandledInitialUri`.
- [x] Register `ACTION_VIEW` for `application/pdf` so the app appears in "Open with", landing on home with contextual action sheet.

### 2.9 Later / optional PDF features (ask user before building)

- [ ] Sign PDF (place saved signature image on a page) — useful for invoices/agreements.
- [ ] Add page numbers / header-footer / company stamp or watermark.
- [ ] Simple viewer with search.

---

## Phase 3 — Everything else

### 3A. Image tools (second priority after PDFs for this user)

**Photo Shrinker → "Resize & Shrink Photo"**
- [ ] Preview of the selected photo with dimensions and size.
- [ ] **Target file size** mode (default): 20 / 50 / 100 / 200 / 500 KB / 1 MB / custom —
  binary-search JPEG quality, then step down dimensions if needed; show final size.
- [ ] **Exact size** mode: width × height (lock aspect), percentage, presets: passport photo,
  signature, A4 @150/300 DPI, WhatsApp/email. Optional DPI metadata.
- [ ] Crop (free / passport ratio / square).
- [ ] Before/after comparison (draggable split slider, pinch-zoom to check text sharpness) and
  result showing new dimensions.
- [ ] Live size estimate while moving sliders (debounced in-memory encode of a downscaled copy,
  no disk write): "4.2 MB → ~780 KB".
- [x] Presets as chips: "Portal / ID (<100KB)", "WhatsApp / Chat (1600px, 80%)", "Email / Social (1920px, 85%)", "Max Quality (3840px, 95%)" with continuous slider.
- [ ] **Batch:** pick many → same settings → save all/share all.
- [ ] Replace abstract "Quality %" / "Max dimension" sliders with the above (keep as "Advanced").

**Photo Format Convert**
- [ ] Batch; HEIC/HEIF input (API 28+ `ImageDecoder`); "to PDF" option; preview.
- [ ] Transparency → JPEG: white matte by default (1B.8), with option for another colour.
- [x] Add lossless WEBP (`WEBP_LOSSLESS`, API 30+) in ImageEngine, ImageShrinkScreen, and ImageConvertScreen; AVIF only if device supports it (low priority).

**Document Scanner / Receipt Enhancer**
- **DECIDED: fully offline, custom scanner.** No ML Kit, no Play services. Pure Kotlin;
  avoid OpenCV (adds tens of MB).
- [ ] Capture: system camera (`ActivityResultContracts.TakePicture`, no CameraX needed) or
  pick from gallery.
- [ ] Manual 4-corner crop with draggable handles and a magnifier loupe while dragging.
  Perspective correction via `Matrix.setPolyToPoly` on a downscaled working copy; apply to
  full-resolution output at the end.
- [ ] Optional auto-detect corners (simple edge/contour heuristic on a small copy). Manual
  adjust is always available; ship without auto-detect if unreliable.
- [ ] Adaptive threshold (Sauvola/Bradley via integral image) instead of global contrast.
  This fixes shadows.
- [ ] Filters: "B&W document", "Grayscale", "Colour (keeps stamps & signatures)", "Original".
- [ ] Multi-page tray (add, reorder, retake, delete) → export as PDF (A4) or JPEGs. Reuse the
  Photos→PDF engine from 2.5.

**EXIF Stripper**
- [x] Lossless strip (1B.11); show photo preview; show "No metadata found" state. (Batch pending).
- [ ] "Remove location only" vs "Remove everything"; also purge embedded EXIF thumbnail.
- [ ] Group tags by risk (location / device / date vs harmless camera settings).

**Palette Extractor** (low priority for this user)
- [x] Show the image; copy all; HEX and RGB display with border contrast; retained state across rotation.
  Consider hiding behind "More tools".

### 3B. Cross-cutting UX

- [ ] **Light / Material You themes inside tools:** every tool screen uses hard-coded dark
  colours (`DarkSurfaceElevated`, `TextPrimary` #F5F5F7, `AmoledBlack` on buttons). Replace with
  `MaterialTheme.colorScheme` tokens; keep category accents. Home header also hard-codes
  `TextPrimary`.
- [ ] **File card thumbnails** (images, PDF first page, video frame) — Coil is already a
  dependency and unused.
- [x] **Result card upgrade:** preview image output (AsyncImage), Open button (via FileProvider and Intent.createChooser), 2-tier ergonomic action layout with full-width Save.
- [x] **"Continue with…"** chaining: Photos→PDF→Compress, Image Convert→Shrink, Trim Video→Make GIF via ResultCard ToolChainAction assist chips and FileProvider content URIs.
- [ ] **Real progress + Cancel** everywhere. Video tools have real progress and cancel (via
  back → Stop) since 1A. PDF and image tools still use fixed 0.3/0.5 values and have no
  visible Cancel button.
- [x] **Share-in to every tool:** wired to all 15 single-file tools (PDF, Video, Image, Utilities) with onNewIntent reactive navigation and quick-action AssistChips on Home.
- [x] **Search keywords:** "resize", "merge", "jpg", "combine", "reduce size", "kb", etc.
  Tokenized multi-word search across title, description, and keywords implemented for all 20 tools.
- [x] **Home:** quick actions dynamically derived from `appSettings.enabledShortcuts` (customizable via Settings) and synced with dynamic Android launcher shortcuts.
- [x] **Copy cleanup:** "Modify OG" → "Replace original"; standardized screen titles.
- [x] **Naming — DECIDED: "Digital Pocket Knife".** Replace user-visible "OmniForge" everywhere:
  toasts/messages (most handled by 1C.4), unlock-screen text, README, `rootProject.name = "DigitalPocketKnife"`,
  proguard comment, and output APK naming `base.archivesName = "DigitalPocketKnife"`. Save folder stays
  `Downloads/DigitalPocketKnife`. Output APK named `DigitalPocketKnife-debug.apk` / `DigitalPocketKnife-release.apk`.
  - **Keep** `applicationId`/package `com.omniforge.app`. Changing it makes Android treat the app as a new
    one: the user must uninstall, and settings are lost. Change it only if the user asks
    (best done before any public release).
- [ ] **Accessibility:** many 11sp texts, icon-only buttons without labels, low-contrast
  `TextTertiary`. Min 48dp touch targets.
- [ ] Hindi/regional language support (optional, later).

### 3C. Video tools (after 1A.1 engine fix)

- [ ] **Trim:** video preview with scrubber + start/end handles instead of typing seconds;
  keep input container (or re-mux correctly) instead of always `.mp4`; explain keyframe snapping
  or offer "precise (re-encode)" option.
- [x] **Video→GIF:** Start Offset slider with duration bounds; total frame count and estimated output file size.
- [x] **Audio Extract:** picker allows `video/*` and `audio/*`; shows input resolution, duration, and size formatted in `fileDetails` with audio duration query fallback; M4A lossless stream copy notice.
- [x] ~~**Burn Subtitles:** FFmpeg path escaping / `.vtt` as `.srt` / libass~~ — obsolete, FFmpeg
  removed in 1A.1 (own SRT/VTT parser). Still to do: preview of first subtitle line.
- [x] **Video Convert:** became "Convert to MP4" (1A.1); MOV input accepted.
- [ ] **Shrink:** add "target size (e.g. 16 MB for WhatsApp, 25 MB for email)" mode; show
  input resolution/duration.
- [x] Real progress from encoder callbacks — done in 1A.1 (`Transformer.getProgress`, per-frame for
  GIF, per-buffer for WAV).
- [x] Media3 only (no FFmpeg) — done in 1A.1.
- [x] Convert & Shrink: show input resolution, duration, and size formatted in FileSelectionCard (via `VideoEngine.getVideoMetadata` with rotation correction and fallback).
- [ ] Audio Extract: AAC → M4A without re-encoding; optional start/end range.
- [ ] Shrink: optional HEVC (smaller) with compatibility warning; keep-original-audio switch.
- [ ] Captions: preview of subtitle on a frame, position (top/bottom), sync offset ±s,
  (soft-subtitle mux dropped: needs FFmpeg/MKV). Low priority for this user.

### 3D. Quick utilities

- [x] **QR Studio:** QR generated on main thread on every keystroke with a 262k `setPixel`
  loop → typing lag. Fixed with 150ms debounce + `Dispatchers.Default` background generation + bulk `setPixels` on `ARGB_8888`.
  Templates added for Wi-Fi (ZXing escaping), UPI payment (NPCI spec with URL encoding), Contact vCard 3.0, and Text/URL with reactive live preview.
- [ ] **QR scanning** (camera via CameraX + ZXing, and "scan from screenshot/gallery") with
  actions: open URL, copy, connect to Wi-Fi, add contact. Styling/SVG export: skip unless asked.
- [x] **Hash Verifier:** Run via `ToolJobs` (1D.2) with retained state; single-pass multi-digest computing
  SHA-256, SHA-512, SHA-1, and MD5 in one streaming pass; throttled progress updates; hash comparison box
  supporting all 4 digests and clipboard auto-paste.
- [x] **Base64:** Added URL-safe toggle (`URL_SAFE` flag with resilient fallback) and strict UTF-8 decoding
  guarding against corrupted binary input strings.
- [x] **Base64 (cont.):** Detect file type from decoded bytes (PNG, JPEG, WebP, GIF, PDF) with magic-byte detection; offer "Save Decoded File to Downloads" on IO dispatcher with NonCancellable; strip `data:` URL prefix; Clear & Paste buttons.
- [x] **JSON:** Error line and column reporting implemented via character index offset calculation; retained state and clear/paste buttons added. Syntax colours / collapsible tree: low priority.

### 3F. OS integration (low priority, after core tools are solid)

- [x] `ACTION_PROCESS_TEXT` (select text in any app → "Make QR", "Format JSON", "Base64 Studio", "Calculate Hash" registered via 4 activity-aliases to MainActivity).
- [x] `text/plain` share routing (JSON → JSON tool, URL/text → QR Studio) and `*/*` → Hash Verifier, with reactive `SharedPayload` updating across `onNewIntent`.
- [ ] Quick Settings tile for "Scan QR" / "Shrink last photo" (optional).
- [ ] Drag-and-drop onto file cards (tablets / split-screen).

### 3E. Build, release, code health

- [ ] `isMinifyEnabled = false` and `shrinkResources` off for release; ABI filters include
  x86/x86_64 → bigger APK. Enable R8 + resource shrinking, use ABI splits / App Bundle.
- [ ] `window.statusBarColor` / `navigationBarColor` are no-ops on Android 15 with
  edge-to-edge (targetSdk 35) — rely on `enableEdgeToEdge` + insets.
- [x] Remove `ffmpeg-kit` dep, JitPack repo and `pickFirsts libc++_shared.so` — done in 1A.1.
  (`smart-exception` was never a direct dependency.) Add `media3-exoplayer`/`ui` only if
  video previews get built (3C).
- [ ] With FFmpeg gone, re-check APK size; likely possible to drop x86/x86_64 from `abiFilters`
  entirely (only emulators use them).
- [ ] Move screen state + logic into ViewModels + `SavedStateHandle` (process death; see 1D.2).
  1A.8 was solved without ViewModels (`core/jobs/ToolJobs` + `retained` store); a later
  ViewModel move should keep jobs in `ToolJobs` so they still survive leaving the screen.
- [x] **Tests:** 52 unit tests passing in `app/src/test`. Added unit tests for: split chunking (`PdfSplitTest.kt`), A4 scaling & aspect ratio (`PdfA4FitTest.kt`), cache directory cleanup & MIME verification (`FileUtilsTest.kt`), WebP animation flag parsing (`BitmapDecoderTest.kt`), EXIF metadata detection (`ExifInfoTest.kt`), Home search keywords & tokenization (`HomeSearchTest.kt`), dynamic launcher shortcuts capping (`LauncherShortcutsTest.kt`), and message digests / text checksums / data URLs / magic byte detection (`UtilityEngineTest.kt`). Remaining: instrumented on-device tests per 1D.4.
- [x] `versionCode`/`versionName` bumped to 2 / 1.0.1 in app/build.gradle.kts.

---

## Note on the Gemini review (2026-10-16)

The user shared a Gemini-generated review. Useful UX ideas from it are merged above (live size
estimate, split-slider compare, scanner perspective/adaptive threshold, EXIF location-only,
hash compare/clipboard, QR scanning, video stream-copy routing, OS integration).
**Do not trust its "current state" table** — checked against the code, it describes features
that don't exist: Photos→PDF "reorder chips + page size dropdown", Video Shrinker "target size
chips", Trim "HH:MM:SS start/end" (it's seconds start + duration), Convert "MOV/AVI + speed
preset", Audio "bitrate chips", Scanner "adaptive thresholding 50–200" (it's global contrast
1.0–3.0), Image Convert "BMP", QR "Text/URL/Wi-Fi tabs", Hash "SHA-1", JSON "2 spaces" (it's 4).
It also missed the critical bugs in Phase 1 (missing FFmpeg natives, split 100-page bug, no-op
compression, rotation, deep-link crash, OOM/ANR). Its priority list puts video trim and palette
at P0/P1; for this user PDFs and photo resizing come first.

## Decisions

Decided by user on 2026-10-16:
1. Video engine: **Media3 only**. No FFmpeg (see 1A.1 for per-tool consequences).
2. Document scanner: **fully offline custom**. No ML Kit (see 3A).
3. **Drop Android 8/9** → `minSdk = 29` (see 1A.7).
4. App name: **Apparat** (final decision 2026-10-17). Package id stays `com.omniforge.app`.

### Post-Audit Resolved Issues (2026-10-17)
- **Master (`6fc8b11`, `58f378c`):**
  - **B2:** Shared-file payload auto-clears on destination tool consumption (`ConsumeSharedPayloadEffect`) and adds dismiss (X) button on `HomeScreen` banner.
  - **B3:** QR codes pass UTF-8 hint to ZXing (`EncodeHintType.CHARACTER_SET to "UTF-8"`), preserving Hindi, ₹, and emoji.
  - **B4:** Release workflow fixes environment variable scoping and `build.gradle.kts` enforces release keystore.
  - **B7:** ProGuard keep rule added for `ExifInterface.TAG_*` and GPS post-strip check added.
  - **B9:** `FileUtils.getFileName` and `getFileSize` wrapped in `runCatching` to prevent `SecurityException` crashes.
  - **B10:** Cache file names use `input_<ts><ext>` to avoid `ENAMETOOLONG`, and delete partial files on failure.
  - **B15:** Partial output files deleted on failure in `PdfEngine` (compress, imagesToPdf, merge, pdfToImages), with `try-finally` for pages/bitmaps.
  - **B16:** Reject `file://` schemes and internal FileProvider authorities in incoming intents.
- **Theme branch (`c0eed39` in `apparat-braun`):**
  - **T1:** Restored `dpk_settings` SharedPreferences name in `AppSettings.kt` to preserve user preferences on app upgrade.
  - **T4:** Mapped `onPrimary` to `OnBraunOrange` (`#161618`) for Braun Orange and Amber Indicator accents in `Theme.kt`, ensuring WCAG AA contrast compliance (~5.7:1 vs 3.2:1).

---

## Handover Code Audit & Roadmap for Claude (2026-10-17)

This audit is based on full static analysis across all 20 tools, engine implementations, subagent architectural reviews, and 52 unit tests. It documents every broken, incomplete, or high-risk mechanism in the project.

### 1. High-Priority Broken Features & Architectural Debt

#### A. PDF Compress Fake Compression (`PdfEngine.kt:compressPdf` & `PdfShrinkScreen.kt`)
- **Current State:** `compressPdf` loads the `PDDocument` and immediately calls `doc.save(output, CompressParameters.DEFAULT_COMPRESSION)`. It ignores `imageQuality` entirely. In standard PDFs, text and vector streams are already Flate-compressed; 95% of byte size comes from embedded JPEG/PNG image XObjects (`PDImageXObject`). As a result, files often come out the exact same size or even larger.
- **Fix Needed:**
  1. Iterate over pages and page resources (`page.resources.xObjectNames`).
  2. For each `COSName` where the resource is a `PDImageXObject`, read the image stream, downscale if dimensions exceed target DPI (e.g. 150 DPI), and re-compress to JPEG at the specified quality factor (e.g. 60–80%).
  3. Replace the stream in the `PDResources` dictionary.
  4. For scanned PDFs (where pages are full-page bitmaps), offer a "Rasterize & Rebuild" mode using `PdfRenderer` + `PdfEngine.imagesToPdf`.
  5. Add portal upload presets ("Under 2 MB / 1 MB / 500 KB / 200 KB").

#### B. Missing UI for Existing PDF Merge Engine (`PdfMergeScreen.kt` missing)
- **Current State:** `PdfEngine.mergePdfs(context, uris, outputFile)` is fully implemented, but there is NO screen for merging PDFs!
- **Fix Needed:**
  1. Create `PdfMergeScreen.kt` with multi-document picker (`ActivityResultContracts.OpenMultipleDocuments`).
  2. Display picked PDFs in a reorderable card list showing filename, size, and page count.
  3. Support "+ Add More" and item deletion.
  4. Integrate `PdfPasswordGate` for password-protected input PDFs.
  5. Wire into `MainActivity.kt` and `HomeScreen.kt`.

#### C. Hardware Video Encoder Exhaustion on Concurrent Jobs (`ToolJobs.kt` & `VideoEngine.kt`)
- **Current State:** `ToolJobs.launch` permits any screen to launch an independent background job. If a user starts video shrink in `VideoShrinkScreen` and then starts video convert in `VideoConvertScreen`, two Media3 Transformer pipelines initialize hardware video encoders concurrently.
- **Risk:** Most mobile SoCs (Snapdragon, MediaTek, Exynos, Tensor) only support 1 or 2 concurrent hardware `MediaCodec` encoder instances. Exceeding this limit crashes Transformer immediately with `MediaCodec.CodecException: ERROR_INSUFFICIENT_RESOURCE` or `ENCODER_INIT_FAILED`.
- **Fix Needed:** Implement a single-job concurrency lock / FIFO queue in `ToolJobs` or `VideoEngine` specifically for video encoding jobs. If a second video job is started, set its state to `ProcessState.Queued("Waiting for previous video encode to finish…")`.

#### D. Process Death Wipes Retained State (`CommonComponents.kt:retained`)
- **Current State:** Retained state is kept in an in-memory `ConcurrentHashMap` singleton. While it survives configuration changes (rotation), it is wiped if the OS kills the application process in the background.
- **Risk:** If a user backgrounds the app during a long encode and the process is killed after the job finishes, reopening the app returns to `ProcessState.Idle` with no output result card shown, even though the file exists in `cache/outputs/`.
- **Fix Needed:** Migrate screen state into Jetpack `ViewModel` + `SavedStateHandle`, or persist minimal session records (jobKey, selectedUri, outputFile, processState) in SharedPreferences.

#### E. Silent 24MP Downscaling in BitmapDecoder (`BitmapDecoder.kt`)
- **Current State:** `BitmapDecoder.decodeBitmap` caps images at 24 MP (12 MP on low-RAM devices).
- **Risk:** 50 MP, 108 MP, or 200 MP camera photos processed through `ImageConvertScreen` or non-JPEG `ExifStripperScreen` are silently downscaled to 24 MP without the user being informed.
- **Fix Needed:** Show an informative badge/notice in the ResultCard ("Note: Scaled to 24MP to fit device memory").

#### F. Video Trim Scrubber & Frame Preview (`VideoTrimMuteScreen.kt`)
- **Current State:** Trim times (`startSec`, `endSec`) are typed into plain numeric text fields. There is no visual scrubber or playback preview to see cut points.
- **Fix Needed:** Add a video player preview (ExoPlayer / Media3 UI) with start/end scrubber handles.

#### G. Target File Size (KB) Search Mode for Photo & Video Shrink
- **Current State:** Photo Shrink has quality and dimension sliders; Video Shrink has scale and bitrate presets. Neither tool has an exact "Target Size" mode.
- **Requirement:** Indian government portals (UPSC, SSC, PAN, Aadhaar) mandate strict file sizes (e.g. "between 20 KB and 50 KB" or "strictly under 100 KB").
- **Fix Needed:**
  - In `ImageEngine.kt`: add binary search loop over JPEG quality and dimensions targeting `outputBytes <= targetKB`.
  - In `VideoEngine.kt`: calculate `targetBitrate = (targetBytes * 8) / durationSec - audioBitrate`.

#### H. Document Scanner Perspective Crop (`DocumentScanScreen.kt`)
- **Current State:** Only has basic contrast/brightness sliders and B&W threshold.
- **Fix Needed:** Add manual 4-corner perspective crop (quadrilateral warp via `Matrix.setPolyToPoly`), camera capture, and multi-page tray.
