package io.kreekt.engine.render

import io.kreekt.engine.scene.Mesh
import io.kreekt.gpu.GpuBufferDescriptor
import io.kreekt.gpu.GpuBufferUsage
import io.kreekt.gpu.GpuDevice
import io.kreekt.gpu.GpuIndexFormat
import io.kreekt.gpu.GpuRenderPassEncoder
import io.kreekt.gpu.GpuTextureFormat
import io.kreekt.gpu.gpuBufferUsage
import io.kreekt.engine.math.Mat4
import io.kreekt.engine.math.mat4
import io.kreekt.engine.material.RenderState
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass

class SceneRenderer(
    private val device: GpuDevice,
    private val colorFormat: GpuTextureFormat
) {
    private val geometryUploader = GeometryUploader(device)
    private val geometryCache = mutableMapOf<Any, UploadedGeometry>()
    private val pipelineCache = mutableMapOf<PipelineKey, UnlitPipelineFactory.PipelineResources>()
    private val meshCache = mutableMapOf<Mesh, MeshResources>()

    suspend fun prepareMeshes(meshes: Collection<Mesh>) {
        meshes.forEach { ensureMeshResources(it) }
    }

    fun prepareMeshesBlocking(meshes: Collection<Mesh>) {
        runBlocking { prepareMeshes(meshes) }
    }

    fun record(pass: GpuRenderPassEncoder, meshes: Collection<Mesh>, viewProjection: Mat4) {
        meshes.forEach { mesh ->
            val resources = meshCache[mesh] ?: return@forEach
            val worldMatrix = mesh.getWorldMatrix()
            val mvp = TMP_MAT.multiply(viewProjection, worldMatrix)
            resources.uniformBuffer.writeFloats(mvp.toFloatArray(copy = true))

            pass.setPipeline(resources.pipeline.pipeline)
            pass.setBindGroup(0, resources.bindGroup)
            pass.setVertexBuffer(0, resources.geometry.vertexBuffer)

            val indexBuffer = resources.geometry.indexBuffer
            val indexCount = resources.geometry.indexCount
            val indexFormat = resources.geometry.indexFormat

            if (indexBuffer != null && indexCount != null && indexCount > 0 && indexFormat != null) {
                pass.setIndexBuffer(indexBuffer, indexFormat, 0L)
                pass.drawIndexed(indexCount)
            } else {
                pass.draw(resources.geometry.vertexCount)
            }
        }
    }

    fun dispose() {
        meshCache.values.forEach { it.uniformBuffer.destroy() }
        geometryCache.values.forEach { it.destroy() }
        meshCache.clear()
        geometryCache.clear()
        pipelineCache.clear()
    }

    private suspend fun ensureMeshResources(mesh: Mesh): MeshResources {
        meshCache[mesh]?.let { return it }
        val blueprint = mesh.material.toBindingBlueprint()
        val pipeline = pipelineCache.getOrPut(PipelineKey(blueprint::class, blueprint.renderState, colorFormat)) {
            blueprint.createPipeline(device, colorFormat)
        }
        val geometry = geometryCache.getOrPut(mesh.geometry) {
            geometryUploader.upload(mesh.geometry, mesh.name)
        }
        val uniformBuffer = device.createBuffer(
            GpuBufferDescriptor(
                label = "${mesh.name}-uniforms",
                size = Float.SIZE_BYTES * 16L,
                usage = gpuBufferUsage(GpuBufferUsage.UNIFORM, GpuBufferUsage.COPY_DST)
            )
        )
        val bindGroup = UnlitPipelineFactory.createUniformBindGroup(
            device = device,
            layout = pipeline.bindGroupLayout,
            uniformBuffer = uniformBuffer,
            label = "${mesh.name}-bind-group"
        )

        val resources = MeshResources(pipeline, geometry, uniformBuffer, bindGroup, blueprint)
        meshCache[mesh] = resources
        return resources
    }

    private data class MeshResources(
        val pipeline: UnlitPipelineFactory.PipelineResources,
        val geometry: UploadedGeometry,
        val uniformBuffer: io.kreekt.gpu.GpuBuffer,
        val bindGroup: io.kreekt.gpu.GpuBindGroup,
        val blueprint: MaterialBindingBlueprint
    )

    private data class PipelineKey(
        val type: KClass<out MaterialBindingBlueprint>,
        val renderState: RenderState,
        val format: GpuTextureFormat
    )

    private companion object {
        private val TMP_MAT = mat4()
    }
}
