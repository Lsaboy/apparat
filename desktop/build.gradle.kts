plugins {
    kotlin("jvm") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.compose") version "1.12.2" apply false
}

// Reports desktop overrides of Android files that have gone stale (see android-overrides/README.md).
// Warns by default; -PstrictOverrides=true (CI) makes it fail.
val checkAndroidOverrides = tasks.register("checkAndroidOverrides") {
    group = "verification"
    val lock = file("android-overrides/overrides.lock")
    val overrides = file("android-overrides")
    val android = file("../app/src/main/java")
    val strict = providers.gradleProperty("strictOverrides").map { it.toBoolean() }.orElse(false)
    inputs.files(fileTree(overrides), fileTree(android) { include("**/*.kt") })
    doLast {
        fun sha(f: java.io.File) = java.security.MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
        val problems = mutableListOf<String>()
        val pinned = lock.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { it.substringBefore(' ') to it.substringAfter(' ').trim() }
        val present = overrides.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(overrides).invariantSeparatorsPath }.toSet()
        for (path in present - pinned.keys) problems += "$path has no line in overrides.lock"
        for ((path, hash) in pinned) {
            val original = android.resolve(path)
            val override = overrides.resolve(path)
            when {
                !override.isFile -> problems += "$path is in overrides.lock but has no override file"
                !original.isFile -> problems += "$path no longer exists in the Android app: delete the override"
                sha(original) == sha(override) -> problems += "$path: Android now has the same code; delete the override"
                sha(original) != hash -> problems += "$path changed in the Android app since the override was made: re-apply the desktop change on top of it and update overrides.lock"
            }
        }
        if (problems.isEmpty()) {
            logger.lifecycle("Android overrides: ${pinned.size} up to date")
        } else {
            val message = "Android overrides need attention:\n - " + problems.joinToString("\n - ")
            if (strict.get()) throw GradleException(message) else logger.warn("WARNING: $message")
        }
    }
}
subprojects {
    tasks.matching { it.name == "test" }.configureEach { dependsOn(checkAndroidOverrides) }
}
