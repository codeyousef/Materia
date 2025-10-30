# Scene Graph API Reference

The scene graph is the hierarchical structure that organizes all 3D objects in Materia. It provides
transformation,
parenting, and rendering management.

## Table of Contents

- [Object3D](#object3d) - Base class for all scene objects
- [Scene](#scene) - Root container
- [Group](#group) - Container for organizing objects
- [Mesh](#mesh) - Renderable 3D object
- [Hierarchy Management](#hierarchy-management)
- [Transformations](#transformations)
- [Traversal](#traversal)

---

## Object3D

Base class for all objects in the scene graph. Provides transformation, hierarchy, and rendering capabilities.

### Properties

#### Transform Properties

```kotlin
val position: Vector3        // Local position
val rotation: Euler          // Local rotation (Euler angles)
val quaternion: Quaternion   // Local rotation (quaternion, synced with rotation)
val scale: Vector3           // Local scale (default: 1, 1, 1)
val matrix: Matrix4          // Local transformation matrix
val matrixWorld: Matrix4     // World transformation matrix
```

#### Hierarchy Properties

```kotlin
val id: Int                  // Unique identifier
var name: String             // Human-readable name
var parent: Object3D?        // Parent object (null for root)
val children: List<Object3D> // Child objects (read-only)
```

#### Rendering Properties

```kotlin
var visible: Boolean         // Render this object? (default: true)
var castShadow: Boolean      // Cast shadows? (default: false)
var receiveShadow: Boolean   // Receive shadows? (default: false)
var matrixAutoUpdate: Boolean // Auto-update matrix? (default: true)
var matrixWorldNeedsUpdate: Boolean // World matrix dirty flag
```

#### Layer System

```kotlin
val layers: Layers           // Layer membership for selective rendering
```

#### Custom Data

```kotlin
val userData: MutableMap<String, Any> // Application-specific data
```

#### Events

```kotlin
var onBeforeRender: ((Object3D) -> Unit)?  // Called before rendering
var onAfterRender: ((Object3D) -> Unit)?   // Called after rendering
```

### Basic Usage

```kotlin
// Create object
val obj = Mesh(geometry, material)

// Set position
obj.position.set(10f, 0f, 5f)
obj.position.x = 20f

// Set rotation (Euler angles in radians)
obj.rotation.y = PI.toFloat() / 4f

// Set scale
obj.scale.set(2f, 2f, 2f)

// Name for debugging
obj.name = "MyObject"
```

### Hierarchy Management

```kotlin
val parent = Group()
val child1 = Mesh(geometry, material)
val child2 = Mesh(geometry, material)

// Add children
parent.add(child1)
parent.add(child1, child2)  // Multiple at once

// Remove children
parent.remove(child1)
parent.remove(child1, child2)

// Remove from parent
child1.removeFromParent()

// Clear all children
parent.clear()

// Attach child (maintains world transform)
parent.attach(child1)

// Find objects
val found = parent.getObjectByName("MyObject")
val byId = parent.getObjectById(123)
val byProp = parent.getObjectByProperty("customProp", "value")
```

### Transformations

```kotlin
// Get world position
val worldPos = obj.getWorldPosition()
val worldPos2 = Vector3()
obj.getWorldPosition(worldPos2)

// Get world rotation
val worldRot = obj.getWorldQuaternion()

// Get world scale
val worldScale = obj.getWorldScale()

// Get world direction (forward vector)
val forward = obj.getWorldDirection()

// Look at target
obj.lookAt(Vector3(0f, 0f, 0f))
obj.lookAt(x = 0f, y = 0f, z = 0f)

// Rotate on axis (local space)
obj.rotateOnAxis(Vector3.UNIT_Y, angle = PI.toFloat() / 4f)

// Rotate on world axis
obj.rotateOnWorldAxis(Vector3.UP, angle = PI.toFloat() / 4f)

// Rotate on X/Y/Z
obj.rotateX(PI.toFloat() / 4f)
obj.rotateY(PI.toFloat() / 4f)
obj.rotateZ(PI.toFloat() / 4f)

// Translate on axis
obj.translateOnAxis(Vector3.UNIT_X, distance = 5f)

// Translate on X/Y/Z
obj.translateX(5f)
obj.translateY(10f)
obj.translateZ(-2f)

// Convert between local and world space
val worldPoint = obj.localToWorld(Vector3(1f, 0f, 0f))
val localPoint = obj.worldToLocal(Vector3(10f, 5f, 0f))

// Apply matrix transform
obj.applyMatrix4(transformMatrix)
```

### Matrix Updates

```kotlin
// Update local matrix from position/rotation/scale
obj.updateMatrix()

// Update world matrix
obj.updateMatrixWorld(force = false)

// Update with parents and children
obj.updateWorldMatrix(
    updateParents = true,
    updateChildren = true
)

// Manual control for static objects
obj.matrixAutoUpdate = false
obj.position.set(10f, 0f, 0f)
obj.updateMatrix()  // Manual update when changed
```

### Traversal

```kotlin
// Traverse all descendants
scene.traverse { obj ->
    println("Found: ${obj.name}")
}

// Traverse only visible objects
scene.traverseVisible { obj ->
    // Process visible objects
}

// Traverse ancestors (up the hierarchy)
child.traverseAncestors { ancestor ->
    println("Ancestor: ${ancestor.name}")
}
```

### Cloning

```kotlin
// Clone object (with children)
val clone = obj.clone(recursive = true)

// Clone without children
val shallow = obj.clone(recursive = false)

// Copy properties from another object
obj.copy(source, recursive = true)
```

### Bounding Volumes

```kotlin
// Get bounding box (override in subclasses)
val bbox: Box3 = obj.getBoundingBox()
```

### Type Information

```kotlin
// Type string for identification
val type: String = obj.type  // "Object3D", "Mesh", "Group", etc.
```

---

## Scene

Root container for all 3D content. The scene is rendered from a camera's perspective.

### Properties

```kotlin
var background: Background?       // Background (color, texture, gradient)
var environment: CubeTexture?     // Environment map for IBL
var fog: Fog?                     // Atmospheric fog
var overrideMaterial: Material?   // Override all object materials
var autoUpdate: Boolean           // Auto-update scene state (default: true)
```

### Background Types

```kotlin
// Solid color
scene.background = Background.Color(Color(0x87CEEB))

// Skybox (cubemap texture)
scene.background = Background.Texture(cubeTexture)

// Gradient
scene.background = Background.Gradient(
    top = Color(0x0077FF),
    bottom = Color(0xFFFFFF)
)

// Transparent (null)
scene.background = null
```

### Fog Types

```kotlin
// Linear fog (near to far distance)
scene.fog = Fog.Linear(
    color = Color(0xCCCCCC),
    near = 10f,
    far = 100f
)

// Exponential fog (density-based)
scene.fog = Fog.Exponential(
    color = Color(0xCCCCCC),
    density = 0.01f
)

// No fog
scene.fog = null
```

### Basic Usage

```kotlin
// Create scene
val scene = Scene()

// Set background color
scene.background = Background.Color(Color(0x000000))

// Add fog
scene.fog = Fog.Linear(
    color = Color(0xCCCCCC),
    near = 1f,
    far = 100f
)

// Add objects
scene.add(mesh, light, camera)

// Environment lighting
scene.environment = loadCubeTexture("environment.hdr")
```

### Scene Builder DSL

```kotlin
val scene = scene {
    // Background
    background(Color(0x000000))

    // Fog
    fog(Color(0xFFFFFF), near = 1f, far = 100f)

    // Add objects
    add(mesh1, mesh2)

    // Create groups
    group("buildings") {
        position(0f, 0f, 0f)
        add(building1, building2)

        // Nested groups
        group("windows") {
            scale(0.5f)
            add(window1, window2)
        }
    }
}
```

### Scene Utilities

```kotlin
// Find all objects of a type
val meshes = SceneUtils.findObjectsOfType<Mesh>(scene)

// Count total objects
val count = SceneUtils.countObjects(scene)

// Get scene bounding box
val bbox = SceneUtils.getBoundingBox(scene)

// Get scene center
val center = SceneUtils.getCenter(scene)

// Deep clone
val sceneCopy = SceneUtils.deepClone(scene)
```

### Extension Functions

```kotlin
// Set background color (RGB)
scene.setBackgroundColor(r = 1f, g = 0.5f, b = 0f)

// Set background color (hex)
scene.setBackgroundColor(0x87CEEB)

// Add fog
scene.addFog(Color(0xCCCCCC), near = 1f, far = 100f)

// Add exponential fog
scene.addFogExp2(Color(0xCCCCCC), density = 0.01f)

// Clear fog
scene.clearFog()

// Check fog/background
if (scene.hasFog()) { /* ... */ }
if (scene.hasBackground()) { /* ... */ }
```

### JSON Export

```kotlin
val json: SceneJSON = scene.toJSON()
```

---

## Group

Container for organizing objects in the hierarchy. Groups have no visual representation.

### Basic Usage

```kotlin
// Create group
val group = Group().apply {
    name = "MyGroup"
    position.set(10f, 0f, 0f)
}

// Add objects
group.add(mesh1, mesh2, mesh3)

// Add to scene
scene.add(group)

// All children move with group
group.position.x += 5f  // Moves all children
```

### Hierarchical Organization

```kotlin
// City example
val city = Group().apply { name = "City" }

val buildings = Group().apply {
    name = "Buildings"
    position.set(0f, 0f, 0f)
}

val roads = Group().apply {
    name = "Roads"
    position.set(0f, -0.1f, 0f)
}

buildings.add(building1, building2, building3)
roads.add(road1, road2)

city.add(buildings, roads)
scene.add(city)

// Now you can manipulate groups independently
buildings.visible = false  // Hide all buildings
roads.position.y = 0f      // Adjust all roads
```

---

## Mesh

Renderable 3D object combining geometry and material.

### Constructor

```kotlin
val mesh = Mesh(
    geometry = BoxGeometry(1f, 1f, 1f),
    material = MeshStandardMaterial().apply {
        color = Color(0xFF0000)
    }
)
```

### Properties

```kotlin
var geometry: BufferGeometry  // Mesh geometry
var material: Material        // Mesh material (or array for multi-material)
```

### Basic Usage

```kotlin
// Create mesh
val geometry = SphereGeometry(radius = 1f)
val material = MeshPhongMaterial().apply {
    color = Color(0x00FF00)
    shininess = 100f
}
val mesh = Mesh(geometry, material)

// Position and add to scene
mesh.position.set(0f, 5f, 0f)
scene.add(mesh)

// Configure rendering
mesh.castShadow = true
mesh.receiveShadow = true
```

### Multi-Material Support

```kotlin
// Geometry with groups for different materials
val geometry = BufferGeometry().apply {
    // ... setup attributes ...
    addGroup(start = 0, count = 100, materialIndex = 0)
    addGroup(start = 100, count = 50, materialIndex = 1)
}

val materials = arrayOf(
    MeshBasicMaterial().apply { color = Color(0xFF0000) },
    MeshBasicMaterial().apply { color = Color(0x00FF00) }
)

val mesh = Mesh(geometry, materials)
```

### Raycasting

```kotlin
// Check if ray intersects mesh
val raycaster = Raycaster(
    origin = Vector3(0f, 0f, 10f),
    direction = Vector3(0f, 0f, -1f)
)

val intersects = raycaster.intersectObject(mesh)
if (intersects.isNotEmpty()) {
    val hit = intersects.first()
    println("Hit at: ${hit.point}")
    println("Distance: ${hit.distance}")
}
```

---

## Hierarchy Management

### Adding Children

```kotlin
val parent = Group()
val child1 = Mesh(geometry, material)
val child2 = Mesh(geometry, material)

// Single child
parent.add(child1)

// Multiple children
parent.add(child1, child2)

// Vararg syntax
val children = arrayOf(child1, child2, child3)
parent.add(*children)

// Check parent
println(child1.parent === parent)  // true
```

### Removing Children

```kotlin
// Remove specific child
parent.remove(child1)

// Remove multiple
parent.remove(child1, child2)

// Remove from parent
child1.removeFromParent()

// Clear all children
parent.clear()
```

### Attaching Objects

```kotlin
// Attach maintains world transform
val oldParent = Group().apply {
    position.set(10f, 0f, 0f)
    add(child)
}

val newParent = Group().apply {
    position.set(20f, 0f, 0f)
}

// Child keeps same world position after attachment
newParent.attach(child)
```

### Finding Objects

```kotlin
// By name
val found = scene.getObjectByName("MyMesh")

// By ID
val byId = scene.getObjectById(123)

// By custom property
scene.userData["customId"] = "abc123"
val byProp = scene.getObjectByProperty("customId", "abc123")
```

---

## Transformations

### Local vs World Space

```kotlin
val parent = Group()
parent.position.x = 100f

val child = Mesh(geometry, material)
child.position.x = 50f
parent.add(child)

// Local position (relative to parent)
println(child.position.x)  // 50

// World position (absolute)
val worldPos = child.getWorldPosition()
println(worldPos.x)  // 150
```

### Rotation

```kotlin
val obj = Mesh(geometry, material)

// Euler angles (radians)
obj.rotation.x = PI.toFloat() / 4f
obj.rotation.y = PI.toFloat() / 2f
obj.rotation.z = 0f

// Quaternion (stays in sync)
obj.quaternion.setFromAxisAngle(Vector3.UNIT_Y, PI.toFloat() / 4f)

// Rotate incrementally
obj.rotateX(0.01f)
obj.rotateY(0.01f)
obj.rotateZ(0.01f)

// Rotate on custom axis
obj.rotateOnAxis(Vector3(1f, 1f, 0f).normalize(), PI.toFloat() / 6f)
```

### Look At

```kotlin
// Make object face a point
val target = Vector3(10f, 0f, 0f)
obj.lookAt(target)

// Component form
obj.lookAt(x = 10f, y = 0f, z = 0f)

// Camera look at
camera.lookAt(scene.position)
```

### Translation

```kotlin
// Move along local axes
obj.translateX(5f)      // Move right
obj.translateY(10f)     // Move up
obj.translateZ(-2f)     // Move backward

// Move along custom axis
val axis = Vector3(1f, 1f, 0f).normalize()
obj.translateOnAxis(axis, distance = 5f)
```

### Space Conversion

```kotlin
// Local to world
val localPoint = Vector3(1f, 0f, 0f)
val worldPoint = obj.localToWorld(localPoint)

// World to local
val worldPoint2 = Vector3(10f, 5f, 0f)
val localPoint2 = obj.worldToLocal(worldPoint2)
```

---

## Traversal

### Depth-First Traversal

```kotlin
// Visit all descendants
scene.traverse { obj ->
    println("${obj.name} at ${obj.position}")

    // Can modify objects
    if (obj is Mesh) {
        obj.visible = true
    }
}
```

### Visible Objects Only

```kotlin
// Only visible objects
scene.traverseVisible { obj ->
    // Process only visible objects
    if (obj is Mesh) {
        // Render mesh
    }
}
```

### Ancestor Traversal

```kotlin
// Walk up the hierarchy
child.traverseAncestors { ancestor ->
    println("Parent: ${ancestor.name}")

    // Can check conditions
    if (ancestor is Scene) {
        println("Reached scene root")
    }
}
```

### Custom Traversal

```kotlin
// Manual recursive traversal
fun traverseCustom(obj: Object3D, depth: Int = 0) {
    val indent = "  ".repeat(depth)
    println("$indent${obj.name}")

    for (child in obj.children) {
        traverseCustom(child, depth + 1)
    }
}

traverseCustom(scene)
```

---

## Performance Optimization

### Static Objects

```kotlin
// Disable auto-update for static objects
staticObject.matrixAutoUpdate = false
staticObject.updateMatrix()  // Manual update only when changed
```

### Matrix Management

```kotlin
// Force matrix update
obj.matrixWorldNeedsUpdate = true

// Update specific parts of hierarchy
obj.updateWorldMatrix(
    updateParents = false,  // Don't update parents
    updateChildren = true   // Do update children
)
```

### Dirty Flagging

The scene graph uses dirty flagging to minimize matrix calculations:

- Matrices only recompute when transforms change
- Updates propagate efficiently through hierarchy
- Manual control available for optimization

---

## Layer System

```kotlin
// Set object layers
mesh.layers.set(0)           // Layer 0 only
mesh.layers.enable(1)        // Also on layer 1
mesh.layers.disable(0)       // Remove from layer 0

// Camera layers
camera.layers.enableAll()    // Render all layers
camera.layers.set(0)         // Only render layer 0
camera.layers.enable(1)      // Also render layer 1

// Test layer membership
if (mesh.layers.test(camera.layers)) {
    // Mesh is visible to camera
}
```

---

## Examples

### Solar System

```kotlin
val solarSystem = Group().apply { name = "SolarSystem" }

val sun = Mesh(SphereGeometry(2f), sunMaterial).apply {
    name = "Sun"
}
solarSystem.add(sun)

val earthOrbit = Group().apply {
    name = "EarthOrbit"
    position.x = 10f
}
solarSystem.add(earthOrbit)

val earth = Mesh(SphereGeometry(0.5f), earthMaterial).apply {
    name = "Earth"
}
earthOrbit.add(earth)

val moon = Mesh(SphereGeometry(0.2f), moonMaterial).apply {
    name = "Moon"
    position.x = 2f
}
earth.add(moon)

// Animation
fun animate(deltaTime: Float) {
    earthOrbit.rotateY(0.001f)  // Orbit
    earth.rotateY(0.01f)        // Earth rotation
    moon.rotateY(0.02f)         // Moon rotation
}
```

### LOD System

```kotlin
val lod = LOD()

// Add levels (distance, object)
lod.addLevel(distance = 0f, Mesh(highDetailGeometry, material))
lod.addLevel(distance = 50f, Mesh(mediumDetailGeometry, material))
lod.addLevel(distance = 100f, Mesh(lowDetailGeometry, material))

scene.add(lod)

// LOD updates automatically based on camera distance
```

---

## See Also

- [Object3D Source](../../../src/commonMain/kotlin/io/materia/core/scene/Object3D.kt)
- [Scene Source](../../../src/commonMain/kotlin/io/materia/core/scene/Scene.kt)
- [Transformations Guide](../../guides/transformations-guide.md)
- [Performance Guide](../../guides/performance-guide.md)
