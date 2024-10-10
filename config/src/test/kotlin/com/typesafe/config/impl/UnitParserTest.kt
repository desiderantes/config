/**
 * Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit

class UnitParserTest : TestUtils() {

	@Test
	fun parseDuration(): Unit {
		val oneSecs = listOf(
			"1s", "1 s", "1seconds", "1 seconds", "   1s    ", "   1    s   ",
			"1second",
			"1000", "1000ms", "1000 ms", "1000   milliseconds", "   1000       milliseconds    ",
			"1000millisecond",
			"1000000us", "1000000   us", "1000000 microseconds", "1000000microsecond",
			"1000000000ns", "1000000000 ns", "1000000000  nanoseconds", "1000000000nanosecond",
			"0.01666666666666666666666m", "0.01666666666666666666666 minutes", "0.01666666666666666666666 minute",
			"0.00027777777777777777777h", "0.00027777777777777777777 hours", "0.00027777777777777777777hour",
			"1.1574074074074073e-05d", "1.1574074074074073e-05  days", "1.1574074074074073e-05day"
		)
		val oneSecInNanos = TimeUnit.SECONDS.toNanos(1)
		oneSecs.forEach { s ->
			val result = SimpleConfig.parseDuration(s, fakeOrigin(), "test")
			assertEquals(oneSecInNanos, result)
		}

		// bad units
		val e = assertThrows(ConfigException.BadValue::class.java) {
			SimpleConfig.parseDuration("100 dollars", fakeOrigin(), "test")
		}
		assertTrue(e.message!!.contains("time unit"))

		// bad number
		val e2 = assertThrows(ConfigException.BadValue::class.java) {
			SimpleConfig.parseDuration("1 00 seconds", fakeOrigin(), "test")
		}
		assertTrue(e2.message!!.contains("duration number"))
	}

	@Test
	fun parsePeriod(): Unit {
		val oneYears = listOf(
			"1y", "1 y", "1year", "1 years", "   1y   ", "   1   y    ",
			"365", "365d", "365 d", "365 days", "   365   days   ", "365day",
			"12m", "12mo", "12 m", "   12   mo   ", "12 months", "12month"
		)
		val epochDate = LocalDate.ofEpochDay(0)
		val oneYear = ChronoUnit.DAYS.between(epochDate, epochDate.plus(Period.ofYears(1)))
		oneYears.forEach { y ->
			val period = SimpleConfig.parsePeriod(y, fakeOrigin(), "test")
			val dayCount = ChronoUnit.DAYS.between(epochDate, epochDate.plus(period))
			assertEquals(oneYear, dayCount)
		}

		// bad units
		val e = assertThrows(ConfigException.BadValue::class.java) {
			SimpleConfig.parsePeriod("100 dollars", fakeOrigin(), "test")
		}
		assertTrue(e.message!!.contains("time unit"), "${e.message!!} was not the expected error message")

		// bad number
		val e2 = assertThrows(ConfigException.BadValue::class.java) {
			SimpleConfig.parsePeriod("1 00 seconds", fakeOrigin(), "test")
		}
		assertTrue(e2.message!!.contains("time unit 'seconds'"), "${e2.message} was not the expected error message")
	}

	// https://github.com/lightbend/config/issues/117
	// this broke because "1d" is a valid double for parseDouble
	@Test
	fun parseOneDayAsMilliseconds(): Unit {
		val result = SimpleConfig.parseDuration("1d", fakeOrigin(), "test")
		val dayInNanos = TimeUnit.DAYS.toNanos(1)
		assertEquals(dayInNanos, result, "could parse 1d")

		val conf = parseConfig("foo = 1d")
		assertEquals(
			1L,
			conf.getDuration("foo", ChronoUnit.DAYS), "could get 1d from conf as days"
		)
		assertEquals(
			dayInNanos,
			conf.getDuration("foo", ChronoUnit.NANOS), "could get 1d from conf as nanos"
		)
		assertEquals(
			TimeUnit.DAYS.toMillis(1),
			conf.getDuration("foo", ChronoUnit.MILLIS), "could get 1d from conf as millis"
		)
	}

	@Test
	fun parseMemorySizeInBytes(): Unit {
		fun parseMem(s: String): BigInteger = SimpleConfig.parseBytes(s, fakeOrigin(), "test")

		assertEquals(BigInteger.valueOf(Long.MAX_VALUE), parseMem("${Long.MAX_VALUE} bytes"))
		assertEquals(BigInteger.valueOf(Long.MIN_VALUE), parseMem("${Long.MIN_VALUE} bytes"))

		val oneMebis = listOf(
			"1048576",
			"1048576b",
			"1048576bytes",
			"1048576byte",
			"1048576  b",
			"1048576  bytes",
			"    1048576  b   ",
			"  1048576  bytes   ",
			"1048576B",
			"1024k",
			"1024K",
			"1024Ki",
			"1024KiB",
			"1024 kibibytes",
			"1024 kibibyte",
			"1m",
			"1M",
			"1 M",
			"1Mi",
			"1MiB",
			"1 mebibytes",
			"1 mebibyte",
			"0.0009765625g",
			"0.0009765625G",
			"0.0009765625Gi",
			"0.0009765625GiB",
			"0.0009765625 gibibytes",
			"0.0009765625 gibibyte"
		)

		for (s in oneMebis) {
			val result = parseMem(s)
			assertEquals(BigInteger.valueOf(1024 * 1024), result)
		}

		val oneMegas = listOf(
			"1000000", "1000000b", "1000000bytes", "1000000byte",
			"1000000  b", "1000000  bytes",
			"    1000000  b   ", "  1000000  bytes   ",
			"1000000B",
			"1000kB", "1000 kilobytes", "1000 kilobyte",
			"1MB", "1 megabytes", "1 megabyte",
			".001GB", ".001 gigabytes", ".001 gigabyte"
		)

		for (s in oneMegas) {
			val result = parseMem(s)
			assertEquals(BigInteger.valueOf(1000 * 1000), result)
		}

		var result = BigInteger.valueOf(1024L * 1024 * 1024)
		for (unit in listOf("tebi", "pebi", "exbi", "zebi", "yobi")) {
			val first = unit.substring(0, 1).uppercase(Locale.getDefault())
			result = result.multiply(BigInteger.valueOf(1024))
			assertEquals(result, parseMem("1$first"))
			assertEquals(result, parseMem("1" + first + "i"))
			assertEquals(result, parseMem("1" + first + "iB"))
			assertEquals(result, parseMem("1" + unit + "byte"))
			assertEquals(result, parseMem("1" + unit + "bytes"))
		}

		result = BigInteger.valueOf(1000L * 1000 * 1000)
		for (unit in listOf("tera", "peta", "exa", "zetta", "yotta")) {
			val first = unit.substring(0, 1).uppercase(Locale.getDefault())
			result = result.multiply(BigInteger.valueOf(1000))
			assertEquals(result, parseMem("1" + first + "B"))
			assertEquals(result, parseMem("1" + unit + "byte"))
			assertEquals(result, parseMem("1" + unit + "bytes"))
		}

		// bad units
		val e = assertThrows(ConfigException.BadValue::class.java) {
			SimpleConfig.parseBytes("100 dollars", fakeOrigin(), "test")
		}
		assertTrue(e.message!!.contains("size-in-bytes unit"))

		// bad number
		val e2 = assertThrows(ConfigException.BadValue::class.java) {
			SimpleConfig.parseBytes("1 00 bytes", fakeOrigin(), "test")
		}
		assertTrue(e2.message!!.contains("size-in-bytes number"))
	}

	// later on we'll want to check this with BigInteger version of getBytes
	@Test
	fun parseHugeMemorySizes(): Unit {
		fun parseMem(s: String): Long = ConfigFactory.parseString("v = $s").getBytes("v")

		fun assertOutOfRange(s: String): Unit {
			val fail = assertThrows(ConfigException.BadValue::class.java) {
				parseMem(s)
			}
			assertTrue(fail.message!!.contains("out of range"), "number was too big")
		}

		fun assertNegativeNumber(s: String): Unit {
			val fail = assertThrows(ConfigException.BadValue::class.java) {
				parseMem(s)
			}
			assertTrue(fail.message!!.contains("negative number"), "number was negative")
		}

		assertOutOfRange("${BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(1))} bytes")
		assertNegativeNumber("${BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.valueOf(1))} bytes")

		for (unit in listOf("zebi", "yobi")) {
			val first = unit.substring(0, 1).uppercase(Locale.getDefault())
			assertOutOfRange("1$first")
			assertOutOfRange("1" + first + "i")
			assertOutOfRange("1" + first + "iB")
			assertOutOfRange("1" + unit + "byte")
			assertOutOfRange("1" + unit + "bytes")
			assertOutOfRange("1.1$first")
			assertNegativeNumber("-1$first")
		}

		for (unit in listOf("zetta", "yotta")) {
			val first = unit.substring(0, 1).uppercase(Locale.getDefault())
			assertOutOfRange("1" + first + "B")
			assertOutOfRange("1" + unit + "byte")
			assertOutOfRange("1" + unit + "bytes")
			assertOutOfRange("1.1" + first + "B")
			assertNegativeNumber("-1" + first + "B")
		}

		assertOutOfRange("1000 exabytes")
		assertOutOfRange("10000000 petabytes")
	}
}
