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
import com.example.data.SessionManager
import com.example.data.api.SupabaseHelper
import com.example.data.api.SupabaseRealtimeManager
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AdminNotificationService : Service() {

    companion object {
        private const val CHANNEL_ID = "admin_orders_channel"
        private const val SERVICE_ID = 101

        /**
         * Starts the background order listener ONLY if user is logged in as admin or super admin.
         */
        fun start(context: Context) {
            if (!SessionManager.isAdminOrSuperAdminLoggedIn(context)) {
                Log.d("AdminNotificationService", "Skipping service start: user is not admin or super admin.")
                return
            }
            try {
                val serviceIntent = Intent(context, AdminNotificationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e("AdminNotificationService", "Failed to start service", e)
            }
        }

        /**
         * Stops the background order listener and removes notifications.
         */
        fun stop(context: Context) {
            try {
                val serviceIntent = Intent(context, AdminNotificationService::class.java)
                context.stopService(serviceIntent)
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(SERVICE_ID)
            } catch (e: Exception) {
                Log.e("AdminNotificationService", "Failed to stop service", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // STRICT PRIVACY CHECK: Only allow service to run if logged in user is admin or super admin
        if (!SessionManager.isAdminOrSuperAdminLoggedIn(applicationContext)) {
            Log.d("AdminNotificationService", "Stopping service: user is not logged in as admin or super admin.")
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

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
            if (order.status == "Pending" && SessionManager.isAdminOrSuperAdminLoggedIn(applicationContext)) {
                showOrderNotification(order.customerUsername, order.productName, order.quantityKg)
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                // If the user logged out or switched to a regular customer account, shut down immediately
                if (!SessionManager.isAdminOrSuperAdminLoggedIn(applicationContext)) {
                    Log.d("AdminNotificationService", "User logged out or not admin, terminating service loop.")
                    stopForeground(true)
                    stopSelf()
                    break
                }

                if (SupabaseHelper.isSupabaseEnabled(applicationContext)) {
                    try {
                        val remoteOrders = SupabaseHelper.fetchOrders(applicationContext)
                        if (remoteOrders != null) {
                            val db = AppDatabase.getDatabase(applicationContext)
                            remoteOrders.forEach { order ->
                                val existing = db.orderDao().getOrderById(order.id)
                                db.orderDao().insertOrder(order)
                                if (existing == null && order.status == "Pending" && (isInitialOrdersLoaded || order.timestamp > serviceStartTime)) {
                                    if (SessionManager.isAdminOrSuperAdminLoggedIn(applicationContext)) {
                                        showOrderNotification(order.customerUsername, order.productName, order.quantityKg)
                                    }
                                }
                            }
                            isInitialOrdersLoaded = true
                        }
                    } catch (e: Exception) {
                        Log.w("AdminService", "Supabase sync notice: ${e.message}")
                    }
                }
                kotlinx.coroutines.delay(25000)
            }
        }
    }

    private fun showOrderNotification(customer: String, product: String, qty: Double) {
        // Double check admin role before posting any system notification
        if (!SessionManager.isAdminOrSuperAdminLoggedIn(applicationContext)) {
            return
        }

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
            .setContentTitle("Admin Order Listener Active")
            .setContentText("Listening for customer orders...")
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
