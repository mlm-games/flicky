package app.flicky.helper

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

inline fun <reified T : ViewModel> viewModelFactory(
    crossinline creator: () -> T
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
        @Suppress("UNCHECKED_CAST")
        return creator() as VM
    }
}

val Process.isAlive: Boolean
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        this.isAlive
    } else {
        try {
            this.exitValue()
            false
        } catch (e: IllegalThreadStateException) {
            true
        }
    }