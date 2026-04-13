# Remaining Work

## Current Status

- `examples:volume-texture` exists for JVM and JS and is the main runnable `Data3DTexture` example.
- `examples:volume-texture-android` now exists and boots the shared scene through a Filament/OpenGL Android wrapper.
- JVM Vulkan volume-texture rendering is unblocked by the `VulkanBufferManager` allocation-ID fix.
- JS/WebGL fallback coverage exists for `Data3DTexture` rendering.
- Android smoke install/launch verification now passes with `:examples:volume-texture-android:smokeAndroid`.
- Android's public wgpu path is still intentionally blocked by `src/androidMain/kotlin/io/materia/renderer/AndroidWgpuCompatibility.kt` because of the upstream `ValueLayout.Companion` packaging/runtime issue.

## Remaining Work

### 1. Verify `Data3DTexture` on Android visually end to end

- Capture a screenshot plus representative logs from the Android volume-texture wrapper.
- Document the expected visual result so future manual checks are deterministic.

### 2. Decide the long-term Android renderer path

- Either fix or replace the Android wgpu backend so the native path works again.
- Or explicitly keep Filament as the Android fallback/runtime path and document that architecture.
- If the wgpu path is restored, remove or relax the current compatibility guard and revalidate volume textures through the real Android renderer.

### 3. Add regression coverage for the recent fixes

- Add a JVM regression test that covers the swapchain resize or uniform-buffer recreation path that previously produced false "Buffer has been destroyed" failures.
- Add Android instrumentation or screenshot-based coverage for the volume-texture example beyond the current install/launch smoke task.
- Re-run the focused example and test tasks after the Android work lands.

### 4. Finish documentation and build plumbing

- Update the example index and docs to mention Android status once it is verified.
- Document how the checked-in Filament material assets are generated so the Android path is reproducible.
- Ensure any new Android example tasks are reflected in CI and contributor docs.
- Clean up the new Android task wiring if configuration-cache compatibility matters for CI.

## Open Risks

- Android volume-texture rendering now boots, but there is not yet a checked-in visual artifact proving the Android output matches JVM/JS.
- The Android wgpu runtime remains blocked by an upstream incompatibility.
- The checked-in Filament material binaries need a reproducible regeneration path if they remain part of the Android solution.