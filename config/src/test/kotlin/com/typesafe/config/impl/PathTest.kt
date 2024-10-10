/**
 * Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import com.typesafe.config.ConfigException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PathTest : TestUtils() {

	@Test
	fun pathEquality() {
		// note: foo.bar is a single key here
		val a = Path.newKey("foo.bar")
		// check that newKey worked
		assertEquals(path("foo.bar"), a)
		val sameAsA = Path.newKey("foo.bar")
		val differentKey = Path.newKey("hello")
		// here foo.bar is two elements
		val twoElements = Path.newPath("foo.bar")
		// check that newPath worked
		assertEquals(path("foo", "bar"), twoElements)
		val sameAsTwoElements = Path.newPath("foo.bar")

		checkEqualObjects(a, a)
		checkEqualObjects(a, sameAsA)
		checkNotEqualObjects(a, differentKey)
		checkNotEqualObjects(a, twoElements)
		checkEqualObjects(twoElements, sameAsTwoElements)
	}

	@Test
	fun pathToString() {
		assertEquals("Path(foo)", path("foo").toString())
		assertEquals("Path(foo.bar)", path("foo", "bar").toString())
		assertEquals("Path(foo.\"bar*\")", path("foo", "bar*").toString())
		assertEquals("Path(\"foo.bar\")", path("foo.bar").toString())
	}

	@Test
	fun pathRender() {
		data class RenderTest(val expected: String, val path: Path)

		val tests = listOf(
			// simple one-element case
			RenderTest("foo", path("foo")),
			// simple two-element case
			RenderTest("foo.bar", path("foo", "bar")),
			// non-safe-char in an element
			RenderTest("foo.\"bar*\"", path("foo", "bar*")),
			// period in an element
			RenderTest("\"foo.bar\"", path("foo.bar")),
			// hyphen and underscore
			RenderTest("foo-bar", path("foo-bar")),
			RenderTest("foo_bar", path("foo_bar")),
			// starts with hyphen
			RenderTest("-foo", path("-foo")),
			// starts with number
			RenderTest("10foo", path("10foo")),
			// empty elements
			RenderTest("\"\".\"\"", path("", "")),
			// internal space
			RenderTest("\"foo bar\"", path("foo bar")),
			// leading and trailing spaces
			RenderTest("\" foo \"", path(" foo ")),
			// trailing space only
			RenderTest("\"foo \"", path("foo ")),
			// numbers with decimal points
			RenderTest("1.2", path("1", "2")),
			RenderTest("1.2.3.4", path("1", "2", "3", "4"))
		)

		for (t in tests) {
			assertEquals(t.expected, t.path.render())
			assertEquals(t.path, PathParser.parsePath(t.expected))
			assertEquals(t.path, PathParser.parsePath(t.path.render()))
		}
	}

	@Test
	fun pathFromPathList() {
		assertEquals(path("foo"), Path.of(listOf(path("foo"))))
		assertEquals(
			path("foo", "bar", "baz", "boo"), Path.of(
				listOf(
					path("foo", "bar"),
					path("baz", "boo")
				)
			)
		)
	}

	@Test
	fun pathPrepend() {
		assertEquals(path("foo", "bar"), path("bar").prepend(path("foo")))
		assertEquals(path("a", "b", "c", "d"), path("c", "d").prepend(path("a", "b")))
	}

	@Test
	fun pathLength() {
		assertEquals(1, path("foo").length())
		assertEquals(2, path("foo", "bar").length())
	}

	@Test
	fun pathParent() {
		assertNull(path("a").parent())
		assertEquals(path("a"), path("a", "b").parent())
		assertEquals(path("a", "b"), path("a", "b", "c").parent())
	}

	@Test
	fun pathLast() {
		assertEquals("a", path("a").last())
		assertEquals("b", path("a", "b").last())
	}

	@Test
	fun pathStartsWith() {
		assertTrue(path("a", "b", "c", "d").startsWith(path("a", "b")))
		assertTrue(path("a", "b", "c", "d").startsWith(path("a", "b", "c", "d")))
		assertFalse(path("a", "b", "c", "d").startsWith(path("b", "c", "d")))
		assertFalse(path("a", "b", "c", "d").startsWith(path("invalidpath")))
	}

	@Test
	fun pathsAreInvalid() {
		// this test is just of the Path.newPath() wrapper, the extensive
		// test of different paths is over in ConfParserTest
		assertThrows(ConfigException.BadPath::class.java) {
			Path.newPath("")
		}

		assertThrows(ConfigException.BadPath::class.java) {
			Path.newPath("..")
		}
	}
}
