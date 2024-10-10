package com.typesafe.config.impl

import com.typesafe.config.*
import org.junit.jupiter.api.Assertions.*
import java.io.*
import java.net.URL
import java.nio.file.Files
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ThreadLocalRandom


abstract class TestUtils {

	val resourceDir by lazy {
		val f = File("src/test/resources")
		if (!f.exists()) {
			val here = File(".").absolutePath
			throw Exception("Tests must be run from the root project directory containing ${f.path}, however the current directory is $here")
		}
		f
	}

	// We'll automatically try each of these with whitespace modifications
	// so no need to add every possible whitespace variation
	protected val validJson: List<ParseTest> = listOf(
		ParseTest("{}"),
		ParseTest("[]"),
		ParseTest("""{ "foo" : "bar" }"""),
		ParseTest("""["foo", "bar"]"""),
		ParseTest("""{ "foo" : 42 }"""),
		ParseTest("{ \"foo\"\n : 42 }"), // newline after key
		ParseTest("{ \"foo\" : \n 42 }"), // newline after colon
		ParseTest("""[10, 11]"""),
		ParseTest("""[10,"foo"]"""),
		ParseTest("""{ "foo" : "bar", "baz" : "boo" }"""),
		ParseTest("""{ "foo" : { "bar" : "baz" }, "baz" : "boo" }"""),
		ParseTest("""{ "foo" : { "bar" : "baz", "woo" : "w00t" }, "baz" : "boo" }"""),
		ParseTest("""{ "foo" : [10,11,12], "baz" : "boo" }"""),
		ParseTest("""[{},{},{},{}]"""),
		ParseTest("""[[[[[[]]]]]]"""),
		ParseTest("""[[1], [1,2], [1,2,3], []]"""), // nested multiple-valued array
		ParseTest("""{"a":{"a":{"a":{"a":{"a":{"a":{"a":{"a":42}}}}}}}}"""),
		ParseTest("[ \"#comment\" ]"), // quoted # comment
		ParseTest("[ \"//comment\" ]"), // quoted // commen)t
		// this long one is mostly to test rendering
		ParseTest("""{ "foo" : { "bar" : "baz", "woo" : "w00t" }, "baz" : { "bar" : "baz", "woo" : [1,2,3,4], "w00t" : true, "a" : false, "b" : 3.14, "c" : null } }"""),
		ParseTest("{}"),
		ParseTest("[ 10e+3 ]")
	) // "+" in a number (moshi doesn't handle)

	private val hexDigits = "0123456789ABCDEF".toCharArray()

	// note: it's important to put {} or [] at the root if you
	// want to test "invalidity reasons" other than "wrong root"
	protected val invalidJsonInvalidConf: List<ParseTest> = listOf(
		ParseTest("{"),
		ParseTest("}"),
		ParseTest("["),
		ParseTest("]"),
		ParseTest(","),
		ParseTest(
			test = "10",
			moshiBehaviorUnexpected = true
		), // value not in array or object, moshi allows this
		ParseTest(
			test = "\"foo\"",
			moshiBehaviorUnexpected = true
		), // value not in array or object, moshi allows it
		ParseTest("\""), // single quote by itself
		ParseTest(
			test = "[,]"
		), // array with just a comma in it; moshi is OK with this
		ParseTest(
			test = "[,,]"
		), // array with just two commas in it; moshi is cool with this too
		ParseTest(test = "[1,2,,]"), // array with two trailing commas
		ParseTest(test = "[,1,2]"), // array with initial comma
		ParseTest(test = "{ , }"), // object with just a comma in it
		ParseTest(test = "{ , , }"), // object with just two commas in it
		ParseTest("{ 1,2 }"), // object with single values not key-value pair
		ParseTest(test = "{ , \"foo\" : 10 }"), // object starts with comma
		ParseTest(test = "{ \"foo\" : 10 ,, }"), // object has two trailing commas
		ParseTest(" \"a\" : 10 ,, "), // two trailing commas for braceless root object
		ParseTest("{ \"foo\" : }"), // no value in object
		ParseTest("{ : 10 }"), // no key in object
		ParseTest(
			test = " \"foo\" : "

		), // no value in object with no braces; moshi-json thinks this is acceptable
		ParseTest(
			test = " : 10 "
		), // no key in object with no braces; moshi is cool with this too since it's a primitive
		ParseTest(" \"foo\" : 10 } "), // close brace but no open
		ParseTest(" \"foo\" : 10 [ "), // no-braces object with trailing gunk
		ParseTest("{ \"foo\" }"), // no value or colon
		ParseTest("{ \"a\" : [ }"), // [ is not a valid value
		ParseTest("{ \"foo\" : 10, true }"), // non-key after comma
		ParseTest("{ foo \n bar : 10 }"), // newline in the middle of the unquoted key
		ParseTest("[ 1, \\"), // ends with backslash
		// these two problems are ignored by the moshi tokenizer
		ParseTest("[:\"foo\", \"bar\"]"), // colon in an array; moshi doesn't throw (tokenizer erases it)
		ParseTest("[\"foo\" : \"bar\"]"), // colon in an array another way, moshi ignores (tokenizer erases it)
		ParseTest("[ \"hello ]"), // unterminated string
		ParseTest(
			test = "{ \"foo\" , true }",
		),
		ParseTest(
			test = "{ \"foo\" : true \"bar\" : false }"
		),
		ParseTest("[ 10, }]"), // array with } as an element
		ParseTest("[ 10, {]"), // array with { as an element
		ParseTest("{}x"), // trailing invalid token after the root object
		ParseTest("[]x"), // trailing invalid token after the root array
		ParseTest(
			test = "{}{}"
		), // trailing token after the root object - moshi OK with it
		ParseTest(
			test = "{}true"
		), // trailing token after the root object; moshi ignores the {}
		ParseTest(test = "[]{}"), // trailing valid token after the root array
		ParseTest(
			test = "[]true"
		), // trailing valid token after the root array, moshi ignores the []
		ParseTest("[\${]"), // unclosed substitution
		ParseTest("[$]"), // '$' by itself
		ParseTest("[$  ]"), // '$' by itself with spaces after
		ParseTest("[\${}]"), // empty substitution (no path)
		ParseTest("[\${?}]"), // no path with ? substitution
		ParseTest(test = "[\${ ?foo}]", whitespaceMatters = true), // space before ? not allowed
		ParseTest("{ \"a\" : [1,2], \"b\" : y\${a}z }"), // trying to interpolate an array in a string
		ParseTest("{ \"a\" : { \"c\" : 2 }, \"b\" : y\${a}z }"), // trying to interpolate an object in a string
		ParseTest("{ \"a\" : \${a} }"), // simple cycle
		ParseTest("[ { \"a\" : 2, \"b\" : \${\${ a }} } ]"), // nested substitution
		ParseTest("[ = ]"), // = is not a valid token in unquoted text
		ParseTest("[ + ]"),
		ParseTest("[ # ]"),
		ParseTest("[ ` ]"),
		ParseTest("[ ^ ]"),
		ParseTest("[ ? ]"),
		ParseTest("[ ! ]"),
		ParseTest("[ @ ]"),
		ParseTest("[ * ]"),
		ParseTest("[ & ]"),
		ParseTest("[ \\ ]"),
		ParseTest("+="),
		ParseTest("[ += ]"),
		ParseTest("+= 10"),
		ParseTest("10 +="),
		ParseTest("[ 10e+3e ]"), // "+" not allowed in unquoted strings, and not a valid number
		ParseTest(
			test = "[ \"foo\nbar\" ]"
		), // unescaped newline in quoted string, moshi doesn't care
		ParseTest("[ # comment ]"),
		ParseTest("\${ #comment }"),
		ParseTest("[ // comment ]"),
		ParseTest("\${ // comment }"),
		ParseTest("{ include \"bar\" : 10 }"), // include with a value after it
		ParseTest("{ include foo }"), // include with unquoted string
		ParseTest("{ include : { \"a\" : 1 } }"), // include used as unquoted key
		ParseTest("a="), // no value
		ParseTest("a:"), // no value with colon
		ParseTest("a= "), // no value with whitespace after
		ParseTest("a.b="), // no value with path
		ParseTest("{ a= }"), // no value inside braces
		ParseTest("{ a: }")
	)// no value with colon inside braces
	protected val validConfInvalidJson: List<ParseTest> = listOf(
		ParseTest(""), // empty document
		ParseTest(" "), // empty document single space
		ParseTest("\n"), // empty document single newline
		ParseTest(" \n \n   \n\n\n"), // complicated empty document
		ParseTest("# foo"), // just a comment
		ParseTest("# bar\n"), // just a comment with a newline
		ParseTest("# foo\n//bar"), // comment then another with no newline
		ParseTest("""{ "foo" = 42 }"""), // equals rather than colon
		ParseTest("""{ "foo" = (42) }"""), // value with round braces
		ParseTest("""{ foo { "bar" : 42 } }"""), // omit the colon for object value
		ParseTest("""{ foo baz { "bar" : 42 } }"""), // omit the colon with unquoted key with spaces
		ParseTest(""" "foo" : 42 """), // omit braces on root object
		ParseTest("""{ "foo" : bar }"""), // no quotes on value
		ParseTest("""{ "foo" : null bar 42 baz true 3.14 "hi" }"""), // bunch of values to concat into a string
		ParseTest("{ foo : \"bar\" }"), // no quotes on key
		ParseTest("{ foo : bar }"), // no quotes on key or value
		ParseTest("{ foo.bar : bar }"), // path expression in key
		ParseTest("{ foo.\"hello world\".baz : bar }"), // partly-quoted path expression in key
		ParseTest("{ foo.bar \n : bar }"), // newline after path expression in key
		ParseTest("{ foo  bar : bar }"), // whitespace in the key
		ParseTest("{ true : bar }"), // key is a non-string token
		ParseTest(
			test = """{ "foo" : "bar", "foo" : "bar2" }""",
			moshiBehaviorUnexpected = true
		), // dup keys - moshi just overrides
		ParseTest("[ 1, 2, 3, ]"), // single trailing comma (moshi fails to throw)
		ParseTest("[1,2,3  , ]"), // single trailing comma with whitespace
		ParseTest("[1,2,3\n\n , \n]"), // single trailing comma with newlines
		ParseTest("[1,]"), // single trailing comma with one-element array
		ParseTest("{ \"foo\" : 10, }"), // extra trailing comma (moshi fails to throw)
		ParseTest("{ \"a\" : \"b\", }"), // single trailing comma in object
		ParseTest("{ a : b, }"), // single trailing comma in object (unquoted strings)
		ParseTest("{ a : b  \n  , \n }"), // single trailing comma in object with newlines
		ParseTest("a : b, c : d,"), // single trailing comma in object with no root braces
		ParseTest("{ a : b\nc : d }"), // skip comma if there's a newline
		ParseTest("a : b\nc : d"), // skip comma if there's a newline and no root braces
		ParseTest("a : b\nc : d,"), // skip one comma but still have one at the end
		ParseTest("[ foo ]"), // not a known token in JSON
		ParseTest("[ t ]"), // start of "true" but ends wrong in JSON
		ParseTest("[ tx ]"),
		ParseTest("[ tr ]"),
		ParseTest("[ trx ]"),
		ParseTest("[ tru ]"),
		ParseTest("[ trux ]"),
		ParseTest("[ truex ]"),
		ParseTest("[ 10x ]"), // number token with trailing junk
		ParseTest("[ / ]"), // unquoted string "slash"
		ParseTest("{ include \"foo\" }"), // valid include
		ParseTest("{ include\n\"foo\" }"), // include with just a newline separating from string
		ParseTest("{ include\"foo\" }"), // include with no whitespace after it
		ParseTest("[ include ]"), // include can be a string value in an array
		ParseTest("{ foo : include }"), // include can be a field value also
		ParseTest("{ include \"foo\", \"a\" : \"b\" }"), // valid include followed by comma and field
		ParseTest("{ foo include : 42 }"), // valid to have a key not starting with include
		ParseTest("[ \${foo} ]"),
		ParseTest("[ \${?foo} ]"),
		ParseTest("[ \${\"foo\"} ]"),
		ParseTest("[ \${foo.bar} ]"),
		ParseTest("[ abc  xyz  \${foo.bar}  qrs tuv ]"), // value concatenation
		ParseTest("[ 1, 2, 3, blah ]"),
		ParseTest("[ \${\"foo.bar\"} ]"),
		ParseTest("{} # comment"),
		ParseTest("{} // comment"),
		ParseTest(
			"""{ "foo" #comment
: 10 }"""
		),
		ParseTest(
			"""{ "foo" // comment
: 10 }"""
		),
		ParseTest(
			"""{ "foo" : #comment
 10 }"""
		),
		ParseTest(
			"""{ "foo" : // comment
 10 }"""
		),
		ParseTest(
			"""{ "foo" : 10 #comment
 }"""
		),
		ParseTest(
			"""{ "foo" : 10 // comment
 }"""
		),
		ParseTest(
			"""[ 10, # comment
 11]"""
		),
		ParseTest(
			"""[ 10, // comment
 11]"""
		),
		ParseTest(
			"""[ 10 # comment
, 11]"""
		),
		ParseTest(
			"""[ 10 // comment
, 11]"""
		),
		ParseTest("""{ /a/b/c : 10 }"""), // key has a slash in it
		ParseTest(test = "[\${ foo.bar}]", whitespaceMatters = true), // substitution with leading spaces
		ParseTest(test = "[\${foo.bar }]", whitespaceMatters = true), // substitution with trailing spaces
		ParseTest(test = "[\${ \"foo.bar\"}]", whitespaceMatters = true), // substitution with leading spaces and quoted
		ParseTest(
			test = "[\${\"foo.bar\" }]",
			whitespaceMatters = true
		), // substitution with trailing spaces and quoted
		ParseTest("[ \${\"foo\"\"bar\"} ]"), // multiple strings in substitution
		ParseTest("[ \${foo  \"bar\" baz} ]"), // multiple strings and whitespace in substitution
		ParseTest("[\${true}]"), // substitution with unquoted true token
		ParseTest("a = [], a += b"), // += operator with previous init
		ParseTest("{ a = [], a += 10 }"), // += in braces object with previous init
		ParseTest("a += b"), // += operator without previous init
		ParseTest("{ a += 10 }"), // += in braces object without previous init
		ParseTest("[ 10e3e3 ]"), // two exponents. this should parse to a string
		ParseTest("[ 1-e3 ]"), // malformed number should end up as a string instead
		ParseTest("[ 1.0.0 ]"), // two decimals, should end up as a string
		ParseTest("[ 1.0. ]") // trailing decimal should end up as a string
	)

	protected val invalidJson = validConfInvalidJson + invalidJsonInvalidConf;
	protected val invalidConf = invalidJsonInvalidConf;

	// .conf is a superset of JSON so validJson just goes in here
	protected val validConf = validConfInvalidJson + validJson;


	fun fakeOrigin(): ConfigOrigin {
		return SimpleConfigOrigin.newSimple("fake origin")
	}

	fun includer(): ConfigIncluder {
		return ConfigImpl.defaultIncluder();
	}

	internal fun tokenTrue() = TokenWithOrigin.Value.newBoolean(fakeOrigin(), true)

	internal fun tokenFalse() =
		TokenWithOrigin.Value.newBoolean(fakeOrigin(), false)

	internal fun tokenNull() = TokenWithOrigin.Value.newNull(fakeOrigin())

	internal fun tokenUnquoted(s: String) = TokenWithOrigin.UnquotedText(fakeOrigin(), s)

	internal fun tokenString(s: String) = TokenWithOrigin.Value.newString(fakeOrigin(), s, "\"" + s + "\"")

	internal fun tokenDouble(d: Double) = TokenWithOrigin.Value.newDouble(fakeOrigin(), d, "" + d)

	internal fun tokenInt(i: Int) = TokenWithOrigin.Value.newInt(fakeOrigin(), i, "" + i)

	internal fun tokenLong(l: Long) = TokenWithOrigin.Value.newLong(fakeOrigin(), l, l.toString())

	internal fun tokenLine(line: Int) = TokenWithOrigin.Line(fakeOrigin().withLineNumber(line))

	internal fun tokenCommentDoubleSlash(text: String) = TokenWithOrigin.Comment.DoubleSlashComment(fakeOrigin(), text)

	internal fun tokenCommentHash(text: String) = TokenWithOrigin.Comment.HashComment(fakeOrigin(), text)


	internal fun tokenWhitespace(text: String) = TokenWithOrigin.IgnoredWhitespace(fakeOrigin(), text)

	internal fun tokenSubstitution(vararg expression: Token): TokenWithOrigin.Substitution {
		return tokenMaybeOptionalSubstitution(false, *expression)
	}

	internal fun tokenOptionalSubstitution(vararg expression: Token): TokenWithOrigin.Substitution {
		return tokenMaybeOptionalSubstitution(true, *expression)
	}

	// quoted string substitution (no interpretation of periods)
	internal fun tokenKeySubstitution(s: String) =
		tokenSubstitution(tokenString(s))

	internal fun tokenize(
		origin: ConfigOrigin,
		input: Reader
	): Iterator<Token> {
		return Tokenizer.tokenize(origin, input, ConfigSyntax.CONF)
	}

	internal fun tokenize(input: Reader): Iterator<Token> {
		return tokenize(SimpleConfigOrigin.newSimple("anonymous Reader"), input)
	}

	internal fun tokenize(s: String): Iterator<Token> {
		val reader = StringReader(s)
		val result = tokenize(reader)
		// reader.close() // can't close until the iterator is traversed, so this tokenize() flavor is inherently broken
		return result
	}

	internal fun tokenizeAsList(s: String) = tokenize(s).asSequence().toList()


	fun tokenizeAsString(s: String) = Tokenizer.render(tokenize(s))


	internal fun configNodeSimpleValue(value: TokenWithOrigin) = ConfigNodeSimpleValue(value)


	internal fun configNodeKey(path: String) = PathParser.parsePathNode(path)

	internal fun configNodeSingleToken(value: Token) = ConfigNodeSingleToken(value)


	internal fun configNodeObject(nodes: List<AbstractConfigNode>) = ConfigNodeObject(nodes)


	internal fun configNodeArray(nodes: List<AbstractConfigNode>) = ConfigNodeArray(nodes)

	internal fun configNodeConcatenation(nodes: List<AbstractConfigNode>) = ConfigNodeConcatenation(nodes)


	internal fun nodeColon() = ConfigNodeSingleToken(StaticToken.COLON)

	internal fun nodeSpace() = ConfigNodeSingleToken(tokenUnquoted(" "))

	internal fun nodeOpenBrace() = ConfigNodeSingleToken(StaticToken.OPEN_CURLY)

	internal fun nodeCloseBrace() = ConfigNodeSingleToken(StaticToken.CLOSE_CURLY)

	internal fun nodeOpenBracket() = ConfigNodeSingleToken(StaticToken.OPEN_SQUARE)

	internal fun nodeCloseBracket() = ConfigNodeSingleToken(StaticToken.CLOSE_SQUARE)

	internal fun nodeComma() = ConfigNodeSingleToken(StaticToken.COMMA)


	internal fun nodeLine(line: Int) = ConfigNodeSingleToken(tokenLine(line))

	internal fun nodeWhitespace(whitespace: String) = ConfigNodeSingleToken(tokenWhitespace(whitespace))

	internal fun nodeKeyValuePair(
		key: ConfigNodeParsedPath,
		value: AbstractConfigNodeValue
	): ConfigNodeField {
		val nodes = listOf(key, nodeSpace(), nodeColon(), nodeSpace(), value)
		return ConfigNodeField(nodes)
	}

	internal fun nodeInt(value: Int) = ConfigNodeSimpleValue(tokenInt(value))

	internal fun nodeString(value: String) = ConfigNodeSimpleValue(tokenString(value))

	internal fun nodeLong(value: Long) = ConfigNodeSimpleValue(tokenLong(value))

	internal fun nodeDouble(value: Double) = ConfigNodeSimpleValue(tokenDouble(value))

	internal fun nodeTrue() = ConfigNodeSimpleValue(tokenTrue())

	internal fun nodeFalse() = ConfigNodeSimpleValue(tokenFalse())

	internal fun nodeCommentHash(text: String) = ConfigNodeComment(tokenCommentHash(text))

	internal fun nodeCommentDoubleSlash(text: String) = ConfigNodeComment(tokenCommentDoubleSlash(text))

	internal fun nodeUnquotedText(text: String) = ConfigNodeSimpleValue(tokenUnquoted(text))

	internal fun nodeNull() = ConfigNodeSimpleValue(tokenNull())

	internal fun nodeKeySubstitution(s: String) = ConfigNodeSimpleValue(tokenKeySubstitution(s))

	internal fun nodeOptionalSubstitution(vararg expression: Token) =
		ConfigNodeSimpleValue(tokenOptionalSubstitution(*expression))

	internal fun nodeSubstitution(vararg expression: Token) = ConfigNodeSimpleValue(tokenSubstitution(*expression))

	fun isWindows(): Boolean = System.getProperty("os.name").lowercase(Locale.ROOT).contains("windows")


	fun userDrive(): String =
		if (isWindows()) System.getProperty("user.dir").takeWhile { it != File.separatorChar } else ""

	// this is importantly NOT using Path.newPath, which relies on
	// the parser; in the test suite we are often testing the parser,
	// so we don't want to use the parser to build the expected result.
	internal fun path(vararg elements: String) = Path.of(*elements)

	protected fun <A> describeFailure(desc: String, code: () -> A): A {
		try {
			return code()
		} catch (t: Throwable) {
			println("Failure on: '%s'".format(desc))
			throw t
		}
	}

	protected fun checkNotEqualObjects(a: Any, b: Any) {
		assertFalse(a == b)
		assertFalse(b == a)
		// hashcode inequality isn't guaranteed, but
		// as long as it happens to work it might
		// detect a bug (if hashcodes are equal,
		// check if it's due to a bug or correct
		// before you remove this)
		assertFalse(a.hashCode() == b.hashCode())
		checkNotEqualToRandomOtherThing(a)
		checkNotEqualToRandomOtherThing(b)
	}

	protected fun checkEqualObjects(a: Any, b: Any) {
		assertTrue(a == b)
		assertTrue(b == a)
		assertTrue(a.hashCode() == b.hashCode())
		checkNotEqualToRandomOtherThing(a)
		checkNotEqualToRandomOtherThing(b)
	}

	protected fun <T : Any> checkSerializationCompat(expectedHex: String, o: T, changedOK: Boolean = false) {
		// be sure we can still deserialize the old one
		val inStream = ByteArrayInputStream(decodeLegibleBinary(expectedHex))
		var failure: Exception? = null
		var inObjectStream: ObjectInputStream? = null
		val deserialized = try {
			inObjectStream = ObjectInputStream(inStream) // this can throw too
			inObjectStream.readObject()
		} catch (e: Exception) {
			failure = (e)
			null
		} finally {
			inObjectStream?.close()
		}

		val why = failure?.let { e -> ": " + e::class.java.simpleName + ": " + e.message } ?: ""

		val byteStream = ByteArrayOutputStream()
		val objectStream = ObjectOutputStream(byteStream)
		objectStream.writeObject(o)
		objectStream.close()
		val hex = encodeLegibleBinary(byteStream.toByteArray())

		fun showCorrectResult(): Unit {
			if (expectedHex != hex) {
				tailrec fun outputStringLiteral(s: String): Unit {
					if (s.isNotEmpty()) {
						val (head, tail) = s.splitAt(80)
						val plus = if (tail.isEmpty()) "" else " +"
						System.err.println("\"" + head + "\"" + plus)
						outputStringLiteral(tail)
					}
				}

				System.err.println("Correct result literal for " + o::class.java.simpleName + " serialization:")
				System.err.println("\"\" + ") // line up all the lines by using empty string on first line
				outputStringLiteral(hex)
			}
		}

		try {
			assertEquals(
				o,
				deserialized,
				"Can no longer deserialize the old format of " + o::class.java.simpleName + why
			)
			assertFalse(failure !== null) // should have thrown if we had a failure

			if (!changedOK)
				assertEquals(
					expectedHex,
					hex,
					o::class.java.simpleName + " serialization has changed (though we still deserialized the old serialization)"
				)
		} catch (e: Throwable) {
			showCorrectResult()
			throw e
		}
	}

	protected fun checkNotSerializable(o: Any?): Unit {
		val byteStream = ByteArrayOutputStream()
		val objectStream = ObjectOutputStream(byteStream)
		val e = assertThrows(NotSerializableException::class.java) {
			objectStream.writeObject(o)
		}
		objectStream.close()
	}

	protected inline fun <reified T : Any> checkSerializable(expectedHex: String, o: T): T {
		val t = checkSerializable(o)
		checkSerializationCompat(expectedHex, o)
		return t
	}

	protected inline fun <reified T : Any> checkSerializableOldFormat(expectedHex: String, o: T): T {
		val t = checkSerializable(o)
		checkSerializationCompat(expectedHex, o, changedOK = true)
		return t
	}

	protected inline fun <reified T : Any> checkSerializableNoMeaningfulEquals(o: T): T {
		assertTrue(
			o is Serializable,
			o::class.java.simpleName + " not an instance of Serializable"
		)

		val a = o as Serializable

		val b = try {
			copyViaSerialize(a)
		} catch (nf: ClassNotFoundException) {
			throw AssertionError(
				"failed to make a copy via serialization, " +
						"possibly caused by http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6446627",
				nf
			)
		} catch (e: Exception) {
			e.printStackTrace(System.err)
			throw AssertionError("failed to make a copy via serialization", e)
		}

		assertTrue(
			T::class.java.isAssignableFrom(b::class.java),
			"deserialized type " + b::class.java.simpleName + " doesn't match serialized type " + a::class.java.simpleName
		)

		return b as T
	}

	protected inline fun <reified T : Any> checkSerializable(o: T): T {
		checkEqualObjects(o, o)

		assertTrue(
			o is Serializable,
			o::class.java.simpleName + " not an instance of Serializable"
		)

		val b = checkSerializableNoMeaningfulEquals(o)

		checkEqualObjects(o, b)
		checkEqualOrigins(o, b)

		return b
	}

	// origin() is not part of value equality but is serialized, so
	// we check it separately
	protected fun <T> checkEqualOrigins(a: T, b: T): Unit {

		when {
			a is ConfigObject && b is ConfigObject -> {
				assertEquals(a.origin(), b.origin())
				for (e in a.entries) {
					checkEqualOrigins(e.value, b[e.key])
				}
			}

			a is ConfigList && b is ConfigList -> {
				assertEquals(a.origin(), b.origin())
				for ((v1, v2) in a.zip(b)) {
					checkEqualOrigins(v1, v2)
				}
			}

			a is ConfigValue && b is ConfigValue -> {
				assertEquals(a.origin(), b.origin())
			}

			else -> {}

		}
	}

	protected fun <R> addOffendingJsonToException(parserName: String, s: String, body: () -> R): R {
		try {
			return body()
		} catch (t: Throwable) {
			val tokens = try {
				"tokens: " + tokenizeAsList(s)
			} catch (e: Throwable) {
				"tokenizer failed: " + e.message;
			}
			// don't use AssertionError because it seems to keep Eclipse
			// from showing the causing exception in JUnit view for some reason
			throw Exception("$parserName parser did wrong thing on '$s', $tokens", t)
		}
	}

	protected fun whitespaceVariations(tests: List<ParseTest>, validInMoshi: Boolean): List<ParseTest> {
		val variations = listOf({ s: String -> s }, // identity
			{ s: String -> " $s" },
			{ s: String -> "$s " },
			{ s: String -> " $s " },
			{ s: String -> s.replace(" ", "") }, // this would break with whitespace in a key or value
			{ s: String -> s.replace(":", " : ") }, // could break with : in a key or value
			{ s: String -> s.replace(",", " , ") } // could break with , in a key or value
		)
		return tests.flatMap { t ->
			if (t.whitespaceMatters) {
				listOf(t)
			} else {
				val withNonAscii = if (t.test.contains(" "))
					listOf(
						ParseTest(
							moshiBehaviorUnexpected = validInMoshi,
							test = t.test.replace(" ", "\u2003")
						)
					) // 2003 = em space, to test non-ascii whitespace
				else
					listOf()
				withNonAscii + variations.map { v -> t.copy(test = v(t.test)) }
			}
		}
	}

	// it's important that these do NOT use the public API to create the
	// instances, because we may be testing that the public API returns the
	// right instance by comparing to these, so using public API here would
	// make the test compare public API to itself.
	internal fun intValue(i: Int) = ConfigInt(fakeOrigin(), i, null)

	internal fun longValue(l: Long) = ConfigLong(fakeOrigin(), l, null)

	internal fun boolValue(b: Boolean) = ConfigBoolean(fakeOrigin(), b)

	internal fun nullValue() = ConfigNull(fakeOrigin())

	internal fun stringValue(s: String) = ConfigString.Quoted(fakeOrigin(), s)

	internal fun doubleValue(d: Double) = ConfigDouble(fakeOrigin(), d, null)

	internal fun parseObject(s: String) = parseConfig(s).root()

	internal fun parseConfig(s: String): SimpleConfig {
		val options =
			ConfigParseOptions.defaults().setOriginDescription("test string")
				.setSyntax(ConfigSyntax.CONF);
		return ConfigFactory.parseString(s, options) as SimpleConfig
	}

	internal fun subst(ref: String, optional: Boolean = false): ConfigReference {
		val path = Path.newPath(ref)
		return ConfigReference(fakeOrigin(), SubstitutionExpression(path, optional))
	}

	internal fun substInString(ref: String, optional: Boolean = false): ConfigConcatenation {
		val path = Path.newPath(ref)
		val pieces: List<AbstractConfigValue> = listOf(
			stringValue("start<"),
			subst(ref, optional),
			stringValue(">end")
		)
		return ConfigConcatenation(fakeOrigin(), pieces)
	}

	protected fun resourceFile(filename: String): File = File(resourceDir, filename)

	protected fun jsonQuotedResourceFile(filename: String): String = quoteJsonString(resourceFile(filename).toString())

	protected fun <T> withContextClassLoader(loader: ClassLoader, body: () -> T): T {
		val executor = Executors.newSingleThreadExecutor()
		val f = executor.submit(Callable {
			val t = Thread.currentThread()
			val old = t.getContextClassLoader()
			t.setContextClassLoader(loader)
			try {
				body()
			} finally {
				t.setContextClassLoader(old)
			}
		})
		return f.get()
	}

	protected fun showDiff(a: ConfigValue, b: ConfigValue, indent: Int = 0) {
		if (a != b) {
			if (a.valueType() != b.valueType()) {
				printIndented(indent, "- " + a.valueType())
				printIndented(indent, "+ " + b.valueType())
			} else if (a.valueType() == ConfigValueType.OBJECT) {
				printIndented(indent, "OBJECT")
				val aS = a as ConfigObject
				val bS = b as ConfigObject
				for (aKV in aS) {
					val bVOption = bS[aKV.key]
					if (aKV.value != bVOption) {
						printIndented(indent + 1, aKV.key)
						if (bVOption != null) {
							showDiff(aKV.value, bVOption, indent + 2)
						} else {
							printIndented(indent + 2, "- " + aKV.value)
							printIndented(indent + 2, "+ (missing)")
						}
					}
				}
			} else {
				printIndented(indent, "- $a")
				printIndented(indent, "+ $b")
			}
		}
	}

	protected fun quoteJsonString(s: String): String = ConfigImplUtil.renderJsonString(s)

	protected fun checkValidationException(e: ConfigException.ValidationFailed, expecteds: Collection<Problem>) {
		val problems = e.problems()
			.sortedWith(Comparator.comparing<ConfigException.ValidationProblem?, String?> { it.path() }
				.thenComparing(Comparator.comparingInt { it.origin().lineNumber() }))
		val sortedExpecteds = expecteds.sortedWith(
			Comparator.comparing<Problem, String> { it.path }.thenComparing(
				kotlin.Comparator.comparingInt { it.line })
		)

		//for (problem in problems)
		//    System.err.println(problem.origin().description() + ": " + problem.path() + ": " + problem.problem())

		for ((problem, expected) in problems.zip(sortedExpecteds)) {
			expected.check(problem)
		}
		assertEquals(
			sortedExpecteds.size,
			problems.size,
			"found expected validation problems, got '$problems' and expected '$sortedExpecteds'"
		)
	}

	protected fun writeFile(f: File, content: String) {
		val writer = PrintWriter(f, Charsets.UTF_8)
		writer.append(content)
		writer.close()
	}

	protected fun <T> withScratchDirectory(testcase: String, body: (File) -> T) {
		val target = Files.createTempDirectory("target").toFile()
		if (!target.isDirectory)
			throw RuntimeException("Expecting $target to exist")
		val suffix = Integer.toHexString(ThreadLocalRandom.current().nextInt())
		val scratch = File(target, "$testcase-$suffix")
		scratch.mkdirs()
		try {
			body(scratch)
		} finally {
			deleteRecursive(scratch)
		}
	}

	protected fun <T> checkSerializableWithCustomSerializer(o: T): T {
		val byteStream = ByteArrayOutputStream()
		val objectStream = CustomObjectOutputStream(byteStream)
		objectStream.writeObject(o)
		objectStream.close()
		val inStream = ByteArrayInputStream(byteStream.toByteArray())
		val inObjectStream = CustomObjectInputStream(inStream)
		val copy = inObjectStream.readObject()
		inObjectStream.close()
		return copy as T
	}

	private fun checkNotEqualToRandomOtherThing(a: Any) {
		assertFalse(a == NotEqualToAnythingElse)
		assertFalse(NotEqualToAnythingElse == a)
	}

	private fun encodeLegibleBinary(bytes: ByteArray): String {
		val sb = StringBuilder()
		for (b in bytes.map { it.toInt().toChar() }) {
			if ((b in 'a'..'z') ||
				(b in 'A'..'Z') ||
				(b in '0'..'9') ||
				b == '-' || b == ':' || b == '.' || b == '/' || b == ' '
			) {
				sb.append('_')
				sb.append(b)
			} else {
				sb.append(hexDigits[(b.code and (0xF0)) shr 4])
				sb.append(hexDigits[b.code and (0x0F)])
			}
		}
		return sb.toString()
	}

	private fun decodeLegibleBinary(s: String): ByteArray {
		val a = ByteArray(s.length / 2) {
			it.toByte()
		}
		var i = 0
		var j = 0
		while (i < s.length) {
			val sub = s.substring(i, i + 2)
			i += 2
			if (sub[0] == '_') {
				a[j] = sub[1].code.toByte()
			} else {
				a[j] = Integer.parseInt(sub, 16).toByte()
			}
			j += 1
		}
		return a
	}

	protected fun copyViaSerialize(o: Serializable): Serializable {
		val byteStream = ByteArrayOutputStream()
		val objectStream = ObjectOutputStream(byteStream)
		objectStream.writeObject(o)
		objectStream.close()
		val inStream = ByteArrayInputStream(byteStream.toByteArray())
		val inObjectStream = ObjectInputStream(inStream)
		val copy = inObjectStream.readObject() as Serializable
		inObjectStream.close()
		return copy
	}

	private fun tokenMaybeOptionalSubstitution(optional: Boolean, vararg expression: Token) =
		TokenWithOrigin.Substitution(fakeOrigin(), optional, expression.toList())

	private fun printIndented(indent: Int, s: String) {
		for (i in 0..indent) {
			System.err.print(' ')
		}
		System.err.println(s)
	}

	private fun deleteRecursive(f: File) {
		if (f.exists()) {
			if (f.isDirectory) {
				val children = f.listFiles()
				if (children != null) {
					for (c in children) {
						deleteRecursive(c)
					}
				}
			}
			f.delete()
		}
	}

	sealed class Problem(open val path: String, open val line: Int) {
		open fun check(p: ConfigException.ValidationProblem) {
			assertEquals(path, p.path(), "matching path")
			assertEquals(line, p.origin().lineNumber(), "matching line for $path")
		}

		protected fun assertMessage(p: ConfigException.ValidationProblem, re: String) {
			assertTrue(
				p.problem().matches(re.toRegex()),
				"didn't get expected message for " + path + ": got '" + p.problem() + "'"
			)
		}
	}

	data class ParseTest(
		val test: String,
		val moshiBehaviorUnexpected: Boolean = false,
		val whitespaceMatters: Boolean = false
	)

	data class Missing(
		override val path: String,
		override val line: Int,
		val expected: String,
	) : Problem(path, line) {
		override fun check(p: ConfigException.ValidationProblem) {
			super.check(p)
			val re = "No setting.*$path.*expecting.*$expected.*"
			assertMessage(p, re)
		}
	}

	data class WrongType(
		override val path: String,
		override val line: Int,
		val expected: String,
		val got: String
	) : Problem(path, line) {
		override fun check(p: ConfigException.ValidationProblem) {
			super.check(p)
			val re =
				"Wrong value type.*$path.*expecting.*$expected.*got.*$got.*"
			assertMessage(p, re)
		}
	}

	data class WrongElementType(
		override val path: String,
		override val line: Int,
		val expected: String,
		val got: String
	) : Problem(path, line) {
		override fun check(p: ConfigException.ValidationProblem) {
			super.check(p)
			val re =
				"List at.*$path.*wrong value type.*expecting.*$expected.*got.*element of.*$got.*"
			assertMessage(p, re)
		}
	}

	class CustomObjectOutputStream(out: OutputStream) : ObjectOutputStream(out) {
		override fun writeUTF(str: String): Unit {
			val bytes = str.toByteArray()
			writeLong(bytes.size.toLong())
			write(bytes)
		}
	}

	class CustomObjectInputStream(`in`: InputStream) : ObjectInputStream(`in`) {
		override fun readUTF(): String {

			val bytes = ByteArray(readLong().toInt()) { _ ->
				readByte()
			}
			return bytes.toString(Charsets.UTF_8)
		}
	}

	protected class TestClassLoader(
		parent: ClassLoader,
		private val additions: Map<String, URL>
	) : ClassLoader(parent) {
		override fun findResources(name: String): Enumeration<URL> {
			val other = super.findResources(name)
			return additions[name]
				?.let { url -> listOf(url).plus(other.toList()) }?.iterator()
				?.let { it ->
					object : Enumeration<URL> {
						override fun hasMoreElements() = it.hasNext()
						override fun nextElement(): URL? = it.next()
					}

				}
				?: other
		}

		override fun findResource(name: String): URL? {
			return additions[name]
		}
	}

	private data object NotEqualToAnythingElse {
	}
}

fun String.splitAt(position: Int): Pair<String, String> {
	return this.take(position) to this.drop(position)
}

fun <T> List<T>.splitAt(position: Int): Pair<List<T>, List<T>> {
	return this.take(position) to this.drop(position)
}