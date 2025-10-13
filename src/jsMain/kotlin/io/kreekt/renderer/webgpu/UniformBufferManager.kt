package io.kreekt.renderer.webgpu

import io.kreekt.camera.Camera
import io.kreekt.core.scene.Mesh

internal data class FrameDebugInfo(
    val frameCount: Int,
    val drawCallCount: Int
)

internal class UniformBufferManager(
    private val deviceProvider: () -> GPUDevice?
) {
    private var uniformBuffer: WebGPUBuffer? = null
    private var bindGroupLayout: GPUBindGroupLayout? = null
    private var pipelineLayout: GPUPipelineLayout? = null
    private var cachedBindGroup: GPUBindGroup? = null

    fun onDeviceReady(device: GPUDevice) {
        ensureLayouts(device)
        ensureUniformBuffer(device)
    }

    fun updateUniforms(
        mesh: Mesh,
        camera: Camera,
        drawIndex: Int,
        frameInfo: FrameDebugInfo,
        enableDiagnostics: Boolean
    ): Boolean {
        val device = deviceProvider() ?: return false
        ensureUniformBuffer(device)

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

        val uniformData = FloatArray(48)
        for (i in 0 until 16) {
            uniformData[i] = projMatrix[i]
            uniformData[16 + i] = viewMatrix[i]
            uniformData[32 + i] = modelMatrix[i]
        }

        val offset = dynamicOffset(drawIndex)
        if (offset + MATRIX_BYTES > UNIFORM_BUFFER_SIZE) {
            console.error("T021 CRITICAL: Buffer overflow prevented! Offset=$offset exceeds buffer size=$UNIFORM_BUFFER_SIZE")
            return false
        }

        uniformBuffer?.upload(uniformData, offset)
        return true
    }

    fun bindGroup(): GPUBindGroup? {
        cachedBindGroup?.let { return it }

        val device = deviceProvider() ?: return null
        ensureLayouts(device)
        ensureUniformBuffer(device)
        val layout = bindGroupLayout ?: return null
        val buffer = uniformBuffer?.getBuffer() ?: return null

        val descriptor = js("({})").unsafeCast<GPUBindGroupDescriptor>()
        descriptor.label = "Cached Uniform Bind Group (Dynamic Offsets)"
        descriptor.layout = layout

        val entries = js("[]").unsafeCast<Array<GPUBindGroupEntry>>()
        val entry = js("({})").unsafeCast<GPUBindGroupEntry>()
        entry.binding = 0

        val resource = js("({})")
        resource.buffer = buffer
        resource.offset = 0
        resource.size = MATRIX_BYTES
        entry.resource = resource

        js("entries.push(entry)")
        descriptor.entries = entries

        val bindGroup = device.createBindGroup(descriptor)
        cachedBindGroup = bindGroup
        return bindGroup
    }

    fun pipelineLayout(): GPUPipelineLayout? {
        pipelineLayout?.let { return it }
        val device = deviceProvider() ?: return null
        ensureLayouts(device)
        return pipelineLayout
    }

    fun dynamicOffset(drawIndex: Int): Int = drawIndex * UNIFORM_SIZE_PER_MESH

    fun dispose() {
        cachedBindGroup = null
        bindGroupLayout = null
        pipelineLayout = null
        uniformBuffer?.dispose()
        uniformBuffer = null
    }

    private fun ensureLayouts(device: GPUDevice) {
        if (bindGroupLayout == null) {
            bindGroupLayout = createUniformBindGroupLayout(device)
        }
        if (pipelineLayout == null) {
            pipelineLayout = createUniformPipelineLayout(device, bindGroupLayout!!)
        }
    }

    private fun ensureUniformBuffer(device: GPUDevice) {
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
            }

            is io.kreekt.core.Result.Error -> {
                console.error("Failed to create uniform buffer: ${buffer.getUsage()}")
                uniformBuffer = null
            }
        }
    }

    private fun createUniformBindGroupLayout(device: GPUDevice): GPUBindGroupLayout {
        val descriptor = js("({})").unsafeCast<GPUBindGroupLayoutDescriptor>()
        descriptor.label = "Uniform Bind Group Layout (Dynamic Offsets)"

        val entries = js("[]").unsafeCast<Array<GPUBindGroupLayoutEntry>>()
        val entry = js("({})").unsafeCast<GPUBindGroupLayoutEntry>()
        entry.binding = 0
        entry.visibility = GPUShaderStage.VERTEX or GPUShaderStage.FRAGMENT

        val buffer = js("({})")
        buffer.type = "uniform"
        buffer.hasDynamicOffset = true
        buffer.minBindingSize = MATRIX_BYTES
        entry.buffer = buffer

        js("entries.push(entry)")
        descriptor.entries = entries

        return device.createBindGroupLayout(descriptor)
    }

    private fun createUniformPipelineLayout(
        device: GPUDevice,
        layout: GPUBindGroupLayout
    ): GPUPipelineLayout {
        val descriptor = js("({})").unsafeCast<GPUPipelineLayoutDescriptor>()
        descriptor.label = "Uniform Pipeline Layout (Dynamic Offsets)"

        val layouts = js("[]")
        layouts[0] = layout
        descriptor.bindGroupLayouts = layouts

        return device.createPipelineLayout(descriptor)
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
        const val UNIFORM_SIZE_PER_MESH = 256
        private const val MATRIX_BYTES = 192
        const val UNIFORM_BUFFER_SIZE = MAX_MESHES_PER_FRAME * UNIFORM_SIZE_PER_MESH
    }
}
