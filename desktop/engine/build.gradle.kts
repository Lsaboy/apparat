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
// Must match the Skia (skiko) version that the Compose Multiplatform release uses (1.12.2 → 0.150.2).
val skikoVersion = "0.150.2"

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    // PDF: upstream Apache PDFBox (the Android app uses its Android port, same API)
    api("org.apache.pdfbox:pdfbox:3.0.8")
    // QR codes
    api("com.google.zxing:core:3.5.4")
    // EXIF/GPS reading for every image format (JPEG, PNG, WEBP, HEIC...)
    implementation("com.drewnoakes:metadata-extractor:2.22.0")
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
    // Android wording ("This phone can't play…"); the desktop message says "computer".
    filter { excludeTestsMatching("*VideoCodecChoiceTest.unsupported message names the codec") }
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Pure-logic files used unchanged from the Android app (single source of truth). They are copied
// into build/ at compile time; edit them in app/src/main/java, not in the copy.
val androidSources = rootProject.file("../app/src/main/java")
val syncAndroidEngineSources = tasks.register<Sync>("syncAndroidEngineSources") {
    from(androidSources) {
        include(
            "com/omniforge/apparat/engine/common/UtilityEngine.kt",
            "com/omniforge/apparat/engine/common/JsonFormatter.kt",
            "com/omniforge/apparat/engine/video/GifEncoder.kt",
            "com/omniforge/apparat/engine/image/TargetKbSearchEngine.kt",
            "com/omniforge/apparat/core/jobs/ExclusiveRunner.kt"
        )
    }
    into(layout.buildDirectory.dir("generated/androidShared"))
}
sourceSets.main {
    kotlin.srcDir(syncAndroidEngineSources)
}

// The Android app's JVM unit tests for engines and utilities run against the desktop versions too.
val androidTests = rootProject.file("../app/src/test/java")
val syncAndroidEngineTests = tasks.register<Sync>("syncAndroidEngineTests") {
    from(androidTests) {
        include("com/omniforge/apparat/engine/**", "com/omniforge/apparat/core/util/**", "com/omniforge/apparat/core/jobs/**")
        // These two also test screen code; they run in the app module instead.
        exclude("**/OutputNamingTest.kt", "**/PdfSplitTest.kt")
    }
    into(layout.buildDirectory.dir("generated/androidSharedTests"))
}
sourceSets.test {
    kotlin.srcDir(syncAndroidEngineTests)
}
