package no.nav.btnchat.common.chatserver

import io.ktor.http.cio.websocket.Frame
import io.ktor.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.WebSocketServerSession
import no.nav.btnchat.common.*
import no.nav.btnchat.common.utils.KafkaUtils
import no.nav.btnchat.common.utils.toJson
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

val logger = LoggerFactory.getLogger("btn-chat.ChatServer")

class ChatServer(private val producer: KafkaProducer<UUID, KafkaMessage>, private val origin: Origin) {
    private val sessions = mutableMapOf<UUID, MutableList<WebSocketServerSession>>()
    private val chat = mutableMapOf<UUID, MutableList<KafkaDataMessage>>()

    suspend fun connected(actorId: ActorId, chatId: UUID, session: DefaultWebSocketServerSession) {
        kafkaSend(KafkaDataMessage(
                chatId = chatId,
                time = LocalDateTime.now(),
                messageId = UUID.randomUUID(),
                origin = origin,
                actorId = actorId,
                eventType = DataEventType.CONNECTED
        ))

        sessions.putIfAbsent(chatId, mutableListOf())
        sessions[chatId]!!.add(session)

        logger.info("[ChatServer::joined] $actorId $chatId")

        logger.info("[ChatServer::resending] $chatId ${chat[chatId]?.size ?: 0}")
        chat[chatId]?.forEach {
            session.outgoing.send(Frame.Text(it.toJson()))
        }
    }

    suspend fun disconnected(actorId: ActorId, chatId: UUID, session: DefaultWebSocketServerSession) {
        kafkaSend(KafkaDataMessage(
                chatId = chatId,
                time = LocalDateTime.now(),
                messageId = UUID.randomUUID(),
                origin = origin,
                actorId = actorId,
                eventType = DataEventType.DISCONNECTED
        ))

        sessions[chatId]?.remove(session)
        logger.info("[ChatServer::leave] $actorId $chatId")
    }

    suspend fun process(kafkaMessage: KafkaMessage) {
        if (kafkaMessage is KafkaDataMessage) {
            sessions[kafkaMessage.chatId]
                    ?.forEach {
                        it.outgoing.send(Frame.Text(kafkaMessage.toJson()))
                    }

            chat.putIfAbsent(kafkaMessage.chatId, mutableListOf())
            chat[kafkaMessage.chatId]?.add(kafkaMessage)
            logger.info("[ChatServer::process] ${kafkaMessage.toJson()}")
        }
    }

    suspend fun sendRequestChat(chatId: UUID, time: LocalDateTime, user: UserActorType, context: ChatContext) {
        kafkaSend(KafkaChatRequestMessage(chatId, time, user, context))
    }

    suspend fun sendApproveChat(chatId: UUID, time: LocalDateTime, employee: Employee) {
        kafkaSend(KafkaChatApproveMessage(chatId, time, employee))
    }

    suspend fun sendCloseChat(chatId: UUID, time: LocalDateTime) {
        kafkaSend(KafkaChatCloseMessage(chatId, time))
    }

    suspend fun sendMessage(actorId: ActorId, chatId: UUID, messageId: UUID, time: LocalDateTime, wsMessage: WSMessage) {
        val message = KafkaDataMessage(
                chatId = chatId,
                time = time,
                messageId = messageId,
                origin = origin,
                actorId = actorId,
                eventType = wsMessage.eventType.toDataEventType(),
                eventData = wsMessage.content
        )
        kafkaSend(message)
    }



    private fun kafkaSend(message: KafkaMessage) {
        producer.send(ProducerRecord(KafkaUtils.chatTopic, UUID.randomUUID(), message))
        logger.info("[ChatServer::send] ${message.toJson()}")
    }
}
