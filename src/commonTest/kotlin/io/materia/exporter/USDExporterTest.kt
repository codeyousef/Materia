package io.materia.exporter

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertNotNull

class USDExporterTest {
    @Test
    fun testUSDExporterCreation() {
        val exporter = USDExporter()
        assertNotNull(exporter)
    }
}
