package io.materia.renderer.webgpu.shaders

import io.materia.renderer.shader.MaterialShaderGenerator
import io.materia.renderer.shader.MaterialShaderLibrary

object PBRShaders {
    private val shaderSource by lazy {
        MaterialShaderGenerator.compile(MaterialShaderLibrary.meshStandard())
    }

    val vertexShader: String
        get() = shaderSource.vertexSource

    val fragmentShader: String
        get() = shaderSource.fragmentSource
}
