#pragma once

#include <cstdint>
#include <jni.h>

namespace materia::vk {

    std::uint64_t createInstance(const char *appName, bool enableValidation);

    std::uint64_t createSurface(std::uint64_t instanceId, JNIEnv *env, jobject surface);

    std::uint64_t createDevice(std::uint64_t instanceId);

    std::uint64_t
    createSwapchain(std::uint64_t instanceId, std::uint64_t deviceId, std::uint64_t surfaceId,
                    std::uint32_t width, std::uint32_t height);

    void destroySwapchain(std::uint64_t instanceId, std::uint64_t deviceId, std::uint64_t surfaceId,
                          std::uint64_t swapchainId);

    void destroySurface(std::uint64_t instanceId, std::uint64_t surfaceId);

    void destroyDevice(std::uint64_t instanceId, std::uint64_t deviceId);

    void destroyInstance(std::uint64_t instanceId);

    void destroyAll();

} // namespace materia::vk
