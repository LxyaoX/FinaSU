package me.weishu.kernelsu.wallpaper

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ThemeValidationTest {
    @Test fun multiPageWallpaperDoesNotRequireSingleBackground() {
        val json = JSONObject().put("isBackgroundEnabled", true).put("isMultiBackgroundEnabled", true)
        assertTrue(ThemeValidation.validateBackground(json, setOf("background_home.png", "background_settings.png")))
        assertFalse(ThemeValidation.validateBackground(json, emptySet()))
        json.put("isMultiBackgroundEnabled", false)
        assertFalse(ThemeValidation.validateBackground(json, setOf("background_home.png")))
        assertTrue(ThemeValidation.validateBackground(json, setOf("background.png")))
    }

    @Test fun optionalSettingsAndMissingPayloadsDoNotRejectTheTheme() {
        listOf(
            JSONObject().put("nightModeEnabled", "true"),
            JSONObject().put("titleImageOffsetX", 3),
            JSONObject().put("folksu_keyColor", 1.5),
            JSONObject().put("isBackgroundEnabled", true),
            JSONObject().put("fontMode", "custom"),
            JSONObject().put("navIcons", JSONObject().put("Home", "missing.png")),
            JSONObject().put("appLanguage", "not_a_locale"),
            JSONObject().put("isDashboardCardBackgroundEnabled", true).put("hasDashboardCardBg", false),
            JSONObject().put("hasFocusCardKernelBg", true),
            JSONObject().put("isVideoBackgroundEnabled", true),
            JSONObject().put("isAdvancedTitleStyleEnabled", true),
            JSONObject().put("navIcons", "legacy"),
        ).forEach { json ->
            ThemeValidation.validate(json, emptyMap())
        }
    }

    @Test fun optAccessorCoercionAndSafeRangesAreNormalized() {
        val json = JSONObject().put("nightModeEnabled", "TRUE")
            .put("titleImageOffsetX", 3).put("folksu_keyColor", 1.5)
            .put("backgroundBlur", "invalid")
        val warnings = ThemeValidation.validate(json, emptyMap())
        assertTrue(json.getBoolean("nightModeEnabled"))
        assertEquals(2.0, json.getDouble("titleImageOffsetX"), 0.0)
        assertEquals(1, json.getInt("folksu_keyColor"))
        assertFalse(json.has("backgroundBlur"))
        assertTrue(warnings.any { it.contains("adjusted") })
    }

    @Test fun oldThemesUseFpDefaultsWithoutResettingAnUnspecifiedLanguage() {
        val json = JSONObject()
        ThemeValidation.validate(json, emptyMap())
        assertEquals("indigo", json.getString("customColor"))
        assertEquals("circle", json.getString("homeLayoutStyle"))
        assertTrue(json.getBoolean("nightModeFollowSys"))
        assertFalse(json.has("appLanguage"))
    }

    @Test fun unusedAndDuplicateMediaAreRetainedWithoutDecoderProbes() {
        val files = mapOf("background.jpg" to java.io.File("first.jpg"),
            "background.png" to java.io.File("second.png"), "foreign.bin" to java.io.File("foreign.bin"))
        val warnings = ThemeValidation.validate(JSONObject().put("isBackgroundEnabled", true), files)
        assertTrue(warnings.any { it.contains("Multiple resources") })
        assertEquals(3, files.size)
    }

    @Test fun legacyFontEnabledWithoutFontFileFallsBackToAppFont() {
        val json = JSONObject().put("isFontEnabled", true)
        val warnings = ThemeValidation.validate(json, emptyMap())
        assertTrue(warnings.any { it.contains("font", ignoreCase = true) })
        assertTrue(json.has("isFontEnabled"))
    }

    @Test fun unknownEnumsPreserveLocalValuesAndExplainLayoutMapping() {
        val json = JSONObject().put("colorContrast", "FUTURE").put("fontMode", "future")
            .put("isFontEnabled", true).put("homeLayoutStyle", "sign")
        val warnings = ThemeValidation.validate(json, emptyMap())
        assertFalse(json.has("colorContrast"))
        assertFalse(json.has("fontMode"))
        assertTrue(json.getBoolean("isFontEnabled"))
        assertEquals("sign", json.getString("homeLayoutStyle"))
        assertTrue(warnings.any { it.contains("circle") })
        assertTrue(warnings.any { it.contains("FUTURE") })
    }
}
