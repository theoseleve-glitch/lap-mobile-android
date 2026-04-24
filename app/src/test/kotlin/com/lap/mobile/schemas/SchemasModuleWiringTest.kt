package com.lap.mobile.schemas

import com.lap.schemas.SchemasModule
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Smoke test proving the `:schemas` Gradle module (at `../lap-schemas/kotlin`)
 * is on the app's classpath. If Gradle includes are missing or the module
 * fails to compile, this test fails at compile time — exactly what T026 asks.
 */
class SchemasModuleWiringTest {
    @Test
    fun schemas_module_namespace_is_com_lap_schemas() {
        assertEquals("com.lap.schemas", SchemasModule.NAMESPACE)
    }
}
