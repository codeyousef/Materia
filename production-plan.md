# MVP Production Readiness Plan

## Goal
Replace all JVM Vulkan placeholders so desktop examples render real frames through the shared GPU abstraction, mirroring the newly functional JS/WebGPU path.

## Step Breakdown

1. **Audit Vulkan Renderer Capabilities**
    - Catalogue the existing low-level Vulkan utilities (
      `src/jvmMain/kotlin/io/materia/renderer/vulkan`) and document reusable pieces for command
      buffers, swapchains, and pipeline setup.
     - `VulkanRenderer` already owns swapchain creation, command pools/buffers, render pass begin/end helpers, and per-frame loop logic.
     - `VulkanEnvironmentManager`, `VulkanSwapchain`, and `VulkanSurface` contain window/surface bootstrap, command pool allocation, and single-use command submission utilities.
     - `VulkanPipeline`, `VulkanBufferManager`, and `VulkanMaterialTextureManager` manage pipeline creation and resource uploads (texture staging, descriptor sets) outside the new abstraction.
   - Identify shader sources/compilation gaps (WGSL → SPIR-V) and decide compilation strategy.
     - Only `.spv` artifacts exist under `src/jvmMain/resources/shaders/`; WGSL isn’t auto-translated today.
     - Need build-time or precompiled SPIR-V path for `GpuShaderModule` to consume.

2. **Expand `io.materia.renderer.gpu` JVM Actuals**
   - Implement `GpuDevice.createCommandEncoder`, `createTexture`, `createSampler`, `createBindGroup*`, `createPipelineLayout`.
   - Implement `GpuQueue.submit` with real `VkSubmitInfo`.
   - Introduce Vulkan-backed `GpuCommandEncoder/Buffer/RenderPassEncoder` and wire render-pass begin/end.
   - Prerequisites identified:
     - Centralise command-pool allocation on `GpuDevice` (reuse `VulkanRenderer` command-pool helpers or extract them into a shared utility).
     - Expose swapchain/surface attachments through reusable render-pass descriptors so the encoder can target the current framebuffer.
     - Map WebGPU-style bind group abstractions to Vulkan descriptor set layouts (can leverage `VulkanMaterialTextureManager` patterns).

3. **Bridge `materia-gpu` JVM Actuals**
   - Update `GpuSurface` to wrap the renderer swapchain (acquire/present real images).
   - Ensure `GpuBuffer`, `GpuTexture`, and `GpuCommandEncoder` attach renderer handles and expose lifecycles.

4. **Shader Pipeline Integration**
   - Establish WGSL → SPIR-V compilation using the **Tint** CLI and load SPIR-V for Vulkan `GpuShaderModule`.
     - Create a Gradle task (e.g., `compileShaders`) that scans `src/commonMain/resources/shaders/*.wgsl`, invokes `tint --format spirv ...`, and writes outputs into `src/jvmMain/resources/shaders/*.spv`.
     - Wire the task into JVM resource processing (`jvmProcessResources`) so SPIR-V artifacts stay up-to-date during builds.
     - Provide clear error messaging when Tint is not installed and reference installation docs.
   - Verify pipeline creation using the generated SPIR-V for the triangle shaders.

5. **Example Integration & Validation**
   - Replace the placeholder logging in `examples/triangle` JVM main with actual frame submission.
   - Run the triangle example to confirm rendering, then propagate fixes to other desktop demos (e.g., VoxelCraft).

6. **Cleanup & Documentation**
   - Remove outdated TODO/placeholder text in docs and code.
   - Update `README.md` / `mvp-plan.md` with new build/run instructions and validations.

## Validation Checklist

- `./gradlew :materia-gpu:compileKotlinJs`, `:examples:triangle:compileKotlinJs`, and JVM builds
  succeed.
- `./gradlew :examples:triangle:run` renders via Vulkan without placeholder logs.
- No remaining placeholder references in core modules or examples.
- Documentation reflects the new cross-platform state.
