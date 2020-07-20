package no.nav.btnchat.common.utils

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.btnchat.common.KafkaMessage
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.UUIDDeserializer
import org.apache.kafka.common.serialization.UUIDSerializer
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.util.*

val logger = LoggerFactory.getLogger("btn-chat.KafkaUtils")
data class KafkaCredential(val username: String, val password: String) {
    override fun toString(): String {
        return "username '$username' password '*******'"
    }
}

private val defaultProducerConfig = Properties().apply {
    put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, UUIDSerializer::class.java)
    put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonSerializer::class.java)
}

private val defaultConsumerConfig = Properties().apply {
    put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
    put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, UUIDDeserializer::class.java)
    put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonDeserializer::class.java)
    put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
}
private val objectmapper = JacksonUtils.objectMapper

object KafkaUtils {
    val log = LoggerFactory.getLogger("btn-chat.KafkaUtils")
    public val chatTopic = "privat-btn-chat-ws"

    fun producerConfig(clientId: String, bootstrapServers: String, credentials: KafkaCredential? = null, properties: Properties = defaultProducerConfig): Properties {
        return Properties()
                .apply {
                    putAll(properties)
                    putAll(commonProperties(bootstrapServers, credentials))
                    put(ProducerConfig.CLIENT_ID_CONFIG, clientId)
                }
    }

    fun consumerConfig(groupId: String, clientId: String, bootstrapServers: String, credentials: KafkaCredential? = null, properties: Properties = defaultConsumerConfig): Properties {
        return Properties()
                .apply {
                    putAll(properties)
                    putAll(commonProperties(bootstrapServers, credentials))
                    put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
                    put(ConsumerConfig.CLIENT_ID_CONFIG, clientId)
                }
    }


    private fun commonProperties(bootstrapServers: String, credential: KafkaCredential? = null): Properties {
        return Properties().apply {
            put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            credential?.let { creds ->
                putAll(credentials(creds))
            }
        }
    }

    private fun credentials(credential: KafkaCredential): Properties {
        return Properties().apply {
            put(SaslConfigs.SASL_MECHANISM, "PLAIN")
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
            put(
                    SaslConfigs.SASL_JAAS_CONFIG,
                    "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${credential.username}\" password=\"${credential.password}\";"
            )

            val trustStoreLocation = System.getenv("NAV_TRUSTSTORE_PATH")
            trustStoreLocation?.let {
                try {
                    put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL")
                    put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, File(it).absolutePath)
                    put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, System.getenv("NAV_TRUSTSTORE_PASSWORD"))
                    log.info("Configured '${SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG}' location ")
                } catch (e: Exception) {
                    log.error("Failed to set '${SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG}' location ", e)
                }
            }
        }
    }

}

class JacksonSerializer : Serializer<KafkaMessage> {
    override fun serialize(topic: String?, data: KafkaMessage?): ByteArray {
        return objectmapper.writeValueAsBytes(data)
    }
}

class JacksonDeserializer : Deserializer<KafkaMessage?> {
    override fun deserialize(topic: String?, data: ByteArray?): KafkaMessage? {
        return try {
            data?.let(objectmapper::readValue)
        } catch (e: Throwable) {
            logger.error("Could not deserialize bytearray: $data", e)
            null
        }
    }
}

suspend fun <KEY, VALUE> KafkaConsumer<KEY, VALUE>.consumeFrom(topic: String, onMessage: suspend (ConsumerRecord<KEY, VALUE>) -> Unit) {
    val consumer = this
    consumer.subscribe(listOf(topic))
    withContext(Dispatchers.IO) {
        while (true) {
            val records = consumer.poll(Duration.ofMillis(10_000))
            for (record in records) {
                onMessage(record)
            }
        }
    }
}

operator fun <KEY, VALUE> ConsumerRecord<KEY, VALUE>.component1(): KEY = this.key()
operator fun <KEY, VALUE> ConsumerRecord<KEY, VALUE>.component2(): VALUE = this.value()
