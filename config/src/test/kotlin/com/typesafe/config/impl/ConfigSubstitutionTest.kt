/**
 * Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl


import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigResolveOptions
import com.typesafe.config.SystemOverride
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test


class ConfigSubstitutionTest : TestUtils() {

	private val simpleObject = parseObject(
		"""|
           |{
           |   "foo" : 42,
           |   "bar" : {
           |       "int" : 43,
           |       "bool" : true,
           |       "null" : null,
           |       "string" : "hello",
           |       "double" : 3.14
           |    }
           |}
           |""".trimMargin()
	)

	private val substChainObject =
		parseObject("\n{\n    \"foo\" : \${bar},\n    \"bar\" : \${a.b.c},\n    \"a\" : { \"b\" : { \"c\" : 57 } }\n}\n")

	private val substCycleObject =
		parseObject("\n{\n    \"foo\" : \${bar},\n    \"bar\" : \${a.b.c},\n    \"a\" : { \"b\" : { \"c\" : \${foo} } }\n}\n")

	// ALL the links have to be optional here for the cycle to be ignored
	private val substCycleObjectOptionalLink =
		parseObject("\n{\n    \"foo\" : \${?bar},\n    \"bar\" : \${?a.b.c},\n    \"a\" : { \"b\" : { \"c\" : \${?foo} } }\n}\n")

	private val substSideEffectCycle =
		parseObject("\n{\n    \"foo\" : \${a.b.c},\n    \"a\" : { \"b\" : { \"c\" : 42, \"cycle\" : \${foo} }, \"cycle\" : \${foo} }\n}\n")

	private val delayedMergeObjectResolveProblem1 = parseObject(
		"\n  defaults {\n    a = 1\n    b = 2\n  }\n  // make item1 into a ConfigDelayedMergeObject\n  item1 = \${defaults}\n  // note that we'll resolve to a non-object value\n  // so item1.b will ignoreFallbacks and not depend on\n  // \${defaults}\n  item1.b = 3\n  // be sure we can resolve a substitution to a value in\n  // a delayed-merge object.\n  item2.b = \${item1.b}\n"
	)

	private val delayedMergeObjectResolveProblem2 =
		parseObject("\n  defaults {\n    a = 1\n    b = 2\n  }\n  // make item1 into a ConfigDelayedMergeObject\n  item1 = \${defaults}\n  // note that we'll resolve to an object value\n  // so item1.b will depend on also looking up \${defaults}\n  item1.b { c : 43 }\n  // be sure we can resolve a substitution to a value in\n  // a delayed-merge object.\n  item2.b = \${item1.b}\n")

	// in this case, item1 is self-referential because
	// it refers to ${defaults} which refers back to
	// ${item1}. When self-referencing, only the
	// value of ${item1} "looking back" should be
	// visible. This is really a test of the
	// self-referencing semantics.
	private val delayedMergeObjectResolveProblem3 =
		parseObject(
			"\n  item1.b.c = 100\n  defaults {\n    // we depend on item1.b.c\n    a = \${item1.b.c}\n    b = 2\n  }\n  // make item1 into a ConfigDelayedMergeObject\n  item1 = \${defaults}\n  // the \${item1.b.c} above in \${defaults} should ignore\n  // this because it only looks back\n  item1.b { c : 43 }\n  // be sure we can resolve a substitution to a value in\n  // a delayed-merge object.\n  item2.b = \${item1.b}\n"
		)

	private val delayedMergeObjectResolveProblem4 =
		parseObject("\n  defaults {\n    a = 1\n    b = 2\n  }\n\n  item1.b = 7\n  // make item1 into a ConfigDelayedMerge\n  item1 = \${defaults}\n  // be sure we can resolve a substitution to a value in\n  // a delayed-merge object.\n  item2.b = \${item1.b}\n")

	private val delayedMergeObjectResolveProblem5 =
		parseObject(
			"\n  defaults {\n    a = \${item1.b} // tricky cycle - we won't see \${defaults}\n                   // as we resolve this\n    b = 2\n  }\n\n  item1.b = 7\n  // make item1 into a ConfigDelayedMerge\n  item1 = \${defaults}\n  // be sure we can resolve a substitution to a value in\n  // a delayed-merge object.\n  item2.b = \${item1.b}\n"
		)

	private val delayedMergeObjectResolveProblem6 =
		parseObject(
			"""|
               |  z = 15
               |  defaults-defaults-defaults {
               |    m = ${"$"}{z}
               |    n.o.p = ${"$"}{z}
               |  }
               |  defaults-defaults {
               |    x = 10
               |    y = 11
               |    asdf = ${"$"}{z}
               |  }
               |  defaults {
               |    a = 1
               |    b = 2
               |  }
               |  defaults-alias = ${"$"}{defaults}
               |  // make item1 into a ConfigDelayedMergeObject several layers deep
               |  // that will NOT become resolved just because we resolve one path
               |  // through it.
               |  item1 = 345
               |  item1 = ${"$"}{?NONEXISTENT}
               |  item1 = ${"$"}{defaults-defaults-defaults}
               |  item1 {}
               |  item1 = ${"$"}{defaults-defaults}
               |  item1 = ${"$"}{defaults-alias}
               |  item1 = ${"$"}{defaults}
               |  item1.b { c : 43 }
               |  item1.xyz = 101
               |  // be sure we can resolve a substitution to a value in
               |  // a delayed-merge object.
               |  item2.b = ${"$"}{item1.b}
               |""".trimMargin()
		)

	private val delayedMergeObjectWithKnownValue =
		parseObject(
			"\n  defaults {\n    a = 1\n    b = 2\n  }\n  // make item1 into a ConfigDelayedMergeObject\n  item1 = \${defaults}\n  // note that we'll resolve to a non-object value\n  // so item1.b will ignoreFallbacks and not depend on\n  // \${defaults}\n  item1.b = 3\n"
		)

	private val delayedMergeObjectNeedsFullResolve =
		parseObject(
			"\n  defaults {\n    a = 1\n    b { c : 31 }\n  }\n  item1 = \${defaults}\n  // because b is an object, fetching it requires resolving \${defaults} above\n  // to see if there are more keys to merge with b.\n  item1.b { c : 41 }\n"
		)

	// objects that mutually refer to each other
	private val delayedMergeObjectEmbrace =
		parseObject(
			"\n  defaults {\n    a = 1\n    b = 2\n  }\n\n  item1 = \${defaults}\n  // item1.c refers to a field in item2 that refers to item1\n  item1.c = \${item2.d}\n  // item1.x refers to a field in item2 that doesn't go back to item1\n  item1.x = \${item2.y}\n\n  item2 = \${defaults}\n  // item2.d refers to a field in item1\n  item2.d = \${item1.a}\n  item2.y = 15\n"
		)

	// objects that mutually refer to each other
	private val plainObjectEmbrace =
		parseObject(
			"\n  item1.a = 10\n  item1.b = \${item2.d}\n  item2.c = 12\n  item2.d = 14\n  item2.e = \${item1.a}\n  item2.f = \${item1.b}   // item1.b goes back to item2\n  item2.g = \${item2.f}   // goes back to ourselves\n"
		)

	private val substComplexObject =
		parseObject(
			"\n{\n    \"foo\" : \${bar},\n    \"bar\" : \${a.b.c},\n    \"a\" : { \"b\" : { \"c\" : 57, \"d\" : \${foo}, \"e\" : { \"f\" : \${foo} } } },\n    \"objA\" : \${a},\n    \"objB\" : \${a.b},\n    \"objE\" : \${a.b.e},\n    \"foo.bar\" : 37,\n    \"arr\" : [ \${foo}, \${a.b.c}, \${\"foo.bar\"}, \${objB.d}, \${objA.b.e.f}, \${objE.f} ],\n    \"ptrToArr\" : \${arr},\n    \"x\" : { \"y\" : { \"ptrToPtrToArr\" : \${ptrToArr} } }\n}\n"
		)

	private val substSystemPropsObject =
		parseObject(
			"\n{\n    \"a\" : \${configtest.a},\n    \"b\" : \${configtest.b}\n}\n"
		)

	private val substEnvVarObject =
	// prefix the names of keys with "key_" to allow us to embed a case sensitive env var name
		// in the key that won't therefore risk a naming collision with env vars themselves
		parseObject(
			"\n{\n    \"key_HOME\" : \${?HOME},\n    \"key_PWD\" : \${?PWD},\n    \"key_SHELL\" : \${?SHELL},\n    \"key_LANG\" : \${?LANG},\n    \"key_PATH\" : \${?PATH},\n    \"key_Path\" : \${?Path}, // many windows machines use Path rather than PATH\n    \"key_NOT_HERE\" : \${?NOT_HERE}\n}\n"
		)


	@Test
	fun resolveTrivialKey() {
		val s = subst("foo")
		val v = resolveWithoutFallbacks(s, simpleObject)
		assertEquals(intValue(42), v)
	}

	@Test
	fun resolveTrivialPath() {
		val s = subst("bar.int")
		val v = resolveWithoutFallbacks(s, simpleObject)
		assertEquals(intValue(43), v)
	}

	@Test
	fun resolveInt() {
		val s = subst("bar.int")
		val v = resolveWithoutFallbacks(s, simpleObject)
		assertEquals(intValue(43), v)
	}

	@Test
	fun resolveBool() {
		val s = subst("bar.bool")
		val v = resolveWithoutFallbacks(s, simpleObject)
		assertEquals(boolValue(true), v)
	}

	@Test
	fun resolveNull() {
		val s = subst("bar.null")
		val v = resolveWithoutFallbacks(s, simpleObject)
		assertEquals(nullValue(), v)
	}

	@Test
	fun resolveString() {
		val s = subst("bar.string")
		val v = resolveWithoutFallbacks(s, simpleObject)
		assertEquals(stringValue("hello"), v)
	}

	@Test
	fun resolveDouble() {
		val s = subst("bar.double")
		val v = resolveWithoutFallbacks(s, simpleObject)
		assertEquals(doubleValue(3.14), v)
	}

	@Test
	fun resolveMissingThrows() {
		val e = assertThrows(ConfigException.UnresolvedSubstitution::class.java) {
			val s = subst("bar.missing")
			val v = resolveWithoutFallbacks(s, simpleObject)
		}
		assertTrue(
			!e.message!!.contains("cycle"),
			"wrong exception: " + e.message
		)
	}

	@Test
	fun resolveIntInString() {
		val s = substInString("bar.int")
		val v = resolveWithoutFallbacks(s, simpleObject)
		assertEquals(stringValue("start<43>end"), v)
	}

	@Test
	fun resolveNullInString() {
		val s = substInString("bar.null")
		val v = resolveWithoutFallbacks(s, simpleObject)
		assertEquals(stringValue("start<null>end"), v)

		// when null is NOT a subst, it should also not become empty
		val o = parseConfig("""{ "a" : null foo bar }""")
		assertEquals("null foo bar", o.getString("a"))
	}

	@Test
	fun resolveMissingInString() {
		val s = substInString("bar.missing", optional = true)
		val v = resolveWithoutFallbacks(s, simpleObject)
		// absent object becomes empty string
		assertEquals(stringValue("start<>end"), v)

		assertThrows(ConfigException.UnresolvedSubstitution::class.java) {
			val s2 = substInString("bar.missing", optional = false)
			resolveWithoutFallbacks(s2, simpleObject)
		}
	}

	@Test
	fun resolveBoolInString() {
		val s = substInString("bar.bool")
		val v = resolveWithoutFallbacks(s, simpleObject)
		assertEquals(stringValue("start<true>end"), v)
	}

	@Test
	fun resolveStringInString() {
		val s = substInString("bar.string")
		val v = resolveWithoutFallbacks(s, simpleObject)
		assertEquals(stringValue("start<hello>end"), v)
	}

	@Test
	fun resolveDoubleInString() {
		val s = substInString("bar.double")
		val v = resolveWithoutFallbacks(s, simpleObject)
		assertEquals(stringValue("start<3.14>end"), v)
	}

	@Test
	fun missingInArray() {

		val obj = parseObject(
			"\n    a : [ \${?missing}, \${?also.missing} ]\n"
		)

		val resolved = resolve(obj)

		assertEquals(emptyList<Any>(), resolved.getList("a"))
	}

	@Test
	fun missingInObject() {

		val obj = parseObject(
			"\n    a : \${?missing}, b : \${?also.missing}, c : \${?b}, d : \${?c}\n"
		)

		val resolved = resolve(obj)

		assertTrue(resolved.isEmpty)
	}

	@Test
	fun chainSubstitutions() {
		val s = subst("foo")
		val v = resolveWithoutFallbacks(s, substChainObject)
		assertEquals(intValue(57), v)
	}

	@Test
	fun substitutionsLookForward() {
		val obj = parseObject("a=1,b=\${a},a=2")
		val resolved = resolve(obj)
		assertEquals(2, resolved.getInt("b"))
	}

	@Test
	fun throwOnIncrediblyTrivialCycle() {
		val s = subst("a")
		val e = assertThrows(ConfigException.UnresolvedSubstitution::class.java) {
			val v = resolveWithoutFallbacks(s, parseObject("a: \${a}"))
		}
		assertTrue(e.message!!.contains("cycle"), "Wrong exception: " + e.message)
		assertTrue(e.message!!.contains("\${a}"), "Wrong exception: " + e.message)
	}

	@Test
	fun throwOnCycles() {
		val s = subst("foo")
		val e = assertThrows(ConfigException.UnresolvedSubstitution::class.java) {
			val v = resolveWithoutFallbacks(s, substCycleObject)
		}
		assertTrue(e.message!!.contains("cycle"), "Wrong exception: " + e.message)
		assertTrue(e.message!!.contains("\${foo}, \${bar}, \${a.b.c}, \${foo}"), "Wrong exception: " + e.message)
	}

	@Test
	fun throwOnOptionalReferenceToNonOptionalCycle() {
		// we look up ${?foo}, but the cycle has hard
		// non-optional links in it so still has to throw.
		val s = subst("foo", optional = true)
		val e = assertThrows(ConfigException.UnresolvedSubstitution::class.java) {
			val v = resolveWithoutFallbacks(s, substCycleObject)
		}
		assertTrue(e.message!!.contains("cycle"), "Wrong exception: " + e.message)
	}

	@Test
	fun optionalLinkCyclesActLikeUndefined() {
		val s = subst("foo", optional = true)
		val v = resolveWithoutFallbacks(s, substCycleObjectOptionalLink)
		assertNull(v, "Cycle with optional links in it resolves to null if it's a cycle")
	}

	@Test
	fun throwOnTwoKeyCycle() {
		val obj = parseObject("a:\${b},b:\${a}")
		val e = assertThrows(ConfigException.UnresolvedSubstitution::class.java) {
			resolve(obj)
		}
		assertTrue(e.message!!.contains("cycle"), "Wrong exception: " + e.message)
	}

	@Test
	fun throwOnFourKeyCycle() {
		val obj = parseObject("a:\${b},b:\${c},c:\${d},d:\${a}")
		val e = assertThrows(ConfigException.UnresolvedSubstitution::class.java) {
			resolve(obj)
		}
		assertTrue(e.message!!.contains("cycle"), "Wrong exception: " + e.message)
	}

	@Test
	fun resolveObject() {
		val resolved = resolveWithoutFallbacks(substChainObject)
		assertEquals(57, resolved.getInt("foo"))
		assertEquals(57, resolved.getInt("bar"))
		assertEquals(57, resolved.getInt("a.b.c"))
	}

	@Test
	fun avoidSideEffectCycles() {
		// The point of this test is that in traversing objects
		// to resolve a path, we need to avoid resolving
		// substitutions that are in the traversed objects but
		// are not directly required to resolve the path.
		// i.e. there should not be a cycle in this test.

		val resolved = resolveWithoutFallbacks(substSideEffectCycle)

		assertEquals(42, resolved.getInt("foo"))
		assertEquals(42, resolved.getInt("a.b.cycle"))
		assertEquals(42, resolved.getInt("a.cycle"))
	}

	@Test
	fun ignoreHiddenUndefinedSubst() {
		// if a substitution is overridden then it shouldn't matter that it's undefined
		val obj = parseObject("a=\${nonexistent},a=42")
		val resolved = resolve(obj)
		assertEquals(42, resolved.getInt("a"))
	}

	@Test
	fun objectDoesNotHideUndefinedSubst() {
		// if a substitution is overridden by an object we still need to
		// evaluate the substitution
		val obj = parseObject("a=\${nonexistent},a={ b : 42 }")
		val e = assertThrows(ConfigException.UnresolvedSubstitution::class.java) {
			resolve(obj)
		}
		assertTrue(e.message!!.contains("Could not resolve"), "wrong exception: " + e.message)
	}

	@Test
	fun ignoreHiddenCircularSubst() {
		// if a substitution is overridden then it shouldn't matter that it's circular
		val obj = parseObject("a=\${a},a=42")
		val resolved = resolve(obj)
		assertEquals(42, resolved.getInt("a"))
	}

	@Test
	fun avoidDelayedMergeObjectResolveProblem1() {
		assertTrue(delayedMergeObjectResolveProblem1.attemptPeekWithPartialResolve("item1") is ConfigDelayedMergeObject)

		val resolved = resolveWithoutFallbacks(delayedMergeObjectResolveProblem1)

		assertEquals(3, resolved.getInt("item1.b"))
		assertEquals(3, resolved.getInt("item2.b"))
	}

	@Test
	fun avoidDelayedMergeObjectResolveProblem2() {
		assertTrue(delayedMergeObjectResolveProblem2.attemptPeekWithPartialResolve("item1") is ConfigDelayedMergeObject)

		val resolved = resolveWithoutFallbacks(delayedMergeObjectResolveProblem2)

		assertEquals(parseObject("{ c : 43 }"), resolved.getObject("item1.b"))
		assertEquals(43, resolved.getInt("item1.b.c"))
		assertEquals(43, resolved.getInt("item2.b.c"))
	}

	@Test
	fun avoidDelayedMergeObjectResolveProblem3() {
		assertTrue(delayedMergeObjectResolveProblem3.attemptPeekWithPartialResolve("item1") is ConfigDelayedMergeObject)

		val resolved = resolveWithoutFallbacks(delayedMergeObjectResolveProblem3)

		assertEquals(parseObject("{ c : 43 }"), resolved.getObject("item1.b"))
		assertEquals(43, resolved.getInt("item1.b.c"))
		assertEquals(43, resolved.getInt("item2.b.c"))
		assertEquals(100, resolved.getInt("defaults.a"))
	}

	@Test
	fun avoidDelayedMergeObjectResolveProblem4() {
		// in this case we have a ConfigDelayedMerge not a ConfigDelayedMergeObject
		assertTrue(delayedMergeObjectResolveProblem4.attemptPeekWithPartialResolve("item1") is ConfigDelayedMerge)

		val resolved = resolveWithoutFallbacks(delayedMergeObjectResolveProblem4)

		assertEquals(2, resolved.getInt("item1.b"))
		assertEquals(2, resolved.getInt("item2.b"))
	}

	@Test
	fun avoidDelayedMergeObjectResolveProblem5() {
		// in this case we have a ConfigDelayedMerge not a ConfigDelayedMergeObject
		assertTrue(delayedMergeObjectResolveProblem5.attemptPeekWithPartialResolve("item1") is ConfigDelayedMerge)

		val resolved = resolveWithoutFallbacks(delayedMergeObjectResolveProblem5)

		assertEquals(2, resolved.getInt("item1.b"), "item1.b")
		assertEquals(2, resolved.getInt("item2.b"), "item2.b")
		assertEquals(7, resolved.getInt("defaults.a"), "defaults.a")
	}

	@Test
	fun avoidDelayedMergeObjectResolveProblem6() {
		assertTrue(delayedMergeObjectResolveProblem6.attemptPeekWithPartialResolve("item1") is ConfigDelayedMergeObject)

		// should be able to attemptPeekWithPartialResolve() a known non-object without resolving
		assertEquals(
			101,
			delayedMergeObjectResolveProblem6.toConfig().getObject("item1").attemptPeekWithPartialResolve("xyz")
				.unwrapped()
		)

		val resolved = resolveWithoutFallbacks(delayedMergeObjectResolveProblem6)

		assertEquals(parseObject("{ c : 43 }"), resolved.getObject("item1.b"))
		assertEquals(43, resolved.getInt("item1.b.c"))
		assertEquals(43, resolved.getInt("item2.b.c"))
		assertEquals(15, resolved.getInt("item1.n.o.p"))
	}

	@Test
	fun fetchKnownValueFromDelayedMergeObject() {
		assertTrue(delayedMergeObjectWithKnownValue.attemptPeekWithPartialResolve("item1") is ConfigDelayedMergeObject)

		assertEquals(3, delayedMergeObjectWithKnownValue.toConfig().getConfig("item1").getInt("b"))
	}

	@Test
	fun failToFetchFromDelayedMergeObjectNeedsFullResolve() {
		assertTrue(delayedMergeObjectWithKnownValue.attemptPeekWithPartialResolve("item1") is ConfigDelayedMergeObject)

		val e = assertThrows(ConfigException.NotResolved::class.java) {
			delayedMergeObjectNeedsFullResolve.toConfig().getObject("item1.b")
		}

		assertTrue(e.message!!.contains("item1.b"), "wrong exception: " + e.message)
	}

	@Test
	fun resolveDelayedMergeObjectEmbrace() {
		assertTrue(delayedMergeObjectEmbrace.attemptPeekWithPartialResolve("item1") is ConfigDelayedMergeObject)
		assertTrue(delayedMergeObjectEmbrace.attemptPeekWithPartialResolve("item2") is ConfigDelayedMergeObject)

		val resolved = delayedMergeObjectEmbrace.toConfig().resolve()
		assertEquals(1, resolved.getInt("item1.c"))
		assertEquals(1, resolved.getInt("item2.d"))
		assertEquals(15, resolved.getInt("item1.x"))
	}

	@Test
	fun resolvePlainObjectEmbrace() {
		assertTrue(plainObjectEmbrace.attemptPeekWithPartialResolve("item1") is SimpleConfigObject)
		assertTrue(plainObjectEmbrace.attemptPeekWithPartialResolve("item2") is SimpleConfigObject)

		val resolved = plainObjectEmbrace.toConfig().resolve()
		assertEquals(14, resolved.getInt("item1.b"))
		assertEquals(10, resolved.getInt("item2.e"))
		assertEquals(14, resolved.getInt("item2.f"))
		assertEquals(14, resolved.getInt("item2.g"))
	}

	@Test
	fun useRelativeToSameFileWhenRelativized() {
		val child = parseObject("foo=in child,bar=\${foo}")

		val values = mapOf("a" to child.relativized(Path.of("a")), "foo" to stringValue("in parent"))


		val resolved = resolve(SimpleConfigObject(fakeOrigin(), values))

		assertEquals("in child", resolved.getString("a.bar"))
	}

	@Test
	fun useRelativeToRootWhenRelativized() {
		// here, "foo" is not defined in the child
		val child = parseObject("bar=\${foo}")

		val values = mapOf("a" to child.relativized(Path.of("a")), "foo" to stringValue("in parent"))

		val resolved = resolve(SimpleConfigObject(fakeOrigin(), values))

		assertEquals("in parent", resolved.getString("a.bar"))
	}

	@Test
	fun complexResolve() {


		val resolved = resolveWithoutFallbacks(substComplexObject)

		assertEquals(57, resolved.getInt("foo"))
		assertEquals(57, resolved.getInt("bar"))
		assertEquals(57, resolved.getInt("a.b.c"))
		assertEquals(57, resolved.getInt("a.b.d"))
		assertEquals(57, resolved.getInt("objB.d"))
		assertEquals(listOf(57, 57, 37, 57, 57, 57), resolved.getIntList("arr"))
		assertEquals(listOf(57, 57, 37, 57, 57, 57), resolved.getIntList("ptrToArr"))
		assertEquals(listOf(57, 57, 37, 57, 57, 57), resolved.getIntList("x.y.ptrToPtrToArr"))
	}

	@Test
	fun doNotSerializeUnresolvedObject() {
		checkNotSerializable(substComplexObject)
	}

	@Test
	fun resolveListFromSystemProps() {
		val props = parseObject(
			"\n              \"a\": \${testList}\n            "
		)

		val systemProperties = mapOf(
			"testList.0" to "0",
			"testList.1" to "1"
		)

		val resolved = SystemOverride.withSystemOverride(
			systemProperties,
			mapOf<String, String>(),
			System.err
		) {
			ConfigImpl.reloadSystemPropertiesConfig()
			resolve(ConfigFactory.systemProperties().withFallback(props).root() as AbstractConfigObject)
		}

		assertEquals(listOf("0", "1"), resolved.getList("a").unwrapped())
	}

	@Test
	fun resolveListFromEnvVars() {
		val props = parseObject(
			"\n              \"a\": \${testList}\n            "
		)

		//"testList.0" and "testList.1" are defined as envVars in build.sbt
		val resolved = resolve(props)

		assertEquals(listOf("0", "1"), resolved.getList("a").unwrapped())
	}

	// this is a weird test, it used to test fallback to system props which made more sense.
	// Now it just tests that if you override with system props, you can use system props
	// in substitutions.
	@Test
	fun overrideWithSystemProps() {
		System.setProperty("configtest.a", "1234")
		System.setProperty("configtest.b", "5678")
		ConfigImpl.reloadSystemPropertiesConfig()

		val resolved = resolve(
			ConfigFactory.systemProperties().withFallback(substSystemPropsObject).root() as AbstractConfigObject
		)

		assertEquals("1234", resolved.getString("a"))
		assertEquals("5678", resolved.getString("b"))
	}

	@Test
	fun fallbackToEnv() {

		val resolved = resolve(substEnvVarObject)

		var existed = 0
		for (k in resolved.root().keys) {
			val envVarName = k.replace("key_", "")
			val e = System.getenv(envVarName)
			if (e != null) {
				existed += 1
				assertEquals(e, resolved.getString(k))
			} else {
				assertNull(resolved.root()[k])
			}
		}
		if (existed == 0) {
			throw Exception("None of the env vars we tried to use for testing were set")
		}
	}

	@Test
	fun noFallbackToEnvIfValuesAreNull() {


		// create a fallback object with all the env var names
		// set to null. we want to be sure this blocks
		// lookup in the environment. i.e. if there is a
		// { HOME : null } then ${HOME} should be null.
		val nullsMap: Map<String, Any?> = substEnvVarObject.keys.associate { k ->
			k.replace("key_", "") to null
		}

		val nulls = ConfigFactory.parseMap(nullsMap, "nulls map")

		val resolved = resolve(substEnvVarObject.withFallback(nulls))

		for (k in resolved.root().keys) {
			assertNotNull(resolved.root().get(k))
			assertEquals(nullValue(), resolved.root().get(k))
		}
	}

	@Test
	fun fallbackToEnvWhenRelativized() {

		val values = mapOf("a" to substEnvVarObject.relativized(Path.of("a")))

		val resolved = resolve(SimpleConfigObject(fakeOrigin(), values))

		var existed = 0
		for (k in resolved.getObject("a").keys) {
			val envVarName = k.replace("key_", "")
			val e = System.getenv(envVarName)
			if (e != null) {
				existed += 1
				assertEquals(e, resolved.getConfig("a").getString(k))
			} else {
				assertNull(resolved.getObject("a").get(k))
			}
		}
		if (existed == 0) {
			throw Exception("None of the env vars we tried to use for testing were set")
		}
	}

	@Test
	fun throwWhenEnvNotFound() {
		val obj = parseObject("{ a : \${NOT_HERE} }")
		assertThrows(ConfigException.UnresolvedSubstitution::class.java) {
			resolve(obj)
		}
	}

	@Test
	fun optionalOverrideNotProvided() {
		val obj = parseObject("{ a: 42, a : \${?NOT_HERE} }")
		val resolved = resolve(obj)
		assertEquals(42, resolved.getInt("a"))
	}

	@Test
	fun optionalOverrideProvided() {
		val obj = parseObject("{ HERE : 43, a: 42, a : \${?HERE} }")
		val resolved = resolve(obj)
		assertEquals(43, resolved.getInt("a"))
	}

	@Test
	fun optionalOverrideOfObjectNotProvided() {
		val obj = parseObject("{ a: { b : 42 }, a : \${?NOT_HERE} }")
		val resolved = resolve(obj)
		assertEquals(42, resolved.getInt("a.b"))
	}

	@Test
	fun optionalOverrideOfObjectProvided() {
		val obj = parseObject("{ HERE : 43, a: { b : 42 }, a : \${?HERE} }")
		val resolved = resolve(obj)
		assertEquals(43, resolved.getInt("a"))
		assertFalse(resolved.hasPath("a.b"))
	}

	@Test
	fun optionalVanishesFromArray() {

		val obj = parseObject("{ a : [ 1, 2, 3, \${?NOT_HERE} ] }")
		val resolved = resolve(obj)
		assertEquals(listOf(1, 2, 3), resolved.getIntList("a"))
	}

	@Test
	fun optionalUsedInArray() {
		val obj = parseObject("{ HERE: 4, a : [ 1, 2, 3, \${?HERE} ] }")
		val resolved = resolve(obj)
		assertEquals(listOf(1, 2, 3, 4), resolved.getIntList("a"))
	}

	@Test
	fun substSelfReference() {
		val obj = parseObject("a=1, a=\${a}")
		val resolved = resolve(obj)
		assertEquals(1, resolved.getInt("a"))
	}

	@Test
	fun substSelfReferenceUndefined() {
		val obj = parseObject("a=\${a}")
		val e = assertThrows(ConfigException.UnresolvedSubstitution::class.java) {
			resolve(obj)
		}
		assertTrue(e.message!!.contains("cycle"), "wrong exception: " + e.message)
	}

	@Test
	fun substSelfReferenceOptional() {
		val obj = parseObject("a=\${?a}")
		val resolved = resolve(obj)
		assertEquals(0, resolved.root().size, "optional self reference disappears")
	}

	@Test
	fun substSelfReferenceAlongPath() {
		val obj = parseObject("a.b=1, a.b=\${a.b}")
		val resolved = resolve(obj)
		assertEquals(1, resolved.getInt("a.b"))
	}

	@Test
	fun substSelfReferenceAlongLongerPath() {
		val obj = parseObject("a.b.c=1, a.b.c=\${a.b.c}")
		val resolved = resolve(obj)
		assertEquals(1, resolved.getInt("a.b.c"))
	}

	@Test
	fun substSelfReferenceAlongPathMoreComplex() {
		// this is an example from the spec
		val obj = parseObject(
			"\n    foo : { a : { c : 1 } }\n    foo : \${foo.a}\n    foo : { a : 2 }\n                "
		)
		val resolved = resolve(obj)
		assertEquals(1, resolved.getInt("foo.c"))
		assertEquals(2, resolved.getInt("foo.a"))
	}

	@Test
	fun substSelfReferenceIndirect() {
		// this has two possible outcomes depending on whether
		// we resolve and memoize a first or b first. currently
		// java 8's hash table makes it resolve OK, but
		// it's also allowed to throw an exception.
		val obj = parseObject("a=1, b=\${a}, a=\${b}")
		val resolved = resolve(obj)
		assertEquals(1, resolved.getInt("a"))
	}

	@Test
	fun substSelfReferenceDoubleIndirect() {
		// this has two possible outcomes depending on whether we
		// resolve and memoize a, b, or c first. currently java
		// 8's hash table makes it resolve OK, but it's also
		// allowed to throw an exception.
		val obj = parseObject("a=1, b=\${c}, c=\${a}, a=\${b}")
		val resolved = resolve(obj)
		assertEquals(1, resolved.getInt("a"))
	}

	@Test
	fun substSelfReferenceIndirectStackCycle() {
		// this situation is undefined, depends on
		// whether we resolve a or b first.
		val obj = parseObject("a=1, b={c=5}, b=\${a}, a=\${b}")
		val resolved = resolve(obj)
		val option1 = parseObject(""" b={c=5}, a={c=5} """).toConfig()
		val option2 = parseObject(""" b=1, a=1 """).toConfig()
		assertTrue(
			resolved == option1 || resolved == option2,
			"not an expected possibility: " + resolved +
					" expected 1: " + option1 + " or 2: " + option2
		)
	}

	@Test
	fun substSelfReferenceObject() {
		val obj = parseObject("a={b=5}, a=\${a}")
		val resolved = resolve(obj)
		assertEquals(5, resolved.getInt("a.b"))
	}

	@Test
	fun substSelfReferenceObjectAlongPath() {
		val obj = parseObject("a.b={c=5}, a.b=\${a.b}")
		val resolved = resolve(obj)
		assertEquals(5, resolved.getInt("a.b.c"))
	}

	@Test
	fun substSelfReferenceInConcat() {
		val obj = parseObject("a=1, a=\${a}foo")
		val resolved = resolve(obj)
		assertEquals("1foo", resolved.getString("a"))
	}

	@Test
	fun substSelfReferenceIndirectInConcat() {
		// this situation is undefined, depends on
		// whether we resolve a or b first. If b first
		// then there's an error because ${a} is undefined.
		// if a first then b=1foo and a=1foo.
		val obj = parseObject("a=1, b=\${a}foo, a=\${b}")

		val either = try {
			resolve(obj)
		} catch (e: ConfigException.UnresolvedSubstitution) {
			e
		}

		val option1 = parseObject("""a:1foo,b:1foo""").toConfig()
		assertTrue(
			either == option1 || either is ConfigException.UnresolvedSubstitution,
			"not an expected possibility: " + either +
					" expected value " + option1 + " or an exception"
		)
	}

	@Test
	fun substOptionalSelfReferenceInConcat() {
		val obj = parseObject("a=\${?a}foo")
		val resolved = resolve(obj)
		assertEquals("foo", resolved.getString("a"))
	}

	@Test
	fun substOptionalIndirectSelfReferenceInConcat() {
		val obj = parseObject("a=\${?b}foo,b=\${?a}")
		val resolved = resolve(obj)
		assertEquals("foo", resolved.getString("a"))
	}

	@Test
	fun substTwoOptionalSelfReferencesInConcat() {
		val obj = parseObject("a=\${?a}foo\${?a}")
		val resolved = resolve(obj)
		assertEquals("foo", resolved.getString("a"))
	}

	@Test
	fun substTwoOptionalSelfReferencesInConcatWithPriorValue() {
		val obj = parseObject("a=1,a=\${?a}foo\${?a}")
		val resolved = resolve(obj)
		assertEquals("1foo1", resolved.getString("a"))
	}

	@Test
	fun substSelfReferenceMiddleOfStack() {
		val obj = parseObject("a=1, a=\${a}, a=2")
		val resolved = resolve(obj)
		// the substitution would be 1, but then 2 overrides
		assertEquals(2, resolved.getInt("a"))
	}

	@Test
	fun substSelfReferenceObjectMiddleOfStack() {
		val obj = parseObject("a={b=5}, a=\${a}, a={c=6}")
		val resolved = resolve(obj)
		assertEquals(5, resolved.getInt("a.b"))
		assertEquals(6, resolved.getInt("a.c"))
	}

	@Test
	fun substOptionalSelfReferenceMiddleOfStack() {
		val obj = parseObject("a=1, a=\${?a}, a=2")
		val resolved = resolve(obj)
		// the substitution would be 1, but then 2 overrides
		assertEquals(2, resolved.getInt("a"))
	}

	@Test
	fun substSelfReferenceBottomOfStack() {
		// self-reference should just be ignored since it's
		// overridden
		val obj = parseObject("a=\${a}, a=1, a=2")
		val resolved = resolve(obj)
		assertEquals(2, resolved.getInt("a"))
	}

	@Test
	fun substOptionalSelfReferenceBottomOfStack() {
		val obj = parseObject("a=\${?a}, a=1, a=2")
		val resolved = resolve(obj)
		assertEquals(2, resolved.getInt("a"))
	}

	@Test
	fun substSelfReferenceTopOfStack() {
		val obj = parseObject("a=1, a=2, a=\${a}")
		val resolved = resolve(obj)
		assertEquals(2, resolved.getInt("a"))
	}

	@Test
	fun substOptionalSelfReferenceTopOfStack() {
		val obj = parseObject("a=1, a=2, a=\${?a}")
		val resolved = resolve(obj)
		assertEquals(2, resolved.getInt("a"))
	}

	@Test
	fun substSelfReferenceAlongAPath() {
		// ${a} in the middle of the stack means "${a} in the stack
		// below us" and so ${a.b} means b inside the "${a} below us"
		// not b inside the final "${a}"
		val obj = parseObject("a={b={c=5}}, a=\${a.b}, a={b=2}")
		val resolved = resolve(obj)
		assertEquals(5, resolved.getInt("a.c"))
	}

	@Test
	fun substSelfReferenceAlongAPathInsideObject() {
		// if the ${a.b} is _inside_ a field value instead of
		// _being_ the field value, it does not look backward.
		val obj = parseObject("a={b={c=5}}, a={ x : \${a.b} }, a={b=2}")
		val resolved = resolve(obj)
		assertEquals(2, resolved.getInt("a.x"))
	}

	@Test
	fun substInChildFieldNotASelfReference1() {
		// here, ${bar.foo} is not a self reference because
		// it's the value of a child field of bar, not bar
		// itself; so we use bar's current value, rather than
		// looking back in the merge stack
		val obj = parseObject("bar : { foo : 42,  \nbaz : \${bar.foo}\n}\n")
		val resolved = resolve(obj)
		assertEquals(42, resolved.getInt("bar.baz"))
		assertEquals(42, resolved.getInt("bar.foo"))
	}

	@Test
	fun substInChildFieldNotASelfReference2() {
		// checking that having bar.foo later in the stack
		// doesn't break the behavior
		val obj = parseObject(
			"\n         bar : { foo : 42,\n                 baz : \${bar.foo}\n         }\n         bar : { foo : 43 }\n            "
		)
		val resolved = resolve(obj)
		assertEquals(43, resolved.getInt("bar.baz"))
		assertEquals(43, resolved.getInt("bar.foo"))
	}

	@Test
	fun substInChildFieldNotASelfReference3() {
		// checking that having bar.foo earlier in the merge
		// stack doesn't break the behavior.
		val obj = parseObject(
			"\n         bar : { foo : 43 }\n         bar : { foo : 42,\n                 baz : \${bar.foo}\n         }\n            "
		)
		val resolved = resolve(obj)
		assertEquals(42, resolved.getInt("bar.baz"))
		assertEquals(42, resolved.getInt("bar.foo"))
	}

	@Test
	fun substInChildFieldNotASelfReference4() {
		// checking that having bar set to non-object earlier
		// doesn't break the behavior.
		val obj = parseObject(
			"\n         bar : 101\n         bar : { foo : 42,\n                 baz : \${bar.foo}\n         }\n            "
		)
		val resolved = resolve(obj)
		assertEquals(42, resolved.getInt("bar.baz"))
		assertEquals(42, resolved.getInt("bar.foo"))
	}

	@Test
	fun substInChildFieldNotASelfReference5() {
		// checking that having bar set to unresolved array earlier
		// doesn't break the behavior.
		val obj = parseObject(
			"\n         x : 0\n         bar : [ \${x}, 1, 2, 3 ]\n         bar : { foo : 42,\n                 baz : \${bar.foo}\n         }\n            "
		)
		val resolved = resolve(obj)
		assertEquals(42, resolved.getInt("bar.baz"))
		assertEquals(42, resolved.getInt("bar.foo"))
	}

	@Test
	fun mutuallyReferringNotASelfReference() {
		val obj = parseObject(
			"\n    // bar.a should end up as 4\n    bar : { a : \${foo.d}, b : 1 }\n    bar.b = 3\n    // foo.c should end up as 3\n    foo : { c : \${bar.b}, d : 2 }\n    foo.d = 4\n                "
		)
		val resolved = resolve(obj)
		assertEquals(4, resolved.getInt("bar.a"))
		assertEquals(3, resolved.getInt("foo.c"))
	}

	@Test
	fun substSelfReferenceMultipleTimes() {
		val obj = parseObject("a=1,a=\${a},a=\${a},a=\${a}")
		val resolved = resolve(obj)
		assertEquals(1, resolved.getInt("a"))
	}

	@Test
	fun substSelfReferenceInConcatMultipleTimes() {
		val obj = parseObject("a=1,a=\${a}x,a=\${a}y,a=\${a}z")
		val resolved = resolve(obj)
		assertEquals("1xyz", resolved.getString("a"))
	}

	@Test
	fun substSelfReferenceInArray() {
		// never "look back" from "inside" an array
		val obj = parseObject("a=1,a=[\${a}, 2]")
		val e = assertThrows(ConfigException.UnresolvedSubstitution::class.java) {
			resolve(obj)
		}
		assertTrue(
			e.message!!.contains("cycle") && e.message!!.contains("\${a}"),
			"wrong exception: " + e.message
		)
	}

	@Test
	fun substSelfReferenceInObject() {
		// never "look back" from "inside" an object
		val obj = parseObject("a=1,a={ x : \${a} }")
		val e = assertThrows(ConfigException.UnresolvedSubstitution::class.java) {
			resolve(obj)
		}
		assertTrue(
			e.message!!.contains("cycle") && e.message!!.contains("\${a}"),
			"wrong exception: " + e.message
		)
	}

	@Test
	fun selfReferentialObjectNotAffectedByOverriding() {
		// this is testing that we can still refer to another
		// field in the same object, even though we are overriding
		// an earlier object.
		val obj = parseObject("a={ x : 42, y : \${a.x} }")
		val resolved = resolve(obj)
		assertEquals(parseObject("{ x : 42, y : 42 }"), resolved.getConfig("a").root())

		// this is expected because if adding "a=1" here affects the outcome,
		// it would be flat-out bizarre.
		val obj2 = parseObject("a=1, a={ x : 42, y : \${a.x} }")
		val resolved2 = resolve(obj2)
		assertEquals(parseObject("{ x : 42, y : 42 }"), resolved2.getConfig("a").root())
	}

	private fun resolveWithoutFallbacks(v: AbstractConfigObject): SimpleConfig {
		val options = ConfigResolveOptions.noSystem()
		return (ResolveContext.resolve(v, v, options) as AbstractConfigObject).toConfig()
	}

	private fun resolveWithoutFallbacks(s: AbstractConfigValue, root: AbstractConfigObject): AbstractConfigValue? {
		val options = ConfigResolveOptions.noSystem()
		return ResolveContext.resolve(s, root, options)
	}

	private fun resolve(v: AbstractConfigObject): SimpleConfig {
		val options = ConfigResolveOptions.defaults()
		val result = ResolveContext.resolve(v, v, options);
		return (result as AbstractConfigObject).toConfig()
	}

	private fun resolve(s: AbstractConfigValue, root: AbstractConfigObject): AbstractConfigValue? {
		val options = ConfigResolveOptions.defaults()
		return ResolveContext.resolve(s, root, options)
	}
}
