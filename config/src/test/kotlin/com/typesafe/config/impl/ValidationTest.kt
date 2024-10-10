/**
 * Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ValidationTest : TestUtils() {

	@Test
	fun validation() {
		val reference = ConfigFactory.parseFile(resourceFile("validate-reference.conf"), ConfigParseOptions.defaults())
		val conf = ConfigFactory.parseFile(resourceFile("validate-invalid.conf"), ConfigParseOptions.defaults())
		val e = assertThrows(ConfigException.ValidationFailed::class.java) {
			conf.checkValid(reference)
		}

		val expecteds = listOf(
			Missing("willBeMissing", 1, "number"),
			WrongType("int3", 7, "number", "object"),
			WrongType("float2", 9, "number", "boolean"),
			WrongType("float3", 10, "number", "list"),
			WrongType("bool1", 11, "boolean", "number"),
			WrongType("bool3", 13, "boolean", "object"),
			Missing("object1.a", 17, "string"),
			WrongType("object2", 18, "object", "list"),
			WrongType("object3", 19, "object", "number"),
			WrongElementType("array3", 22, "boolean", "object"),
			WrongElementType("array4", 23, "object", "number"),
			WrongType("array5", 24, "list", "number"),
			WrongType("a.b.c.d.e.f.g", 28, "boolean", "number"),
			Missing("a.b.c.d.e.f.j", 28, "boolean"),
			WrongType("a.b.c.d.e.f.i", 30, "boolean", "list")
		)

		checkValidationException(e, expecteds)
	}

	@Test
	fun validationWithRoot() {
		val objectWithB = parseObject("""{ b : c }""")
		val reference = ConfigFactory.parseFile(
			resourceFile("validate-reference.conf"),
			ConfigParseOptions.defaults()
		).withFallback(objectWithB)
		val conf = ConfigFactory.parseFile(resourceFile("validate-invalid.conf"), ConfigParseOptions.defaults())
		val e = assertThrows(ConfigException.ValidationFailed::class.java) {
			conf.checkValid(reference, "a", "b")
		}

		val expecteds = listOf(
			Missing("b", 1, "string"),
			WrongType("a.b.c.d.e.f.g", 28, "boolean", "number"),
			Missing("a.b.c.d.e.f.j", 28, "boolean"),
			WrongType("a.b.c.d.e.f.i", 30, "boolean", "list")
		)

		checkValidationException(e, expecteds)
	}

	@Test
	fun validationCatchesUnresolved() {
		val reference = parseConfig("""{ a : 2 }""")
		val conf = parseConfig("{ b : \${c}, c : 42 }")
		val e = assertThrows(ConfigException.NotResolved::class.java) {
			conf.checkValid(reference)
		}
		assertTrue(
			e.message!!.contains("resolve"),
			"expected different message, got: " + e.message
		)
	}

	@Test
	fun validationCatchesListOverriddenWithNumber() {
		val reference = parseConfig("""{ a : [{},{},{}] }""")
		val conf = parseConfig("""{ a : 42 }""")
		val e = assertThrows(ConfigException.ValidationFailed::class.java) {
			conf.checkValid(reference)
		}

		val expecteds = listOf(WrongType("a", 1, "list", "number"))

		checkValidationException(e, expecteds)
	}

	@Test
	fun validationCatchesListOverriddenWithDifferentList() {
		val reference = parseConfig("""{ a : [true,false,false] }""")
		val conf = parseConfig("""{ a : [42,43] }""")
		val e = assertThrows(ConfigException.ValidationFailed::class.java) {
			conf.checkValid(reference)
		}

		val expecteds = listOf(WrongElementType("a", 1, "boolean", "number"))

		checkValidationException(e, expecteds)
	}

	@Test
	fun validationFailedSerializable(): Unit {
		// Reusing a previous test case to generate an error
		val reference = parseConfig("""{ a : [{},{},{}] }""")
		val conf = parseConfig("""{ a : 42 }""")
		val e = assertThrows(ConfigException.ValidationFailed::class.java) {
			conf.checkValid(reference)
		}

		val expecteds = listOf(WrongType("a", 1, "list", "number"))

		val actual = checkSerializableNoMeaningfulEquals(e)
		checkValidationException(actual, expecteds)
	}

	@Test
	fun validationAllowsListOverriddenWithSameTypeList() {
		val reference = parseConfig("""{ a : [1,2,3] }""")
		val conf = parseConfig("""{ a : [4,5] }""")
		conf.checkValid(reference)
	}

	@Test
	fun validationCatchesListOverriddenWithNoIndexesObject() {
		val reference = parseConfig("""{ a : [1,2,3] }""")
		val conf = parseConfig("""{ a : { notANumber: foo } }""")
		val e = assertThrows(ConfigException.ValidationFailed::class.java) {
			conf.checkValid(reference)
		}

		val expecteds = listOf(WrongType("a", 1, "list", "object"))

		checkValidationException(e, expecteds)
	}

	@Test
	fun validationAllowsListOverriddenWithIndexedObject() {
		val reference = parseConfig("""{ a : [a,b,c] }""")
		val conf = parseConfig("""{ a : { "0" : x, "1" : y } }""")
		conf.checkValid(reference)
		assertEquals(
			listOf("x", "y"),
			conf.getStringList("a"), "got the sequence from overriding list with indexed object"
		)
	}
}
