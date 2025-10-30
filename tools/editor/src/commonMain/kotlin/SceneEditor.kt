@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.materia.tools.editor

import io.materia.tools.editor.data.SceneEditorProject
import io.materia.tools.editor.data.EditorAction
import io.materia.tools.editor.data.Vector3
import io.materia.tools.editor.data.SerializedObject3D
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant

/**
 * SceneEditor - Main interface for the scene editor implementation
 *
 * This interface defines the core functionality of the Materia scene editor,
 * including project management, object manipulation, camera controls, and
 * real-time collaboration features.
 *
 * Platform-specific implementations:
 * - Web: HTML5 Canvas + WebGL/WebGPU
 * - Desktop: Compose Multiplatform + LWJGL/Vulkan
 * - Mobile: Native renderers (future)
 */
interface SceneEditor {

    // Project Management
    /**
     * Current project state. Emits updates when project changes.
     */
    val currentProject: StateFlow<SceneEditorProject?>

    /**
     * Loading state indicator
     */
    val isLoading: StateFlow<Boolean>

    /**
     * Error state with optional error message
     */
    val errorState: StateFlow<String?>

    /**
     * Initialize the scene editor with viewport configuration
     */
    suspend fun initialize(width: Int, height: Int): Result<Unit>

    /**
     * Load an existing project by ID
     */
    suspend fun loadProject(projectId: String): Result<SceneEditorProject>

    /**
     * Create a new empty project
     */
    suspend fun createProject(name: String, template: ProjectTemplate = ProjectTemplate.BASIC): Result<SceneEditorProject>

    /**
     * Save the current project
     */
    suspend fun saveProject(): Result<Unit>

    /**
     * Save the project with a new name (Save As)
     */
    suspend fun saveProjectAs(newName: String): Result<SceneEditorProject>

    /**
     * Export project to various formats
     */
    suspend fun exportProject(format: ExportFormat, options: ExportOptions = ExportOptions()): Result<ByteArray>

    // Scene Object Management
    /**
     * Add a new object to the scene
     */
    suspend fun addObject(objectType: ObjectType, position: Vector3 = Vector3.ZERO): Result<String>

    /**
     * Remove object from scene by ID
     */
    suspend fun removeObject(objectId: String): Result<Unit>

    /**
     * Duplicate an existing object
     */
    suspend fun duplicateObject(objectId: String): Result<String>

    /**
     * Select objects (supports multi-selection)
     */
    fun selectObjects(objectIds: List<String>)

    /**
     * Get currently selected object IDs
     */
    val selectedObjects: StateFlow<List<String>>

    /**
     * Move object to new position
     */
    suspend fun moveObject(objectId: String, newPosition: Vector3): Result<Unit>

    /**
     * Rotate object (Euler angles in degrees)
     */
    suspend fun rotateObject(objectId: String, rotation: Vector3): Result<Unit>

    /**
     * Scale object
     */
    suspend fun scaleObject(objectId: String, scale: Vector3): Result<Unit>

    /**
     * Set object visibility
     */
    suspend fun setObjectVisibility(objectId: String, visible: Boolean): Result<Unit>

    /**
     * Update object material
     */
    suspend fun setObjectMaterial(objectId: String, materialId: String): Result<Unit>

    // Camera and Viewport
    /**
     * Current camera position and orientation
     */
    val cameraTransform: StateFlow<CameraTransform>

    /**
     * Set camera position and target
     */
    suspend fun setCameraPosition(position: Vector3, target: Vector3? = null): Result<Unit>

    /**
     * Focus camera on object or selection
     */
    suspend fun focusCamera(objectId: String? = null): Result<Unit>

    /**
     * Set camera projection (perspective/orthographic)
     */
    suspend fun setCameraProjection(type: CameraType, fov: Float? = null): Result<Unit>

    /**
     * Resize viewport
     */
    suspend fun resizeViewport(width: Int, height: Int): Result<Unit>

    /**
     * Toggle viewport features
     */
    suspend fun toggleGrid(visible: Boolean): Result<Unit>
    suspend fun toggleAxesHelper(visible: Boolean): Result<Unit>
    suspend fun toggleWireframe(enabled: Boolean): Result<Unit>
    suspend fun toggleStats(visible: Boolean): Result<Unit>

    // Lighting and Environment
    /**
     * Add light to scene
     */
    suspend fun addLight(lightType: LightType, position: Vector3): Result<String>

    /**
     * Update light properties
     */
    suspend fun updateLight(lightId: String, properties: LightProperties): Result<Unit>

    /**
     * Set environment settings
     */
    suspend fun setEnvironment(settings: EnvironmentSettings): Result<Unit>

    // History and Undo/Redo
    /**
     * Undo last action
     */
    suspend fun undo(): Result<Unit>

    /**
     * Redo last undone action
     */
    suspend fun redo(): Result<Unit>

    /**
     * Check if undo is available
     */
    val canUndo: StateFlow<Boolean>

    /**
     * Check if redo is available
     */
    val canRedo: StateFlow<Boolean>

    /**
     * Clear history
     */
    suspend fun clearHistory(): Result<Unit>

    // Tools and Interaction
    /**
     * Current active tool
     */
    val activeTool: StateFlow<EditorTool>

    /**
     * Set active tool
     */
    fun setActiveTool(tool: EditorTool)

    /**
     * Current transform mode (local/global/view)
     */
    val transformMode: StateFlow<TransformMode>

    /**
     * Set transform mode
     */
    fun setTransformMode(mode: TransformMode)

    /**
     * Snap to grid setting
     */
    val snapToGrid: StateFlow<Boolean>

    /**
     * Toggle snap to grid
     */
    fun toggleSnapToGrid(enabled: Boolean)

    /**
     * Set grid size
     */
    fun setGridSize(size: Float)

    // Performance and Statistics
    /**
     * Rendering statistics
     */
    val renderStats: StateFlow<RenderStatistics>

    /**
     * Toggle performance monitoring
     */
    fun togglePerformanceMonitoring(enabled: Boolean)

    // Asset Management
    /**
     * Import 3D model from file
     */
    suspend fun importModel(filePath: String, position: Vector3 = Vector3.ZERO): Result<List<String>>

    /**
     * Import texture
     */
    suspend fun importTexture(filePath: String): Result<String>

    /**
     * Import material definition
     */
    suspend fun importMaterial(materialData: String): Result<String>

    // Collaboration (future enhancement)
    /**
     * Join collaborative session
     */
    suspend fun joinSession(sessionId: String): Result<Unit>

    /**
     * Leave collaborative session
     */
    suspend fun leaveSession(): Result<Unit>

    /**
     * Send collaborative action to other users
     */
    suspend fun broadcastAction(action: EditorAction): Result<Unit>

    // Cleanup
    /**
     * Dispose resources and cleanup
     */
    suspend fun dispose()
}

/**
 * SceneEditorAPI - HTTP API interface for scene editor backend operations
 *
 * This interface handles server-side operations like project persistence,
 * asset management, and collaboration features.
 */
interface SceneEditorAPI {
    /**
     * List all available projects for the current user
     */
    suspend fun listProjects(): Result<List<ProjectSummary>>

    /**
     * Create a new project
     */
    suspend fun createProject(request: CreateProjectRequest): Result<SceneEditorProject>

    /**
     * Get project by ID
     */
    suspend fun getProject(projectId: String): Result<SceneEditorProject>

    /**
     * Update existing project
     */
    suspend fun updateProject(projectId: String, project: SceneEditorProject): Result<Unit>

    /**
     * Delete project
     */
    suspend fun deleteProject(projectId: String): Result<Unit>

    /**
     * Add action to project history
     */
    suspend fun addHistoryAction(projectId: String, action: EditorAction): Result<Unit>

    /**
     * Export project to file format
     */
    suspend fun exportProject(projectId: String, format: ExportFormat): Result<ByteArray>

    /**
     * Upload asset file
     */
    suspend fun uploadAsset(projectId: String, assetData: ByteArray, filename: String): Result<String>

    /**
     * Get asset by ID
     */
    suspend fun getAsset(assetId: String): Result<ByteArray>

    /**
     * List project assets
     */
    suspend fun listAssets(projectId: String): Result<List<AssetSummary>>
}

// Supporting Data Classes

/**
 * Project template for creating new projects
 */
enum class ProjectTemplate {
    BASIC,       // Empty scene with default lighting
    LIGHTING,    // Various light setups
    MATERIALS,   // Material showcase
    ANIMATION,   // Animation examples
    PARTICLES,   // Particle effects
    PHYSICS      // Physics simulation
}

/**
 * Export format options
 */
enum class ExportFormat {
    GLTF,       // glTF 2.0 (recommended)
    OBJ,        // Wavefront OBJ
    FBX,        // Autodesk FBX
    JSON,       // Materia JSON format
    PLY,        // Stanford PLY
    STL         // STereoLithography
}

/**
 * Export configuration options
 */
data class ExportOptions(
    val includeTextures: Boolean = true,
    val includeMaterials: Boolean = true,
    val includeAnimations: Boolean = true,
    val optimizeGeometry: Boolean = false,
    val compressionLevel: Int = 0, // 0 = none, 9 = maximum
    val embedAssets: Boolean = false
)

/**
 * Object types that can be created in the scene
 */
enum class ObjectType {
    // Primitives
    BOX, SPHERE, PLANE, CYLINDER, CONE, TORUS,

    // Advanced geometry
    ICOSAHEDRON, DODECAHEDRON, OCTAHEDRON,

    // Helpers
    GROUP, EMPTY, HELPER,

    // Imported
    IMPORTED_MODEL
}

/**
 * Light types for scene lighting
 */
enum class LightType {
    DIRECTIONAL,    // Sun-like directional light
    POINT,          // Omnidirectional point light
    SPOT,           // Focused spotlight
    AREA,           // Area light (rectangular)
    AMBIENT,        // Ambient lighting
    HEMISPHERE      // Hemisphere light (sky + ground)
}

/**
 * Editor tools for object manipulation
 */
enum class EditorTool {
    SELECT,         // Selection tool
    TRANSLATE,      // Move objects
    ROTATE,         // Rotate objects
    SCALE,          // Scale objects
    EXTRUDE,        // Extrude faces (advanced)
    KNIFE,          // Cut geometry (advanced)
    BRUSH,          // Sculpting brush (advanced)
    CAMERA          // Camera navigation
}

/**
 * Transform coordinate system mode
 */
enum class TransformMode {
    GLOBAL,         // World coordinates
    LOCAL,          // Object local coordinates
    VIEW            // Camera view coordinates
}

/**
 * Camera projection types
 */
enum class CameraType {
    PERSPECTIVE,    // Perspective projection
    ORTHOGRAPHIC    // Orthographic projection
}

/**
 * Camera transform state
 */
data class CameraTransform(
    val position: Vector3,
    val target: Vector3,
    val up: Vector3 = Vector3.UP,
    val fov: Float = 75f,
    val near: Float = 0.1f,
    val far: Float = 1000f
)

/**
 * Light properties for configuration
 */
data class LightProperties(
    val color: io.materia.tools.editor.data.Color,
    val intensity: Float,
    val range: Float? = null,          // For point/spot lights
    val angle: Float? = null,          // For spot lights (degrees)
    val penumbra: Float? = null,       // For spot lights (0-1)
    val castShadows: Boolean = true,
    val shadowMapSize: Int = 1024
)

/**
 * Rendering performance statistics
 */
data class RenderStatistics(
    val fps: Float,
    val frameTime: Float,               // milliseconds
    val triangles: Int,
    val drawCalls: Int,
    val memoryUsage: Long,              // bytes
    val gpuMemoryUsage: Long,           // bytes
    val shaderCompileTime: Float = 0f   // milliseconds
)

/**
 * Project summary for project listing
 */
data class ProjectSummary(
    val id: String,
    val name: String,
    val description: String = "",
    val created: Instant,
    val modified: Instant,
    val thumbnail: ByteArray? = null,
    val size: Long = 0,                 // bytes
    val objectCount: Int = 0,
    val tags: List<String> = emptyList()
)

/**
 * Create project request
 */
data class CreateProjectRequest(
    val name: String,
    val description: String = "",
    val template: ProjectTemplate = ProjectTemplate.BASIC,
    val tags: List<String> = emptyList()
) {
    /**
     * Validate the request parameters
     */
    fun validate(): Result<Unit> {
        return when {
            name.isBlank() -> Result.failure(IllegalArgumentException("Project name cannot be blank"))
            name.length > 100 -> Result.failure(IllegalArgumentException("Project name too long (max 100 characters)"))
            description.length > 500 -> Result.failure(IllegalArgumentException("Description too long (max 500 characters)"))
            tags.size > 10 -> Result.failure(IllegalArgumentException("Too many tags (max 10)"))
            tags.any { it.length > 20 } -> Result.failure(IllegalArgumentException("Tag too long (max 20 characters)"))
            else -> Result.success(Unit)
        }
    }
}

/**
 * Asset summary for asset listing
 */
data class AssetSummary(
    val id: String,
    val filename: String,
    val type: AssetType,
    val size: Long,
    val uploaded: Instant,
    val thumbnail: ByteArray? = null
)

/**
 * Asset types supported by the editor
 */
enum class AssetType {
    MODEL,          // 3D models (.gltf, .obj, .fbx)
    TEXTURE,        // Images (.png, .jpg, .hdr)
    MATERIAL,       // Material definitions (.json)
    AUDIO,          // Audio files (.wav, .mp3)
    SCRIPT,         // Shader scripts (.wgsl, .glsl)
    ANIMATION       // Animation clips (.gltf, .json)
}

/**
 * Environment settings for scene appearance
 */
data class EnvironmentSettings(
    val skyboxType: io.materia.tools.editor.data.SkyboxType,
    val skyboxData: String? = null,     // File path or color hex
    val ambientIntensity: Float = 0.3f,
    val environmentMap: String? = null,  // HDR environment map
    val fog: FogSettings? = null,
    val toneMappingType: ToneMappingType = ToneMappingType.ACES,
    val exposure: Float = 1.0f
)

/**
 * Fog settings for atmospheric effects
 */
data class FogSettings(
    val type: io.materia.tools.editor.data.FogType,
    val color: io.materia.tools.editor.data.Color,
    val near: Float,
    val far: Float,
    val density: Float = 0.001f
)

/**
 * Tone mapping algorithms
 */
enum class ToneMappingType {
    NONE,           // No tone mapping
    LINEAR,         // Simple linear mapping
    REINHARD,       // Reinhard tone mapping
    CINEON,         // Cineon tone mapping
    ACES,           // Academy Color Encoding System
    UNCHARTED2      // Uncharted 2 tone mapping
}

/**
 * Default implementation helpers
 */
object SceneEditorDefaults {

    /**
     * Create default viewport settings
     */
    fun defaultViewportSettings() = io.materia.tools.editor.data.ViewportSettings.default()

    /**
     * Create default camera settings
     */
    fun defaultCameraSettings() = io.materia.tools.editor.data.CameraSettings.default()

    /**
     * Create default tool settings
     */
    fun defaultToolSettings() = io.materia.tools.editor.data.ToolSettings.default()

    /**
     * Create default environment
     */
    fun defaultEnvironment() = EnvironmentSettings(
        skyboxType = io.materia.tools.editor.data.SkyboxType.COLOR,
        skyboxData = "#87CEEB", // Sky blue
        ambientIntensity = 0.3f
    )

    /**
     * Create default render statistics
     */
    fun defaultRenderStats() = RenderStatistics(
        fps = 60f,
        frameTime = 16.67f,
        triangles = 0,
        drawCalls = 0,
        memoryUsage = 0L,
        gpuMemoryUsage = 0L
    )
}