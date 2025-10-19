# KreeKt Parity Roadmap

## Vision
Achieve feature and behavioural parity with Three.js while targeting modern GPU backends (WebGPU for JS, Vulkan for JVM/Native) through a clean multiplatform architecture. The end goal is a production-ready renderer, asset pipeline, and tooling stack that behave consistently across all supported targets.

## Guiding Principles
- **Parity first**: replicate Three.js user-facing APIs, rendering behaviour, and ecosystem breadth before pursuing experimental features.
- **Backend abstraction**: keep common code backend-agnostic; isolate WebGPU/Vulkan specifics behind expect/actual interfaces.
- **Deterministic rendering**: deterministic frame output across backends (validated via golden image tests).
- **Extensibility**: architecture must accommodate future backends (Metal/D3D12), node-based materials, and editor workflows.

---

## Milestone Overview

| Milestone | Scope | Primary Deliverables |
|-----------|-------|----------------------|
| **M1. GPU Abstraction & Core Infrastructure** | Establish backend-neutral GPU device/queue/buffer/texture APIs. | Expect/actual GPU wrappers, WebGPU + Vulkan backing implementations, renderer refactor entry points. |
| **M2. Material & Shader System Parity** | Port Three.js shader library to WGSL/SPIR-V; introduce node material pipeline. | Shader chunk registry, material descriptors, uniform/reflection system. |
| **M3. Geometry & Attribute Pipeline** | Support full attribute sets (morph, skinning, instancing) and buffer lifecycle. | Vertex layout manager, buffer allocators, index formats, geometry cache. |
| **M4. Render Loop & Scene Management** | Scene traversal, render lists, state sorting, clipping, fog, background. | Render list builder, pipeline/state binding, multipass handling, color space controls. |
| **M5. Lighting & Shadows** | Directional/spot/point lights, probes, cascaded shadows, IBL pipeline on GPU. | Shadow map atlas, GPU PMREM/BRDF LUT generation, lighting uniform system. |
| **M6. Post-processing & Render Targets** | Effect composer equivalent, MRT, tone mapping, common post FX. | Render graph/composer, builtin passes (bloom, SSAO, FXAA, depth-of-field). |
| **M7. Asset Pipeline & Loaders** | GLTF 2.0 parity, texture/geometry compression, audio loaders. | GLTF loader + KTX2/DRACO/Basis decoders, HDR/EXR support, resource cache & dependency graph. |
| **M8. Animation, XR, Utilities** | Port animation mixer, physics hooks, XR capabilities. | Animation system parity, XRSession wrappers, physics integration points. |
| **M9. Tooling, Testing, Docs** | Automation, profiling, CLI tools, docs parity. | Screenshot regression harness, perf benchmarks, editor integrations, comprehensive docs. |

---

## Detailed Milestones

### M1. GPU Abstraction & Core Infrastructure *(In Progress)*
1. **GPU wrapper layer**
   - ✅ Expect/actual definitions for devices, queues, request config, limits, and device factories (WebGPU actual in place; Vulkan actual returns placeholder until wired).
   - ✅ Buffer + command encoder/buffer wrappers and queue submission helpers (WebGPU actual) with handle unwrapping for legacy interop.
   - ✅ Texture/sampler/bind-group abstractions & resource lifetime utilities.
2. **Backend factories**
   - ✅ WebGPU context acquisition via navigator.gpu (including adapter info/limits extraction, buffers/encoders/textures abstraction).
   - ✅ Vulkan context bootstrap (instance/adapter selection, logical device/queue creation, descriptor pool setup) completed with abstraction wiring.
3. **Renderer integration groundwork**
   - ✅ WebGPU renderer now acquires device/queue through abstraction and uses wrapped command encoders/queues for submission.
   - ✅ Provide helper utilities for resource lifetime tracking and staging buffer reuse (per-frame allocators, persistently mapped buffers).
   - ✅ Update buffer pool/other managers to adopt abstraction instead of raw handles.
4. **Validation**
   - ✅ Compile-time checks on all targets after renderer refactor.
   - ✅ Diagnostic logging of backend/device capabilities surfaced via the abstraction.

### M2. Material & Shader System Parity (In Progress)
- Port Three.js shader chunks to WGSL and GLSL (SPIR-V) counterparts.
- Build shader registry and module cache keyed per material configuration.
- Define material descriptors (uniforms, textures, defines, blending states).
- Introduce node-based material graph with code generation for both backends.
- **Active:** Wire WebGPU texture sampling (albedo, normal) through `FRAGMENT_BINDINGS`/`FRAGMENT_INIT_EXTRA` overrides and shared descriptor metadata.
- **Up next:** Reflect texture bindings into Vulkan/SPIR-V material pipelines once layout negotiation is mirrored.

### M3. Geometry & Attribute Pipeline (In Progress)
- Implement geometry builder that maps Three.js BufferGeometry semantics to GPU buffers.
- Support interleaved and deinterleaved layouts, instanced attributes, morph targets, skeletal weights.
- Add buffer suballocation and orphaning strategies for dynamic attributes.
- **Next:** Surface GeometryMetadata + attribute requirements to Vulkan vertex input layouts and harmonise WebGPU/Vulkan pipeline selection.

### M4. Render Loop & Scene Management
- Scene traversal identical to Three.js (layers, visibility, frustum culling).
- Render list generator with opaque/transparent sorting and state deduplication.
- Multi-pass capability (shadow, main, post) with per-pass state overrides.
- Tone mapping, output encoding, fog, background environment management.

### M5. Lighting & Shadows
- Light management for point/spot/directional/rect area lights, probes, hemispherical lights.
- Cascaded shadow maps for directional lights, cubemap shadows for point lights.
- GPU-based PMREM (environment prefilter) and BRDF LUT calculators.
- Light uniform buffers structured to match Three.js implementations.

### M6. Post-processing & Render Targets
- Effect composer analogue supporting chained passes with ping-pong FBOs.
- Builtin passes: Copy, RenderScene, ShaderPass, UnrealBloom, SSAO, FXAA, DoF.
- MRT support, half/float precision render targets, texture pooling.

### M7. Asset Pipeline & Loaders
- GLTF 2.0 loader with full material, animation, skinning support.
- Compression pipelines: DRACO geometry, Basis/KTX2 textures, meshopt.
- HDR/EXR readers for environment maps, audio loaders, async resource cache.

### M8. Animation, XR, Utilities
- Animation system parity (keyframe tracks, blend trees, morph/skin support).
- XR session integration (navigation, controllers, reference spaces).
- Physics hooks (scene graph update callbacks, collider syncing).

### M9. Tooling, Testing, Docs
- Automated screenshot regression per backend/per scene.
- GPU/CPU performance benchmarking harness.
- CLI tools for shader compilation, asset preprocessing.
- Documentation parity (API reference, guides, migration docs) and example galleries mirroring Three.js sites.

---

## Execution Strategy
1. **Milestone gating**: move to the next milestone only when current APIs are stable, tested, and integrated across platforms.
2. **Feature flags**: expose new systems behind flags until fully validated.
3. **Test-first**: port Three.js example scenes/tests as acceptance criteria for each milestone.
4. **Documentation alongside code**: update docs/examples per milestone to keep parity visible.

---

## Current Focus
- Stabilise WebGPU material texture manager + shader overrides (albedo/normal sampling path).
- Mirror bind-group expectations and GeometryMetadata into Vulkan pipeline selection before enabling normal mapping.
- Update validation/tests to cover new shader override hooks and texture bindings across targets.

### Immediate Next Steps
1. ✅ Restore Kotlin/JS build stability by swapping shared mutable caches for atomic/persistent registries and cross-platform locking (MaterialShaderLibrary, MaterialShaderGenerator, ShaderChunkRegistry, MaterialDescriptorRegistry); validated with `./gradlew compileKotlinJs`.
2. ✅ Re-enable geometry metadata helpers on Kotlin/JS by centralising build-option derivation (MaterialDescriptor.buildGeometryOptions) and adding coverage for instancing, morphs, and optional attributes.
3. ✅ Add regression coverage for shader placeholder replacements so escaped bindings/initialiser snippets stay correct on WGSL output.
4. ✅ Vulkan renderer now samples bound albedo textures (normal/environment bindings still TODO); JVM smoke test covers `MeshBasicMaterial` + `Texture2D`, with documented gaps around environment maps and mipmap generation.
5. ✅ Vulkan shader/material pipeline now perturbs normals using tangent space (falls back gracefully when tangents are absent).
6. ✅ Texture fidelity: Vulkan path now generates mip levels when supported and respects sampler filtering/wrap modes (no anisotropic filtering yet).
7. ✅ Lighting parity: bind and sample environment cube textures (prefilter/BRDF) so PBR materials behave consistently across Vulkan/WebGPU.
8. ✅ PBR material support: wire roughness/metalness/ambient occlusion maps and tangent-space lighting into Vulkan shaders with proper descriptor bindings.
9. 🚧 Validation: broaden coverage (integration smoke, docs) around the new BRDF/environment wiring and highlight any remaining parity deltas.

### Upcoming Tasks
- 📌 Update example/demo pipelines to call `processEnvironmentForScene` so scenes automatically receive prefiltered cubes + BRDF LUTs.
- 📌 Capture parity notes (docs + RenderStats) comparing fallback LUT vs generated LUT to guide QA expectations.
- 📌 Add headset/scene regression that exercises the new helper end-to-end (HDR → IBL → render) on both backends.
