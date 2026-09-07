package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.api.SupabaseHelper
import com.example.data.api.SupabaseRealtimeManager
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AdminNotificationService : Service() {

    private val CHANNEL_ID = "admin_orders_channel"
    private val SERVICE_ID = 101

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(SERVICE_ID, notification)
        }

        listenForRealtimeSync()

        return START_STICKY
    }

    private val serviceStartTime = System.currentTimeMillis()
    private var isListening = false
    private var isInitialOrdersLoaded = false

    private fun listenForRealtimeSync() {
        if (isListening) return
        isListening = true

        // Register Realtime WebSocket listener for immediate instant push notifications
        SupabaseRealtimeManager.initialize(applicationContext)
        SupabaseRealtimeManager.addOrderListener { order ->
            if (order.status == "Pending") {
                showOrderNotification(order.customerUsername, order.productName, order.quantityKg)
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                if (SupabaseHelper.isSupabaseEnabled(applicationContext)) {
                    try {
                        val remoteProducts = SupabaseHelper.fetchProducts(applicationContext)
                        if (remoteProducts != null && remoteProducts.isNotEmpty()) {
                            val db = AppDatabase.getDatabase(applicationContext)
                            remoteProducts.forEach { db.productDao().insertProduct(it) }
                        }

                        val remoteOrders = SupabaseHelper.fetchOrders(applicationContext)
                        if (remoteOrders != null) {
                            val db = AppDatabase.getDatabase(applicationContext)
                            remoteOrders.forEach { order ->
                                val existing = db.orderDao().getOrderById(order.id)
                                db.orderDao().insertOrder(order)
                                if (existing == null && order.status == "Pending" && (isInitialOrdersLoaded || order.timestamp > serviceStartTime)) {
                                    showOrderNotification(order.customerUsername, order.productName, order.quantityKg)
                                }
                            }
                            isInitialOrdersLoaded = true
                        }

                        val remoteNotifs = SupabaseHelper.fetchNotifications(applicationContext)
                        if (remoteNotifs != null && remoteNotifs.isNotEmpty()) {
                            val db = AppDatabase.getDatabase(applicationContext)
                            remoteNotifs.forEach { db.notificationDao().insertNotification(it) }
                        }
                    } catch (e: Exception) {
                        Log.e("AdminService", "Supabase sync error", e)
                    }
                }
                kotlinx.coroutines.delay(8000)
            }
        }
    }

    private fun showOrderNotification(customer: String, product: String, qty: Double) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Order from $customer!")
            .setContentText("Ordered $qty kg of $product")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(android.app.Notification.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createForegroundNotification(): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sync Service Active")
            .setContentText("Monitoring orders and catalog updates...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Admin Orders Channel"
            val descriptionText = "Notifications for new customer orders"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
