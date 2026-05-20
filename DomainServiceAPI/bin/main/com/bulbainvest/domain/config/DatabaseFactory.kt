package com.bulbainvest.domain.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import javax.sql.DataSource

object DatabaseFactory {
    fun init(cfg: DbConfig): DataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = cfg.jdbcUrl
            username = cfg.user
            password = cfg.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        }
        val ds = HikariDataSource(hikariConfig)
        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .sqlMigrationPrefix("V")
            .sqlMigrationSeparator("__")
            .sqlMigrationSuffixes(".sql")
            .validateMigrationNaming(true)
            .load()
            .migrate()
        Database.connect(ds)
        return ds
    }
}
