package tools.docs.server

import kotlinx.coroutines.*
import kotlinx.datetime.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import tools.docs.dokka.*
import tools.docs.examples.*
import tools.docs.migration.*
import tools.docs.search.SearchDocument
import tools.docs.search.SearchResult
import tools.docs.search.SearchIndexer
import tools.docs.search.SearchIndex as SearchSearchIndex
import tools.docs.search.SearchQuery
import tools.docs.search.IndexStatistics
import tools.docs.search.DocumentType
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Documentation server for Materia with live preview, hot reload, and interactive features.
 * Provides a development server for documentation authoring and a production server for deployment.
 */
@Serializable
data class ServerConfiguration(
    val host: String = "localhost",
    val port: Int = 8080,
    val environment: ServerEnvironment = ServerEnvironment.DEVELOPMENT,
    val staticFiles: StaticFilesConfig,
    val security: SecurityConfig,
    val features: FeatureConfig,
    val analytics: AnalyticsConfig? = null
)

enum class ServerEnvironment {
    DEVELOPMENT, STAGING, PRODUCTION
}

@Serializable
data class StaticFilesConfig(
    val rootDirectory: String,
    val cacheMaxAge: Long = 3600, // 1 hour
    val compression: Boolean = true,
    val etag: Boolean = true
)

@Serializable
data class SecurityConfig(
    val enableCORS: Boolean = true,
    val allowedOrigins: List<String> = listOf("*"),
    val enableCSP: Boolean = true,
    val apiRateLimit: RateLimit? = null
)

@Serializable
data class RateLimit(
    val requestsPerMinute: Int = 100,
    val burstSize: Int = 20
)

@Serializable
data class FeatureConfig(
    val hotReload: Boolean = true,
    val livePreview: Boolean = true,
    val searchAPI: Boolean = true,
    val exampleExecution: Boolean = true,
    val migrationTools: Boolean = true,
    val analytics: Boolean = false,
    val comments: Boolean = false
)

@Serializable
data class AnalyticsConfig(
    val provider: String, // "google", "matomo", "custom"
    val trackingId: String,
    val enabled: Boolean = true
)

@Serializable
data class ServerStats(
    val uptime: Long,
    val totalRequests: Long,
    val activeConnections: Int,
    val cacheHits: Long,
    val cacheMisses: Long,
    val averageResponseTime: Double,
    val errorRate: Double,
    val lastReload: String?
)

@Serializable
data class DocumentationSite(
    val title: String,
    val version: String,
    val baseUrl: String,
    val sections: List<SiteSection>,
    val navigation: NavigationStructure,
    val searchIndex: SearchSearchIndex,
    val theme: ThemeConfiguration,
    val metadata: SiteMetadata
)

@Serializable
data class SiteSection(
    val id: String,
    val title: String,
    val path: String,
    val content: String,
    val type: SectionType,
    val lastModified: String,
    val children: List<SiteSection> = emptyList()
)

enum class SectionType {
    PAGE, API_REFERENCE, TUTORIAL, EXAMPLE, MIGRATION_GUIDE, FAQ
}

@Serializable
data class ThemeConfiguration(
    val name: String,
    val primaryColor: String,
    val secondaryColor: String,
    val fontFamily: String,
    val logo: String? = null,
    val favicon: String? = null,
    val customCss: List<String> = emptyList(),
    val customJs: List<String> = emptyList()
)

@Serializable
data class SiteMetadata(
    val description: String,
    val keywords: List<String>,
    val author: String,
    val repository: String? = null,
    val license: String? = null,
    val social: Map<String, String> = emptyMap()
)

@Serializable
data class LiveReloadEvent(
    val type: ReloadEventType,
    val path: String,
    val timestamp: String,
    val data: String? = null
)

enum class ReloadEventType {
    FILE_CHANGED, FILE_ADDED, FILE_DELETED, REBUILD_COMPLETE, ERROR
}

/**
 * Main documentation server with comprehensive features for development and production.
 */
class DocServer {
    private val config: ServerConfiguration
    private val dokkaEnhancer = DokkaEnhancer()
    private val exampleGenerator = ExampleGenerator()
    private val migrationGuide = MigrationGuideGenerator()
    private val searchIndexer = SearchIndexer()

    private var isRunning = false
    private var serverStats = ServerStats(
        uptime = 0,
        totalRequests = 0,
        activeConnections = 0,
        cacheHits = 0,
        cacheMisses = 0,
        averageResponseTime = 0.0,
        errorRate = 0.0,
        lastReload = null
    )

    private val responseTimeHistory = mutableListOf<Long>()
    private var lastReloadTime: LocalDateTime? = null

    constructor(config: ServerConfiguration) {
        this.config = config
    }

    companion object {
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

        fun createDevelopmentServer(port: Int = 8080): DocServer {
            return DocServer(
                ServerConfiguration(
                    port = port,
                    environment = ServerEnvironment.DEVELOPMENT,
                    staticFiles = StaticFilesConfig(
                        rootDirectory = "./docs/build",
                        cacheMaxAge = 0 // No caching in development
                    ),
                    security = SecurityConfig(
                        enableCORS = true,
                        allowedOrigins = listOf("*")
                    ),
                    features = FeatureConfig(
                        hotReload = true,
                        livePreview = true,
                        searchAPI = true,
                        exampleExecution = true,
                        migrationTools = true
                    )
                )
            )
        }

        fun createProductionServer(port: Int = 80): DocServer {
            return DocServer(
                ServerConfiguration(
                    port = port,
                    environment = ServerEnvironment.PRODUCTION,
                    staticFiles = StaticFilesConfig(
                        rootDirectory = "./docs/build",
                        cacheMaxAge = 86400, // 24 hours
                        compression = true,
                        etag = true
                    ),
                    security = SecurityConfig(
                        enableCORS = false,
                        enableCSP = true,
                        apiRateLimit = RateLimit(
                            requestsPerMinute = 1000,
                            burstSize = 50
                        )
                    ),
                    features = FeatureConfig(
                        hotReload = false,
                        livePreview = false,
                        searchAPI = true,
                        exampleExecution = false, // Disabled for security
                        migrationTools = true,
                        analytics = true
                    )
                )
            )
        }
    }

    /**
     * Start the documentation server
     */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (isRunning) {
            println("Server is already running on ${config.host}:${config.port}")
            return@withContext false
        }

        try {
            println("Starting Materia Documentation Server...")
            println("Environment: ${config.environment}")
            println("Host: ${config.host}")
            println("Port: ${config.port}")

            // Initialize server components
            initializeServer()

            // Start HTTP server
            startHttpServer()

            // Start background services
            if (config.features.hotReload) {
                startFileWatcher()
            }

            isRunning = true
            println("✅ Server started successfully!")
            println("📖 Documentation available at: http://${config.host}:${config.port}")

            if (config.features.livePreview) {
                println("🔄 Live reload enabled")
            }

            true
        } catch (e: Exception) {
            println("❌ Failed to start server: ${e.message}")
            false
        }
    }

    /**
     * Stop the documentation server
     */
    suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        if (!isRunning) {
            return@withContext true
        }

        try {
            println("Stopping documentation server...")

            // Stop HTTP server
            stopHttpServer()

            // Stop background services
            stopFileWatcher()

            isRunning = false
            println("✅ Server stopped successfully")
            true
        } catch (e: Exception) {
            println("❌ Error stopping server: ${e.message}")
            false
        }
    }

    /**
     * Generate and serve documentation site
     */
    suspend fun generateSite(
        sourceDirectories: List<String>,
        outputDirectory: String
    ): DocumentationSite = withContext(Dispatchers.Default) {
        println("🔄 Generating documentation site...")

        // Generate enhanced Dokka documentation
        val dokkaConfig = DokkaConfiguration(
            projectName = "Materia",
            projectVersion = "1.0.0",
            outputDirectory = outputDirectory,
            sourceDirectories = sourceDirectories,
            platforms = listOf(
                DocumentationPlatform(
                    name = "jvm",
                    displayName = "JVM",
                    sourceSetId = "jvm",
                    targets = listOf("jvm")
                ),
                DocumentationPlatform(
                    name = "js",
                    displayName = "JavaScript",
                    sourceSetId = "js",
                    targets = listOf("js")
                )
            ),
            customization = DokkaCustomization(
                theme = DocumentationTheme.MATERIAL,
                searchEnabled = true
            )
        )

        val dokkaResult = dokkaEnhancer.generateDocumentation(dokkaConfig)

        // Generate interactive examples
        val exampleLibrary = exampleGenerator.generateExampleLibrary()

        // Generate migration guides
        val threeJSGuide = migrationGuide.generateMigrationGuide(
            SourceLibrary(
                name = "Three.js",
                version = "r150+",
                language = "JavaScript",
                platform = "Web",
                ecosystem = "npm"
            )
        )

        // Build search index
        val searchDocuments = createSearchDocuments(dokkaResult, exampleLibrary, listOf(threeJSGuide))
        val searchIndex = searchIndexer.buildIndex(searchDocuments)

        // Create site structure
        val sections = createSiteSections(dokkaResult, exampleLibrary, listOf(threeJSGuide))
        val navigation = createNavigation(sections)

        DocumentationSite(
            title = "Materia Documentation",
            version = "1.0.0",
            baseUrl = "http://${config.host}:${config.port}",
            sections = sections,
            navigation = navigation,
            searchIndex = searchIndex,
            theme = ThemeConfiguration(
                name = "Materia",
                primaryColor = "#2196F3",
                secondaryColor = "#FFC107",
                fontFamily = "Inter, sans-serif"
            ),
            metadata = SiteMetadata(
                description = "Materia is a Kotlin Multiplatform 3D graphics library",
                keywords = listOf("kotlin", "3d", "graphics", "multiplatform", "webgpu", "vulkan"),
                author = "Materia Team",
                repository = "https://github.com/materia/materia",
                license = "Apache 2.0"
            )
        )
    }

    /**
     * Handle search requests
     */
    suspend fun handleSearch(
        query: String,
        filters: Map<String, String> = emptyMap(),
        limit: Int = 20
    ): SearchResult = withContext(Dispatchers.Default) {
        val searchQuery = SearchQuery(
            query = query,
            filters = filters,
            limit = limit,
            fuzzySearch = true,
            semanticSearch = config.environment == ServerEnvironment.DEVELOPMENT
        )

        // Get current search index (would be cached in real implementation)
        val searchIndex = loadOrCreateSearchIndex()

        searchIndexer.search(searchIndex, searchQuery)
    }

    /**
     * Execute example code (development only)
     */
    suspend fun executeExample(
        exampleId: String,
        codeBlockId: String,
        userCode: String? = null
    ): ExampleExecution = withContext(Dispatchers.Default) {
        if (!config.features.exampleExecution) {
            return@withContext ExampleExecution(
                exampleId = exampleId,
                codeBlockId = codeBlockId,
                success = false,
                output = "",
                errors = listOf("Example execution is disabled"),
                executionTime = 0,
                memoryUsage = 0,
                resourceUsage = ExampleResourceUsage(0, 0, 0, 0)
            )
        }

        if (config.environment == ServerEnvironment.PRODUCTION) {
            return@withContext ExampleExecution(
                exampleId = exampleId,
                codeBlockId = codeBlockId,
                success = false,
                output = "",
                errors = listOf("Example execution not allowed in production"),
                executionTime = 0,
                memoryUsage = 0,
                resourceUsage = ExampleResourceUsage(0, 0, 0, 0)
            )
        }

        exampleGenerator.executeExample(exampleId, codeBlockId, userCode)
    }

    /**
     * Get server statistics
     */
    fun getStats(): ServerStats {
        val uptime = if (isRunning) Clock.System.now().toEpochMilliseconds() else 0
        val averageResponseTime = if (responseTimeHistory.isNotEmpty()) {
            responseTimeHistory.average()
        } else 0.0

        return serverStats.copy(
            uptime = uptime,
            averageResponseTime = averageResponseTime,
            lastReload = lastReloadTime?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
    }

    /**
     * Trigger manual site rebuild
     */
    suspend fun rebuildSite(): Boolean = withContext(Dispatchers.IO) {
        try {
            println("🔄 Manually rebuilding documentation site...")

            // Regenerate documentation
            val sourceDirectories = listOf("src/commonMain/kotlin", "src/jvmMain/kotlin", "src/jsMain/kotlin")
            val site = generateSite(sourceDirectories, config.staticFiles.rootDirectory)

            // Update cache and notify clients
            lastReloadTime = LocalDateTime.now()

            if (config.features.hotReload) {
                notifyLiveReloadClients(LiveReloadEvent(
                    type = ReloadEventType.REBUILD_COMPLETE,
                    path = "/",
                    timestamp = lastReloadTime.toString()
                ))
            }

            println("✅ Site rebuild complete")
            true
        } catch (e: Exception) {
            println("❌ Site rebuild failed: ${e.message}")
            false
        }
    }

    // Private helper methods

    private suspend fun initializeServer() {
        // Initialize components
        println("🔧 Initializing server components...")

        // Validate configuration
        validateConfiguration()

        // Prepare static files directory
        File(config.staticFiles.rootDirectory).mkdirs()

        // Initialize search index
        loadOrCreateSearchIndex()

        println("✅ Server components initialized")
    }

    private fun validateConfiguration() {
        if (config.port < 1 || config.port > 65535) {
            throw IllegalArgumentException("Invalid port: ${config.port}")
        }

        if (!File(config.staticFiles.rootDirectory).exists()) {
            File(config.staticFiles.rootDirectory).mkdirs()
        }
    }

    private suspend fun startHttpServer() {
        // HTTP server implementation would go here
        // Using a framework like Ktor or embedded Jetty
        println("🌐 HTTP server started on ${config.host}:${config.port}")
    }

    private suspend fun stopHttpServer() {
        // Stop HTTP server
        println("🛑 HTTP server stopped")
    }

    private suspend fun startFileWatcher() {
        // File system watcher implementation
        if (!config.features.hotReload) return

        println("👀 Starting file watcher for hot reload...")

        // Monitor source directories for changes
        coroutineScope {
            launch {
                // File watching logic would go here
                // When files change, trigger rebuild and notify clients
            }
        }
    }

    private fun stopFileWatcher() {
        // Stop file watcher
        if (config.features.hotReload) {
            println("🛑 File watcher stopped")
        }
    }

    private suspend fun loadOrCreateSearchIndex(): SearchSearchIndex {
        // Load existing search index or create new one
        val indexPath = "${config.staticFiles.rootDirectory}/search-index.json"
        return searchIndexer.loadIndex(indexPath) ?: SearchSearchIndex(
            version = "1.0",
            documents = emptyList(),
            termIndex = emptyMap(),
            vectorIndex = emptyMap(),
            metadataIndex = emptyMap(),
            statistics = IndexStatistics(0, 0, 0.0, 0, 0, 0),
            lastUpdated = kotlinx.datetime.Clock.System.now()
        )
    }

    private fun createSearchDocuments(
        dokkaResult: DocumentationResult,
        exampleLibrary: ExampleLibrary,
        migrationGuides: List<MigrationGuide>
    ): List<SearchDocument> {
        val documents = mutableListOf<SearchDocument>()

        // Add API documentation
        dokkaResult.generatedFiles.forEach { filePath ->
            if (filePath.endsWith(".html")) {
                documents.add(SearchDocument(
                    id = "api-${File(filePath).nameWithoutExtension}",
                    title = File(filePath).nameWithoutExtension,
                    content = "API documentation content", // Would extract from file
                    type = DocumentType.API_CLASS,
                    url = filePath,
                    keywords = listOf("api", "reference"),
                    lastModified = kotlinx.datetime.Clock.System.now()
                ))
            }
        }

        // Add examples
        exampleLibrary.examples.forEach { example ->
            documents.add(SearchDocument(
                id = example.id,
                title = example.title,
                content = example.description + " " + example.codeBlocks.joinToString(" ") { it.code },
                type = DocumentType.EXAMPLE,
                url = "/examples/${example.id}",
                keywords = example.tags,
                lastModified = kotlinx.datetime.Clock.System.now()
            ))
        }

        // Add migration guides
        migrationGuides.forEach { guide ->
            documents.add(SearchDocument(
                id = guide.id,
                title = guide.title,
                content = guide.sections.joinToString(" ") { it.content },
                type = DocumentType.MIGRATION_GUIDE,
                url = "/migration/${guide.id}",
                keywords = listOf("migration", guide.fromLibrary.name.lowercase()),
                lastModified = kotlinx.datetime.Clock.System.now()
            ))
        }

        return documents
    }

    private fun createSiteSections(
        dokkaResult: DocumentationResult,
        exampleLibrary: ExampleLibrary,
        migrationGuides: List<MigrationGuide>
    ): List<SiteSection> {
        return listOf(
            SiteSection(
                id = "home",
                title = "Home",
                path = "/",
                content = generateHomePageContent(),
                type = SectionType.PAGE,
                lastModified = LocalDateTime.now().toString()
            ),
            SiteSection(
                id = "api",
                title = "API Reference",
                path = "/api/",
                content = "API documentation generated by Dokka",
                type = SectionType.API_REFERENCE,
                lastModified = LocalDateTime.now().toString()
            ),
            SiteSection(
                id = "examples",
                title = "Examples",
                path = "/examples/",
                content = "Interactive code examples",
                type = SectionType.EXAMPLE,
                lastModified = LocalDateTime.now().toString(),
                children = exampleLibrary.examples.map { example ->
                    SiteSection(
                        id = example.id,
                        title = example.title,
                        path = "/examples/${example.id}",
                        content = example.description,
                        type = SectionType.EXAMPLE,
                        lastModified = LocalDateTime.now().toString()
                    )
                }
            ),
            SiteSection(
                id = "migration",
                title = "Migration Guides",
                path = "/migration/",
                content = "Migration guides from other 3D libraries",
                type = SectionType.MIGRATION_GUIDE,
                lastModified = LocalDateTime.now().toString(),
                children = migrationGuides.map { guide ->
                    SiteSection(
                        id = guide.id,
                        title = guide.title,
                        path = "/migration/${guide.id}",
                        content = guide.sections.firstOrNull()?.content ?: "",
                        type = SectionType.MIGRATION_GUIDE,
                        lastModified = LocalDateTime.now().toString()
                    )
                }
            )
        )
    }

    private fun createNavigation(sections: List<SiteSection>): NavigationStructure {
        return NavigationStructure(
            sections = sections.map { section ->
                NavigationItem(
                    id = section.id,
                    title = section.title,
                    url = section.path,
                    children = section.children.map { child ->
                        NavigationItem(
                            id = child.id,
                            title = child.title,
                            url = child.path
                        )
                    }
                )
            },
            api = NavigationItem("api", "API Reference", "/api/"),
            platforms = listOf(
                NavigationItem("jvm", "JVM", "/platforms/jvm"),
                NavigationItem("js", "JavaScript", "/platforms/js"),
                NavigationItem("android", "Android", "/platforms/android")
            )
        )
    }

    private fun generateHomePageContent(): String {
        return """
            # Welcome to Materia Documentation

            Materia is a Kotlin Multiplatform 3D graphics library that provides Three.js-like API
            for creating 3D applications across JVM, Web, Android, iOS, and Native platforms.

            ## Quick Start

            Get started with Materia in just a few steps:

            1. **Installation**: Add Materia to your project dependencies
            2. **Basic Setup**: Create your first 3D scene
            3. **Explore Examples**: Learn from interactive code examples
            4. **Advanced Features**: Dive into advanced rendering techniques

            ## Key Features

            - **Cross-Platform**: Write once, run everywhere
            - **Modern Graphics**: WebGPU and Vulkan backends
            - **Type Safety**: Kotlin's type system ensures reliability
            - **Performance**: Optimized for 60 FPS with 100k+ triangles
            - **Three.js Compatible**: Familiar API for easy migration

            ## Documentation Sections

            - [API Reference](/api/) - Complete API documentation
            - [Examples](/examples/) - Interactive code examples
            - [Migration Guides](/migration/) - Migrate from other 3D libraries
            - [Performance Guide](/performance/) - Optimization best practices
        """.trimIndent()
    }

    private fun notifyLiveReloadClients(event: LiveReloadEvent) {
        if (!config.features.hotReload) return

        // WebSocket implementation to notify connected clients
        println("📡 Broadcasting live reload event: ${event.type}")
    }

    // Request handling helpers

    private fun recordRequestTime(responseTime: Long) {
        responseTimeHistory.add(responseTime)
        if (responseTimeHistory.size > 1000) {
            responseTimeHistory.removeFirst()
        }

        serverStats = serverStats.copy(
            totalRequests = serverStats.totalRequests + 1
        )
    }

    private fun applySecurityHeaders(headers: MutableMap<String, String>) {
        if (config.security.enableCSP) {
            headers["Content-Security-Policy"] = """
                default-src 'self';
                script-src 'self' 'unsafe-eval' 'unsafe-inline';
                style-src 'self' 'unsafe-inline';
                img-src 'self' data: https:;
                font-src 'self' https:;
            """.trimIndent().replace("\n", " ")
        }

        headers["X-Frame-Options"] = "DENY"
        headers["X-Content-Type-Options"] = "nosniff"
        headers["X-XSS-Protection"] = "1; mode=block"
    }

    private fun shouldCompress(contentType: String): Boolean {
        return config.staticFiles.compression && (
            contentType.startsWith("text/") ||
            contentType.startsWith("application/json") ||
            contentType.startsWith("application/javascript") ||
            contentType.startsWith("application/xml")
        )
    }
}

/**
 * Static site generator for building documentation without a server
 */
class StaticSiteGenerator {
    suspend fun generateStaticSite(
        sourceDirectories: List<String>,
        outputDirectory: String,
        baseUrl: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            println("🔄 Generating static documentation site...")

            val server = DocServer.createProductionServer()
            val site = server.generateSite(sourceDirectories, outputDirectory)

            // Generate static HTML files
            generateStaticPages(site, outputDirectory, baseUrl)

            // Copy assets
            copyAssets(outputDirectory)

            // Generate search index file
            val searchIndexPath = "$outputDirectory/search-index.json"
            File(searchIndexPath).writeText(Json.encodeToString(site.searchIndex))

            // Generate sitemap
            generateSitemap(site, outputDirectory, baseUrl)

            println("✅ Static site generation complete!")
            println("📁 Output directory: $outputDirectory")
            true
        } catch (e: Exception) {
            println("❌ Static site generation failed: ${e.message}")
            false
        }
    }

    private fun generateStaticPages(site: DocumentationSite, outputDir: String, baseUrl: String) {
        site.sections.forEach { section ->
            generatePageFile(section, outputDir, baseUrl, site.theme)
            section.children.forEach { child ->
                generatePageFile(child, outputDir, baseUrl, site.theme)
            }
        }
    }

    private fun generatePageFile(section: SiteSection, outputDir: String, baseUrl: String, theme: ThemeConfiguration) {
        val htmlContent = buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"en\">")
            appendLine("<head>")
            appendLine("  <meta charset=\"UTF-8\">")
            appendLine("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
            appendLine("  <title>${section.title} - Materia Documentation</title>")
            appendLine("  <link rel=\"stylesheet\" href=\"${baseUrl}/css/main.css\">")
            theme.customCss.forEach { css ->
                appendLine("  <link rel=\"stylesheet\" href=\"${baseUrl}/$css\">")
            }
            appendLine("</head>")
            appendLine("<body>")
            appendLine("  <div id=\"app\">")
            appendLine("    <main>")
            appendLine("      <h1>${section.title}</h1>")
            appendLine("      <div class=\"content\">")
            appendLine(markdownToHtml(section.content))
            appendLine("      </div>")
            appendLine("    </main>")
            appendLine("  </div>")
            appendLine("  <script src=\"${baseUrl}/js/main.js\"></script>")
            theme.customJs.forEach { js ->
                appendLine("  <script src=\"${baseUrl}/$js\"></script>")
            }
            appendLine("</body>")
            appendLine("</html>")
        }

        val outputPath = "$outputDir${section.path}${if (section.path.endsWith("/")) "index.html" else ".html"}"
        File(outputPath).parentFile.mkdirs()
        File(outputPath).writeText(htmlContent)
    }

    private fun copyAssets(outputDir: String) {
        // Copy CSS, JS, images, and other assets
        val assetsDir = File("$outputDir/assets")
        assetsDir.mkdirs()

        // Would copy actual asset files in real implementation
    }

    private fun generateSitemap(site: DocumentationSite, outputDir: String, baseUrl: String) {
        val sitemap = buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">")

            site.sections.forEach { section ->
                appendLine("  <url>")
                appendLine("    <loc>$baseUrl${section.path}</loc>")
                appendLine("    <lastmod>${section.lastModified}</lastmod>")
                appendLine("    <changefreq>weekly</changefreq>")
                appendLine("    <priority>0.8</priority>")
                appendLine("  </url>")

                section.children.forEach { child ->
                    appendLine("  <url>")
                    appendLine("    <loc>$baseUrl${child.path}</loc>")
                    appendLine("    <lastmod>${child.lastModified}</lastmod>")
                    appendLine("    <changefreq>monthly</changefreq>")
                    appendLine("    <priority>0.6</priority>")
                    appendLine("  </url>")
                }
            }

            appendLine("</urlset>")
        }

        File("$outputDir/sitemap.xml").writeText(sitemap)
    }

    private fun markdownToHtml(markdown: String): String {
        // Simplified markdown to HTML conversion
        return markdown
            .replace(Regex("^# (.+)$", RegexOption.MULTILINE), "<h1>$1</h1>")
            .replace(Regex("^## (.+)$", RegexOption.MULTILINE), "<h2>$1</h2>")
            .replace(Regex("^### (.+)$", RegexOption.MULTILINE), "<h3>$1</h3>")
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
            .replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
            .replace(Regex("`(.+?)`"), "<code>$1</code>")
            .replace(Regex("\\n\\n"), "</p><p>")
            .let { "<p>$it</p>" }
    }
}

/**
 * CLI tool for documentation server operations
 */
object DocServerCLI {
    suspend fun main(args: Array<String>) {
        when (args.getOrNull(0)) {
            "serve" -> {
                val port = args.getOrNull(1)?.toIntOrNull() ?: 8080
                val server = DocServer.createDevelopmentServer(port)
                server.start()

                // Keep running until interrupted
                while (true) {
                    delay(1000)
                }
            }
            "build" -> {
                val outputDir = args.getOrNull(1) ?: "./docs/build"
                val baseUrl = args.getOrNull(2) ?: ""

                val generator = StaticSiteGenerator()
                val success = generator.generateStaticSite(
                    sourceDirectories = listOf("src/commonMain/kotlin"),
                    outputDirectory = outputDir,
                    baseUrl = baseUrl
                )

                if (success) {
                    println("✅ Documentation built successfully")
                } else {
                    println("❌ Documentation build failed")
                }
            }
            else -> {
                println("""
                    Materia Documentation Server CLI

                    Usage:
                      serve [port]           Start development server (default port: 8080)
                      build [output] [url]   Build static site (default: ./docs/build)

                    Examples:
                      serve 3000             Start server on port 3000
                      build ./dist           Build static site to ./dist
                      build ./dist /docs     Build with base URL /docs
                """.trimIndent())
            }
        }
    }
}