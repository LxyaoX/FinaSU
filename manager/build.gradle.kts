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
 * Version name to use when the clone has no reachable tag.
 *
 * `git describe --tags --always` then degrades to a bare commit id, which is not a version: the
 * About page showed a hash and the APK lost the `v` prefix that the updater and the release
 * workflow both match on. `managerFallbackVersion` (gradle.properties) overrides the default,
 * and a reachable `v*` tag still wins. Everything is read inside the function on purpose: the
 * script calls getVersionName() before later top-level properties would be initialized.
 */
fun getVersionName(): String {
    val described = getGitDescribe()
    val fallback = (findProperty("managerFallbackVersion") as String?)
        ?.takeIf { it.isNotBlank() } ?: "v1.0.0"
    // 7-40 lowercase hex and nothing else is describe's commit-id fallback, not a tag.
    val isCommitId = described.matches(Regex("[0-9a-f]{7,40}"))
    return if (described.isEmpty() || isCommitId) fallback else described
}
