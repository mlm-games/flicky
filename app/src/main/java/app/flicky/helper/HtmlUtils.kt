package app.flicky.helper

import android.text.Spanned
import androidx.core.text.HtmlCompat

object HtmlUtils {
    fun containsHtml(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        // pre-check to avoid regex on plain strings
        if (!text.contains('<') || !text.contains('>')) return false
        val regex = Regex("<\\s*/?\\s*[a-zA-Z]+[^>]*>")
        return regex.containsMatchIn(text)
    }

    fun toPlainText(html: String): String =
        HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .trim()

    fun toSpanned(html: String): Spanned =
        HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
}