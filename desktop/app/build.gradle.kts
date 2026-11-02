import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(21)
}

val appVersion = "1.5.0"

// ---------------------------------------------------------------------------------------------
// Shared UI: the Android app's screens, theme and components compile here unchanged. They are
// copied into build/ at compile time, so always edit them under app/src/main/java.
// Desktop replacements for the few Android-only files live in src/main/kotlin.
// ---------------------------------------------------------------------------------------------
val androidSources = rootProject.file("../app/src/main/java")
val syncAndroidUiSources = tasks.register<Sync>("syncAndroidUiSources") {
    from(androidSources) {
        include(
            "com/omniforge/apparat/feature/**",
            "com/omniforge/apparat/core/model/**",
            "com/omniforge/apparat/core/theme/**",
            "com/omniforge/apparat/core/ui/**",
            "com/omniforge/apparat/core/jobs/ToolJobs.kt"
        )
    }
    into(layout.buildDirectory.dir("generated/androidShared"))
}

val androidTests = rootProject.file("../app/src/test/java")
val syncAndroidUiTests = tasks.register<Sync>("syncAndroidUiTests") {
    from(androidTests) {
        include(
            "com/omniforge/apparat/feature/**",
            "com/omniforge/apparat/core/theme/**",
            "com/omniforge/apparat/core/settings/**",
            "com/omniforge/apparat/core/util/OutputNamingTest.kt",
            "com/omniforge/apparat/engine/pdf/PdfSplitTest.kt"
        )
    }
    into(layout.buildDirectory.dir("generated/androidSharedTests"))
}

sourceSets {
    main { kotlin.srcDir(syncAndroidUiSources) }
    test { kotlin.srcDir(syncAndroidUiTests) }
}

dependencies {
    implementation(project(":engine"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test { useJUnit() }

compose.desktop {
    application {
        mainClass = "com.omniforge.apparat.desktop.MainKt"
        jvmArgs += listOf(
            "-Dfile.encoding=UTF-8", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=60",
            // Lets the app set its X11 window class on Linux (ignored elsewhere).
            "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED"
        )

        nativeDistributions {
            // .deb for Debian/Ubuntu, .msi and .exe installers for Windows. Each is built on its own
            // OS (./gradlew -p desktop packageDeb on Linux, packageMsi/packageExe on Windows). The
            // Flatpak is built from packaging/flatpak. A private, trimmed Java runtime is bundled:
            // nothing else needs to be installed.
            targetFormats(TargetFormat.Deb, TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Apparat"
            packageVersion = appVersion
            description = "Offline PDF, video, image and utility tools"
            vendor = "Omniforge"
            copyright = "© 2026 Omniforge"

            modules(
                "java.instrument", "java.management", "java.naming", "java.prefs", "java.sql",
                "jdk.unsupported", "jdk.crypto.ec", "jdk.zipfs", "jdk.accessibility"
            )

            val icons = rootProject.file("packaging/icons")
            for ((ext, mime) in listOf(
                "pdf" to "application/pdf",
                "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "webp" to "image/webp",
                "heic" to "image/heic", "mp4" to "video/mp4", "mkv" to "video/x-matroska",
                "webm" to "video/webm", "mov" to "video/quicktime"
            )) {
                fileAssociation(mimeType = mime, extension = ext, description = "Open with Apparat")
            }

            linux {
                packageName = "apparat"
                iconFile.set(icons.resolve("apparat.png"))
                debMaintainer = "apparat@omniforge.app"
                menuGroup = "Utility"
                appCategory = "utils"
                appRelease = "1"
                shortcut = true
            }
            windows {
                iconFile.set(icons.resolve("apparat.ico"))
                menuGroup = "Apparat"
                menu = true
                shortcut = true
                // Installs for the current user: no administrator prompt.
                perUserInstall = true
                dirChooser = false
                // Fixed so new versions upgrade the old install instead of installing side by side.
                upgradeUuid = "6f1c3c3e-5a3e-4c55-9e57-1d0b0f5a7a21"
            }
        }
    }
}

// Offscreen screenshots of every screen (used on CI to check the UI without a display).
tasks.register<JavaExec>("screenshots") {
    group = "verification"
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.omniforge.apparat.desktop.ScreenshotsKt")
    val dir = layout.buildDirectory.dir("screenshots").get().asFile
    args(dir.absolutePath)
    systemProperty("java.awt.headless", "true")
    systemProperty("apparat.home", layout.buildDirectory.dir("screenshot-home").get().asFile.absolutePath)
}
