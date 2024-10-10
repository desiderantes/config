/**
 * Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl


import com.desiderantes.moshi.ast.*
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.typesafe.config.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger

class JsonTest : TestUtils() {

	fun parse(s: String): ConfigValue {
		val options =
			ConfigParseOptions.defaults().setOriginDescription("test json string").setSyntax(ConfigSyntax.JSON)
		return Parseable.newString(s, options).parseValue()
	}

	private fun parseAsConf(s: String): ConfigValue {
		val options =
			ConfigParseOptions.defaults().setOriginDescription("test conf string").setSyntax(ConfigSyntax.CONF)
		return Parseable.newString(s, options).parseValue()
	}

	@Test
	@Disabled
	fun invalidJsonThrows() {
		var tested = 0
		// be sure Moshi throws on the string
		val generated = whitespaceVariations(invalidJson, false)

		for (invalid in generated) {
			if (invalid.moshiBehaviorUnexpected) {
				// moshi unexpectedly doesn't throw, confirm that
				addOffendingJsonToException("moshi-nonthrowing", invalid.test) {
					assertDoesNotThrow({
						fromJsonWithMoshiParser(invalid.test)
					}, "Did throw on position $tested by $invalid")
					tested += 1
				}
			} else {
				addOffendingJsonToException("moshi", invalid.test) {
					assertThrows(ConfigException::class.java, {
						fromJsonWithMoshiParser(invalid.test)
					}, "Did not throw on position $tested by $invalid")
					tested += 1
				}
			}

		}

		assertTrue(tested > 100) // just checking we ran a bunch of tests
		tested = 0

		// be sure we also throw
		for (invalid in whitespaceVariations(invalidJson, false)) {
			addOffendingJsonToException("config", invalid.test) {
				assertThrows(ConfigException::class.java) {
					parse(invalid.test)
				}
				tested += 1
			}
		}

		assertTrue(tested > 100)
	}

	@Test
	fun validJsonWorks() {
		var tested = 0

		// be sure we do the same thing as Moshi when we build our JSON "DOM"
		for (valid in whitespaceVariations(validJson, validInMoshi = true)) {
			val moshiAST = if (valid.moshiBehaviorUnexpected) {
				SimpleConfigObject.empty()
			} else {
				addOffendingJsonToException("moshi", valid.test) {
					fromJsonWithMoshiParser(valid.test)
				}
			}
			val ourAST = addOffendingJsonToException("config-json", valid.test) {
				parse(valid.test)
			}
			val ourConfAST = addOffendingJsonToException("config-conf", valid.test) {
				parseAsConf(valid.test)
			}
			if (valid.moshiBehaviorUnexpected) {
				// ignore this for now
			} else {
				addOffendingJsonToException("config", valid.test) {
					assertEquals(moshiAST, ourAST)
				}
			}

			// check that our parser gives the same result in JSON mode and ".conf" mode.
			// i.e. this tests that ".conf" format is a superset of JSON.
			addOffendingJsonToException("config", valid.test) {
				assertEquals(ourAST, ourConfAST)
			}

			tested += 1
		}

		assertTrue(tested > 100) // just verify we ran a lot of tests
	}

	@Test
	fun renderingJsonStrings() {
		fun r(s: String) = ConfigImplUtil.renderJsonString(s)

		assertEquals(""""abcdefg"""", r("""abcdefg"""))
		assertEquals(""""\" \\ \n \b \f \r \t"""", r("\" \\ \n \b \u000c \r \t"))
		// control characters are escaped. Remember that Unicode escapes
		// are weird and happen on the source file before doing other processing.
		assertEquals("\"\\" + "u001f\"", r("\u001f"))
	}

	// parse a string using Moshi's AST. We then test by ensuring we have the same results as
	// moshi for a variety of JSON strings.

	private fun toMoshi(value: ConfigValue): JValue<*> {
		return when (value) {
			is ConfigObject -> JObject(value.map { (k, va) -> JField(k, toMoshi(va)) })
			is ConfigList -> JArray(value.map { elem -> toMoshi(elem) })
			is ConfigBoolean -> if (value.unwrapped()) JTrue else JFalse
			is ConfigInt -> JInt(BigInteger.valueOf(value.unwrapped().toLong()))
			is ConfigLong -> JInt(BigInteger.valueOf(value.unwrapped()))
			is ConfigDouble -> JDouble(BigDecimal(value.unwrapped()))
			is ConfigString -> JString(value.unwrapped())
			is ConfigNull -> JNull
			else -> throw ConfigException.UnspecifiedProblem("Value outside of known hierarchy: $value")
		}
	}

	private fun fromMoshi(moshiValue: JValue<*>): AbstractConfigValue {

		return when (moshiValue) {
			is JObject -> SimpleConfigObject(
				fakeOrigin(),
				moshiValue.fields.associate { field -> field.name to fromMoshi(field.value) })

			is JArray -> SimpleConfigList(fakeOrigin(), moshiValue.values.map { fromMoshi(it) })

			is JInt -> try {
				intValue(moshiValue.value.intValueExact())
			} catch (e: ArithmeticException) {
				longValue(moshiValue.value.toLong())
			}

			is JBoolean -> ConfigBoolean(fakeOrigin(), moshiValue.value)

			is JDouble -> doubleValue(moshiValue.value.toDouble())

			is JString -> ConfigString.Quoted(fakeOrigin(), moshiValue.value)

			is JNull -> ConfigNull(fakeOrigin())

			JNothing -> throw ConfigException.UnspecifiedProblem("Moshi returned JNothing, probably an empty document (?)")
		}
	}

	// For string quoting, check behavior of escaping a random character instead of one on the list;
	// moshi seems to oddly treat that as a \ literal

	private fun <T> withMoshiExceptionsConverted(block: () -> T): T {
		return try {
			block()
		} catch (e: JsonEncodingException) {
			throw ConfigException.UnspecifiedParseError(SimpleConfigOrigin.newSimple("moshi parser"), e.message, e)
		} catch (e: JsonDataException) {
			throw ConfigException.UnspecifiedParseError(SimpleConfigOrigin.newSimple("moshi parser"), e.message, e)
		} catch (e: IOException) {
			throw ConfigException.UnspecifiedParseError(SimpleConfigOrigin.newSimple("moshi parser"), e.message, e)
		} catch (e: Exception) {
			throw ConfigException.UnspecifiedParseError(SimpleConfigOrigin.newSimple("moshi parser"), e.message, e)
		}
	}

	private fun fromJsonWithMoshiParser(json: String): ConfigValue {
		return withMoshiExceptionsConverted {
			val adapter = AstAdapter()
			val jValue = adapter.fromJson(json)
			fromMoshi(jValue!!)
		}
	}
}
