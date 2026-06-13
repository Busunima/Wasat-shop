package com.wasat.shop.core.di

import android.content.Context
import com.wasat.shop.BuildConfig
import com.wasat.shop.core.network.AuthInterceptor
import com.wasat.shop.core.network.CacheControlInterceptor
import com.wasat.shop.core.network.OfflineCacheInterceptor
import com.wasat.shop.core.network.WasatApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val HTTP_CACHE_BYTES = 10L * 1024 * 1024 // 10 МБ дискового кэша

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true // сервер может расширять ответы без поломки клиента
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        authInterceptor: AuthInterceptor,
        offlineCacheInterceptor: OfflineCacheInterceptor,
        cacheControlInterceptor: CacheControlInterceptor,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "http_cache"), HTTP_CACHE_BYTES))
            // auth добавляет Authorization до кэша (нужно для Vary); offline — фолбэк
            // на кэш без сети; cacheControl (сетевой) делает GET-ответы хранимыми.
            .addInterceptor(authInterceptor)
            .addInterceptor(offlineCacheInterceptor)
            .addNetworkInterceptor(cacheControlInterceptor)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideWasatApi(retrofit: Retrofit): WasatApi = retrofit.create(WasatApi::class.java)
}
