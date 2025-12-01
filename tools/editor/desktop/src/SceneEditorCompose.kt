package io.materia.tools.editor.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import io.materia.tools.editor.SceneEditor
import io.materia.tools.editor.data.*
import kotlinx.coroutines.launch
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import java.awt.Canvas
import java.awt.Graphics
import javax.swing.JPanel

/**
 * SceneEditorCompose - Compose Multiplatform implementation of the scene editor for desktop
 *
 * This provides a professional desktop 3D scene editor using:
 * - Compose Multiplatform for UI
 * - LWJGL for OpenGL/Vulkan rendering
 * - Native desktop integration
 * - Keyboard and mouse shortcuts
 * - Docking panels and professional workflow
 */

/**
 * Main Scene Editor Application Window
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneEditorWindow(
    state: SceneEditorState,
    onCloseRequest: () -> Unit
) {
    var windowState by remember { mutableStateOf(WindowState()) }
    val coroutineScope = rememberCoroutineScope()

    MaterialTheme(
        colorScheme = if (state.theme == EditorTheme.DARK) darkColorScheme() else lightColorScheme()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Menu Bar
            MenuBar(
                state = state,
                onAction = { action ->
                    coroutineScope.launch {
                        state.handleAction(action)
                    }
                }
            )

            // Toolbar
            EditorToolbar(
                state = state,
                onToolChange = { tool -> state.setActiveTool(tool) },
                onAction = { action ->
                    coroutineScope.launch {
                        state.handleAction(action)
                    }
                }
            )

            // Main Content Area
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                // Left Panel - Scene Hierarchy
                SceneHierarchyPanel(
                    state = state,
                    modifier = Modifier.width(280.dp)
                )

                // Center - 3D Viewport
                ViewportPanel(
                    state = state,
                    modifier = Modifier.weight(1f)
                )

                // Right Panel - Properties
                PropertiesPanel(
                    state = state,
                    modifier = Modifier.width(280.dp)
                )
            }

            // Status Bar
            StatusBar(
                state = state,
                modifier = Modifier.height(32.dp)
            )
        }
    }
}

/**
 * Menu Bar with standard desktop menu structure
 */
@Composable
fun MenuBar(
    state: SceneEditorState,
    onAction: (EditorAction) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MenuDropdown(
                title = "File",
                items = listOf(
                    "New Project" to EditorAction.CreateProject,
                    "Open Project" to EditorAction.LoadProject,
                    "Save" to EditorAction.SaveProject,
                    "Save As..." to EditorAction.SaveProjectAs,
                    "Export..." to EditorAction.ExportProject,
                    "Exit" to EditorAction.Exit
                ),
                onItemClick = onAction
            )

            MenuDropdown(
                title = "Edit",
                items = listOf(
                    "Undo" to EditorAction.Undo,
                    "Redo" to EditorAction.Redo,
                    "Cut" to EditorAction.Cut,
                    "Copy" to EditorAction.Copy,
                    "Paste" to EditorAction.Paste,
                    "Delete" to EditorAction.Delete
                ),
                onItemClick = onAction
            )

            MenuDropdown(
                title = "View",
                items = listOf(
                    "Toggle Grid" to EditorAction.ToggleGrid,
                    "Toggle Axes" to EditorAction.ToggleAxes,
                    "Toggle Wireframe" to EditorAction.ToggleWireframe,
                    "Toggle Stats" to EditorAction.ToggleStats,
                    "Focus Selected" to EditorAction.FocusSelected
                ),
                onItemClick = onAction
            )

            MenuDropdown(
                title = "Add",
                items = listOf(
                    "Cube" to EditorAction.AddCube,
                    "Sphere" to EditorAction.AddSphere,
                    "Plane" to EditorAction.AddPlane,
                    "Cylinder" to EditorAction.AddCylinder,
                    "Light" to EditorAction.AddLight,
                    "Camera" to EditorAction.AddCamera
                ),
                onItemClick = onAction
            )
        }
    }
}

/**
 * Dropdown menu component
 */
@Composable
fun MenuDropdown(
    title: String,
    items: List<Pair<String, EditorAction>>,
    onItemClick: (EditorAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(
            onClick = { expanded = true }
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { (name, action) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onItemClick(action)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Editor Toolbar with tools and actions
 */
@Composable
fun EditorToolbar(
    state: SceneEditorState,
    onToolChange: (EditorTool) -> Unit,
    onAction: (EditorAction) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Tool Group - Selection and Transform
            ToolGroup {
                ToolButton(
                    icon = Icons.Default.NearMe,
                    tooltip = "Select (V)",
                    isActive = state.activeTool.value == EditorTool.SELECT,
                    onClick = { onToolChange(EditorTool.SELECT) }
                )

                ToolButton(
                    icon = Icons.Default.OpenWith,
                    tooltip = "Move (G)",
                    isActive = state.activeTool.value == EditorTool.TRANSLATE,
                    onClick = { onToolChange(EditorTool.TRANSLATE) }
                )

                ToolButton(
                    icon = Icons.Default.RotateRight,
                    tooltip = "Rotate (R)",
                    isActive = state.activeTool.value == EditorTool.ROTATE,
                    onClick = { onToolChange(EditorTool.ROTATE) }
                )

                ToolButton(
                    icon = Icons.Default.AspectRatio,
                    tooltip = "Scale (S)",
                    isActive = state.activeTool.value == EditorTool.SCALE,
                    onClick = { onToolChange(EditorTool.SCALE) }
                )
            }

            Divider(
                modifier = Modifier
                    .height(24.dp)
                    .width(1.dp)
            )

            // Transform Mode
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Mode:",
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedButton(
                    onClick = { /* Toggle transform mode */ },
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = state.transformMode.value.name,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Divider(
                modifier = Modifier
                    .height(24.dp)
                    .width(1.dp)
            )

            // History Actions
            ToolGroup {
                ToolButton(
                    icon = Icons.Default.Undo,
                    tooltip = "Undo (Ctrl+Z)",
                    enabled = state.canUndo.value,
                    onClick = { onAction(EditorAction.Undo) }
                )

                ToolButton(
                    icon = Icons.Default.Redo,
                    tooltip = "Redo (Ctrl+Y)",
                    enabled = state.canRedo.value,
                    onClick = { onAction(EditorAction.Redo) }
                )
            }

            Divider(
                modifier = Modifier
                    .height(24.dp)
                    .width(1.dp)
            )

            // Animation Controls
            ToolGroup {
                ToolButton(
                    icon = Icons.Default.PlayArrow,
                    tooltip = "Play Animation",
                    onClick = { onAction(EditorAction.PlayAnimation) }
                )

                ToolButton(
                    icon = Icons.Default.Stop,
                    tooltip = "Stop Animation",
                    onClick = { onAction(EditorAction.StopAnimation) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Settings and Options
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Snap to Grid
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = state.snapToGrid.value,
                        onCheckedChange = { state.setSnapToGrid(it) }
                    )
                    Text(
                        text = "Snap",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Grid Size
                OutlinedTextField(
                    value = state.gridSize.value.toString(),
                    onValueChange = { /* Update grid size */ },
                    label = { Text("Grid") },
                    modifier = Modifier.width(80.dp),
                    singleLine = true
                )
            }
        }
    }
}

/**
 * Tool group container
 */
@Composable
fun ToolGroup(
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(4.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            content = content
        )
    }
}

/**
 * Individual tool button
 */
@Composable
fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tooltip: String,
    isActive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(32.dp)
            .background(
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(4.dp)
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = tooltip,
            tint = if (isActive) {
                MaterialTheme.colorScheme.onPrimary
            } else if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            }
        )
    }
}

/**
 * Scene Hierarchy Panel
 */
@Composable
fun SceneHierarchyPanel(
    state: SceneEditorState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Column {
            // Panel Header
            PanelHeader(
                title = "Scene",
                actions = {
                    IconButton(
                        onClick = { /* Add object menu */ }
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add Object"
                        )
                    }
                }
            )

            // Scene Tree
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                state.currentProject.value?.scene?.objects?.let { objects ->
                    items(objects) { obj ->
                        SceneObjectItem(
                            obj = obj,
                            isSelected = state.selectedObjects.value.contains(obj.id),
                            onSelected = { state.selectObject(obj.id) },
                            onVisibilityToggle = { state.setObjectVisibility(obj.id, !obj.visible) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Scene object item in hierarchy
 */
@Composable
fun SceneObjectItem(
    obj: SerializedObject3D,
    isSelected: Boolean,
    onSelected: () -> Unit,
    onVisibilityToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                }
            )
            .clickable { onSelected() },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Object Icon
            Icon(
                imageVector = when (obj.type) {
                    ObjectType.MESH -> Icons.Default.Cube
                    ObjectType.LIGHT -> Icons.Default.Lightbulb
                    ObjectType.CAMERA -> Icons.Default.Videocam
                    ObjectType.GROUP -> Icons.Default.Folder
                    ObjectType.HELPER -> Icons.Default.Help
                },
                contentDescription = obj.type.name,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Object Name
            Text(
                text = obj.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )

            // Visibility Toggle
            IconButton(
                onClick = onVisibilityToggle,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (obj.visible) {
                        Icons.Default.Visibility
                    } else {
                        Icons.Default.VisibilityOff
                    },
                    contentDescription = "Toggle Visibility",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 3D Viewport Panel with LWJGL integration
 */
@Composable
fun ViewportPanel(
    state: SceneEditorState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // LWJGL OpenGL Canvas
            SwingPanel(
                background = MaterialTheme.colorScheme.background.toArgb(),
                modifier = Modifier.fillMaxSize(),
                factory = {
                    LWJGLCanvas(state)
                }
            )

            // Viewport Overlay
            ViewportOverlay(
                state = state,
                modifier = Modifier.fillMaxSize()
            )

            // Loading Indicator
            if (state.isLoading.value) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp)
                            )
                            Text("Loading...")
                        }
                    }
                }
            }
        }
    }
}

/**
 * LWJGL OpenGL Canvas for 3D rendering
 */
class LWJGLCanvas(
    private val state: SceneEditorState
) : Canvas() {
    private var initialized = false

    override fun paint(g: Graphics) {
        super.paint(g)

        if (!initialized) {
            initializeOpenGL()
            initialized = true
        }

        render()
    }

    private fun initializeOpenGL() {
        try {
            // Initialize LWJGL OpenGL context
            GL.createCapabilities()

            // Set up basic OpenGL state
            glEnable(GL_DEPTH_TEST)
            glEnable(GL_CULL_FACE)
            glClearColor(0.2f, 0.2f, 0.3f, 1.0f)

            state.onRendererInitialized()
        } catch (e: Exception) {
            state.setError("Failed to initialize OpenGL: ${e.message}")
        }
    }

    private fun render() {
        // Clear the framebuffer
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        // Render the scene
        // This would integrate with the actual Materia renderer
        renderScene()

        // Update statistics
        state.updateRenderStats()
    }

    private fun renderScene() {
        // Scene rendering delegated to Materia engine viewport
    }
}

/**
 * Viewport overlay with gizmos and helpers
 */
@Composable
fun ViewportOverlay(
    state: SceneEditorState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Stats Overlay
        if (state.statsVisible.value) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "FPS: ${state.renderStats.value.fps}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Triangles: ${state.renderStats.value.triangles}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Draw Calls: ${state.renderStats.value.drawCalls}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Camera Info
        Card(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = "Camera: ${state.cameraType.value.name}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

/**
 * Properties Panel
 */
@Composable
fun PropertiesPanel(
    state: SceneEditorState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Column {
            PanelHeader(title = "Properties")

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    // Transform Properties
                    PropertyGroup(title = "Transform") {
                        Vector3Property(
                            label = "Position",
                            value = state.selectedObjectTransform.value.position,
                            onValueChange = { /* Update position */ }
                        )

                        Vector3Property(
                            label = "Rotation",
                            value = state.selectedObjectTransform.value.rotation,
                            onValueChange = { /* Update rotation */ }
                        )

                        Vector3Property(
                            label = "Scale",
                            value = state.selectedObjectTransform.value.scale,
                            onValueChange = { /* Update scale */ }
                        )
                    }
                }

                item {
                    // Material Properties
                    PropertyGroup(title = "Material") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Material:",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(80.dp)
                            )

                            OutlinedTextField(
                                value = state.selectedObjectMaterial.value ?: "None",
                                onValueChange = { },
                                readOnly = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Color:",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(80.dp)
                            )

                            // Color picker would go here
                            Surface(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color.White,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {}
                        }
                    }
                }

                item {
                    // Visibility Properties
                    PropertyGroup(title = "Visibility") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = state.selectedObjectVisible.value,
                                onCheckedChange = { /* Update visibility */ }
                            )
                            Text(
                                text = "Visible",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = state.selectedObjectCastShadows.value,
                                onCheckedChange = { /* Update shadows */ }
                            )
                            Text(
                                text = "Cast Shadows",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Panel header component
 */
@Composable
fun PanelHeader(
    title: String,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            actions()
        }
    }
}

/**
 * Property group container
 */
@Composable
fun PropertyGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                content = content
            )
        }
    }
}

/**
 * Vector3 property input
 */
@Composable
fun Vector3Property(
    label: String,
    value: Vector3,
    onValueChange: (Vector3) -> Unit
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                value = value.x.toString(),
                onValueChange = { newX ->
                    newX.toFloatOrNull()?.let { x ->
                        onValueChange(value.copy(x = x))
                    }
                },
                label = { Text("X") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            OutlinedTextField(
                value = value.y.toString(),
                onValueChange = { newY ->
                    newY.toFloatOrNull()?.let { y ->
                        onValueChange(value.copy(y = y))
                    }
                },
                label = { Text("Y") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            OutlinedTextField(
                value = value.z.toString(),
                onValueChange = { newZ ->
                    newZ.toFloatOrNull()?.let { z ->
                        onValueChange(value.copy(z = z))
                    }
                },
                label = { Text("Z") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
    }
}

/**
 * Status Bar
 */
@Composable
fun StatusBar(
    state: SceneEditorState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Project Info
            Text(
                text = state.currentProject.value?.name ?: "No Project",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Save Status
            Icon(
                imageVector = if (state.hasUnsavedChanges.value) {
                    Icons.Default.Circle
                } else {
                    Icons.Default.CheckCircle
                },
                contentDescription = "Save Status",
                modifier = Modifier.size(12.dp),
                tint = if (state.hasUnsavedChanges.value) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Selection Info
            Text(
                text = "Selected: ${state.selectedObjects.value.size}",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Camera Info
            Text(
                text = "Camera: ${state.cameraType.value.name}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Scene Editor State Management
 */
class SceneEditorState : SceneEditor {
    // State properties
    private val _currentProject = mutableStateOf<SceneEditorProject?>(null)
    override val currentProject: State<SceneEditorProject?> = _currentProject

    private val _isLoading = mutableStateOf(false)
    override val isLoading: State<Boolean> = _isLoading

    private val _errorState = mutableStateOf<String?>(null)
    override val errorState: State<String?> = _errorState

    private val _selectedObjects = mutableStateOf<List<String>>(emptyList())
    override val selectedObjects: State<List<String>> = _selectedObjects

    private val _activeTool = mutableStateOf(EditorTool.SELECT)
    override val activeTool: State<EditorTool> = _activeTool

    private val _transformMode = mutableStateOf(TransformMode.GLOBAL)
    override val transformMode: State<TransformMode> = _transformMode

    private val _snapToGrid = mutableStateOf(false)
    override val snapToGrid: State<Boolean> = _snapToGrid

    private val _canUndo = mutableStateOf(false)
    override val canUndo: State<Boolean> = _canUndo

    private val _canRedo = mutableStateOf(false)
    override val canRedo: State<Boolean> = _canRedo

    private val _cameraTransform = mutableStateOf(
        CameraTransform(
            position = Vector3(5f, 5f, 5f),
            target = Vector3.ZERO
        )
    )
    override val cameraTransform: State<CameraTransform> = _cameraTransform

    private val _renderStats = mutableStateOf(
        RenderStatistics(
            fps = 60f,
            frameTime = 16.67f,
            triangles = 0,
            drawCalls = 0,
            memoryUsage = 0L,
            gpuMemoryUsage = 0L
        )
    )
    override val renderStats: State<RenderStatistics> = _renderStats

    // Additional state for UI
    val theme = mutableStateOf(EditorTheme.DARK)
    val hasUnsavedChanges = mutableStateOf(false)
    val statsVisible = mutableStateOf(true)
    val cameraType = mutableStateOf(CameraType.PERSPECTIVE)
    val gridSize = mutableStateOf(1.0f)

    // Selected object properties
    val selectedObjectTransform = mutableStateOf(Transform3D(Vector3.ZERO, Vector3.ZERO, Vector3.ONE))
    val selectedObjectMaterial = mutableStateOf<String?>(null)
    val selectedObjectVisible = mutableStateOf(true)
    val selectedObjectCastShadows = mutableStateOf(true)

    // SceneEditor interface implementation
    override suspend fun initialize(width: Int, height: Int): Result<Unit> {
        return try {
            _isLoading.value = true
            // Initialize renderer
            _isLoading.value = false
            Result.success(Unit)
        } catch (e: Exception) {
            _isLoading.value = false
            _errorState.value = e.message
            Result.failure(e)
        }
    }

    override suspend fun loadProject(projectId: String): Result<SceneEditorProject> {
        return try {
            _isLoading.value = true
            // Load project from API
            // _currentProject.value = loadedProject
            _isLoading.value = false
            Result.success(_currentProject.value!!)
        } catch (e: Exception) {
            _isLoading.value = false
            _errorState.value = e.message
            Result.failure(e)
        }
    }

    override suspend fun createProject(name: String, template: ProjectTemplate): Result<SceneEditorProject> {
        return try {
            val project = SceneEditorProject.createEmpty(name)
            _currentProject.value = project
            hasUnsavedChanges.value = true
            Result.success(project)
        } catch (e: Exception) {
            _errorState.value = e.message
            Result.failure(e)
        }
    }

    override suspend fun saveProject(): Result<Unit> {
        return try {
            // Save project via API
            hasUnsavedChanges.value = false
            Result.success(Unit)
        } catch (e: Exception) {
            _errorState.value = e.message
            Result.failure(e)
        }
    }

    override suspend fun saveProjectAs(newName: String): Result<SceneEditorProject> {
        TODO("Not yet implemented")
    }

    override suspend fun exportProject(format: ExportFormat, options: ExportOptions): Result<ByteArray> {
        TODO("Not yet implemented")
    }

    override suspend fun addObject(objectType: ObjectType, position: Vector3): Result<String> {
        TODO("Not yet implemented")
    }

    override suspend fun removeObject(objectId: String): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun duplicateObject(objectId: String): Result<String> {
        TODO("Not yet implemented")
    }

    override fun selectObjects(objectIds: List<String>) {
        _selectedObjects.value = objectIds
    }

    fun selectObject(objectId: String) {
        selectObjects(listOf(objectId))
    }

    override suspend fun moveObject(objectId: String, newPosition: Vector3): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun rotateObject(objectId: String, rotation: Vector3): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun scaleObject(objectId: String, scale: Vector3): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun setObjectVisibility(objectId: String, visible: Boolean): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun setObjectMaterial(objectId: String, materialId: String): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun setCameraPosition(position: Vector3, target: Vector3?): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun focusCamera(objectId: String?): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun setCameraProjection(type: CameraType, fov: Float?): Result<Unit> {
        cameraType.value = type
        return Result.success(Unit)
    }

    override suspend fun resizeViewport(width: Int, height: Int): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun toggleGrid(visible: Boolean): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun toggleAxesHelper(visible: Boolean): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun toggleWireframe(enabled: Boolean): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun toggleStats(visible: Boolean): Result<Unit> {
        statsVisible.value = visible
        return Result.success(Unit)
    }

    override suspend fun addLight(lightType: LightType, position: Vector3): Result<String> {
        TODO("Not yet implemented")
    }

    override suspend fun updateLight(lightId: String, properties: LightProperties): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun setEnvironment(settings: EnvironmentSettings): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun undo(): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun redo(): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun clearHistory(): Result<Unit> {
        TODO("Not yet implemented")
    }

    override fun setActiveTool(tool: EditorTool) {
        _activeTool.value = tool
    }

    override fun setTransformMode(mode: TransformMode) {
        _transformMode.value = mode
    }

    override fun toggleSnapToGrid(enabled: Boolean) {
        _snapToGrid.value = enabled
    }

    fun setSnapToGrid(enabled: Boolean) {
        _snapToGrid.value = enabled
    }

    override fun setGridSize(size: Float) {
        gridSize.value = size
    }

    override fun togglePerformanceMonitoring(enabled: Boolean) {
        TODO("Not yet implemented")
    }

    override suspend fun importModel(filePath: String, position: Vector3): Result<List<String>> {
        TODO("Not yet implemented")
    }

    override suspend fun importTexture(filePath: String): Result<String> {
        TODO("Not yet implemented")
    }

    override suspend fun importMaterial(materialData: String): Result<String> {
        TODO("Not yet implemented")
    }

    override suspend fun joinSession(sessionId: String): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun leaveSession(): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun broadcastAction(action: EditorAction): Result<Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun dispose() {
        // Cleanup resources
    }

    // Internal methods
    fun handleAction(action: EditorAction) {
        // Handle editor actions
    }

    fun onRendererInitialized() {
        // Renderer initialization callback
    }

    fun setError(message: String) {
        _errorState.value = message
    }

    fun updateRenderStats() {
        // Update rendering statistics
    }
}

/**
 * Editor actions
 */
sealed class EditorAction {
    object CreateProject : EditorAction()
    object LoadProject : EditorAction()
    object SaveProject : EditorAction()
    object SaveProjectAs : EditorAction()
    object ExportProject : EditorAction()
    object Exit : EditorAction()

    object Undo : EditorAction()
    object Redo : EditorAction()
    object Cut : EditorAction()
    object Copy : EditorAction()
    object Paste : EditorAction()
    object Delete : EditorAction()

    object ToggleGrid : EditorAction()
    object ToggleAxes : EditorAction()
    object ToggleWireframe : EditorAction()
    object ToggleStats : EditorAction()
    object FocusSelected : EditorAction()

    object AddCube : EditorAction()
    object AddSphere : EditorAction()
    object AddPlane : EditorAction()
    object AddCylinder : EditorAction()
    object AddLight : EditorAction()
    object AddCamera : EditorAction()

    object PlayAnimation : EditorAction()
    object StopAnimation : EditorAction()
}

/**
 * Main application entry point
 */
fun main() = application {
    val state = remember { SceneEditorState() }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Materia Scene Editor",
        state = WindowState(width = 1400.dp, height = 900.dp)
    ) {
        SceneEditorWindow(
            state = state,
            onCloseRequest = ::exitApplication
        )
    }
}