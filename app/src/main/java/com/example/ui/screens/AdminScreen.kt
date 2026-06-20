package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Order
import com.example.data.model.Product
import com.example.data.model.StockLog
import com.example.ui.viewmodel.DairyViewModel
import com.example.ui.theme.FarmGreenPrimary
import com.example.ui.theme.ButterGoldSecondary
import java.text.SimpleDateFormat
import java.util.*

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.AdminNotificationService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(viewModel: DairyViewModel) {
    val context = LocalContext.current

    // Always start the foreground listener service so it remains active in background immediately upon opening AdminScreen
    LaunchedEffect(Unit) {
        val serviceIntent = Intent(context, AdminNotificationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    // Request POST_NOTIFICATIONS permission in Android 13+ (Tiramisu)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* Preference is checked, notification permission handles visibility */ }
        LaunchedEffect(Unit) {
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val currentUser by viewModel.currentUser.collectAsState()
    val products by viewModel.productsList.collectAsState()
    val orders by viewModel.adminOrdersList.collectAsState()
    val stockLogs by viewModel.stockLogList.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val adminNotifications by viewModel.adminNotificationsList.collectAsState()
    val unreadAdminCount = remember(adminNotifications) { adminNotifications.count { !it.isRead } }
    var showAdminNotificationsDialog by remember { mutableStateOf(false) }

    var activeTab by remember { mutableStateOf(0) } // 0: Inventory, 1: Customer Orders, 2: Stock Logs
    var searchQueries by remember { mutableStateOf("") }

    // Search and filter for orders
    var orderSearchQuery by remember { mutableStateOf("") }
    var orderStatusFilter by remember { mutableStateOf("All") }

    // Search for audit logs
    var logSearchQuery by remember { mutableStateOf("") }

    // Confirmation dialog for clearing all logs
    var showClearLogsDialog by remember { mutableStateOf(false) }

    // Confirmation state for order deletion
    var orderToDeleteId by remember { mutableStateOf<Long?>(null) }

    // Dialog editing states
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var priceInput by remember { mutableStateOf("") }
    var stockInput by remember { mutableStateOf("") }

    // Expanded Edit state fields
    var nameInput by remember { mutableStateOf("") }
    var amharicNameInput by remember { mutableStateOf("") }
    var categoryInput by remember { mutableStateOf("") }
    var unitNameInput by remember { mutableStateOf("") }
    var descInput by remember { mutableStateOf("") }
    var nutInfoInput by remember { mutableStateOf("") }
    var imageUrlInput by remember { mutableStateOf("") }

    // Dialog adding states
    var isAddingProduct by remember { mutableStateOf(false) }
    var newIdInput by remember { mutableStateOf("") }
    var newNameInput by remember { mutableStateOf("") }
    var newAmharicNameInput by remember { mutableStateOf("") }
    var newCategoryInput by remember { mutableStateOf("Dairy (የወተት)") }
    var newPriceInput by remember { mutableStateOf("") }
    var newStockInput by remember { mutableStateOf("") }
    var newUnitNameInput by remember { mutableStateOf("kg") }
    var newDescInput by remember { mutableStateOf("") }
    var newNutInfoInput by remember { mutableStateOf("") }
    var newImageUrlInput by remember { mutableStateOf("") }

    // Deletion states
    var productToDelete by remember { mutableStateOf<Product?>(null) }

    // Credentials Editing state
    var showProfileDialog by remember { mutableStateOf(false) }
    var adminUsernameInput by remember { mutableStateOf("") }
    var adminPasswordInput by remember { mutableStateOf("") }
    var adminConfirmPasswordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Surface(
                color = FarmGreenPrimary,
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Title and action buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Admin Portal: ${currentUser?.fullName ?: "Manager"}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "🛡️", fontSize = 18.sp)
                            }
                            Text(
                                text = "Real-Time Pricing, Catalogs & Audit Operations",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.82f)
                                )
                            )
                        }

                        // Top header action icons
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Admin Notifications Bell
                            Box(modifier = Modifier.padding(end = 4.dp)) {
                                IconButton(
                                    onClick = { showAdminNotificationsDialog = true },
                                    modifier = Modifier.testTag("admin_notifications_bell_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Admin Notifications",
                                        tint = if (unreadAdminCount > 0) Color(0xFFFFD54F) else Color.White
                                    )
                                }
                                if (unreadAdminCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 4.dp, end = 4.dp)
                                            .size(15.dp)
                                            .clip(CircleShape)
                                            .background(Color.Red),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = unreadAdminCount.toString(),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = {
                                    adminUsernameInput = currentUser?.username ?: ""
                                    adminPasswordInput = ""
                                    adminConfirmPasswordInput = ""
                                    showProfileDialog = true
                                },
                                modifier = Modifier.testTag("admin_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ManageAccounts,
                                    contentDescription = "Edit Profile Credentials",
                                    tint = Color.White
                                )
                            }
                            IconButton(
                                onClick = { 
                                    viewModel.logout() 
                                },
                                modifier = Modifier.testTag("logout_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = "Log out",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.25f))
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ANGEL ETHIOPIAN CHEESE",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = 0.3.sp
                                )
                            )
                            Text(
                                text = "አንጀል የኢትዮጵያ አይብ (አስተዳዳሪ)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ButterGoldSecondary,
                                    fontSize = 10.sp
                                )
                            )
                        }

                        // Prominent Gold Add Product Button in the green header
                        Button(
                            onClick = {
                                newIdInput = ""
                                newNameInput = ""
                                newAmharicNameInput = ""
                                newCategoryInput = "Dairy (የወተት)"
                                newPriceInput = ""
                                newStockInput = ""
                                newUnitNameInput = "kg"
                                newDescInput = ""
                                newNutInfoInput = ""
                                newImageUrlInput = ""
                                isAddingProduct = true
                            },
                            modifier = Modifier.testTag("admin_add_product_header_button").height(28.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ButterGoldSecondary,
                                contentColor = FarmGreenPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Product",
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Add Product", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.Inventory, contentDescription = "Inventory") },
                    label = { Text("Inventory") },
                    modifier = Modifier.testTag("nav_admin_inventory")
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = {
                        BadgedBox(
                            badge = {
                                val pendingCount = orders.count { it.status == "Pending" }
                                if (pendingCount > 0) {
                                    Badge { Text("$pendingCount") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = "Orders")
                        }
                    },
                    label = { Text("Orders") },
                    modifier = Modifier.testTag("nav_admin_orders")
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Default.HistoryToggleOff, contentDescription = "Logs") },
                    label = { Text("Audit Logs") },
                    modifier = Modifier.testTag("nav_admin_logs")
                )
            }
        },
        contentWindowInsets = WindowInsets.navigationBars,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Info Toast alerts
                AnimatedVisibility(visible = errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(errorMessage ?: "", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { viewModel.clearStatus() }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss")
                            }
                        }
                    }
                }
                AnimatedVisibility(visible = statusMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(statusMessage ?: "", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { viewModel.clearStatus() }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss")
                            }
                        }
                    }
                }

                if (activeTab == 0) {
                    // TAB 0: Manage Stock & Pricing (allows setting the prize of product)
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Search bar
                        OutlinedTextField(
                            value = searchQueries,
                            onValueChange = { searchQueries = it },
                            placeholder = { Text("Filter items...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )

                        val filtered = products.filter {
                            it.name.contains(searchQueries, ignoreCase = true) ||
                            it.category.contains(searchQueries, ignoreCase = true) ||
                            it.amharicName.contains(searchQueries)
                        }

                        // Short summary widgets inside admin panel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Low stock items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    Text("${products.count { it.stockKg <= 10.0 }} items", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Unique Users", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    Text("Active Live Channels", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("admin_inventory_list"),
                            contentPadding = PaddingValues(bottom = 16.dp, start = 16.dp, end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filtered, key = { it.id }) { product ->
                                AdminProductCard(
                                    product = product,
                                    onEditSelected = {
                                        editingProduct = product
                                        imageUrlInput = product.imageUrl
                                        priceInput = product.pricePerKg.toString()
                                        stockInput = product.stockKg.toString()
                                        nameInput = product.name
                                        amharicNameInput = product.amharicName
                                        categoryInput = product.category
                                        unitNameInput = product.unitName
                                        descInput = product.description
                                        nutInfoInput = product.nutritionalInfo
                                    },
                                    onDeleteSelected = {
                                        productToDelete = product
                                    },
                                    onQuickIncrease = {
                                        val delta = if (product.unitName.lowercase() in listOf("pack", "piece", "egg", "eggs")) 1.0 else 5.0
                                        viewModel.adminQuickAdjustStock(product.id, delta)
                                    },
                                    onQuickDecrease = {
                                        val delta = if (product.unitName.lowercase() in listOf("pack", "piece", "egg", "eggs")) 1.0 else 5.0
                                        viewModel.adminQuickAdjustStock(product.id, -delta)
                                    }
                                )
                            }
                        }
                    }
                } else if (activeTab == 1) {
                    // TAB 1: Real-time Customer Orders status dispatcher
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Search for Orders
                        OutlinedTextField(
                            value = orderSearchQuery,
                            onValueChange = { orderSearchQuery = it },
                            placeholder = { Text("Search customer or product...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                        )

                        // Status filter row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val statuses = listOf("All", "Pending", "Confirmed", "Delivered", "Cancelled")
                            statuses.forEach { status ->
                                val isSelected = orderStatusFilter == status
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { orderStatusFilter = status },
                                    label = { Text(status) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }

                        val filteredOrders = orders.filter { order ->
                            val matchesStatus = orderStatusFilter == "All" || order.status == orderStatusFilter
                            val matchesSearch = order.customerUsername.contains(orderSearchQuery, ignoreCase = true) ||
                                                order.productName.contains(orderSearchQuery, ignoreCase = true) ||
                                                order.amharicName.contains(orderSearchQuery)
                            matchesStatus && matchesSearch
                        }

                        if (filteredOrders.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No matching orders found.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .testTag("admin_orders_list"),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredOrders, key = { it.id }) { order ->
                                    AdminOrderCard(
                                        order = order,
                                        onUpdateStatus = { ordId, nextStatus ->
                                            viewModel.adminUpdateOrderStatus(ordId, nextStatus)
                                        },
                                        onDeleteOrder = {
                                            orderToDeleteId = order.id
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // TAB 2: Dynamic Stock log transactional tracking
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = logSearchQuery,
                                onValueChange = { logSearchQuery = it },
                                placeholder = { Text("Search logs...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )

                            if (stockLogs.isNotEmpty()) {
                                Button(
                                    onClick = { showClearLogsDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all logs")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        val filteredLogs = stockLogs.filter { log ->
                            log.productName.contains(logSearchQuery, ignoreCase = true) ||
                            log.reason.contains(logSearchQuery, ignoreCase = true) ||
                            log.operator.contains(logSearchQuery, ignoreCase = true)
                        }

                        if (filteredLogs.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No audit logs found.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .testTag("admin_audit_logs_list"),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredLogs, key = { it.id }) { log ->
                                    AuditLogCard(log = log)
                                }
                            }
                        }
                    }
                }
            }

            // DETAILED PRODUCT EDIT DIALOG (Supports editing name, prices, stock, units, description, ingredients)
            if (editingProduct != null) {
                val prod = editingProduct!!
                AlertDialog(
                    onDismissRequest = { editingProduct = null },
                    title = {
                        Text(
                            text = "Edit Product: ${prod.name}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    text = {
                        val scrollState = androidx.compose.foundation.rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                        ) {
                            Text(
                                "Modify the product details as needed below.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // English Name & Amharic Name side by side
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = nameInput,
                                    onValueChange = { nameInput = it },
                                    label = { Text("Name (EN)", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .testTag("admin_name_edit_field")
                                )
                                OutlinedTextField(
                                    value = amharicNameInput,
                                    onValueChange = { amharicNameInput = it },
                                    label = { Text("ስም (አማርኛ)", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .testTag("admin_amharic_name_edit_field")
                                )
                            }

                            // Category & Unit Name side by side
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = categoryInput,
                                    onValueChange = { categoryInput = it },
                                    label = { Text("Category", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.1f)
                                )
                                OutlinedTextField(
                                    value = unitNameInput,
                                    onValueChange = { unitNameInput = it },
                                    label = { Text("Unit (eg. kg)", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.9f)
                                )
                            }

                            // Price & Stock Input side by side
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = priceInput,
                                    onValueChange = { priceInput = it },
                                    label = { Text("Price (ETB)", fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .testTag("admin_price_edit_field")
                                )
                                OutlinedTextField(
                                    value = stockInput,
                                    onValueChange = { stockInput = it },
                                    label = { Text("Stock Qty", fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .testTag("admin_stock_edit_field")
                                )
                            }

                            // Description Input
                            OutlinedTextField(
                                value = descInput,
                                onValueChange = { descInput = it },
                                label = { Text("Description", fontSize = 11.sp) },
                                maxLines = 2,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                             )

                             // Nutritional Info Input
                             OutlinedTextField(
                                 value = nutInfoInput,
                                 onValueChange = { nutInfoInput = it },
                                 label = { Text("Nutritional Details", fontSize = 11.sp) },
                                 maxLines = 2,
                                 modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                             )

                             // Image URL (Add Picture) Input
                             OutlinedTextField(
                                 value = imageUrlInput,
                                 onValueChange = { imageUrlInput = it },
                                 label = { Text("Add Picture (URL)", fontSize = 11.sp) },
                                 singleLine = true,
                                 modifier = Modifier.fillMaxWidth().testTag("admin_edit_image_url_field")
                             )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val parsedPrice = priceInput.toDoubleOrNull() ?: -1.0
                                val parsedStock = stockInput.toDoubleOrNull() ?: -1.0
                                if (nameInput.isBlank()) {
                                    viewModel.adminUpdateProduct(prod.id, -1.0, -1.0)
                                } else if (parsedPrice >= 0.0 && parsedStock >= 0.0) {
                                    val updatedProduct = prod.copy(
                                        name = nameInput.trim(),
                                        amharicName = amharicNameInput.trim(),
                                        category = categoryInput.trim(),
                                        pricePerKg = parsedPrice,
                                        stockKg = parsedStock,
                                        unitName = unitNameInput.trim(),
                                        description = descInput.trim(),
                                        nutritionalInfo = nutInfoInput.trim(),
                                        imageUrl = imageUrlInput.trim()
                                    )
                                    viewModel.adminEditProduct(updatedProduct)
                                    editingProduct = null
                                } else {
                                    viewModel.adminUpdateProduct(prod.id, -1.0, -1.0)
                                }
                            },
                            modifier = Modifier.testTag("admin_save_adjustments_button")
                        ) {
                            Text("Save Details")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { editingProduct = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // ADD NEW PRODUCT DIALOG (Supports entering code ID, name, price, units)
            if (isAddingProduct) {
                AlertDialog(
                    onDismissRequest = { isAddingProduct = false },
                    title = {
                        Text(
                            text = "Add New Product",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    text = {
                        val scrollState = androidx.compose.foundation.rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                        ) {
                            Text(
                                "Specify the details for the new product to add it to the retail catalog.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            // ID & Category side by side
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newIdInput,
                                    onValueChange = { newIdInput = it.lowercase().replace(" ", "_") },
                                    label = { Text("ID / Code", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("admin_new_id_field")
                                )
                                OutlinedTextField(
                                    value = newCategoryInput,
                                    onValueChange = { newCategoryInput = it },
                                    label = { Text("Category", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // English Name & Amharic Name side by side
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newNameInput,
                                    onValueChange = { newNameInput = it },
                                    label = { Text("Name (EN)", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .testTag("admin_new_name_field")
                                )
                                OutlinedTextField(
                                    value = newAmharicNameInput,
                                    onValueChange = { newAmharicNameInput = it },
                                    label = { Text("ስም (አማርኛ)", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.9f)
                                )
                            }

                            // Price, Stock & Unit Name side by side
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newPriceInput,
                                    onValueChange = { newPriceInput = it },
                                    label = { Text("Price (ETB)", fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = newStockInput,
                                    onValueChange = { newStockInput = it },
                                    label = { Text("Stock Qty", fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = newUnitNameInput,
                                    onValueChange = { newUnitNameInput = it },
                                    label = { Text("Unit (eg. kg)", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.9f)
                                )
                            }

                            // Description Input
                            OutlinedTextField(
                                value = newDescInput,
                                onValueChange = { newDescInput = it },
                                label = { Text("Description", fontSize = 11.sp) },
                                maxLines = 2,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                            )

                            // Nutritional Info Input
                            OutlinedTextField(
                                value = newNutInfoInput,
                                onValueChange = { newNutInfoInput = it },
                                label = { Text("Nutritional Info / Ingredients", fontSize = 11.sp) },
                                maxLines = 2,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                            )

                            // Image URL (Add Picture) Input
                            OutlinedTextField(
                                value = newImageUrlInput,
                                onValueChange = { newImageUrlInput = it },
                                label = { Text("Add Picture (URL)", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("admin_new_image_url_field")
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val parsedPrice = newPriceInput.toDoubleOrNull() ?: 0.0
                                val parsedStock = newStockInput.toDoubleOrNull() ?: 0.0
                                if (newIdInput.isBlank() || newNameInput.isBlank()) {
                                    viewModel.adminUpdateProduct("", -1.0, -1.0)
                                } else {
                                    val customProduct = Product(
                                        id = newIdInput.trim(),
                                        name = newNameInput.trim(),
                                        amharicName = newAmharicNameInput.trim(),
                                        category = newCategoryInput.trim(),
                                        pricePerKg = parsedPrice,
                                        stockKg = parsedStock,
                                        unitName = newUnitNameInput.trim(),
                                        description = newDescInput.trim(),
                                        imageUrl = newImageUrlInput.trim(),
                                        nutritionalInfo = newNutInfoInput.trim().ifBlank { "High quality local farm fresh product." }
                                    )
                                    viewModel.adminAddProduct(customProduct)
                                    isAddingProduct = false
                                }
                            },
                            modifier = Modifier.testTag("admin_save_new_product_button")
                        ) {
                            Text("Create Product")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { isAddingProduct = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // PERMANENT DELETION CONFIRMATION DIALOG
            if (productToDelete != null) {
                val prod = productToDelete!!
                AlertDialog(
                    onDismissRequest = { productToDelete = null },
                    title = {
                        Text(
                            text = "Delete Product?",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    text = {
                        Text(
                            text = "Are you absolutely sure you want to permanently delete '${prod.name}' (${prod.amharicName}) from the catalog?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.adminDeleteProduct(prod.id)
                                productToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("admin_confirm_delete_button")
                        ) {
                            Text("Yes, Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { productToDelete = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // ORDER DELETION CONFIRMATION DIALOG
            if (orderToDeleteId != null) {
                val ordId = orderToDeleteId!!
                AlertDialog(
                    onDismissRequest = { orderToDeleteId = null },
                    title = {
                        Text(
                            text = "Delete Order Record?",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    text = {
                        Text(
                            text = "Do you want to permanently delete Order #ORD-$ordId from database records?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.adminDeleteOrder(ordId)
                                orderToDeleteId = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { orderToDeleteId = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // CLEAR LOGS CONFIRMATION DIALOG
            if (showClearLogsDialog) {
                AlertDialog(
                    onDismissRequest = { showClearLogsDialog = false },
                    title = {
                        Text(
                            text = "Clear All Audit Logs?",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    text = {
                        Text(
                            text = "Are you sure you want to permanently purge all stock and catalog log entries from history?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.adminClearAllLogs()
                                showClearLogsDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Purge Logs")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearLogsDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // ADMIN NOTIFICATIONS DIALOG
            if (showAdminNotificationsDialog) {
                AlertDialog(
                    onDismissRequest = {
                        viewModel.markAllAdminNotificationsAsRead()
                        showAdminNotificationsDialog = false
                    },
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "System Notifications",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "የስርዓት እንቅስቃሴዎች",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                )
                            }
                            if (unreadAdminCount > 0) {
                                TextButton(
                                    onClick = { viewModel.markAllAdminNotificationsAsRead() }
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.DoneAll,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Mark Read",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    },
                    text = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                        ) {
                            if (adminNotifications.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "🔔",
                                        fontSize = 48.sp,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                    Text(
                                        text = "No updates",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "You're all caught up with orders.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(adminNotifications, key = { it.id }) { notification ->
                                        val dateStr = remember(notification.timestamp) {
                                            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                                            sdf.format(Date(notification.timestamp))
                                        }
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (notification.isRead) {
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                } else {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                                }
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            border = androidx.compose.foundation.BorderStroke(
                                                width = 1.dp,
                                                color = if (notification.isRead) Color.LightGray.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            when (notification.status) {
                                                                "Confirmed" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                                "Delivered" -> Color(0xFFE8F5E9)
                                                                "Cancelled" -> MaterialTheme.colorScheme.errorContainer
                                                                else -> MaterialTheme.colorScheme.secondaryContainer
                                                            }
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = when (notification.status) {
                                                            "Confirmed" -> Icons.Default.CheckCircle
                                                            "Delivered" -> Icons.Default.LocalShipping
                                                            "Cancelled" -> Icons.Default.Cancel
                                                            else -> Icons.Default.Notifications
                                                        },
                                                        contentDescription = null,
                                                        tint = when (notification.status) {
                                                            "Confirmed" -> MaterialTheme.colorScheme.primary
                                                            "Delivered" -> Color(0xFF2D7D32)
                                                            "Cancelled" -> Color.Red
                                                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                                                        },
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(10.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = notification.title,
                                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                            color = if (notification.isRead) Color.Gray else Color.Black
                                                        )
                                                        if (!notification.isRead) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(8.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color.Red)
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = notification.titleAmharic,
                                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                        color = if (notification.isRead) Color.Gray.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary,
                                                        fontSize = 11.sp
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = notification.message,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color.DarkGray
                                                    )
                                                    Text(
                                                        text = notification.messageAmharic,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color.DarkGray,
                                                        fontSize = 10.sp,
                                                        lineHeight = 12.sp
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = dateStr,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.Gray
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(4.dp))

                                                IconButton(
                                                    onClick = { viewModel.deleteNotification(notification.id) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Delete notification",
                                                        tint = Color.Gray,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.markAllAdminNotificationsAsRead()
                                showAdminNotificationsDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Close / ዝጋ")
                        }
                    }
                )
            }

            // CREDENTIALS CHANGE DIALOG (Change username and password)
            if (showProfileDialog) {
                AlertDialog(
                    onDismissRequest = { showProfileDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ManageAccounts,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Edit Portal Credentials",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Update your admin portal username and secure access password.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )

                            OutlinedTextField(
                                value = adminUsernameInput,
                                onValueChange = { adminUsernameInput = it },
                                label = { Text("Manager Username") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("admin_username_edit_field")
                            )

                            OutlinedTextField(
                                value = adminPasswordInput,
                                onValueChange = { adminPasswordInput = it },
                                label = { Text("New Password (blank to keep current)") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                        )
                                    }
                                },
                                visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("admin_password_edit_field")
                            )

                            if (adminPasswordInput.isNotEmpty()) {
                                OutlinedTextField(
                                    value = adminConfirmPasswordInput,
                                    onValueChange = { adminConfirmPasswordInput = it },
                                    label = { Text("Confirm New Password") },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("admin_confirm_password_field")
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (adminUsernameInput.trim().isEmpty()) {
                                    viewModel.setErrorMessage("Username cannot be empty")
                                    return@Button
                                }
                                if (adminPasswordInput.isNotEmpty() && adminPasswordInput != adminConfirmPasswordInput) {
                                    viewModel.setErrorMessage("Passwords do not match!")
                                    return@Button
                                }
                                viewModel.adminUpdateCredentials(
                                    newUsername = adminUsernameInput,
                                    newPasswordPlain = adminPasswordInput.ifEmpty { null }
                                )
                                showProfileDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("admin_save_credentials_button")
                        ) {
                            Text("Save Changes")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showProfileDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AdminProductCard(
    product: Product,
    onEditSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onQuickIncrease: () -> Unit,
    onQuickDecrease: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PremiumProductBanner(
                id = product.id,
                height = 52.dp,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
                imageUrl = product.imageUrl
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val formattedName = if (product.amharicName.isNotBlank()) {
                         "${product.name} (1 ${product.unitName}) • ${product.amharicName}"
                    } else {
                        "${product.name} (1 ${product.unitName})"
                    }
                    Text(
                        text = formattedName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (product.stockKg <= 0.0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFFEBEE))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("Out of Stock", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                        }
                    } else if (product.stockKg <= 10.0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFFFAEB))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("Low Stock", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB78103))
                        }
                    }
                }

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "1 ${product.unitName} Price: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${String.format("%.2f", product.pricePerKg)} ETB",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Stock: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    val isLow = product.stockKg <= 10.0
                    Text(
                        text = "${String.format("%.1f", product.stockKg)} ${product.unitName}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isLow) Color.Red else MaterialTheme.colorScheme.onSurface
                        )
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Inline Quick +/- Stock increment and decrement buttons
                    val delta = if (product.unitName.lowercase() in listOf("pack", "piece", "egg", "eggs")) 1.0 else 5.0
                    IconButton(
                        onClick = onQuickDecrease,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Quick decrease stock by $delta",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "${if (delta % 1.0 == 0.0) delta.toInt() else delta}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    IconButton(
                        onClick = onQuickIncrease,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Quick increase stock by $delta",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onEditSelected,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .testTag("admin_edit_action_${product.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit product settings",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onDeleteSelected,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .testTag("admin_delete_action_${product.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete product",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AdminOrderCard(
    order: Order,
    onUpdateStatus: (Long, String) -> Unit,
    onDeleteOrder: () -> Unit
) {
    val dateString = remember(order.timestamp) {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(order.timestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Order #ORD-${order.id}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(text = "By Customer: ${order.customerUsername}", style = MaterialTheme.typography.bodySmall)
                }
                Text(text = dateString, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                PremiumProductBanner(
                    id = order.productId,
                    height = 36.dp,
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = "${order.productName} (${order.amharicName})", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    Text(text = "Requested Weight: ${order.quantityKg} units • Total: ${String.format("%.2f", order.totalPrice)} ETB", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "🏠 Delivery Address: ${order.deliveryAddress}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Dropdown or sequential action buttons to switch Order States
                Text(
                    text = "Status: ${order.status}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = when (order.status) {
                            "Delivered" -> Color(0xFF2E7D32)
                            "Cancelled" -> MaterialTheme.colorScheme.error
                            "Confirmed" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel action for pending/confirmed
                    if (order.status == "Pending" || order.status == "Confirmed") {
                        IconButton(
                            onClick = { onUpdateStatus(order.id, "Cancelled") },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "Cancel order",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (order.status == "Pending") {
                        Button(
                            onClick = { onUpdateStatus(order.id, "Confirmed") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Process", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (order.status == "Confirmed" || order.status == "Pending") {
                        Button(
                            onClick = { onUpdateStatus(order.id, "Delivered") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Deliver", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (order.status == "Delivered") {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFE8F5E9))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delivered", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            }
                        }
                    }

                    // Delete order completely if delivered or cancelled
                    if (order.status == "Delivered" || order.status == "Cancelled") {
                        IconButton(
                            onClick = onDeleteOrder,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete order record permanently",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuditLogCard(log: StockLog) {
    val dateString = remember(log.timestamp) {
        val sdf = SimpleDateFormat("HH:mm:ss - MMM dd", Locale.getDefault())
        sdf.format(Date(log.timestamp))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PremiumProductBanner(
                        id = log.productId,
                        height = 22.dp,
                        modifier = Modifier.size(22.dp).clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = log.productName, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                }
                Text(text = dateString, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = log.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Operator: ${log.operator}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )

                // Stock change indicator bubble
                val isPositive = log.changeAmount >= 0.0
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isPositive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isPositive) "+${String.format("%.1f", log.changeAmount)} stock"
                               else "${String.format("%.1f", log.changeAmount)} stock",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPositive) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
            }
        }
    }
}


