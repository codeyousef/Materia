package io.materia.util

import android.util.Base64
import kotlin.io.encoding.Base64 as KotlinBase64
import kotlin.io.encoding.ExperimentalEncodingApi

actual object Base64Compat {
    actual fun decode(value: String): ByteArray = try {
        Base64.decode(value, Base64.DEFAULT)
    } catch (error: RuntimeException) {
        if (error.isNotMocked()) {
            decodeWithKotlin(value)
        } else {
            throw error
        }
    }

    actual fun encode(bytes: ByteArray): String = try {
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (error: RuntimeException) {
        if (error.isNotMocked()) {
            encodeWithKotlin(bytes)
        } else {
            throw error
        }
    }
}

private fun RuntimeException.isNotMocked(): Boolean =
    message?.contains("not mocked", ignoreCase = true) == true

@OptIn(ExperimentalEncodingApi::class)
private fun decodeWithKotlin(value: String): ByteArray = KotlinBase64.decode(value)

@OptIn(ExperimentalEncodingApi::class)
private fun encodeWithKotlin(bytes: ByteArray): String = KotlinBase64.encode(bytes)
