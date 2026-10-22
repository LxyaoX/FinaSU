package me.weishu.kernelsu.ui.util

import java.util.Locale

fun cssColorFromArgb(argb: Int): String {
    val a = ((argb ushr 24) and 0xFF) / 255f
    val r = (argb ushr 16) and 0xFF
    val g = (argb ushr 8) and 0xFF
    val b = argb and 0xFF
    return "rgba(${r},${g},${b},${"%.3f".format(Locale.ROOT, a)})"
}
