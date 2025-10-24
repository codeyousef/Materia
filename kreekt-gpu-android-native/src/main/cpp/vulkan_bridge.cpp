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
#include <limits>
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

    std::atomic<Id> g_nextId{1};

    struct VulkanInstance;
    struct VulkanSurface;
    struct VulkanDevice;
    struct VulkanSwapchain;
    struct VulkanTexture;
    struct VulkanTextureView;
    struct VulkanBuffer;
    struct VulkanShaderModule;
    struct VulkanSampler;
    struct VulkanBindGroupLayout;
    struct VulkanBindGroup;
    struct VulkanPipelineLayout;
    struct VulkanRenderPipeline;
    struct VulkanCommandEncoder;
    struct VulkanCommandBufferWrapper;
    struct VulkanRenderPassEncoder;
    struct VulkanSurfaceFrame;

    struct InstanceDispatch {
        PFN_vkCreateAndroidSurfaceKHR createAndroidSurfaceKHR = nullptr;
        PFN_vkDestroySurfaceKHR destroySurfaceKHR = nullptr;
        PFN_vkGetPhysicalDeviceSurfaceSupportKHR getSurfaceSupportKHR = nullptr;
        PFN_vkGetPhysicalDeviceSurfaceCapabilitiesKHR getSurfaceCapabilitiesKHR = nullptr;
        PFN_vkGetPhysicalDeviceSurfaceFormatsKHR getSurfaceFormatsKHR = nullptr;
        PFN_vkGetPhysicalDeviceSurfacePresentModesKHR getSurfacePresentModesKHR = nullptr;
    };

    struct DeviceDispatch {
        PFN_vkCreateSwapchainKHR createSwapchainKHR = nullptr;
        PFN_vkDestroySwapchainKHR destroySwapchainKHR = nullptr;
        PFN_vkGetSwapchainImagesKHR getSwapchainImagesKHR = nullptr;
        PFN_vkAcquireNextImageKHR acquireNextImageKHR = nullptr;
        PFN_vkQueuePresentKHR queuePresentKHR = nullptr;
    };

    struct VulkanBuffer {
        VkBuffer buffer = VK_NULL_HANDLE;
        VkDeviceMemory memory = VK_NULL_HANDLE;
        VkDeviceSize size = 0;
        VkBufferUsageFlags usage = 0;
    };

    struct VulkanShaderModule {
        VkShaderModule module = VK_NULL_HANDLE;
    };

    struct VulkanSampler {
        VkSampler sampler = VK_NULL_HANDLE;
    };

    struct VulkanTexture {
        VkImage image = VK_NULL_HANDLE;
        VkDeviceMemory memory = VK_NULL_HANDLE;
        VkFormat format = VK_FORMAT_UNDEFINED;
        uint32_t width = 0;
        uint32_t height = 0;
        bool ownsImage = true;
        bool ownsMemory = true;
    };

    struct VulkanTextureView {
        VulkanTexture *texture = nullptr;
        VkImageView view = VK_NULL_HANDLE;
    };

    struct BindGroupLayoutEntry {
        uint32_t binding = 0;
        VkDescriptorType descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
        VkShaderStageFlags stageFlags = VK_SHADER_STAGE_VERTEX_BIT;
    };

    struct VulkanBindGroupLayout {
        VkDescriptorSetLayout layout = VK_NULL_HANDLE;
        std::vector<BindGroupLayoutEntry> entries;
    };

    struct BindGroupEntry {
        uint32_t binding = 0;
        VulkanBuffer *buffer = nullptr;
        VkDeviceSize offset = 0;
        VkDeviceSize range = 0;
        VulkanTextureView *textureView = nullptr;
        VulkanSampler *sampler = nullptr;
    };

    struct VulkanBindGroup {
        VulkanBindGroupLayout *layout = nullptr;
        VkDescriptorSet descriptorSet = VK_NULL_HANDLE;
        std::vector<BindGroupEntry> entries;
    };

    struct VulkanPipelineLayout {
        VkPipelineLayout layout = VK_NULL_HANDLE;
        std::vector<VulkanBindGroupLayout *> setLayouts;
    };

    struct VulkanRenderPipeline {
        VkPipeline pipeline = VK_NULL_HANDLE;
        VulkanPipelineLayout *pipelineLayout = nullptr;
        VkRenderPass renderPass = VK_NULL_HANDLE;
        VkPrimitiveTopology topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
    };

    struct VulkanCommandEncoder {
        VulkanDevice *device = nullptr;
        VkCommandBuffer commandBuffer = VK_NULL_HANDLE;
        VulkanRenderPipeline *currentPipeline = nullptr;
        VulkanSwapchain *targetSwapchain = nullptr;
        uint32_t swapchainImageIndex = 0;
    };

    struct VulkanCommandBufferWrapper {
        VulkanDevice *device = nullptr;
        VkCommandBuffer commandBuffer = VK_NULL_HANDLE;
        VulkanSwapchain *swapchain = nullptr;
        uint32_t imageIndex = 0;
    };

    struct VulkanRenderPassEncoder {
        VulkanCommandEncoder *encoder = nullptr;
        bool recording = false;
    };

    struct VulkanSurfaceFrame {
        VulkanSwapchain *swapchain = nullptr;
        uint32_t imageIndex = 0;
        Id textureId = 0;
        Id viewId = 0;
    };

    struct VulkanSwapchain {
        VulkanDevice *device = nullptr;
        VkSwapchainKHR swapchain = VK_NULL_HANDLE;
        VkFormat format = VK_FORMAT_B8G8R8A8_UNORM;
        VkExtent2D extent{0, 0};
        VkRenderPass renderPass = VK_NULL_HANDLE;
        std::vector<VkImage> images;
        std::vector<Id> textureIds;
        std::vector<Id> textureViewIds;
        std::vector<VkFramebuffer> framebuffers;
        VkSemaphore imageAvailableSemaphore = VK_NULL_HANDLE;
        VkSemaphore renderFinishedSemaphore = VK_NULL_HANDLE;
        VkFence inFlightFence = VK_NULL_HANDLE;
    };

    struct VulkanSurface {
        VkSurfaceKHR surface = VK_NULL_HANDLE;
        ANativeWindow *window = nullptr;
        std::unordered_map<Id, std::unique_ptr<VulkanSwapchain>> swapchains;
    };

    struct VulkanDevice {
        VulkanInstance *instance = nullptr;
        VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
        VkDevice device = VK_NULL_HANDLE;
        VkQueue graphicsQueue = VK_NULL_HANDLE;
        VkQueue presentQueue = VK_NULL_HANDLE;
        uint32_t graphicsQueueFamily = 0;
        uint32_t presentQueueFamily = 0;
        VkCommandPool commandPool = VK_NULL_HANDLE;
        VkDescriptorPool descriptorPool = VK_NULL_HANDLE;
        DeviceDispatch dispatch{};

        std::unordered_map<Id, std::unique_ptr<VulkanBuffer>> buffers;
        std::unordered_map<Id, std::unique_ptr<VulkanShaderModule>> shaderModules;
        std::unordered_map<Id, std::unique_ptr<VulkanSampler>> samplers;
        std::unordered_map<Id, std::unique_ptr<VulkanTexture>> textures;
        std::unordered_map<Id, std::unique_ptr<VulkanTextureView>> textureViews;
        std::unordered_map<Id, std::unique_ptr<VulkanBindGroupLayout>> bindGroupLayouts;
        std::unordered_map<Id, std::unique_ptr<VulkanBindGroup>> bindGroups;
        std::unordered_map<Id, std::unique_ptr<VulkanPipelineLayout>> pipelineLayouts;
        std::unordered_map<Id, std::unique_ptr<VulkanRenderPipeline>> renderPipelines;
        std::unordered_map<Id, std::unique_ptr<VulkanCommandEncoder>> commandEncoders;
        std::unordered_map<Id, std::unique_ptr<VulkanCommandBufferWrapper>> commandBuffers;
        std::unordered_map<Id, std::unique_ptr<VulkanRenderPassEncoder>> renderPassEncoders;
    };

    struct VulkanInstance {
        VkInstance instance = VK_NULL_HANDLE;
        bool validationEnabled = false;
        InstanceDispatch dispatch{};
        std::unordered_map<Id, std::unique_ptr<VulkanSurface>> surfaces;
        std::unordered_map<Id, std::unique_ptr<VulkanDevice>> devices;
    };

    std::unordered_map<Id, std::unique_ptr<VulkanInstance>> g_instances;
    std::mutex g_registryMutex;

// Utility ------------------------------------------------------------------

    Id generateId() {
        return g_nextId.fetch_add(1, std::memory_order_relaxed);
    }

    template<typename Registry, typename Object>
    Id storeObject(Registry &registry, std::unique_ptr<Object> object) {
        const Id id = generateId();
        registry.emplace(id, std::move(object));
        return id;
    }

    template<typename Registry, typename Object>
    Object *getObject(Registry &registry, Id id) {
        auto it = registry.find(id);
        return it == registry.end() ? nullptr : it->second.get();
    }

    template<typename Registry, typename Object>
    std::unique_ptr<Object> removeObject(Registry &registry, Id id) {
        auto it = registry.find(id);
        if (it == registry.end()) return nullptr;
        auto ptr = std::move(it->second);
        registry.erase(it);
        return ptr;
    }

    uint32_t findMemoryType(VkPhysicalDevice physicalDevice, uint32_t typeFilter,
                            VkMemoryPropertyFlags properties) {
        VkPhysicalDeviceMemoryProperties memProperties;
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, &memProperties);

        for (uint32_t i = 0; i < memProperties.memoryTypeCount; ++i) {
            if ((typeFilter & (1u << i)) &&
                (memProperties.memoryTypes[i].propertyFlags & properties) == properties) {
                return i;
            }
        }

        throw std::runtime_error("Failed to find suitable Vulkan memory type");
    }

    VkShaderStageFlags toShaderStage(uint32_t mask) {
        VkShaderStageFlags flags = 0;
        if (mask & 0x1) flags |= VK_SHADER_STAGE_VERTEX_BIT;
        if (mask & 0x2) flags |= VK_SHADER_STAGE_FRAGMENT_BIT;
        if (mask & 0x4) flags |= VK_SHADER_STAGE_COMPUTE_BIT;
        return flags;
    }

    VkPrimitiveTopology toTopology(int topology) {
        switch (topology) {
            case 0:
                return VK_PRIMITIVE_TOPOLOGY_POINT_LIST;
            case 1:
                return VK_PRIMITIVE_TOPOLOGY_LINE_LIST;
            case 2:
                return VK_PRIMITIVE_TOPOLOGY_LINE_STRIP;
            case 3:
                return VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
            case 4:
                return VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
            default:
                return VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        }
    }

    VkCullModeFlags toCullMode(int cullMode) {
        switch (cullMode) {
            case 1:
                return VK_CULL_MODE_FRONT_BIT;
            case 2:
                return VK_CULL_MODE_BACK_BIT;
            default:
                return VK_CULL_MODE_NONE;
        }
    }

    VkBlendFactor toBlendFactor(int value) {
        switch (value) {
            case 1:
                return VK_BLEND_FACTOR_SRC_ALPHA;
            case 2:
                return VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
            case 3:
                return VK_BLEND_FACTOR_ONE;
            case 4:
                return VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA;
            default:
                return VK_BLEND_FACTOR_ONE;
        }
    }

    VkBlendOp toBlendOp(int value) {
        return value == 1 ? VK_BLEND_OP_ADD : VK_BLEND_OP_ADD;
    }

    VkFormat toVertexFormat(int value) {
        switch (value) {
            case 0:
                return VK_FORMAT_R32_SFLOAT;
            case 1:
                return VK_FORMAT_R32G32_SFLOAT;
            case 2:
                return VK_FORMAT_R32G32B32_SFLOAT;
            case 3:
                return VK_FORMAT_R32G32B32A32_SFLOAT;
            default:
                return VK_FORMAT_R32G32B32_SFLOAT;
        }
    }

    VkDescriptorType toDescriptorType(int resourceType) {
        switch (resourceType) {
            case 0:
                return VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
            case 1:
                return VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
            case 2:
                return VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE;
            case 3:
                return VK_DESCRIPTOR_TYPE_SAMPLER;
            case 4:
                return VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
            default:
                return VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
        }
    }

    VkFormat toColorFormat(int fmt) {
        switch (fmt) {
            case 0:
                return VK_FORMAT_R8G8B8A8_UNORM;
            case 1:
                return VK_FORMAT_B8G8R8A8_UNORM;
            case 2:
                return VK_FORMAT_R16G16B16A16_SFLOAT;
            default:
                return VK_FORMAT_B8G8R8A8_UNORM;
        }
    }

    bool isDepthFormat(VkFormat format) {
        switch (format) {
            case VK_FORMAT_D16_UNORM:
            case VK_FORMAT_D32_SFLOAT:
            case VK_FORMAT_D24_UNORM_S8_UINT:
                return true;
            default:
                return false;
    }
    }

    PFN_vkVoidFunction loadInstanceProc(VkInstance instance, const char *name) {
        return vkGetInstanceProcAddr(instance, name);
    }

    PFN_vkVoidFunction loadDeviceProc(VkDevice device, const char *name) {
        return vkGetDeviceProcAddr(device, name);
    }

    void populateInstanceDispatch(VulkanInstance &instance) {
        instance.dispatch.createAndroidSurfaceKHR = reinterpret_cast<PFN_vkCreateAndroidSurfaceKHR>(loadInstanceProc(
                instance.instance, "vkCreateAndroidSurfaceKHR"));
        instance.dispatch.destroySurfaceKHR = reinterpret_cast<PFN_vkDestroySurfaceKHR>(loadInstanceProc(
                instance.instance, "vkDestroySurfaceKHR"));
        instance.dispatch.getSurfaceSupportKHR = reinterpret_cast<PFN_vkGetPhysicalDeviceSurfaceSupportKHR>(loadInstanceProc(
                instance.instance, "vkGetPhysicalDeviceSurfaceSupportKHR"));
        instance.dispatch.getSurfaceCapabilitiesKHR = reinterpret_cast<PFN_vkGetPhysicalDeviceSurfaceCapabilitiesKHR>(loadInstanceProc(
                instance.instance, "vkGetPhysicalDeviceSurfaceCapabilitiesKHR"));
        instance.dispatch.getSurfaceFormatsKHR = reinterpret_cast<PFN_vkGetPhysicalDeviceSurfaceFormatsKHR>(loadInstanceProc(
                instance.instance, "vkGetPhysicalDeviceSurfaceFormatsKHR"));
        instance.dispatch.getSurfacePresentModesKHR = reinterpret_cast<PFN_vkGetPhysicalDeviceSurfacePresentModesKHR>(loadInstanceProc(
                instance.instance, "vkGetPhysicalDeviceSurfacePresentModesKHR"));
    }

    void populateDeviceDispatch(VulkanDevice &device) {
        device.dispatch.createSwapchainKHR = reinterpret_cast<PFN_vkCreateSwapchainKHR>(loadDeviceProc(
                device.device, "vkCreateSwapchainKHR"));
        device.dispatch.destroySwapchainKHR = reinterpret_cast<PFN_vkDestroySwapchainKHR>(loadDeviceProc(
                device.device, "vkDestroySwapchainKHR"));
        device.dispatch.getSwapchainImagesKHR = reinterpret_cast<PFN_vkGetSwapchainImagesKHR>(loadDeviceProc(
                device.device, "vkGetSwapchainImagesKHR"));
        device.dispatch.acquireNextImageKHR = reinterpret_cast<PFN_vkAcquireNextImageKHR>(loadDeviceProc(
                device.device, "vkAcquireNextImageKHR"));
        device.dispatch.queuePresentKHR = reinterpret_cast<PFN_vkQueuePresentKHR>(loadDeviceProc(
                device.device, "vkQueuePresentKHR"));
    }

    bool isValidationLayerAvailable(const char *layerName) {
        uint32_t layerCount = 0;
        vkEnumerateInstanceLayerProperties(&layerCount, nullptr);
        std::vector<VkLayerProperties> layers(layerCount);
        vkEnumerateInstanceLayerProperties(&layerCount, layers.data());
        for (const auto &layer: layers) {
            if (strcmp(layer.layerName, layerName) == 0) {
                return true;
            }
        }
        return false;
    }

    VkFormat chooseSurfaceFormat(const std::vector<VkSurfaceFormatKHR> &formats) {
        for (const auto &format: formats) {
            if (format.format == VK_FORMAT_B8G8R8A8_UNORM &&
                format.colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return format.format;
            }
        }
        return formats.empty() ? VK_FORMAT_B8G8R8A8_UNORM : formats.front().format;
    }

    VkExtent2D chooseExtent(const VkSurfaceCapabilitiesKHR &caps, uint32_t width, uint32_t height) {
        if (caps.currentExtent.width != std::numeric_limits<uint32_t>::max()) {
            return caps.currentExtent;
        }
        VkExtent2D extent{};
        extent.width = std::max(caps.minImageExtent.width,
                                std::min(caps.maxImageExtent.width, width));
        extent.height = std::max(caps.minImageExtent.height,
                                 std::min(caps.maxImageExtent.height, height));
        return extent;
    }

    uint32_t selectImageCount(const VkSurfaceCapabilitiesKHR &caps) {
        uint32_t count = caps.minImageCount + 1;
        if (caps.maxImageCount > 0 && count > caps.maxImageCount) {
            count = caps.maxImageCount;
        }
        return count;
    }

    VkRenderPass
    createRenderPass(VkDevice device, VkFormat colorFormat, VkImageLayout finalLayout) {
        VkAttachmentDescription colorAttachment{};
        colorAttachment.format = colorFormat;
        colorAttachment.samples = VK_SAMPLE_COUNT_1_BIT;
        colorAttachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
        colorAttachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
        colorAttachment.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
        colorAttachment.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
        colorAttachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        colorAttachment.finalLayout = finalLayout;

        VkAttachmentReference colorRef{};
        colorRef.attachment = 0;
        colorRef.layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

        VkSubpassDescription subpass{};
        subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
        subpass.colorAttachmentCount = 1;
        subpass.pColorAttachments = &colorRef;

        VkSubpassDependency dependency{};
        dependency.srcSubpass = VK_SUBPASS_EXTERNAL;
        dependency.dstSubpass = 0;
        dependency.srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dependency.dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        dependency.srcAccessMask = 0;
        dependency.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;

        VkRenderPassCreateInfo info{};
        info.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
        info.attachmentCount = 1;
        info.pAttachments = &colorAttachment;
        info.subpassCount = 1;
        info.pSubpasses = &subpass;
        info.dependencyCount = 1;
        info.pDependencies = &dependency;

        VkRenderPass renderPass = VK_NULL_HANDLE;
        if (vkCreateRenderPass(device, &info, nullptr, &renderPass) != VK_SUCCESS) {
            throw std::runtime_error("Failed to create render pass");
        }
        return renderPass;
    }

    VkPipelineShaderStageCreateInfo
    makeShaderStage(VkShaderStageFlagBits stage, VkShaderModule module) {
        VkPipelineShaderStageCreateInfo stageInfo{};
        stageInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        stageInfo.stage = stage;
        stageInfo.module = module;
        stageInfo.pName = "main";
        return stageInfo;
    }

    VkDescriptorType descriptorTypeForEntry(const BindGroupLayoutEntry &entry) {
        return entry.descriptorType;
    }

    VulkanInstance *requireInstance(Id instanceId) {
        auto it = g_instances.find(instanceId);
        if (it == g_instances.end()) {
            throw std::runtime_error("Invalid instance handle");
        }
        return it->second.get();
    }

    VulkanDevice *requireDevice(VulkanInstance &instance, Id deviceId) {
        auto it = instance.devices.find(deviceId);
        if (it == instance.devices.end()) {
            throw std::runtime_error("Invalid device handle");
        }
        return it->second.get();
    }

    VulkanSurface *requireSurface(VulkanInstance &instance, Id surfaceId) {
        auto it = instance.surfaces.find(surfaceId);
        if (it == instance.surfaces.end()) {
            throw std::runtime_error("Invalid surface handle");
        }
        return it->second.get();
    }

    VulkanSwapchain *requireSwapchain(VulkanSurface &surface, Id swapchainId) {
        auto it = surface.swapchains.find(swapchainId);
        if (it == surface.swapchains.end()) {
            throw std::runtime_error("Invalid swapchain handle");
        }
        return it->second.get();
    }

    VulkanBuffer *requireBuffer(VulkanDevice &device, Id bufferId) {
        auto it = device.buffers.find(bufferId);
        if (it == device.buffers.end()) {
            throw std::runtime_error("Invalid buffer handle");
        }
        return it->second.get();
    }

    VulkanShaderModule *requireShaderModule(VulkanDevice &device, Id shaderId) {
        auto it = device.shaderModules.find(shaderId);
        if (it == device.shaderModules.end()) {
            throw std::runtime_error("Invalid shader module handle");
        }
        return it->second.get();
    }

    VulkanSampler *requireSampler(VulkanDevice &device, Id samplerId) {
        auto it = device.samplers.find(samplerId);
        if (it == device.samplers.end()) {
            throw std::runtime_error("Invalid sampler handle");
        }
        return it->second.get();
    }

    VulkanTexture *requireTexture(VulkanDevice &device, Id textureId) {
        auto it = device.textures.find(textureId);
        if (it == device.textures.end()) {
            throw std::runtime_error("Invalid texture handle");
        }
        return it->second.get();
    }

    VulkanTextureView *requireTextureView(VulkanDevice &device, Id viewId) {
        auto it = device.textureViews.find(viewId);
        if (it == device.textureViews.end()) {
            throw std::runtime_error("Invalid texture view handle");
        }
        return it->second.get();
    }

    VulkanBindGroupLayout *requireBindGroupLayout(VulkanDevice &device, Id layoutId) {
        auto it = device.bindGroupLayouts.find(layoutId);
        if (it == device.bindGroupLayouts.end()) {
            throw std::runtime_error("Invalid bind group layout handle");
        }
        return it->second.get();
    }

    VulkanBindGroup *requireBindGroup(VulkanDevice &device, Id groupId) {
        auto it = device.bindGroups.find(groupId);
        if (it == device.bindGroups.end()) {
            throw std::runtime_error("Invalid bind group handle");
        }
        return it->second.get();
    }

    VulkanPipelineLayout *requirePipelineLayout(VulkanDevice &device, Id layoutId) {
        auto it = device.pipelineLayouts.find(layoutId);
        if (it == device.pipelineLayouts.end()) {
            throw std::runtime_error("Invalid pipeline layout handle");
        }
        return it->second.get();
    }

    VulkanRenderPipeline *requireRenderPipeline(VulkanDevice &device, Id pipelineId) {
        auto it = device.renderPipelines.find(pipelineId);
        if (it == device.renderPipelines.end()) {
            throw std::runtime_error("Invalid pipeline handle");
        }
        return it->second.get();
    }

    VulkanCommandEncoder *requireCommandEncoder(VulkanDevice &device, Id encoderId) {
        auto it = device.commandEncoders.find(encoderId);
        if (it == device.commandEncoders.end()) {
            throw std::runtime_error("Invalid command encoder handle");
        }
        return it->second.get();
    }

    VulkanCommandBufferWrapper *requireCommandBuffer(VulkanDevice &device, Id commandBufferId) {
        auto it = device.commandBuffers.find(commandBufferId);
        if (it == device.commandBuffers.end()) {
            throw std::runtime_error("Invalid command buffer handle");
        }
        return it->second.get();
    }

    VulkanRenderPassEncoder *requireRenderPassEncoder(VulkanDevice &device, Id encoderId) {
        auto it = device.renderPassEncoders.find(encoderId);
        if (it == device.renderPassEncoders.end()) {
            throw std::runtime_error("Invalid render pass encoder handle");
        }
        return it->second.get();
    }

    VkDescriptorPool createDescriptorPool(VkDevice device) {
        std::array<VkDescriptorPoolSize, 3> poolSizes{};
        poolSizes[0].type = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
        poolSizes[0].descriptorCount = 512;
        poolSizes[1].type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        poolSizes[1].descriptorCount = 256;
        poolSizes[2].type = VK_DESCRIPTOR_TYPE_SAMPLER;
        poolSizes[2].descriptorCount = 256;

        VkDescriptorPoolCreateInfo poolInfo{};
        poolInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
        poolInfo.poolSizeCount = static_cast<uint32_t>(poolSizes.size());
        poolInfo.pPoolSizes = poolSizes.data();
        poolInfo.maxSets = 512;

        VkDescriptorPool descriptorPool = VK_NULL_HANDLE;
        if (vkCreateDescriptorPool(device, &poolInfo, nullptr, &descriptorPool) != VK_SUCCESS) {
            throw std::runtime_error("Failed to create descriptor pool");
        }
        return descriptorPool;
    }

    void destroySwapchainInternal(VulkanDevice &device, VulkanSurface &surface, Id swapchainId) {
        auto swapchainPtr = removeObject(surface.swapchains, swapchainId);
        if (!swapchainPtr) return;

        vkDeviceWaitIdle(device.device);

        if (swapchainPtr->inFlightFence != VK_NULL_HANDLE) {
            vkDestroyFence(device.device, swapchainPtr->inFlightFence, nullptr);
        }
        if (swapchainPtr->renderFinishedSemaphore != VK_NULL_HANDLE) {
            vkDestroySemaphore(device.device, swapchainPtr->renderFinishedSemaphore, nullptr);
        }
        if (swapchainPtr->imageAvailableSemaphore != VK_NULL_HANDLE) {
            vkDestroySemaphore(device.device, swapchainPtr->imageAvailableSemaphore, nullptr);
        }
        for (auto framebuffer: swapchainPtr->framebuffers) {
            vkDestroyFramebuffer(device.device, framebuffer, nullptr);
        }
        if (swapchainPtr->renderPass != VK_NULL_HANDLE) {
            vkDestroyRenderPass(device.device, swapchainPtr->renderPass, nullptr);
        }
        for (Id texViewId: swapchainPtr->textureViewIds) {
            device.textureViews.erase(texViewId);
        }
        for (Id texId: swapchainPtr->textureIds) {
            device.textures.erase(texId);
    }
        if (swapchainPtr->swapchain != VK_NULL_HANDLE) {
            device.dispatch.destroySwapchainKHR(device.device, swapchainPtr->swapchain, nullptr);
        }
    }

// ----------------------------------------------------------------------------
// Vulkan instance / device / surface management
// ----------------------------------------------------------------------------

    Id createInstanceInternal(const char *appName, bool enableValidation) {
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

        VkInstance instanceHandle = VK_NULL_HANDLE;
        if (vkCreateInstance(&createInfo, nullptr, &instanceHandle) != VK_SUCCESS) {
            throw std::runtime_error("Failed to create Vulkan instance");
        }

        auto instance = std::make_unique<VulkanInstance>();
        instance->instance = instanceHandle;
        instance->validationEnabled = enableValidation;
        populateInstanceDispatch(*instance);

        Id instanceId = storeObject(g_instances, std::move(instance));
        VK_LOG_INFO("Created Vulkan instance (id=%" PRIu64 ")", instanceId);
        return instanceId;
    }

    Id createSurfaceInternal(VulkanInstance &instance, JNIEnv *env, jobject surfaceObj) {
        ANativeWindow *window = ANativeWindow_fromSurface(env, surfaceObj);
        if (!window) {
            throw std::runtime_error("Failed to obtain ANativeWindow from Surface");
        }

        VkAndroidSurfaceCreateInfoKHR surfaceInfo{};
        surfaceInfo.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
        surfaceInfo.window = window;

        VkSurfaceKHR surfaceHandle = VK_NULL_HANDLE;
        if (instance.dispatch.createAndroidSurfaceKHR(instance.instance, &surfaceInfo, nullptr,
                                                      &surfaceHandle) != VK_SUCCESS) {
            ANativeWindow_release(window);
            throw std::runtime_error("Failed to create Android Vulkan surface");
        }

        auto surface = std::make_unique<VulkanSurface>();
        surface->surface = surfaceHandle;
        surface->window = window;

        Id surfaceId = storeObject(instance.surfaces, std::move(surface));
        VK_LOG_INFO("Created Vulkan surface (surface=%" PRIu64 ")", surfaceId);
        return surfaceId;
    }

    Id createDeviceInternal(VulkanInstance &instance, bool requestDiscrete) {
        uint32_t deviceCount = 0;
        vkEnumeratePhysicalDevices(instance.instance, &deviceCount, nullptr);
        if (deviceCount == 0) {
            throw std::runtime_error("No Vulkan devices available");
        }
        std::vector<VkPhysicalDevice> physicalDevices(deviceCount);
        vkEnumeratePhysicalDevices(instance.instance, &deviceCount, physicalDevices.data());

        VkPhysicalDevice selectedDevice = VK_NULL_HANDLE;
        uint32_t graphicsQueueFamily = UINT32_MAX;
        uint32_t presentQueueFamily = UINT32_MAX;

        for (auto candidate: physicalDevices) {
            VkPhysicalDeviceProperties props;
            vkGetPhysicalDeviceProperties(candidate, &props);
            if (requestDiscrete && props.deviceType != VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
                continue;
            }

            uint32_t queueCount = 0;
            vkGetPhysicalDeviceQueueFamilyProperties(candidate, &queueCount, nullptr);
            std::vector<VkQueueFamilyProperties> queueFamilies(queueCount);
            vkGetPhysicalDeviceQueueFamilyProperties(candidate, &queueCount, queueFamilies.data());

            for (uint32_t i = 0; i < queueCount; ++i) {
                bool graphics = (queueFamilies[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0;
                if (!graphics) continue;

                bool presentSupported = true;
                // If there are surfaces, validate present support against the first surface
                if (!instance.surfaces.empty()) {
                    VkBool32 supported = VK_FALSE;
                    auto &surfaceEntry = *instance.surfaces.begin();
                    instance.dispatch.getSurfaceSupportKHR(candidate, i,
                                                           surfaceEntry.second->surface,
                                                           &supported);
                    presentSupported = supported == VK_TRUE;
                }

                if (presentSupported) {
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
            selectedDevice = physicalDevices.front();
            graphicsQueueFamily = 0;
            presentQueueFamily = 0;
        }

        float queuePriority = 1.0f;
        VkDeviceQueueCreateInfo queueInfo{};
        queueInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
        queueInfo.queueFamilyIndex = graphicsQueueFamily;
        queueInfo.queueCount = 1;
        queueInfo.pQueuePriorities = &queuePriority;

        std::vector<const char *> extensions = {
                VK_KHR_SWAPCHAIN_EXTENSION_NAME
        };

        VkPhysicalDeviceFeatures features{};

        VkDeviceCreateInfo deviceInfo{};
        deviceInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
        deviceInfo.queueCreateInfoCount = 1;
        deviceInfo.pQueueCreateInfos = &queueInfo;
        deviceInfo.pEnabledFeatures = &features;
        deviceInfo.enabledExtensionCount = static_cast<uint32_t>(extensions.size());
        deviceInfo.ppEnabledExtensionNames = extensions.data();

        VkDevice deviceHandle = VK_NULL_HANDLE;
        if (vkCreateDevice(selectedDevice, &deviceInfo, nullptr, &deviceHandle) != VK_SUCCESS) {
            throw std::runtime_error("Failed to create Vulkan device");
        }

        auto device = std::make_unique<VulkanDevice>();
        device->instance = &instance;
        device->physicalDevice = selectedDevice;
        device->device = deviceHandle;
        device->graphicsQueueFamily = graphicsQueueFamily;
        device->presentQueueFamily = presentQueueFamily;
        vkGetDeviceQueue(deviceHandle, graphicsQueueFamily, 0, &device->graphicsQueue);
        vkGetDeviceQueue(deviceHandle, presentQueueFamily, 0, &device->presentQueue);
        populateDeviceDispatch(*device);

        VkCommandPoolCreateInfo poolInfo{};
        poolInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
        poolInfo.queueFamilyIndex = graphicsQueueFamily;
        poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;

        if (vkCreateCommandPool(deviceHandle, &poolInfo, nullptr, &device->commandPool) !=
            VK_SUCCESS) {
            throw std::runtime_error("Failed to create command pool");
        }

        device->descriptorPool = createDescriptorPool(deviceHandle);

        Id deviceId = storeObject(instance.devices, std::move(device));
        VK_LOG_INFO("Created Vulkan device (device=%" PRIu64 ")", deviceId);
        return deviceId;
    }

    void destroyDeviceInternal(VulkanInstance &instance, Id deviceId) {
        auto devicePtr = removeObject(instance.devices, deviceId);
        if (!devicePtr) return;

        vkDeviceWaitIdle(devicePtr->device);

        for (auto &surfaceEntry: instance.surfaces) {
            std::vector<Id> swapchainIds;
            swapchainIds.reserve(surfaceEntry.second->swapchains.size());
            for (const auto &swapEntry: surfaceEntry.second->swapchains) {
                swapchainIds.push_back(swapEntry.first);
            }
            for (Id swapId: swapchainIds) {
                destroySwapchainInternal(*devicePtr, *surfaceEntry.second, swapId);
            }
        }

        for (auto &entry: devicePtr->renderPipelines) {
            if (entry.second->pipeline != VK_NULL_HANDLE) {
                vkDestroyPipeline(devicePtr->device, entry.second->pipeline, nullptr);
            }
            if (entry.second->renderPass != VK_NULL_HANDLE) {
                vkDestroyRenderPass(devicePtr->device, entry.second->renderPass, nullptr);
            }
        }

        for (auto &entry: devicePtr->pipelineLayouts) {
            if (entry.second->layout != VK_NULL_HANDLE) {
                vkDestroyPipelineLayout(devicePtr->device, entry.second->layout, nullptr);
            }
        }

        for (auto &entry: devicePtr->bindGroups) {
            // Descriptor sets freed with pool reset
            (void) entry;
        }

        for (auto &entry: devicePtr->bindGroupLayouts) {
            if (entry.second->layout != VK_NULL_HANDLE) {
                vkDestroyDescriptorSetLayout(devicePtr->device, entry.second->layout, nullptr);
            }
        }

        for (auto &entry: devicePtr->samplers) {
            if (entry.second->sampler != VK_NULL_HANDLE) {
                vkDestroySampler(devicePtr->device, entry.second->sampler, nullptr);
            }
        }

        for (auto &entry: devicePtr->textureViews) {
            if (entry.second->view != VK_NULL_HANDLE) {
                vkDestroyImageView(devicePtr->device, entry.second->view, nullptr);
            }
        }

        for (auto &entry: devicePtr->textures) {
            if (entry.second->ownsImage && entry.second->image != VK_NULL_HANDLE) {
                vkDestroyImage(devicePtr->device, entry.second->image, nullptr);
            }
            if (entry.second->ownsMemory && entry.second->memory != VK_NULL_HANDLE) {
                vkFreeMemory(devicePtr->device, entry.second->memory, nullptr);
            }
        }

        for (auto &entry: devicePtr->buffers) {
            if (entry.second->buffer != VK_NULL_HANDLE) {
                vkDestroyBuffer(devicePtr->device, entry.second->buffer, nullptr);
            }
            if (entry.second->memory != VK_NULL_HANDLE) {
                vkFreeMemory(devicePtr->device, entry.second->memory, nullptr);
            }
        }

        if (devicePtr->descriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(devicePtr->device, devicePtr->descriptorPool, nullptr);
        }

        if (devicePtr->commandPool != VK_NULL_HANDLE) {
            vkDestroyCommandPool(devicePtr->device, devicePtr->commandPool, nullptr);
        }

        vkDestroyDevice(devicePtr->device, nullptr);
    }

    void destroySurfaceInternal(VulkanInstance &instance, Id surfaceId) {
        auto surfacePtr = removeObject(instance.surfaces, surfaceId);
        if (!surfacePtr) return;

        std::vector<std::pair<Id, VulkanDevice *>> swapInfos;
        swapInfos.reserve(surfacePtr->swapchains.size());
        for (auto &swapEntry: surfacePtr->swapchains) {
            swapInfos.emplace_back(swapEntry.first, swapEntry.second->device);
        }
        for (auto &info: swapInfos) {
            destroySwapchainInternal(*info.second, *surfacePtr, info.first);
        }

        if (surfacePtr->surface != VK_NULL_HANDLE) {
            instance.dispatch.destroySurfaceKHR(instance.instance, surfacePtr->surface, nullptr);
        }

        if (surfacePtr->window) {
            ANativeWindow_release(surfacePtr->window);
        }
    }

// ----------------------------------------------------------------------------
// Swapchain management
// ----------------------------------------------------------------------------

    Id
    createSwapchainInternal(VulkanInstance &instance, VulkanDevice &device, VulkanSurface &surface,
                            uint32_t width, uint32_t height) {
        VkSurfaceCapabilitiesKHR capabilities{};
        instance.dispatch.getSurfaceCapabilitiesKHR(device.physicalDevice, surface.surface,
                                                    &capabilities);

        uint32_t formatCount = 0;
        instance.dispatch.getSurfaceFormatsKHR(device.physicalDevice, surface.surface, &formatCount,
                                               nullptr);
        std::vector<VkSurfaceFormatKHR> formats(formatCount);
        instance.dispatch.getSurfaceFormatsKHR(device.physicalDevice, surface.surface, &formatCount,
                                               formats.data());

        VkFormat surfaceFormat = chooseSurfaceFormat(formats);
        VkExtent2D extent = chooseExtent(capabilities, width, height);
        uint32_t imageCount = selectImageCount(capabilities);

        VkSwapchainCreateInfoKHR createInfo{};
        createInfo.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
        createInfo.surface = surface.surface;
        createInfo.minImageCount = imageCount;
        createInfo.imageFormat = surfaceFormat;
        createInfo.imageColorSpace = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
        createInfo.imageExtent = extent;
        createInfo.imageArrayLayers = 1;
        createInfo.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;

        uint32_t queueFamilyIndices[] = {device.graphicsQueueFamily, device.presentQueueFamily};
        if (device.graphicsQueueFamily != device.presentQueueFamily) {
            createInfo.imageSharingMode = VK_SHARING_MODE_CONCURRENT;
            createInfo.queueFamilyIndexCount = 2;
            createInfo.pQueueFamilyIndices = queueFamilyIndices;
        } else {
            createInfo.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
        }

        createInfo.preTransform = capabilities.currentTransform;
        createInfo.compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
        createInfo.presentMode = VK_PRESENT_MODE_FIFO_KHR;
        createInfo.clipped = VK_TRUE;

        VkSwapchainKHR swapchainHandle = VK_NULL_HANDLE;
        if (device.dispatch.createSwapchainKHR(device.device, &createInfo, nullptr,
                                               &swapchainHandle) != VK_SUCCESS) {
            throw std::runtime_error("Failed to create swapchain");
        }

        auto swapchain = std::make_unique<VulkanSwapchain>();
        swapchain->device = &device;
        swapchain->swapchain = swapchainHandle;
        swapchain->format = surfaceFormat;
        swapchain->extent = extent;
        swapchain->renderPass = createRenderPass(device.device, surfaceFormat,
                                                 VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

        uint32_t actualImageCount = 0;
        device.dispatch.getSwapchainImagesKHR(device.device, swapchainHandle, &actualImageCount,
                                              nullptr);
        swapchain->images.resize(actualImageCount);
        device.dispatch.getSwapchainImagesKHR(device.device, swapchainHandle, &actualImageCount,
                                              swapchain->images.data());

        swapchain->textureIds.reserve(actualImageCount);
        swapchain->textureViewIds.reserve(actualImageCount);
        swapchain->framebuffers.resize(actualImageCount);

        for (uint32_t i = 0; i < actualImageCount; ++i) {
            auto texture = std::make_unique<VulkanTexture>();
            texture->image = swapchain->images[i];
            texture->format = surfaceFormat;
            texture->width = extent.width;
            texture->height = extent.height;
            texture->ownsImage = false;
            texture->ownsMemory = false;
            Id textureId = storeObject(device.textures, std::move(texture));
            swapchain->textureIds.push_back(textureId);

            VkImageViewCreateInfo viewInfo{};
            viewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
            viewInfo.image = swapchain->images[i];
            viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
            viewInfo.format = surfaceFormat;
            viewInfo.components = {
                    VK_COMPONENT_SWIZZLE_IDENTITY, VK_COMPONENT_SWIZZLE_IDENTITY,
                    VK_COMPONENT_SWIZZLE_IDENTITY, VK_COMPONENT_SWIZZLE_IDENTITY
            };
            viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
            viewInfo.subresourceRange.baseMipLevel = 0;
            viewInfo.subresourceRange.levelCount = 1;
            viewInfo.subresourceRange.baseArrayLayer = 0;
            viewInfo.subresourceRange.layerCount = 1;

            VkImageView imageView = VK_NULL_HANDLE;
            if (vkCreateImageView(device.device, &viewInfo, nullptr, &imageView) != VK_SUCCESS) {
                throw std::runtime_error("Failed to create swapchain image view");
            }

            auto textureView = std::make_unique<VulkanTextureView>();
            textureView->texture = requireTexture(device, textureId);
            textureView->view = imageView;
            Id viewId = storeObject(device.textureViews, std::move(textureView));
            swapchain->textureViewIds.push_back(viewId);

            VkImageView attachments[] = {imageView};
            VkFramebufferCreateInfo framebufferInfo{};
            framebufferInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
            framebufferInfo.renderPass = swapchain->renderPass;
            framebufferInfo.attachmentCount = 1;
            framebufferInfo.pAttachments = attachments;
            framebufferInfo.width = extent.width;
            framebufferInfo.height = extent.height;
            framebufferInfo.layers = 1;

            if (vkCreateFramebuffer(device.device, &framebufferInfo, nullptr,
                                    &swapchain->framebuffers[i]) != VK_SUCCESS) {
                throw std::runtime_error("Failed to create swapchain framebuffer");
            }
        }

        VkSemaphoreCreateInfo semaphoreInfo{};
        semaphoreInfo.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;

        VkFenceCreateInfo fenceInfo{};
        fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
        fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;

        if (vkCreateSemaphore(device.device, &semaphoreInfo, nullptr,
                              &swapchain->imageAvailableSemaphore) != VK_SUCCESS) {
            throw std::runtime_error("Failed to create swapchain semaphore (imageAvailable)");
        }
        if (vkCreateSemaphore(device.device, &semaphoreInfo, nullptr,
                              &swapchain->renderFinishedSemaphore) != VK_SUCCESS) {
            throw std::runtime_error("Failed to create swapchain semaphore (renderFinished)");
        }
        if (vkCreateFence(device.device, &fenceInfo, nullptr, &swapchain->inFlightFence) !=
            VK_SUCCESS) {
            throw std::runtime_error("Failed to create swapchain fence");
        }

        Id swapchainId = storeObject(surface.swapchains, std::move(swapchain));
        VK_LOG_INFO("Created swapchain (swapchain=%" PRIu64 ")", swapchainId);
        return swapchainId;
    }

    std::unique_ptr<VulkanSurfaceFrame>
    acquireFrameInternal(VulkanDevice &device, VulkanSurface &surface, VulkanSwapchain &swapchain) {
        vkWaitForFences(device.device, 1, &swapchain.inFlightFence, VK_TRUE, UINT64_MAX);
        vkResetFences(device.device, 1, &swapchain.inFlightFence);

        uint32_t imageIndex = 0;
        VkResult res = device.dispatch.acquireNextImageKHR(
                device.device,
                swapchain.swapchain,
                UINT64_MAX,
                swapchain.imageAvailableSemaphore,
                VK_NULL_HANDLE,
                &imageIndex);

        if (res == VK_ERROR_OUT_OF_DATE_KHR || res == VK_SUBOPTIMAL_KHR) {
            throw std::runtime_error("Swapchain out of date, recreation required");
        }
        if (res != VK_SUCCESS) {
            throw std::runtime_error("Failed to acquire swapchain image");
        }

        auto frame = std::make_unique<VulkanSurfaceFrame>();
        frame->swapchain = &swapchain;
        frame->imageIndex = imageIndex;
        frame->textureId = swapchain.textureIds[imageIndex];
        frame->viewId = swapchain.textureViewIds[imageIndex];
        return frame;
    }

    void presentFrameInternal(VulkanDevice &device, VulkanSwapchain &swapchain,
                              VulkanCommandBufferWrapper &commandBuffer, uint32_t imageIndex) {
        VkSemaphore waitSemaphores[] = {swapchain.renderFinishedSemaphore};
        VkSwapchainKHR swapchains[] = {swapchain.swapchain};

        VkPresentInfoKHR presentInfo{};
        presentInfo.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
        presentInfo.waitSemaphoreCount = 1;
        presentInfo.pWaitSemaphores = waitSemaphores;
        presentInfo.swapchainCount = 1;
        presentInfo.pSwapchains = swapchains;
        presentInfo.pImageIndices = &imageIndex;

        VkResult res = device.dispatch.queuePresentKHR(device.presentQueue, &presentInfo);
        if (res == VK_ERROR_OUT_OF_DATE_KHR || res == VK_SUBOPTIMAL_KHR) {
            // surface needs resize; signal via exception
            throw std::runtime_error("Swapchain presentation failed; surface outdated");
        }
        if (res != VK_SUCCESS) {
            throw std::runtime_error("Failed to present swapchain image");
        }
    }

// ----------------------------------------------------------------------------
// Buffer management
// ----------------------------------------------------------------------------

    Id createBufferInternal(VulkanDevice &device, VkDeviceSize size, VkBufferUsageFlags usage,
                            VkMemoryPropertyFlags properties) {
        VkBufferCreateInfo bufferInfo{};
        bufferInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
        bufferInfo.size = size;
        bufferInfo.usage = usage;
        bufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

        VkBuffer buffer = VK_NULL_HANDLE;
        if (vkCreateBuffer(device.device, &bufferInfo, nullptr, &buffer) != VK_SUCCESS) {
            throw std::runtime_error("Failed to create Vulkan buffer");
        }

        VkMemoryRequirements memRequirements;
        vkGetBufferMemoryRequirements(device.device, buffer, &memRequirements);

        VkMemoryAllocateInfo allocInfo{};
        allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
        allocInfo.allocationSize = memRequirements.size;
        allocInfo.memoryTypeIndex = findMemoryType(device.physicalDevice,
                                                   memRequirements.memoryTypeBits, properties);

        VkDeviceMemory bufferMemory = VK_NULL_HANDLE;
        if (vkAllocateMemory(device.device, &allocInfo, nullptr, &bufferMemory) != VK_SUCCESS) {
            throw std::runtime_error("Failed to allocate Vulkan buffer memory");
        }

        vkBindBufferMemory(device.device, buffer, bufferMemory, 0);

        auto bufferObj = std::make_unique<VulkanBuffer>();
        bufferObj->buffer = buffer;
        bufferObj->memory = bufferMemory;
        bufferObj->size = size;
        bufferObj->usage = usage;

        Id bufferId = storeObject(device.buffers, std::move(bufferObj));
        return bufferId;
    }

    void
    writeBufferInternal(VulkanDevice &device, VulkanBuffer &buffer, const void *data, size_t size,
                        size_t offset) {
        void *mapped = nullptr;
        vkMapMemory(device.device, buffer.memory, offset, size, 0, &mapped);
        std::memcpy(mapped, data, size);
        vkUnmapMemory(device.device, buffer.memory);
    }

// ----------------------------------------------------------------------------
// Shader modules
// ----------------------------------------------------------------------------

    Id createShaderModuleInternal(VulkanDevice &device, const std::vector<std::uint32_t> &spirv) {
        VkShaderModuleCreateInfo createInfo{};
        createInfo.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
        createInfo.codeSize = spirv.size() * sizeof(uint32_t);
        createInfo.pCode = spirv.data();

        VkShaderModule module = VK_NULL_HANDLE;
        if (vkCreateShaderModule(device.device, &createInfo, nullptr, &module) != VK_SUCCESS) {
            throw std::runtime_error("Failed to create shader module");
        }

        auto shader = std::make_unique<VulkanShaderModule>();
        shader->module = module;
        return storeObject(device.shaderModules, std::move(shader));
    }

// ----------------------------------------------------------------------------
// Samplers / Textures
// ----------------------------------------------------------------------------

    Id createSamplerInternal(VulkanDevice &device, VkFilter minFilter, VkFilter magFilter) {
        VkSamplerCreateInfo createInfo{};
        createInfo.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
        createInfo.magFilter = magFilter;
        createInfo.minFilter = minFilter;
        createInfo.mipmapMode = VK_SAMPLER_MIPMAP_MODE_LINEAR;
        createInfo.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        createInfo.addressModeV = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        createInfo.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
        createInfo.mipLodBias = 0.0f;
        createInfo.compareOp = VK_COMPARE_OP_ALWAYS;
        createInfo.minLod = 0.0f;
        createInfo.maxLod = 0.0f;

        VkSampler sampler = VK_NULL_HANDLE;
        if (vkCreateSampler(device.device, &createInfo, nullptr, &sampler) != VK_SUCCESS) {
            throw std::runtime_error("Failed to create sampler");
        }

        auto samplerObj = std::make_unique<VulkanSampler>();
        samplerObj->sampler = sampler;
        return storeObject(device.samplers, std::move(samplerObj));
    }

    Id createTextureInternal(VulkanDevice &device, VkFormat format, uint32_t width, uint32_t height,
                             VkImageUsageFlags usage) {
        VkImageCreateInfo imageInfo{};
        imageInfo.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
        imageInfo.imageType = VK_IMAGE_TYPE_2D;
        imageInfo.extent.width = width;
        imageInfo.extent.height = height;
        imageInfo.extent.depth = 1;
        imageInfo.mipLevels = 1;
        imageInfo.arrayLayers = 1;
        imageInfo.format = format;
        imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
        imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        imageInfo.usage = usage;
        imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
        imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;

        VkImage image = VK_NULL_HANDLE;
        if (vkCreateImage(device.device, &imageInfo, nullptr, &image) != VK_SUCCESS) {
            throw std::runtime_error("Failed to create image");
        }

        VkMemoryRequirements memRequirements;
        vkGetImageMemoryRequirements(device.device, image, &memRequirements);

        VkMemoryAllocateInfo allocInfo{};
        allocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
        allocInfo.allocationSize = memRequirements.size;
        allocInfo.memoryTypeIndex = findMemoryType(device.physicalDevice,
                                                   memRequirements.memoryTypeBits,
                                                   VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

        VkDeviceMemory memory = VK_NULL_HANDLE;
        if (vkAllocateMemory(device.device, &allocInfo, nullptr, &memory) != VK_SUCCESS) {
            throw std::runtime_error("Failed to allocate image memory");
        }

        vkBindImageMemory(device.device, image, memory, 0);

        auto texture = std::make_unique<VulkanTexture>();
        texture->image = image;
        texture->memory = memory;
        texture->format = format;
        texture->width = width;
        texture->height = height;
        texture->ownsImage = true;
        texture->ownsMemory = true;
        return storeObject(device.textures, std::move(texture));
    }

    Id createTextureViewInternal(VulkanDevice &device, VulkanTexture &texture,
                                 VkImageViewType viewType, VkFormat overrideFormat) {
        VkImageViewCreateInfo viewInfo{};
        viewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
        viewInfo.image = texture.image;
        viewInfo.viewType = viewType;
        viewInfo.format = overrideFormat == VK_FORMAT_UNDEFINED ? texture.format : overrideFormat;
        viewInfo.components = {
                VK_COMPONENT_SWIZZLE_IDENTITY,
                VK_COMPONENT_SWIZZLE_IDENTITY,
                VK_COMPONENT_SWIZZLE_IDENTITY,
                VK_COMPONENT_SWIZZLE_IDENTITY
        };
        viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        viewInfo.subresourceRange.baseMipLevel = 0;
        viewInfo.subresourceRange.levelCount = 1;
        viewInfo.subresourceRange.baseArrayLayer = 0;
        viewInfo.subresourceRange.layerCount = 1;

        VkImageView view = VK_NULL_HANDLE;
        if (vkCreateImageView(device.device, &viewInfo, nullptr, &view) != VK_SUCCESS) {
            throw std::runtime_error("Failed to create image view");
        }

        auto textureView = std::make_unique<VulkanTextureView>();
        textureView->texture = &texture;
        textureView->view = view;
        return storeObject(device.textureViews, std::move(textureView));
    }

// ----------------------------------------------------------------------------
// Bind group layouts and descriptor sets
// ----------------------------------------------------------------------------

    Id createBindGroupLayoutInternal(VulkanDevice &device,
                                     const std::vector<BindGroupLayoutEntry> &entries) {
        std::vector<VkDescriptorSetLayoutBinding> bindings;
        bindings.reserve(entries.size());

        for (const auto &entry: entries) {
            VkDescriptorSetLayoutBinding binding{};
            binding.binding = entry.binding;
            binding.descriptorType = entry.descriptorType;
            binding.descriptorCount = 1;
            binding.stageFlags = entry.stageFlags;
            bindings.push_back(binding);
        }

        VkDescriptorSetLayoutCreateInfo layoutInfo{};
        layoutInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
        layoutInfo.bindingCount = static_cast<uint32_t>(bindings.size());
        layoutInfo.pBindings = bindings.data();

        VkDescriptorSetLayout layout = VK_NULL_HANDLE;
        if (vkCreateDescriptorSetLayout(device.device, &layoutInfo, nullptr, &layout) !=
            VK_SUCCESS) {
            throw std::runtime_error("Failed to create descriptor set layout");
        }

        auto layoutObj = std::make_unique<VulkanBindGroupLayout>();
        layoutObj->layout = layout;
        layoutObj->entries = entries;
        return storeObject(device.bindGroupLayouts, std::move(layoutObj));
    }

    Id createBindGroupInternal(VulkanDevice &device, VulkanBindGroupLayout &layout,
                               const std::vector<BindGroupEntry> &entries) {
        VkDescriptorSetAllocateInfo allocInfo{};
        allocInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
        allocInfo.descriptorPool = device.descriptorPool;
        allocInfo.descriptorSetCount = 1;
        allocInfo.pSetLayouts = &layout.layout;

        VkDescriptorSet descriptorSet = VK_NULL_HANDLE;
        if (vkAllocateDescriptorSets(device.device, &allocInfo, &descriptorSet) != VK_SUCCESS) {
            throw std::runtime_error("Failed to allocate descriptor set");
        }

        std::vector<VkWriteDescriptorSet> writes;
        std::vector<VkDescriptorBufferInfo> bufferInfos;
        std::vector<VkDescriptorImageInfo> imageInfos;
        writes.reserve(entries.size());

        for (const auto &entry: entries) {
            VkWriteDescriptorSet write{};
            write.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
            write.dstSet = descriptorSet;
            write.dstBinding = entry.binding;
            write.descriptorCount = 1;

            if (entry.buffer) {
                bufferInfos.emplace_back();
                bufferInfos.back().buffer = entry.buffer->buffer;
                bufferInfos.back().offset = entry.offset;
                bufferInfos.back().range = entry.range;
                write.descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
                write.pBufferInfo = &bufferInfos.back();
            } else if (entry.textureView && entry.sampler) {
                imageInfos.emplace_back();
                imageInfos.back().imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
                imageInfos.back().imageView = entry.textureView->view;
                imageInfos.back().sampler = entry.sampler->sampler;
                write.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
                write.pImageInfo = &imageInfos.back();
            } else if (entry.textureView) {
                imageInfos.emplace_back();
                imageInfos.back().imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
                imageInfos.back().imageView = entry.textureView->view;
                imageInfos.back().sampler = VK_NULL_HANDLE;
                write.descriptorType = VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE;
                write.pImageInfo = &imageInfos.back();
            } else if (entry.sampler) {
                imageInfos.emplace_back();
                imageInfos.back().imageLayout = VK_IMAGE_LAYOUT_UNDEFINED;
                imageInfos.back().imageView = VK_NULL_HANDLE;
                imageInfos.back().sampler = entry.sampler->sampler;
                write.descriptorType = VK_DESCRIPTOR_TYPE_SAMPLER;
                write.pImageInfo = &imageInfos.back();
            } else {
                throw std::runtime_error("Unsupported bind group entry");
            }

            writes.push_back(write);
        }

        vkUpdateDescriptorSets(device.device, static_cast<uint32_t>(writes.size()), writes.data(),
                               0, nullptr);

        auto bindGroup = std::make_unique<VulkanBindGroup>();
        bindGroup->layout = &layout;
        bindGroup->descriptorSet = descriptorSet;
        bindGroup->entries = entries;

        // store uses copy of entries - allowed since structures are simple
        Id bindGroupId = storeObject(device.bindGroups, std::move(bindGroup));
        return bindGroupId;
    }

// ----------------------------------------------------------------------------
// Pipeline layouts and render pipelines
// ----------------------------------------------------------------------------

    Id createPipelineLayoutInternal(VulkanDevice &device,
                                    const std::vector<VulkanBindGroupLayout *> &layouts) {
        std::vector<VkDescriptorSetLayout> setLayouts;
        setLayouts.reserve(layouts.size());
        for (auto *layout: layouts) {
            setLayouts.push_back(layout->layout);
        }

        VkPipelineLayoutCreateInfo info{};
        info.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
        info.setLayoutCount = static_cast<uint32_t>(setLayouts.size());
        info.pSetLayouts = setLayouts.data();

        VkPipelineLayout pipelineLayout = VK_NULL_HANDLE;
        if (vkCreatePipelineLayout(device.device, &info, nullptr, &pipelineLayout) != VK_SUCCESS) {
            throw std::runtime_error("Failed to create pipeline layout");
    }

        auto layoutObj = std::make_unique<VulkanPipelineLayout>();
        layoutObj->layout = pipelineLayout;
        layoutObj->setLayouts = layouts;
        return storeObject(device.pipelineLayouts, std::move(layoutObj));
    }

    Id createRenderPipelineInternal(
            VulkanDevice &device,
            VulkanPipelineLayout &pipelineLayout,
            VulkanShaderModule *vertexShader,
            VulkanShaderModule *fragmentShader,
            const std::vector<VkVertexInputBindingDescription> &bindings,
            const std::vector<VkVertexInputAttributeDescription> &attributes,
            VkPrimitiveTopology topology,
            VkCullModeFlags cullMode,
            bool enableBlend,
            VkFormat colorFormat,
            VkRenderPass renderPass) {
        VkPipelineShaderStageCreateInfo vertStage = makeShaderStage(VK_SHADER_STAGE_VERTEX_BIT,
                                                                    vertexShader->module);
        VkPipelineShaderStageCreateInfo fragStage = makeShaderStage(VK_SHADER_STAGE_FRAGMENT_BIT,
                                                                    fragmentShader->module);
        VkPipelineShaderStageCreateInfo stages[] = {vertStage, fragStage};

        VkPipelineVertexInputStateCreateInfo vertexInput{};
        vertexInput.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
        vertexInput.vertexBindingDescriptionCount = static_cast<uint32_t>(bindings.size());
        vertexInput.pVertexBindingDescriptions = bindings.data();
        vertexInput.vertexAttributeDescriptionCount = static_cast<uint32_t>(attributes.size());
        vertexInput.pVertexAttributeDescriptions = attributes.data();

        VkPipelineInputAssemblyStateCreateInfo inputAssembly{};
        inputAssembly.sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
        inputAssembly.topology = topology;
        inputAssembly.primitiveRestartEnable = VK_FALSE;

        VkPipelineViewportStateCreateInfo viewportState{};
        viewportState.sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
        viewportState.viewportCount = 1;
        viewportState.scissorCount = 1;

        VkPipelineRasterizationStateCreateInfo rasterizer{};
        rasterizer.sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
        rasterizer.depthClampEnable = VK_FALSE;
        rasterizer.rasterizerDiscardEnable = VK_FALSE;
        rasterizer.polygonMode = VK_POLYGON_MODE_FILL;
        rasterizer.cullMode = cullMode;
        rasterizer.frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE;
        rasterizer.depthBiasEnable = VK_FALSE;
        rasterizer.lineWidth = 1.0f;

        VkPipelineMultisampleStateCreateInfo multisample{};
        multisample.sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
        multisample.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;

        VkPipelineColorBlendAttachmentState colorBlendAttachment{};
        colorBlendAttachment.colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
                                              VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;
        if (enableBlend) {
            colorBlendAttachment.blendEnable = VK_TRUE;
            colorBlendAttachment.srcColorBlendFactor = VK_BLEND_FACTOR_SRC_ALPHA;
            colorBlendAttachment.dstColorBlendFactor = VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
            colorBlendAttachment.colorBlendOp = VK_BLEND_OP_ADD;
            colorBlendAttachment.srcAlphaBlendFactor = VK_BLEND_FACTOR_ONE;
            colorBlendAttachment.dstAlphaBlendFactor = VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
            colorBlendAttachment.alphaBlendOp = VK_BLEND_OP_ADD;
        } else {
            colorBlendAttachment.blendEnable = VK_FALSE;
        }

        VkPipelineColorBlendStateCreateInfo colorBlend{};
        colorBlend.sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
        colorBlend.attachmentCount = 1;
        colorBlend.pAttachments = &colorBlendAttachment;

        std::array<VkDynamicState, 2> dynamicStates = {VK_DYNAMIC_STATE_VIEWPORT,
                                                       VK_DYNAMIC_STATE_SCISSOR};
        VkPipelineDynamicStateCreateInfo dynamicState{};
        dynamicState.sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO;
        dynamicState.dynamicStateCount = static_cast<uint32_t>(dynamicStates.size());
        dynamicState.pDynamicStates = dynamicStates.data();

        VkGraphicsPipelineCreateInfo pipelineInfo{};
        pipelineInfo.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
        pipelineInfo.stageCount = 2;
        pipelineInfo.pStages = stages;
        pipelineInfo.pVertexInputState = &vertexInput;
        pipelineInfo.pInputAssemblyState = &inputAssembly;
        pipelineInfo.pViewportState = &viewportState;
        pipelineInfo.pRasterizationState = &rasterizer;
        pipelineInfo.pMultisampleState = &multisample;
        pipelineInfo.pColorBlendState = &colorBlend;
        pipelineInfo.pDynamicState = &dynamicState;
        pipelineInfo.layout = pipelineLayout.layout;
        pipelineInfo.renderPass = renderPass;
        pipelineInfo.subpass = 0;

        VkPipeline pipeline = VK_NULL_HANDLE;
        if (vkCreateGraphicsPipelines(device.device, VK_NULL_HANDLE, 1, &pipelineInfo, nullptr,
                                      &pipeline) != VK_SUCCESS) {
            throw std::runtime_error("Failed to create graphics pipeline");
        }

        auto pipelineObj = std::make_unique<VulkanRenderPipeline>();
        pipelineObj->pipeline = pipeline;
        pipelineObj->pipelineLayout = &pipelineLayout;
        pipelineObj->renderPass = renderPass;
        pipelineObj->topology = topology;
        return storeObject(device.renderPipelines, std::move(pipelineObj));
    }

// ----------------------------------------------------------------------------
// Command encoding
// ----------------------------------------------------------------------------

    Id createCommandEncoderInternal(VulkanDevice &device) {
        VkCommandBufferAllocateInfo allocInfo{};
        allocInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
        allocInfo.commandPool = device.commandPool;
        allocInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        allocInfo.commandBufferCount = 1;

        VkCommandBuffer commandBuffer = VK_NULL_HANDLE;
        if (vkAllocateCommandBuffers(device.device, &allocInfo, &commandBuffer) != VK_SUCCESS) {
            throw std::runtime_error("Failed to allocate command buffer");
        }

        VkCommandBufferBeginInfo beginInfo{};
        beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
        beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
        if (vkBeginCommandBuffer(commandBuffer, &beginInfo) != VK_SUCCESS) {
            throw std::runtime_error("Failed to begin command buffer");
        }

        auto encoder = std::make_unique<VulkanCommandEncoder>();
        encoder->device = &device;
        encoder->commandBuffer = commandBuffer;
        encoder->currentPipeline = nullptr;
        encoder->targetSwapchain = nullptr;
        encoder->swapchainImageIndex = 0;
        return storeObject(device.commandEncoders, std::move(encoder));
    }

    Id beginRenderPassInternal(
            VulkanDevice &device,
            VulkanCommandEncoder &encoder,
            VulkanRenderPipeline &pipeline,
            VulkanTextureView &targetView,
            VulkanSwapchain *swapchain,
            uint32_t swapchainImageIndex,
            float clearR,
            float clearG,
            float clearB,
            float clearA) {
        VkFramebuffer framebuffer = VK_NULL_HANDLE;
        VkRenderPass renderPass = pipeline.renderPass;
        VkExtent2D extent{};

        if (swapchain) {
            framebuffer = swapchain->framebuffers[swapchainImageIndex];
            extent = swapchain->extent;
        } else {
            VkImageView attachments[] = {targetView.view};
            VkFramebufferCreateInfo framebufferInfo{};
            framebufferInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
            framebufferInfo.renderPass = pipeline.renderPass;
            framebufferInfo.attachmentCount = 1;
            framebufferInfo.pAttachments = attachments;
            framebufferInfo.width = targetView.texture->width;
            framebufferInfo.height = targetView.texture->height;
            framebufferInfo.layers = 1;

            if (vkCreateFramebuffer(device.device, &framebufferInfo, nullptr, &framebuffer) !=
                VK_SUCCESS) {
                throw std::runtime_error("Failed to create transient framebuffer");
            }
            extent.width = targetView.texture->width;
            extent.height = targetView.texture->height;
        }

        VkClearValue clearValue{};
        clearValue.color = {{clearR, clearG, clearB, clearA}};

        VkRenderPassBeginInfo renderPassInfo{};
        renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
        renderPassInfo.renderPass = renderPass;
        renderPassInfo.framebuffer = framebuffer;
        renderPassInfo.renderArea.offset = {0, 0};
        renderPassInfo.renderArea.extent = extent;
        renderPassInfo.clearValueCount = 1;
        renderPassInfo.pClearValues = &clearValue;

        vkCmdBeginRenderPass(encoder.commandBuffer, &renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

        VkViewport viewport{};
        viewport.x = 0.0f;
        viewport.y = 0.0f;
        viewport.width = static_cast<float>(extent.width);
        viewport.height = static_cast<float>(extent.height);
        viewport.minDepth = 0.0f;
        viewport.maxDepth = 1.0f;
        vkCmdSetViewport(encoder.commandBuffer, 0, 1, &viewport);

        VkRect2D scissor{};
        scissor.offset = {0, 0};
        scissor.extent = extent;
        vkCmdSetScissor(encoder.commandBuffer, 0, 1, &scissor);

        auto passEncoder = std::make_unique<VulkanRenderPassEncoder>();
        passEncoder->encoder = &encoder;
        passEncoder->recording = true;

        encoder.currentPipeline = &pipeline;
        encoder.targetSwapchain = swapchain;
        encoder.swapchainImageIndex = swapchainImageIndex;

        return storeObject(device.renderPassEncoders, std::move(passEncoder));
    }

    void endRenderPassInternal(VulkanRenderPassEncoder &passEncoder) {
        if (!passEncoder.recording) return;
        vkCmdEndRenderPass(passEncoder.encoder->commandBuffer);
        passEncoder.recording = false;
    }

    Id finishCommandEncoderInternal(VulkanDevice &device, VulkanCommandEncoder &encoder) {
        if (vkEndCommandBuffer(encoder.commandBuffer) != VK_SUCCESS) {
            throw std::runtime_error("Failed to end command buffer");
        }

        auto commandBuffer = std::make_unique<VulkanCommandBufferWrapper>();
        commandBuffer->device = &device;
        commandBuffer->commandBuffer = encoder.commandBuffer;
        commandBuffer->swapchain = encoder.targetSwapchain;
        commandBuffer->imageIndex = encoder.swapchainImageIndex;

        return storeObject(device.commandBuffers, std::move(commandBuffer));
    }

    void
    submitCommandBufferInternal(VulkanDevice &device, VulkanCommandBufferWrapper &commandBuffer,
                                VulkanSwapchain *swapchain) {
        VkSemaphore waitSemaphores[] = {
                swapchain ? swapchain->imageAvailableSemaphore : VK_NULL_HANDLE};
        VkPipelineStageFlags waitStages[] = {VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT};

        VkSubmitInfo submitInfo{};
        submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;

        if (swapchain) {
            submitInfo.waitSemaphoreCount = 1;
            submitInfo.pWaitSemaphores = waitSemaphores;
            submitInfo.pWaitDstStageMask = waitStages;
        } else {
            submitInfo.waitSemaphoreCount = 0;
        }

        submitInfo.commandBufferCount = 1;
        submitInfo.pCommandBuffers = &commandBuffer.commandBuffer;

        VkSemaphore signalSemaphores[] = {
                swapchain ? swapchain->renderFinishedSemaphore : VK_NULL_HANDLE};
        if (swapchain) {
            submitInfo.signalSemaphoreCount = 1;
            submitInfo.pSignalSemaphores = signalSemaphores;
        } else {
            submitInfo.signalSemaphoreCount = 0;
        }

        if (vkQueueSubmit(device.graphicsQueue, 1, &submitInfo,
                          swapchain ? swapchain->inFlightFence : VK_NULL_HANDLE) != VK_SUCCESS) {
            throw std::runtime_error("Failed to submit Vulkan command buffer");
        }
    }

    void destroyCommandEncoderInternal(VulkanDevice &device, Id encoderId) {
        auto encoderPtr = removeObject(device.commandEncoders, encoderId);
        if (!encoderPtr) return;
        // Command buffer ownership transferred to wrapper on finish; if unfinished, free now
        if (encoderPtr->commandBuffer != VK_NULL_HANDLE) {
            vkFreeCommandBuffers(device.device, device.commandPool, 1, &encoderPtr->commandBuffer);
        }
    }

    void destroyCommandBufferInternal(VulkanDevice &device, Id commandBufferId) {
        auto bufferPtr = removeObject(device.commandBuffers, commandBufferId);
        if (!bufferPtr) return;
        if (bufferPtr->commandBuffer != VK_NULL_HANDLE) {
            vkFreeCommandBuffers(device.device, device.commandPool, 1, &bufferPtr->commandBuffer);
        }
    }

// ----------------------------------------------------------------------------
// JNI bridge helpers (conversion)
// ----------------------------------------------------------------------------

    std::vector<std::uint32_t> toUint32Vector(JNIEnv *env, jbyteArray byteArray) {
        const jsize length = env->GetArrayLength(byteArray);
        std::vector<std::uint32_t> out((length + 3) / 4, 0);
        jbyte *elements = env->GetByteArrayElements(byteArray, nullptr);
        std::memcpy(out.data(), elements, length);
        env->ReleaseByteArrayElements(byteArray, elements, JNI_ABORT);
        return out;
    }

    std::vector<BindGroupLayoutEntry>
    toBindGroupLayoutEntries(JNIEnv *env, jintArray bindingArray, jintArray typeArray,
                             jintArray stageArray) {
        jsize count = env->GetArrayLength(bindingArray);
        std::vector<BindGroupLayoutEntry> entries(count);

        jint *bindings = env->GetIntArrayElements(bindingArray, nullptr);
        jint *types = env->GetIntArrayElements(typeArray, nullptr);
        jint *stages = env->GetIntArrayElements(stageArray, nullptr);

        for (jsize i = 0; i < count; ++i) {
            entries[i].binding = static_cast<uint32_t>(bindings[i]);
            entries[i].descriptorType = toDescriptorType(types[i]);
            entries[i].stageFlags = toShaderStage(static_cast<uint32_t>(stages[i]));
        }

        env->ReleaseIntArrayElements(bindingArray, bindings, JNI_ABORT);
        env->ReleaseIntArrayElements(typeArray, types, JNI_ABORT);
        env->ReleaseIntArrayElements(stageArray, stages, JNI_ABORT);

        return entries;
    }

    std::vector<BindGroupEntry> toBindGroupEntries(
            VulkanDevice &device,
            JNIEnv *env,
            jintArray bindingArray,
            jlongArray bufferArray,
            jlongArray offsetArray,
            jlongArray sizeArray,
            jlongArray textureViewArray,
            jlongArray samplerArray) {
        jsize count = env->GetArrayLength(bindingArray);
        std::vector<BindGroupEntry> entries(count);

        jint *bindings = env->GetIntArrayElements(bindingArray, nullptr);
        jlong *buffers = env->GetLongArrayElements(bufferArray, nullptr);
        jlong *offsets = env->GetLongArrayElements(offsetArray, nullptr);
        jlong *sizes = env->GetLongArrayElements(sizeArray, nullptr);
        jlong *textureViews = env->GetLongArrayElements(textureViewArray, nullptr);
        jlong *samplers = env->GetLongArrayElements(samplerArray, nullptr);

        for (jsize i = 0; i < count; ++i) {
            entries[i].binding = static_cast<uint32_t>(bindings[i]);
            if (buffers[i] != 0) {
                entries[i].buffer = requireBuffer(device, static_cast<Id>(buffers[i]));
                entries[i].offset = static_cast<VkDeviceSize>(offsets[i]);
                entries[i].range = static_cast<VkDeviceSize>(sizes[i]);
            }
            if (textureViews[i] != 0) {
                entries[i].textureView = requireTextureView(device,
                                                            static_cast<Id>(textureViews[i]));
            }
            if (samplers[i] != 0) {
                entries[i].sampler = requireSampler(device, static_cast<Id>(samplers[i]));
            }
        }

        env->ReleaseIntArrayElements(bindingArray, bindings, JNI_ABORT);
        env->ReleaseLongArrayElements(bufferArray, buffers, JNI_ABORT);
        env->ReleaseLongArrayElements(offsetArray, offsets, JNI_ABORT);
        env->ReleaseLongArrayElements(sizeArray, sizes, JNI_ABORT);
        env->ReleaseLongArrayElements(textureViewArray, textureViews, JNI_ABORT);
        env->ReleaseLongArrayElements(samplerArray, samplers, JNI_ABORT);

        return entries;
    }

    std::vector<VkVertexInputBindingDescription>
    toBindingDescriptions(JNIEnv *env, jintArray bindingIndexArray, jintArray strideArray,
                          jintArray stepModeArray) {
        jsize count = env->GetArrayLength(bindingIndexArray);
        std::vector<VkVertexInputBindingDescription> bindings(count);

        jint *bindingIndices = env->GetIntArrayElements(bindingIndexArray, nullptr);
        jint *strides = env->GetIntArrayElements(strideArray, nullptr);
        jint *stepModes = env->GetIntArrayElements(stepModeArray, nullptr);

        for (jsize i = 0; i < count; ++i) {
            bindings[i].binding = static_cast<uint32_t>(bindingIndices[i]);
            bindings[i].stride = static_cast<uint32_t>(strides[i]);
            bindings[i].inputRate =
                    stepModes[i] == 1 ? VK_VERTEX_INPUT_RATE_INSTANCE : VK_VERTEX_INPUT_RATE_VERTEX;
        }

        env->ReleaseIntArrayElements(bindingIndexArray, bindingIndices, JNI_ABORT);
        env->ReleaseIntArrayElements(strideArray, strides, JNI_ABORT);
        env->ReleaseIntArrayElements(stepModeArray, stepModes, JNI_ABORT);

        return bindings;
    }

    std::vector<VkVertexInputAttributeDescription>
    toAttributeDescriptions(JNIEnv *env, jintArray locationArray, jintArray bindingArray,
                            jintArray formatArray, jintArray offsetArray) {
        jsize count = env->GetArrayLength(locationArray);
        std::vector<VkVertexInputAttributeDescription> attrs(count);

        jint *locations = env->GetIntArrayElements(locationArray, nullptr);
        jint *bindings = env->GetIntArrayElements(bindingArray, nullptr);
        jint *formats = env->GetIntArrayElements(formatArray, nullptr);
        jint *offsets = env->GetIntArrayElements(offsetArray, nullptr);

        for (jsize i = 0; i < count; ++i) {
            attrs[i].location = static_cast<uint32_t>(locations[i]);
            attrs[i].binding = static_cast<uint32_t>(bindings[i]);
            attrs[i].format = toVertexFormat(formats[i]);
            attrs[i].offset = static_cast<uint32_t>(offsets[i]);
        }

        env->ReleaseIntArrayElements(locationArray, locations, JNI_ABORT);
        env->ReleaseIntArrayElements(bindingArray, bindings, JNI_ABORT);
        env->ReleaseIntArrayElements(formatArray, formats, JNI_ABORT);
        env->ReleaseIntArrayElements(offsetArray, offsets, JNI_ABORT);

        return attrs;
    }

// ----------------------------------------------------------------------------
// JNI exports
// ----------------------------------------------------------------------------

} // namespace

namespace kreekt::vk {

    std::uint64_t createInstance(const char *appName, bool enableValidation) {
        std::lock_guard<std::mutex> lock(g_registryMutex);
        return createInstanceInternal(appName, enableValidation);
    }

    std::uint64_t createSurface(std::uint64_t instanceId, JNIEnv *env, jobject surfaceObj) {
        std::lock_guard<std::mutex> lock(g_registryMutex);
        VulkanInstance *instance = requireInstance(instanceId);
        return createSurfaceInternal(*instance, env, surfaceObj);
    }

    std::uint64_t createDevice(std::uint64_t instanceId) {
        std::lock_guard<std::mutex> lock(g_registryMutex);
        VulkanInstance *instance = requireInstance(instanceId);
        return createDeviceInternal(*instance, true);
    }

    std::uint64_t
    createSwapchain(std::uint64_t instanceId, std::uint64_t deviceId, std::uint64_t surfaceId,
                    std::uint32_t width, std::uint32_t height) {
        std::lock_guard<std::mutex> lock(g_registryMutex);
        VulkanInstance *instance = requireInstance(instanceId);
        VulkanDevice *device = requireDevice(*instance, deviceId);
        VulkanSurface *surface = requireSurface(*instance, surfaceId);
        return createSwapchainInternal(*instance, *device, *surface, width, height);
    }

    std::unique_ptr<VulkanSurfaceFrame>
    acquireFrame(std::uint64_t instanceId, std::uint64_t deviceId, std::uint64_t surfaceId,
                 std::uint64_t swapchainId) {
        VulkanInstance *instance = requireInstance(instanceId);
        VulkanDevice *device = requireDevice(*instance, deviceId);
        VulkanSurface *surface = requireSurface(*instance, surfaceId);
        VulkanSwapchain *swapchain = requireSwapchain(*surface, swapchainId);
        return acquireFrameInternal(*device, *surface, *swapchain);
    }

    void presentFrame(std::uint64_t instanceId, std::uint64_t deviceId, std::uint64_t surfaceId,
                      std::uint64_t swapchainId, VulkanCommandBufferWrapper &commandBuffer,
                      uint32_t imageIndex) {
        VulkanInstance *instance = requireInstance(instanceId);
        VulkanDevice *device = requireDevice(*instance, deviceId);
        VulkanSurface *surface = requireSurface(*instance, surfaceId);
        VulkanSwapchain *swapchain = requireSwapchain(*surface, swapchainId);
        presentFrameInternal(*device, *swapchain, commandBuffer, imageIndex);
    }

    void destroySwapchain(std::uint64_t instanceId, std::uint64_t deviceId, std::uint64_t surfaceId,
                          std::uint64_t swapchainId) {
        VulkanInstance *instance = requireInstance(instanceId);
        VulkanDevice *device = requireDevice(*instance, deviceId);
        VulkanSurface *surface = requireSurface(*instance, surfaceId);
        destroySwapchainInternal(*device, *surface, swapchainId);
    }

    void destroySurface(std::uint64_t instanceId, std::uint64_t surfaceId) {
        VulkanInstance *instance = requireInstance(instanceId);
        destroySurfaceInternal(*instance, surfaceId);
    }

    void destroyDevice(std::uint64_t instanceId, std::uint64_t deviceId) {
        VulkanInstance *instance = requireInstance(instanceId);
        destroyDeviceInternal(*instance, deviceId);
    }

    void destroyInstance(std::uint64_t instanceId) {
        auto instancePtr = removeObject(g_instances, instanceId);
        if (!instancePtr) return;
        for (auto &deviceEntry: instancePtr->devices) {
            destroyDeviceInternal(*instancePtr, deviceEntry.first);
        }
        for (auto &surfaceEntry: instancePtr->surfaces) {
            destroySurfaceInternal(*instancePtr, surfaceEntry.first);
        }
        if (instancePtr->instance != VK_NULL_HANDLE) {
            vkDestroyInstance(instancePtr->instance, nullptr);
        }
    }

    void destroyAll() {
        for (auto &instanceEntry: g_instances) {
            destroyInstance(instanceEntry.first);
        }
        g_instances.clear();
    }

} // namespace kreekt::vk

// ----------------------------------------------------------------------------
// JNI exported functions (wrappers around internal helpers)
// ----------------------------------------------------------------------------

extern "C" {

JNIEXPORT jlong JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkInit(JNIEnv *env, jclass, jstring appName,
                                              jboolean enableValidation) {
    const char *name = env->GetStringUTFChars(appName, nullptr);
    std::uint64_t id = kreekt::vk::createInstance(name, enableValidation == JNI_TRUE);
    env->ReleaseStringUTFChars(appName, name);
    return static_cast<jlong>(id);
}

JNIEXPORT jlong JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCreateSurface(JNIEnv *env, jclass, jlong instanceId,
                                                       jobject surface) {
    std::lock_guard<std::mutex> lock(g_registryMutex);
    return static_cast<jlong>(kreekt::vk::createSurface(static_cast<std::uint64_t>(instanceId), env,
                                                        surface));
}

JNIEXPORT jlong JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCreateDevice(JNIEnv *env, jclass, jlong instanceId) {
    (void) env;
    std::lock_guard<std::mutex> lock(g_registryMutex);
    return static_cast<jlong>(kreekt::vk::createDevice(static_cast<std::uint64_t>(instanceId)));
}

JNIEXPORT jlong JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCreateSwapchain(JNIEnv *env, jclass, jlong instanceId,
                                                         jlong deviceId, jlong surfaceId,
                                                         jint width, jint height) {
    (void) env;
    std::lock_guard<std::mutex> lock(g_registryMutex);
    return static_cast<jlong>(kreekt::vk::createSwapchain(static_cast<std::uint64_t>(instanceId),
                                                          static_cast<std::uint64_t>(deviceId),
                                                          static_cast<std::uint64_t>(surfaceId),
                                                          static_cast<uint32_t>(width),
                                                          static_cast<uint32_t>(height)));
}

JNIEXPORT jlongArray JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkSwapchainAcquireFrame(JNIEnv *env, jclass,
                                                               jlong instanceId, jlong deviceId,
                                                               jlong surfaceId, jlong swapchainId) {
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    VulkanSurface *surface = requireSurface(*instance, static_cast<Id>(surfaceId));
    VulkanSwapchain *swapchain = requireSwapchain(*surface, static_cast<Id>(swapchainId));

    auto frame = acquireFrameInternal(*device, *surface, *swapchain);
    jlongArray result = env->NewLongArray(3);
    jlong values[3];
    values[0] = static_cast<jlong>(frame->imageIndex);
    values[1] = static_cast<jlong>(frame->textureId);
    values[2] = static_cast<jlong>(frame->viewId);
    env->SetLongArrayRegion(result, 0, 3, values);

    return result;
}

JNIEXPORT void JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkSwapchainPresentFrame(JNIEnv *env, jclass,
                                                               jlong instanceId, jlong deviceId,
                                                               jlong surfaceId, jlong swapchainId,
                                                               jlong commandBufferId,
                                                               jint imageIndex) {
    (void) env;
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    VulkanSurface *surface = requireSurface(*instance, static_cast<Id>(surfaceId));
    VulkanSwapchain *swapchain = requireSwapchain(*surface, static_cast<Id>(swapchainId));
    VulkanCommandBufferWrapper *commandBuffer = requireCommandBuffer(*device,
                                                                     static_cast<Id>(commandBufferId));
    presentFrameInternal(*device, *swapchain, *commandBuffer, static_cast<uint32_t>(imageIndex));
}

JNIEXPORT jlong JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCreateBuffer(JNIEnv *env, jclass, jlong instanceId,
                                                      jlong deviceId, jlong size, jint usageFlags,
                                                      jint memoryProperties) {
    (void) env;
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    return static_cast<jlong>(createBufferInternal(*device, static_cast<VkDeviceSize>(size),
                                                   static_cast<VkBufferUsageFlags>(usageFlags),
                                                   static_cast<VkMemoryPropertyFlags>(memoryProperties)));
}

JNIEXPORT void JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkWriteBuffer(JNIEnv *env, jclass, jlong instanceId,
                                                     jlong deviceId, jlong bufferId,
                                                     jbyteArray data, jint offset) {
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    VulkanBuffer *buffer = requireBuffer(*device, static_cast<Id>(bufferId));

    jsize length = env->GetArrayLength(data);
    std::vector<std::uint8_t> tmp(length);
    env->GetByteArrayRegion(data, 0, length, reinterpret_cast<jbyte *>(tmp.data()));
    writeBufferInternal(*device, *buffer, tmp.data(), tmp.size(), static_cast<size_t>(offset));
}

JNIEXPORT void JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkWriteBufferFloats(JNIEnv *env, jclass, jlong instanceId,
                                                           jlong deviceId, jlong bufferId,
                                                           jfloatArray data, jint offset) {
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    VulkanBuffer *buffer = requireBuffer(*device, static_cast<Id>(bufferId));

    jsize length = env->GetArrayLength(data);
    std::vector<float> tmp(length);
    env->GetFloatArrayRegion(data, 0, length, tmp.data());
    writeBufferInternal(*device, *buffer, tmp.data(), tmp.size() * sizeof(float),
                        static_cast<size_t>(offset));
}

JNIEXPORT jlong JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCreateShaderModule(JNIEnv *env, jclass, jlong instanceId,
                                                            jlong deviceId, jbyteArray spirvBytes) {
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    auto spirv = toUint32Vector(env, spirvBytes);
    return static_cast<jlong>(createShaderModuleInternal(*device, spirv));
}

JNIEXPORT jlong JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCreateSampler(JNIEnv *env, jclass, jlong instanceId,
                                                       jlong deviceId, jint minFilter,
                                                       jint magFilter) {
    (void) env;
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    return static_cast<jlong>(createSamplerInternal(*device, static_cast<VkFilter>(minFilter),
                                                    static_cast<VkFilter>(magFilter)));
}

JNIEXPORT jlong JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCreateTexture(JNIEnv *env, jclass, jlong instanceId,
                                                       jlong deviceId, jint format, jint width,
                                                       jint height, jint usageFlags) {
    (void) env;
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    return static_cast<jlong>(createTextureInternal(*device, toColorFormat(format),
                                                    static_cast<uint32_t>(width),
                                                    static_cast<uint32_t>(height),
                                                    static_cast<VkImageUsageFlags>(usageFlags)));
}

JNIEXPORT jlong JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCreateTextureView(JNIEnv *env, jclass, jlong instanceId,
                                                           jlong deviceId, jlong textureId,
                                                           jint viewType, jint overrideFormat) {
    (void) env;
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    VulkanTexture *texture = requireTexture(*device, static_cast<Id>(textureId));
    return static_cast<jlong>(createTextureViewInternal(*device, *texture,
                                                        static_cast<VkImageViewType>(viewType),
                                                        overrideFormat >= 0 ? toColorFormat(
                                                                overrideFormat)
                                                                            : VK_FORMAT_UNDEFINED));
}

JNIEXPORT jlong JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCreateBindGroupLayout(JNIEnv *env, jclass,
                                                               jlong instanceId, jlong deviceId,
                                                               jintArray bindings,
                                                               jintArray resourceTypes,
                                                               jintArray visibilityMask) {
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    auto entries = toBindGroupLayoutEntries(env, bindings, resourceTypes, visibilityMask);
    return static_cast<jlong>(createBindGroupLayoutInternal(*device, entries));
}

JNIEXPORT jlong JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCreateBindGroup(JNIEnv *env, jclass, jlong instanceId,
                                                         jlong deviceId, jlong layoutId,
                                                         jintArray bindings, jlongArray buffers,
                                                         jlongArray offsets, jlongArray sizes,
                                                         jlongArray textureViews,
                                                         jlongArray samplers) {
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    VulkanBindGroupLayout *layout = requireBindGroupLayout(*device, static_cast<Id>(layoutId));
    auto entries = toBindGroupEntries(*device, env, bindings, buffers, offsets, sizes, textureViews,
                                      samplers);
    return static_cast<jlong>(createBindGroupInternal(*device, *layout, entries));
}

JNIEXPORT jlong JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCreatePipelineLayout(JNIEnv *env, jclass, jlong instanceId,
                                                              jlong deviceId,
                                                              jlongArray layoutHandles) {
    (void) env;
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));

    jsize count = env->GetArrayLength(layoutHandles);
    std::vector<VulkanBindGroupLayout *> layouts;
    layouts.reserve(count);
    jlong *handles = env->GetLongArrayElements(layoutHandles, nullptr);
    for (jsize i = 0; i < count; ++i) {
        layouts.push_back(requireBindGroupLayout(*device, static_cast<Id>(handles[i])));
    }
    env->ReleaseLongArrayElements(layoutHandles, handles, JNI_ABORT);

    return static_cast<jlong>(createPipelineLayoutInternal(*device, layouts));
}

JNIEXPORT jlong JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCreateRenderPipeline(
        JNIEnv *env,
        jclass,
        jlong instanceId,
        jlong deviceId,
        jlong pipelineLayoutId,
        jlong vertexShaderId,
        jlong fragmentShaderId,
        jintArray bindingIndices,
        jintArray strides,
        jintArray stepModes,
        jintArray attributeLocations,
        jintArray attributeBindings,
        jintArray attributeFormats,
        jintArray attributeOffsets,
        jint topology,
        jint cullMode,
        jboolean enableBlend,
        jint colorFormat,
        jlong renderPassHandle) {
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    VulkanPipelineLayout *pipelineLayout = requirePipelineLayout(*device,
                                                                 static_cast<Id>(pipelineLayoutId));
    VulkanShaderModule *vertexShader = requireShaderModule(*device,
                                                           static_cast<Id>(vertexShaderId));
    VulkanShaderModule *fragmentShader = requireShaderModule(*device,
                                                             static_cast<Id>(fragmentShaderId));

    auto bindings = toBindingDescriptions(env, bindingIndices, strides, stepModes);
    auto attributes = toAttributeDescriptions(env, attributeLocations, attributeBindings,
                                              attributeFormats, attributeOffsets);

    VkRenderPass renderPass =
            renderPassHandle != 0 ? reinterpret_cast<VkRenderPass>(renderPassHandle)
                                  : createRenderPass(device->device, toColorFormat(colorFormat),
                                                     VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

    return static_cast<jlong>(createRenderPipelineInternal(
            *device,
            *pipelineLayout,
            vertexShader,
            fragmentShader,
            bindings,
            attributes,
            toTopology(topology),
            toCullMode(cullMode),
            enableBlend == JNI_TRUE,
            toColorFormat(colorFormat),
            renderPass
    ));
}

JNIEXPORT jlong JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCreateCommandEncoder(JNIEnv *env, jclass, jlong instanceId,
                                                              jlong deviceId) {
    (void) env;
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    return static_cast<jlong>(createCommandEncoderInternal(*device));
}

JNIEXPORT jlong JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCommandEncoderBeginRenderPass(
        JNIEnv *env,
        jclass,
        jlong instanceId,
        jlong deviceId,
        jlong encoderId,
        jlong pipelineId,
        jlong textureViewId,
        jboolean isSwapchain,
        jint swapchainImageIndex,
        jfloat clearR,
        jfloat clearG,
        jfloat clearB,
        jfloat clearA) {
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    VulkanCommandEncoder *encoder = requireCommandEncoder(*device, static_cast<Id>(encoderId));
    VulkanRenderPipeline *pipeline = requireRenderPipeline(*device, static_cast<Id>(pipelineId));
    VulkanTextureView *view = requireTextureView(*device, static_cast<Id>(textureViewId));

    VulkanSwapchain *swapchain = nullptr;
    if (isSwapchain == JNI_TRUE) {
        for (auto &surfaceEntry: instance->surfaces) {
            for (auto &swapchainEntry: surfaceEntry.second->swapchains) {
                if (std::find(swapchainEntry.second->textureViewIds.begin(),
                              swapchainEntry.second->textureViewIds.end(),
                              static_cast<Id>(textureViewId)) !=
                    swapchainEntry.second->textureViewIds.end()) {
                    swapchain = swapchainEntry.second.get();
                    break;
                }
            }
            if (swapchain) break;
        }
    }

    return static_cast<jlong>(beginRenderPassInternal(
            *device,
            *encoder,
            *pipeline,
            *view,
            swapchain,
            static_cast<uint32_t>(swapchainImageIndex),
            clearR,
            clearG,
            clearB,
            clearA
    ));
}

JNIEXPORT void JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCommandEncoderEndRenderPass(JNIEnv *env, jclass,
                                                                     jlong instanceId,
                                                                     jlong deviceId,
                                                                     jlong renderPassEncoderId) {
    (void) env;
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    VulkanRenderPassEncoder *passEncoder = requireRenderPassEncoder(*device,
                                                                    static_cast<Id>(renderPassEncoderId));
    endRenderPassInternal(*passEncoder);
}

JNIEXPORT void JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCommandEncoderSetPipeline(JNIEnv *env, jclass,
                                                                   jlong instanceId, jlong deviceId,
                                                                   jlong encoderId,
                                                                   jlong pipelineId) {
    (void) env;
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    VulkanCommandEncoder *encoder = requireCommandEncoder(*device, static_cast<Id>(encoderId));
    VulkanRenderPipeline *pipeline = requireRenderPipeline(*device, static_cast<Id>(pipelineId));
    vkCmdBindPipeline(encoder->commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline->pipeline);
    encoder->currentPipeline = pipeline;
}

JNIEXPORT void JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCommandEncoderSetVertexBuffer(JNIEnv *env, jclass,
                                                                       jlong instanceId,
                                                                       jlong deviceId,
                                                                       jlong encoderId, jint slot,
                                                                       jlong bufferId,
                                                                       jlong offset) {
    (void) env;
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    VulkanCommandEncoder *encoder = requireCommandEncoder(*device, static_cast<Id>(encoderId));
    VulkanBuffer *buffer = requireBuffer(*device, static_cast<Id>(bufferId));
    VkDeviceSize offsets[] = {static_cast<VkDeviceSize>(offset)};
    vkCmdBindVertexBuffers(encoder->commandBuffer, static_cast<uint32_t>(slot), 1, &buffer->buffer,
                           offsets);
}

JNIEXPORT void JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCommandEncoderSetIndexBuffer(JNIEnv *env, jclass,
                                                                      jlong instanceId,
                                                                      jlong deviceId,
                                                                      jlong encoderId,
                                                                      jlong bufferId,
                                                                      jint indexType,
                                                                      jlong offset) {
    (void) env;
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    VulkanCommandEncoder *encoder = requireCommandEncoder(*device, static_cast<Id>(encoderId));
    VulkanBuffer *buffer = requireBuffer(*device, static_cast<Id>(bufferId));
    VkIndexType type = indexType == 0 ? VK_INDEX_TYPE_UINT16 : VK_INDEX_TYPE_UINT32;
    vkCmdBindIndexBuffer(encoder->commandBuffer, buffer->buffer, static_cast<VkDeviceSize>(offset),
                         type);
}

JNIEXPORT void JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCommandEncoderSetBindGroup(JNIEnv *env, jclass,
                                                                    jlong instanceId,
                                                                    jlong deviceId, jlong encoderId,
                                                                    jint index, jlong bindGroupId) {
    (void) env;
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    VulkanCommandEncoder *encoder = requireCommandEncoder(*device, static_cast<Id>(encoderId));
    VulkanBindGroup *bindGroup = requireBindGroup(*device, static_cast<Id>(bindGroupId));
    vkCmdBindDescriptorSets(encoder->commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS,
                            encoder->currentPipeline->pipelineLayout->layout,
                            static_cast<uint32_t>(index), 1, &bindGroup->descriptorSet, 0, nullptr);
}

JNIEXPORT void JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCommandEncoderDraw(JNIEnv *env, jclass, jlong instanceId,
                                                            jlong deviceId, jlong encoderId,
                                                            jint vertexCount, jint instanceCount,
                                                            jint firstVertex, jint firstInstance) {
    (void) env;
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    VulkanCommandEncoder *encoder = requireCommandEncoder(*device, static_cast<Id>(encoderId));
    vkCmdDraw(encoder->commandBuffer, static_cast<uint32_t>(vertexCount),
              static_cast<uint32_t>(instanceCount), static_cast<uint32_t>(firstVertex),
              static_cast<uint32_t>(firstInstance));
}

JNIEXPORT void JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCommandEncoderDrawIndexed(JNIEnv *env, jclass,
                                                                   jlong instanceId, jlong deviceId,
                                                                   jlong encoderId, jint indexCount,
                                                                   jint instanceCount,
                                                                   jint firstIndex,
                                                                   jint vertexOffset,
                                                                   jint firstInstance) {
    (void) env;
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    VulkanCommandEncoder *encoder = requireCommandEncoder(*device, static_cast<Id>(encoderId));
    vkCmdDrawIndexed(encoder->commandBuffer, static_cast<uint32_t>(indexCount),
                     static_cast<uint32_t>(instanceCount), static_cast<uint32_t>(firstIndex),
                     static_cast<int32_t>(vertexOffset), static_cast<uint32_t>(firstInstance));
}

JNIEXPORT jlong JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkCommandEncoderFinish(JNIEnv *env, jclass, jlong instanceId,
                                                              jlong deviceId, jlong encoderId) {
    (void) env;
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    VulkanCommandEncoder *encoder = requireCommandEncoder(*device, static_cast<Id>(encoderId));
    return static_cast<jlong>(finishCommandEncoderInternal(*device, *encoder));
}

JNIEXPORT void JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkQueueSubmit(JNIEnv *env, jclass, jlong instanceId,
                                                     jlong deviceId, jlong commandBufferId,
                                                     jboolean hasSwapchain, jint imageIndex) {
    (void) env;
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    VulkanCommandBufferWrapper *commandBuffer = requireCommandBuffer(*device,
                                                                     static_cast<Id>(commandBufferId));

    VulkanSwapchain *swapchain = nullptr;
    if (hasSwapchain == JNI_TRUE) {
        swapchain = commandBuffer->swapchain;
        commandBuffer->imageIndex = static_cast<uint32_t>(imageIndex);
    }

    submitCommandBufferInternal(*device, *commandBuffer, swapchain);
}

JNIEXPORT void JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkDestroyCommandBuffer(JNIEnv *env, jclass, jlong instanceId,
                                                              jlong deviceId,
                                                              jlong commandBufferId) {
    (void) env;
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    destroyCommandBufferInternal(*device, static_cast<Id>(commandBufferId));
}

JNIEXPORT void JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkDestroyCommandEncoder(JNIEnv *env, jclass,
                                                               jlong instanceId, jlong deviceId,
                                                               jlong encoderId) {
    (void) env;
    std::lock_guard<std::mutex> lock(g_registryMutex);
    VulkanInstance *instance = requireInstance(static_cast<Id>(instanceId));
    VulkanDevice *device = requireDevice(*instance, static_cast<Id>(deviceId));
    destroyCommandEncoderInternal(*device, static_cast<Id>(encoderId));
}

JNIEXPORT void JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkDestroyInstance(JNIEnv *env, jclass, jlong instanceId) {
    (void) env;
    std::lock_guard<std::mutex> lock(g_registryMutex);
    kreekt::vk::destroyInstance(static_cast<std::uint64_t>(instanceId));
}

JNIEXPORT void JNICALL
Java_io_kreekt_gpu_bridge_VulkanBridge_vkDestroyAll(JNIEnv *env, jclass) {
    (void) env;
    std::lock_guard<std::mutex> lock(g_registryMutex);
    kreekt::vk::destroyAll();
}

} // extern "C"
