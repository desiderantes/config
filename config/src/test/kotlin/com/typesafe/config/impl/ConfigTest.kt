/**
 * Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import com.typesafe.config.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class ConfigTest : TestUtils() {

	private val cycleObject = parseObject(
		"\n{\n    \"foo\" : \${bar},\n    \"bar\" : \${a.b.c},\n    \"a\" : { \"b\" : { \"c\" : \${foo} } }\n}\n"
	)


	private fun mergeUnresolved(vararg toMerge: AbstractConfigObject): AbstractConfigObject {
		return if (toMerge.isEmpty()) {
			SimpleConfigObject.empty()
		} else {
			toMerge.reduce { first, second -> first.withFallback(second) }
		}
	}

	private fun merge(vararg toMerge: AbstractConfigObject): AbstractConfigObject {
		val obj = mergeUnresolved(*toMerge)
		return resolveNoSystem(obj, obj) as AbstractConfigObject

	}

	@Test
	fun mergeTrivial() {
		val obj1 = parseObject("""{ "a" : 1 }""")
		val obj2 = parseObject("""{ "b" : 2 }""")
		val merged = merge(obj1, obj2).toConfig()

		assertEquals(1, merged.getInt("a"))
		assertEquals(2, merged.getInt("b"))
		assertEquals(2, merged.root().size)
	}

	@Test
	fun mergeEmpty() {
		val merged = merge().toConfig()

		assertEquals(0, merged.root().size)
	}

	@Test
	fun mergeOne() {
		val obj1 = parseObject("""{ "a" : 1 }""")
		val merged = merge(obj1).toConfig()

		assertEquals(1, merged.getInt("a"))
		assertEquals(1, merged.root().size)
	}

	@Test
	fun mergeOverride() {
		val obj1 = parseObject("""{ "a" : 1 }""")
		val obj2 = parseObject("""{ "a" : 2 }""")
		val merged = merge(obj1, obj2).toConfig()

		assertEquals(1, merged.getInt("a"))
		assertEquals(1, merged.root().size)

		val merged2 = merge(obj2, obj1).toConfig()

		assertEquals(2, merged2.getInt("a"))
		assertEquals(1, merged2.root().size)
	}

	@Test
	fun mergeN() {
		val obj1 = parseObject("""{ "a" : 1 }""")
		val obj2 = parseObject("""{ "b" : 2 }""")
		val obj3 = parseObject("""{ "c" : 3 }""")
		val obj4 = parseObject("""{ "d" : 4 }""")

		associativeMerge(listOf(obj1, obj2, obj3, obj4)) { merged ->
			assertEquals(1, merged.getInt("a"))
			assertEquals(2, merged.getInt("b"))
			assertEquals(3, merged.getInt("c"))
			assertEquals(4, merged.getInt("d"))
			assertEquals(4, merged.root().size)
		}
	}

	@Test
	fun mergeOverrideN() {
		val obj1 = parseObject("""{ "a" : 1 }""")
		val obj2 = parseObject("""{ "a" : 2 }""")
		val obj3 = parseObject("""{ "a" : 3 }""")
		val obj4 = parseObject("""{ "a" : 4 }""")
		associativeMerge(listOf(obj1, obj2, obj3, obj4)) { merged ->
			assertEquals(1, merged.getInt("a"))
			assertEquals(1, merged.root().size)
		}

		associativeMerge(listOf(obj4, obj3, obj2, obj1)) { merged2 ->
			assertEquals(4, merged2.getInt("a"))
			assertEquals(1, merged2.root().size)
		}
	}

	@Test
	fun mergeNested() {
		val obj1 = parseObject("""{ "root" : { "a" : 1, "z" : 101 } }""")
		val obj2 = parseObject("""{ "root" : { "b" : 2, "z" : 102 } }""")
		val merged = merge(obj1, obj2).toConfig()

		assertEquals(1, merged.getInt("root.a"))
		assertEquals(2, merged.getInt("root.b"))
		assertEquals(101, merged.getInt("root.z"))
		assertEquals(1, merged.root().size)
		assertEquals(3, merged.getConfig("root").root().size)
	}

	@Test
	fun mergeWithEmpty() {
		val obj1 = parseObject("""{ "a" : 1 }""")
		val obj2 = parseObject("""{ }""")
		val merged = merge(obj1, obj2).toConfig()

		assertEquals(1, merged.getInt("a"))
		assertEquals(1, merged.root().size)

		val merged2 = merge(obj2, obj1).toConfig()

		assertEquals(1, merged2.getInt("a"))
		assertEquals(1, merged2.root().size)
	}

	@Test
	fun mergeOverrideObjectAndPrimitive() {
		val obj1 = parseObject("""{ "a" : 1 }""")
		val obj2 = parseObject("""{ "a" : { "b" : 42 } }""")
		val merged = merge(obj1, obj2).toConfig()

		assertEquals(1, merged.getInt("a"))
		assertEquals(1, merged.root().size)

		val merged2 = merge(obj2, obj1).toConfig()

		assertEquals(42, merged2.getConfig("a").getInt("b"))
		assertEquals(42, merged2.getInt("a.b"))
		assertEquals(1, merged2.root().size)
		assertEquals(1, merged2.getObject("a").size)
	}

	@Test
	fun mergeOverrideObjectAndSubstitution() {
		val obj1 = parseObject("""{ "a" : 1 }""")
		val obj2 = parseObject("{ \"a\" : { \"b\" : \${c} }, \"c\" : 42 }")
		val merged = merge(obj1, obj2).toConfig()

		assertEquals(1, merged.getInt("a"))
		assertEquals(2, merged.root().size)

		val merged2 = merge(obj2, obj1).toConfig()

		assertEquals(42, merged2.getConfig("a").getInt("b"))
		assertEquals(42, merged2.getInt("a.b"))
		assertEquals(2, merged2.root().size)
		assertEquals(1, merged2.getObject("a").size)
	}

	@Test
	fun mergeObjectThenPrimitiveThenObject() {
		// the semantic here is that the primitive blocks the
		// object that occurs at lower priority. This is consistent
		// with duplicate keys in the same file.
		val obj1 = parseObject("""{ "a" : { "b" : 42 } }""")
		val obj2 = parseObject("""{ "a" : 2 }""")
		val obj3 = parseObject("""{ "a" : { "b" : 43, "c" : 44 } }""")

		associativeMerge(listOf(obj1, obj2, obj3)) { merged ->
			assertEquals(42, merged.getInt("a.b"))
			assertEquals(1, merged.root().size)
			assertEquals(1, merged.getObject("a").size)
		}

		associativeMerge(listOf(obj3, obj2, obj1)) { merged2 ->
			assertEquals(43, merged2.getInt("a.b"))
			assertEquals(44, merged2.getInt("a.c"))
			assertEquals(1, merged2.root().size)
			assertEquals(2, merged2.getObject("a").size)
		}
	}

	@Test
	fun mergeObjectThenSubstitutionThenObject() {
		// the semantic here is that the primitive blocks the
		// object that occurs at lower priority. This is consistent
		// with duplicate keys in the same file.
		val obj1 = parseObject("{ \"a\" : { \"b\" : \${f} } }")
		val obj2 = parseObject("{ \"a\" : 2 }")
		val obj3 = parseObject("{ \"a\" : { \"b\" : \${d}, \"c\" : \${e} }, \"d\" : 43, \"e\" : 44, \"f\" : 42 }")

		associativeMerge(listOf(obj1, obj2, obj3)) { unresolved ->
			val merged = resolveNoSystem(unresolved, unresolved)!!
			assertEquals(42, merged.getInt("a.b"))
			assertEquals(4, merged.root().size)
			assertEquals(1, merged.getObject("a").size)
		}

		associativeMerge(listOf(obj3, obj2, obj1)) { unresolved ->
			val merged2 = resolveNoSystem(unresolved, unresolved)!!
			assertEquals(43, merged2.getInt("a.b"))
			assertEquals(44, merged2.getInt("a.c"))
			assertEquals(4, merged2.root().size)
			assertEquals(2, merged2.getObject("a").size)
		}
	}

	@Test
	fun mergePrimitiveThenObjectThenPrimitive() {
		// the primitive should override the object
		val obj1 = parseObject("""{ "a" : 1 }""")
		val obj2 = parseObject("""{ "a" : { "b" : 42 } }""")
		val obj3 = parseObject("""{ "a" : 3 }""")

		associativeMerge(listOf(obj1, obj2, obj3)) { merged ->
			assertEquals(1, merged.getInt("a"))
			assertEquals(1, merged.root().size)
		}
	}

	@Test
	fun mergeSubstitutionThenObjectThenSubstitution() {
		// the substitution should override the object
		val obj1 = parseObject("{ \"a\" : \${b}, \"b\" : 1 }")
		val obj2 = parseObject("{ \"a\" : { \"b\" : 42 } }")
		val obj3 = parseObject("{ \"a\" : \${c}, \"c\" : 2 }")

		associativeMerge(listOf(obj1, obj2, obj3)) { merged ->
			val resolved = resolveNoSystem(merged, merged)!!

			assertEquals(1, resolved.getInt("a"))
			assertEquals(3, resolved.root().size)
		}
	}

	@Test
	fun mergeSubstitutedValues() {
		val obj1 = parseObject("{ \"a\" : { \"x\" : 1, \"z\" : 4 }, \"c\" : \${a} }")
		val obj2 = parseObject("{ \"b\" : { \"y\" : 2, \"z\" : 5 }, \"c\" : \${b} }")

		val resolved = merge(obj1, obj2).toConfig()

		assertEquals(3, resolved.getObject("c").size)
		assertEquals(1, resolved.getInt("c.x"))
		assertEquals(2, resolved.getInt("c.y"))
		assertEquals(4, resolved.getInt("c.z"))
	}

	@Test
	fun mergeObjectWithSubstituted() {
		val obj1 = parseObject("""{ "a" : { "x" : 1, "z" : 4 }, "c" : { "z" : 42 } }""")
		val obj2 = parseObject("{ \"b\" : { \"y\" : 2, \"z\" : 5 }, \"c\" : \${b} }")

		val resolved = merge(obj1, obj2).toConfig()

		assertEquals(2, resolved.getObject("c").size)
		assertEquals(2, resolved.getInt("c.y"))
		assertEquals(42, resolved.getInt("c.z"))

		val resolved2 = merge(obj2, obj1).toConfig()

		assertEquals(2, resolved2.getObject("c").size)
		assertEquals(2, resolved2.getInt("c.y"))
		assertEquals(5, resolved2.getInt("c.z"))
	}

	@Test
	fun mergeHidesCycles() {
		// the point here is that we should not try to evaluate a substitution
		// that's been overridden, and thus not end up with a cycle as long
		// as we override the problematic link in the cycle.
		val e = assertThrows(ConfigException.UnresolvedSubstitution::class.java) {
			val v = resolveNoSystem(subst("foo"), cycleObject)
		}
		assertTrue(e.message!!.contains("cycle"), "wrong exception: " + e.message)

		val fixUpCycle = parseObject(""" { "a" : { "b" : { "c" : 57 } } } """)
		val merged = mergeUnresolved(fixUpCycle, cycleObject)
		val v = resolveNoSystem(subst("foo"), merged)
		assertEquals(intValue(57), v)
	}

	@Test
	fun mergeWithObjectInFrontKeepsCycles() {
		// the point here is that if our eventual value will be an object, then
		// we have to evaluate the substitution to see if it's an object to merge,
		// so we don't avoid the cycle.
		val e = assertThrows(ConfigException.UnresolvedSubstitution::class.java) {
			val v = resolveNoSystem(subst("foo"), cycleObject)
		}
		assertTrue(e.message!!.contains("cycle"), "wrong exception: " + e.message)

		val fixUpCycle = parseObject(""" { "a" : { "b" : { "c" : { "q" : "u" } } } } """)
		val merged = mergeUnresolved(fixUpCycle, cycleObject)
		val e2 = assertThrows(ConfigException.UnresolvedSubstitution::class.java) {
			val v = resolveNoSystem(subst("foo"), merged)
		}
		// TODO: it would be nicer if the above threw BadValue with an
		// explanation about the cycle.
		//assertTrue(e2.getMessage().contains("cycle"))
	}

	@Test
	fun mergeSeriesOfSubstitutions() {
		val obj1 = parseObject("{ \"a\" : { \"x\" : 1, \"q\" : 4 }, \"j\" : \${a} }")
		val obj2 = parseObject("{ \"b\" : { \"y\" : 2, \"q\" : 5 }, \"j\" : \${b} }")
		val obj3 = parseObject("{ \"c\" : { \"z\" : 3, \"q\" : 6 }, \"j\" : \${c} }")

		associativeMerge(listOf(obj1, obj2, obj3)) { merged ->
			val resolved = resolveNoSystem(merged, merged)!!

			assertEquals(4, resolved.getObject("j").size)
			assertEquals(1, resolved.getInt("j.x"))
			assertEquals(2, resolved.getInt("j.y"))
			assertEquals(3, resolved.getInt("j.z"))
			assertEquals(4, resolved.getInt("j.q"))
		}
	}

	@Test
	fun mergePrimitiveAndTwoSubstitutions() {
		val obj1 = parseObject("""{ "j" : 42 }""")
		val obj2 = parseObject("{ \"b\" : { \"y\" : 2, \"q\" : 5 }, \"j\" : \${b} }")
		val obj3 = parseObject("{ \"c\" : { \"z\" : 3, \"q\" : 6 }, \"j\" : \${c} }")

		associativeMerge(listOf(obj1, obj2, obj3)) { merged ->
			val resolved = resolveNoSystem(merged, merged)!!

			assertEquals(3, resolved.root().size)
			assertEquals(42, resolved.getInt("j"))
			assertEquals(2, resolved.getInt("b.y"))
			assertEquals(3, resolved.getInt("c.z"))
		}
	}

	@Test
	fun mergeObjectAndTwoSubstitutions() {
		val obj1 = parseObject("""{ "j" : { "x" : 1, "q" : 4 } }""")
		val obj2 = parseObject("{ \"b\" : { \"y\" : 2, \"q\" : 5 }, \"j\" : \${b} }")
		val obj3 = parseObject("{ \"c\" : { \"z\" : 3, \"q\" : 6 }, \"j\" : \${c} }")

		associativeMerge(listOf(obj1, obj2, obj3)) { merged ->
			val resolved = resolveNoSystem(merged, merged)!!

			assertEquals(4, resolved.getObject("j").size)
			assertEquals(1, resolved.getInt("j.x"))
			assertEquals(2, resolved.getInt("j.y"))
			assertEquals(3, resolved.getInt("j.z"))
			assertEquals(4, resolved.getInt("j.q"))
		}
	}

	@Test
	fun mergeObjectSubstitutionObjectSubstitution() {
		val obj1 = parseObject("""{ "j" : { "w" : 1, "q" : 5 } }""")
		val obj2 = parseObject("{ \"b\" : { \"x\" : 2, \"q\" : 6 }, \"j\" : \${b} }")
		val obj3 = parseObject("""{ "j" : { "y" : 3, "q" : 7 } }""")
		val obj4 = parseObject("{ \"c\" : { \"z\" : 4, \"q\" : 8 }, \"j\" : \${c} }")

		associativeMerge(listOf(obj1, obj2, obj3, obj4)) { merged ->
			val resolved = resolveNoSystem(merged, merged)!!

			assertEquals(5, resolved.getObject("j").size)
			assertEquals(1, resolved.getInt("j.w"))
			assertEquals(2, resolved.getInt("j.x"))
			assertEquals(3, resolved.getInt("j.y"))
			assertEquals(4, resolved.getInt("j.z"))
			assertEquals(5, resolved.getInt("j.q"))
		}
	}

	@Test
	fun ignoredMergesDoNothing() {
		val conf = parseConfig("{ a : 1 }")
		testIgnoredMergesDoNothing(conf)
	}

	@Test
	fun testNoMergeAcrossArray() {
		val conf = parseConfig("a: {b:1}, a: [2,3], a:{c:4}")
		assertFalse(conf.hasPath("a.b"), "a.b found in: $conf")
		assertTrue(conf.hasPath("a.c"), "a.c not found in: $conf")
	}

	@Test
	fun testNoMergeAcrossUnresolvedArray() {
		val conf = parseConfig("a: {b:1}, a: [2,\${x}], a:{c:4}, x: 42")
		assertFalse(conf.hasPath("a.b"), "a.b found in: $conf")
		assertTrue(conf.hasPath("a.c"), "a.c not found in: $conf")
	}

	@Test
	fun testNoMergeLists() {
		val conf = parseConfig("a: [1,2], a: [3,4]")
		assertEquals(listOf(3, 4), conf.getIntList("a"), "lists did not merge")
	}

	@Test
	fun testListsWithFallback() {
		val list1 = ConfigValueFactory.fromIterable(listOf(1, 2, 3))
		val list2 = ConfigValueFactory.fromIterable(listOf(4, 5, 6))
		val merged1 = list1.withFallback(list2)
		val merged2 = list2.withFallback(list1)
		assertEquals(list1, merged1, "lists did not merge 1")
		assertEquals(list2, merged2, "lists did not merge 2")
		assertFalse(list1 == list2, "equals is working on these")
		assertFalse(list1 == merged2, "equals is working on these")
		assertFalse(list2 == merged1, "equals is working on these")
	}

	@Test
	fun integerRangeChecks() {
		val conf =
			parseConfig("{ tooNegative: " + (Integer.MIN_VALUE - 1L) + ", tooPositive: " + (Integer.MAX_VALUE + 1L) + "}")
		val en = assertThrows(ConfigException.WrongType::class.java) {
			conf.getInt("tooNegative")
		}
		assertTrue(en.message!!.contains("range"))

		val ep = assertThrows(ConfigException.WrongType::class.java) {
			conf.getInt("tooPositive")
		}
		assertTrue(ep.message!!.contains("range"))
	}

	@Test
	fun test01Getting() {
		val conf = ConfigFactory.load("test01")

		// get all the primitive types
		assertEquals(42, conf.getInt("ints.fortyTwo"))
		assertEquals(42, conf.getInt("ints.fortyTwoAgain"))
		assertEquals(42L, conf.getLong("ints.fortyTwoAgain"))
		assertEquals(42.1, conf.getDouble("floats.fortyTwoPointOne"), 1e-6)
		assertEquals(42.1, conf.getDouble("floats.fortyTwoPointOneAgain"), 1e-6)
		assertEquals(0.33, conf.getDouble("floats.pointThirtyThree"), 1e-6)
		assertEquals(0.33, conf.getDouble("floats.pointThirtyThreeAgain"), 1e-6)
		assertEquals("abcd", conf.getString("strings.abcd"))
		assertEquals("abcd", conf.getString("strings.abcdAgain"))
		assertEquals("null bar 42 baz true 3.14 hi", conf.getString("strings.concatenated"))
		assertEquals(true, conf.getBoolean("booleans.trueAgain"))
		assertEquals(false, conf.getBoolean("booleans.falseAgain"))

		// to get null we have to use the get() method from Map,
		// which takes a key and not a path
		assertEquals(nullValue(), conf.getObject("nulls")["null"])
		assertNull(conf.root()["notinthefile"])

		// get stuff with getValue
		assertEquals(intValue(42), conf.getValue("ints.fortyTwo"))
		assertEquals(stringValue("abcd"), conf.getValue("strings.abcd"))

		// get stuff with getAny
		assertEquals(42, conf.getAnyRef("ints.fortyTwo"))
		assertEquals("abcd", conf.getAnyRef("strings.abcd"))
		assertEquals(false, conf.getAnyRef("booleans.falseAgain"))

		// get empty array as any type of array
		assertEquals(emptyList<Any>(), conf.getAnyRefList("arrays.empty"))
		assertEquals(emptyList<Int>(), conf.getIntList("arrays.empty"))
		assertEquals(emptyList<Long>(), conf.getLongList("arrays.empty"))
		assertEquals(emptyList<String>(), conf.getStringList("arrays.empty"))
		assertEquals(emptyList<Long>(), conf.getLongList("arrays.empty"))
		assertEquals(emptyList<Double>(), conf.getDoubleList("arrays.empty"))
		assertEquals(emptyList<Any>(), conf.getObjectList("arrays.empty"))
		assertEquals(emptyList<Boolean>(), conf.getBooleanList("arrays.empty"))
		assertEquals(emptyList<Number>(), conf.getNumberList("arrays.empty"))
		assertEquals(emptyList<Any>(), conf.getList("arrays.empty"))

		// get typed arrays
		assertEquals(listOf(1, 2, 3), conf.getIntList("arrays.ofInt"))
		assertEquals(listOf(1L, 2L, 3L), conf.getLongList("arrays.ofInt"))
		assertEquals(listOf("a", "b", "c"), conf.getStringList("arrays.ofString"))
		assertEquals(listOf(3.14, 4.14, 5.14), conf.getDoubleList("arrays.ofDouble"))
		assertEquals(listOf(null, null, null), conf.getAnyRefList("arrays.ofNull"))
		assertEquals(listOf(true, false), conf.getBooleanList("arrays.ofBoolean"))
		val listOfLists = conf.getAnyRefList("arrays.ofArray").map {
			it as List<Any>
		}
		assertEquals(listOf(listOf("a", "b", "c"), listOf("a", "b", "c"), listOf("a", "b", "c")), listOfLists)
		assertEquals(3, conf.getObjectList("arrays.ofObject").size)

		assertEquals(listOf("a", "b"), conf.getStringList("arrays.firstElementNotASubst"))

		// plain getList should work
		assertEquals(listOf(intValue(1), intValue(2), intValue(3)), conf.getList("arrays.ofInt"))
		assertEquals(listOf(stringValue("a"), stringValue("b"), stringValue("c")), conf.getList("arrays.ofString"))

		// make sure floats starting with a '.' are parsed as strings (they will be converted to double on demand)
		assertEquals(ConfigValueType.STRING, conf.getValue("floats.pointThirtyThree").valueType())
	}

	@Test
	fun test01Exceptions() {
		val conf = ConfigFactory.load("test01")

		// should throw Missing if key doesn't exist
		assertThrows(ConfigException.Missing::class.java) {
			conf.getInt("doesnotexist")
		}

		// should throw Null if key is null
		assertThrows(ConfigException.Null::class.java) {
			conf.getInt("nulls.null")
		}

		assertThrows(ConfigException.Null::class.java) {
			conf.getIntList("nulls.null")
		}

		assertThrows(ConfigException.Null::class.java) {
			conf.getDuration("nulls.null")
		}

		assertThrows(ConfigException.Null::class.java) {
			conf.getBytes("nulls.null")
		}

		assertThrows(ConfigException.Null::class.java) {
			conf.getMemorySize("nulls.null")
		}

		// should throw WrongType if key is wrong type and not convertible
		assertThrows(ConfigException.WrongType::class.java) {
			conf.getInt("booleans.trueAgain")
		}

		assertThrows(ConfigException.WrongType::class.java) {
			conf.getBooleanList("arrays.ofInt")
		}

		assertThrows(ConfigException.WrongType::class.java) {
			conf.getIntList("arrays.ofBoolean")
		}

		assertThrows(ConfigException.WrongType::class.java) {
			conf.getObjectList("arrays.ofInt")
		}

		assertThrows(ConfigException.WrongType::class.java) {
			conf.getDuration("ints")
		}

		assertThrows(ConfigException.WrongType::class.java) {
			conf.getBytes("ints")
		}

		assertThrows(ConfigException.WrongType::class.java) {
			conf.getMemorySize("ints")
		}

		// should throw BadPath on various bad paths
		assertThrows(ConfigException.BadPath::class.java) {
			conf.getInt(".bad")
		}

		assertThrows(ConfigException.BadPath::class.java) {
			conf.getInt("bad.")
		}

		assertThrows(ConfigException.BadPath::class.java) {
			conf.getInt("bad..bad")
		}

		// should throw BadValue on things that don't parse
		// as durations and sizes

		assertThrows(ConfigException.BadValue::class.java) {
			conf.getDuration("strings.a")
		}

		assertThrows(ConfigException.BadValue::class.java) {
			conf.getBytes("strings.a")
		}

		assertThrows(ConfigException.BadValue::class.java) {
			conf.getMemorySize("strings.a")
		}

	}

	@Test
	fun test01Conversions() {
		val conf = ConfigFactory.load("test01")

		// should convert numbers to string
		assertEquals("42", conf.getString("ints.fortyTwo"))
		assertEquals("42.1", conf.getString("floats.fortyTwoPointOne"))
		assertEquals(".33", conf.getString("floats.pointThirtyThree"))

		// should convert string to number
		assertEquals(57, conf.getInt("strings.number"))
		assertEquals(3.14, conf.getDouble("strings.double"), 1e-6)
		assertEquals(0.33, conf.getDouble("strings.doubleStartingWithDot"), 1e-6)

		// should convert strings to boolean
		assertEquals(true, conf.getBoolean("strings.true"))
		assertEquals(true, conf.getBoolean("strings.yes"))
		assertEquals(false, conf.getBoolean("strings.false"))
		assertEquals(false, conf.getBoolean("strings.no"))

		// converting some random string to boolean fails though
		assertThrows(ConfigException.WrongType::class.java) {
			conf.getBoolean("strings.abcd")
		}

		// FIXME test convert string "null" to a null value

		// should not convert strings to object or list
		assertThrows(ConfigException.WrongType::class.java) {
			conf.getObject("strings.a")
		}

		assertThrows(ConfigException.WrongType::class.java) {
			conf.getList("strings.a")
		}

		// should not convert object or list to string
		assertThrows(ConfigException.WrongType::class.java) {
			conf.getString("ints")
		}

		assertThrows(ConfigException.WrongType::class.java) {
			conf.getString("arrays.ofInt")
		}

		// should get durations
		fun asNanos(secs: Long) = TimeUnit.SECONDS.toNanos(secs)

		assertEquals(1000L, conf.getDuration("durations.second", ChronoUnit.MILLIS))
		assertEquals(asNanos(1), conf.getDuration("durations.second", ChronoUnit.NANOS))
		assertEquals(1000L, conf.getDuration("durations.secondAsNumber", ChronoUnit.MILLIS))
		assertEquals(asNanos(1), conf.getDuration("durations.secondAsNumber", ChronoUnit.NANOS))
		assertEquals(
			listOf(1000L, 2000L, 3000L, 4000L),
			conf.getDurationList("durations.secondsList", ChronoUnit.MILLIS)
		)
		assertEquals(
			listOf(asNanos(1), asNanos(2), asNanos(3), asNanos(4)),
			conf.getDurationList("durations.secondsList", ChronoUnit.NANOS)
		)
		assertEquals(500L, conf.getDuration("durations.halfSecond", ChronoUnit.MILLIS))
		assertEquals(4878955355435272204L, conf.getDuration("durations.largeNanos", ChronoUnit.NANOS))
		assertEquals(4878955355435272204L, conf.getDuration("durations.plusLargeNanos", ChronoUnit.NANOS))
		assertEquals(-4878955355435272204L, conf.getDuration("durations.minusLargeNanos", ChronoUnit.NANOS))

		// get durations as java.time.Duration
		assertEquals(1000L, conf.getDuration("durations.second").toMillis())
		assertEquals(asNanos(1), conf.getDuration("durations.second").toNanos())
		assertEquals(1000L, conf.getDuration("durations.secondAsNumber").toMillis())
		assertEquals(asNanos(1), conf.getDuration("durations.secondAsNumber").toNanos())
		assertEquals(
			listOf(1000L, 2000L, 3000L, 4000L),
			conf.getDurationList("durations.secondsList").map { it.toMillis() })
		assertEquals(
			listOf(asNanos(1), asNanos(2), asNanos(3), asNanos(4)),
			conf.getDurationList("durations.secondsList").map { it.toNanos() })
		assertEquals(500L, conf.getDuration("durations.halfSecond").toMillis())
		assertEquals(4878955355435272204L, conf.getDuration("durations.largeNanos").toNanos())
		assertEquals(4878955355435272204L, conf.getDuration("durations.plusLargeNanos").toNanos())
		assertEquals(-4878955355435272204L, conf.getDuration("durations.minusLargeNanos").toNanos())

		fun assertDurationAsChronoUnit(unit: ChronoUnit): Unit {
			fun ns2unit(l: Long) = TimeUnit.of(unit).convert(l, TimeUnit.NANOSECONDS)

			fun ms2unit(l: Long) = TimeUnit.of(unit).convert(l, TimeUnit.MILLISECONDS)

			fun s2unit(i: Long) = TimeUnit.of(unit).convert(i, TimeUnit.SECONDS)

			assertEquals(ms2unit(1000L), conf.getDuration("durations.second", unit))
			assertEquals(s2unit(1), conf.getDuration("durations.second", unit))
			assertEquals(ms2unit(1000L), conf.getDuration("durations.secondAsNumber", unit))
			assertEquals(s2unit(1), conf.getDuration("durations.secondAsNumber", unit))
			assertEquals(
				listOf(1000L, 2000L, 3000L, 4000L).map { ms2unit(it) },
				conf.getDurationList("durations.secondsList", unit)
			)
			assertEquals(
				listOf(1, 2, 3, 4).map { s2unit(it.toLong()) },
				conf.getDurationList("durations.secondsList", unit)
			)
			assertEquals(ms2unit(500L), conf.getDuration("durations.halfSecond", unit))
			assertEquals(ms2unit(1L), conf.getDuration("durations.millis", unit))
			assertEquals(ms2unit(2L), conf.getDuration("durations.micros", unit))
			assertEquals(ns2unit(4878955355435272204L), conf.getDuration("durations.largeNanos", unit))
			assertEquals(ns2unit(4878955355435272204L), conf.getDuration("durations.plusLargeNanos", unit))
			assertEquals(ns2unit(-4878955355435272204L), conf.getDuration("durations.minusLargeNanos", unit))
		}

		assertDurationAsChronoUnit(ChronoUnit.NANOS)
		assertDurationAsChronoUnit(ChronoUnit.MICROS)
		assertDurationAsChronoUnit(ChronoUnit.MILLIS)
		assertDurationAsChronoUnit(ChronoUnit.SECONDS)
		assertDurationAsChronoUnit(ChronoUnit.MINUTES)
		assertDurationAsChronoUnit(ChronoUnit.HOURS)
		assertDurationAsChronoUnit(ChronoUnit.DAYS)

		// periods
		assertEquals(1, conf.getPeriod("periods.day").get(ChronoUnit.DAYS))
		assertEquals(2, conf.getPeriod("periods.dayAsNumber").days)
		assertEquals(3 * 7, conf.getTemporal("periods.week").get(ChronoUnit.DAYS))
		assertEquals(5, conf.getTemporal("periods.month").get(ChronoUnit.MONTHS))
		assertEquals(8, conf.getTemporal("periods.year").get(ChronoUnit.YEARS))

		// should get size in bytes
		assertEquals(1024 * 1024L, conf.getBytes("memsizes.meg"))
		assertEquals(1024 * 1024L, conf.getBytes("memsizes.megAsNumber"))
		assertEquals(
			listOf(1024 * 1024L, 1024 * 1024L, 1024L * 1024L),
			conf.getBytesList("memsizes.megsList")
		)
		assertEquals(512 * 1024L, conf.getBytes("memsizes.halfMeg"))

		// should get size as a ConfigMemorySize
		assertEquals(1024 * 1024L, conf.getMemorySize("memsizes.meg").toLongBytes())
		assertEquals(1024 * 1024L, conf.getMemorySize("memsizes.megAsNumber").toLongBytes())
		assertEquals(
			listOf(1024 * 1024L, 1024 * 1024L, 1024L * 1024L),
			conf.getMemorySizeList("memsizes.megsList").map { it.toLongBytes() })
		assertEquals(512 * 1024L, conf.getMemorySize("memsizes.halfMeg").toLongBytes())

		assertEquals(BigInteger("1000000000000000000000000"), conf.getMemorySize("memsizes.yottabyte").bytes())
		assertEquals(
			listOf(BigInteger("1000000000000000000000000"), BigInteger("500000000000000000000000")),
			conf.getMemorySizeList("memsizes.yottabyteList").map { it.bytes() })
	}

	@Test
	fun test01MergingOtherFormats() {
		val conf = ConfigFactory.load("test01")

		// should have loaded stuff from .json
		assertEquals(1, conf.getInt("fromJson1"))
		assertEquals("A", conf.getString("fromJsonA"))

		// should have loaded stuff from .properties
		assertEquals("abc", conf.getString("fromProps.abc"))
		assertEquals(1, conf.getInt("fromProps.one"))
		assertEquals(true, conf.getBoolean("fromProps.bool"))
	}

	@Test
	fun test01ToString() {
		val conf = ConfigFactory.load("test01")

		// toString() on conf objects doesn't throw (toString is just a debug string so not testing its result)
		conf.toString()
	}

	@Test
	fun test01SystemFallbacks() {
		val conf = ConfigFactory.load("test01")
		val jv = System.getProperty("java.version")
		assertNotNull(jv)
		assertEquals(jv, conf.getString("system.javaversion"))
		val home = System.getenv("HOME")
		if (home != null) {
			assertEquals(home, conf.getString("system.home"))
		} else {
			assertEquals(null, conf.getObject("system")["home"])
		}
	}

	@Test
	fun test01Origins() {
		val conf = ConfigFactory.load("test01")

		val o1 = conf.getValue("ints.fortyTwo").origin()
		// the checkout directory would be in between this startsWith and endsWith
		assertTrue(
			o1.description().startsWith("test01.conf @"),
			"description starts with resource '" + o1.description() + "'"
		)
		assertTrue(
			o1.description().endsWith("/config/build/resources/test/test01.conf: 3"),
			"description ends with url and line '" + o1.description() + "'"
		)
		assertEquals("test01.conf", o1.resource())
		assertTrue(o1.uri().path.endsWith("/config/build/resources/test/test01.conf"), "url ends with resource file")
		assertEquals(3, o1.lineNumber())

		val o2 = conf.getValue("fromJson1").origin()
		// the checkout directory would be in between this startsWith and endsWith
		assertTrue(
			o2.description().startsWith("test01.json @"),
			"description starts with json resource '" + o2.description() + "'"
		)
		assertTrue(
			o2.description().endsWith("/config/build/resources/test/test01.json: 2"),
			"description of json resource ends with url and line '" + o2.description() + "'"
		)
		assertEquals("test01.json", o2.resource())
		assertTrue(
			o2.uri().path.endsWith("/config/build/resources/test/test01.json"),
			"url ends with json resource file"
		)
		assertEquals(2, o2.lineNumber())

		val o3 = conf.getValue("fromProps.bool").origin()
		// the checkout directory would be in between this startsWith and endsWith
		assertTrue(
			o3.description().startsWith("test01.properties @"),
			"description starts with props resource '" + o3.description() + "'"
		)
		assertTrue(
			o3.description().endsWith("/config/build/resources/test/test01.properties"),
			"description of props resource ends with url '" + o3.description() + "'"
		)
		assertEquals("test01.properties", o3.resource())
		assertTrue(
			o3.uri().path.endsWith("/config/build/resources/test/test01.properties"),
			"url ends with props resource file"
		)
		// we don't have line numbers for properties files
		assertEquals(-1, o3.lineNumber())
	}

	@Test
	fun test01EntrySet() {
		val conf = ConfigFactory.load("test01")

		val javaEntries = conf.entrySet()
		val entries = javaEntries.associate { e -> e.key to e.value }
		assertEquals(intValue(42), entries["ints.fortyTwo"])
		assertFalse(entries.containsKey("nulls.null"))
	}

	@Test
	fun test01Serializable() {
		// we can't ever test an expected serialization here because it
		// will have system props in it that vary by test system,
		// and the ConfigOrigin in there will also vary by test system
		val conf = ConfigFactory.load("test01")
		val confCopy = checkSerializable(conf)
	}

	@Test
	fun test02SubstitutionsWithWeirdPaths() {
		val conf = ConfigFactory.load("test02")

		assertEquals(42, conf.getInt("42_a"))
		assertEquals(42, conf.getInt("42_b"))
		assertEquals(57, conf.getInt("57_a"))
		assertEquals(57, conf.getInt("57_b"))
		assertEquals(103, conf.getInt("103_a"))
	}

	@Test
	fun test02UseWeirdPathsWithConfigObject() {
		val conf = ConfigFactory.load("test02")

		// we're checking that the getters in ConfigObject support
		// these weird path expressions
		assertEquals(42, conf.getInt(""" "".""."" """))
		assertEquals(57, conf.getInt("a.b.c"))
		assertEquals(57, conf.getInt(""" "a"."b"."c" """))
		assertEquals(103, conf.getInt(""" "a.b.c" """))
	}

	@Test
	fun test03Includes() {
		val conf = ConfigFactory.load("test03")

		// include should have overridden the "ints" value in test03
		assertEquals(42, conf.getInt("test01.ints.fortyTwo"))
		// include should have been overridden by 42
		assertEquals(42, conf.getInt("test01.booleans"))
		assertEquals(42, conf.getInt("test01.booleans"))
		// include should have gotten .properties and .json also
		assertEquals("abc", conf.getString("test01.fromProps.abc"))
		assertEquals("A", conf.getString("test01.fromJsonA"))
		// test02 was included
		assertEquals(57, conf.getInt("test02.a.b.c"))
		// equiv01/original.json was included (it has a slash in the name)
		assertEquals("a", conf.getString("equiv01.strings.a"))

		// Now check that substitutions still work
		assertEquals(42, conf.getInt("test01.ints.fortyTwoAgain"))
		assertEquals(listOf("a", "b", "c"), conf.getStringList("test01.arrays.ofString"))
		assertEquals(103, conf.getInt("test02.103_a"))

		// and system fallbacks still work
		val jv = System.getProperty("java.version")
		assertNotNull(jv)
		assertEquals(jv, conf.getString("test01.system.javaversion"))
		val home = System.getenv("HOME")
		if (home != null) {
			assertEquals(home, conf.getString("test01.system.home"))
		} else {
			assertEquals(null, conf.getObject("test01.system")["home"])
		}
		val concatenated = conf.getString("test01.system.concatenated")
		assertTrue(concatenated.contains("Your Java version"))
		assertTrue(concatenated.contains(jv))
		assertTrue(concatenated.contains(conf.getString("test01.system.userhome")))

		// check that includes into the root object work and that
		// "substitutions look relative-to-included-file first then at root second" works
		assertEquals("This is in the included file", conf.getString("a"))
		assertEquals("This is in the including file", conf.getString("b"))
		assertEquals("This is in the included file", conf.getString("subtree.a"))
		assertEquals("This is in the including file", conf.getString("subtree.b"))
	}

	@Test
	fun test04LoadAkkaReference() {
		val conf = ConfigFactory.load("test04")

		// Note, test04 is an unmodified old-style akka.conf,
		// which means it has an outer akka{} namespace.
		// that namespace wouldn't normally be used with
		// this library because the conf object is not global,
		// it's per-module already.
		assertEquals("2.0-SNAPSHOT", conf.getString("akka.version"))
		assertEquals(8, conf.getInt("akka.event-handler-dispatcher.max-pool-size"))
		assertEquals("round-robin", conf.getString("akka.actor.deployment.\"/app/service-ping\".router"))
		assertEquals(true, conf.getBoolean("akka.stm.quick-release"))
	}

	@Test
	fun test05LoadPlayApplicationConf() {
		val conf = ConfigFactory.load("test05")

		assertEquals("prod", conf.getString("%prod.application.mode"))
		assertEquals("Yet another blog", conf.getString("blog.title"))
	}

	@Test
	fun test06Merge() {
		// test06 mostly exists because its render() round trip is tricky
		val conf = ConfigFactory.load("test06")

		assertEquals(2, conf.getInt("x"))
		assertEquals(10, conf.getInt("y.foo"))
		assertEquals("world", conf.getString("y.hello"))
	}

	@Test
	fun test07IncludingResourcesFromFiles() {
		// first, check that when loading from classpath we include another classpath resource
		val fromClasspath = ConfigFactory.parseResources(ConfigTest::class.java, "/test07.conf")

		assertEquals("This is to test classpath searches.", fromClasspath.getString("test-lib.description"))

		// second, check that when loading from a file it falls back to classpath
		val fromFile = ConfigFactory.parseFile(resourceFile("test07.conf"))

		assertEquals("This is to test classpath searches.", fromFile.getString("test-lib.description"))

		// third, check that a file: URL is the same
		val fromURL = ConfigFactory.parseURI(resourceFile("test07.conf").toURI())

		assertEquals("This is to test classpath searches.", fromURL.getString("test-lib.description"))
	}

	@Test
	fun test08IncludingSlashPrefixedResources() {
		// first, check that when loading from classpath we include another classpath resource
		val fromClasspath = ConfigFactory.parseResources(ConfigTest::class.java, "/test08.conf")

		assertEquals("This is to test classpath searches.", fromClasspath.getString("test-lib.description"))

		// second, check that when loading from a file it falls back to classpath
		val fromFile = ConfigFactory.parseFile(resourceFile("test08.conf"))

		assertEquals("This is to test classpath searches.", fromFile.getString("test-lib.description"))

		// third, check that a file: URL is the same
		val fromURL = ConfigFactory.parseURI(resourceFile("test08.conf").toURI())

		assertEquals("This is to test classpath searches.", fromURL.getString("test-lib.description"))
	}

	@Test
	fun test09DelayedMerge() {
		val conf = ConfigFactory.parseResources(ConfigTest::class.java, "/test09.conf")
		assertEquals(
			ConfigDelayedMergeObject::class.java.getSimpleName(),
			conf.root()["a"]!!::class.java.getSimpleName()
		)
		assertEquals(
			ConfigDelayedMerge::class.java.getSimpleName(),
			conf.root()["b"]!!::class.java.getSimpleName()
		)

		// a.c should work without resolving because no more merging is needed to compute it
		assertEquals(3, conf.getInt("a.c"))

		assertThrows(ConfigException.NotResolved::class.java) {
			conf.getInt("a.q")
		}

		// be sure resolving doesn't throw
		val resolved = conf.resolve()
		assertEquals(3, resolved.getInt("a.c"))
		assertEquals(5, resolved.getInt("b"))
		assertEquals(10, resolved.getInt("a.q"))
	}

	@Test
	fun test10DelayedMergeRelativizing() {
		val conf = ConfigFactory.parseResources(ConfigTest::class.java, "/test10.conf")
		val resolved = conf.resolve()
		assertEquals(3, resolved.getInt("foo.a.c"))
		assertEquals(5, resolved.getInt("foo.b"))
		assertEquals(10, resolved.getInt("foo.a.q"))

		assertEquals(3, resolved.getInt("bar.nested.a.c"))
		assertEquals(5, resolved.getInt("bar.nested.b"))
		assertEquals(10, resolved.getInt("bar.nested.a.q"))
	}

	@Test
	fun testEnvVariablesNameMangling() {
		assertEquals("a", ConfigImplUtil.envVariableAsProperty("prefix_a", "prefix_"))
		assertEquals("a.b", ConfigImplUtil.envVariableAsProperty("prefix_a_b", "prefix_"))
		assertEquals("a.b.c", ConfigImplUtil.envVariableAsProperty("prefix_a_b_c", "prefix_"))
		assertEquals("a.b-c-d", ConfigImplUtil.envVariableAsProperty("prefix_a_b__c__d", "prefix_"))
		assertEquals("a.b_c_d", ConfigImplUtil.envVariableAsProperty("prefix_a_b___c___d", "prefix_"))

		assertThrows(ConfigException.BadPath::class.java) {
			ConfigImplUtil.envVariableAsProperty("prefix_____", "prefix_")
		}
		assertThrows(ConfigException.BadPath::class.java) {
			ConfigImplUtil.envVariableAsProperty("prefix_a_b___c____d", "prefix_")
		}
	}

	@Test
	fun testLoadWithEnvSubstitutions() {
		System.setProperty("config.override_with_env_vars", "true")

		try {
			val loader02 = TestClassLoader(
				this::class.java.getClassLoader(),
				mapOf("reference.conf" to resourceFile("test02.conf").toURI().toURL())
			)

			val loader04 = TestClassLoader(
				this::class.java.getClassLoader(),
				mapOf("reference.conf" to resourceFile("test04.conf").toURI().toURL())
			)

			val conf02 = withContextClassLoader(loader02) {
				ConfigFactory.load()
			}

			val conf04 = withContextClassLoader(loader04) {
				ConfigFactory.load()
			}

			assertEquals(1, conf02.getInt("42_a"))
			assertEquals(2, conf02.getInt("a.b.c"))
			assertEquals(3, conf02.getInt("a-c"))
			assertEquals(4, conf02.getInt("a_c"))

			assertThrows(ConfigException.Missing::class.java) {
				conf02.getInt("CONFIG_FORCE_a_b_c")
			}

			assertEquals("foo", conf04.getString("akka.version"))
			assertEquals(10, conf04.getInt("akka.event-handler-dispatcher.max-pool-size"))
		} finally {
			System.clearProperty("config.override_with_env_vars")
		}
	}

	@Test
	fun renderRoundTrip() {
		val allBooleans = listOf(true, false)
		val optionsCombos = allBooleans.flatMap { allBooleans.map { that -> listOf(it, that) } }
			.flatMap { allBooleans.map { that -> it.plus(that) } }
			.flatMap { allBooleans.map { that -> it.plus(that) } }
			.map { (formatted, originComments, comments, json) ->
				ConfigRenderOptions.defaults()
					.setFormatted(formatted)
					.setOriginComments(originComments)
					.setComments(comments)
					.setJson(json)
			}

		for (i in 1..10) {
			val numString = i.toString()
			val name = "/test" + (
					if (numString.length == 1) "0" else ""
					) + numString
			val conf = ConfigFactory.parseResourcesAnySyntax(
				ConfigTest::class.java, name,
				ConfigParseOptions.defaults().setAllowMissing(false)
			)
			for (renderOptions in optionsCombos) {
				val unresolvedRender = conf.root().render(renderOptions)
				val resolved = conf.resolve()
				val resolvedRender = resolved.root().render(renderOptions)
				val unresolvedParsed = ConfigFactory.parseString(unresolvedRender, ConfigParseOptions.defaults())
				val resolvedParsed = ConfigFactory.parseString(resolvedRender, ConfigParseOptions.defaults())
				try {
					assertEquals(conf.root(), unresolvedParsed.root(), "unresolved options=$renderOptions")
					assertEquals(resolved.root(), resolvedParsed.root(), "resolved options=$renderOptions")
				} catch (e: Throwable) {
					System.err.println("UNRESOLVED diff:")
					showDiff(conf.root(), unresolvedParsed.root())
					System.err.println("RESOLVED diff:")
					showDiff(resolved.root(), resolvedParsed.root())
					throw e
				}
				if (renderOptions.json && !(renderOptions.comments || renderOptions.originComments)) {
					// should get valid JSON if we don't have comments and are resolved
					val json = try {
						ConfigFactory.parseString(
							resolvedRender,
							ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON)
						)
					} catch (e: Exception) {
						System.err.println("resolvedRender is not valid json: $resolvedRender")
						throw e
					}
				}
				// rendering repeatedly should not make the file different (e.g. shouldn't make it longer)
				// unless the debug comments are in there
				if (!renderOptions.originComments) {
					val renderedAgain = resolvedParsed.root().render(renderOptions)
					// TODO the strings should be THE SAME not just the same length,
					// but there's a bug right now that sometimes object keys seem to
					// be re-ordered. Need to fix.
					assertEquals(
						resolvedRender.length, renderedAgain.length,
						"render changed, resolved options=$renderOptions"
					)
				}
			}
		}
	}

	@Test
	fun renderShowEnvVariableValues(): Unit {
		val config = ConfigFactory.load("env-variables")
		assertEquals("A", config.getString("secret"))
		assertEquals("B", config.getStringList("secrets")[0])
		assertEquals("C", config.getStringList("secrets")[1])
		val hideRenderOpt = ConfigRenderOptions.defaults().setShowEnvVariableValues(false)
			.setIndentMode(ConfigRenderOptions.IndentMode.FOUR_SPACES)
		val rendered1 = config.root().render(hideRenderOpt)
		assertTrue(rendered1.contains(""""secret" : "<env variable>""""))
		assertTrue(
			rendered1.contains(
				"""|    "secrets" : [
               |        # env variables
               |        "<env variable>",
               |        # env variables
               |        "<env variable>"
               |    ]""".trimMargin()
			)
		)

		val showRenderOpt = ConfigRenderOptions.defaults().setIndentMode(ConfigRenderOptions.IndentMode.FOUR_SPACES)
		val rendered2 = config.root().render(showRenderOpt)
		assertTrue(rendered2.contains(""""secret" : "A""""))
		assertTrue(
			rendered2.contains(
				"""|    "secrets" : [
               |        # env variables
               |        "B",
               |        # env variables
               |        "C"
               |    ]""".trimMargin()
			)
		)
	}

	@Test
	fun serializeRoundTrip() {
		for (i in 1..10) {
			val numString = i.toString()
			val name = "/test" + (
					if (numString.length == 1) "0" else ""
					) + numString
			val conf = ConfigFactory.parseResourcesAnySyntax(
				ConfigTest::class.java, name,
				ConfigParseOptions.defaults().setAllowMissing(false)
			)
			val resolved = conf.resolve()
			checkSerializable(resolved)
		}
	}

	@Test
	fun isResolvedWorks() {
		val resolved = ConfigFactory.parseString("foo = 1")
		assertTrue(resolved.isResolved, "config with no substitutions starts as resolved")
		val unresolved = ConfigFactory.parseString("foo = \${a}, a=42")
		assertFalse(unresolved.isResolved, "config with substitutions starts as not resolved")
		val resolved2 = unresolved.resolve()
		assertTrue(resolved2.isResolved, "after resolution, config is now resolved")
	}

	@Test
	fun allowUnresolvedDoesAllowUnresolvedArrayElements() {
		val values = ConfigFactory.parseString("unknown = [someVal], known = 42")
		val unresolved = ConfigFactory.parseString("concat = [\${unknown}[]], sibling = [\${unknown}, \${known}]")
		unresolved.resolve(ConfigResolveOptions.defaults().setAllowUnresolved(true))
		unresolved.withFallback(values).resolve()
		unresolved.resolveWith(values)
	}

	@Test
	fun allowUnresolvedDoesAllowUnresolved() {
		val values = ConfigFactory.parseString("{ foo = 1, bar = 2, m = 3, n = 4}")
		assertTrue(values.isResolved, "config with no substitutions starts as resolved")
		val unresolved =
			ConfigFactory.parseString("a = \${foo}, b = \${bar}, c { x = \${m}, y = \${n}, z = foo\${m}bar }, alwaysResolveable=\${alwaysValue}, alwaysValue=42")
		assertFalse(unresolved.isResolved, "config with substitutions starts as not resolved")

		// resolve() by default throws with unresolveable substs
		assertThrows(ConfigException.UnresolvedSubstitution::class.java) {
			unresolved.resolve(ConfigResolveOptions.defaults())
		}
		// we shouldn't be able to get a value without resolving it
		assertThrows(ConfigException.NotResolved::class.java) {
			unresolved.getInt("alwaysResolveable")
		}
		val allowedUnresolved = unresolved.resolve(ConfigResolveOptions.defaults().setAllowUnresolved(true))
		// when we partially-resolve we should still resolve what we can
		assertEquals(42, allowedUnresolved.getInt("alwaysResolveable"), "we resolved the resolveable")
		// but unresolved should still all throw
		for (k in listOf("a", "b", "c.x", "c.y")) {
			assertThrows(ConfigException.NotResolved::class.java) {
				allowedUnresolved.getInt(k)
			}
		}
		assertThrows(ConfigException.NotResolved::class.java) {
			allowedUnresolved.getString("c.z")
		}

		// and the partially-resolved thing is not resolved
		assertFalse(allowedUnresolved.isResolved, "partially-resolved object is not resolved")

		// scope "val resolved"
		run {
			// and given the values for the resolve, we should be able to
			val resolved = allowedUnresolved.withFallback(values).resolve()
			for (kv in listOf("a" to 1, "b" to 2, "c.x" to 3, "c.y" to 4)) {
				assertEquals(kv.second, resolved.getInt(kv.first))
			}
			assertEquals("foo3bar", resolved.getString("c.z"))
			assertTrue(resolved.isResolved, "fully resolved object is resolved")
		}

		// we should also be able to use resolveWith
		run {
			val resolved = allowedUnresolved.resolveWith(values)
			for (kv in listOf("a" to 1, "b" to 2, "c.x" to 3, "c.y" to 4)) {
				assertEquals(kv.second, resolved.getInt(kv.first))
			}
			assertEquals("foo3bar", resolved.getString("c.z"))
			assertTrue(resolved.isResolved, "fully resolved object is resolved")
		}
	}

	@Test
	fun resolveWithWorks(): Unit {
		// the a=42 is present here to be sure it gets ignored when we resolveWith
		val unresolved = ConfigFactory.parseString("foo = \${a}, a = 42")
		assertEquals(42, unresolved.resolve().getInt("foo"))
		val source = ConfigFactory.parseString("a = 43")
		val resolved = unresolved.resolveWith(source)
		assertEquals(43, resolved.getInt("foo"))
	}

	@Test
	fun resolveFallback(): Unit {
		runFallbackTest(
			"x=a,y=b",
			"x=\${a},y=\${b}", false,
			DummyResolver("", "", null)
		)
		runFallbackTest(
			"x=\"a.b.c\",y=\"a.b.d\"",
			"x=\${a.b.c},y=\${a.b.d}", false,
			DummyResolver("", "", null)
		)
		runFallbackTest(
			"x=\${a.b.c},y=\${a.b.d}",
			"x=\${a.b.c},y=\${a.b.d}", true,
			DummyResolver("x.", "", null)
		)
		runFallbackTest(
			"x=\${a.b.c},y=\"e.f\"",
			"x=\${a.b.c},y=\${d.e.f}", true,
			DummyResolver("d.", "", null)
		)
		runFallbackTest(
			"w=\"Y.c.d\",x=\${a},y=\"X.b\",z=\"Y.c\"",
			"x=\${a},y=\${a.b},z=\${a.b.c},w=\${a.b.c.d}", true,
			DummyResolver("a.b.", "Y.", null),
			DummyResolver("a.", "X.", null)
		)

		runFallbackTest("x=\${a.b.c}", "x=\${a.b.c}", true, DummyResolver("x.", "", null))
		val e = assertThrows(ConfigException.UnresolvedSubstitution::class.java) {
			runFallbackTest("x=\${a.b.c}", "x=\${a.b.c}", false, DummyResolver("x.", "", null))
		}
		assertTrue(e.message!!.contains("\${a.b.c}"))
	}

	private fun resolveNoSystem(v: AbstractConfigValue, root: AbstractConfigObject): AbstractConfigValue? {
		return ResolveContext.resolve(v, root, ConfigResolveOptions.noSystem())
	}

	private fun resolveNoSystem(v: SimpleConfig, root: SimpleConfig): SimpleConfig? {
		return (ResolveContext.resolve(
			v.root(), root.root(),
			ConfigResolveOptions.noSystem()
		) as AbstractConfigObject?)?.toConfig()
	}

	// Merging should always be associative (same results however the values are grouped,
	// as long as they remain in the same order)
	private fun associativeMerge(allObjects: List<AbstractConfigObject>, assertions: (SimpleConfig) -> Unit) {
		fun makeTrees(objects: List<AbstractConfigObject>): List<AbstractConfigObject> {
			return when (val n = objects.size) {
				0 -> emptyList()
				1 -> listOf(objects[0])
				2 -> listOf(objects[0].withFallback(objects[1]))
				else -> {
					val leftSplits = (1 until n).map { i ->
						val pair = objects.splitAt(i)
						val first = pair.first.reduce { acc, t -> acc.withFallback(t) }
						val second = pair.second.reduce { acc, t -> acc.withFallback(t) }
						first.withFallback(second)
					}
					val rightSplits = (1 until n).map { i ->
						val pair = objects.splitAt(i)
						val first = pair.first.reduceRight { acc, t -> acc.withFallback(t) }
						val second = pair.second.reduceRight { acc, t -> acc.withFallback(t) }
						first.withFallback(second)
					}
					(leftSplits + rightSplits)
				}
			}
		}

		val trees = makeTrees(allObjects)
		for (tree in trees) {
			// if this fails, we were not associative.
			if (trees.first() != tree)
				throw AssertionError(
					"Merge was not associative, " +
							"verify that it should not be, then don't use associativeMerge " +
							"for this one. two results were: \none: " + trees[0] + "\ntwo: " +
							tree + "\noriginal list: " + allObjects
				)
		}

		for (tree in trees) {
			assertions(tree.toConfig())
		}
	}

	private fun ignoresFallbacks(m: ConfigMergeable): Boolean {
		return when (m) {
			is AbstractConfigValue -> m.ignoresFallbacks()
			is SimpleConfig -> m.root().ignoresFallbacks()
			else -> throw IllegalArgumentException("ignoresFallbacks not defined for " + m::class)
		}
	}

	private fun testIgnoredMergesDoNothing(nonEmpty: ConfigMergeable) {
		// falling back to a primitive once should switch us to "ignoreFallbacks" mode
		// and then twice should "return this". Falling back to an empty object should
		// return this unless the empty object was ignoreFallbacks and then we should
		// "catch" its ignoreFallbacks.

		// some of what this tests is just optimization, not API contract (withFallback
		// can return a new object anytime it likes) but want to be sure we do the
		// optimizations.

		val empty = SimpleConfigObject.empty(null)
		val primitive = intValue(42)
		val emptyIgnoringFallbacks = empty.withFallback(primitive)
		val nonEmptyIgnoringFallbacks = nonEmpty.withFallback(primitive)

		assertEquals(false, empty.ignoresFallbacks())
		assertEquals(true, primitive.ignoresFallbacks())
		assertEquals(true, emptyIgnoringFallbacks.ignoresFallbacks())
		assertEquals(false, ignoresFallbacks(nonEmpty))
		assertEquals(true, ignoresFallbacks(nonEmptyIgnoringFallbacks))

		assertTrue(nonEmpty !== nonEmptyIgnoringFallbacks)
		assertTrue(empty !== emptyIgnoringFallbacks)

		// falling back from one object to another should not make us ignore fallbacks
		assertEquals(false, ignoresFallbacks(nonEmpty.withFallback(empty)))
		assertEquals(false, ignoresFallbacks(empty.withFallback(nonEmpty)))
		assertEquals(false, ignoresFallbacks(empty.withFallback(empty)))
		assertEquals(false, ignoresFallbacks(nonEmpty.withFallback(nonEmpty)))

		// falling back from primitive just returns this
		assertTrue(primitive === primitive.withFallback(empty))
		assertTrue(primitive === primitive.withFallback(nonEmpty))
		assertTrue(primitive === primitive.withFallback(nonEmptyIgnoringFallbacks))

		// falling back again from an ignoreFallbacks should be a no-op, return this
		assertTrue(nonEmptyIgnoringFallbacks === nonEmptyIgnoringFallbacks.withFallback(empty))
		assertTrue(nonEmptyIgnoringFallbacks === nonEmptyIgnoringFallbacks.withFallback(primitive))
		assertTrue(emptyIgnoringFallbacks === emptyIgnoringFallbacks.withFallback(empty))
		assertTrue(emptyIgnoringFallbacks === emptyIgnoringFallbacks.withFallback(primitive))
	}

	private fun runFallbackTest(
		expected: String, source: String,
		allowUnresolved: Boolean, vararg resolvers: ConfigResolver
	): Unit {
		val unresolved = ConfigFactory.parseString(source)
		var options = ConfigResolveOptions.defaults().setAllowUnresolved(allowUnresolved)
		for (resolver in resolvers)
			options = options.appendResolver(resolver)
		val obj = unresolved.resolve(options).root()
		assertEquals(expected, obj.render(ConfigRenderOptions.concise().setJson(false)))
	}

	/**
	 * A resolver that replaces paths that start with a particular prefix with
	 * strings where that prefix has been replaced with another prefix.
	 */
	class DummyResolver(val prefix: String, val newPrefix: String, val fallback: ConfigResolver?) : ConfigResolver {

		override fun lookup(path: String): ConfigValue? {
			return if (path.startsWith(prefix))
				ConfigValueFactory.fromAnyRef(newPrefix + path.substring(prefix.length))
			else fallback?.lookup(path)
		}

		override fun withFallback(f: ConfigResolver): ConfigResolver {
			return if (fallback == null)
				DummyResolver(prefix, newPrefix, f)
			else
				DummyResolver(prefix, newPrefix, fallback.withFallback(f))
		}

	}

}
