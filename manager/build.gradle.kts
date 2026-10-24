plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.compose.compiler) apply false
}

extra["androidMinSdkVersion"] = 34
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

/** Short HEAD hash, exactly what `git describe` appends after the `g`. */
fun getGitShortHash(): String {
    val process = Runtime.getRuntime().exec(arrayOf("git", "rev-parse", "--short", "HEAD"))
    return process.inputStream.bufferedReader().use { it.readText().trim() }
}

fun getVersionCode(): Int {
    val commitCount = getGitCommitCount()
    return 30000 + commitCount
}

/**
 * Manager version name, in upstream KernelSU's shape: `v3.3.2` on the commit that raises the
 * base version, and `v3.3.2-<commits>-g<hash>` after it, where `<commits>` counts from that
 * commit - the same 0-based number `git describe` would print.
 *
 * `git describe --tags --always` stays the source of truth, so a reachable `v*` tag reproduces
 * upstream verbatim. Without tags describe degrades to a bare commit id, and the same shape is
 * derived from `managerBaseVersion` (gradle.properties) plus the commit that introduced it.
 */
fun getVersionName(): String {
    val described = getGitDescribe()
    val isCommitId = described.matches(Regex("[0-9a-f]{7,40}"))
    if (described.isNotEmpty() && !isCommitId) return described

    val base = (findProperty("managerBaseVersion") as String?)?.takeIf { it.isNotBlank() }
        ?: "v3.3.2"
    val since = commitsSinceBaseVersion(base)
    return if (since <= 0) base else "$base-$since-g${getGitShortHash()}"
}

/**
 * Commits since the one that introduced `managerBaseVersion=<base>`, i.e. the number
 * `git describe` appends. Zero on that commit itself, and also when git cannot answer, so the
 * version degrades to the bare base instead of failing the build.
 */
fun commitsSinceBaseVersion(base: String): Int = runCatching {
    val anchor = Runtime.getRuntime().exec(
        arrayOf("git", "log", "-1", "--format=%H", "-S", "managerBaseVersion=$base", "--", "gradle.properties")
    ).inputStream.bufferedReader().use { it.readText().trim() }
    if (anchor.isEmpty()) return@runCatching 0
    Runtime.getRuntime().exec(arrayOf("git", "rev-list", "--count", "$anchor..HEAD"))
        .inputStream.bufferedReader().use { it.readText().trim().toInt() }
}.getOrDefault(0)
