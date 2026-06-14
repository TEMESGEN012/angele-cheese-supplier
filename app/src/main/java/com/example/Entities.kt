package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val username: String,
    val passwordHash: String, // Securely simulated SHA-256 password hash
    val fullName: String,
    val role: String, // "admin" or "customer"
    val phoneNumber: String = "",
    val profileIndex: Int = 0 // 0-5 to select avatar
)

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val id: String, // e.g. "butter", "cheese"
    val name: String,
    val amharicName: String,
    val category: String,
    val pricePerKg: Double, // Admin can set this price for 1 Unit/1kg
    val stockKg: Double, // Current available inventory levels in real-time
    val unitName: String = "kg", // base unit (normally kg, liters or piece)
    val description: String,
    val imageUrl: String = "", // Placeholders for rich graphics
    val nutritionalInfo: String = "High quality local farm fresh product."
)

@Entity(tableName = "orders")
data class Order(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerUsername: String,
    val productId: String,
    val productName: String,
    val amharicName: String,
    val quantityKg: Double,
    val totalPrice: Double,
    val status: String, // "Pending", "Confirmed", "Delivered"
    val deliveryAddress: String = "Standard Pickup Store",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "stock_logs")
data class StockLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: String,
    val productName: String,
    val changeAmount: Double, // e.g. -5.0 for order, +20.0 for admin restock
    val previousStock: Double,
    val newStock: Double,
    val reason: String, // "Order placed", "Admin Adjust Price/Stock", "Initial stock"
    val operator: String, // Admin or customer name
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "notifications")
data class Notification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerUsername: String,
    val orderId: Long,
    val title: String,
    val titleAmharic: String,
    val message: String,
    val messageAmharic: String,
    val status: String, // "Confirmed", "Delivered", "Cancelled"
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

