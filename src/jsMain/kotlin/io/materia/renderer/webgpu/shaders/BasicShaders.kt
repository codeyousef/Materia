package io.materia.renderer.webgpu.shaders

import io.materia.renderer.shader.MaterialShaderGenerator
import io.materia.renderer.shader.MaterialShaderLibrary

/**
 * WGSL shader library for the basic WebGPU material routed through the shared shader registry.
 */
object BasicShaders {
    private val shaderSource by lazy {
        MaterialShaderGenerator.compile(MaterialShaderLibrary.basic())
    }

    val vertexShader: String
        get() = shaderSource.vertexSource

    val fragmentShader: String
        get() = shaderSource.fragmentSource
}
