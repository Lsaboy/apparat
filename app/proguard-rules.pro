# Proguard rules for Apparat
-keep class com.tom_roush.pdfbox.** { *; }

# Preserve ExifInterface TAG_* fields for reflection in ImageEngine
-keepclassmembers class androidx.exifinterface.media.ExifInterface {
    public static final java.lang.String TAG_*;
}
