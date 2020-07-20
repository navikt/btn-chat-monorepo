package no.nav.btnchat.common.infrastructure

import AutoKonfigAware
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.nohus.autokonfig.types.StringSetting
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import javax.sql.DataSource

open class DbConfig(val appname: String) : AutoKonfigAware() {
    val jdbcUrl by StringSetting(default = "jdbc:postgresql://localhost:7050/btn-chat")
    val vaultMountpath by StringSetting(default = "")
}

class DataSourceConfiguration(val env: DbConfig) {
    private val log = LoggerFactory.getLogger("btn-chat.DataSourceConfiguration")
    private var userDataSource = createDatasource("user")
    private var adminDataSource = createDatasource("admin")

    fun userDataSource() = userDataSource
    fun adminDataSource() = adminDataSource

    private fun createDatasource(user: String): DataSource {
        val mountPath = env.vaultMountpath
        val config = HikariConfig()
        config.jdbcUrl = env.jdbcUrl
        config.minimumIdle = 0
        config.maximumPoolSize = 4
        config.connectionTimeout = 5000
        config.maxLifetime = 30000
        config.isAutoCommit = false

        log.info("Creating DataSource to: ${config.jdbcUrl}")

        if (env.vaultMountpath.isEmpty()) {
            config.username = env.appname
            config.password = env.appname
            return HikariDataSource(config)
                    .also {
                        val dbUser = dbRole(env.appname, "user")
                        it.connection.prepareStatement("SET ROLE '$dbUser'").execute()
                    }
        }

        return HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
                config,
                mountPath,
                dbRole(env.appname, user)
        )
    }

    companion object {
        private fun dbRole(appname: String, user: String): String = "$appname-$user"

        fun migrateDb(env: DbConfig, dataSource: DataSource) {
            Flyway
                    .configure()
                    .dataSource(dataSource)
                    .also {
                        val dbUser = dbRole(env.appname, "admin")
                        it.initSql("SET ROLE '$dbUser'")
                    }
                    .load()
                    .migrate()
        }
    }
}
