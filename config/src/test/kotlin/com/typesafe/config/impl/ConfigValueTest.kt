/**
 * Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */
package com.typesafe.config.impl


import com.typesafe.config.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.URI

class ConfigValueTest : TestUtils() {

	@Test
	fun configOriginEquality() {
		val a = SimpleConfigOrigin.newSimple("foo")
		val sameAsA = SimpleConfigOrigin.newSimple("foo")
		val b = SimpleConfigOrigin.newSimple("bar")

		checkEqualObjects(a, a)
		checkEqualObjects(a, sameAsA)
		checkNotEqualObjects(a, b)
	}

	@Test
	fun configOriginNotSerializable() {
		val a = SimpleConfigOrigin.newSimple("foo")
		checkNotSerializable(a)
	}

	@Test
	fun configIntEquality() {
		val a = intValue(42)
		val sameAsA = intValue(42)
		val b = intValue(43)

		checkEqualObjects(a, a)
		checkEqualObjects(a, sameAsA)
		checkNotEqualObjects(a, b)
	}

	@Test
	fun configIntSerializable() {
		val expectedSerialization = "" +
				"ACED0005_s_r00_._c_o_m_._t_y_p_e_s_a_f_e_._c_o_n_f_i_g_._i_m_p_l_._S_e_r_i_a_l_i" +
				"_z_e_d_C_o_n_f_i_g_V_a_l_u_e00000000000000010C0000_x_p_w_902000000_-050000001906" +
				"0000000D000B_f_a_k_e_ _o_r_i_g_i_n090000000100010400000009020000002A0002_4_20103" +
				"000000010001_x"
		val a = intValue(42)
		val b = checkSerializable(expectedSerialization, a)
		assertEquals(42, b.unwrapped())
	}

	@Test
	fun configLongEquality() {
		val a = longValue(Integer.MAX_VALUE + 42L)
		val sameAsA = longValue(Integer.MAX_VALUE + 42L)
		val b = longValue(Integer.MAX_VALUE + 43L)

		checkEqualObjects(a, a)
		checkEqualObjects(a, sameAsA)
		checkNotEqualObjects(a, b)
	}

	@Test
	fun configLongSerializable() {
		val expectedSerialization = "" +
				"ACED0005_s_r00_._c_o_m_._t_y_p_e_s_a_f_e_._c_o_n_f_i_g_._i_m_p_l_._S_e_r_i_a_l_i" +
				"_z_e_d_C_o_n_f_i_g_V_a_l_u_e00000000000000010C0000_x_p_w_E02000000_9050000001906" +
				"0000000D000B_f_a_k_e_ _o_r_i_g_i_n090000000100010400000015030000000080000029000A" +
				"_2_1_4_7_4_8_3_6_8_90103000000010001_x"

		val a = longValue(Integer.MAX_VALUE + 42L)
		val b = checkSerializable(expectedSerialization, a)
		assertEquals(Integer.MAX_VALUE + 42L, b.unwrapped())
	}

	@Test
	fun configIntAndLongEquality() {
		val longVal = longValue(42L)
		val intValue = longValue(42)
		val longValueB = longValue(43L)
		val intValueB = longValue(43)

		checkEqualObjects(intValue, longVal)
		checkEqualObjects(intValueB, longValueB)
		checkNotEqualObjects(intValue, longValueB)
		checkNotEqualObjects(intValueB, longVal)
	}

	@Test
	fun configDoubleEquality() {
		val a = doubleValue(3.14)
		val sameAsA = doubleValue(3.14)
		val b = doubleValue(4.14)

		checkEqualObjects(a, a)
		checkEqualObjects(a, sameAsA)
		checkNotEqualObjects(a, b)
	}

	@Test
	fun configDoubleSerializable() {
		val expectedSerialization = "" +
				"ACED0005_s_r00_._c_o_m_._t_y_p_e_s_a_f_e_._c_o_n_f_i_g_._i_m_p_l_._S_e_r_i_a_l_i" +
				"_z_e_d_C_o_n_f_i_g_V_a_l_u_e00000000000000010C0000_x_p_w3F02000000_3050000001906" +
				"0000000D000B_f_a_k_e_ _o_r_i_g_i_n09000000010001040000000F0440091EB8_QEB851F0004" +
				"_3_._1_40103000000010001_x"

		val a = doubleValue(3.14)
		val b = checkSerializable(expectedSerialization, a)
		assertEquals(3.14, b.unwrapped())
	}

	@Test
	fun configIntAndDoubleEquality() {
		val doubleVal = doubleValue(3.0)
		val intValue = longValue(3)
		val doubleValueB = doubleValue(4.0)
		val intValueB = intValue(4)

		checkEqualObjects(intValue, doubleVal)
		checkEqualObjects(intValueB, doubleValueB)
		checkNotEqualObjects(intValue, doubleValueB)
		checkNotEqualObjects(intValueB, doubleVal)
	}

	@Test
	fun configNullSerializable() {
		val expectedSerialization = "" +
				"ACED0005_s_r00_._c_o_m_._t_y_p_e_s_a_f_e_._c_o_n_f_i_g_._i_m_p_l_._S_e_r_i_a_l_i" +
				"_z_e_d_C_o_n_f_i_g_V_a_l_u_e00000000000000010C0000_x_p_w_10200000025050000001906" +
				"0000000D000B_f_a_k_e_ _o_r_i_g_i_n090000000100010400000001000103000000010001_x"

		val a = nullValue()
		val b = checkSerializable(expectedSerialization, a)
		assertNull(b.unwrapped(), "b is null")
	}

	@Test
	fun configBooleanSerializable() {
		val expectedSerialization = "" +
				"ACED0005_s_r00_._c_o_m_._t_y_p_e_s_a_f_e_._c_o_n_f_i_g_._i_m_p_l_._S_e_r_i_a_l_i" +
				"_z_e_d_C_o_n_f_i_g_V_a_l_u_e00000000000000010C0000_x_p_w_20200000026050000001906" +
				"0000000D000B_f_a_k_e_ _o_r_i_g_i_n09000000010001040000000201010103000000010001_x"

		val a = boolValue(true)
		val b = checkSerializable(expectedSerialization, a)
		assertEquals(true, b.unwrapped())
	}

	@Test
	fun configStringSerializable() {
		val expectedSerialization = "" +
				"ACED0005_s_r00_._c_o_m_._t_y_p_e_s_a_f_e_._c_o_n_f_i_g_._i_m_p_l_._S_e_r_i_a_l_i" +
				"_z_e_d_C_o_n_f_i_g_V_a_l_u_e00000000000000010C0000_x_p_w_F02000000_:050000001906" +
				"0000000D000B_f_a_k_e_ _o_r_i_g_i_n090000000100010400000016050013_T_h_e_ _q_u_i_c" +
				"_k_ _b_r_o_w_n_ _f_o_x0103000000010001_x"

		val a = stringValue("The quick brown fox")
		val b = checkSerializable(expectedSerialization, a)
		assertEquals("The quick brown fox", b.unwrapped())
	}

	@Test
	fun configObjectEquality() {
		val aMap = configMap("a" to 1, "b" to 2, "c" to 3)
		val sameAsAMap = configMap("a" to 1, "b" to 2, "c" to 3)
		val bMap = configMap("a" to 3, "b" to 4, "c" to 5)
		// different keys is a different case in the equals implementation
		val cMap = configMap("x" to 3, "y" to 4, "z" to 5)
		val a = SimpleConfigObject(fakeOrigin(), aMap)
		val sameAsA = SimpleConfigObject(fakeOrigin(), sameAsAMap)
		val b = SimpleConfigObject(fakeOrigin(), bMap)
		val c = SimpleConfigObject(fakeOrigin(), cMap)

		checkEqualObjects(a, a)
		checkEqualObjects(a, sameAsA)
		checkEqualObjects(b, b)
		checkEqualObjects(c, c)
		checkNotEqualObjects(a, b)
		checkNotEqualObjects(a, c)
		checkNotEqualObjects(b, c)

		// the config for an equal object is also equal
		val config = a.toConfig()
		checkEqualObjects(config, config)
		checkEqualObjects(config, sameAsA.toConfig())
		checkEqualObjects(a.toConfig(), config)
		checkNotEqualObjects(config, b.toConfig())
		checkNotEqualObjects(config, c.toConfig())

		// configs are not equal to objects
		checkNotEqualObjects(a, a.toConfig())
		checkNotEqualObjects(b, b.toConfig())
	}

	@Test
	fun java6ConfigObjectSerializable() {
		val expectedSerialization = "" +
				"ACED0005_s_r00_._c_o_m_._t_y_p_e_s_a_f_e_._c_o_n_f_i_g_._i_m_p_l_._S_e_r_i_a_l_i" +
				"_z_e_d_C_o_n_f_i_g_V_a_l_u_e00000000000000010C0000_x_p_w_z02000000_n050000001906" +
				"0000000D000B_f_a_k_e_ _o_r_i_g_i_n0900000001000104000000_J07000000030001_a050000" +
				"000101040000000802000000010001_1010001_c050000000101040000000802000000030001_301" +
				"0001_b050000000101040000000802000000020001_2010103000000010001_x"

		val aMap = configMap("a" to 1, "b" to 2, "c" to 3)
		val a = SimpleConfigObject(fakeOrigin(), aMap)
		val b = checkSerializableOldFormat(expectedSerialization, a)
		assertEquals(1, b.toConfig().getInt("a"))
		// check that deserialized Config and ConfigObject refer to each other
		assertTrue(b.toConfig().root() == b)
	}

	@Test
	fun java6ConfigConfigSerializable() {
		val expectedSerialization = "" +
				"ACED0005_s_r00_._c_o_m_._t_y_p_e_s_a_f_e_._c_o_n_f_i_g_._i_m_p_l_._S_e_r_i_a_l_i" +
				"_z_e_d_C_o_n_f_i_g_V_a_l_u_e00000000000000010C0000_x_p_w_z02000000_n050000001906" +
				"0000000D000B_f_a_k_e_ _o_r_i_g_i_n0900000001000104000000_J07000000030001_a050000" +
				"000101040000000802000000010001_1010001_c050000000101040000000802000000030001_301" +
				"0001_b050000000101040000000802000000020001_2010103000000010101_x"

		val aMap = configMap("a" to 1, "b" to 2, "c" to 3)
		val a = SimpleConfigObject(fakeOrigin(), aMap)
		val b = checkSerializableOldFormat(expectedSerialization, a.toConfig())
		assertEquals(1, b.getInt("a"))
		// check that deserialized Config and ConfigObject refer to each other
		assertTrue(b.root().toConfig() == b)
	}

	@Test
	fun configObjectSerializable() {
		val expectedSerialization = "" +
				"ACED0005_s_r00_._c_o_m_._t_y_p_e_s_a_f_e_._c_o_n_f_i_g_._i_m_p_l_._S_e_r_i_a_l_i" +
				"_z_e_d_C_o_n_f_i_g_V_a_l_u_e00000000000000010C0000_x_p_w_z02000000_n050000001906" +
				"0000000D000B_f_a_k_e_ _o_r_i_g_i_n0900000001000104000000_J07000000030001_a050000" +
				"000101040000000802000000010001_1010001_b050000000101040000000802000000020001_201" +
				"0001_c050000000101040000000802000000030001_3010103000000010001_x"

		val aMap = configMap("a" to 1, "b" to 2, "c" to 3)
		val a = SimpleConfigObject(fakeOrigin(), aMap)
		val b = checkSerializable(expectedSerialization, a)
		assertEquals(1, b.toConfig().getInt("a"))
		// check that deserialized Config and ConfigObject refer to each other
		assertTrue(b.toConfig().root() == b)
	}

	@Test
	fun configConfigSerializable() {
		val expectedSerialization = "" +
				"ACED0005_s_r00_._c_o_m_._t_y_p_e_s_a_f_e_._c_o_n_f_i_g_._i_m_p_l_._S_e_r_i_a_l_i" +
				"_z_e_d_C_o_n_f_i_g_V_a_l_u_e00000000000000010C0000_x_p_w_z02000000_n050000001906" +
				"0000000D000B_f_a_k_e_ _o_r_i_g_i_n0900000001000104000000_J07000000030001_a050000" +
				"000101040000000802000000010001_1010001_b050000000101040000000802000000020001_201" +
				"0001_c050000000101040000000802000000030001_3010103000000010101_x"

		val aMap = configMap("a" to 1, "b" to 2, "c" to 3)
		val a = SimpleConfigObject(fakeOrigin(), aMap)
		val b = checkSerializable(expectedSerialization, a.toConfig())
		assertEquals(1, b.getInt("a"))
		// check that deserialized Config and ConfigObject refer to each other
		assertTrue(b.root().toConfig() == b)
	}

	/**
	 * Reproduces the issue <a href=https://github.com/lightbend/config/issues/461>#461</a>.
	 * <p>
	 * We use a custom de-/serializer that encodes String objects in a JDK-incompatible way. Encoding used here
	 * is rather simplistic: a long indicating the length in bytes (JDK uses a variable length integer) followed
	 * by the string's bytes. Running this test with the original SerializedConfigValue.readExternal()
	 * implementation results in an EOFException thrown during deserialization.
	 */
	@Test
	fun configConfigCustomSerializable() {
		val aMap = configMap("a" to 1, "b" to 2, "c" to 3)
		val expected = SimpleConfigObject(fakeOrigin(), aMap).toConfig()
		val actual = checkSerializableWithCustomSerializer(expected)

		assertEquals(expected, actual)
	}

	@Test
	fun configListEquality() {
		val aScalaSeq = listOf(1, 2, 3).map {
			intValue(it)
		}
		val aList = SimpleConfigList(fakeOrigin(), aScalaSeq)
		val sameAsAList = SimpleConfigList(fakeOrigin(), aScalaSeq)
		val bScalaSeq = listOf(4, 5, 6).map {
			intValue(it)
		}
		val bList = SimpleConfigList(fakeOrigin(), bScalaSeq)

		checkEqualObjects(aList, aList)
		checkEqualObjects(aList, sameAsAList)
		checkNotEqualObjects(aList, bList)
	}

	@Test
	fun configListSerializable() {
		val expectedSerialization = "" +
				"ACED0005_s_r00_._c_o_m_._t_y_p_e_s_a_f_e_._c_o_n_f_i_g_._i_m_p_l_._S_e_r_i_a_l_i" +
				"_z_e_d_C_o_n_f_i_g_V_a_l_u_e00000000000000010C0000_x_p_w_q02000000_e050000001906" +
				"0000000D000B_f_a_k_e_ _o_r_i_g_i_n0900000001000104000000_A0600000003050000000101" +
				"040000000802000000010001_101050000000101040000000802000000020001_201050000000101" +
				"040000000802000000030001_3010103000000010001_x"
		val aScalaSeq = listOf(1, 2, 3).map {
			intValue(it)
		}
		val aList = SimpleConfigList(fakeOrigin(), aScalaSeq)
		val bList = checkSerializable(expectedSerialization, aList)
		assertEquals(1, bList[0].unwrapped())
	}

	@Test
	fun configReferenceEquality() {
		val a = subst("foo")
		val sameAsA = subst("foo")
		val b = subst("bar")
		val c = subst("foo", optional = true)

		checkEqualObjects(a, a)
		checkEqualObjects(a, sameAsA)
		checkNotEqualObjects(a, b)
		checkNotEqualObjects(a, c)

	}

	@Test
	fun configReferenceNotSerializable() {
		val a = subst("foo")
		checkNotSerializable(a)
	}

	@Test
	fun configConcatenationEquality() {
		val a = substInString("foo")
		val sameAsA = substInString("foo")
		val b = substInString("bar")
		val c = substInString("foo", optional = true)

		checkEqualObjects(a, a)
		checkEqualObjects(a, sameAsA)
		checkNotEqualObjects(a, b)
		checkNotEqualObjects(a, c)
	}

	@Test
	fun configConcatenationNotSerializable() {
		val a = substInString("foo")
		checkNotSerializable(a)
	}

	@Test
	fun configDelayedMergeEquality() {
		val s1 = subst("foo")
		val s2 = subst("bar")
		val a = ConfigDelayedMerge(fakeOrigin(), listOf<AbstractConfigValue>(s1, s2))
		val sameAsA = ConfigDelayedMerge(fakeOrigin(), listOf<AbstractConfigValue>(s1, s2))
		val b = ConfigDelayedMerge(fakeOrigin(), listOf<AbstractConfigValue>(s2, s1))

		checkEqualObjects(a, a)
		checkEqualObjects(a, sameAsA)
		checkNotEqualObjects(a, b)
	}

	@Test
	fun configDelayedMergeNotSerializable() {
		val s1 = subst("foo")
		val s2 = subst("bar")
		val a = ConfigDelayedMerge(fakeOrigin(), listOf<AbstractConfigValue>(s1, s2))
		checkNotSerializable(a)
	}

	@Test
	fun configDelayedMergeObjectEquality() {
		val empty = SimpleConfigObject.empty()
		val s1 = subst("foo")
		val s2 = subst("bar")
		val a = ConfigDelayedMergeObject(fakeOrigin(), listOf<AbstractConfigValue>(empty, s1, s2))
		val sameAsA = ConfigDelayedMergeObject(fakeOrigin(), listOf<AbstractConfigValue>(empty, s1, s2))
		val b = ConfigDelayedMergeObject(fakeOrigin(), listOf<AbstractConfigValue>(empty, s2, s1))

		checkEqualObjects(a, a)
		checkEqualObjects(a, sameAsA)
		checkNotEqualObjects(a, b)
	}

	@Test
	fun configDelayedMergeObjectNotSerializable() {
		val empty = SimpleConfigObject.empty()
		val s1 = subst("foo")
		val s2 = subst("bar")
		val a = ConfigDelayedMergeObject(fakeOrigin(), listOf<AbstractConfigValue>(empty, s1, s2))
		checkNotSerializable(a)
	}

	@Test
	fun valuesToString() {
		// just check that these don't throw, the exact output
		// isn't super important since it's just for debugging
		intValue(10).toString()
		longValue(11).toString()
		doubleValue(3.14).toString()
		stringValue("hi").toString()
		nullValue().toString()
		boolValue(true).toString()
		val emptyObj = SimpleConfigObject.empty()
		emptyObj.toString()
		SimpleConfigList(fakeOrigin(), emptyList<AbstractConfigValue>()).toString()
		subst("a").toString()
		substInString("b").toString()
		val dm = ConfigDelayedMerge(fakeOrigin(), listOf<AbstractConfigValue>(subst("a"), subst("b")))
		dm.toString()
		val dmo = ConfigDelayedMergeObject(fakeOrigin(), listOf<AbstractConfigValue>(emptyObj, subst("a"), subst("b")))
		dmo.toString()

		fakeOrigin().toString()
	}

	@Test
	fun configObjectUnwraps() {
		val m = SimpleConfigObject(
			fakeOrigin(),
			configMap("a" to 1, "b" to 2, "c" to 3)
		)
		assertEquals(mapOf("a" to 1, "b" to 2, "c" to 3), m.unwrapped())
	}

	@Test
	fun configObjectImplementsMap() {
		val m: ConfigObject = SimpleConfigObject(
			fakeOrigin(),
			configMap("a" to 1, "b" to 2, "c" to 3)
		)

		assertEquals(intValue(1), m["a"])
		assertEquals(intValue(2), m["b"])
		assertEquals(intValue(3), m["c"])
		assertNull(m["d"])


		assertTrue(m.containsKey("a"))
		assertFalse(m.containsKey("z"))

		assertTrue(m.containsValue(intValue(1)))
		assertFalse(m.containsValue(intValue(10)))


		assertFalse(m.isEmpty())

		assertEquals(3, m.size)

		val values = setOf(intValue(1), intValue(2), intValue(3))
		assertEquals(values, m.values.toSet())
		assertEquals(values, m.entries.map {
			it.value
		}.toSet())

		val keys = setOf("a", "b", "c")
		assertEquals(keys, m.keys.toSet())
		assertEquals(keys, (m.entries.map {
			it.key
		}).toSet())

		unsupported {
			m.clear()
		}
		unsupported {
			m["hello"] = intValue(42)
		}
		unsupported {
			m.putAll(emptyMap<String, AbstractConfigValue>())
		}
		unsupported {
			m.remove("a")
		}
	}

	@Test
	fun configListImplementsList() {
		val kotlinList = listOf<AbstractConfigValue>(stringValue("a"), stringValue("b"), stringValue("c"))
		val l: ConfigList = SimpleConfigList(
			fakeOrigin(),
			kotlinList
		)

		assertEquals(kotlinList.first(), l[0])
		assertEquals(kotlinList[1], l[1])
		assertEquals(kotlinList[2], l[2])

		assertTrue(l.contains(stringValue("a")))

		assertTrue(l.containsAll(listOf<AbstractConfigValue>(stringValue("b"))))
		assertFalse(l.containsAll(listOf<AbstractConfigValue>(stringValue("d"))))

		assertEquals(1, l.indexOf(kotlinList[1]))

		assertFalse(l.isEmpty())

		assertEquals(kotlinList, l)

		unsupported {
			l.iterator().remove()
		}

		assertEquals(1, l.lastIndexOf(kotlinList[1]))

		val li = l.listIterator()
		var i = 0
		while (li.hasNext()) {
			assertEquals(i > 0, li.hasPrevious())
			assertEquals(i, li.nextIndex())
			assertEquals(i - 1, li.previousIndex())

			unsupported {
				li.remove()
			}
			unsupported {
				li.add(intValue(3))
			}
			unsupported {
				li.set(stringValue("foo"))
			}

			val v = li.next()
			assertEquals(l[i], v)

			if (li.hasPrevious()) {
				// go backward
				assertEquals(kotlinList[i], li.previous())
				// go back forward
				li.next()
			}

			i += 1
		}

		l.listIterator(1) // doesn't throw!

		assertEquals(3, l.size)

		assertEquals(kotlinList.drop(1), l.subList(1, l.size))

		unsupported {
			l.add(intValue(3))
		}
		unsupported {
			l.add(1, intValue(4))
		}
		unsupported {
			l.addAll(listOf<ConfigValue>())
		}
		unsupported {
			l.addAll(1, listOf<ConfigValue>())
		}
		unsupported {
			l.clear()
		}
		unsupported {
			l.remove(intValue(2))
		}
		unsupported {
			l.removeAt(1)
		}
		unsupported {
			l.removeAll(listOf<ConfigValue>(intValue(1)))
		}
		unsupported {
			l.retainAll(listOf<ConfigValue>(intValue(1)))
		}
		unsupported {
			l[0] = intValue(42)
		}
	}

	@Test
	fun notResolvedThrown() {
		// ConfigSubstitution
		unresolved {
			subst("foo").valueType()
		}
		unresolved {
			subst("foo").unwrapped()
		}

		// ConfigDelayedMerge
		val dm = ConfigDelayedMerge(fakeOrigin(), listOf<AbstractConfigValue>(subst("a"), subst("b")))
		unresolved {
			dm.valueType()
		}
		unresolved {
			dm.unwrapped()
		}

		// ConfigDelayedMergeObject
		val emptyObj = SimpleConfigObject.empty()
		val dmo = ConfigDelayedMergeObject(
			fakeOrigin(),
			listOf<AbstractConfigValue>(emptyObj, subst("a"), subst("b"))
		)
		assertEquals(ConfigValueType.OBJECT, dmo.valueType())
		unresolved {
			dmo.unwrapped()
		}
		unresolved {
			dmo["foo"]
		}
		unresolved {
			dmo.containsKey(null)
		}
		unresolved {
			dmo.containsValue(null)
		}
		unresolved {
			dmo.entries
		}
		unresolved {
			dmo.isEmpty()
		}
		unresolved {
			dmo.keys
		}
		unresolved {
			dmo.size
		}
		unresolved {
			dmo.values
		}
		unresolved {
			dmo.toConfig().getInt("foo")
		}
	}

	@Test
	fun roundTripNumbersThroughString() {
		// formats rounded off with E notation
		val a = "132454454354353245.3254652656454808909932874873298473298472"
		// formats as 100000.0
		val b = "1e6"
		// formats as 5.0E-5
		val c = "0.00005"
		// formats as 1E100 (capital E)
		val d = "1e100"

		val obj = parseConfig("{ a : $a, b : $b, c : $c, d : $d}")
		assertEquals(listOf(a, b, c, d),
			listOf("a", "b", "c", "d").map {
				obj.getString(it)
			})

		// make sure it still works if we're doing concatenation
		val obj2 = parseConfig("{ a : xx $a yy, b : xx $b yy, c : xx $c yy, d : xx $d yy}")
		assertEquals(listOf(a, b, c, d).map {
			"xx $it yy"
		},
			listOf("a", "b", "c", "d").map {
				obj2.getString(it)
			})
	}

	@Test
	fun mergeOriginsWorks() {
		fun o(desc: String, empty: Boolean): SimpleConfigObject {
			val values = if (!empty) mapOf("hello" to intValue(37)) else emptyMap()
			return SimpleConfigObject(SimpleConfigOrigin.newSimple(desc), values)
		}

		fun m(vararg values: AbstractConfigObject): String? {
			return AbstractConfigObject.mergeOrigins(*values).description()
		}

		// simplest case
		assertEquals("merge of a,b", m(o("a", empty = false), o("b", empty = false)))
		// combine duplicate "merge of"
		assertEquals("merge of a,x,y", m(o("a", empty = false), o("merge of x,y", empty = false)))
		assertEquals("merge of a,b,x,y", m(o("merge of a,b", empty = false), o("merge of x,y", empty = false)))
		// ignore empty objects
		assertEquals("a", m(o("foo", empty = true), o("a", empty = false)))
		// unless they are all empty, pick the first one
		assertEquals("foo", m(o("foo", empty = true), o("a", empty = true)))
		// merge just one
		assertEquals("foo", m(o("foo", empty = false)))
		// merge three
		assertEquals("merge of a,b,c", m(o("a", empty = false), o("b", empty = false), o("c", empty = false)))
	}

	@Test
	fun hasPathWorks() {
		val empty = parseConfig("{}")

		assertFalse(empty.hasPath("foo"))

		val obj = parseConfig("a=null, b.c.d=11, foo=bar")

		// returns true for the non-null values
		assertTrue(obj.hasPath("foo"))
		assertTrue(obj.hasPath("b.c.d"))
		assertTrue(obj.hasPath("b.c"))
		assertTrue(obj.hasPath("b"))

		// hasPath() is false for null values but containsKey is true
		assertEquals(nullValue(), obj.root()["a"])
		assertTrue(obj.root().containsKey("a"))
		assertFalse(obj.hasPath("a"))

		// false for totally absent values
		assertFalse(obj.root().containsKey("notinhere"))
		assertFalse(obj.hasPath("notinhere"))

		// throws proper exceptions
		assertThrows(ConfigException.BadPath::class.java) {
			empty.hasPath("a.")
		}

		assertThrows(ConfigException.BadPath::class.java) {
			empty.hasPath("..")
		}

	}

	@Test
	fun newNumberWorks() {
		fun nL(v: Long) = ConfigNumber.newNumber(fakeOrigin(), v, null)

		fun nD(v: Double) = ConfigNumber.newNumber(fakeOrigin(), v, null)

		// the general idea is that the destination type should depend
		// only on the actual numeric value, not on the type of the source
		// value.
		assertEquals(3.14, nD(3.14).unwrapped())
		assertEquals(1, nL(1).unwrapped())
		assertEquals(1, nD(1.0).unwrapped())
		assertEquals(Int.MAX_VALUE + 1L, nL(Int.MAX_VALUE + 1L).unwrapped())
		assertEquals(Int.MIN_VALUE - 1L, nL(Int.MIN_VALUE - 1L).unwrapped())
		assertEquals(Int.MAX_VALUE + 1L, nD(Int.MAX_VALUE + 1.0).unwrapped())
		assertEquals(Int.MIN_VALUE - 1L, nD(Int.MIN_VALUE - 1.0).unwrapped())
	}

	@Test
	fun automaticBooleanConversions() {
		val trues = parseObject("{ a=true, b=yes, c=on }").toConfig()
		assertEquals(true, trues.getBoolean("a"))
		assertEquals(true, trues.getBoolean("b"))
		assertEquals(true, trues.getBoolean("c"))

		val falses = parseObject("{ a=false, b=no, c=off }").toConfig()
		assertEquals(false, falses.getBoolean("a"))
		assertEquals(false, falses.getBoolean("b"))
		assertEquals(false, falses.getBoolean("c"))
	}

	@Test
	fun configOriginFileAndLine() {
		val hasFilename = SimpleConfigOrigin.newFile("foo")
		val noFilename = SimpleConfigOrigin.newSimple("bar")
		val filenameWithLine = hasFilename.withLineNumber(3)
		val noFilenameWithLine = noFilename.withLineNumber(4)

		assertEquals("foo", hasFilename.filename())
		assertEquals("foo", filenameWithLine.filename())
		assertNull(noFilename.filename())
		assertNull(noFilenameWithLine.filename())

		assertEquals("foo", hasFilename.description())
		assertEquals("bar", noFilename.description())

		assertEquals(-1, hasFilename.lineNumber())
		assertEquals(-1, noFilename.lineNumber())

		assertEquals("foo: 3", filenameWithLine.description())
		assertEquals("bar: 4", noFilenameWithLine.description())

		assertEquals(3, filenameWithLine.lineNumber())
		assertEquals(4, noFilenameWithLine.lineNumber())

		// the filename is made absolute when converting to url
		assertTrue(hasFilename.uri().toASCIIString().contains("foo"))
		assertNull(noFilename.uri())
		val rootFile = SimpleConfigOrigin.newFile("/baz")
		val rootFileURL = if (isWindows()) "file:/${userDrive()}/baz" else "file:/baz"
		assertEquals(rootFileURL, rootFile.uri().toASCIIString())

		val urlOrigin = SimpleConfigOrigin.newURL(URI("file:/foo"))
		assertEquals("/foo", urlOrigin.filename())
		assertEquals("file:/foo", urlOrigin.uri().toASCIIString())
	}

	@Test
	fun withOnly() {
		val obj = parseObject("{ a=1, b=2, c.d.y=3, e.f.g=4, c.d.z=5 }")
		assertEquals(parseObject("{ a=1 }"), obj.withOnlyKey("a"), "keep only a")
		assertEquals(parseObject("{ e.f.g=4 }"), obj.withOnlyKey("e"), "keep only e")
		assertEquals(parseObject("{ c.d.y=3, c.d.z=5 }"), obj.toConfig().withOnlyPath("c.d").root(), "keep only c.d")
		assertEquals(parseObject("{ c.d.z=5 }"), obj.toConfig().withOnlyPath("c.d.z").root(), "keep only c.d.z")
		assertEquals(parseObject("{ }"), obj.withOnlyKey("nope"), "keep nonexistent key")
		assertEquals(parseObject("{ }"), obj.toConfig().withOnlyPath("q.w.e.r.t.y").root(), "keep nonexistent path")
		assertEquals(
			parseObject("{ }"),
			obj.toConfig().withOnlyPath("a.nonexistent").root(),
			"keep only nonexistent underneath non-object"
		)
		assertEquals(
			parseObject("{ }"),
			obj.toConfig().withOnlyPath("c.d.z.nonexistent").root(),
			"keep only nonexistent underneath nested non-object"
		)
	}

	@Test
	fun withOnlyInvolvingUnresolved() {
		val obj = parseObject("{ a {}, a=\${x}, b=\${y}, b=\${z}, x={asf:1}, y=2, z=3 }")
		assertEquals(
			parseObject("{ a={asf:1} }"),
			obj.toConfig().resolve().withOnlyPath("a.asf").root(),
			"keep only a.asf"
		)

		assertThrows(ConfigException.UnresolvedSubstitution::class.java) {
			obj.withOnlyKey("a").toConfig().resolve()
		}

		assertThrows(ConfigException.UnresolvedSubstitution::class.java) {
			obj.withOnlyKey("b").toConfig().resolve()
		}

		assertEquals(ResolveStatus.UNRESOLVED, obj.resolveStatus())
		assertEquals(ResolveStatus.RESOLVED, obj.withOnlyKey("z").resolveStatus())
	}

	@Test
	fun without() {
		val obj = parseObject("{ a=1, b=2, c.d.y=3, e.f.g=4, c.d.z=5 }")
		assertEquals(parseObject("{ b=2, c.d.y=3, e.f.g=4, c.d.z=5 }"), obj.withoutKey("a"), "without a")
		assertEquals(parseObject("{ a=1, b=2, e.f.g=4 }"), obj.withoutKey("c"), "without c")
		assertEquals(
			parseObject("{ a=1, b=2, e.f.g=4, c={} }"),
			obj.toConfig().withoutPath("c.d").root(),
			"without c.d"
		)
		assertEquals(
			parseObject("{ a=1, b=2, c.d.y=3, e.f.g=4 }"),
			obj.toConfig().withoutPath("c.d.z").root(),
			"without c.d.z"
		)
		assertEquals(
			parseObject("{ a=1, b=2, c.d.y=3, e.f.g=4, c.d.z=5 }"),
			obj.withoutKey("nonexistent"),
			"without nonexistent key"
		)
		assertEquals(
			parseObject("{ a=1, b=2, c.d.y=3, e.f.g=4, c.d.z=5 }"),
			obj.toConfig().withoutPath("q.w.e.r.t.y").root(),
			"without nonexistent path"
		)
		assertEquals(
			parseObject("{ a=1, b=2, c.d.y=3, e.f.g=4, c.d.z=5 }"),
			obj.toConfig().withoutPath("a.foo").root(),
			"without nonexistent path with existing prefix"
		)
	}

	@Test
	fun withoutInvolvingUnresolved() {
		val obj = parseObject("{ a {}, a=\${x}, b=\${y}, b=\${z}, x={asf:1}, y=2, z=3 }")
		assertEquals(
			parseObject("{ a={}, b=3, x={asf:1}, y=2, z=3 }"),
			obj.toConfig().resolve().withoutPath("a.asf").root(),
			"without a.asf"
		)

		assertThrows(ConfigException.UnresolvedSubstitution::class.java) {
			obj.withoutKey("x").toConfig().resolve()
		}

		assertThrows(ConfigException.UnresolvedSubstitution::class.java) {
			obj.withoutKey("z").toConfig().resolve()
		}

		assertEquals(ResolveStatus.UNRESOLVED, obj.resolveStatus())
		assertEquals(ResolveStatus.UNRESOLVED, obj.withoutKey("a").resolveStatus())
		assertEquals(ResolveStatus.RESOLVED, obj.withoutKey("a").withoutKey("b").resolveStatus())
	}

	@Test
	fun atPathWorksOneElement() {
		val v = ConfigValueFactory.fromAnyRef(42)
		val config = v.atPath("a")
		assertEquals(parseConfig("a=42"), config)
		assertTrue(config.getValue("a") == v)
		assertTrue(config.origin().description().contains("atPath"))
	}

	@Test
	fun atPathWorksTwoElements() {
		val v = ConfigValueFactory.fromAnyRef(42)
		val config = v.atPath("a.b")
		assertEquals(parseConfig("a.b=42"), config)
		assertTrue(config.getValue("a.b") == v)
		assertTrue(config.origin().description().contains("atPath"))
	}

	@Test
	fun atPathWorksFourElements() {
		val v = ConfigValueFactory.fromAnyRef(42)
		val config = v.atPath("a.b.c.d")
		assertEquals(parseConfig("a.b.c.d=42"), config)
		assertTrue(config.getValue("a.b.c.d") == v)
		assertTrue(config.origin().description().contains("atPath"))
	}

	@Test
	fun atKeyWorks() {
		val v = ConfigValueFactory.fromAnyRef(42)
		val config = v.atKey("a")
		assertEquals(parseConfig("a=42"), config)
		assertTrue(config.getValue("a") == v)
		assertTrue(config.origin().description().contains("atKey"))
	}

	@Test
	fun withValueDepth1FromEmpty() {
		val v = ConfigValueFactory.fromAnyRef(42)
		val config = ConfigFactory.empty().withValue("a", v)
		assertEquals(parseConfig("a=42"), config)
		assertTrue(config.getValue("a") == v)
	}

	@Test
	fun withValueDepth2FromEmpty() {
		val v = ConfigValueFactory.fromAnyRef(42)
		val config = ConfigFactory.empty().withValue("a.b", v)
		assertEquals(parseConfig("a.b=42"), config)
		assertTrue(config.getValue("a.b") == v)
	}

	@Test
	fun withValueDepth3FromEmpty() {
		val v = ConfigValueFactory.fromAnyRef(42)
		val config = ConfigFactory.empty().withValue("a.b.c", v)
		assertEquals(parseConfig("a.b.c=42"), config)
		assertTrue(config.getValue("a.b.c") == v)
	}

	@Test
	fun withValueDepth1OverwritesExisting() {
		val v = ConfigValueFactory.fromAnyRef(47)
		val old = v.atPath("a")
		val config = old.withValue("a", ConfigValueFactory.fromAnyRef(42))
		assertEquals(parseConfig("a=42"), config)
		assertEquals(42, config.getInt("a"))
	}

	@Test
	fun withValueDepth2OverwritesExisting() {
		val v = ConfigValueFactory.fromAnyRef(47)
		val old = v.atPath("a.b")
		val config = old.withValue("a.b", ConfigValueFactory.fromAnyRef(42))
		assertEquals(parseConfig("a.b=42"), config)
		assertEquals(42, config.getInt("a.b"))
	}

	@Test
	fun withValueInsideExistingObject() {
		val v = ConfigValueFactory.fromAnyRef(47)
		val old = v.atPath("a.c")
		val config = old.withValue("a.b", ConfigValueFactory.fromAnyRef(42))
		assertEquals(parseConfig("a.b=42,a.c=47"), config)
		assertEquals(42, config.getInt("a.b"))
		assertEquals(47, config.getInt("a.c"))
	}

	@Test
	fun withValueBuildComplexConfig() {
		val v1 = ConfigValueFactory.fromAnyRef(1)
		val v2 = ConfigValueFactory.fromAnyRef(2)
		val v3 = ConfigValueFactory.fromAnyRef(3)
		val v4 = ConfigValueFactory.fromAnyRef(4)
		val config = ConfigFactory.empty()
			.withValue("a", v1)
			.withValue("b.c", v2)
			.withValue("b.d", v3)
			.withValue("x.y.z", v4)
		assertEquals(parseConfig("a=1,b.c=2,b.d=3,x.y.z=4"), config)
	}

	@Test
	fun configOriginsInSerialization() {

		val bases = listOf(
			SimpleConfigOrigin.newSimple("foo"),
			SimpleConfigOrigin.newFile("/tmp/blahblah"),
			SimpleConfigOrigin.newURL(URI("http://example.com")),
			SimpleConfigOrigin.newResource("myresource"),
			SimpleConfigOrigin.newResource("myresource", URI("file://foo/bar"))
		)
		val combos = bases.flatMap { base ->
			listOf(
				base to base.withComments(listOf("this is a comment", "another one")),
				base to base.withComments(null),
				base to base.withLineNumber(41),
				base to SimpleConfigOrigin.mergeOrigins(base.withLineNumber(10), base.withLineNumber(20))
			)
		} +
				bases.windowed(2).map { seq -> seq.first() to seq.drop(1).first() } +
				bases.windowed(3).map { seq -> seq.first() to seq.drop(2).first() } +
				bases.windowed(4).map { seq -> seq.first() to seq.drop(3).first() }
		val withFlipped = combos + combos.map { it.second to it.first }
		val withDuplicate = withFlipped + withFlipped.map { p -> p.first to p.first }
		val values = withDuplicate.flatMap { combo ->
			listOf(
				// second inside first
				SimpleConfigList(
					combo.first,
					listOf<AbstractConfigValue>(ConfigInt(combo.second, 42, "42"))
				),
				// triple-nested means we have to null then un-null then null, which is a tricky case
				// in the origin-serialization code.
				SimpleConfigList(
					combo.first,
					listOf<AbstractConfigValue>(
						SimpleConfigList(
							combo.second,
							listOf<AbstractConfigValue>(ConfigInt(combo.first, 42, "42"))
						)
					)
				)
			)
		}

		fun top(v: SimpleConfigList) = v.origin()

		fun middle(v: SimpleConfigList) = v[0].origin()

		fun bottom(v: SimpleConfigList) = when (val list = v[0]) {
			is ConfigList -> (list[0].origin())
			else -> null
		}

		//System.err.println("values=\n  " + values.map(v -> top(v).description + ", " + middle(v).description + ", " + bottom(v).map(_.description)).mkString("\n  "))
		for (v in values) {
			val deserialized = checkSerializable(v)
			// double-check that checkSerializable verified the origins
			assertEquals(top(v), top(deserialized))
			assertEquals(middle(v), middle(deserialized))
			assertEquals(bottom(v), bottom(deserialized))
		}
	}

	@Test
	fun renderWithNewlinesInDescription() {
		val v = ConfigValueFactory.fromAnyRef(89, "this is a description\nwith some\nnewlines")
		val list = SimpleConfigList(
			SimpleConfigOrigin.newSimple("\n5\n6\n7\n"),
			listOf(v as AbstractConfigValue)
		)
		val conf = ConfigFactory.empty().withValue("bar", list)
		val rendered = conf.root().render()

		fun assertHas(s: String): Unit =
			assertTrue(rendered.contains(s), "has ${s.replace("\n", "\\n")} in it")

		assertHas("is a description\n")
		assertHas("with some\n")
		assertHas("newlines\n")
		assertHas("#\n")
		assertHas("5\n")
		assertHas("6\n")
		assertHas("7\n")
		val parsed = ConfigFactory.parseString(rendered)

		assertEquals(conf, parsed)
	}

	@Test
	fun renderSorting(): Unit {
		val config =
			parseConfig("""0=a,1=b,2=c,999999999999999999999999999999999999999999999=0,3=d,10=e,20a=f,20=g,30=h""")
		val rendered = config.root().render(ConfigRenderOptions.concise())
		assertEquals(
			"""{"0":"a","1":"b","2":"c","3":"d","10":"e","20":"g","30":"h","999999999999999999999999999999999999999999999":0,"20a":"f"}""",
			rendered
		)
	}

	private fun configMap(vararg pairs: Pair<String, Int>): Map<String, AbstractConfigValue> {
		return pairs.associate { it.first to intValue(it.second) }
	}

	private fun unsupported(body: () -> Unit) {
		assertThrows(UnsupportedOperationException::class.java, body)
	}

	private fun unresolved(body: () -> Unit) {
		assertThrows(ConfigException.NotResolved::class.java, body)
	}
}
