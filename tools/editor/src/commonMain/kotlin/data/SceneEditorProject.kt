package io.materia.tools.editor.data

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * SceneEditorProject - Data model for persisted scene editing session state
 *
 * This data class represents the complete state of a scene editing session, including
 * the scene content, camera settings, viewport configuration, tool settings, and
 * edit history for undo/redo functionality.
 *
 * The project can be serialized to JSON or binary format for persistence.
 */
@Serializable
data class SceneEditorProject @OptIn(ExperimentalUuidApi::class) constructor(
    val id: String = Uuid.random().toString(),
    val name: String,
    val version: String,
    val created: Instant,
    val modified: Instant,
    val scene: SceneData,
    val camera: CameraSettings,
    val viewport: ViewportSettings,
    val tools: ToolSettings,
    val history: List<EditorAction>
) {
    init {
        require(name.isNotBlank()) { "Project name must be non-empty" }
        require(version.matches(Regex("""\d+\.\d+\.\d+"""))) { "Version must follow semantic versioning (x.y.z)" }
        require(history.size <= 100) { "History limited to last 100 actions" }
        // Validate that all referenced materials and textures exist
        val referencedMaterials = scene.objects.mapNotNull { it.materialId }.toSet()
        val availableMaterials = scene.materials.keys
        require(referencedMaterials.all { it in availableMaterials }) {
            "All referenced materials must exist in scene.materials"
        }
    }

    /**
     * Creates a new project with updated modification time
     */
    fun withModified(newModified: Instant = kotlinx.datetime.Clock.System.now()): SceneEditorProject {
        return copy(modified = newModified)
    }

    /**
     * Adds an action to the history, maintaining the 100-action limit
     */
    fun addToHistory(action: EditorAction): SceneEditorProject {
        val newHistory = (history + action).takeLast(100)
        return copy(history = newHistory, modified = kotlinx.datetime.Clock.System.now())
    }

    companion object {
        /**
         * Creates a new empty project with default settings
         */
        fun createEmpty(name: String): SceneEditorProject {
            val now = kotlinx.datetime.Clock.System.now()
            return SceneEditorProject(
                name = name,
                version = "1.0.0",
                created = now,
                modified = now,
                scene = SceneData.empty(),
                camera = CameraSettings.default(),
                viewport = ViewportSettings.default(),
                tools = ToolSettings.default(),
                history = emptyList()
            )
        }
    }
}

/**
 * SceneData - Container for all scene content including objects, lights, materials, and environment
 */
@Serializable
data class SceneData(
    val objects: List<SerializedObject3D>,
    val lights: List<SerializedLight>,
    val materials: Map<String, SerializedMaterial>,
    val textures: Map<String, TextureReference>,
    val environment: EnvironmentSettings
) {
    companion object {
        fun empty(): SceneData = SceneData(
            objects = emptyList(),
            lights = listOf(SerializedLight.defaultDirectional()),
            materials = emptyMap(),
            textures = emptyMap(),
            environment = EnvironmentSettings.default()
        )
    }
}

/**
 * SerializedObject3D - Serializable representation of a 3D object in the scene
 */
@Serializable
data class SerializedObject3D(
    val id: String,
    val name: String,
    val type: ObjectType,
    val transform: Transform3D,
    val materialId: String? = null,
    val geometryData: GeometryData,
    val visible: Boolean = true,
    val children: List<SerializedObject3D> = emptyList(),
    val userData: Map<String, String> = emptyMap()
)

/**
 * SerializedLight - Serializable representation of a light source
 */
@Serializable
data class SerializedLight(
    val id: String,
    val name: String,
    val type: LightType,
    val transform: Transform3D,
    val color: Color,
    val intensity: Float,
    val range: Float? = null,
    val angle: Float? = null,
    val penumbra: Float? = null,
    val castShadows: Boolean = true
) {
    companion object {
        fun defaultDirectional(): SerializedLight = SerializedLight(
            id = "default-directional",
            name = "Directional Light",
            type = LightType.DIRECTIONAL,
            transform = Transform3D(
                position = Vector3(0f, 10f, 10f),
                rotation = Vector3(-45f, 0f, 0f),
                scale = Vector3.ONE
            ),
            color = Color.WHITE,
            intensity = 3.0f
        )
    }
}

/**
 * SerializedMaterial - Serializable representation of a material definition
 */
@Serializable
data class SerializedMaterial(
    val id: String,
    val name: String,
    val type: MaterialType,
    val properties: Map<String, MaterialProperty>,
    val textureSlots: Map<String, String> = emptyMap(), // slot name -> texture id
    val shaderCode: String? = null
)

/**
 * TextureReference - Reference to a texture asset with loading information
 */
@Serializable
data class TextureReference(
    val id: String,
    val path: String,
    val type: TextureType,
    val settings: TextureSettings
)

/**
 * EnvironmentSettings - Configuration for scene environment and lighting
 */
@Serializable
data class EnvironmentSettings(
    val skyboxType: SkyboxType,
    val skyboxData: String? = null, // File path or color hex
    val ambientIntensity: Float,
    val environmentMap: String? = null,
    val fog: FogSettings? = null
) {
    companion object {
        fun default(): EnvironmentSettings = EnvironmentSettings(
            skyboxType = SkyboxType.COLOR,
            skyboxData = "#87CEEB", // Sky blue
            ambientIntensity = 0.3f
        )
    }
}

/**
 * CameraSettings - Camera configuration for the scene editor
 */
@Serializable
data class CameraSettings(
    val type: CameraType,
    val fov: Float,
    val near: Float,
    val far: Float,
    val transform: Transform3D,
    val controls: CameraControls
) {
    companion object {
        fun default(): CameraSettings = CameraSettings(
            type = CameraType.PERSPECTIVE,
            fov = 75f,
            near = 0.1f,
            far = 1000f,
            transform = Transform3D(
                position = Vector3(5f, 5f, 5f),
                rotation = Vector3(-30f, 45f, 0f),
                scale = Vector3.ONE
            ),
            controls = CameraControls.default()
        )
    }
}

/**
 * ViewportSettings - Configuration for the scene editor viewport
 */
@Serializable
data class ViewportSettings(
    val width: Int,
    val height: Int,
    val gridVisible: Boolean,
    val axesHelperVisible: Boolean,
    val statsVisible: Boolean,
    val wireframeMode: Boolean,
    val backgroundColor: Color,
    val showGizmos: Boolean = true,
    val enablePostProcessing: Boolean = false
) {
    init {
        require(width > 0) { "Viewport width must be positive" }
        require(height > 0) { "Viewport height must be positive" }
    }

    companion object {
        fun default(): ViewportSettings = ViewportSettings(
            width = 1280,
            height = 720,
            gridVisible = true,
            axesHelperVisible = true,
            statsVisible = true,
            wireframeMode = false,
            backgroundColor = Color(0.2f, 0.2f, 0.3f, 1.0f)
        )
    }
}

/**
 * ToolSettings - Configuration for editor tools and interface
 */
@Serializable
data class ToolSettings(
    val selectedTool: EditorTool,
    val transformMode: TransformMode,
    val snapToGrid: Boolean,
    val gridSize: Float,
    val autoSave: Boolean,
    val autoSaveInterval: Int, // seconds
    val theme: EditorTheme,
    val shortcuts: Map<String, String> = emptyMap()
) {
    companion object {
        fun default(): ToolSettings = ToolSettings(
            selectedTool = EditorTool.SELECT,
            transformMode = TransformMode.LOCAL,
            snapToGrid = false,
            gridSize = 1.0f,
            autoSave = true,
            autoSaveInterval = 300, // 5 minutes
            theme = EditorTheme.DARK
        )
    }
}

/**
 * EditorAction - Represents an action that can be undone/redone
 */
@Serializable
data class EditorAction(
    val id: String,
    val type: ActionType,
    val timestamp: Instant,
    val description: String,
    val data: Map<String, String> = emptyMap()
)

// Supporting data classes and enums

@Serializable
data class Transform3D(
    val position: Vector3,
    val rotation: Vector3, // Euler angles in degrees
    val scale: Vector3
)

@Serializable
data class Vector3(
    val x: Float,
    val y: Float,
    val z: Float
) {
    companion object {
        val ZERO = Vector3(0f, 0f, 0f)
        val ONE = Vector3(1f, 1f, 1f)
        val UP = Vector3(0f, 1f, 0f)
        val RIGHT = Vector3(1f, 0f, 0f)
        val FORWARD = Vector3(0f, 0f, 1f)
    }
}

@Serializable
data class Color(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float = 1.0f
) {
    init {
        require(r in 0f..1f) { "Red component must be in range 0-1" }
        require(g in 0f..1f) { "Green component must be in range 0-1" }
        require(b in 0f..1f) { "Blue component must be in range 0-1" }
        require(a in 0f..1f) { "Alpha component must be in range 0-1" }
    }

    companion object {
        val WHITE = Color(1f, 1f, 1f, 1f)
        val BLACK = Color(0f, 0f, 0f, 1f)
        val RED = Color(1f, 0f, 0f, 1f)
        val GREEN = Color(0f, 1f, 0f, 1f)
        val BLUE = Color(0f, 0f, 1f, 1f)
        val YELLOW = Color(1f, 1f, 0f, 1f)
        val CYAN = Color(0f, 1f, 1f, 1f)
        val MAGENTA = Color(1f, 0f, 1f, 1f)
    }
}

@Serializable
data class GeometryData(
    val type: GeometryType,
    val parameters: Map<String, Float> = emptyMap(),
    val customData: String? = null // For imported geometry
)

@Serializable
data class MaterialProperty(
    val type: PropertyType,
    val value: String, // JSON-serialized value
    val range: FloatRange? = null
)

@Serializable
data class FloatRange(
    val min: Float,
    val max: Float,
    val step: Float = 0.01f
)

@Serializable
data class TextureSettings(
    val wrapS: TextureWrap,
    val wrapT: TextureWrap,
    val magFilter: TextureFilter,
    val minFilter: TextureFilter,
    val generateMipmaps: Boolean = true,
    val flipY: Boolean = true
)

@Serializable
data class FogSettings(
    val type: FogType,
    val color: Color,
    val near: Float,
    val far: Float,
    val density: Float = 0.001f
)

@Serializable
data class CameraControls(
    val type: ControlsType,
    val target: Vector3,
    val autoRotate: Boolean,
    val autoRotateSpeed: Float,
    val enableZoom: Boolean,
    val enablePan: Boolean,
    val enableRotate: Boolean,
    val zoomSpeed: Float,
    val panSpeed: Float,
    val rotateSpeed: Float
) {
    companion object {
        fun default(): CameraControls = CameraControls(
            type = ControlsType.ORBIT,
            target = Vector3.ZERO,
            autoRotate = false,
            autoRotateSpeed = 2.0f,
            enableZoom = true,
            enablePan = true,
            enableRotate = true,
            zoomSpeed = 1.0f,
            panSpeed = 1.0f,
            rotateSpeed = 1.0f
        )
    }
}

// Enums

@Serializable
enum class ObjectType {
    MESH, LIGHT, CAMERA, GROUP, HELPER
}

@Serializable
enum class LightType {
    DIRECTIONAL, POINT, SPOT, AREA, AMBIENT
}

@Serializable
enum class MaterialType {
    STANDARD, PHYSICAL, TOON, MATCAP, CUSTOM_SHADER, DEPTH, NORMAL
}

@Serializable
enum class TextureType {
    DIFFUSE, NORMAL, ROUGHNESS, METALLIC, EMISSIVE, OCCLUSION, ENVIRONMENT, CUBEMAP
}

@Serializable
enum class SkyboxType {
    COLOR, GRADIENT, HDRI, CUBEMAP
}

@Serializable
enum class CameraType {
    PERSPECTIVE, ORTHOGRAPHIC
}

@Serializable
enum class EditorTool {
    SELECT, TRANSLATE, ROTATE, SCALE, EXTRUDE, KNIFE, BRUSH
}

@Serializable
enum class TransformMode {
    GLOBAL, LOCAL, VIEW
}

@Serializable
enum class EditorTheme {
    LIGHT, DARK, HIGH_CONTRAST
}

@Serializable
enum class ActionType {
    CREATE, DELETE, MODIFY, TRANSFORM, MATERIAL_CHANGE, LIGHT_CHANGE, CAMERA_CHANGE
}

@Serializable
enum class GeometryType {
    BOX, SPHERE, PLANE, CYLINDER, CONE, TORUS, CUSTOM
}

@Serializable
enum class PropertyType {
    FLOAT, VEC2, VEC3, VEC4, COLOR, BOOLEAN, STRING, TEXTURE
}

@Serializable
enum class TextureWrap {
    REPEAT, CLAMP_TO_EDGE, MIRRORED_REPEAT
}

@Serializable
enum class TextureFilter {
    NEAREST, LINEAR, NEAREST_MIPMAP_NEAREST, LINEAR_MIPMAP_NEAREST, NEAREST_MIPMAP_LINEAR, LINEAR_MIPMAP_LINEAR
}

@Serializable
enum class FogType {
    LINEAR, EXPONENTIAL, EXPONENTIAL_SQUARED
}

@Serializable
enum class ControlsType {
    ORBIT, FLY, FIRST_PERSON, MAP
}