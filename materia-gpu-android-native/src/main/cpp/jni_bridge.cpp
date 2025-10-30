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
Java_io_materia_gpu_bridge_VulkanBridge_vkInit(
        JNIEnv *env,
        jclass,
        jstring appName,
        jboolean enableValidation
) {
    const char *nameCStr = getCString(env, appName);
    std::uint64_t id = materia::vk::createInstance(nameCStr, enableValidation == JNI_TRUE);
    releaseCString(env, appName, nameCStr);
    return static_cast<jlong>(id);
}

JNIEXPORT jlong
JNICALL
Java_io_materia_gpu_bridge_VulkanBridge_vkCreateSurface(
        JNIEnv *env,
        jclass,
        jlong instanceId,
        jobject surface
) {
    return static_cast<jlong>(materia::vk::createSurface(static_cast<std::uint64_t>(instanceId),
                                                         env,
                                                         surface));
}

JNIEXPORT jlong
JNICALL
Java_io_materia_gpu_bridge_VulkanBridge_vkCreateDevice(
        JNIEnv *,
        jclass,
        jlong instanceId
) {
    return static_cast<jlong>(materia::vk::createDevice(static_cast<std::uint64_t>(instanceId)));
}

JNIEXPORT jlong
JNICALL
Java_io_materia_gpu_bridge_VulkanBridge_vkCreateSwapchain(
        JNIEnv *,
        jclass,
        jlong deviceId,
        jlong surfaceId,
        jint width,
        jint height
) {
    return static_cast<jlong>(
            materia::vk::createSwapchain(
                    static_cast<std::uint64_t>(deviceId),
                    static_cast<std::uint64_t>(surfaceId),
                    static_cast<std::uint32_t>(width),
                    static_cast<std::uint32_t>(height)
            )
    );
}

JNIEXPORT jboolean
JNICALL
Java_io_materia_gpu_bridge_VulkanBridge_vkDrawFrame(
        JNIEnv *,
        jclass,
        jlong deviceId,
        jlong swapchainId,
        jfloat clearR,
        jfloat clearG,
        jfloat clearB,
        jfloat clearA
) {
    const bool result = materia::vk::drawFrame(
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
Java_io_materia_gpu_bridge_VulkanBridge_vkResizeSwapchain(
        JNIEnv *,
        jclass,
        jlong deviceId,
        jlong
        surfaceId,
        jlong swapchainId,
        jint
        width,
        jint height
) {
    materia::vk::resizeSwapchain(
            static_cast <std::uint64_t>(deviceId),
            static_cast <std::uint64_t>(surfaceId),
            static_cast <std::uint64_t>(swapchainId),
            static_cast <std::uint32_t>(width),
            static_cast <std::uint32_t>(height)
    );
}

JNIEXPORT void JNICALL
Java_io_materia_gpu_bridge_VulkanBridge_vkDestroySwapchain(
        JNIEnv
        *,
        jclass,
        jlong deviceId,
        jlong
        swapchainId
) {
    materia::vk::destroySwapchain(
            static_cast
                    <std::uint64_t>(deviceId),
            static_cast
                    <std::uint64_t>(swapchainId)
    );
}

JNIEXPORT void JNICALL
Java_io_materia_gpu_bridge_VulkanBridge_vkDestroySurface(
        JNIEnv
        *,
        jclass,
        jlong instanceId,
        jlong
        surfaceId
) {
    materia::vk::destroySurface(
            static_cast
                    <std::uint64_t>(instanceId),
            static_cast
                    <std::uint64_t>(surfaceId)
    );
}

JNIEXPORT void JNICALL
Java_io_materia_gpu_bridge_VulkanBridge_vkDestroyDevice(
        JNIEnv
        *,
        jclass,
        jlong instanceId
) {
    materia::vk::destroyDevice(static_cast
                                       <std::uint64_t>(instanceId)
    );
}

JNIEXPORT void JNICALL
Java_io_materia_gpu_bridge_VulkanBridge_vkDestroyInstance(
        JNIEnv
        *,
        jclass,
        jlong instanceId
) {
    materia::vk::destroyInstance(static_cast
                                         <std::uint64_t>(instanceId)
    );
}

JNIEXPORT void JNICALL
Java_io_materia_gpu_bridge_VulkanBridge_vkDestroyAll(
        JNIEnv
        *,
        jclass
) {
    materia::vk::destroyAll();

}

} // extern "C"
