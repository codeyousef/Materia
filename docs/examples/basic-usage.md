# Basic Usage Examples

This document provides practical code examples for common Materia usage patterns.

## Table of Contents

- [Creating a Basic Scene](#creating-a-basic-scene)
- [Geometry Examples](#geometry-examples)
- [Material Examples](#material-examples)
- [Lighting Examples](#lighting-examples)
- [Animation Examples](#animation-examples)
- [Camera Controls](#camera-controls)
- [Loading Models](#loading-models)
- [Performance Optimization](#performance-optimization)

## Creating a Basic Scene

### Minimal Example

```kotlin
import io.materia.core.scene.*
import io.materia.core.math.*
import io.materia.camera.PerspectiveCamera
import io.materia.geometry.BoxGeometry
import io.materia.material.MeshBasicMaterial
import io.materia.mesh.Mesh

fun main() {
    // Create scene
    val scene = Scene()

    // Create camera
    val camera = PerspectiveCamera(
        fov = 75f,
        aspect = 800f / 600f,
        near = 0.1f,
        far = 1000f
    )
    camera.position.z = 5f

    // Create cube
    val geometry = BoxGeometry(1f, 1f, 1f)
    val material = MeshBasicMaterial().apply {
        color = Color(0x00ff00)
    }
    val cube = Mesh(geometry, material)
    scene.add(cube)

    // Create renderer
    val renderer = createRenderer()
    renderer.setSize(800, 600)

    // Render loop
    fun animate() {
        cube.rotation.x += 0.01f
        cube.rotation.y += 0.01f
        renderer.render(scene, camera)
        window.requestAnimationFrame(::animate)
    }
    animate()
}
```

### With Lighting

```kotlin
import io.materia.light.*

// Create scene with lighting
val scene = Scene().apply {
    background = Color(0x222222)
}

// Add ambient light
val ambientLight = AmbientLight(Color(0x404040), 0.5f)
scene.add(ambientLight)

// Add directional light with shadows
val directionalLight = DirectionalLight(Color(0xffffff), 1.0f).apply {
    position.set(5f, 10f, 7.5f)
    castShadow = true
    shadow.camera.near = 0.1f
    shadow.camera.far = 50f
    shadow.mapSize.set(1024, 1024)
}
scene.add(directionalLight)

// Create mesh with shadow support
val mesh = Mesh(geometry, material).apply {
    castShadow = true
    receiveShadow = true
}
scene.add(mesh)

// Enable shadows in renderer
renderer.shadowMap.enabled = true
renderer.shadowMap.type = ShadowMapType.PCFSoftShadowMap
```

## Geometry Examples

### Primitive Geometries

```kotlin
import io.materia.geometry.*

// Box/Cube
val box = BoxGeometry(
    width = 1f,
    height = 1f,
    depth = 1f,
    widthSegments = 1,
    heightSegments = 1,
    depthSegments = 1
)

// Sphere
val sphere = SphereGeometry(
    radius = 1f,
    widthSegments = 32,
    heightSegments = 16,
    phiStart = 0f,
    phiLength = PI.toFloat() * 2f,
    thetaStart = 0f,
    thetaLength = PI.toFloat()
)

// Plane
val plane = PlaneGeometry(
    width = 10f,
    height = 10f,
    widthSegments = 10,
    heightSegments = 10
)

// Cylinder
val cylinder = CylinderGeometry(
    radiusTop = 1f,
    radiusBottom = 1f,
    height = 2f,
    radialSegments = 32,
    heightSegments = 1
)

// Cone
val cone = ConeGeometry(
    radius = 1f,
    height = 2f,
    radialSegments = 32
)

// Torus
val torus = TorusGeometry(
    radius = 1f,
    tube = 0.4f,
    radialSegments = 16,
    tubularSegments = 100
)
```

### Custom BufferGeometry

```kotlin
import io.materia.geometry.BufferGeometry
import io.materia.geometry.BufferAttribute

// Create custom triangle
val geometry = BufferGeometry()

val vertices = floatArrayOf(
    -1f, -1f, 0f,  // vertex 0
     1f, -1f, 0f,  // vertex 1
     0f,  1f, 0f   // vertex 2
)

val normals = floatArrayOf(
    0f, 0f, 1f,
    0f, 0f, 1f,
    0f, 0f, 1f
)

val uvs = floatArrayOf(
    0f, 0f,
    1f, 0f,
    0.5f, 1f
)

geometry.setAttribute("position", BufferAttribute(vertices, 3))
geometry.setAttribute("normal", BufferAttribute(normals, 3))
geometry.setAttribute("uv", BufferAttribute(uvs, 2))

geometry.computeBoundingBox()
geometry.computeBoundingSphere()
```

### Procedural Geometry

```kotlin
import io.materia.geometry.ExtrudeGeometry
import io.materia.shape.Shape

// Create a star shape
val shape = Shape()
val outerRadius = 1f
val innerRadius = 0.5f
val points = 5

for (i in 0 until points * 2) {
    val angle = (i / (points * 2f)) * PI.toFloat() * 2f
    val radius = if (i % 2 == 0) outerRadius else innerRadius
    val x = cos(angle) * radius
    val y = sin(angle) * radius

    if (i == 0) {
        shape.moveTo(x, y)
    } else {
        shape.lineTo(x, y)
    }
}

// Extrude the shape
val extrudeSettings = ExtrudeGeometry.Options(
    depth = 0.5f,
    bevelEnabled = true,
    bevelThickness = 0.1f,
    bevelSize = 0.1f,
    bevelSegments = 3
)

val geometry = ExtrudeGeometry(shape, extrudeSettings)
```

## Material Examples

### Basic Materials

```kotlin
import io.materia.material.*

// Basic unlit material
val basicMaterial = MeshBasicMaterial().apply {
    color = Color(0xff0000)
    wireframe = false
}

// Lambert material (diffuse lighting)
val lambertMaterial = MeshLambertMaterial().apply {
    color = Color(0x00ff00)
    emissive = Color(0x000000)
    emissiveIntensity = 0f
}

// Phong material (specular highlights)
val phongMaterial = MeshPhongMaterial().apply {
    color = Color(0x0000ff)
    specular = Color(0x111111)
    shininess = 30f
}
```

### PBR Materials

```kotlin
// Standard PBR material
val standardMaterial = MeshStandardMaterial().apply {
    color = Color(0xffffff)
    metalness = 0.5f
    roughness = 0.5f
    emissive = Color(0x000000)
    emissiveIntensity = 0f
}

// Physical material (advanced PBR)
val physicalMaterial = MeshPhysicalMaterial().apply {
    color = Color(0xffffff)
    metalness = 0f
    roughness = 0.1f
    clearcoat = 1f
    clearcoatRoughness = 0.1f
    transmission = 1f  // Glass-like transparency
    thickness = 0.5f
    ior = 1.5f  // Index of refraction
}
```

### Textured Materials

```kotlin
import io.materia.texture.TextureLoader

val textureLoader = TextureLoader()

// Load textures
val colorMap = textureLoader.load("textures/color.jpg")
val normalMap = textureLoader.load("textures/normal.jpg")
val roughnessMap = textureLoader.load("textures/roughness.jpg")
val metalnessMap = textureLoader.load("textures/metalness.jpg")

// Apply to material
val material = MeshStandardMaterial().apply {
    map = colorMap
    normalMap = normalMap
    roughnessMap = roughnessMap
    metalnessMap = metalnessMap
    normalScale = Vector2(1f, 1f)
}
```

### Custom Shader Material

```kotlin
val shaderMaterial = ShaderMaterial().apply {
    vertexShader = """
        attribute vec3 position;
        attribute vec3 normal;
        attribute vec2 uv;

        uniform mat4 modelViewMatrix;
        uniform mat4 projectionMatrix;
        uniform mat3 normalMatrix;

        varying vec3 vNormal;
        varying vec2 vUv;

        void main() {
            vNormal = normalize(normalMatrix * normal);
            vUv = uv;
            gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }
    """.trimIndent()

    fragmentShader = """
        precision mediump float;

        varying vec3 vNormal;
        varying vec2 vUv;

        uniform vec3 color;
        uniform float time;

        void main() {
            vec3 light = normalize(vec3(1.0, 1.0, 1.0));
            float dProd = max(0.0, dot(vNormal, light));
            vec3 animatedColor = color * (0.5 + 0.5 * sin(time + vUv.x * 10.0));
            gl_FragColor = vec4(animatedColor * dProd, 1.0);
        }
    """.trimIndent()

    uniforms = mapOf(
        "color" to Uniform(Color(0xff0000)),
        "time" to Uniform(0f)
    )
}

// Update in animation loop
fun animate(time: Float) {
    shaderMaterial.uniforms["time"]?.value = time
}
```

## Lighting Examples

### Basic Lights

```kotlin
// Ambient light (global illumination)
val ambient = AmbientLight(Color(0x404040), intensity = 0.5f)

// Hemisphere light (sky/ground)
val hemisphere = HemisphereLight(
    skyColor = Color(0xffffbb),
    groundColor = Color(0x080820),
    intensity = 1f
)

// Directional light (sun-like)
val directional = DirectionalLight(Color(0xffffff), intensity = 1f).apply {
    position.set(10f, 10f, 10f)
    target.position.set(0f, 0f, 0f)
}

// Point light (omnidirectional)
val point = PointLight(Color(0xffffff), intensity = 1f, distance = 100f, decay = 2f).apply {
    position.set(0f, 5f, 0f)
}

// Spot light (cone-shaped)
val spot = SpotLight(
    color = Color(0xffffff),
    intensity = 1f,
    distance = 100f,
    angle = PI.toFloat() / 6f,
    penumbra = 0.1f,
    decay = 2f
).apply {
    position.set(0f, 10f, 0f)
    target.position.set(0f, 0f, 0f)
}
```

### Advanced Lighting

```kotlin
// Area light (rectangular)
val rectLight = RectAreaLight(
    color = Color(0xffffff),
    intensity = 1f,
    width = 10f,
    height = 10f
).apply {
    position.set(0f, 5f, 0f)
    lookAt(Vector3(0f, 0f, 0f))
}

// Light probe (image-based lighting)
val lightProbe = LightProbe()
lightProbe.fromCubeTexture(cubeMap)
scene.add(lightProbe)

// Environment map
scene.environment = cubeMap
scene.environmentIntensity = 1f
```

## Animation Examples

### Simple Animation

```kotlin
import io.materia.animation.*

// Animate properties manually
fun animate(deltaTime: Float) {
    mesh.position.y = sin(currentTime) * 2f
    mesh.rotation.y += 0.01f
    mesh.scale.x = 1f + sin(currentTime * 2f) * 0.2f
}
```

### Animation Clips

```kotlin
// Create animation clip
val positionTrack = KeyframeTrack(
    name = "mesh.position",
    times = floatArrayOf(0f, 1f, 2f),
    values = floatArrayOf(
        0f, 0f, 0f,    // frame 0
        0f, 5f, 0f,    // frame 1
        0f, 0f, 0f     // frame 2
    ),
    interpolation = InterpolationType.LINEAR
)

val rotationTrack = KeyframeTrack(
    name = "mesh.rotation",
    times = floatArrayOf(0f, 2f),
    values = floatArrayOf(
        0f, 0f, 0f,            // frame 0
        0f, PI.toFloat(), 0f   // frame 2
    ),
    interpolation = InterpolationType.LINEAR
)

val clip = AnimationClip(
    name = "bounce",
    duration = 2f,
    tracks = listOf(positionTrack, rotationTrack)
)

// Play animation
val mixer = AnimationMixer(mesh)
val action = mixer.clipAction(clip)
action.play()

// Update in loop
fun animate(deltaTime: Float) {
    mixer.update(deltaTime)
}
```

### Skeletal Animation

```kotlin
import io.materia.animation.SkeletalAnimationSystem

// Load model with skeleton
val loader = GLTFLoader()
loader.load("character.gltf") { gltf ->
    val model = gltf.scene
    scene.add(model)

    // Get animations
    val animations = gltf.animations
    val mixer = AnimationMixer(model)

    // Play walk animation
    val walkAction = mixer.clipAction(animations[0])
    walkAction.play()

    // Blend to run animation
    val runAction = mixer.clipAction(animations[1])
    walkAction.crossFadeTo(runAction, duration = 0.5f)
}
```

## Camera Controls

### Orbit Controls

```kotlin
import io.materia.controls.OrbitControls

val controls = OrbitControls(camera, renderer.domElement).apply {
    enableDamping = true
    dampingFactor = 0.05f
    minDistance = 1f
    maxDistance = 100f
    maxPolarAngle = PI.toFloat() / 2f
}

// Update in animation loop
fun animate() {
    controls.update()
    renderer.render(scene, camera)
}
```

### First Person Controls

```kotlin
import io.materia.controls.FirstPersonControls

val controls = FirstPersonControls(camera, renderer.domElement).apply {
    movementSpeed = 5f
    lookSpeed = 0.1f
    verticalMovement = true
}

fun animate(deltaTime: Float) {
    controls.update(deltaTime)
    renderer.render(scene, camera)
}
```

## Loading Models

### GLTF Loader

```kotlin
import io.materia.loader.GLTFLoader

val loader = GLTFLoader()

// Basic loading
loader.load("model.gltf") { gltf ->
    scene.add(gltf.scene)
}

// With progress callback
loader.load(
    url = "large-model.gltf",
    onLoad = { gltf ->
        println("Model loaded!")
        scene.add(gltf.scene)
    },
    onProgress = { progress ->
        val percent = (progress.loaded / progress.total) * 100
        println("Loading: ${percent.toInt()}%")
    },
    onError = { error ->
        println("Error loading model: $error")
    }
)

// Access animations
loader.load("animated-character.gltf") { gltf ->
    val model = gltf.scene
    scene.add(model)

    val mixer = AnimationMixer(model)
    gltf.animations.forEach { clip ->
        mixer.clipAction(clip).play()
    }
}
```

### OBJ Loader

```kotlin
import io.materia.loader.OBJLoader

val objLoader = OBJLoader()
objLoader.load("model.obj") { group ->
    scene.add(group)
}

// With MTL materials
import io.materia.loader.MTLLoader

val mtlLoader = MTLLoader()
mtlLoader.load("model.mtl") { materials ->
    objLoader.materials = materials
    objLoader.load("model.obj") { group ->
        scene.add(group)
    }
}
```

## Performance Optimization

### Level of Detail (LOD)

```kotlin
import io.materia.lod.LOD

val lod = LOD()

// Add detail levels
lod.addLevel(highDetailMesh, distance = 0f)
lod.addLevel(mediumDetailMesh, distance = 50f)
lod.addLevel(lowDetailMesh, distance = 100f)

scene.add(lod)

// Update in animation loop
fun animate() {
    lod.update(camera)
    renderer.render(scene, camera)
}
```

### Instancing

```kotlin
import io.materia.instancing.InstancedMesh

val count = 1000
val instancedMesh = InstancedMesh(geometry, material, count)

// Set transforms for each instance
val matrix = Matrix4()
for (i in 0 until count) {
    val x = (Random.nextFloat() - 0.5f) * 100f
    val y = (Random.nextFloat() - 0.5f) * 100f
    val z = (Random.nextFloat() - 0.5f) * 100f

    matrix.makeTranslation(x, y, z)
    instancedMesh.setMatrixAt(i, matrix)
}

scene.add(instancedMesh)
```

### Object Pooling

```kotlin
class BulletPool {
    private val pool = mutableListOf<Mesh>()
    private val active = mutableListOf<Mesh>()

    fun acquire(): Mesh {
        return if (pool.isNotEmpty()) {
            pool.removeAt(0).also { it.visible = true }
        } else {
            createBullet()
        }.also { active.add(it) }
    }

    fun release(bullet: Mesh) {
        bullet.visible = false
        active.remove(bullet)
        pool.add(bullet)
    }

    private fun createBullet(): Mesh {
        return Mesh(sphereGeometry, bulletMaterial)
    }
}
```

## See Also

- [Advanced Techniques](advanced-techniques.md)
- [Best Practices](best-practices.md)
- [API Reference](../api-reference/README.md)
