package app.flicky.helper

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import app.flicky.R

fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        val i = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (i.resolveActivity(context.packageManager) != null) {
            context.startActivity(i)
        }
    }
}

fun shareText(context: Context, text: String) {
    val shareTitle = context.getString(R.string.action_share)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, shareTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}