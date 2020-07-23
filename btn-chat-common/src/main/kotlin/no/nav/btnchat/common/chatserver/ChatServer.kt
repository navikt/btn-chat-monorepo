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

class ChatServer(
        private val producer: KafkaProducer<UUID, KafkaMessage>,
        private val chatDao: ChatDAO,
        private val origin: Origin
) {
    private val sessions = mutableMapOf<UUID, MutableList<WebSocketServerSession>>()

    suspend fun onConnect(actorId: ActorId, chatId: UUID, session: DefaultWebSocketServerSession) {
        val time = LocalDateTime.now()
        val messageId = UUID.randomUUID()
        val chat = chatDao.getChatData(chatId)
        chatDao.saveChatMessage(actorId, chatId, messageId, time, WSMessage(DataEventType.CONNECTED))
        kafkaSend(KafkaDataMessage(
                chatId = chatId,
                time = time,
                messageId = messageId,
                origin = origin,
                actorId = actorId,
                eventType = DataEventType.CONNECTED
        ))

        sessions.putIfAbsent(chatId, mutableListOf())
        sessions[chatId]!!.add(session)

        logger.info("[ChatServer::joined] $actorId $chatId")
        logger.info("[ChatServer::resending] $chatId ${chat.messages.size}")
        chat.messages.forEach {
            session.outgoing.send(Frame.Text(it.toJson()))
        }
    }

    suspend fun onDisconnect(actorId: ActorId, chatId: UUID, session: DefaultWebSocketServerSession) {
        val time = LocalDateTime.now()
        val messageId = UUID.randomUUID()
        chatDao.saveChatMessage(actorId, chatId, messageId, time, WSMessage(DataEventType.DISCONNECTED))
        kafkaSend(KafkaDataMessage(
                chatId = chatId,
                time = time,
                messageId = messageId,
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

    suspend fun onMessage(actorId: ActorId, chatId: UUID, wsMessage: WSMessage) {
        val time = LocalDateTime.now()
        val messageId = UUID.randomUUID()

        chatDao.saveChatMessage(actorId, chatId, messageId, time, wsMessage)
        kafkaSend(KafkaDataMessage(
                chatId = chatId,
                time = time,
                messageId = messageId,
                origin = origin,
                actorId = actorId,
                eventType = wsMessage.eventType,
                eventData = wsMessage.content
        ))
    }



    private fun kafkaSend(message: KafkaMessage) {
        producer.send(ProducerRecord(KafkaUtils.chatTopic, UUID.randomUUID(), message))
        logger.info("[ChatServer::send] ${message.toJson()}")
    }
}
