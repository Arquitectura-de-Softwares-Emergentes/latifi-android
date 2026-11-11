package com.latifi.wallet.data.api

import okhttp3.Interceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.File

/** A MockWebServer that stands for the API, with the real HTTP stack of [ApiClientFactory] in front of it. */
class ApiTestRig(
    interceptors: List<Interceptor> = emptyList(),
    authenticator: okhttp3.Authenticator? = null,
) : AutoCloseable {
    val server = MockWebServer().apply { start() }
    val api: LatiFiApi = ApiClientFactory.api(
        ApiClientFactory.retrofit(
            baseUrl = server.url("/api/v1/").toString(),
            client = ApiClientFactory.okHttpClient(debug = false, interceptors = interceptors, authenticator = authenticator),
        ),
    )

    fun enqueueJson(body: String, code: Int = 200, headers: Map<String, String> = emptyMap()) {
        val response = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
        headers.forEach { (name, value) -> response.setHeader(name, value) }
        server.enqueue(response)
    }

    fun enqueueFixture(name: String, code: Int = 200) = enqueueJson(fixture(name), code)

    fun enqueueError(code: Int, errorCode: String, headers: Map<String, String> = emptyMap()) =
        enqueueJson("""{"status":$code,"code":"$errorCode","message":"server text that must never reach a screen"}""", code, headers)

    override fun close() = server.shutdown()

    companion object {
        /** A fixture of `src/test/resources/fixtures/api`. The working directory of a unit test is the app module. */
        fun fixture(name: String): String = File("src/test/resources/fixtures/api/$name").readText()
    }
}
