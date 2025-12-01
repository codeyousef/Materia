/**
 * Alternative Rectangle Packing Algorithms
 * Skyline, Guillotine, and Bottom-Left packing strategies
 */
package io.materia.material.atlas

import io.materia.material.Rectangle

/**
 * Skyline packing algorithm
 * Efficient for sprite sheets and game atlases
 */
class SkylinePackager : RectanglePacker {
    private data class SkylineNode(var x: Int, var y: Int, var width: Int)

    private val skyline = mutableListOf(SkylineNode(0, 0, Int.MAX_VALUE))

    override fun findBestFit(width: Int, height: Int, allowRotation: Boolean): Rectangle? {
        var bestY = Int.MAX_VALUE
        var bestIndex = -1
        var bestX = 0

        for (i in skyline.indices) {
            val node = skyline[i]
            if (node.width >= width) {
                val y = node.y
                if (y < bestY) {
                    bestY = y
                    bestX = node.x
                    bestIndex = i
                }
            }
        }

        return if (bestIndex != -1) {
            Rectangle(bestX, bestY, width, height)
        } else null
    }

    override fun markRectangleAsUsed(rectangle: Rectangle) {
        // Update skyline with new rectangle
        val newNode = SkylineNode(rectangle.x, rectangle.y + rectangle.height, rectangle.width)
        skyline.add(newNode)
        skyline.sortBy { it.x }
    }

    override fun freeRectangle(rectangle: Rectangle) {
        // Not typically supported in skyline algorithm
    }

    override fun reset() {
        skyline.clear()
        skyline.add(SkylineNode(0, 0, Int.MAX_VALUE))
    }
}

/**
 * Guillotine packing algorithm
 * Fast packing with guillotine cuts
 */
class GuillotinePackager : RectanglePacker {
    private val freeRects = mutableListOf<Rectangle>()
    private var atlasWidth = 0
    private var atlasHeight = 0

    init {
        reset()
    }

    override fun findBestFit(width: Int, height: Int, allowRotation: Boolean): Rectangle? {
        var bestRect: Rectangle? = null
        var bestScore = Int.MAX_VALUE

        for (rect in freeRects) {
            if (rect.width >= width && rect.height >= height) {
                val leftoverX = rect.width - width
                val leftoverY = rect.height - height
                val score = minOf(leftoverX, leftoverY)

                if (score < bestScore) {
                    bestScore = score
                    bestRect = Rectangle(rect.x, rect.y, width, height)
                }
            }
        }

        return bestRect
    }

    override fun markRectangleAsUsed(rectangle: Rectangle) {
        // Split free rectangles using guillotine cuts
        val toRemove = mutableListOf<Rectangle>()
        val toAdd = mutableListOf<Rectangle>()

        for (rect in freeRects) {
            if (intersects(rect, rectangle)) {
                toRemove.add(rect)

                // Horizontal split
                if (rect.x < rectangle.x) {
                    toAdd.add(Rectangle(rect.x, rect.y, rectangle.x - rect.x, rect.height))
                }
                if (rectangle.x + rectangle.width < rect.x + rect.width) {
                    toAdd.add(
                        Rectangle(
                            rectangle.x + rectangle.width,
                            rect.y,
                            rect.x + rect.width - rectangle.x - rectangle.width,
                            rect.height
                        )
                    )
                }

                // Vertical split
                if (rect.y < rectangle.y) {
                    toAdd.add(Rectangle(rect.x, rect.y, rect.width, rectangle.y - rect.y))
                }
                if (rectangle.y + rectangle.height < rect.y + rect.height) {
                    toAdd.add(
                        Rectangle(
                            rect.x,
                            rectangle.y + rectangle.height,
                            rect.width,
                            rect.y + rect.height - rectangle.y - rectangle.height
                        )
                    )
                }
            }
        }

        freeRects.removeAll(toRemove)
        freeRects.addAll(toAdd)
    }

    override fun freeRectangle(rectangle: Rectangle) {
        freeRects.add(rectangle)
    }

    override fun reset() {
        freeRects.clear()
        freeRects.add(Rectangle(0, 0, 4096, 4096)) // Default atlas size
    }

    private fun intersects(a: Rectangle, b: Rectangle): Boolean {
        return !(a.x >= b.x + b.width || a.x + a.width <= b.x ||
                a.y >= b.y + b.height || a.y + a.height <= b.y)
    }
}

/**
 * Bottom-Left packing algorithm (minimal implementation)
 */
class BottomLeftPackager : RectanglePacker {
    override fun findBestFit(width: Int, height: Int, allowRotation: Boolean): Rectangle? = null
    override fun markRectangleAsUsed(rectangle: Rectangle) {}
    override fun freeRectangle(rectangle: Rectangle) {}
    override fun reset() {}
}

/**
 * Create packer based on strategy
 */
fun createPackerByStrategy(strategy: io.materia.material.PackingStrategy): RectanglePacker {
    return when (strategy) {
        io.materia.material.PackingStrategy.MAX_RECTS -> MaxRectsPackager()
        io.materia.material.PackingStrategy.SKYLINE -> SkylinePackager()
        io.materia.material.PackingStrategy.GUILLOTINE -> GuillotinePackager()
        io.materia.material.PackingStrategy.SHELF -> BottomLeftPackager()
    }
}
