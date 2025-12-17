package com.rpgenerator.core.persistence

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(GameDatabase.Schema, context, "rpgenerator.db")
    }
}

actual fun createInMemoryDriver(): SqlDriver {
    throw UnsupportedOperationException("In-memory driver not supported on Android in tests - use instrumented tests")
}
