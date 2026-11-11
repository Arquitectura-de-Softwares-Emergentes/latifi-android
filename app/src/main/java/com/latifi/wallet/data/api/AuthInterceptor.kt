package com.latifi.wallet.data.api

import com.latifi.wallet.domain.session.SessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.time.Clock

/**
 * Attaches `Authorization: Bearer <token>` only while the stored token has not expired. A stale
 * token is never sent, so a public read such as `getProfile` stays anonymous instead of earning
 * a 401 (the API answers 401 to an expired token even on a public read). The auth operations
 * never carry a token.
 *
 * Interceptors run on an OkHttp thread, so reading the store with `runBlocking` blocks nothing
 * the UI waits on.
 */
class AuthInterceptor(
    private val sessions: SessionStore,
    private val clock: Clock,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (isAuthPath(request.url) || request.header(AUTHORIZATION) != null) return chain.proceed(request)
        val session = runBlocking { sessions.current() }
        if (session == null || !session.expiresAt.isAfter(clock.instant())) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(AUTHORIZATION, "$BEARER ${session.token}").build())
    }

    companion object {
        const val AUTHORIZATION = "Authorization"
        const val BEARER = "Bearer"

        /** True for `/auth/challenge` and `/auth/verify`, whatever the base path is. */
        fun isAuthPath(url: HttpUrl): Boolean = url.pathSegments.dropLast(1).lastOrNull() == "auth"
    }
}
