# Volume Texture Example

This example is the first runnable scene dedicated to `Data3DTexture`. It renders a rotating cube and sphere that both sample the same generated 3D texture through `MeshBasicMaterial.map`.

## Targets

| Platform | Command | Notes |
|---|---|---|
| Desktop (JVM Vulkan) | `./gradlew :examples:volume-texture:runJvm` | Uses the real Vulkan 3D texture path. |
| Browser (WebGPU/WebGL) | `./gradlew :examples:volume-texture:jsBrowserRun` | Uses WebGPU when available and falls back to WebGL through `RendererFactory`. |

## What To Look For

- The cube should show a stable tricolor gradient with blocky banding from the voxel pattern.
- The sphere should show the same volume field sampled from curved local-space positions.
- On browsers without WebGPU, the example should still render through WebGL with the CPU-sampled fallback path.

## Purpose

- Demonstrates the built-in `Data3DTexture` consumer on `MeshBasicMaterial.map`.
- Gives a quick manual check for JS WebGPU, JVM Vulkan, and JS WebGL fallback behaviour.