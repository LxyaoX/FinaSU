package me.weishu.kernelsu.ui.component.material

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import me.weishu.kernelsu.ksuApp

/**
 * Whether setting sliders expose discrete steps, which also draws the tick marks on their track.
 *
 * Playback and progress sliders ignore it: those values are a position in a file, not a setting.
 */
object SliderTrackConfig {

    private const val PREFS = "settings"
    private const val KEY = "discrete_slider"

    const val DEFAULT_STEPS = 19

    var isDiscrete: Boolean by mutableStateOf(true)
        private set

    val steps: Int
        get() = if (isDiscrete) DEFAULT_STEPS else 0

    fun setDiscreteSteps(discrete: Boolean) {
        if (isDiscrete == discrete) return
        isDiscrete = discrete
        ksuApp.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY, discrete) }
    }

    fun load(context: Context) {
        isDiscrete = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)
    }
}
