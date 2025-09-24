package app.flicky.helper

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import kotlinx.coroutines.delay

object TvFocusConfig {
    const val TV_FOCUS_ANIMATION_DURATION = 100 // ms
    const val FOCUS_DEBOUNCE_DELAY = 50L

    val tvFocusAnimationSpec = tween<Float>(
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
            delay(TvFocusConfig.FOCUS_DEBOUNCE_DELAY)
            setFocused(it)
            setPendingFocus(null)
        }
    }

    return focused to { newFocus: Boolean -> setPendingFocus(newFocus) }
}

fun Modifier.cardAsFocusGroup() = this.focusGroup().focusProperties { canFocus = false }
