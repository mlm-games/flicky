package app.flicky.ui.components.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.flicky.data.model.FDroidApp
import app.flicky.helper.DeviceUtils

@Composable
fun AdaptiveAppCard(
    app: FDroidApp,
    autofocus: Boolean = false,
    onClick: () -> Unit = {}
) {
    val isTV = DeviceUtils.isTV(LocalContext.current.packageManager)
    if (isTV) {
        TVAppCard(app = app, autofocus = autofocus, onClick = onClick)
    } else {
        MobileAppCard(app = app, onClick = onClick)
    }
}