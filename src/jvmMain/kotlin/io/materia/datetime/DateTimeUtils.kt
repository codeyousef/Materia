package io.materia.datetime

import java.time.Instant

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun currentTimeString(): String = Instant.now().toString()
