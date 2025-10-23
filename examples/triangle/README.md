# Triangle Smoke Test

The triangle example is the fast path validation that our GPU layer boots end-to-end. It clears the backbuffer and renders a single WGSL triangle through `RendererFactory`, exercising swapchain creation and pipeline setup on every platform.

---

## Targets & Launch Commands

| Platform | Command | Notes |
|----------|---------|-------|
| Web (wasmJs + WebGPU) | `./gradlew :examples:triangle:wasmJsBrowserRun` | Opens a dev server with hot reload. Requires a WebGPU-capable browser. |
| Desktop (JVM Vulkan) | `./gradlew :examples:triangle:run` | Launches a GLFW window with vsync enabled. |
| Android (Vulkan) | `./gradlew :examples:triangle:installDebug` then `adb shell am start -n com.kreekt.examples.triangle/.MainActivity` | Uses the same renderer path as desktop. |

iOS/macOS MoltenVK targets will reuse the desktop scene once the native launcher is wired up.

---

## Expected Performance

- **Desktop (Vulkan):** 60 FPS locked (vsync). GPU time should stay below 0.1 ms.
- **Web (WebGPU):** 60 FPS on Chrome 120+ or Safari TP. Expect the dev server to cold-start in <15 seconds.
- **Android:** 60 FPS on mid-tier 2022 hardware (Adreno 640+, Mali-G77+). Surface recreation should stay under 100 ms.

If the window fails to present, capture logs with `--stacktrace` and verify driver support:

```bash
./gradlew :examples:triangle:run --stacktrace
```

---

## Controls

There are no runtime controls. Resize events on desktop/mobile should rescale the swapchain automatically. Web builds track canvas resize events and adjust the surface in the RAF loop.

---

## Diagnostic Checklist

Use this sample to validate a platform bring-up:

1. `RendererFactory.detectAvailableBackends()` returns at least one backend (WebGPU or Vulkan).
2. `GpuSurfaceFactory.create(...)` configures the preferred swapchain format.
3. Command buffers submit without validation errors (`VK_SUCCESS` or WebGPU log clean).
4. Closing the window tears down device resources without crashes or lingering surfaces.

If any step fails, fix it before moving to `embedding-galaxy` or `force-graph`.
