import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.temporal.ChronoUnit

class ApiExamples {

	@Test
	fun readSomeConfig() {
		val conf = ConfigFactory.load("test01")

		// you don't have to write the types explicitly of course,
		// just doing that to show what they are.
		val a: Int = conf.getInt("ints.fortyTwo")
		val child: Config = conf.getConfig("ints")
		val b: Int = child.getInt("fortyTwo")
		val ms: Long = conf.getDuration("durations.halfSecond", ChronoUnit.MILLIS)

		// a Config has an associated tree of values, with a ConfigObject
		// at the root. The ConfigObject implements java.util.Map
		val obj: ConfigObject = conf.root()

		// this is how you do conf.getInt "manually" on the value tree, if you
		// were so inclined. (This is not a good approach vs. conf.getInt() above,
		// just showing how ConfigObject stores a tree of values.)
		val c: Int = (obj["ints"] as ConfigObject)["fortyTwo"]?.unwrapped()
			?.let { it as Int } ?: 0


		val d: Int = when (val x = conf.getAnyRef("ints.fortyTwo")) {
			is Int -> x
			is Long -> x.toInt()
			else -> throw IllegalArgumentException()
		}

		assertEquals(42, d)
		assertEquals(42, c)

	}
}