package com.rpgenerator.core.util

actual fun randomUUID(): String = java.util.UUID.randomUUID().toString()
