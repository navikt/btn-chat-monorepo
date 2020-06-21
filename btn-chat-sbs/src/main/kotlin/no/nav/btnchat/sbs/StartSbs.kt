package no.nav.btnchat.sbs


import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import no.nav.btnchat.common.chatserver.chatModule
import no.nav.btnchat.common.infrastructure.AuthConfig
import no.nav.btnchat.common.infrastructure.HttpServer
import no.nav.btnchat.common.infrastructure.standardAppSetup
import org.slf4j.LoggerFactory

val logger = LoggerFactory.getLogger("btn-chat.btn-sbs")
val bootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS") ?: "localhost:9092"
fun main () {
    HttpServer.create("btn-chat-sbs", 7076) { state ->
        standardAppSetup(state, AuthConfig.UseMock("12345678910"))
        chatModule(
                state = state,
                bootstrapServers = bootstrapServers
        )

        routing {
            route(state.appname) {
                get {
                    call.respond("SBS-app")
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