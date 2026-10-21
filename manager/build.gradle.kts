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
 * Manager version name.
 *
 * Upstream KernelSU derives it from `git describe --tags --always`: the bare tag (`v3.3.2`) on
 * the tagged commit, and `v3.3.2-12-g1a2b3c4` after it. This clone carries no tag, so describe
 * degrades to a commit id; build the same shape from `managerBaseVersion` plus the commit count
 * and the short hash. A reachable `v*` tag still wins, which is exactly upstream behaviour.
 */
fun getVersionName(): String {
    val described = getGitDescribe()
    val isCommitId = described.matches(Regex("[0-9a-f]{7,40}"))
    if (described.isNotEmpty() && !isCommitId) return described
    val base = (findProperty("managerBaseVersion") as String?)?.takeIf { it.isNotBlank() }
        ?: "v3.3.2"
    return "$base-${getGitCommitCount()}-g${getGitShortHash()}"
}

fun getGitShortHash(): String {
    val process = Runtime.getRuntime().exec(arrayOf("git", "rev-parse", "--short", "HEAD"))
    return process.inputStream.bufferedReader().use { it.readText().trim() }
}
