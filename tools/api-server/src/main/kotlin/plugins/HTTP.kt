package io.materia.tools.api.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureHTTP() {
    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024) // bytes
        }
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)

        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Requested-With")
        allowHeader("X-Tool-Version")

        // Development CORS
        if (isDevelopment()) {
            allowHost("localhost:3000")
            allowHost("localhost:8080")
            allowHost("127.0.0.1:3000")
            allowHost("127.0.0.1:8080")
        }

        // Production CORS
        allowHost("tools.materia.dev", schemes = listOf("https"))
        allowHost("materia.dev", schemes = listOf("https"))

        allowCredentials = true
        maxAgeInSeconds = 24 * 60 * 60 // 24 hours
    }

    install(CachingHeaders) {
        options { call, outgoingContent ->
            when (outgoingContent.contentType?.withoutParameters()) {
                ContentType.Text.CSS -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60))
                ContentType.Application.JavaScript -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60))
                ContentType.Image.Any -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60))
                else -> null
            }
        }
    }
}

private fun Application.isDevelopment(): Boolean {
    return environment.config.propertyOrNull("ktor.environment")?.getString() == "dev"
}