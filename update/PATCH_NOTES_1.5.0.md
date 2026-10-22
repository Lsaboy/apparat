# Apparat 1.5.0

- Version code: 10
- Version name: 1.5.0
- File: `Apparat-1.5.0.aab`
- SHA-256: `f050b0f4aa7328410bbed2fce2ae1cbb8224171f5c5de30aa5518ab6d9fe34f0`
- Package: `com.omniforge.apparat`
- Signed with the release key (CN=Vedant Sharma, OU=Apparat).

## Play Console release notes (paste into "What's new", under 500 characters)

```
<en-US>
• Open AVIF and HEIC photos, and save as HEIC on phones that support it
• Smaller videos with HEVC and AV1 output on phones that support them
• Updated video engine with many codec fixes
• Smoother image previews
• General stability fixes and library updates
</en-US>
```

## Full notes

### New
- **AVIF and HEIC photos:** Convert and Shrink can open AVIF and HEIC photos. On phones whose hardware supports it, you can also save as HEIC.
- **HEVC and AV1 video:** Video Convert and Shrink can now output HEVC (H.265). They can also output AV1 on phones with an AV1 encoder. You only see these options if your phone can encode them.
- **Photos to PDF** is now listed under Images.

### Improved
- **Video engine:** updated from Media3 1.4.1 to 1.11.1, which brings seven releases of codec and Transformer fixes. Trim, mute, compress, convert, GIF, subtitles and audio extract all run on the new engine.
- **Image previews:** the image library moved to Coil 3.
- **ARM-only bundle:** the bundle now contains only ARM code (`arm64-v8a` and `armeabi-v7a`), which every real Android phone uses. The x86 code was only needed for emulators. Play already sends each phone only its own code, so download size stays about the same.
- **QR codes and EXIF stripping:** ZXing is updated to 3.5.4 and ExifInterface to 1.4.2.

### Under the hood
- **Build tools:** Kotlin 2.4.21, Gradle 9.8.2 and AGP 9.4.1.
- **Libraries:** the AndroidX libraries and Compose BOM 2026.09.00 are updated.
- **Target:** compileSdk 37, with the target still Android 16 (API 36). App behaviour doesn't change.
- **Fix:** muting a video and extracting audio to M4A no longer add an empty track to the output. This bug came in with the new video engine and was fixed before this release.

### Before promoting from closed testing
Test these on a phone:
- **Video:**
  - Extract audio to M4A and to WAV. The M4A should have no video track.
  - Mute a clip.
  - Trim a clip.
  - Compress at every preset.
  - Convert a video to GIF.
  - Burn in subtitles.
  - Cancel an export halfway through.
  - Rotate the phone during an export.
- **Images:**
  - Check the output previews.
  - Try the palette extractor on a large photo.
  - Strip EXIF from a photo that has metadata.
  - Check the document scan preview.
- **PDF:** merge, split, compress and password-protect a PDF.
- **QR:** generate a code and scan one.

### Known
- Play will still warn about native debug symbols. This is harmless: the native code comes from prebuilt libraries, not from Apparat.
