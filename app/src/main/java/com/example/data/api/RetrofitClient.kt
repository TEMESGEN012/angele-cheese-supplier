package com.example.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private var currentBaseUrl: String? = null
    private var apiService: ApiService? = null

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    fun getApiService(baseUrl: String): ApiService {
        // Ensure trailing slash
        val sanitizedUrl = if (baseUrl.trim().endsWith("/")) baseUrl.trim() else "${baseUrl.trim()}/"
        if (apiService == null || currentBaseUrl != sanitizedUrl) {
            currentBaseUrl = sanitizedUrl
            apiService = Retrofit.Builder()
                .baseUrl(sanitizedUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(ApiService::class.java)
        }
        return apiService!!
    }
}
