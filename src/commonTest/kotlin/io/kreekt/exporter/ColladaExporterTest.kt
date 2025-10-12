package io.kreekt.exporter

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertNotNull

class ColladaExporterTest {
    @Test
    fun testColladaExporterCreation() {
        val exporter = ColladaExporter()
        assertNotNull(exporter)
    }
}
