# Proguard rules for Apparat
-keep class com.tom_roush.pdfbox.** { *; }

# Preserve ExifInterface TAG_* fields for reflection in ImageEngine
-keepclassmembers class androidx.exifinterface.media.ExifInterface {
    public static final java.lang.String TAG_*;
}

# PDFBox references an optional JPEG 2000 codec that isn't bundled; JPX images in a PDF
# then fail to decode and are skipped (compressPdf keeps them as they are).
-dontwarn com.gemalto.jp2.**
