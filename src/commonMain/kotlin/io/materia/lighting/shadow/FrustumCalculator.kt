package io.materia.lighting.shadow

import io.materia.camera.Camera
import io.materia.core.math.Box3
import io.materia.core.math.Matrix4
import io.materia.core.math.Vector3
import io.materia.core.scene.Scene
import io.materia.lighting.DirectionalLight
import kotlin.math.*

/**
 * Frustum calculation for shadow mapping
 */
internal class FrustumCalculator {

    /**
     * Calculate cascade splits for CSM
     */
    fun calculateCascadeSplits(camera: Camera, cascadeCount: Int): FloatArray {
        val splits = FloatArray(cascadeCount + 1)
        splits[0] = camera.near
        splits[cascadeCount] = camera.far

        val range = camera.far - camera.near
        val ratio = camera.far / camera.near

        for (i in 1 until cascadeCount) {
            val p = i.toFloat() / cascadeCount

            val logSplit = camera.near * ratio.pow(p)
            val linearSplit = camera.near + range * p

            val lambda = 0.5f
            splits[i] = linearSplit * lambda + logSplit * (1f - lambda)
        }

        return splits
    }

    /**
     * Calculate frustum for directional light
     */
    fun calculateDirectionalLightFrustum(light: DirectionalLight, scene: Scene): LightFrustum {
        val sceneBounds = Box3()
        scene.traverse { obj ->
            if (obj is io.materia.core.scene.Mesh) {
                val objBounds = obj.geometry.boundingBox ?: Box3()
                val worldMin = objBounds.min.copy().applyMatrix4(obj.matrixWorld)
                val worldMax = objBounds.max.copy().applyMatrix4(obj.matrixWorld)
                sceneBounds.expandByPoint(worldMin)
                sceneBounds.expandByPoint(worldMax)
            }
        }

        sceneBounds.expandByScalar(1.0f)

        val lightPos = light.position
        val target = lightPos + light.direction
        val lightViewMatrix = createLookAtMatrix(lightPos, target, Vector3.UP)

        val corners = arrayOf(
            Vector3(sceneBounds.min.x, sceneBounds.min.y, sceneBounds.min.z),
            Vector3(sceneBounds.max.x, sceneBounds.min.y, sceneBounds.min.z),
            Vector3(sceneBounds.min.x, sceneBounds.max.y, sceneBounds.min.z),
            Vector3(sceneBounds.max.x, sceneBounds.max.y, sceneBounds.min.z),
            Vector3(sceneBounds.min.x, sceneBounds.min.y, sceneBounds.max.z),
            Vector3(sceneBounds.max.x, sceneBounds.min.y, sceneBounds.max.z),
            Vector3(sceneBounds.min.x, sceneBounds.max.y, sceneBounds.max.z),
            Vector3(sceneBounds.max.x, sceneBounds.max.y, sceneBounds.max.z)
        )

        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY

        for (corner in corners) {
            val transformed = corner.copy().applyMatrix4(lightViewMatrix)
            minX = min(minX, transformed.x)
            maxX = max(maxX, transformed.x)
            minY = min(minY, transformed.y)
            maxY = max(maxY, transformed.y)
            minZ = min(minZ, transformed.z)
            maxZ = max(maxZ, transformed.z)
        }

        return LightFrustum(minX, maxX, minY, maxY, minZ, maxZ)
    }

    /**
     * Calculate frustum for directional light from camera view
     */
    fun calculateDirectionalLightFrustumFromCamera(
        light: DirectionalLight,
        camera: Camera
    ): LightFrustum {
        val frustumCorners = getCameraFrustumCorners(camera)

        val lightPos = light.position
        val target = lightPos + light.direction
        val lightViewMatrix = createLookAtMatrix(lightPos, target, Vector3.UP)

        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY

        for (corner in frustumCorners) {
            val transformed = corner.copy().applyMatrix4(lightViewMatrix)
            minX = min(minX, transformed.x)
            maxX = max(maxX, transformed.x)
            minY = min(minY, transformed.y)
            maxY = max(maxY, transformed.y)
            minZ = min(minZ, transformed.z)
            maxZ = max(maxZ, transformed.z)
        }

        val padding = 10f
        minZ -= padding
        maxZ += padding

        return LightFrustum(minX, maxX, minY, maxY, minZ, maxZ)
    }

    /**
     * Calculate frustum for a cascade
     */
    fun calculateCascadeFrustum(camera: Camera, near: Float, far: Float): LightFrustum {
        val frustumCorners = arrayOf(
            Vector3(-1f, -1f, -1f), Vector3(1f, -1f, -1f),
            Vector3(1f, 1f, -1f), Vector3(-1f, 1f, -1f),
            Vector3(-1f, -1f, 1f), Vector3(1f, -1f, 1f),
            Vector3(1f, 1f, 1f), Vector3(-1f, 1f, 1f)
        )

        val invProjView =
            camera.projectionMatrix.multiply(camera.matrixWorldInverse.clone().invert())
        for (i in frustumCorners.indices) {
            frustumCorners[i] = frustumCorners[i].applyMatrix4(invProjView)
        }

        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY

        for (corner in frustumCorners) {
            minX = min(minX, corner.x)
            maxX = max(maxX, corner.x)
            minY = min(minY, corner.y)
            maxY = max(maxY, corner.y)
            minZ = min(minZ, corner.z)
            maxZ = max(maxZ, corner.z)
        }

        return LightFrustum(minX, maxX, minY, maxY, minZ, maxZ)
    }

    /**
     * Get camera frustum corners in world space
     */
    private fun getCameraFrustumCorners(camera: Camera): Array<Vector3> {
        val corners = arrayOf(
            Vector3(-1f, -1f, -1f), Vector3(1f, -1f, -1f),
            Vector3(1f, 1f, -1f), Vector3(-1f, 1f, -1f),
            Vector3(-1f, -1f, 1f), Vector3(1f, -1f, 1f),
            Vector3(1f, 1f, 1f), Vector3(-1f, 1f, 1f)
        )

        val invProjView =
            camera.projectionMatrix.clone().multiply(camera.matrixWorldInverse).invert()
        for (i in corners.indices) {
            corners[i] = corners[i].applyMatrix4(invProjView)
        }

        return corners
    }

    /**
     * Create a look-at matrix for light space transformation
     */
    private fun createLookAtMatrix(position: Vector3, target: Vector3, up: Vector3): Matrix4 {
        val zAxis = (position - target).normalized
        val xAxis = up.cross(zAxis).normalized
        val yAxis = zAxis.cross(xAxis)

        return Matrix4().set(
            xAxis.x, yAxis.x, zAxis.x, position.x,
            xAxis.y, yAxis.y, zAxis.y, position.y,
            xAxis.z, yAxis.z, zAxis.z, position.z,
            0f, 0f, 0f, 1f
        ).invert()
    }
}
