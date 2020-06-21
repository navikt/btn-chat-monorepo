package no.nav.btnchat.common.chatserver

import io.ktor.http.cio.websocket.Frame
import io.ktor.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.WebSocketServerSession
import no.nav.btnchat.common.utils.KafkaChatMessage
import no.nav.btnchat.common.utils.KafkaChatMessageType
import no.nav.btnchat.common.utils.KafkaUtils
import no.nav.btnchat.common.utils.toJson
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.*

val logger = LoggerFactory.getLogger("btn-chat.ChatServer")
class ChatServer(private val producer: KafkaProducer<UUID, KafkaChatMessage>) {
    private val sessions = mutableMapOf<String, MutableList<WebSocketServerSession>>()
    private val chat = mutableMapOf<String, MutableList<KafkaChatMessage>>()

    suspend fun joined(subject: String, chatId: String, session: DefaultWebSocketServerSession) {
        kafkaSend(KafkaChatMessage(
                timestamp = System.currentTimeMillis(),
                ident = subject,
                chatId = chatId,
                type = KafkaChatMessageType.JOINED
        ))

        sessions.putIfAbsent(chatId, mutableListOf())
        sessions[chatId]!!.add(session)

        logger.info("[ChatServer::joined] $subject $chatId")

        logger.info("[ChatServer::resending] $chatId ${chat[chatId]?.size ?: 0}")
        chat[chatId]?.forEach {
            session.outgoing.send(Frame.Text(it.toJson()))
        }
    }

    suspend fun leave(subject: String, chatId: String, session: DefaultWebSocketServerSession) {
        kafkaSend(KafkaChatMessage(
                timestamp = System.currentTimeMillis(),
                ident = subject,
                chatId = chatId,
                type = KafkaChatMessageType.LEAVED
        ))
        sessions[chatId]?.remove(session)
        logger.info("[ChatServer::leave] $subject $chatId")
    }

    suspend fun process(kafkaMessage: KafkaChatMessage) {
        sessions[kafkaMessage.chatId]
                ?.forEach {
                    it.outgoing.send(Frame.Text(kafkaMessage.toJson()))
                }

        chat.putIfAbsent(kafkaMessage.chatId, mutableListOf())
        chat[kafkaMessage.chatId]?.add(kafkaMessage)
        logger.info("[ChatServer::process] ${kafkaMessage.toJson()}")
    }

    suspend fun send(subject: String, chatId: String, content: String) {
        val message = KafkaChatMessage(
                timestamp = System.currentTimeMillis(),
                ident = subject,
                chatId = chatId,
                type = KafkaChatMessageType.MESSAGE,
                content = content
        )
        kafkaSend(message)
        logger.info("[ChatServer::send] ${message.toJson()}")
    }


    private fun kafkaSend(message: KafkaChatMessage) {
        producer.send(ProducerRecord(KafkaUtils.chatTopic, UUID.randomUUID(), message))
    }

}
