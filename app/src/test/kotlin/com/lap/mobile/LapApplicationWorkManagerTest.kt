package com.lap.mobile

import androidx.work.Configuration
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.javaField

/**
 * Verifies the WorkManager + Hilt wiring contract (T146). Application
 * classes need an Android Context to instantiate, so this test is
 * reflective — it asserts the class signature declares
 * Configuration.Provider and exposes the HiltWorkerFactory injection
 * point that the runtime requires for HiltWorker constructors to be
 * resolved.
 */
class LapApplicationWorkManagerTest {
    @Test
    fun lap_application_implements_work_manager_configuration_provider() {
        val implementsProvider = Configuration.Provider::class.java
            .isAssignableFrom(LapApplication::class.java)

        assertTrue(
            "LapApplication must implement WorkManager Configuration.Provider so HiltWorkerFactory is wired",
            implementsProvider,
        )
    }

    @Test
    fun lap_application_declares_hilt_worker_factory_injection_point() {
        val factoryField = LapApplication::class
            .declaredMemberProperties
            .firstOrNull { property ->
                val javaField = property.javaField ?: return@firstOrNull false
                javaField.type.name == "androidx.hilt.work.HiltWorkerFactory"
            }

        assertNotNull(
            "LapApplication must inject HiltWorkerFactory so HiltWorker constructors resolve",
            factoryField,
        )
    }
}
