# Remaining Work

## Android Platform
- [x] ~~Fix Vulkan surface acquisition timeout issues on emulators and physical devices~~
  - Implemented dimension validation before surface creation
  - Added retry logic with better timeout parameters (30 attempts × 200ms)
  - Added surface readiness checks in Activities before Vulkan initialization
  - Increased Kotlin-side timeouts to 10s (Triangle, Force Graph) and kept 12s (Embedding Galaxy)
  - See `docs/ANDROID_VULKAN_FIXES.md` for details

## Asset Pipeline
- [ ] Replace the placeholder logic in `CompressedTextureLoader` for KTX2/DDS/PVR/ASTC so headers, mip levels, and compression formats are parsed correctly instead of using hard-coded defaults.
- [ ] Add integration tests for the compressed texture loader (happy-path plus malformed payloads) that cover KTX2, DDS, PVR, and ASTC inputs.

## Native Target
- [ ] Provide real native implementations (or disable the target) for `SurfaceFactory`, `RenderSurface`, `RendererFactory`, `Renderer`, and `BackendNegotiator`; right now they throw or return stubs which blocks native builds.
- [ ] Implement a native `PerformanceMonitor` counterpart that reports real metrics rather than returning a stubbed monitor.

## Web XR

- [ ] Replace the stubbed WebXR bridge in `src/jsMain/kotlin/io/materia/xr/XRPlatformStubs.kt` with
  actual feature detection and permission handling to match the expect API.

## Validation & Coverage
- [ ] Expand loader regression coverage (Collada/FBX/Draco/KTX2/EXR) with real-world fixtures and malformed cases as outlined in the previous checkpoint summary.
- [ ] Enable end-to-end asset validation that wires the loaders into `AdvancedAssetLoader` once the above TODOs are resolved.
