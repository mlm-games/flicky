@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)


package app.flicky.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flicky.AppGraph
import app.flicky.R
import app.flicky.data.model.FDroidApp
import app.flicky.data.repository.AppSettings
import app.flicky.helper.DeviceUtils
import app.flicky.helper.rememberDebouncedFocusState
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun AdaptiveAppCard(
    app: FDroidApp,
    autofocus: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val isTV = DeviceUtils.isTV(LocalContext.current.packageManager)
    if (isTV) {
        TVAppCard(app = app, autofocus = autofocus, onClick = onClick, onLongClick = onLongClick)
    } else {
        MobileAppCard(app = app, onClick = onClick, onLongClick = onLongClick)
    }
}

@Composable
private fun CategoryVersionRow(
    category: String,
    version: String,
    categoryColor: androidx.compose.ui.graphics.Color,
    versionColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Layout(
        content = {
            Text(
                text = category,
                style = typography.labelSmall,
                color = categoryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formattedVer(version),
                style = typography.labelSmall,
                color = versionColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = modifier
    ) { measurables, constraints ->
        val versionPlaceable = measurables[1].measure(constraints.copy(minWidth = 0))
        val spacing = 8.dp.roundToPx()

        val categoryMaxWidth = (constraints.maxWidth - versionPlaceable.width - spacing)
            .coerceAtLeast(0)
        val categoryPlaceable = measurables[0].measure(
            constraints.copy(minWidth = 0, maxWidth = categoryMaxWidth)
        )

        val showVersion = categoryPlaceable.width + spacing + versionPlaceable.width <= constraints.maxWidth

        val height = maxOf(categoryPlaceable.height, versionPlaceable.height)

        layout(constraints.maxWidth, height) {
            categoryPlaceable.placeRelative(0, (height - categoryPlaceable.height) / 2)
            if (showVersion) {
                versionPlaceable.placeRelative(
                    constraints.maxWidth - versionPlaceable.width,
                    (height - versionPlaceable.height) / 2
                )
            }
        }
    }
}


@Composable
fun MobileAppCard(
    app: FDroidApp,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val shape = MaterialTheme.shapes.medium

    ElevatedCard(
        shape = shape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = colorScheme.surface,
            contentColor = colorScheme.onSurface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            Column(Modifier.padding(12.dp)) {
                val settings by AppGraph.settings.settingsFlow.collectAsState(initial = AppSettings())
                if (settings.showAppIcons) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(app.iconUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = app.name,
                        placeholder = painterResource(R.drawable.ic_app_placeholder),
                        error = painterResource(R.drawable.ic_app_placeholder),
                        modifier = Modifier.fillMaxWidth().height(140.dp)
                    )
                } else {
                    Box(Modifier.fillMaxWidth().height(140.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    app.name,
                    style = typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    app.summary,
                    style = typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                CategoryVersionRow(
                    category = app.category,
                    version = app.version,
                    categoryColor = colorScheme.primary,
                    versionColor = colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}


@Composable
fun TVAppCard(
    app: FDroidApp,
    autofocus: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val shape = RoundedCornerShape(16.dp)
    val (focused, setFocused) = rememberDebouncedFocusState()

    val colors = colorScheme

    ElevatedCard(
        shape = shape,
        modifier = Modifier
            .scale(if (focused) 1.05f else 1f)
            .focusRequester(remember { FocusRequester() })
            .onFocusChanged { setFocused(it.isFocused) }
            .fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (focused) colorScheme.primaryContainer else colorScheme.surface,
            contentColor = if (focused) colorScheme.onPrimaryContainer else colorScheme.onSurface
        )
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            Column(Modifier.padding(16.dp)) {
                val settings by AppGraph.settings.settingsFlow.collectAsState(initial = AppSettings())
                if (settings.showAppIcons) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(app.iconUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = app.name,
                        placeholder = painterResource(R.drawable.ic_app_placeholder),
                        error = painterResource(R.drawable.ic_app_placeholder),
                        modifier = Modifier.fillMaxWidth().height(140.dp)
                    )
                } else {
                    Box(Modifier.fillMaxWidth().height(140.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    app.name,
                    style = typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (focused) colors.onPrimaryContainer else colors.onSurface
                )
                Text(
                    app.summary,
                    style = typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (focused) colors.onPrimaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                CategoryVersionRow(
                    category = app.category,
                    version = app.version,
                    categoryColor = if (focused) colors.onPrimaryContainer.copy(alpha = 0.7f) else colorScheme.primary,
                    versionColor = if (focused) colors.onPrimaryContainer.copy(alpha = 0.6f) else colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

fun formattedVer(ver: String): String {
    return if (!ver.startsWith("v")) { "v${ver}" } else { ver }
}