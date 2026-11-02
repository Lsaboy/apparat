// Platform layer + processing engines for the desktop app. Pure JVM (no Compose), so it builds and
// tests on its own. Classes here mirror the Android app's APIs so its screens compile unchanged.
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

/** Native classifier for the OS doing the build (packages are built per OS). */
val hostOs = System.getProperty("os.name").lowercase()
val hostArch = System.getProperty("os.arch").lowercase()
val javacppPlatform = when {
    hostOs.contains("win") -> "windows-x86_64"
    hostOs.contains("mac") -> if (hostArch.contains("aarch64")) "macosx-arm64" else "macosx-x86_64"
    else -> if (hostArch.contains("aarch64")) "linux-arm64" else "linux-x86_64"
}
val skikoPlatform = when {
    hostOs.contains("win") -> "windows-x64"
    hostOs.contains("mac") -> if (hostArch.contains("aarch64")) "macos-arm64" else "macos-x64"
    else -> if (hostArch.contains("aarch64")) "linux-arm64" else "linux-x64"
}

val ffmpegVersion = "8.1.2-1.5.14"
val javacppVersion = "1.5.14"
val skikoVersion = "0.156.0"

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    // PDF: upstream Apache PDFBox (the Android app uses its Android port, same API)
    api("org.apache.pdfbox:pdfbox:3.0.8")
    // QR codes
    api("com.google.zxing:core:3.5.4")
    // Image decode/encode/resize (JPEG, PNG, WEBP) through Skia, which Compose Desktop ships anyway
    api("org.jetbrains.skiko:skiko-awt:$skikoVersion")
    runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-$skikoPlatform:$skikoVersion")
    // FFmpeg (GPL build: libx264) as a bundled executable, used for all video/audio tools
    implementation("org.bytedeco:javacpp:$javacppVersion")
    implementation("org.bytedeco:ffmpeg:$ffmpegVersion")
    implementation("org.bytedeco:javacpp:$javacppVersion:$javacppPlatform")
    implementation("org.bytedeco:ffmpeg:$ffmpegVersion:$javacppPlatform-gpl")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
