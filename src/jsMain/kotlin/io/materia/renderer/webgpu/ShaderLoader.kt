/**
 * T033: Shader Loader for WebGPU
 * Feature: 019-we-should-not
 *
 * Helper functions to load WGSL shaders for WebGPU.
 */

package io.materia.renderer.webgpu

/**
 * Load basic WGSL shader from embedded source.
 *
 * T033: For MVP, shader is embedded directly.
 * Future: Load from external files or resources.
 *
 * @return WGSL shader source code
 */
fun loadBasicShader(): String {
    // T033: Embedded basic.wgsl shader
    // In production, this could be loaded from external file
    return """
// Basic WGSL Shader (T031)
// Compatible with WebGPU (native) and Vulkan (via SPIR-V compilation)

// Uniform buffer for transformation matrices
struct Uniforms {
    modelViewProjection: mat4x4<f32>,
};

@group(0) @binding(0)
var<uniform> uniforms: Uniforms;

// Vertex shader input
struct VertexInput {
    @location(0) position: vec3<f32>,
    @location(1) color: vec3<f32>,
};

// Vertex shader output / Fragment shader input
struct VertexOutput {
    @builtin(position) position: vec4<f32>,
    @location(0) color: vec3<f32>,
};

// Vertex shader
@vertex
fn vs_main(input: VertexInput) -> VertexOutput {
    var output: VertexOutput;

    // Transform vertex position by MVP matrix
    output.position = uniforms.modelViewProjection * vec4<f32>(input.position, 1.0);

    // Pass color to fragment shader
    output.color = input.color;

    return output;
}

// Fragment shader
@fragment
fn fs_main(input: VertexOutput) -> @location(0) vec4<f32> {
    // Output per-vertex color with full opacity
    return vec4<f32>(input.color, 1.0);
}
    """.trimIndent()
}

/**
 * Create basic WebGPU pipeline with embedded shader.
 *
 * @param device WebGPU device
 * @return GPURenderPipeline or null on failure
 */
fun createBasicPipeline(device: dynamic): dynamic {
    return try {
        val shaderCode = loadBasicShader()

        // Create shader module
        val shaderModule = device.createShaderModule(js("({code: shaderCode})"))

        // Create pipeline (simplified for MVP)
        val pipelineDescriptor = js("({})")
        pipelineDescriptor.vertex = js("({})")
        pipelineDescriptor.vertex.module = shaderModule
        pipelineDescriptor.vertex.entryPoint = "vs_main"

        pipelineDescriptor.fragment = js("({})")
        pipelineDescriptor.fragment.module = shaderModule
        pipelineDescriptor.fragment.entryPoint = "fs_main"
        pipelineDescriptor.fragment.targets = js("([{format: 'bgra8unorm'}])")

        pipelineDescriptor.primitive = js("({topology: 'triangle-list'})")

        device.createRenderPipeline(pipelineDescriptor)
    } catch (e: Throwable) {
        console.error("Failed to create basic pipeline: ${e.message}")
        null
    }
}
