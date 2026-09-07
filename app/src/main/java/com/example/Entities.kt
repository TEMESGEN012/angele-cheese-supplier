package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "users")
data class User(
    @PrimaryKey 
    @Json(name = "username") val username: String,
    @Json(name = "password_hash") val passwordHash: String, // Securely simulated SHA-256 password hash
    @Json(name = "full_name") val fullName: String,
    @Json(name = "role") val role: String, // "admin" or "customer"
    @Json(name = "phone_number") val phoneNumber: String = "",
    @Json(name = "profile_index") val profileIndex: Int = 0 // 0-5 to select avatar
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "products")
data class Product(
    @PrimaryKey 
    @Json(name = "id") val id: String, // e.g. "butter", "cheese"
    @Json(name = "name") val name: String,
    @Json(name = "amharic_name") val amharicName: String = "",
    @Json(name = "category") val category: String = "Dairy (የወተት)",
    @Json(name = "price_per_kg") val pricePerKg: Double = 0.0, // Admin can set this price for 1 Unit/1kg
    @Json(name = "stock_kg") val stockKg: Double = 0.0, // Current available inventory levels in real-time
    @Json(name = "unit_name") val unitName: String = "kg", // base unit (normally kg, liters or piece)
    @Json(name = "description") val description: String = "",
    @Json(name = "image_url") val imageUrl: String = "", // Placeholders for rich graphics
    @Json(name = "nutritional_info") val nutritionalInfo: String = "High quality local farm fresh product."
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "orders")
data class Order(
    @PrimaryKey 
    @Json(name = "id") val id: Long = 0,
    @Json(name = "customer_username") val customerUsername: String,
    @Json(name = "product_id") val productId: String,
    @Json(name = "product_name") val productName: String,
    @Json(name = "amharic_name") val amharicName: String = "",
    @Json(name = "quantity_kg") val quantityKg: Double = 1.0,
    @Json(name = "total_price") val totalPrice: Double = 0.0,
    @Json(name = "status") val status: String = "Pending", // "Pending", "Confirmed", "Delivered"
    @Json(name = "delivery_address") val deliveryAddress: String = "Standard Pickup Store",
    @Json(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "stock_logs")
data class StockLog(
    @PrimaryKey 
    @Json(name = "id") val id: Long = 0,
    @Json(name = "product_id") val productId: String,
    @Json(name = "product_name") val productName: String,
    @Json(name = "change_amount") val changeAmount: Double = 0.0, // e.g. -5.0 for order, +20.0 for admin restock
    @Json(name = "previous_stock") val previousStock: Double = 0.0,
    @Json(name = "new_stock") val newStock: Double = 0.0,
    @Json(name = "reason") val reason: String = "", // "Order placed", "Admin Adjust Price/Stock", "Initial stock"
    @Json(name = "operator") val operator: String = "", // Admin or customer name
    @Json(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "notifications")
data class Notification(
    @PrimaryKey 
    @Json(name = "id") val id: Long = 0,
    @Json(name = "customer_username") val customerUsername: String,
    @Json(name = "order_id") val orderId: Long = 0,
    @Json(name = "title") val title: String,
    @Json(name = "title_amharic") val titleAmharic: String = "",
    @Json(name = "message") val message: String,
    @Json(name = "message_amharic") val messageAmharic: String = "",
    @Json(name = "status") val status: String = "Pending", // "Confirmed", "Delivered", "Cancelled"
    @Json(name = "is_read") val isRead: Boolean = false,
    @Json(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)


