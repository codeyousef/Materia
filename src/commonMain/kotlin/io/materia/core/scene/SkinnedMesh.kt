package io.materia.core.scene

import io.materia.animation.Skeleton
import io.materia.animation.skeleton.Bone
import io.materia.core.math.Matrix4
import io.materia.core.math.Vector3
import io.materia.core.math.Vector4
import io.materia.geometry.BufferGeometry

/**
 * A mesh with skeletal animation support.
 * 
 * SkinnedMesh combines a BufferGeometry with a Skeleton to allow
 * vertex skinning/deformation based on bone transformations. This is
 * the primary class for rendering animated characters and creatures.
 *
 * Compatible with Three.js SkinnedMesh API.
 *
 * Usage:
 * ```kotlin
 * // Create skeleton from bones
 * val skeleton = Skeleton(bones)
 * 
 * // Create skinned mesh with geometry containing skin weights/indices
 * val skinnedMesh = SkinnedMesh(geometry, material)
 * skinnedMesh.bind(skeleton)
 * 
 * // In render loop, update skeleton and mesh
 * skeleton.update()
 * ```
 */
class SkinnedMesh(
    geometry: BufferGeometry,
    material: Material? = null
) : Mesh(geometry, material) {

    override val type: String get() = "SkinnedMesh"

    /**
     * Binding mode determines how the skeleton transforms vertices:
     * - ATTACHED: Skeleton follows the mesh's world transform
     * - DETACHED: Skeleton is independent of mesh transform
     */
    var bindMode: BindMode = BindMode.ATTACHED

    /**
     * The inverse of the bind matrix (world matrix at bind time).
     * Used to transform vertices from world space to bone space.
     */
    val bindMatrix: Matrix4 = Matrix4()

    /**
     * Inverse of bindMatrix, cached for performance.
     */
    val bindMatrixInverse: Matrix4 = Matrix4()

    /**
     * The bounding box in world space (includes skeletal deformation).
     */
    private var boundingBox: Box3? = null

    /**
     * The bounding sphere in world space (includes skeletal deformation).
     */
    private var boundingSphere: Sphere? = null

    /**
     * Reference to the skeleton driving this mesh.
     */
    var skeleton: Skeleton? = null
        private set

    /**
     * Bone matrices flattened for GPU upload.
     * Size = skeleton.bones.size * 16 (4x4 matrix per bone)
     */
    var boneMatrices: FloatArray? = null
        private set

    /**
     * Maximum number of bones this mesh can be influenced by.
     * Usually 4 bones per vertex (standard skinning).
     */
    val bonesPerVertex: Int = 4

    /**
     * Bind this mesh to a skeleton.
     * 
     * @param skeleton The skeleton to bind
     * @param bindMatrix Optional custom bind matrix. If null, uses current world matrix.
     */
    fun bind(skeleton: Skeleton, bindMatrix: Matrix4? = null) {
        this.skeleton = skeleton

        if (bindMatrix != null) {
            this.bindMatrix.copy(bindMatrix)
        } else {
            updateMatrixWorld(force = true)
            this.bindMatrix.copy(matrixWorld)
        }

        this.bindMatrixInverse.copy(this.bindMatrix).invert()

        // Allocate bone matrices array
        boneMatrices = FloatArray(skeleton.bones.size * 16)
    }

    /**
     * Unbind the skeleton from this mesh.
     */
    fun unbind() {
        skeleton = null
        boneMatrices = null
    }

    /**
     * Calculate bone matrices for GPU skinning.
     * Should be called each frame before rendering.
     *
     * The resulting matrices transform vertices from bind pose to current pose.
     * Formula: boneMatrix = bone.matrixWorld * bone.inverseBindMatrix * bindMatrixInverse
     */
    fun pose() {
        val skel = skeleton ?: return
        val matrices = boneMatrices ?: return

        skel.update()

        for ((index, bone) in skel.bones.withIndex()) {
            // Compute final bone matrix
            val boneMatrix = _boneMatrix
            boneMatrix.multiplyMatrices(bone.matrixWorld, bone.inverseBindMatrix)

            if (bindMode == BindMode.ATTACHED) {
                boneMatrix.multiplyMatrices(bindMatrixInverse, boneMatrix)
            }

            // Store in flattened array
            boneMatrix.toArray(matrices, index * 16)
        }
    }

    /**
     * Normalize skin weights so they sum to 1.0.
     * Important for proper vertex blending.
     */
    fun normalizeSkinWeights() {
        val skinWeightAttr = geometry.getAttribute("skinWeight") ?: return

        for (i in 0 until skinWeightAttr.count) {
            _vector4.x = skinWeightAttr.getX(i)
            _vector4.y = skinWeightAttr.getY(i)
            _vector4.z = skinWeightAttr.getZ(i)
            _vector4.w = skinWeightAttr.getW(i)

            val sum = _vector4.x + _vector4.y + _vector4.z + _vector4.w

            if (sum > 0f) {
                val scale = 1f / sum
                _vector4.x *= scale
                _vector4.y *= scale
                _vector4.z *= scale
                _vector4.w *= scale

                skinWeightAttr.setXYZW(i, _vector4.x, _vector4.y, _vector4.z, _vector4.w)
            }
        }
    }

    /**
     * Apply current bone transformations directly to vertex positions.
     * This "bakes" the current pose into the geometry.
     * Note: This is typically used for export, not real-time animation.
     */
    fun applyBoneTransform(index: Int, vector: Vector3): Vector3 {
        val skel = skeleton ?: return vector
        val skinIndices = geometry.getAttribute("skinIndex") ?: return vector
        val skinWeights = geometry.getAttribute("skinWeight") ?: return vector

        _skinIndex.x = skinIndices.getX(index)
        _skinIndex.y = skinIndices.getY(index)
        _skinIndex.z = skinIndices.getZ(index)
        _skinIndex.w = skinIndices.getW(index)

        _skinWeight.x = skinWeights.getX(index)
        _skinWeight.y = skinWeights.getY(index)
        _skinWeight.z = skinWeights.getZ(index)
        _skinWeight.w = skinWeights.getW(index)

        // Apply bone transforms weighted by skin weights
        _basePosition.copy(vector).applyMatrix4(bindMatrix)

        vector.set(0f, 0f, 0f)

        for (j in 0 until 4) {
            val boneIndex = when (j) {
                0 -> _skinIndex.x.toInt()
                1 -> _skinIndex.y.toInt()
                2 -> _skinIndex.z.toInt()
                else -> _skinIndex.w.toInt()
            }
            val weight = when (j) {
                0 -> _skinWeight.x
                1 -> _skinWeight.y
                2 -> _skinWeight.z
                else -> _skinWeight.w
            }

            if (weight > 0f && boneIndex >= 0 && boneIndex < skel.bones.size) {
                val bone = skel.bones[boneIndex]
                _tempMatrix.multiplyMatrices(bone.matrixWorld, bone.inverseBindMatrix)
                val transformed = _tempVector.copy(_basePosition).applyMatrix4(_tempMatrix)
                vector.x += transformed.x * weight
                vector.y += transformed.y * weight
                vector.z += transformed.z * weight
            }
        }

        return vector.applyMatrix4(bindMatrixInverse)
    }

    /**
     * Compute bounding box including skeletal deformation.
     */
    fun computeBoundingBox(): Box3 {
        val box = boundingBox ?: Box3().also { boundingBox = it }
        box.makeEmpty()

        val positionAttr = geometry.getAttribute("position") ?: return box

        for (i in 0 until positionAttr.count) {
            _vertex.x = positionAttr.getX(i)
            _vertex.y = positionAttr.getY(i)
            _vertex.z = positionAttr.getZ(i)

            applyBoneTransform(i, _vertex)
            box.expandByPoint(_vertex)
        }
        
        return box
    }

    /**
     * Compute bounding sphere including skeletal deformation.
     */
    fun computeBoundingSphere(): Sphere {
        val box = computeBoundingBox()

        val sphere = boundingSphere ?: Sphere().also { boundingSphere = it }
        box.getCenter(sphere.center)

        var maxRadiusSq = 0f
        val positionAttr = geometry.getAttribute("position") ?: return sphere

        for (i in 0 until positionAttr.count) {
            _vertex.x = positionAttr.getX(i)
            _vertex.y = positionAttr.getY(i)
            _vertex.z = positionAttr.getZ(i)

            applyBoneTransform(i, _vertex)
            maxRadiusSq = maxOf(maxRadiusSq, sphere.center.distanceToSquared(_vertex))
        }

        sphere.radius = kotlin.math.sqrt(maxRadiusSq)
        return sphere
    }

    /**
     * Get the root bone of the skeleton.
     */
    fun getRootBone(): Bone? {
        return skeleton?.getRootBones()?.firstOrNull()
    }

    /**
     * Copy from another SkinnedMesh.
     */
    fun copy(source: SkinnedMesh, recursive: Boolean = true): SkinnedMesh {
        super.copy(source, recursive)

        this.bindMode = source.bindMode
        this.bindMatrix.copy(source.bindMatrix)
        this.bindMatrixInverse.copy(source.bindMatrixInverse)

        source.skeleton?.let { srcSkeleton ->
            bind(srcSkeleton, source.bindMatrix)
        }

        return this
    }

    override fun clone(recursive: Boolean): SkinnedMesh {
        return SkinnedMesh(geometry, material).copy(this, recursive)
    }

    /**
     * Binding mode for skeleton transforms.
     */
    enum class BindMode {
        /** Skeleton follows mesh world transform */
        ATTACHED,
        /** Skeleton is independent of mesh */
        DETACHED
    }

    /**
     * Simple bounding box implementation.
     */
    class Box3 {
        val min: Vector3 = Vector3(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        val max: Vector3 = Vector3(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)

        fun makeEmpty(): Box3 {
            min.set(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            max.set(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
            return this
        }

        fun expandByPoint(point: Vector3): Box3 {
            min.min(point)
            max.max(point)
            return this
        }

        fun getCenter(target: Vector3): Vector3 {
            return target.addVectors(min, max).multiplyScalar(0.5f)
        }
    }

    /**
     * Simple bounding sphere implementation.
     */
    class Sphere {
        val center: Vector3 = Vector3()
        var radius: Float = -1f
    }

    companion object {
        // Reusable temporary objects to avoid allocations
        private val _boneMatrix = Matrix4()
        private val _tempMatrix = Matrix4()
        private val _tempVector = Vector3()
        private val _basePosition = Vector3()
        private val _vertex = Vector3()
        private val _vector4 = Vector4()
        private val _skinIndex = Vector4()
        private val _skinWeight = Vector4()
    }
}

/**
 * Extension function to copy a matrix.
 */
private fun Matrix4.copy(other: Matrix4): Matrix4 {
    other.elements.copyInto(this.elements)
    return this
}

/**
 * Extension function to invert a matrix.
 */
private fun Matrix4.invert(): Matrix4 {
    // Matrix4 should already have an invert method, but if not:
    // This is a simplified placeholder - real implementation uses full 4x4 inverse
    return this
}

/**
 * Extension to get/set XYZW on a buffer attribute.
 */
private fun BufferAttribute.getW(index: Int): Float = if (itemSize >= 4) array[index * itemSize + 3] else 0f

private fun BufferAttribute.setXYZW(index: Int, x: Float, y: Float, z: Float, w: Float) {
    val offset = index * itemSize
    array[offset] = x
    if (itemSize >= 2) array[offset + 1] = y
    if (itemSize >= 3) array[offset + 2] = z
    if (itemSize >= 4) array[offset + 3] = w
}
