# Android first: how the desktop app lives next to it

## The rule

**The Android app is the product. The desktop app follows it and never pushes back on it.**

- The dependency goes one way: the desktop build *reads* Android source files (screens, theme,
  pure logic). The Android build never reads anything from `desktop/`.
- Nothing under `desktop/` can change the Play build. It is a separate Gradle build
  (`./gradlew -p desktop …`). The root `settings.gradle.kts`, `app/`, the Play workflow and
  `build-release.sh` know nothing about it.
- The `desktop-port` branch changes **no file outside `desktop/`** except two new workflow
  files (`desktop-ci.yml`, `desktop-release.yml`). `git diff master -- app/` is empty.
- When an Android change breaks the desktop build, the fix goes in `desktop/`. Never revert or
  hold back an Android change for the desktop's sake.
- Desktop CI is informational. It must never be a required check for Android work.

## Branch or folder?

| | Separate branch (`desktop-port`) | `desktop/` folder on `master` |
|---|---|---|
| Risk to Android | None | None (separate build, separate workflows) |
| Android fixes reach desktop | When you merge `master` into the branch | Immediately |
| Desktop breakage noticed | At merge time, in a batch | On the commit that caused it (desktop CI, not blocking) |
| Day-to-day cost | Remember to merge; two branches to think about | One branch; desktop CI runs on Android commits too |
| Merge conflicts | Practically none: the branch only adds files | n/a |

**Recommendation:**

1. **Now, while the Play release is in testing:** keep the desktop on `desktop-port`. Don't merge
   it. Android work continues on `master` exactly as before.
2. **Regularly** (after each Android release, or weekly): `git checkout desktop-port && git merge
   master`, then let Desktop CI run. Merges are conflict-free because the branch only adds
   files. If CI goes red, fix it inside `desktop/` (see "When the desktop build breaks").
3. **When the Android release is stable:** merge `desktop-port` into `master`. It adds the
   `desktop/` folder and two workflows, and touches nothing else. From then on there's one branch,
   and desktop CI tells you early (without blocking) when an Android change needs a desktop
   follow-up.

Step 3 is optional. Staying on a branch forever works too; the cost is only the periodic merge.

## When the desktop build breaks after an Android change

The desktop compiles the Android screens, so the failure is a compile error that names the problem:

| Error | Cause | Fix (all inside `desktop/`) |
|---|---|---|
| `Unresolved reference` to an `android.*` / `androidx.activity.*` API | A screen started using a new Android API | Add a small stand-in under `desktop/engine/src/main/kotlin/android/…` or `desktop/app/src/main/kotlin/androidx/…` |
| A function in `PdfEngine`, `ImageEngine`, `VideoEngine`, `FileUtils`, `OutputSaver`, `AppSettings`… has new parameters | The Android engine API changed | Make the same change in its desktop version |
| `ToolModels.kt no longer declares 'val ALL_TOOLS = listOf('` | The tool list was restructured | Update the rename in `desktop/app/build.gradle.kts` |
| `checkAndroidOverrides` warning | An Android file the desktop patches has changed | See `android-overrides/README.md` |

If you need a desktop release while the build is broken, build it from the last green commit
or tag. Desktop releases don't have to track every Android commit.

**Desktop-only patches to Android files:** `desktop/android-overrides/<path>` replaces that
Android file in the desktop build only. Each one is pinned to the exact Android file it was made
from (`overrides.lock`). The build warns when that Android file changes, or when Android has
caught up and the override can be deleted. Keep these rare; today there is one (the QR fix
below).

## Where the platforms deliberately differ

| | Android | Desktop |
|---|---|---|
| Video engine | Media3 Transformer: hardware codecs, small APK, no GPL | Bundled FFmpeg (GPL build: x264/x265/SVT-AV1, MP3, Opus, FLAC…) |
| PDF | PDFBox-Android | Apache PDFBox 3 |
| Images | Android Bitmap/ImageDecoder, HEIC output | Skia; HEIC opened via FFmpeg, not written |
| Extra tools | — | Desktop-only tools (below) |
| Sharing, launcher shortcuts, icon variants | Yes | Drag-and-drop, "Open with", file-manager reveal |

**Don't add FFmpeg to the Android app.** It would add ~30-60 MB per ABI and GPL obligations,
and software encoding drains phone batteries. Media3 uses the phone's hardware encoders, which is
the right tool there.

### Desktop-only tools

- Listed in `desktop/app/src/main/kotlin/com/omniforge/apparat/core/model/DesktopTools.kt`
  (`DESKTOP_ONLY_TOOLS`). The desktop build renames Android's `ALL_TOOLS` to `ANDROID_TOOLS` in
  its copy and defines `ALL_TOOLS = ANDROID_TOOLS + DESKTOP_ONLY_TOOLS`. Home, search and
  categories pick them up with no Android change.
- Screens live in `desktop/app/src/main/kotlin/.../desktop/feature/`, and engines in
  `desktop/engine/src/main/kotlin/.../desktop/engine/`. Register the route in
  `ApparatDesktopApp.kt`.
- Screens are written against the same APIs as Android screens (shared components,
  `ToolJobs`, `retained`, the activity-result pickers). If you ever want one on Android, the
  screen moves over almost unchanged and only its engine needs an Android version.

Done so far:
- **Batch Processor**: many PDFs/photos/videos/audio files, one operation (compress PDFs,
  shrink photos, photos under N KB, convert photos, strip metadata, shrink/convert videos,
  convert audio), results written straight into a folder. Dropping a folder on the window opens
  it. Also on the command line: `apparat --batch compress-pdf ~/Scans` (`apparat --help`).
- **Audio Converter**: MP3/M4A/Opus/FLAC/WAV, bitrate, loudness levelling.
- **Rotate, Flip & Speed**: lossless rotation, mirror/flip, 0.25×-4× with pitch kept.

### More desktop ideas, most valuable first

1. ~~Batch mode~~ and ~~command line~~: done (see above). Next step for batch: more operations
   (split every PDF, PDF→images, merge per folder) and per-operation quality settings.
2. **Windows console.** The Windows launcher is a GUI program, so `--batch`/`--self-test` print
   nothing in a terminal there. The self-test also writes and opens a report file. A separate
   `apparat-cli.exe` console launcher would fix this properly.
3. **Hardware video encoding.** The bundled FFmpeg includes NVENC (NVIDIA), QSV (Intel) and
   VAAPI. Try them and fall back to x264. That makes 4K shrinking several times faster.
4. **PDF page tools with thumbnails.** Reorder, rotate or delete pages, add page numbers or a
   watermark. A big screen makes page grids practical.
5. **Video:** join clips, crop, extract frames / contact sheet, change resolution or frame rate.
6. **Watch folder.** Anything saved into a folder gets processed automatically (for example,
   scans become compressed PDFs).
7. **Keyboard shortcuts** (Ctrl+O open, Ctrl+S save, Ctrl+F search on Home).

## Two Android findings (no Android files changed)

1. **QR Studio could show "Failed to generate QR code: The coroutine scope left the
   composition"** when the text changes while a code is being generated, because the generation
   treats its own cancellation as an error. The one-line fix is on branch
   **`fix/qr-cancelled-generation`** (based on `master`). Merge it whenever suits the release;
   then delete `desktop/android-overrides/.../QrStudioScreen.kt` and its line in `overrides.lock`.
2. **Sharing several PDFs or photos into the app may lose them.** `MainActivity` clears the shared
   payload in a `LaunchedEffect` while Merge PDFs / Photos to PDF are still importing it. Clearing
   it changes `shareToken`, which restarts their `LaunchedEffect(shareToken)` and cancels the
   import halfway. The desktop app had the identical bug and was fixed in its own navigation by
   keeping the payload until the user leaves the screen. Worth a test on a phone: share 3 PDFs
   from Files to Apparat and check that all 3 appear in Merge PDFs.

## Branches made in this session

| Branch | What | Action |
|---|---|---|
| `desktop-port` | The desktop app: `desktop/` + `desktop-ci.yml` + `desktop-release.yml`. Touches no Android file | Keep as the desktop branch; merge `master` into it regularly |
| `fix/qr-cancelled-generation` | One-line Android QR fix, based on `master` | Merge into `master` when it suits the Play release (verified: Android unit tests + debug build pass with it) |
| `ci/android-checks` | `android-ci.yml`: Android unit tests + debug APK on every push/PR. Doesn't sign or upload | Recommended for `master`: catches a broken build before it reaches Play. Passes on today's `master` |
| `verify/qr-fix-android-build` | The two branches above combined, used once to prove the fix builds on Android | Delete |
| `ci-screenshots`, `ci-flatpak` | Written by desktop CI (screenshots, self-test output) | Delete any time; recreated on each run |

## Versions and releases

- Desktop version: `appVersion` in `desktop/app/build.gradle.kts` (plus `APP_VERSION` in
  `Main.kt` and the Flatpak metainfo). It is independent of the Android `versionName`;
  matching numbers are nice but not required.
- Android releases: unchanged (`v*` tags → Play workflow).
- Desktop releases: run **Desktop Release** by hand from the Actions tab (or on a `v*` tag once
  merged). It builds the .deb, the .flatpak, and the Windows .msi/.exe.
