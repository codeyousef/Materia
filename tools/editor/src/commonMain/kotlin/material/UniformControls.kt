@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.materia.tools.editor.material

import io.materia.tools.editor.data.MaterialDefinition
import io.materia.tools.editor.data.UniformValue
import io.materia.tools.editor.data.UniformType
import io.materia.tools.editor.data.Color
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * UniformControls - Dynamic UI generation for material uniform parameters
 *
 * This component provides a comprehensive uniform editing system including:
 * - Dynamic UI generation based on shader uniform declarations
 * - Type-safe value editing with validation
 * - Real-time preview updates during editing
 * - Uniform grouping and categorization
 * - Preset values and animation support
 * - Undo/redo functionality for parameter changes
 */
class UniformControls(
    private val scope: CoroutineScope,
    private val onUniformChanged: (String, UniformValue) -> Unit = { _, _ -> }
) {

    // Uniform state
    private val _material = MutableStateFlow<MaterialDefinition?>(null)
    private val _uniforms = MutableStateFlow<Map<String, UniformValue>>(emptyMap())
    private val _selectedUniform = MutableStateFlow<String?>(null)
    private val _editingUniform = MutableStateFlow<String?>(null)

    // UI state
    private val _grouping = MutableStateFlow(UniformGrouping.BY_TYPE)
    private val _showAdvanced = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    private val _collapsedGroups = MutableStateFlow<Set<String>>(emptySet())

    // Animation state
    private val _animatedUniforms = MutableStateFlow<Set<String>>(emptySet())
    private val _animationTime = MutableStateFlow(0f)
    private val _animationSpeed = MutableStateFlow(1f)

    // Validation state
    private val _validationErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _hasUnsavedChanges = MutableStateFlow(false)

    // Undo/Redo state
    private val undoStack = mutableListOf<UniformOperation>()
    private val redoStack = mutableListOf<UniformOperation>()
    private val maxUndoSteps = 100

    // Public read-only state
    val material: StateFlow<MaterialDefinition?> = _material.asStateFlow()
    val uniforms: StateFlow<Map<String, UniformValue>> = _uniforms.asStateFlow()
    val selectedUniform: StateFlow<String?> = _selectedUniform.asStateFlow()
    val editingUniform: StateFlow<String?> = _editingUniform.asStateFlow()

    val grouping: StateFlow<UniformGrouping> = _grouping.asStateFlow()
    val showAdvanced: StateFlow<Boolean> = _showAdvanced.asStateFlow()
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val collapsedGroups: StateFlow<Set<String>> = _collapsedGroups.asStateFlow()

    val animatedUniforms: StateFlow<Set<String>> = _animatedUniforms.asStateFlow()
    val animationTime: StateFlow<Float> = _animationTime.asStateFlow()
    val animationSpeed: StateFlow<Float> = _animationSpeed.asStateFlow()

    val validationErrors: StateFlow<Map<String, String>> = _validationErrors.asStateFlow()
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    // Derived state - filtered and grouped uniforms
    val filteredUniforms: StateFlow<Map<String, UniformValue>> = combine(
        _uniforms, _searchQuery
    ) { uniforms, query ->
        if (query.isBlank()) {
            uniforms
        } else {
            uniforms.filter { (name, uniform) ->
                name.contains(query, ignoreCase = true) ||
                uniform.displayName?.contains(query, ignoreCase = true) == true ||
                uniform.category?.contains(query, ignoreCase = true) == true
            }
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = emptyMap()
    )

    val groupedUniforms: StateFlow<Map<String, List<Pair<String, UniformValue>>>> = combine(
        filteredUniforms, _grouping
    ) { uniforms, grouping ->
        when (grouping) {
            UniformGrouping.BY_TYPE -> groupUniformsByType(uniforms)
            UniformGrouping.BY_CATEGORY -> groupUniformsByCategory(uniforms)
            UniformGrouping.ALPHABETICAL -> groupUniformsAlphabetically(uniforms)
            UniformGrouping.NONE -> mapOf("All" to uniforms.toList())
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = emptyMap()
    )

    val canUndo: StateFlow<Boolean> = MutableStateFlow(false).apply {
        scope.launch {
            while (true) {
                value = undoStack.isNotEmpty()
                kotlinx.coroutines.delay(100)
            }
        }
    }

    val canRedo: StateFlow<Boolean> = MutableStateFlow(false).apply {
        scope.launch {
            while (true) {
                value = redoStack.isNotEmpty()
                kotlinx.coroutines.delay(100)
            }
        }
    }

    // Material management

    fun setMaterial(material: MaterialDefinition?) {
        _material.value = material
        _uniforms.value = material?.uniforms ?: emptyMap()
        _validationErrors.value = emptyMap()
        _hasUnsavedChanges.value = false
        clearUndoHistory()
    }

    fun refreshUniforms() {
        _material.value?.let { material ->
            _uniforms.value = material.uniforms
        }
    }

    // Uniform value management

    fun setUniformValue(name: String, value: Any) {
        val currentUniforms = _uniforms.value
        val uniform = currentUniforms[name] ?: return

        val newUniform = try {
            uniform.copy(value = value).also { validateUniform(name, it) }
        } catch (e: Exception) {
            _validationErrors.value = _validationErrors.value + (name to e.message.orEmpty())
            return
        }

        // Clear validation error if value is valid
        _validationErrors.value = _validationErrors.value - name

        // Record operation for undo/redo
        recordOperation(UniformOperation.ValueChange(name, uniform, newUniform))

        // Update uniforms
        _uniforms.value = currentUniforms + (name to newUniform)
        _hasUnsavedChanges.value = true

        // Notify listeners
        onUniformChanged(name, newUniform)
    }

    fun setUniformConstraints(name: String, min: Float?, max: Float?, step: Float?) {
        val currentUniforms = _uniforms.value
        val uniform = currentUniforms[name] ?: return

        val newUniform = uniform.copy(min = min, max = max, step = step)

        recordOperation(UniformOperation.ConstraintChange(name, uniform, newUniform))

        _uniforms.value = currentUniforms + (name to newUniform)
        _hasUnsavedChanges.value = true

        onUniformChanged(name, newUniform)
    }

    fun setUniformMetadata(name: String, displayName: String?, description: String?, category: String?) {
        val currentUniforms = _uniforms.value
        val uniform = currentUniforms[name] ?: return

        val newUniform = uniform.copy(
            displayName = displayName,
            description = description,
            category = category
        )

        recordOperation(UniformOperation.MetadataChange(name, uniform, newUniform))

        _uniforms.value = currentUniforms + (name to newUniform)
        _hasUnsavedChanges.value = true

        onUniformChanged(name, newUniform)
    }

    fun addUniform(name: String, type: UniformType, value: Any) {
        val currentUniforms = _uniforms.value
        if (currentUniforms.containsKey(name)) {
            throw IllegalArgumentException("Uniform '$name' already exists")
        }

        val uniform = UniformValue(type = type, value = value)
        validateUniform(name, uniform)

        recordOperation(UniformOperation.Add(name, uniform))

        _uniforms.value = currentUniforms + (name to uniform)
        _hasUnsavedChanges.value = true

        onUniformChanged(name, uniform)
    }

    fun removeUniform(name: String) {
        val currentUniforms = _uniforms.value
        val uniform = currentUniforms[name] ?: return

        recordOperation(UniformOperation.Remove(name, uniform))

        _uniforms.value = currentUniforms - name
        _hasUnsavedChanges.value = true
        _validationErrors.value = _validationErrors.value - name

        if (_selectedUniform.value == name) {
            _selectedUniform.value = null
        }
        if (_editingUniform.value == name) {
            _editingUniform.value = null
        }
    }

    fun duplicateUniform(name: String, newName: String) {
        val currentUniforms = _uniforms.value
        val uniform = currentUniforms[name] ?: return

        if (currentUniforms.containsKey(newName)) {
            throw IllegalArgumentException("Uniform '$newName' already exists")
        }

        val newUniform = uniform.copy(
            displayName = uniform.displayName?.let { "$it (Copy)" }
        )

        recordOperation(UniformOperation.Add(newName, newUniform))

        _uniforms.value = currentUniforms + (newName to newUniform)
        _hasUnsavedChanges.value = true

        onUniformChanged(newName, newUniform)
    }

    // UI state management

    fun setSelectedUniform(name: String?) {
        _selectedUniform.value = name
    }

    fun setEditingUniform(name: String?) {
        _editingUniform.value = name
    }

    fun setGrouping(grouping: UniformGrouping) {
        _grouping.value = grouping
    }

    fun setShowAdvanced(show: Boolean) {
        _showAdvanced.value = show
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleGroupCollapsed(groupName: String) {
        val collapsed = _collapsedGroups.value
        _collapsedGroups.value = if (collapsed.contains(groupName)) {
            collapsed - groupName
        } else {
            collapsed + groupName
        }
    }

    fun expandAllGroups() {
        _collapsedGroups.value = emptySet()
    }

    fun collapseAllGroups() {
        val grouped = groupedUniforms.value
        _collapsedGroups.value = grouped.keys.toSet()
    }

    // Animation support

    fun setUniformAnimated(name: String, animated: Boolean) {
        val current = _animatedUniforms.value
        _animatedUniforms.value = if (animated) {
            current + name
        } else {
            current - name
        }
    }

    fun setAnimationTime(time: Float) {
        _animationTime.value = time
        updateAnimatedUniforms()
    }

    fun setAnimationSpeed(speed: Float) {
        require(speed >= 0f) { "Animation speed must be non-negative" }
        _animationSpeed.value = speed
    }

    private fun updateAnimatedUniforms() {
        val time = _animationTime.value
        val animatedNames = _animatedUniforms.value
        val currentUniforms = _uniforms.value

        animatedNames.forEach { name ->
            val uniform = currentUniforms[name] ?: return@forEach
            val animatedValue = calculateAnimatedValue(uniform, time)
            if (animatedValue != uniform.value) {
                setUniformValue(name, animatedValue)
            }
        }
    }

    private fun calculateAnimatedValue(uniform: UniformValue, time: Float): Any {
        return when (uniform.type) {
            UniformType.FLOAT -> {
                val base = (uniform.value as? Number)?.toFloat() ?: 0f
                val amplitude = (uniform.max ?: 1f) - (uniform.min ?: 0f)
                base + sin(time) * amplitude * 0.1f
            }
            UniformType.VEC2 -> {
                val list = uniform.value as? List<*> ?: return uniform.value
                if (list.size < 2) return uniform.value
                listOf(
                    (list[0] as? Number)?.toFloat()?.let { it + sin(time) * 0.1f } ?: 0f,
                    (list[1] as? Number)?.toFloat()?.let { it + cos(time) * 0.1f } ?: 0f
                )
            }
            UniformType.VEC3 -> {
                val list = uniform.value as? List<*> ?: return uniform.value
                if (list.size < 3) return uniform.value
                listOf(
                    (list[0] as? Number)?.toFloat()?.let { it + sin(time) * 0.1f } ?: 0f,
                    (list[1] as? Number)?.toFloat()?.let { it + cos(time) * 0.1f } ?: 0f,
                    (list[2] as? Number)?.toFloat()?.let { it + sin(time * 0.7f) * 0.1f } ?: 0f
                )
            }
            UniformType.VEC4 -> {
                val list = uniform.value as? List<*> ?: return uniform.value
                if (list.size < 4) return uniform.value
                listOf(
                    (list[0] as? Number)?.toFloat()?.let { it + sin(time) * 0.1f } ?: 0f,
                    (list[1] as? Number)?.toFloat()?.let { it + cos(time) * 0.1f } ?: 0f,
                    (list[2] as? Number)?.toFloat()?.let { it + sin(time * 0.7f) * 0.1f } ?: 0f,
                    (list[3] as? Number)?.toFloat() ?: 1f
                )
            }
            else -> uniform.value
        }
    }

    // Preset management

    fun savePreset(name: String): UniformPreset {
        val preset = UniformPreset(
            name = name,
            uniforms = _uniforms.value.toMap(),
            description = "Custom preset created on ${kotlinx.datetime.Clock.System.now()}",
            tags = listOf("custom")
        )
        return preset
    }

    fun loadPreset(preset: UniformPreset) {
        recordOperation(UniformOperation.PresetLoad(_uniforms.value, preset.uniforms))

        _uniforms.value = preset.uniforms
        _hasUnsavedChanges.value = true

        preset.uniforms.forEach { (name, uniform) ->
            onUniformChanged(name, uniform)
        }
    }

    fun getDefaultPresets(): List<UniformPreset> {
        return listOf(
            UniformPreset(
                name = "Default PBR",
                uniforms = mapOf(
                    "baseColor" to UniformValue(UniformType.VEC3, listOf(0.8f, 0.8f, 0.8f)),
                    "roughness" to UniformValue(UniformType.FLOAT, 0.5f),
                    "metallic" to UniformValue(UniformType.FLOAT, 0.0f)
                ),
                description = "Standard PBR material values",
                tags = listOf("pbr", "default")
            ),
            UniformPreset(
                name = "Metallic",
                uniforms = mapOf(
                    "baseColor" to UniformValue(UniformType.VEC3, listOf(0.7f, 0.7f, 0.7f)),
                    "roughness" to UniformValue(UniformType.FLOAT, 0.1f),
                    "metallic" to UniformValue(UniformType.FLOAT, 1.0f)
                ),
                description = "Shiny metallic material",
                tags = listOf("pbr", "metal")
            ),
            UniformPreset(
                name = "Plastic",
                uniforms = mapOf(
                    "baseColor" to UniformValue(UniformType.VEC3, listOf(1.0f, 0.2f, 0.2f)),
                    "roughness" to UniformValue(UniformType.FLOAT, 0.8f),
                    "metallic" to UniformValue(UniformType.FLOAT, 0.0f)
                ),
                description = "Matte plastic material",
                tags = listOf("pbr", "plastic", "dielectric")
            ),
            UniformPreset(
                name = "Glass",
                uniforms = mapOf(
                    "baseColor" to UniformValue(UniformType.VEC3, listOf(1.0f, 1.0f, 1.0f)),
                    "roughness" to UniformValue(UniformType.FLOAT, 0.0f),
                    "metallic" to UniformValue(UniformType.FLOAT, 0.0f),
                    "transparency" to UniformValue(UniformType.FLOAT, 0.9f)
                ),
                description = "Clear glass material",
                tags = listOf("pbr", "glass", "transparent")
            )
        )
    }

    // Undo/Redo functionality

    fun undo() {
        if (undoStack.isEmpty()) return

        val operation = undoStack.removeLastOrNull() ?: return
        redoStack.add(operation)

        // Limit redo stack size
        if (redoStack.size > maxUndoSteps) {
            redoStack.removeFirst()
        }

        applyOperation(operation.reverse())
    }

    fun redo() {
        if (redoStack.isEmpty()) return

        val operation = redoStack.removeLastOrNull() ?: return
        undoStack.add(operation)

        applyOperation(operation)
    }

    fun clearUndoHistory() {
        undoStack.clear()
        redoStack.clear()
    }

    private fun recordOperation(operation: UniformOperation) {
        undoStack.add(operation)
        redoStack.clear()

        // Limit undo stack size
        if (undoStack.size > maxUndoSteps) {
            undoStack.removeFirst()
        }
    }

    private fun applyOperation(operation: UniformOperation) {
        val currentUniforms = _uniforms.value.toMutableMap()

        when (operation) {
            is UniformOperation.ValueChange -> {
                currentUniforms[operation.name] = operation.newValue
                onUniformChanged(operation.name, operation.newValue)
            }
            is UniformOperation.ConstraintChange -> {
                currentUniforms[operation.name] = operation.newValue
                onUniformChanged(operation.name, operation.newValue)
            }
            is UniformOperation.MetadataChange -> {
                currentUniforms[operation.name] = operation.newValue
                onUniformChanged(operation.name, operation.newValue)
            }
            is UniformOperation.Add -> {
                currentUniforms[operation.name] = operation.uniform
                onUniformChanged(operation.name, operation.uniform)
            }
            is UniformOperation.Remove -> {
                currentUniforms.remove(operation.name)
            }
            is UniformOperation.PresetLoad -> {
                currentUniforms.clear()
                currentUniforms.putAll(operation.newUniforms)
                operation.newUniforms.forEach { (name, uniform) ->
                    onUniformChanged(name, uniform)
                }
            }
        }

        _uniforms.value = currentUniforms
        _hasUnsavedChanges.value = true
    }

    // Validation

    private fun validateUniform(name: String, uniform: UniformValue) {
        when (uniform.type) {
            UniformType.FLOAT -> {
                val value = (uniform.value as? Number)?.toFloat()
                    ?: throw IllegalArgumentException("FLOAT value must be a number")
                uniform.min?.let { min ->
                    if (value < min) throw IllegalArgumentException("Value $value below minimum $min")
                }
                uniform.max?.let { max ->
                    if (value > max) throw IllegalArgumentException("Value $value above maximum $max")
                }
            }
            UniformType.VEC2 -> {
                val list = uniform.value as? List<*>
                    ?: throw IllegalArgumentException("VEC2 value must be a list")
                if (list.size != 2) throw IllegalArgumentException("VEC2 value must have 2 components")
                list.forEach { component ->
                    if (component !is Number) throw IllegalArgumentException("VEC2 components must be numeric")
                }
            }
            UniformType.VEC3 -> {
                val list = uniform.value as? List<*>
                    ?: throw IllegalArgumentException("VEC3 value must be a list")
                if (list.size != 3) throw IllegalArgumentException("VEC3 value must have 3 components")
                list.forEach { component ->
                    if (component !is Number) throw IllegalArgumentException("VEC3 components must be numeric")
                }
            }
            UniformType.VEC4 -> {
                val list = uniform.value as? List<*>
                    ?: throw IllegalArgumentException("VEC4 value must be a list")
                if (list.size != 4) throw IllegalArgumentException("VEC4 value must have 4 components")
                list.forEach { component ->
                    if (component !is Number) throw IllegalArgumentException("VEC4 components must be numeric")
                }
            }
            UniformType.MATRIX3 -> {
                val list = uniform.value as? List<*>
                    ?: throw IllegalArgumentException("MATRIX3 value must be a list")
                if (list.size != 9) throw IllegalArgumentException("MATRIX3 value must have 9 components")
                list.forEach { component ->
                    if (component !is Number) throw IllegalArgumentException("MATRIX3 components must be numeric")
                }
            }
            UniformType.MATRIX4 -> {
                val list = uniform.value as? List<*>
                    ?: throw IllegalArgumentException("MATRIX4 value must be a list")
                if (list.size != 16) throw IllegalArgumentException("MATRIX4 value must have 16 components")
                list.forEach { component ->
                    if (component !is Number) throw IllegalArgumentException("MATRIX4 components must be numeric")
                }
            }
            UniformType.BOOL -> {
                if (uniform.value !is Boolean) {
                    throw IllegalArgumentException("BOOL value must be a boolean")
                }
            }
            UniformType.INT -> {
                if (uniform.value !is Int) {
                    throw IllegalArgumentException("INT value must be an integer")
                }
            }
            UniformType.TEXTURE_2D, UniformType.TEXTURE_CUBE, UniformType.TEXTURE_3D -> {
                if (uniform.value !is String) {
                    throw IllegalArgumentException("Texture uniform value must be a string (texture ID)")
                }
            }
        }
    }

    // Grouping functions

    private fun groupUniformsByType(uniforms: Map<String, UniformValue>): Map<String, List<Pair<String, UniformValue>>> {
        return uniforms.toList().groupBy { (_, uniform) ->
            when (uniform.type) {
                UniformType.FLOAT -> "Scalars"
                UniformType.VEC2, UniformType.VEC3, UniformType.VEC4 -> "Vectors"
                UniformType.MATRIX3, UniformType.MATRIX4 -> "Matrices"
                UniformType.BOOL -> "Booleans"
                UniformType.INT -> "Integers"
                UniformType.TEXTURE_2D, UniformType.TEXTURE_CUBE, UniformType.TEXTURE_3D -> "Textures"
            }
        }
    }

    private fun groupUniformsByCategory(uniforms: Map<String, UniformValue>): Map<String, List<Pair<String, UniformValue>>> {
        return uniforms.toList().groupBy { (_, uniform) ->
            uniform.category ?: "Uncategorized"
        }
    }

    private fun groupUniformsAlphabetically(uniforms: Map<String, UniformValue>): Map<String, List<Pair<String, UniformValue>>> {
        val sorted = uniforms.toList().sortedBy { (name, _) -> name }
        return mapOf("All Uniforms" to sorted)
    }

    // Utility functions

    fun getUniformDisplayName(name: String): String {
        val uniform = _uniforms.value[name]
        return uniform?.displayName ?: name.replaceFirstChar { it.uppercase() }
            .replace(Regex("([A-Z])"), " $1")
            .trim()
    }

    fun getUniformDescription(name: String): String? {
        return _uniforms.value[name]?.description
    }

    fun isUniformAdvanced(name: String): Boolean {
        val uniform = _uniforms.value[name] ?: return false
        return uniform.category?.contains("advanced", ignoreCase = true) == true ||
                name.contains("debug", ignoreCase = true) ||
                name.contains("internal", ignoreCase = true)
    }

    fun exportUniforms(): String {
        // Export uniforms as JSON for sharing/backup
        return kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.serializer<Map<String, UniformValue>>(),
            _uniforms.value
        )
    }

    fun importUniforms(json: String) {
        try {
            val importedUniforms = kotlinx.serialization.json.Json.decodeFromString<Map<String, UniformValue>>(json)

            recordOperation(UniformOperation.PresetLoad(_uniforms.value, importedUniforms))

            _uniforms.value = importedUniforms
            _hasUnsavedChanges.value = true

            importedUniforms.forEach { (name, uniform) ->
                onUniformChanged(name, uniform)
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid uniform data: ${e.message}")
        }
    }
}

// Data classes and enums

data class UniformPreset(
    val name: String,
    val uniforms: Map<String, UniformValue>,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val author: String = "Unknown",
    val created: kotlinx.datetime.Instant = kotlinx.datetime.Clock.System.now()
)

sealed class UniformOperation {
    abstract fun reverse(): UniformOperation

    data class ValueChange(
        val name: String,
        val oldValue: UniformValue,
        val newValue: UniformValue
    ) : UniformOperation() {
        override fun reverse() = ValueChange(name, newValue, oldValue)
    }

    data class ConstraintChange(
        val name: String,
        val oldValue: UniformValue,
        val newValue: UniformValue
    ) : UniformOperation() {
        override fun reverse() = ConstraintChange(name, newValue, oldValue)
    }

    data class MetadataChange(
        val name: String,
        val oldValue: UniformValue,
        val newValue: UniformValue
    ) : UniformOperation() {
        override fun reverse() = MetadataChange(name, newValue, oldValue)
    }

    data class Add(
        val name: String,
        val uniform: UniformValue
    ) : UniformOperation() {
        override fun reverse() = Remove(name, uniform)
    }

    data class Remove(
        val name: String,
        val uniform: UniformValue
    ) : UniformOperation() {
        override fun reverse() = Add(name, uniform)
    }

    data class PresetLoad(
        val oldUniforms: Map<String, UniformValue>,
        val newUniforms: Map<String, UniformValue>
    ) : UniformOperation() {
        override fun reverse() = PresetLoad(newUniforms, oldUniforms)
    }
}

enum class UniformGrouping {
    BY_TYPE, BY_CATEGORY, ALPHABETICAL, NONE
}

// UI Control data classes

data class FloatControl(
    val value: Float,
    val min: Float? = null,
    val max: Float? = null,
    val step: Float? = null,
    val precision: Int = 3
) {
    fun isInRange(): Boolean {
        return (min == null || value >= min) && (max == null || value <= max)
    }

    fun clamp(): FloatControl {
        val clampedValue = when {
            min != null && value < min -> min
            max != null && value > max -> max
            else -> value
        }
        return copy(value = clampedValue)
    }
}

data class VectorControl(
    val values: List<Float>,
    val min: Float? = null,
    val max: Float? = null,
    val step: Float? = null,
    val precision: Int = 3,
    val labels: List<String> = when(values.size) {
        2 -> listOf("X", "Y")
        3 -> listOf("X", "Y", "Z")
        4 -> listOf("X", "Y", "Z", "W")
        else -> values.indices.map { "[$it]" }
    }
) {
    fun isInRange(): Boolean {
        return values.all { value ->
            (min == null || value >= min) && (max == null || value <= max)
        }
    }

    fun clamp(): VectorControl {
        val clampedValues = values.map { value ->
            when {
                min != null && value < min -> min
                max != null && value > max -> max
                else -> value
            }
        }
        return copy(values = clampedValues)
    }
}

data class ColorControl(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float = 1f,
    val hasAlpha: Boolean = false,
    val colorSpace: ColorSpace = ColorSpace.SRGB
) {
    fun toColor(): Color = Color(r, g, b, a)

    fun toList(): List<Float> = if (hasAlpha) listOf(r, g, b, a) else listOf(r, g, b)

    companion object {
        fun fromList(values: List<Float>): ColorControl {
            return when (values.size) {
                3 -> ColorControl(values[0], values[1], values[2])
                4 -> ColorControl(values[0], values[1], values[2], values[3], hasAlpha = true)
                else -> throw IllegalArgumentException("Color must have 3 or 4 components")
            }
        }
    }
}

data class BooleanControl(
    val value: Boolean,
    val style: BooleanStyle = BooleanStyle.CHECKBOX
)

data class IntegerControl(
    val value: Int,
    val min: Int? = null,
    val max: Int? = null,
    val step: Int = 1
) {
    fun isInRange(): Boolean {
        return (min == null || value >= min) && (max == null || value <= max)
    }

    fun clamp(): IntegerControl {
        val clampedValue = when {
            min != null && value < min -> min
            max != null && value > max -> max
            else -> value
        }
        return copy(value = clampedValue)
    }
}

data class TextureControl(
    val textureId: String,
    val availableTextures: List<TextureReference> = emptyList(),
    val allowEmpty: Boolean = true
)

data class TextureReference(
    val id: String,
    val name: String,
    val path: String,
    val width: Int = 0,
    val height: Int = 0,
    val format: String = "unknown"
)

enum class ColorSpace {
    SRGB, LINEAR
}

enum class BooleanStyle {
    CHECKBOX, TOGGLE, BUTTON
}