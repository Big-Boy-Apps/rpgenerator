package com.rpgenerator.core.util

import platform.Foundation.NSUUID

actual fun randomUUID(): String = NSUUID().UUIDString()
