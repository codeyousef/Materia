package io.kreekt.renderer.webgpu.shaders

import io.kreekt.renderer.shader.MaterialShaderGenerator
import io.kreekt.renderer.shader.MaterialShaderLibrary

object PBRShaders {
    private val shaderSource by lazy {
        MaterialShaderGenerator.compile(MaterialShaderLibrary.meshStandard())
    }

    val vertexShader: String
        get() = shaderSource.vertexSource

    val fragmentShader: String
        get() = shaderSource.fragmentSource
}
