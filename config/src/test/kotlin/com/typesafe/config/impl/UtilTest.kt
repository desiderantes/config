/**
 * Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import com.typesafe.config.ConfigSyntax
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UtilTest : TestUtils() {
	private val supplementaryChars by lazy {
		val sb = StringBuilder()
		val codepoints = listOf(
			0x2070E, 0x20731, 0x20779, 0x20C53, 0x20C78,
			0x20C96, 0x20CCF, 0x20CD5, 0x20D15, 0x20D7C
		)
		for (c in codepoints) {
			sb.appendCodePoint(c)
		}
		assertTrue(sb.length > codepoints.size)
		sb.toString()
	}
	val lotsOfStrings: List<String> = (invalidJson + validConf).map { it.test }

	@Test
	fun unicodeTrimSupplementaryChars() {
		assertEquals("", ConfigImplUtil.unicodeTrim(""))
		assertEquals("a", ConfigImplUtil.unicodeTrim("a"))
		assertEquals("abc", ConfigImplUtil.unicodeTrim("abc"))
		assertEquals("", ConfigImplUtil.unicodeTrim("   \n   \n  \u00A0 "))
		assertEquals(supplementaryChars, ConfigImplUtil.unicodeTrim(supplementaryChars))

		val s = " \u00A0 \n  $supplementaryChars  \n  \u00A0 "
		val asciiTrimmed = s.trim()
		val unitrimmed = ConfigImplUtil.unicodeTrim(s)

		//Kotlin knows how to trim
		assertTrue(asciiTrimmed == unitrimmed)
		assertEquals(supplementaryChars, unitrimmed)
	}

	@Test
	fun definitionOfWhitespace() {
		assertTrue(ConfigImplUtil.isWhitespace(' '.code))
		assertTrue(ConfigImplUtil.isWhitespace('\n'.code))
		// these three are nonbreaking spaces
		assertTrue(ConfigImplUtil.isWhitespace('\u00A0'.code))
		assertTrue(ConfigImplUtil.isWhitespace('\u2007'.code))
		assertTrue(ConfigImplUtil.isWhitespace('\u202F'.code))
		// vertical tab, a weird one
		assertTrue(ConfigImplUtil.isWhitespace('\u000B'.code))
		// file separator, another weird one
		assertTrue(ConfigImplUtil.isWhitespace('\u001C'.code))
	}

	@Test
	fun equalsThatHandlesNull() {
		assertTrue(ConfigImplUtil.equalsHandlingNull(null, null))
		assertFalse(ConfigImplUtil.equalsHandlingNull(Object(), null))
		assertFalse(ConfigImplUtil.equalsHandlingNull(null, Object()))
		assertTrue(ConfigImplUtil.equalsHandlingNull("", ""))
	}

	@Test
	fun renderJsonString() {
		for (s in lotsOfStrings) {
			roundtripJson(s)
		}
	}

	@Test
	fun renderUnquotedIfPossible() {
		for (s in lotsOfStrings) {
			roundtripUnquoted(s)
		}
	}

	@Test
	fun syntaxFromExtensionConf(): Unit {
		assertEquals(ConfigSyntax.CONF, ConfigImplUtil.syntaxFromExtension("application.conf"))
	}

	@Test
	fun syntaxFromExtensionJson(): Unit {
		assertEquals(ConfigSyntax.JSON, ConfigImplUtil.syntaxFromExtension("application.json"))
	}

	@Test
	fun syntaxFromExtensionProperties(): Unit {
		assertEquals(ConfigSyntax.PROPERTIES, ConfigImplUtil.syntaxFromExtension("application.properties"))
	}

	@Test
	fun syntaxFromExtensionUnknown(): Unit {
		assertNull(ConfigImplUtil.syntaxFromExtension("application.exe"))
	}

	@Test
	fun syntaxFromExtensionNull(): Unit {
		assertNull(ConfigImplUtil.syntaxFromExtension(null))
	}

	private fun roundtripJson(s: String) {
		val rendered = ConfigImplUtil.renderJsonString(s)
		val parsed = parseConfig("{ foo: " + rendered + "}").getString("foo")
		assertTrue(
			s == parsed,
			"String round-tripped through maybe-unquoted escaping '" + s + "' " + s.length +
					" rendering '" + rendered + "' " + rendered.length +
					" parsed '" + parsed + "' " + parsed.length
		)
	}

	private fun roundtripUnquoted(s: String) {
		val rendered = ConfigImplUtil.renderStringUnquotedIfPossible(s)
		val parsed = parseConfig("{ foo: " + rendered + "}").getString("foo")
		assertTrue(
			s == parsed,
			"String round-tripped through maybe-unquoted escaping '" + s + "' " + s.length +
					" rendering '" + rendered + "' " + rendered.length +
					" parsed '" + parsed + "' " + parsed.length
		)
	}
}
