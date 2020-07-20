package no.nav.btnchat.common.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.features.CORS
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.jackson.JacksonConverter
import io.ktor.metrics.dropwizard.DropwizardMetrics
import io.ktor.request.path
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.addShutdownHook
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.prometheus.client.dropwizard.DropwizardExports
import no.nav.btnchat.common.Origin
import no.nav.btnchat.common.utils.JacksonUtils
import org.slf4j.event.Level

data class ApplicationState(
        val appname: String,
        val port: Int,
        val origin: Origin,
        var running: Boolean = true,
        var initialized: Boolean = false
) {
    fun start() {
        initialized = true
    }

    fun stop() {
        initialized = false
    }
}

object HttpServer {
    fun create(appname: String, port: Int, origin: Origin, config: Application.(state: ApplicationState) -> Unit): ApplicationEngine {
        val applicationState = ApplicationState(appname, port, origin)

        val applicationServer = embeddedServer(Netty, port) {
            config(this, applicationState)

            applicationState.start()
        }

        applicationServer.addShutdownHook {
            log.info("Shutdown hook called, shutting down gracefully")
            applicationState.stop()
            applicationServer.stop(5000, 5000)
        }

        return applicationServer
    }
}

fun Application.naisApplication(applicationState: ApplicationState) {
    routing {
        route(applicationState.appname) {
            naisRoutes(readinessCheck = { applicationState.initialized }, livenessCheck = { applicationState.running })
        }
    }
}

fun Application.statusPages() {
    install(StatusPages) {
        notFoundHandler()
        exceptionHandler()
    }
}

fun Application.cors(hosts: List<String> = listOf("*"),  methods: List<HttpMethod> = listOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Delete)) {
    install(CORS) {
        allowCredentials = true
        hosts.forEach { host(it) }
        methods.forEach { method(it) }
    }
}

sealed class AuthConfig {
    class UseMock(val ident: String) : AuthConfig()
    class JwksUrl(val url: String) : AuthConfig()
}
fun Application.authentication(config: AuthConfig) {
    install(Authentication) {
        when (config) {
            is AuthConfig.UseMock -> setupMock(SubjectPrincipal(config.ident))
            is AuthConfig.JwksUrl -> setupJWT(config.url)
        }
    }
}

fun Application.jsonSupport(objectMapper: ObjectMapper) {
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(objectMapper))
    }
}
fun Application.callLogging(appname: String) {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/$appname/api") }
        mdc("userId", Security::getSubject)
    }
}

fun Application.metrics() {
    install(DropwizardMetrics) {
        io.prometheus.client.CollectorRegistry.defaultRegistry.register(DropwizardExports(registry))
    }
}

fun Application.standardAppSetup(applicationState: ApplicationState, authConfig: AuthConfig) {
    naisApplication(applicationState)
    statusPages()
    cors()
    authentication(authConfig)
    jsonSupport(JacksonUtils.objectMapper)
    callLogging(applicationState.appname)
    metrics()
}

