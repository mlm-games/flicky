package app.flicky.helper

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

fun Context.isMeteredNetwork(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    return cm.isActiveNetworkMetered
}

@Composable
fun rememberUnmeteredConnection(): Boolean {
    val context = LocalContext.current
    val cm = remember {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }
    var unmetered by remember { mutableStateOf(isUnmetered(cm)) }

    DisposableEffect(cm) {
        if (cm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return@DisposableEffect onDispose { }
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                unmetered = networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_NOT_METERED
                )
            }

            override fun onLost(network: Network) {
                unmetered = isUnmetered(cm)
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        onDispose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }
    return unmetered
}

private fun isUnmetered(cm: ConnectivityManager?): Boolean {
    if (cm == null) return false
    val hasNetwork = cm.activeNetwork != null
    return hasNetwork && !cm.isActiveNetworkMetered
}
