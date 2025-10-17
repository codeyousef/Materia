package io.kreekt.renderer.webgpu

import io.kreekt.renderer.gpu.GpuDevice
import io.kreekt.renderer.gpu.unwrapHandle

/**
 * Resource descriptor for tracking recreatable resources.
 */
sealed class ResourceDescriptor {
    data class Buffer(val descriptor: BufferDescriptor) : ResourceDescriptor()
    data class Texture(val descriptor: TextureDescriptor) : ResourceDescriptor()
    data class Pipeline(val descriptor: RenderPipelineDescriptor) : ResourceDescriptor()
}

/**
 * Context loss recovery manager.
 * T035: Automatically recovers from GPU context loss by recreating resources.
 *
 * Handles driver crashes, tab backgrounding, and power management events.
 */
class ContextLossRecovery {
    private val trackedResources = mutableListOf<ResourceDescriptor>()
    private var isRecovering = false
    private var lossCount = 0

    // Callbacks
    var onContextLost: (() -> Unit)? = null
    var onContextRestored: (() -> Unit)? = null

    /**
     * Tracks a resource for automatic recovery.
     * @param descriptor Resource descriptor containing creation parameters
     */
    fun track(descriptor: ResourceDescriptor) {
        trackedResources.add(descriptor)
    }

    /**
     * Tracks multiple resources.
     */
    fun trackAll(descriptors: List<ResourceDescriptor>) {
        trackedResources.addAll(descriptors)
    }

    /**
     * Handles context loss event.
     */
    fun handleContextLoss() {
        if (isRecovering) return

        lossCount++
        isRecovering = true
        console.warn("GPU context lost (event #$lossCount)")
        onContextLost?.invoke()
    }

    /**
     * Recovers all tracked resources with a new device.
     * @param device New GPU device
     * @return Result indicating success or failure
     */
    suspend fun recover(device: GpuDevice): io.kreekt.core.Result<RecoveryStats> {
        if (!isRecovering) {
            return io.kreekt.core.Result.Success(RecoveryStats(0, 0, 0, 0))
        }

        console.info("Starting context recovery: ${trackedResources.size} resources...")

        var buffersRecreated = 0
        var texturesRecreated = 0
        var pipelinesRecreated = 0
        var failures = 0

        val rawDevice = device.unwrapHandle() as? GPUDevice ?: return io.kreekt.core.Result.Error(
            "Unable to unwrap WebGPU device for recovery",
            IllegalStateException("GPU device unavailable")
        )

        // Recreate all tracked resources
        trackedResources.forEach { descriptor ->
            try {
                when (descriptor) {
                    is ResourceDescriptor.Buffer -> {
                        val buffer = WebGPUBuffer(device, descriptor.descriptor)
                        buffer.create()
                        buffersRecreated++
                    }
                    is ResourceDescriptor.Texture -> {
                        val texture = WebGPUTexture(rawDevice, descriptor.descriptor)
                        texture.create()
                        texturesRecreated++
                    }
                    is ResourceDescriptor.Pipeline -> {
                        val pipeline = WebGPUPipeline(rawDevice, descriptor.descriptor)
                        pipeline.create()
                        pipelinesRecreated++
                    }
                }
            } catch (e: Exception) {
                console.error("Failed to recreate resource: ${e.message}")
                failures++
            }
        }

        val stats = RecoveryStats(
            buffersRecreated = buffersRecreated,
            texturesRecreated = texturesRecreated,
            pipelinesRecreated = pipelinesRecreated,
            failures = failures
        )

        isRecovering = false

        if (failures > 0) {
            console.warn("Context recovery completed with $failures failures")
            return io.kreekt.core.Result.Error(
                "Recovery completed with $failures failures",
                RuntimeException("Recovery completed with $failures failures")
            )
        } else {
            console.info("Context recovery successful: $stats")
            onContextRestored?.invoke()
            return io.kreekt.core.Result.Success(stats)
        }
    }

    /**
     * Clears all tracked resources.
     */
    fun clear() {
        trackedResources.clear()
        lossCount = 0
        isRecovering = false
    }

    /**
     * Gets the number of tracked resources.
     */
    fun getTrackedCount(): Int = trackedResources.size

    /**
     * Gets the number of context loss events.
     */
    fun getLossCount(): Int = lossCount

    /**
     * Checks if currently recovering.
     */
    fun isRecovering(): Boolean = isRecovering

    /**
     * Monitors a GPU device for context loss.
     * @param device GPU device to monitor
     */
    suspend fun monitorDevice(device: GPUDevice) {
        try {
            // Wait for device.lost promise
            device.lost.await<dynamic>()
            handleContextLoss()
        } catch (e: Exception) {
            console.error("Error monitoring device loss: ${e.message}")
        }
    }
}

/**
 * Recovery statistics.
 */
data class RecoveryStats(
    val buffersRecreated: Int,
    val texturesRecreated: Int,
    val pipelinesRecreated: Int,
    val failures: Int
) {
    val total: Int get() = buffersRecreated + texturesRecreated + pipelinesRecreated
    val successRate: Float get() = if (total > 0) (total - failures).toFloat() / total else 1f
}
