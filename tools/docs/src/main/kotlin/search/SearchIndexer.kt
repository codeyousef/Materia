package tools.docs.search

import kotlinx.coroutines.*
import kotlinx.datetime.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File
import kotlin.math.*
import kotlin.time.ExperimentalTime
import kotlin.time.Duration

/**
 * Advanced search index builder for Materia documentation.
 * Creates searchable indices with full-text search, semantic matching, and intelligent ranking.
 */
@OptIn(ExperimentalTime::class)
@Serializable
data class SearchDocument(
    val id: String,
    val title: String,
    val content: String,
    val type: DocumentType,
    val url: String,
    val keywords: List<String>,
    val metadata: Map<String, String> = emptyMap(),
    val lastModified: kotlinx.datetime.Instant,
    val language: String = "en"
)

enum class DocumentType {
    API_CLASS,
    API_FUNCTION,
    API_PROPERTY,
    TUTORIAL,
    GUIDE,
    EXAMPLE,
    FAQ,
    MIGRATION_GUIDE,
    REFERENCE
}

@Serializable
data class SearchIndex(
    val version: String,
    val documents: List<IndexedDocument>,
    val termIndex: Map<String, List<DocumentScore>>,
    val vectorIndex: Map<String, List<Double>>, // For semantic search
    val metadataIndex: Map<String, List<String>>,
    val statistics: IndexStatistics,
    val lastUpdated: kotlinx.datetime.Instant
)

@Serializable
data class IndexedDocument(
    val id: String,
    val title: String,
    val type: DocumentType,
    val url: String,
    val excerpt: String,
    val keywords: List<String>,
    val termFrequencies: Map<String, Double>,
    val documentVector: List<Double>,
    val boost: Double = 1.0
)

@Serializable
data class DocumentScore(
    val documentId: String,
    val frequency: Int,
    val positions: List<Int>,
    val fieldBoosts: Map<String, Double> = emptyMap()
)

@Serializable
data class IndexStatistics(
    val totalDocuments: Int,
    val totalTerms: Int,
    val averageDocumentLength: Double,
    val vocabularySize: Int,
    val indexSize: Long,
    val buildTime: Long
)

@Serializable
data class SearchQuery(
    val query: String,
    val filters: Map<String, String> = emptyMap(),
    val documentTypes: List<DocumentType> = emptyList(),
    val limit: Int = 20,
    val offset: Int = 0,
    val includeContent: Boolean = false,
    val fuzzySearch: Boolean = true,
    val semanticSearch: Boolean = false
)

@Serializable
data class SearchResult(
    val query: String,
    val results: List<SearchHit>,
    val totalHits: Int,
    val searchTime: Long,
    val suggestions: List<String> = emptyList(),
    val facets: Map<String, List<FacetValue>> = emptyMap()
)

@Serializable
data class SearchHit(
    val document: IndexedDocument,
    val score: Double,
    val highlights: List<Highlight>,
    val explanation: String? = null
)

@Serializable
data class Highlight(
    val field: String,
    val fragment: String,
    val startOffset: Int,
    val endOffset: Int
)

@Serializable
data class FacetValue(
    val value: String,
    val count: Int
)

@Serializable
data class SearchSuggestion(
    val term: String,
    val frequency: Int,
    val distance: Int // Edit distance from original query
)

/**
 * Main search indexer with advanced features for documentation search.
 */
class SearchIndexer {
    private val stopWords = setOf(
        "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
        "has", "he", "in", "is", "it", "its", "of", "on", "that", "the",
        "to", "was", "will", "with", "the", "this", "but", "they", "have",
        "had", "what", "said", "each", "which", "she", "do", "how", "their",
        "if", "up", "out", "many", "then", "them", "these", "so", "some", "her",
        "would", "make", "like", "into", "him", "time", "two", "more", "go", "no",
        "way", "could", "my", "than", "first", "been", "call", "who", "oil", "sit",
        "now", "find", "down", "day", "did", "get", "come", "made", "may", "part"
    )

    private val stemmer = PorterStemmer()
    private var currentIndex: SearchIndex? = null

    companion object {
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

        // TF-IDF scoring parameters
        private const val K1 = 1.2 // BM25 k1 parameter
        private const val B = 0.75 // BM25 b parameter
    }

    /**
     * Build comprehensive search index from documents
     */
    suspend fun buildIndex(
        documents: List<SearchDocument>,
        includeSemanticVectors: Boolean = true
    ): SearchIndex = withContext(Dispatchers.Default) {
        val startTime = Clock.System.now().toEpochMilliseconds()

        // Preprocess documents
        val preprocessedDocs = documents.map { preprocessDocument(it) }

        // Build term index with TF-IDF scores
        val termIndex = buildTermIndex(preprocessedDocs)

        // Build vector index for semantic search (if enabled)
        val vectorIndex = if (includeSemanticVectors) {
            buildVectorIndex(preprocessedDocs)
        } else emptyMap()

        // Build metadata index for faceted search
        val metadataIndex = buildMetadataIndex(documents)

        // Create indexed documents
        val indexedDocuments = preprocessedDocs.map { doc ->
            createIndexedDocument(doc, termIndex, vectorIndex)
        }

        // Calculate statistics
        val statistics = calculateIndexStatistics(
            documents = indexedDocuments,
            termIndex = termIndex,
            buildTime = Clock.System.now().toEpochMilliseconds() - startTime
        )

        SearchIndex(
            version = "1.0",
            documents = indexedDocuments,
            termIndex = termIndex,
            vectorIndex = vectorIndex,
            metadataIndex = metadataIndex,
            statistics = statistics,
            lastUpdated = kotlinx.datetime.Clock.System.now()
        )
    }

    /**
     * Perform advanced search with ranking and highlighting
     */
    suspend fun search(
        index: SearchIndex,
        query: SearchQuery
    ): SearchResult = withContext(Dispatchers.Default) {
        val startTime = Clock.System.now().toEpochMilliseconds()

        // Parse and normalize query
        val queryTerms = normalizeQuery(query.query)

        // Get candidate documents
        val candidates = if (query.semanticSearch && index.vectorIndex.isNotEmpty()) {
            performSemanticSearch(index, queryTerms)
        } else {
            performTermSearch(index, queryTerms)
        }

        // Apply filters
        val filteredCandidates = applyFilters(candidates, query, index)

        // Score and rank documents
        val scoredResults = scoreDocuments(filteredCandidates, queryTerms, index)

        // Apply pagination
        val paginatedResults = scoredResults
            .drop(query.offset)
            .take(query.limit)

        // Generate highlights
        val resultsWithHighlights = paginatedResults.map { hit ->
            hit.copy(highlights = generateHighlights(hit.document, queryTerms))
        }

        // Generate search suggestions
        val suggestions = generateSuggestions(query.query, index)

        // Generate facets
        val facets = generateFacets(filteredCandidates, index)

        SearchResult(
            query = query.query,
            results = resultsWithHighlights,
            totalHits = filteredCandidates.size,
            searchTime = Clock.System.now().toEpochMilliseconds() - startTime,
            suggestions = suggestions,
            facets = facets
        )
    }

    /**
     * Update index incrementally with new/modified documents
     */
    suspend fun updateIndex(
        index: SearchIndex,
        updates: List<SearchDocument>,
        deletes: List<String> = emptyList()
    ): SearchIndex = withContext(Dispatchers.Default) {
        // Remove deleted documents
        val updatedDocuments = index.documents.filter { it.id !in deletes }.toMutableList()

        // Remove/update existing documents
        updates.forEach { update ->
            updatedDocuments.removeAll { it.id == update.id }
        }

        // Add new/updated documents
        val preprocessedUpdates = updates.map { preprocessDocument(it) }
        val newIndexedDocs = preprocessedUpdates.map { doc ->
            val termFreqs = calculateTermFrequencies(tokenizeText(doc.content))
            IndexedDocument(
                id = doc.id,
                title = doc.title,
                type = doc.type,
                url = doc.url,
                excerpt = generateExcerpt(doc.content),
                keywords = doc.keywords,
                termFrequencies = termFreqs,
                documentVector = emptyList(), // Would compute if semantic search enabled
                boost = calculateDocumentBoost(doc)
            )
        }

        updatedDocuments.addAll(newIndexedDocs)

        // Rebuild indices
        val allDocuments = updatedDocuments.map { indexedToSearchDoc(it) }
        buildIndex(allDocuments, index.vectorIndex.isNotEmpty())
    }

    /**
     * Export index to file
     */
    suspend fun exportIndex(
        index: SearchIndex,
        outputPath: String,
        format: IndexFormat = IndexFormat.JSON
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            when (format) {
                IndexFormat.JSON -> {
                    val jsonData = json.encodeToString(index)
                    File(outputPath).writeText(jsonData)
                }
                IndexFormat.BINARY -> {
                    // Binary format for faster loading
                    exportBinaryIndex(index, outputPath)
                }
                IndexFormat.ELASTICSEARCH -> {
                    // Export in Elasticsearch format
                    exportElasticsearchIndex(index, outputPath)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Load index from file
     */
    suspend fun loadIndex(
        inputPath: String,
        format: IndexFormat = IndexFormat.JSON
    ): SearchIndex? = withContext(Dispatchers.IO) {
        try {
            when (format) {
                IndexFormat.JSON -> {
                    val jsonData = File(inputPath).readText()
                    json.decodeFromString<SearchIndex>(jsonData)
                }
                IndexFormat.BINARY -> {
                    loadBinaryIndex(inputPath)
                }
                IndexFormat.ELASTICSEARCH -> {
                    loadElasticsearchIndex(inputPath)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    // Private helper methods

    private fun preprocessDocument(document: SearchDocument): SearchDocument {
        // Clean and normalize content
        val cleanContent = cleanText(document.content)

        // Extract additional keywords from content
        val extractedKeywords = extractKeywords(cleanContent)

        return document.copy(
            content = cleanContent,
            keywords = document.keywords + extractedKeywords
        )
    }

    private fun cleanText(text: String): String {
        return text
            .replace(Regex("<[^>]+>"), "") // Remove HTML tags
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .replace(Regex("[^\\w\\s-.]"), " ") // Remove special characters except basic punctuation
            .trim()
    }

    private fun extractKeywords(content: String): List<String> {
        val words = tokenizeText(content)
        val frequencies = words.groupingBy { it }.eachCount()

        return frequencies
            .filter { it.value >= 2 } // Appear at least twice
            .filter { it.key.length >= 3 } // At least 3 characters
            .toList()
            .sortedByDescending { it.second }
            .take(10)
            .map { it.first }
    }

    private fun tokenizeText(text: String): List<String> {
        return text.lowercase()
            .split(Regex("\\W+"))
            .filter { it.isNotBlank() }
            .filter { it !in stopWords }
            .map { stemmer.stem(it) }
    }

    private fun buildTermIndex(documents: List<SearchDocument>): Map<String, List<DocumentScore>> {
        val termIndex = mutableMapOf<String, MutableList<DocumentScore>>()

        documents.forEachIndexed { docIndex, document ->
            val tokens = tokenizeText(document.content)
            val termPositions = mutableMapOf<String, MutableList<Int>>()

            tokens.forEachIndexed { position, term ->
                termPositions.getOrPut(term) { mutableListOf() }.add(position)
            }

            termPositions.forEach { (term, positions) ->
                val score = DocumentScore(
                    documentId = document.id,
                    frequency = positions.size,
                    positions = positions,
                    fieldBoosts = mapOf<String, Double>(
                        "title" to if (document.title.lowercase().contains(term)) 2.0 else 1.0,
                        "keywords" to if (document.keywords.any { it.lowercase().contains(term) }) 1.5 else 1.0
                    )
                )

                termIndex.getOrPut(term) { mutableListOf() }.add(score)
            }
        }

        return termIndex.mapValues { it.value.toList() }
    }

    private fun buildVectorIndex(documents: List<SearchDocument>): Map<String, List<Double>> {
        // Simplified TF-IDF vector computation
        val allTerms = documents.flatMap { tokenizeText(it.content) }.distinct()
        val vectorIndex = mutableMapOf<String, List<Double>>()

        documents.forEach { document ->
            val tokens = tokenizeText(document.content)
            val termFreqs = tokens.groupingBy { it }.eachCount()

            val vector = allTerms.map { term ->
                val tf = termFreqs[term]?.toDouble() ?: 0.0
                val idf = ln(documents.size.toDouble() / (documents.count { doc ->
                    tokenizeText(doc.content).contains(term)
                } + 1))
                tf * idf
            }

            vectorIndex[document.id] = normalizeVector(vector)
        }

        return vectorIndex
    }

    private fun buildMetadataIndex(documents: List<SearchDocument>): Map<String, List<String>> {
        val metadataIndex = mutableMapOf<String, MutableList<String>>()

        documents.forEach { document ->
            // Index document type
            metadataIndex.getOrPut("type") { mutableListOf() }.add(document.type.name)

            // Index custom metadata
            document.metadata.forEach { (key, value) ->
                metadataIndex.getOrPut(key) { mutableListOf() }.add(value)
            }

            // Index keywords
            document.keywords.forEach { keyword ->
                metadataIndex.getOrPut("keywords") { mutableListOf() }.add(keyword)
            }
        }

        return metadataIndex.mapValues { it.value.toList() }
    }

    private fun createIndexedDocument(
        document: SearchDocument,
        termIndex: Map<String, List<DocumentScore>>,
        vectorIndex: Map<String, List<Double>>
    ): IndexedDocument {
        val termFrequencies = calculateTermFrequencies(tokenizeText(document.content))
        val documentVector = vectorIndex[document.id] ?: emptyList()

        return IndexedDocument(
            id = document.id,
            title = document.title,
            type = document.type,
            url = document.url,
            excerpt = generateExcerpt(document.content),
            keywords = document.keywords,
            termFrequencies = termFrequencies,
            documentVector = documentVector,
            boost = calculateDocumentBoost(document)
        )
    }

    private fun calculateTermFrequencies(tokens: List<String>): Map<String, Double> {
        val frequencies = tokens.groupingBy { it }.eachCount()
        val maxFreq = frequencies.maxOfOrNull { it.value } ?: 1

        return frequencies.mapValues { (_, freq) ->
            freq.toDouble() / maxFreq // Normalized term frequency
        }
    }

    private fun generateExcerpt(content: String, maxLength: Int = 200): String {
        return if (content.length <= maxLength) {
            content
        } else {
            content.take(maxLength - 3) + "..."
        }
    }

    private fun calculateDocumentBoost(document: SearchDocument): Double {
        var boost = 1.0

        // Boost based on document type
        boost *= when (document.type) {
            DocumentType.API_CLASS, DocumentType.API_FUNCTION -> 1.2
            DocumentType.TUTORIAL, DocumentType.GUIDE -> 1.1
            DocumentType.EXAMPLE -> 1.0
            DocumentType.FAQ -> 0.9
            else -> 1.0
        }

        // Boost based on content quality indicators
        if (document.keywords.size > 5) boost *= 1.05
        if (document.content.length > 1000) boost *= 1.02

        return boost
    }

    private fun normalizeQuery(query: String): List<String> {
        return tokenizeText(query)
    }

    private fun performTermSearch(
        index: SearchIndex,
        queryTerms: List<String>
    ): List<IndexedDocument> {
        val candidates = mutableSetOf<String>()

        queryTerms.forEach { term ->
            index.termIndex[term]?.forEach { score ->
                candidates.add(score.documentId)
            }
        }

        return index.documents.filter { it.id in candidates }
    }

    private fun performSemanticSearch(
        index: SearchIndex,
        queryTerms: List<String>
    ): List<IndexedDocument> {
        // Simplified semantic search using vector similarity
        val queryVector = computeQueryVector(queryTerms, index)

        return index.documents
            .map { doc ->
                val similarity = cosineSimilarity(queryVector, doc.documentVector)
                doc to similarity
            }
            .filter { it.second > 0.1 } // Similarity threshold
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun applyFilters(
        candidates: List<IndexedDocument>,
        query: SearchQuery,
        index: SearchIndex
    ): List<IndexedDocument> {
        var filtered = candidates

        // Filter by document types
        if (query.documentTypes.isNotEmpty()) {
            filtered = filtered.filter { it.type in query.documentTypes }
        }

        // Apply custom filters
        query.filters.forEach { (key, value) ->
            filtered = filtered.filter { document ->
                when (key) {
                    "keywords" -> document.keywords.any { it.contains(value, ignoreCase = true) }
                    else -> true // Unknown filter, ignore
                }
            }
        }

        return filtered
    }

    private fun scoreDocuments(
        documents: List<IndexedDocument>,
        queryTerms: List<String>,
        index: SearchIndex
    ): List<SearchHit> {
        return documents.map { document ->
            val score = calculateBM25Score(document, queryTerms, index)
            SearchHit(
                document = document,
                score = score,
                highlights = emptyList() // Will be filled later
            )
        }.sortedByDescending { it.score }
    }

    private fun calculateBM25Score(
        document: IndexedDocument,
        queryTerms: List<String>,
        index: SearchIndex
    ): Double {
        val docLength = document.termFrequencies.values.sum()
        val avgDocLength = index.statistics.averageDocumentLength

        return queryTerms.sumOf { term ->
            val tf = document.termFrequencies[term] ?: 0.0
            val df = index.termIndex[term]?.size ?: 0
            val idf = ln((index.statistics.totalDocuments - df + 0.5) / (df + 0.5))

            val numerator = tf * (K1 + 1)
            val denominator = tf + K1 * (1 - B + B * (docLength / avgDocLength))

            idf * (numerator / denominator)
        } * document.boost
    }

    private fun generateHighlights(
        document: IndexedDocument,
        queryTerms: List<String>
    ): List<Highlight> {
        // Simplified highlighting - would be more sophisticated in real implementation
        return queryTerms.mapNotNull { term ->
            val index = document.excerpt.lowercase().indexOf(term.lowercase())
            if (index >= 0) {
                Highlight(
                    field = "excerpt",
                    fragment = document.excerpt,
                    startOffset = index,
                    endOffset = index + term.length
                )
            } else null
        }
    }

    private fun generateSuggestions(query: String, index: SearchIndex): List<String> {
        val queryTerms = normalizeQuery(query)
        val allTerms = index.termIndex.keys

        return queryTerms.flatMap { queryTerm ->
            allTerms.filter { term ->
                val distance = levenshteinDistance(queryTerm, term)
                distance <= 2 && distance > 0 // Allow up to 2 character differences
            }.take(3)
        }.distinct().take(5)
    }

    private fun generateFacets(
        documents: List<IndexedDocument>,
        index: SearchIndex
    ): Map<String, List<FacetValue>> {
        val facets = mutableMapOf<String, List<FacetValue>>()

        // Document type facets
        val typeCounts = documents.groupingBy { it.type.name }.eachCount()
        facets["type"] = typeCounts.map { (type, count) ->
            FacetValue(type, count)
        }.sortedByDescending { it.count }

        return facets
    }

    private fun calculateIndexStatistics(
        documents: List<IndexedDocument>,
        termIndex: Map<String, List<DocumentScore>>,
        buildTime: Long
    ): IndexStatistics {
        val totalTerms = documents.sumOf { it.termFrequencies.size }
        val avgDocLength = documents.map { it.termFrequencies.values.sum() }.average()

        return IndexStatistics(
            totalDocuments = documents.size,
            totalTerms = totalTerms,
            averageDocumentLength = avgDocLength,
            vocabularySize = termIndex.size,
            indexSize = 0, // Would calculate actual size
            buildTime = buildTime
        )
    }

    // Vector operations
    private fun normalizeVector(vector: List<Double>): List<Double> {
        val magnitude = sqrt(vector.sumOf { it * it })
        return if (magnitude > 0) vector.map { it / magnitude } else vector
    }

    private fun computeQueryVector(queryTerms: List<String>, index: SearchIndex): List<Double> {
        // Simplified query vector computation
        return index.documents.firstOrNull()?.documentVector?.map { 0.0 } ?: emptyList()
    }

    private fun cosineSimilarity(vector1: List<Double>, vector2: List<Double>): Double {
        if (vector1.size != vector2.size || vector1.isEmpty()) return 0.0

        val dotProduct = vector1.zip(vector2).sumOf { (a, b) -> a * b }
        val magnitude1 = sqrt(vector1.sumOf { it * it })
        val magnitude2 = sqrt(vector2.sumOf { it * it })

        return if (magnitude1 > 0 && magnitude2 > 0) {
            dotProduct / (magnitude1 * magnitude2)
        } else 0.0
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                dp[i][j] = if (s1[i - 1] == s2[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1]) + 1
                }
            }
        }

        return dp[s1.length][s2.length]
    }

    private fun indexedToSearchDoc(indexed: IndexedDocument): SearchDocument {
        return SearchDocument(
            id = indexed.id,
            title = indexed.title,
            content = indexed.excerpt, // Limited content
            type = indexed.type,
            url = indexed.url,
            keywords = indexed.keywords,
            lastModified = kotlinx.datetime.Clock.System.now()
        )
    }

    // Export/Import methods (simplified implementations)
    private fun exportBinaryIndex(index: SearchIndex, outputPath: String) {
        // Binary serialization implementation
    }

    private fun exportElasticsearchIndex(index: SearchIndex, outputPath: String) {
        // Elasticsearch format export
    }

    private fun loadBinaryIndex(inputPath: String): SearchIndex? {
        // Binary deserialization implementation
        return null
    }

    private fun loadElasticsearchIndex(inputPath: String): SearchIndex? {
        // Elasticsearch format import
        return null
    }
}

enum class IndexFormat {
    JSON, BINARY, ELASTICSEARCH
}

/**
 * Porter Stemmer implementation for word stemming
 */
class PorterStemmer {
    fun stem(word: String): String {
        // Simplified Porter stemmer implementation
        var result = word.lowercase()

        // Step 1a
        if (result.endsWith("sses")) {
            result = result.dropLast(2)
        } else if (result.endsWith("ies")) {
            result = result.dropLast(2)
        } else if (result.endsWith("ss")) {
            // Keep as is
        } else if (result.endsWith("s") && result.length > 1) {
            result = result.dropLast(1)
        }

        // Step 1b (simplified)
        if (result.endsWith("ing") && result.length > 3) {
            result = result.dropLast(3)
        } else if (result.endsWith("ed") && result.length > 2) {
            result = result.dropLast(2)
        }

        return result
    }
}

/**
 * Utility functions for search operations
 */
object SearchUtils {
    fun highlightText(text: String, terms: List<String>, prefix: String = "<mark>", suffix: String = "</mark>"): String {
        var highlighted = text
        terms.forEach { term ->
            val regex = Regex("(?i)\\b$term\\b")
            highlighted = highlighted.replace(regex, "$prefix$term$suffix")
        }
        return highlighted
    }

    fun extractSnippet(text: String, term: String, contextLength: Int = 100): String {
        val index = text.lowercase().indexOf(term.lowercase())
        if (index == -1) return text.take(contextLength)

        val start = maxOf(0, index - contextLength / 2)
        val end = minOf(text.length, index + term.length + contextLength / 2)

        val snippet = text.substring(start, end)
        return if (start > 0) "...$snippet" else snippet + if (end < text.length) "..." else ""
    }

    fun calculateRelevanceScore(
        document: IndexedDocument,
        query: String,
        clickCount: Int = 0,
        lastClickTime: kotlinx.datetime.Instant? = null
    ): Double {
        var score = 1.0

        // Content relevance
        val queryTerms = query.lowercase().split(Regex("\\s+"))
        val titleMatches = queryTerms.count { document.title.lowercase().contains(it) }
        val keywordMatches = queryTerms.count { term ->
            document.keywords.any { it.lowercase().contains(term) }
        }

        score += titleMatches * 0.3 + keywordMatches * 0.2

        // Click-through rate boost
        if (clickCount > 0) {
            score += ln(clickCount.toDouble() + 1) * 0.1
        }

        // Recency boost
        lastClickTime?.let { clickTime ->
            val daysSinceClick = (Clock.System.now() - clickTime).inWholeDays.toInt()
            if (daysSinceClick < 30) {
                score += (30 - daysSinceClick) / 30.0 * 0.1
            }
        }

        return score
    }

    fun validateQuery(query: String): QueryValidation {
        val issues = mutableListOf<String>()

        if (query.isBlank()) {
            issues.add("Query cannot be empty")
        }

        if (query.length < 2) {
            issues.add("Query too short - minimum 2 characters")
        }

        if (query.length > 200) {
            issues.add("Query too long - maximum 200 characters")
        }

        val specialChars = query.count { !it.isLetterOrDigit() && !it.isWhitespace() }
        if (specialChars > query.length / 2) {
            issues.add("Too many special characters")
        }

        return QueryValidation(
            valid = issues.isEmpty(),
            issues = issues,
            suggestions = if (issues.isNotEmpty()) {
                listOf("Try using simpler terms", "Remove special characters", "Use keywords instead of full sentences")
            } else emptyList()
        )
    }
}

@Serializable
data class QueryValidation(
    val valid: Boolean,
    val issues: List<String>,
    val suggestions: List<String>
)