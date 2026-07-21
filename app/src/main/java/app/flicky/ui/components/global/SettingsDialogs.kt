package app.flicky.ui.components.global

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Dialog for slider-based settings
 */
@Suppress("NonObservableLocale")
@Composable
fun SliderSettingDialog(
    title: String,
    currentValue: Float,
    min: Float,
    max: Float,
    step: Float,
    onDismiss: () -> Unit,
    onValueSelected: (Float) -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(currentValue) }

    FlickyDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = {
            TextButton(
                onClick = { onValueSelected(sliderValue) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colorScheme.primary
                )
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colorScheme.onSurfaceVariant
                )
            ) {
                Text("Cancel")
            }
        }
    ) {
        Column {
            // Value display
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = String.format(Locale.getDefault(), "%.1f", sliderValue),
                        style = typography.headlineMedium,
                        color = colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val stepsCount = max(0, ((max - min) / step).roundToInt() - 1)

            Slider(
                value = sliderValue,
                onValueChange = { v ->
                    val snapped = (round((v - min) / step) * step + min).coerceIn(min, max)
                    sliderValue = snapped
                },
                valueRange = min..max,
                steps = stepsCount,
                colors = SliderDefaults.colors(
                    thumbColor = colorScheme.primary,
                    activeTrackColor = colorScheme.primary,
                    inactiveTrackColor = colorScheme.surfaceVariant
                )
            )

            // Min/Max labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = min.toString(),
                    style = typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = max.toString(),
                    style = typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DropdownSettingDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onOptionSelected: (Int) -> Unit
) {
    SelectionDialog(
        title = title,
        items = options.indices.toList(),
        selectedItem = selectedIndex,
        onItemSelected = onOptionSelected,
        onDismiss = onDismiss,
        itemContent = { index -> options[index] }
    )
}

/**
 * Base themed dialog wrapper for consistent styling across the app
 */
@Composable
fun FlickyDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    icon: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        icon = icon,
        title = title?.let {
            {
                Text(
                    text = it,
                    style = typography.headlineSmall,
                    color = colorScheme.onSurface
                )
            }
        },
        text = {
            Surface(
                color = colorScheme.surface,
                contentColor = colorScheme.onSurfaceVariant
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    content = content
                )
            }
        },
        properties = properties,
        containerColor = colorScheme.surface,
        iconContentColor = colorScheme.secondary,
        titleContentColor = colorScheme.onSurface,
        textContentColor = colorScheme.onSurfaceVariant,
        tonalElevation = 6.dp
    )
}

/**
 * Confirmation dialog with standard buttons
 */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDangerous: Boolean = false
) {
    FlickyDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isDangerous) {
                        colorScheme.error
                    } else {
                        colorScheme.primary
                    }
                )
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colorScheme.onSurfaceVariant
                )
            ) {
                Text(dismissText)
            }
        }
    ) {
        Text(
            text = message,
            style = typography.bodyMedium,
            color = colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Input dialog for text entry
 */
@Composable
fun InputDialog(
    title: String,
    label: String,
    value: String,
    placeholder: String = "",
    confirmText: String = "OK",
    dismissText: String = "Cancel",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    validator: (String) -> Boolean = { true }
) {
    var inputValue by remember { mutableStateOf(value) }
    val isValid = validator(inputValue)

    FlickyDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(inputValue) },
                enabled = isValid,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colorScheme.primary,
                    disabledContentColor = colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colorScheme.onSurfaceVariant
                )
            ) {
                Text(dismissText)
            }
        }
    ) {
        OutlinedTextField(
            value = inputValue,
            onValueChange = { inputValue = it },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            isError = !isValid && inputValue.isNotEmpty(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = colorScheme.outline,
                errorBorderColor = colorScheme.error,
                focusedLabelColor = colorScheme.primary,
                unfocusedLabelColor = colorScheme.onSurfaceVariant,
                errorLabelColor = colorScheme.error
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}