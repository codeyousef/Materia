/**
 * Rectangle Packing Algorithms
 * Core packing algorithms for texture atlas generation
 */
package io.materia.material.atlas

import io.materia.material.Rectangle

/**
 * Base interface for rectangle packing algorithms
 */
interface RectanglePacker {
    fun findBestFit(width: Int, height: Int, allowRotation: Boolean): Rectangle?
    fun markRectangleAsUsed(rectangle: Rectangle)
    fun freeRectangle(rectangle: Rectangle)
    fun reset()
}

/**
 * Max Rects packing algorithm implementation
 * Industry-standard algorithm for efficient rectangle packing
 */
class MaxRectsPackager : RectanglePacker {
    private val freeRectangles = mutableListOf<Rectangle>()
    private val usedRectangles = mutableListOf<Rectangle>()

    init {
        reset()
    }

    override fun findBestFit(width: Int, height: Int, allowRotation: Boolean): Rectangle? {
        var bestRectangle: Rectangle? = null
        var bestShortSideFit = Int.MAX_VALUE
        var bestLongSideFit = Int.MAX_VALUE

        for (rect in freeRectangles) {
            // Try normal orientation
            if (rect.width >= width && rect.height >= height) {
                val leftoverHorizontal = rect.width - width
                val leftoverVertical = rect.height - height
                val shortSideFit = minOf(leftoverHorizontal, leftoverVertical)
                val longSideFit = maxOf(leftoverHorizontal, leftoverVertical)

                if (shortSideFit < bestShortSideFit ||
                    (shortSideFit == bestShortSideFit && longSideFit < bestLongSideFit)
                ) {
                    bestRectangle = Rectangle(rect.x, rect.y, width, height)
                    bestShortSideFit = shortSideFit
                    bestLongSideFit = longSideFit
                }
            }

            // Try rotated orientation
            if (allowRotation && rect.width >= height && rect.height >= width) {
                val leftoverHorizontal = rect.width - height
                val leftoverVertical = rect.height - width
                val shortSideFit = minOf(leftoverHorizontal, leftoverVertical)
                val longSideFit = maxOf(leftoverHorizontal, leftoverVertical)

                if (shortSideFit < bestShortSideFit ||
                    (shortSideFit == bestShortSideFit && longSideFit < bestLongSideFit)
                ) {
                    bestRectangle = Rectangle(rect.x, rect.y, height, width)
                    bestShortSideFit = shortSideFit
                    bestLongSideFit = longSideFit
                }
            }
        }

        return bestRectangle
    }

    override fun markRectangleAsUsed(rectangle: Rectangle) {
        usedRectangles.add(rectangle)

        // Split intersecting free rectangles
        val toRemove = mutableListOf<Rectangle>()
        val toAdd = mutableListOf<Rectangle>()

        for (freeRect in freeRectangles) {
            if (rectanglesIntersect(rectangle, freeRect)) {
                toRemove.add(freeRect)
                // Create new rectangles from the split
                val splitRects = splitRectangle(freeRect, rectangle)
                toAdd.addAll(splitRects)
            }
        }

        freeRectangles.removeAll(toRemove)
        freeRectangles.addAll(toAdd)

        // Remove redundant rectangles
        pruneRectangles()
    }

    override fun freeRectangle(rectangle: Rectangle) {
        usedRectangles.remove(rectangle)
        freeRectangles.add(rectangle)
        // Merge adjacent free rectangles
        mergeRectangles()
    }

    override fun reset() {
        freeRectangles.clear()
        usedRectangles.clear()
        freeRectangles.add(Rectangle(0, 0, 2048, 2048)) // Default atlas size
    }

    private fun rectanglesIntersect(a: Rectangle, b: Rectangle): Boolean {
        return !(a.x >= b.x + b.width || a.x + a.width <= b.x ||
                a.y >= b.y + b.height || a.y + a.height <= b.y)
    }

    private fun splitRectangle(freeRect: Rectangle, usedRect: Rectangle): List<Rectangle> {
        val result = mutableListOf<Rectangle>()

        // Left side
        if (usedRect.x > freeRect.x && usedRect.x < freeRect.x + freeRect.width) {
            result.add(
                Rectangle(
                    freeRect.x, freeRect.y,
                    usedRect.x - freeRect.x, freeRect.height
                )
            )
        }

        // Right side
        if (usedRect.x + usedRect.width < freeRect.x + freeRect.width) {
            result.add(
                Rectangle(
                    usedRect.x + usedRect.width, freeRect.y,
                    freeRect.x + freeRect.width - (usedRect.x + usedRect.width), freeRect.height
                )
            )
        }

        // Bottom side
        if (usedRect.y > freeRect.y && usedRect.y < freeRect.y + freeRect.height) {
            result.add(
                Rectangle(
                    freeRect.x, freeRect.y,
                    freeRect.width, usedRect.y - freeRect.y
                )
            )
        }

        // Top side
        if (usedRect.y + usedRect.height < freeRect.y + freeRect.height) {
            result.add(
                Rectangle(
                    freeRect.x, usedRect.y + usedRect.height,
                    freeRect.width, freeRect.y + freeRect.height - (usedRect.y + usedRect.height)
                )
            )
        }

        return result
    }

    private fun pruneRectangles() {
        val toRemove = mutableListOf<Rectangle>()
        for (i in freeRectangles.indices) {
            for (j in freeRectangles.indices) {
                if (i != j && rectangleContains(freeRectangles[j], freeRectangles[i])) {
                    toRemove.add(freeRectangles[i])
                    break
                }
            }
        }
        freeRectangles.removeAll(toRemove)
    }

    private fun rectangleContains(container: Rectangle, contained: Rectangle): Boolean {
        return container.x <= contained.x &&
                container.y <= contained.y &&
                container.x + container.width >= contained.x + contained.width &&
                container.y + container.height >= contained.y + contained.height
    }

    private fun mergeRectangles() {
        // Simplified merge with iteration limit to prevent infinite loops
        var merged = true
        var iterations = 0
        val maxIterations = freeRectangles.size * freeRectangles.size

        while (merged && iterations < maxIterations) {
            merged = false
            iterations++

            for (i in freeRectangles.indices) {
                for (j in i + 1 until freeRectangles.size) {
                    val rect1 = freeRectangles[i]
                    val rect2 = freeRectangles[j]
                    val mergedRect = tryMergeRectangles(rect1, rect2)
                    if (mergedRect != null) {
                        freeRectangles.removeAt(j)
                        freeRectangles.removeAt(i)
                        freeRectangles.add(mergedRect)
                        merged = true
                        break
                    }
                }
                if (merged) break
            }
        }

        if (iterations >= maxIterations) {
            println("WARNING: Rectangle merging hit iteration limit ($maxIterations)")
        }
    }

    private fun tryMergeRectangles(a: Rectangle, b: Rectangle): Rectangle? {
        // Can merge horizontally
        if (a.y == b.y && a.height == b.height) {
            if (a.x + a.width == b.x) {
                return Rectangle(a.x, a.y, a.width + b.width, a.height)
            }
            if (b.x + b.width == a.x) {
                return Rectangle(b.x, b.y, a.width + b.width, a.height)
            }
        }

        // Can merge vertically
        if (a.x == b.x && a.width == b.width) {
            if (a.y + a.height == b.y) {
                return Rectangle(a.x, a.y, a.width, a.height + b.height)
            }
            if (b.y + b.height == a.y) {
                return Rectangle(a.x, b.y, a.width, a.height + b.height)
            }
        }

        return null
    }
}
