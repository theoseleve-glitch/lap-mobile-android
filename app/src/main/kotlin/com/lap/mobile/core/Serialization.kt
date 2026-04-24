package com.lap.mobile.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

/**
 * Project-wide SerializersModule for app-local sealed hierarchies that
 * aren't part of the shared lap-schemas contract. Schema types
 * (`SourceTag`, `EnvironmentTag`, `SessionManifest`, …) live in the
 * `:schemas` module and round-trip via `com.lap.schemas.v1.LapSchemasJson`
 * with snake_case naming; don't re-register them here.
 */
val lapSerializersModule: SerializersModule = SerializersModule {
    // Register polymorphic hierarchies for app-owned sealed types here.
    // Block B audit (T040): no duplicates — schema types come from :schemas.
}

/**
 * Project-wide Json instance for app-local types. Use
 * [com.lap.schemas.v1.LapSchemasJson] instead when round-tripping any
 * lap-schemas type (it applies snake_case naming to match Pydantic).
 */
val lapJson: Json = Json {
    serializersModule = lapSerializersModule
    classDiscriminator = "@type"
    encodeDefaults = false
    ignoreUnknownKeys = true
    isLenient = false
    explicitNulls = false
}
