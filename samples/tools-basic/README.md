# Materia Tools - Basic Usage Example

This sample demonstrates the basic usage of Materia development tools for creating a simple 3D
scene.

## Overview

This example shows how to:

- Set up a basic Materia project
- Use the Scene Editor to create a simple 3D scene
- Apply materials with the Material Editor
- Add basic animations with the Animation Editor
- Monitor performance with the Performance Profiler

## Prerequisites

- Java 17 or later
- Node.js 18 or later (for web tools)
- Materia Tools installed

## Project Structure

```
samples/tools-basic/
├── README.md
├── build.gradle.kts
├── settings.gradle.kts
├── src/
│   ├── commonMain/kotlin/
│   │   ├── BasicScene.kt
│   │   ├── MaterialSetup.kt
│   │   └── AnimationSetup.kt
│   ├── jvmMain/kotlin/
│   │   └── DesktopApp.kt
│   └── jsMain/kotlin/
│       └── WebApp.kt
├── scenes/
│   ├── basic-scene.materia
│   └── materials.json
└── tools/
    ├── launch-scene-editor.sh
    ├── launch-material-editor.sh
    └── launch-profiler.sh
```

## Getting Started

### 1. Build the Project

```bash
./gradlew build
```

### 2. Launch Development Tools

#### Scene Editor
```bash
# Desktop version
./tools/launch-scene-editor.sh

# Web version
open http://localhost:3000/tools/editor
```

#### Material Editor
```bash
# Desktop version
./tools/launch-material-editor.sh

# Web version
open http://localhost:3000/tools/editor#materials
```

#### Performance Profiler
```bash
# Desktop version
./tools/launch-profiler.sh

# Web version
open http://localhost:3000/tools/profiler
```

### 3. Basic Workflow

1. **Create a Scene**
   - Open the Scene Editor
   - Add a cube, sphere, and plane to the scene
   - Position objects using the transform controls
   - Save as `scenes/basic-scene.materia`

2. **Create Materials**
   - Open the Material Editor
   - Create a basic material with diffuse color
   - Add a material with texture mapping
   - Save materials to `scenes/materials.json`

3. **Add Animation**
   - Select the cube in the Scene Editor
   - Switch to Animation mode
   - Create rotation keyframes
   - Preview the animation

4. **Monitor Performance**
   - Run the application
   - Open the Performance Profiler
   - Monitor FPS, draw calls, and memory usage

## Sample Code

### Basic Scene Setup

```kotlin
// BasicScene.kt
import io.materia.scene.*
import io.materia.geometry.*
import io.materia.material.*
import io.materia.math.*

class BasicScene {
    val scene = Scene()

    fun setupScene() {
        // Create a cube
        val cubeGeometry = BoxGeometry(width = 2.0, height = 2.0, depth = 2.0)
        val cubeMaterial = BasicMaterial(color = Color(1.0, 0.0, 0.0))
        val cube = Mesh(cubeGeometry, cubeMaterial)
        cube.position.set(x = -3.0, y = 0.0, z = 0.0)
        scene.add(cube)

        // Create a sphere
        val sphereGeometry = SphereGeometry(radius = 1.5)
        val sphereMaterial = BasicMaterial(color = Color(0.0, 1.0, 0.0))
        val sphere = Mesh(sphereGeometry, sphereMaterial)
        sphere.position.set(x = 0.0, y = 0.0, z = 0.0)
        scene.add(sphere)

        // Create a plane
        val planeGeometry = PlaneGeometry(width = 10.0, height = 10.0)
        val planeMaterial = BasicMaterial(color = Color(0.5, 0.5, 0.5))
        val plane = Mesh(planeGeometry, planeMaterial)
        plane.rotation.x = -Math.PI / 2
        plane.position.y = -2.0
        scene.add(plane)

        // Add lighting
        val ambientLight = AmbientLight(color = Color(0.4, 0.4, 0.4))
        scene.add(ambientLight)

        val directionalLight = DirectionalLight(color = Color(0.8, 0.8, 0.8))
        directionalLight.position.set(x = 5.0, y = 10.0, z = 5.0)
        scene.add(directionalLight)
    }
}
```

### Material Configuration

```kotlin
// MaterialSetup.kt
import io.materia.material.*
import io.materia.texture.*

class MaterialSetup {
    fun createBasicMaterials(): Map<String, Material> {
        return mapOf(
            "red" to BasicMaterial(
                color = Color(1.0, 0.0, 0.0),
                metalness = 0.0,
                roughness = 0.5
            ),
            "metal" to StandardMaterial(
                color = Color(0.8, 0.8, 0.9),
                metalness = 1.0,
                roughness = 0.2
            ),
            "textured" to StandardMaterial(
                map = TextureLoader.load("textures/diffuse.jpg"),
                normalMap = TextureLoader.load("textures/normal.jpg"),
                roughnessMap = TextureLoader.load("textures/roughness.jpg")
            )
        )
    }
}
```

### Basic Animation

```kotlin
// AnimationSetup.kt
import io.materia.animation.*
import io.materia.math.*

class AnimationSetup {
    fun createRotationAnimation(target: Object3D): AnimationClip {
        val track = QuaternionKeyframeTrack(
            name = "${target.name}.quaternion",
            times = doubleArrayOf(0.0, 1.0, 2.0),
            values = arrayOf(
                Quaternion.fromEuler(0.0, 0.0, 0.0),
                Quaternion.fromEuler(0.0, Math.PI, 0.0),
                Quaternion.fromEuler(0.0, Math.PI * 2, 0.0)
            )
        )

        return AnimationClip(
            name = "rotation",
            duration = 2.0,
            tracks = listOf(track)
        )
    }
}
```

## Tool Integration

### Scene Editor Integration

The Scene Editor can load and save scene files in Materia format:

```kotlin
// Loading a scene
val sceneLoader = SceneLoader()
val scene = sceneLoader.load("scenes/basic-scene.materia")

// Saving a scene
val sceneExporter = SceneExporter()
sceneExporter.save(scene, "scenes/basic-scene.materia")
```

### Material Editor Integration

Materials can be defined in JSON format and loaded at runtime:

```json
{
  "materials": {
    "cube_material": {
      "type": "StandardMaterial",
      "color": [1.0, 0.0, 0.0],
      "metalness": 0.0,
      "roughness": 0.5
    },
    "sphere_material": {
      "type": "StandardMaterial",
      "color": [0.0, 1.0, 0.0],
      "metalness": 0.8,
      "roughness": 0.2
    }
  }
}
```

### Performance Monitoring

```kotlin
// Performance monitoring setup
val profiler = PerformanceProfiler()
profiler.startProfiling()

// In your render loop
profiler.beginFrame()
renderer.render(scene, camera)
profiler.endFrame()

// View results
val metrics = profiler.getMetrics()
println("FPS: ${metrics.fps}")
println("Draw calls: ${metrics.drawCalls}")
println("Triangles: ${metrics.triangles}")
```

## Running the Example

### Desktop Version

```bash
./gradlew :samples:tools-basic:run
```

### Web Version

```bash
./gradlew :samples:tools-basic:jsBrowserDevelopmentRun
```

## Expected Output

When you run this example, you should see:
- A red cube on the left
- A green sphere in the center
- A gray plane as the ground
- The cube rotating continuously
- Performance metrics displayed in the console

## Next Steps

- Try modifying the scene in the Scene Editor
- Create custom materials with the Material Editor
- Add more complex animations
- Experiment with different lighting setups
- Monitor performance impact of changes

## Troubleshooting

### Common Issues

1. **Tools won't start**
   - Ensure Java 17+ is installed
   - Check that Node.js 18+ is available for web tools
   - Verify Materia Tools are properly installed

2. **Scene not loading**
   - Check file paths are correct
   - Ensure scene file is valid Materia format
   - Verify materials are properly defined

3. **Performance issues**
   - Use the Performance Profiler to identify bottlenecks
   - Check for excessive draw calls
   - Monitor memory usage

### Getting Help

- Check the main Materia documentation
- Visit the GitHub issues page
- Join the Materia community Discord

## License

This example is licensed under the same terms as the Materia project.