package app.flicky.data.repository

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

fun applyAppLocale(languageTag: String?) {
    val target = languageTag
        ?.let { LocaleListCompat.forLanguageTags(it) }
        ?: LocaleListCompat.getEmptyLocaleList()
    if (AppCompatDelegate.getApplicationLocales().toLanguageTags() == target.toLanguageTags()) return
    AppCompatDelegate.setApplicationLocales(target)
}
