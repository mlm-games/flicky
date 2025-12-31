package app.flicky.ui.components.snackbar

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun LauncherSnackbarHost(
    hostState: SnackbarHostState,
    manager: SnackbarManager,
) {
    val scope = rememberCoroutineScope()

    LaunchedEffect(manager) {
        manager.events.collect { event ->
            val result = hostState.showSnackbar(
                message = event.message,
                actionLabel = event.actionLabel,
                withDismissAction = event.withDismissAction,
                duration = event.duration
            )

            if (result == SnackbarResult.ActionPerformed && event.onAction != null) {
                scope.launch { event.onAction.invoke() }
            }
        }
    }

    SnackbarHost(hostState = hostState)
}