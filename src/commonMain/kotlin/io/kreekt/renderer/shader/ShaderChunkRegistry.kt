package io.kreekt.renderer.shader

import kotlinx.atomicfu.atomic
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/**
 * Rendering shader stages supported by the material system.
 */
enum class ShaderStageType {
    VERTEX,
    FRAGMENT,
    COMPUTE
}

/**
 * Supported shading languages for reusable chunks.
 */
enum class ShaderLanguage {
    ANY,
    WGSL,
    GLSL
}

/**
 * Represents a reusable shader chunk. The chunk can optionally be scoped to a specific
 * [ShaderStageType]; when `stage` is `null` the chunk is treated as shared across all stages.
 */
data class ShaderChunk(
    val name: String,
    val source: String,
    val stage: ShaderStageType? = null,
    val language: ShaderLanguage = ShaderLanguage.ANY
)

/**
 * Registry that stores shader chunks and resolves `#include <chunk>` directives when compiling
 * complete shader modules. The registry is responsible only for chunk lookup and include expansion;
 * higher level components (e.g. [MaterialShaderGenerator]) provide caching and descriptor handling.
 */
object ShaderChunkRegistry {
    private val includeRegex = Regex("#include\\s*<([A-Za-z0-9_./-]+)>")
    private val chunkRegistry = atomic(persistentMapOf<String, PersistentList<ShaderChunk>>())

    private fun findChunk(
        registry: PersistentMap<String, PersistentList<ShaderChunk>>,
        name: String,
        stage: ShaderStageType,
        language: ShaderLanguage
    ): ShaderChunk? {
        val candidates = registry[name] ?: return null
        return candidates.firstOrNull { it.stage == stage && it.language == language }
            ?: candidates.firstOrNull { it.stage == stage && it.language == ShaderLanguage.ANY }
            ?: candidates.firstOrNull { it.stage == null && it.language == language }
            ?: candidates.firstOrNull { it.stage == null && it.language == ShaderLanguage.ANY }
    }

    /**
     * Registers a shader [chunk]. If a chunk with the same name and stage already exists the
     * behaviour depends on [replaceExisting]. When `replaceExisting` is false a duplicate
     * registration results in an [IllegalStateException].
     */
    fun register(chunk: ShaderChunk, replaceExisting: Boolean = false) {
        while (true) {
            val current = chunkRegistry.value
            val existing = current[chunk.name] ?: persistentListOf()
            val index = existing.indexOfFirst { it.stage == chunk.stage && it.language == chunk.language }
            val updatedList = when {
                index >= 0 && !replaceExisting ->
                    error("Shader chunk '${chunk.name}' already registered for stage ${chunk.stage} and language ${chunk.language}")
                index >= 0 -> existing.set(index, chunk)
                else -> existing.add(chunk)
            }
            val updated = current.put(chunk.name, updatedList)
            if (chunkRegistry.compareAndSet(current, updated)) {
                return
            }
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
    fun contains(name: String, stage: ShaderStageType? = null, language: ShaderLanguage? = null): Boolean {
        val candidates = chunkRegistry.value[name] ?: return false
        if (stage == null) {
            if (language == null) return candidates.isNotEmpty()
            return candidates.any { it.language == language || it.language == ShaderLanguage.ANY }
        }
        return candidates.any {
            (it.stage == stage || it.stage == null) &&
                (language == null || it.language == language || it.language == ShaderLanguage.ANY)
        }
    }

    /**
     * Clears the registry. Intended for tests; production code should not invoke this directly.
     */
    internal fun clearForTests() {
        chunkRegistry.value = persistentMapOf()
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
        language: ShaderLanguage = ShaderLanguage.ANY,
        replacements: Map<String, String> = emptyMap()
    ): String {
        require(chunkNames.isNotEmpty()) { "At least one shader chunk must be specified for stage $stage" }
        val snapshot = chunkRegistry.value
        val builder = StringBuilder()
        chunkNames.forEachIndexed { index, name ->
            val chunk = findChunk(snapshot, name, stage, language)
                ?: error("Shader chunk '$name' not registered for stage $stage (language=$language)")
            val resolved = resolveChunk(snapshot, chunk, stage, language, mutableListOf())
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

    private fun resolveChunk(
        registry: PersistentMap<String, PersistentList<ShaderChunk>>,
        chunk: ShaderChunk,
        stage: ShaderStageType,
        language: ShaderLanguage,
        stack: MutableList<String>
    ): String {
        val key = chunk.name + "::" + (chunk.stage ?: "ANY") + "::" + chunk.language
        if (key in stack) {
            error("Circular shader chunk include detected: ${stack.joinToString(" -> ")} -> ${chunk.name}")
        }

        stack += key
        var source = chunk.source

        source = includeRegex.replace(source) { match ->
            val includeName = match.groupValues[1]
            val includeChunk = findChunk(registry, includeName, stage, language)
                ?: error("Shader chunk '$includeName' referenced from '${chunk.name}' is not registered for stage $stage (language=$language)")
            resolveChunk(registry, includeChunk, stage, language, stack)
        }

        stack.removeAt(stack.size - 1)
        return source
    }
}
