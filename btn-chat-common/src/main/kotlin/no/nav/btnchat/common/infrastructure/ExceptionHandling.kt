package no.nav.btnchat.common.infrastructure

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.features.StatusPages
import io.ktor.features.origin
import io.ktor.http.HttpStatusCode
import io.ktor.request.ApplicationRequest
import io.ktor.response.respond

fun StatusPages.Configuration.exceptionHandler() {
    exception<Throwable> { cause ->
        call.logErrorAndRespond(cause) { "An internal error occurred during routing" }
    }

    exception<IllegalArgumentException> { cause ->
        call.logErrorAndRespond(cause, HttpStatusCode.BadRequest) {
            "The request was either invalid or lacked required parameters"
        }
    }

}

fun StatusPages.Configuration.notFoundHandler() {
    status(HttpStatusCode.NotFound) {
        call.respond(
                HttpStatusCode.NotFound,
                HttpErrorResponse(
                        message = "The page or operation requested does not exist.",
                        code = HttpStatusCode.NotFound, url = call.request.url()
                )
        )
    }
}

private suspend inline fun ApplicationCall.logErrorAndRespond(
        cause: Throwable,
        status: HttpStatusCode = HttpStatusCode.InternalServerError,
        lazyMessage: () -> String
) {
    val message = lazyMessage()
    log.error(message, cause)
    val response = HttpErrorResponse(
            url = this.request.url(),
            cause = cause.toString(),
            message = message,
            code = status
    )
    log.error("Status Page Response: $response")
    this.respond(status, response)
}

internal data class HttpErrorResponse(
        val url: String,
        val message: String? = null,
        val cause: String? = null,
        val code: HttpStatusCode = HttpStatusCode.InternalServerError
)

internal fun ApplicationRequest.url(): String {
    val port = when (origin.port) {
        in listOf(80, 443) -> ""
        else -> ":${origin.port}"
    }
    return "${origin.scheme}://${origin.host}$port${origin.uri}"
}
