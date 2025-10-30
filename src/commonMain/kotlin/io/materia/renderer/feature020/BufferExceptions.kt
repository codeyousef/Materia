package io.materia.renderer.feature020

/**
 * Exception thrown when buffer handle is invalid (destroyed or corrupted).
 * Feature 020 - Production-Ready Renderer
 */
class InvalidBufferException(message: String) : Exception(message)

/**
 * Exception thrown when GPU memory allocation fails.
 * Feature 020 - Production-Ready Renderer
 */
class OutOfMemoryException(message: String) : Exception(message)

/**
 * Exception thrown when renderer is not initialized.
 * Feature 020 - Production-Ready Renderer
 */
class RendererInitializationException(message: String) : Exception(message)
