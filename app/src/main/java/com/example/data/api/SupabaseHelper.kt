package com.example.data.api

import android.content.Context
import android.util.Log
import com.example.data.model.Notification
import com.example.data.model.Order
import com.example.data.model.Product
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class SupabaseAppConfig(
    val id: Int = 1,
    val minimum_version_code: Int,
    val update_url: String
)

object SupabaseHelper {
    private const val TAG = "SupabaseHelper"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun getPrefs(context: Context) =
        context.getSharedPreferences("dairy_api_prefs", Context.MODE_PRIVATE)

    fun isSupabaseEnabled(context: Context): Boolean {
        val prefs = getPrefs(context)
        val dbType = prefs.getString("db_type", "supabase") ?: "supabase"
        val url = getSupabaseUrl(context)
        val key = getSupabaseKey(context)
        return (dbType == "supabase" || dbType.isBlank()) && url.isNotBlank() && key.isNotBlank()
    }

    fun getSupabaseUrl(context: Context): String {
        val raw = getPrefs(context).getString("supabase_url", "https://ibwjydljyjbrqbpspbuk.supabase.co") ?: "https://ibwjydljyjbrqbpspbuk.supabase.co"
        return sanitizeSupabaseUrl(raw)
    }

    fun sanitizeSupabaseUrl(url: String): String {
        return url.trim()
            .replace(Regex("/rest/v1/?$"), "")
            .replace(Regex("/realtime/v1/?$"), "")
            .trimEnd('/')
    }

    fun getSupabaseKey(context: Context): String {
        return getPrefs(context).getString("supabase_key", "sb_publishable_zGGs5I2TL6jQtJNSM1n53A_MeYSsLFn")?.trim() ?: "sb_publishable_zGGs5I2TL6jQtJNSM1n53A_MeYSsLFn"
    }

    private fun buildRequest(context: Context, path: String, method: String, jsonBody: String? = null): Request? {
        val baseUrl = getSupabaseUrl(context)
        val key = getSupabaseKey(context)
        if (baseUrl.isBlank() || key.isBlank()) return null

        val url = "$baseUrl/rest/v1/$path"

        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=merge-duplicates") // Upsert support in PostgREST

        when (method) {
            "POST" -> {
                val body = (jsonBody ?: "{}").toRequestBody("application/json".toMediaType())
                requestBuilder.post(body)
            }
            "GET" -> requestBuilder.get()
            "DELETE" -> requestBuilder.delete()
            "PATCH" -> {
                val body = (jsonBody ?: "{}").toRequestBody("application/json".toMediaType())
                requestBuilder.patch(body)
            }
        }

        return requestBuilder.build()
    }

    private inline fun <T> executeRequestWithRetry(
        context: Context,
        path: String,
        method: String,
        jsonBody: String? = null,
        parser: (String) -> T?
    ): T? {
        val request = buildRequest(context, path, method, jsonBody) ?: return null
        val maxAttempts = 2
        var lastCode = 0

        for (attempt in 1..maxAttempts) {
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    lastCode = response.code
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return null
                        return parser(body)
                    } else if (response.code in listOf(502, 503, 504)) {
                        Log.w(TAG, "$path server gateway notice: ${response.code} (attempt $attempt/$maxAttempts)")
                        if (attempt < maxAttempts) {
                            try { Thread.sleep(1500L) } catch (_: InterruptedException) {}
                        }
                    } else {
                        Log.w(TAG, "$path request unfulfilled: ${response.code} ${response.message}")
                        return null
                    }
                }
            } catch (e: Exception) {
                if (e is UnknownHostException || e.message?.contains("Unable to resolve host", ignoreCase = true) == true) {
                    Log.w(TAG, "$path host resolution notice: Supabase host unreachable (project may be paused or device offline). Continuing with local Room database.")
                    return null
                }
                Log.w(TAG, "$path attempt $attempt/$maxAttempts network warning: ${e.message}")
                if (attempt < maxAttempts) {
                    try { Thread.sleep(1500L) } catch (_: InterruptedException) {}
                }
            }
        }
        if (lastCode in listOf(502, 503, 504)) {
            Log.w(TAG, "$path server gateway timeout ($lastCode). Continuing with local Room database.")
        }
        return null
    }

    private fun executeBooleanWithRetry(
        context: Context,
        path: String,
        method: String,
        jsonBody: String? = null
    ): Boolean {
        val request = buildRequest(context, path, method, jsonBody) ?: return false
        val maxAttempts = 2

        for (attempt in 1..maxAttempts) {
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return true
                    } else if (response.code in listOf(502, 503, 504)) {
                        Log.w(TAG, "$path server gateway notice: ${response.code} (attempt $attempt/$maxAttempts)")
                        if (attempt < maxAttempts) {
                            try { Thread.sleep(1500L) } catch (_: InterruptedException) {}
                        }
                    } else {
                        Log.w(TAG, "$path boolean request unfulfilled: ${response.code}")
                        return false
                    }
                }
            } catch (e: Exception) {
                if (e is UnknownHostException || e.message?.contains("Unable to resolve host", ignoreCase = true) == true) {
                    Log.w(TAG, "$path host resolution notice: Supabase host unreachable (project may be paused or device offline). Continuing with local Room database.")
                    return false
                }
                Log.w(TAG, "$path boolean attempt $attempt/$maxAttempts error: ${e.message}")
                if (attempt < maxAttempts) {
                    try { Thread.sleep(1500L) } catch (_: InterruptedException) {}
                }
            }
        }
        return false
    }

    // --- App Config Sync ---

    fun fetchAppConfig(context: Context): SupabaseAppConfig? {
        return executeRequestWithRetry(context, "app_config?id=eq.1&select=*", "GET") { responseBody ->
            val listType = Types.newParameterizedType(List::class.java, SupabaseAppConfig::class.java)
            val listAdapter = moshi.adapter<List<SupabaseAppConfig>>(listType)
            val list = listAdapter.fromJson(responseBody)
            list?.firstOrNull()
        }
    }

    fun updateAppConfig(context: Context, minCode: Int, url: String): Boolean {
        val config = SupabaseAppConfig(id = 1, minimum_version_code = minCode, update_url = url)
        val adapter = moshi.adapter(SupabaseAppConfig::class.java)
        val json = adapter.toJson(config)
        return executeBooleanWithRetry(context, "app_config", "POST", json)
    }

    // --- Products Sync ---

    fun fetchProducts(context: Context): List<Product>? {
        return executeRequestWithRetry(context, "products?select=*", "GET") { responseBody ->
            val listType = Types.newParameterizedType(List::class.java, Product::class.java)
            val adapter = moshi.adapter<List<Product>>(listType)
            adapter.fromJson(responseBody)
        }
    }

    fun upsertProduct(context: Context, product: Product): Boolean {
        val adapter = moshi.adapter(Product::class.java)
        val json = adapter.toJson(product)
        return executeBooleanWithRetry(context, "products", "POST", json)
    }

    fun deleteProduct(context: Context, id: String): Boolean {
        return executeBooleanWithRetry(context, "products?id=eq.$id", "DELETE")
    }

    // --- Orders Sync ---

    fun fetchOrders(context: Context): List<Order>? {
        return executeRequestWithRetry(context, "orders?select=*", "GET") { responseBody ->
            val listType = Types.newParameterizedType(List::class.java, Order::class.java)
            val adapter = moshi.adapter<List<Order>>(listType)
            adapter.fromJson(responseBody)
        }
    }

    fun upsertOrder(context: Context, order: Order): Boolean {
        val adapter = moshi.adapter(Order::class.java)
        val json = adapter.toJson(order)
        return executeBooleanWithRetry(context, "orders", "POST", json)
    }

    fun deleteOrder(context: Context, id: Long): Boolean {
        return executeBooleanWithRetry(context, "orders?id=eq.$id", "DELETE")
    }

    // --- Notifications Sync ---

    fun fetchNotifications(context: Context): List<Notification>? {
        return executeRequestWithRetry(context, "notifications?select=*", "GET") { responseBody ->
            val listType = Types.newParameterizedType(List::class.java, Notification::class.java)
            val adapter = moshi.adapter<List<Notification>>(listType)
            adapter.fromJson(responseBody)
        }
    }

    fun upsertNotification(context: Context, notification: Notification): Boolean {
        val adapter = moshi.adapter(Notification::class.java)
        val json = adapter.toJson(notification)
        return executeBooleanWithRetry(context, "notifications", "POST", json)
    }
}
