package io.kreekt.renderer.vulkan

import io.kreekt.renderer.material.MaterialRenderState
import io.kreekt.renderer.webgpu.VertexBufferLayout
import io.kreekt.renderer.webgpu.VertexFormat
import io.kreekt.renderer.webgpu.VertexStepMode
import io.kreekt.renderer.webgpu.PrimitiveTopology
import io.kreekt.renderer.webgpu.CullMode
import io.kreekt.renderer.webgpu.FrontFace
import io.kreekt.renderer.webgpu.BlendFactor
import io.kreekt.renderer.webgpu.BlendOperation
import org.lwjgl.BufferUtils
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.shaderc.Shaderc
import org.lwjgl.vulkan.VK12.*
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo
import org.lwjgl.vulkan.VkShaderModuleCreateInfo
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription

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
    fun createPipeline(
        renderPass: Long,
        width: Int,
        height: Int,
        descriptorSetLayouts: LongArray,
        vertexLayouts: List<VertexBufferLayout>,
        renderState: MaterialRenderState,
        vertexSource: String,
        fragmentSource: String
    ): Boolean {
        dispose()

        return try {
            val vertexSpv = compileShader(vertexSource, Shaderc.shaderc_glsl_vertex_shader, "triangle.vert")
            val fragmentSpv = compileShader(fragmentSource, Shaderc.shaderc_glsl_fragment_shader, "triangle.frag")

            vertexShaderModule = createShaderModule(vertexSpv)
            fragmentShaderModule = createShaderModule(fragmentSpv)

            MemoryUtil.memFree(vertexSpv)
            MemoryUtil.memFree(fragmentSpv)

            pipelineLayout = createPipelineLayout(descriptorSetLayouts)
            if (pipelineLayout == VK_NULL_HANDLE) {
                throw IllegalStateException("Failed to create pipeline layout")
            }

            graphicsPipeline = createGraphicsPipeline(renderPass, width, height, vertexLayouts, renderState)
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

    private fun createPipelineLayout(descriptorSetLayouts: LongArray): Long {
        return MemoryStack.stackPush().use { stack ->
            val setLayoutsBuffer = stack.mallocLong(descriptorSetLayouts.size)
            descriptorSetLayouts.forEachIndexed { index, layout ->
                setLayoutsBuffer.put(index, layout)
            }
            val createInfo = org.lwjgl.vulkan.VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(setLayoutsBuffer)
                .pPushConstantRanges(null)

            val pPipelineLayout = stack.mallocLong(1)
            val result = vkCreatePipelineLayout(device, createInfo, null, pPipelineLayout)
            if (result != VK_SUCCESS) VK_NULL_HANDLE else pPipelineLayout[0]
        }
    }

    private fun createGraphicsPipeline(
        renderPass: Long,
        width: Int,
        height: Int,
        vertexLayouts: List<VertexBufferLayout>,
        renderState: MaterialRenderState
    ): Long {
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

            val bindingDescriptions = VkVertexInputBindingDescription.calloc(vertexLayouts.size, stack)
            val attributeCount = vertexLayouts.sumOf { it.attributes.size }
            val attributeDescriptions = VkVertexInputAttributeDescription.calloc(attributeCount, stack)

            var attributeIndex = 0
            vertexLayouts.forEachIndexed { bindingIndex, layout ->
                bindingDescriptions[bindingIndex]
                    .binding(bindingIndex)
                    .stride(layout.arrayStride)
                    .inputRate(if (layout.stepMode == VertexStepMode.VERTEX) VK_VERTEX_INPUT_RATE_VERTEX else VK_VERTEX_INPUT_RATE_INSTANCE)

                layout.attributes.forEach { attribute ->
                    attributeDescriptions[attributeIndex]
                        .binding(bindingIndex)
                        .location(attribute.shaderLocation)
                        .format(toVulkanFormat(attribute.format))
                        .offset(attribute.offset)
                    attributeIndex += 1
                }
            }

            val vertexInputState = org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                .pVertexBindingDescriptions(bindingDescriptions)
                .pVertexAttributeDescriptions(attributeDescriptions)

            val inputAssemblyState = org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                .topology(toVulkanTopology(renderState.topology))
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
                .cullMode(toVulkanCullMode(renderState.cullMode))
                .frontFace(toVulkanFrontFace(renderState.frontFace))
                .depthBiasEnable(false)
                .lineWidth(1.0f)

            val multisampleState = org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
                .minSampleShading(1.0f)
                .sampleShadingEnable(false)

            val colorBlendAttachment = org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState.calloc(1, stack)
                .blendEnable(renderState.colorTarget.blendState != null)
                .colorWriteMask(renderState.colorTarget.writeMask.bits)

            renderState.colorTarget.blendState?.let { blend ->
                colorBlendAttachment
                    .srcColorBlendFactor(toVulkanBlendFactor(blend.color.srcFactor))
                    .dstColorBlendFactor(toVulkanBlendFactor(blend.color.dstFactor))
                    .colorBlendOp(toVulkanBlendOperation(blend.color.operation))
                    .srcAlphaBlendFactor(toVulkanBlendFactor(blend.alpha.srcFactor))
                    .dstAlphaBlendFactor(toVulkanBlendFactor(blend.alpha.dstFactor))
                    .alphaBlendOp(toVulkanBlendOperation(blend.alpha.operation))
            } ?: run {
                colorBlendAttachment
                    .srcColorBlendFactor(VK_BLEND_FACTOR_ONE)
                    .dstColorBlendFactor(VK_BLEND_FACTOR_ZERO)
                    .colorBlendOp(VK_BLEND_OP_ADD)
                    .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                    .dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO)
                    .alphaBlendOp(VK_BLEND_OP_ADD)
            }

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

    fun getPipelineLayout(): Long = pipelineLayout

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

    private fun toVulkanFormat(format: VertexFormat): Int = when (format) {
        VertexFormat.FLOAT32 -> VK_FORMAT_R32_SFLOAT
        VertexFormat.FLOAT32X2 -> VK_FORMAT_R32G32_SFLOAT
        VertexFormat.FLOAT32X3 -> VK_FORMAT_R32G32B32_SFLOAT
        VertexFormat.FLOAT32X4 -> VK_FORMAT_R32G32B32A32_SFLOAT
        else -> throw IllegalArgumentException("Unsupported vertex format: $format")
    }

    private fun toVulkanTopology(topology: PrimitiveTopology): Int = when (topology) {
        PrimitiveTopology.POINT_LIST -> VK_PRIMITIVE_TOPOLOGY_POINT_LIST
        PrimitiveTopology.LINE_LIST -> VK_PRIMITIVE_TOPOLOGY_LINE_LIST
        PrimitiveTopology.LINE_STRIP -> VK_PRIMITIVE_TOPOLOGY_LINE_STRIP
        PrimitiveTopology.TRIANGLE_LIST -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST
        PrimitiveTopology.TRIANGLE_STRIP -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP
    }

    private fun toVulkanCullMode(cullMode: CullMode): Int = when (cullMode) {
        CullMode.NONE -> VK_CULL_MODE_NONE
        CullMode.FRONT -> VK_CULL_MODE_FRONT_BIT
        CullMode.BACK -> VK_CULL_MODE_BACK_BIT
    }

    private fun toVulkanFrontFace(frontFace: FrontFace): Int = when (frontFace) {
        FrontFace.CCW -> VK_FRONT_FACE_COUNTER_CLOCKWISE
        FrontFace.CW -> VK_FRONT_FACE_CLOCKWISE
    }

    private fun toVulkanBlendFactor(factor: BlendFactor): Int = when (factor) {
        BlendFactor.ZERO -> VK_BLEND_FACTOR_ZERO
        BlendFactor.ONE -> VK_BLEND_FACTOR_ONE
        BlendFactor.SRC -> VK_BLEND_FACTOR_SRC_COLOR
        BlendFactor.ONE_MINUS_SRC -> VK_BLEND_FACTOR_ONE_MINUS_SRC_COLOR
        BlendFactor.SRC_ALPHA -> VK_BLEND_FACTOR_SRC_ALPHA
        BlendFactor.ONE_MINUS_SRC_ALPHA -> VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA
        BlendFactor.DST -> VK_BLEND_FACTOR_DST_COLOR
        BlendFactor.ONE_MINUS_DST -> VK_BLEND_FACTOR_ONE_MINUS_DST_COLOR
        BlendFactor.DST_ALPHA -> VK_BLEND_FACTOR_DST_ALPHA
        BlendFactor.ONE_MINUS_DST_ALPHA -> VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA
    }

    private fun toVulkanBlendOperation(operation: BlendOperation): Int = when (operation) {
        BlendOperation.ADD -> VK_BLEND_OP_ADD
        BlendOperation.SUBTRACT -> VK_BLEND_OP_SUBTRACT
        BlendOperation.REVERSE_SUBTRACT -> VK_BLEND_OP_REVERSE_SUBTRACT
        BlendOperation.MIN -> VK_BLEND_OP_MIN
        BlendOperation.MAX -> VK_BLEND_OP_MAX
    }
}
