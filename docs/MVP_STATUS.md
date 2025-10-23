# MVP Status Snapshot

This file mirrors the high-level items from `mvp-plan.md` so stakeholders can see progress without digging through the full plan. ✅ marks completed work, 🔄 highlights in-flight efforts.

---

## GPU / Core Abstractions

- [ ] Surface & swapchain integration parity (Vulkan/WebGPU)
- [ ] Render-pass encoding, pipeline creation, texture/bind-group plumbing
- [x] Shader module + pipeline descriptors shared across targets
- [x] Command encoding with submit path + minimal sync primitives
- [ ] Delegate `:kreekt-gpu` implementations to renderer backends (`VulkanRenderer`, `WebGPURenderer`)

## Rendering Engine

- [x] Scene graph foundations (`Node`, transforms, dirty propagation)
- [x] Camera utilities (`PerspectiveCamera`, `OrbitController`)
- [x] Materials (`UnlitColorMaterial`, `UnlitPointsMaterial`)
- [x] Math primitives + value classes (`Vec`, `Mat4`, `Quat`, `Color`)
- [x] Instancing utilities (Catmull–Rom spline, arena + ring buffer helpers, bounding volumes)
- [ ] Material bindings to backend descriptor sets
- [ ] Geometry upload and draw submissions via real renderer paths

## Examples

- [ ] Triangle smoke test exercising real GPU backends (WebGPU/Vulkan)
- [ ] Embedding Galaxy demo (instanced points, query shockwave)
- [ ] Force Graph demo (layout bake + TF-IDF vs semantic toggle)

## Tooling & Tests

- [x] Frame memory utilities covered by unit tests
- [x] Bounding volume + spline math coverage
- [ ] GPU attribute validation (InstancedPoints stride/offset)
- [ ] Integration tests for renderer submission paths

## Documentation

- [x] API style guide (`docs/API_STYLE.md`)
- [x] Performance notes (`docs/PERF_NOTES.md`)
- [ ] Root README refresh with target matrix and quickstarts
- [ ] Example READMEs detailing FPS expectations and controls

---

The checklist is intentionally concise. For day-to-day execution continue updating `mvp-plan.md`. Update this file whenever a high-level milestone moves to ✅.
