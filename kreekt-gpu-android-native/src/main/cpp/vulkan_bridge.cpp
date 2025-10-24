#include "vulkan_bridge.hpp"

#include <android/log.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>

#include <algorithm>
#include <array>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <inttypes.h>
#include <memory>
#include <mutex>
#include <optional>
#include <stdexcept>
#include <unordered_map>
#include <utility>
#include <vector>

namespace {

#define VK_LOG_TAG "KreeKtVk"

#define VK_LOG_INFO(...) __android_log_print(ANDROID_LOG_INFO, VK_LOG_TAG, __VA_ARGS__)
#define VK_LOG_ERROR(...) __android_log_print(ANDROID_LOG_ERROR, VK_LOG_TAG, __VA_ARGS__)

    using Id = std::uint64_t;

    std::atomic <Id> g_nextId{1};
    std::mutex g_registryMutex;

    template<typename T>
    Id makeId(T &&value, std::unordered_map <Id, std::unique_ptr<T>> &registry) = delete;

    template<typename T>
    Id registerObject(std::unique_ptr <T> object,
                      std::unordered_map <Id, std::unique_ptr<T>> &registry) {
        const Id id = g_nextId.fetch_add(1, std::memory_order_relaxed);
        registry.emplace(id, std::move(object));
        return id;
    }

    template<typename T>
    T *lookupObject(Id id, std::unordered_map <Id, std::unique_ptr<T>> &registry) {
        auto it = registry.find(id);
        return it == registry.end() ? nullptr : it->second.get();
    }

    template<typename T>
    std::unique_ptr <T> removeObject(Id id, std::unordered_map <Id, std::unique_ptr<T>> &registry) {
        auto it = registry.find(id);
        if (it == registry.end()) {
            return nullptr;
        }
        auto ptr = std::move(it->second);
        registry.erase(it);
        return ptr;
    }

    struct InstanceDispatch {
        PFN_vkDestroySurfaceKHR destroySurfaceKHR = nullptr;
        PFN_vkCreateAndroidSurfaceKHR createAndroidSurfaceKHR = nullptr;
        PFN_vkGetPhysicalDeviceSurfaceSupportKHR getPhysicalDeviceSurfaceSupportKHR = nullptr;
        PFN_vkGetPhysicalDeviceSurfaceCapabilitiesKHR getPhysicalDeviceSurfaceCapabilitiesKHR = nullptr;
        PFN_vkGetPhysicalDeviceSurfaceFormatsKHR getPhysicalDeviceSurfaceFormatsKHR = nullptr;
        PFN_vkGetPhysicalDeviceSurfacePresentModesKHR getPhysicalDeviceSurfacePresentModesKHR = nullptr;
    };

    struct DeviceDispatch {
        PFN_vkCreateSwapchainKHR createSwapchainKHR = nullptr;
        PFN_vkDestroySwapchainKHR destroySwapchainKHR = nullptr;
        PFN_vkGetSwapchainImagesKHR getSwapchainImagesKHR = nullptr;
        PFN_vkAcquireNextImageKHR acquireNextImageKHR = nullptr;
        PFN_vkQueuePresentKHR queuePresentKHR = nullptr;
    };

    struct VulkanSwapchain {
        VkSwapchainKHR swapchain = VK_NULL_HANDLE;
        VkFormat imageFormat = VK_FORMAT_UNDEFINED;
        VkExtent2D extent{};
        std::vector <VkImage> images;
        std::vector <VkImageView> imageViews;
        VkRenderPass renderPass = VK_NULL_HANDLE;
        std::vector <VkFramebuffer> framebuffers;
        std::vector <VkCommandBuffer> commandBuffers;
        VkSemaphore imageAvailableSemaphore = VK_NULL_HANDLE;
        VkSemaphore renderFinishedSemaphore = VK_NULL_HANDLE;
        VkFence inFlightFence = VK_NULL_HANDLE;
        VkClearColorValue clearColor{{0.05f, 0.05f, 0.1f, 1.0f}};
    };

    struct VulkanSurface {
        VkSurfaceKHR surface = VK_NULL_HANDLE;
        ANativeWindow *window = nullptr;
        std::unordered_map <Id, std::unique_ptr<VulkanSwapchain>> swapchains;
    };

    struct VulkanDevice {
        VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
        VkDevice device = VK_NULL_HANDLE;
        uint32_t graphicsQueueFamily = 0;
        uint32_t presentQueueFamily = 0;
        VkQueue graphicsQueue = VK_NULL_HANDLE;
        VkQueue presentQueue = VK_NULL_HANDLE;
        VkCommandPool commandPool = VK_NULL_HANDLE;
        DeviceDispatch dispatch{};
    };

    struct VulkanInstance {
        VkInstance instance = VK_NULL_HANDLE;
        bool validationEnabled = false;
        InstanceDispatch dispatch{};
        std::unordered_map <Id, std::unique_ptr<VulkanSurface>> surfaces;
        std::unordered_map <Id, std::unique_ptr<VulkanDevice>> devices;
    };

    std::unordered_map <Id, std::unique_ptr<VulkanInstance>> g_instances;

    bool hasExtension(const std::vector <VkExtensionProperties> &available, const char *name) {
        for (const auto &ext: available) {
            if (strcmp(ext.extensionName, name) == 0) {
                return true;
            }
        }
        return false;
    }

    template<typename T>
    T loadInstanceProc(VkInstance instance, const char *name) {
        return reinterpret_cast<T>(vkGetInstanceProcAddr(instance, name));
    }

    template<typename T>
    T loadDeviceProc(VkDevice device, const char *name) {
        return reinterpret_cast<T>(vkGetDeviceProcAddr(device, name));
    }

    void populateInstanceDispatch(VulkanInstance &instance) {
        instance.dispatch.createAndroidSurfaceKHR =
                loadInstanceProc<PFN_vkCreateAndroidSurfaceKHR>(instance.instance,
                                                                "vkCreateAndroidSurfaceKHR");
        instance.dispatch.destroySurfaceKHR =
                loadInstanceProc<PFN_vkDestroySurfaceKHR>(instance.instance, "vkDestroySurfaceKHR");
        instance.dispatch.getPhysicalDeviceSurfaceSupportKHR =
                loadInstanceProc<PFN_vkGetPhysicalDeviceSurfaceSupportKHR>(instance.instance,
                                                                           "vkGetPhysicalDeviceSurfaceSupportKHR");
        instance.dispatch.getPhysicalDeviceSurfaceCapabilitiesKHR =
                loadInstanceProc<PFN_vkGetPhysicalDeviceSurfaceCapabilitiesKHR>(instance.instance,
                                                                                "vkGetPhysicalDeviceSurfaceCapabilitiesKHR");
        instance.dispatch.getPhysicalDeviceSurfaceFormatsKHR =
                loadInstanceProc<PFN_vkGetPhysicalDeviceSurfaceFormatsKHR>(instance.instance,
                                                                           "vkGetPhysicalDeviceSurfaceFormatsKHR");
        instance.dispatch.getPhysicalDeviceSurfacePresentModesKHR =
                loadInstanceProc<PFN_vkGetPhysicalDeviceSurfacePresentModesKHR>(instance.instance,
                                                                                "vkGetPhysicalDeviceSurfacePresentModesKHR");
    }

    void populateDeviceDispatch(VulkanDevice &device) {
        device.dispatch.createSwapchainKHR =
                loadDeviceProc<PFN_vkCreateSwapchainKHR>(device.device, "vkCreateSwapchainKHR");
        device.dispatch.destroySwapchainKHR =
                loadDeviceProc<PFN_vkDestroySwapchainKHR>(device.device, "vkDestroySwapchainKHR");
        device.dispatch.getSwapchainImagesKHR =
                loadDeviceProc<PFN_vkGetSwapchainImagesKHR>(device.device,
                                                            "vkGetSwapchainImagesKHR");
        device.dispatch.acquireNextImageKHR =
                loadDeviceProc<PFN_vkAcquireNextImageKHR>(device.device, "vkAcquireNextImageKHR");
        device.dispatch.queuePresentKHR =
                loadDeviceProc<PFN_vkQueuePresentKHR>(device.device, "vkQueuePresentKHR");
    }

    bool isValidationLayerAvailable(const char *layerName) {
        uint32_t layerCount = 0;
        vkEnumerateInstanceLayerProperties(&layerCount, nullptr);
        std::vector <VkLayerProperties> layers(layerCount);
        vkEnumerateInstanceLayerProperties(&layerCount, layers.data());
        for (const auto &layer: layers) {
            if (strcmp(layer.layerName, layerName) == 0) {
                return true;
            }
        }
        return false;
    }

    VkFormat chooseSurfaceFormat(const std::vector <VkSurfaceFormatKHR> &formats) {
        for (const auto &format: formats) {
            if (format.format == VK_FORMAT_B8G8R8A8_UNORM &&
                format.colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return format.format;
            }
        }
        return formats.empty() ? VK_FORMAT_B8G8R8A8_UNORM : formats.front().format;
    }

    VkExtent2D chooseExtent(const VkSurfaceCapabilitiesKHR &caps, uint32_t width, uint32_t height) {
        if (caps.currentExtent.width != UINT32_MAX) {
            return caps.currentExtent;
        }

        VkExtent2D extent{};
        extent.width = std::clamp(width, caps.minImageExtent.width, caps.maxImageExtent.width);
        extent.height = std::clamp(height, caps.minImageExtent.height, caps.maxImageExtent.height);
        return extent;
    }

    uint32_t selectImageCount(const VkSurfaceCapabilitiesKHR &caps) {
        uint32_t count = caps.minImageCount + 1;
        if (caps.maxImageCount > 0 && count > caps.maxImageCount) {
            count = caps.maxImageCount;
        }
        return count;
    }

    void buildSwapchainResources(
            VulkanInstance &instance,
            VulkanDevice &device,
            VulkanSurface &surface,
            VulkanSwapchain &swapchain,
            uint32_t width,
            uint32_t height
    ) {
        VkSurfaceCapabilitiesKHR capabilities{};
        instance.dispatch.getPhysicalDeviceSurfaceCapabilitiesKHR(
                device.physicalDevice,
                surface.surface,
                &capabilities);

        uint32_t formatCount = 0;
        instance.dispatch.getPhysicalDeviceSurfaceFormatsKHR(
                device.physicalDevice,
                surface.surface,
                &formatCount,
                nullptr);
        std::vector <VkSurfaceFormatKHR> formats(formatCount);
        instance.dispatch.getPhysicalDeviceSurfaceFormatsKHR(
                device.physicalDevice,
                surface.surface,
                &formatCount,
                formats.data());

        VkFormat surfaceFormat = chooseSurfaceFormat(formats);
        VkExtent2D extent = chooseExtent(capabilities, width, height);
        uint32_t imageCount = selectImageCount(capabilities);

        VkSwapchainCreateInfoKHR swapchainInfo{};
        swapchainInfo.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
        swapchainInfo.surface = surface.surface;
        swapchainInfo.minImageCount = imageCount;
        swapchainInfo.imageFormat = surfaceFormat;
        swapchainInfo.imageColorSpace = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
        swapchainInfo.imageExtent = extent;
        swapchainInfo.imageArrayLayers = 1;
        swapchainInfo.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;

        uint32_t queueFamilyIndices[] = {device.graphicsQueueFamily, device.presentQueueFamily};
        if (device.graphicsQueueFamily != device.presentQueueFamily) {
            swapchainInfo.imageSharingMode = VK_SHARING_MODE_CONCURRENT;
            swapchainInfo.queueFamilyIndexCount = 2;
            swapchainInfo.pQueueFamilyIndices = queueFamilyIndices;
        } else {
            swapchainInfo.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
        }

        swapchainInfo.preTransform = capabilities.currentTransform;
        swapchainInfo.compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
        swapchainInfo.presentMode = VK_PRESENT_MODE_FIFO_KHR;
        swapchainInfo.clipped = VK_TRUE;
        swapchainInfo.oldSwapchain = VK_NULL_HANDLE;

        VkSwapchainKHR newSwapchain = VK_NULL_HANDLE;
        VkResult res = device.dispatch.createSwapchainKHR(device.device, &swapchainInfo, nullptr,
                                                          &newSwapchain);
        if (res != VK_SUCCESS) {
            VK_LOG_ERROR("vkCreateSwapchainKHR failed (%d)", res);
            throw std::runtime_error("Failed to create swapchain");
        }

        swapchain.swapchain = newSwapchain;

        uint32_t swapchainImageCount = 0;
        device.dispatch.getSwapchainImagesKHR(device.device, swapchain.swapchain,
                                              &swapchainImageCount, nullptr);
        swapchain.images.resize(swapchainImageCount);
        device.dispatch.getSwapchainImagesKHR(device.device, swapchain.swapchain,
                                              &swapchainImageCount, swapchain.images.data());

        swapchain.imageFormat = surfaceFormat;
        swapchain.extent = extent;

        swapchain.imageViews.resize(swapchain.images.size());
        for (size_t i = 0; i < swapchain.images.size(); ++i) {
            VkImageViewCreateInfo viewInfo{};
            viewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
            viewInfo.image = swapchain.images[i];
            viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
            viewInfo.format = swapchain.imageFormat;
            viewInfo.components = {VK_COMPONENT_SWIZZLE_IDENTITY, VK_COMPONENT_SWIZZLE_IDENTITY,
                                   VK_COMPONENT_SWIZZLE_IDENTITY, VK_COMPONENT_SWIZZLE_IDENTITY};
            viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
            viewInfo.subresourceRange.baseMipLevel = 0;
            viewInfo.subresourceRange.levelCount = 1;
            viewInfo.subresourceRange.baseArrayLayer = 0;
            viewInfo.subresourceRange.layerCount = 1;

            res = vkCreateImageView(device.device, &viewInfo, nullptr, &swapchain.imageViews[i]);
            if (res != VK_SUCCESS) {
                VK_LOG_ERROR("vkCreateImageView failed (%d)", res);
                throw std::runtime_error("Failed to create swapchain image view");
            }
        }

        VkAttachmentDescription colorAttachment{};
        colorAttachment.format = swapchain.imageFormat;
        colorAttachment.samples = VK_SAMPLE_COUNT_1_BIT;
        colorAttachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
        colorAttachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        colorAttachment.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        colorAttachment.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
        colorAttachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        colorAttachment.finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;

        VkAttachmentReference colorAttachmentRef{};
        colorAttachmentRef.attachment = 0;
        colorAttachmentRef.layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

        VkSubpassDescription subpass{};
        subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
        subpass.colorAttachmentCount = 1;
        subpass.pColorAttachments = &colorAttachmentRef;

        VkSubpassDependency dependency{};
        dependency.srcSubpass = VK_SUBPASS_EXTERNAL;
        dependency.dstSubpass = 0;
        dependency.srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dependency.srcAccessMask = 0;
        dependency.dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dependency.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;

        VkRenderPassCreateInfo renderPassInfo{};
        renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
        renderPassInfo.attachmentCount = 1;
        renderPassInfo.pAttachments = &colorAttachment;
        renderPassInfo.subpassCount = 1;
        renderPassInfo.pSubpasses = &subpass;
        renderPassInfo.dependencyCount = 1;
        renderPassInfo.pDependencies = &dependency;

        res = vkCreateRenderPass(device.device, &renderPassInfo, nullptr, &swapchain.renderPass);
        if (res != VK_SUCCESS) {
            VK_LOG_ERROR("vkCreateRenderPass failed (%d)", res);
            throw std::runtime_error("Failed to create render pass");
        }

        swapchain.framebuffers.resize(swapchain.imageViews.size());
        for (size_t i = 0; i < swapchain.imageViews.size(); ++i) {
            VkImageView attachments[] = {swapchain.imageViews[i]};

            VkFramebufferCreateInfo framebufferInfo{};
            framebufferInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
            framebufferInfo.renderPass = swapchain.renderPass;
            framebufferInfo.attachmentCount = 1;
            framebufferInfo.pAttachments = attachments;
            framebufferInfo.width = swapchain.extent.width;
            framebufferInfo.height = swapchain.extent.height;
            framebufferInfo.layers = 1;

            res = vkCreateFramebuffer(device.device, &framebufferInfo, nullptr,
                                      &swapchain.framebuffers[i]);
            if (res != VK_SUCCESS) {
                VK_LOG_ERROR("vkCreateFramebuffer failed (%d)", res);
                throw std::runtime_error("Failed to create framebuffer");
            }
        }

        ensureCommandBuffers(device, swapchain);
        recordCommandBuffers(device, swapchain);

        VkSemaphoreCreateInfo semaphoreInfo{};
        semaphoreInfo.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;

        VkFenceCreateInfo fenceInfo{};
        fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
        fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;

        res = vkCreateSemaphore(device.device, &semaphoreInfo, nullptr,
                                &swapchain.imageAvailableSemaphore);
        if (res != VK_SUCCESS) {
            throw std::runtime_error("Failed to create semaphore (imageAvailable)");
        }
        res = vkCreateSemaphore(device.device, &semaphoreInfo, nullptr,
                                &swapchain.renderFinishedSemaphore);
        if (res != VK_SUCCESS) {
            throw std::runtime_error("Failed to create semaphore (renderFinished)");
        }
        res = vkCreateFence(device.device, &fenceInfo, nullptr, &swapchain.inFlightFence);
        if (res != VK_SUCCESS) {
            throw std::runtime_error("Failed to create fence");
        }
    }

    void destroySwapchainObjects(VulkanDevice &device, VulkanSwapchain &swapchain) {
        if (swapchain.inFlightFence != VK_NULL_HANDLE) {
            vkDestroyFence(device.device, swapchain.inFlightFence, nullptr);
            swapchain.inFlightFence = VK_NULL_HANDLE;
        }
        if (swapchain.renderFinishedSemaphore != VK_NULL_HANDLE) {
            vkDestroySemaphore(device.device, swapchain.renderFinishedSemaphore, nullptr);
            swapchain.renderFinishedSemaphore = VK_NULL_HANDLE;
        }
        if (swapchain.imageAvailableSemaphore != VK_NULL_HANDLE) {
            vkDestroySemaphore(device.device, swapchain.imageAvailableSemaphore, nullptr);
            swapchain.imageAvailableSemaphore = VK_NULL_HANDLE;
        }
        if (!swapchain.commandBuffers.empty()) {
            vkFreeCommandBuffers(device.device, device.commandPool,
                                 static_cast<uint32_t>(swapchain.commandBuffers.size()),
                                 swapchain.commandBuffers.data());
            swapchain.commandBuffers.clear();
        }
        for (auto framebuffer: swapchain.framebuffers) {
            vkDestroyFramebuffer(device.device, framebuffer, nullptr);
        }
        swapchain.framebuffers.clear();
        for (auto view: swapchain.imageViews) {
            vkDestroyImageView(device.device, view, nullptr);
        }
        swapchain.imageViews.clear();
        swapchain.images.clear();
        if (swapchain.renderPass != VK_NULL_HANDLE) {
            vkDestroyRenderPass(device.device, swapchain.renderPass, nullptr);
            swapchain.renderPass = VK_NULL_HANDLE;
        }
        if (swapchain.swapchain != VK_NULL_HANDLE) {
            device.dispatch.destroySwapchainKHR(device.device, swapchain.swapchain, nullptr);
            swapchain.swapchain = VK_NULL_HANDLE;
        }
    }

    void recordCommandBuffers(VulkanDevice &device, VulkanSwapchain &swapchain) {
        VkCommandBufferBeginInfo beginInfo{};
        beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
        beginInfo.flags = VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT;

        VkClearValue clear{};
        clear.color = swapchain.clearColor;

        for (size_t i = 0; i < swapchain.commandBuffers.size(); ++i) {
            VkCommandBuffer cmd = swapchain.commandBuffers[i];
            vkBeginCommandBuffer(cmd, &beginInfo);

            VkRenderPassBeginInfo renderPassInfo{};
            renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
            renderPassInfo.renderPass = swapchain.renderPass;
            renderPassInfo.framebuffer = swapchain.framebuffers[i];
            renderPassInfo.renderArea.offset = {0, 0};
            renderPassInfo.renderArea.extent = swapchain.extent;
            renderPassInfo.clearValueCount = 1;
            renderPassInfo.pClearValues = &clear;

            vkCmdBeginRenderPass(cmd, &renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
            vkCmdEndRenderPass(cmd);

            vkEndCommandBuffer(cmd);
        }
    }

    void ensureCommandBuffers(VulkanDevice &device, VulkanSwapchain &swapchain) {
        if (!swapchain.commandBuffers.empty()) {
            vkFreeCommandBuffers(device.device, device.commandPool,
                                 static_cast<uint32_t>(swapchain.commandBuffers.size()),
                                 swapchain.commandBuffers.data());
            swapchain.commandBuffers.clear();
        }

        VkCommandBufferAllocateInfo allocInfo{};
        allocInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
        allocInfo.commandPool = device.commandPool;
        allocInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        allocInfo.commandBufferCount = static_cast<uint32_t>(swapchain.images.size());

        swapchain.commandBuffers.resize(swapchain.images.size());
        VkResult res = vkAllocateCommandBuffers(device.device, &allocInfo,
                                                swapchain.commandBuffers.data());
        if (res != VK_SUCCESS) {
            throw std::runtime_error("Failed to allocate command buffers for swapchain");
        }
    }

} // namespace

namespace kreekt::vk {

    std::uint64_t createInstance(const char *appName, bool enableValidation) {
        std::lock_guard <std::mutex> lock(g_registryMutex);

        VkApplicationInfo appInfo{};
        appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
        appInfo.pApplicationName = appName;
        appInfo.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
        appInfo.pEngineName = "KreeKt";
        appInfo.engineVersion = VK_MAKE_VERSION(1, 0, 0);
        appInfo.apiVersion = VK_API_VERSION_1_1;

        std::vector<const char *> extensions = {
                VK_KHR_SURFACE_EXTENSION_NAME,
                VK_KHR_ANDROID_SURFACE_EXTENSION_NAME
        };

        std::vector<const char *> layers;
        if (enableValidation && isValidationLayerAvailable("VK_LAYER_KHRONOS_validation")) {
            layers.push_back("VK_LAYER_KHRONOS_validation");
        }

        VkInstanceCreateInfo createInfo{};
        createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
        createInfo.pApplicationInfo = &appInfo;
        createInfo.enabledExtensionCount = static_cast<uint32_t>(extensions.size());
        createInfo.ppEnabledExtensionNames = extensions.data();
        createInfo.enabledLayerCount = static_cast<uint32_t>(layers.size());
        createInfo.ppEnabledLayerNames = layers.empty() ? nullptr : layers.data();

        VkInstance instance = VK_NULL_HANDLE;
        VkResult res = vkCreateInstance(&createInfo, nullptr, &instance);
        if (res != VK_SUCCESS) {
            VK_LOG_ERROR("vkCreateInstance failed (%d)", res);
            throw std::runtime_error("Failed to create Vulkan instance");
        }

        auto vkInstance = std::make_unique<VulkanInstance>();
        vkInstance->instance = instance;
        vkInstance->validationEnabled = enableValidation;
        populateInstanceDispatch(*vkInstance);

        const Id id = registerObject(std::move(vkInstance), g_instances);
        VK_LOG_INFO("Created Vulkan instance (id=%" PRIu64 ")", id);
        return id;
    }

    std::uint64_t createSurface(std::uint64_t instanceId, JNIEnv *env, jobject surfaceObj) {
        std::lock_guard <std::mutex> lock(g_registryMutex);
        auto *instance = lookupObject(instanceId, g_instances);
        if (!instance) {
            throw std::runtime_error("Invalid instance id in createSurface");
        }

        ANativeWindow *window = ANativeWindow_fromSurface(env, surfaceObj);
        if (!window) {
            throw std::runtime_error("Failed to acquire ANativeWindow from Surface");
        }

        VkAndroidSurfaceCreateInfoKHR surfaceInfo{};
        surfaceInfo.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
        surfaceInfo.window = window;

        VkSurfaceKHR surface = VK_NULL_HANDLE;
        VkResult res = instance->dispatch.createAndroidSurfaceKHR(instance->instance, &surfaceInfo,
                                                                  nullptr, &surface);
        if (res != VK_SUCCESS) {
            ANativeWindow_release(window);
            VK_LOG_ERROR("vkCreateAndroidSurfaceKHR failed (%d)", res);
            throw std::runtime_error("Failed to create Android surface");
        }

        auto vkSurface = std::make_unique<VulkanSurface>();
        vkSurface->surface = surface;
        vkSurface->window = window;

        const Id surfaceId = registerObject(std::move(vkSurface), instance->surfaces);
        VK_LOG_INFO("Created Vulkan surface (instance=%" PRIu64 ", surface=%" PRIu64 ")",
                    instanceId, surfaceId);
        return surfaceId;
    }

    std::uint64_t createDevice(std::uint64_t instanceId) {
        std::lock_guard <std::mutex> lock(g_registryMutex);
        auto *instance = lookupObject(instanceId, g_instances);
        if (!instance) {
            throw std::runtime_error("Invalid instance id in createDevice");
        }

        uint32_t deviceCount = 0;
        vkEnumeratePhysicalDevices(instance->instance, &deviceCount, nullptr);
        if (deviceCount == 0) {
            throw std::runtime_error("No Vulkan physical devices available on this Android device");
        }
        std::vector <VkPhysicalDevice> physicalDevices(deviceCount);
        vkEnumeratePhysicalDevices(instance->instance, &deviceCount, physicalDevices.data());

        VkPhysicalDevice selectedDevice = VK_NULL_HANDLE;
        uint32_t graphicsQueueFamily = UINT32_MAX;
        uint32_t presentQueueFamily = UINT32_MAX;

        for (auto candidate: physicalDevices) {
            uint32_t queueCount = 0;
            vkGetPhysicalDeviceQueueFamilyProperties(candidate, &queueCount, nullptr);
            std::vector <VkQueueFamilyProperties> queueFamilies(queueCount);
            vkGetPhysicalDeviceQueueFamilyProperties(candidate, &queueCount, queueFamilies.data());

            for (uint32_t i = 0; i < queueCount; ++i) {
                const bool graphics = (queueFamilies[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0;
                if (!graphics) continue;

                bool present = true;
                for (const auto &entry: instance->surfaces) {
                    VkBool32 supported = VK_FALSE;
                    instance->dispatch.getPhysicalDeviceSurfaceSupportKHR(candidate, i,
                                                                          entry.second->surface,
                                                                          &supported);
                    if (supported == VK_FALSE) {
                        present = false;
                        break;
                    }
                }

                if (present) {
                    selectedDevice = candidate;
                    graphicsQueueFamily = i;
                    presentQueueFamily = i;
                    break;
                }
            }

            if (selectedDevice != VK_NULL_HANDLE) {
                break;
            }
        }

        if (selectedDevice == VK_NULL_HANDLE) {
            // Fall back to using the first device and assume queue 0 supports present (common on Android).
            selectedDevice = physicalDevices.front();
            graphicsQueueFamily = 0;
            presentQueueFamily = 0;
        }

        std::vector<const char *> deviceExtensions = {
                VK_KHR_SWAPCHAIN_EXTENSION_NAME
        };

        float queuePriority = 1.0f;
        std::vector <VkDeviceQueueCreateInfo> queueInfos;

        std::array<uint32_t, 2> uniqueQueues = {graphicsQueueFamily, presentQueueFamily};
        std::sort(uniqueQueues.begin(), uniqueQueues.end());
        uniqueQueues.erase(std::unique(uniqueQueues.begin(), uniqueQueues.end()),
                           uniqueQueues.end());

        for (uint32_t queueFamily: uniqueQueues) {
            VkDeviceQueueCreateInfo queueInfo{};
            queueInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
            queueInfo.queueFamilyIndex = queueFamily;
            queueInfo.queueCount = 1;
            queueInfo.pQueuePriorities = &queuePriority;
            queueInfos.push_back(queueInfo);
        }

        VkPhysicalDeviceFeatures features{};

        VkDeviceCreateInfo deviceInfo{};
        deviceInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
        deviceInfo.queueCreateInfoCount = static_cast<uint32_t>(queueInfos.size());
        deviceInfo.pQueueCreateInfos = queueInfos.data();
        deviceInfo.pEnabledFeatures = &features;
        deviceInfo.enabledExtensionCount = static_cast<uint32_t>(deviceExtensions.size());
        deviceInfo.ppEnabledExtensionNames = deviceExtensions.data();

        VkDevice device = VK_NULL_HANDLE;
        VkResult res = vkCreateDevice(selectedDevice, &deviceInfo, nullptr, &device);
        if (res != VK_SUCCESS) {
            VK_LOG_ERROR("vkCreateDevice failed (%d)", res);
            throw std::runtime_error("Failed to create Vulkan device");
        }

        auto vkDevice = std::make_unique<VulkanDevice>();
        vkDevice->physicalDevice = selectedDevice;
        vkDevice->device = device;
        vkDevice->graphicsQueueFamily = graphicsQueueFamily;
        vkDevice->presentQueueFamily = presentQueueFamily;
        vkGetDeviceQueue(device, graphicsQueueFamily, 0, &vkDevice->graphicsQueue);
        vkGetDeviceQueue(device, presentQueueFamily, 0, &vkDevice->presentQueue);

        populateDeviceDispatch(*vkDevice);

        VkCommandPoolCreateInfo poolInfo{};
        poolInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
        poolInfo.queueFamilyIndex = graphicsQueueFamily;
        poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;

        res = vkCreateCommandPool(device, &poolInfo, nullptr, &vkDevice->commandPool);
        if (res != VK_SUCCESS) {
            VK_LOG_ERROR("vkCreateCommandPool failed (%d)", res);
            throw std::runtime_error("Failed to create Vulkan command pool");
        }

        const Id deviceId = registerObject(std::move(vkDevice), instance->devices);
        VK_LOG_INFO("Created Vulkan device (instance=%" PRIu64 ", device=%" PRIu64 ")", instanceId,
                    deviceId);
        return deviceId;
    }

    std::uint64_t createSwapchain(
            std::uint64_t deviceId,
            std::uint64_t surfaceId,
            std::uint32_t width,
            std::uint32_t height) {
        std::lock_guard <std::mutex> lock(g_registryMutex);

        VulkanInstance *ownerInstance = nullptr;
        VulkanDevice *device = nullptr;
        VulkanSurface *surface = nullptr;
        for (auto &[instId, instPtr]: g_instances) {
            device = lookupObject(deviceId, instPtr->devices);
            if (device) {
                surface = lookupObject(surfaceId, instPtr->surfaces);
                if (!surface) {
                    throw std::runtime_error(
                            "Surface does not belong to the same instance as the device");
                }
                ownerInstance = instPtr.get();
                break;
            }
        }

        if (!device || !ownerInstance || !surface) {
            throw std::runtime_error("Invalid device/surface id for swapchain creation");
        }

        auto swapchain = std::make_unique<VulkanSwapchain>();
        buildSwapchainResources(*ownerInstance, *device, *surface, *swapchain, width, height);

        const Id swapchainId = registerObject(std::move(swapchain), surface->swapchains);
        VK_LOG_INFO(
                "Created Vulkan swapchain (device=%" PRIu64 ", surface=%" PRIu64 ", swapchain=%" PRIu64 ")",
                deviceId, surfaceId, swapchainId);
        return swapchainId;
    }

    bool drawFrame(
            std::uint64_t deviceId,
            std::uint64_t swapchainId,
            float clearR,
            float clearG,
            float clearB,
            float clearA) {
        std::lock_guard <std::mutex> lock(g_registryMutex);

        VulkanDevice *device = nullptr;
        VulkanSwapchain *swapchain = nullptr;
        for (auto &[instanceId, instancePtr]: g_instances) {
            device = lookupObject(deviceId, instancePtr->devices);
            if (!device) continue;

            for (auto &[surfaceId, surfacePtr]: instancePtr->surfaces) {
                swapchain = lookupObject(swapchainId, surfacePtr->swapchains);
                if (swapchain) {
                    break;
                }
            }
            if (swapchain) break;
        }

        if (!device || !swapchain) {
            throw std::runtime_error("Invalid device/swapchain id in drawFrame");
        }

        swapchain->clearColor = {{clearR, clearG, clearB, clearA}};
        recordCommandBuffers(*device, *swapchain);

        vkWaitForFences(device->device, 1, &swapchain->inFlightFence, VK_TRUE, UINT64_MAX);
        vkResetFences(device->device, 1, &swapchain->inFlightFence);

        uint32_t imageIndex = 0;
        VkResult res = device->dispatch.acquireNextImageKHR(
                device->device,
                swapchain->swapchain,
                UINT64_MAX,
                swapchain->imageAvailableSemaphore,
                VK_NULL_HANDLE,
                &imageIndex);

        if (res == VK_ERROR_OUT_OF_DATE_KHR) {
            return false;
        }
        if (res != VK_SUCCESS && res != VK_SUBOPTIMAL_KHR) {
            VK_LOG_ERROR("vkAcquireNextImageKHR failed (%d)", res);
            throw std::runtime_error("Failed to acquire next swapchain image");
        }

        VkPipelineStageFlags waitStages[] = {VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT};

        VkSubmitInfo submitInfo{};
        submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
        submitInfo.waitSemaphoreCount = 1;
        submitInfo.pWaitSemaphores = &swapchain->imageAvailableSemaphore;
        submitInfo.pWaitDstStageMask = waitStages;
        submitInfo.commandBufferCount = 1;
        submitInfo.pCommandBuffers = &swapchain->commandBuffers[imageIndex];
        submitInfo.signalSemaphoreCount = 1;
        submitInfo.pSignalSemaphores = &swapchain->renderFinishedSemaphore;

        res = vkQueueSubmit(device->graphicsQueue, 1, &submitInfo, swapchain->inFlightFence);
        if (res != VK_SUCCESS) {
            VK_LOG_ERROR("vkQueueSubmit failed (%d)", res);
            throw std::runtime_error("Failed to submit draw command buffer");
        }

        VkPresentInfoKHR presentInfo{};
        presentInfo.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
        presentInfo.waitSemaphoreCount = 1;
        presentInfo.pWaitSemaphores = &swapchain->renderFinishedSemaphore;
        presentInfo.swapchainCount = 1;
        presentInfo.pSwapchains = &swapchain->swapchain;
        presentInfo.pImageIndices = &imageIndex;

        res = device->dispatch.queuePresentKHR(device->presentQueue, &presentInfo);
        if (res == VK_ERROR_OUT_OF_DATE_KHR || res == VK_SUBOPTIMAL_KHR) {
            return false;
        }
        if (res != VK_SUCCESS) {
            VK_LOG_ERROR("vkQueuePresentKHR failed (%d)", res);
            throw std::runtime_error("Failed to present swapchain image");
        }

        return true;
    }

    void resizeSwapchain(
            std::uint64_t deviceId,
            std::uint64_t surfaceId,
            std::uint64_t swapchainId,
            std::uint32_t width,
            std::uint32_t height) {
        std::lock_guard <std::mutex> lock(g_registryMutex);

        VulkanInstance *ownerInstance = nullptr;
        VulkanDevice *device = nullptr;
        VulkanSurface *surface = nullptr;
        VulkanSwapchain *swapchain = nullptr;

        for (auto &[instId, instPtr]: g_instances) {
            device = lookupObject(deviceId, instPtr->devices);
            if (!device) continue;
            surface = lookupObject(surfaceId, instPtr->surfaces);
            if (!surface) continue;
            swapchain = lookupObject(swapchainId, surface->swapchains);
            if (!swapchain) continue;
            ownerInstance = instPtr.get();
            break;
        }

        if (!device || !surface || !swapchain) {
            throw std::runtime_error("Invalid handles supplied to resizeSwapchain");
        }

        vkDeviceWaitIdle(device->device);
        destroySwapchainObjects(*device, *swapchain);
        buildSwapchainResources(*ownerInstance, *device, *surface, *swapchain, width, height);
    }

    void destroySwapchain(std::uint64_t deviceId, std::uint64_t swapchainId) {
        std::lock_guard <std::mutex> lock(g_registryMutex);
        for (auto &[instanceId, instancePtr]: g_instances) {
            VulkanDevice *device = lookupObject(deviceId, instancePtr->devices);
            if (!device) continue;
            for (auto &[surfaceId, surfacePtr]: instancePtr->surfaces) {
                auto swapchainPtr = removeObject(swapchainId, surfacePtr->swapchains);
                if (swapchainPtr) {
                    vkDeviceWaitIdle(device->device);
                    destroySwapchainObjects(*device, *swapchainPtr);
                    return;
                }
            }
        }
    }

    void destroySurface(std::uint64_t instanceId, std::uint64_t surfaceId) {
        std::lock_guard <std::mutex> lock(g_registryMutex);
        auto *instance = lookupObject(instanceId, g_instances);
        if (!instance) return;
        auto surfacePtr = removeObject(surfaceId, instance->surfaces);
        if (!surfacePtr) return;

        for (auto &swapchainEntry: surfacePtr->swapchains) {
            // Acquire device pointer
            for (auto &[deviceId, devicePtr]: instance->devices) {
                destroySwapchainObjects(*devicePtr, *swapchainEntry.second);
            }
        }

        if (surfacePtr->surface != VK_NULL_HANDLE) {
            instance->dispatch.destroySurfaceKHR(instance->instance, surfacePtr->surface, nullptr);
        }
        if (surfacePtr->window) {
            ANativeWindow_release(surfacePtr->window);
        }
    }

    void destroyDevice(std::uint64_t instanceId) {
        std::lock_guard <std::mutex> lock(g_registryMutex);
        auto *instance = lookupObject(instanceId, g_instances);
        if (!instance) return;

        for (auto &[deviceId, devicePtr]: instance->devices) {
            VkDevice device = devicePtr->device;
            if (device == VK_NULL_HANDLE) continue;

            vkDeviceWaitIdle(device);

            for (auto &surfaceEntry: instance->surfaces) {
                auto &surface = surfaceEntry.second;
                for (auto &swapchainEntry: surface->swapchains) {
                    destroySwapchainObjects(*devicePtr, *swapchainEntry.second);
                }
                surface->swapchains.clear();
            }

            if (devicePtr->commandPool != VK_NULL_HANDLE) {
                vkDestroyCommandPool(device, devicePtr->commandPool, nullptr);
                devicePtr->commandPool = VK_NULL_HANDLE;
            }

            vkDestroyDevice(device, nullptr);
            devicePtr->device = VK_NULL_HANDLE;
        }

        instance->devices.clear();
    }

    void destroyInstance(std::uint64_t instanceId) {
        std::lock_guard <std::mutex> lock(g_registryMutex);
        auto instancePtr = removeObject(instanceId, g_instances);
        if (!instancePtr) return;

        destroyDevice(instanceId);
        for (auto &surfaceEntry: instancePtr->surfaces) {
            destroySurface(instanceId, surfaceEntry.first);
        }
        instancePtr->surfaces.clear();

        if (instancePtr->instance != VK_NULL_HANDLE) {
            vkDestroyInstance(instancePtr->instance, nullptr);
            instancePtr->instance = VK_NULL_HANDLE;
        }
    }

    void destroyAll() {
        std::lock_guard <std::mutex> lock(g_registryMutex);
        for (auto &[instanceId, instancePtr]: g_instances) {
            destroyDevice(instanceId);
            for (auto &surfaceEntry: instancePtr->surfaces) {
                destroySurface(instanceId, surfaceEntry.first);
            }
            instancePtr->surfaces.clear();
            if (instancePtr->instance != VK_NULL_HANDLE) {
                vkDestroyInstance(instancePtr->instance, nullptr);
            }
        }
        g_instances.clear();
    }

} // namespace kreekt::vk
