/**
 * Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl


import com.typesafe.config.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.io.StringReader
import java.net.URI
import java.util.*


class ConfParserTest : TestUtils() {

	private fun parseWithoutResolving(s: String): AbstractConfigValue {
		val options =
			ConfigParseOptions.defaults().setOriginDescription("test conf string").setSyntax(ConfigSyntax.CONF)
		return Parseable.newString(s, options).parseValue()
	}

	private fun parse(s: String): AbstractConfigValue? {
		// resolve substitutions so we can test problems with that, like cycles or
		// interpolating arrays into strings
		return when (val tree = parseWithoutResolving(s)) {
			is AbstractConfigObject ->
				ResolveContext.resolve(tree, tree, ConfigResolveOptions.noSystem())

			else -> tree
		}
	}

	@Test
	fun invalidConfThrows(): Unit {
		// be sure we throw
		for (invalid in whitespaceVariations(invalidConf, validInMoshi = false)) {
			addOffendingJsonToException("config", invalid.test) {
				assertThrows(ConfigException::class.java) {
					parse(invalid.test)
				}
			}
		}
	}

	@Test
	fun validConfWorks(): Unit {
		// all we're checking here unfortunately is that it doesn't throw.
		// for a more thorough check, use the EquivalentsTest stuff.
		for (valid in whitespaceVariations(validConf, true)) {
			val ourAST = addOffendingJsonToException("config-conf", valid.test) {
				return@addOffendingJsonToException parse(valid.test)
			}
			// let's also check round-trip rendering
			val rendered = ourAST!!.render()
			val reparsed = addOffendingJsonToException("config-conf-reparsed", rendered) {
				parse(rendered)
			}
			assertEquals(ourAST, reparsed)
		}
	}

	@Test
	fun pathParsing() {
		assertEquals(path("a"), parsePath("a"))
		assertEquals(path("a", "b"), parsePath("a.b"))
		assertEquals(path("a.b"), parsePath("\"a.b\""))
		assertEquals(path("a."), parsePath("\"a.\""))
		assertEquals(path(".b"), parsePath("\".b\""))
		assertEquals(path("true"), parsePath("true"))
		assertEquals(path("a"), parsePath(" a "))
		assertEquals(path("a ", "b"), parsePath(" a .b"))
		assertEquals(path("a ", " b"), parsePath(" a . b"))
		assertEquals(path("a  b"), parsePath(" a  b"))
		assertEquals(path("a", "b.c", "d"), parsePath("a.\"b.c\".d"))
		assertEquals(path("3", "14"), parsePath("3.14"))
		assertEquals(path("3", "14", "159"), parsePath("3.14.159"))
		assertEquals(path("a3", "14"), parsePath("a3.14"))
		assertEquals(path(""), parsePath("\"\""))
		assertEquals(path("a", "", "b"), parsePath("a.\"\".b"))
		assertEquals(path("a", ""), parsePath("a.\"\""))
		assertEquals(path("", "b"), parsePath("\"\".b"))
		assertEquals(path("", "", ""), parsePath(""" "".""."" """))
		assertEquals(path("a-c"), parsePath("a-c"))
		assertEquals(path("a_c"), parsePath("a_c"))
		assertEquals(path("-"), parsePath("\"-\""))
		assertEquals(path("-"), parsePath("-"))
		assertEquals(path("-foo"), parsePath("-foo"))
		assertEquals(path("-10"), parsePath("-10"))

		// here 10.0 is part of an unquoted string
		assertEquals(path("foo10", "0"), parsePath("foo10.0"))
		// here 10.0 is a number that gets value-concatenated
		assertEquals(path("10", "0foo"), parsePath("10.0foo"))
		// just a number
		assertEquals(path("10", "0"), parsePath("10.0"))
		// multiple-decimal number
		assertEquals(path("1", "2", "3", "4"), parsePath("1.2.3.4"))

		for (invalid in listOf("", " ", "  \n   \n  ", "a.", ".b", "a..b", "a\${b}c", "\"\".", ".\"\"")) {
			try {
				assertThrows(ConfigException.BadPath::class.java) {
					parsePath(invalid)
				}
			} catch (e: Throwable) {
				System.err.println("failed on: '$invalid'")
				throw e;
			}
		}
	}

	@Test
	fun duplicateKeyLastWins() {
		val obj = parseConfig("""{ "a" : 10, "a" : 11 } """)

		assertEquals(1, obj.root().size)
		assertEquals(11, obj.getInt("a"))
	}

	@Test
	fun duplicateKeyObjectsMerged() {
		val obj = parseConfig("""{ "a" : { "x" : 1, "y" : 2 }, "a" : { "x" : 42, "z" : 100 } }""")

		assertEquals(1, obj.root().size)
		assertEquals(3, obj.getObject("a").size)
		assertEquals(42, obj.getInt("a.x"))
		assertEquals(2, obj.getInt("a.y"))
		assertEquals(100, obj.getInt("a.z"))
	}

	@Test
	fun duplicateKeyObjectsMergedRecursively() {
		val obj = parseConfig("""{ "a" : { "b" : { "x" : 1, "y" : 2 } }, "a" : { "b" : { "x" : 42, "z" : 100 } } }""")

		assertEquals(1, obj.root().size)
		assertEquals(1, obj.getObject("a").size)
		assertEquals(3, obj.getObject("a.b").size)
		assertEquals(42, obj.getInt("a.b.x"))
		assertEquals(2, obj.getInt("a.b.y"))
		assertEquals(100, obj.getInt("a.b.z"))
	}

	@Test
	fun duplicateKeyObjectsMergedRecursivelyDeeper() {
		val obj =
			parseConfig("""{ "a" : { "b" : { "c" : { "x" : 1, "y" : 2 } } }, "a" : { "b" : { "c" : { "x" : 42, "z" : 100 } } } }""")

		assertEquals(1, obj.root().size)
		assertEquals(1, obj.getObject("a").size)
		assertEquals(1, obj.getObject("a.b").size)
		assertEquals(3, obj.getObject("a.b.c").size)
		assertEquals(42, obj.getInt("a.b.c.x"))
		assertEquals(2, obj.getInt("a.b.c.y"))
		assertEquals(100, obj.getInt("a.b.c.z"))
	}

	@Test
	fun duplicateKeyObjectNullObject() {
		// null is supposed to "reset" the object at key "a"
		val obj = parseConfig("""{ a : { b : 1 }, a : null, a : { c : 2 } }""")
		assertEquals(1, obj.root().size)
		assertEquals(1, obj.getObject("a").size)
		assertEquals(2, obj.getInt("a.c"))
	}

	@Test
	fun duplicateKeyObjectNumberObject() {
		val obj = parseConfig("""{ a : { b : 1 }, a : 42, a : { c : 2 } }""")
		assertEquals(1, obj.root().size)
		assertEquals(1, obj.getObject("a").size)
		assertEquals(2, obj.getInt("a.c"))
	}

	@Test
	fun impliedCommaHandling() {
		val valids = listOf(
			"""
// one line
{
  a : y, b : z, c : [ 1, 2, 3 ]
}""",
			"""
// multiline but with all commas
{
  a : y,
  b : z,
  c : [
    1,
    2,
    3,
  ],
}
""",
			"""
// multiline with no commas
{
  a : y
  b : z
  c : [
    1
    2
    3
  ]
}
"""
		)

		fun dropCurlies(s: String): String {
			// drop the outside curly braces
			val first = s.indexOf('{')
			val last = s.lastIndexOf('}')
			return s.substring(0, first) + s.substring(first + 1, last) + s.substring(last + 1, s.length)
		}

		val changes = listOf(
			{ s: String -> s },
			{ s: String -> s.replace("\n", "\n\n") },
			{ s: String -> s.replace("\n", "\n\n\n") },
			{ s: String -> s.replace(",\n", "\n,\n") },
			{ s: String -> s.replace(",\n", "\n\n,\n\n") },
			{ s: String -> s.replace("\n", " \n ") },
			{ s: String -> s.replace(",\n", "  \n  \n  ,  \n  \n  ") },
			{ s: String -> dropCurlies(s) })

		var tested = 0
		for (v in valids) {
			for (change in changes) {
				tested += 1
				val obj = parseConfig(change(v))
				assertEquals(3, obj.root().size)
				assertEquals("y", obj.getString("a"))
				assertEquals("z", obj.getString("b"))
				assertEquals(listOf(1, 2, 3), obj.getIntList("c"))
			}
		}

		assertEquals(valids.size * changes.size, tested)

		// with no newline or comma, we do value concatenation
		val noNewlineInArray = parseConfig(" { c : [ 1 2 3 ] } ")
		assertEquals(listOf("1 2 3"), noNewlineInArray.getStringList("c"))

		val noNewlineInArrayWithQuoted = parseConfig(""" { c : [ "4" "5" "6" ] } """)
		assertEquals(listOf("4 5 6"), noNewlineInArrayWithQuoted.getStringList("c"))

		val noNewlineInObject = parseConfig(" { a : b c } ")
		assertEquals("b c", noNewlineInObject.getString("a"))

		val noNewlineAtEnd = parseConfig("a : b")
		assertEquals("b", noNewlineAtEnd.getString("a"))

		assertThrows(ConfigException::class.java) {
			parseConfig("{ a : y b : z }")
		}

		assertThrows(ConfigException::class.java) {
			parseConfig("""{ "a" : "y" "b" : "z" }""")
		}
	}

	@Test
	fun keysWithSlash() {
		val obj = parseConfig("""/a/b/c=42, x/y/z : 32""")
		assertEquals(42, obj.getInt("/a/b/c"))
		assertEquals(32, obj.getInt("x/y/z"))
	}

	@Test
	fun lineNumbersInErrors() {
		// error is at the last char
		lineNumberTest(1, "}")
		lineNumberTest(2, "\n}")
		lineNumberTest(3, "\n\n}")

		// error is before a final newline
		lineNumberTest(1, "}\n")
		lineNumberTest(2, "\n}\n")
		lineNumberTest(3, "\n\n}\n")

		// with unquoted string
		lineNumberTest(1, "foo")
		lineNumberTest(2, "\nfoo")
		lineNumberTest(3, "\n\nfoo")

		// with quoted string
		lineNumberTest(1, "\"foo\"")
		lineNumberTest(2, "\n\"foo\"")
		lineNumberTest(3, "\n\n\"foo\"")

		// newlines in triple-quoted string should not hose up the numbering
		lineNumberTest(1, "a : \"\"\"foo\"\"\"}")
		lineNumberTest(2, "a : \"\"\"foo\n\"\"\"}")
		lineNumberTest(3, "a : \"\"\"foo\nbar\nbaz\"\"\"}")
		//   newlines after the triple quoted string
		lineNumberTest(5, "a : \"\"\"foo\nbar\nbaz\"\"\"\n\n}")
		//   triple quoted string ends in a newline
		lineNumberTest(6, "a : \"\"\"foo\nbar\nbaz\n\"\"\"\n\n}")
		//   end in the middle of triple-quoted string
		lineNumberTest(5, "a : \"\"\"foo\n\n\nbar\n")
	}

	@Test
	fun toStringForParseablesWorks() {
		// just be sure the toString don't throw, to get test coverage
		val options = ConfigParseOptions.defaults()
		Parseable.newFile(File("foo"), options).toString()
		Parseable.newResources(ConfParserTest::class.java, "foo", options).toString()
		Parseable.newURI(URI.create("file:///foo"), options).toString()
		Parseable.newProperties(Properties(), options).toString()
		Parseable.newReader(StringReader("{}"), options).toString()
	}

	@Test
	fun trackCommentsForSingleField() {
		// no comments
		val conf0 = parseConfig(
			"""
                {
                foo=10 }
                """
		)
		assertComments(listOf(), conf0, "foo")

		// comment in front of a field is used
		val conf1 = parseConfig(
			"""
                { # Before
                foo=10 }
                """
		)
		assertComments(listOf(" Before"), conf1, "foo")

		// comment with a blank line after is dropped
		val conf2 = parseConfig(
			"""
                { # BlankAfter

                foo=10 }
                """
		)
		assertComments(listOf(), conf2, "foo")

		// comment in front of a field is used with no root {}
		val conf3 = parseConfig(
			"""
                # BeforeNoBraces
                foo=10
                """
		)
		assertComments(listOf(" BeforeNoBraces"), conf3, "foo")

		// comment with a blank line after is dropped with no root {}
		val conf4 = parseConfig(
			"""
                # BlankAfterNoBraces

                foo=10
                """
		)
		assertComments(listOf(), conf4, "foo")

		// comment same line after field is used
		val conf5 = parseConfig(
			"""
                {
                foo=10 # SameLine
                }
                """
		)
		assertComments(listOf(" SameLine"), conf5, "foo")

		// comment before field separator is used
		val conf6 = parseConfig(
			"""
                {
                foo # BeforeSep
                =10
                }
                """
		)
		assertComments(listOf(" BeforeSep"), conf6, "foo")

		// comment after field separator is used
		val conf7 = parseConfig(
			"""
                {
                foo= # AfterSep
                10
                }
                """
		)
		assertComments(listOf(" AfterSep"), conf7, "foo")

		// comment on next line is NOT used
		val conf8 = parseConfig(
			"""
                {
                foo=10
                # NextLine
                }
                """
		)
		assertComments(listOf(), conf8, "foo")

		// comment before field separator on new line
		val conf9 = parseConfig(
			"""
                {
                foo
                # BeforeSepOwnLine
                =10
                }
                """
		)
		assertComments(listOf(" BeforeSepOwnLine"), conf9, "foo")

		// comment after field separator on its own line
		val conf10 = parseConfig(
			"""
                {
                foo=
                # AfterSepOwnLine
                10
                }
                """
		)
		assertComments(listOf(" AfterSepOwnLine"), conf10, "foo")

		// comments comments everywhere
		val conf11 = parseConfig(
			"""
                {# Before
                foo
                # BeforeSep
                = # AfterSepSameLine
                # AfterSepNextLine
                10 # AfterValue
                # AfterValueNewLine (should NOT be used)
                }
                """
		)
		assertComments(
			listOf(" Before", " BeforeSep", " AfterSepSameLine", " AfterSepNextLine", " AfterValue"),
			conf11,
			"foo"
		)

		// empty object
		val conf12 = parseConfig(
			"""# BeforeEmpty
                {} #AfterEmpty
                # NewLine
                """
		)
		assertComments(listOf(" BeforeEmpty", "AfterEmpty"), conf12)

		// empty array
		val conf13 = parseConfig(
			"""
                foo=
                # BeforeEmptyArray
                  [] #AfterEmptyArray
                # NewLine
                """
		)
		assertComments(listOf(" BeforeEmptyArray", "AfterEmptyArray"), conf13, "foo")

		// array element
		val conf14 = parseConfig(
			"""
                foo=[
                # BeforeElement
                10 # AfterElement
                ]
                """
		)
		assertComments(listOf(" BeforeElement", " AfterElement"), conf14, "foo", 0)

		// field with comma after it
		val conf15 = parseConfig(
			"""
                foo=10, # AfterCommaField
                """
		)
		assertComments(listOf(" AfterCommaField"), conf15, "foo")

		// element with comma after it
		val conf16 = parseConfig(
			"""
                foo=[10, # AfterCommaElement
                ]
                """
		)
		assertComments(listOf(" AfterCommaElement"), conf16, "foo", 0)

		// field with comma after it but comment isn't on the field's line, so not used
		val conf17 = parseConfig(
			"""
                foo=10
                , # AfterCommaFieldNotUsed
                """
		)
		assertComments(listOf(), conf17, "foo")

		// element with comma after it but comment isn't on the field's line, so not used
		val conf18 = parseConfig(
			"""
                foo=[10
                , # AfterCommaElementNotUsed
                ]
                """
		)
		assertComments(listOf(), conf18, "foo", 0)

		// comment on new line, before comma, should not be used
		val conf19 = parseConfig(
			"""
                foo=10
                # BeforeCommaFieldNotUsed
                ,
                """
		)
		assertComments(listOf(), conf19, "foo")

		// comment on new line, before comma, should not be used
		val conf20 = parseConfig(
			"""
                foo=[10
                # BeforeCommaElementNotUsed
                ,
                ]
                """
		)
		assertComments(listOf(), conf20, "foo", 0)

		// comment on same line before comma
		val conf21 = parseConfig(
			"""
                foo=10 # BeforeCommaFieldSameLine
                ,
                """
		)
		assertComments(listOf(" BeforeCommaFieldSameLine"), conf21, "foo")

		// comment on same line before comma
		val conf22 = parseConfig(
			"""
                foo=[10 # BeforeCommaElementSameLine
                ,
                ]
                """
		)
		assertComments(listOf(" BeforeCommaElementSameLine"), conf22, "foo", 0)

		// comment with a line containing only whitespace after is dropped
		val conf23 = parseConfig(
			"""
                { # BlankAfter

                foo=10 }
                              """
		)
		assertComments(listOf(), conf23, "foo")
	}

	@Test
	fun trackCommentsForMultipleFields() {
		// nested objects
		val conf5 = parseConfig(
			"""
             # Outside
             bar {
                # Ignore me

                # Middle
                # two lines
                baz {
                    # Inner
                    foo=10 # AfterInner
                    # This should be ignored
                } # AfterMiddle
                # ignored
             } # AfterOutside
             # ignored!
             """
		)
		assertComments(listOf(" Inner", " AfterInner"), conf5, "bar.baz.foo")
		assertComments(listOf(" Middle", " two lines", " AfterMiddle"), conf5, "bar.baz")
		assertComments(listOf(" Outside", " AfterOutside"), conf5, "bar")

		// multiple fields
		val conf6 = parseConfig(
			"""{
                # this is not with a field

                # this is field A
                a : 10,
                # this is field B
                b : 12 # goes with field B which has no comma
                # this is field C
                c : 14, # goes with field C after comma
                # not used
                # this is not used
                # nor is this
                # multi-line block

                # this is with field D
                # this is with field D also
                d : 16

                # this is after the fields
    }"""
		)
		assertComments(listOf(" this is field A"), conf6, "a")
		assertComments(listOf(" this is field B", " goes with field B which has no comma"), conf6, "b")
		assertComments(listOf(" this is field C", " goes with field C after comma"), conf6, "c")
		assertComments(listOf(" this is with field D", " this is with field D also"), conf6, "d")

		// array
		val conf7 = parseConfig(
			"""
                # before entire array
                array = [
                # goes with 0
                0,
                # goes with 1
                1, # with 1 after comma
                # goes with 2
                2 # no comma after 2
                # not with anything
                ] # after entire array
                """
		)
		assertComments(listOf(" goes with 0"), conf7, "array", 0)
		assertComments(listOf(" goes with 1", " with 1 after comma"), conf7, "array", 1)
		assertComments(listOf(" goes with 2", " no comma after 2"), conf7, "array", 2)
		assertComments(listOf(" before entire array", " after entire array"), conf7, "array")

		// properties-like syntax
		val conf8 = parseConfig(
			"""
                # ignored comment

                # x.y comment
                x.y = 10
                # x.z comment
                x.z = 11
                # x.a comment
                x.a = 12
                # a.b comment
                a.b = 14
                a.c = 15
                a.d = 16 # a.d comment
                # ignored comment
                """
		)

		assertComments(listOf(" x.y comment"), conf8, "x.y")
		assertComments(listOf(" x.z comment"), conf8, "x.z")
		assertComments(listOf(" x.a comment"), conf8, "x.a")
		assertComments(listOf(" a.b comment"), conf8, "a.b")
		assertComments(listOf(), conf8, "a.c")
		assertComments(listOf(" a.d comment"), conf8, "a.d")
		// here we're concerned that comments apply only to leaf
		// nodes, not to parent objects.
		assertComments(listOf(), conf8, "x")
		assertComments(listOf(), conf8, "a")
	}

	@Test
	fun includeFile() {
		val conf = ConfigFactory.parseString("include file(" + jsonQuotedResourceFile("test01") + ")")

		// should have loaded conf, json, properties
		assertEquals(42, conf.getInt("ints.fortyTwo"))
		assertEquals(1, conf.getInt("fromJson1"))
		assertEquals("abc", conf.getString("fromProps.abc"))
	}

	@Test
	fun includeFileWithExtension() {
		val conf = ConfigFactory.parseString("include file(" + jsonQuotedResourceFile("test01.conf") + ")")

		assertEquals(42, conf.getInt("ints.fortyTwo"))
		assertFalse(conf.hasPath("fromJson1"))
		assertFalse(conf.hasPath("fromProps.abc"))
	}

	@Test
	fun includeFileWhitespaceInsideParens() {
		val conf = ConfigFactory.parseString("include file(  \n  " + jsonQuotedResourceFile("test01") + "  \n  )")

		// should have loaded conf, json, properties
		assertEquals(42, conf.getInt("ints.fortyTwo"))
		assertEquals(1, conf.getInt("fromJson1"))
		assertEquals("abc", conf.getString("fromProps.abc"))
	}

	@Test
	fun includeFileNoWhitespaceOutsideParens() {
		val e = assertThrows(ConfigException.Parse::class.java) {
			ConfigFactory.parseString("include file (" + jsonQuotedResourceFile("test01") + ")")
		}
		assertTrue(e.message!!.contains("expecting include parameter"), "wrong exception: " + e.message)
	}

	@Test
	fun includeFileNotQuoted() {
		val f = resourceFile("test01")
		val e = assertThrows(ConfigException.Parse::class.java) {
			ConfigFactory.parseString("include file(" + f + ")")
		}
		assertTrue(
			e.message!!.contains("expecting include file() parameter to be a quoted string"),
			"wrong exception: " + e.message
		)
	}

	@Test
	fun includeFileNotQuotedAndSpecialChar() {
		val f = resourceFile("test01")
		val e = assertThrows(ConfigException.Parse::class.java) {
			ConfigFactory.parseString("include file(:" + f + ")")
		}
		assertTrue(
			e.message!!.contains("expecting include file() parameter to be a quoted string, rather than: ':'"),
			"wrong exception: " + e.message
		)
	}

	@Test
	fun includeFileUnclosedParens() {
		val e = assertThrows(ConfigException.Parse::class.java) {
			ConfigFactory.parseString("include file(" + jsonQuotedResourceFile("test01") + " something")
		}
		assertTrue(e.message!!.contains("expecting a close parentheses"), "wrong exception: " + e.message)
	}

	@Test
	fun includeURLBasename() {
		// "AnySyntax" trick doesn't work for url() includes
		val url = resourceFile("test01").toURI().toASCIIString()
		val conf = ConfigFactory.parseString("include url(" + quoteJsonString(url) + ")")

		assertTrue(conf.isEmpty(), "including basename URI doesn't load anything")
	}

	@Test
	fun includeURLWithExtension() {
		val url = resourceFile("test01.conf").toURI().toASCIIString()
		val conf = ConfigFactory.parseString("include url(" + quoteJsonString(url) + ")")

		assertEquals(42, conf.getInt("ints.fortyTwo"))
		assertFalse(conf.hasPath("fromJson1"))
		assertFalse(conf.hasPath("fromProps.abc"))
	}

	@Test
	fun includeURLInvalid() {
		val e = assertThrows(ConfigException.Parse::class.java) {
			ConfigFactory.parseString("include url(\"junk:junk:junk\")")
		}
		assertTrue(e.message!!.contains("invalid URI"), "wrong exception: " + e.message)
	}

	@Test
	fun includeResources() {
		val conf = ConfigFactory.parseString("include classpath(\"test01\")")

		// should have loaded conf, json, properties
		assertEquals(42, conf.getInt("ints.fortyTwo"))
		assertEquals(1, conf.getInt("fromJson1"))
		assertEquals("abc", conf.getString("fromProps.abc"))
	}

	@Test
	fun includeRequiredMissing() {
		// set this to allowMissing=true to demonstrate that the missing inclusion causes failure despite this setting
		val missing = ConfigParseOptions.defaults().setAllowMissing(true)

		val ex = assertThrows(Exception::class.java) {
			ConfigFactory.parseString("include required(classpath( \"nonexistant\") )", missing)
		}

		val actual = ex.message!!
		val expected = ".*resource not found on classpath.*"
		assertTrue(actual.matches(expected.toRegex()), "expected match for <$expected> but got <$actual>")
	}

	@Test
	fun includeRequiredFoundButNestedIncludeMissing() {
		// set this to allowMissing=true to demonstrate that the missing nested inclusion is permitted despite this setting
		val missing = ConfigParseOptions.defaults().setAllowMissing(false)

		// test03 has a missing include
		val conf = ConfigFactory.parseString("include required(classpath( \"test03\") )", missing)

		val expected = "This is in the included file"
		val actual = conf.getString("foo")
		assertTrue(actual.matches(expected.toRegex()), "expected match for <$expected> but got <$actual>")
	}

	@Test
	fun includeRequiredFound() {
		val confs = listOf(
			"include required(\"test01\")",
			"include required( \"test01\" )",

			"include required(classpath(\"test01\"))",
			"include required( classpath(\"test01\"))",
			"include required(classpath(\"test01\") )",
			"include required( classpath(\"test01\") )",

			"include required(classpath( \"test01\" ))",
			"include required( classpath( \"test01\" ))",
			"include required(classpath( \"test01\" ) )",
			"include required( classpath( \"test01\" ) )"
		)

		// should have loaded conf, json, properties
		confs.forEach { c ->
			try {
				val conf = ConfigFactory.parseString(c)
				assertEquals(42, conf.getInt("ints.fortyTwo"))
				assertEquals(1, conf.getInt("fromJson1"))
				assertEquals("abc", conf.getString("fromProps.abc"))
			} catch (ex: Exception) {
				System.err.println("failed parsing: $c")
				throw ex
			}
		}
	}

	@Test
	fun includeURLHeuristically() {
		val url = resourceFile("test01.conf").toURI().toASCIIString()
		val conf = ConfigFactory.parseString("include " + quoteJsonString(url))

		assertEquals(42, conf.getInt("ints.fortyTwo"))
		assertFalse(conf.hasPath("fromJson1"))
		assertFalse(conf.hasPath("fromProps.abc"))
	}

	@Test
	fun includeURLBasenameHeuristically() {
		// "AnySyntax" trick doesn't work for url includes
		val url = resourceFile("test01").toURI().toASCIIString()
		val conf = ConfigFactory.parseString("include " + quoteJsonString(url))

		assertTrue(conf.isEmpty(), "including basename URI doesn't load anything")
	}

	@Test
	fun acceptBOMStartingFile() {
		// BOM at start of file should be ignored
		val conf = ConfigFactory.parseResources("bom.conf")
		assertEquals("bar", conf.getString("foo"))
	}

	@Test
	fun acceptBOMStartOfStringConfig() {
		// BOM at start of file is just whitespace, so ignored
		val conf = ConfigFactory.parseString("\uFEFFfoo=bar")
		assertEquals("bar", conf.getString("foo"))
	}

	@Test
	fun acceptBOMInStringValue() {
		// BOM inside quotes should be preserved, just as other whitespace would be
		val conf = ConfigFactory.parseString("foo=\"\uFEFF\uFEFF\"")
		assertEquals("\uFEFF\uFEFF", conf.getString("foo"))
	}

	@Test
	fun acceptBOMWhitespace() {
		// BOM here should be treated like other whitespace (ignored, since no quotes)
		val conf = ConfigFactory.parseString("foo= \uFEFFbar\uFEFF")
		assertEquals("bar", conf.getString("foo"))
	}

	@Test
	fun acceptMultiPeriodNumericPath() {
		val conf1 = ConfigFactory.parseString("0.1.2.3=foobar1")
		assertEquals("foobar1", conf1.getString("0.1.2.3"))
		val conf2 = ConfigFactory.parseString("0.1.2.3.ABC=foobar2")
		assertEquals("foobar2", conf2.getString("0.1.2.3.ABC"))
		val conf3 = ConfigFactory.parseString("ABC.0.1.2.3=foobar3")
		assertEquals("foobar3", conf3.getString("ABC.0.1.2.3"))
	}

	private fun parsePath(s: String): Path? {
		var firstException: ConfigException? = null
		var secondException: ConfigException? = null

		// parse first by wrapping into a whole document and using
		// the regular parser.
		val result: Path? = try {
			when (val tree = parseWithoutResolving("[\${" + s + "}]")) {
				is ConfigList ->
					when (val ref = tree[0]) {
						is ConfigReference -> ref.expression().path()
						else -> null
					}

				else -> null
			}
		} catch (e: ConfigException) {
			firstException = e
			null
		}

		// also parse with the standalone path parser and be sure the
		// outcome is the same.
		try {
			val shouldBeSame = PathParser.parsePath(s)
			assertEquals(result, shouldBeSame)
		} catch (e: ConfigException) {
			secondException = e
		}

		if (firstException == null && secondException != null)
			throw AssertionError("only the standalone path parser threw", secondException)
		if (firstException != null && secondException == null)
			throw AssertionError("only the whole-document parser threw", firstException)

		if (firstException != null)
			throw firstException

		return result
	}

	private fun lineNumberTest(num: Int, text: String) {
		val e = assertThrows(ConfigException::class.java) {
			parseConfig(text)
		}
		if (!e.message!!.contains("$num:"))
			throw Exception("error message did not contain line '" + num + "' '" + text.replace("\n", "\\n") + "'", e)
	}

	private fun assertComments(comments: List<String>, conf: Config) {
		assertEquals(comments, conf.root().origin().comments())
	}

	private fun assertComments(comments: List<String>, conf: Config, path: String) {
		assertEquals(comments, conf.getValue(path).origin().comments())
	}

	private fun assertComments(comments: List<String>, conf: Config, path: String, index: Int) {
		val v = conf.getList(path)[index]
		assertEquals(comments, v.origin().comments())
	}
}
