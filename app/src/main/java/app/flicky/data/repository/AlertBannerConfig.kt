package app.flicky.data.repository

import java.time.LocalDate

data class AlertBannerDefinition(
    val id: String,
    val message: String,
    val linkText: String,
    val linkUrl: String,
    val expiryDate: LocalDate
) {
    val isExpired: Boolean
        get() = LocalDate.now().isAfter(expiryDate)
}

object AlertBanners {
    val KEEP_ANDROID_OPEN = AlertBannerDefinition(
        id = "keep_android_open_2026",
        message = "F-Droid is under threat. Google is changing the way you install apps on your phone. We need your help.",
        linkText = "Learn more",
        linkUrl = "https://keepandroidopen.org",
        expiryDate = LocalDate.of(2026, 9, 1)
    )

    val activeBanners: List<AlertBannerDefinition>
        get() = listOf(KEEP_ANDROID_OPEN).filter { !it.isExpired }
}
