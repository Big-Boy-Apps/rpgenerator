package com.rpgenerator.core.persistence

import app.cash.sqldelight.db.SqlDriver

expect class DriverFactory {
    fun createDriver(): SqlDriver
}

expect fun createInMemoryDriver(): SqlDriver
