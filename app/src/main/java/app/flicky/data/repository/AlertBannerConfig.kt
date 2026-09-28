package app.flicky.data.repository

import java.time.LocalDate

data class AlertBannerDefinition(
    val id: String,
    val messageRes: Int,
    val linkTextRes: Int,
    val linkUrl: String,
    val expiryDate: LocalDate
) {
    val isExpired: Boolean
        get() = LocalDate.now().isAfter(expiryDate)
}

object AlertBanners {
    val KEEP_ANDROID_OPEN = AlertBannerDefinition(
        id = "keep_android_open_2026",
        messageRes = app.flicky.R.string.alert_keep_android_open,
        linkTextRes = app.flicky.R.string.read_more,
        linkUrl = "https://keepandroidopen.org",
        expiryDate = LocalDate.of(2026, 9, 1)
    )

    val activeBanners: List<AlertBannerDefinition>
        get() = listOf(KEEP_ANDROID_OPEN).filter { !it.isExpired }
}
