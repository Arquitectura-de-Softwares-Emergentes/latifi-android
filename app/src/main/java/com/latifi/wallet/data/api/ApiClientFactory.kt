package com.latifi.wallet.data.api

import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds the HTTP stack of the LatiFi API: OkHttp, kotlinx.serialization and Retrofit. Nothing
 * here knows a host, the base URL comes from `AppConfig.apiBaseUrl` and ends with a slash.
 */
object ApiClientFactory {
    /** Unknown keys are ignored so a new server field never breaks the app, and nulls are not written. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    const val CONNECT_TIMEOUT_SECONDS = 10L
    const val READ_TIMEOUT_SECONDS = 20L

    /**
     * The client of the API. Interceptors run in the order given, the authenticator answers 401.
     * Logging exists only when [debug] is true, stays at BASIC (request line and status, no
     * headers, no bodies) and redacts `Authorization` as a second line of defense.
     */
    fun okHttpClient(
        debug: Boolean,
        interceptors: List<Interceptor>,
        authenticator: Authenticator?,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        interceptors.forEach { builder.addInterceptor(it) }
        authenticator?.let { builder.authenticator(it) }
        if (debug) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                    redactHeader("Authorization")
                },
            )
        }
        return builder.build()
    }

    /** A plain client for the JSON-RPC node: no interceptor, so the API token never reaches it, and no logging. */
    fun rpcHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    /** [baseUrl] must end with a slash, as `AppConfig.apiBaseUrl` does. */
    fun retrofit(baseUrl: String, client: OkHttpClient, json: Json = ApiClientFactory.json): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    fun api(retrofit: Retrofit): LatiFiApi = retrofit.create(LatiFiApi::class.java)
}
