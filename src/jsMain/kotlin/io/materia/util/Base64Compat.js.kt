package io.materia.util

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
actual object Base64Compat {
    actual fun decode(value: String): ByteArray = Base64.decode(value)

    actual fun encode(bytes: ByteArray): String = Base64.encode(bytes)
}
