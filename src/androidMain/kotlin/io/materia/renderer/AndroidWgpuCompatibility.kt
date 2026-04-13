package io.materia.renderer

import android.os.Build

private const val ANDROID_WGPU_REASON = "Android rendering is blocked by an upstream wgpu4k packaging issue. " +
    "The bundled Android backend expects java.lang.foreign.ValueLayout.Companion, " +
    "but Android resolves java.lang.foreign.ValueLayout without that field."

fun ensureAndroidWgpuRuntimeCompatible() {
    val valueLayoutClass = try {
        Class.forName("java.lang.foreign.ValueLayout")
    } catch (error: Throwable) {
        throw androidWgpuCompatibilityException(error)
    }

    val hasCompanionField = valueLayoutClass.declaredFields.any { it.name == "Companion" }
    if (!hasCompanionField) {
        throw androidWgpuCompatibilityException()
    }
}

fun Throwable.isAndroidWgpuCompatibilityFailure(): Boolean {
    val text = buildString {
        append(this@isAndroidWgpuCompatibilityFailure::class.qualifiedName.orEmpty())
        append('\n')
        append(message.orEmpty())
        cause?.let {
            append('\n')
            append(it::class.qualifiedName.orEmpty())
            append('\n')
            append(it.message.orEmpty())
        }
    }

    return this is LinkageError ||
        text.contains("ValueLayout.Companion") ||
        text.contains("ValueLayout\$Companion") ||
        text.contains("java.lang.foreign.ValueLayout")
}

fun androidWgpuCompatibilityException(
    cause: Throwable? = null
): RendererInitializationException.DeviceCreationFailedException {
    val reason = buildString {
        append(ANDROID_WGPU_REASON)
        append(" Upgrade or replace the Android wgpu backend to enable rendering.")
        cause?.message?.takeIf { it.isNotBlank() }?.let {
            append(" Original error: ")
            append(it)
        }
    }

    return RendererInitializationException.DeviceCreationFailedException(
        backend = BackendType.VULKAN,
        adapterInfo = Build.MODEL ?: "Android Device",
        reason = reason
    )
}