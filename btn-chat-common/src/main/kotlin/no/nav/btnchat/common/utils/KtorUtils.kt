package no.nav.btnchat.common.utils

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.auth.principal
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.util.pipeline.PipelineContext
import io.ktor.websocket.WebSocketServerSession
import no.nav.btnchat.common.infrastructure.SubjectPrincipal

suspend fun PipelineContext<Unit, ApplicationCall>.withSubject(body: suspend (subject: String) -> Unit) {
    this.call.principal<SubjectPrincipal>()
            ?.subject
            ?.let { body(it) }
            ?: call.respond(HttpStatusCode.BadRequest)
}

suspend fun WebSocketServerSession.withSubject(body: suspend (subject: String) -> Unit ) {
    this.call.principal<SubjectPrincipal>()
            ?.subject
            ?.let { body(it) }
            ?: call.respond(io.ktor.http.HttpStatusCode.BadRequest)
}
