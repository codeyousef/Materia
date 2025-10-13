package io.kreekt.renderer.feature020

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Contract tests for [SwapchainManager] using the simulated implementation.
 */
class SwapchainManagerTest {

    private lateinit var swapchainManager: SwapchainManager

    @BeforeTest
    fun setup() {
        swapchainManager = SimulatedSwapchainManager(initialWidth = 800, initialHeight = 600)
    }

    @Test
    fun testAcquirePresent_normalFlow_succeeds() {
        val image = swapchainManager.acquireNextImage()

        assertNotNull(image)
        assertTrue(image.isReady())

        swapchainManager.presentImage(image)
    }

    @Test
    fun testRecreateSwapchain_validDimensions_succeeds() {
        val originalExtent = swapchainManager.getExtent()
        assertEquals(800, originalExtent.first)
        assertEquals(600, originalExtent.second)

        swapchainManager.recreateSwapchain(1024, 768)

        val newExtent = swapchainManager.getExtent()
        assertEquals(1024, newExtent.first)
        assertEquals(768, newExtent.second)
    }

    @Test
    fun testRecreateSwapchain_invalidDimensions_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            swapchainManager.recreateSwapchain(0, 600)
        }

        assertFailsWith<IllegalArgumentException> {
            swapchainManager.recreateSwapchain(800, -1)
        }
    }

    @Test
    fun testGetExtent_returnsCurrentDimensions() {
        val extent = swapchainManager.getExtent()
        assertTrue(extent.first > 0)
        assertTrue(extent.second > 0)
    }

    @Test
    fun testPresentImage_outOfOrder_throwsException() {
        val firstImage = swapchainManager.acquireNextImage()
        val secondImage = swapchainManager.acquireNextImage()

        assertFailsWith<SwapchainException> {
            swapchainManager.presentImage(firstImage)
        }

        swapchainManager.presentImage(secondImage)
    }
}
