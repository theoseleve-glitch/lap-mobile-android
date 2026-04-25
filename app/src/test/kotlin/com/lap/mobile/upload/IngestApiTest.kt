package com.lap.mobile.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.POST
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions

class IngestApiTest {
    @Test
    fun ingest_method_is_annotated_with_post() {
        val method = IngestApi::class.functions.first { it.name == "ingestSession" }

        val post = method.findAnnotation<POST>()

        assertTrue("ingestSession must be @POST-annotated", post != null)
    }

    @Test
    fun ingest_endpoint_path_matches_server_contract() {
        val method = IngestApi::class.functions.first { it.name == "ingestSession" }
        val post = method.findAnnotation<POST>()!!

        // Path must match the server route per ADR-012; renaming requires
        // a coordinated server change.
        assertEquals("/api/v1/sessions/ingest", post.value)
    }

    @Test
    fun ingest_method_is_suspend_so_callers_use_coroutines() {
        val method = IngestApi::class.functions.first { it.name == "ingestSession" }

        assertTrue("ingestSession must be suspend so the upload worker can await it", method.isSuspend)
    }
}
