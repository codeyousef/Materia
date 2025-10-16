# Remaining Work

- Integrate WebGPU renderer with the new prefiltered cube mip-chain so roughness picks correct mip levels.
- Extend multi-platform tests (JVM/native) to exercise the updated IBL paths.
- Profile the CPU IBL convolution; consider offloading to GPU compute once available.
- Verify WebGPU stats reporting inside a live scene and update docs with the new metrics.
