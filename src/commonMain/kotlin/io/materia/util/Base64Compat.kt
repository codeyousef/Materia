package io.materia.util

/**
 * Multiplatform Base64 helpers that avoid relying on JDK APIs not available
 * on older Android API levels. Android's unit test environment targets API 24,
 * which predates `java.util.Base64`, so we provide expect/actual shims.
 */
expect object Base64Compat {
    fun decode(value: String): ByteArray
    fun encode(bytes: ByteArray): String
}
