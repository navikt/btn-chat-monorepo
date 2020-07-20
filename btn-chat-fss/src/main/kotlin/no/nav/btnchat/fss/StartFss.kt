package no.nav.btnchat.fss

import dev.nohus.autokonfig.types.BooleanSetting
import dev.nohus.autokonfig.types.StringSetting
import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.features.BadRequestException
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.defaultResource
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import no.nav.btnchat.common.Employee
import no.nav.btnchat.common.Origin
import no.nav.btnchat.common.adt.Result
import no.nav.btnchat.common.chatserver.ChatDAO
import no.nav.btnchat.common.chatserver.chatModule
import no.nav.btnchat.common.infrastructure.*
import no.nav.btnchat.common.utils.withSubject
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

val logger = LoggerFactory.getLogger("btn-chat.btn-fss")

class Config(appname: String) : DbConfig(appname) {
    val enabledKafka by BooleanSetting(default = true)
    val kafkaBootstrapServers by StringSetting(default ="localhost:9092")
}

fun main() {
    val appname = "btn-chat-fss"
    val config = Config(appname)
    val dbConfig = DataSourceConfiguration(config)
    DataSourceConfiguration.migrateDb(config, dbConfig.adminDataSource())

    HttpServer.create(appname, 7075, Origin.FSS) { state ->
        val chatDao = ChatDAO(dbConfig.userDataSource(), state.origin)
        standardAppSetup(state, AuthConfig.UseMock("Z999999"))
        val chatServer = chatModule(
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

                authenticate {
                    route("/api") {
                        post("/request/{uuid}/approve") {
                            withSubject { subject ->
                                val employee = Employee(subject)
                                val uuid = call.parameters["uuid"]
                                val chatId = uuid?.let(UUID::fromString) ?: throw BadRequestException("Invalid chatId: $uuid")

                                val time = LocalDateTime.now()

                                when (val chat = chatDao.approveChatRequest(chatId, time, employee)) {
                                    is Result.Failure -> call.respond(HttpStatusCode.NotFound, chat.error)
                                    is Result.Success -> call.respond(chat.value)
                                }
                                chatServer.sendApproveChat(chatId, time, employee)
                            }
                        }

                        post("/request/{uuid}/close") {
                            withSubject { subject ->
                                val employee = Employee(subject)
                                val uuid = call.parameters["uuid"]
                                val chatId = uuid?.let(UUID::fromString) ?: throw BadRequestException("Invalid chatId: $uuid")

                                val time = LocalDateTime.now()

                                when (val chat = chatDao.closeChatRequest(chatId, time, employee)) {
                                    is Result.Failure -> call.respond(HttpStatusCode.NotFound, chat.error)
                                    is Result.Success -> call.respond(chat.value)
                                }
                                chatServer.sendCloseChat(chatId, time)
                            }
                        }
                    }
                }
            }
        }
        logger.info("App started...")
    }.start(wait = true)
}
