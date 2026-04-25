package com.lap.mobile.di

import com.lap.mobile.upload.BundleUploader
import com.lap.mobile.upload.UploadResult
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

private val PLACEHOLDER_HTTP_CODE: Int? = null

/**
 * Hilt providers for the upload pipeline (T146/T147). [Clock] is
 * pinned to `Clock.systemUTC()` so the orchestrator's nextRetryAt
 * computations (T154+) use UTC consistently with the journal +
 * server canonicalisation (ADR-007 / ADR-012).
 *
 * [BundleUploader] is a placeholder that always returns
 * [UploadResult.Retryable] so the worker doesn't accidentally mark
 * any session "completed" before the real HTTP impl ([IngestApi],
 * T148) lands. Replacing this provider with the Retrofit-backed
 * impl is a one-line @Binds change in the next task.
 */
@Module
@InstallIn(SingletonComponent::class)
object UploadModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun provideBundleUploader(): BundleUploader = BundleUploader { _, _ ->
        UploadResult.Retryable(httpCode = PLACEHOLDER_HTTP_CODE, message = "uploader not yet implemented (T148)")
    }
}
