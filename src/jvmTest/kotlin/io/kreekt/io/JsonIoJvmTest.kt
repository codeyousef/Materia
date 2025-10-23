package io.kreekt.io

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

class JsonIoJvmTest {

    private val tempFiles = mutableListOf<Path>()

    @AfterTest
    fun cleanup() {
        tempFiles.forEach { it.deleteIfExists() }
        tempFiles.clear()
    }

    @Serializable
    data class Sample(val value: Int)

    @Test
    fun loadJsonFromFilesystem() {
        val temp = Files.createTempFile("json-io", ".json")
        tempFiles.add(temp)
        saveJson(temp.toString(), Sample.serializer(), Sample(42))

        val decoded = runBlocking { loadJson(temp.toString(), Sample.serializer()) }
        assertEquals(42, decoded.value)
    }

    @Test
    fun loadJsonReifiedShortcut() {
        val path = Files.createTempFile("json-io-reified", ".json")
        tempFiles.add(path)
        path.writeText("""{"value":99}""")

        val decoded = runBlocking { loadJson<Sample>(path.toString()) }
        assertEquals(99, decoded.value)
    }
}
