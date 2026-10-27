package me.weishu.kernelsu.ui.screen.settings.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.bottombar.FloatingBarConfig
import me.weishu.kernelsu.ui.component.bottombar.NavMode
import me.weishu.kernelsu.ui.component.bottombar.NavModeConfig
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.settings.SettingsCategory
import me.weishu.kernelsu.ui.screen.settings.SettingsUiState
import me.weishu.kernelsu.ui.theme.FolkType
import top.yukonga.miuix.kmp.preference.ArrowPreference

/** FDK §5.2: emphasis is a weight, never a different colour. */
private val MatchEmphasis = SpanStyle(fontWeight = FontWeight.SemiBold)

/**
 * The query's results, grouped by category.
 *
 * Availability is part of the match: a row the device cannot show is never offered, which is what
 * keeps the search honest about what the pages actually contain. The filtering is deliberately not
 * remembered — the predicates read settings that live outside [uiState], and the matching itself is
 * far cheaper than getting that wrong.
 */
@Composable
fun settingsSearchGroups(
    query: String,
    uiState: SettingsUiState,
    entries: List<ResolvedSettingsEntry>,
): List<SettingsSearchGroup> {
    val navBar = rememberNavBarForm()
    return groupSettingsResults(
        matchSettings(query, entries.filter { it.isAvailable(uiState, navBar) })
    )
}

/** The navigation bar's persisted form, which decides whether its floating rows exist at all. */
private data class NavBarForm(val floating: Boolean)

@Composable
private fun rememberNavBarForm(): NavBarForm {
    // The two configs read their values straight from preferences, so their revisions are what keep
    // the search in step when the bar is reconfigured.
    val navModeRevision by NavModeConfig.revision.collectAsState()
    val floatingBarRevision by FloatingBarConfig.revision.collectAsState()
    return remember(navModeRevision, floatingBarRevision) {
        NavBarForm(floating = NavModeConfig.mode == NavMode.Floating)
    }
}

private fun ResolvedSettingsEntry.isAvailable(uiState: SettingsUiState, navBar: NavBarForm): Boolean {
    val search = entry
    return search.available(uiState) && (!search.requiresFloatingBar || navBar.floating)
}

/**
 * The results under the search field: one group per category, in the hub's own order.
 */
@Composable
fun SettingsSearchResults(
    groups: List<SettingsSearchGroup>,
    onSelect: (SettingsSearchEntry) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    listState: LazyListState = rememberLazyListState(),
) {
    if (groups.isEmpty()) {
        SettingsSearchEmptyState(modifier.fillMaxSize())
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        groups.forEach { group ->
            item(key = group.key) {
                SegmentedColumn(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
                    title = group.title,
                ) {
                    group.results.forEach { result ->
                        item(key = result.entry.entry.id) {
                            SettingsResultRow(result) { onSelect(result.entry.entry) }
                        }
                    }
                }
            }
        }
        item(key = "spacer") { Spacer(Modifier.height(8.dp)) }
    }
}

/**
 * What the full screen search shows before anything is typed: the categories themselves, so
 * opening the field and then remembering what you wanted is not a dead end.
 */
@Composable
fun SettingsSearchShortcuts(
    onOpenCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        SegmentedColumn(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        ) {
            SettingsCategory.entries.forEach { category ->
                item(key = category.key) {
                    val label = stringResource(category.titleRes)
                    ArrowPreference(
                        title = label,
                        startAction = { Icon(category.icon, label) },
                        onClick = { onOpenCategory(category.key) },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsResultRow(result: SettingsSearchResult, onClick: () -> Unit) {
    val entry = result.entry
    SegmentedListItem(
        onClick = onClick,
        headlineContent = { Text(matchedText(entry.title, result.titleMatches)) },
        supportingContent = if (entry.summary.isNotEmpty()) {
            { Text(matchedText(entry.summary, result.summaryMatches)) }
        } else {
            null
        },
        leadingContent = { Icon(entry.entry.icon, entry.title) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
    )
}

/** Emphasises the parts of [text] the query hit; the rest of the row keeps its own style. */
private fun matchedText(text: String, ranges: List<IntRange>): AnnotatedString {
    if (ranges.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        ranges.forEach { range ->
            val start = range.first.coerceIn(0, text.length)
            val end = (range.last + 1).coerceIn(start, text.length)
            if (start < end) addStyle(MatchEmphasis, start, end)
        }
    }
}

@Composable
private fun SettingsSearchEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_search_empty),
            style = FolkType.Summary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
