/**
 * Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TokenizerTest : TestUtils() {

	@Test
	fun tokenizeEmptyString() {
		val source = ""
		val expected = listOf<Token>()
		tokenizerTest(expected, source)
	}

	@Test
	fun tokenizeNewlines() {
		val source = "\n\n"
		val expected = listOf(tokenLine(1), tokenLine(2))
		tokenizerTest(expected, source)
	}

	@Test
	fun tokenizeAllTypesNoSpaces() {
		// all token types with no spaces (not sure JSON spec wants this to work,
		// but spec is unclear to me when spaces are required, and banning them
		// is actually extra work).
		val source = """,:=}{][+="foo"""" + "\"\"\"bar\"\"\"" + "true3.14false42null\${a.b}\${?x.y}\${\"c.d\"}" + "\n"
		val expected = listOf(
			StaticToken.COMMA, StaticToken.COLON, StaticToken.EQUALS, StaticToken.CLOSE_CURLY,
			StaticToken.OPEN_CURLY, StaticToken.CLOSE_SQUARE, StaticToken.OPEN_SQUARE, StaticToken.PLUS_EQUALS,
			tokenString("foo"), tokenString("bar"), tokenTrue(), tokenDouble(3.14), tokenFalse(),
			tokenLong(42), tokenNull(), tokenSubstitution(tokenUnquoted("a.b")),
			tokenOptionalSubstitution(tokenUnquoted("x.y")),
			tokenKeySubstitution("c.d"), tokenLine(1)
		)
		tokenizerTest(expected, source)
	}

	@Test
	fun tokenizeAllTypesWithSingleSpaces() {
		val source =
			""" , : = } { ] [ += "foo" """ + "\"\"\"bar\"\"\"" + " 42 true 3.14 false null \${a.b} \${?x.y} \${\"c.d\"} " + "\n "
		val expected = listOf(
			tokenWhitespace(" "),
			StaticToken.COMMA,
			tokenWhitespace(" "),
			StaticToken.COLON,
			tokenWhitespace(" "),
			StaticToken.EQUALS,
			tokenWhitespace(" "),
			StaticToken.CLOSE_CURLY,
			tokenWhitespace(" "),
			StaticToken.OPEN_CURLY,
			tokenWhitespace(" "),
			StaticToken.CLOSE_SQUARE,
			tokenWhitespace(" "),
			StaticToken.OPEN_SQUARE,
			tokenWhitespace(" "),
			StaticToken.PLUS_EQUALS,
			tokenWhitespace(" "),
			tokenString("foo"),
			tokenUnquoted(" "),
			tokenString("bar"),
			tokenUnquoted(" "),
			tokenLong(42),
			tokenUnquoted(" "),
			tokenTrue(),
			tokenUnquoted(" "),
			tokenDouble(3.14),
			tokenUnquoted(" "),
			tokenFalse(),
			tokenUnquoted(" "),
			tokenNull(),
			tokenUnquoted(" "),
			tokenSubstitution(tokenUnquoted("a.b")),
			tokenUnquoted(" "),
			tokenOptionalSubstitution(tokenUnquoted("x.y")),
			tokenUnquoted(" "),
			tokenKeySubstitution("c.d"),
			tokenWhitespace(" "),
			tokenLine(1),
			tokenWhitespace(" ")
		)
		tokenizerTest(expected, source)
	}

	@Test
	fun tokenizeAllTypesWithMultipleSpaces() {
		val source =
			"""   ,   :   =   }   {   ]   [   +=   "foo"   """ + "\"\"\"bar\"\"\"" + "   42   true   3.14   false   null   \${a.b}   \${?x.y}   \${\"c.d\"}  " + "\n   "
		val expected = listOf(
			tokenWhitespace("   "),
			StaticToken.COMMA,
			tokenWhitespace("   "),
			StaticToken.COLON,
			tokenWhitespace("   "),
			StaticToken.EQUALS,
			tokenWhitespace("   "),
			StaticToken.CLOSE_CURLY,
			tokenWhitespace("   "),
			StaticToken.OPEN_CURLY,
			tokenWhitespace("   "),
			StaticToken.CLOSE_SQUARE,
			tokenWhitespace("   "),
			StaticToken.OPEN_SQUARE,
			tokenWhitespace("   "),
			StaticToken.PLUS_EQUALS,
			tokenWhitespace("   "),
			tokenString("foo"),
			tokenUnquoted("   "),
			tokenString("bar"),
			tokenUnquoted("   "),
			tokenLong(42),
			tokenUnquoted("   "),
			tokenTrue(),
			tokenUnquoted("   "),
			tokenDouble(3.14),
			tokenUnquoted("   "),
			tokenFalse(),
			tokenUnquoted("   "),
			tokenNull(),
			tokenUnquoted("   "),
			tokenSubstitution(tokenUnquoted("a.b")),
			tokenUnquoted("   "),
			tokenOptionalSubstitution(tokenUnquoted("x.y")),
			tokenUnquoted("   "),
			tokenKeySubstitution("c.d"),
			tokenWhitespace("  "),
			tokenLine(1),
			tokenWhitespace("   ")
		)
		tokenizerTest(expected, source)
	}

	@Test
	fun tokenizeTrueAndUnquotedText() {
		val source = """truefoo"""
		val expected = listOf(tokenTrue(), tokenUnquoted("foo"))
		tokenizerTest(expected, source)
	}

	@Test
	fun tokenizeFalseAndUnquotedText() {
		val source = """falsefoo"""
		val expected = listOf(tokenFalse(), tokenUnquoted("foo"))
		tokenizerTest(expected, source)
	}

	@Test
	fun tokenizeNullAndUnquotedText() {
		val source = """nullfoo"""
		val expected = listOf(tokenNull(), tokenUnquoted("foo"))
		tokenizerTest(expected, source)
	}

	@Test
	fun tokenizeUnquotedTextContainingRoundBrace() {
		val source = """(footrue)"""
		val expected = listOf(tokenUnquoted("(footrue)"))
		tokenizerTest(expected, source)
	}

	@Test
	fun tokenizeUnquotedTextContainingTrue() {
		val source = """footrue"""
		val expected = listOf(tokenUnquoted("footrue"))
		tokenizerTest(expected, source)
	}

	@Test
	fun tokenizeUnquotedTextContainingSpaceTrue() {
		val source = """foo true"""
		val expected = listOf(tokenUnquoted("foo"), tokenUnquoted(" "), tokenTrue())
		tokenizerTest(expected, source)
	}

	@Test
	fun tokenizeTrueAndSpaceAndUnquotedText() {
		val source = """true foo"""
		val expected = listOf(tokenTrue(), tokenUnquoted(" "), tokenUnquoted("foo"))
		tokenizerTest(expected, source)
	}

	@Test
	fun tokenizeUnquotedTextContainingSlash() {
		tokenizerTest(listOf(tokenUnquoted("a/b/c/")), "a/b/c/")
		tokenizerTest(listOf(tokenUnquoted("/")), "/")
		tokenizerTest(listOf(tokenUnquoted("/"), tokenUnquoted(" "), tokenUnquoted("/")), "/ /")
		tokenizerTest(listOf(tokenCommentDoubleSlash("")), "//")
	}

	@Test
	fun tokenizeUnquotedTextKeepsSpaces() {
		val source = "    foo     \n"
		val expected = listOf(
			tokenWhitespace("    "), tokenUnquoted("foo"), tokenWhitespace("     "),
			tokenLine(1)
		)
		tokenizerTest(expected, source)
	}

	@Test
	fun tokenizeUnquotedTextKeepsInternalSpaces() {
		val source = "    foo  bar baz   \n"
		val expected = listOf(
			tokenWhitespace("    "), tokenUnquoted("foo"), tokenUnquoted("  "),
			tokenUnquoted("bar"), tokenUnquoted(" "), tokenUnquoted("baz"), tokenWhitespace("   "),
			tokenLine(1)
		)
		tokenizerTest(expected, source)
	}

	@Test
	fun tokenizeMixedUnquotedQuoted() {
		val source = "    foo\"bar\"baz   \n"
		val expected = listOf(
			tokenWhitespace("    "), tokenUnquoted("foo"),
			tokenString("bar"), tokenUnquoted("baz"), tokenWhitespace("   "),
			tokenLine(1)
		)
		tokenizerTest(expected, source)
	}

	@Test
	fun tokenizerUnescapeStrings(): Unit {
		data class UnescapeTest(val escaped: String, val result: ConfigString)

		fun fromPair(pair: Pair<String, String>): UnescapeTest =
			UnescapeTest(pair.first, ConfigString.Quoted(fakeOrigin(), pair.second))


		// getting the actual 6 chars we want in a string is a little pesky.
		// \u005C is backslash. Just prove we're doing it right here.
		assertEquals(6, "\\u0046".length)
		assertEquals('4', "\\u0046"[4])
		assertEquals('6', "\\u0046"[5])

		val tests = listOf(
			(""" "" """ to ""),
			(" \"\\u0000\" " to Character.toString(0)), // nul byte
			(""" "\"\\\/\b\f\n\r\t" """ to "\"\\/\b\u000c\n\r\t"),
			(" \"\\u0046\" " to "F"),
			(" \"\\u0046\\u0046\" " to "FF")
		)
			.map { fromPair(it) }

		for (t in tests) {
			describeFailure(t.toString()) {
				val expected = listOf(
					tokenWhitespace(" "), TokenWithOrigin.Value(t.result, t.toString()),
					tokenWhitespace(" ")
				)
				tokenizerTest(expected, t.escaped)
			}
		}
	}

	@Test
	fun tokenizerReturnsProblemOnInvalidStrings(): Unit {
		val invalidTests = listOf(
			""" "\" """, // nothing after a backslash
			""" "\q" """, // there is no \q escape sequence
			"\"\\u123\"", // too short
			"\"\\u12\"", // too short
			"\"\\u1\"", // too short
			"\"\\u\"", // too short
			"\"", // just a single quote
			""" "abcdefg""", // no end quote
			"""\"\""", // file ends with a backslash
			"$", // file ends with a $
			"\${" // file ends with a ${
		)

		for (t in invalidTests) {
			val tokenized = tokenizeAsList(t)
			val maybeProblem = tokenized.filterIsInstance<TokenWithOrigin.Problem>().firstOrNull()
			assertTrue(maybeProblem != null, "expected failure for <$t> but got $t")
		}
	}

	@Test
	fun tokenizerEmptyTripleQuoted(): Unit {
		val source = "\"\"\"\"\"\""
		val expected = listOf(tokenString(""))
		tokenizerTest(expected, source)
	}

	@Test
	fun tokenizerTrivialTripleQuoted(): Unit {
		val source = "\"\"\"bar\"\"\""
		val expected = listOf(tokenString("bar"))
		tokenizerTest(expected, source)
	}

	@Test
	fun tokenizerNoEscapesInTripleQuoted(): Unit {
		val source = "\"\"\"\\n\"\"\""
		val expected = listOf(tokenString("\\n"))
		tokenizerTest(expected, source)
	}

	@Test
	fun tokenizerTrailingQuotesInTripleQuoted(): Unit {
		val source = "\"\"\"\"\"\"\"\"\""
		val expected = listOf(tokenString("\"\"\""))
		tokenizerTest(expected, source)
	}

	@Test
	fun tokenizerNewlineInTripleQuoted(): Unit {
		val source = "\"\"\"foo\nbar\"\"\""
		val expected = listOf(tokenString("foo\nbar"))
		tokenizerTest(expected, source)
	}

	@Test
	fun tokenizerParseNumbers(): Unit {
		abstract class NumberTest(open val s: String, open val result: Token)
		data class LongTest(override val s: String, override val result: Token) : NumberTest(s, result)
		data class DoubleTest(override val s: String, override val result: Token) : NumberTest(s, result)

		fun pair2inttest(pair: Pair<String, Int>): LongTest = LongTest(pair.first, tokenInt(pair.second))

		fun pair2longtest(pair: Pair<String, Long>): LongTest = LongTest(pair.first, tokenLong(pair.second))

		fun pair2doubletest(pair: Pair<String, Double>): DoubleTest = DoubleTest(pair.first, tokenDouble(pair.second))

		val tests = listOf(
			pair2inttest("1" to 1),
			pair2doubletest("1.2" to 1.2),
			pair2doubletest("1e6" to 1e6),
			pair2doubletest("1e-6" to 1e-6),
			pair2doubletest("1E-6" to 1e-6), // capital E is allowed
			pair2inttest("-1" to -1),
			pair2doubletest("-1.2" to -1.2)
		)

		for (t in tests) {
			describeFailure(t.toString()) {
				val expected = listOf(t.result)
				tokenizerTest(expected, t.s)
			}
		}
	}

	@Test
	fun commentsHandledInVariousContexts() {
		tokenizerTest(listOf(tokenString("//bar")), "\"//bar\"")
		tokenizerTest(listOf(tokenString("#bar")), "\"#bar\"")
		tokenizerTest(listOf(tokenUnquoted("bar"), tokenCommentDoubleSlash("comment")), "bar//comment")
		tokenizerTest(listOf(tokenUnquoted("bar"), tokenCommentHash("comment")), "bar#comment")
		tokenizerTest(listOf(tokenInt(10), tokenCommentDoubleSlash("comment")), "10//comment")
		tokenizerTest(listOf(tokenInt(10), tokenCommentHash("comment")), "10#comment")
		tokenizerTest(listOf(tokenDouble(3.14), tokenCommentDoubleSlash("comment")), "3.14//comment")
		tokenizerTest(listOf(tokenDouble(3.14), tokenCommentHash("comment")), "3.14#comment")
		// be sure we keep the newline
		tokenizerTest(
			listOf(tokenInt(10), tokenCommentDoubleSlash("comment"), tokenLine(1), tokenInt(12)),
			"10//comment\n12"
		)
		tokenizerTest(listOf(tokenInt(10), tokenCommentHash("comment"), tokenLine(1), tokenInt(12)), "10#comment\n12")
		// be sure we handle multi-line comments
		tokenizerTest(
			listOf(tokenCommentDoubleSlash("comment"), tokenLine(1), tokenCommentDoubleSlash("comment2")),
			"//comment\n//comment2"
		)
		tokenizerTest(
			listOf(tokenCommentHash("comment"), tokenLine(1), tokenCommentHash("comment2")),
			"#comment\n#comment2"
		)
		tokenizerTest(
			listOf(
				tokenWhitespace("        "), tokenCommentDoubleSlash("comment\r"),
				tokenLine(1), tokenWhitespace("        "), tokenCommentDoubleSlash("comment2        "),
				tokenLine(2), tokenCommentDoubleSlash("comment3        "),
				tokenLine(3), tokenLine(4), tokenCommentDoubleSlash("comment4")
			),
			"        //comment\r\n        //comment2        \n//comment3        \n\n//comment4"
		)
		tokenizerTest(
			listOf(
				tokenWhitespace("        "), tokenCommentHash("comment\r"),
				tokenLine(1), tokenWhitespace("        "), tokenCommentHash("comment2        "),
				tokenLine(2), tokenCommentHash("comment3        "),
				tokenLine(3), tokenLine(4), tokenCommentHash("comment4")
			),
			"        #comment\r\n        #comment2        \n#comment3        \n\n#comment4"
		)
	}

	@Test
	fun tokenizeReservedChars() {
		for (invalid in "+`^?!@*&\\") {
			val tokenized = tokenizeAsList(invalid.toString())
			assertEquals(3, tokenized.size)
			assertEquals(StaticToken.START, tokenized.first())
			assertEquals(StaticToken.END, tokenized[2])
			val problem = tokenized[1] as TokenWithOrigin.Problem
			if (invalid == '+')
				assertEquals("end of file", problem.what())
			else
				assertEquals("" + invalid, problem.what())
		}
	}

	// FIXME most of this file should be using this method
	private fun tokenizerTest(expected: List<Token>, s: String) {
		assertEquals(
			listOf(StaticToken.START) + expected + listOf(StaticToken.END),
			tokenizeAsList(s)
		)
		assertEquals(s, tokenizeAsString(s))
	}
}
