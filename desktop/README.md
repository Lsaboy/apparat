# Apparat for Linux and Windows

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

## Desktop differences

| Android | Desktop |
|---|---|
| System file picker, share sheet, "Open with" | Native open/save dialogs; drag files onto the window; "Open with Apparat" in the file manager (.deb/Flatpak register PDF, image and video types) |
| Share button | Shows the file selected in the file manager |
| Saves to Downloads/Apparat (MediaStore) | Saves to `~/Downloads/Apparat` (`%USERPROFILE%\Downloads\Apparat` on Windows), or the folder picked in Settings |
| Media3 hardware encoding | FFmpeg software encoding (libx264). HEVC/AV1 options appear only if the bundled FFmpeg has those encoders |
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
