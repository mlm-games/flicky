package app.flicky.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.flicky.R
import app.flicky.data.repository.AlertBannerDefinition

private val AlertBannerBackgroundColor = Color(0xFFFFEBEE)
private val AlertBannerTextColor = Color(0xFFB71C1C)
private val AlertBannerLinkColor = Color(0xFF1565C0)

@Composable
fun AlertBanner(
    banner: AlertBannerDefinition,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AlertBannerBackgroundColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val annotatedText = buildAnnotatedString {
            withStyle(SpanStyle(color = AlertBannerTextColor)) {
                append(banner.message)
                append(" ")
            }
            pushStringAnnotation(tag = "URL", annotation = banner.linkUrl)
            withStyle(
                SpanStyle(
                    color = AlertBannerLinkColor,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Medium
                )
            ) {
                append(banner.linkText)
            }
            pop()
        }

        Text(
            text = annotatedText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .clickable { openUrlSafely(context, banner.linkUrl) },
            color = AlertBannerTextColor
        )

        Spacer(Modifier.width(8.dp))

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.width(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.dismiss_banner),
                tint = AlertBannerTextColor
            )
        }
    }
}

private fun openUrlSafely(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(
            context,
            context.getString(R.string.no_browser_available),
            Toast.LENGTH_SHORT
        ).show()
    }
}
