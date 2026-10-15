package me.weishu.kernelsu.ui.screen.settings.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import me.weishu.kernelsu.ui.component.settings.SettingsCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchMatchTest {

    @Test
    fun `folding drops case accents and punctuation`() {
        assertEquals("modulo", foldToSearchText("Módulo"))
        assertEquals("hidden fingerprint", foldToSearchText("Hidden/Fingerprint"))
        assertEquals("no mount", foldToSearchText("No-Mount"))
        assertEquals("搜索设置", foldToSearchText("搜索设置"))
    }

    @Test
    fun `a query is split into its folded words`() {
        assertEquals(listOf("selinux", "mode"), settingsSearchTokens("  SELinux   Mode "))
        assertTrue(settingsSearchTokens("   ").isEmpty())
    }

    @Test
    fun `every token has to match somewhere`() {
        val title = "Check for module updates"
        assertEquals(
            80 + 80,
            settingsMatchScore("module updates", title, summary = "", parentTitle = "", aliases = emptyList()),
        )
        assertNull(
            settingsMatchScore("module wallpaper", title, summary = "", parentTitle = "", aliases = emptyList()),
        )
    }

    @Test
    fun `a title hit outranks an alias which outranks the summary`() {
        val titleHit = settingsMatchScore("adb", "ADB root", "", "", emptyList())!!
        val aliasHit = settingsMatchScore("adb", "Shell su", "", "", listOf("adb"))!!
        val summaryHit = settingsMatchScore("adb", "Shell su", "Grants adb only", "", emptyList())!!
        val groupHit = settingsMatchScore("adbd", "Shell su", "", "adbd", emptyList())!!

        assertTrue(titleHit > aliasHit)
        assertTrue(aliasHit > summaryHit)
        assertTrue(summaryHit > groupHit)
    }

    @Test
    fun `a prefix outranks the same word inside the title`() {
        val prefix = settingsMatchScore("mode", "Mode", "", "", emptyList())!!
        val word = settingsMatchScore("mode", "SELinux mode", "", "", emptyList())!!
        val inside = settingsMatchScore("mode", "SELinuxmodes", "", "", emptyList())!!

        assertTrue(prefix > word)
        assertTrue(word > inside)
    }

    @Test
    fun `match ranges point back at the original text`() {
        assertEquals(listOf(8..11), settingsMatchRanges("SELinux mode", listOf("mode")))
        assertEquals(emptyList<IntRange>(), settingsMatchRanges("SELinux mode", listOf("camera")))
    }

    @Test
    fun `ranges survive accent folding and merge overlaps`() {
        // "Módulo" folds to "modulo", so the hit still has to bracket the accented character.
        assertEquals(listOf(0..5), settingsMatchRanges("Módulo", listOf("modulo")))
        assertEquals(listOf(0..4), settingsMatchRanges("ababa", listOf("ab", "ba")))
    }

    @Test
    fun `results keep the index order when the scores tie`() {
        val entries = listOf(
            resolved(id = "first", category = SettingsCategory.SECURITY, title = "SELinux hide"),
            resolved(id = "second", category = SettingsCategory.SECURITY, title = "SELinux mode"),
            resolved(id = "third", category = SettingsCategory.SECURITY, title = "Shell su"),
        )

        val results = matchSettings("selinux", entries)

        assertEquals(listOf("first", "second"), results.map { it.entry.entry.id })
    }

    @Test
    fun `groups follow the hub order and the hub rows come last`() {
        val entries = listOf(
            resolved(id = "hub.about", category = null, title = "About"),
            resolved(id = "function.sulog", category = SettingsCategory.FUNCTION, title = "SU log"),
            resolved(id = "security.adb", category = SettingsCategory.SECURITY, title = "ADB root"),
        )

        val groups = groupSettingsResults(matchSettings("o", entries))

        // FUNCTION is declared before SECURITY, and the hub's own rows belong to neither.
        assertEquals(
            listOf(SettingsCategory.FUNCTION.key, SettingsCategory.SECURITY.key, HubSearchGroupKey),
            groups.map { it.key },
        )
    }

    private fun resolved(id: String, category: SettingsCategory?, title: String) = ResolvedSettingsEntry(
        entry = SettingsSearchEntry(
            id = id,
            titleRes = 0,
            icon = Icons.Filled.Search,
            target = SettingsSearchTarget.Category(category?.key ?: HubSearchGroupKey),
            category = category,
        ),
        title = title,
        summary = "",
        parentTitle = category?.name ?: "Settings",
        foldedTitle = foldToSearchText(title),
        foldedSummary = "",
        foldedParent = "",
        foldedAliases = emptyList(),
    )
}
