# API Reference

Complete API reference for the KreeKt 3D graphics library.

## Modules

### [Core](core/README.md)

Core functionality including math primitives, scene graph, and utilities.

- **Math**: Vector3, Matrix4, Quaternion, Euler, Color
- **Scene**: Object3D, Scene, Group
- **Transform**: Position, rotation, scale management
- **Utilities**: Event system, layers, helpers

### [Renderer](renderer/README.md)

Rendering system and GPU abstraction.

- **WebGPU**: WebGPU rendering backend
- **Vulkan**: Vulkan rendering backend
- **Buffer**: Vertex and index buffer management
- **State**: Rendering state management
- **Shader**: Shader compilation and management

### [Material](material/README.md)

Material system and shader management.

- **Base Materials**: Material, MeshBasicMaterial
- **PBR Materials**: MeshStandardMaterial, MeshPhysicalMaterial
- **Shader Materials**: ShaderMaterial, RawShaderMaterial
- **Node Materials**: Node-based material system
- **Texture Atlas**: Texture atlas management

### [Geometry](geometry/README.md)

Geometry classes and primitive generation.

- **Primitives**: BoxGeometry, SphereGeometry, PlaneGeometry
- **Procedural**: LatheGeometry, ExtrudeGeometry
- **Text**: TextGeometry, FontLoader
- **Processing**: Geometry optimization, UV generation
- **Buffer**: BufferGeometry, BufferAttribute

### [Camera](camera/README.md)

Camera system for scene viewpoints.

- **PerspectiveCamera**: Perspective projection camera
- **OrthographicCamera**: Orthographic projection camera
- **ArrayCamera**: Multi-camera rendering
- **CubeCamera**: Cube map rendering
- **StereoCamera**: Stereo 3D rendering

### [Light](light/README.md)

Lighting system.

- **Basic Lights**: AmbientLight, DirectionalLight, PointLight, SpotLight
- **Area Lights**: RectAreaLight
- **Light Probes**: LightProbe, HemisphereLight
- **Shadows**: Shadow mapping, cascaded shadows
- **IBL**: Image-based lighting

### [Animation](animation/README.md)

Animation system and skeletal animation.

- **Animation Mixer**: Animation playback and blending
- **Animation Clips**: Keyframe animation data
- **Skeletal Animation**: Bone hierarchy and skinning
- **Morph Targets**: Blend shape animation
- **IK**: Inverse kinematics solvers
- **State Machines**: Animation state management

### [Physics](physics/README.md)

Physics engine integration.

- **Rigid Bodies**: Dynamic, kinematic, static bodies
- **Collision**: Collision detection and response
- **Shapes**: Box, sphere, capsule, mesh colliders
- **Constraints**: Joints and constraints
- **Character Controller**: Character movement

### [XR](xr/README.md)

VR/AR support.

- **XR Session**: Session management
- **Input**: Controller and hand tracking
- **Spatial Tracking**: 6DOF tracking
- **AR Features**: Plane detection, anchors
- **VR Features**: Room-scale, teleportation

### [Loader](loader/README.md)

Asset loading system.

- **GLTF**: GLTF/GLB loader
- **OBJ**: Wavefront OBJ loader
- **GLTF/GLB**: Comprehensive glTF 2.0 loader
- **OBJ**: Wavefront OBJ loader (positions, UVs, normals)
- **PLY**: ASCII PLY loader (positions, normals, UVs)
- **STL**: ASCII/Binary STL loader
- **Texture**: Image and texture loading
- **Font**: Font loading for text

## Quick Reference

### Core Classes

| Class        | Description                            |
|--------------|----------------------------------------|
| `Vector3`    | 3D vector for positions and directions |
| `Matrix4`    | 4x4 transformation matrix              |
| `Quaternion` | Rotation representation                |
| `Euler`      | Euler angle rotation                   |
| `Color`      | RGB color                              |
| `Object3D`   | Base class for scene objects           |
| `Scene`      | Root container for 3D objects          |
| `Group`      | Container for organizing objects       |

### Geometry Classes

| Class              | Description                          |
|--------------------|--------------------------------------|
| `BoxGeometry`      | Box/cube geometry                    |
| `SphereGeometry`   | Sphere geometry                      |
| `PlaneGeometry`    | Flat plane geometry                  |
| `CylinderGeometry` | Cylinder geometry                    |
| `BufferGeometry`   | Base geometry with buffer attributes |

### Material Classes

| Class                  | Description               |
|------------------------|---------------------------|
| `MeshBasicMaterial`    | Simple unlit material     |
| `MeshStandardMaterial` | PBR material              |
| `MeshPhysicalMaterial` | Advanced PBR material     |
| `ShaderMaterial`       | Custom shader material    |
| `PointsMaterial`       | Material for point clouds |
| `LineMaterial`         | Material for lines        |

### Camera Classes

| Class                | Description             |
|----------------------|-------------------------|
| `PerspectiveCamera`  | Perspective projection  |
| `OrthographicCamera` | Orthographic projection |
| `ArrayCamera`        | Multiple cameras        |
| `CubeCamera`         | Environment map camera  |
| `StereoCamera`       | Stereo 3D camera        |

### Light Classes

| Class              | Description                 |
|--------------------|-----------------------------|
| `AmbientLight`     | Global ambient illumination |
| `DirectionalLight` | Directional sun-like light  |
| `PointLight`       | Omnidirectional point light |
| `SpotLight`        | Cone-shaped spotlight       |
| `RectAreaLight`    | Rectangular area light      |
| `HemisphereLight`  | Sky/ground hemisphere light |

## Common Patterns

### Creating Objects

```kotlin
// Geometry + Material = Mesh
val geometry = BoxGeometry(1f, 1f, 1f)
val material = MeshStandardMaterial().apply {
    color = Color(0xff0000)
}
val mesh = Mesh(geometry, material)
```

### Transforming Objects

```kotlin
// Position
object3d.position.set(x, y, z)

// Rotation (Euler angles)
object3d.rotation.y = PI.toFloat() / 2f

// Rotation (Quaternion)
object3d.quaternion.setFromAxisAngle(Vector3.UP, angle)

// Scale
object3d.scale.set(2f, 2f, 2f)
```

### Scene Hierarchy

```kotlin
val scene = Scene()
val group = Group()
scene.add(group)

val child = Mesh(geometry, material)
group.add(child)

// Traverse hierarchy
scene.traverse { obj ->
    println(obj.name)
}
```

### Animation

```kotlin
val mixer = AnimationMixer(model)
val action = mixer.clipAction(clip)
action.play()

// Update in loop
fun animate(deltaTime: Float) {
    mixer.update(deltaTime)
}
```

## Type Hierarchy

```
Object3D (abstract)
├── Camera (abstract)
│   ├── PerspectiveCamera
│   ├── OrthographicCamera
│   ├── ArrayCamera
│   └── CubeCamera
├── Light (abstract)
│   ├── AmbientLight
│   ├── DirectionalLight
│   ├── PointLight
│   ├── SpotLight
│   └── RectAreaLight
├── Mesh
├── SkinnedMesh
├── InstancedMesh
├── Line
├── LineSegments
├── Points
├── Sprite
├── Group
└── Bone

Material (abstract)
├── MeshBasicMaterial
├── MeshLambertMaterial
├── MeshPhongMaterial
├── MeshStandardMaterial
├── MeshPhysicalMaterial
├── ShaderMaterial
├── RawShaderMaterial
├── PointsMaterial
├── LineMaterial
└── ShadowMaterial

BufferGeometry
├── BoxGeometry
├── SphereGeometry
├── PlaneGeometry
├── CylinderGeometry
├── ConeGeometry
├── TorusGeometry
├── ExtrudeGeometry
└── TextGeometry
```

## Naming Conventions

### Properties

- `camelCase` for properties and functions
- `PascalCase` for classes and types
- `SCREAMING_SNAKE_CASE` for constants

### Mutability

- Most operations are **mutable** (modify in-place) for performance
- Use `clone()` for immutable operations
- Operators (`+`, `-`, `*`, `/`) create new instances

```kotlin
// Mutable (modifies original)
vector.add(otherVector)

// Immutable (creates new instance)
val result = vector + otherVector
```

## See Also

- [Getting Started Guide](../guides/getting-started.md)
- [Architecture Overview](../architecture/overview.md)
- [Examples](../examples/basic-usage.md)
