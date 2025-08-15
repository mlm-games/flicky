package app.flicky.helper

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        val i = Intent(Intent.ACTION_VIEW, url.toUri()).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        val pm = context.packageManager
        if (i.resolveActivity(pm) != null) {
            context.startActivity(i)
        }
    }
}