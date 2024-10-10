package com.typesafe.config.impl


import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

// terrible http server that's good enough for our test suite
class ToyHttp(val handler: (Request) -> Response) {


	private val serverSocket = ServerSocket()
	val port: Int
		get() = serverSocket.getLocalPort()
	val baseUrl: String
		get() = serverSocket.localSocketAddress.toString()
	private val thread = Thread { mainLoop() }

	init {

		serverSocket.bind(InetSocketAddress(DEFAULT_HOSTNAME, DEFAULT_PORT))
		thread.setDaemon(true)
		thread.setName("ToyHttp")
		thread.start()
	}

	fun stop() {
		try {
			serverSocket.close()
		} catch (_: IOException) {
		}
		try {
			thread.interrupt()
		} catch (_: InterruptedException) {
		}
		thread.join()
	}

	private tailrec fun mainLoop() {
		val done: Boolean = try {
			val socket = serverSocket.accept()
			try {
				handleRequest(socket)
			} catch (e: Exception) {
				when (e) {
					is EOFException, is IOException -> {
						System.err.println(
							"error handling http request: ${e::class.java.name}: ${e.message} ${
								e.stackTrace.joinToString("\n")
							}"
						)
					}
				}

			}
			false
		} catch (e: SocketException) {
			true
		}
		if (!done)
			mainLoop()
	}

	private fun handleRequest(socket: Socket) {
		val `in` = socket.getInputStream()
		val out = socket.getOutputStream()
		try {
			// HTTP requests look like this:
			// GET /path/here HTTP/1.0
			// SomeHeader: bar
			// OtherHeader: foo
			// \r\n
			val reader = BufferedReader(InputStreamReader(`in`))
			val path = parsePath(reader)
			val header = parseHeader(reader)
			//System.err.println(s"request path '$path' headers $header")
			val response = handler(Request(path, header))
			//System.err.println(s"response $response")
			sendResponse(out, response)
		} finally {
			`in`.close()
			out.close()
		}
	}

	private fun parseHeader(reader: BufferedReader): Map<String, String> {

		fun readHeaders(sofar: Map<String, String>): Map<String, String> {
			val line = reader.readLine()
			val colon = line.indexOf(':')
			return if (colon > 0) {
				val name = line.substring(0, colon).lowercase(Locale.getDefault())
				val value = line.substring(colon + 1).replace("^[ \t]+".toRegex(), "")
				readHeaders(sofar.plus(name to value))
			} else {
				sofar
			}
		}

		return readHeaders(emptyMap())
	}

	private fun parsePath(reader: BufferedReader): String {
		val methodPathProto = reader.readLine().split(" +")
		val method = methodPathProto[0]
		val path = methodPathProto[1]

		return path
	}

	private fun codeText(code: Int) = when (code) {
		200 -> "OK"
		404 -> "Not Found"
		500 -> "Internal Server Error"
		else -> throw RuntimeException("add text for $code")
	}

	private fun sendResponse(out: OutputStream, response: Response) {
		//val stuff = new java.io.ByteArrayOutputStream
		//val writer = new PrintWriter(new OutputStreamWriter(stuff, StandardCharsets.UTF_8))
		val writer = PrintWriter(OutputStreamWriter(out, StandardCharsets.UTF_8))
		val dateFormat = SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US)
		dateFormat.timeZone = TimeZone.getTimeZone("GMT")

		writer.append("HTTP/1.1 ${response.code} ${codeText(response.code)}\r\n")
		writer.append("Date: ${dateFormat.format(Date())}\r\n")
		writer.append("Content-Type: ${response.contentType}; charset=utf-8\r\n")
		val bytes = response.body.byteInputStream(Charsets.UTF_8)
		writer.append("Content-Length: $bytes\r\n")
		writer.append("\r\n")
		writer.append(response.body)
		writer.flush()
	}


	companion object {
		const val DEFAULT_PORT = 9999
		const val DEFAULT_HOSTNAME = "127.0.0.1"
		fun apply(handler: (Request) -> Response): ToyHttp = ToyHttp(handler)
	}

	data class Request(val path: String, val headers: Map<String, String>)

	data class Response(val code: Int, val contentType: String, val body: String)
}
