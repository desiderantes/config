package com.typesafe.config.impl

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import com.typesafe.config.ConfigSyntax
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.net.URI

class HttpTest : TestUtils() {

	private fun uri(path: String): URI = URI("${baseUrl()}/$path")

	@Test
	fun parseEmpty() {
		foreachSyntaxOptions { options ->
			val conf = ConfigFactory.parseURI(uri("empty"), options)
			assertTrue(conf.root().isEmpty(), "empty conf was parsed")
		}
	}

	@Test
	fun parseFooIs42() {
		foreachSyntaxOptions { options ->
			val conf = ConfigFactory.parseURI(uri("foo"), options)
			assertEquals(42, conf.getInt("foo"))
		}
	}

	@Test
	fun notFoundThrowsIO() {
		val e = assertThrows(ConfigException.IO::class.java) {
			ConfigFactory.parseURI(uri("notfound"), ConfigParseOptions.defaults().setAllowMissing(false))
		}
		assertTrue(e.message!!.contains("/notfound"), "expected different exception for notfound, got $e")
	}

	@Test
	fun internalErrorThrowsBroken() {
		val e = assertThrows(ConfigException.BugOrBroken::class.java) {
			ConfigFactory.parseURI(uri("error"), ConfigParseOptions.defaults().setAllowMissing(false))
		}
		assertTrue(e.message!!.contains("/error"), "expected different exception for error url, got $e")
	}

	@Test
	fun notFoundDoesNotThrowIfAllowingMissing() {
		val conf = ConfigFactory.parseURI(uri("notfound"), ConfigParseOptions.defaults().setAllowMissing(true))
		assertEquals(0, conf.root().size)
	}

	@Test
	fun internalErrorThrowsEvenIfAllowingMissing() {
		val e = assertThrows(ConfigException.BugOrBroken::class.java) {
			ConfigFactory.parseURI(uri("error"), ConfigParseOptions.defaults().setAllowMissing(true))
		}
		assertTrue(
			e.message!!.contains("/error"),
			"expected different exception for error url when allowing missing, got $e"
		)
	}

	@Test
	fun relativeInclude() {
		val conf = ConfigFactory.parseURI(uri("includes-a-friend"))
		assertEquals(42, conf.getInt("foo"))
		assertEquals(43, conf.getInt("bar"))
	}

	private fun foreachSyntax(body: (ConfigSyntax?) -> Unit) {
		for (syntax in ConfigSyntax.entries.plus(null)) {
			body(syntax)
		}
	}

	private fun foreachSyntaxOptions(body: (ConfigParseOptions) -> Unit): Unit = foreachSyntax { syntaxOption ->
		val options = syntaxOption?.let { syntax ->
			ConfigParseOptions.defaults().setSyntax(syntax)
		} ?: ConfigParseOptions.defaults()


		body(options)
	}

	companion object {

		private const val JSON_CONTENT_TYPE = "application/json"
		private const val PROPERTIES_CONTENT_TYPE = "text/x-java-properties"
		private const val HOCON_CONTENT_TYPE = "application/hocon"

		private var server: ToyHttp? = null

		fun port(): Int = server?.port ?: throw Exception("http server isn't running")

		fun hostname(): String = server?.baseUrl ?: throw Exception("http server isn't running")

		fun baseUrl() = URI.create("http://${hostname().drop(1)}").normalize().toString()

		@JvmStatic
		@BeforeAll
		fun startServer() {
			server = ToyHttp(::handleRequest)
		}

		@JvmStatic
		@AfterAll
		fun stopServer() {
			server?.stop()
		}

		private fun handleThreeTypes(
			request: ToyHttp.Request,
			json: String,
			props: String,
			hocon: String
		): ToyHttp.Response {
			return when (val accept = request.headers["accept"]) {
				JSON_CONTENT_TYPE, null -> ToyHttp.Response(200, JSON_CONTENT_TYPE, json)
				PROPERTIES_CONTENT_TYPE -> ToyHttp.Response(200, PROPERTIES_CONTENT_TYPE, props)
				HOCON_CONTENT_TYPE -> ToyHttp.Response(200, HOCON_CONTENT_TYPE, hocon)
				else -> ToyHttp.Response(500, "text/plain", "bad content type '$accept'")
			}
		}

		private fun handleRequest(request: ToyHttp.Request): ToyHttp.Response {
			return when (val path = request.path) {
				"/empty" -> handleThreeTypes(request, "{}", "", "")

				"/foo", "/foo.conf" -> handleThreeTypes(request, "{ \"foo\" : 42 }", "foo:42", "foo=42")

				"/notfound" -> ToyHttp.Response(404, "text/plain", "This is never found")

				"/error" -> ToyHttp.Response(500, "text/plain", "This is always an error")

				"/includes-a-friend" ->
					// currently, a suffix-less include like this will cause
					// us to search for foo.conf, foo.json, foo.properties, but
					// not load plain foo.
					ToyHttp.Response(
						200, HOCON_CONTENT_TYPE,
						"""
                  include "foo"
                  include "foo/bar"
                  """
					)

				"/foo/bar.conf" -> ToyHttp.Response(200, HOCON_CONTENT_TYPE, "{ bar = 43 }")

				else ->
					ToyHttp.Response(404, "text/plain", "Never heard of '$path'")
			}
		}
	}
}
