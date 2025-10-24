# Force Graph Re-rank

The force graph demo renders 2–5k nodes with 6–10k edges and lets you toggle between TF‑IDF and semantic weighting. Layouts are baked offline and loaded at runtime so each target shows the same deterministic positions.

---

## Launch Commands

| Platform | Command | Notes |
|----------|---------|-------|
| Web (WebGPU) | `./gradlew :examples:force-graph:jsBrowserRun` | Opens the webpack dev server; chrome 120+ recommended for WebGPU. |
| Desktop (JVM Vulkan) | `./gradlew :examples:force-graph:run` | Ideal for profiling layout tweening and uniform uploads. |
| Android (Vulkan) | `./gradlew :examples:force-graph:installDebug` | Uses the same baked data set; ensure the JSON asset is bundled. |

---

## Performance Goals

- **Desktop:** ≥60 FPS @1080p with tweening active. CPU layout interpolation <4 ms/frame.
- **Web:** ≥45 FPS on Chrome/Safari with WebGPU. Expect ~2 ms GPU for instanced lines and points.
- **Android:** ≥30 FPS on modern phones. Toggle animation should finish in 500 ms.

If you see stutters:

1. Confirm the baked layout JSON is loaded from disk/cache (no re-bake at runtime).
2. Check uniform ring buffer usage—two buffers (TF‑IDF + semantic) should fit in a single frame bucket.
3. Ensure pipeline warm-up happens before first toggle (`prewarmPipelines()` in scene init).

---

## Controls

- `T` – Switch to TF‑IDF weighting.
- `S` – Switch to semantic weighting.
- `Space` – Toggle between the two modes.
- Mouse / touch drag – Orbit the camera.
- Scroll / pinch – Zoom.

HUD overlays show current mode, node count, and frame time. Toggle commands queue a tween that interpolates edge thickness/opacity over 0.5 s.

---

## Data & Layout

1. Source graph baked with `SyntheticDataFactory.forceGraph()` producing deterministic node/edge lists.
2. Layout solved on the JVM/native target and exported as JSON (`data/layouts/force-graph.json`).
3. At runtime the scene loads the JSON, builds instanced buffers, and caches both TF‑IDF and semantic uniforms.
4. Toggling weights only swaps bind groups—no geometry rebuild is required.

Enable capture via `SAVE_CAPTURE=true` to dump PNG snapshots after each mode switch (`examples/_captures/force-graph/`).
