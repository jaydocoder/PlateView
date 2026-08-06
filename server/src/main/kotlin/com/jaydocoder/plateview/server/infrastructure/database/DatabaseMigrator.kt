package com.jaydocoder.plateview.server.infrastructure.database

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.util.AttributeKey
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway

internal data class DatabaseSettings(
    val jdbcUrl: String,
    val username: String,
    val password: String,
)

internal fun Application.configureDatabaseMigration() {
    val databaseSettings = databaseSettingsOrNull() ?: return

    val migrateOnStart = environment.config
        .propertyOrNull("database.migrateOnStart")
        ?.getString()
        ?.toBooleanStrictOrNull()
        ?: true

    if (migrateOnStart) {
        migrateDatabase(databaseSettings)
    }
}

internal fun Application.databaseSettingsOrNull(): DatabaseSettings? {
    val jdbcUrl = environment.config.propertyOrNull("database.jdbcUrl")?.getString()
    val username = environment.config.propertyOrNull("database.username")?.getString()
    val password = environment.config.propertyOrNull("database.password")?.getString()

    if (jdbcUrl == null && username == null && password == null) {
        return null
    }

    require(!jdbcUrl.isNullOrBlank()) { "数据库连接地址不能为空" }
    require(!username.isNullOrBlank()) { "数据库用户名不能为空" }
    require(password != null) { "数据库密码不能为空" }

    return DatabaseSettings(
        jdbcUrl = jdbcUrl,
        username = username,
        password = password,
    )
}

internal fun migrateDatabase(databaseSettings: DatabaseSettings) {
    Flyway.configure()
        .dataSource(
            databaseSettings.jdbcUrl,
            databaseSettings.username,
            databaseSettings.password,
        )
        .locations("classpath:db/migration")
        .load()
        .migrate()
}

internal val AuditLogWriterKey = AttributeKey<AuditLogWriter>("auditLogWriter")

internal fun Application.configureDatabaseRuntime() {
    val databaseSettings = databaseSettingsOrNull() ?: return
    val dataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = databaseSettings.jdbcUrl
            username = databaseSettings.username
            password = databaseSettings.password
            maximumPoolSize = 8
            minimumIdle = 1
            connectionTimeout = 5_000
            validationTimeout = 2_000
            poolName = "plateview-database"
        },
    )

    attributes.put(AuditLogWriterKey, JdbcAuditLogWriter(dataSource))
    monitor.subscribe(ApplicationStopped) {
        dataSource.close()
    }
}
