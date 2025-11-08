package app.flicky.ui.components.global

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets


@Composable
fun MyScreenScaffold(
    title: String,
    actions: @Composable (RowScope.() -> Unit) = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                TopAppBar(
                    title = {
                        Text(
                            title,
                            color = colorScheme.onSurface
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colorScheme.surface,
                        titleContentColor = colorScheme.onSurface
                    ),
                    actions = actions,
                    windowInsets = WindowInsets()
                )
            }
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = colorScheme.background
        ) {
            content(paddingValues)
        }
    }
}


