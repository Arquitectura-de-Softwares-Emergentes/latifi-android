package com.latifi.wallet.di

import com.latifi.wallet.BuildConfig
import com.latifi.wallet.core.AppConfig
import com.latifi.wallet.data.api.AcceptLanguageInterceptor
import com.latifi.wallet.data.api.ApiClientFactory
import com.latifi.wallet.data.api.AuthInterceptor
import com.latifi.wallet.data.api.LatiFiApi
import com.latifi.wallet.data.api.ReauthAuthenticator
import com.latifi.wallet.domain.auth.ReauthCoordinator
import com.latifi.wallet.domain.language.LanguageController
import com.latifi.wallet.domain.session.SessionStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.time.Clock
import javax.inject.Singleton

/**
 * The HTTP stack of the LatiFi API. Nothing here runs until a Live repository asks for the
 * [LatiFiApi], so Demo mode builds none of it.
 */
@Module
@InstallIn(SingletonComponent::class)
object ApiModule {
    @Provides
    @Singleton
    fun provideJson(): Json = ApiClientFactory.json

    @Provides
    @Singleton
    fun provideOkHttpClient(
        sessions: SessionStore,
        clock: Clock,
        languages: LanguageController,
        coordinator: ReauthCoordinator,
    ): OkHttpClient =
        ApiClientFactory.okHttpClient(
            debug = BuildConfig.DEBUG,
            interceptors = listOf(AcceptLanguageInterceptor(languages), AuthInterceptor(sessions, clock)),
            authenticator = ReauthAuthenticator(coordinator, sessions, clock),
        )

    @Provides
    @Singleton
    fun provideRetrofit(config: AppConfig, client: OkHttpClient, json: Json): Retrofit =
        ApiClientFactory.retrofit(config.apiBaseUrl, client, json)

    @Provides
    @Singleton
    fun provideLatiFiApi(retrofit: Retrofit): LatiFiApi = ApiClientFactory.api(retrofit)
}
