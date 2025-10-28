package io.kreekt.examples.common

import android.util.Log

private const val TAG = "KreeKtHud"

actual fun platformRender(lines: List<String>) {
    Log.d(TAG, lines.joinToString(separator = " | "))
}
