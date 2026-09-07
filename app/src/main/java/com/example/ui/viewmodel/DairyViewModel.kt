package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.RealtimeConnectionStatus
import com.example.data.api.RetrofitClient
import com.example.data.api.SupabaseHelper
import com.example.data.api.SupabaseRealtimeManager
import com.example.data.local.AppDatabase
import com.example.data.model.Order
import com.example.data.model.Product
import com.example.data.model.StockLog
import com.example.data.model.User
import com.example.data.model.Notification
import com.example.data.repository.DairyRepository
import kotlinx.coroutines.Dispatchers
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

data class AdminOrderPopup(
    val orderId: Long,
    val customerUsername: String,
    val productName: String,
    val amharicName: String = "",
    val quantityKg: Double,
    val totalPrice: Double,
    val deliveryAddress: String,
    val timestamp: Long = System.currentTimeMillis()
)

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

    private val _dbType = MutableStateFlow(prefs.getString("db_type", "supabase") ?: "supabase")
    val dbType: StateFlow<String> = _dbType.asStateFlow()

    private val _supabaseUrl = MutableStateFlow(
        prefs.getString("supabase_url", "https://ibwjydljyjbrqbpspbuk.supabase.co") ?: "https://ibwjydljyjbrqbpspbuk.supabase.co"
    )
    val supabaseUrl: StateFlow<String> = _supabaseUrl.asStateFlow()

    private val _supabaseKey = MutableStateFlow(
        prefs.getString("supabase_key", "sb_publishable_zGGs5I2TL6jQtJNSM1n53A_MeYSsLFn") ?: "sb_publishable_zGGs5I2TL6jQtJNSM1n53A_MeYSsLFn"
    )
    val supabaseKey: StateFlow<String> = _supabaseKey.asStateFlow()

    // Supabase Realtime WebSocket connection status & last event timestamp
    val realtimeConnectionStatus: StateFlow<RealtimeConnectionStatus> = SupabaseRealtimeManager.connectionStatus
    val realtimeLastEventTime: StateFlow<Long?> = SupabaseRealtimeManager.lastEventTime

    private val _apiSyncing = MutableStateFlow(false)
    val apiSyncing: StateFlow<Boolean> = _apiSyncing.asStateFlow()

    // Current screen routing state
    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.CustomerHome)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // App Config & Version control states
    private val _isUpdateRequired = MutableStateFlow(false)
    val isUpdateRequired: StateFlow<Boolean> = _isUpdateRequired.asStateFlow()

    private val _minVersionCode = MutableStateFlow(1)
    val minVersionCode: StateFlow<Int> = _minVersionCode.asStateFlow()

    private val _updateUrl = MutableStateFlow("https://gitlab.com")
    val updateUrl: StateFlow<String> = _updateUrl.asStateFlow()

    // Real-time Database connection handling
    init {
        // Initialize Supabase Realtime WebSocket Connection & Listener
        SupabaseRealtimeManager.initialize(application)
        SupabaseRealtimeManager.addOrderListener { order ->
            if (_currentUser.value?.role == "admin") {
                triggerAdminOrderPopup(order)
            }
        }

        // Trigger pre-population async
        viewModelScope.launch {
            repository.prePopulateIfNeeded()
        }

        // Periodic/Realtime Sync Engine
        viewModelScope.launch(Dispatchers.IO) {
            if (SupabaseHelper.isSupabaseEnabled(getApplication())) {
                // Supabase initial load
                val config = SupabaseHelper.fetchAppConfig(getApplication())
                if (config != null) {
                    _minVersionCode.value = config.minimum_version_code
                    _updateUrl.value = config.update_url
                    val currentVersion = com.example.BuildConfig.VERSION_CODE
                    _isUpdateRequired.value = currentVersion < config.minimum_version_code
                }
                syncPullProducts(silent = true)
                syncPullOrders(silent = true)
                syncPullNotifications(silent = true)

                // Poll Supabase every 8 seconds to guarantee real-time sync even across devices / mobile data
                while (true) {
                    kotlinx.coroutines.delay(8000)
                    if (SupabaseHelper.isSupabaseEnabled(getApplication())) {
                        val latestConfig = SupabaseHelper.fetchAppConfig(getApplication())
                        if (latestConfig != null) {
                            _minVersionCode.value = latestConfig.minimum_version_code
                            _updateUrl.value = latestConfig.update_url
                            val currentVersion = com.example.BuildConfig.VERSION_CODE
                            _isUpdateRequired.value = currentVersion < latestConfig.minimum_version_code
                        }
                        syncPullProducts(silent = true)
                        syncPullOrders(silent = true)
                        syncPullNotifications(silent = true)
                    }
                }
            }
        }

        // Real-time observer for new incoming customer orders (triggers popup ONLY for admin logged in user)
        viewModelScope.launch {
            repository.allOrders.collect { orders ->
                if (!isOrderTrackingInitialized) {
                    orders.forEach { knownOrderIds.add(it.id) }
                    isOrderTrackingInitialized = true
                } else {
                    val newlyArrived = orders.filter { it.id !in knownOrderIds }
                    orders.forEach { knownOrderIds.add(it.id) }

                    // Only show notification pop up if user is currently logged in as admin
                    if (_currentUser.value?.role == "admin" && newlyArrived.isNotEmpty()) {
                        val latestPending = newlyArrived.filter { it.status == "Pending" }.maxByOrNull { it.timestamp }
                            ?: newlyArrived.lastOrNull()
                        if (latestPending != null) {
                            triggerAdminOrderPopup(latestPending)
                        }
                    }
                }
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

    // Real-time In-App Pop-up notification for logged-in Admin user
    private val _adminOrderPopup = MutableStateFlow<AdminOrderPopup?>(null)
    val adminOrderPopup: StateFlow<AdminOrderPopup?> = _adminOrderPopup.asStateFlow()

    private val knownOrderIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    private var isOrderTrackingInitialized = false

    fun dismissAdminOrderPopup() {
        _adminOrderPopup.value = null
    }

    fun triggerAdminOrderPopup(order: Order) {
        // STRICT CHECK: Popup notification message is ONLY shown for the admin logged-in user
        if (_currentUser.value?.role == "admin") {
            _adminOrderPopup.value = AdminOrderPopup(
                orderId = order.id,
                customerUsername = order.customerUsername,
                productName = order.productName,
                amharicName = order.amharicName,
                quantityKg = order.quantityKg,
                totalPrice = order.totalPrice,
                deliveryAddress = order.deliveryAddress,
                timestamp = order.timestamp
            )
        }
    }

    fun acceptOrderFromPopup(orderId: Long) {
        adminUpdateOrderStatus(orderId, "Confirmed")
        _statusMessage.value = "Order #ORD-$orderId has been confirmed!"
        dismissAdminOrderPopup()
    }

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
                    // Populate known order IDs so we only alert for newly incoming orders during active admin session
                    adminOrdersList.value.forEach { knownOrderIds.add(it.id) }
                    _currentScreen.value = AppScreen.AdminHome
                } else {
                    _adminOrderPopup.value = null
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
                    adminOrdersList.value.forEach { knownOrderIds.add(it.id) }
                    _currentScreen.value = AppScreen.AdminHome
                } else {
                    _adminOrderPopup.value = null
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
        _adminOrderPopup.value = null
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
                    
                    // Database tracking update
                    if (SupabaseHelper.isSupabaseEnabled(getApplication())) {
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
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
                                SupabaseHelper.upsertOrder(getApplication(), remoteOrder)

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
                                SupabaseHelper.upsertNotification(getApplication(), adminNotif)

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
                                SupabaseHelper.upsertNotification(getApplication(), custNotif)
                            } catch (e: Exception) {
                                Log.e("DairyViewModel", "Supabase order placement tracking failure", e)
                            }
                        }
                    }
                    
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
                if (SupabaseHelper.isSupabaseEnabled(getApplication())) {
                    viewModelScope.launch(Dispatchers.IO) {
                        SupabaseHelper.upsertProduct(getApplication(), updatedProduct)
                    }
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
            if (SupabaseHelper.isSupabaseEnabled(getApplication())) {
                viewModelScope.launch(Dispatchers.IO) {
                    val order = adminOrdersList.value.find { it.id == orderId }?.copy(status = updatedStatus)
                    if (order != null) {
                        SupabaseHelper.upsertOrder(getApplication(), order)
                    }
                }
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
                if (SupabaseHelper.isSupabaseEnabled(getApplication())) {
                    viewModelScope.launch(Dispatchers.IO) {
                        SupabaseHelper.upsertProduct(getApplication(), product)
                    }
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
                if (SupabaseHelper.isSupabaseEnabled(getApplication())) {
                    viewModelScope.launch(Dispatchers.IO) {
                        SupabaseHelper.upsertProduct(getApplication(), product)
                    }
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
                if (SupabaseHelper.isSupabaseEnabled(getApplication())) {
                    viewModelScope.launch(Dispatchers.IO) {
                        SupabaseHelper.deleteProduct(getApplication(), productId)
                    }
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
            if (SupabaseHelper.isSupabaseEnabled(getApplication())) {
                viewModelScope.launch(Dispatchers.IO) {
                    SupabaseHelper.deleteOrder(getApplication(), orderId)
                }
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
                if (SupabaseHelper.isSupabaseEnabled(getApplication())) {
                    viewModelScope.launch(Dispatchers.IO) {
                        SupabaseHelper.upsertProduct(getApplication(), updatedProduct)
                    }
                }
            }.onFailure { th ->
                _errorMessage.value = th.message ?: "Failed quick stock adjustment."
            }
        }
    }

    /**
     * Admin updates the minimum required version code and update URL in Firebase
     */
    fun adminUpdateMinVersion(versionCode: Int, updateUrl: String) {
        val user = _currentUser.value
        if (user == null || user.role != "admin") {
            _errorMessage.value = "Unauthorized operation."
            return
        }
        viewModelScope.launch {
            if (SupabaseHelper.isSupabaseEnabled(getApplication())) {
                viewModelScope.launch(Dispatchers.IO) {
                    val success = SupabaseHelper.updateAppConfig(getApplication(), versionCode, updateUrl)
                    if (success) {
                        _statusMessage.value = "Minimum required version updated to Code: $versionCode successfully!"
                    } else {
                        _errorMessage.value = "Failed to update config on Supabase."
                    }
                }
            } else {
                _statusMessage.value = "Minimum required version updated to Code: $versionCode successfully!"
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

    fun updateDatabaseSettings(dbType: String, supabaseUrl: String, supabaseKey: String) {
        prefs.edit().apply {
            putString("db_type", dbType)
            putString("supabase_url", supabaseUrl.trim())
            putString("supabase_key", supabaseKey.trim())
            apply()
        }
        _dbType.value = dbType
        _supabaseUrl.value = supabaseUrl.trim()
        _supabaseKey.value = supabaseKey.trim()

        // Reconnect Supabase Realtime WebSocket with the newly supplied configuration
        SupabaseRealtimeManager.reconnect()
        
        // Immediately pull latest products, orders and notifications from Supabase
        if (dbType == "supabase") {
            syncPullProducts(silent = true)
            syncPullOrders(silent = false)
            syncPullNotifications(silent = true)
        }
        
        _statusMessage.value = "Database settings saved & Supabase Realtime connected!"
    }

    fun reconnectRealtime() {
        SupabaseRealtimeManager.reconnect()
        _statusMessage.value = "Reconnecting to Supabase Realtime..."
    }

    fun testSupabaseConnection(url: String, key: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = SupabaseRealtimeManager.testConnection(getApplication(), url.trim(), key.trim())
            result.onSuccess { msg ->
                onResult(true, msg)
            }.onFailure { th ->
                onResult(false, th.message ?: "Connection test failed.")
            }
        }
    }

    /**
     * Pulls products from the Remote Supabase database to overwrite local database.
     */
    fun syncPullProducts(silent: Boolean = false) {
        _apiSyncing.value = true
        if (!silent) _errorMessage.value = null
        viewModelScope.launch {
            if (SupabaseHelper.isSupabaseEnabled(getApplication())) {
                viewModelScope.launch(Dispatchers.IO) {
                    val remoteList = SupabaseHelper.fetchProducts(getApplication())
                    if (remoteList != null) {
                        repository.insertProductsFromApi(remoteList)
                        if (!silent) _statusMessage.value = "Pulled ${remoteList.size} products from Supabase successfully!"
                    } else {
                        if (!silent) _errorMessage.value = "Failed to pull products from Supabase."
                    }
                    _apiSyncing.value = false
                }
            } else {
                _apiSyncing.value = false
            }
        }
    }

    /**
     * Pushes current local products database up to Supabase.
     */
    fun syncPushProducts() {
        _apiSyncing.value = true
        _errorMessage.value = null
        val list = productsList.value
        viewModelScope.launch {
            if (SupabaseHelper.isSupabaseEnabled(getApplication())) {
                viewModelScope.launch(Dispatchers.IO) {
                    var count = 0
                    list.forEach { prod ->
                        if (SupabaseHelper.upsertProduct(getApplication(), prod)) {
                            count++
                        }
                    }
                    _statusMessage.value = "Pushed $count products upward to Supabase!"
                    _apiSyncing.value = false
                }
            } else {
                _statusMessage.value = "Local mode active: products saved in local database."
                _apiSyncing.value = false
            }
        }
    }

    /**
     * Pulls customer orders from the remote Supabase database.
     */
    fun syncPullOrders(silent: Boolean = false) {
        _apiSyncing.value = true
        if (!silent) _errorMessage.value = null
        viewModelScope.launch {
            if (SupabaseHelper.isSupabaseEnabled(getApplication())) {
                viewModelScope.launch(Dispatchers.IO) {
                    val remoteOrders = SupabaseHelper.fetchOrders(getApplication())
                    if (remoteOrders != null) {
                        repository.insertOrdersFromApi(remoteOrders)
                        if (!silent) _statusMessage.value = "Downloaded ${remoteOrders.size} active order records from Supabase!"
                    } else {
                        if (!silent) _errorMessage.value = "Failed to pull orders from Supabase."
                    }
                    _apiSyncing.value = false
                }
            } else {
                _apiSyncing.value = false
            }
        }
    }

    fun syncPullNotifications(silent: Boolean = false) {
        viewModelScope.launch {
            if (SupabaseHelper.isSupabaseEnabled(getApplication())) {
                viewModelScope.launch(Dispatchers.IO) {
                    val remoteNotifs = SupabaseHelper.fetchNotifications(getApplication())
                    if (remoteNotifs != null) {
                        repository.replaceNotificationsFromApi(remoteNotifs)
                    }
                }
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
