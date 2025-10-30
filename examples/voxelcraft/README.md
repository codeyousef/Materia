# VoxelCraft - Minecraft Clone Example

A fully functional creative-mode voxel building game demonstrating Materia's multiplatform 3D
graphics capabilities.

## Browser Requirements (JavaScript Target)

⚠️ **WebGPU Required** - WebGL fallback not yet implemented

### Supported Browsers
- **Chrome/Edge 113+** (enabled by default) ✅
- **Firefox Nightly** with `dom.webgpu.enabled` flag
- **Safari Technology Preview**

### Check WebGPU Support
Open browser console (F12) and type:
```javascript
navigator.gpu
```
- Returns object → WebGPU available ✅
- Returns undefined → WebGPU not available ❌

### Enable WebGPU in Firefox
1. Open `about:config`
2. Search for `dom.webgpu.enabled`
3. Set to `true`
4. Restart Firefox

## Running the Example

### JavaScript (Browser)
```bash
./gradlew :examples:voxelcraft:runJs
# Opens http://localhost:8080
```

### JVM (Desktop - Requires Vulkan)
```bash
./gradlew :examples:voxelcraft:runJvm
```

## Controls
- **WASD** - Move
- **Mouse** - Look around
- **F** - Toggle flight mode
- **Space/Shift** - Up/Down (flight mode)

## Features
- Procedural terrain (512x512x256 blocks, Simplex noise)
- Chunk-based rendering (1,024 chunks)
- First-person controls
- Flight mode
- Performance: 60 FPS target @ 100k triangles

## Architecture

Uses Materia's platform-agnostic API:
```kotlin
val surface = SurfaceFactory.create(windowOrCanvas)
val renderer = RendererFactory.create(surface)
renderer.render(scene, camera)
```

No platform-specific rendering code! Works on both JVM (Vulkan) and JS (WebGPU).
