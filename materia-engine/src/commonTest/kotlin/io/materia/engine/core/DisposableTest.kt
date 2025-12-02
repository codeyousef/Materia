package io.materia.engine.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Unit tests for the Disposable interface and DisposableContainer.
 */
class DisposableTest {

    /**
     * Test implementation of Disposable for testing.
     */
    private class TestDisposable(val id: String) : Disposable {
        private var _disposed = false
        override val isDisposed: Boolean get() = _disposed
        var disposeCallCount = 0
            private set

        override fun dispose() {
            if (!_disposed) {
                _disposed = true
                disposeCallCount++
            }
        }
    }

    @Test
    fun disposable_initiallyNotDisposed() {
        val disposable = TestDisposable("test")
        assertFalse(disposable.isDisposed)
    }

    @Test
    fun disposable_disposeMarksAsDisposed() {
        val disposable = TestDisposable("test")
        disposable.dispose()
        assertTrue(disposable.isDisposed)
    }

    @Test
    fun disposable_disposeIsIdempotent() {
        val disposable = TestDisposable("test")
        disposable.dispose()
        disposable.dispose()
        disposable.dispose()
        
        assertEquals(1, disposable.disposeCallCount)
        assertTrue(disposable.isDisposed)
    }

    @Test
    fun disposableContainer_initiallyEmpty() {
        val container = DisposableContainer()
        assertFalse(container.isDisposed)
        assertEquals(0, container.size)
    }

    @Test
    fun disposableContainer_addIncreasesSize() {
        val container = DisposableContainer()
        val resource1 = TestDisposable("r1")
        val resource2 = TestDisposable("r2")
        
        container += resource1
        assertEquals(1, container.size)
        
        container += resource2
        assertEquals(2, container.size)
    }

    @Test
    fun disposableContainer_addReturnsResource() {
        val container = DisposableContainer()
        val resource = TestDisposable("test")
        
        val returned = container.add(resource)
        
        assertEquals(resource, returned)
        assertEquals(resource.id, returned.id)
    }

    @Test
    fun disposableContainer_disposeDisposesAllResources() {
        val container = DisposableContainer()
        val resource1 = TestDisposable("r1")
        val resource2 = TestDisposable("r2")
        val resource3 = TestDisposable("r3")
        
        container += resource1
        container += resource2
        container += resource3
        
        container.dispose()
        
        assertTrue(container.isDisposed)
        assertTrue(resource1.isDisposed)
        assertTrue(resource2.isDisposed)
        assertTrue(resource3.isDisposed)
    }

    @Test
    fun disposableContainer_disposesInReverseOrder() {
        val container = DisposableContainer()
        val disposeOrder = mutableListOf<String>()
        
        class TrackingDisposable(private val id: String) : Disposable {
            private var _disposed = false
            override val isDisposed: Boolean get() = _disposed
            override fun dispose() {
                if (!_disposed) {
                    _disposed = true
                    disposeOrder.add(id)
                }
            }
        }
        
        container += TrackingDisposable("first")
        container += TrackingDisposable("second")
        container += TrackingDisposable("third")
        
        container.dispose()
        
        assertEquals(listOf("third", "second", "first"), disposeOrder)
    }

    @Test
    fun disposableContainer_disposeIsIdempotent() {
        val container = DisposableContainer()
        val resource = TestDisposable("test")
        container += resource
        
        container.dispose()
        container.dispose()
        container.dispose()
        
        assertEquals(1, resource.disposeCallCount)
    }

    @Test
    fun disposableContainer_cannotAddToDisposedContainer() {
        val container = DisposableContainer()
        container.dispose()
        
        assertFailsWith<IllegalStateException> {
            container += TestDisposable("new")
        }
    }

    @Test
    fun disposableContainer_continuesDisposingAfterException() {
        val container = DisposableContainer()
        val disposed = mutableListOf<String>()
        
        class FailingDisposable : Disposable {
            override val isDisposed: Boolean = false
            override fun dispose() {
                throw RuntimeException("Intentional failure")
            }
        }
        
        class TrackingDisposable(private val id: String) : Disposable {
            private var _disposed = false
            override val isDisposed: Boolean get() = _disposed
            override fun dispose() {
                _disposed = true
                disposed.add(id)
            }
        }
        
        container += TrackingDisposable("first")
        container += FailingDisposable()
        container += TrackingDisposable("third")
        
        // Should not throw, should continue disposing
        container.dispose()
        
        // Both tracking disposables should be disposed despite the failure
        assertTrue("third" in disposed)
        assertTrue("first" in disposed)
    }

    @Test
    fun useExtension_disposesAfterBlock() {
        val disposable = TestDisposable("test")
        
        disposable.use {
            assertFalse(it.isDisposed)
        }
        
        assertTrue(disposable.isDisposed)
    }

    @Test
    fun useExtension_returnsBlockResult() {
        val disposable = TestDisposable("test")
        
        val result = disposable.use {
            42
        }
        
        assertEquals(42, result)
        assertTrue(disposable.isDisposed)
    }

    @Test
    fun useExtension_disposesEvenOnException() {
        val disposable = TestDisposable("test")
        
        assertFailsWith<RuntimeException> {
            disposable.use {
                throw RuntimeException("Test exception")
            }
        }
        
        assertTrue(disposable.isDisposed)
    }

    @Test
    fun checkNotDisposed_throwsWhenDisposed() {
        val disposable = TestDisposable("test")
        disposable.dispose()
        
        assertFailsWith<IllegalStateException> {
            disposable.checkNotDisposed("TestResource")
        }
    }

    @Test
    fun checkNotDisposed_succeedsWhenNotDisposed() {
        val disposable = TestDisposable("test")
        
        // Should not throw
        disposable.checkNotDisposed("TestResource")
    }

    @Test
    fun checkNotDisposed_includesResourceNameInMessage() {
        val disposable = TestDisposable("test")
        disposable.dispose()
        
        val exception = assertFailsWith<IllegalStateException> {
            disposable.checkNotDisposed("MyCustomResource")
        }
        
        assertTrue(exception.message?.contains("MyCustomResource") == true)
    }
}
