package me.weishu.kernelsu.ui.navigation3

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.weishu.kernelsu.ksuApp

/**
 * Preview played by the in-app navigation while the back gesture is dragged, mirroring the choice
 * BakaSU offers under its predictive back settings.
 *
 * [Default] keeps whatever androidx.navigation3 ships (the page fades out into the one below it);
 * the other values replace the pop, so the same animation plays whether back comes from the
 * gesture, the system bar or an explicit pop call. [None] keeps the page still until the gesture
 * completes.
 */
enum class PredictiveBackAnimation(val storageValue: String) {
    /** Page shrinks and slides out in the exit direction, revealing the page below it. */
    Scale("scale"),

    /** Cross-activity style: both pages hint at a scale change while the page drifts away. */
    Aosp("aosp"),

    /**
     * miuix's own cover motion: the page slides its whole width away while the page below drifts a
     * quarter of the width and lifts the dimming it carries while it is covered.
     */
    Miuix("miuix"),

    /** KernelSU classic: the leaving page scales down and fades, the revealed page slides in. */
    KernelSUClassic("ksu_classic"),

    /** Whatever androidx.navigation3 does by default. */
    Default("default"),

    /** No preview at all; the page changes once the gesture is completed. */
    None("none"),
    ;

    /**
     * Whether the style keeps everything still until the gesture completes. Such a style cannot be
     * expressed as a transition (a seeked transition that animates nothing jumps when it is played
     * out), so the gesture is completed above the navigation display instead.
     */
    val previewsNothing: Boolean get() = this == None

    companion object {
        fun fromValue(value: String?): PredictiveBackAnimation =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: Scale
    }
}

/**
 * Direction the leaving page travels in for the styles that slide it away. [FollowGesture]
 * mirrors the edge the gesture started from, so a drag from the right edge exits to the left.
 */
enum class PredictiveBackExitDirection(val storageValue: String) {
    FollowGesture("follow_gesture"),
    AlwaysRight("always_right"),
    AlwaysLeft("always_left"),
    ;

    companion object {
        fun fromValue(value: String?): PredictiveBackExitDirection =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: FollowGesture
    }
}

/**
 * Persisted predictive back animation choice. [revision] bumps on every write so the navigation
 * display and the settings screen pick the change up immediately.
 */
object PredictiveBackConfig {

    private const val PREFS = "settings"
    private const val KEY_ANIMATION = "predictive_back_animation"
    private const val KEY_EXIT_DIRECTION = "predictive_back_exit_direction"

    private val prefs get() = ksuApp.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    var animation: PredictiveBackAnimation
        get() = PredictiveBackAnimation.fromValue(prefs.getString(KEY_ANIMATION, null))
        set(value) {
            prefs.edit { putString(KEY_ANIMATION, value.storageValue) }
            _revision.value++
        }

    var exitDirection: PredictiveBackExitDirection
        get() = PredictiveBackExitDirection.fromValue(prefs.getString(KEY_EXIT_DIRECTION, null))
        set(value) {
            prefs.edit { putString(KEY_EXIT_DIRECTION, value.storageValue) }
            _revision.value++
        }
}
