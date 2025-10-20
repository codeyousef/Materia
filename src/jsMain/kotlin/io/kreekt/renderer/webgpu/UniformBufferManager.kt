package io.kreekt.renderer.webgpu

import io.kreekt.camera.Camera
import io.kreekt.core.scene.Mesh
import io.kreekt.renderer.gpu.GpuBindGroup
import io.kreekt.renderer.gpu.GpuBindGroupDescriptor
import io.kreekt.renderer.gpu.GpuBindGroupEntry
import io.kreekt.renderer.gpu.GpuBindGroupLayout
import io.kreekt.renderer.gpu.GpuBindGroupLayoutDescriptor
import io.kreekt.renderer.gpu.GpuBindGroupLayoutEntry
import io.kreekt.renderer.gpu.GpuBindingResource
import io.kreekt.renderer.gpu.GpuBufferBindingLayout
import io.kreekt.renderer.gpu.GpuBufferBindingType
import io.kreekt.renderer.gpu.GpuDevice
import io.kreekt.renderer.gpu.GpuPipelineLayout
import io.kreekt.renderer.gpu.GpuPipelineLayoutDescriptor
import io.kreekt.renderer.gpu.GpuShaderStage
import io.kreekt.renderer.material.MaterialDescriptorRegistry

internal data class FrameDebugInfo(
    val frameCount: Int,
    val drawCallCount: Int
)

internal class UniformBufferManager(
    private val deviceProvider: () -> GpuDevice?,
    private val statsTracker: RenderStatsTracker? = null
) {
    private var uniformBuffer: WebGPUBuffer? = null
    private var bindGroupLayout: GpuBindGroupLayout? = null
    private var pipelineLayout: GpuPipelineLayout? = null
    private var cachedBindGroup: GpuBindGroup? = null
    private var uniformBufferSizeBytes: Long = 0

    fun onDeviceReady(device: GpuDevice) {
        ensureLayouts(device)
        ensureUniformBuffer(device)
    }

    fun updateUniforms(
        mesh: Mesh,
        camera: Camera,
        drawIndex: Int,
        frameInfo: FrameDebugInfo,
        enableDiagnostics: Boolean,
        materialUniforms: MaterialUniformData? = null
    ): Boolean {
        val gpuDevice = deviceProvider() ?: return false
        ensureUniformBuffer(gpuDevice)

        if (uniformBuffer == null) {
            console.warn("Uniform buffer unavailable; skipping mesh")
            return false
        }

        if (enableDiagnostics && frameInfo.frameCount < 50 && frameInfo.drawCallCount == 0) {
            console.log("T021 Frame ${frameInfo.frameCount}: updateUniforms called, drawCallCount=${frameInfo.drawCallCount}")
        }

        val projMatrix = camera.projectionMatrix.elements
        val viewMatrix = camera.matrixWorldInverse.elements
        val modelMatrix = mesh.matrixWorld.elements

        if (enableDiagnostics && frameInfo.frameCount < 3 && frameInfo.drawCallCount < 2) {
            logMatrices(frameInfo, camera, projMatrix, viewMatrix, modelMatrix)
        }

        val uniformData = FloatArray(68)
        for (i in 0 until 16) {
            uniformData[i] = projMatrix[i]
            uniformData[16 + i] = viewMatrix[i]
            uniformData[32 + i] = modelMatrix[i]
        }

        val baseColor = materialUniforms?.baseColor ?: DEFAULT_BASE_COLOR
        uniformData[48] = baseColor.getOrNull(0) ?: 1f
        uniformData[49] = baseColor.getOrNull(1) ?: 1f
        uniformData[50] = baseColor.getOrNull(2) ?: 1f
        uniformData[51] = baseColor.getOrNull(3) ?: 1f

        val roughness = materialUniforms?.roughness ?: 1f
        val metalness = materialUniforms?.metalness ?: 0f
        val envIntensity = materialUniforms?.envIntensity ?: 1f
        val mipCount = materialUniforms?.prefilterMipCount ?: 1
        uniformData[52] = roughness
        uniformData[53] = metalness
        uniformData[54] = envIntensity
        uniformData[55] = mipCount.toFloat()

        val cameraPosition = materialUniforms?.cameraPosition ?: floatArrayOf(
            camera.position.x,
            camera.position.y,
            camera.position.z
        )
        uniformData[56] = cameraPosition.getOrNull(0) ?: camera.position.x
        uniformData[57] = cameraPosition.getOrNull(1) ?: camera.position.y
        uniformData[58] = cameraPosition.getOrNull(2) ?: camera.position.z
        uniformData[59] = cameraPosition.getOrNull(3) ?: 1f

        val morphInfluenceSource = mesh.morphTargetInfluences
            ?: (mesh.userData["morphTargetInfluences"] as? MutableList<Float>)
            ?: emptyList<Float>()
        for (i in 0 until MAX_MORPH_TARGETS) {
            uniformData[60 + i] = morphInfluenceSource.getOrNull(i) ?: 0f
        }

        val offset = dynamicOffset(drawIndex)
        if (offset + OBJECT_BYTES > UNIFORM_BUFFER_SIZE) {
            console.error("T021 CRITICAL: Buffer overflow prevented! Offset=$offset exceeds buffer size=$UNIFORM_BUFFER_SIZE")
            return false
        }

        uniformBuffer?.upload(uniformData, offset)
        return true
    }

    fun bindGroup(): GpuBindGroup? {
        cachedBindGroup?.let { return it }

        val gpuDevice = deviceProvider() ?: return null
        ensureLayouts(gpuDevice)
        ensureUniformBuffer(gpuDevice)
        val layout = bindGroupLayout ?: return null
        val gpuBuffer = uniformBuffer?.gpuBuffer() ?: return null

        val descriptor = GpuBindGroupDescriptor(
            layout = layout,
            entries = listOf(
                GpuBindGroupEntry(
                    binding = 0,
                    resource = GpuBindingResource.Buffer(
                        buffer = gpuBuffer,
                        offset = 0,
                        size = OBJECT_BYTES.toLong()
                    )
                )
            ),
            label = "Uniform Bind Group (Dynamic Offsets)"
        )

        val bindGroup = gpuDevice.createBindGroup(descriptor)
        cachedBindGroup = bindGroup
        return bindGroup
    }

    private var extraLayoutsCache: List<GpuBindGroupLayout> = emptyList()

    fun pipelineLayout(extraLayouts: List<GpuBindGroupLayout> = emptyList()): GpuPipelineLayout? {
        val gpuDevice = deviceProvider() ?: return null
        ensureLayouts(gpuDevice)
        if (pipelineLayout != null && extraLayoutsCache == extraLayouts) {
            return pipelineLayout
        }

        val coreLayout = bindGroupLayout ?: return null
        val layouts = buildList {
            add(coreLayout)
            addAll(extraLayouts)
        }

        val descriptor = GpuPipelineLayoutDescriptor(
            bindGroupLayouts = layouts,
            label = "Uniform Pipeline Layout (Dynamic Offsets)"
        )

        pipelineLayout = gpuDevice.createPipelineLayout(descriptor)
        extraLayoutsCache = extraLayouts
        return pipelineLayout
    }

    fun dynamicOffset(drawIndex: Int): Int = drawIndex * UNIFORM_SIZE_PER_MESH

    fun dispose() {
        cachedBindGroup = null
        bindGroupLayout = null
        pipelineLayout = null
        extraLayoutsCache = emptyList()
        if (uniformBuffer != null && uniformBufferSizeBytes > 0) {
            statsTracker?.recordBufferDeallocated(uniformBufferSizeBytes)
            uniformBufferSizeBytes = 0
        }
        uniformBuffer?.dispose()
        uniformBuffer = null
    }

    private fun ensureLayouts(device: GpuDevice) {
        if (bindGroupLayout == null) {
            bindGroupLayout = createUniformBindGroupLayout(device)
        }
        // Pipeline layout constructed on demand with optional extra layouts
    }

    private fun ensureUniformBuffer(device: GpuDevice) {
        if (uniformBuffer != null) return

        val buffer = WebGPUBuffer(
            device,
            BufferDescriptor(
                size = UNIFORM_BUFFER_SIZE,
                usage = GPUBufferUsage.UNIFORM or GPUBufferUsage.COPY_DST,
                label = "Uniform Buffer (Multi-Mesh, $MAX_MESHES_PER_FRAME max)"
            )
        )

        when (buffer.create()) {
            is io.kreekt.core.Result.Success -> {
                uniformBuffer = buffer
                cachedBindGroup = null
                uniformBufferSizeBytes = UNIFORM_BUFFER_SIZE.toLong()
                statsTracker?.recordBufferAllocated(uniformBufferSizeBytes)
            }

            is io.kreekt.core.Result.Error -> {
                console.error("Failed to create uniform buffer: ${buffer.getUsage()}")
                uniformBuffer = null
            }
        }
    }

    private fun createUniformBindGroupLayout(device: GpuDevice): GpuBindGroupLayout {
        val descriptor = GpuBindGroupLayoutDescriptor(
            entries = listOf(
                GpuBindGroupLayoutEntry(
                    binding = 0,
                    visibility = GpuShaderStage.VERTEX.bits or GpuShaderStage.FRAGMENT.bits,
                    buffer = GpuBufferBindingLayout(
                        type = GpuBufferBindingType.UNIFORM,
                        hasDynamicOffset = true,
                        minBindingSize = OBJECT_BYTES.toLong()
                    )
                )
            ),
            label = "Uniform Bind Group Layout (Dynamic Offsets)"
        )
        return device.createBindGroupLayout(descriptor)
    }

    private fun logMatrices(
        frameInfo: FrameDebugInfo,
        camera: Camera,
        projMatrix: FloatArray,
        viewMatrix: FloatArray,
        modelMatrix: FloatArray
    ) {
        console.log("T021 Frame ${frameInfo.frameCount}, Draw ${frameInfo.drawCallCount}:")
        console.log("  Projection matrix:")
        console.log("    [${projMatrix[0]}, ${projMatrix[4]}, ${projMatrix[8]}, ${projMatrix[12]}]")
        console.log("    [${projMatrix[1]}, ${projMatrix[5]}, ${projMatrix[9]}, ${projMatrix[13]}]")
        console.log("    [${projMatrix[2]}, ${projMatrix[6]}, ${projMatrix[10]}, ${projMatrix[14]}]")
        console.log("    [${projMatrix[3]}, ${projMatrix[7]}, ${projMatrix[11]}, ${projMatrix[15]}]")
        console.log("  View matrix:")
        console.log("    [${viewMatrix[0]}, ${viewMatrix[4]}, ${viewMatrix[8]}, ${viewMatrix[12]}]")
        console.log("    [${viewMatrix[1]}, ${viewMatrix[5]}, ${viewMatrix[9]}, ${viewMatrix[13]}]")
        console.log("    [${viewMatrix[2]}, ${viewMatrix[6]}, ${viewMatrix[10]}, ${viewMatrix[14]}]")
        console.log("    [${viewMatrix[3]}, ${viewMatrix[7]}, ${viewMatrix[11]}, ${viewMatrix[15]}]")
        console.log("  Model matrix:")
        console.log("    [${modelMatrix[0]}, ${modelMatrix[4]}, ${modelMatrix[8]}, ${modelMatrix[12]}]")
        console.log("    [${modelMatrix[1]}, ${modelMatrix[5]}, ${modelMatrix[9]}, ${modelMatrix[13]}]")
        console.log("    [${modelMatrix[2]}, ${modelMatrix[6]}, ${modelMatrix[10]}, ${modelMatrix[14]}]")
        console.log("    [${modelMatrix[3]}, ${modelMatrix[7]}, ${modelMatrix[11]}, ${modelMatrix[15]}]")
        console.log("  Camera pos: (${camera.position.x}, ${camera.position.y}, ${camera.position.z})")
        console.log("  Camera rot: (${camera.rotation.x}, ${camera.rotation.y}, ${camera.rotation.z})")
    }

    companion object {
        const val MAX_MESHES_PER_FRAME = 200
        private const val UNIFORM_ALIGNMENT = 256
        private const val MAX_MORPH_TARGETS = 8
        private val OBJECT_BYTES_INTERNAL = MaterialDescriptorRegistry.uniformBlockSizeBytes()
        val OBJECT_BYTES: Int = OBJECT_BYTES_INTERNAL
        val UNIFORM_SIZE_PER_MESH: Int =
            ((OBJECT_BYTES + UNIFORM_ALIGNMENT - 1) / UNIFORM_ALIGNMENT) * UNIFORM_ALIGNMENT
        val UNIFORM_BUFFER_SIZE: Int = MAX_MESHES_PER_FRAME * UNIFORM_SIZE_PER_MESH
        private val DEFAULT_BASE_COLOR = floatArrayOf(1f, 1f, 1f, 1f)
    }
}

internal data class MaterialUniformData(
    val baseColor: FloatArray,
    val roughness: Float,
    val metalness: Float,
    val envIntensity: Float,
    val prefilterMipCount: Int,
    val cameraPosition: FloatArray
)
