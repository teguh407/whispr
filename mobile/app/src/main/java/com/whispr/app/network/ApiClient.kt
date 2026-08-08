package com.whispr.app.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private var baseUrl = "http://43.153.207.36/"
    private var authToken: String? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder().apply {
            authToken?.let { addHeader("Authorization", "Bearer $it") }
        }.build()
        chain.proceed(request)
    }

    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    var api: ApiService = createApi()

    private fun createApi(): ApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/') + "/"
        api = createApi()
    }

    fun setToken(token: String?) {
        authToken = token
    }

    fun getBaseUrl() = baseUrl

    /** Build a full media URL from a relative path like "/uploads/photos/xxx.jpg".
     *  Handles double-slash by trimming the leading slash from the path. */
    fun buildMediaUrl(path: String?): String {
        if (path.isNullOrBlank()) return ""
        val cleanPath = path.trimStart('/')
        return baseUrl.trimEnd('/') + "/" + cleanPath
    }

    fun getWsUrl(path: String): String {
        val base = baseUrl.replace("http://", "ws://").replace("https://", "wss://").trimEnd('/')
        return "$base$path"
    }
}
