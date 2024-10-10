/**
 * Copyright (C) 2015 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl

import com.typesafe.config.ConfigMemorySize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import java.math.BigInteger

class ConfigMemorySizeTest : TestUtils() {

	@Test
	fun testEquals() {
		assertTrue(
			ConfigMemorySize(10) == ConfigMemorySize(10),
			"Equal ConfigMemorySize are equal"
		)
		assertTrue(
			ConfigMemorySize(10) != ConfigMemorySize(11),
			"Different ConfigMemorySize are not equal"
		)
	}

	@Test
	fun testToUnits() {
		val kilobyte = ConfigMemorySize(1024)
		assertEquals(1024, kilobyte.toLongBytes())
	}

	@Test
	fun testGetBytes() {
		val yottabyte = ConfigMemorySize(BigInteger("1000000000000000000000000"))
		assertEquals(BigInteger("1000000000000000000000000"), yottabyte.bytes())
	}
}
