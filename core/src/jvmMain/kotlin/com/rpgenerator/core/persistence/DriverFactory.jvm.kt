package com.rpgenerator.core.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

actual class DriverFactory(private val databasePath: String = "rpgenerator.db") {
    actual fun createDriver(): SqlDriver {
        return JdbcSqliteDriver("jdbc:sqlite:$databasePath").also {
            GameDatabase.Schema.create(it)
        }
    }
}

actual fun createInMemoryDriver(): SqlDriver {
    return JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
        GameDatabase.Schema.create(it)
    }
}
