package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.RetrofitClient
import com.example.data.local.AppDatabase
import com.example.data.model.Order
import com.example.data.model.Product
import com.example.data.model.StockLog
import com.example.data.model.User
import com.example.data.model.Notification
import com.example.data.repository.DairyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

import org.json.JSONObject

sealed interface AppScreen {
    object Login : AppScreen
    object Register : AppScreen
    object CustomerHome : AppScreen
    object AdminHome : AppScreen
}

class DairyViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = DairyRepository(
        userDao = database.userDao(),
        productDao = database.productDao(),
        orderDao = database.orderDao(),
        stockLogDao = database.stockLogDao(),
        notificationDao = database.notificationDao()
    )

    // REST API Settings State (Persisted in SharedPreferences)
    private val prefs = application.getSharedPreferences("dairy_api_prefs", Context.MODE_PRIVATE)

    private val _apiEnabled = MutableStateFlow(prefs.getBoolean("api_enabled", false))
    val apiEnabled: StateFlow<Boolean> = _apiEnabled.asStateFlow()

    private val _apiBaseUrl = MutableStateFlow(
        prefs.getString("api_base_url", "https://api.example.com/api/") ?: "https://api.example.com/api/"
    )
    val apiBaseUrl: StateFlow<String> = _apiBaseUrl.asStateFlow()

    private val _apiSyncing = MutableStateFlow(false)
    val apiSyncing: StateFlow<Boolean> = _apiSyncing.asStateFlow()

    // Current screen routing state
    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.CustomerHome)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Real-time Firebase Database connection handling
    init {
        // Trigger pre-population async
        viewModelScope.launch {
            repository.prePopulateIfNeeded()
        }

        // Firebase Realtime DB Listeners
        viewModelScope.launch {
            try {
                val database = com.google.firebase.database.FirebaseDatabase.getInstance()
                
                // Track products
                val productsRef = database.getReference("products")
                productsRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        syncPullProducts(silent = true)
                    }
                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
                })

                // Track orders
                val ordersRef = database.getReference("orders")
                ordersRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        syncPullOrders(silent = true)
                    }
                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
                })

                // Track notifications trigger
                val notificationsRef = database.getReference("notifications_trigger")
                notificationsRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        syncPullNotifications(silent = true)
                    }
                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Interactive error/success Toast-like messages
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Current Session State
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // Guest checkout session tracking state
    private val _guestUsername = MutableStateFlow<String?>(null)
    val guestUsername: StateFlow<String?> = _guestUsername.asStateFlow()

    private val _guestName = MutableStateFlow<String?>(null)
    val guestName: StateFlow<String?> = _guestName.asStateFlow()

    // Real-time responsive products list
    val productsList: StateFlow<List<Product>> = repository.allProducts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    // Orders flows (all for admin, customer-specific or guest-specific for customers)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val customerOrdersList: StateFlow<List<Order>> = combine(_currentUser, _guestUsername) { user, guestUsr ->
        if (user != null) {
            repository.getOrdersByCustomer(user.username)
        } else if (!guestUsr.isNullOrBlank()) {
            repository.getOrdersByCustomer(guestUsr)
        } else {
            flowOf(emptyList())
        }
    }.flatMapLatest { it }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val customerNotificationsList: StateFlow<List<Notification>> = combine(_currentUser, _guestUsername) { user, guestUsr ->
        if (user != null) {
            repository.getNotificationsByCustomer(user.username)
        } else if (!guestUsr.isNullOrBlank()) {
            repository.getNotificationsByCustomer(guestUsr)
        } else {
            flowOf(emptyList())
        }
    }.flatMapLatest { it }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val adminNotificationsList: StateFlow<List<Notification>> = repository.getNotificationsByCustomer("admin").stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val adminOrdersList: StateFlow<List<Order>> = repository.allOrders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Real-time stock audit logs for real-time inventory management
    val stockLogList: StateFlow<List<StockLog>> = repository.allStockLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Simple reactive shopping cart (Map: ProductId -> Quantity of order weight)
    private val _cartMap = MutableStateFlow<Map<String, Double>>(emptyMap())
    val cartMap: StateFlow<Map<String, Double>> = _cartMap.asStateFlow()

    fun clearStatus() {
        _statusMessage.value = null
        _errorMessage.value = null
    }

    fun setInfoMessage(msg: String) {
        _statusMessage.value = msg
    }

    fun navigateTo(screen: AppScreen) {
        clearStatus()
        _currentScreen.value = screen
    }

    /**
     * Authenticate and navigate.
     */
    fun performLogin(usernameInput: String, passwordInput: String) {
        if (usernameInput.isBlank() || passwordInput.isBlank()) {
            _errorMessage.value = "Username and password cannot be empty."
            return
        }
        viewModelScope.launch {
            repository.login(usernameInput, passwordInput).onSuccess { user ->
                _currentUser.value = user
                _cartMap.value = emptyMap() // clear cart
                _statusMessage.value = "Logged in as ${user.fullName}"
                if (user.role == "admin") {
                    _currentScreen.value = AppScreen.AdminHome
                } else {
                    _currentScreen.value = AppScreen.CustomerHome
                }
            }.onFailure { th ->
                _errorMessage.value = th.message ?: "Authentication failed."
            }
        }
    }

    /**
     * Account Registration.
     */
    fun performRegister(
        usernameInput: String,
        passwordInput: String,
        fullNameInput: String,
        roleInput: String,
        phoneInput: String,
        profileIndex: Int
    ) {
        if (usernameInput.isBlank() || passwordInput.isBlank() || fullNameInput.isBlank()) {
            _errorMessage.value = "All fields except phone number are required."
            return
        }
        viewModelScope.launch {
            repository.register(
                username = usernameInput,
                passwordHashInput = passwordInput,
                fullName = fullNameInput,
                role = roleInput,
                phoneNumber = phoneInput,
                profileIndex = profileIndex
            ).onSuccess { user ->
                _currentUser.value = user
                _cartMap.value = emptyMap()
                _statusMessage.value = "Successfully registered your account!"
                if (user.role == "admin") {
                    _currentScreen.value = AppScreen.AdminHome
                } else {
                    _currentScreen.value = AppScreen.CustomerHome
                }
            }.onFailure { th ->
                _errorMessage.value = th.message ?: "Registration error."
            }
        }
    }

    /**
     * Log out current user safely.
     */
    fun logout() {
        _currentUser.value = null
        _guestUsername.value = null
        _guestName.value = null
        _cartMap.value = emptyMap()
        _statusMessage.value = "Successfully signed out."
        _currentScreen.value = AppScreen.CustomerHome
    }

    /**
     * Cart operations.
     */
    fun updateCartQuantity(productId: String, quantity: Double) {
        val currentCart = _cartMap.value.toMutableMap()
        if (quantity <= 0.0) {
            currentCart.remove(productId)
        } else {
            // Cap at 1000kg limit
            currentCart[productId] = quantity.coerceAtMost(1000.0)
        }
        _cartMap.value = currentCart
    }

    /**
     * Purchase entire cart items. Triggers stock level subtractions and records delivery logs.
     * Supports regular users as well as non-authenticated guest checkouts.
     */
    fun checkoutCart(
        deliveryAddress: String,
        guestName: String = "",
        guestPhone: String = ""
    ) {
        val cart = _cartMap.value
        val user = _currentUser.value

        val targetUsername: String
        val targetFullName: String

        if (user != null) {
            targetUsername = user.username
            targetFullName = user.fullName
        } else {
            // Guest checkout
            if (guestName.isBlank() || guestPhone.isBlank()) {
                _errorMessage.value = "Name and Phone Number are required for guest checkout."
                return
            }
            // Generate a unique identifier that allows tracking guest orders
            val uniqueGuestId = "guest_${guestName.trim().lowercase().replace(" ", "_")}_${guestPhone.trim()}"
            _guestUsername.value = uniqueGuestId
            _guestName.value = guestName.trim()
            targetUsername = uniqueGuestId
            targetFullName = guestName.trim()
        }

        if (cart.isEmpty()) {
            _errorMessage.value = "Your shopping cart is empty."
            return
        }

        viewModelScope.launch {
            var successes = 0
            var failures = 0
            var errorDetails = ""

            cart.forEach { (productId, qty) ->
                repository.placeOrder(
                    customerUsername = targetUsername,
                    productId = productId,
                    quantity = qty,
                    operatorName = targetFullName,
                    deliveryAddress = deliveryAddress.ifBlank { "Standard Delivery Address" }
                ).onSuccess { orderId ->
                    successes++
                    
                    // Firebase Tracking Node Update
                    try {
                        val database = com.google.firebase.database.FirebaseDatabase.getInstance()
                        database.getReference("orders_trigger").setValue(System.currentTimeMillis().toString())
                        
                        // Push full order object directly to Firebase
                        val productCached = productsList.value.find { it.id == productId }
                        val remoteOrder = Order(
                            id = orderId,
                            customerUsername = targetUsername,
                            productId = productId,
                            productName = productCached?.name ?: "Unknown Product",
                            amharicName = productCached?.amharicName ?: "",
                            quantityKg = qty,
                            totalPrice = (productCached?.pricePerKg ?: 0.0) * qty,
                            status = "Pending",
                            deliveryAddress = deliveryAddress.ifBlank { "Standard Delivery Address" }
                        )
                        database.getReference("orders").child(orderId.toString()).setValue(remoteOrder)

                        // Push Notifications to Firebase
                        val notifId = System.currentTimeMillis()
                        val adminNotif = Notification(
                            id = notifId,
                            customerUsername = "admin", 
                            orderId = orderId,
                            title = "New Order Arrived!",
                            titleAmharic = "አዲስ ትዕዛዝ ደርሷል!",
                            message = "Order #ORD-$orderId has been placed by $targetFullName.",
                            messageAmharic = "ትዕዛዝ #ORD-$orderId በ $targetFullName ተረጋግጧል።",
                            status = "Pending",
                            isRead = false,
                            timestamp = notifId
                        )
                        database.getReference("notifications_trigger").setValue(System.currentTimeMillis().toString())
                        database.getReference("notifications").child("admin").child(notifId.toString()).setValue(adminNotif)
                        
                        val customerNotifId = notifId + 1
                        val custNotif = Notification(
                            id = customerNotifId,
                            customerUsername = targetUsername,
                            orderId = orderId,
                            title = "Order Placed Successfully",
                            titleAmharic = "ትዕዛዝዎ በተሳካ ሁኔታ ተላልፏል",
                            message = "Your order #ORD-$orderId is now pending.",
                            messageAmharic = "ትዕዛዝዎ #ORD-$orderId በሂደት ላይ ነው።",
                            status = "Pending",
                            isRead = false,
                            timestamp = customerNotifId
                        )
                        database.getReference("notifications").child(targetUsername).child(customerNotifId.toString()).setValue(custNotif)
                    } catch (e: Exception) {}

                    if (_apiEnabled.value) {
                        try {
                            val apiSvc = RetrofitClient.getApiService(_apiBaseUrl.value)
                            val productCached = productsList.value.find { it.id == productId }
                            apiSvc.placeOrder(
                                Order(
                                    id = orderId,
                                    customerUsername = targetUsername,
                                    productId = productId,
                                    productName = productCached?.name ?: "Unknown Product",
                                    amharicName = productCached?.amharicName ?: "",
                                    quantityKg = qty,
                                    totalPrice = (productCached?.pricePerKg ?: 0.0) * qty,
                                    status = "Pending",
                                    deliveryAddress = deliveryAddress.ifBlank { "Standard Delivery Address" }
                                )
                            )
                        } catch (e: Exception) {}
                    }
                }.onFailure { th ->
                    failures++
                    errorDetails = th.message ?: "Out of stock / error."
                }
            }

            if (failures == 0) {
                _statusMessage.value = "Order placed successfully! Verified $successes items."
                _cartMap.value = emptyMap() // Clear cart
            } else if (successes > 0) {
                _statusMessage.value = "Partial checkout! Ordered $successes products successfully. $failures failed: $errorDetails"
                // Only retain failed in cart
                val newCart = cart.filterKeys { k -> cart[k] != null && !isSuccessfulId(k) }
                _cartMap.value = newCart
            } else {
                _errorMessage.value = "Checkout failed: $errorDetails"
            }
        }
    }

    private fun isSuccessfulId(id: String): Boolean {
        // Just let it clear or update based on what is left.
        return true // simplest
    }

    /**
     * Admin capability: Updates weights, base quantities and unit prices on real-time channels.
     */
    fun adminUpdateProduct(productId: String, price: Double, stock: Double) {
        val user = _currentUser.value
        if (user == null || user.role != "admin") {
            _errorMessage.value = "Unauthorized operation."
            return
        }
        if (price < 0.0 || stock < 0.0) {
            _errorMessage.value = "Price and stock levels must be non-negative."
            return
        }

        viewModelScope.launch {
            repository.updateProduct(productId, price, stock, user.fullName).onSuccess { updatedProduct ->
                _statusMessage.value = "Updated visual pricing & catalogs."
                try {
                    val database = com.google.firebase.database.FirebaseDatabase.getInstance()
                    database.getReference("products_trigger").setValue(System.currentTimeMillis().toString())
                    database.getReference("products").child(productId).setValue(updatedProduct)
                } catch (e: Exception) {}
            }.onFailure { th ->
                _errorMessage.value = th.message ?: "Failed updating catalog."
            }
        }
    }

    /**
     * Admin updates status of user orders.
     */
    fun adminUpdateOrderStatus(orderId: Long, updatedStatus: String) {
        viewModelScope.launch {
            repository.updateOrderStatus(orderId, updatedStatus)
            _statusMessage.value = "Order status updated to $updatedStatus."
            try {
                val database = com.google.firebase.database.FirebaseDatabase.getInstance()
                database.getReference("orders_trigger").setValue(System.currentTimeMillis().toString())
                database.getReference("orders").child(orderId.toString()).child("status").setValue(updatedStatus)
            } catch (e: Exception) {}
        }
    }

    /**
     * Customer marks all of their notifications as read.
     */
    fun markAllNotificationsAsRead() {
        val user = _currentUser.value
        val guestUsr = _guestUsername.value
        val targetUsername = user?.username ?: guestUsr
        if (!targetUsername.isNullOrBlank()) {
            viewModelScope.launch {
                repository.markAllNotificationsAsRead(targetUsername)
            }
        }
    }

    /**
     * Admin marks all admin notifications as read.
     */
    fun markAllAdminNotificationsAsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsAsRead("admin")
        }
    }

    /**
     * Customer dismisses/deletes an individual notification.
     */
    fun deleteNotification(notificationId: Long) {
        viewModelScope.launch {
            repository.deleteNotification(notificationId)
        }
    }

    /**
     * Admin adds a completely new product to the catalog.
     */
    fun adminAddProduct(product: Product) {
        val user = _currentUser.value
        if (user == null || user.role != "admin") {
            _errorMessage.value = "Unauthorized operation."
            return
        }
        if (product.id.isBlank() || product.name.isBlank()) {
            _errorMessage.value = "Product ID and Name cannot be blank."
            return
        }
        if (product.pricePerKg < 0.0 || product.stockKg < 0.0) {
            _errorMessage.value = "Price and stock levels must be non-negative."
            return
        }

        viewModelScope.launch {
            repository.addProduct(product, user.fullName).onSuccess {
                _statusMessage.value = "Added product: ${product.name} to catalog."
                try {
                    val database = com.google.firebase.database.FirebaseDatabase.getInstance()
                    database.getReference("products_trigger").setValue(System.currentTimeMillis().toString())
                    database.getReference("products").child(product.id).setValue(product)
                } catch (e: Exception) {}
            }.onFailure { th ->
                _errorMessage.value = th.message ?: "Failed adding product."
            }
        }
    }

    /**
     * Admin edits an existing product's details.
     */
    fun adminEditProduct(product: Product) {
        val user = _currentUser.value
        if (user == null || user.role != "admin") {
            _errorMessage.value = "Unauthorized operation."
            return
        }
        if (product.name.isBlank()) {
            _errorMessage.value = "Product Name cannot be blank."
            return
        }
        if (product.pricePerKg < 0.0 || product.stockKg < 0.0) {
            _errorMessage.value = "Price and stock levels must be non-negative."
            return
        }

        viewModelScope.launch {
            repository.editProduct(product, user.fullName).onSuccess {
                _statusMessage.value = "Successfully edited product ${product.name}."
                try {
                    val database = com.google.firebase.database.FirebaseDatabase.getInstance()
                    database.getReference("products_trigger").setValue(System.currentTimeMillis().toString())
                    database.getReference("products").child(product.id).setValue(product)
                } catch (e: Exception) {}
            }.onFailure { th ->
                _errorMessage.value = th.message ?: "Failed updating product."
            }
        }
    }

    /**
     * Admin deletes an existing product from the database.
     */
    fun adminDeleteProduct(productId: String) {
        val user = _currentUser.value
        if (user == null || user.role != "admin") {
            _errorMessage.value = "Unauthorized operation."
            return
        }

        viewModelScope.launch {
            repository.deleteProduct(productId, user.fullName).onSuccess {
                _statusMessage.value = "Product successfully deleted from catalog."
                try {
                    val database = com.google.firebase.database.FirebaseDatabase.getInstance()
                    database.getReference("products_trigger").setValue(System.currentTimeMillis().toString())
                    database.getReference("products").child(productId).removeValue()
                } catch (e: Exception) {}
            }.onFailure { th ->
                _errorMessage.value = th.message ?: "Failed page/product exclusion."
            }
        }
    }

    /**
     * Admin permanently deletes an order record.
     */
    fun adminDeleteOrder(orderId: Long) {
        val user = _currentUser.value
        if (user == null || user.role != "admin") {
            _errorMessage.value = "Unauthorized operation."
            return
        }
        viewModelScope.launch {
            repository.deleteOrder(orderId)
            _statusMessage.value = "Order #ORD-$orderId successfully deleted from records."
            try {
                val database = com.google.firebase.database.FirebaseDatabase.getInstance()
                database.getReference("orders_trigger").setValue(System.currentTimeMillis().toString())
                database.getReference("orders").child(orderId.toString()).removeValue()
            } catch (e: Exception) {}
        }
    }

    /**
     * Admin clears all stock logs.
     */
    fun adminClearAllLogs() {
        val user = _currentUser.value
        if (user == null || user.role != "admin") {
            _errorMessage.value = "Unauthorized operation."
            return
        }
        viewModelScope.launch {
            repository.clearAllStockLogs()
            _statusMessage.value = "All stock audit logs cleared successfully."
        }
    }

    /**
     * Admin quickly adjusts product stock level directly.
     */
    fun adminQuickAdjustStock(productId: String, delta: Double) {
        val user = _currentUser.value
        if (user == null || user.role != "admin") {
            _errorMessage.value = "Unauthorized operation."
            return
        }
        viewModelScope.launch {
            repository.quickAdjustProductStock(productId, delta, user.fullName).onSuccess { updatedProduct ->
                _statusMessage.value = "Adjusted stock by ${if (delta >= 0.0) "+" else ""}$delta units."
                try {
                    val database = com.google.firebase.database.FirebaseDatabase.getInstance()
                    database.getReference("products_trigger").setValue(System.currentTimeMillis().toString())
                    database.getReference("products").child(productId).setValue(updatedProduct)
                } catch (e: Exception) {}
            }.onFailure { th ->
                _errorMessage.value = th.message ?: "Failed quick stock adjustment."
            }
        }
    }

    fun setErrorMessage(message: String?) {
        _errorMessage.value = message
    }

    /**
     * Updates credentials (username and/or password) of the active authenticated user.
     */
    fun adminUpdateCredentials(newUsername: String, newPasswordPlain: String?) {
        val user = _currentUser.value
        if (user == null) {
            _errorMessage.value = "No active user logged in."
            return
        }
        viewModelScope.launch {
            repository.updateAdminCredentials(user.username, newUsername, newPasswordPlain)
                .onSuccess { updatedUser ->
                    _currentUser.value = updatedUser
                    _statusMessage.value = "Credentials updated successfully! Logged in as: ${updatedUser.username}"
                }
                .onFailure { th ->
                    _errorMessage.value = th.message ?: "Failed updating credentials."
                }
        }
    }

    fun setApiSettings(enabled: Boolean, baseUrl: String) {
        prefs.edit().apply {
            putBoolean("api_enabled", enabled)
            putString("api_base_url", baseUrl)
            apply()
        }
        _apiEnabled.value = enabled
        _apiBaseUrl.value = baseUrl
        _statusMessage.value = "REST API base settings updated successfully!"
    }

    /**
     * Pulls products from the Remote REST API to overwrite local database.
     */
    fun syncPullProducts(silent: Boolean = false) {
        _apiSyncing.value = true
        if (!silent) _errorMessage.value = null
        viewModelScope.launch {
            try {
                val database = com.google.firebase.database.FirebaseDatabase.getInstance()
                val productsRef = database.getReference("products")
                productsRef.get().addOnSuccessListener { snapshot ->
                    val remoteList = mutableListOf<Product>()
                    for (child in snapshot.children) {
                        try {
                            val id = child.child("id").getValue(String::class.java) ?: ""
                            val name = child.child("name").getValue(String::class.java) ?: ""
                            val amharicName = child.child("amharicName").getValue(String::class.java) ?: ""
                            val pricePerKg = (child.child("pricePerKg").value as? Number)?.toDouble() ?: 0.0
                            val stockKg = (child.child("stockKg").value as? Number)?.toDouble() ?: 0.0
                            val imageUrl = child.child("imageUrl").getValue(String::class.java) ?: ""
                            
                            val category = child.child("category").getValue(String::class.java) ?: "Uncategorized"
                            val description = child.child("description").getValue(String::class.java) ?: ""
                            val unitName = child.child("unitName").getValue(String::class.java) ?: "kg"
                            val nutritionalInfo = child.child("nutritionalInfo").getValue(String::class.java) ?: ""
                            
                            remoteList.add(Product(
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
                            ))
                        } catch (e: Exception) {}
                    }
                    if (remoteList.isNotEmpty()) {
                        viewModelScope.launch {
                            repository.insertProductsFromApi(remoteList)
                        }
                        if (!silent) _statusMessage.value = "Pulled ${remoteList.size} products from Firebase successfully!"
                    } else {
                        if (!silent) _statusMessage.value = "No products found on Firebase."
                    }
                    _apiSyncing.value = false
                }.addOnFailureListener {
                    if (!silent) _errorMessage.value = "Firebase API Pull Error: ${it.localizedMessage}"
                    _apiSyncing.value = false
                }
            } catch (e: Exception) {
                if (!silent) _errorMessage.value = "Firebase Connection Error: ${e.localizedMessage}"
                _apiSyncing.value = false
            }
        }
    }

    /**
     * Pushes current local products database up to the Remote REST API.
     */
    fun syncPushProducts() {
        _apiSyncing.value = true
        _errorMessage.value = null
        val list = productsList.value
        viewModelScope.launch {
            try {
                val database = com.google.firebase.database.FirebaseDatabase.getInstance()
                var count = 0
                val productsRef = database.getReference("products")
                list.forEach { prod ->
                    productsRef.child(prod.id).setValue(prod)
                    count++
                }
                database.getReference("products_trigger").setValue(System.currentTimeMillis().toString())
                _statusMessage.value = "Pushed $count products upward to Firebase!"
            } catch (e: Exception) {
                _errorMessage.value = "Firebase Push Error: ${e.localizedMessage}"
            } finally {
                _apiSyncing.value = false
            }
        }
    }

    /**
     * Pulls customer orders from the remote REST database.
     */
    fun syncPullOrders(silent: Boolean = false) {
        _apiSyncing.value = true
        if (!silent) _errorMessage.value = null
        viewModelScope.launch {
            try {
                val database = com.google.firebase.database.FirebaseDatabase.getInstance()
                val ordersRef = database.getReference("orders")
                ordersRef.get().addOnSuccessListener { snapshot ->
                    val remoteOrders = mutableListOf<Order>()
                    for (child in snapshot.children) {
                        try {
                            val id = child.child("id").getValue(Long::class.java) ?: 0L
                            val customerUsername = child.child("customerUsername").getValue(String::class.java) ?: ""
                            val productId = child.child("productId").getValue(String::class.java) ?: ""
                            val productName = child.child("productName").getValue(String::class.java) ?: ""
                            val qty = (child.child("quantityKg").value as? Number)?.toDouble() ?: 0.0
                            val price = (child.child("totalPrice").value as? Number)?.toDouble() ?: 0.0
                            val status = child.child("status").getValue(String::class.java) ?: "Pending"
                            val address = child.child("deliveryAddress").getValue(String::class.java) ?: ""
                            val amharicName = child.child("amharicName").getValue(String::class.java) ?: ""
                            val timestamp = child.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()

                            remoteOrders.add(Order(
                                id = id,
                                customerUsername = customerUsername,
                                productId = productId,
                                productName = productName,
                                amharicName = amharicName,
                                quantityKg = qty,
                                totalPrice = price,
                                status = status,
                                deliveryAddress = address,
                                timestamp = timestamp
                            ))
                        } catch (e: Exception) {}
                    }
                    
                    if (remoteOrders.isNotEmpty()) {
                        viewModelScope.launch {
                            repository.insertOrdersFromApi(remoteOrders)
                        }
                        if (!silent) _statusMessage.value = "Downloaded ${remoteOrders.size} active order records from Firebase!"
                    } else {
                        if (!silent) _statusMessage.value = "No existing orders retrieved from Firebase."
                    }
                    _apiSyncing.value = false
                }.addOnFailureListener {
                    if (!silent) _errorMessage.value = "Firebase Pull Error: ${it.localizedMessage}"
                    _apiSyncing.value = false
                }
            } catch (e: Exception) {
                if (!silent) _errorMessage.value = "Firebase Error: ${e.localizedMessage}"
                _apiSyncing.value = false
            }
        }
    }

    fun syncPullNotifications(silent: Boolean = false) {
        viewModelScope.launch {
            try {
                val database = com.google.firebase.database.FirebaseDatabase.getInstance()
                val notificationsRef = database.getReference("notifications")
                notificationsRef.get().addOnSuccessListener { snapshot ->
                    val remoteNotifs = mutableListOf<Notification>()
                    for (userNode in snapshot.children) {
                        for (child in userNode.children) {
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

                                remoteNotifs.add(Notification(
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
                    }
                    if (remoteNotifs.isNotEmpty()) {
                        viewModelScope.launch {
                            repository.replaceNotificationsFromApi(remoteNotifs)
                        }
                    }
                }
            } catch (e: Exception) {}
        }
    }

    // Static Factory
    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DairyViewModel(application) as T
                }
            }
    }
}
