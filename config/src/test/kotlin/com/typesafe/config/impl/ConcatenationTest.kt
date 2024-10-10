/**
 * Copyright (C) 2012 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import com.typesafe.config.ConfigException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConcatenationTest : TestUtils() {

	@Test
	fun noSubstitutionsStringConcat() {
		val conf = parseConfig(""" a :  true "xyz" 123 foo  """).resolve()
		assertEquals("true xyz 123 foo", conf.getString("a"))
	}

	@Test
	fun trivialStringConcat() {
		val conf = parseConfig(" a : \${x}foo, x = 1 ").resolve()
		assertEquals("1foo", conf.getString("a"))
	}

	@Test
	fun twoSubstitutionsStringConcat() {
		val conf = parseConfig(" a : \${x}foo\${x}, x = 1 ").resolve()
		assertEquals("1foo1", conf.getString("a"))
	}

	@Test
	fun stringConcatCannotSpanLines() {
		val e = assertThrows(ConfigException.Parse::class.java) {
			parseConfig(
				" a : \${x}\n                foo, x = 1 "
			)
		}
		assertTrue(
			e.message!!.contains("not be followed") &&
					e.message!!.contains("','"),
			"wrong exception: " + e.message
		)
	}

	@Test
	fun noObjectsInStringConcat() {
		val e = assertThrows(ConfigException.WrongType::class.java) {
			parseConfig(""" a : abc { x : y } """)
		}
		assertTrue(
			e.message!!.contains("Cannot concatenate") &&
					e.message!!.contains("abc") &&
					e.message!!.contains("""{"x":"y"}"""),
			"wrong exception: " + e.message
		)
	}

	@Test
	fun noObjectConcatWithNull() {
		val e = assertThrows(ConfigException.WrongType::class.java) {
			parseConfig(""" a : null { x : y } """)
		}
		assertTrue(
			e.message!!.contains("Cannot concatenate") &&
					e.message!!.contains("null") &&
					e.message!!.contains("""{"x":"y"}"""),
			"wrong exception: " + e.message
		)
	}

	@Test
	fun noArraysInStringConcat() {
		val e = assertThrows(ConfigException.WrongType::class.java) {
			parseConfig(""" a : abc [1, 2] """)
		}
		assertTrue(
			e.message!!.contains("Cannot concatenate") &&
					e.message!!.contains("abc") &&
					e.message!!.contains("[1,2]"),
			"wrong exception: " + e.message
		)
	}

	@Test
	fun noObjectsSubstitutedInStringConcat() {
		val e = assertThrows(ConfigException.WrongType::class.java) {
			parseConfig(" a : abc \${x}, x : { y : z } ").resolve()
		}
		assertTrue(
			e.message!!.contains("Cannot concatenate") &&
					e.message!!.contains("abc"),
			"wrong exception: " + e.message
		)
	}

	@Test
	fun noArraysSubstitutedInStringConcat() {
		val e = assertThrows(ConfigException.WrongType::class.java) {
			parseConfig(" a : abc \${x}, x : [1,2] ").resolve()
		}
		assertTrue(
			e.message!!.contains("Cannot concatenate") &&
					e.message!!.contains("abc"),
			"wrong exception: " + e.message
		)
	}

	@Test
	fun noSubstitutionsListConcat() {
		val conf = parseConfig(""" a :  [1,2] [3,4]  """)
		assertEquals(listOf(1, 2, 3, 4), conf.getList("a").unwrapped())
	}

	@Test
	fun listConcatWithSubstitutions() {
		val conf = parseConfig(" a :  \${x} [3,4] \${y}, x : [1,2], y : [5,6]  ").resolve()
		assertEquals(listOf(1, 2, 3, 4, 5, 6), conf.getList("a").unwrapped())
	}

	@Test
	fun listConcatSelfReferential() {
		val conf = parseConfig(" a : [1, 2], a : \${a} [3,4], a : \${a} [5,6]  ").resolve()
		assertEquals(listOf(1, 2, 3, 4, 5, 6), conf.getList("a").unwrapped())
	}

	@Test
	fun noSubstitutionsListConcatCannotSpanLines() {
		val e = assertThrows(ConfigException.Parse::class.java) {
			parseConfig(" a :  [1,2]\n                [3,4]  ")
		}
		assertTrue(
			e.message!!.contains("expecting") &&
					e.message!!.contains("'['"),
			"wrong exception: " + e.message
		)
	}

	@Test
	fun listConcatCanSpanLinesInsideBrackets() {
		val conf = parseConfig(
			""" a :  [1,2
               ] [3,4]  """
		)
		assertEquals(listOf(1, 2, 3, 4), conf.getList("a").unwrapped())
	}

	@Test
	fun noSubstitutionsObjectConcat() {
		val conf = parseConfig(""" a : { b : c } { x : y }  """)
		assertEquals(mapOf("b" to "c", "x" to "y"), conf.getObject("a").unwrapped())
	}

	@Test
	fun objectConcatMergeOrder() {
		val conf = parseConfig(""" a : { b : 1 } { b : 2 } { b : 3 } { b : 4 } """)
		assertEquals(4, conf.getInt("a.b"))
	}

	@Test
	fun objectConcatWithSubstitutions() {
		val conf = parseConfig(" a : \${x} { b : 1 } \${y}, x : { a : 0 }, y : { c : 2 } ").resolve()
		assertEquals(mapOf("a" to 0, "b" to 1, "c" to 2), conf.getObject("a").unwrapped())
	}

	@Test
	fun objectConcatSelfReferential() {
		val conf = parseConfig(" a : { a : 0 }, a : \${a} { b : 1 }, a : \${a} { c : 2 } ").resolve()
		assertEquals(mapOf("a" to 0, "b" to 1, "c" to 2), conf.getObject("a").unwrapped())
	}

	@Test
	fun objectConcatSelfReferentialOverride() {
		val conf = parseConfig(" a : { b : 3 }, a : { b : 2 } \${a} ").resolve()
		assertEquals(mapOf("b" to 3), conf.getObject("a").unwrapped())
	}

	@Test
	fun noSubstitutionsObjectConcatCannotSpanLines() {
		val e = assertThrows(ConfigException.Parse::class.java) {
			parseConfig(
				""" a :  { b : c }
                    { x : y }"""
			)
		}
		assertTrue(
			e.message!!.contains("expecting") &&
					e.message!!.contains("'{'"),
			"wrong exception: " + e.message
		)
	}

	@Test
	fun objectConcatCanSpanLinesInsideBraces() {
		val conf = parseConfig(
			""" a :  { b : c
    } { x : y }  """
		)
		assertEquals(mapOf("b" to "c", "x" to "y"), conf.getObject("a").unwrapped())
	}

	@Test
	fun stringConcatInsideArrayValue() {
		val conf = parseConfig(""" a : [ foo bar 10 ] """)
		assertEquals(listOf("foo bar 10"), conf.getStringList("a"))
	}

	@Test
	fun stringNonConcatInsideArrayValue() {
		val conf = parseConfig(
			""" a : [ foo
                bar
                10 ] """
		)
		assertEquals(listOf("foo", "bar", "10"), conf.getStringList("a"))
	}

	@Test
	fun objectConcatInsideArrayValue() {
		val conf = parseConfig(""" a : [ { b : c } { x : y } ] """)
		assertEquals(listOf(mapOf("b" to "c", "x" to "y")), conf.getObjectList("a").map { it.unwrapped() })
	}

	@Test
	fun objectNonConcatInsideArrayValue() {
		val conf = parseConfig(
			""" a : [ { b : c }
                { x : y } ] """
		)
		assertEquals(listOf(mapOf("b" to "c"), mapOf("x" to "y")), conf.getObjectList("a").map { it.unwrapped() })
	}

	@Test
	fun listConcatInsideArrayValue() {
		val conf = parseConfig(""" a : [ [1, 2] [3, 4] ] """)
		assertEquals(
			listOf(listOf(1, 2, 3, 4)),
			// well that's a little silly
			conf.getList("a").unwrapped()
		)
	}

	@Test
	fun listNonConcatInsideArrayValue() {
		val conf = parseConfig(
			""" a : [ [1, 2]
                [3, 4] ] """
		)
		assertEquals(
			listOf(listOf(1, 2), listOf(3, 4)),
			// well that's a little silly
			conf.getList("a").unwrapped()
		)
	}

	@Test
	fun stringConcatsAreKeys() {
		val conf = parseConfig(""" 123 foo : "value" """)
		assertEquals("value", conf.getString("123 foo"))
	}

	@Test
	fun objectsAreNotKeys() {

		val e = assertThrows(ConfigException.Parse::class.java) {
			parseConfig("""{ { a : 1 } : "value" }""")
		}
		assertTrue(
			e.message!!.contains("expecting a close parentheses") && e.message!!.contains("'{'"),
			"wrong exception: " + e.message
		)
	}

	@Test
	fun arraysAreNotKeys() {
		val e = assertThrows(ConfigException.Parse::class.java) {
			parseConfig("""{ [ "a" ] : "value" }""")
		}
		assertTrue(
			e.message!!.contains("expecting a close parentheses") && e.message!!.contains("'['"),
			"wrong exception: " + e.message
		)
	}

	@Test
	fun emptyArrayPlusEquals() {
		val conf = parseConfig(""" a = [], a += 2 """).resolve()
		assertEquals(listOf(2), conf.getIntList("a"))
	}

	@Test
	fun missingArrayPlusEquals() {
		val conf = parseConfig(""" a += 2 """).resolve()
		assertEquals(listOf(2), conf.getIntList("a"))
	}

	@Test
	fun shortArrayPlusEquals() {
		val conf = parseConfig(""" a = [1], a += 2 """).resolve()
		assertEquals(listOf(1, 2), conf.getIntList("a"))
	}

	@Test
	fun numberPlusEquals() {
		val e = assertThrows(ConfigException.WrongType::class.java) {
			parseConfig(""" a = 10, a += 2 """).resolve()
		}
		assertTrue(
			e.message!!.contains("Cannot concatenate") &&
					e.message!!.contains("10") &&
					e.message!!.contains("[2]"),
			"wrong exception: " + e.message
		)
	}

	@Test
	fun stringPlusEquals() {
		val e = assertThrows(ConfigException.WrongType::class.java) {
			parseConfig(""" a = abc, a += 2 """).resolve()
		}
		assertTrue(
			e.message!!.contains("Cannot concatenate") &&
					e.message!!.contains("abc") &&
					e.message!!.contains("[2]"),
			"wrong exception: " + e.message
		)
	}

	@Test
	fun objectPlusEquals() {
		val e = assertThrows(ConfigException.WrongType::class.java) {
			parseConfig(""" a = { x : y }, a += 2 """).resolve()
		}
		assertTrue(
			e.message!!.contains("Cannot concatenate") &&
					e.message!!.contains("\"x\":\"y\"") &&
					e.message!!.contains("[2]"),
			"wrong exception: " + e.message
		)
	}

	@Test
	fun plusEqualsNestedPath() {
		val conf = parseConfig(""" a.b.c = [1], a.b.c += 2 """).resolve()
		assertEquals(listOf(1, 2), conf.getIntList("a.b.c"))
	}

	@Test
	fun plusEqualsNestedObjects() {
		val conf = parseConfig(""" a : { b : { c : [1] } }, a : { b : { c += 2 } }""").resolve()
		assertEquals(listOf(1, 2), conf.getIntList("a.b.c"))
	}

	@Test
	fun plusEqualsSingleNestedObject() {
		val conf = parseConfig(""" a : { b : { c : [1], c += 2 } }""").resolve()
		assertEquals(listOf(1, 2), conf.getIntList("a.b.c"))
	}

	@Test
	fun substitutionPlusEqualsSubstitution() {
		val conf = parseConfig(" a = \${x}, a += \${y}, x = [1], y = 2 ").resolve()
		assertEquals(listOf(1, 2), conf.getIntList("a"))
	}

	@Test
	fun plusEqualsMultipleTimes() {
		val conf = parseConfig(""" a += 1, a += 2, a += 3 """).resolve()
		assertEquals(listOf(1, 2, 3), conf.getIntList("a"))
	}

	@Test
	fun plusEqualsMultipleTimesNested() {
		val conf = parseConfig(""" x { a += 1, a += 2, a += 3 } """).resolve()
		assertEquals(listOf(1, 2, 3), conf.getIntList("x.a"))
	}

	@Test
	fun plusEqualsAnObjectMultipleTimes() {
		val conf = parseConfig(""" a += { b: 1 }, a += { b: 2 }, a += { b: 3 } """).resolve()
		assertEquals(listOf(1, 2, 3), conf.getObjectList("a").map { it.toConfig().getInt("b") })
	}

	@Test
	fun plusEqualsAnObjectMultipleTimesNested() {
		val conf = parseConfig(""" x { a += { b: 1 }, a += { b: 2 }, a += { b: 3 } } """).resolve()
		assertEquals(listOf(1, 2, 3), conf.getObjectList("x.a").map { it.toConfig().getInt("b") })
	}

	// We would ideally make this case NOT throw an exception but we need to do some work
	// to get there, see https://github.com/lightbend/config/issues/160
	@Test
	fun plusEqualsMultipleTimesNestedInArray() {
		val e = assertThrows(ConfigException.Parse::class.java) {
			val conf = parseConfig("""x = [ { a += 1, a += 2, a += 3 } ] """).resolve()
			assertEquals(listOf(1, 2, 3), conf.getObjectList("x")[0].toConfig().getIntList("a"))
		}
		assertTrue(e.message!!.contains("limitation"))
	}

	// We would ideally make this case NOT throw an exception but we need to do some work
	// to get there, see https://github.com/lightbend/config/issues/160
	@Test
	fun plusEqualsMultipleTimesNestedInPlusEquals() {
		val e = assertThrows(ConfigException.Parse::class.java) {
			val conf = parseConfig("""x += { a += 1, a += 2, a += 3 } """).resolve()
			assertEquals(listOf(1, 2, 3), conf.getObjectList("x")[0].toConfig().getIntList("a"))
		}
		assertTrue(e.message!!.contains("limitation"))
	}

	// from https://github.com/lightbend/config/issues/177
	@Test
	fun arrayConcatenationInDoubleNestedDelayedMerge() {
		val unresolved = parseConfig("d { x = [] }, c : \${d}, c { x += 1, x += 2 }")
		val conf = unresolved.resolve()
		assertEquals(listOf(1, 2), conf.getIntList("c.x"))
	}

	// from https://github.com/lightbend/config/issues/177
	@Test
	fun arrayConcatenationAsPartOfDelayedMerge() {
		val unresolved = parseConfig(" c { x: [], x : \${c.x}[1], x : \${c.x}[2] }")
		val conf = unresolved.resolve()
		assertEquals(listOf(1, 2), conf.getIntList("c.x"))
	}

	// from https://github.com/lightbend/config/issues/177
	@Test
	fun arrayConcatenationInDoubleNestedDelayedMerge2() {
		val unresolved = parseConfig("d { x = [] }, c : \${d}, c { x : \${c.x}[1], x : \${c.x}[2] }")
		val conf = unresolved.resolve()
		assertEquals(listOf(1, 2), conf.getIntList("c.x"))
	}

	// from https://github.com/lightbend/config/issues/177
	@Test
	fun arrayConcatenationInTripleNestedDelayedMerge() {
		val unresolved =
			parseConfig("{ r: { d.x=[] }, q: \${r}, q : { d { x = [] }, c : \${q.d}, c { x : \${q.c.x}[1], x : \${q.c.x}[2] } } }")
		val conf = unresolved.resolve()
		assertEquals(listOf(1, 2), conf.getIntList("q.c.x"))
	}

	@Test
	fun concatUndefinedSubstitutionWithString() {
		val conf = parseConfig("a = foo\${?bar}").resolve()
		assertEquals("foo", conf.getString("a"))
	}

	@Test
	fun concatDefinedOptionalSubstitutionWithString() {
		val conf = parseConfig("bar=bar, a = foo\${?bar}").resolve()
		assertEquals("foobar", conf.getString("a"))
	}

	@Test
	fun concatUndefinedSubstitutionWithArray() {
		val conf = parseConfig("a = [1] \${?bar}").resolve()
		assertEquals(listOf(1), conf.getIntList("a"))
	}

	@Test
	fun concatDefinedOptionalSubstitutionWithArray() {
		val conf = parseConfig("bar=[2], a = [1] \${?bar}").resolve()
		assertEquals(listOf(1, 2), conf.getIntList("a"))
	}

	@Test
	fun concatUndefinedSubstitutionWithObject() {
		val conf = parseConfig("a = { x : \"foo\" } \${?bar}").resolve()
		assertEquals("foo", conf.getString("a.x"))
	}

	@Test
	fun concatDefinedOptionalSubstitutionWithObject() {
		val conf = parseConfig("bar={ y : 42 }, a = { x : \"foo\" } \${?bar}").resolve()
		assertEquals("foo", conf.getString("a.x"))
		assertEquals(42, conf.getInt("a.y"))
	}

	@Test
	fun concatTwoUndefinedSubstitutions() {
		val conf = parseConfig("a = \${?foo}\${?bar}").resolve()
		assertFalse(conf.hasPath("a"), "no field 'a'")
	}

	@Test
	fun concatSeveralUndefinedSubstitutions() {
		val conf = parseConfig("a = \${?foo}\${?bar}\${?baz}\${?woooo}").resolve()
		assertFalse(conf.hasPath("a"), "no field 'a'")
	}

	@Test
	fun concatTwoUndefinedSubstitutionsWithASpace() {
		val conf = parseConfig("a = \${?foo} \${?bar}").resolve()
		assertEquals(" ", conf.getString("a"))
	}

	@Test
	fun concatTwoDefinedSubstitutionsWithASpace() {
		val conf = parseConfig("foo=abc, bar=def, a = \${foo} \${bar}").resolve()
		assertEquals("abc def", conf.getString("a"))
	}

	@Test
	fun concatTwoUndefinedSubstitutionsWithEmptyString() {
		val conf = parseConfig("a = \"\"\${?foo}\${?bar}").resolve()
		assertEquals("", conf.getString("a"))
	}

	@Test
	fun concatSubstitutionsThatAreObjectsWithNoSpace() {
		val conf = parseConfig("foo = { a : 1}, bar = { b : 2 }, x = \${foo}\${bar}").resolve()
		assertEquals(1, conf.getInt("x.a"))
		assertEquals(2, conf.getInt("x.b"))
	}

	// whitespace is insignificant if substitutions don't turn out to be a string
	@Test
	fun concatSubstitutionsThatAreObjectsWithSpace() {
		val conf = parseConfig("foo = { a : 1}, bar = { b : 2 }, x = \${foo} \${bar}").resolve()
		assertEquals(1, conf.getInt("x.a"))
		assertEquals(2, conf.getInt("x.b"))
	}

	// whitespace is insignificant if substitutions don't turn out to be a string
	@Test
	fun concatSubstitutionsThatAreListsWithSpace() {
		val conf = parseConfig("foo = [1], bar = [2], x = \${foo} \${bar}").resolve()
		assertEquals(listOf(1, 2), conf.getIntList("x"))
	}

	// but quoted whitespace should be an error
	@Test
	fun concatSubstitutionsThatAreObjectsWithQuotedSpace() {
		val e = assertThrows(ConfigException.WrongType::class.java) {
			parseConfig("foo = { a : 1}, bar = { b : 2 }, x = \${foo}\"  \"\${bar}").resolve()
		}
	}

	// but quoted whitespace should be an error
	@Test
	fun concatSubstitutionsThatAreListsWithQuotedSpace() {
		val e = assertThrows(ConfigException.WrongType::class.java) {
			parseConfig("foo = [1], bar = [2], x = \${foo}\"  \"\${bar}").resolve()
		}
	}
}
