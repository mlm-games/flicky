package app.flicky.helper

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

object TvFocusConfig {
    const val TV_FOCUS_ANIMATION_DURATION = 100 // ms
    const val FOCUS_DEBOUNCE_DELAY = 50L
    const val INITIAL_FOCUS_DELAY = 64L
    const val PRIMARY_FOCUS_DELAY = 96L

    val tvFocusAnimationSpec = tween<Color>(
        durationMillis = TV_FOCUS_ANIMATION_DURATION,
        easing = LinearEasing
    )
}

@Composable
fun rememberDebouncedFocusState(): Pair<Boolean, (Boolean) -> Unit> {
    val (focused, setFocused) = remember { mutableStateOf(false) }
    val (pendingFocus, setPendingFocus) = remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(pendingFocus) {
        pendingFocus?.let {
            delay(TvFocusConfig.FOCUS_DEBOUNCE_DELAY.milliseconds)
            setFocused(it)
            setPendingFocus(null)
        }
    }

    return focused to { newFocus: Boolean -> setPendingFocus(newFocus) }
}

fun Modifier.cardAsFocusGroup() = this.focusGroup().focusProperties { canFocus = false }

fun FocusRequester.safeRequestFocus(): Boolean =
    runCatching { requestFocus() }.getOrDefault(false)


fun Modifier.tvContentPane(focusRequester: FocusRequester): Modifier =
    this
        .focusRequester(focusRequester)
        .focusGroup()

@Composable
fun Modifier.tvFocusRing(
    shape: Shape = RoundedCornerShape(12.dp),
    borderWidth: Dp = 2.dp
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val color by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = TvFocusConfig.tvFocusAnimationSpec,
        label = "tvFocusRing"
    )
    return this
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .border(borderWidth, color, shape)
}

/**
 * TV only callers should gate this
 */
@Composable
fun RequestFocusOnKey(
    key: Any?,
    focusRequester: FocusRequester,
    enabled: Boolean = true,
    delayMs: Long = TvFocusConfig.INITIAL_FOCUS_DELAY,
) {
    LaunchedEffect(key, enabled) {
        if (!enabled) return@LaunchedEffect
        delay(delayMs.milliseconds)
        focusRequester.safeRequestFocus()
    }
}
