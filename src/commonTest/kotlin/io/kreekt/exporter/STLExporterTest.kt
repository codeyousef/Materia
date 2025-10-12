package io.kreekt.exporter

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertNotNull

class STLExporterTest {
    @Test
    fun testSTLExporterCreation() {
        val exporter = STLExporter()
        assertNotNull(exporter)
    }
}
