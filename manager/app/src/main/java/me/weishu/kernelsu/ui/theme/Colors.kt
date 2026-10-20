package me.weishu.kernelsu.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import me.weishu.kernelsu.R

data class KeyColorOption(val argb: Int, @StringRes val labelRes: Int)

val keyColorOptions = listOf(
    KeyColorOption(Color(0xFFF44336).toArgb(), R.string.color_red),
    KeyColorOption(Color(0xFFE91E63).toArgb(), R.string.color_pink),
    KeyColorOption(Color(0xFF9C27B0).toArgb(), R.string.color_purple),
    KeyColorOption(Color(0xFF673AB7).toArgb(), R.string.color_deep_purple),
    KeyColorOption(Color(0xFF3F51B5).toArgb(), R.string.color_indigo),
    KeyColorOption(Color(0xFF2196F3).toArgb(), R.string.color_blue),
    KeyColorOption(Color(0xFF00BCD4).toArgb(), R.string.color_cyan),
    KeyColorOption(Color(0xFF009688).toArgb(), R.string.color_teal),
    KeyColorOption(Color(0xFF4FAF50).toArgb(), R.string.color_green),
    KeyColorOption(Color(0xFFFFEB3B).toArgb(), R.string.color_yellow),
    KeyColorOption(Color(0xFFFFC107).toArgb(), R.string.color_amber),
    KeyColorOption(Color(0xFFFF9800).toArgb(), R.string.color_orange),
    KeyColorOption(Color(0xFF795548).toArgb(), R.string.color_brown),
    KeyColorOption(Color(0xFF607D8F).toArgb(), R.string.color_blue_grey),
    KeyColorOption(Color(0xFFFF9CA8).toArgb(), R.string.color_sakura),
)
