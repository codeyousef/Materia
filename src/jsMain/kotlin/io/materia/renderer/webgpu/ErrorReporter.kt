package io.materia.renderer.webgpu

/**
 * Error handling and logging for WebGPU renderer.
 * T040: Clear, actionable error messages for debugging.
 *
 * Provides structured error reporting with:
 * - Detailed error context
 * - Suggested solutions
 * - Error categorization
 */
object ErrorReporter {
    private val errorLog = mutableListOf<ErrorRecord>()
    private var errorCount = 0
    private var warningCount = 0

    /**
     * Reports a shader compilation error.
     * @param shaderType Vertex or fragment shader
     * @param errors Compilation error messages
     * @param source Shader source code
     */
    fun reportShaderError(shaderType: String, errors: String, source: String) {
        errorCount++

        val message = buildString {
            appendLine("Shader Compilation Error ($shaderType):")
            appendLine(errors)
            appendLine()
            appendLine("Suggestions:")
            appendLine("- Check WGSL syntax and built-in functions")
            appendLine("- Verify attribute locations match vertex buffer layout")
            appendLine("- Ensure uniform buffer bindings are correct")
        }

        val record = ErrorRecord(
            category = ErrorCategory.SHADER,
            severity = ErrorSeverity.ERROR,
            message = message,
            context = mapOf(
                "shaderType" to shaderType,
                "errors" to errors,
                "sourceLength" to source.length.toString()
            )
        )

        errorLog.add(record)
        console.error(message)
    }

    /**
     * Reports a buffer allocation or upload error.
     * @param operation Operation that failed (create, upload, etc.)
     * @param bufferSize Size of buffer in bytes
     * @param error Exception message
     */
    fun reportBufferError(operation: String, bufferSize: Int, error: String) {
        errorCount++

        val message = buildString {
            appendLine("Buffer Error ($operation):")
            appendLine("Failed to $operation buffer of size $bufferSize bytes")
            appendLine("Error: $error")
            appendLine()
            appendLine("Suggestions:")
            appendLine("- Check available GPU memory")
            appendLine("- Verify buffer size doesn't exceed maxBufferSize limit")
            appendLine("- Ensure buffer usage flags are compatible")
        }

        val record = ErrorRecord(
            category = ErrorCategory.BUFFER,
            severity = ErrorSeverity.ERROR,
            message = message,
            context = mapOf(
                "operation" to operation,
                "bufferSize" to bufferSize.toString(),
                "error" to error
            )
        )

        errorLog.add(record)
        console.error(message)
    }

    /**
     * Reports a texture creation or upload error.
     * @param operation Operation that failed
     * @param width Texture width
     * @param height Texture height
     * @param format Texture format
     * @param error Exception message
     */
    fun reportTextureError(
        operation: String,
        width: Int,
        height: Int,
        format: String,
        error: String
    ) {
        errorCount++

        val message = buildString {
            appendLine("Texture Error ($operation):")
            appendLine("Failed to $operation texture ${width}x${height} ($format)")
            appendLine("Error: $error")
            appendLine()
            appendLine("Suggestions:")
            appendLine("- Check texture dimensions don't exceed maxTextureDimension2D")
            appendLine("- Verify format is supported by the device")
            appendLine("- Ensure sufficient GPU memory is available")
        }

        val record = ErrorRecord(
            category = ErrorCategory.TEXTURE,
            severity = ErrorSeverity.ERROR,
            message = message,
            context = mapOf(
                "operation" to operation,
                "width" to width.toString(),
                "height" to height.toString(),
                "format" to format,
                "error" to error
            )
        )

        errorLog.add(record)
        console.error(message)
    }

    /**
     * Reports a pipeline creation error.
     * @param error Exception message
     * @param descriptor Pipeline descriptor details
     */
    fun reportPipelineError(error: String, descriptor: String) {
        errorCount++

        val message = buildString {
            appendLine("Pipeline Creation Error:")
            appendLine("Error: $error")
            appendLine()
            appendLine("Pipeline descriptor:")
            appendLine(descriptor)
            appendLine()
            appendLine("Suggestions:")
            appendLine("- Verify shader modules compiled successfully")
            appendLine("- Check vertex buffer layout matches shader attributes")
            appendLine("- Ensure depth/stencil format is compatible")
        }

        val record = ErrorRecord(
            category = ErrorCategory.PIPELINE,
            severity = ErrorSeverity.ERROR,
            message = message,
            context = mapOf(
                "error" to error,
                "descriptor" to descriptor
            )
        )

        errorLog.add(record)
        console.error(message)
    }

    /**
     * Reports a rendering error.
     * @param stage Rendering stage (clear, draw, submit, etc.)
     * @param error Exception message
     */
    fun reportRenderingError(stage: String, error: String) {
        errorCount++

        val message = buildString {
            appendLine("Rendering Error ($stage):")
            appendLine("Error: $error")
            appendLine()
            appendLine("Suggestions:")
            appendLine("- Check that renderer is initialized")
            appendLine("- Verify all resources are created successfully")
            appendLine("- Ensure scene and camera are valid")
        }

        val record = ErrorRecord(
            category = ErrorCategory.RENDERING,
            severity = ErrorSeverity.ERROR,
            message = message,
            context = mapOf(
                "stage" to stage,
                "error" to error
            )
        )

        errorLog.add(record)
        console.error(message)
    }

    /**
     * Reports a context loss event.
     * @param reason Reason for context loss
     * @param canRecover Whether recovery is possible
     */
    fun reportContextLoss(reason: String, canRecover: Boolean) {
        warningCount++

        val message = buildString {
            appendLine("GPU Context Lost:")
            appendLine("Reason: $reason")
            appendLine("Can recover: $canRecover")
            appendLine()
            if (canRecover) {
                appendLine("Attempting automatic recovery...")
            } else {
                appendLine("Manual page reload required")
            }
        }

        val record = ErrorRecord(
            category = ErrorCategory.CONTEXT,
            severity = if (canRecover) ErrorSeverity.WARNING else ErrorSeverity.ERROR,
            message = message,
            context = mapOf(
                "reason" to reason,
                "canRecover" to canRecover.toString()
            )
        )

        errorLog.add(record)
        console.warn(message)
    }

    /**
     * Reports a warning (non-fatal issue).
     * @param category Error category
     * @param message Warning message
     */
    fun reportWarning(category: ErrorCategory, message: String) {
        warningCount++

        val record = ErrorRecord(
            category = category,
            severity = ErrorSeverity.WARNING,
            message = message,
            context = emptyMap()
        )

        errorLog.add(record)
        console.warn("[${category.name}] $message")
    }

    /**
     * Gets error statistics.
     */
    fun getStats(): ErrorStats {
        return ErrorStats(
            errorCount = errorCount,
            warningCount = warningCount,
            totalIssues = errorCount + warningCount,
            errorsByCategory = errorLog.groupBy { it.category }
                .mapValues { it.value.size }
        )
    }

    /**
     * Gets recent error records.
     * @param limit Maximum number of records to return
     */
    fun getRecentErrors(limit: Int = 10): List<ErrorRecord> {
        return errorLog.takeLast(limit)
    }

    /**
     * Clears error log.
     */
    fun clear() {
        errorLog.clear()
        errorCount = 0
        warningCount = 0
    }
}

/**
 * Error category for classification.
 */
enum class ErrorCategory {
    SHADER,
    BUFFER,
    TEXTURE,
    PIPELINE,
    RENDERING,
    CONTEXT,
    OTHER
}

/**
 * Error severity level.
 */
enum class ErrorSeverity {
    ERROR,
    WARNING,
    INFO
}

/**
 * Error record for logging.
 */
data class ErrorRecord(
    val category: ErrorCategory,
    val severity: ErrorSeverity,
    val message: String,
    val context: Map<String, String>,
    val timestamp: Double = js("Date.now()").unsafeCast<Double>()
)

/**
 * Error statistics.
 */
data class ErrorStats(
    val errorCount: Int,
    val warningCount: Int,
    val totalIssues: Int,
    val errorsByCategory: Map<ErrorCategory, Int>
)
