package io.materia.exporter

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertNotNull

class GLTFExporterTest {
    @Test
    fun testGLTFExporterCreation() {
        val exporter = GLTFExporter()
        assertNotNull(exporter)
    }
}
