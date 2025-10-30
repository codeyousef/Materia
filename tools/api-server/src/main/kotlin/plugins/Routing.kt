package io.materia.tools.api.plugins

import io.materia.tools.api.routes.*
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        // API versioning
        route("/api/v1") {
            // Tool APIs
            sceneEditorRoutes()
            materialEditorRoutes()
            animationEditorRoutes()
            performanceProfilerRoutes()
            testingFrameworkRoutes()
            documentationRoutes()
            cicdRoutes()

            // Project management
            projectRoutes()

            // File operations
            fileRoutes()

            // Asset management
            assetRoutes()
        }

        // Static file serving for tools
        staticToolRoutes()

        // WebSocket endpoints
        webSocketRoutes()
    }
}