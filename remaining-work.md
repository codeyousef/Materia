# Remaining Work

## Current Status

- `examples:volume-texture` exists for JVM and JS and is the main runnable `Data3DTexture` example.
- JVM Vulkan volume-texture rendering is unblocked by the `VulkanBufferManager` allocation-ID fix.
- JS/WebGL fallback coverage exists for `Data3DTexture` rendering.
- Android's public wgpu path is still intentionally blocked by `src/androidMain/kotlin/io/materia/renderer/AndroidWgpuCompatibility.kt` because of the upstream `ValueLayout.Companion` packaging/runtime issue.

## Remaining Work

### 1. Ship an Android volume-texture example

- Add an `examples:volume-texture-android` module and wire it into `settings.gradle.kts`.
- Reuse the shared `VolumeTextureExample` scene instead of duplicating scene construction.
- Build the Android wrapper on top of the working Filament/OpenGL pattern already used by `examples:triangle-android`.

### 2. Verify `Data3DTexture` on Android end to end

- Traverse the shared scene meshes and sample `Data3DTexture` data via `VolumeTextureSampler` into vertex colors or an equivalent Android-compatible path.
- Run the Android example on the emulator or device and capture a screenshot plus logs.
- Document the expected visual result so future manual checks are deterministic.

### 3. Decide the long-term Android renderer path

- Either fix or replace the Android wgpu backend so the native path works again.
- Or explicitly keep Filament as the Android fallback/runtime path and document that architecture.
- If the wgpu path is restored, remove or relax the current compatibility guard and revalidate volume textures through the real Android renderer.

### 4. Add regression coverage for the recent fixes

- Add a JVM regression test that covers the swapchain resize or uniform-buffer recreation path that previously produced false "Buffer has been destroyed" failures.
- Add Android smoke or instrumentation coverage for the volume-texture example once the module exists.
- Re-run the focused example and test tasks after the Android work lands.

### 5. Finish documentation and build plumbing

- Update the example index and docs to mention Android status once it is verified.
- Document how the checked-in Filament material assets are generated so the Android path is reproducible.
- Ensure any new Android example tasks are reflected in CI and contributor docs.

## Open Risks

- Android volume-texture rendering is still unverified because no Android wrapper example exists yet.
- The Android wgpu runtime remains blocked by an upstream incompatibility.
- The checked-in Filament material binaries need a reproducible regeneration path if they remain part of the Android solution.