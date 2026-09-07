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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
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

    // --- App Config Sync ---

    fun fetchAppConfig(context: Context): SupabaseAppConfig? {
        val request = buildRequest(context, "app_config?id=eq.1&select=*", "GET") ?: return null
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "fetchAppConfig failed: ${response.code} ${response.message}")
                    return null
                }
                val responseBody = response.body?.string() ?: return null
                val listType = Types.newParameterizedType(List::class.java, SupabaseAppConfig::class.java)
                val listAdapter = moshi.adapter<List<SupabaseAppConfig>>(listType)
                val list = listAdapter.fromJson(responseBody)
                list?.firstOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching app config from Supabase", e)
            null
        }
    }

    fun updateAppConfig(context: Context, minCode: Int, url: String): Boolean {
        val config = SupabaseAppConfig(id = 1, minimum_version_code = minCode, update_url = url)
        val adapter = moshi.adapter(SupabaseAppConfig::class.java)
        val json = adapter.toJson(config)
        
        // PostgREST upsert works by POST with Prefer: resolution=merge-duplicates
        val request = buildRequest(context, "app_config", "POST", json) ?: return false
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "updateAppConfig failed: ${response.code} ${response.body?.string()}")
                    false
                } else {
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating app config on Supabase", e)
            false
        }
    }

    // --- Products Sync ---

    fun fetchProducts(context: Context): List<Product>? {
        val request = buildRequest(context, "products?select=*", "GET") ?: return null
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "fetchProducts failed: ${response.code}")
                    return null
                }
                val responseBody = response.body?.string() ?: return null
                val listType = Types.newParameterizedType(List::class.java, Product::class.java)
                val adapter = moshi.adapter<List<Product>>(listType)
                adapter.fromJson(responseBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching products from Supabase", e)
            null
        }
    }

    fun upsertProduct(context: Context, product: Product): Boolean {
        val adapter = moshi.adapter(Product::class.java)
        val json = adapter.toJson(product)
        val request = buildRequest(context, "products", "POST", json) ?: return false
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "upsertProduct failed: ${response.code} ${response.body?.string()}")
                    false
                } else {
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error upserting product on Supabase", e)
            false
        }
    }

    fun deleteProduct(context: Context, id: String): Boolean {
        val request = buildRequest(context, "products?id=eq.$id", "DELETE") ?: return false
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "deleteProduct failed: ${response.code}")
                    false
                } else {
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting product on Supabase", e)
            false
        }
    }

    // --- Orders Sync ---

    fun fetchOrders(context: Context): List<Order>? {
        val request = buildRequest(context, "orders?select=*", "GET") ?: return null
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "fetchOrders failed: ${response.code}")
                    return null
                }
                val responseBody = response.body?.string() ?: return null
                val listType = Types.newParameterizedType(List::class.java, Order::class.java)
                val adapter = moshi.adapter<List<Order>>(listType)
                adapter.fromJson(responseBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching orders from Supabase", e)
            null
        }
    }

    fun upsertOrder(context: Context, order: Order): Boolean {
        val adapter = moshi.adapter(Order::class.java)
        val json = adapter.toJson(order)
        val request = buildRequest(context, "orders", "POST", json) ?: return false
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "upsertOrder failed: ${response.code} ${response.body?.string()}")
                    false
                } else {
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error upserting order on Supabase", e)
            false
        }
    }

    fun deleteOrder(context: Context, id: Long): Boolean {
        val request = buildRequest(context, "orders?id=eq.$id", "DELETE") ?: return false
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "deleteOrder failed: ${response.code}")
                    false
                } else {
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting order on Supabase", e)
            false
        }
    }

    // --- Notifications Sync ---

    fun fetchNotifications(context: Context): List<Notification>? {
        val request = buildRequest(context, "notifications?select=*", "GET") ?: return null
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "fetchNotifications failed: ${response.code}")
                    return null
                }
                val responseBody = response.body?.string() ?: return null
                val listType = Types.newParameterizedType(List::class.java, Notification::class.java)
                val adapter = moshi.adapter<List<Notification>>(listType)
                adapter.fromJson(responseBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching notifications from Supabase", e)
            null
        }
    }

    fun upsertNotification(context: Context, notification: Notification): Boolean {
        val adapter = moshi.adapter(Notification::class.java)
        val json = adapter.toJson(notification)
        val request = buildRequest(context, "notifications", "POST", json) ?: return false
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "upsertNotification failed: ${response.code} ${response.body?.string()}")
                    false
                } else {
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error upserting notification on Supabase", e)
            false
        }
    }
}
