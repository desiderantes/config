/**
 * Copyright (C) 2011 Typesafe Inc. <http://typesafe.com>
 */

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory


object Util {

	tailrec fun timeHelper(iterations: Int, retried: Boolean, body: () -> Unit): Double {
		// warm up
		for (i in 1..20.coerceAtLeast(iterations / 10)) {
			body()
		}

		val start = System.nanoTime()
		for (i in 1..iterations) {
			body()
		}
		val end = System.nanoTime()

		val elapsed = end - start

		val nanosInMillisecond = 1000000L

		return if (elapsed < (1000 * nanosInMillisecond)) {
			System.err.println("Total time for $iterations was less than a second; trying with more iterations")
			timeHelper(iterations * 10, true, body)
		} else {
			if (retried)
				println("with $iterations we got a long enough sample (${elapsed.toDouble() / nanosInMillisecond}ms)")
			(elapsed.toDouble() / iterations) / nanosInMillisecond
		}
	}

	fun time(iterations: Int, body: () -> Unit): Double {
		return timeHelper(iterations, retried = false, body)
	}

	fun loop(args: Array<String>, body: () -> Unit) {
		if (args.contains("-loop")) {
			println("looping; ctrl+C to escape")
			while (true) {
				body()
			}
		}
	}
}

object FileLoad {

	fun task() {
		val conf = ConfigFactory.load("test04")
		if ("2.0-SNAPSHOT" != conf.getString("akka.version")) {
			throw Exception("broken file load")
		}
	}

	fun main(args: Array<String>) {
		val ms = Util.time(4000) {
			task()
		}


		println("file load: " + ms + "ms")

		Util.loop(args) {
			task()
		}
	}
}

object Resolve {

	fun task(conf: Config) {
		conf.resolve()
		if (conf.getInt("103_a") != 103) {
			throw Exception("broken file load")
		}
	}

	fun main(args: Array<String>) {
		val conf: Config = ConfigFactory.load("test02")
		val ms = Util.time(3000000) {
			task(conf)
		}
		println("resolve: " + ms + "ms")

		Util.loop(args) {
			task(conf)
		}
	}
}

object GetExistingPath {
	fun task(conf: Config) {
		if (conf.getInt("aaaaa.bbbbb.ccccc.d") != 42) {
			throw Exception("broken get")
		}
	}

	fun main(args: Array<String>) {

		val conf = ConfigFactory.parseString("aaaaa.bbbbb.ccccc.d=42").resolve()
		val ms = Util.time(2000000) {
			task(conf)
		}


		println("GetExistingPath: " + ms + "ms")

		Util.loop(args) {
			task(conf)
		}
	}
}

object GetSeveralExistingPaths {
	fun task(conf: Config) {
		if (conf.getInt("aaaaa.bbbbb.ccccc.d") != 42 ||
			conf.getInt("aaaaa.qqqqq.rrrrr") != 43 ||
			conf.getInt("xxxxx.yyyyy.zzzzz") != 44
		) {
			throw Exception("broken get")
		}
	}

	fun main(args: Array<String>) {

		val conf =
			ConfigFactory.parseString("aaaaa { bbbbb.ccccc.d=42, qqqqq.rrrrr = 43 }, xxxxx.yyyyy.zzzzz = 44 ").resolve()
		val ms = Util.time(5000000) {
			task(conf)
		}


		println("GetSeveralExistingPaths: " + ms + "ms")

		Util.loop(args) {
			task(conf)
		}
	}
}

object HasPathOnMissing {


	fun task(conf: Config) {
		if (conf.hasPath("aaaaa.bbbbb.ccccc.e")) {
			throw Exception("we shouldn't have this path")
		}
	}

	fun main(args: Array<String>) {
		val conf = ConfigFactory.parseString("aaaaa.bbbbb.ccccc.d=42,x=10, y=11, z=12").resolve()
		val ms = Util.time(20000000) {
			task(conf)
		}
		println("HasPathOnMissing: " + ms + "ms")

		Util.loop(args) {
			task(conf)
		}
	}
}

object CatchExceptionOnMissing {


	fun anotherStackFrame(remaining: Int, body: () -> Unit): Int {
		return if (remaining == 0) {
			body()
			123
		} else {
			42 + anotherStackFrame(remaining - 1, body)
		}
	}

	fun task(conf: Config) {
		try {
			conf.getInt("aaaaa.bbbbb.ccccc.e")
		} catch (_: ConfigException.Missing) {

		}
	}

	fun main(args: Array<String>) {
		val conf = ConfigFactory.parseString("aaaaa.bbbbb.ccccc.d=42,x=10, y=11, z=12").resolve()

		anotherStackFrame(40) {
			val ms = Util.time(300000) {
				task(conf)
			}
			println("CatchExceptionOnMissing: " + ms + "ms")

			Util.loop(args) {
				task(conf)
			}
		}
	}
}