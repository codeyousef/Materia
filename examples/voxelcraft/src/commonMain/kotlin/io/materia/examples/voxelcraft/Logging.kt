package io.materia.examples.voxelcraft

import io.materia.util.MateriaLogger

private const val TAG = "VoxelCraft"

internal fun logDebug(message: String) = MateriaLogger.debug(TAG, message)

internal fun logInfo(message: String) = MateriaLogger.info(TAG, message)

internal fun logWarn(message: String) = MateriaLogger.warn(TAG, message)

internal fun logError(message: String, throwable: Throwable? = null) =
    MateriaLogger.error(TAG, message, throwable)

