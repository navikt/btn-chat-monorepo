package no.nav.btnchat.fss

import dev.nohus.autokonfig.AutoKonfig
import dev.nohus.autokonfig.getKeySource
import no.nav.btnchat.common.infrastructure.DataSourceConfiguration

fun main() {
    val appname = "btn-chat-fss"
    val config = Config(appname)

    println(config.kafkaBootstrapServers)
    println(AutoKonfig.getKeySource("kafkaBootstrapServers"))

//    val dbConfig = DataSourceConfiguration(config)
//    DataSourceConfiguration.migrateDb(config, dbConfig.adminDataSource())
}
