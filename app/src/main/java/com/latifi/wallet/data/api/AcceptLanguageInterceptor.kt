package com.latifi.wallet.data.api

import com.latifi.wallet.domain.language.LanguageController
import okhttp3.Interceptor
import okhttp3.Response

/** Sends `Accept-Language` with `en-US` or `es-419`, read from the app language on every request. */
class AcceptLanguageInterceptor(private val languages: LanguageController) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Accept-Language", languages.current().tag)
            .build()
        return chain.proceed(request)
    }
}
