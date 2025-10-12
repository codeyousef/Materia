package io.kreekt.exporter

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertNotNull

class OBJExporterTest {
    @Test
    fun testOBJExporterCreation() {
        val exporter = OBJExporter()
        assertNotNull(exporter)
    }
}
