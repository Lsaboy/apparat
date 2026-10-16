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

## Status (updated 2026-10-16)

- **Done:** all of 1A (1A.1–1A.8), plus 1B.4, 1B.6, 1B.9, which the same changes fixed.
  Commits `f7a7c19` … `33a52a8` on local `master`, **not pushed**.
- **Nothing in 1A has run on a device or emulator yet.** It builds, and the unit tests
  (GIF encoder, subtitle parser, decode-size maths) pass. See **1D.1** first: device
  verification of 1A comes before new work.
- **Next, in order:** 1D.1 (verify 1A) → 1D.2 (bugs/risks in the new 1A code) → rest of 1B →
  1C → rest of 1D → Phase 2.
- **Build environment:** `JAVA_HOME` isn't set system-wide. Use
  `export JAVA_HOME=$JAVA_HOME` before `./gradlew`. The SDK's
  `system-images/android-34/google_apis/x86_64` is an empty stub (never downloaded) and no AVD
  exists. Installing the image (~1.5 GB) needs the user's OK. `/dev/kvm` is available.

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
- [ ] **1C.3 "Share Sheet Shortcuts" setting is never read.** Launcher shortcuts are static
  in `res/xml/shortcuts.xml`. Either wire to `ShortcutManagerCompat` dynamic shortcuts or remove.
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
- [ ] Accept shared PDF (currently ignores share-in).

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
- [ ] Presets as chips: "Govt / portal upload", "WhatsApp", "Email", "Print".
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
- [ ] **"Continue with…"** chaining: Split→Compress, Photos→PDF→Compress, Shrink→Share.
- [ ] **Real progress + Cancel** everywhere. Video tools have real progress and cancel (via
  back → Stop) since 1A. PDF and image tools still use fixed 0.3/0.5 values and have no
  visible Cancel button.
- [x] **Share-in to every tool:** wired to all 15 single-file tools (PDF, Video, Image, Utilities) with onNewIntent reactive navigation and quick-action AssistChips on Home.
- [x] **Search keywords:** "resize", "merge", "jpg", "combine", "reduce size", "kb", etc.
  Tokenized multi-word search across title, description, and keywords implemented for all 20 tools.
- [ ] **Home:** quick actions are hard-coded (Unlock, Trim, Shrink Photo, Shrink Video) —
  let user pin tools; add Recent files / recent tools.
- [ ] **Copy cleanup:** "Modify OG" → "Replace original"; shorten long tool titles.
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
- [ ] **Video→GIF:** start time is hard-coded `0` (`VideoToGifScreen.kt:155`) — add start selection
  with preview; show estimated size.
- [ ] **Audio Extract:** picker only allows `video/*` though home routes audio shares to it.
- [x] ~~**Burn Subtitles:** FFmpeg path escaping / `.vtt` as `.srt` / libass~~ — obsolete, FFmpeg
  removed in 1A.1 (own SRT/VTT parser). Still to do: preview of first subtitle line.
- [x] **Video Convert:** became "Convert to MP4" (1A.1); MOV input accepted.
- [ ] **Shrink:** add "target size (e.g. 16 MB for WhatsApp, 25 MB for email)" mode; show
  input resolution/duration.
- [x] Real progress from encoder callbacks — done in 1A.1 (`Transformer.getProgress`, per-frame for
  GIF, per-buffer for WAV).
- [x] Media3 only (no FFmpeg) — done in 1A.1.
- [ ] Convert: show input codec/resolution (MediaExtractor/MediaMetadataRetriever); say
  "instant, no quality loss" when it will only remux.
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

- [ ] `ACTION_PROCESS_TEXT` (select text in any app → "Make QR / Format JSON / Base64 / Hash").
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
- [ ] **Tests:** JUnit added in 1A (`app/src/test`: GifEncoder, SubtitleParser, BitmapDecoder
  size maths). Still to add: page range parsing/validation, split chunking, target-size search,
  output naming; instrumented tests per 1D.4.
- [ ] `versionCode`/`versionName` still 1 / 1.0.0 — bump per release.

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
4. App name: **Digital Pocket Knife**. Package id stays `com.omniforge.app` unless asked (see 3B).

Still open (ask when reached):
5. Which Phase 2.9 extras (sign, stamp, page numbers) are worth building.
6. ~~Video→GIF and Burn Subtitles under Media3~~ — implemented in 1A.1 (pure-Kotlin GIF
   encoder; subtitles via a Media3 `BitmapOverlay`). Check quality/speed on a device; remove if poor.

## Verification checklist per phase

- Build: `JAVA_HOME=$JAVA_HOME ./gradlew assembleDebug testDebugUnitTest` clean;
  `lintDebug` with no new errors.
- Phase 1A specifics: see 1D.1 (detailed per-item device checks).
- Phase 1: test on API 29 + API 35 emulators and a real phone: 10-page and 150-page PDFs,
  encrypted PDF, 100 MB scanned PDF, 108 MP portrait photo, PNG with transparency, every video
  tool once, deep link `adb shell am start -d dpk://nonsense`.
- Phase 2: split/merge/organize round-trips, page counts match, output opens in another viewer.
- Phase 3: light theme pass on every screen, share-in from Gallery/Files/WhatsApp.
