package com.jaydocoder.plateview.data.network

import com.jaydocoder.plateview.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideHttpClient(runtimePolicyRepository: ClientRuntimePolicyRepository): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(RequestIdInterceptor())
        .addInterceptor { chain ->
            val request = chain.request()
            val policy = runtimePolicyRepository.policy.value
            val rewritten = rewriteApiUrl(request.url.toString(), BuildConfig.API_BASE_URL, policy.apiBaseUrl)
                ?.toHttpUrlOrNull() ?: request.url
            val primaryRequest = request.newBuilder().url(rewritten).build()
            val fallbackUrl = policy.previousApiBaseUrl
                ?.takeIf { it != policy.apiBaseUrl && request.body?.isOneShot() != true }
                ?.let { rewriteApiUrl(request.url.toString(), BuildConfig.API_BASE_URL, it) }
                ?.toHttpUrlOrNull()
            try {
                val response = chain.proceed(primaryRequest)
                if (response.code !in RETRYABLE_GATEWAY_CODES || fallbackUrl == null) return@addInterceptor response
                response.close()
                chain.proceed(request.newBuilder().url(fallbackUrl).build()).also { fallback ->
                    if (fallback.code !in RETRYABLE_GATEWAY_CODES) runtimePolicyRepository.rollbackApiBaseUrl(policy.apiBaseUrl)
                }
            } catch (error: IOException) {
                if (fallbackUrl == null) throw error
                chain.proceed(request.newBuilder().url(fallbackUrl).build()).also { fallback ->
                    if (fallback.code !in RETRYABLE_GATEWAY_CODES) runtimePolicyRepository.rollbackApiBaseUrl(policy.apiBaseUrl)
                }
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}

private val RETRYABLE_GATEWAY_CODES = setOf(502, 503, 504)

internal fun rewriteApiUrl(requestUrl: String, compiledBaseUrl: String, runtimeBaseUrl: String): String? {
    val request = requestUrl.toHttpUrlOrNull() ?: return null
    val compiled = compiledBaseUrl.toHttpUrlOrNull() ?: return null
    val runtime = runtimeBaseUrl.toHttpUrlOrNull() ?: return null
    val compiledPath = compiled.encodedPath.trimEnd('/')
    val relativePath = request.encodedPath.removePrefix(compiledPath).trimStart('/')
    return request.newBuilder()
        .scheme(runtime.scheme)
        .host(runtime.host)
        .port(runtime.port)
        .encodedPath(runtime.encodedPath.trimEnd('/') + "/" + relativePath)
        .build()
        .toString()
}
