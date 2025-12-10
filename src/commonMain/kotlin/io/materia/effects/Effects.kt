/**
 * Materia Effects Module
 * 
 * High-level APIs for fullscreen shader effects and WebGPU rendering.
 * 
 * ## Features
 * 
 * ### UniformBlock - Type-safe uniform buffer management
 * ```kotlin
 * val uniforms = uniformBlock {
 *     float("time")
 *     vec2("resolution")
 *     vec4("color")
 * }
 * ```
 * 
 * ### FullScreenEffect - Simplified fullscreen shader effects
 * ```kotlin
 * val effect = fullScreenEffect {
 *     fragmentShader = """
 *         @fragment
 *         fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
 *             return vec4<f32>(uv, 0.0, 1.0);
 *         }
 *     """
 *     uniforms {
 *         float("time")
 *     }
 * }
 * ```
 * 
 * ### WGSLLib - Reusable shader snippets
 * ```kotlin
 * val shader = """
 *     ${WGSLLib.Hash.HASH_22}
 *     ${WGSLLib.Noise.VALUE_2D}
 *     ${WGSLLib.Fractal.FBM}
 *     ${WGSLLib.Color.COSINE_PALETTE}
 *     
 *     @fragment
 *     fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
 *         let n = fbm(uv * 10.0, 6);
 *         let color = cosinePalette(n, ...);
 *         return vec4<f32>(color, 1.0);
 *     }
 * """
 * ```
 * 
 * ### RenderLoop - Animation loop management
 * ```kotlin
 * val loop = RenderLoop { frame ->
 *     effect.updateUniforms {
 *         set("time", frame.totalTime)
 *     }
 * }
 * loop.timeScale = 0.5f  // Slow motion
 * loop.start()
 * ```
 * 
 * ### WebGPUCanvasConfig - Canvas configuration
 * ```kotlin
 * val config = WebGPUCanvasConfig(
 *     options = webGPUCanvasOptions {
 *         alphaMode = AlphaMode.PREMULTIPLIED
 *         powerPreference = PowerPreference.HIGH_PERFORMANCE
 *     }
 * )
 * ```
 */
package io.materia.effects

// Re-export all public APIs for convenience
// Users can import io.materia.effects.* to get everything
