package no.nav.btnchat.fss

import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.http.content.defaultResource
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import no.nav.btnchat.common.chatserver.chatModule
import no.nav.btnchat.common.infrastructure.AuthConfig
import no.nav.btnchat.common.infrastructure.HttpServer
import no.nav.btnchat.common.infrastructure.standardAppSetup
import org.slf4j.LoggerFactory
import java.io.File

val logger = LoggerFactory.getLogger("btn-chat.btn-fss")
fun readFileAsText(fileName: String) = File(fileName).readText(Charsets.UTF_8)
        .also { logger.info("Rest file: $fileName Length: ${it.length}") }

object config {
    val enabledKafka = (System.getenv("ENABLED_KAFKA") ?: "true").toBoolean()
    val bootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS") ?: "localhost:9092"
    val serviceuserUsername = readFileAsText("/var/run/secrets/nais.io/serviceuser/username")
    val serviceuserPassword = readFileAsText("/var/run/secrets/nais.io/serviceuser/password")
}

fun main() {
    HttpServer.create("btn-chat-fss", 7075) { state ->
        standardAppSetup(state, AuthConfig.UseMock("Z999999"))
        if (config.enabledKafka) {
            chatModule(
                    state = state,
                    bootstrapServers = config.bootstrapServers
            )
        }

        routing {
            route(state.appname) {
                static {
                    resources("webapp")
                    defaultResource("index.html", "webapp")
                }

                authenticate {
                    route("/api") {
                        get("/test") {
                            call.respond("Test ok")
                        }
                    }
                }
            }
        }
        logger.info("App started...")
    }.start(wait = true)
}
