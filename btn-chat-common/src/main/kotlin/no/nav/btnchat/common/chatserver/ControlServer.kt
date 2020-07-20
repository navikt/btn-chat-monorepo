package no.nav.btnchat.common.chatserver

import io.ktor.http.cio.websocket.Frame
import io.ktor.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.WebSocketServerSession
import no.nav.btnchat.common.*
import no.nav.btnchat.common.utils.toJson
import java.util.*

class ControlServer(private val origin: Origin) {
    private val sessions = mutableMapOf<UUID, WebSocketServerSession>()
    suspend fun process(kafkaMessage: KafkaMessage) {
        if (origin == Origin.SBS) {
            return
        }
        when (kafkaMessage) {
            is KafkaDataMessage -> Unit
            is KafkaChatRequestMessage -> send(kafkaMessage)
            is KafkaChatApproveMessage -> send(kafkaMessage)
            is KafkaChatCloseMessage -> send(kafkaMessage)
        }
    }

    fun connected(sessionId: UUID, session: DefaultWebSocketServerSession) {
        sessions[sessionId] = session
    }

    fun disconnected(sessionId: UUID) {
        sessions.remove(sessionId)
    }

    private suspend fun send(message: KafkaMessage): Unit {
        val wsMessage = Frame.Text(message.toJson())
        sessions.values.forEach {
            it.outgoing.send(wsMessage)
        }
    }
}
