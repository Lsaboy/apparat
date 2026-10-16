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

- [ ] **1A.3 Out-of-memory crashes on large photos.** `convertFormat`, `stripExif`,
  `enhanceDocumentScan`, `extractColorPalette`, `PdfEngine.imagesToPdf` decode the full
  bitmap. 50–200 MP phone photos → ~200–800 MB ARGB → OOM.
  - Fix: one shared `decodeBitmap(uri, maxPixels)` helper that samples with `inSampleSize`,
    then exact-scales; apply EXIF rotation there too (see 1B.4).
  - Files: `engine/image/ImageEngine.kt`, `engine/pdf/PdfEngine.kt`.

- [ ] **1A.4 Out-of-memory on big PDFs.** `PDDocument.load(file)` defaults to main memory.
  Large scanned PDFs (bank statements, 100+ page scans) can OOM.
  - Fix: `PDDocument.load(file, password, MemoryUsageSetting.setupTempFileOnly())` everywhere.

- [ ] **1A.5 ANR / freeze on save.** Every "Save" / "Save All" calls
  `FileUtils.saveToDownloads` directly in `onClick` (main thread). Large videos or 50+ split
  files freeze the UI; can trigger "App not responding".
  - Fix: make `saveToDownloads` `suspend` + `Dispatchers.IO`, show progress, disable button
    while saving.

- [ ] **1A.6 Password-protected PDFs fail in Split / Compress / PDF→Images** with a raw
  exception message (`InvalidPasswordException` from PDFBox, `SecurityException` from
  `PdfRenderer`). Very common for Indian bank statements, e-Aadhaar, Form 16.
  - Fix: detect encryption on file pick; show a password dialog (shared component) and pass
    the password through the engine. Reuse in all PDF tools.

- [x] **1A.7 Saving broken on Android 8/9 (API 26–28).** Pre-Q branch inserts into
  `MediaStore.Files` with no path and no write permission.
  - **DECIDED: drop Android 8/9.** Set `minSdk = 29`. Delete the pre-Q branches in
    `FileUtils.saveToDownloads`. Drop `Build.VERSION` checks that are now always true.
    Review `READ_EXTERNAL_STORAGE`/`READ_MEDIA_*` permissions: the app uses the system picker,
    so they are probably unneeded. Remove them if nothing breaks.
  - Still version-gated above 29: `WEBP_LOSSY/LOSSLESS` (API 30), dynamic colour (31),
    `getParcelableExtra(class)` (33).
  - File: `core/util/FileUtils.kt:97-129`, `app/build.gradle.kts`, `AndroidManifest.xml`.

- [ ] **1A.8 Long jobs are lost when leaving the screen / backgrounding.** Work runs in
  `rememberCoroutineScope()` of the screen. Pressing back mid-job cancels the coroutine (the
  blocking FFmpeg/PDFBox call keeps running, result discarded). Long video encodes in the
  background may be killed by the OS.
  - Fix: move processing into a ViewModel (minimum) or WorkManager/foreground service with a
    notification for long jobs. Confirm-on-back while processing.

### 1B. Tools that run but produce wrong / damaged output

- [ ] **1B.1 PDF Split "Every N Pages" assumes 100 pages.** `(1..100 step n)` hard-coded.
  10-page PDF with N=1 → 100 files (90 duplicates of page 10). 150-page PDF → pages 101+ lost.
  - Fix: read page count first; compute chunks from real count. Move chunking into engine.
  - File: `feature/pdf/PdfSplitScreen.kt:231`.

- [ ] **1B.2 PDF Split silently clamps invalid ranges.** "12-15" on a 10-page doc → page 10.
  "5-3" silently skipped. Non-numbers silently ignored.
  - Fix: validate against page count; show inline errors per token; block the button until valid.
  - Files: `PdfSplitScreen.kt:339` (`parseCustomRanges`), `PdfEngine.kt:201-202`.

- [ ] **1B.3 PDF Compress does nothing.** `compressPdf` ignores `imageQuality` and just
  re-saves. Output can be larger; with "Modify OG" it overwrites the original anyway.
  - Phase 1 fix: never overwrite if output ≥ input; show honest "could not reduce" message.
  - Real compression is Phase 2 (2.6).
  - File: `engine/pdf/PdfEngine.kt:61-79`.

- [ ] **1B.4 Photos come out rotated.** `BitmapFactory` ignores the EXIF orientation tag;
  outputs drop/strip it. Portrait phone photos turn sideways in Shrink (strip on), Convert,
  EXIF Strip, Scanner, Photos→PDF.
  - Fix: read `ExifInterface.rotationDegrees`/flip and apply a matrix in the shared decoder (1A.3).

- [ ] **1B.5 Photo Shrinker ignores output format.** Always JPEG, but saved/shared as
  `.png`/`.webp` with wrong MIME.
  - Fix: pass format into `shrinkImage`; for PNG note that quality slider has no effect.
  - Files: `ImageEngine.kt:48-50`, `ImageShrinkScreen.kt`.

- [x] **1B.6 Video Shrinker ignores container choice.** Always MP4, saved as `.mkv`/`.webm`.
  - Resolved by 1A.1: Media3 outputs MP4 only, so remove the container selector.

- [ ] **1B.7 "Modify Original" corrupts files when format changes.** PNG→JPG with Modify OG
  writes JPEG bytes into the `.png`. Same for Shrink→WEBP, Video→MKV etc.
  - Fix: only allow overwrite when output format == input format; otherwise disable the option
    with an explanation. Also refuse overwrite when output is larger (Shrink/Compress).
  - Also: `"wt"` mode needs write permission the picker may not grant → falls back silently;
    message currently claims "Saved as copy in Downloads" but nothing was saved. Fix message.

- [ ] **1B.8 PNG/WEBP with transparency → black background** when converted to JPEG.
  - Fix: draw onto white canvas before JPEG encode.

- [ ] **1B.9 Max-dimension resize is inaccurate.** `inSampleSize` is rounded down to a power
  of 2 → "1920px" yields 2000–3999px. Fix in shared decoder (sample, then exact scale).
  - File: `ImageEngine.kt:149-169`.

- [ ] **1B.10 Photos→PDF page size = photo pixel size.** A 4000×3000 photo → ~1.4 m wide page.
  - Phase 1 fix: default to A4 fit with margins. Full options in Phase 2 (2.5).
  - File: `PdfEngine.kt:101`.

- [ ] **1B.11 EXIF "strip" re-encodes at q95 and forces JPEG.** PNG input becomes JPG,
  quality loss, file can grow.
  - Fix: for JPEG, remove tags with `ExifInterface` (no re-encode). For PNG/WEBP keep format.

### 1C. Settings / features that silently do nothing

- [ ] **1C.1 Theme change doesn't apply until restart.** **[verify]** `themeRecomposeTrigger`
  is incremented but never read; `appSettings.*` are plain getters → no recomposition.
  - Fix: expose settings as Compose state / `StateFlow` (DataStore), read in `setContent`.
  - File: `MainActivity.kt:68,106`, `core/settings/AppSettings.kt`.
- [ ] **1C.2 "Default Save Mode" setting is never read.** Every tool hard-codes `NEW_COPY`.
- [ ] **1C.3 "Share Sheet Shortcuts" setting is never read.** Launcher shortcuts are static
  in `res/xml/shortcuts.xml`. Either wire to `ShortcutManagerCompat` dynamic shortcuts or remove.
- [ ] **1C.4 Toasts lie about save folder.** 11 places say "Downloads/OmniForge"; files go to
  `Downloads/DigitalPocketKnife`. Centralise the folder name in one constant.
- [ ] **1C.5 Temp files never deleted.** `cache/outputs/` grows forever (PDF→Images PNGs are
  big). Clean outputs older than N hours on app start + clear on new job per tool.

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
- [ ] Name the PDF (default `Scan_2026-10-16.pdf`, not `combined_photos.pdf`).
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

- [ ] On pick: if not encrypted, say "This PDF has no password" and skip.
- [ ] Distinguish wrong password vs damaged file (catch `InvalidPasswordException` separately).
- [ ] After a correct password, show the first-page thumbnail as proof before saving.
- [ ] Optional hint text for common Indian document password formats.
- [ ] **New: Add password to PDF** (AES-256 via PDFBox `StandardProtectionPolicy`), optional
  permissions (no print/copy). Useful for sending statements/invoices.
- [ ] Copy cleanup: drop "DRM-free" wording (misleading — it's password removal).

### 2.8 PDF share-in / open-with

- [ ] Shared PDF reaches every PDF tool (today only Compress gets it).
- [ ] Register `ACTION_VIEW` for `application/pdf` so the app appears in "Open with"; show a
  PDF action sheet (Split / Merge / Compress / Unlock / To Images / Organize).

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
- [ ] Add lossless WEBP (`WEBP_LOSSLESS`, API 30+); AVIF only if device supports it (low priority).

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
- [ ] Lossless strip (1B.11); show photo preview; show "No metadata found" state; batch.
- [ ] "Remove location only" vs "Remove everything"; also purge embedded EXIF thumbnail.
- [ ] Group tags by risk (location / device / date vs harmless camera settings).

**Palette Extractor** (low priority for this user)
- [ ] Show the image; copy all; use `androidx.palette` (vibrant/muted) instead of naive
  most-frequent-colour (returns background colours). Tap-to-sample eyedropper optional.
  Consider hiding behind "More tools".

### 3B. Cross-cutting UX

- [ ] **Light / Material You themes inside tools:** every tool screen uses hard-coded dark
  colours (`DarkSurfaceElevated`, `TextPrimary` #F5F5F7, `AmoledBlack` on buttons). Replace with
  `MaterialTheme.colorScheme` tokens; keep category accents. Home header also hard-codes
  `TextPrimary`.
- [ ] **File card thumbnails** (images, PDF first page, video frame) — Coil is already a
  dependency and unused.
- [ ] **Result card upgrade:** preview output, Open, rename before save, "Save As…" (pick
  folder via `CreateDocument`), "Show in folder", Share all for multi-file.
- [ ] **"Continue with…"** chaining: Split→Compress, Photos→PDF→Compress, Shrink→Share.
- [ ] **Real progress + Cancel** everywhere (fake fixed 0.3/0.5 values today).
- [ ] **Share-in to every tool:** currently only 6 routes receive the shared URI. Add
  `ACTION_SEND_MULTIPLE` (images → Photos→PDF / batch shrink; PDFs → Merge). Shared-file banner
  should show a thumbnail and be dismissible. Handle `onNewIntent` if app already open.
- [ ] **Search keywords:** "resize", "merge", "jpg", "combine", "reduce size", "kb" find
  nothing today. Add a `keywords` list to `ToolItem`.
- [ ] **Home:** quick actions are hard-coded (Unlock, Trim, Shrink Photo, Shrink Video) —
  let user pin tools; add Recent files / recent tools.
- [ ] **Copy cleanup:** "Modify OG" → "Replace original"; shorten long tool titles.
- [ ] **Naming — DECIDED: "Digital Pocket Knife".** Replace user-visible "OmniForge" everywhere:
  toasts/messages (most handled by 1C.4), unlock-screen text ("OmniForge will decrypt…"),
  README, `rootProject.name`, proguard comment. Rename `OmniForgeApp` → `PocketKnifeApp` and
  `OmniForgeTheme` → `PocketKnifeTheme` (code only). Save folder stays
  `Downloads/DigitalPocketKnife`. Name the release APK `DigitalPocketKnife-<version>.apk`
  instead of "UTilities Alpha 1".
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
- [ ] **Burn Subtitles:** path escaping does `replace(":")` then `replace("\\","/")`, which breaks
  its own escape (`VideoEngine.kt:108`); `.vtt` copied as `subtitles.srt`; needs libass-enabled
  build. Add preview of first subtitle line.
- [ ] **Video Convert:** home says MOV is supported; it isn't. Becomes "Convert to MP4" (1A.1).
- [ ] **Shrink:** add "target size (e.g. 16 MB for WhatsApp, 25 MB for email)" mode; show
  input resolution/duration.
- [ ] Real progress from encoder callbacks (Media3 `getProgress` / FFmpeg statistics).
- [ ] Media3 only (no FFmpeg). Transformer copies streams when no effects apply (trim, mute,
  remux, AAC rip). It re-encodes on hardware only when needed.
- [ ] Convert: show input codec/resolution (MediaExtractor/MediaMetadataRetriever); say
  "instant, no quality loss" when it will only remux.
- [ ] Audio Extract: AAC → M4A without re-encoding; optional start/end range.
- [ ] Shrink: optional HEVC (smaller) with compatibility warning; keep-original-audio switch.
- [ ] Captions: preview of subtitle on a frame, position (top/bottom), sync offset ±s,
  (soft-subtitle mux dropped: needs FFmpeg/MKV). Low priority for this user.

### 3D. Quick utilities

- [ ] **QR Studio:** QR generated on main thread on every keystroke with a 262k `setPixel`
  loop → typing lag. Debounce + background + `setPixels`. Description promises Wi-Fi but there
  is no Wi-Fi form (SSID/password/security). Add templates: Wi-Fi, **UPI payment QR**
  (very useful in India), vCard, URL. (Save PNG / Share already exist.) Accept `text/plain`
  share-in.
- [ ] **QR scanning** (camera via CameraX + ZXing, and "scan from screenshot/gallery") with
  actions: open URL, copy, connect to Wi-Fi, add contact. Styling/SVG export: skip unless asked.
- [ ] **Hash Verifier:** reads the file twice (MD5 then SHA-256) — compute both in one pass;
  home description claims SHA-1 (not offered — add SHA-1/SHA-512); progress for big files;
  text-hash mode; compare two files; detect a hash in clipboard and auto-compare; show hash in
  4-char groups with mismatches highlighted.
- [ ] **Base64:** text only; `DEFAULT` decode rejects URL-safe input; decoding binary to String
  shows garbage. Add URL-safe toggle and file encode/decode.
- [ ] **Base64 (cont.):** detect file type from decoded bytes (PDF/PNG/JPEG) and offer
  preview + "Save as file"; data-URL prefix toggle.
- [ ] **JSON:** `org.json` escapes `/` as `\/` and loses big-number precision — minor; show
  error line/column. Syntax colours / collapsible tree: low priority.

### 3F. OS integration (low priority, after core tools are solid)

- [ ] `ACTION_PROCESS_TEXT` (select text in any app → "Make QR / Format JSON / Base64 / Hash").
- [ ] `text/plain` share routing (JSON → JSON tool, URL → QR) and `*/*` → Hash Verifier.
- [ ] Quick Settings tile for "Scan QR" / "Shrink last photo" (optional).
- [ ] Drag-and-drop onto file cards (tablets / split-screen).

### 3E. Build, release, code health

- [ ] `isMinifyEnabled = false` and `shrinkResources` off for release; ABI filters include
  x86/x86_64 → bigger APK. Enable R8 + resource shrinking, use ABI splits / App Bundle.
- [ ] `window.statusBarColor` / `navigationBarColor` are no-ops on Android 15 with
  edge-to-edge (targetSdk 35) — rely on `enableEdgeToEdge` + insets.
- [ ] Remove `ffmpeg-kit` + `smart-exception` deps and the JitPack repo (1A.1). Drop
  `pickFirsts libc++_shared.so` if nothing else needs it. Add `media3-exoplayer`/`ui` only if
  video previews get built (3C).
- [ ] With FFmpeg gone, re-check APK size; likely possible to drop x86/x86_64 from `abiFilters`
  entirely (only emulators use them).
- [ ] Move screen state + logic into ViewModels (also fixes 1A.8, process death).
- [ ] **No tests exist.** Add unit tests for: page range parsing/validation, split chunking,
  target-size search, output naming, orientation handling.
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

- Build: `./gradlew assembleDebug` clean.
- Phase 1: test on API 29 + API 35 emulators and a real phone: 10-page and 150-page PDFs,
  encrypted PDF, 100 MB scanned PDF, 108 MP portrait photo, PNG with transparency, every video
  tool once, deep link `adb shell am start -d dpk://nonsense`.
- Phase 2: split/merge/organize round-trips, page counts match, output opens in another viewer.
- Phase 3: light theme pass on every screen, share-in from Gallery/Files/WhatsApp.
