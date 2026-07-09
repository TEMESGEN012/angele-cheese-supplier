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
import com.example.data.local.AppDatabase
import com.example.data.model.Order
import com.example.data.model.Product
import com.example.data.model.Notification as DbNotification
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
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

        val database = FirebaseDatabase.getInstance("https://angel-cheese-supplier-default-rtdb.firebaseio.com")

        // 1. Core Background Order Sync & Notification triggers
        val ordersRef = database.getReference("orders")

        ordersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                isInitialOrdersLoaded = true
                Log.d("AdminService", "Initial orders loading complete.")
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        ordersRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                syncAndNotifyOrder(snapshot, isNewAdded = true)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                syncAndNotifyOrder(snapshot, isNewAdded = false)
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                try {
                    val id = snapshot.child("id").getValue(Long::class.java) ?: 0L
                    if (id != 0L) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                AppDatabase.getDatabase(applicationContext).orderDao().deleteOrderById(id)
                                Log.d("AdminService", "Removed order $id locally from DB removal")
                            } catch (ex: Exception) {
                                Log.e("AdminService", "Error deleting deleted order from local DB", ex)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AdminService", "Error parsing removed order snapshot", e)
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e("AdminService", "Orders listener cancelled: ${error.message}")
            }
        })

        // 2. Real-time Products catalog background synchronization
        val productsRef = database.getReference("products")
        productsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                syncProduct(snapshot)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                syncProduct(snapshot)
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                try {
                    val id = snapshot.child("id").getValue(String::class.java)
                    if (!id.isNullOrBlank()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                AppDatabase.getDatabase(applicationContext).productDao().deleteProductById(id)
                                Log.d("AdminService", "Deleted product $id locally")
                            } catch (ex: Exception) {
                                Log.e("AdminService", "Error deleting product $id locally", ex)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AdminService", "Error deleting removed product", e)
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })

        // 3. Customer Notifications list synchronization
        val notificationsRef = database.getReference("notifications")
        notificationsRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                syncNotificationsForUser(snapshot)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                syncNotificationsForUser(snapshot)
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun syncAndNotifyOrder(snapshot: DataSnapshot, isNewAdded: Boolean) {
        try {
            val id = snapshot.child("id").getValue(Long::class.java) ?: 0L
            val customer = snapshot.child("customerUsername").getValue(String::class.java) ?: ""
            val productId = snapshot.child("productId").getValue(String::class.java) ?: ""
            val productName = snapshot.child("productName").getValue(String::class.java) ?: ""
            val qty = (snapshot.child("quantityKg").value as? Number)?.toDouble() ?: 0.0
            val totalPrice = (snapshot.child("totalPrice").value as? Number)?.toDouble() ?: 0.0
            val status = snapshot.child("status").getValue(String::class.java) ?: "Pending"
            val address = snapshot.child("deliveryAddress").getValue(String::class.java) ?: "Standard Pickup Store"
            val amharicName = snapshot.child("amharicName").getValue(String::class.java) ?: ""
            val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()

            val orderObj = Order(
                id = id,
                customerUsername = customer,
                productId = productId,
                productName = productName,
                amharicName = amharicName,
                quantityKg = qty,
                totalPrice = totalPrice,
                status = status,
                deliveryAddress = address,
                timestamp = timestamp
            )

            // Save or Update locally in Room immediately
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AppDatabase.getDatabase(applicationContext).orderDao().insertOrder(orderObj)
                    Log.d("AdminService", "Synced order $id successfully to local DB")
                } catch (ex: Exception) {
                    Log.e("AdminService", "Error syncing order directly to local DB", ex)
                }
            }

            // Only trigger a status-bar push notification if it is a NEW pending order
            if (isNewAdded && status == "Pending" && (isInitialOrdersLoaded || timestamp > serviceStartTime)) {
                showOrderNotification(customer, productName, qty)
            }
        } catch (e: Exception) {
            Log.e("AdminService", "Error parsing order snapshot", e)
        }
    }

    private fun syncProduct(snapshot: DataSnapshot) {
        try {
            val id = snapshot.child("id").getValue(String::class.java) ?: ""
            if (id.isBlank()) return
            val name = snapshot.child("name").getValue(String::class.java) ?: ""
            val amharicName = snapshot.child("amharicName").getValue(String::class.java) ?: ""
            val category = snapshot.child("category").getValue(String::class.java) ?: "Uncategorized"
            val pricePerKg = (snapshot.child("pricePerKg").value as? Number)?.toDouble() ?: 0.0
            val stockKg = (snapshot.child("stockKg").value as? Number)?.toDouble() ?: 0.0
            val unitName = snapshot.child("unitName").getValue(String::class.java) ?: "kg"
            val description = snapshot.child("description").getValue(String::class.java) ?: ""
            val imageUrl = snapshot.child("imageUrl").getValue(String::class.java) ?: ""
            val nutritionalInfo = snapshot.child("nutritionalInfo").getValue(String::class.java) ?: "High quality local farm fresh product."

            val productObj = Product(
                id = id,
                name = name,
                amharicName = amharicName,
                category = category,
                pricePerKg = pricePerKg,
                stockKg = stockKg,
                unitName = unitName,
                description = description,
                imageUrl = imageUrl,
                nutritionalInfo = nutritionalInfo
            )

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AppDatabase.getDatabase(applicationContext).productDao().insertProduct(productObj)
                    Log.d("AdminService", "Synced product $id successfully to local DB")
                } catch (ex: Exception) {
                    Log.e("AdminService", "Error saving synced product locally", ex)
                }
            }
        } catch (e: Exception) {
            Log.e("AdminService", "Error parsing synced product snapshot", e)
        }
    }

    private fun syncNotificationsForUser(userSnapshot: DataSnapshot) {
        try {
            val list = mutableListOf<DbNotification>()
            for (child in userSnapshot.children) {
                try {
                    val id = child.child("id").getValue(Long::class.java) ?: 0L
                    val custUser = child.child("customerUsername").getValue(String::class.java) ?: ""
                    val orderId = child.child("orderId").getValue(Long::class.java) ?: 0L
                    val title = child.child("title").getValue(String::class.java) ?: ""
                    val titleAmharic = child.child("titleAmharic").getValue(String::class.java) ?: ""
                    val message = child.child("message").getValue(String::class.java) ?: ""
                    val messageAmharic = child.child("messageAmharic").getValue(String::class.java) ?: ""
                    val status = child.child("status").getValue(String::class.java) ?: ""
                    val isRead = child.child("isRead").getValue(Boolean::class.java) ?: false
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()

                    list.add(DbNotification(
                        id = id,
                        customerUsername = custUser,
                        orderId = orderId,
                        title = title,
                        titleAmharic = titleAmharic,
                        message = message,
                        messageAmharic = messageAmharic,
                        status = status,
                        isRead = isRead,
                        timestamp = timestamp
                    ))
                } catch (e: Exception) {}
            }
            if (list.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getDatabase(applicationContext)
                        for (notif in list) {
                            db.notificationDao().insertNotification(notif)
                        }
                        Log.d("AdminService", "Synced ${list.size} notification objects to local DB")
                    } catch (ex: Exception) {
                        Log.e("AdminService", "Error inserting notifications", ex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AdminService", "Error parsing notifications node", e)
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
            .setContentTitle("Real-time Database Sync Active")
            .setContentText("Continuously synchronizing local catalog & orders database in real-time...")
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
