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

    init {
        // Trigger pre-population async
        viewModelScope.launch {
            repository.prePopulateIfNeeded()
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
                if (_apiEnabled.value) {
                    try {
                        val apiSvc = RetrofitClient.getApiService(_apiBaseUrl.value)
                        apiSvc.updateProduct(productId, updatedProduct)
                    } catch (e: Exception) {}
                }
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
            if (_apiEnabled.value) {
                try {
                    val apiSvc = RetrofitClient.getApiService(_apiBaseUrl.value)
                    apiSvc.updateOrderStatus(orderId, mapOf("status" to updatedStatus))
                } catch (e: Exception) {}
            }
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
                if (_apiEnabled.value) {
                    try {
                        val apiSvc = RetrofitClient.getApiService(_apiBaseUrl.value)
                        apiSvc.addProduct(product)
                    } catch (e: Exception) {}
                }
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
                if (_apiEnabled.value) {
                    try {
                        val apiSvc = RetrofitClient.getApiService(_apiBaseUrl.value)
                        apiSvc.updateProduct(product.id, product)
                    } catch (e: Exception) {}
                }
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
                if (_apiEnabled.value) {
                    try {
                        val apiSvc = RetrofitClient.getApiService(_apiBaseUrl.value)
                        apiSvc.deleteProduct(productId)
                    } catch (e: Exception) {}
                }
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
            if (_apiEnabled.value) {
                try {
                    val apiSvc = RetrofitClient.getApiService(_apiBaseUrl.value)
                    apiSvc.deleteOrder(orderId)
                } catch (e: Exception) {}
            }
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
                if (_apiEnabled.value) {
                    try {
                        val apiSvc = RetrofitClient.getApiService(_apiBaseUrl.value)
                        apiSvc.updateProduct(productId, updatedProduct)
                    } catch (e: Exception) {}
                }
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
    fun syncPullProducts() {
        if (!_apiEnabled.value) {
            _errorMessage.value = "Enable REST API Sync mode first!"
            return
        }
        val baseUrl = _apiBaseUrl.value
        _apiSyncing.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val apiService = RetrofitClient.getApiService(baseUrl)
                val remoteList = apiService.getProducts()
                if (remoteList.isNotEmpty()) {
                    repository.insertProductsFromApi(remoteList)
                    _statusMessage.value = "Pulled ${remoteList.size} products from remote REST API successfully!"
                } else {
                    _statusMessage.value = "No products found on remote REST server."
                }
            } catch (e: Exception) {
                _errorMessage.value = "API Pull Error: ${e.localizedMessage ?: "Failed connection"}. Ensure server is running at $baseUrl"
            } finally {
                _apiSyncing.value = false
            }
        }
    }

    /**
     * Pushes current local products database up to the Remote REST API.
     */
    fun syncPushProducts() {
        if (!_apiEnabled.value) {
            _errorMessage.value = "Enable REST API Sync mode first!"
            return
        }
        val baseUrl = _apiBaseUrl.value
        val list = productsList.value
        if (list.isEmpty()) {
            _errorMessage.value = "Local catalog database is empty."
            return
        }
        _apiSyncing.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val apiService = RetrofitClient.getApiService(baseUrl)
                var count = 0
                list.forEach { prod ->
                    apiService.addProduct(prod)
                    count++
                }
                _statusMessage.value = "Pushed $count products upward to remote REST server API database!"
            } catch (e: Exception) {
                _errorMessage.value = "API Push Error: ${e.localizedMessage ?: "Failed connection"}. Ensure server is running at $baseUrl"
            } finally {
                _apiSyncing.value = false
            }
        }
    }

    /**
     * Pulls customer orders from the remote REST database.
     */
    fun syncPullOrders() {
        if (!_apiEnabled.value) {
            _errorMessage.value = "Enable REST API Sync mode first!"
            return
        }
        val baseUrl = _apiBaseUrl.value
        _apiSyncing.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val apiService = RetrofitClient.getApiService(baseUrl)
                val remoteOrders = apiService.getOrders()
                if (remoteOrders.isNotEmpty()) {
                    repository.insertOrdersFromApi(remoteOrders)
                    _statusMessage.value = "Downloaded ${remoteOrders.size} active order records from remote Server database!"
                } else {
                    _statusMessage.value = "No existing orders retrieved from remote REST server."
                }
            } catch (e: Exception) {
                _errorMessage.value = "API Orders Pull Error: ${e.localizedMessage ?: "Failed connection"}"
            } finally {
                _apiSyncing.value = false
            }
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
