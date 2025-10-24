package app.flicky.install

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class InstallEvent(
    val sessionId: Int,
    val status: Int,
    val message: String? = null,
    val otherPackage: String? = null
)

object SessionInstallBus {
    private val _events = MutableSharedFlow<InstallEvent>(replay = 1, extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    suspend fun publish(sessionId: Int, resultCode: Int, message: String? = null, other: String? = null) {
        _events.emit(InstallEvent(sessionId, resultCode, message, other))
    }
}