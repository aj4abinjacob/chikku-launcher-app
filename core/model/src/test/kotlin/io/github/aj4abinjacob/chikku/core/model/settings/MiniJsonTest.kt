package io.github.aj4abinjacob.chikku.core.model.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class MiniJsonTest {

    @Test
    fun roundTripsNestedValuesAndEscapes() {
        val value = linkedMapOf(
            "s" to "quote\" backslash\\ newline\n tab\t unicode é \u0001",
            "n" to 42L,
            "d" to 1.5,
            "neg" to -7L,
            "b" to true,
            "z" to null,
            "list" to listOf(1L, "two", listOf<Any?>(), mapOf<String, Any?>()),
        )
        assertThat(MiniJson.parse(MiniJson.stringify(value))).isEqualTo(value)
    }

    @Test
    fun parsesStandardEscapesAndExponents() {
        assertThat(MiniJson.parse("\"\\u0041\\/\"")).isEqualTo("A/")
        assertThat(MiniJson.parse(" 1e3 ")).isEqualTo(1000.0)
        assertThat(MiniJson.parse("[ ]")).isEqualTo(emptyList<Any?>())
    }

    @Test
    fun rejectsMalformedAndOverlyDeepInput() {
        listOf("", "{", "[1,]", "{\"a\" 1}", "tru", "\"open", "1 2", "{'a':1}").forEach { bad ->
            assertThrows(MiniJson.ParseException::class.java) { MiniJson.parse(bad) }
        }
        assertThrows(MiniJson.ParseException::class.java) { MiniJson.parse("[".repeat(500) + "]".repeat(500)) }
    }
}
