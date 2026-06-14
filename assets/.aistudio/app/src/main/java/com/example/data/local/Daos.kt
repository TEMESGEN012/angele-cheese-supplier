package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.User
import com.example.data.model.Product
import com.example.data.model.Order
import com.example.data.model.StockLog
import com.example.data.model.Notification
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: User)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Update
    suspend fun updateUser(user: User)

    @Query("DELETE FROM users WHERE username = :username")
    suspend fun deleteUserByUsername(username: String)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY category ASC, name ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: String): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<Product>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product)

    @Update
    suspend fun updateProduct(product: Product)

    @Query("UPDATE products SET pricePerKg = :price, stockKg = :stock WHERE id = :id")
    suspend fun updatePriceAndStock(id: String, price: Double, stock: Double)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProductById(id: String)
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    fun getAllOrders(): Flow<List<Order>>

    @Query("SELECT * FROM orders WHERE customerUsername = :username ORDER BY timestamp DESC")
    fun getOrdersByCustomer(username: String): Flow<List<Order>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: Order): Long

    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    suspend fun getOrderById(orderId: Long): Order?

    @Query("UPDATE orders SET status = :status WHERE id = :orderId")
    suspend fun updateOrderStatus(orderId: Long, status: String)

    @Query("DELETE FROM orders WHERE id = :orderId")
    suspend fun deleteOrderById(orderId: Long)
}

@Dao
interface StockLogDao {
    @Query("SELECT * FROM stock_logs ORDER BY timestamp DESC LIMIT 100")
    fun getAllLogs(): Flow<List<StockLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: StockLog)

    @Query("DELETE FROM stock_logs")
    suspend fun deleteAllLogs()
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE customerUsername = :username ORDER BY timestamp DESC")
    fun getNotificationsByCustomer(username: String): Flow<List<Notification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: Notification): Long

    @Query("UPDATE notifications SET isRead = 1 WHERE customerUsername = :username")
    suspend fun markAllAsRead(username: String)

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteNotification(id: Long)
}

