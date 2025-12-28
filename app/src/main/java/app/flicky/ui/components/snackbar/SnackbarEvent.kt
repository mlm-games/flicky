package app.flicky.ui.components.snackbar

import androidx.compose.material3.SnackbarDuration

data class SnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val withDismissAction: Boolean = false,
    val duration: SnackbarDuration = SnackbarDuration.Short,
    val onAction: (suspend () -> Unit)? = null,
)