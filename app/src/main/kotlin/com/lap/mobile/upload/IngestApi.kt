package com.lap.mobile.upload

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface IngestApi {
    @Multipart
    @POST("/api/v1/sessions/ingest")
    suspend fun ingestSession(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Part manifest: MultipartBody.Part,
        @Part chunks: List<MultipartBody.Part>,
    ): Response<Unit>
}
