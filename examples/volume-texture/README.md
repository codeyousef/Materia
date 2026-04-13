# Volume Texture Example

This example is the first runnable scene dedicated to `Data3DTexture`. It renders a rotating cube and sphere that both sample the same generated 3D texture through `MeshBasicMaterial.map`.

## Targets

| Platform | Command | Notes |
|---|---|---|
| Desktop (JVM Vulkan) | `./gradlew :examples:volume-texture:runJvm` | Uses the real Vulkan 3D texture path. |
| Browser (WebGPU/WebGL) | `./gradlew :examples:volume-texture:jsBrowserRun` | Uses WebGPU when available and falls back to WebGL through `RendererFactory`. |
| iOS / macOS desktop (Mac Catalyst) wrapper | Build `examples/volume-texture-ios-app/MateriaVolumeTextureDemo.xcodeproj` for an iOS Simulator destination or `My Mac (Mac Catalyst)` | Bundles the working JS/WebGL example inside a native app shell because the current Apple native renderer for this API is still stubbed. |
| Android (Filament/OpenGL) | `./gradlew :examples:volume-texture-android:runAndroid` | Boots the shared scene headlessly, samples the volume texture on the CPU into vertex colors, and renders through the working Android Filament path. |

## What To Look For

- The cube should show a stable tricolor gradient with blocky banding from the voxel pattern.
- The sphere should show the same volume field sampled from curved local-space positions.
- On browsers without WebGPU, the example should still render through WebGL with the CPU-sampled fallback path.
- On Android, the scene should launch through the dedicated wrapper app and show the same cube/sphere color field rather than a flat fallback color.

## Purpose

- Demonstrates the built-in `Data3DTexture` consumer on `MeshBasicMaterial.map`.
- Gives a quick manual check for JS WebGPU, JVM Vulkan, and JS WebGL fallback behaviour.
- Provides an Android verification path while the public Android wgpu backend remains blocked upstream.

## Android Smoke Test

- Install, launch, and verify the Android wrapper log with `./gradlew :examples:volume-texture-android:smokeAndroid`.
