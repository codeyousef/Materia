package io.materia.util

import java.util.Base64

actual object Base64Compat {
    actual fun decode(value: String): ByteArray =
        Base64.getDecoder().decode(value)

    actual fun encode(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)
}
