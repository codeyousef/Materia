package io.kreekt.renderer.webgpu.shaders

import io.kreekt.renderer.shader.MaterialShaderGenerator
import io.kreekt.renderer.shader.MaterialShaderLibrary
import io.kreekt.renderer.shader.ShaderLanguage

object PBRShaders {
    private val shaderSource by lazy {
        MaterialShaderGenerator.compile(MaterialShaderLibrary.meshStandard(), ShaderLanguage.WGSL)
    }

    val vertexShader: String
        get() = shaderSource.vertexSource

    val fragmentShader: String
        get() = shaderSource.fragmentSource
}
