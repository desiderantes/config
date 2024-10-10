/**
 * Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import com.typesafe.config.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import java.io.File

class EquivalentsTest : TestUtils() {

	// would like each "equivNN" directory to be a suite and each file in the dir
	// to be a test, but not sure how to convince junit to do that.
	@Test
	fun testEquivalents() {
		var dirCount = 0
		var fileCount = 0
		for (equiv in equivDirs()) {
			dirCount += 1

			val files = filesForEquiv(equiv)
			val (originals, others) = files.partition { f -> f.getName().startsWith("original.") }
			if (originals.isEmpty())
				throw RuntimeException("Need a file named 'original' in " + equiv.path)
			if (originals.size > 1)
				throw RuntimeException("Multiple 'original' files in " + equiv.path + ": " + originals)
			val original = parse(originals[0])

			for (testFile in others) {
				fileCount += 1
				val value = parse(testFile)
				describeFailure(testFile.path) {
					try {
						assertEquals(original, value)
					} catch (e: Throwable) {
						showDiff(original, value)
						throw e
					}
				}

				// check that all .json files can be parsed as .conf,
				// i.e. .conf must be a superset of JSON
				if (testFile.getName().endsWith(".json")) {
					val parsedAsConf = parse(ConfigSyntax.CONF, testFile)
					describeFailure(testFile.path + " parsed as .conf") {
						try {
							assertEquals(original, parsedAsConf)
						} catch (e: Throwable) {
							showDiff(original, parsedAsConf)
							throw e
						}
					}
				}
			}
		}

		// This is a little "checksum" to be sure we really tested what we were expecting.
		// it breaks every time you add a file, so you have to update it.
		assertEquals(5, dirCount)
		// this is the number of files not named original.*
		assertEquals(15, fileCount)
	}

	private fun equivDirs(): List<File> {
		val rawEquivs = resourceDir.listFiles()
		val equivs = rawEquivs.filter { f -> f.getName().startsWith("equiv") }
		return equivs
	}

	private fun filesForEquiv(equiv: File): List<File> {
		val rawFiles = equiv.listFiles()
		val files = rawFiles.filter { f -> f.getName().endsWith(".json") || f.getName().endsWith(".conf") }
		return files
	}

	private fun postParse(value: ConfigValue): ConfigValue {
		return when (value) {
			is AbstractConfigObject -> {
				// for purposes of these tests, substitutions are only
				// against the same file's root, and without looking at
				// system prop or env variable fallbacks.
				ResolveContext.resolve(value, value, ConfigResolveOptions.noSystem())!!
			}

			else -> value
		}
	}

	private fun parse(flavor: ConfigSyntax, f: File): ConfigValue {
		val options = ConfigParseOptions.defaults().setSyntax(flavor)
		return postParse(ConfigFactory.parseFile(f, options).root())
	}

	private fun parse(f: File): ConfigValue {
		val options = ConfigParseOptions.defaults()
		return postParse(ConfigFactory.parseFile(f, options).root())
	}
}
