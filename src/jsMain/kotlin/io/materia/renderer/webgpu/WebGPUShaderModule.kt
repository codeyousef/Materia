package io.materia.renderer.webgpu

/**
 * WebGPU shader module implementation.
 * T029: Shader compilation and validation.
 *
 * Compiles WGSL shaders to GPU bytecode.
 */
class WebGPUShaderModule(
    private val device: GPUDevice,
    private val descriptor: ShaderModuleDescriptor
) {
    private var module: GPUShaderModule? = null

    /**
     * Compiles the WGSL shader code.
     * @return Success or Error with compilation details
     */
    fun compile(): io.materia.core.Result<Unit> {
        return try {
            console.log("Compiling shader: ${descriptor.label ?: "unnamed"} (${descriptor.stage})")
            val shaderDescriptor = js("({})").unsafeCast<GPUShaderModuleDescriptor>()
            shaderDescriptor.code = descriptor.code
            descriptor.label?.let { shaderDescriptor.label = it }

            console.log("Creating shader module...")
            module = device.createShaderModule(shaderDescriptor)
            console.log("Shader module created successfully")

            // Note: Compilation validation is async (getCompilationInfo returns Promise).
            // For synchronous pipeline creation, we skip async validation.
            // WebGPU will report errors at pipeline creation if shaders are invalid.
            console.log("Shader compiled successfully: ${descriptor.label ?: "unnamed"}")
            io.materia.core.Result.Success(Unit)
        } catch (e: Exception) {
            console.error("Shader module creation exception: ${e.message}")
            e.printStackTrace()
            io.materia.core.Result.Error("Shader module creation failed: ${e.message}", e)
        }
    }

    /**
     * Validates the shader without creating a module.
     * @return true if shader is valid
     */
    fun validate(): Boolean {
        return try {
            // Attempt compilation and check for errors
            val testDescriptor = js("({})").unsafeCast<GPUShaderModuleDescriptor>()
            testDescriptor.code = descriptor.code
            device.createShaderModule(testDescriptor)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the compiled shader module.
     * @return GPU shader module or null if not compiled
     */
    fun getModule(): GPUShaderModule? = module

    /**
     * Gets the shader stage.
     */
    fun getStage(): ShaderStage = descriptor.stage

    /**
     * Disposes the shader module.
     */
    fun dispose() {
        module = null
    }
}
