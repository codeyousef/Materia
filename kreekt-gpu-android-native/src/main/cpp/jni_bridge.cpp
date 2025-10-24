#include "vulkan_bridge.hpp"

#include <jni.h>

namespace {

    inline const char *getCString(JNIEnv *env, jstring str) {
        return env->GetStringUTFChars(str, nullptr);
    }

    inline void releaseCString(JNIEnv *env, jstring str, const char *cstr) {
        env->ReleaseStringUTFChars(str, cstr);
    }

} // namespace

extern "C" {

JNIEXPORT jlong
JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkInit(
        JNIEnv *env,
        jclass,
        jstring appName,
        jboolean enableValidation
) {
    const char *nameCStr = getCString(env, appName);
    std::uint64_t id = kreekt::vk::createInstance(nameCStr, enableValidation == JNI_TRUE);
    releaseCString(env, appName, nameCStr);
    return static_cast<jlong>(id);
}

JNIEXPORT jlong
JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCreateSurface(
        JNIEnv *env,
        jclass,
        jlong instanceId,
        jobject surface
) {
    return static_cast<jlong>(kreekt::vk::createSurface(static_cast<std::uint64_t>(instanceId), env,
                                                        surface));
}

JNIEXPORT jlong
JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCreateDevice(
        JNIEnv *,
        jclass,
        jlong instanceId
) {
    return static_cast<jlong>(kreekt::vk::createDevice(static_cast<std::uint64_t>(instanceId)));
}

JNIEXPORT jlong
JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCreateSwapchain(
        JNIEnv *,
        jclass,
        jlong deviceId,
        jlong surfaceId,
        jint width,
        jint height
) {
    return static_cast<jlong>(
            kreekt::vk::createSwapchain(
                    static_cast<std::uint64_t>(deviceId),
                    static_cast<std::uint64_t>(surfaceId),
                    static_cast<std::uint32_t>(width),
                    static_cast<std::uint32_t>(height)
            )
    );
}

JNIEXPORT jboolean
JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkDrawFrame(
        JNIEnv *,
        jclass,
        jlong deviceId,
        jlong swapchainId,
        jfloat clearR,
        jfloat clearG,
        jfloat clearB,
        jfloat clearA
) {
    const bool result = kreekt::vk::drawFrame(
            static_cast<std::uint64_t>(deviceId),
            static_cast<std::uint64_t>(swapchainId),
            clearR,
            clearG,
            clearB,
            clearA
    );
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkResizeSwapchain(
        JNIEnv * ,
jclass ,
jlong deviceId,
        jlong
surfaceId ,
jlong swapchainId,
        jint
width ,
jint height
) {
kreekt::vk::resizeSwapchain (
static_cast <std::uint64_t>(deviceId),
static_cast <std::uint64_t>(surfaceId),
static_cast <std::uint64_t>(swapchainId),
static_cast <std::uint32_t>(width),
static_cast <std::uint32_t>(height)
) ;
}

JNIEXPORT void JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkDestroySwapchain(
        JNIEnv
*,
jclass,
jlong deviceId,
        jlong
swapchainId
) {
kreekt::vk::destroySwapchain(
static_cast
<std::uint64_t>(deviceId),
static_cast
<std::uint64_t>(swapchainId)
);
}

JNIEXPORT void JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkDestroySurface(
        JNIEnv
*,
jclass,
jlong instanceId,
        jlong
surfaceId
) {
kreekt::vk::destroySurface(
static_cast
<std::uint64_t>(instanceId),
static_cast
<std::uint64_t>(surfaceId)
);
}

JNIEXPORT void JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkDestroyDevice(
        JNIEnv
*,
jclass,
jlong instanceId
) {
kreekt::vk::destroyDevice(static_cast
<std::uint64_t>(instanceId)
);
}

JNIEXPORT void JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkDestroyInstance(
        JNIEnv
*,
jclass,
jlong instanceId
) {
kreekt::vk::destroyInstance(static_cast
<std::uint64_t>(instanceId)
);
}

JNIEXPORT void JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkDestroyAll(
        JNIEnv
*,
jclass
) {
kreekt::vk::destroyAll();

}

} // extern "C"
