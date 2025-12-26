@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)


package app.flicky.ui.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flicky.AppGraph
import app.flicky.R
import app.flicky.data.model.FDroidApp
import app.flicky.data.repository.AppSettings
import app.flicky.helper.DeviceUtils
import app.flicky.helper.TvFocusConfig
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
fun MobileAppCard(
    app: FDroidApp,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val shape = RoundedCornerShape(16.dp)

    ElevatedCard(
        shape = shape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = colorScheme.surface,
            contentColor = colorScheme.onSurface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        // This receives ripple/focus/pressed overlays
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape) // clips focus indication to rounded corners
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(12.dp)
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
                Text(app.name, style = typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.summary, style = typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(text = app.category, style = typography.labelSmall, color = colorScheme.primary)
                    Spacer(Modifier.weight(1f))
                    Text(text = "v${app.version}", style = typography.labelSmall)
                }
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
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = TvFocusConfig.tvFocusAnimationSpec,
        label = "tv_card_scale"
    )
    val colors = colorScheme
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autofocus) { if (autofocus) focusRequester.requestFocus() }

    ElevatedCard(
        shape = shape,
        modifier = Modifier
            .scale(scale),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (focused) colors.primaryContainer else colors.surface,
            contentColor = if (focused) colors.onPrimaryContainer else colors.onSurface
        )
    ) {
        Box(
            modifier = Modifier
                .scale(scale)
                .fillMaxWidth()
                .clip(shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(16.dp)
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
                    color = if (focused) colors.onPrimaryContainer else colors.onSurface
                )
                Text(
                    app.summary,
                    style = typography.bodySmall,
                    maxLines = 2,
                    color = if (focused) colors.onPrimaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant
                )
            }
        }
    }
}