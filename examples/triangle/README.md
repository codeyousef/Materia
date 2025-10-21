# KreeKt Triangle Example

This example demonstrates the core functionality of the KreeKt 3D library with a simple scene containing:

- **Rotating cube** with PBR material and emissive properties
- **Floating sphere** with animated position and pulsing emission
- **Ground plane** with basic material
- **Decorative cubes** arranged in a line
- **Dynamic lighting** with directional, ambient, point, and spot lights
- **Animated camera** that orbits around the scene
- **Interactive controls** for manual camera movement

## Features Demonstrated

### 🎨 **Materials & Rendering**

- PBR (Physically Based Rendering) materials
- Metallic and roughness properties
- Emissive materials with animation
- Transparency and opacity
- Color variations

### 💡 **Lighting System**

- Directional light (simulates sun)
- Ambient light (global illumination)
- Point light (local illumination)
- Spot light (focused beam with shadows)
- Dynamic light positioning

### 🎬 **Animation**

- Object rotation and translation
- Camera orbit animation
- Material property animation (emissive pulsing)
- Smooth interpolation using trigonometric functions

### 🎮 **Interaction**

- Keyboard controls (WASD + QE)
- Mouse look controls
- Real-time camera manipulation
- Platform-specific input handling

### 🏗️ **Architecture**

- Cross-platform Kotlin Multiplatform code
- Common scene logic with platform-specific renderers
- WebGPU for web, Vulkan/OpenGL for desktop
- Proper resource management and cleanup

## Running the Example

### 🖥️ **Desktop (JVM)**

```bash
# Run directly
./gradlew :examples:basic-scene:runJvm

# Or use the simple launcher
./gradlew :examples:basic-scene:runSimple

# Or build and run the JAR
./gradlew :examples:basic-scene:jvmJar
java -jar examples/basic-scene/build/libs/basic-scene-jvm.jar
```

**Desktop Controls:**

- `WASD` - Move camera forward/back/left/right
- `Q/E` - Move camera up/down
- `Mouse` - Look around (click and drag)
- `ESC` - Exit application

### 🌐 **Web Browser (JavaScript)**

```bash
# Run in browser (opens automatically)
./gradlew :examples:basic-scene:runJs

# Or start development server manually
./gradlew :examples:basic-scene:jsBrowserDevelopmentRun
# Then open http://localhost:8080

# Or build for production
./gradlew :examples:basic-scene:jsBrowserDistribution
```

**Web Controls:**

- `WASD` - Move camera forward/back/left/right
- `Q/E` - Move camera up/down
- `Mouse` - Look around (click and drag)

### 📱 **Mobile (Future)**

Mobile implementations for Android and iOS will be added in future examples.

## Technical Details

### Performance Targets

- **60 FPS** on modern hardware
- **Efficient rendering** with minimal draw calls
- **Smooth animations** with proper frame timing
- **Responsive controls** with low input latency

### Rendering Pipeline

1. **Scene Setup** - Create objects, materials, lights
2. **Animation Update** - Update object transforms and properties
3. **Camera Update** - Handle input and update camera position
4. **Render Pass** - Submit draw calls to GPU
5. **Present** - Display frame to screen

### Memory Management

- Proper resource disposal on cleanup
- Efficient object pooling for frequently created objects
- Minimal garbage collection pressure
- Platform-specific optimizations

## Code Structure

```
src/
├── commonMain/kotlin/
│   └── TriangleExample.kt     # Core scene logic
├── jvmMain/kotlin/
│   └── TriangleExample.jvm.kt # Desktop implementation
└── jsMain/kotlin/
    └── TriangleExample.js.kt  # Web implementation
```

### Key Classes

- **TriangleExample** - Main scene management and rendering loop
- **InputState** - Platform-specific input handling
- **Renderer** - Platform-specific rendering backend
- **Scene** - 3D scene graph with objects and lights
- **Camera** - Perspective camera with controls

## Extending the Example

### Adding New Objects

```kotlin
val newObject = Object3D().apply {
    geometry = PrimitiveGeometry.createTorus(1.0f, 0.3f, 16, 100)
    material = PBRMaterial().apply {
        baseColor = Color.BLUE
        metallic = 0.8f
        roughness = 0.2f
    }
    position.set(2.0f, 1.0f, 0.0f)
}
scene.add(newObject)
```

### Custom Materials

```kotlin
val customMaterial = PBRMaterial().apply {
    baseColor = Color(0.9f, 0.1f, 0.5f)
    metallic = 0.0f
    roughness = 0.7f
    emissive = Color(0.1f, 0.0f, 0.1f)
    transparent = true
    opacity = 0.9f
}
```

### Animation Sequences

```kotlin
// In the render loop
val animationSpeed = 2.0f
object.rotation.y = time * animationSpeed
object.position.y = sin(time * 3.0f) * 0.5f + baseHeight
```

## Dependencies

- **KreeKt Core** - 3D math, scene graph, rendering
- **LWJGL** - Desktop OpenGL/Vulkan bindings
- **Kotlinx Coroutines** - Async programming
- **Kotlinx Serialization** - Data serialization

## Troubleshooting

### Desktop Issues

- **No window appears**: Check LWJGL native libraries are correct for your platform
- **Poor performance**: Ensure GPU drivers are up to date
- **Crash on startup**: Check OpenGL/Vulkan support

### Web Issues

- **Blank screen**: Check browser console for WebGPU/WebGL support
- **Controls not working**: Ensure canvas has focus (click on it)
- **Performance issues**: Try a different browser or disable other tabs

### Common Issues

- **Out of memory**: Reduce scene complexity or object count
- **Stuttering**: Check system load and close other applications
- **Input lag**: Ensure vsync is enabled and frame rate is stable

## Next Steps

Try these other examples to explore more KreeKt features:

- **Material Showcase** - Advanced PBR materials and textures
- **Animation Demo** - Skeletal animation and morphing
- **Physics Simulation** - Rigid body dynamics and constraints
- **VR Experience** - Virtual reality scene exploration
- **Performance Test** - Stress testing with thousands of objects

---

*This example demonstrates the core capabilities of KreeKt. For more advanced features, see the other examples in
the `examples/` directory.*