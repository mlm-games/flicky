package app.flicky.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * TV-friendly setting section wrapper.
 * - Keeps spacing consistent
 * - Avoids extra cards to reduce nested surfaces
 */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .fillMaxWidth()
            ) {
                content()
            }
        }
    }
}

/**
 * Default clickable settings row (no trailing control).
 * TV-friendly with focus ring + scale and large target. (To use for other apps)
 */
@Composable
fun SettingsItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    description: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    SettingRowContainer(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
    ) {
        // Text block
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Chevron
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Toggle row with switch trailing.
 * Whole row is toggleable; the Switch mirrors state.
 */
@Composable
fun SettingsToggle(
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true
) {
    val stateDesc = if (isChecked) "On" else "Off"
    SettingRowContainer(
        enabled = enabled,
        onClick = { onCheckedChange(!isChecked) },
        modifier = modifier.semantics {
            this.stateDescription = stateDesc
        },
        role = Role.Switch
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Switch(
            checked = isChecked,
            onCheckedChange = { onCheckedChange(it) },
            enabled = enabled
        )
    }
}

/**
 * Action-style row with trailing button.
 * Designed for one-off actions: Clear Cache, Export, Import, etc.
 */
@Composable
fun SettingsAction(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    buttonText: String = "Run",
    enabled: Boolean = true
) {
    SettingRowContainer(
        enabled = enabled,
        onClick = onClick, // whole row clickable
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text(buttonText)
        }
    }
}


/**
 * Internal Shared row surface that is DPAD/TV friendly:
 * - Big touch target
 * - Focus scale + border ring
 */
@Composable
private fun SettingRowContainer(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role? = null,
    shape: Shape = RoundedCornerShape(14.dp),
    content: @Composable RowScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.02f else 1f, label = "setting_row_scale")
    val borderColor by animateColorAsState(
        targetValue = when {
            focused && enabled -> colors.primary
            else -> colors.outline.copy(alpha = 0.15f)
        },
        label = "setting_row_border"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            focused && enabled -> colors.primaryContainer.copy(alpha = 0.35f)
            else -> colors.surfaceVariant.copy(alpha = 0.40f)
        },
        label = "setting_row_bg"
    )

    val interaction = remember { MutableInteractionSource() }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = containerColor,
        tonalElevation = if (focused) 2.dp else 0.dp,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .semantics(mergeDescendants = true) {
                if (role != null) this.role = role
            },
        interactionSource = interaction
    ) {
        DisableSelection {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 64.dp) // large TV-friendly target
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .alpha(if (enabled) 1f else 0.6f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}
