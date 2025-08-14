package app.flicky.install

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SessionInstallBus {
    private val _events = MutableSharedFlow<Pair<Int, Int>>(replay = 0)
    val events = _events.asSharedFlow()

    suspend fun publish(sessionId: Int, resultCode: Int) {
        _events.emit(sessionId to resultCode)
    }
}