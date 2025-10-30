package io.materia.datetime

import kotlin.js.Date

actual fun currentTimeMillis(): Long = Date.now().toLong()

actual fun currentTimeString(): String = Date().toISOString()
