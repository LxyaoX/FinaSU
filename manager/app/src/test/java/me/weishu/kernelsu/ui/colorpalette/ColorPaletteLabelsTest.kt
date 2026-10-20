package me.weishu.kernelsu.ui.colorpalette

import com.materialkolor.PaletteStyle
import me.weishu.kernelsu.ui.screen.colorpalette.paletteStyleLabelRes
import me.weishu.kernelsu.ui.theme.keyColorOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorPaletteLabelsTest {

    @Test
    fun everyPaletteStyleHasItsOwnName() {
        val labels = PaletteStyle.entries.map { paletteStyleLabelRes(it) }

        assertTrue(labels.none { it == 0 })
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun keyColorsKeepTheirStoredValues() {
        val stored = keyColorOptions.map { it.argb }

        assertEquals(
            listOf(
                0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5,
                0xFF2196F3, 0xFF00BCD4, 0xFF009688, 0xFF4FAF50, 0xFFFFEB3B,
                0xFFFFC107, 0xFFFF9800, 0xFF795548, 0xFF607D8F, 0xFFFF9CA8
            ).map { it.toInt() },
            stored
        )
    }

    @Test
    fun keyColorNamesAreUnique() {
        val labels = keyColorOptions.map { it.labelRes }

        assertTrue(labels.none { it == 0 })
        assertEquals(labels.size, labels.toSet().size)
    }
}
