package io.materia.engine.render

import io.materia.engine.geometry.AttributeSemantic
import io.materia.engine.geometry.AttributeType
import io.materia.engine.geometry.Geometry
import io.materia.engine.geometry.GeometryAttribute
import io.materia.engine.geometry.GeometryLayout
import io.materia.engine.math.Mat4
import io.materia.engine.math.mat4
import io.materia.engine.material.RenderState
import io.materia.engine.scene.InstancedPoints
import io.materia.engine.scene.Mesh
import io.materia.engine.scene.VertexBuffer
import io.materia.gpu.GpuBufferDescriptor
import io.materia.gpu.GpuBufferUsage
import io.materia.gpu.GpuDevice
import io.materia.gpu.GpuIndexFormat
import io.materia.gpu.GpuRenderPassEncoder
import io.materia.gpu.GpuTextureFormat
import io.materia.gpu.gpuBufferUsage
import kotlin.reflect.KClass

class SceneRenderer(
    private val device: GpuDevice,
    private val colorFormat: GpuTextureFormat
) {
    private val geometryUploader = GeometryUploader(device)
    private val geometryCache = mutableMapOf<Any, UploadedGeometry>()
    private val pipelineCache = mutableMapOf<PipelineKey, UnlitPipelineFactory.PipelineResources>()
    private val meshCache = mutableMapOf<Mesh, MeshResources>()
    private val pointsCache = mutableMapOf<InstancedPoints, PointsResources>()

    fun prepare(meshes: Collection<Mesh>, points: Collection<InstancedPoints>) {
        val meshSet = meshes.toSet()
        val pointSet = points.toSet()

        meshCache.keys.toList().forEach { mesh ->
            if (mesh !in meshSet) {
                meshCache.remove(mesh)?.let { resources ->
                    resources.dispose()
                    geometryCache.remove(resources.sourceGeometry)?.destroy()
                }
            }
        }

        pointsCache.keys.toList().forEach { node ->
            if (node !in pointSet) {
                pointsCache.remove(node)?.let { resources ->
                    resources.dispose()
                    geometryCache.remove(resources.sourceNode)?.destroy()
                }
            }
        }

        meshes.forEach { ensureMeshResources(it) }
        points.forEach { ensurePointsResources(it) }
    }

    fun prepareBlocking(meshes: Collection<Mesh>, points: Collection<InstancedPoints>) {
        prepare(meshes, points)
    }

    fun record(
        pass: GpuRenderPassEncoder,
        meshes: Collection<Mesh>,
        points: Collection<InstancedPoints>,
        viewProjection: Mat4
    ) {
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

        points.forEach { pointNode ->
            val resources = pointsCache[pointNode] ?: return@forEach
            val worldMatrix = pointNode.getWorldMatrix()
            val mvp = TMP_MAT.multiply(viewProjection, worldMatrix)
            resources.uniformBuffer.writeFloats(mvp.toFloatArray(copy = true))

            pass.setPipeline(resources.pipeline.pipeline)
            pass.setBindGroup(0, resources.bindGroup)
            pass.setVertexBuffer(0, resources.geometry.vertexBuffer)
            pass.draw(1, resources.instanceCount)
        }
    }

    fun dispose() {
        meshCache.values.forEach { it.uniformBuffer.destroy() }
        pointsCache.values.forEach { it.uniformBuffer.destroy() }
        geometryCache.values.forEach { it.destroy() }
        pointsCache.clear()
        meshCache.clear()
        geometryCache.clear()
        pipelineCache.clear()
    }

    private fun ensureMeshResources(mesh: Mesh): MeshResources {
        val blueprint = mesh.material.toBindingBlueprint()
        meshCache[mesh]?.let { existing ->
            if (existing.sourceGeometry === mesh.geometry &&
                existing.blueprint::class == blueprint::class &&
                existing.blueprint.renderState == blueprint.renderState
            ) {
                return existing
            } else {
                existing.dispose()
                geometryCache.remove(existing.sourceGeometry)?.destroy()
                meshCache.remove(mesh)
            }
        }
        val pipeline = pipelineCache.getOrPut(
            PipelineKey(
                blueprint::class,
                blueprint.renderState,
                colorFormat
            )
        ) {
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

        val resources =
            MeshResources(mesh.geometry, pipeline, geometry, uniformBuffer, bindGroup, blueprint)
        meshCache[mesh] = resources
        return resources
    }

    private fun ensurePointsResources(node: InstancedPoints): PointsResources {
        val blueprint = node.material.toBindingBlueprint()
        pointsCache[node]?.let { existing ->
            if (existing.sourceNode === node &&
                existing.blueprint::class == blueprint::class &&
                existing.blueprint.renderState == blueprint.renderState
            ) {
                return existing
            } else {
                existing.dispose()
                geometryCache.remove(existing.sourceNode)?.destroy()
                pointsCache.remove(node)
            }
        }

        require(node.componentsPerInstance == 11) {
            "InstancedPoints expects 11 floats per instance (pos3 + color3 + size + extra4), got ${node.componentsPerInstance}"
        }

        val pipeline = pipelineCache.getOrPut(
            PipelineKey(
                blueprint::class,
                blueprint.renderState,
                colorFormat
            )
        ) {
            blueprint.createPipeline(device, colorFormat)
        }

        val geometry = geometryCache.getOrPut(node) {
            val geometry = buildInstancedPointsGeometry(node)
            geometryUploader.upload(geometry, node.name)
        }

        val uniformBuffer = device.createBuffer(
            GpuBufferDescriptor(
                label = "${node.name}-uniforms",
                size = Float.SIZE_BYTES * 16L,
                usage = gpuBufferUsage(GpuBufferUsage.UNIFORM, GpuBufferUsage.COPY_DST)
            )
        )
        val bindGroup = UnlitPipelineFactory.createUniformBindGroup(
            device = device,
            layout = pipeline.bindGroupLayout,
            uniformBuffer = uniformBuffer,
            label = "${node.name}-bind-group"
        )

        val instanceCount = node.instanceData.size / node.componentsPerInstance
        val resources = PointsResources(
            node,
            pipeline,
            geometry,
            uniformBuffer,
            bindGroup,
            blueprint,
            instanceCount
        )
        pointsCache[node] = resources
        return resources
    }

    private data class MeshResources(
        val sourceGeometry: Geometry,
        val pipeline: UnlitPipelineFactory.PipelineResources,
        val geometry: UploadedGeometry,
        val uniformBuffer: io.materia.gpu.GpuBuffer,
        val bindGroup: io.materia.gpu.GpuBindGroup,
        val blueprint: MaterialBindingBlueprint
    ) {
        fun dispose() {
            uniformBuffer.destroy()
        }
    }

    private data class PointsResources(
        val sourceNode: InstancedPoints,
        val pipeline: UnlitPipelineFactory.PipelineResources,
        val geometry: UploadedGeometry,
        val uniformBuffer: io.materia.gpu.GpuBuffer,
        val bindGroup: io.materia.gpu.GpuBindGroup,
        val blueprint: MaterialBindingBlueprint,
        val instanceCount: Int
    ) {
        fun dispose() {
            uniformBuffer.destroy()
            geometry.destroy()
        }
    }

    private data class PipelineKey(
        val type: KClass<out MaterialBindingBlueprint>,
        val renderState: RenderState,
        val format: GpuTextureFormat
    )

    private companion object {
        private val TMP_MAT = mat4()
    }

    private fun buildInstancedPointsGeometry(node: InstancedPoints): Geometry {
        val layout = GeometryLayout(
            stride = node.componentsPerInstance * Float.SIZE_BYTES,
            attributes = mapOf(
                AttributeSemantic.POSITION to GeometryAttribute(0, 3, AttributeType.FLOAT32),
                AttributeSemantic.COLOR to GeometryAttribute(
                    Float.SIZE_BYTES * 3,
                    3,
                    AttributeType.FLOAT32
                )
            )
        )
        return Geometry(
            vertexBuffer = VertexBuffer(node.instanceData, layout.stride),
            layout = layout
        )
    }
}
