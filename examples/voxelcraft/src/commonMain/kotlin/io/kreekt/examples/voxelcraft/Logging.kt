package io.kreekt.examples.voxelcraft

import io.kreekt.util.KreektLogger

private const val TAG = "VoxelCraft"

internal fun logDebug(message: String) = KreektLogger.debug(TAG, message)

internal fun logInfo(message: String) = KreektLogger.info(TAG, message)

internal fun logWarn(message: String) = KreektLogger.warn(TAG, message)

internal fun logError(message: String, throwable: Throwable? = null) =
    KreektLogger.error(TAG, message, throwable)

