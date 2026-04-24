package com.lap.mobile.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test fixture sealed hierarchy — proves the polymorphic wiring works end
 * to end before Block B defines real polymorphic types (SourceTag,
 * EnvironmentTag, etc.).
 */
@Serializable
sealed interface SerializationFixture {
    val id: String

    @Serializable
    @SerialName("a")
    data class TypeA(override val id: String, val count: Int) : SerializationFixture

    @Serializable
    @SerialName("b")
    data class TypeB(override val id: String, val name: String) : SerializationFixture
}

class SerializationTest {
    @Test
    fun `lapJson is configured`() {
        assertNotNull(lapJson)
    }

    @Test
    fun `round-trips a simple serializable`() {
        val original = SerializationFixture.TypeA(id = "abc", count = 42)
        val payload = lapJson.encodeToString<SerializationFixture>(original)
        val restored = lapJson.decodeFromString<SerializationFixture>(payload)
        assertEquals(original, restored)
    }

    @Test
    fun `polymorphic tag preserves sealed subtype`() {
        val a: SerializationFixture = SerializationFixture.TypeA(id = "x", count = 7)
        val b: SerializationFixture = SerializationFixture.TypeB(id = "y", name = "seven")

        val aJson = lapJson.encodeToString(a)
        val bJson = lapJson.encodeToString(b)

        assertTrue("TypeA payload should carry @type discriminator: $aJson", aJson.contains("\"@type\":\"a\""))
        assertTrue("TypeB payload should carry @type discriminator: $bJson", bJson.contains("\"@type\":\"b\""))

        assertTrue(lapJson.decodeFromString<SerializationFixture>(aJson) is SerializationFixture.TypeA)
        assertTrue(lapJson.decodeFromString<SerializationFixture>(bJson) is SerializationFixture.TypeB)
    }

    @Test
    fun `ignoreUnknownKeys tolerates forward-compat payloads`() {
        val withExtra = """{"@type":"a","id":"abc","count":1,"futureField":"yo"}"""
        val decoded = lapJson.decodeFromString<SerializationFixture>(withExtra)
        assertEquals(SerializationFixture.TypeA(id = "abc", count = 1), decoded)
    }
}
