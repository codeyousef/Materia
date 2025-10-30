package io.materia.shape

import io.materia.core.math.Vector2
import kotlin.math.max
import kotlin.math.min

/**
 * Shape utility functions including Earcut triangulation algorithm
 * Triangulates 2D polygons with holes using the Earcut algorithm
 *
 * Based on Three.js ShapeUtils and Earcut library
 */
object ShapeUtils {

    /**
     * Calculate the area of a 2D contour
     */
    fun area(contour: List<Vector2>): Float {
        val n = contour.size
        if (n < 3) return 0f // Need at least 3 points for an area

        var a = 0f

        for (p in 0 until n) {
            val q = (p + 1) % n
            a += contour[p].x * contour[q].y - contour[q].x * contour[p].y
        }

        return a * 0.5f
    }

    /**
     * Check if contour is clockwise
     */
    fun isClockWise(pts: List<Vector2>): Boolean {
        return area(pts) < 0
    }

    /**
     * Triangulate a shape with holes using Earcut algorithm
     * Returns array of triangle indices
     */
    fun triangulate(contour: List<Vector2>, holes: List<List<Vector2>> = emptyList()): List<Int> {
        // Flatten vertices
        val vertices = mutableListOf<Float>()
        val holeIndices = mutableListOf<Int>()

        // Add main contour
        for (point in contour) {
            vertices.add(point.x)
            vertices.add(point.y)
        }

        // Add holes
        for (hole in holes) {
            holeIndices.add(vertices.size / 2)
            for (point in hole) {
                vertices.add(point.x)
                vertices.add(point.y)
            }
        }

        return earcut(vertices, holeIndices)
    }

    /**
     * Triangulate using ear clipping
     * This is a simplified implementation - full Earcut has more optimizations
     */
    fun triangulateShape(contour: List<Vector2>, holes: List<List<Vector2>>): List<List<Int>> {
        val indices = triangulate(contour, holes)
        val faces = mutableListOf<List<Int>>()

        for (i in indices.indices step 3) {
            faces.add(listOf(indices[i], indices[i + 1], indices[i + 2]))
        }

        return faces
    }

    /**
     * Earcut triangulation algorithm
     * Converts polygon to triangles using ear clipping
     */
    private fun earcut(data: List<Float>, holeIndices: List<Int>, dim: Int = 2): List<Int> {
        val hasHoles = holeIndices.isNotEmpty()
        val outerLen = if (hasHoles) holeIndices[0] * dim else data.size
        var outerNode = linkedList(data, 0, outerLen, dim, true)
        val triangles = mutableListOf<Int>()

        if (outerNode == null || outerNode.next == outerNode.prev) return triangles

        // Link holes if present
        if (hasHoles) {
            outerNode = eliminateHoles(data, holeIndices, outerNode, dim)
        }

        // Run ear clipping (outerNode is non-null from linkedList)
        earcutLinked(outerNode, triangles, dim)

        return triangles
    }

    /**
     * Create a circular linked list from polygon points
     */
    private fun linkedList(
        data: List<Float>,
        start: Int,
        end: Int,
        dim: Int,
        clockwise: Boolean
    ): Node? {
        var last: Node? = null

        if (clockwise == (signedArea(data, start, end, dim) > 0)) {
            for (i in start until end step dim) {
                if (i + 1 < data.size) {
                    last = insertNode(i / dim, data[i], data[i + 1], last)
                }
            }
        } else {
            for (i in (end - dim) downTo start step dim) {
                if (i + 1 < data.size) {
                    last = insertNode(i / dim, data[i], data[i + 1], last)
                }
            }
        }

        last?.next?.let { next ->
            if (equals(last, next)) {
                removeNode(last)
                last = next
            }
        }

        return last
    }

    /**
     * Eliminate holes by connecting to outer ring
     */
    private fun eliminateHoles(
        data: List<Float>,
        holeIndices: List<Int>,
        outerNode: Node,
        dim: Int
    ): Node {
        val queue = mutableListOf<Node>()

        for (i in holeIndices.indices) {
            val start = holeIndices[i] * dim
            val end = if (i < holeIndices.size - 1) holeIndices[i + 1] * dim else data.size

            val list = linkedList(data, start, end, dim, false)
            if (list == list?.next) list?.steiner = true
            list?.let { queue.add(getLeftmost(it)) }
        }

        queue.sortBy { it.x }

        var result = outerNode
        for (node in queue) {
            eliminateHole(node, result)
            result = filterPoints(result, result.next ?: result) ?: result
        }

        return result
    }

    /**
     * Find the leftmost point in a linked list
     */
    private fun getLeftmost(start: Node): Node {
        var p: Node? = start
        var leftmost = start

        do {
            if (p != null && (p.x < leftmost.x || (p.x == leftmost.x && p.y < leftmost.y))) {
                leftmost = p
            }
            p = p?.next
        } while (p != null && p != start)

        return leftmost
    }

    /**
     * Connect hole to outer ring
     */
    private fun eliminateHole(hole: Node, outerNode: Node) {
        var bridge = findHoleBridge(hole, outerNode) ?: return
        val bridgeReverse = splitPolygon(bridge, hole)

        bridgeReverse.next?.let { filterPoints(bridgeReverse, it) }
        bridge.next?.let { filterPoints(bridge, it) }
    }

    /**
     * Find a bridge connecting hole to outer polygon
     */
    private fun findHoleBridge(hole: Node, outerNode: Node): Node? {
        var p: Node? = outerNode
        val hx = hole.x
        val hy = hole.y
        var qx = Float.NEGATIVE_INFINITY
        var m: Node? = null

        do {
            val pNext = p?.next
            if (p != null && pNext != null && hy <= p.y && hy >= pNext.y && pNext.y != p.y) {
                // Guard against division by zero
                val denominator = pNext.y - p.y
                val x = if (kotlin.math.abs(denominator) >= 0.000001f) {
                    p.x + (hy - p.y) * (pNext.x - p.x) / denominator
                } else {
                    p.x // Degenerate case: vertical line segment
                }
                if (x <= hx && x > qx) {
                    qx = x
                    m = if (x == hx) {
                        if (hy == p.y) p else if (hy == pNext.y) pNext else p
                    } else {
                        p
                    }
                }
            }
            p = p?.next
        } while (p != null && p != outerNode)

        return m
    }

    /**
     * Perform ear clipping on linked list
     */
    private fun earcutLinked(
        earParam: Node?,
        triangles: MutableList<Int>,
        dim: Int,
        pass: Int = 0
    ) {
        if (earParam == null) return

        var ear: Node? = earParam
        var stop: Node? = ear
        var prev: Node
        var next: Node
        val maxIterations = 5000 // Safety limit for ear-cutting
        var iterations = 0

        while (ear != null && ear.prev != ear.next && iterations < maxIterations) {
            iterations++
            val prevNode = ear.prev ?: break
            val nextNode = ear.next ?: break
            prev = prevNode
            next = nextNode

            if (isEar(ear)) {
                triangles.add(prevNode.i)
                triangles.add(ear.i)
                triangles.add(nextNode.i)

                removeNode(ear)
                ear = nextNode.next
                stop = nextNode.next
                continue
            }

            ear = nextNode

            if (ear == stop) {
                when (pass) {
                    0 -> {
                        val filtered = filterPoints(ear, null)
                        if (filtered != null) {
                            earcutLinked(filtered, triangles, dim, 1)
                        }
                    }

                    1 -> {
                        val filtered = filterPoints(ear, null)
                        if (filtered != null) {
                            ear = cureLocalIntersections(filtered, triangles)
                            earcutLinked(ear, triangles, dim, 2)
                        }
                    }
                }
                break
            }
        }

        // Warn if iteration limit was reached (possible infinite loop or complex polygon)
        if (iterations >= maxIterations) {
            println("WARNING: ShapeUtils.earcutLinked reached max iterations ($maxIterations). Polygon may be too complex or malformed.")
        }
    }

    /**
     * Check if ear is valid
     */
    private fun isEar(ear: Node): Boolean {
        val a = ear.prev ?: return false
        val b = ear
        val c = ear.next ?: return false

        if (area(a, b, c) >= 0) return false

        var p = c.next

        while (p != null && p != ear.prev) {
            val pPrev = p.prev
            val pNext = p.next
            if (pPrev != null && pNext != null &&
                pointInTriangle(a.x, a.y, b.x, b.y, c.x, c.y, p.x, p.y) &&
                area(pPrev, p, pNext) >= 0
            ) {
                return false
            }
            p = p.next
        }

        return true
    }

    /**
     * Calculate signed area of triangle
     */
    private fun area(p: Node, q: Node, r: Node): Float {
        return (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y)
    }

    /**
     * Check if point is in triangle
     */
    private fun pointInTriangle(
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
        cx: Float,
        cy: Float,
        px: Float,
        py: Float
    ): Boolean {
        return (cx - px) * (ay - py) - (ax - px) * (cy - py) >= 0 &&
                (ax - px) * (by - py) - (bx - px) * (ay - py) >= 0 &&
                (bx - px) * (cy - py) - (cx - px) * (by - py) >= 0
    }

    /**
     * Calculate signed area of polygon
     */
    private fun signedArea(data: List<Float>, start: Int, end: Int, dim: Int): Float {
        var sum = 0f
        var j = end - dim

        for (i in start until end step dim) {
            if (i + 1 < data.size && j + 1 < data.size) {
                sum += (data[j] - data[i]) * (data[i + 1] + data[j + 1])
            }
            j = i
        }

        return sum
    }

    /**
     * Filter out collinear or duplicate points
     */
    private fun filterPoints(start: Node, end: Node?): Node? {
        // start parameter is non-null Node type, so this check is unnecessary
        val e = end ?: start

        var p = start
        var again: Boolean

        do {
            again = false

            val pNext = p.next
            val pPrev = p.prev
            if (pNext != null && pPrev != null &&
                !p.steiner && (equals(p, pNext) || area(pPrev, p, pNext) == 0f)
            ) {
                removeNode(p)
                val ePrev = e.prev ?: return null
                p = ePrev
                if (p == p.next) break
                again = true
            } else {
                p = pNext ?: break
            }
        } while (again || p != e)

        return e
    }

    /**
     * Cure local self-intersections
     */
    private fun cureLocalIntersections(start: Node, triangles: MutableList<Int>): Node? {
        var p: Node? = start

        do {
            val a = p ?: break
            val aNext = a.next ?: break
            val b = aNext.next ?: break
            val bNext = b.next ?: break

            if (!equals(a, b) && intersects(a, aNext, b, bNext) && locallyInside(
                    a,
                    b
                ) && locallyInside(b, a)
            ) {
                triangles.add(a.i)
                triangles.add(aNext.i)
                triangles.add(b.i)

                removeNode(aNext)
                removeNode(bNext)

                p = b
            }
            p = p?.next
        } while (p != null && p != start)

        return p?.let { filterPoints(it, null) }
    }

    /**
     * Check if segments intersect
     */
    private fun intersects(p1: Node, q1: Node, p2: Node, q2: Node): Boolean {
        val o1 = sign(area(p1, q1, p2))
        val o2 = sign(area(p1, q1, q2))
        val o3 = sign(area(p2, q2, p1))
        val o4 = sign(area(p2, q2, q1))

        return if (o1 != o2 && o3 != o4) true
        else if (o1 == 0 && onSegment(p1, p2, q1)) true
        else if (o2 == 0 && onSegment(p1, q2, q1)) true
        else if (o3 == 0 && onSegment(p2, p1, q2)) true
        else o4 == 0 && onSegment(p2, q1, q2)
    }

    /**
     * Check if point q lies on segment pr
     */
    private fun onSegment(p: Node, q: Node, r: Node): Boolean {
        return q.x <= max(p.x, r.x) && q.x >= min(p.x, r.x) &&
                q.y <= max(p.y, r.y) && q.y >= min(p.y, r.y)
    }

    private fun sign(num: Float): Int = when {
        num > 0 -> 1
        num < 0 -> -1
        else -> 0
    }

    private fun equals(p1: Node, p2: Node): Boolean {
        return p1.x == p2.x && p1.y == p2.y
    }

    private fun locallyInside(a: Node, b: Node): Boolean {
        val aPrev = a.prev ?: return false
        val aNext = a.next ?: return false
        return if (area(aPrev, a, aNext) < 0) {
            area(a, b, aNext) >= 0 && area(a, aPrev, b) >= 0
        } else {
            area(a, b, aPrev) < 0 || area(a, aNext, b) < 0
        }
    }

    private fun splitPolygon(a: Node, b: Node): Node {
        val a2 = Node(a.i, a.x, a.y)
        val b2 = Node(b.i, b.x, b.y)
        val an = a.next ?: return a2
        val bp = b.prev ?: return a2

        a.next = b
        b.prev = a

        a2.next = an
        an.prev = a2

        b2.next = a2
        a2.prev = b2

        bp.next = b2
        b2.prev = bp

        return b2
    }

    private fun insertNode(i: Int, x: Float, y: Float, last: Node?): Node {
        val p = Node(i, x, y)

        if (last == null) {
            p.prev = p
            p.next = p
        } else {
            p.next = last.next
            p.prev = last
            last.next?.prev = p
            last.next = p
        }

        return p
    }

    private fun removeNode(p: Node) {
        p.next?.prev = p.prev
        p.prev?.next = p.next
        p.prevZ?.let { it.nextZ = p.nextZ }
        p.nextZ?.let { it.prevZ = p.prevZ }
    }

    /**
     * Node in linked list for triangulation
     */
    private data class Node(
        val i: Int,
        val x: Float,
        val y: Float
    ) {
        var prev: Node? = null
        var next: Node? = null
        var z: Float = 0f
        var prevZ: Node? = null
        var nextZ: Node? = null
        var steiner: Boolean = false
    }
}