package com.rpgenerator.core.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class DriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(GameDatabase.Schema, "rpgenerator.db")
    }
}

actual fun createInMemoryDriver(): SqlDriver {
    return NativeSqliteDriver(GameDatabase.Schema, ":memory:")
}
