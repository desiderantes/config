package com.typesafe.config.impl

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.InputStreamReader

class ParseableReaderTest : TestUtils() {

	@Test
	fun parse(): Unit {
		val filename = "/test01.properties"
		val configInput = InputStreamReader(this::class.java.getResourceAsStream(filename)!!)
		val config = ConfigFactory.parseReader(
			configInput, ConfigParseOptions.defaults()
				.setSyntaxFromFilename(filename)
		)
		assertEquals("hello^^", config.getString("fromProps.specialChars"))
	}

	@Test
	fun parseIncorrectFormat(): Unit {
		val filename = "/test01.properties"
		val configInput = InputStreamReader(this::class.java.getResourceAsStream(filename)!!)
		val e = assertThrows(ConfigException.Parse::class.java) {
			ConfigFactory.parseReader(configInput)
		}
		assertTrue(e.message!!.contains("Expecting end of input or a comma, got '^'"))
	}
}
