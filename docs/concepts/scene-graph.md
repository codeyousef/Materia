# Scene Graph

The scene graph is the hierarchical structure that organizes all 3D objects in a Materia scene.

## Overview

A scene graph is a tree structure where each node represents an object in 3D space. Parent-child relationships determine how transformations (position, rotation, scale) are inherited.

```
Scene (root)
├── Camera
├── DirectionalLight
├── Group "Environment"
│   ├── Mesh "Ground"
│   └── Mesh "Skybox"
└── Group "Characters"
    ├── Group "Player"
    │   ├── SkinnedMesh "Body"
    │   └── Mesh "Weapon"
    └── Group "Enemy"
        └── SkinnedMesh "Body"
```

## Core Classes

### Object3D

The base class for all scene graph nodes.

```kotlin
val object3d = Object3D()
object3d.position.set(1f, 2f, 3f)
object3d.rotation.y = PI / 2
object3d.scale.set(2f, 2f, 2f)
```

### Scene

The root of the scene graph. There's typically one per render.

```kotlin
val scene = Scene()
scene.background = Color(0x1a1a2e)
scene.add(camera)
scene.add(light)
scene.add(mesh)
```

### Group

Empty container for organizing objects.

```kotlin
val robot = Group()
robot.name = "robot"

val body = Mesh(bodyGeometry, material)
val head = Mesh(headGeometry, material)
head.position.y = 1.5f

robot.add(body)
robot.add(head)
scene.add(robot)

// Transform the whole robot
robot.rotation.y = PI / 4
```

## Hierarchy Operations

### Adding Children

```kotlin
parent.add(child)
parent.add(child1, child2, child3)  // Add multiple
```

### Removing Children

```kotlin
parent.remove(child)
child.removeFromParent()
parent.clear()  // Remove all children
```

### Reparenting

```kotlin
// Move to new parent, keeping world transform
newParent.attach(child)
```

## Transform Inheritance

Child transforms are relative to their parent:

```kotlin
val parent = Group()
parent.position.set(10f, 0f, 0f)

val child = Mesh(geometry, material)
child.position.set(5f, 0f, 0f)  // Local position

parent.add(child)
scene.add(parent)

// Child world position is (15, 0, 0)
val worldPos = child.getWorldPosition()  // Vector3(15, 0, 0)
```

### Local vs World Space

```kotlin
// Local (relative to parent)
object.position      // Local position
object.rotation      // Local rotation
object.scale         // Local scale
object.matrix        // Local transform matrix

// World (absolute)
object.getWorldPosition(target)    // World position
object.getWorldQuaternion(target)  // World rotation
object.getWorldScale(target)       // World scale
object.matrixWorld                 // World transform matrix

// Conversion
object.localToWorld(vector)  // Convert local point to world
object.worldToLocal(vector)  // Convert world point to local
```

## Traversal

### Traverse All Descendants

```kotlin
scene.traverse { object3d ->
    println(object3d.name)
}
```

### Traverse Visible Only

```kotlin
scene.traverseVisible { object3d ->
    // Skip invisible objects
}
```

### Traverse Ancestors

```kotlin
child.traverseAncestors { ancestor ->
    println(ancestor.name)
}
```

## Finding Objects

### By Name

```kotlin
val player = scene.getObjectByName("Player")
```

### By ID

```kotlin
val object3d = scene.getObjectById(42)
```

### By Property

```kotlin
val meshes = mutableListOf<Mesh>()
scene.traverse { obj ->
    if (obj is Mesh) {
        meshes.add(obj)
    }
}
```

## Matrix Updates

Transforms are stored as matrices for GPU efficiency. Materia handles updates automatically, but you can control this:

```kotlin
// Automatic (default)
object3d.matrixAutoUpdate = true  // Update matrix from position/rotation/scale

// Manual
object3d.matrixAutoUpdate = false
object3d.matrix.compose(position, quaternion, scale)
object3d.matrixWorldNeedsUpdate = true

// Force update
object3d.updateMatrix()           // Update local matrix
object3d.updateMatrixWorld(true)  // Update world matrix (and descendants)
```

## Visibility

```kotlin
// Hide object (and all descendants)
object3d.visible = false

// Conditional visibility
object3d.layers.set(1)  // Only render on layer 1
camera.layers.enable(1)  // Camera sees layer 1
```

## Layers

Layers control which objects cameras can see:

```kotlin
// Set object to layer 2
mesh.layers.set(2)

// Enable multiple layers
mesh.layers.enable(0)
mesh.layers.enable(1)

// Camera configuration
camera.layers.enableAll()       // See all layers
camera.layers.set(0)            // Only layer 0
camera.layers.enable(1)         // Also layer 1
camera.layers.disable(2)        // Not layer 2
camera.layers.toggle(3)         // Toggle layer 3
```

## Best Practices

### Use Groups for Organization

```kotlin
// Good: Organized hierarchy
val scene = Scene()
val environment = Group().apply { name = "environment" }
val characters = Group().apply { name = "characters" }
val ui = Group().apply { name = "ui" }

scene.add(environment, characters, ui)
```

### Minimize Hierarchy Depth

Deep hierarchies have more matrix multiplications:

```kotlin
// Avoid: Too deep
grandparent.add(parent)
parent.add(child)
child.add(grandchild)
grandchild.add(greatGrandchild)

// Better: Flatter when possible
parent.add(child1, child2, child3)
```

### Reuse Geometry and Materials

```kotlin
// Share geometry and material
val geometry = BoxGeometry(1f, 1f, 1f)
val material = MeshStandardMaterial().apply { color = Color.RED }

val meshes = (0 until 100).map { i ->
    Mesh(geometry, material).apply {
        position.x = i.toFloat()
    }
}
```

### Use Instancing for Many Similar Objects

```kotlin
// For 1000+ similar objects
val instancedMesh = InstancedMesh(geometry, material, count = 1000)

for (i in 0 until 1000) {
    val matrix = Matrix4()
    matrix.setPosition(positions[i])
    instancedMesh.setMatrixAt(i, matrix)
}

scene.add(instancedMesh)
```

## See Also

- [Core API Reference](../api-reference/core.md) - Object3D, Group, Scene
- [Transformations](transformations.md) - Position, rotation, scale
- [Performance](../advanced/performance.md) - Optimization tips
