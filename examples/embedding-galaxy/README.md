# Embedding Galaxy

This showcase renders 20k synthetic embeddings as instanced points. Every few seconds a query vector triggers a shockwave that grows point size and emissive intensity for the nearest neighbours. The scene stresses instancing, bind-group caching, and the uniform ring buffer.

---

## Launch Commands

| Platform | Command | Notes |
|----------|---------|-------|
| Web (wasmJs + WebGPU) | `./gradlew :examples:embedding-galaxy:wasmJsBrowserRun` | Starts a dev server with hot reload. Requires WebGPU. |
| Desktop (JVM Vulkan) | `./gradlew :examples:embedding-galaxy:run` | Ideal for profiling pipeline warm-up and instancing throughput. |
| Android (Vulkan) | `./gradlew :examples:embedding-galaxy:installDebug` | Same scene as desktop; ensure `USE_LOW_POWER=false` in the config for smooth playback. |

---

## Performance Targets

- **Desktop:** ≥60 FPS @1080p on mid-tier GPUs (RTX 2060 / RX 5600). Frame time dominated by point sprite pipeline.
- **Web:** ≥45 FPS on Chrome 120+ with WebGPU enabled. Expect a 1–2 second compile hitch on first shader warm-up.
- **Android:** ≥30 FPS on Adreno 640+ at 1080p. Shockwave animation should stay under 8 ms CPU.

If frame time regresses, inspect:

1. Arena/ring buffer pressure (`FrameArena.used` spikes).
2. Shader pipeline cache misses (pipeline creation during frame).
3. Bind group churn (descriptor recycling working set).

---

## Controls

- `Q` – Trigger a new query shockwave manually.
- `R` – Reset the animation timeline.
- `P` – Pause/resume scene updates.
- Mouse / touch drag – Orbit the camera.
- Scroll / pinch – Zoom in/out.

The shockwave runs automatically every 4 seconds. Manual triggers help when capturing GPU traces.

---

## Data Pipeline

1. Synthetic clusters generated via `SyntheticDataFactory`.
2. Instanced attributes packed into a single buffer (position, color, metadata).
3. Frame-local uniforms allocated from `UniformRingBuffer`.
4. Camera motion follows a Catmull–Rom spline track.

Snapshots are available by enabling `SAVE_CAPTURE` in the example configuration; captures are written to `examples/_captures/embedding-galaxy/`.
