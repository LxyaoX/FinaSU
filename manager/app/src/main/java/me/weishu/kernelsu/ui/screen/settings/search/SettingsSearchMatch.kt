package me.weishu.kernelsu.ui.screen.settings.search

import java.text.Normalizer
import java.util.Locale
import me.weishu.kernelsu.ui.component.settings.SettingsCategory

/** The group key the hub's own rows share; they have no category to group under. */
internal const val HubSearchGroupKey = "settings"

/**
 * A [SettingsSearchEntry] with its text resolved in the current locale.
 *
 * The folded copies the matcher compares against are built once here, so typing only ever folds
 * the query itself.
 */
data class ResolvedSettingsEntry(
    val entry: SettingsSearchEntry,
    val title: String,
    val summary: String,
    val parentTitle: String,
    val foldedTitle: String,
    val foldedSummary: String,
    val foldedParent: String,
    val foldedAliases: List<String>,
)

/** One row that matched, with the ranges to emphasise. */
data class SettingsSearchResult(
    val entry: ResolvedSettingsEntry,
    val score: Int,
    val titleMatches: List<IntRange>,
    val summaryMatches: List<IntRange>,
)

/** The rows of one category, in the order they should be drawn. */
data class SettingsSearchGroup(
    val key: String,
    val title: String,
    val results: List<SettingsSearchResult>,
)

private const val ScoreTitlePrefix = 100
private const val ScoreTitleWord = 80
private const val ScoreAliasPrefix = 70
private const val ScoreTitleContains = 60
private const val ScoreAliasContains = 45
private const val ScoreSummaryContains = 30
private const val ScoreParentContains = 20

/**
 * Folds [text] into the form the matcher compares: lower case, without combining marks, with every
 * non-alphanumeric run collapsed into a single space.
 *
 * Folding per character (rather than on the whole string at once) is what keeps a mapping back to
 * the original indices, which the highlight ranges need.
 */
internal fun foldText(text: String): FoldedText {
    val folded = StringBuilder(text.length)
    val origins = ArrayList<Int>(text.length)
    text.forEachIndexed { index, char ->
        foldChar(char).forEach { foldedChar ->
            if (foldedChar == ' ' && (folded.isEmpty() || folded.last() == ' ')) return@forEach
            folded.append(foldedChar)
            origins.add(index)
        }
    }
    return FoldedText(folded.toString().trimEnd(), origins)
}

/** The folded form alone, for callers that never need to point back at the original text. */
internal fun foldToSearchText(text: String): String = foldText(text).value

private fun foldChar(char: Char): String {
    val decomposed = Normalizer.normalize(char.toString(), Normalizer.Form.NFKD)
    val builder = StringBuilder(decomposed.length)
    decomposed.forEach { part ->
        when {
            isCombiningMark(part) -> Unit
            part.isLetterOrDigit() -> builder.append(part.lowercaseChar())
            else -> builder.append(' ')
        }
    }
    return builder.toString()
}

private fun isCombiningMark(char: Char): Boolean {
    val type = Character.getType(char)
    return type == Character.NON_SPACING_MARK.toInt() ||
        type == Character.COMBINING_SPACING_MARK.toInt() ||
        type == Character.ENCLOSING_MARK.toInt()
}

/** A folded string plus, for each folded character, the index of the character it came from. */
internal class FoldedText(private val text: String, private val origins: MutableList<Int>) {
    val value: String
        get() = text

    /** Maps a `[start, end)` range of the folded string back onto [text]'s source string. */
    fun originalRange(start: Int, endExclusive: Int): IntRange =
        origins[start]..origins[endExclusive - 1]
}

/**
 * Splits a query into the words every match must contain.
 *
 * The whole query is folded first, so "Módulo   card" and "modulo card" mean the same thing.
 */
fun settingsSearchTokens(query: String): List<String> =
    foldToSearchText(query).split(' ').filter { it.isNotEmpty() }

/**
 * How well a row answers the query, or `null` when the row does not belong in the results.
 *
 * Every token has to land somewhere, and each one contributes its best hit: a title hit outweighs
 * an alias, which outweighs a mention in the summary or the category name.
 */
fun settingsMatchScore(
    query: String,
    title: String,
    summary: String,
    parentTitle: String,
    aliases: List<String>,
): Int? = settingsMatchScore(
    tokens = settingsSearchTokens(query),
    foldedTitle = foldToSearchText(title),
    foldedSummary = foldToSearchText(summary),
    foldedParent = foldToSearchText(parentTitle),
    foldedAliases = aliases.map(::foldToSearchText),
)

internal fun settingsMatchScore(
    tokens: List<String>,
    foldedTitle: String,
    foldedSummary: String,
    foldedParent: String,
    foldedAliases: List<String>,
): Int? {
    if (tokens.isEmpty()) return null
    var total = 0
    tokens.forEach { token ->
        val score = tokenScore(token, foldedTitle, foldedSummary, foldedParent, foldedAliases) ?: return null
        total += score
    }
    return total
}

private fun tokenScore(
    token: String,
    foldedTitle: String,
    foldedSummary: String,
    foldedParent: String,
    foldedAliases: List<String>,
): Int? {
    var best: Int? = null

    val titleStart = foldedTitle.indexOf(token)
    if (titleStart >= 0) {
        best = when {
            titleStart == 0 -> ScoreTitlePrefix
            foldedTitle[titleStart - 1] == ' ' -> ScoreTitleWord
            else -> ScoreTitleContains
        }
    }

    foldedAliases.forEach { alias ->
        val start = alias.indexOf(token)
        if (start < 0) return@forEach
        val score = if (start == 0) ScoreAliasPrefix else ScoreAliasContains
        val current = best
        if (current == null || score > current) best = score
    }

    val afterAliases = best
    if (foldedSummary.contains(token) && (afterAliases == null || ScoreSummaryContains > afterAliases)) {
        best = ScoreSummaryContains
    }
    val afterSummary = best
    if (foldedParent.contains(token) && (afterSummary == null || ScoreParentContains > afterSummary)) {
        best = ScoreParentContains
    }
    return best
}

/** The ranges of [text] the query hit, merged and in source order, so the row can emphasise them. */
fun settingsMatchRanges(text: String, tokens: List<String>): List<IntRange> {
    if (text.isEmpty() || tokens.isEmpty()) return emptyList()
    val folded = foldText(text)
    if (folded.value.isEmpty()) return emptyList()

    val ranges = ArrayList<IntRange>()
    tokens.forEach { token ->
        var from = 0
        while (true) {
            val start = folded.value.indexOf(token, from)
            if (start < 0) break
            ranges += folded.originalRange(start, start + token.length)
            from = start + 1
        }
    }
    return mergeRanges(ranges)
}

private fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
    if (ranges.isEmpty()) return emptyList()
    val sorted = ranges.sortedBy { it.first }
    val merged = ArrayList<IntRange>()
    var current = sorted.first()
    sorted.drop(1).forEach { range ->
        current = if (range.first <= current.last + 1) {
            current.first..maxOf(current.last, range.last)
        } else {
            merged += current
            range
        }
    }
    merged += current
    return merged
}

/**
 * The rows answering [query], best first.
 *
 * Sorting is stable, so rows that score the same stay in the order the index declares them — which
 * is the order their pages show them in.
 */
fun matchSettings(query: String, entries: List<ResolvedSettingsEntry>): List<SettingsSearchResult> {
    val tokens = settingsSearchTokens(query)
    if (tokens.isEmpty()) return emptyList()

    return entries
        .mapNotNull { entry ->
            val score = settingsMatchScore(
                tokens = tokens,
                foldedTitle = entry.foldedTitle,
                foldedSummary = entry.foldedSummary,
                foldedParent = entry.foldedParent,
                foldedAliases = entry.foldedAliases,
            ) ?: return@mapNotNull null
            SettingsSearchResult(
                entry = entry,
                score = score,
                titleMatches = settingsMatchRanges(entry.title, tokens),
                summaryMatches = settingsMatchRanges(entry.summary, tokens),
            )
        }
        .sortedByDescending { it.score }
}

/**
 * Bundles results per category, in the hub's own order, with the hub's rows last.
 *
 * Keeping the categories in a fixed order means the groups do not swap places while the query
 * changes; inside a group the best hit still comes first.
 */
fun groupSettingsResults(results: List<SettingsSearchResult>): List<SettingsSearchGroup> =
    results
        .groupBy { it.entry.entry.category }
        .entries
        .sortedBy { (category, _) -> category?.ordinal ?: SettingsCategory.entries.size }
        .map { (category, groupResults) ->
            SettingsSearchGroup(
                key = category?.key ?: HubSearchGroupKey,
                title = groupResults.first().entry.parentTitle,
                results = groupResults,
            )
        }

/** Builds the resolved row the matcher and the result list both work on. */
internal fun resolveEntry(
    entry: SettingsSearchEntry,
    title: String,
    summary: String,
    parentTitle: String,
): ResolvedSettingsEntry = ResolvedSettingsEntry(
    entry = entry,
    title = title,
    summary = summary,
    parentTitle = parentTitle,
    foldedTitle = foldToSearchText(title),
    foldedSummary = foldToSearchText(summary),
    foldedParent = foldToSearchText(parentTitle),
    foldedAliases = entry.aliases.map(::foldToSearchText),
)
