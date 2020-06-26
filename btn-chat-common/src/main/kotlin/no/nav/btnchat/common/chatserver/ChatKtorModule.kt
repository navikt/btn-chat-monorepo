package no.nav.btnchat.common.chatserver

import io.ktor.application.Application
import io.ktor.application.ApplicationStopping
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.BadRequestException
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.pingPeriod
import io.ktor.http.cio.websocket.readText
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import no.nav.btnchat.common.infrastructure.ApplicationState
import no.nav.btnchat.common.utils.*
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import java.io.File
import java.time.Duration
import java.util.*

fun readFileAsText(fileName: String) = File(fileName).readText(Charsets.UTF_8)
        .also { logger.info("Rest file: $fileName Length: ${it.length}") }

fun Application.chatModule(state: ApplicationState, bootstrapServers: String) {
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

    val producer = KafkaProducer<UUID, KafkaChatMessage>(KafkaUtils.producerConfig(
            clientId = "${state.appname}-producer",
            bootstrapServers = bootstrapServers,
            credentials = credentials
    ))
    val chatserver = ChatServer(producer)

    val consumerJob = async(Dispatchers.IO) {
        val consumer = KafkaConsumer<UUID, KafkaChatMessage>(KafkaUtils.consumerConfig(
                groupId = UUID.randomUUID().toString(),
                clientId = "${state.appname}-consumer",
                bootstrapServers = bootstrapServers,
                credentials = credentials
        ))

        consumer.consumeFrom(KafkaUtils.chatTopic) { (_, value) -> chatserver.process(value) }

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
            authenticate {
                route("/api") {
                    webSocket("/chat/{chatId}") {
                        withSubject { subject ->
                            logger.info("Subject: $subject")
                            val chatId = call.parameters["chatId"] ?: throw BadRequestException("No chatId found")
                            try {
                                chatserver.joined(subject, chatId, this)

                                for (frame in incoming) {
                                    when (frame) {
                                        is Frame.Text -> {
                                            chatserver.send(subject, chatId, frame.readText())
                                        }
                                    }
                                }

                            } catch (e: Throwable) {
                                logger.error("Websocket error", e)
                            } finally {
                                chatserver.leave(subject, chatId, this)
                            }
                        }
                    }
                }
            }
        }
    }
}
