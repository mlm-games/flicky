package app.flicky.ui.components.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import app.flicky.data.model.FDroidApp
import coil.compose.AsyncImage

@Composable
fun TVAppCard(
    app: FDroidApp,
    autofocus: Boolean = false,
    onClick: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.05f else 1f, label = "tv_card_scale")


    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
//        border = BorderStroke(
//            width = if (focused) 3.dp else 1.dp,
//            color = if (focused) AppColors.PrimaryGreen else Color.Gray.copy(alpha = 0.3f)
//        ), // Already handles pretty well
    ) {
        Column(Modifier.padding(16.dp)) {
            AsyncImage(
                model = app.iconUrl,
                contentDescription = app.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                app.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            Text(
                app.summary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
        }
    }

}