package io.materia.util

import android.util.Log

private const val TAG = "Materia"

actual object console {
    actual fun log(message: String) {
        Log.i(TAG, message)
    }

    actual fun warn(message: String) {
        Log.w(TAG, message)
    }

    actual fun error(message: String) {
        Log.e(TAG, message)
    }
}
