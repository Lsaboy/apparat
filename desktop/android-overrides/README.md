# Desktop-only patches to Android files

A file here replaces the Android file at the same path **in the desktop build only**
(`app/src/main/java/<path>` is left untouched, so the Android app is never affected).

Use this sparingly, for a fix the desktop needs before the Android app takes it. Each override is
pinned in `overrides.lock` to the Android file it was made from (SHA-256). `./gradlew -p desktop
checkAndroidOverrides` (also run by `test` and on CI) reports when:

- the Android original changed since the override was made → re-apply your desktop change on top
  of the new Android file, then update its hash in `overrides.lock`;
- the Android file now equals the override (Android took the fix) → delete the override and its line.

Current overrides:

| File | Why | Android follow-up |
|---|---|---|
| `feature/utilities/QrStudioScreen.kt` | Don't show a cancelled QR generation as "Failed to generate QR code" | Branch `fix/qr-cancelled-generation` (one line); delete this override once it's merged |
