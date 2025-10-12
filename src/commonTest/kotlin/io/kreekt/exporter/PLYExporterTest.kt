package io.kreekt.exporter

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertNotNull

class PLYExporterTest {
    @Test
    fun testPLYExporterCreation() {
        val exporter = PLYExporter()
        assertNotNull(exporter)
    }
}
