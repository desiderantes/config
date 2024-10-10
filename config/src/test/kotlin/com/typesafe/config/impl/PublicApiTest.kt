/**
 * Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import com.typesafe.config.*
import equiv03.SomethingInEquiv03
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.io.File
import java.io.StringReader
import java.net.URI
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*

class PublicApiTest : TestUtils() {

	private val defaultValueDesc = "hardcoded value";

	@BeforeEach
	fun before(): Unit {
		// TimeZone.getDefault internally invokes System.setProperty("user.timezone", <default time zone>) and it may
		// cause flaky tests depending on tests order and jvm options. This method is invoked
		// eg. by URLConnection.getContentType (it reads headers and gets default time zone).
		TimeZone.getDefault()
	}

	@Test
	fun basicLoadAndGet() {
		val conf = ConfigFactory.load("test01")

		val a = conf.getInt("ints.fortyTwo")
		val child = conf.getConfig("ints")
		val c = child.getInt("fortyTwo")
		val ms = conf.getDuration("durations.halfSecond", ChronoUnit.MILLIS)

		// should have used system variables
		if (System.getenv("HOME") != null)
			assertEquals(System.getenv("HOME"), conf.getString("system.home"))

		assertEquals(System.getProperty("java.version"), conf.getString("system.javaversion"))
	}

	@Test
	fun noSystemVariables() {
		// should not have used system variables
		val conf = ConfigFactory.parseResourcesAnySyntax(PublicApiTest::class.java, "/test01")
			.resolve(ConfigResolveOptions.noSystem())

		assertThrows(ConfigException.Missing::class.java) {
			conf.getString("system.home")
		}
		assertThrows(ConfigException.Missing::class.java) {
			conf.getString("system.javaversion")
		}
	}

	@Test
	fun canLimitLoadToJson() {
		val options = ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON);
		val conf = ConfigFactory.load("test01", options, ConfigResolveOptions.defaults())

		assertEquals(1, conf.getInt("fromJson1"))
		assertThrows(ConfigException.Missing::class.java) {
			conf.getInt("ints.fortyTwo")
		}
	}

	@Test
	fun canLimitLoadToProperties() {
		val options = ConfigParseOptions.defaults().setSyntax(ConfigSyntax.PROPERTIES);
		val conf = ConfigFactory.load("test01", options, ConfigResolveOptions.defaults())

		assertEquals(1, conf.getInt("fromProps.one"))
		assertThrows(ConfigException.Missing::class.java) {
			conf.getInt("ints.fortyTwo")
		}
	}

	@Test
	fun canLimitLoadToConf() {
		val options = ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF);
		val conf = ConfigFactory.load("test01", options, ConfigResolveOptions.defaults())

		assertEquals(42, conf.getInt("ints.fortyTwo"))
		assertThrows(ConfigException.Missing::class.java) {
			conf.getInt("fromJson1")
		}
		assertThrows(ConfigException.Missing::class.java) {
			conf.getInt("fromProps.one")
		}
	}

	@Test
	fun emptyConfigs() {
		assertTrue(ConfigFactory.empty().isEmpty())
		assertEquals("empty config", ConfigFactory.empty().origin().description())
		assertTrue(ConfigFactory.empty("foo").isEmpty())
		assertEquals("foo", ConfigFactory.empty("foo").origin().description())
	}

	@Test
	fun fromJavaBoolean() {
		testFromValue(boolValue(true), true)
		testFromValue(boolValue(false), false)
	}

	@Test
	fun fromJavaNull() {
		testFromValue(nullValue(), null);
	}

	@Test
	fun fromJavaNumbers() {
		testFromValue(intValue(5), 5)
		testFromValue(longValue(6), 6L)
		testFromValue(doubleValue(3.14), 3.14)

		class WeirdNumber(val v: Double) : Number() {
			override fun toByte(): Byte = v.toInt().toByte()
			override fun toDouble(): Double = v
			override fun toInt(): Int = v.toInt()
			override fun toLong(): Long = v.toLong()
			override fun toShort(): Short = v.toInt().toShort()
			override fun toFloat(): Float = v.toFloat()
		}

		val weirdNumber = WeirdNumber(5.1);
		testFromValue(doubleValue(5.1), weirdNumber)
	}

	@Test
	fun fromJavaString() {
		testFromValue(stringValue("hello world"), "hello world")
	}

	@Test
	fun fromJavaMap() {
		val emptyMapValue = emptyMap<String, AbstractConfigValue>()
		val aMapValue =
			mapOf("a" to 1, "b" to 2, "c" to 3).mapValues { (k, v) -> (intValue(v) as AbstractConfigValue) }

		testFromValue(SimpleConfigObject(fakeOrigin(), emptyMapValue), emptyMap<String, Int>())
		testFromValue(SimpleConfigObject(fakeOrigin(), aMapValue), mapOf("a" to 1, "b" to 2, "c" to 3))

		assertEquals(
			"hardcoded value",
			ConfigValueFactory.fromMap(mapOf("a" to 1, "b" to 2, "c" to 3)).origin().description()
		)
		assertEquals(
			"foo",
			ConfigValueFactory.fromMap(mapOf("a" to 1, "b" to 2, "c" to 3), "foo").origin().description()
		)
	}

	@Test
	fun fromJavaCollection() {
		val emptyListValue = emptyList<AbstractConfigValue>()
		val aListValue = listOf(1, 2, 3).map(::intValue)

		testFromValue(SimpleConfigList(fakeOrigin(), emptyListValue), emptyList<Any>())
		testFromValue(SimpleConfigList(fakeOrigin(), aListValue), listOf(1, 2, 3))

		// test with a non-List (but has to be ordered)
		val treeSet = TreeSet<Int>();
		treeSet.add(1)
		treeSet.add(2)
		treeSet.add(3)

		testFromValue(SimpleConfigList(fakeOrigin(), emptyListValue), emptySet<String>())
		testFromValue(SimpleConfigList(fakeOrigin(), aListValue), treeSet)

		// testFromValue doesn't test the fromIterable public wrapper around fromAnyRef,
		// do so here.
		assertEquals(SimpleConfigList(fakeOrigin(), aListValue), ConfigValueFactory.fromIterable(listOf(1, 2, 3)))
		assertEquals(SimpleConfigList(fakeOrigin(), aListValue), ConfigValueFactory.fromIterable(treeSet))

		assertEquals("hardcoded value", ConfigValueFactory.fromIterable(listOf(1, 2, 3)).origin().description())
		assertEquals("foo", ConfigValueFactory.fromIterable(treeSet, "foo").origin().description())
	}

	@Test
	fun fromConfigMemorySize() {
		testFromValue(longValue(1024), ConfigMemorySize(1024));
		testFromValue(longValue(512), ConfigMemorySize(512));
	}

	@Test
	fun fromDuration() {
		testFromValue(longValue(1000), Duration.ofMillis(1000));
		testFromValue(longValue(1000 * 60 * 60 * 24), Duration.ofDays(1));
	}

	@Test
	fun fromExistingConfigValue() {
		testFromValue(longValue(1000), longValue(1000));
		testFromValue(stringValue("foo"), stringValue("foo"));

		val aMapValue = SimpleConfigObject(fakeOrigin(),
			mapOf("a" to 1, "b" to 2, "c" to 3).mapValues { (k, v) -> (intValue(v) as AbstractConfigValue) })

		testFromValue(aMapValue, aMapValue)
	}

	@Test
	fun fromExistingJavaListOfConfigValue() {
		// you can mix "unwrapped" List with ConfigValue elements
		val list = listOf(longValue(1), longValue(2), longValue(3))
		testFromValue(
			SimpleConfigList(
				fakeOrigin(), listOf(
					longValue(1) as AbstractConfigValue,
					longValue(2) as AbstractConfigValue,
					longValue(3) as AbstractConfigValue
				)
			),
			list
		);
	}

	@Test
	fun roundTripUnwrap() {
		val conf = ConfigFactory.load("test01")
		assertTrue(conf.root().size > 4) // "has a lot of stuff in it"
		val unwrapped = conf.root().unwrapped()
		val rewrapped = ConfigValueFactory.fromMap(unwrapped, conf.origin().description())
		val reunwrapped = rewrapped.unwrapped()
		assertEquals(conf.root(), rewrapped)
		assertEquals(reunwrapped, unwrapped)
	}

	@Test
	fun fromJavaPathMap() {
		// first the same tests as with fromMap, but use parseMap
		val emptyMapValue = emptyMap<String, AbstractConfigValue>()
		val aMapValue = mapOf("a" to 1, "b" to 2, "c" to 3).mapValues { (k, v) -> (intValue(v) as AbstractConfigValue) }
		testFromPathMap(SimpleConfigObject(fakeOrigin(), emptyMapValue), emptyMap<String, Object>())
		testFromPathMap(SimpleConfigObject(fakeOrigin(), aMapValue), mapOf("a" to 1, "b" to 2, "c" to 3))

		assertEquals(
			"hardcoded value",
			ConfigFactory.parseMap(mapOf("a" to 1, "b" to 2, "c" to 3)).origin().description()
		)
		assertEquals("foo", ConfigFactory.parseMap(mapOf("a" to 1, "b" to 2, "c" to 3), "foo").origin().description())

		// now some tests with paths; be sure to test nested path maps
		val simplePathMapValue = mapOf("x.y" to 4, "z" to 5)
		val pathMapValue = mapOf("a.c" to 1, "b" to simplePathMapValue)

		val conf = ConfigFactory.parseMap(pathMapValue)

		assertEquals(2, conf.root().size)
		assertEquals(4, conf.getInt("b.x.y"))
		assertEquals(5, conf.getInt("b.z"))
		assertEquals(1, conf.getInt("a.c"))
	}

	@Test
	fun brokenPathMap() {
		// "a" is both number 1 and an object
		val pathMapValue = mapOf("a" to 1, "a.b" to 2)
		assertThrows(ConfigException.BugOrBroken::class.java) {
			ConfigFactory.parseMap(pathMapValue)
		}
	}

	@Test
	fun defaultParseOptions() {
		val d = ConfigParseOptions.defaults()
		assertEquals(true, d.getAllowMissing())
		assertNull(d.getIncluder())
		assertNull(d.getOriginDescription())
		assertNull(d.getSyntax())
	}

	@Test
	fun allowMissing() {
		val e = assertThrows(ConfigException.IO::class.java) {
			ConfigFactory.parseFile(
				resourceFile("nonexistent.conf"),
				ConfigParseOptions.defaults().setAllowMissing(false)
			)
		}
		assertNotFound(e)

		val conf = ConfigFactory.parseFile(
			resourceFile("nonexistent.conf"),
			ConfigParseOptions.defaults().setAllowMissing(true)
		)
		assertTrue(conf.isEmpty(), "is empty")
	}

	@Test
	fun allowMissingFileAnySyntax() {
		val e = assertThrows(ConfigException.IO::class.java) {
			ConfigFactory.parseFileAnySyntax(
				resourceFile("nonexistent"),
				ConfigParseOptions.defaults().setAllowMissing(false)
			)
		}
		assertNotFound(e)

		val conf = ConfigFactory.parseFileAnySyntax(
			resourceFile("nonexistent"),
			ConfigParseOptions.defaults().setAllowMissing(true)
		)
		assertTrue(conf.isEmpty(), "is empty")
	}

	@Test
	fun allowMissingResourcesAnySyntax() {
		val e = assertThrows(ConfigException.IO::class.java) {
			ConfigFactory.parseResourcesAnySyntax(
				PublicApiTest::class.java,
				"nonexistent",
				ConfigParseOptions.defaults().setAllowMissing(false)
			)
		}
		assertNotFound(e)

		val conf = ConfigFactory.parseResourcesAnySyntax(
			PublicApiTest::class.java,
			"nonexistent",
			ConfigParseOptions.defaults().setAllowMissing(true)
		)
		assertTrue(conf.isEmpty(), "is empty")
	}

	@Test
	fun includesCanBeMissingThoughFileCannot() {
		// test03.conf contains some nonexistent includes. check that
		// setAllowMissing on the file (which is not missing) doesn't
		// change that the includes are allowed to be missing.
		// This can break because some options might "propagate" through
		// to includes, but we don't want them all to do so.
		val conf =
			ConfigFactory.parseFile(resourceFile("test03.conf"), ConfigParseOptions.defaults().setAllowMissing(false))
		assertEquals(42, conf.getInt("test01.booleans"))

		val conf2 =
			ConfigFactory.parseFile(resourceFile("test03.conf"), ConfigParseOptions.defaults().setAllowMissing(true))
		assertEquals(conf, conf2)
	}

	@Test
	fun includersAreUsedWithFiles() {
		val included = whatWasIncluded { opts -> ConfigFactory.parseFile(resourceFile("test03.conf"), opts) }

		assertEquals(listOf(
			"test01", "test02.conf", "equiv01/original.json",
			"nothere", "nothere.conf", "nothere.json", "nothere.properties",
			"test03-included.conf", "test03-included.conf"
		),
			included.map { it.name })
	}

	@Test
	fun includersAreUsedRecursivelyWithFiles() {
		// includes.conf has recursive includes in it
		val included = whatWasIncluded { opts -> ConfigFactory.parseFile(resourceFile("equiv03/includes.conf"), opts) }

		assertEquals(listOf(
			"letters/a.conf",
			"numbers/1.conf",
			"numbers/2",
			"letters/b.json",
			"letters/c",
			"root/foo.conf"
		),
			included.map { it.name })
	}

	@Test
	fun includersAreUsedRecursivelyWithString() {
		val included =
			whatWasIncluded { opts -> ConfigFactory.parseString(""" include "equiv03/includes.conf" """, opts) }

		assertEquals(listOf(
			"equiv03/includes.conf",
			"letters/a.conf",
			"numbers/1.conf",
			"numbers/2",
			"letters/b.json",
			"letters/c",
			"root/foo.conf"
		),
			included.map { it.name })
	}

	// full includer should only be used with the file(), url(), classpath() syntax.
	@Test
	fun fullIncluderNotUsedWithoutNewSyntax() {
		val included = whatWasIncluded { opts -> ConfigFactory.parseFile(resourceFile("equiv03/includes.conf"), opts) }

		assertEquals(listOf(
			"letters/a.conf",
			"numbers/1.conf",
			"numbers/2",
			"letters/b.json",
			"letters/c",
			"root/foo.conf"
		),
			included.map { it.name })

		val includedFull =
			whatWasIncludedFull { opts -> ConfigFactory.parseFile(resourceFile("equiv03/includes.conf"), opts) }
		assertEquals(included, includedFull)
	}

	@Test
	fun includersAreUsedWithClasspath() {
		val included =
			whatWasIncluded { opts -> ConfigFactory.parseResources(PublicApiTest::class.java, "/test03.conf", opts) }

		assertEquals(listOf(
			"test01", "test02.conf", "equiv01/original.json",
			"nothere", "nothere.conf", "nothere.json", "nothere.properties",
			"test03-included.conf", "test03-included.conf"
		),
			included.map { it.name })
	}

	@Test
	fun includersAreUsedRecursivelyWithClasspath() {
		// includes.conf has recursive includes in it; here we look it up
		// with an "absolute" class path resource.
		val included = whatWasIncluded { opts ->
			ConfigFactory.parseResources(
				PublicApiTest::class.java,
				"/equiv03/includes.conf",
				opts
			)
		}

		assertEquals(listOf(
			"letters/a.conf",
			"numbers/1.conf",
			"numbers/2",
			"letters/b.json",
			"letters/c",
			"root/foo.conf"
		),
			included.map { it.name })
	}

	@Test
	fun includersAreUsedRecursivelyWithClasspathRelativeResource() {
		// includes.conf has recursive includes in it; here we look it up
		// with a "class-relative" class path resource
		val included =
			whatWasIncluded { opts ->
				ConfigFactory.parseResources(
					SomethingInEquiv03::class.java,
					"includes.conf",
					opts
				)
			}

		assertEquals(listOf(
			"letters/a.conf",
			"numbers/1.conf",
			"numbers/2",
			"letters/b.json",
			"letters/c",
			"root/foo.conf"
		),
			included.map { it.name })
	}

	@Test
	fun includersAreUsedRecursivelyWithURL() {
		// includes.conf has recursive includes in it; here we look it up
		// with a URL
		val included =
			whatWasIncluded { opts ->
				ConfigFactory.parseURI(
					resourceFile("/equiv03/includes.conf").toURI(),
					opts
				)
			}

		assertEquals(listOf(
			"letters/a.conf",
			"numbers/1.conf",
			"numbers/2",
			"letters/b.json",
			"letters/c",
			"root/foo.conf"
		),
			included.map { it.name })
	}

	@Test
	fun fullIncluderUsed() {
		val included = whatWasIncludedFull { opts ->
			ConfigFactory.parseString(
				"""
                    include "equiv03/includes.conf"
                    include file("nonexistent")
                    include url("file:/nonexistent")
                    include classpath("nonexistent")
                """, opts
			)
		}
		assertEquals(listOf(
			"equiv03/includes.conf", "letters/a.conf", "numbers/1.conf",
			"numbers/2", "letters/b.json", "letters/c", "root/foo.conf",
			"file(nonexistent)", "url(file:/nonexistent)", "classpath(nonexistent)"
		),
			included.map
			{ it.name })
	}

	@Test
	fun nonFullIncluderSurvivesNewStyleIncludes() {
		val included = whatWasIncluded { opts ->
			ConfigFactory.parseString(
				"""
                    include "equiv03/includes.conf"
                    include file("nonexistent")
                    include url("file:/nonexistent")
                    include classpath("nonexistent")
                """, opts
			)
		}
		assertEquals(listOf(
			"equiv03/includes.conf", "letters/a.conf", "numbers/1.conf",
			"numbers/2", "letters/b.json", "letters/c", "root/foo.conf"
		),
			included.map { it.name })
	}

	@Test
	fun stringParsing() {
		val conf = ConfigFactory.parseString("{ a : b }", ConfigParseOptions.defaults())
		assertEquals("b", conf.getString("a"))
	}

	@Test
	fun readerParsing() {
		val conf = ConfigFactory.parseReader(StringReader("{ a : b }"), ConfigParseOptions.defaults())
		assertEquals("b", conf.getString("a"))
	}

	@Test
	fun anySyntax() {
		// test01 has all three syntaxes; first load with basename
		val conf = ConfigFactory.parseFileAnySyntax(resourceFile("test01"), ConfigParseOptions.defaults())
		assertEquals(42, conf.getInt("ints.fortyTwo"))
		assertEquals("A", conf.getString("fromJsonA"))
		assertEquals("true", conf.getString("fromProps.bool"))

		// now include a suffix, should only load one of them
		val onlyProps =
			ConfigFactory.parseFileAnySyntax(resourceFile("test01.properties"), ConfigParseOptions.defaults())
		assertFalse(onlyProps.hasPath("ints.fortyTwo"))
		assertFalse(onlyProps.hasPath("fromJsonA"))
		assertEquals("true", onlyProps.getString("fromProps.bool"))

		// force only one syntax via options
		val onlyPropsViaOptions = ConfigFactory.parseFileAnySyntax(
			resourceFile("test01.properties"),
			ConfigParseOptions.defaults().setSyntax(ConfigSyntax.PROPERTIES)
		)
		assertFalse(onlyPropsViaOptions.hasPath("ints.fortyTwo"))
		assertFalse(onlyPropsViaOptions.hasPath("fromJsonA"))
		assertEquals("true", onlyPropsViaOptions.getString("fromProps.bool"))

		// make sure it works with resources too
		val fromResources = ConfigFactory.parseResourcesAnySyntax(
			PublicApiTest::class.java, "/test01",
			ConfigParseOptions.defaults()
		)
		assertEquals(42, fromResources.getInt("ints.fortyTwo"))
		assertEquals("A", fromResources.getString("fromJsonA"))
		assertEquals("true", fromResources.getString("fromProps.bool"))
	}

	@Test
	fun resourceFromAnotherClasspath() {
		val conf =
			ConfigFactory.parseResources(PublicApiTest::class.java, "/test-lib.conf", ConfigParseOptions.defaults())

		assertEquals("This is to test classpath searches.", conf.getString("test-lib.description"))
	}

	@Test
	fun multipleResourcesUsed() {
		val conf = ConfigFactory.parseResources(PublicApiTest::class.java, "/test01.conf")

		assertEquals(42, conf.getInt("ints.fortyTwo"))
		assertEquals(true, conf.getBoolean("test-lib.fromTestLib"))

		// check that each value has its own ConfigOrigin
		val v1 = conf.getValue("ints.fortyTwo")
		val v2 = conf.getValue("test-lib.fromTestLib")
		assertEquals("test01.conf", v1.origin().resource(), "v1 has right origin resource")
		assertEquals("test01.conf", v2.origin().resource(), "v2 has right origin resource")
		assertEquals(v1.origin().resource(), v2.origin().resource())
		assertFalse(v1.origin().uri().equals(v2.origin().uri()), "same urls in " + v1.origin() + " " + v2.origin())
		assertFalse(v1.origin().filename() == v2.origin().filename())
	}

	@Test
	fun splitAndJoinPath() {
		// the actual join-path logic should be tested OK in the non-public-API tests,
		// this is just to test the public wrappers.

		assertEquals("\"\".a.b.\"$\"", ConfigUtil.joinPath("", "a", "b", "$"))
		assertEquals("\"\".a.b.\"$\"", ConfigUtil.joinPath(listOf("", "a", "b", "$")))
		assertEquals(listOf("", "a", "b", "$"), ConfigUtil.splitPath("\"\".a.b.\"$\""))

		// invalid stuff throws
		assertThrows(ConfigException::class.java) {
			ConfigUtil.splitPath("$")
		}
		assertThrows(ConfigException::class.java) {
			ConfigUtil.joinPath()
		}
		assertThrows(ConfigException::class.java) {
			ConfigUtil.joinPath(emptyList<String>())
		}
	}

	@Test
	fun quoteString() {
		// the actual quote logic should be tested OK in the non-public-API tests,
		// this is just to test the public wrapper.

		assertEquals("\"\"", ConfigUtil.quoteString(""))
		assertEquals("\"a\"", ConfigUtil.quoteString("a"))
		assertEquals("\"\\n\"", ConfigUtil.quoteString("\n"))
	}

	@Test
	fun usesContextClassLoaderForReferenceConf() {
		val loaderA1 = TestClassLoader(
			this::class.java.getClassLoader(),
			mapOf("reference.conf" to resourceFile("a_1.conf").toURI().toURL())
		)
		val loaderB2 = TestClassLoader(
			this::class.java.getClassLoader(),
			mapOf("reference.conf" to resourceFile("b_2.conf").toURI().toURL())
		)

		val configA1 = withContextClassLoader(loaderA1) {
			ConfigFactory.load()
		}
		assertEquals(1, configA1.getInt("a"))
		assertFalse(configA1.hasPath("b"), "no b")

		val configB2 = withContextClassLoader(loaderB2) {
			ConfigFactory.load()
		}
		assertEquals(2, configB2.getInt("b"))
		assertFalse(configB2.hasPath("a"), "no a")

		val configPlain = ConfigFactory.load()
		assertFalse(configPlain.hasPath("a"), "no a")
		assertFalse(configPlain.hasPath("b"), "no b")
	}

	@Test
	fun supportsConfigLoadingStrategyAlteration(): Unit {
		assertEquals(null, System.getProperty("config.strategy"), "config.strategy is not set")
		System.setProperty("config.strategy", TestStrategy::class.java.canonicalName)

		try {
			val incovationsBeforeTest = TestStrategy.invocations
			val loaderA1 = TestClassLoader(
				this::class.java.getClassLoader(),
				mapOf("reference.conf" to resourceFile("a_1.conf").toURI().toURL())
			)

			val configA1 = withContextClassLoader(loaderA1) {
				ConfigFactory.load()
			}
			ConfigFactory.load()
			assertEquals(1, configA1.getInt("a"))
			assertEquals(2, TestStrategy.invocations - incovationsBeforeTest)
		} finally {
			System.clearProperty("config.strategy")
		}
	}

	@Test
	fun loadEnvironmentVariablesOverridesIfConfigured(): Unit {
		assertEquals(
			null,
			System.getProperty("config.override_with_env_vars"),
			"config.override_with_env_vars is not set"
		)

		System.setProperty("config.override_with_env_vars", "true")

		try {
			val loaderB2 = TestClassLoader(
				this::class.java.getClassLoader(),
				mapOf("reference.conf" to resourceFile("b_2.conf").toURI().toURL())
			)

			val configB2 = withContextClassLoader(loaderB2) {
				ConfigFactory.load()
			}

			assertEquals(5, configB2.getInt("b"))
		} finally {
			System.clearProperty("config.override_with_env_vars")
		}
	}

	@Test
	fun usesContextClassLoaderForApplicationConf() {
		val loaderA1 = TestClassLoader(
			this::class.java.getClassLoader(),
			mapOf("application.conf" to resourceFile("a_1.conf").toURI().toURL())
		)
		val loaderB2 = TestClassLoader(
			this::class.java.getClassLoader(),
			mapOf("application.conf" to resourceFile("b_2.conf").toURI().toURL())
		)

		val configA1 = withContextClassLoader(loaderA1) {
			ConfigFactory.load()
		}
		assertEquals(1, configA1.getInt("a"))
		assertFalse(configA1.hasPath("b"), "no b")

		val configB2 = withContextClassLoader(loaderB2) {
			ConfigFactory.load()
		}
		assertEquals(2, configB2.getInt("b"))
		assertFalse(configB2.hasPath("a"), "no a")

		val configPlain = ConfigFactory.load()
		assertFalse(configPlain.hasPath("a"), "no a")
		assertFalse(configPlain.hasPath("b"), "no b")
	}

	@Test
	fun usesSuppliedClassLoaderForReferenceConf() {
		val loaderA1 = TestClassLoader(
			this::class.java.getClassLoader(),
			mapOf("reference.conf" to resourceFile("a_1.conf").toURI().toURL())
		)
		val loaderB2 = TestClassLoader(
			this::class.java.getClassLoader(),
			mapOf("reference.conf" to resourceFile("b_2.conf").toURI().toURL())
		)

		val configA1 = ConfigFactory.load(loaderA1)

		assertEquals(1, configA1.getInt("a"))
		assertFalse(configA1.hasPath("b"), "no b")

		val configB2 = ConfigFactory.load(loaderB2)

		assertEquals(2, configB2.getInt("b"))
		assertFalse(configB2.hasPath("a"), "no a")

		val configPlain = ConfigFactory.load()
		assertFalse(configPlain.hasPath("a"), "no a")
		assertFalse(configPlain.hasPath("b"), "no b")

		// check the various overloads that take a loader parameter
		for (
		c in listOf(
			ConfigFactory.parseResources(loaderA1, "reference.conf"),
			ConfigFactory.parseResourcesAnySyntax(loaderA1, "reference"),
			ConfigFactory.parseResources(loaderA1, "reference.conf", ConfigParseOptions.defaults()),
			ConfigFactory.parseResourcesAnySyntax(loaderA1, "reference", ConfigParseOptions.defaults()),
			ConfigFactory.load(loaderA1, "application"),
			ConfigFactory.load(loaderA1, "application", ConfigParseOptions.defaults(), ConfigResolveOptions.defaults()),
			ConfigFactory.load(loaderA1, "application", ConfigParseOptions.defaults(), ConfigResolveOptions.defaults()),
			ConfigFactory.load(loaderA1, ConfigFactory.parseString("")),
			ConfigFactory.load(loaderA1, ConfigFactory.parseString(""), ConfigResolveOptions.defaults()),
			ConfigFactory.defaultReference(loaderA1)
		)
		) {
			assertEquals(1, c.getInt("a"))
			assertFalse(c.hasPath("b"), "no b")
		}

		// check providing the loader via ConfigParseOptions
		val withLoader = ConfigParseOptions.defaults().setClassLoader(loaderA1);
		for (
		c in listOf(
			ConfigFactory.parseResources("reference.conf", withLoader),
			ConfigFactory.parseResourcesAnySyntax("reference", withLoader),
			ConfigFactory.load("application", withLoader, ConfigResolveOptions.defaults())
		)
		) {
			assertEquals(1, c.getInt("a"))
			assertFalse(c.hasPath("b"), "no b")
		}

		// check not providing the loader
		for (
		c in listOf(
			ConfigFactory.parseResources("reference.conf"),
			ConfigFactory.parseResourcesAnySyntax("reference"),
			ConfigFactory.parseResources("reference.conf", ConfigParseOptions.defaults()),
			ConfigFactory.parseResourcesAnySyntax("reference", ConfigParseOptions.defaults()),
			ConfigFactory.load("application"),
			ConfigFactory.load("application", ConfigParseOptions.defaults(), ConfigResolveOptions.defaults()),
			ConfigFactory.load(ConfigFactory.parseString("")),
			ConfigFactory.load(ConfigFactory.parseString(""), ConfigResolveOptions.defaults()),
			ConfigFactory.defaultReference()
		)
		) {
			assertFalse(c.hasPath("a"), "no a")
			assertFalse(c.hasPath("b"), "no b")
		}

		// check providing the loader via current context
		withContextClassLoader(loaderA1) {
			for (
			c in listOf(
				ConfigFactory.parseResources("reference.conf"),
				ConfigFactory.parseResourcesAnySyntax("reference"),
				ConfigFactory.parseResources("reference.conf", ConfigParseOptions.defaults()),
				ConfigFactory.parseResourcesAnySyntax("reference", ConfigParseOptions.defaults()),
				ConfigFactory.load("application"),
				ConfigFactory.load("application", ConfigParseOptions.defaults(), ConfigResolveOptions.defaults()),
				ConfigFactory.load(ConfigFactory.parseString("")),
				ConfigFactory.load(ConfigFactory.parseString(""), ConfigResolveOptions.defaults()),
				ConfigFactory.defaultReference()
			)
			) {
				assertEquals(1, c.getInt("a"))
				assertFalse(c.hasPath("b"), "no b")
			}
		}
	}

	@Test
	fun usesSuppliedClassLoaderForApplicationConf() {
		val loaderA1 = TestClassLoader(
			this::class.java.getClassLoader(),
			mapOf("application.conf" to resourceFile("a_1.conf").toURI().toURL())
		)
		val loaderB2 = TestClassLoader(
			this::class.java.getClassLoader(),
			mapOf("application.conf" to resourceFile("b_2.conf").toURI().toURL())
		)

		val configA1 = ConfigFactory.load(loaderA1)

		assertEquals(1, configA1.getInt("a"))
		assertFalse(configA1.hasPath("b"), "no b")

		val configB2 = ConfigFactory.load(loaderB2)

		assertEquals(2, configB2.getInt("b"))
		assertFalse(configB2.hasPath("a"), "no a")

		val configPlain = ConfigFactory.load()
		assertFalse(configPlain.hasPath("a"), "no a")
		assertFalse(configPlain.hasPath("b"), "no b")

		// check the various overloads that take a loader parameter
		for (
		c in listOf(
			ConfigFactory.parseResources(loaderA1, "application.conf"),
			ConfigFactory.parseResourcesAnySyntax(loaderA1, "application"),
			ConfigFactory.parseResources(loaderA1, "application.conf", ConfigParseOptions.defaults()),
			ConfigFactory.parseResourcesAnySyntax(loaderA1, "application", ConfigParseOptions.defaults()),
			ConfigFactory.load(loaderA1, "application"),
			ConfigFactory.load(loaderA1, "application", ConfigParseOptions.defaults(), ConfigResolveOptions.defaults()),
			ConfigFactory.defaultApplication(loaderA1)
		)
		) {
			assertEquals(1, c.getInt("a"))
			assertFalse(c.hasPath("b"), "no b")
		}

		// check providing the loader via ConfigParseOptions
		val withLoader = ConfigParseOptions.defaults().setClassLoader(loaderA1);
		for (
		c in listOf(
			ConfigFactory.parseResources("application.conf", withLoader),
			ConfigFactory.parseResourcesAnySyntax("application", withLoader),
			ConfigFactory.defaultApplication(withLoader),
			ConfigFactory.load(withLoader, ConfigResolveOptions.defaults()),
			ConfigFactory.load("application", withLoader, ConfigResolveOptions.defaults())
		)
		) {
			assertEquals(1, c.getInt("a"))
			assertFalse(c.hasPath("b"), "no b")
		}

		// check not providing the loader
		for (
		c in listOf(
			ConfigFactory.parseResources("application.conf"),
			ConfigFactory.parseResourcesAnySyntax("application"),
			ConfigFactory.parseResources("application.conf", ConfigParseOptions.defaults()),
			ConfigFactory.parseResourcesAnySyntax("application", ConfigParseOptions.defaults()),
			ConfigFactory.load("application"),
			ConfigFactory.defaultApplication(),
			ConfigFactory.load("application", ConfigParseOptions.defaults(), ConfigResolveOptions.defaults())
		)
		) {
			assertFalse(c.hasPath("a"), "no a")
			assertFalse(c.hasPath("b"), "no b")
		}

		// check providing the loader via current context
		withContextClassLoader(loaderA1) {
			for (
			c in listOf(
				ConfigFactory.parseResources("application.conf"),
				ConfigFactory.parseResourcesAnySyntax("application"),
				ConfigFactory.parseResources("application.conf", ConfigParseOptions.defaults()),
				ConfigFactory.parseResourcesAnySyntax("application", ConfigParseOptions.defaults()),
				ConfigFactory.load("application"),
				ConfigFactory.defaultApplication(),
				ConfigFactory.load("application", ConfigParseOptions.defaults(), ConfigResolveOptions.defaults())
			)
			) {
				assertEquals(1, c.getInt("a"))
				assertFalse(c.hasPath("b"), "no b")
			}
		}
	}

	@Test
	fun cachedDefaultConfig() {
		val load1 = ConfigFactory.load()
		val load2 = ConfigFactory.load()
		assertTrue(load1 == load2, "load() was cached")
		assertEquals(load1, load2)

		// the other loader has to have some reference.conf or else we just get
		// back the system properties singleton which is not per-class-loader
		val otherLoader = TestClassLoader(
			this::class.java.getClassLoader(),
			mapOf("reference.conf" to resourceFile("a_1.conf").toURI().toURL())
		)
		val load3 = ConfigFactory.load(otherLoader)
		val load4 = ConfigFactory.load(otherLoader)
		assertTrue(load1 !== load3, "different config for different classloaders")
		assertTrue(load3 === load4, "load(loader) was cached")
		assertEquals(load3, load4)

		val load5 = ConfigFactory.load()
		val load6 = ConfigFactory.load()
		assertTrue(load5 === load6, "load() was cached again")
		assertEquals(load5, load5)
		assertEquals(load1, load5)

		val load7 = ConfigFactory.load(otherLoader)
		assertTrue(load3 !== load7, "cache was dropped when switching loaders")
		assertEquals(load3, load7)
	}

	@Test
	fun cachedReferenceConfig() {
		val load1 = ConfigFactory.defaultReference()
		val load2 = ConfigFactory.defaultReference()
		assertTrue(load1 === load2, "defaultReference() was cached")
		assertEquals(load1, load2)

		// the other loader has to have some reference.conf or else we just get
		// back the system properties singleton which is not per-class-loader
		val otherLoader = TestClassLoader(
			this::class.java.getClassLoader(),
			mapOf("reference.conf" to resourceFile("a_1.conf").toURI().toURL())
		)
		val load3 = ConfigFactory.defaultReference(otherLoader)
		val load4 = ConfigFactory.defaultReference(otherLoader)
		assertTrue(load1 !== load3, "different config for different classloaders")
		assertTrue(load3 === load4, "defaultReference(loader) was cached")
		assertEquals(load3, load4)

		val load5 = ConfigFactory.defaultReference()
		val load6 = ConfigFactory.defaultReference()
		assertTrue(load5 === load6, "defaultReference() was cached again")
		assertEquals(load5, load5)
		assertEquals(load1, load5)

		val load7 = ConfigFactory.defaultReference(otherLoader)
		assertTrue(load3 !== load7, "cache was dropped when switching loaders")
		assertEquals(load3, load7)
	}

	@Test
	fun detectIncludeCycle() {
		val e = assertThrows(ConfigException.Parse::class.java) {
			ConfigFactory.load("cycle")
		}

		assertTrue(e.message!!.contains("include statements nested"), "wrong exception: " + e.message!!)
	}

	// We would ideally make this case NOT throw an exception but we need to do some work
// to get there, see https://github.com/lightbend/config/issues/160
	@Test
	fun detectIncludeFromList() {
		val e = assertThrows(ConfigException.Parse::class.java) {
			ConfigFactory.load("include-from-list.conf")
		}

		assertTrue(e.message!!.contains("limitation"), "wrong exception: " + e.message!!)
	}

	@Test
	fun missingOverrideResourceFails() {
		assertNull(System.getProperty("config.file"), "config.file is not set")
		val old = System.getProperty("config.resource")
		try {
			System.setProperty("config.resource", "donotexists.conf")
			assertThrows(ConfigException.IO::class.java) {
				ConfigFactory.load()
			}
		} finally {
			// cleanup properties
			old?.let { v ->
				System.setProperty("config.resource", v)
			} ?: run {
				System.clearProperty("config.resource")
			}
			assertEquals(old, System.getProperty("config.resource"), "config.resource restored")
			ConfigImpl.reloadSystemPropertiesConfig()
		}
	}

	@Test
	fun missingOverrideFileFails() {
		assertNull(System.getProperty("config.resource"), "config.resource is not set")
		val old = System.getProperty("config.file")
		try {
			System.setProperty("config.file", "donotexists.conf")
			assertThrows(ConfigException.IO::class.java) {
				ConfigFactory.load()
			}
		} finally {
			// cleanup properties
			old?.let { v ->
				System.setProperty("config.file", v)
				v
			} ?: run {
				System.clearProperty("config.file")
			}
			assertEquals(old, System.getProperty("config.file"), "config.file restored")
			ConfigImpl.reloadSystemPropertiesConfig()
		}
	}

	@Test
	fun exceptionSerializable() {
		// ArrayList is a serialization problem so we want to cover it in tests
		val comments = arrayListOf("comment 1", "comment 2")
		val e = ConfigException.WrongType(
			SimpleConfigOrigin.newSimple("an origin").withComments(comments),
			"this is a message", RuntimeException("this is a cause")
		)
		val eCopy = checkSerializableNoMeaningfulEquals(e)
		assertTrue(e.message!! == eCopy.message!!, "messages equal after deserialize")
		assertTrue(e.cause!!.message!! == eCopy.cause!!.message!!, "cause messages equal after deserialize")
		assertTrue(e.origin().equals(eCopy.origin()), "origins equal after deserialize")
	}

	@Test
	fun exceptionSerializableWithNullOrigin() {
		val e = ConfigException.Missing("this is a message", RuntimeException("this is a cause"))
		assertTrue(e.origin() == null, "origin null before serialize")
		val eCopy = checkSerializableNoMeaningfulEquals(e)
		assertTrue(e.message!! == eCopy.message!!, "messages equal after deserialize")
		assertTrue(e.cause!!.message!! == eCopy.cause!!.message!!, "cause messages equal after deserialize")
		assertTrue(e.origin() == null, "origin null after deserialize")
	}

	@Test
	fun exceptionSerializableWithWrongType() {
		val e = assertThrows(ConfigException.WrongType::class.java) {
			val o = ConfigValueFactory.fromAnyRef(mapOf("item" to "uhoh, fail"))
			if (o is ConfigObject) {
				o.toConfig().getStringList("item")
			}
		}
		val eCopy = checkSerializableNoMeaningfulEquals(e)
		assertTrue(e.message!! == eCopy.message!!, "messages equal after deserialize")
	}

	@Test
	fun invalidateCaches() {
		val conf0 = ConfigFactory.load()
		val sys0 = ConfigFactory.systemProperties()
		val conf1 = ConfigFactory.load()
		val sys1 = ConfigFactory.systemProperties()
		ConfigFactory.invalidateCaches()
		val conf2 = ConfigFactory.load()
		val sys2 = ConfigFactory.systemProperties()
		System.setProperty("invalidateCachesTest", "Hello!")
		ConfigFactory.invalidateCaches()
		val conf3 = ConfigFactory.load()
		val sys3 = ConfigFactory.systemProperties()
		val conf4 = ConfigFactory.load()
		val sys4 = ConfigFactory.systemProperties()
		System.clearProperty("invalidateCachesTest")

		assertTrue(sys0 === sys1, "stuff gets cached sys")
		assertTrue(conf0 === conf1, "stuff gets cached conf")

		assertTrue(!sys0.hasPath("invalidateCachesTest"), "test system property is not set sys")
		assertTrue(!conf0.hasPath("invalidateCachesTest"), "test system property is not set conf")

		assertTrue(sys1 !== sys2, "invalidate caches works on unchanged system props sys")
		assertTrue(conf1 !== conf2, "invalidate caches works on unchanged system props conf")

		assertTrue(sys2 !== sys3, "invalidate caches works on changed system props sys")
		assertTrue(conf2 !== conf3, "invalidate caches works on changed system props conf")

		assertEquals(sys1, sys2, "invalidate caches doesn't change value if no system prop changes sys")
		assertEquals(conf1, conf2, "invalidate caches doesn't change value if no system prop changes conf")

		assertTrue(sys3.hasPath("invalidateCachesTest"), "test system property is set sys")
		assertTrue(conf3.hasPath("invalidateCachesTest"), "test system property is set conf")

		assertTrue(sys2 !== sys3, "invalidate caches DOES change value if system props changed sys")
		assertTrue(conf2 !== conf3, "invalidate caches DOES change value if system props changed conf")

		assertTrue(sys3 === sys4, "stuff gets cached repeatedly sys")
		assertTrue(conf3 === conf4, "stuff gets cached repeatedly conf")
	}

	@Test
	fun invalidateReferenceConfig(): Unit {
		val orig = ConfigFactory.defaultReference()
		val cached = ConfigFactory.defaultReference()
		assertTrue(orig === cached, "reference config was cached")

		ConfigFactory.invalidateCaches()
		val changed = ConfigFactory.defaultReference()
		assertTrue(orig !== changed, "reference config was invalidated")
	}

	@Test
	fun invalidateFullConfig(): Unit {
		val orig = ConfigFactory.load()
		val cached = ConfigFactory.load()
		assertTrue(orig === cached, "full config was cached")

		ConfigFactory.invalidateCaches()
		val changed = ConfigFactory.load()
		assertTrue(orig !== changed, "full config was invalidated")
	}

	@Test
	fun canUseSomeValuesWithoutResolving(): Unit {
		val conf = ConfigFactory.parseString("a=42,b=\${NOPE}")
		assertEquals(42, conf.getInt("a"))
		assertThrows(ConfigException.NotResolved::class.java) {
			conf.getInt("b")
		}
	}

	@Test
	fun heuristicIncludeChecksClasspath(): Unit {
		// from https://github.com/lightbend/config/issues/188
		withScratchDirectory("heuristicIncludeChecksClasspath") { dir ->
			val f = File(dir, "foo.conf")
			writeFile(
				f,
				"""
include "onclasspath"
"""
			)
			val conf = ConfigFactory.parseFile(f)
			assertEquals(42, conf.getInt("onclasspath"))
		}
	}

	@Test
	fun fileIncludeStatements(): Unit {
		val file = resourceFile("file-include.conf")
		val conf = ConfigFactory.parseFile(file)
		assertEquals(41, conf.getInt("base"), "got file-include.conf")
		assertEquals(42, conf.getInt("foo"), "got subdir/foo.conf")
		assertEquals(43, conf.getInt("bar"), "got bar.conf")

		// these two do not work right now, because we do not
		// treat the filename as relative to the including file
		// if file() is specified, so `include file("bar-file.conf")`
		// fails.
		//assertEquals("got bar-file.conf", 44, conf.getInt("bar-file"))
		//assertEquals("got subdir/baz.conf", 45, conf.getInt("baz"))
		assertFalse(conf.hasPath("bar-file"), "did not get bar-file.conf")
		assertFalse(conf.hasPath("baz"), "did not get subdir/baz.conf")
	}

	@Test
	fun hasPathOrNullWorks(): Unit {
		val conf = ConfigFactory.parseString("x.a=null,x.b=42")
		assertFalse(conf.hasPath("x.a"), "hasPath says false for null")
		assertTrue(conf.hasPathOrNull("x.a"), "hasPathOrNull says true for null")

		assertTrue(conf.hasPath("x.b"), "hasPath says true for non-null")
		assertTrue(conf.hasPathOrNull("x.b"), "hasPathOrNull says true for non-null")

		assertFalse(conf.hasPath("x.c"), "hasPath says false for missing")
		assertFalse(conf.hasPathOrNull("x.c"), "hasPathOrNull says false for missing")

		// this is to be sure we handle a null along the path correctly
		assertFalse(conf.hasPath("x.a.y"), "hasPath says false for missing under null")
		assertFalse(conf.hasPathOrNull("x.a.y"), "hasPathOrNull says false for missing under null")

		// this is to be sure we handle missing along the path correctly
		assertFalse(conf.hasPath("x.c.y"), "hasPath says false for missing under missing")
		assertFalse(conf.hasPathOrNull("x.c.y"), "hasPathOrNull says false for missing under missing")
	}

	@Test
	fun getIsNullWorks(): Unit {
		val conf = ConfigFactory.parseString("x.a=null,x.b=42")

		assertTrue(conf.getIsNull("x.a"), "getIsNull says true for null")
		assertFalse(conf.getIsNull("x.b"), "getIsNull says false for non-null")
		assertThrows(ConfigException.Missing::class.java) {
			conf.getIsNull("x.c")
		}
		// missing underneath null
		assertThrows(ConfigException.Missing::class.java) {
			conf.getIsNull("x.a.y")
		}
		// missing underneath missing
		assertThrows(ConfigException.Missing::class.java) {
			conf.getIsNull("x.c.y")
		}
	}

	@Test
	fun applicationConfCanOverrideReferenceConf(): Unit {
		val loader = TestClassLoader(
			this::class.java.getClassLoader(),
			mapOf(
				"reference.conf" to resourceFile("test13-reference-with-substitutions.conf").toURI().toURL(),
				"application.conf" to resourceFile("test13-application-override-substitutions.conf").toURI().toURL()
			)
		)

		assertEquals("b", ConfigFactory.defaultReference(loader).getString("a"))
		assertEquals("overridden", ConfigFactory.load(loader).getString("a"))
	}

	@Test
	fun referenceConfMustResolveIndependently(): Unit {
		val loader = TestClassLoader(
			this::class.java.getClassLoader(),
			mapOf(
				"reference.conf" to resourceFile("test13-reference-bad-substitutions.conf").toURI().toURL(),
				"application.conf" to resourceFile("test13-application-override-substitutions.conf").toURI().toURL()
			)
		)

		assertThrows(ConfigException.UnresolvedSubstitution::class.java) {
			ConfigFactory.load(loader)
		}
	}

	private fun testFromValue(expectedValue: ConfigValue, createFrom: Any?) {
		assertEquals(expectedValue, ConfigValueFactory.fromAnyRef(createFrom))

		assertEquals(expectedValue, ConfigValueFactory.fromAnyRef(createFrom, "foo"))

		// description is ignored for createFrom that is already a ConfigValue
		when (createFrom) {
			is ConfigValue -> assertEquals(
				createFrom.origin().description(),
				ConfigValueFactory.fromAnyRef(createFrom).origin().description()
			)

			else -> {
				assertEquals(defaultValueDesc, ConfigValueFactory.fromAnyRef(createFrom).origin().description())
				assertEquals("foo", ConfigValueFactory.fromAnyRef(createFrom, "foo").origin().description())
			}
		}
	}

	private fun testFromPathMap(expectedValue: ConfigObject, createFrom: Map<String, Any>) {
		assertEquals(expectedValue, ConfigFactory.parseMap(createFrom).root())
		assertEquals(defaultValueDesc, ConfigFactory.parseMap(createFrom).origin().description())
		assertEquals(expectedValue, ConfigFactory.parseMap(createFrom, "foo").root())
		assertEquals("foo", ConfigFactory.parseMap(createFrom, "foo").origin().description())
	}

	private fun assertNotFound(e: ConfigException) {
		assertTrue(
			e.message!!.contains("No such") ||
					e.message!!.contains("not found") ||
					e.message!!.contains("were found") ||
					e.message!!.contains("java.io.FileNotFoundException"), "Message text: " + e.message!!
		)
	}

	private fun whatWasIncluded(parser: (ConfigParseOptions) -> Config): List<Included> {
		val included = mutableListOf<Included>()
		val includer = RecordingIncluder(null, included)

		val conf = parser(ConfigParseOptions.defaults().setIncluder(includer).setAllowMissing(false))

		return included.toList()
	}

	private fun whatWasIncludedFull(parser: (ConfigParseOptions) -> Config): List<Included> {
		val included = mutableListOf<Included>()
		val includer = RecordingFullIncluder(null, included)

		val conf = parser(ConfigParseOptions.defaults().setIncluder(includer).setAllowMissing(false))

		return included.toList()
	}

	sealed interface IncludeKind

	data class Included(val name: String, val fallback: ConfigIncluder?, val kind: IncludeKind)

	open class RecordingIncluder(val fallback: ConfigIncluder?, val included: MutableList<Included>) : ConfigIncluder {
		override fun include(context: ConfigIncludeContext, name: String): ConfigObject? {
			included += Included(name, fallback, IncludeKindHeuristic)
			return fallback?.include(context, name)
		}

		override fun withFallback(fallback: ConfigIncluder): ConfigIncluder? {
			return when (this.fallback) {
				fallback -> this
				null -> RecordingIncluder(fallback, included)
				else -> RecordingIncluder(this.fallback.withFallback(fallback), included)
			}
		}
	}

	class RecordingFullIncluder(fallback: ConfigIncluder?, included: MutableList<Included>) :
		RecordingIncluder(fallback, included), ConfigIncluderFile, ConfigIncluderURL, ConfigIncluderClasspath {
		override fun includeFile(context: ConfigIncludeContext, file: File): ConfigObject {
			included += Included("file(" + file.getName() + ")", fallback, IncludeKindFile)
			return (fallback as ConfigIncluderFile).includeFile(context, file)
		}

		override fun includeURI(context: ConfigIncludeContext, url: URI): ConfigObject {
			included += Included("url(" + url.toASCIIString() + ")", fallback, IncludeKindURL)
			return (fallback as ConfigIncluderURL).includeURI(context, url)
		}

		override fun includeResources(context: ConfigIncludeContext, name: String): ConfigObject {
			included += Included("classpath(" + name + ")", fallback, IncludeKindFile)
			return (fallback as ConfigIncluderClasspath).includeResources(context, name)
		}

		override fun withFallback(fallback: ConfigIncluder): ConfigIncluder {
			return when (this.fallback) {
				fallback -> this
				null -> RecordingFullIncluder(fallback, included)
				else -> RecordingFullIncluder(this.fallback.withFallback(fallback), included)
			}
		}
	}

	data object IncludeKindHeuristic : IncludeKind

	data object IncludeKindFile : IncludeKind

	data object IncludeKindURL : IncludeKind

	data object IncludeKindClasspath : IncludeKind

}

class TestStrategy : DefaultConfigLoadingStrategy() {
	override fun parseApplicationConfig(parseOptions: ConfigParseOptions): Config {
		TestStrategy.increment()
		return super.parseApplicationConfig(parseOptions)
	}

	companion object {
		var invocations = 0
			private set

		fun increment() {
			invocations += 1
		}
	}
}
