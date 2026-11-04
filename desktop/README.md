# Apparat for Linux and Windows

> How this fits with the Android app (branching, keeping them in sync, desktop-only features):
> see [STRATEGY.md](STRATEGY.md). The Android app comes first; nothing here changes its build.

The desktop app has the same 21 tools, screens and Braun look as the Android app. Its screens
are the Android app's own Compose code, compiled for desktop with Compose Multiplatform.

## Install

| System | File | Notes |
|---|---|---|
| Ubuntu / Debian / Mint / Pop!_OS | `apparat_1.5.0-1_amd64.deb` | `sudo apt install ./apparat_1.5.0-1_amd64.deb` (or double-click). Starts from the app menu as **Apparat**. |
| Any Linux with Flatpak | `Apparat.flatpak` | `flatpak install --user Apparat.flatpak`, then `flatpak run com.omniforge.Apparat`. Needs the Flathub remote for the Freedesktop runtime. |
| Windows 10 / 11 | `Apparat-1.5.0.msi` or `Apparat-1.5.0.exe` | One-click installer for the current user (no admin prompt). Adds Start-menu and desktop shortcuts. |

After installing, `Apparat --self-test` checks that everything works on that computer (FFmpeg,
video, audio, GIF, image and PDF engines) without opening a window:
`/opt/apparat/bin/Apparat --self-test`, `flatpak run com.omniforge.Apparat --self-test`, or
`"%LOCALAPPDATA%\Apparat\Apparat.exe" --self-test` on Windows (in a terminal).

Nothing else needs to be installed. Every installer carries a private Java runtime (trimmed to
the modules the app uses) and FFmpeg. The private runtime is only used by Apparat: it doesn't
touch or replace a Java you already have, and an installed Java isn't needed or used.

## Build

Requires JDK 17 or newer. From the repository root:

```bash
./gradlew -p desktop run                 # start the app
./gradlew -p desktop test                # engine + shared tests
./gradlew -p desktop packageDeb          # Linux:   desktop/app/build/compose/binaries/main/deb/
./gradlew -p desktop packageMsi packageExe   # Windows: .../main/msi/ and .../main/exe/
desktop/packaging/flatpak/build-flatpak.sh   # Linux:   desktop/build/Apparat.flatpak
```

Installers are built on the system they're for (a .deb on Linux, .msi/.exe on Windows). The
**Desktop Release** GitHub workflow builds all of them on version tags (`v*`) or when run by hand.
The Windows installers use WiX, which the Compose Gradle plugin downloads by itself.

The desktop build is separate from the Android build: `./gradlew` on its own still builds only
the Android app, so Android Studio and the Play Store workflow are unaffected.

## Tests

`./gradlew -p desktop test` runs:

- **Engine tests** (`desktop/engine/src/test`): every PDF, image and video function against real
  files made on the fly (password PDFs, rotated phone videos, WEBM, EXIF-tagged JPEGs…), plus the
  Android app's own engine/utility unit tests, run against the desktop versions.
- **End-to-end UI tests** (`desktop/app/src/test/.../EndToEndUiTest.kt`): open the real screens,
  feed a file in, click the tool's button, wait for the job and save the result
  (Compress PDF, Video Shrinker, Shrink Photo, JSON, QR, drop two PDFs → Merge).
- The Android app's screen-logic tests (split ranges, output names, search, theme contrast).

`./gradlew -p desktop :app:screenshots` renders every screen offscreen into
`desktop/app/build/screenshots/`.

The **Desktop CI** workflow runs all of that on Linux, builds and installs the .deb and the
.flatpak, starts both under a virtual display, and pushes the screenshots to the `ci-screenshots`
and `ci-flatpak` branches (throwaway branches, safe to delete; they are force-pushed on every run).

## How it's put together

```
desktop/
├── engine/      pure JVM, no UI (builds and tests on its own)
│   ├── android/…            small desktop versions of the Android APIs the shared code calls
│   │                        (Uri = a file, Context, Bitmap, Toast, SharedPreferences, ClipboardManager,
│   │                        PdfRenderer, MediaMetadataRetriever, Base64…)
│   ├── engine/pdf           PdfEngine on Apache PDFBox 3 (same functions as Android)
│   ├── engine/image         BitmapDecoder + ImageEngine on Skia; lossless JPEG EXIF handling
│   ├── engine/video         VideoEngine on the bundled FFmpeg (libx264, AAC…)
│   └── core/util            FileUtils / CodecSupport for desktop
├── app/         Compose Desktop window
│   ├── androidx/…, coil3/…  desktop versions of the Android-only UI APIs (file pickers via
│   │                        rememberLauncherForActivityResult, BackHandler, AsyncImage…)
│   ├── core/…               AppSettings, OutputSaver, ToolJobService for desktop
│   └── desktop/             main(), navigation (the desktop MainActivity), dialogs, notifications
└── packaging/   icons (made from the Android launcher vectors) and the Flatpak manifest
```

**Shared code, edited once.** These Android files are compiled into the desktop app unchanged
(the build copies them into `build/generated/androidShared`; edit the originals under
`app/src/main/java`):

- every screen in `feature/**`, plus `core/ui/**`, `core/theme/**`, `core/model/**` and `core/jobs/ToolJobs.kt`
- `engine/common/UtilityEngine.kt`, `JsonFormatter.kt`, `engine/video/GifEncoder.kt`,
  `engine/image/TargetKbSearchEngine.kt`, `core/jobs/ExclusiveRunner.kt`

The Android unit tests for those files also run in the desktop build.

Files with a desktop version (same functions, different insides) are the engines (`PdfEngine`,
`ImageEngine`, `BitmapDecoder`, `VideoEngine`, `VideoCodecs`, `Subtitles`), `FileUtils`,
`OutputSaver`, `AppSettings`, `CodecSupport`, `ToolJobService` and `MainActivity`
(`desktop/app/.../desktop/ApparatDesktopApp.kt`). When you change one of these on Android in a
way the screens see (a new parameter, a new function), make the same change in the desktop version;
the desktop build fails to compile until you do, so nothing slips through.

If a new screen uses an Android API that has no desktop version yet, the desktop build fails
with "Unresolved reference". Add a small stand-in under `desktop/engine/src/main/kotlin/android/…`
(or `desktop/app/src/main/kotlin/androidx/…` for UI APIs).

## Desktop-only tools

Desktop-only tools: **Batch Processor** (one operation on many files, into a folder; drop a
folder on the window to start one), **Watch Folder** (files saved into a folder get a batch
operation automatically), **Audio Converter** (MP3/M4A/Opus/FLAC/WAV, bitrate, loudness
levelling), **Rotate, Flip & Speed**, **Page Tools: Reorder, Rotate & Number** (PDF page grid),
**Join Videos**, **Crop Video**, **Resolution & Frame Rate** and **Frames & Contact Sheet**.
Batches also run from a terminal: `apparat --batch compress-pdf ~/Scans` (see `apparat --help`).
On Windows use `apparat-cli.exe`, installed next to `Apparat.exe` (per-user installs go to
`%LOCALAPPDATA%\Apparat`); `Apparat.exe` is a GUI program and prints nothing. They are listed in
`app/src/main/kotlin/com/omniforge/apparat/core/model/DesktopTools.kt`; see STRATEGY.md for how
to add more.

Shortcuts: Ctrl+O open, Ctrl+S save, Ctrl+Shift+S save as, Ctrl+F search tools, Esc or Alt+Left
back.

## Hardware video

Video tools (shrink, convert, subtitles, strip, trim re-encode, rotate/flip/speed) use the
graphics card when they can. Settings → **Hardware video encoding** shows what was found and turns
it off. `Apparat --self-test` prints the same report and which path the test encode used.

| System | GPU | API |
|---|---|---|
| Linux | AMD (Radeon), Intel | VA-API (`h264_vaapi`, `hevc_vaapi`, `av1_vaapi`) |
| Linux | NVIDIA | NVENC (+ CUDA decode) |
| Windows | NVIDIA | NVENC |
| Windows | Intel | Quick Sync (QSV) |
| Windows | AMD | D3D12 video encode, then Media Foundation. The bundled FFmpeg has no AMF |

Each encode tries, in order: everything on the GPU (decode, scale, encode; only when the sole
filter is a resize) → GPU decode + GPU encode → CPU decode + GPU encode → CPU in the chosen codec →
CPU H.264. A failed step is remembered for the session, and two failed GPU encodes in a row turn
the GPU off for that codec until restart. A broken driver costs one retry, not a failed job.
No question is asked: a short message says the CPU took over (once per kind of failure per
session), and Settings → Hardware video shows how often it happened.

Known pitfalls and what the app does about them:

- **Missing VA-API driver (Linux, AMD/Intel).** Needs `mesa-va-drivers` (Pop!_OS/Ubuntu: usually
  installed; Fedora: `mesa-va-drivers-freeworld` from RPM Fusion, the stock one has H.264/HEVC
  encoding removed). Intel also needs `intel-media-va-driver`. Check with `vainfo`. The report
  says which package is missing.
- **Permissions.** `/dev/dri/renderD*` must be readable: the user must be in the `render` (some
  distros: `video`) group. The report names the group.
- **Old bundled libva.** The FFmpeg build ships libva 2.14, too old for current Mesa. The app
  loads the system libva first; nothing to do.
- **Several GPUs** (laptop iGPU + dGPU): each render node is probed and the one that encodes wins.
  `APPARAT_VAAPI_DEVICE=/dev/dri/renderD129` forces one.
- **NVIDIA on Linux:** `nvidia-vaapi-driver` only decodes, so NVIDIA cards use NVENC directly.
  NVENC in this FFmpeg needs driver 570 or newer.
- **AV1 encoding** needs a recent GPU (AMD RX 7000 / RDNA3+, Intel Arc, NVIDIA RTX 40+). Older
  cards fall back to SVT-AV1 on the CPU.
- **Size limits.** H.264 hardware encoders stop at 4096 px; above that (and below 128 px) the
  CPU is used.
- **10-bit / HDR sources** are converted to 8-bit without tone mapping, on GPU and CPU alike.
  Colours of HDR clips may look flat.
- **Quality.** GPU encoders get a bitrate (never above the source's); at the same size they look
  slightly worse than x264. Turn the setting off for best quality per MB.
- **Rotated phone videos** on the full-GPU path: the rotation flag is restored by a stream-copy
  remux, so they play upright.
- **Flatpak** has `--device=dri`; GPU access works there too.
- `APPARAT_HWACCEL=off` disables it; `=vaapi|nvenc|qsv|d3d12va|mf` allows only that API.
- CI machines have no GPU, so CI only tests the fallback (a fake VA-API device must end in a
  correct CPU encode). The GPU paths themselves are tested by running `--self-test` on the
  actual computer.

## Desktop differences

| Android | Desktop |
|---|---|
| System file picker, share sheet, "Open with" | Native open/save dialogs; drag files onto the window; "Open with Apparat" in the file manager (.deb/Flatpak register PDF, image and video types) |
| Share button | Shows the file selected in the file manager |
| Saves to Downloads/Apparat (MediaStore) | Saves to `~/Downloads/Apparat` (`%USERPROFILE%\Downloads\Apparat` on Windows), or the folder picked in Settings |
| Media3 hardware encoding | FFmpeg: GPU encoding where it works (see "Hardware video"), otherwise x264/x265/SVT-AV1 |
| Trim & Mute re-encodes the frames before the first keyframe | Stream copy; the MP4 edit list starts playback exactly at the chosen time |
| HEIC output | Not offered (HEIC and AVIF photos can still be opened) |
| Back gesture | Esc, Alt+Left, or the back arrow |
| Launcher icon variant | Changes the window/taskbar icon |
| Background service + notification | Jobs run while the window is open; a desktop notification says when a tool you left has finished. Closing the window during a job asks first |

Settings and cache live in `~/.config/apparat` and `~/.cache/apparat` on Linux (inside
`~/.var/app/com.omniforge.Apparat/` for the Flatpak) and `%APPDATA%\Apparat` /
`%LOCALAPPDATA%\Apparat` on Windows.

## Licence note

The bundled FFmpeg is the GPL build (it includes libx264). Distributing it means the app's
installers are subject to the GPL's terms; see https://ffmpeg.org/legal.html.
