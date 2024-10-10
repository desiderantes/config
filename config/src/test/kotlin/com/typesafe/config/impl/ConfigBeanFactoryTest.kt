/**
 * Copyright (C) 2013 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import beanconfig.*
import beanconfig.EnumsConfig.Solution
import com.typesafe.config.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.io.InputStreamReader

import java.time.Duration

class ConfigBeanFactoryTest : TestUtils() {

	@Test
	fun testToCamelCase() {
		assertEquals("configProp", ConfigImplUtil.toCamelCase("config-prop"))
		assertEquals("configProp", ConfigImplUtil.toCamelCase("configProp"))
		assertEquals("fooBar", ConfigImplUtil.toCamelCase("foo-----bar"))
		assertEquals("fooBar", ConfigImplUtil.toCamelCase("fooBar"))
		assertEquals("foo", ConfigImplUtil.toCamelCase("-foo"))
		assertEquals("bar", ConfigImplUtil.toCamelCase("bar-"))
	}

	@Test
	fun testCreate() {
		val configIs: InputStream =
			this::class.java.getClassLoader().getResourceAsStream("beanconfig/beanconfig01.conf")!!
		val config: Config = ConfigFactory.parseReader(
			InputStreamReader(configIs),
			ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF)
		).resolve()
		val beanConfig: TestBeanConfig = ConfigBeanFactory.create(config, TestBeanConfig::class.java)
		assertNotNull(beanConfig)
		// recursive bean inside the first bean
		assertEquals(3, beanConfig.numbers.intVal)
	}

	@Test
	fun testValidation() {
		val configIs: InputStream =
			this::class.java.getClassLoader().getResourceAsStream("beanconfig/beanconfig01.conf")!!
		val config: Config = ConfigFactory.parseReader(
			InputStreamReader(configIs),
			ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF)
		).resolve().getConfig("validation")
		val e = assertThrows(ConfigException.ValidationFailed::class.java) {
			ConfigBeanFactory.create(config, ValidationBeanConfig::class.java)
		}

		val expecteds = listOf(
			Missing("propNotListedInConfig", 77, "string"),
			WrongType("shouldBeInt", 78, "number", "boolean"),
			WrongType("should-be-boolean", 79, "boolean", "number"),
			WrongType("should-be-list", 80, "list", "string")
		)

		checkValidationException(e, expecteds)
	}

	@Test
	fun testCreateBool() {
		val beanConfig: BooleansConfig =
			ConfigBeanFactory.create(loadConfig().getConfig("booleans"), BooleansConfig::class.java)
		assertNotNull(beanConfig)
		assertEquals(true, beanConfig.trueVal)
		assertEquals(false, beanConfig.falseVal)
	}

	@Test
	fun testCreateString() {
		val beanConfig: StringsConfig =
			ConfigBeanFactory.create(loadConfig().getConfig("strings"), StringsConfig::class.java)
		assertNotNull(beanConfig)
		assertEquals("abcd", beanConfig.abcd)
		assertEquals("yes", beanConfig.yes)
	}

	@Test
	fun testCreateEnum() {
		val beanConfig: EnumsConfig = ConfigBeanFactory.create(loadConfig().getConfig("enums"), EnumsConfig::class.java)
		assertNotNull(beanConfig)
		assertEquals(EnumsConfig.Problem.P1, beanConfig.problem)
		assertEquals(listOf(Solution.S1, Solution.S3), beanConfig.solutions)
	}

	@Test
	fun testCreateNumber() {
		val beanConfig: NumbersConfig =
			ConfigBeanFactory.create(loadConfig().getConfig("numbers"), NumbersConfig::class.java)
		assertNotNull(beanConfig)

		assertEquals(3, beanConfig.intVal)
		assertEquals(3, beanConfig.intObj)

		assertEquals(4L, beanConfig.longVal)
		assertEquals(4L, beanConfig.longObj)

		assertEquals(1.0, beanConfig.doubleVal, 1e-6)
		assertEquals(1.0, beanConfig.doubleObj, 1e-6)
	}

	@Test
	fun testCreateList() {
		val beanConfig: ArraysConfig =
			ConfigBeanFactory.create(loadConfig().getConfig("arrays"), ArraysConfig::class.java)
		assertNotNull(beanConfig)
		assertEquals(emptyList<Any?>(), beanConfig.empty)
		assertEquals(listOf(1, 2, 3), beanConfig.ofInt)
		assertEquals(listOf(32L, 42L, 52L), beanConfig.ofLong)
		assertEquals(listOf("a", "b", "c"), beanConfig.ofString)
		//assertEquals(List(List("a", "b", "c").asJava,
		//    List("a", "b", "c").asJava,
		//    List("a", "b", "c").asJava).asJava,
		//    beanConfig.getOfArray)
		assertEquals(3, beanConfig.ofObject.size)
		assertEquals(3, beanConfig.ofDouble.size)
		assertEquals(3, beanConfig.ofConfig.size)
		assertTrue(beanConfig.ofConfig[0] is Config)
		assertEquals(3, beanConfig.ofConfigObject.size)
		assertTrue(beanConfig.ofConfigObject[0] is ConfigObject)
		assertEquals(
			listOf(intValue(1), intValue(2), stringValue("a")),
			beanConfig.ofConfigValue
		)
		assertEquals(
			listOf(Duration.ofMillis(1), Duration.ofHours(2), Duration.ofDays(3)),
			beanConfig.ofDuration
		)
		assertEquals(
			listOf(
				ConfigMemorySize(1024),
				ConfigMemorySize(1048576),
				ConfigMemorySize(1073741824)
			),
			beanConfig.ofMemorySize
		)

		val stringsConfigOne = StringsConfig()
		stringsConfigOne.abcd = "testAbcdOne"
		stringsConfigOne.yes = "testYesOne"
		val stringsConfigTwo = StringsConfig()
		stringsConfigTwo.abcd = "testAbcdTwo"
		stringsConfigTwo.yes = "testYesTwo"

		assertEquals(listOf(stringsConfigOne, stringsConfigTwo), beanConfig.ofStringBean)
	}

	@Test
	fun testCreateSet() {
		val beanConfig: SetsConfig = ConfigBeanFactory.create(loadConfig().getConfig("sets"), SetsConfig::class.java)
		assertNotNull(beanConfig)
		assertEquals(emptySet<Any>(), beanConfig.empty)
		assertEquals(setOf(1, 2, 3), beanConfig.ofInt)
		assertEquals(setOf(32L, 42L, 52L), beanConfig.ofLong)
		assertEquals(setOf("a", "b", "c"), beanConfig.ofString)
		assertEquals(3, beanConfig.ofObject.size)
		assertEquals(3, beanConfig.ofDouble.size)
		assertEquals(3, beanConfig.ofConfig.size)
		assertTrue(beanConfig.ofConfig.iterator().next() is Config)
		assertEquals(3, beanConfig.ofConfigObject.size)
		assertTrue(beanConfig.ofConfigObject.iterator().next() is ConfigObject)
		assertEquals(
			setOf(intValue(1), intValue(2), stringValue("a")),
			beanConfig.ofConfigValue
		)
		assertEquals(
			setOf(Duration.ofMillis(1), Duration.ofHours(2), Duration.ofDays(3)),
			beanConfig.ofDuration
		)
		assertEquals(
			setOf(
				ConfigMemorySize(1024),
				ConfigMemorySize(1048576),
				ConfigMemorySize(1073741824)
			),
			beanConfig.ofMemorySize
		)

		val stringsConfigOne = StringsConfig()
		stringsConfigOne.abcd = "testAbcdOne"
		stringsConfigOne.yes = "testYesOne"
		val stringsConfigTwo = StringsConfig()
		stringsConfigTwo.abcd = "testAbcdTwo"
		stringsConfigTwo.yes = "testYesTwo"

		assertEquals(setOf(stringsConfigOne, stringsConfigTwo), beanConfig.ofStringBean)
	}

	@Test
	fun testCreateDuration() {
		val beanConfig: DurationsConfig =
			ConfigBeanFactory.create(loadConfig().getConfig("durations"), DurationsConfig::class.java)
		assertNotNull(beanConfig)
		assertEquals(Duration.ofMillis(500), beanConfig.halfSecond)
		assertEquals(Duration.ofMillis(1000), beanConfig.second)
		assertEquals(Duration.ofMillis(1000), beanConfig.secondAsNumber)
	}

	@Test
	fun testCreateBytes() {
		val beanConfig: BytesConfig = ConfigBeanFactory.create(loadConfig().getConfig("bytes"), BytesConfig::class.java)
		assertNotNull(beanConfig)
		assertEquals(ConfigMemorySize(1024), beanConfig.kibibyte)
		assertEquals(ConfigMemorySize(1000), beanConfig.kilobyte)
		assertEquals(ConfigMemorySize(1000), beanConfig.thousandBytes)
	}

	@Test
	fun testPreferCamelNames() {
		val beanConfig =
			ConfigBeanFactory.create(loadConfig().getConfig("preferCamelNames"), PreferCamelNamesConfig::class.java)
		assertNotNull(beanConfig)

		assertEquals("yes", beanConfig.fooBar)
		assertEquals("yes", beanConfig.bazBar)
	}

	@Test
	fun testValues() {
		val beanConfig = ConfigBeanFactory.create(loadConfig().getConfig("values"), ValuesConfig::class.java)
		assertNotNull(beanConfig)
		assertEquals(42, beanConfig.obj)
		assertEquals("abcd", beanConfig.config.getString("abcd"))
		assertEquals(3, beanConfig.configObj.toConfig().getInt("intVal"))
		assertEquals(stringValue("hello world"), beanConfig.configValue)
		assertEquals(listOf(1, 2, 3).map { intValue(it) }, beanConfig.list)
		assertEquals(true, beanConfig.unwrappedMap["shouldBeInt"])
		assertEquals(42, beanConfig.unwrappedMap["should-be-boolean"])
	}

	@Test
	fun testOptionalProperties() {
		val beanConfig: ObjectsConfig =
			ConfigBeanFactory.create(loadConfig().getConfig("objects"), ObjectsConfig::class.java)
		assertNotNull(beanConfig)
		assertNotNull(beanConfig.valueObject)
		assertNull(beanConfig.valueObject.optionalValue)
		assertNull(beanConfig.valueObject.default)
		assertEquals("notNull", beanConfig.valueObject.mandatoryValue)
	}

	@Test
	fun testNotAnOptionalProperty() {
		val e = assertThrows(ConfigException.ValidationFailed::class.java) {
			ConfigBeanFactory.create(parseConfig("{valueObject: {}}"), ObjectsConfig::class.java)
		}
		assertTrue(e.message!!.contains("No setting"), "missing value error")
		assertTrue(e.message!!.contains("mandatoryValue"), "error about the right property")

	}

	@Test
	fun testNotABeanField() {
		val e = assertThrows(ConfigException.BadBean::class.java) {
			ConfigBeanFactory.create(parseConfig("notBean=42"), NotABeanFieldConfig::class.java)
		}
		assertTrue(e.message!!.contains("unsupported type"), "unsupported type error")
		assertTrue(e.message!!.contains("notBean"), "error about the right property")
	}

	@Test
	fun testNotAnEnumField() {
		val e = assertThrows(ConfigException.BadValue::class.java) {
			ConfigBeanFactory.create(parseConfig("{problem=P1,solutions=[S4]}"), EnumsConfig::class.java)
		}
		assertTrue(e.message!!.contains("Invalid value"), "invalid value error")
		assertTrue(e.message!!.contains("solutions"), "error about the right property")
		assertTrue(e.message!!.contains("should be one of [S1, S2, S3]"), "error enumerates the enum constants")
	}

	@Test
	fun testUnsupportedListElement() {
		val e = assertThrows(ConfigException.BadBean::class.java) {
			ConfigBeanFactory.create(parseConfig("uri=[42]"), UnsupportedListElementConfig::class.java)
		}
		assertTrue(e.message!!.contains("unsupported list element type"), "unsupported element type error")
		assertTrue(e.message!!.contains("uri"), "error about the right property")
	}

	@Test
	fun testUnsupportedMapKey() {
		val e = assertThrows(ConfigException.BadBean::class.java) {
			ConfigBeanFactory.create(parseConfig("map={}"), UnsupportedMapKeyConfig::class.java)
		}
		assertTrue(e.message!!.contains("unsupported Map"), "unsupported map type error")
		assertTrue(e.message!!.contains("'map'"), "error about the right property")
	}

	@Test
	fun testUnsupportedMapValue() {
		val e = assertThrows(ConfigException.BadBean::class.java) {
			ConfigBeanFactory.create(parseConfig("map={}"), UnsupportedMapValueConfig::class.java)
		}
		assertTrue(e.message!!.contains("unsupported Map"), "unsupported map type error")
		assertTrue(e.message!!.contains("'map'"), "error about the right property")
	}

	@Test
	fun testDifferentFieldNameFromAccessors() {
		val e = assertThrows(ConfigException.ValidationFailed::class.java) {
			ConfigBeanFactory.create(ConfigFactory.empty(), DifferentFieldNameFromAccessorsConfig::class.java)
		}
		assertTrue(e.message!!.contains("No setting"), "only one missing value error")
	}

	private fun loadConfig(): Config {
		val configIs: InputStream =
			this::class.java.getClassLoader().getResourceAsStream("beanconfig/beanconfig01.conf")!!
		return configIs.use { configStream ->
			val config: Config = ConfigFactory.parseReader(
				InputStreamReader(configStream),
				ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF)
			).resolve()
			config
		}
	}

}
