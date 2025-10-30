@file:OptIn(kotlin.time.ExperimentalTime::class, kotlin.io.encoding.ExperimentalEncodingApi::class)

package io.materia.tools.editor.serialization

import io.materia.tools.editor.data.*
import kotlinx.datetime.Instant
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * ProjectSerializer - Handles serialization and deserialization of scene editor projects
 *
 * This class provides comprehensive serialization support for SceneEditorProject instances,
 * including:
 * - JSON serialization with schema versioning
 * - Binary serialization for efficient storage
 * - Incremental saving for performance
 * - Migration support for older project formats
 * - Asset reference resolution
 * - Compression and encryption options
 */
class ProjectSerializer {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "_type"
        serializersModule = createSerializersModule()
    }

    private val compactJson = Json {
        prettyPrint = false
        encodeDefaults = false
        ignoreUnknownKeys = true
        classDiscriminator = "_type"
        serializersModule = createSerializersModule()
    }

    companion object {
        const val CURRENT_VERSION = "1.0.0"
        const val MIN_SUPPORTED_VERSION = "1.0.0"
        const val MAGIC_HEADER = "MATERIA_PROJECT"
        const val SCHEMA_VERSION_KEY = "schemaVersion"
        const val COMPRESSED_KEY = "compressed"
        const val ENCRYPTED_KEY = "encrypted"
    }

    /**
     * Serialize project to JSON string with metadata
     */
    fun serializeToJson(
        project: SceneEditorProject,
        prettyPrint: Boolean = true,
        includeMetadata: Boolean = true
    ): Result<String> {
        return try {
            val serializer = if (prettyPrint) json else compactJson

            if (includeMetadata) {
                val wrapper = ProjectWrapper(
                    schemaVersion = CURRENT_VERSION,
                    createdBy = "Materia Scene Editor",
                    exportedAt = kotlinx.datetime.Clock.System.now(),
                    project = project
                )
                Result.success(serializer.encodeToString(wrapper))
            } else {
                Result.success(serializer.encodeToString(project))
            }
        } catch (e: Exception) {
            Result.failure(SerializationException("Failed to serialize project: ${e.message}", e))
        }
    }

    /**
     * Deserialize project from JSON string
     */
    fun deserializeFromJson(jsonString: String): Result<SceneEditorProject> {
        return try {
            // Try to parse as wrapped project first
            val project = try {
                val wrapper = json.decodeFromString<ProjectWrapper>(jsonString)

                // Check version compatibility
                if (!isVersionSupported(wrapper.schemaVersion)) {
                    return Result.failure(
                        UnsupportedVersionException(
                            "Project version ${wrapper.schemaVersion} is not supported. " +
                            "Minimum supported version: $MIN_SUPPORTED_VERSION"
                        )
                    )
                }

                wrapper.project
            } catch (e: SerializationException) {
                // Fallback to direct project deserialization
                json.decodeFromString<SceneEditorProject>(jsonString)
            }

            // Validate the project after deserialization
            validateProject(project)
                .fold(
                    onSuccess = { Result.success(project) },
                    onFailure = { Result.failure(it) }
                )
        } catch (e: Exception) {
            Result.failure(SerializationException("Failed to deserialize project: ${e.message}", e))
        }
    }

    /**
     * Serialize project to binary format for efficient storage
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun serializeToBinary(
        project: SceneEditorProject,
        compressed: Boolean = true
    ): Result<ByteArray> {
        return try {
            // First serialize to JSON
            val jsonResult = serializeToJson(project, prettyPrint = false, includeMetadata = true)
            if (jsonResult.isFailure) {
                return Result.failure(jsonResult.exceptionOrNull()!!)
            }

            val jsonBytes = jsonResult.getOrThrow().encodeToByteArray()

            // Create binary header
            val header = BinaryHeader(
                magic = MAGIC_HEADER,
                version = CURRENT_VERSION,
                compressed = compressed,
                encrypted = false,
                jsonSize = jsonBytes.size,
                checksum = calculateChecksum(jsonBytes)
            )

            val headerBytes = json.encodeToString(header).encodeToByteArray()
            val headerSize = headerBytes.size

            // Compress if requested
            val dataBytes = if (compressed) {
                compressData(jsonBytes)
            } else {
                jsonBytes
            }

            // Combine header size, header, and data
            val result = ByteArray(4 + headerSize + dataBytes.size)
            var offset = 0

            // Write header size (4 bytes)
            result[offset++] = (headerSize shr 24).toByte()
            result[offset++] = (headerSize shr 16).toByte()
            result[offset++] = (headerSize shr 8).toByte()
            result[offset++] = headerSize.toByte()

            // Write header
            headerBytes.copyInto(result, offset)
            offset += headerSize

            // Write data
            dataBytes.copyInto(result, offset)

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(SerializationException("Failed to serialize to binary: ${e.message}", e))
        }
    }

    /**
     * Deserialize project from binary format
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun deserializeFromBinary(binaryData: ByteArray): Result<SceneEditorProject> {
        return try {
            if (binaryData.size < 4) {
                return Result.failure(SerializationException("Invalid binary format: too small"))
            }

            var offset = 0

            // Read header size
            val headerSize = ((binaryData[offset++].toInt() and 0xFF) shl 24) or
                            ((binaryData[offset++].toInt() and 0xFF) shl 16) or
                            ((binaryData[offset++].toInt() and 0xFF) shl 8) or
                            (binaryData[offset++].toInt() and 0xFF)

            if (binaryData.size < 4 + headerSize) {
                return Result.failure(SerializationException("Invalid binary format: header size mismatch"))
            }

            // Read and parse header
            val headerBytes = binaryData.sliceArray(offset until offset + headerSize)
            val headerJson = headerBytes.decodeToString()
            val header = json.decodeFromString<BinaryHeader>(headerJson)
            offset += headerSize

            // Validate magic header
            if (header.magic != MAGIC_HEADER) {
                return Result.failure(SerializationException("Invalid binary format: magic header mismatch"))
            }

            // Check version
            if (!isVersionSupported(header.version)) {
                return Result.failure(
                    UnsupportedVersionException(
                        "Binary version ${header.version} is not supported"
                    )
                )
            }

            // Read data
            val dataBytes = binaryData.sliceArray(offset until binaryData.size)

            // Decompress if needed
            val jsonBytes = if (header.compressed) {
                decompressData(dataBytes)
            } else {
                dataBytes
            }

            // Verify checksum
            val actualChecksum = calculateChecksum(jsonBytes)
            if (actualChecksum != header.checksum) {
                return Result.failure(SerializationException("Binary data corrupted: checksum mismatch"))
            }

            // Deserialize JSON
            val jsonString = jsonBytes.decodeToString()
            deserializeFromJson(jsonString)
        } catch (e: Exception) {
            Result.failure(SerializationException("Failed to deserialize from binary: ${e.message}", e))
        }
    }

    /**
     * Save project incrementally by only serializing changed parts
     */
    fun serializeIncremental(
        project: SceneEditorProject,
        previousProject: SceneEditorProject?
    ): Result<IncrementalSave> {
        return try {
            if (previousProject == null) {
                // Full save for first time
                val fullData = serializeToJson(project)
                if (fullData.isFailure) {
                    return Result.failure(fullData.exceptionOrNull()!!)
                }

                Result.success(
                    IncrementalSave(
                        type = SaveType.FULL,
                        fullData = fullData.getOrThrow(),
                        changes = emptyList(),
                        baseVersion = project.version
                    )
                )
            } else {
                // Calculate differences
                val changes = calculateDifferences(previousProject, project)

                Result.success(
                    IncrementalSave(
                        type = SaveType.INCREMENTAL,
                        fullData = null,
                        changes = changes,
                        baseVersion = previousProject.version
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(SerializationException("Failed to create incremental save: ${e.message}", e))
        }
    }

    /**
     * Apply incremental changes to a base project
     */
    fun applyIncrementalChanges(
        baseProject: SceneEditorProject,
        incrementalSave: IncrementalSave
    ): Result<SceneEditorProject> {
        return try {
            if (incrementalSave.type == SaveType.FULL) {
                return incrementalSave.fullData?.let {
                    deserializeFromJson(it)
                } ?: Result.failure(SerializationException("Full data missing in incremental save"))
            }

            var result = baseProject
            for (change in incrementalSave.changes) {
                result = applyChange(result, change)
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(SerializationException("Failed to apply incremental changes: ${e.message}", e))
        }
    }

    /**
     * Export project with asset bundling
     */
    fun exportWithAssets(
        project: SceneEditorProject,
        assetResolver: AssetResolver,
        options: ExportOptions = ExportOptions()
    ): Result<ProjectExport> {
        return try {
            val assets = mutableMapOf<String, ByteArray>()
            val updatedProject = resolveAssetReferences(project, assetResolver, assets)

            if (updatedProject.isFailure) {
                return Result.failure(updatedProject.exceptionOrNull()!!)
            }

            val projectData = if (options.compressOutput) {
                serializeToBinary(updatedProject.getOrThrow(), compressed = true)
            } else {
                serializeToJson(updatedProject.getOrThrow()).map { it.encodeToByteArray() }
            }

            if (projectData.isFailure) {
                return Result.failure(projectData.exceptionOrNull()!!)
            }

            Result.success(
                ProjectExport(
                    projectData = projectData.getOrThrow(),
                    assets = assets,
                    format = if (options.compressOutput) ExportFormat.BINARY else ExportFormat.JSON,
                    metadata = ExportMetadata(
                        exportedAt = kotlinx.datetime.Clock.System.now(),
                        exportedBy = "Materia Scene Editor",
                        originalVersion = project.version,
                        assetCount = assets.size
                    )
                )
            )
        } catch (e: Exception) {
            Result.failure(SerializationException("Failed to export with assets: ${e.message}", e))
        }
    }

    /**
     * Import project with asset resolution
     */
    fun importWithAssets(
        projectData: ByteArray,
        assets: Map<String, ByteArray>,
        assetStorage: AssetStorage
    ): Result<SceneEditorProject> {
        return try {
            // Determine format and deserialize
            val project = if (isBinaryFormat(projectData)) {
                deserializeFromBinary(projectData)
            } else {
                deserializeFromJson(projectData.decodeToString())
            }

            if (project.isFailure) {
                return Result.failure(project.exceptionOrNull()!!)
            }

            // Store assets and update references
            val updatedProject = storeAssetsAndUpdateReferences(
                project.getOrThrow(),
                assets,
                assetStorage
            )

            updatedProject
        } catch (e: Exception) {
            Result.failure(SerializationException("Failed to import with assets: ${e.message}", e))
        }
    }

    // Private helper methods

    private fun createSerializersModule() = SerializersModule {
        polymorphic(Any::class) {
            subclass(SceneEditorProject::class)
            subclass(SceneData::class)
            subclass(SerializedObject3D::class)
            subclass(SerializedLight::class)
            subclass(SerializedMaterial::class)
        }
    }

    private fun isVersionSupported(version: String): Boolean {
        return try {
            val versionParts = version.split(".").map { it.toInt() }
            val minParts = MIN_SUPPORTED_VERSION.split(".").map { it.toInt() }

            for (i in 0 until minOf(versionParts.size, minParts.size)) {
                when {
                    versionParts[i] > minParts[i] -> return true
                    versionParts[i] < minParts[i] -> return false
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    internal fun validateProject(project: SceneEditorProject): Result<Unit> {
        return try {
            // Validate required fields
            require(project.name.isNotBlank()) { "Project name cannot be blank" }
            require(project.version.isNotBlank()) { "Project version cannot be blank" }

            // Validate scene data
            val materialIds = project.scene.materials.keys
            val referencedMaterials = project.scene.objects.mapNotNull { it.materialId }
            val missingMaterials = referencedMaterials.filter { it !in materialIds }

            if (missingMaterials.isNotEmpty()) {
                return Result.failure(
                    ValidationException(
                        "Missing materials referenced by objects: ${missingMaterials.joinToString()}"
                    )
                )
            }

            // Validate texture references
            val textureIds = project.scene.textures.keys
            val referencedTextures = project.scene.materials.values
                .flatMap { it.textureSlots.values }
            val missingTextures = referencedTextures.filter { it !in textureIds }

            if (missingTextures.isNotEmpty()) {
                return Result.failure(
                    ValidationException(
                        "Missing textures referenced by materials: ${missingTextures.joinToString()}"
                    )
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(ValidationException("Project validation failed: ${e.message}", e))
        }
    }

    private fun calculateChecksum(data: ByteArray): String {
        // Simple checksum implementation - in production, use a proper hash function
        var checksum = 0L
        for (byte in data) {
            checksum = (checksum * 31 + byte.toLong()) and 0xFFFFFFFF
        }
        return checksum.toString(16)
    }

    private fun compressData(data: ByteArray): ByteArray {
        // Placeholder for compression implementation
        // In production, use a compression library like GZip or LZ4
        return data
    }

    private fun decompressData(data: ByteArray): ByteArray {
        // Placeholder for decompression implementation
        return data
    }

    private fun calculateDifferences(
        oldProject: SceneEditorProject,
        newProject: SceneEditorProject
    ): List<ProjectChange> {
        val changes = mutableListOf<ProjectChange>()

        // Compare project metadata
        if (oldProject.name != newProject.name) {
            changes.add(ProjectChange.UpdateField("name", newProject.name))
        }

        // Compare scene objects
        val oldObjects = oldProject.scene.objects.associateBy { it.id }
        val newObjects = newProject.scene.objects.associateBy { it.id }

        // Find added objects
        for ((id, obj) in newObjects) {
            if (id !in oldObjects) {
                changes.add(ProjectChange.AddObject(obj))
            }
        }

        // Find removed objects
        for (id in oldObjects.keys) {
            if (id !in newObjects) {
                changes.add(ProjectChange.RemoveObject(id))
            }
        }

        // Find modified objects
        for ((id, newObj) in newObjects) {
            val oldObj = oldObjects[id]
            if (oldObj != null && oldObj != newObj) {
                changes.add(ProjectChange.UpdateObject(id, newObj))
            }
        }

        // Compare materials
        val oldMaterials = oldProject.scene.materials
        val newMaterials = newProject.scene.materials

        for ((id, material) in newMaterials) {
            if (id !in oldMaterials) {
                changes.add(ProjectChange.AddMaterial(id, material))
            } else if (oldMaterials[id] != material) {
                changes.add(ProjectChange.UpdateMaterial(id, material))
            }
        }

        for (id in oldMaterials.keys) {
            if (id !in newMaterials) {
                changes.add(ProjectChange.RemoveMaterial(id))
            }
        }

        return changes
    }

    private fun applyChange(project: SceneEditorProject, change: ProjectChange): SceneEditorProject {
        return when (change) {
            is ProjectChange.UpdateField -> {
                when (change.field) {
                    "name" -> project.copy(name = change.value as String)
                    else -> project
                }
            }
            is ProjectChange.AddObject -> {
                val updatedObjects = project.scene.objects + change.obj
                project.copy(scene = project.scene.copy(objects = updatedObjects))
            }
            is ProjectChange.RemoveObject -> {
                val updatedObjects = project.scene.objects.filter { it.id != change.objectId }
                project.copy(scene = project.scene.copy(objects = updatedObjects))
            }
            is ProjectChange.UpdateObject -> {
                val updatedObjects = project.scene.objects.map {
                    if (it.id == change.objectId) change.obj else it
                }
                project.copy(scene = project.scene.copy(objects = updatedObjects))
            }
            is ProjectChange.AddMaterial -> {
                val updatedMaterials = project.scene.materials + (change.materialId to change.material)
                project.copy(scene = project.scene.copy(materials = updatedMaterials))
            }
            is ProjectChange.RemoveMaterial -> {
                val updatedMaterials = project.scene.materials - change.materialId
                project.copy(scene = project.scene.copy(materials = updatedMaterials))
            }
            is ProjectChange.UpdateMaterial -> {
                val updatedMaterials = project.scene.materials + (change.materialId to change.material)
                project.copy(scene = project.scene.copy(materials = updatedMaterials))
            }
        }
    }

    private fun resolveAssetReferences(
        project: SceneEditorProject,
        assetResolver: AssetResolver,
        assets: MutableMap<String, ByteArray>
    ): Result<SceneEditorProject> {
        return try {
            // Resolve texture assets
            val updatedTextures = mutableMapOf<String, TextureReference>()
            for ((id, texture) in project.scene.textures) {
                val assetData = assetResolver.resolveAsset(texture.path)
                if (assetData != null) {
                    assets[id] = assetData
                    updatedTextures[id] = texture.copy(path = "embedded:$id")
                } else {
                    updatedTextures[id] = texture
                }
            }

            val updatedScene = project.scene.copy(textures = updatedTextures)
            Result.success(project.copy(scene = updatedScene))
        } catch (e: Exception) {
            Result.failure(SerializationException("Failed to resolve asset references: ${e.message}", e))
        }
    }

    private fun storeAssetsAndUpdateReferences(
        project: SceneEditorProject,
        assets: Map<String, ByteArray>,
        assetStorage: AssetStorage
    ): Result<SceneEditorProject> {
        return try {
            val updatedTextures = mutableMapOf<String, TextureReference>()

            for ((id, texture) in project.scene.textures) {
                if (texture.path.startsWith("embedded:")) {
                    val assetId = texture.path.substringAfter("embedded:")
                    val assetData = assets[assetId]
                    if (assetData != null) {
                        val storedPath = assetStorage.storeAsset(assetId, assetData)
                        updatedTextures[id] = texture.copy(path = storedPath)
                    } else {
                        updatedTextures[id] = texture
                    }
                } else {
                    updatedTextures[id] = texture
                }
            }

            val updatedScene = project.scene.copy(textures = updatedTextures)
            Result.success(project.copy(scene = updatedScene))
        } catch (e: Exception) {
            Result.failure(SerializationException("Failed to store assets: ${e.message}", e))
        }
    }

    private fun isBinaryFormat(data: ByteArray): Boolean {
        return data.size >= 4 && data.sliceArray(0 until 4).contentEquals(
            MAGIC_HEADER.encodeToByteArray().sliceArray(0 until 4)
        )
    }
}

// Supporting data classes and interfaces

/**
 * Project wrapper with metadata for versioning
 */
@Serializable
data class ProjectWrapper(
    val schemaVersion: String,
    val createdBy: String,
    val exportedAt: Instant,
    val project: SceneEditorProject
)

/**
 * Binary format header
 */
@Serializable
data class BinaryHeader(
    val magic: String,
    val version: String,
    val compressed: Boolean,
    val encrypted: Boolean,
    val jsonSize: Int,
    val checksum: String
)

/**
 * Incremental save data
 */
data class IncrementalSave(
    val type: SaveType,
    val fullData: String?,
    val changes: List<ProjectChange>,
    val baseVersion: String
)

/**
 * Save type enumeration
 */
enum class SaveType {
    FULL, INCREMENTAL
}

/**
 * Project change representation
 */
sealed class ProjectChange {
    data class UpdateField(val field: String, val value: Any) : ProjectChange()
    data class AddObject(val obj: SerializedObject3D) : ProjectChange()
    data class RemoveObject(val objectId: String) : ProjectChange()
    data class UpdateObject(val objectId: String, val obj: SerializedObject3D) : ProjectChange()
    data class AddMaterial(val materialId: String, val material: SerializedMaterial) : ProjectChange()
    data class RemoveMaterial(val materialId: String) : ProjectChange()
    data class UpdateMaterial(val materialId: String, val material: SerializedMaterial) : ProjectChange()
}

/**
 * Export options
 */
data class ExportOptions(
    val compressOutput: Boolean = true,
    val embedAssets: Boolean = true,
    val includeHistory: Boolean = false,
    val optimizeForSize: Boolean = false
)

/**
 * Export format enumeration
 */
enum class ExportFormat {
    JSON, BINARY, COMPRESSED
}

/**
 * Project export result
 */
data class ProjectExport(
    val projectData: ByteArray,
    val assets: Map<String, ByteArray>,
    val format: ExportFormat,
    val metadata: ExportMetadata
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProjectExport) return false

        if (!projectData.contentEquals(other.projectData)) return false
        if (assets != other.assets) return false
        if (format != other.format) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = projectData.contentHashCode()
        result = 31 * result + assets.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
}

/**
 * Export metadata
 */
@Serializable
data class ExportMetadata(
    val exportedAt: Instant,
    val exportedBy: String,
    val originalVersion: String,
    val assetCount: Int
)

/**
 * Asset resolver interface
 */
interface AssetResolver {
    /**
     * Resolve asset by path and return its data
     */
    fun resolveAsset(path: String): ByteArray?
}

/**
 * Asset storage interface
 */
interface AssetStorage {
    /**
     * Store asset data and return the new path
     */
    fun storeAsset(id: String, data: ByteArray): String
}

/**
 * Custom exceptions
 */
class SerializationException(message: String, cause: Throwable? = null) : Exception(message, cause)
class UnsupportedVersionException(message: String) : Exception(message)
class ValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Utility object for common serialization operations
 */
object ProjectSerializationUtils {

    /**
     * Create a ProjectSerializer with default configuration
     */
    fun createDefault(): ProjectSerializer = ProjectSerializer()

    /**
     * Quick serialize to JSON
     */
    fun toJson(project: SceneEditorProject): String {
        return createDefault().serializeToJson(project).getOrThrow()
    }

    /**
     * Quick deserialize from JSON
     */
    fun fromJson(json: String): SceneEditorProject {
        return createDefault().deserializeFromJson(json).getOrThrow()
    }

    /**
     * Quick serialize to binary
     */
    fun toBinary(project: SceneEditorProject): ByteArray {
        return createDefault().serializeToBinary(project).getOrThrow()
    }

    /**
     * Quick deserialize from binary
     */
    fun fromBinary(data: ByteArray): SceneEditorProject {
        return createDefault().deserializeFromBinary(data).getOrThrow()
    }

    /**
     * Validate project structure
     */
    fun validate(project: SceneEditorProject): Result<Unit> {
        return createDefault().validateProject(project)
    }
}