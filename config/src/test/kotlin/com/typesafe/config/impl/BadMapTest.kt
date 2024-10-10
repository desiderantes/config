package com.typesafe.config.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BadMapTest : TestUtils() {
	@Test
	fun copyingPut() {
		val map = BadMap<String, String>()
		val copy = map.copyingPut("key", "value")

		assertNull(map.get("key"))
		assertEquals("value", copy.get("key"))
	}

	@Test
	fun retrieveOldElement() {
		val map = BadMap<String, String>()
			.copyingPut("key1", "value1")
			.copyingPut("key2", "value2")
			.copyingPut("key3", "value3")

		assertEquals("value1", map.get("key1"))
		assertEquals("value2", map.get("key2"))
		assertEquals("value3", map.get("key3"))
	}

	@Test
	fun putOverride() {
		val map = BadMap<String, String>()
			.copyingPut("key", "value1")
			.copyingPut("key", "value2")
			.copyingPut("key", "value3")

		assertEquals("value3", map.get("key"))
	}

	@Test
	fun notFound() {
		val map = BadMap<String, String>()

		assertNull(map.get("invalid key"))
	}

	@Test
	fun putMany() {
		val entries = (1..1000).map { i -> "key$i" to "value$i" }
		var map = BadMap<String, String>()

		for ((key, value) in entries) {
			map = map.copyingPut(key, value)
		}

		for ((key, value) in entries) {
			assertEquals(value, map.get(key))
		}
	}

	@Test
	fun putSameHash() {
		val hash = 2
		val entries = (1..10).map { i -> UniqueKeyWithHash(hash) to "value$i" }
		var map = BadMap<UniqueKeyWithHash, String>()

		for ((key, value) in entries) {
			map = map.copyingPut(key, value)
		}

		for ((key, value) in entries) {
			assertEquals(value, map.get(key))
		}
	}

	@Test
	fun putSameHashModLength() {
		// given that the table will eventually be the following size, we insert entries who should
		// eventually all share the same index and then later be redistributed once rehashed
		val size = 11
		val entries = (1..size * 2).map { i -> (UniqueKeyWithHash(size * i) to "value$i") }
		var map = BadMap<UniqueKeyWithHash, String>()

		for ((key, value) in entries) {
			map = map.copyingPut(key, value)
		}

		for ((key, value) in entries) {
			assertEquals(value, map.get(key))
		}
	}

	private class UniqueKeyWithHash(val hash: Int) {
		override fun hashCode(): Int = hash
	}
}
