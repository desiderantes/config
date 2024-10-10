package com.typesafe.config.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConfigNodeTest : TestUtils() {

	@Test
	fun createBasicConfigNode() {
		//Ensure a ConfigNodeSingleToken can handle all its required token types
		singleTokenNodeTest(StaticToken.START)
		singleTokenNodeTest(StaticToken.END)
		singleTokenNodeTest(StaticToken.OPEN_CURLY)
		singleTokenNodeTest(StaticToken.CLOSE_CURLY)
		singleTokenNodeTest(StaticToken.OPEN_SQUARE)
		singleTokenNodeTest(StaticToken.CLOSE_SQUARE)
		singleTokenNodeTest(StaticToken.COMMA)
		singleTokenNodeTest(StaticToken.EQUALS)
		singleTokenNodeTest(StaticToken.COLON)
		singleTokenNodeTest(StaticToken.PLUS_EQUALS)
		singleTokenNodeTest(tokenUnquoted("             "))
		singleTokenNodeTest(tokenWhitespace("             "))
		singleTokenNodeTest(tokenLine(1))
		singleTokenNodeTest(tokenCommentDoubleSlash("  this is a double slash comment   "))
		singleTokenNodeTest(tokenCommentHash("   this is a hash comment   "))
	}

	@Test
	fun createConfigNodeSetting() {
		//Ensure a ConfigNodeSetting can handle the normal key types
		keyNodeTest("foo")
		keyNodeTest("\"Hello I am a key how are you today\"")
	}

	@Test
	fun pathNodeSubpath() {
		val origPath = "a.b.c.\"@$%#@!@#$\".\"\".1234.5678"
		val pathNode = configNodeKey(origPath)
		assertEquals(origPath, pathNode.render())
		assertEquals("c.\"@$%#@!@#$\".\"\".1234.5678", pathNode.subPath(2).render())
		assertEquals("5678", pathNode.subPath(6).render())
	}

	@Test
	fun createConfigNodeSimpleValue() {
		//Ensure a ConfigNodeSimpleValue can handle the normal value types
		simpleValueNodeTest(tokenInt(10))
		simpleValueNodeTest(tokenLong(10000))
		simpleValueNodeTest(tokenDouble(3.14159))
		simpleValueNodeTest(tokenFalse())
		simpleValueNodeTest(tokenTrue())
		simpleValueNodeTest(tokenNull())
		simpleValueNodeTest(tokenString("Hello my name is string"))
		simpleValueNodeTest(tokenUnquoted("mynameisunquotedstring"))
		simpleValueNodeTest(tokenKeySubstitution("c.d"))
		simpleValueNodeTest(tokenOptionalSubstitution(tokenUnquoted("x.y")))
		simpleValueNodeTest(tokenSubstitution(tokenUnquoted("a.b")))
	}

	@Test
	fun createConfigNodeField() {
		// Supports Quoted and Unquoted keys
		fieldNodeTest(configNodeKey("\"abc\""), nodeInt(123), nodeInt(245))
		fieldNodeTest(configNodeKey("abc"), nodeInt(123), nodeInt(245))

		// Can replace value with values of different types
		fieldNodeTest(configNodeKey("\"abc\""), nodeInt(123), nodeString("I am a string"))
		fieldNodeTest(
			configNodeKey("\"abc\""),
			nodeInt(123),
			configNodeObject(listOf(nodeOpenBrace(), nodeCloseBrace()))
		)
	}

	@Test
	fun replaceNodes() {
		//Ensure simple values can be replaced by other simple values
		topLevelValueReplaceTest(nodeInt(10), nodeInt(15))
		topLevelValueReplaceTest(nodeLong(10000), nodeInt(20))
		topLevelValueReplaceTest(nodeDouble(3.14159), nodeLong(10000))
		topLevelValueReplaceTest(nodeFalse(), nodeTrue())
		topLevelValueReplaceTest(nodeTrue(), nodeNull())
		topLevelValueReplaceTest(nodeNull(), nodeString("Hello my name is string"))
		topLevelValueReplaceTest(nodeString("Hello my name is string"), nodeUnquotedText("mynameisunquotedstring"))
		topLevelValueReplaceTest(nodeUnquotedText("mynameisunquotedstring"), nodeKeySubstitution("c.d"))
		topLevelValueReplaceTest(nodeInt(10), nodeOptionalSubstitution(tokenUnquoted("x.y")))
		topLevelValueReplaceTest(nodeInt(10), nodeSubstitution(tokenUnquoted("a.b")))
		topLevelValueReplaceTest(nodeSubstitution(tokenUnquoted("a.b")), nodeInt(10))

		// Ensure arrays can be replaced
		val array = configNodeArray(
			listOf(
				nodeOpenBracket(), nodeInt(10),
				nodeSpace(), nodeComma(), nodeSpace(), nodeInt(15), nodeCloseBracket()
			)
		)
		topLevelValueReplaceTest(nodeInt(10), array)
		topLevelValueReplaceTest(array, nodeInt(10))
		topLevelValueReplaceTest(array, configNodeObject(listOf(nodeOpenBrace(), nodeCloseBrace())))

		// Ensure objects can be replaced
		val nestedMap = configNodeObject(
			listOf(
				nodeOpenBrace(),
				nodeKeyValuePair(configNodeKey("abc"), configNodeSimpleValue(tokenString("a string"))),
				nodeCloseBrace()
			)
		)
		topLevelValueReplaceTest(nestedMap, nodeInt(10))
		topLevelValueReplaceTest(nodeInt(10), nestedMap)
		topLevelValueReplaceTest(array, nestedMap)
		topLevelValueReplaceTest(nestedMap, array)
		topLevelValueReplaceTest(nestedMap, configNodeObject(listOf(nodeOpenBrace(), nodeCloseBrace())))

		// Ensure concatenations can be replaced
		val concatenation = configNodeConcatenation(listOf(nodeInt(10), nodeSpace(), nodeString("Hello")))
		topLevelValueReplaceTest(concatenation, nodeInt(12))
		topLevelValueReplaceTest(nodeInt(12), concatenation)
		topLevelValueReplaceTest(nestedMap, concatenation)
		topLevelValueReplaceTest(concatenation, nestedMap)
		topLevelValueReplaceTest(array, concatenation)
		topLevelValueReplaceTest(concatenation, array)

		//Ensure a key with format "a.b" will be properly replaced
		topLevelValueReplaceTest(nodeInt(10), nestedMap, "foo.bar")
	}

	@Test
	fun removeDuplicates() {
		val emptyMapNode = configNodeObject(listOf(nodeOpenBrace(), nodeCloseBrace()))
		val emptyArrayNode = configNodeArray(listOf(nodeOpenBracket(), nodeCloseBracket()))
		//Ensure duplicates of a key are removed from a map
		replaceDuplicatesTest(nodeInt(10), nodeTrue(), nodeNull())
		replaceDuplicatesTest(emptyMapNode, emptyMapNode, emptyMapNode)
		replaceDuplicatesTest(emptyArrayNode, emptyArrayNode, emptyArrayNode)
		replaceDuplicatesTest(nodeInt(10), emptyMapNode, emptyArrayNode)
	}

	@Test
	fun addNonExistentPaths() {
		nonExistentPathTest(nodeInt(10))
		nonExistentPathTest(configNodeArray(listOf(nodeOpenBracket(), nodeInt(15), nodeCloseBracket())))
		nonExistentPathTest(
			configNodeObject(
				listOf(
					nodeOpenBrace(), nodeKeyValuePair(configNodeKey("foo"), nodeDouble(3.14)),
					nodeCloseBrace()
				)
			)
		)
	}

	@Test
	fun replaceNestedNodes() {
		// Test that all features of node replacement in a map work in a complex map containing nested maps
		val origText =
			"foo : bar\nbaz : {\n\t\"abc.def\" : 123\n\t//This is a comment about the below setting\n\n\tabc : {\n\t\t" +
					"def : \"this is a string\"\n\t\tghi : \${\"a.b\"}\n\t}\n}\nbaz.abc.ghi : 52\nbaz.abc.ghi : 53\n}"
		val lowestLevelMap = configNodeObject(
			listOf(
				nodeOpenBrace(),
				nodeLine(6),
				nodeWhitespace("\t\t"),
				nodeKeyValuePair(configNodeKey("def"), configNodeSimpleValue(tokenString("this is a string"))),
				nodeLine(7),
				nodeWhitespace("\t\t"),
				nodeKeyValuePair(configNodeKey("ghi"), configNodeSimpleValue(tokenKeySubstitution("a.b"))),
				nodeLine(8),
				nodeWhitespace("\t"),
				nodeCloseBrace()
			)
		)
		val higherLevelMap = configNodeObject(
			listOf(
				nodeOpenBrace(),
				nodeLine(2),
				nodeWhitespace("\t"),
				nodeKeyValuePair(configNodeKey("\"abc.def\""), configNodeSimpleValue(tokenInt(123))),
				nodeLine(3),
				nodeWhitespace("\t"),
				nodeCommentDoubleSlash(("This is a comment about the below setting")),
				nodeLine(4),
				nodeLine(5),
				nodeWhitespace("\t"),
				nodeKeyValuePair(configNodeKey("abc"), lowestLevelMap),
				nodeLine(9),
				nodeCloseBrace()
			)
		)
		val origNode = configNodeObject(
			listOf(
				nodeKeyValuePair(configNodeKey("foo"), configNodeSimpleValue(tokenUnquoted("bar"))), nodeLine(1),
				nodeKeyValuePair(configNodeKey("baz"), higherLevelMap), nodeLine(10),
				nodeKeyValuePair(configNodeKey("baz.abc.ghi"), configNodeSimpleValue(tokenInt(52))), nodeLine(11),
				nodeKeyValuePair(configNodeKey("baz.abc.ghi"), configNodeSimpleValue(tokenInt(53))), nodeLine(12),
				nodeCloseBrace()
			)
		)
		assertEquals(origText, origNode.render())
		val finalText =
			"foo : bar\nbaz : {\n\t\"abc.def\" : true\n\t//This is a comment about the below setting\n\n\tabc : {\n\t\t" +
					"def : false\n\t\t\n\t\t\"this.does.not.exist@@@+$#\" : {\n\t\t  end : doesnotexist\n\t\t}\n\t}\n}\n\nbaz.abc.ghi : randomunquotedString\n}"

		//Can replace settings in nested maps
		// Paths with quotes in the name are treated as a single Path, rather than multiple sub-paths
		var newNode = origNode.setValueOnPath("baz.\"abc.def\"", configNodeSimpleValue(tokenTrue()))
		newNode = newNode.setValueOnPath("baz.abc.def", configNodeSimpleValue(tokenFalse()))

		// Repeats are removed from nested maps
		newNode = newNode.setValueOnPath("baz.abc.ghi", configNodeSimpleValue(tokenUnquoted("randomunquotedString")))

		// Missing paths are added to the top level if they don't appear anywhere, including in nested maps
		newNode = newNode.setValueOnPath(
			"baz.abc.\"this.does.not.exist@@@+$#\".end",
			configNodeSimpleValue(tokenUnquoted("doesnotexist"))
		)

		// The above operations cause the resultant map to be rendered properly
		assertEquals(finalText, newNode.render())
	}

	private fun singleTokenNodeTest(token: Token) {
		val node = configNodeSingleToken(token)
		assertEquals(node.render(), token.tokenText())
	}

	private fun keyNodeTest(path: String) {
		val node = configNodeKey(path)
		assertEquals(path, node.render())
	}

	private fun simpleValueNodeTest(token: TokenWithOrigin) {
		val node = configNodeSimpleValue(token)
		assertEquals(node.render(), token.tokenText())
	}

	private fun fieldNodeTest(
		key: ConfigNodeParsedPath,
		value: AbstractConfigNodeValue,
		newValue: AbstractConfigNodeValue
	) {
		val keyValNode = nodeKeyValuePair(key, value)
		assertEquals(key.render() + " : " + value.render(), keyValNode.render())
		assertEquals(key.render(), keyValNode.path().render())
		assertEquals(value.render(), keyValNode.value().render())

		val newKeyValNode = keyValNode.replaceValue(newValue)
		assertEquals(key.render() + " : " + newValue.render(), newKeyValNode.render())
		assertEquals(newValue.render(), newKeyValNode.value().render())
	}

	private fun topLevelValueReplaceTest(
		value: AbstractConfigNodeValue,
		newValue: AbstractConfigNodeValue,
		key: String = "foo"
	) {
		val complexNodeChildren = listOf(
			nodeOpenBrace(),
			nodeKeyValuePair(configNodeKey(key), value),
			nodeCloseBrace()
		)
		val complexNode = configNodeObject(complexNodeChildren)
		val newNode = complexNode.setValueOnPath(key, newValue)
		val origText = "{" + key + " : " + value.render() + "}"
		val finalText = "{" + key + " : " + newValue.render() + "}"

		assertEquals(origText, complexNode.render())
		assertEquals(finalText, newNode.render())
	}

	private fun replaceDuplicatesTest(
		value1: AbstractConfigNodeValue,
		value2: AbstractConfigNodeValue,
		value3: AbstractConfigNodeValue
	) {
		val key = configNodeKey("foo")
		val keyValPair1 = nodeKeyValuePair(key, value1)
		val keyValPair2 = nodeKeyValuePair(key, value2)
		val keyValPair3 = nodeKeyValuePair(key, value3)
		val complexNode = configNodeObject(listOf(keyValPair1, keyValPair2, keyValPair3))
		val origText = keyValPair1.render() + keyValPair2.render() + keyValPair3.render()
		val finalText = key.render() + " : 15"

		assertEquals(origText, complexNode.render())
		assertEquals(finalText, complexNode.setValueOnPath("foo", nodeInt(15)).render())
	}

	private fun nonExistentPathTest(value: AbstractConfigNodeValue) {
		val node = configNodeObject(listOf(nodeKeyValuePair(configNodeKey("bar"), nodeInt(15))))
		assertEquals("bar : 15", node.render())
		val newNode = node.setValueOnPath("foo", value)
		val finalText = "bar : 15, foo : " + value.render()
		assertEquals(finalText, newNode.render())
	}

}
