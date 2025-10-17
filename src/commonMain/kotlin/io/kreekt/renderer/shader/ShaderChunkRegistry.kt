package io.kreekt.renderer.shader

/**
 * Rendering shader stages supported by the material system.
 */
enum class ShaderStageType {
    VERTEX,
    FRAGMENT,
    COMPUTE
}

/**
 * Represents a reusable shader chunk. The chunk can optionally be scoped to a specific
 * [ShaderStageType]; when `stage` is `null` the chunk is treated as shared across all stages.
 */
data class ShaderChunk(
    val name: String,
    val source: String,
    val stage: ShaderStageType? = null
)

/**
 * Registry that stores shader chunks and resolves `#include <chunk>` directives when compiling
 * complete shader modules. The registry is responsible only for chunk lookup and include expansion;
 * higher level components (e.g. [MaterialShaderGenerator]) provide caching and descriptor handling.
 */
object ShaderChunkRegistry {
    private val includeRegex = Regex("#include\\s*<([A-Za-z0-9_./-]+)>")
    private val chunks = mutableMapOf<String, MutableList<ShaderChunk>>()

    private fun findChunk(name: String, stage: ShaderStageType): ShaderChunk? {
        val candidates = chunks[name] ?: return null
        return candidates.firstOrNull { it.stage == stage }
            ?: candidates.firstOrNull { it.stage == null }
    }

    /**
     * Registers a shader [chunk]. If a chunk with the same name and stage already exists the
     * behaviour depends on [replaceExisting]. When `replaceExisting` is false a duplicate
     * registration results in an [IllegalStateException].
     */
    fun register(chunk: ShaderChunk, replaceExisting: Boolean = false) {
        val entries = chunks.getOrPut(chunk.name) { mutableListOf() }
        val index = entries.indexOfFirst { it.stage == chunk.stage }
        if (index >= 0) {
            if (replaceExisting) {
                entries[index] = chunk
            } else {
                error("Shader chunk '${chunk.name}' already registered for stage ${chunk.stage}")
            }
        } else {
            entries += chunk
        }
    }

    /**
     * Registers all [chunkList]. Equivalent to calling [register] for each chunk.
     */
    fun registerAll(chunkList: Iterable<ShaderChunk>, replaceExisting: Boolean = false) {
        chunkList.forEach { register(it, replaceExisting) }
    }

    /**
     * Returns true when a chunk with [name] is registered for [stage]. When [stage] is `null`
     * the method checks for any registration regardless of stage.
     */
    fun contains(name: String, stage: ShaderStageType? = null): Boolean {
        val candidates = chunks[name] ?: return false
        if (stage == null) {
            return candidates.isNotEmpty()
        }
        return candidates.any { it.stage == stage || it.stage == null }
    }

    /**
     * Clears the registry. Intended for tests; production code should not invoke this directly.
     */
    internal fun clearForTests() {
        chunks.clear()
    }

    /**
     * Expands the provided [chunkNames] into a complete shader module for the supplied [stage].
     * The expansion performs depth-first resolution of `#include` directives while preventing
     * cyclic dependencies. After concatenation the optional [replacements] map is applied, where
     * each key is substituted for the token `{{KEY}}` in the resulting source.
     */
    fun assemble(
        chunkNames: List<String>,
        stage: ShaderStageType,
        replacements: Map<String, String> = emptyMap()
    ): String {
        require(chunkNames.isNotEmpty()) { "At least one shader chunk must be specified for stage $stage" }
        val builder = StringBuilder()
        chunkNames.forEachIndexed { index, name ->
            val chunk = findChunk(name, stage)
                ?: error("Shader chunk '$name' not registered for stage $stage")
            val resolved = resolveChunk(chunk, stage, mutableListOf())
            builder.append(resolved.trim())
            if (index != chunkNames.lastIndex) {
                builder.appendLine()
                builder.appendLine()
            }
        }
        var combined = builder.toString()
        replacements.forEach { (key, value) ->
            combined = combined.replace("{{${key}}}", value)
        }
        return combined
    }

    private fun resolveChunk(chunk: ShaderChunk, stage: ShaderStageType, stack: MutableList<String>): String {
        val key = chunk.name + "::" + (chunk.stage ?: "ANY")
        if (key in stack) {
            error("Circular shader chunk include detected: ${stack.joinToString(" -> ")} -> ${chunk.name}")
        }

        stack += key
        var source = chunk.source

        source = includeRegex.replace(source) { match ->
            val includeName = match.groupValues[1]
            val includeChunk = findChunk(includeName, stage)
                ?: error("Shader chunk '$includeName' referenced from '${chunk.name}' is not registered for stage $stage")
            resolveChunk(includeChunk, stage, stack)
        }

        stack.removeAt(stack.size - 1)
        return source
    }
}
