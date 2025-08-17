package app.flicky.ui.components

import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.flicky.helper.HtmlUtils

/**
 * - If HTML is present and rich=true, renders HTML (links clickable).
 * - Otherwise renders plain text (HTML stripped if needed).
 *
 * For lists/grids, set rich=false to keep performance high.
 */
@Composable
fun SmartText(
    text: String,
    modifier: Modifier = Modifier,
    rich: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    ellipsizeEnd: Boolean = false
) {
    val hasHtml = remember(text) { HtmlUtils.containsHtml(text) }
    val plain = remember(text, hasHtml) { if (hasHtml) HtmlUtils.toPlainText(text) else text }

    if (rich && hasHtml) {
        val color = MaterialTheme.colorScheme.onSurface.toArgb()
        val link = MaterialTheme.colorScheme.primary.toArgb()
        val spanned = remember(text) { HtmlUtils.toSpanned(text) }

        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                TextView(ctx).apply {
                    setTextColor(color)
                    setLinkTextColor(link)
                    textSize = 14f
                    movementMethod = LinkMovementMethod.getInstance()
                    setLineSpacing(0f, 1.1f)
                    if (maxLines != Int.MAX_VALUE) {
                        this.maxLines = maxLines
                        if (ellipsizeEnd) this.ellipsize = TextUtils.TruncateAt.END
                    }
                }
            },
            update = { tv ->
                tv.setTextColor(color)
                tv.setLinkTextColor(link)
                if (maxLines != Int.MAX_VALUE) {
                    tv.maxLines = maxLines
                    tv.ellipsize = if (ellipsizeEnd) TextUtils.TruncateAt.END else null
                } else {
                    tv.maxLines = Int.MAX_VALUE
                    tv.ellipsize = null
                }
                tv.text = spanned
            }
        )
    } else {
        Text(
            text = plain,
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines
        )
    }
}

@Composable
fun SmartExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    rich: Boolean = true,
    collapsedMaxLines: Int = 6,
    showReadMoreThreshold: Int = 300
) {
    var expanded by remember { mutableStateOf(false) }
    val hasHtml = remember(text) { HtmlUtils.containsHtml(text) }

    Column(modifier) {
        SmartText(
            text = text,
            rich = rich && hasHtml,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            ellipsizeEnd = !expanded
        )
        if (!expanded && text.length > showReadMoreThreshold) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { expanded = true }) {
                Text("Read more")
            }
        }
    }
}