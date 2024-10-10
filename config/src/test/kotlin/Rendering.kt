package foo

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import kotlin.properties.Delegates


object RenderExample {
	var formatted by Delegates.notNull<Boolean>()
	var originComments by Delegates.notNull<Boolean>()
	var comments by Delegates.notNull<Boolean>()
	var hocon by Delegates.notNull<Boolean>()
	var hideEnvVariableValues by Delegates.notNull<Boolean>()
	lateinit var options: ConfigRenderOptions

	fun render(what: String) {
		val conf = ConfigFactory.defaultOverrides()
			.withFallback(ConfigFactory.parseResourcesAnySyntax(ConfigFactory::class.java, "/$what"))
			.withFallback(ConfigFactory.defaultReference())

		println("=== BEGIN UNRESOLVED toString() $what")
		print(conf.root().toString())
		println("=== END UNRESOLVED toString() $what")

		println("=== BEGIN UNRESOLVED $what")
		print(conf.root().render(options))
		println("=== END UNRESOLVED $what")

		println("=== BEGIN RESOLVED $what")
		print(conf.resolve().root().render(options))
		println("=== END RESOLVED $what")
	}

	fun main(args: Array<String>) {
		init(args)
		render("test01")
		render("test06")
		render("test05")
		render("test12")
	}

	fun init(args: Array<String>) {
		formatted = args.contains("--formatted")
		originComments = args.contains("--origin-comments")
		comments = args.contains("--comments")
		hocon = args.contains("--hocon")
		hideEnvVariableValues = args.contains("--hide-env-variable-values")
		options = ConfigRenderOptions.defaults()
			.setFormatted(formatted)
			.setOriginComments(originComments)
			.setComments(comments)
			.setJson(!hocon)
			.setShowEnvVariableValues(!hideEnvVariableValues)
	}
}

object RenderOptions {
	val conf: Config = ConfigFactory.parseString(
		"""
            foo=[1,2,3]
            # comment1
            bar {
                a = 42
                #comment2
                b = { c = "hello", d = true }
                #    comment3
                e = $\{something}
                f = {}
            }
"""
	)
	private val rendered by lazy {
		allBooleanLists(4).fold(0) { count, values ->
			val formatted = values.first()
			val originComments = values[1]
			val comments = values[2]
			val json = values[3]

			val options = ConfigRenderOptions.defaults()
				.setFormatted(formatted)
				.setOriginComments(originComments)
				.setComments(comments)
				.setJson(json)
			val renderSpec = options.toString().replace("ConfigRenderOptions", "")
			println("=== $count RENDER WITH $renderSpec===")
			print(conf.root().render(options))
			println("=== $count END RENDER WITH $renderSpec===")
			count + 1
		}
	}

	// ah, efficiency
	fun allBooleanLists(length: Int): Sequence<List<Boolean>> {

		return generateSequence(listOf<Boolean>()) {
			(listOf(true) + it) + (listOf(false) + it)
		}.take(length)
	}

	fun main(args: Array<String>) {
		println("Rendered $rendered option combinations")
	}
}