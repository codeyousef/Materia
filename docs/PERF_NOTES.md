# Performance Notes

Materia targets **60 FPS** on desktop and **45 FPS** on Web with instanced scenes up to ~20k
elements. The shortcuts below summarise proven tactics for keeping frame times predictable.

---

## Frame Memory Management

- Use `FrameArena` for transient uniform payloads and CPU scratch buffers. Reset once per frame.
- Stream per-frame uniforms via `UniformRingBuffer` so GPU reads never overlap writes.
- Avoid allocating Kotlin collections in render paths; reuse value classes (`Vec3`, `Mat4`) and slices.

## GPU Pipelines

- Pre-create pipelines and bind-group layouts during scene loading. Runtime creation is expensive on Vulkan.
- Cache descriptor sets keyed by material state (`UnlitColorMaterial`, `UnlitPointsMaterial` etc.).
- Keep vertex formats tightly packed: 32-bit floats for positions/colors, `Float32x4` for instancing extras.

## Buffer Updates

- Batch uploads on the GPU queue: map once, write contiguous ranges, unmap.
- For instanced point clouds, prefer single interleaved buffers (position/color/size) to minimise binds.
- When targeting WebGPU, use `COPY_DST` staging buffers and queue writes to avoid blocking `mapAsync`.

## Draw Submission

- Group draw calls by pipeline and bind groups to reduce command buffer churn.
- Issue UI overlays and post-processing after scene draws to avoid unnecessary state toggles.
- Use additive blending for dense point sprites to skip depth writes when possible.

## Profiling Tips

- Capture GPU traces with RenderDoc (Vulkan) or Chrome WebGPU Profiler for browser builds.
- Instrument frame times using Kotlin’s `TimeSource` reports; feed numbers into the in-engine HUD.
- Track arena usage (`FrameArena.used`) to ensure headroom. Spikes often signal hidden allocations.

Following these patterns keeps hot loops predictable and makes it easier to scale Materia scenes
across the supported platforms.
