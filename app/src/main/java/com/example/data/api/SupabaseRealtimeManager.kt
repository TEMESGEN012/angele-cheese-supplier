package com.example.data.api

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.Notification
import com.example.data.model.Order
import com.example.data.model.Product
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

enum class RealtimeConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

object SupabaseRealtimeManager {
    private const val TAG = "SupabaseRealtime"

    private val _connectionStatus = MutableStateFlow(RealtimeConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<RealtimeConnectionStatus> = _connectionStatus.asStateFlow()

    private val _lastEventTime = MutableStateFlow<Long?>(null)
    val lastEventTime: StateFlow<Long?> = _lastEventTime.asStateFlow()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private val messageRef = AtomicInteger(1)
    private var appContext: Context? = null

    // Listeners for real-time incoming orders to immediately notify the UI & Background Services
    private val orderListeners = java.util.concurrent.CopyOnWriteArrayList<(Order) -> Unit>()

    var onNewOrderReceived: ((Order) -> Unit)? = null

    fun addOrderListener(listener: (Order) -> Unit) {
        if (!orderListeners.contains(listener)) {
            orderListeners.add(listener)
        }
    }

    fun removeOrderListener(listener: (Order) -> Unit) {
        orderListeners.remove(listener)
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        connectIfEnabled()
    }

    fun reconnect() {
        disconnect()
        connectIfEnabled()
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        reconnectJob?.cancel()
        reconnectJob = null

        try {
            webSocket?.close(1000, "Normal closure")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing websocket", e)
        }
        webSocket = null
        _connectionStatus.value = RealtimeConnectionStatus.DISCONNECTED
    }

    fun connectIfEnabled() {
        val context = appContext ?: return
        if (!SupabaseHelper.isSupabaseEnabled(context)) {
            _connectionStatus.value = RealtimeConnectionStatus.DISCONNECTED
            return
        }

        val rawUrl = SupabaseHelper.getSupabaseUrl(context)
        val apiKey = SupabaseHelper.getSupabaseKey(context)
        if (rawUrl.isBlank() || apiKey.isBlank()) {
            _connectionStatus.value = RealtimeConnectionStatus.DISCONNECTED
            return
        }

        disconnect()

        val wsScheme = if (rawUrl.startsWith("https://", ignoreCase = true)) "wss://" else "ws://"
        val cleanHost = rawUrl
            .replaceFirst("https://", "", ignoreCase = true)
            .replaceFirst("http://", "", ignoreCase = true)
            .trimEnd('/')

        val wsUrl = "$wsScheme$cleanHost/realtime/v1/websocket?apikey=$apiKey&vsn=1.0.0"
        Log.d(TAG, "Connecting to Supabase Realtime WebSocket: $wsUrl")

        _connectionStatus.value = RealtimeConnectionStatus.CONNECTING

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("apikey", apiKey)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Connected to Supabase Realtime WebSocket!")
                _connectionStatus.value = RealtimeConnectionStatus.CONNECTED
                startHeartbeat(webSocket)
                joinRealtimeChannel(webSocket)

                // Perform an initial sync upon connecting to ensure local DB matches remote
                scope.launch {
                    syncInitialData(context)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _lastEventTime.value = System.currentTimeMillis()
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Supabase WebSocket closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Supabase WebSocket closed: $code / $reason")
                _connectionStatus.value = RealtimeConnectionStatus.DISCONNECTED
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Supabase WebSocket failure: ${t.message}", t)
                _connectionStatus.value = RealtimeConnectionStatus.ERROR
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(5000)
            val context = appContext
            if (context != null && SupabaseHelper.isSupabaseEnabled(context)) {
                Log.d(TAG, "Attempting to reconnect Supabase Realtime...")
                connectIfEnabled()
            }
        }
    }

    private fun joinRealtimeChannel(ws: WebSocket) {
        try {
            val ref = messageRef.getAndIncrement().toString()
            val joinPayload = JSONObject().apply {
                put("topic", "realtime:public")
                put("event", "phx_join")
                put("ref", ref)
                put("payload", JSONObject().apply {
                    put("config", JSONObject().apply {
                        put("broadcast", JSONObject().apply {
                            put("ack", false)
                            put("self", false)
                        })
                        put("presence", JSONObject().apply {
                            put("key", "")
                        })
                        put("postgres_changes", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("event", "*")
                                put("schema", "public")
                                put("table", "orders")
                            })
                            put(JSONObject().apply {
                                put("event", "*")
                                put("schema", "public")
                                put("table", "products")
                            })
                            put(JSONObject().apply {
                                put("event", "*")
                                put("schema", "public")
                                put("table", "notifications")
                            })
                            put(JSONObject().apply {
                                put("event", "*")
                                put("schema", "public")
                                put("table", "app_config")
                            })
                        })
                    })
                })
            }
            ws.send(joinPayload.toString())
            Log.d(TAG, "Sent Phoenix Channel join request for public postgres changes.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send channel join", e)
        }
    }

    private fun startHeartbeat(ws: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(25000)
                try {
                    val ref = messageRef.getAndIncrement().toString()
                    val heartbeat = JSONObject().apply {
                        put("topic", "phoenix")
                        put("event", "heartbeat")
                        put("payload", JSONObject())
                        put("ref", ref)
                    }
                    ws.send(heartbeat.toString())
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat failed", e)
                    break
                }
            }
        }
    }

    private fun handleIncomingMessage(jsonText: String) {
        try {
            val root = JSONObject(jsonText)
            val event = root.optString("event")
            val topic = root.optString("topic")

            if (event == "postgres_changes" || topic.startsWith("realtime")) {
                val payload = root.optJSONObject("payload") ?: return
                // Check if data is inside payload or payload.data
                val data = if (payload.has("data")) payload.optJSONObject("data") ?: payload else payload

                val table = data.optString("table", payload.optString("table"))
                val eventType = (data.optString("type", data.optString("eventType", payload.optString("type")))).uppercase()
                val newObj = data.optJSONObject("record") ?: data.optJSONObject("new") ?: payload.optJSONObject("record") ?: payload.optJSONObject("new")
                val oldObj = data.optJSONObject("old_record") ?: data.optJSONObject("old") ?: payload.optJSONObject("old_record") ?: payload.optJSONObject("old")

                Log.d(TAG, "Realtime Change Event: table=$table, eventType=$eventType")

                val context = appContext ?: return
                val db = AppDatabase.getDatabase(context)

                when (table.lowercase()) {
                    "orders" -> handleOrderRealtimeChange(db, eventType, newObj, oldObj)
                    "products" -> handleProductRealtimeChange(db, eventType, newObj, oldObj)
                    "notifications" -> handleNotificationRealtimeChange(db, eventType, newObj)
                    "app_config" -> handleAppConfigRealtimeChange(context, newObj)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming realtime message: $jsonText", e)
        }
    }

    private fun handleOrderRealtimeChange(
        db: AppDatabase,
        eventType: String,
        newObj: JSONObject?,
        oldObj: JSONObject?
    ) {
        scope.launch {
            try {
                if (eventType == "DELETE") {
                    val id = oldObj?.optLong("id") ?: newObj?.optLong("id")
                    if (id != null && id > 0) {
                        db.orderDao().deleteOrderById(id)
                    }
                } else if (newObj != null) {
                    val orderId = newObj.optLong("id", System.currentTimeMillis())
                    val existingOrder = db.orderDao().getOrderById(orderId)

                    val order = Order(
                        id = orderId,
                        customerUsername = newObj.optString("customer_username", newObj.optString("customerUsername", "Customer")),
                        productId = newObj.optString("product_id", newObj.optString("productId", "cheese")),
                        productName = newObj.optString("product_name", newObj.optString("productName", "Dairy Product")),
                        amharicName = newObj.optString("amharic_name", newObj.optString("amharicName", "")),
                        quantityKg = newObj.optDouble("quantity_kg", newObj.optDouble("quantityKg", 1.0)),
                        totalPrice = newObj.optDouble("total_price", newObj.optDouble("totalPrice", 0.0)),
                        status = newObj.optString("status", "Pending"),
                        deliveryAddress = newObj.optString("delivery_address", newObj.optString("deliveryAddress", "Standard Pickup Store")),
                        timestamp = newObj.optLong("timestamp", System.currentTimeMillis())
                    )

                    db.orderDao().insertOrder(order)
                    Log.i(TAG, "Processed Realtime Order #${order.id} ($eventType): status=${order.status}")

                    if (eventType == "INSERT" || (existingOrder == null && order.status == "Pending")) {
                        onNewOrderReceived?.invoke(order)
                        orderListeners.forEach { listener ->
                            try {
                                listener(order)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error notifying order listener", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing order realtime update", e)
            }
        }
    }

    private fun handleProductRealtimeChange(
        db: AppDatabase,
        eventType: String,
        newObj: JSONObject?,
        oldObj: JSONObject?
    ) {
        scope.launch {
            try {
                if (eventType == "DELETE") {
                    val id = oldObj?.optString("id") ?: newObj?.optString("id")
                    if (!id.isNullOrBlank()) {
                        db.productDao().deleteProductById(id)
                    }
                } else if (newObj != null) {
                    val product = Product(
                        id = newObj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = newObj.optString("name", "Product"),
                        amharicName = newObj.optString("amharicName", newObj.optString("amharic_name", "")),
                        category = newObj.optString("category", "Dairy (የወተት)"),
                        pricePerKg = newObj.optDouble("pricePerKg", newObj.optDouble("price_per_kg", 0.0)),
                        stockKg = newObj.optDouble("stockKg", newObj.optDouble("stock_kg", 0.0)),
                        unitName = newObj.optString("unitName", newObj.optString("unit_name", "kg")),
                        description = newObj.optString("description", "Fresh local dairy product"),
                        imageUrl = newObj.optString("imageUrl", newObj.optString("image_url", "")),
                        nutritionalInfo = newObj.optString("nutritionalInfo", newObj.optString("nutritional_info", "High quality local farm fresh product."))
                    )
                    db.productDao().insertProduct(product)
                    Log.i(TAG, "Processed Realtime Product update: ${product.name}, stock=${product.stockKg}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing product realtime update", e)
            }
        }
    }

    private fun handleNotificationRealtimeChange(
        db: AppDatabase,
        eventType: String,
        newObj: JSONObject?
    ) {
        scope.launch {
            try {
                if (newObj != null) {
                    val notif = Notification(
                        id = newObj.optLong("id", System.currentTimeMillis()),
                        customerUsername = newObj.optString("customerUsername", newObj.optString("customer_username", "admin")),
                        orderId = newObj.optLong("orderId", newObj.optLong("order_id", 0L)),
                        title = newObj.optString("title", "Notification"),
                        titleAmharic = newObj.optString("titleAmharic", newObj.optString("title_amharic", "")),
                        message = newObj.optString("message", ""),
                        messageAmharic = newObj.optString("messageAmharic", newObj.optString("message_amharic", "")),
                        status = newObj.optString("status", "Pending"),
                        isRead = newObj.optBoolean("isRead", newObj.optBoolean("is_read", false)),
                        timestamp = newObj.optLong("timestamp", System.currentTimeMillis())
                    )
                    db.notificationDao().insertNotification(notif)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification realtime update", e)
            }
        }
    }

    private fun handleAppConfigRealtimeChange(context: Context, newObj: JSONObject?) {
        scope.launch {
            try {
                if (newObj != null) {
                    val minCode = newObj.optInt("minimum_version_code", newObj.optInt("minimumVersionCode", 1))
                    val updateUrl = newObj.optString("update_url", newObj.optString("updateUrl", ""))
                    Log.i(TAG, "Processed Realtime AppConfig update: minCode=$minCode")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing app_config realtime update", e)
            }
        }
    }

    private suspend fun syncInitialData(context: Context) {
        try {
            val db = AppDatabase.getDatabase(context)

            val products = SupabaseHelper.fetchProducts(context)
            if (products != null && products.isNotEmpty()) {
                products.forEach { db.productDao().insertProduct(it) }
            }

            val orders = SupabaseHelper.fetchOrders(context)
            if (orders != null && orders.isNotEmpty()) {
                orders.forEach { db.orderDao().insertOrder(it) }
            }

            val notifs = SupabaseHelper.fetchNotifications(context)
            if (notifs != null && notifs.isNotEmpty()) {
                notifs.forEach { db.notificationDao().insertNotification(it) }
            }
            Log.d(TAG, "Initial sync with Supabase completed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Initial sync failed", e)
        }
    }

    /**
     * Test Supabase REST & WebSocket connection connectivity.
     */
    suspend fun testConnection(context: Context, rawUrl: String, key: String): Result<String> {
        return try {
            val cleanBaseUrl = SupabaseHelper.sanitizeSupabaseUrl(rawUrl)
            val wsScheme = if (cleanBaseUrl.startsWith("https://", ignoreCase = true)) "wss://" else "ws://"
            val cleanHost = cleanBaseUrl
                .replaceFirst("https://", "", ignoreCase = true)
                .replaceFirst("http://", "", ignoreCase = true)
                .trimEnd('/')

            val restUrl = "$cleanBaseUrl/rest/v1/products?select=count"
            val request = Request.Builder()
                .url(restUrl)
                .addHeader("apikey", key.trim())
                .addHeader("Authorization", "Bearer ${key.trim()}")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful || response.code == 200 || response.code == 206) {
                Result.success("Connection Successful! Realtime endpoint: $wsScheme$cleanHost")
            } else {
                Result.failure(Exception("Supabase returned HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
