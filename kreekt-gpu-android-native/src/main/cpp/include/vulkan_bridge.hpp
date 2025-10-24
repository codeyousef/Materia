/*
 * Native Vulkan bridge for Android.
 *
 * Provides a small C++ API that is invoked from JNI to manage Vulkan instance,
 * device, surface, swapchain, and frame rendering required by the Android
 * `:kreekt-gpu` actuals.
 */

#pragma once

#include <cstdint>

struct JNIEnv;

class _jobject;

using jobject = _jobject *;

namespace kreekt::vk {

    std::uint64_t createInstance(const char *appName, bool enableValidation);

    std::uint64_t createSurface(std::uint64_t instanceId, JNIEnv *env, jobject surface);

    std::uint64_t createDevice(std::uint64_t instanceId);

    std::uint64_t createSwapchain(
            std::uint64_t deviceId,
            std::uint64_t surfaceId,
            std::uint32_t width,
            std::uint32_t height);

    bool drawFrame(
            std::uint64_t deviceId,
            std::uint64_t swapchainId,
            float clearR,
            float clearG,
            float clearB,
            float clearA);

    void resizeSwapchain(
            std::uint64_t deviceId,
            std::uint64_t surfaceId,
            std::uint64_t swapchainId,
            std::uint32_t width,
            std::uint32_t height);

    void destroySwapchain(std::uint64_t deviceId, std::uint64_t swapchainId);

    void destroySurface(std::uint64_t instanceId, std::uint64_t surfaceId);

    void destroyDevice(std::uint64_t instanceId);

    void destroyInstance(std::uint64_t instanceId);

    void destroyAll();

} // namespace kreekt::vk
