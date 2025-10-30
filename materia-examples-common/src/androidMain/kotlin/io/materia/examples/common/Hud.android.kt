package io.materia.examples.common

import android.util.Log

private const val TAG = "MateriaHud"

actual fun platformRender(lines: List<String>) {
    Log.d(TAG, lines.joinToString(separator = " | "))
}
