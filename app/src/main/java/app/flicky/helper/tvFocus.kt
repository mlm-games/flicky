package app.flicky.helper

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties

fun Modifier.cardAsFocusGroup() = this.focusGroup().focusProperties { canFocus = false }

@Composable
fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier =
    this.then(Modifier).let { clickableModifier ->
        clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        ) { onClick() }
    }