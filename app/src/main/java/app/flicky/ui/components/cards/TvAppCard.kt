package app.flicky.ui.components.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.flicky.data.model.FDroidApp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import app.flicky.R


@Composable
fun TVAppCard(
    app: FDroidApp,
    autofocus: Boolean = false,
    onClick: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.05f else 1f, label = "tv_card_scale")
    val colors = MaterialTheme.colorScheme

    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (focused) colors.primaryContainer else colors.surface,
            contentColor = if (focused) colors.onPrimaryContainer else colors.onSurface
        ),
//        border = if (focused) {
//            BorderStroke(2.dp, colors.primary)
//        } else null
    ) {
        Column(Modifier.padding(16.dp)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(app.iconUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = app.name,
                placeholder = painterResource(R.drawable.ic_app_placeholder),
                error = painterResource(R.drawable.ic_app_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                app.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                color = if (focused) colors.onPrimaryContainer else colors.onSurface
            )
            Text(
                app.summary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                color = if (focused) colors.onPrimaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant
            )
        }
    }
}