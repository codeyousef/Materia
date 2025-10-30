/**
 * Platonic solids implementation following Three.js r180 API
 * All five regular convex polyhedra: Tetrahedron, Octahedron, Icosahedron, Dodecahedron, Hexahedron (Cube)
 */
package io.materia.geometry

import kotlin.math.*

/**
 * Tetrahedron geometry (4 faces, simplest platonic solid)
 *
 * @param radius Radius of the circumscribed sphere (default: 1)
 * @param detail Number of subdivision levels (default: 0)
 */
class TetrahedronGeometry(
    radius: Float = 1f,
    detail: Int = 0
) : PolyhedronGeometry(
    vertices = floatArrayOf(
        1f, 1f, 1f,
        -1f, -1f, 1f,
        -1f, 1f, -1f,
        1f, -1f, -1f
    ),
    indices = intArrayOf(
        2, 1, 0,
        0, 3, 2,
        1, 3, 0,
        2, 3, 1
    ),
    radius = radius,
    detail = detail
)

/**
 * Octahedron geometry (8 faces)
 *
 * @param radius Radius of the circumscribed sphere (default: 1)
 * @param detail Number of subdivision levels (default: 0)
 */
class OctahedronGeometry(
    radius: Float = 1f,
    detail: Int = 0
) : PolyhedronGeometry(
    vertices = floatArrayOf(
        1f, 0f, 0f,
        -1f, 0f, 0f,
        0f, 1f, 0f,
        0f, -1f, 0f,
        0f, 0f, 1f,
        0f, 0f, -1f
    ),
    indices = intArrayOf(
        0, 2, 4,
        0, 4, 3,
        0, 3, 5,
        0, 5, 2,
        1, 2, 5,
        1, 5, 3,
        1, 3, 4,
        1, 4, 2
    ),
    radius = radius,
    detail = detail
)

/**
 * Icosahedron geometry (20 faces, commonly used for sphere approximation)
 *
 * @param radius Radius of the circumscribed sphere (default: 1)
 * @param detail Number of subdivision levels (default: 0)
 */
class IcosahedronGeometry(
    radius: Float = 1f,
    detail: Int = 0
) : PolyhedronGeometry(
    vertices = run {
        val t = (1f + sqrt(5f)) / 2f  // Golden ratio

        floatArrayOf(
            -1f, t, 0f,
            1f, t, 0f,
            -1f, -t, 0f,
            1f, -t, 0f,

            0f, -1f, t,
            0f, 1f, t,
            0f, -1f, -t,
            0f, 1f, -t,

            t, 0f, -1f,
            t, 0f, 1f,
            -t, 0f, -1f,
            -t, 0f, 1f
        )
    },
    indices = intArrayOf(
        0, 11, 5,
        0, 5, 1,
        0, 1, 7,
        0, 7, 10,
        0, 10, 11,

        1, 5, 9,
        5, 11, 4,
        11, 10, 2,
        10, 7, 6,
        7, 1, 8,

        3, 9, 4,
        3, 4, 2,
        3, 2, 6,
        3, 6, 8,
        3, 8, 9,

        4, 9, 5,
        2, 4, 11,
        6, 2, 10,
        8, 6, 7,
        9, 8, 1
    ),
    radius = radius,
    detail = detail
)

/**
 * Dodecahedron geometry (12 faces)
 *
 * @param radius Radius of the circumscribed sphere (default: 1)
 * @param detail Number of subdivision levels (default: 0)
 */
class DodecahedronGeometry(
    radius: Float = 1f,
    detail: Int = 0
) : PolyhedronGeometry(
    vertices = run {
        val t = (1f + sqrt(5f)) / 2f  // Golden ratio
        val r = 1f / t

        floatArrayOf(
            // (±1, ±1, ±1)
            -1f, -1f, -1f,
            -1f, -1f, 1f,
            -1f, 1f, -1f,
            -1f, 1f, 1f,
            1f, -1f, -1f,
            1f, -1f, 1f,
            1f, 1f, -1f,
            1f, 1f, 1f,

            // (0, ±1/φ, ±φ)
            0f, -r, -t,
            0f, -r, t,
            0f, r, -t,
            0f, r, t,

            // (±1/φ, ±φ, 0)
            -r, -t, 0f,
            -r, t, 0f,
            r, -t, 0f,
            r, t, 0f,

            // (±φ, 0, ±1/φ)
            -t, 0f, -r,
            t, 0f, -r,
            -t, 0f, r,
            t, 0f, r
        )
    },
    indices = intArrayOf(
        3, 11, 7,
        3, 7, 15,
        3, 15, 13,
        7, 19, 17,
        7, 17, 6,
        7, 6, 15,
        17, 4, 8,
        17, 8, 10,
        17, 10, 6,
        8, 0, 16,
        8, 16, 2,
        8, 2, 10,
        0, 12, 1,
        0, 1, 18,
        0, 18, 16,
        6, 10, 2,
        6, 2, 13,
        6, 13, 15,
        2, 16, 18,
        2, 18, 3,
        2, 3, 13,
        18, 1, 9,
        18, 9, 11,
        18, 11, 3,
        4, 14, 12,
        4, 12, 0,
        4, 0, 8,
        11, 9, 5,
        11, 5, 19,
        11, 19, 7,
        19, 5, 14,
        19, 14, 4,
        19, 4, 17,
        1, 12, 14,
        1, 14, 5,
        1, 5, 9
    ),
    radius = radius,
    detail = detail
)
