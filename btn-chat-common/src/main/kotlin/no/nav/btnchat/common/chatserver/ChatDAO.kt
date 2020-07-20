package no.nav.btnchat.common.chatserver

import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.btnchat.common.*
import no.nav.btnchat.common.adt.Result
import no.nav.btnchat.common.utils.execute
import no.nav.btnchat.common.utils.transactional
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

data class ChatDTO(
        val chatId: UUID,
        val requestTime: LocalDateTime,
        val approveTime: LocalDateTime?,
        val closeTime: LocalDateTime?,
        val userId: ActorId,
        val employeeId: Employee?,
        val context: ChatContext,
        val status: StatusEventType,
        val result: String?
) {

    companion object {
        fun fromRow(row: Row): ChatDTO = ChatDTO(
                chatId = UUID.fromString(row.string("chatId")),
                requestTime = row.localDateTime("requestTime"),
                approveTime = row.localDateTimeOrNull("approveTime"),
                closeTime = row.localDateTimeOrNull("closeTime"),
                userId = ActorId.fromString(row.string("userId")),
                employeeId = row.stringOrNull("employeeId")?.let(::Employee),
                context = ChatContext.valueOf(row.string("context")),
                status = StatusEventType.valueOf(row.string("status")),
                result = row.stringOrNull("result")
        )
    }
}

class ChatDAO(private val dataSource: DataSource, private val origin: Origin) {
    suspend fun hasExistingChat(user: UserActorType, context: ChatContext): ChatDTO? {
        return when (user) {
            is Anonymous -> null
            is Fnr -> transactional(dataSource) { tx ->
                queryOf(
                        "SELECT * FROM chat WHERE userId = ? AND context = ? AND status != ?",
                        user.value,
                        context.name,
                        StatusEventType.CLOSED.name
                )
                        .map(ChatDTO.Companion::fromRow)
                        .asSingle
                        .execute(tx)
                null
            }
            else -> null
        }
    }

    suspend fun createChatRequest(user: UserActorType, time: LocalDateTime, context: ChatContext): ChatDTO {
        return transactional(dataSource) { tx ->
            val chatId = UUID.randomUUID()

            queryOf(
                    "INSERT INTO chat(chatId, requestTime, userId, context, status) VALUES(?, ?, ?, ?, ?)",
                    chatId.toString(), Timestamp.valueOf(time), user.asActorId().value, context.name, StatusEventType.REQUESTED.name
            )
                    .asUpdate
                    .execute(tx)

            getChat(tx, chatId, false)!!
        }
    }

    suspend fun approveChatRequest(chatId: UUID, time: LocalDateTime, employee: Employee): Result<ChatDTO, String> {
        return transactional(dataSource) { tx ->
            val existingChat = getChat(tx = tx, chatId = chatId, selectForUpdate = true)
            if (existingChat == null) {
                Result.failure("Chat not found, chatId: $chatId")
            } else {
                queryOf(
                        "UPDATE chat SET approveTime = ?, employeeId = ?, status = ? WHERE chatId = ?",
                        Timestamp.valueOf(time), employee.value, StatusEventType.APPROVED.name, chatId.toString()
                )
                        .asUpdate
                        .execute(tx)

                Result.ofNotNull(getChat(tx, chatId, false), "Chat approval not updated, chatId: $chatId employee: ${employee.value}")
            }
        }
    }

    suspend fun closeChatRequest(chatId: UUID, time: LocalDateTime, employee: Employee): Result<ChatDTO, String> {
        return transactional(dataSource) { tx ->
            val existingChat = getChat(tx = tx, chatId = chatId, selectForUpdate = true)
            when {
                existingChat == null -> Result.failure("Chat not found, chatId: $chatId")
                existingChat.employeeId != employee -> Result.failure("Employee ($employee) not owner of chat $chatId")
                else -> {
                    queryOf(
                            "UPDATE chat SET closeTime = ?, status = ? WHERE chatId = ?",
                            Timestamp.valueOf(time), StatusEventType.CLOSED.name, chatId.toString()
                    )
                            .asUpdate
                            .execute(tx)

                    Result.ofNotNull(getChat(tx, chatId, false), "Chat closing not updated, chatId: $chatId")
                }
            }
        }
    }

    suspend fun canAccessChat(chatId: UUID, actorId: ActorId): Boolean {
        val chat: ChatDTO? = transactional(dataSource) { tx -> getChat(tx, chatId) }
        return if (chat == null) {
            false
        } else if (actorId is Anonymous && chat.userId is Anonymous) {
            true
        } else if (actorId is Employee && chat.employeeId == actorId) {
            true
        } else if (actorId is Fnr && chat.userId == actorId) {
            true
        } else {
            false
        }
    }

    suspend fun getChat(chatId: UUID): Result<ChatDTO, String> {
        return transactional(dataSource) { tx ->
            Result.ofNotNull(getChat(tx, chatId), "Could not find chatId: $chatId")
        }
    }

    private fun getChat(tx: TransactionalSession, chatId: UUID, selectForUpdate: Boolean = false): ChatDTO? {
        var query = "SELECT * FROM chat where chatId = ?"
        if (selectForUpdate) {
            query += " FOR UPDATE"
        }

        return queryOf("SELECT * FROM chat where chatId = ?", chatId.toString())
                .map(ChatDTO.Companion::fromRow)
                .asSingle
                .execute(tx)
    }

    suspend fun saveChatMessage(actorId: ActorId, chatId: UUID, messageId: UUID, time: LocalDateTime, wsMessage: WSMessage) {
        return transactional(dataSource) { tx ->
            queryOf(
                    "INSERT INTO chatdata(chatId, time, messageId, origin, actorId, eventType, eventData) VALUES(?, ?, ?, ?, ?, ?, ?)",
                    chatId.toString(), Timestamp.valueOf(time), messageId.toString(), origin.name, actorId.value, wsMessage.eventType.name, wsMessage.content
            )
                    .asUpdate
                    .execute(tx)

            return@transactional
        }
    }
}

