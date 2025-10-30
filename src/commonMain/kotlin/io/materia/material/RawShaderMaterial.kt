package io.materia.material

/**
 * RawShaderMaterial - Minimal preprocessing shader material
 * Three.js r180 compatible
 *
 * Similar to ShaderMaterial but with minimal automatic preprocessing.
 * User must manually define all attributes, uniforms, and precision qualifiers.
 * Provides maximum control for expert shader developers.
 * Does not inject built-in uniforms or automatic defines.
 */
class RawShaderMaterial(
    vertexShader: String = "",
    fragmentShader: String = "",
    computeShader: String = "",
    materialName: String = "RawShaderMaterial"
) : ShaderMaterial(vertexShader, fragmentShader, computeShader, materialName) {

    init {
        // Disable automatic preprocessing features
        features.clear()
        defines.clear()
        includes.clear()
    }

    /**
     * Raw shader material does not add any automatic features
     */
    override fun addFeature(feature: String) {
        // No automatic features in raw mode
        // User must manually add features if needed
        super.addFeature(feature)
    }

    /**
     * Clone the raw shader material
     */
    override fun clone(): ShaderMaterial {
        return RawShaderMaterial(
            vertexShader = vertexShader,
            fragmentShader = fragmentShader,
            computeShader = computeShader,
            materialName = name
        ).apply {
            // Copy only explicitly set properties
            _uniforms.putAll(this@RawShaderMaterial._uniforms)
            _attributes.putAll(this@RawShaderMaterial._attributes)
            _textures.putAll(this@RawShaderMaterial._textures)
            _storageBuffers.putAll(this@RawShaderMaterial._storageBuffers)

            // Copy render state
            blending = this@RawShaderMaterial.blending
            depthTest = this@RawShaderMaterial.depthTest
            cullMode = this@RawShaderMaterial.cullMode
            primitiveTopology = this@RawShaderMaterial.primitiveTopology
            wireframe = this@RawShaderMaterial.wireframe

            // Copy compute settings
            workgroupSize = this@RawShaderMaterial.workgroupSize.clone()
            dispatchSize = this@RawShaderMaterial.dispatchSize.clone()
        }
    }
}
