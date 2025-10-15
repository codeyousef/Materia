package io.kreekt.renderer.vulkan

import org.lwjgl.BufferUtils
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.VK12.*
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo
import org.lwjgl.vulkan.VkShaderModuleCreateInfo

/**
 * Vulkan graphics pipeline responsible for compiling shaders and creating the fixed pipeline state
 * used by the renderer. The implementation deliberately keeps the shader extremely small: the
 * vertex shader forwards clip-space positions, and the fragment shader outputs vertex colours.
 */
class VulkanPipeline(
    private val device: VkDevice
) {
    private var vertexShaderModule: Long = VK_NULL_HANDLE
    private var fragmentShaderModule: Long = VK_NULL_HANDLE
    private var pipelineLayout: Long = VK_NULL_HANDLE
    private var graphicsPipeline: Long = VK_NULL_HANDLE

    /**
     * Compile shaders (GLSL -> SPIR-V) and create the Vulkan graphics pipeline.
     *
     * @param renderPass Render pass the pipeline depends on.
     * @param width Swapchain width (used for static viewport/scissor configuration).
     * @param height Swapchain height.
     */
    fun createPipeline(renderPass: Long, width: Int, height: Int): Boolean {
        dispose()

        return try {
            val vertexSpv = compileShader(VERTEX_SHADER_SOURCE, Shaderc.shaderc_glsl_vertex_shader, "triangle.vert")
            val fragmentSpv = compileShader(FRAGMENT_SHADER_SOURCE, Shaderc.shaderc_glsl_fragment_shader, "triangle.frag")

            vertexShaderModule = createShaderModule(vertexSpv)
            fragmentShaderModule = createShaderModule(fragmentSpv)

            MemoryUtil.memFree(vertexSpv)
            MemoryUtil.memFree(fragmentSpv)

            pipelineLayout = createPipelineLayout()
            if (pipelineLayout == VK_NULL_HANDLE) {
                throw IllegalStateException("Failed to create pipeline layout")
            }

            graphicsPipeline = createGraphicsPipeline(renderPass, width, height)
            graphicsPipeline != VK_NULL_HANDLE
        } catch (exc: Exception) {
            dispose()
            false
        }
    }

    private fun compileShader(source: String, kind: Int, name: String): java.nio.ByteBuffer {
        val compiler = Shaderc.shaderc_compiler_initialize()
        val options = Shaderc.shaderc_compile_options_initialize()

        val result = Shaderc.shaderc_compile_into_spv(compiler, source, kind, name, "main", options)
        val status = Shaderc.shaderc_result_get_compilation_status(result)
        if (status != Shaderc.shaderc_compilation_status_success) {
            val error = Shaderc.shaderc_result_get_error_message(result)
            Shaderc.shaderc_result_release(result)
            Shaderc.shaderc_compile_options_release(options)
            Shaderc.shaderc_compiler_release(compiler)
            throw IllegalStateException("Shader compilation failed for $name: $error")
        }

        val length = Shaderc.shaderc_result_get_length(result).toInt()
        val output = BufferUtils.createByteBuffer(length)
        output.put(Shaderc.shaderc_result_get_bytes(result))
        output.flip()

        Shaderc.shaderc_result_release(result)
        Shaderc.shaderc_compile_options_release(options)
        Shaderc.shaderc_compiler_release(compiler)

        return output
    }

    private fun createShaderModule(spirvCode: java.nio.ByteBuffer): Long {
        return MemoryStack.stackPush().use { stack ->
            val createInfo = VkShaderModuleCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                .pCode(spirvCode)

            val pShaderModule = stack.mallocLong(1)
            val result = vkCreateShaderModule(device, createInfo, null, pShaderModule)
            if (result != VK_SUCCESS) {
                VK_NULL_HANDLE
            } else {
                pShaderModule[0]
            }
        }
    }

    private fun createPipelineLayout(): Long {
        return MemoryStack.stackPush().use { stack ->
            val createInfo = org.lwjgl.vulkan.VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(null)
                .pPushConstantRanges(null)

            val pPipelineLayout = stack.mallocLong(1)
            val result = vkCreatePipelineLayout(device, createInfo, null, pPipelineLayout)
            if (result != VK_SUCCESS) VK_NULL_HANDLE else pPipelineLayout[0]
        }
    }

    private fun createGraphicsPipeline(renderPass: Long, width: Int, height: Int): Long {
        return MemoryStack.stackPush().use { stack ->
            val entryPoint = stack.UTF8("main")

            val shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack)
            shaderStages[0]
                .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK_SHADER_STAGE_VERTEX_BIT)
                .module(vertexShaderModule)
                .pName(entryPoint)

            shaderStages[1]
                .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                .module(fragmentShaderModule)
                .pName(entryPoint)

            val bindingDescription = org.lwjgl.vulkan.VkVertexInputBindingDescription.calloc(1, stack)
            bindingDescription[0]
                .binding(0)
                .stride(VERTEX_STRIDE)
                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX)

            val attributeDescriptions = org.lwjgl.vulkan.VkVertexInputAttributeDescription.calloc(2, stack)
            attributeDescriptions[0]
                .binding(0)
                .location(0)
                .format(VK_FORMAT_R32G32B32_SFLOAT)
                .offset(0)

            attributeDescriptions[1]
                .binding(0)
                .location(1)
                .format(VK_FORMAT_R32G32B32_SFLOAT)
                .offset(POSITION_COMPONENTS * java.lang.Float.BYTES)

            val vertexInputState = org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                .pVertexBindingDescriptions(bindingDescription)
                .pVertexAttributeDescriptions(attributeDescriptions)

            val inputAssemblyState = org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                .primitiveRestartEnable(false)

            val viewport = org.lwjgl.vulkan.VkViewport.calloc(1, stack)
                .x(0f)
                .y(0f)
                .width(width.toFloat())
                .height(height.toFloat())
                .minDepth(0f)
                .maxDepth(1f)

            val scissor = org.lwjgl.vulkan.VkRect2D.calloc(1, stack)
            scissor.offset().set(0, 0)
            scissor.extent().set(width, height)

            val viewportState = org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                .pViewports(viewport)
                .pScissors(scissor)

            val rasterizationState = org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                .depthClampEnable(false)
                .rasterizerDiscardEnable(false)
                .polygonMode(VK_POLYGON_MODE_FILL)
                .cullMode(VK_CULL_MODE_BACK_BIT)
                .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                .depthBiasEnable(false)
                .lineWidth(1.0f)

            val multisampleState = org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
                .minSampleShading(1.0f)
                .sampleShadingEnable(false)

            val colorBlendAttachment = org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState.calloc(1, stack)
                .blendEnable(false)
                .srcColorBlendFactor(VK_BLEND_FACTOR_ONE)
                .dstColorBlendFactor(VK_BLEND_FACTOR_ZERO)
                .colorBlendOp(VK_BLEND_OP_ADD)
                .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                .dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO)
                .alphaBlendOp(VK_BLEND_OP_ADD)
                .colorWriteMask(
                    VK_COLOR_COMPONENT_R_BIT or
                        VK_COLOR_COMPONENT_G_BIT or
                        VK_COLOR_COMPONENT_B_BIT or
                        VK_COLOR_COMPONENT_A_BIT
                )

            val colorBlendState = org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                .logicOpEnable(false)
                .pAttachments(colorBlendAttachment)

            val pipelineInfo = org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                .pStages(shaderStages)
                .pVertexInputState(vertexInputState)
                .pInputAssemblyState(inputAssemblyState)
                .pViewportState(viewportState)
                .pRasterizationState(rasterizationState)
                .pMultisampleState(multisampleState)
                .pDepthStencilState(null)
                .pColorBlendState(colorBlendState)
                .layout(pipelineLayout)
                .renderPass(renderPass)
                .subpass(0)

            val pGraphicsPipeline = stack.mallocLong(1)
            val result = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pGraphicsPipeline)
            if (result != VK_SUCCESS) VK_NULL_HANDLE else pGraphicsPipeline[0]
        }
    }

    fun bind(commandBuffer: VkCommandBuffer) {
        if (graphicsPipeline != VK_NULL_HANDLE) {
            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline)
        }
    }

    fun getPipelineHandle(): Long = graphicsPipeline

    fun dispose() {
        if (graphicsPipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(device, graphicsPipeline, null)
            graphicsPipeline = VK_NULL_HANDLE
        }

        if (pipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(device, pipelineLayout, null)
            pipelineLayout = VK_NULL_HANDLE
        }

        if (vertexShaderModule != VK_NULL_HANDLE) {
            vkDestroyShaderModule(device, vertexShaderModule, null)
            vertexShaderModule = VK_NULL_HANDLE
        }

        if (fragmentShaderModule != VK_NULL_HANDLE) {
            vkDestroyShaderModule(device, fragmentShaderModule, null)
            fragmentShaderModule = VK_NULL_HANDLE
        }
    }

    companion object {
        private const val POSITION_COMPONENTS = 3
        private const val COLOR_COMPONENTS = 3
        private const val VERTEX_STRIDE = (POSITION_COMPONENTS + COLOR_COMPONENTS) * java.lang.Float.BYTES

        private const val VERTEX_SHADER_SOURCE = """
            #version 450
            layout(location = 0) in vec3 inPosition;
            layout(location = 1) in vec3 inColor;
            layout(location = 0) out vec3 outColor;
            void main() {
                gl_Position = vec4(inPosition, 1.0);
                outColor = inColor;
            }
        """

        private const val FRAGMENT_SHADER_SOURCE = """
            #version 450
            layout(location = 0) in vec3 inColor;
            layout(location = 0) out vec4 outColor;
            void main() {
                outColor = vec4(inColor, 1.0);
            }
        """
    }
}
