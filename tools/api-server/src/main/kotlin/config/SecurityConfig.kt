package io.materia.tools.api.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Security configuration for Materia Tools API Server
 * Handles authentication, authorization, CORS, and security headers
 */

private val logger = LoggerFactory.getLogger("SecurityConfig")

fun Application.configureSecurity() {
    configureAuthentication()
    configureCORS()
    configureStatusPages()
    configureSecurityHeaders()
}

/**
 * JWT-based authentication for API access
 */
fun Application.configureAuthentication() {
    val jwtConfig = JWTConfig.fromEnvironment()

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "Materia Tools API"

            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtConfig.secret))
                    .withAudience(jwtConfig.audience)
                    .withIssuer(jwtConfig.issuer)
                    .build()
            )

            validate { credential ->
                if (credential.payload.getClaim("username").asString() != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            challenge { defaultScheme, realm ->
                call.respond(HttpStatusCode.Unauthorized, SecurityError(
                    error = "invalid_token",
                    message = "JWT token is invalid or missing",
                    code = "AUTH_001"
                ))
            }
        }

        // Development mode - no authentication required
        if (isDevelopmentMode()) {
            bearer("dev-auth") {
                authenticate { tokenCredential ->
                    if (tokenCredential.token == "dev-token") {
                        UserIdPrincipal("dev-user")
                    } else {
                        null
                    }
                }
            }
        }
    }

    logger.info("Authentication configured with JWT realm: ${jwtConfig.audience}")
}

/**
 * CORS configuration for cross-origin requests
 */
fun Application.configureCORS() {
    install(CORS) {
        // HTTP methods
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Head)

        // Headers
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.AccessControlAllowHeaders)
        allowHeader(HttpHeaders.AccessControlAllowOrigin)
        allowHeader("X-Requested-With")
        allowHeader("X-Tool-Version")
        allowHeader("X-Client-ID")
        allowHeader("X-Session-ID")

        // Development origins
        if (isDevelopmentMode()) {
            allowHost("localhost:3000")
            allowHost("localhost:8080")
            allowHost("127.0.0.1:3000")
            allowHost("127.0.0.1:8080")
            allowHost("0.0.0.0:3000")
            allowHost("0.0.0.0:8080")
        }

        // Production origins
        allowHost("tools.materia.dev", schemes = listOf("https"))
        allowHost("materia.dev", schemes = listOf("https"))
        allowHost("app.materia.dev", schemes = listOf("https"))

        // Subdomain support
        allowHostRegex(Regex(".*\\.materia\\.dev"))

        // Credentials and timing
        allowCredentials = true
        allowNonSimpleContentTypes = true
        maxAgeInSeconds = 24 * 60 * 60 // 24 hours

        // Custom validation
        allowHeadersPrefixed("X-Custom-")
    }

    logger.info("CORS configured for development: ${isDevelopmentMode()}")
}

/**
 * Security headers for enhanced protection
 */
fun Application.configureSecurityHeaders() {
    intercept(ApplicationCallPipeline.Plugins) {
        call.response.headers.apply {
            // Security headers
            append("X-Content-Type-Options", "nosniff")
            append("X-Frame-Options", "DENY")
            append("X-XSS-Protection", "1; mode=block")
            append("Referrer-Policy", "strict-origin-when-cross-origin")

            // Content Security Policy
            if (!isDevelopmentMode()) {
                append("Content-Security-Policy", buildCSP())
            }

            // HSTS for production HTTPS
            if (isProductionMode() && call.request.origin.scheme == "https") {
                append("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
            }

            // Custom headers
            append("X-Powered-By", "Materia-Tools-API")
            append("X-API-Version", "1.0.0")
        }
    }
}

/**
 * Error handling and status pages
 */
fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<SecurityException> { call, cause ->
            logger.warn("Security exception: ${cause.message}")
            call.respond(
                HttpStatusCode.Forbidden,
                SecurityError(
                    error = "access_denied",
                    message = cause.message ?: "Access denied",
                    code = "SEC_001"
                )
            )
        }

        exception<AuthenticationException> { call, cause ->
            logger.warn("Authentication exception: ${cause.message}")
            call.respond(
                HttpStatusCode.Unauthorized,
                SecurityError(
                    error = "authentication_failed",
                    message = cause.message ?: "Authentication failed",
                    code = "AUTH_002"
                )
            )
        }

        exception<IllegalArgumentException> { call, cause ->
            logger.warn("Validation exception: ${cause.message}")
            call.respond(
                HttpStatusCode.BadRequest,
                SecurityError(
                    error = "invalid_request",
                    message = cause.message ?: "Invalid request parameters",
                    code = "VAL_001"
                )
            )
        }

        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                status,
                SecurityError(
                    error = "not_found",
                    message = "The requested resource was not found",
                    code = "NOT_FOUND"
                )
            )
        }

        status(HttpStatusCode.MethodNotAllowed) { call, status ->
            call.respond(
                status,
                SecurityError(
                    error = "method_not_allowed",
                    message = "HTTP method not allowed for this endpoint",
                    code = "METHOD_NOT_ALLOWED"
                )
            )
        }
    }
}

/**
 * JWT Configuration
 */
data class JWTConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val expirationHours: Long = 24
) {
    companion object {
        fun fromEnvironment(): JWTConfig {
            return JWTConfig(
                secret = System.getenv("JWT_SECRET") ?: generateDefaultSecret(),
                issuer = System.getenv("JWT_ISSUER") ?: "materia-tools",
                audience = System.getenv("JWT_AUDIENCE") ?: "materia-tools-api",
                expirationHours = System.getenv("JWT_EXPIRATION_HOURS")?.toLongOrNull() ?: 24
            )
        }

        private fun generateDefaultSecret(): String {
            logger.warn("Using generated JWT secret - set JWT_SECRET environment variable in production")
            return Base64.getEncoder().encodeToString(UUID.randomUUID().toString().toByteArray())
        }
    }
}

/**
 * Security error response format
 */
@Serializable
data class SecurityError(
    val error: String,
    val message: String,
    val code: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Build Content Security Policy
 */
private fun buildCSP(): String {
    return listOf(
        "default-src 'self'",
        "script-src 'self' 'unsafe-inline' 'unsafe-eval'", // WebGL/WebGPU requires unsafe-eval
        "style-src 'self' 'unsafe-inline'",
        "img-src 'self' data: blob:",
        "font-src 'self' data:",
        "connect-src 'self' ws: wss:",
        "worker-src 'self' blob:",
        "child-src 'self' blob:",
        "frame-ancestors 'none'",
        "base-uri 'self'",
        "form-action 'self'"
    ).joinToString("; ")
}

/**
 * Environment detection
 */
private fun Application.isDevelopmentMode(): Boolean {
    return environment.config.propertyOrNull("ktor.environment")?.getString() == "dev" ||
           System.getenv("ENVIRONMENT") == "development"
}

private fun Application.isProductionMode(): Boolean {
    return environment.config.propertyOrNull("ktor.environment")?.getString() == "prod" ||
           System.getenv("ENVIRONMENT") == "production"
}

/**
 * Rate limiting configuration
 */
class RateLimitConfig {
    companion object {
        const val DEFAULT_REQUESTS_PER_MINUTE = 100
        const val BURST_REQUESTS = 20
        const val WINDOW_SIZE_MINUTES = 1
    }
}

/**
 * Security audit logging
 */
object SecurityAudit {
    private val auditLogger = LoggerFactory.getLogger("SecurityAudit")

    fun logAuthenticationAttempt(username: String?, success: Boolean, ip: String) {
        if (success) {
            auditLogger.info("Authentication successful for user: $username from IP: $ip")
        } else {
            auditLogger.warn("Authentication failed for user: $username from IP: $ip")
        }
    }

    fun logAuthorization(username: String?, resource: String, granted: Boolean, ip: String) {
        if (granted) {
            auditLogger.info("Access granted to $username for resource: $resource from IP: $ip")
        } else {
            auditLogger.warn("Access denied to $username for resource: $resource from IP: $ip")
        }
    }

    fun logSecurityEvent(event: String, details: String, ip: String) {
        auditLogger.warn("Security event: $event - $details from IP: $ip")
    }
}