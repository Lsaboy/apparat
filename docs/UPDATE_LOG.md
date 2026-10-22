# Apparat — Update Log

Planned and completed maintenance work, newest first. Feature work lives in
[PROJECT_GUIDE.md §7](PROJECT_GUIDE.md#7-roadmap--what-to-build-next-and-how); this log is for
upkeep (dependencies, toolchain, platform changes).

---

## 2026-10-22 — Dependency update (focus #1)

**Status:** in progress (Step 0 done). This is the first thing to work on, before any §7 roadmap item.

**Why:** the build is pinned to late-2024 versions (AGP 8.7.2, Kotlin 2.0.21, Compose BOM
2024.10.00, Media3 1.4.1, Coil 2.7.0). Media3 has had ~7 minor releases of codec and Transformer
fixes since then, and AGP 9 changes how Kotlin is configured. Updating now, while the app is
pre-alpha with no users, is cheap. Later it gets harder.

**Ground rules**
- Do one step per commit, and keep the app building and green after each one. Don't bundle steps.
- After every step, run all of these:
  - `./gradlew :app:testDebugUnitTest` (all 99 tests pass)
  - `./gradlew assemblePreview` (R8 build, which catches missing keep rules)
  - Install the preview build on the phone and smoke-test the tools the step touches.
- Release flow: `./build-release.sh` → closed testing → promote only after a day of use.
- Version numbers below were current on 2026-10-22. Confirm them on the release pages when
  implementing, and pick the latest **stable** (no alpha/beta/rc).

### Checklist

- [x] **Step 0: Version catalog (no version changes)**
- [ ] **Step 1: AndroidX libraries on the current toolchain**
- [x] **Step 2: Toolchain: Kotlin 2.4.21, Gradle 9.8.2, AGP 9.4.1** (done before Step 1; Step 1 is deferred until after it)
- [ ] **Step 3: Media3 1.4.1 → 1.11.x**
- [ ] **Step 4: Coil 2.7.0 → 3.x**
- [ ] **Step 5: Remaining libraries + R8 rules review**
- [ ] *(Optional)* Drop x86 / x86_64 from `abiFilters`

### Step 0: Version catalog

Create `gradle/libs.versions.toml` and move every version and plugin from the root
`build.gradle.kts` and `app/build.gradle.kts` into it. Use `libs.*` aliases in the build files.

- Keep every version exactly as it is. This step only moves them.
- Done when the build output is identical, the tests pass, and later steps change versions in
  one file.

### Step 1: AndroidX libraries (current toolchain)

These are low-risk bumps that don't need a new AGP/Kotlin.

| Library | Now | Target (2026-10-22) |
|---|---|---|
| core-ktx | 1.13.1 | 1.19.x |
| activity-compose | 1.9.3 | 1.13.x |
| lifecycle-runtime-ktx / viewmodel-compose | 2.8.6 | latest stable |
| navigation-compose | 2.8.3 | 2.10.x |
| kotlinx-coroutines-android | 1.9.0 | latest stable |
| desugar_jdk_libs | 2.1.2 | latest stable |
| Compose BOM | 2024.10.00 | 2026.08.00 (Compose 1.12) |

- Newer AndroidX releases may require a higher compileSdk, Kotlin or AGP version. If one does
  (Gradle will say so), leave that library and do it after Step 2. The Compose BOM is the most
  likely one to hit this.
- Watch for Material 3 deprecations, such as `Divider` → `HorizontalDivider`, `menuAnchor()`
  overloads, and pull-to-refresh/segmented-button API changes. Fix the warnings instead of
  suppressing them.
- **Smoke test:** go through every screen once and check the theme, the bottom sheets, the back
  navigation, and the share/save flows.

### Step 2: Toolchain

This is the biggest step, so give it its own commit (or two).

1. **Kotlin 2.0.21 → 2.4.0.** Bump `org.jetbrains.kotlin.plugin.compose` to the same version.
2. **Gradle wrapper 8.11.1 → 9.6.0 or newer.** AGP 9.4 requires Gradle 9.6 or newer.
   - Run `./gradlew wrapper --gradle-version <x>`.
3. **AGP 8.7.2 → 9.4.x.** AGP 9 builds Kotlin in:
   - Remove the `org.jetbrains.kotlin.android` plugin from both build files.
   - Replace `android { kotlinOptions { jvmTarget = "17" } }` with a top-level
     `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }`, or drop it if the AGP
     default already matches.
   - Remove any `android.builtInKotlin=false` / `android.newDsl=false` from `gradle.properties`
     if something adds them.
   - Re-check the custom `preview` build type and the signing config in `build-release.sh`.
     The DSL types changed in AGP 9.
4. **compileSdk / targetSdk 37** is a separate decision. AGP 9.4 supports API 37, but raising
   targetSdk changes runtime behaviour. Keep both at 36 in this step and raise them later as
   their own change after reading the API 37 behaviour changes.

- Done when a clean build has no deprecation warnings from the build scripts, and the tests and
  preview build pass.

### Step 3: Media3 1.4.1 → 1.11.x

This is the riskiest library, because video is where codec bugs show up.

- **Files:**
  - `engine/video/VideoEngine.kt`
  - `engine/video/VideoCodecs.kt`
  - `core/util/CodecSupport.kt`
- Bump `media3-transformer`, `media3-effect` and `media3-common` together. They must match.
- Expect deprecations or renames in the `Transformer.Builder`, `EditedMediaItem`, `Composition`
  and `ExportResult` APIs. Keep the `@OptIn(UnstableApi::class)` annotations.
- Re-check the H.264 / AAC encoder selection (`MimeTypes.VIDEO_H264`) and the WAV decode path
  (MediaCodec → PCM).
- **Phone test (S26), each tool with one short and one long (>2 min) clip:**
  - Video compress at every preset.
  - Trim.
  - Video → GIF.
  - Audio extract to M4A and to WAV.
  - Cancel halfway through. The temp files must be cleaned up and there must be no crash.
  - Rotate the phone during an export.

### Step 4: Coil 2.7.0 → 3.x

- Dependency: `io.coil-kt:coil-compose:2.7.0` → `io.coil-kt.coil3:coil-compose:3.5.x`.
  - Don't add `coil-network-*`. The app is offline, and Coil 3 doesn't load network images
    unless that artifact is added.
- Imports change from `coil.*` to `coil3.*` in these 4 files:
  - `core/ui/CommonComponents.kt`
  - `feature/image/PaletteExtractorScreen.kt`
  - `feature/image/ExifStripperScreen.kt`
  - `feature/image/DocumentScanScreen.kt`
- Watch for these API changes:
  - `ImageRequest.Builder` options moved.
  - `allowHardware(false)` is still required wherever the bitmap is read (palette extraction).
- **Smoke test:** check the thumbnails, the palette extractor on a large photo, and the EXIF
  stripper preview.

### Step 5: Remaining libraries + R8 review

- **PdfBox-Android:** 2.0.27.0 is still the latest release, so there is nothing to bump. Re-check
  that the `proguard-rules.pro` PdfBox keeps are still needed after the AGP 9 / R8 upgrade. Test
  this with a preview build: merge, split, compress, and password-protect a PDF.
- **ZXing core 3.5.3 and ExifInterface 1.3.7:** bump to the latest stable if one exists. Test
  QR generate and scan, and the EXIF strip.

### Optional: trim ABIs

`abiFilters` currently ships `arm64-v8a`, `armeabi-v7a`, `x86_64` and `x86`. Play delivers per-ABI
splits from the bundle, so this only affects bundle size and emulator support. Dropping x86 and
x86_64 for release only is fine, but keep them for debug so the emulator still works.

### Not in scope

- SharedPreferences → DataStore. The current prefs work, so it stays optional.
- `debugSymbolLevel` for the Play native-symbols warning. Do it only if it is asked for.
- Any feature work from §7.
