Got it — here’s a concrete decision + wiring plan to unblock Android Vulkan for the MVP.

# Decision (approved path)

**Add a tiny NDK C++ module and JNI shim** that exposes just the Vulkan bits we need (
instance/device/swapchain/sync/draw) to the Kotlin `:materia-gpu` Android actuals. We’ll **not**
chase
a general JVM binding; Android’s Vulkan is native-only, and LWJGL doesn’t apply here. We’ll keep
shaders **single-source WGSL** and **compile to SPIR-V at build time** so Android only loads`.spv`
from assets — matching our “WebGPU semantics everywhere; Vulkan adapts underneath” rule .

# Minimal stack (vendor OK)

* **volk** – function loader (header-only).
* **vk-bootstrap** – instance/device selection, queue setup (header-only).
* **VMA** – GPU memory allocator.
* **Tint (host-side Gradle task)** – WGSL → SPIR-V at build; package `.spv` in
  `examples/triangle-android/src/main/assets/shaders/`.

All are permissive (MIT/Apache-2). We vendor them into `third_party/` for reproducible builds.

# Module layout

```
:materia-gpu/
  src/commonMain/...           # WebGPU-like API (expect)
  src/androidMain/...          # Android actuals -> JNI calls

:materia-gpu-android-native/    # NEW (CMake + NDK)
  CMakeLists.txt
  include/materia_vk.hpp
  src/main/cpp/vulkan_bridge.cpp  # Vulkan implementation + JNI entrypoints
  third_party/{volk,vk_bootstrap,vma}/

examples/triangle-android/
  src/main/java/...               # Activity + SurfaceView
  src/main/assets/shaders/*.spv
```

_Pseudo-only: `FrameTargets` is a small structure carrying the pipeline/view/swapchain identifiers
returned from the Kotlin Vulkan bridge. See `GpuAndroidBackend.kt` for the concrete implementation._

# Task Tracker (WIP)

- [x] Align native render-pass encoding with WebGPU ordering (remove pipeline dependency from
  `vkCommandEncoderBeginRenderPass`).
- [x] Load Android shader assets (`.spv`) via `AndroidVulkanAssets` helper.
- [x] Replace `GpuStubs.kt` with Vulkan-backed actuals for instance/adapter/device creation.
- [x] Implement JNI-backed resource creation (buffers, textures, samplers, bind group layouts &
  groups).
- [x] Bridge command encoder, render pass, and queue submission/presentation through JNI.
- [x] Hook EngineRenderer/Triangle Android activity into the Vulkan backend (initialise assets,
  manage surface lifecycle).
- [x] Ensure Gradle pipeline packages SPIR-V assets for Android builds.

# Android surface path (what we wire)

1. `SurfaceView` → `holder.surface`
2. `ANativeWindow* win = ANativeWindow_fromSurface(env, surfaceObj);`
3. `vkCreateAndroidSurfaceKHR(instance, &VkAndroidSurfaceCreateInfoKHR{ .window = win }, …)`
4. Enable `VK_KHR_surface`, `VK_KHR_android_surface`; choose present queue; create swapchain.
5. Render loop uses semaphores/fences; recreate swapchain on size/orientation changes.

# Kotlin ↔ JNI interface (thin, purpose-built)

**Kotlin actuals (androidMain)** call into JNI; **no big object graph across the boundary, just
IDs/handles**.

```kotlin
// androidMain (actuals)
internal external fun vkInit(appName: String, enableValidation: Boolean): Long
internal external fun vkCreateDevice(instanceId: Long): Long
internal external fun vkCreateSurface(instanceId: Long, surface: android.view.Surface): Long
internal external fun vkCreateSwapchain(
    instanceId: Long,
    deviceId: Long,
    surfaceId: Long,
    width: Int,
    height: Int
): Long
internal external fun vkSwapchainAcquireFrame(
    instanceId: Long,
    deviceId: Long,
    surfaceId: Long,
    swapchainId: Long
): LongArray
internal external fun vkCreateCommandEncoder(instanceId: Long, deviceId: Long): Long
internal external fun vkCommandEncoderBeginRenderPass(
    instanceId: Long,
    deviceId: Long,
    encoderId: Long,
    textureViewId: Long,
    isSwapchain: Boolean,
    swapchainImageIndex: Int,
    clearR: Float,
    clearG: Float,
    clearB: Float,
    clearA: Float
): Long
internal external fun vkCommandEncoderSetPipeline(
    instanceId: Long,
    deviceId: Long,
    encoderId: Long,
    pipelineId: Long
)
internal external fun vkCommandEncoderEndRenderPass(
    instanceId: Long,
    deviceId: Long,
    renderPassEncoderId: Long
)
internal external fun vkCommandEncoderSetBindGroup(
    instanceId: Long,
    deviceId: Long,
    encoderId: Long,
    index: Int,
    bindGroupId: Long
)
internal external fun vkCommandEncoderDraw(
    instanceId: Long,
    deviceId: Long,
    encoderId: Long,
    vertexCount: Int,
    instanceCount: Int,
    firstVertex: Int,
    firstInstance: Int
)
internal external fun vkCommandEncoderFinish(
    instanceId: Long,
    deviceId: Long,
    encoderId: Long
): Long
internal external fun vkQueueSubmit(
    instanceId: Long,
    deviceId: Long,
    commandBufferId: Long,
    hasSwapchain: Boolean,
    imageIndex: Int
)
internal external fun vkSwapchainPresentFrame(
    instanceId: Long,
    deviceId: Long,
    surfaceId: Long,
    swapchainId: Long,
    commandBufferId: Long,
    imageIndex: Int
)
internal external fun vkDestroyCommandBuffer(instanceId: Long, deviceId: Long, commandBufferId: Long)
internal external fun vkDestroySwapchain(instanceId: Long, deviceId: Long, surfaceId: Long, swapchainId: Long)
internal external fun vkDestroySurface(instanceId: Long, surfaceId: Long)
internal external fun vkDestroyDevice(instanceId: Long, deviceId: Long)
internal external fun vkDestroyInstance(instanceId: Long)
```

```kotlin
// expect/actual sketch
expect class GpuDevice { /* ... */ }
actual class GpuDevice {
    private val instance = vkInit("Materia", BuildConfig.DEBUG)
    private val device = vkCreateDevice(instance)

    fun recordAndSubmit(frame: FrameTargets) {
        val encoderId = vkCreateCommandEncoder(instance, device)
        val passId = vkCommandEncoderBeginRenderPass(
            instance,
            device,
            encoderId,
            frame.colorViewId,
            frame.isSwapchain,
            frame.imageIndex,
            frame.clear[0], frame.clear[1], frame.clear[2], frame.clear[3]
        )
        vkCommandEncoderSetPipeline(instance, device, encoderId, frame.pipelineId)
        vkCommandEncoderSetBindGroup(instance, device, encoderId, 0, frame.bindGroupId)
        vkCommandEncoderDraw(instance, device, encoderId, vertexCount = 3, instanceCount = 1, firstVertex = 0, firstInstance = 0)
        vkCommandEncoderEndRenderPass(instance, device, passId)

        val commandBufferId = vkCommandEncoderFinish(instance, device, encoderId)
        vkQueueSubmit(instance, device, commandBufferId, frame.isSwapchain, frame.imageIndex)
        if (frame.isSwapchain) {
            vkSwapchainPresentFrame(instance, device, frame.surfaceId, frame.swapchainId, commandBufferId, frame.imageIndex)
        }
        vkDestroyCommandBuffer(instance, device, commandBufferId)
    }
}
```

**JNI side (C++)** keeps real Vulkan handles in a small registry keyed by `jlong` IDs.

```cpp
// vulkan_bridge.cpp (snippets)
extern "C" JNIEXPORT jlong JNICALL
Java_io_materia_gpu_bridge_VulkanBridge_vkCommandEncoderBeginRenderPass(
        JNIEnv*, jclass,
        jlong instanceId,
        jlong deviceId,
        jlong encoderId,
        jlong textureViewId,
        jboolean isSwapchain,
        jint imageIndex,
        jfloat clearR,
        jfloat clearG,
        jfloat clearB,
        jfloat clearA) {
    VulkanInstance* instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice* device = requireDevice(*instance, static_cast<Id>(deviceId));
    VulkanCommandEncoder* encoder = requireCommandEncoder(*device, static_cast<Id>(encoderId));
    VulkanTextureView* view = requireTextureView(*device, static_cast<Id>(textureViewId));
    VulkanSwapchain* swapchain = nullptr;
    if (isSwapchain == JNI_TRUE) {
        for (auto &surfaceEntry : instance->surfaces) {
            for (auto &swapEntry : surfaceEntry.second->swapchains) {
                if (std::find(swapEntry.second->textureViewIds.begin(),
                              swapEntry.second->textureViewIds.end(),
                              static_cast<Id>(textureViewId)) != swapEntry.second->textureViewIds.end()) {
                    swapchain = swapEntry.second.get();
                    break;
                }
            }
            if (swapchain) break;
        }
    }
    return static_cast<jlong>(beginRenderPassInternal(
        *device,
        *encoder,
        *view,
        swapchain,
        static_cast<uint32_t>(imageIndex),
        clearR, clearG, clearB, clearA));
}

extern "C" JNIEXPORT void JNICALL
Java_io_materia_gpu_bridge_VulkanBridge_vkQueueSubmit(
        JNIEnv*, jclass,
        jlong instanceId,
        jlong deviceId,
        jlong commandBufferId,
        jboolean hasSwapchain,
        jint imageIndex) {
    VulkanInstance* instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice* device = requireDevice(*instance, static_cast<Id>(deviceId));
    VulkanCommandBufferWrapper* commandBuffer = requireCommandBuffer(*device, static_cast<Id>(commandBufferId));
    submitCommandBufferInternal(*device, *commandBuffer,
        hasSwapchain == JNI_TRUE ? commandBuffer->swapchain : nullptr);
    if (hasSwapchain == JNI_TRUE) {
        presentFrameInternal(*device, *commandBuffer->swapchain, *commandBuffer, static_cast<uint32_t>(imageIndex));
    }
}
```

# Build plumbing

**Gradle (root + Triangle Android app)**

```kotlin
// Root build.gradle.kts
val syncAndroidShaders = tasks.register<Sync>("syncAndroidShaders") {
    group = "build"
    description = "Copy compiled SPIR-V shaders into the Triangle Android assets"

    dependsOn(compileShaders)
    from(layout.projectDirectory.dir("src/jvmMain/resources/shaders")) { include("**/*.spv") }
    into(layout.projectDirectory.dir("examples/triangle-android/src/main/assets/shaders"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// examples/triangle-android/build.gradle.kts
android {
    packaging { jniLibs.keepDebugSymbols += "**/*.so" }
}

dependencies {
    implementation(project(":materia-gpu-android-native"))
}

tasks.named("preBuild") {
    dependsOn(rootProject.tasks.named("syncAndroidShaders"))
}
```

**CMakeLists.txt (core bits)**

```cmake
cmake_minimum_required(VERSION 3.22)
project(materia_vk LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 20)

add_library(materia_vk SHARED
        src/main/cpp/vulkan_bridge.cpp
        third_party/volk/volk.c
)

target_include_directories(materia_vk PRIVATE
        src/main/cpp/include
        third_party/volk
        third_party/vk_bootstrap
        third_party/vma
)

find_library(log-lib log)
find_library(android-lib android)
find_library(vulkan-lib vulkan)

target_link_libraries(materia_vk
        PRIVATE
        ${vulkan-lib}
        ${android-lib}
        ${log-lib}
)

add_definitions(-DVK_USE_PLATFORM_ANDROID_KHR)
```

# Shader toolchain on Android

* **At build:** The `syncAndroidShaders` Gradle task depends on Tint compilation and copies
  `src/jvmMain/resources/shaders/*.spv` into
  `examples/triangle-android/src/main/assets/shaders/`.
* **At runtime:** Kotlin loads `assets/shaders/unlit_color.vert.spv` (etc.) via
  `AndroidVulkanAssets` and forwards the bytes to `vkCreateShaderModule`.
  This matches the “WGSL as single source; compile to SPIR-V for Vulkan/MVK” rule in the MVP plan.

# What you can do right now

* Verify the end-to-end flow by running `./gradlew :examples:triangle-android:assembleDebug` once the
  Android SDK/NDK is configured locally.
* Pass `-PvkEnableValidation=true` when you need verbose Vulkan validation output in logcat; omit it for
  release builds.
* Watch logcat with `VK_LAYER_KHRONOS_validation` enabled while rotating/resizing the surface to
  validate swapchain recreation.
* Remaining engineering TODOs are captured below (validation harness, debug-utils toggle).

# Alternatives (if you prefer)

* **Third-party wrapper:** If you really want a prebuilt wrapper, we could vendor **Vulkan-Hpp** and
  expose a slightly nicer C++ interface, but the JNI surface remains similar.
* **Runtime WGSL→SPIR-V on Android:** Possible by bundling Tint/Naga native, but heavier; for MVP we
  keep it **build-time**.

If this looks good, I’ll reflect it in the Android target bootstrap and swap the Android stubs in
`:materia-gpu` to the JNI calls above.

# Command encoder bridge (implemented)

The JNI layer now records command buffers for the Triangle renderer end-to-end. Render passes begin
as soon as Kotlin calls `beginRenderPass`, mirroring WebGPU ordering, and pipelines are bound
afterwards via `vkCommandEncoderSetPipeline`.

- Render passes are cached per `(format, finalLayout)` on the native device so both pipeline
  creation and command recording share compatible `VkRenderPass` handles.
- Swapchain framebuffers are reused; offscreen targets allocate transient framebuffers for the
  duration of a render pass.
- Command submission goes through `vkQueueSubmit` + `vkSwapchainPresentFrame`. When the device
  advertises `VK_KHR_timeline_semaphore`, submissions also signal a timeline semaphore and frame
  acquisition waits on the last submitted value. Devices without timeline support continue to use
  the per-swapchain fence path.

# Kotlin actual wiring checklist

- ✅ `GpuAndroidBackend.kt` now delegates all device, swapchain, and command encoder calls to JNI and
  keeps only opaque IDs on the Kotlin side.
- ✅ `AndroidVulkanAssets` initialises once per `Context`, caches the `AssetManager`, and serves
  `.spv` blobs to shader module creation.
- ✅ Surface lifecycle is managed via `GpuSurface` + `TriangleActivity`, triggering swapchain rebuilds
  on resize/destroy and clearing native resources.
- ✅ JNI entrypoints validate handle lookups and throw `IllegalStateException` when Kotlin supplies
  missing or stale IDs.
- ✅ Synchronisation prefers timeline semaphores when available; the legacy fence path remains as a
  fallback for older drivers.

# Android app lifecycle integration

- `VulkanBridge` loads `libmateria_vk.so`, and `TriangleActivity` initialises
  `AndroidVulkanAssets` before the renderer boots.
- Validation layers (when enabled via `-PvkEnableValidation=true`) also hook `VK_EXT_debug_utils` 
  so warnings/errors are forwarded to logcat.
- `SurfaceFactory` hands a `SurfaceHolder` to `GpuSurface`, which creates/destroys swapchains via JNI
  whenever `configure`/`resize` runs.
- `Choreographer` drives the render loop, recording Vulkan command buffers through the bridge and
  presenting via `vkQueueSubmit` + `vkSwapchainPresentFrame`.
- Rotation/size changes trigger `GpuSurface.resize`, which tears down and recreates the swapchain
  using the updated dimensions.

# Build & asset pipeline notes

- `syncAndroidShaders` copies compiled SPIR-V blobs into the Triangle Android app before every
  `preBuild`.
- The native module is shared across the project; the example app depends on
  `:materia-gpu-android-native` so the packaged `.so` is merged automatically.
- CI should run `./gradlew :examples:triangle-android:assembleDebug` to cover the native build once
  the Android SDK/NDK has been provisioned (e.g., `sdkmanager "ndk;26.1.10909125"`).
