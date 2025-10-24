Got it — here’s a concrete decision + wiring plan to unblock Android Vulkan for the MVP.

# Decision (approved path)

**Add a tiny NDK C++ module and JNI shim** that exposes just the Vulkan bits we need (
instance/device/swapchain/sync/draw) to the Kotlin `:kreekt-gpu` Android actuals. We’ll **not**chase
a general JVM binding; Android’s Vulkan is native-only, and LWJGL doesn’t apply here. We’ll keep
shaders **single-source WGSL** and **compile to SPIR-V at build time** so Android only loads`.spv`
from assets — matching our “WebGPU semantics everywhere; Vulkan adapts underneath” rule .

# Minimal stack (vendor OK)

* **volk** – function loader (header-only).
* **vk-bootstrap** – instance/device selection, queue setup (header-only).
* **VMA** – GPU memory allocator.
* **Tint (host-side Gradle task)** – WGSL → SPIR-V at build; package `.spv` in
  `androidApp/src/main/assets/shaders/`.

All are permissive (MIT/Apache-2). We vendor them into `third_party/` for reproducible builds.

# Module layout

```
:kreekt-gpu/
  src/commonMain/...           # WebGPU-like API (expect)
  src/androidMain/...          # Android actuals -> JNI calls

:kreekt-gpu-android-native/    # NEW (CMake + NDK)
  CMakeLists.txt
  include/kreekt_vk.hpp
  src/kreekt_vk.cpp            # Vulkan impl
  src/jni_bridge.cpp           # JNI entrypoints
  third_party/{volk,vk_bootstrap,vma}/

androidApp/
  src/main/java/...            # Activity + SurfaceView
  src/main/assets/shaders/*.spv
```

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
internal external fun vkInit(appName: String, validation: Boolean): Long /* VkInstance handle id */
internal external fun vkCreateDevice(instanceId: Long): Long
internal external fun vkCreateSurface(instanceId: Long, surface: android.view.Surface): Long
internal external fun vkCreateSwapchain(
    deviceId: Long,
    surfaceId: Long,
    width: Int,
    height: Int
): Long
internal external fun vkDrawFrame(deviceId: Long, swapchainId: Long): Boolean
internal external fun vkResizeSwapchain(deviceId: Long, surfaceId: Long, width: Int, height: Int)
internal external fun vkDestroyAll()
```

```kotlin
// expect/actual sketch
expect class GpuDevice { /* ... */ }
actual class GpuDevice {
    private val instance = vkInit("KreeKt", BuildConfig.DEBUG)
    private val device = vkCreateDevice(instance)
    fun createSurface(surface: Surface): GpuSurface {
        val sid = vkCreateSurface(instance, surface)
        // ...
    }
    fun draw() = vkDrawFrame(device, swapchain)
}
```

**JNI side (C++)** keeps real Vulkan handles in a small registry keyed by `jlong` IDs.

```cpp
// jni_bridge.cpp (snippets)
extern "C" JNIEXPORT jlong JNICALL
Java_io_kreekt_gpu_native_VkBridge_vkInit(JNIEnv* env, jclass, jstring appName, jboolean validation) {
    // volkInitialize(); vk-bootstrap to create instance with VK_KHR_android_surface
    // store VkInstance in registry; return id
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_kreekt_gpu_native_VkBridge_vkCreateSurface(JNIEnv* env, jclass, jlong instanceId, jobject surfaceObj) {
    ANativeWindow* win = ANativeWindow_fromSurface(env, surfaceObj);
    // vkCreateAndroidSurfaceKHR(...win...); store VkSurfaceKHR; return id
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_kreekt_gpu_native_VkBridge_vkDrawFrame(JNIEnv*, jclass, jlong deviceId, jlong swapchainId) {
    // acquire -> record minimal pass -> submit -> present
    return JNI_TRUE;
}
```

# Build plumbing

**Gradle (Android app `build.gradle.kts`)**

```kotlin
android {
    defaultConfig {
        externalNativeBuild { cmake { cppFlags("-std=c++20") } }
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    externalNativeBuild { cmake { path = file("../kreekt-gpu-android-native/CMakeLists.txt") } }
    packaging {
        jniLibs.keepDebugSymbols += "**/*.so"
        resources.pickFirsts += "*/shaders/*.spv"
    }
}
```

**CMakeLists.txt (core bits)**

```cmake
cmake_minimum_required(VERSION 3.22)
project(kreekt_vk LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 20)

add_library(kreekt_vk SHARED
        src/kreekt_vk.cpp
        src/jni_bridge.cpp
        third_party/volk/volk.c
)

target_include_directories(kreekt_vk PRIVATE
        include
        third_party/volk
        third_party/vk_bootstrap
        third_party/vma
        ${ANDROID_NDK}/sources/android/native_app_glue
)

find_library(log-lib log)
find_library(android-lib android)
# Link Vulkan loader from NDK
find_library(vulkan-lib vulkan)
target_link_libraries(kreekt_vk PRIVATE ${vulkan-lib} ${android-lib} ${log-lib})
add_definitions(-DVK_USE_PLATFORM_ANDROID_KHR)
```

# Shader toolchain on Android

* **At build:** Gradle task runs Tint on `resources/shaders/*.wgsl` → emits `.spv` into
  `androidApp/src/main/assets/shaders/`.
* **At runtime:** Kotlin loads `assets/shaders/unlit_color.vert.spv` (etc.), passes bytes to JNI →
  `vkCreateShaderModule`.
  This matches the “WGSL as single source; compile to SPIR-V for Vulkan/MVK” rule in the MVP plan .

# What you can do right now

* Proceed with this plan. Create `:kreekt-gpu-android-native` and wire the **five** JNI calls above.
* Use `vk-bootstrap` for instance/device + queue selection and `volk` for dispatch; you’ll be at
  first pixels fast.
* Start with **Triangle** path only (clear + draw 3 verts) so `:examples:triangle:installDebug`
  yields on-device pixels, which is one of our quickstart gates .

# Alternatives (if you prefer)

* **Third-party wrapper:** If you really want a prebuilt wrapper, we could vendor **Vulkan-Hpp** and
  expose a slightly nicer C++ interface, but the JNI surface remains similar.
* **Runtime WGSL→SPIR-V on Android:** Possible by bundling Tint/Naga native, but heavier; for MVP we
  keep it **build-time**.

If this looks good, I’ll reflect it in the Android target bootstrap and swap the Android stubs in
`:kreekt-gpu` to the JNI calls above.
