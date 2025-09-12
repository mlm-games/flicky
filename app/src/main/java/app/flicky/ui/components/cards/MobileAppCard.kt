package app.flicky.ui.components.cards

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.flicky.AppGraph
import app.flicky.data.model.FDroidApp
import coil.compose.AsyncImage
import app.flicky.R
import app.flicky.data.repository.AppSettings
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileAppCard(
    app: FDroidApp,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick) // instead of onClick param
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