package com.lap.mobile.capture.ble

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

private const val PARSER_FUZZ_SEED: Long = 0xCAFEBABEL
private const val FRAMER_FUZZ_SEED: Long = 0xDEADBEEFL
private const val PARSER_ITERATIONS: Int = 5_000
private const val FRAMER_ITERATIONS: Int = 200
private const val MAX_PARSER_PAYLOAD: Int = 256
private const val MAX_FRAMER_STREAM: Int = 4_096

/**
 * Deterministic fuzz of every UBX class parser plus the streaming
 * framer (T127). Goal: no parser ever throws on arbitrary bytes —
 * length checks must trip first, sign-extension and offset arithmetic
 * must stay in-range. Failure mode would be a `UbxFramer.frames` flow
 * crashing the BLE collector mid-session, which would silently end
 * a recording.
 *
 * Seeded RNG so any failure is reproducible — the test prints the
 * iteration number + payload hex on failure.
 */
class UbxParserFuzzTest {
    @Test
    fun every_parser_returns_null_or_value_without_throwing_on_random_bytes() {
        val random = Random(PARSER_FUZZ_SEED)
        repeat(PARSER_ITERATIONS) { iteration ->
            val size = random.nextInt(MAX_PARSER_PAYLOAD + 1)
            val payload = ByteArray(size).also { random.nextBytes(it) }
            tryEveryParser(payload, iteration)
        }
    }

    @Test
    fun framer_drains_random_byte_stream_without_throwing() = runTest {
        val random = Random(FRAMER_FUZZ_SEED)
        val framer = UbxFramer()
        repeat(FRAMER_ITERATIONS) { iteration ->
            val streamSize = random.nextInt(MAX_FRAMER_STREAM + 1)
            val chunk = ByteArray(streamSize).also { random.nextBytes(it) }
            try {
                framer.frames(flowOf(chunk)).toList()
            } catch (t: Throwable) {
                fail(
                    "framer threw on random stream " +
                        "(seed=$FRAMER_FUZZ_SEED, iter=$iteration, size=$streamSize): $t",
                )
            }
        }
    }

    @Suppress("LongMethod", "TooGenericExceptionCaught")
    private fun tryEveryParser(payload: ByteArray, iteration: Int) {
        val parserNames = listOf(
            "NavPvtParser" to { NavPvtParser.parse(payload) },
            "NavStatusParser" to { NavStatusParser.parse(payload) },
            "NavPvatParser" to { NavPvatParser.parse(payload) },
            "NavRelPosNedParser" to { NavRelPosNedParser.parse(payload) },
            "EsfRawParser" to { EsfRawParser.parse(payload) },
            "EsfAlgParser" to { EsfAlgParser.parse(payload) },
            "EsfMeasParser" to { EsfMeasParser.parse(payload) },
            "EsfStatusParser" to { EsfStatusParser.parse(payload) },
            "SecUniqIdParser" to { SecUniqIdParser.parse(payload) },
            "MonVerParser" to { MonVerParser.parse(payload) },
        )
        for ((name, parse) in parserNames) {
            try {
                parse()
            } catch (t: Throwable) {
                fail(
                    "$name threw on random payload " +
                        "(seed=$PARSER_FUZZ_SEED, iter=$iteration, " +
                        "size=${payload.size}, hex=${payload.toHex()}): $t",
                )
            }
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }
}
