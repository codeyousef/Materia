/**
 * Common test helpers - expect/actual declarations
 */
package io.kreekt.renderer

import io.kreekt.core.scene.Mesh

// Platform detection
expect enum class Platform {
    JS, JVM, NATIVE
}

expect fun getPlatform(): Platform

// Test surface creation
expect fun createTestSurface(width: Int, height: Int): RenderSurface

// Log capture
expect suspend fun captureLog(block: suspend () -> Unit): String

// Test geometry
expect fun createTestCube(): Mesh

// Utility functions
expect fun currentTimeMillis(): Long
expect fun formatDouble(value: Double, decimals: Int): String

// Error testing (platform-specific implementation needed)
expect fun createInvalidSurface(): RenderSurface
