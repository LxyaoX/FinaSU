plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.compose.compiler) apply false
}

extra["androidMinSdkVersion"] = 31
extra["androidTargetSdkVersion"] = 37
extra["androidCompileSdkVersion"] = 37
extra["androidCompileSdkVersionMinor"] = 0
extra["androidBuildToolsVersion"] = "37.0.0"
extra["androidCompileNdkVersion"] = libs.versions.ndk.get()
extra["androidSourceCompatibility"] = JavaVersion.VERSION_21
extra["androidTargetCompatibility"] = JavaVersion.VERSION_21
extra["managerVersionCode"] = getVersionCode()
extra["managerVersionName"] = getVersionName()

fun getGitCommitCount(): Int {
    val process = Runtime.getRuntime().exec(arrayOf("git", "rev-list", "--count", "HEAD"))
    return process.inputStream.bufferedReader().use { it.readText().trim().toInt() }
}

fun getGitDescribe(): String {
    val process = Runtime.getRuntime().exec(arrayOf("git", "describe", "--tags", "--always"))
    return process.inputStream.bufferedReader().use { it.readText().trim() }
}

fun getVersionCode(): Int {
    val commitCount = getGitCommitCount()
    return 30000 + commitCount
}

/**
 * Version name used when the clone has no reachable tag.
 *
 * `git describe --tags --always` then degrades to a bare commit id, which is not a version: the
 * About page showed a hash and the release asset lost the `v` prefix that both the updater and
 * the release workflow key on. Tagging `v*` still wins when a tag exists.
 */
val managerFallbackVersion =
    (findProperty("managerFallbackVersion") as String?)?.takeIf { it.isNotBlank() } ?: "v1.0.0"

/** A `git describe` fallback to a commit id: lowercase hex, 7 to 40 chars, nothing else. */
val commitIdPattern = Regex("[0-9a-f]{7,40}")

fun getVersionName(): String {
    val described = getGitDescribe()
    return if (described.isEmpty() || commitIdPattern.matches(described)) {
        managerFallbackVersion
    } else {
        described
    }
}
