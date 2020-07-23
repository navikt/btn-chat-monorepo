package no.nav.btnchat.common

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.btnchat.common.utils.JacksonUtils
import no.nav.btnchat.common.utils.fromJson
import java.time.LocalDateTime
import java.util.*

const val anonymous = "Anonymous"

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "subtype")
@JsonSubTypes(
        JsonSubTypes.Type(value = Fnr::class, name = "Fnr"),
        JsonSubTypes.Type(value = Employee::class, name = "Employee"),
        JsonSubTypes.Type(value = Anonymous::class, name = anonymous)
)
sealed class ActorId(open val value: String) {
    val subtype = this::class.java.simpleName

    companion object {
        fun fromString(value: String): ActorId {
            return when {
                value == anonymous -> Anonymous()
                value.length == 11 -> Fnr(value)
                else -> Employee(value)
            }
        }
    }
}

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "subtype")
@JsonSubTypes(
        JsonSubTypes.Type(value = Fnr::class, name = "Fnr"),
        JsonSubTypes.Type(value = Anonymous::class, name = anonymous)
)
interface UserActorType {
    fun asActorId(): ActorId = this as ActorId
    companion object {
        fun fromString(value: String): UserActorType {
            if (value == anonymous) {
                return Anonymous()
            }
            return Fnr(value)
        }
    }
}
data class Fnr(override val value: String) : ActorId(value), UserActorType
data class Employee(override val value: String) : ActorId(value)
class Anonymous : ActorId(anonymous), UserActorType

enum class Origin {
    SBS, FSS
}

enum class StatusEventType {
    REQUESTED, APPROVED, CLOSED
}

enum class DataEventType {
    CONNECTED, DISCONNECTED, MESSAGE
}

enum class ChatContext {
    ARBD, FMLI, HJLPM, PENS, OVRG
}

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "subtype")
@JsonSubTypes(
        JsonSubTypes.Type(value = KafkaChatRequestMessage::class, name = "KafkaChatRequestMessage"),
        JsonSubTypes.Type(value = KafkaChatApproveMessage::class, name = "KafkaChatApproveMessage"),
        JsonSubTypes.Type(value = KafkaChatCloseMessage::class, name = "KafkaChatCloseMessage"),
        JsonSubTypes.Type(value = KafkaDataMessage::class, name = "KafkaDataMessage")
)
sealed class KafkaMessage {
    val subtype = this::class.java.simpleName
}

data class KafkaChatRequestMessage(
        val chatId: UUID,
        val time: LocalDateTime,
        val userId: UserActorType,
        val context: ChatContext
): KafkaMessage() {
    val eventType = StatusEventType.REQUESTED
}
data class KafkaChatApproveMessage(
        val chatId: UUID,
        val time: LocalDateTime,
        val employee: Employee
): KafkaMessage() {
    val eventType = StatusEventType.APPROVED
}
data class KafkaChatCloseMessage(
        val chatId: UUID,
        val time: LocalDateTime
): KafkaMessage() {
    val eventType = StatusEventType.CLOSED
}

data class KafkaDataMessage(
        val chatId: UUID,
        val time: LocalDateTime,
        val messageId: UUID,
        val origin: Origin,
        val actorId: ActorId,
        val eventType: DataEventType,
        val eventData: String? = null,
        val transient: Boolean = false
) : KafkaMessage()


data class WSMessage(
        val eventType: DataEventType,
        val content: String? = null
)

fun main() {
    val requestMessage = KafkaChatRequestMessage(
            UUID.randomUUID(),
            LocalDateTime.now(),
            Anonymous(),
            ChatContext.ARBD
    )
    val approveMessage = KafkaChatApproveMessage(
            UUID.randomUUID(),
            LocalDateTime.now(),
            Employee("Z999999")
    )
    val closeMessage = KafkaChatCloseMessage(
            UUID.randomUUID(),
            LocalDateTime.now()
    )

    val dataMessage = KafkaDataMessage(
            UUID.randomUUID(),
            LocalDateTime.now(),
            UUID.randomUUID(),
            Origin.FSS,
            Employee("Z99999"),
            DataEventType.MESSAGE,
            "Melding til bruker"
    )

//    val serialized = JacksonUtils.objectMapper.writeValueAsString(dataMessage)
//    println(serialized)
//    val deserialized = serialized.fromJson<KafkaMessage>()
//    println(deserialized)


    val originalList: List<KafkaMessage> = listOf(requestMessage, approveMessage, closeMessage, dataMessage)
    val serializedList = JacksonUtils.objectMapper.writeValueAsString(originalList)
    println(serializedList)
    val deserializedList = serializedList.fromJson<List<KafkaMessage>>()
    println(deserializedList)
}
