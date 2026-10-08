package app.flicky.install

import android.app.PendingIntent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.ConcurrentHashMap

data class InstallEvent(
    val sessionId: Int,
    val status: Int,
    val message: String? = null,
    val otherPackage: String? = null,
    val confirmIntent: PendingIntent? = null
)

object SessionInstallBus {
    private val flows = ConcurrentHashMap<Int, MutableSharedFlow<InstallEvent>>()

    fun events(sessionId: Int): SharedFlow<InstallEvent> =
        flows.computeIfAbsent(sessionId) {
            MutableSharedFlow(
                replay = 1,
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        }

    fun publish(event: InstallEvent) {
        flows[event.sessionId]?.tryEmit(event)
    }

    fun publish(sessionId: Int, resultCode: Int, message: String? = null, other: String? = null) {
        publish(InstallEvent(sessionId, resultCode, message, other))
    }

    fun release(sessionId: Int) {
        flows.remove(sessionId)
    }
}
