package no.nav.btnchat.sbs


import dev.nohus.autokonfig.types.BooleanSetting
import dev.nohus.autokonfig.types.StringSetting
import io.ktor.application.call
import io.ktor.features.BadRequestException
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.defaultResource
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import no.nav.btnchat.common.ChatContext
import no.nav.btnchat.common.Origin
import no.nav.btnchat.common.adt.Result.Failure
import no.nav.btnchat.common.adt.Result.Success
import no.nav.btnchat.common.chatserver.ChatDAO
import no.nav.btnchat.common.chatserver.ChatDTO
import no.nav.btnchat.common.chatserver.chatModule
import no.nav.btnchat.common.infrastructure.*
import no.nav.btnchat.common.infrastructure.Security.getUserActorType
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

val logger = LoggerFactory.getLogger("btn-chat.btn-sbs")

class Config(appname: String) : DbConfig(appname) {
    val enabledKafka by BooleanSetting(default = true)
    val kafkaBootstrapServers by StringSetting(default ="localhost:9092")
}

data class RequestData(val context: ChatContext)

fun main() {
    val appname = "btn-chat-sbs"
    val config = Config(appname)
    val dbConfig = DataSourceConfiguration(config)
    HttpServer.create(appname, 7076, Origin.SBS) { state ->
        val chatDao = ChatDAO(dbConfig.userDataSource(), state.origin)
        standardAppSetup(state, AuthConfig.UseMock("12345678910"))
        val chatserver = chatModule(
                state = state,
                bootstrapServers = config.kafkaBootstrapServers,
                chatDAO = chatDao
        )

        routing {
            route(state.appname) {
                static {
                    resources("webapp")
                    defaultResource("index.html", "webapp")
                }

                route("/api") {
                    get("/chat/{uuid}") {
                        val uuid = call.parameters["uuid"]
                        val chatId = uuid?.let(UUID::fromString) ?: throw BadRequestException("Invalid chatId: $uuid")

                        when (val chat = chatDao.getChat(chatId)) {
                            is Failure -> call.respond(HttpStatusCode.NotFound, chat.error)
                            is Success -> call.respond(chat.value)
                        }
                    }

                    post("/request") {
                        val user = getUserActorType(call)
                        val data: RequestData = call.receive()
                        val existingChat: ChatDTO? = chatDao.hasExistingChat(user, data.context)

                        if (existingChat != null) {
                            call.respond(existingChat)
                        } else {
                            val time = LocalDateTime.now()
                            val chat = chatDao.createChatRequest(user, time, data.context)
                            call.respond(chat)
                            chatserver.sendRequestChat(chat.chatId, time, user, data.context)
                        }
                    }
                }
            }
        }

        logger.info("App started...")
    }.start(wait = true)
}
