package com.example.metaguardrail.ai

import com.example.metaguardrail.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

object BackendProxyConfig {

    fun backendBaseUrl(): String = BuildConfig.BACKEND_BASE_URL
    fun devApiKey(): String = BuildConfig.DEV_BASETEN_API_KEY
    fun isDevMode(): Boolean = BuildConfig.IS_DEV_BACKEND
    fun museModelId(): String = BuildConfig.MUSE_SPARK_MODEL
    fun isBackendConfigured(): Boolean =
        backendBaseUrl() != "https://meta-guardrail-backend.example.com" && backendBaseUrl().isNotBlank()

    fun createOkHttpClient(
        apiKeyProvider: () -> String?,
        logger: Logger = AndroidLogger
    ): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val apiKey = apiKeyProvider()?.trim()
            val builder = original.newBuilder()
            if (!apiKey.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $apiKey")
            }
            chain.proceed(builder.build())
        }

        val safeLoggingInterceptor = Interceptor { chain ->
            val request = chain.request()
            val startNs = System.nanoTime()
            val response = try {
                chain.proceed(request)
            } catch (e: Exception) {
                logger.d(
                    "BackendProxyConfig",
                    "HTTP ${request.method} ${request.url.host}${request.url.encodedPath} failed: ${e.javaClass.simpleName} length=${request.body?.contentLength() ?: -1}"
                )
                throw e
            }
            val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
            logger.d(
                "BackendProxyConfig",
                "HTTP ${request.method} ${request.url.host}${request.url.encodedPath} -> ${response.code} " +
                    "reqLen=${request.body?.contentLength() ?: -1} respLen=${response.body?.contentLength() ?: -1} ${tookMs}ms"
            )
            response
        }

        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(safeLoggingInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE })
            .build()
    }

    fun createService(
        baseUrl: String,
        client: OkHttpClient
    ): MuseSparkService {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
            encodeDefaults = true
        }
        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MuseSparkService::class.java)
    }

    fun createMuseService(
        apiKeyProvider: () -> String? = { devApiKey().ifBlank { null } }
    ): MuseSparkService {
        val client = createOkHttpClient(apiKeyProvider)
        return createService(backendBaseUrl(), client)
    }
}
