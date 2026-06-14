package com.example.data.repository

import com.example.data.local.UserDao
import com.example.data.local.ProductDao
import com.example.data.local.OrderDao
import com.example.data.local.StockLogDao
import com.example.data.local.NotificationDao
import com.example.data.model.User
import com.example.data.model.Product
import com.example.data.model.Order
import com.example.data.model.StockLog
import com.example.data.model.Notification
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DairyRepository(
    private val userDao: UserDao,
    private val productDao: ProductDao,
    private val orderDao: OrderDao,
    private val stockLogDao: StockLogDao,
    private val notificationDao: NotificationDao
) {
    val allProducts: Flow<List<Product>> = productDao.getAllProducts()
    val allOrders: Flow<List<Order>> = orderDao.getAllOrders()
    val allStockLogs: Flow<List<StockLog>> = stockLogDao.getAllLogs()

    fun getOrdersByCustomer(username: String): Flow<List<Order>> {
        return orderDao.getOrdersByCustomer(username)
    }

    fun getNotificationsByCustomer(username: String): Flow<List<Notification>> {
        return notificationDao.getNotificationsByCustomer(username)
    }

    suspend fun markAllNotificationsAsRead(username: String) = withContext(Dispatchers.IO) {
        notificationDao.markAllAsRead(username)
    }

    suspend fun deleteNotification(id: Long) = withContext(Dispatchers.IO) {
        notificationDao.deleteNotification(id)
    }


    /**
     * Checks if database is empty and populates initial 10 products and default accounts if so.
     */
    suspend fun prePopulateIfNeeded() = withContext(Dispatchers.IO) {
        // Defined standard base products catalog
        val initialProducts = listOf(
            Product(
                id = "butter",
                name = "Butter",
                amharicName = "ቅቤ",
                category = "Dairy (የወተት)",
                pricePerKg = 950.0,
                stockKg = 45.0,
                unitName = "kg",
                description = "Traditional Ethiopian spiced butter (Niter Kibbeh) made from pure cream and seasoned with native highland herbs. Essential for authentic stews.",
                nutritionalInfo = "Fat: 81g, Protein: 1g, Calories: 717 kcal per 100g. 100% natural, no additives."
            ),
            Product(
                id = "buttermilk",
                name = "Buttermilk",
                amharicName = "አረራ",
                category = "Dairy (የወተት)",
                pricePerKg = 150.0,
                stockKg = 35.0,
                unitName = "Liter",
                description = "Refreshing traditional low-fat fermented buttermilk (Arera) with a slightly sour, tangy taste. Perfect alongside savory foods.",
                nutritionalInfo = "Protein: 3.3g, Fat: 0.9g, Calcium: 116mg per 100ml. Probiotic rich."
            ),
            Product(
                id = "cheese",
                name = "Cheese",
                amharicName = "አይብ",
                category = "Dairy (የወተት)",
                pricePerKg = 420.0,
                stockKg = 40.0,
                unitName = "kg",
                description = "Soft, crumbly, and mild Ethiopian cottage cheese (Ayib). Traditionally paired with minced collard greens or Kitfo to temper heat.",
                nutritionalInfo = "Protein: 11g, Calcium: 83mg, Calories: 98 kcal per 100g."
            ),
            Product(
                id = "milk",
                name = "Milk",
                amharicName = "ወተት",
                category = "Dairy (የወተት)",
                pricePerKg = 110.0,
                stockKg = 120.0,
                unitName = "Liter",
                description = "Pure, fresh pasteurized whole cow's milk delivered daily from local family cooperatives in the high pastures.",
                nutritionalInfo = "Protein: 3.2g, Vitamin D: 42 IU, Calcium: 125mg per 100ml."
            ),
            Product(
                id = "yogurt",
                name = "Yogurt",
                amharicName = "እርጎ",
                category = "Dairy (የወተት)",
                pricePerKg = 240.0,
                stockKg = 50.0,
                unitName = "Liter",
                description = "Thick, creamy, and mildly tart natural fermented yogurt (Ergo). Handcrafted using traditional clay vessels.",
                nutritionalInfo = "Active cultures, Protein: 4.5g, Calcium: 150mg per 100g."
            ),
            Product(
                id = "egg",
                name = "Eggs",
                amharicName = "እንቁላል",
                category = "Farm Produce (ምርት)",
                pricePerKg = 280.0, // 1 pack of 12 represents 1 unit/kg equivalent
                stockKg = 80.0,
                unitName = "Pack",
                description = "Fresh brown eggs sourced directly from organic, cage-free pasture chickens. Rich and deep-colored golden yolks.",
                nutritionalInfo = "Protein: 13g, Iron: 1.2mg, Calories: 143 kcal per 100g."
            ),
            Product(
                id = "honey",
                name = "Honey",
                amharicName = "ማር",
                category = "Natural Sweetener",
                pricePerKg = 780.0,
                stockKg = 30.0,
                unitName = "kg",
                description = "100% raw high-altitude amber forest honey. Sourced ethically from wild hives in Bale and Kaffa biosphere zones.",
                nutritionalInfo = "Natural sugars (fructose & glucose), antioxidant dense, trace minerals. High digestive enzymes."
            ),
            Product(
                id = "enjera",
                name = "Enjera",
                amharicName = "እንጀራ",
                category = "Staple (ዋና ምግብ)",
                pricePerKg = 220.0, // 1 pack of 5 large rolls
                stockKg = 150.0,
                unitName = "Pack",
                description = "Soft, spongy, multi-eyed sourdough flatbread printed from premium organic red and white Teff grains. 3-day fermented.",
                nutritionalInfo = "Fiber: 8g, Iron: 14mg (extremely high), Gluten-Free, Vegan-friendly."
            ),
            Product(
                id = "shiro",
                name = "Shiro",
                amharicName = "ሽሮ",
                category = "Spices & Spiciness",
                pricePerKg = 380.0,
                stockKg = 90.0,
                unitName = "kg",
                description = "Grounded roasted chickpeas and field peas combined with cardamom, coriander, and garlic. Simple instruction: boil with oil/butter and water.",
                nutritionalInfo = "Extremely high in vegan protein (20g per 100g), high fiber and complex carbohydrates."
            )
        )

        val userCount = userDao.getUserCount()
        if (userCount == 0) {
            // Add default super admin
            userDao.insertUser(
                User(
                    username = "anteneh",
                    passwordHash = hashPassword("Ante,098"),
                    fullName = "Anteneh (Super Admin)",
                    role = "admin",
                    phoneNumber = "",
                    profileIndex = 2
                )
            )

            productDao.insertProducts(initialProducts)

            // Insert initial logs
            initialProducts.forEach { prod ->
                stockLogDao.insertLog(
                    StockLog(
                        productId = prod.id,
                        productName = prod.name,
                        changeAmount = prod.stockKg,
                        previousStock = 0.0,
                        newStock = prod.stockKg,
                        reason = "Initialize system store catalog",
                        operator = "System Auto-Population"
                    )
                )
            }
        }

        // Keep database text details (name, amharicName, category, description, unitName, nutritionalInfo)
        // perfectly matched with definitions, to easily support hot reloads or database renaming,
        // while safely keeping dynamic stock and custom price choices unmodified.
        initialProducts.forEach { definedProd ->
            val existing = productDao.getProductById(definedProd.id)
            if (existing != null) {
                if (existing.name != definedProd.name ||
                    existing.amharicName != definedProd.amharicName ||
                    existing.category != definedProd.category ||
                    existing.description != definedProd.description ||
                    existing.unitName != definedProd.unitName ||
                    existing.nutritionalInfo != definedProd.nutritionalInfo
                ) {
                    val updated = existing.copy(
                        name = definedProd.name,
                        amharicName = definedProd.amharicName,
                        category = definedProd.category,
                        description = definedProd.description,
                        unitName = definedProd.unitName,
                        nutritionalInfo = definedProd.nutritionalInfo
                    )
                    productDao.updateProduct(updated)
                }
            } else {
                productDao.insertProduct(definedProd)
            }
        }
    }

    /**
     * Authenticates user securely using SHA-256 password hash comparison.
     */
    suspend fun login(username: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        val trimmed = username.trim().lowercase()
        val user = userDao.getUserByUsername(trimmed)
            ?: return@withContext Result.failure(Exception("Username not found."))

        val inputHash = hashPassword(password)
        if (user.passwordHash == inputHash) {
            Result.success(user)
        } else {
            Result.failure(Exception("Incorrect password."))
        }
    }

    /**
     * Registers a new user. Holds constraints on unique username.
     */
    suspend fun register(
        username: String,
        passwordHashInput: String,
        fullName: String,
        role: String,
        phoneNumber: String,
        profileIndex: Int
    ): Result<User> = withContext(Dispatchers.IO) {
        val trimmedUsername = username.trim().lowercase()
        if (trimmedUsername.isEmpty() || passwordHashInput.isEmpty()) {
            return@withContext Result.failure(Exception("Username and password cannot be empty."))
        }

        if (role.lowercase() == "admin") {
            val adminCount = userDao.getUserCountByRole("admin")
            if (adminCount >= 1) {
                return@withContext Result.failure(Exception("An admin already exists. Only one admin is allowed."))
            }
        }

        val existing = userDao.getUserByUsername(trimmedUsername)
        if (existing != null) {
            return@withContext Result.failure(Exception("Username already in use."))
        }

        val newUser = User(
            username = trimmedUsername,
            passwordHash = hashPassword(passwordHashInput),
            fullName = fullName,
            role = role,
            phoneNumber = phoneNumber,
            profileIndex = profileIndex
        )
        try {
            userDao.insertUser(newUser)
            Result.success(newUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Admin modifies product price and real-time stock levels. Real-time updates push stock logs.
     */
    suspend fun updateProduct(
        productId: String,
        newPrice: Double,
        newStock: Double,
        operatorName: String
    ): Result<Product> = withContext(Dispatchers.IO) {
        val product = productDao.getProductById(productId)
            ?: return@withContext Result.failure(Exception("Product not found."))

        val stockDifference = newStock - product.stockKg

        // Update database
        productDao.updatePriceAndStock(productId, newPrice, newStock)

        // Log if stock changed or price changed
        if (stockDifference != 0.0 || newPrice != product.pricePerKg) {
            val logReason = buildString {
                if (stockDifference != 0.0) append("Stock adjusted from ${product.stockKg} to $newStock (Change: ${String.format("%.1f", stockDifference)}). ")
                if (newPrice != product.pricePerKg) append("Price adjusted from ${product.pricePerKg} to $newPrice ETB.")
            }
            stockLogDao.insertLog(
                StockLog(
                    productId = productId,
                    productName = product.name,
                    changeAmount = stockDifference,
                    previousStock = product.stockKg,
                    newStock = newStock,
                    reason = logReason,
                    operator = operatorName
                )
            )
        }

        val updatedProduct = product.copy(pricePerKg = newPrice, stockKg = newStock)
        Result.success(updatedProduct)
    }

    /**
     * Customer orders products. Decrements from available stock inside synchronised logic or Room updates.
     */
    suspend fun placeOrder(
        customerUsername: String,
        productId: String,
        quantity: Double,
        operatorName: String,
        deliveryAddress: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (quantity <= 0.0) {
            return@withContext Result.failure(Exception("Quantity must be greater than zero."))
        }

        val product = productDao.getProductById(productId)
            ?: return@withContext Result.failure(Exception("Product not found."))

        if (product.stockKg < quantity) {
            return@withContext Result.failure(Exception("Insufficient stock. Only ${product.stockKg} ${product.unitName} left."))
        }

        val newStock = product.stockKg - quantity
        val totalPrice = product.pricePerKg * quantity

        // Deduct from catalog
        productDao.updatePriceAndStock(productId, product.pricePerKg, newStock)

        // Record the transaction order
        val order = Order(
            customerUsername = customerUsername,
            productId = productId,
            productName = product.name,
            amharicName = product.amharicName,
            quantityKg = quantity,
            totalPrice = totalPrice,
            status = "Pending",
            deliveryAddress = deliveryAddress
        )
        val orderId = orderDao.insertOrder(order)

        // Insert notification for admin about a new order coming ("order arrived")
        try {
            notificationDao.insertNotification(
                Notification(
                    customerUsername = "admin",
                    orderId = orderId,
                    title = "New Order Arrived!",
                    titleAmharic = "አዲስ ትዕዛዝ ደርሷል!",
                    message = "Order #ORD-$orderId was placed by $customerUsername for $quantity ${getUnitPlural(product.name, quantity)} of ${product.name}.",
                    messageAmharic = "ትዕዛዝ ቁጥር #ORD-$orderId በ $customerUsername ተመዝግቧል (${quantity} ${product.amharicName})።",
                    status = "Pending",
                    isRead = false,
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {}

        // Log transaction movement
        stockLogDao.insertLog(
            StockLog(
                productId = productId,
                productName = product.name,
                changeAmount = -quantity,
                previousStock = product.stockKg,
                newStock = newStock,
                reason = "Order placed #ORD-${orderId}",
                operator = operatorName
            )
        )

        Result.success(orderId)
    }

    /**
     * Admin updates delivery/payment/processing state of customer orders.
     */
    suspend fun updateOrderStatus(
        orderId: Long,
        newStatus: String
    ): Boolean = withContext(Dispatchers.IO) {
        val order = orderDao.getOrderById(orderId)
        orderDao.updateOrderStatus(orderId, newStatus)
        if (order != null) {
            // Setup title & message based on status
            val titleEN = when (newStatus) {
                "Confirmed" -> "Order Confirmed!"
                "Delivered" -> "Order Delivered!"
                "Cancelled" -> "Order Cancelled"
                else -> "Order Updated"
            }
            val titleAM = when (newStatus) {
                "Confirmed" -> "ትዕዛዝዎ ተረጋግጧል!"
                "Delivered" -> "ትዕዛዝዎ ደርሷል!"
                "Cancelled" -> "ትዕዛዝዎ ተሰርዟል"
                else -> "የትዕዛዝ መረጃ"
            }
            val msgEN = when (newStatus) {
                "Confirmed" -> "Your order #ORD-${order.id} for ${order.quantityKg} ${getUnitPlural(order.productName, order.quantityKg)} of ${order.productName} is being processed."
                "Delivered" -> "Your order #ORD-${order.id} for ${order.quantityKg} ${getUnitPlural(order.productName, order.quantityKg)} of ${order.productName} was successfully delivered. Thank you!"
                "Cancelled" -> "Your order #ORD-${order.id} for ${order.productName} was cancelled."
                else -> "Your order #ORD-${order.id} status was updated to $newStatus."
            }
            val msgAM = when (newStatus) {
                "Confirmed" -> "ትዕዛዝ ቁጥር #ORD-${order.id} ለ ${order.quantityKg} እቃ ${order.amharicName} በመዘጋጀት ላይ ይገኛል።"
                "Delivered" -> "ትዕዛዝ ቁጥር #ORD-${order.id} ለ ${order.quantityKg} እቃ ${order.amharicName} በተሳካ ሁኔታ ደርሷል። እናመሰግናለን!"
                "Cancelled" -> "ትዕዛዝ ቁጥር #ORD-${order.id} ለ ${order.amharicName} በሱቁ ተሰርዟል።"
                else -> "ትዕዛዝ ቁጥር #ORD-${order.id} ሁኔታ ወደ $newStatus ተቀይሯል።"
            }
            notificationDao.insertNotification(
                Notification(
                    customerUsername = order.customerUsername,
                    orderId = orderId,
                    title = titleEN,
                    titleAmharic = titleAM,
                    message = msgEN,
                    messageAmharic = msgAM,
                    status = newStatus,
                    isRead = false,
                    timestamp = System.currentTimeMillis()
                )
            )

            // Insert admin notification as well (order status changed/done)
            try {
                notificationDao.insertNotification(
                    Notification(
                        customerUsername = "admin",
                        orderId = orderId,
                        title = "Order Status Changed: $newStatus",
                        titleAmharic = "የትዕዛዝ ሁኔታ ተቀይሯል: $newStatus",
                        message = "Order #ORD-${order.id} is now $newStatus (updated by admin).",
                        messageAmharic = "ትዕዛዝ ቁጥር #ORD-${order.id} አሁን $newStatus ሆኗል።",
                        status = newStatus,
                        isRead = false,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {}
        }
        true
    }

    private fun getUnitPlural(productName: String, qty: Double): String {
        return if (productName.lowercase().contains("milk") || productName.lowercase().contains("yogurt")) {
            if (qty == 1.0) "liter" else "liters"
        } else {
            if (qty == 1.0) "kg" else "kgs"
        }
    }


    /**
     * Admin adds a new product to the catalog.
     */
    suspend fun addProduct(
        product: Product,
        operatorName: String
    ): Result<Product> = withContext(Dispatchers.IO) {
        val existing = productDao.getProductById(product.id)
        if (existing != null) {
            return@withContext Result.failure(Exception("Product ID '${product.id}' already exists."))
        }
        try {
            productDao.insertProduct(product)
            stockLogDao.insertLog(
                StockLog(
                    productId = product.id,
                    productName = product.name,
                    changeAmount = product.stockKg,
                    previousStock = 0.0,
                    newStock = product.stockKg,
                    reason = "Added new product: ${product.name} (${product.amharicName})",
                    operator = operatorName
                )
            )
            Result.success(product)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Admin modifies full product details (name, amharicName, price, stock, category, etc.).
     */
    suspend fun editProduct(
        product: Product,
        operatorName: String
    ): Result<Product> = withContext(Dispatchers.IO) {
        val existing = productDao.getProductById(product.id)
            ?: return@withContext Result.failure(Exception("Product not found."))
        try {
            productDao.updateProduct(product)
            val stockDiff = product.stockKg - existing.stockKg
            if (stockDiff != 0.0 || product.pricePerKg != existing.pricePerKg || product.name != existing.name || product.amharicName != existing.amharicName) {
                stockLogDao.insertLog(
                    StockLog(
                        productId = product.id,
                        productName = product.name,
                        changeAmount = stockDiff,
                        previousStock = existing.stockKg,
                        newStock = product.stockKg,
                        reason = "Product details edited by admin (Name: ${product.name}, Price: ${product.pricePerKg} ETB)",
                        operator = operatorName
                    )
                )
            }
            Result.success(product)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Admin deletes a product from the database.
     */
    suspend fun deleteProduct(
        productId: String,
        operatorName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val existing = productDao.getProductById(productId)
            ?: return@withContext Result.failure(Exception("Product not found."))
        try {
            productDao.deleteProductById(productId)
            stockLogDao.insertLog(
                StockLog(
                    productId = productId,
                    productName = existing.name,
                    changeAmount = -existing.stockKg,
                    previousStock = existing.stockKg,
                    newStock = 0.0,
                    reason = "Product deleted from catalog",
                    operator = operatorName
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Admin deletes an order permanently.
     */
    suspend fun deleteOrder(orderId: Long): Boolean = withContext(Dispatchers.IO) {
        orderDao.deleteOrderById(orderId)
        true
    }

    /**
     * Admin clears all stock logs from the system.
     */
    suspend fun clearAllStockLogs(): Boolean = withContext(Dispatchers.IO) {
        stockLogDao.deleteAllLogs()
        true
    }

    /**
     * Quick stock level adjustment (+ / -) by admin.
     */
    suspend fun quickAdjustProductStock(
        productId: String,
        delta: Double,
        operatorName: String
    ): Result<Product> = withContext(Dispatchers.IO) {
        val existing = productDao.getProductById(productId)
            ?: return@withContext Result.failure(Exception("Product not found."))
        
        val newStock = (existing.stockKg + delta).coerceAtLeast(0.0)
        val updated = existing.copy(stockKg = newStock)
        try {
            productDao.updateProduct(updated)
            stockLogDao.insertLog(
                StockLog(
                    productId = productId,
                    productName = existing.name,
                    changeAmount = delta,
                    previousStock = existing.stockKg,
                    newStock = newStock,
                    reason = "Quick inventory adjustment: ${if (delta >= 0) "Added" else "Removed"} ${String.format("%.1f", Math.abs(delta))} ${existing.unitName}",
                    operator = operatorName
                )
            )
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates an admin's or user's username and/or password.
     * Since username is the primary key, if username is changed, we insert a new record and delete the old one.
     */
    suspend fun updateAdminCredentials(
        oldUsername: String,
        newUsername: String,
        newPasswordPlain: String?
    ): Result<User> = withContext(Dispatchers.IO) {
        val trimmedNew = newUsername.trim().lowercase()
        if (trimmedNew.isEmpty()) {
            return@withContext Result.failure(Exception("Username cannot be empty."))
        }
        
        val existing = userDao.getUserByUsername(oldUsername)
            ?: return@withContext Result.failure(Exception("Active profile not found."))

        // If username is changing, verify the new one isn't already taken
        if (oldUsername != trimmedNew) {
            val taken = userDao.getUserByUsername(trimmedNew)
            if (taken != null) {
                return@withContext Result.failure(Exception("The username '$trimmedNew' is already taken."))
            }
        }

        val updatedPasswordHash = if (!newPasswordPlain.isNullOrBlank()) {
            hashPassword(newPasswordPlain)
        } else {
            existing.passwordHash
        }

        val updatedUser = existing.copy(
            username = trimmedNew,
            passwordHash = updatedPasswordHash
        )

        try {
            if (oldUsername != trimmedNew) {
                // Since primary key changed, we insert new first then delete old
                userDao.insertUser(updatedUser)
                userDao.deleteUserByUsername(oldUsername)
            } else {
                userDao.updateUser(updatedUser)
            }
            Result.success(updatedUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun insertProductsFromApi(products: List<Product>) {
        productDao.insertProducts(products)
    }

    suspend fun insertOrdersFromApi(orders: List<Order>) {
        orders.forEach { order ->
            orderDao.insertOrder(order)
        }
    }

    suspend fun insertNotificationsFromApi(notifications: List<Notification>) {
        notifications.forEach { notification ->
            notificationDao.insertNotification(notification)
        }
    }

    suspend fun replaceNotificationsFromApi(notifications: List<Notification>) = withContext(Dispatchers.IO) {
        notificationDao.deleteAllNotifications()
        notifications.forEach { notificationDao.insertNotification(it) }
    }

    suspend fun addProductSilently(product: Product) {
        productDao.insertProduct(product)
    }

    /**
     * Helper to compute SHA-256 hash.
     */
    private fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
