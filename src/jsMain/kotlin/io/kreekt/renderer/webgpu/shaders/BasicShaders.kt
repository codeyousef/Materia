package io.kreekt.renderer.webgpu.shaders

import io.kreekt.renderer.shader.MaterialShaderGenerator
import io.kreekt.renderer.shader.MaterialShaderLibrary

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
