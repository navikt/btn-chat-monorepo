package no.nav.btnchat.common.chatserver

import io.ktor.application.Application
import io.ktor.application.ApplicationStopping
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.BadRequestException
import io.ktor.http.cio.websocket.*
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import no.nav.btnchat.common.KafkaMessage
import no.nav.btnchat.common.Origin
import no.nav.btnchat.common.WSMessage
import no.nav.btnchat.common.infrastructure.ApplicationState
import no.nav.btnchat.common.infrastructure.Security.getActorId
import no.nav.btnchat.common.utils.*
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import java.io.File
import java.time.Duration
import java.util.*

fun readFileAsText(fileName: String) = File(fileName).readText(Charsets.UTF_8)
        .also { logger.info("Rest file: $fileName Length: ${it.length}") }

fun Application.chatModule(state: ApplicationState, bootstrapServers: String, chatDAO: ChatDAO): ChatServer {
    val credentials: KafkaCredential? = try {
        val serviceuserUsername = readFileAsText("/var/run/secrets/nais.io/serviceuser/username")
        val serviceuserPassword = readFileAsText("/var/run/secrets/nais.io/serviceuser/password")
        KafkaCredential(serviceuserUsername, serviceuserPassword)
    } catch (e: Exception) {
        logger.error("Could not create kafka-credentials", e)
        null
    }

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(60)
    }

    val producer = KafkaProducer<UUID, KafkaMessage>(KafkaUtils.producerConfig(
            clientId = "${state.appname}-producer",
            bootstrapServers = bootstrapServers,
            credentials = credentials
    ))

    val chatserver = ChatServer(producer, chatDAO, state.origin)
    val controlServer = ControlServer(state.origin)

    val consumerJob = async(Dispatchers.IO) {
        val consumer = KafkaConsumer<UUID, KafkaMessage>(KafkaUtils.consumerConfig(
                groupId = UUID.randomUUID().toString(),
                clientId = "${state.appname}-consumer",
                bootstrapServers = bootstrapServers,
                credentials = credentials
        ))

        consumer.consumeFrom(KafkaUtils.chatTopic) { (_, value) ->
            chatserver.process(value)
            controlServer.process(value)
        }

        environment.monitor.subscribe(ApplicationStopping) {
            consumer.close()
        }
        return@async consumer
    }
    environment.monitor.subscribe(ApplicationStopping) {
        consumerJob.cancel()
    }

    routing {
        route(state.appname) {
            route("/api") {
                authenticate {
                    if (state.origin == Origin.FSS) {
                        get("/control") {
//                            call.respond(chatDAO.getStatus())
                        }

                        webSocket("/control") {
                            val sessionId = UUID.randomUUID()
                            val actorId = getActorId(call)

                            try {
                                controlServer.connected(sessionId, this)
                            } catch (e: Throwable) {
                                logger.error("Websocket error", e)
                            } finally {
                                controlServer.disconnected(sessionId)
                            }
                        }
                    }
                }

                webSocket("/chat/{chatId}") {
                    val actorId = getActorId(call)
                    logger.info("Subject: $actorId")
                    val chatId = (call.parameters["chatId"] ?: throw BadRequestException("No chatId found")).toUUID()

                    if (!chatDAO.canAccessChat(chatId, actorId)) {
                        this.close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "User ${actorId.value} cannot access $chatId"))
                    }

                    try {
                        chatserver.onConnect(actorId, chatId, this)

                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    val text = frame.readText()
                                    try {
                                        val wsMessage: WSMessage = text.fromJson()
                                        chatserver.onMessage(actorId, chatId, wsMessage)
                                    } catch (e: Exception) {
                                        logger.error("Error parsing WSmessage: $text", e)
                                    }
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        logger.error("Websocket error", e)
                    } finally {
                        chatserver.onDisconnect(actorId, chatId, this)
                    }
                }
            }

        }
    }

    return chatserver
}
