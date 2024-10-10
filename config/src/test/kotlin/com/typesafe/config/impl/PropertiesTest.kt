/**
 * Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

class PropertiesTest : TestUtils() {
	@Test
	fun pathSplitting() {
		fun last(s: String) = PropertiesParser.lastElement(s)

		fun exceptLast(s: String) = PropertiesParser.exceptLastElement(s)

		assertEquals("a", last("a"))
		assertNull(exceptLast("a"))

		assertEquals("b", last("a.b"))
		assertEquals("a", exceptLast("a.b"))

		assertEquals("c", last("a.b.c"))
		assertEquals("a.b", exceptLast("a.b.c"))

		assertEquals("", last(""))
		assertNull(null, exceptLast(""))

		assertEquals("", last("."))
		assertEquals("", exceptLast("."))

		assertEquals("", last(".."))
		assertEquals(".", exceptLast(".."))

		assertEquals("", last("..."))
		assertEquals("..", exceptLast("..."))
	}

	@Test
	fun pathObjectCreating() {
		fun p(key: String) = PropertiesParser.pathFromPropertyKey(key)

		assertEquals(path("a"), p("a"))
		assertEquals(path("a", "b"), p("a.b"))
		assertEquals(path(""), p(""))
	}

	@Test
	fun funkyPathsInProperties() {
		fun testPath(propsPath: String, confPath: String) {
			val props = Properties()

			props.setProperty(propsPath, propsPath)

			val conf = ConfigFactory.parseProperties(props, ConfigParseOptions.defaults())

			assertEquals(propsPath, conf.getString(confPath))
		}

		// the easy ones
		testPath("x", "x")
		testPath("y.z", "y.z")
		testPath("q.r.s", "q.r.s")

		// weird empty path element stuff
		testPath("", "\"\"")
		testPath(".", "\"\".\"\"")
		testPath("..", "\"\".\"\".\"\"")
		testPath("a.", "a.\"\"")
		testPath(".b", "\"\".b")

		// quotes in .properties
		testPath("\"", "\"\\\"\"")
	}

	@Test
	fun objectsWinOverStrings() {
		val props = Properties()

		props.setProperty("a.b", "foo")
		props.setProperty("a", "bar")

		props.setProperty("x", "baz")
		props.setProperty("x.y", "bar")
		props.setProperty("x.y.z", "foo")

		val conf = ConfigFactory.parseProperties(props, ConfigParseOptions.defaults())

		assertEquals(2, conf.root().size)
		assertEquals("foo", conf.getString("a.b"))
		assertEquals("foo", conf.getString("x.y.z"))
	}

	@Test
	fun makeListWithNumericKeys() {

		val props = Properties()
		props.setProperty("a.0", "0")
		props.setProperty("a.1", "1")
		props.setProperty("a.2", "2")
		props.setProperty("a.3", "3")
		props.setProperty("a.4", "4")

		val conf = ConfigFactory.parseProperties(props, ConfigParseOptions.defaults())
		val reference = ConfigFactory.parseString("{ a : [0,1,2,3,4] }")
		assertEquals(listOf(0, 1, 2, 3, 4), conf.getIntList("a"))
		conf.checkValid(reference)
	}

	@Test
	fun makeListWithNumericKeysWithGaps() {

		val props = Properties()
		props.setProperty("a.1", "0")
		props.setProperty("a.2", "1")
		props.setProperty("a.4", "2")

		val conf = ConfigFactory.parseProperties(props, ConfigParseOptions.defaults())
		val reference = ConfigFactory.parseString("{ a : [0,1,2] }")
		assertEquals(listOf(0, 1, 2), conf.getIntList("a"))
		conf.checkValid(reference)
	}

	@Test
	fun makeListWithNumericKeysWithNoise() {

		val props = Properties()
		props.setProperty("a.-1", "-1")
		props.setProperty("a.foo", "-2")
		props.setProperty("a.0", "0")
		props.setProperty("a.1", "1")
		props.setProperty("a.2", "2")
		props.setProperty("a.3", "3")
		props.setProperty("a.4", "4")

		val conf = ConfigFactory.parseProperties(props, ConfigParseOptions.defaults())
		val reference = ConfigFactory.parseString("{ a : [0,1,2,3,4] }")
		assertEquals(listOf(0, 1, 2, 3, 4), conf.getIntList("a"))
		conf.checkValid(reference)
	}

	@Test
	fun noNumericKeysAsListFails() {

		val props = Properties()
		props.setProperty("a.bar", "0")

		val conf = ConfigFactory.parseProperties(props, ConfigParseOptions.defaults())
		val e = assertThrows(ConfigException.WrongType::class.java) {
			conf.getList("a")
		}
		assertTrue(e.message!!.contains("LIST"), "expected exception thrown")
	}

	@Test
	fun makeListWithNumericKeysAndMerge() {

		val props = Properties()
		props.setProperty("a.0", "0")
		props.setProperty("a.1", "1")
		props.setProperty("a.2", "2")

		val conf1 = ConfigFactory.parseProperties(props, ConfigParseOptions.defaults())
		assertEquals(listOf(0, 1, 2), conf1.getIntList("a"))

		val conf2 = ConfigFactory.parseString(
			"\n                a += 3\n                a += 4\n                a = \${a} [ 5, 6 ]\n                a = [-2, -1] \${a}\n                "
		)
		val conf = conf2.withFallback(conf1).resolve()
		val reference = ConfigFactory.parseString("{ a : [-2,-1,0,1,2,3,4,5,6] }")

		assertEquals(listOf(-2, -1, 0, 1, 2, 3, 4, 5, 6), conf.getIntList("a"))
		conf.checkValid(reference)
	}

	@Test
	fun skipNonStringsInProperties() {
		val props = Properties()
		props.put("a", ThreadLocal<String>())
		props.put("b", Date())

		val conf = ConfigFactory.parseProperties(props)

		assertEquals(0, conf.root().size)
	}
}
