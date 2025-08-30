package app.flicky.helper

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object DebugLog {
    private val _lines = MutableSharedFlow<String>(replay = 50, extraBufferCapacity = 100)
    val lines: SharedFlow<String> = _lines.asSharedFlow()

    fun log(tag: String, message: String) {
        _lines.tryEmit("[$tag] $message")
    }
}