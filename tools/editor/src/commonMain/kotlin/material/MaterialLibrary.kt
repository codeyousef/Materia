package io.materia.tools.editor.material

import io.materia.tools.editor.data.MaterialDefinition
import io.materia.tools.editor.data.MaterialType
import io.materia.tools.editor.data.UniformValue
import io.materia.tools.editor.data.UniformType
import io.materia.tools.editor.data.MaterialSettings
import io.materia.tools.editor.data.MaterialMetadata
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * MaterialLibrary - Comprehensive material library management system
 *
 * This component provides a complete material library solution including:
 * - Material storage and organization with categories and tags
 * - Search and filtering capabilities across all material properties
 * - Import/export functionality for various formats (JSON, WGSL, etc.)
 * - Template system with industry-standard PBR presets
 * - Version control and material history tracking
 * - Collaboration features with sharing and synchronization
 * - Custom shader template management
 */
class MaterialLibrary(
    private val scope: CoroutineScope,
    private val storageProvider: MaterialStorageProvider,
    private val onLibraryChanged: () -> Unit = {}
) {

    // Library state
    private val _materials = MutableStateFlow<Map<String, MaterialDefinition>>(emptyMap())
    private val _categories = MutableStateFlow<Map<String, MaterialCategory>>(emptyMap())
    private val _templates = MutableStateFlow<Map<String, MaterialTemplate>>(emptyMap())
    private val _tags = MutableStateFlow<Set<String>>(emptySet())

    // UI state
    private val _selectedMaterial = MutableStateFlow<String?>(null)
    private val _selectedCategory = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _filterOptions = MutableStateFlow(MaterialFilter())
    private val _sortBy = MutableStateFlow(MaterialSortBy.NAME)
    private val _viewMode = MutableStateFlow(MaterialViewMode.GRID)

    // Operation state
    private val _isLoading = MutableStateFlow(false)
    private val _isSaving = MutableStateFlow(false)
    private val _lastError = MutableStateFlow<String?>(null)
    private val _pendingChanges = MutableStateFlow<Set<String>>(emptySet())

    // Statistics
    private val _statistics = MutableStateFlow(LibraryStatistics())

    // Public read-only state
    val materials: StateFlow<Map<String, MaterialDefinition>> = _materials.asStateFlow()
    val categories: StateFlow<Map<String, MaterialCategory>> = _categories.asStateFlow()
    val templates: StateFlow<Map<String, MaterialTemplate>> = _templates.asStateFlow()
    val tags: StateFlow<Set<String>> = _tags.asStateFlow()

    val selectedMaterial: StateFlow<String?> = _selectedMaterial.asStateFlow()
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val filterOptions: StateFlow<MaterialFilter> = _filterOptions.asStateFlow()
    val sortBy: StateFlow<MaterialSortBy> = _sortBy.asStateFlow()
    val viewMode: StateFlow<MaterialViewMode> = _viewMode.asStateFlow()

    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    val pendingChanges: StateFlow<Set<String>> = _pendingChanges.asStateFlow()
    val hasPendingChanges: StateFlow<Boolean> = _pendingChanges.map { it.isNotEmpty() }

    val statistics: StateFlow<LibraryStatistics> = _statistics.asStateFlow()

    // Derived state - filtered and sorted materials
    val filteredMaterials: StateFlow<List<MaterialDefinition>> = combine(
        _materials, _searchQuery, _filterOptions, _sortBy
    ) { materials, query, filter, sortBy ->
        val filtered = materials.values
            .filter { material -> matchesFilter(material, query, filter) }
            .sortedWith(getSortComparator(sortBy))

        filtered
    }

    val materialsByCategory: StateFlow<Map<String, List<MaterialDefinition>>> = combine(
        filteredMaterials, _categories
    ) { materials, categories ->
        val categorized = materials.groupBy { material ->
            material.metadata.tags.firstOrNull { tag ->
                categories.containsKey(tag)
            } ?: "Uncategorized"
        }

        // Sort categories by display order
        categorized.toSortedMap { a, b ->
            val categoryA = categories[a]
            val categoryB = categories[b]
            when {
                categoryA == null && categoryB == null -> a.compareTo(b)
                categoryA == null -> 1
                categoryB == null -> -1
                else -> categoryA.displayOrder.compareTo(categoryB.displayOrder)
            }
        }
    }

    init {
        // Initialize library
        scope.launch {
            loadLibrary()
        }

        // Update statistics when materials change
        scope.launch {
            _materials.collect { materials ->
                updateStatistics(materials.values)
            }
        }

        // Update tags when materials change
        scope.launch {
            _materials.collect { materials ->
                val allTags = materials.values.flatMap { it.metadata.tags }.toSet()
                _tags.value = allTags
            }
        }
    }

    // Library management

    suspend fun loadLibrary() {
        _isLoading.value = true
        _lastError.value = null

        try {
            val libraryData = storageProvider.loadLibrary()

            _materials.value = libraryData.materials
            _categories.value = libraryData.categories
            _templates.value = libraryData.templates

            // Load default templates if library is empty
            if (libraryData.templates.isEmpty()) {
                loadDefaultTemplates()
            }

        } catch (e: Exception) {
            _lastError.value = "Failed to load library: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun saveLibrary() {
        _isSaving.value = true
        _lastError.value = null

        try {
            val libraryData = LibraryData(
                materials = _materials.value,
                categories = _categories.value,
                templates = _templates.value,
                version = 1,
                lastModified = Clock.System.now()
            )

            storageProvider.saveLibrary(libraryData)
            _pendingChanges.value = emptySet()
            onLibraryChanged()

        } catch (e: Exception) {
            _lastError.value = "Failed to save library: ${e.message}"
        } finally {
            _isSaving.value = false
        }
    }

    suspend fun saveIfNeeded() {
        if (_pendingChanges.value.isNotEmpty()) {
            saveLibrary()
        }
    }

    suspend fun reloadLibrary() {
        _pendingChanges.value = emptySet()
        loadLibrary()
    }

    // Material management

    @OptIn(ExperimentalUuidApi::class)
    fun addMaterial(material: MaterialDefinition): String {
        val id = material.id.ifEmpty { Uuid.random().toString() }
        val materialWithId = material.copy(id = id)

        val currentMaterials = _materials.value.toMutableMap()
        currentMaterials[id] = materialWithId
        _materials.value = currentMaterials

        markPendingChange(id)
        return id
    }

    fun updateMaterial(id: String, material: MaterialDefinition) {
        val currentMaterials = _materials.value
        if (!currentMaterials.containsKey(id)) {
            throw IllegalArgumentException("Material with ID '$id' not found")
        }

        val updatedMaterial = material.copy(id = id).withUpdatedMetadata()
        val newMaterials = currentMaterials + (id to updatedMaterial)
        _materials.value = newMaterials

        markPendingChange(id)
    }

    fun removeMaterial(id: String) {
        val currentMaterials = _materials.value.toMutableMap()
        currentMaterials.remove(id)
        _materials.value = currentMaterials

        if (_selectedMaterial.value == id) {
            _selectedMaterial.value = null
        }

        markPendingChange(id)
    }

    fun duplicateMaterial(id: String, newName: String): String {
        val original = _materials.value[id]
            ?: throw IllegalArgumentException("Material with ID '$id' not found")

        val duplicate = original.duplicate(newName)
        return addMaterial(duplicate)
    }

    fun getMaterial(id: String): MaterialDefinition? {
        return _materials.value[id]
    }

    fun getMaterialsByType(type: MaterialType): List<MaterialDefinition> {
        return _materials.value.values.filter { it.type == type }
    }

    fun getMaterialsByTag(tag: String): List<MaterialDefinition> {
        return _materials.value.values.filter { it.metadata.tags.contains(tag) }
    }

    // Category management

    @OptIn(ExperimentalUuidApi::class)
    fun addCategory(name: String, description: String = "", color: String = "#808080"): String {
        val id = Uuid.random().toString()
        val category = MaterialCategory(
            id = id,
            name = name,
            description = description,
            color = color,
            displayOrder = _categories.value.size,
            created = Clock.System.now()
        )

        val currentCategories = _categories.value.toMutableMap()
        currentCategories[id] = category
        _categories.value = currentCategories

        markPendingChange("category_$id")
        return id
    }

    fun updateCategory(id: String, name: String, description: String = "", color: String = "#808080") {
        val currentCategories = _categories.value
        val category = currentCategories[id]
            ?: throw IllegalArgumentException("Category with ID '$id' not found")

        val updatedCategory = category.copy(
            name = name,
            description = description,
            color = color
        )

        val newCategories = currentCategories + (id to updatedCategory)
        _categories.value = newCategories

        markPendingChange("category_$id")
    }

    fun removeCategory(id: String) {
        val currentCategories = _categories.value.toMutableMap()
        currentCategories.remove(id)
        _categories.value = currentCategories

        if (_selectedCategory.value == id) {
            _selectedCategory.value = null
        }

        markPendingChange("category_$id")
    }

    fun reorderCategories(categoryIds: List<String>) {
        val currentCategories = _categories.value.toMutableMap()

        categoryIds.forEachIndexed { index, id ->
            val category = currentCategories[id]
            if (category != null) {
                currentCategories[id] = category.copy(displayOrder = index)
            }
        }

        _categories.value = currentCategories
        markPendingChange("categories_reorder")
    }

    // Template management

    @OptIn(ExperimentalUuidApi::class)
    fun addTemplate(template: MaterialTemplate): String {
        val id = template.id.ifEmpty { Uuid.random().toString() }
        val templateWithId = template.copy(id = id)

        val currentTemplates = _templates.value.toMutableMap()
        currentTemplates[id] = templateWithId
        _templates.value = currentTemplates

        markPendingChange("template_$id")
        return id
    }

    fun updateTemplate(id: String, template: MaterialTemplate) {
        val currentTemplates = _templates.value
        if (!currentTemplates.containsKey(id)) {
            throw IllegalArgumentException("Template with ID '$id' not found")
        }

        val updatedTemplate = template.copy(id = id)
        val newTemplates = currentTemplates + (id to updatedTemplate)
        _templates.value = newTemplates

        markPendingChange("template_$id")
    }

    fun removeTemplate(id: String) {
        val currentTemplates = _templates.value.toMutableMap()
        val template = currentTemplates[id]

        if (template?.isBuiltIn == true) {
            throw IllegalArgumentException("Cannot remove built-in template")
        }

        currentTemplates.remove(id)
        _templates.value = currentTemplates

        markPendingChange("template_$id")
    }

    fun createMaterialFromTemplate(templateId: String, name: String): MaterialDefinition {
        val template = _templates.value[templateId]
            ?: throw IllegalArgumentException("Template with ID '$templateId' not found")

        return template.createMaterial(name)
    }

    // Search and filtering

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterOptions(filter: MaterialFilter) {
        _filterOptions.value = filter
    }

    fun setSortBy(sortBy: MaterialSortBy) {
        _sortBy.value = sortBy
    }

    fun setViewMode(viewMode: MaterialViewMode) {
        _viewMode.value = viewMode
    }

    fun setSelectedMaterial(id: String?) {
        _selectedMaterial.value = id
    }

    fun setSelectedCategory(id: String?) {
        _selectedCategory.value = id
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _filterOptions.value = MaterialFilter()
    }

    // Import/Export functionality

    suspend fun importMaterials(data: String, format: ImportFormat): ImportResult {
        return try {
            when (format) {
                ImportFormat.JSON -> importFromJson(data)
                ImportFormat.WGSL -> importFromWGSL(data)
                ImportFormat.GLTF -> importFromGLTF(data)
                ImportFormat.OBJ_MTL -> importFromOBJ(data)
            }
        } catch (e: Exception) {
            ImportResult(
                success = false,
                importedMaterials = emptyList(),
                errors = listOf("Import failed: ${e.message}")
            )
        }
    }

    suspend fun exportMaterials(materialIds: List<String>, format: ExportFormat): ExportResult {
        return try {
            val materials = materialIds.mapNotNull { _materials.value[it] }
            when (format) {
                ExportFormat.JSON -> exportToJson(materials)
                ExportFormat.WGSL -> exportToWGSL(materials)
                ExportFormat.GLTF -> exportToGLTF(materials)
                ExportFormat.LIBRARY -> exportLibraryFile(materials)
            }
        } catch (e: Exception) {
            ExportResult(
                success = false,
                data = "",
                filename = "",
                errors = listOf("Export failed: ${e.message}")
            )
        }
    }

    suspend fun exportLibrary(): ExportResult {
        val allMaterials = _materials.value.values.toList()
        return exportToJson(allMaterials).copy(filename = "material_library.json")
    }

    // Collaboration features

    suspend fun shareMaterial(id: String): ShareResult {
        val material = _materials.value[id]
            ?: return ShareResult(false, "", "Material not found")

        return try {
            val shareData = Json.encodeToString(MaterialDefinition.serializer(), material)
            val shareId = storageProvider.shareMaterial(shareData)
            ShareResult(true, shareId, "Material shared successfully")
        } catch (e: Exception) {
            ShareResult(false, "", "Failed to share material: ${e.message}")
        }
    }

    suspend fun importSharedMaterial(shareId: String): ImportResult {
        return try {
            val shareData = storageProvider.getSharedMaterial(shareId)
            val material = Json.decodeFromString(MaterialDefinition.serializer(), shareData)

            val importedId = addMaterial(material)
            saveLibrary()

            ImportResult(
                success = true,
                importedMaterials = listOf(importedId),
                errors = emptyList()
            )
        } catch (e: Exception) {
            ImportResult(
                success = false,
                importedMaterials = emptyList(),
                errors = listOf("Failed to import shared material: ${e.message}")
            )
        }
    }

    suspend fun syncWithRemoteLibrary(remoteUrl: String): SyncResult {
        // Placeholder for remote synchronization
        return SyncResult(
            success = false,
            syncedMaterials = emptyList(),
            conflicts = emptyList(),
            errors = listOf("Remote sync not implemented")
        )
    }

    // Private implementation

    private fun markPendingChange(changeId: String) {
        val pending = _pendingChanges.value.toMutableSet()
        pending.add(changeId)
        _pendingChanges.value = pending
    }

    private fun updateStatistics(materials: Collection<MaterialDefinition>) {
        val stats = LibraryStatistics(
            totalMaterials = materials.size,
            materialsByType = materials.groupBy { it.type }.mapValues { it.value.size },
            materialsByTag = materials.flatMap { it.metadata.tags }
                .groupBy { it }.mapValues { it.value.size },
            totalTemplates = _templates.value.size,
            totalCategories = _categories.value.size,
            lastModified = Clock.System.now()
        )
        _statistics.value = stats
    }

    private fun matchesFilter(
        material: MaterialDefinition,
        query: String,
        filter: MaterialFilter
    ): Boolean {
        // Text search
        if (query.isNotBlank()) {
            val searchText = query.lowercase()
            val matchesName = material.name.lowercase().contains(searchText)
            val matchesDescription = material.metadata.description.lowercase().contains(searchText)
            val matchesTags = material.metadata.tags.any { it.lowercase().contains(searchText) }
            val matchesAuthor = material.metadata.author.lowercase().contains(searchText)

            if (!matchesName && !matchesDescription && !matchesTags && !matchesAuthor) {
                return false
            }
        }

        // Type filter
        if (filter.types.isNotEmpty() && !filter.types.contains(material.type)) {
            return false
        }

        // Tag filter
        if (filter.tags.isNotEmpty()) {
            val hasMatchingTag = filter.tags.any { material.metadata.tags.contains(it) }
            if (!hasMatchingTag) return false
        }

        // Author filter
        if (filter.authors.isNotEmpty() && !filter.authors.contains(material.metadata.author)) {
            return false
        }

        // Date range filter
        if (filter.dateRange != null) {
            val created = material.metadata.created
            if (created < filter.dateRange.start || created > filter.dateRange.end) {
                return false
            }
        }

        // Custom shader filter
        if (filter.hasCustomShader != null) {
            val hasShader = material.shaderSource != null
            if (filter.hasCustomShader != hasShader) {
                return false
            }
        }

        return true
    }

    private fun getSortComparator(sortBy: MaterialSortBy): Comparator<MaterialDefinition> {
        return when (sortBy) {
            MaterialSortBy.NAME -> compareBy { it.name.lowercase() }
            MaterialSortBy.TYPE -> compareBy<MaterialDefinition> { it.type }.thenBy { it.name.lowercase() }
            MaterialSortBy.CREATED -> compareByDescending { it.metadata.created }
            MaterialSortBy.MODIFIED -> compareByDescending { it.metadata.lastModified }
            MaterialSortBy.AUTHOR -> compareBy<MaterialDefinition> { it.metadata.author }.thenBy { it.name.lowercase() }
        }
    }

    private fun loadDefaultTemplates() {
        val defaultTemplates = listOf(
            createPBRTemplate(),
            createUnlitTemplate(),
            createStandardTemplate(),
            createToonTemplate(),
            createGlassTemplate(),
            createMetalTemplate(),
            createFabricTemplate()
        )

        val templatesMap = defaultTemplates.associateBy { it.id }
        _templates.value = templatesMap
    }

    // Import implementations

    private fun importFromJson(data: String): ImportResult {
        val materials = mutableListOf<String>()
        val errors = mutableListOf<String>()

        try {
            // Try to parse as single material
            val material = Json.decodeFromString<MaterialDefinition>(data)
            val id = addMaterial(material)
            materials.add(id)
        } catch (e: Exception) {
            try {
                // Try to parse as array of materials
                val materialList = Json.decodeFromString<List<MaterialDefinition>>(data)
                materialList.forEach { material ->
                    try {
                        val id = addMaterial(material)
                        materials.add(id)
                    } catch (materialError: Exception) {
                        errors.add("Failed to import material '${material.name}': ${materialError.message}")
                    }
                }
            } catch (arrayError: Exception) {
                errors.add("Invalid JSON format: ${e.message}")
            }
        }

        return ImportResult(
            success = materials.isNotEmpty(),
            importedMaterials = materials,
            errors = errors
        )
    }

    private fun importFromWGSL(data: String): ImportResult {
        // Parse WGSL shader and create a basic material
        val material = MaterialDefinition.createUnlit(
            name = "Imported WGSL Material",
            color = listOf(1f, 1f, 1f)
        ).copy(
            shaderSource = io.materia.tools.editor.data.ShaderCode(
                vertex = extractVertexShader(data),
                fragment = extractFragmentShader(data)
            )
        )

        val id = addMaterial(material)
        return ImportResult(
            success = true,
            importedMaterials = listOf(id),
            errors = emptyList()
        )
    }

    private fun importFromGLTF(data: String): ImportResult {
        // Placeholder for GLTF material import
        return ImportResult(
            success = false,
            importedMaterials = emptyList(),
            errors = listOf("GLTF import not implemented")
        )
    }

    private fun importFromOBJ(data: String): ImportResult {
        // Placeholder for OBJ/MTL material import
        return ImportResult(
            success = false,
            importedMaterials = emptyList(),
            errors = listOf("OBJ/MTL import not implemented")
        )
    }

    // Export implementations

    private fun exportToJson(materials: List<MaterialDefinition>): ExportResult {
        val json = if (materials.size == 1) {
            Json.encodeToString(MaterialDefinition.serializer(), materials.first())
        } else {
            Json.encodeToString(materials)
        }

        val filename = if (materials.size == 1) {
            "${materials.first().name.replace(" ", "_")}.json"
        } else {
            "materials_${materials.size}.json"
        }

        return ExportResult(
            success = true,
            data = json,
            filename = filename,
            errors = emptyList()
        )
    }

    private fun exportToWGSL(materials: List<MaterialDefinition>): ExportResult {
        val wgslCode = materials.joinToString("\n\n") { material ->
            """
            // Material: ${material.name}
            // Type: ${material.type}
            // Author: ${material.metadata.author}

            ${material.shaderSource?.vertex ?: "// No vertex shader"}

            ${material.shaderSource?.fragment ?: "// No fragment shader"}
            """.trimIndent()
        }

        return ExportResult(
            success = true,
            data = wgslCode,
            filename = "materials.wgsl",
            errors = emptyList()
        )
    }

    private fun exportToGLTF(materials: List<MaterialDefinition>): ExportResult {
        // Placeholder for GLTF export
        return ExportResult(
            success = false,
            data = "",
            filename = "",
            errors = listOf("GLTF export not implemented")
        )
    }

    private fun exportLibraryFile(materials: List<MaterialDefinition>): ExportResult {
        val libraryData = LibraryData(
            materials = materials.associateBy { it.id },
            categories = _categories.value,
            templates = _templates.value,
            version = 1,
            lastModified = Clock.System.now()
        )

        val json = Json.encodeToString(LibraryData.serializer(), libraryData)

        return ExportResult(
            success = true,
            data = json,
            filename = "material_library.materia",
            errors = emptyList()
        )
    }

    // Utility functions

    private fun extractVertexShader(wgslCode: String): String {
        // Extract vertex shader from WGSL code
        val vertexStart = wgslCode.indexOf("@vertex")
        if (vertexStart == -1) return ""

        var braceCount = 0
        var inFunction = false
        val result = StringBuilder()

        for (i in vertexStart until wgslCode.length) {
            val char = wgslCode[i]
            result.append(char)

            when (char) {
                '{' -> {
                    inFunction = true
                    braceCount++
                }
                '}' -> {
                    braceCount--
                    if (inFunction && braceCount == 0) {
                        break
                    }
                }
            }
        }

        return result.toString()
    }

    private fun extractFragmentShader(wgslCode: String): String {
        // Extract fragment shader from WGSL code
        val fragmentStart = wgslCode.indexOf("@fragment")
        if (fragmentStart == -1) return ""

        var braceCount = 0
        var inFunction = false
        val result = StringBuilder()

        for (i in fragmentStart until wgslCode.length) {
            val char = wgslCode[i]
            result.append(char)

            when (char) {
                '{' -> {
                    inFunction = true
                    braceCount++
                }
                '}' -> {
                    braceCount--
                    if (inFunction && braceCount == 0) {
                        break
                    }
                }
            }
        }

        return result.toString()
    }

    // Template creation functions

    @OptIn(ExperimentalUuidApi::class)
    private fun createPBRTemplate(): MaterialTemplate {
        return MaterialTemplate(
            id = "template_pbr_standard",
            name = "PBR Standard",
            description = "Physically Based Rendering material with metallic workflow",
            category = "PBR",
            isBuiltIn = true,
            uniforms = mapOf(
                "baseColor" to UniformValue(UniformType.VEC3, listOf(0.8f, 0.8f, 0.8f)),
                "roughness" to UniformValue(UniformType.FLOAT, 0.5f),
                "metallic" to UniformValue(UniformType.FLOAT, 0.0f),
                "emissive" to UniformValue(UniformType.VEC3, listOf(0f, 0f, 0f))
            ),
            shaderTemplate = null, // Would use built-in PBR shader
            previewImage = null,
            tags = listOf("pbr", "standard", "builtin")
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun createUnlitTemplate(): MaterialTemplate {
        return MaterialTemplate(
            id = "template_unlit",
            name = "Unlit",
            description = "Simple unlit material for UI and effects",
            category = "Basic",
            isBuiltIn = true,
            uniforms = mapOf(
                "color" to UniformValue(UniformType.VEC4, listOf(1f, 1f, 1f, 1f))
            ),
            shaderTemplate = """
                @vertex
                fn vs_main(@location(0) position: vec3<f32>) -> @builtin(position) vec4<f32> {
                    return vec4<f32>(position, 1.0);
                }

                @group(0) @binding(0) var<uniform> color: vec4<f32>;

                @fragment
                fn fs_main() -> @location(0) vec4<f32> {
                    return color;
                }
            """.trimIndent(),
            previewImage = null,
            tags = listOf("unlit", "simple", "builtin")
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun createStandardTemplate(): MaterialTemplate {
        return MaterialTemplate(
            id = "template_standard",
            name = "Standard",
            description = "Basic lit material with diffuse and specular",
            category = "Basic",
            isBuiltIn = true,
            uniforms = mapOf(
                "albedo" to UniformValue(UniformType.VEC3, listOf(0.8f, 0.8f, 0.8f)),
                "specular" to UniformValue(UniformType.FLOAT, 0.5f),
                "shininess" to UniformValue(UniformType.FLOAT, 32f)
            ),
            shaderTemplate = null, // Would use built-in standard shader
            previewImage = null,
            tags = listOf("standard", "lit", "builtin")
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun createToonTemplate(): MaterialTemplate {
        return MaterialTemplate(
            id = "template_toon",
            name = "Toon",
            description = "Cartoon-style material with stepped lighting",
            category = "Stylized",
            isBuiltIn = true,
            uniforms = mapOf(
                "color" to UniformValue(UniformType.VEC3, listOf(1f, 0.5f, 0.2f)),
                "steps" to UniformValue(UniformType.INT, 3),
                "rimPower" to UniformValue(UniformType.FLOAT, 2f)
            ),
            shaderTemplate = null, // Would use built-in toon shader
            previewImage = null,
            tags = listOf("toon", "stylized", "cartoon", "builtin")
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun createGlassTemplate(): MaterialTemplate {
        return MaterialTemplate(
            id = "template_glass",
            name = "Glass",
            description = "Transparent glass material with refraction",
            category = "Transparent",
            isBuiltIn = true,
            uniforms = mapOf(
                "color" to UniformValue(UniformType.VEC3, listOf(1f, 1f, 1f)),
                "transparency" to UniformValue(UniformType.FLOAT, 0.9f),
                "ior" to UniformValue(UniformType.FLOAT, 1.5f),
                "roughness" to UniformValue(UniformType.FLOAT, 0.0f)
            ),
            shaderTemplate = null, // Would use built-in glass shader
            previewImage = null,
            tags = listOf("glass", "transparent", "refraction", "builtin")
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun createMetalTemplate(): MaterialTemplate {
        return MaterialTemplate(
            id = "template_metal",
            name = "Metal",
            description = "Metallic material preset",
            category = "PBR",
            isBuiltIn = true,
            uniforms = mapOf(
                "baseColor" to UniformValue(UniformType.VEC3, listOf(0.7f, 0.7f, 0.7f)),
                "roughness" to UniformValue(UniformType.FLOAT, 0.1f),
                "metallic" to UniformValue(UniformType.FLOAT, 1.0f)
            ),
            shaderTemplate = null, // Would use built-in PBR shader
            previewImage = null,
            tags = listOf("metal", "metallic", "pbr", "builtin")
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun createFabricTemplate(): MaterialTemplate {
        return MaterialTemplate(
            id = "template_fabric",
            name = "Fabric",
            description = "Fabric material with subsurface scattering",
            category = "Organic",
            isBuiltIn = true,
            uniforms = mapOf(
                "baseColor" to UniformValue(UniformType.VEC3, listOf(0.8f, 0.2f, 0.2f)),
                "roughness" to UniformValue(UniformType.FLOAT, 0.9f),
                "metallic" to UniformValue(UniformType.FLOAT, 0.0f),
                "subsurface" to UniformValue(UniformType.FLOAT, 0.3f)
            ),
            shaderTemplate = null, // Would use built-in fabric shader
            previewImage = null,
            tags = listOf("fabric", "cloth", "organic", "subsurface", "builtin")
        )
    }
}

// Data classes and interfaces

interface MaterialStorageProvider {
    suspend fun loadLibrary(): LibraryData
    suspend fun saveLibrary(data: LibraryData)
    suspend fun shareMaterial(materialData: String): String
    suspend fun getSharedMaterial(shareId: String): String
}

@Serializable
data class LibraryData(
    val materials: Map<String, MaterialDefinition>,
    val categories: Map<String, MaterialCategory>,
    val templates: Map<String, MaterialTemplate>,
    val version: Int,
    val lastModified: Instant
)

@Serializable
data class MaterialCategory(
    val id: String,
    val name: String,
    val description: String = "",
    val color: String = "#808080", // Hex color for UI display
    val displayOrder: Int = 0,
    val created: Instant
)

@Serializable
data class MaterialTemplate @OptIn(ExperimentalUuidApi::class) constructor(
    val id: String = Uuid.random().toString(),
    val name: String,
    val description: String = "",
    val category: String = "Custom",
    val isBuiltIn: Boolean = false,
    val uniforms: Map<String, UniformValue>,
    val shaderTemplate: String? = null, // WGSL shader template
    val previewImage: String? = null, // Base64 encoded preview
    val tags: List<String> = emptyList(),
    val author: String = "Unknown",
    val created: Instant = Clock.System.now()
) {
    @OptIn(ExperimentalUuidApi::class)
    fun createMaterial(name: String): MaterialDefinition {
        val now = Clock.System.now()
        return MaterialDefinition(
            id = Uuid.random().toString(),
            name = name,
            type = MaterialType.CUSTOM_SHADER,
            shaderSource = shaderTemplate?.let { template ->
                io.materia.tools.editor.data.ShaderCode(
                    vertex = extractVertexFromTemplate(template),
                    fragment = extractFragmentFromTemplate(template)
                )
            },
            uniforms = uniforms,
            textures = emptyMap(),
            settings = MaterialSettings.default(),
            metadata = MaterialMetadata(
                author = "User",
                description = "Created from template: $name",
                tags = listOf("template", category.lowercase()),
                created = now,
                lastModified = now,
                version = 1
            )
        )
    }

    private fun extractVertexFromTemplate(template: String): String {
        val vertexStart = template.indexOf("@vertex")
        if (vertexStart == -1) return ""

        var braceCount = 0
        var inFunction = false
        val result = StringBuilder()

        for (i in vertexStart until template.length) {
            val char = template[i]
            result.append(char)

            when (char) {
                '{' -> {
                    inFunction = true
                    braceCount++
                }
                '}' -> {
                    braceCount--
                    if (inFunction && braceCount == 0) {
                        break
                    }
                }
            }
        }

        return result.toString()
    }

    private fun extractFragmentFromTemplate(template: String): String {
        val fragmentStart = template.indexOf("@fragment")
        if (fragmentStart == -1) return ""

        var braceCount = 0
        var inFunction = false
        val result = StringBuilder()

        for (i in fragmentStart until template.length) {
            val char = template[i]
            result.append(char)

            when (char) {
                '{' -> {
                    inFunction = true
                    braceCount++
                }
                '}' -> {
                    braceCount--
                    if (inFunction && braceCount == 0) {
                        break
                    }
                }
            }
        }

        return result.toString()
    }
}

@Serializable
data class MaterialFilter(
    val types: Set<MaterialType> = emptySet(),
    val tags: Set<String> = emptySet(),
    val authors: Set<String> = emptySet(),
    val dateRange: DateRange? = null,
    val hasCustomShader: Boolean? = null
)

@Serializable
data class DateRange(
    val start: Instant,
    val end: Instant
)

data class LibraryStatistics(
    val totalMaterials: Int = 0,
    val materialsByType: Map<MaterialType, Int> = emptyMap(),
    val materialsByTag: Map<String, Int> = emptyMap(),
    val totalTemplates: Int = 0,
    val totalCategories: Int = 0,
    val lastModified: Instant = Clock.System.now()
)

data class ImportResult(
    val success: Boolean,
    val importedMaterials: List<String>,
    val errors: List<String>
)

data class ExportResult(
    val success: Boolean,
    val data: String,
    val filename: String,
    val errors: List<String>
)

data class ShareResult(
    val success: Boolean,
    val shareId: String,
    val message: String
)

data class SyncResult(
    val success: Boolean,
    val syncedMaterials: List<String>,
    val conflicts: List<String>,
    val errors: List<String>
)

enum class MaterialSortBy {
    NAME, TYPE, CREATED, MODIFIED, AUTHOR
}

enum class MaterialViewMode {
    GRID, LIST, TILES
}

enum class ImportFormat {
    JSON, WGSL, GLTF, OBJ_MTL
}

enum class ExportFormat {
    JSON, WGSL, GLTF, LIBRARY
}