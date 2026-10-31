package me.weishu.kernelsu.data

/**
 * The text between two marker lines of one shell probe.
 *
 * Both bundled payloads report their state and their configuration in a single round trip: the
 * state as one `key=value` line each, the configuration between a pair of markers. The markers are
 * what keeps a prop file - whose own lines look like `spoofBuild=true` - from being mistaken for
 * state, and a missing marker degrades to "no configuration" rather than to half of one.
 */
internal fun block(text: String, begin: String, end: String): String {
    val start = text.indexOf(begin)
    if (start < 0) return ""
    val bodyStart = text.indexOf('\n', start)
    if (bodyStart < 0) return ""
    val stop = text.indexOf(end, bodyStart + 1)
    if (stop < 0) return ""
    return text.substring(bodyStart + 1, stop)
}
