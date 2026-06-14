package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Order
import com.example.data.model.Product
import com.example.data.model.Notification
import com.example.ui.viewmodel.DairyViewModel
import com.example.ui.viewmodel.AppScreen
import com.example.ui.theme.FarmGreenPrimary
import com.example.ui.theme.ButterGoldSecondary
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerScreen(viewModel: DairyViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val guestName by viewModel.guestName.collectAsState()
    val products by viewModel.productsList.collectAsState()
    val orders by viewModel.customerOrdersList.collectAsState()
    val cart by viewModel.cartMap.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val apiEnabled by viewModel.apiEnabled.collectAsState()
    val apiSyncing by viewModel.apiSyncing.collectAsState()

    val notifications by viewModel.customerNotificationsList.collectAsState()
    val unreadCount = remember(notifications) { notifications.count { !it.isRead } }
    var showNotificationsDialog by remember { mutableStateOf(false) }

    var activeTab by remember { mutableStateOf(0) } // 0: Browse, 1: Track Orders

    // Auto-sync products when customer opens the Browse tab
    LaunchedEffect(activeTab) {
        if (activeTab == 0) {
            viewModel.syncPullProducts(silent = true)
        }
    }
    var selectedCategory by remember { mutableStateOf("All") }

    // Detailed Modal states (assigned when clicking "1kg and the prize")
    var detailProduct by remember { mutableStateOf<Product?>(null) }
    var detailQuantity by remember { mutableStateOf(1.0) }
    var deliveryAddressInput by remember { mutableStateOf("") }
    
    // Guest checkout specific fields
    var guestNameInput by remember { mutableStateOf("") }
    var guestPhoneInput by remember { mutableStateOf("") }
    
    var isCheckingOutDirectly by remember { mutableStateOf(false) }

    // Reset guest fields when modal opens
    LaunchedEffect(detailProduct) {
        if (detailProduct != null) {
            guestNameInput = ""
            guestPhoneInput = ""
            deliveryAddressInput = ""
        }
    }

    // Aggregate category groups
    val categories = remember(products) {
        listOf("All") + products.map { it.category }.distinct()
    }

    Scaffold(
        topBar = {
            if (activeTab == 0) {
                Surface(
                    color = FarmGreenPrimary,
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .statusBarsPadding()
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header Row: logo + title & logout/admin buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "🥛",
                                    fontSize = 28.sp,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Column {
                                    Text(
                                        text = "ANGEL ETHIOPIAN CHEESE",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            letterSpacing = 0.5.sp
                                        )
                                    )
                                    Text(
                                        text = "ኤንጀል የኢትዮጵያ አይብ",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = ButterGoldSecondary
                                        )
                                    )
                                }
                            }

                            // Actions: Admin Portal login / logout / notifications
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Manual Refresh/Sync Button
                                IconButton(
                                    onClick = {
                                        viewModel.syncPullProducts()
                                    },
                                    modifier = Modifier.testTag("customer_refresh_catalog_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh Catalog / ሲንክ",
                                        tint = Color.White
                                    )
                                }

                                // Notification Bell Icon
                                Box(modifier = Modifier.padding(end = 4.dp)) {
                                    IconButton(
                                        onClick = { showNotificationsDialog = true },
                                        modifier = Modifier.testTag("customer_notifications_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Notifications,
                                            contentDescription = "Notifications",
                                            tint = if (unreadCount > 0) ButterGoldSecondary else Color.White
                                        )
                                    }
                                    if (unreadCount > 0) {
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
                                                text = unreadCount.toString(),
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                if (currentUser != null) {
                                    IconButton(
                                        onClick = { viewModel.logout() },
                                        modifier = Modifier.testTag("logout_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Logout,
                                            contentDescription = "Log out",
                                            tint = Color.White
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.navigateTo(AppScreen.Login) },
                                        modifier = Modifier.testTag("login_button").height(28.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ButterGoldSecondary,
                                            contentColor = FarmGreenPrimary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Login,
                                            contentDescription = "Admin Staff Portal Login",
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Admin Portal", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.95f)
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.25f))
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        val context = LocalContext.current
                        val dialPhone = { number: String ->
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                    data = android.net.Uri.parse("tel:$number")
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {}
                        }

                        // Call Action Chips and greeting subtitle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(0.55f)) {
                                val greetingName = currentUser?.fullName ?: guestName ?: "Guest"
                                Text(
                                    text = "Hello, $greetingName 👋",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "📍 Hawassa, Bishoftu, Shashemene & Addis Abeba (Free Delivery)\n    ሀዋሳ ፣ ቢሾፍቱ ፣ ሻሸመኔ እና አዲስ አበባ (ነጻ ማድረሻ)",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = ButterGoldSecondary,
                                        fontSize = 12.sp,
                                        lineHeight = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Call Us",
                                        modifier = Modifier.size(14.dp),
                                        tint = ButterGoldSecondary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Call Us / ይደውሉልን",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "0911015225",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        modifier = Modifier.clickable { dialPhone("0911015225") }
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(width = 1.dp, height = 12.dp)
                                            .background(Color.White.copy(alpha = 0.35f))
                                    )
                                    Text(
                                        text = "0979209456",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        modifier = Modifier.clickable { dialPhone("0979209456") }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // For Tab 1 (Track Orders)
                LargeTopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val greetingName = currentUser?.fullName ?: guestName ?: "Guest"
                                Text(
                                    text = "Hello, $greetingName",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (currentUser != null) {
                                        AVATAR_LIST.getOrNull(currentUser?.profileIndex ?: 0)?.first ?: "🥛"
                                    } else {
                                        "🥛"
                                    },
                                    fontSize = 18.sp
                                )
                            }
                            Text(
                                        text = "📍 Hawassa, Bishoftu, Shashemene & Addis Abeba (Free Delivery)",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    },
                    actions = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Notification Bell Icon (Track Orders)
                            Box(modifier = Modifier.padding(end = 4.dp)) {
                                IconButton(
                                    onClick = { showNotificationsDialog = true },
                                    modifier = Modifier.testTag("customer_notifications_button_track")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Notifications",
                                        tint = if (unreadCount > 0) FarmGreenPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                if (unreadCount > 0) {
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
                                            text = unreadCount.toString(),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            if (currentUser != null) {
                                IconButton(
                                    onClick = { viewModel.logout() },
                                    modifier = Modifier.testTag("logout_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Logout,
                                        contentDescription = "Log out",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.navigateTo(AppScreen.Login) },
                                    modifier = Modifier.testTag("login_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Login,
                                        contentDescription = "Admin Staff Portal Login",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Admin Portal", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        },
        bottomBar = {
            // Screen switching tab bar
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.Storefront, contentDescription = "Shop") },
                    label = { Text("Browse Items") },
                    modifier = Modifier.testTag("nav_browse_tab")
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
                            Icon(Icons.Default.LocalShipping, contentDescription = "Shipments")
                        }
                    },
                    label = { Text("Track Orders") },
                    modifier = Modifier.testTag("nav_track_tab")
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Inline Alert system for purchases/errors
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
                                Icon(Icons.Default.Close, contentDescription = "Close")
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
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    }
                }

                if (activeTab == 0) {
                    // TAB 0: Browse shelf

                    val filteredProducts = remember(products, selectedCategory) {
                        if (selectedCategory == "All") products
                        else products.filter { it.category == selectedCategory }
                    }

                    if (filteredProducts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🥛", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No products matching category.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("product_list_container"),
                            contentPadding = PaddingValues(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredProducts, key = { it.id }) { product ->
                                ProductShelfCard(
                                    product = product,
                                    onPressPriceTag = {
                                        detailProduct = product
                                        detailQuantity = 1.0
                                        deliveryAddressInput = ""
                                        isCheckingOutDirectly = false
                                        viewModel.clearStatus()
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // TAB 1: Track order states
                    if (orders.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🗳️", fontSize = 54.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("You haven't placed any orders yet.", style = MaterialTheme.typography.titleMedium)
                                Text("Browse our catalog to order fresh quality treats!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("customer_orders_container"),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(orders, key = { it.id }) { order ->
                                CustomerOrderCard(order = order)
                            }
                        }
                    }
                }
            }

            // DETAILED PRODUCT BOTTOM PANEL / DIALOG OR DRAWER ("press 1kg you will see the more")
            if (detailProduct != null) {
                val prod = detailProduct!!
                AlertDialog(
                    onDismissRequest = { detailProduct = null },
                    confirmButton = {
                        Button(
                            onClick = {
                                isCheckingOutDirectly = true
                            },
                            modifier = Modifier.testTag("direct_buy_next_step")
                        ) {
                            Text("Secure Purchase")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { detailProduct = null }) {
                            Text("Go Back")
                        }
                    },
                    icon = {
                        PremiumProductBanner(
                            id = prod.id,
                            height = 110.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp)),
                            imageUrl = prod.imageUrl
                        )
                    },
                    title = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Format amharic elegantly with explicit design size details
                            val titleText = if (prod.amharicName.isNotBlank()) {
                                "${prod.name} (1 ${prod.unitName}) • ${prod.amharicName}"
                            } else {
                                "${prod.name} (1 ${prod.unitName})"
                            }
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 20.sp),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = prod.category,
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Center
                            )
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize()
                        ) {
                            if (!isCheckingOutDirectly) {
                                // DETAILED STAGE ("see the more")
                                Text(
                                    text = prod.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "🌾 Nutrition & Origin • ስነ-ምግብ",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            prod.nutritionalInfo,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Stock Warning Indicator (Real-Time)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                    val isLow = prod.stockKg <= 10.0
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (isLow) Color.Red else Color.Green)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isLow) "LOW STOCK: Only ${String.format("%.1f", prod.stockKg)} ${prod.unitName} remaining!"
                                               else "In Stock: ${String.format("%.1f", prod.stockKg)} ${prod.unitName} available",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isLow) Color.Red else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }

                                // Interactive Weight Counter Calculator
                                Row(
                                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    IconButton(
                                        onClick = { if (detailQuantity > 0.5) detailQuantity -= 0.5 },
                                        enabled = detailQuantity > 0.5
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Less weight")
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "${String.format("%.1f", detailQuantity)} ${prod.unitName}",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "Selected Weight",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }

                                    IconButton(
                                        onClick = { if (detailQuantity < prod.stockKg) detailQuantity += 0.5 },
                                        enabled = detailQuantity < prod.stockKg
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "More weight")
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Real-Time Computed Pricing Tag
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Unified Unit Price:",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        "1 ${prod.unitName} = ${String.format("%.2f", prod.pricePerKg)} ETB",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Total Amount:",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                    )
                                    Text(
                                        "${String.format("%.2f", prod.pricePerKg * detailQuantity)} ETB",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                    )
                                }
                            } else {
                                // SECURE DIRECT CHECKOUT STAGE
                                if (currentUser == null) {
                                    Text(
                                        "Guest Details • ባለቤት መረጃ",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = guestNameInput,
                                        onValueChange = { guestNameInput = it },
                                        label = { Text("Your Full Name * • ስም") },
                                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("guest_name_input")
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = guestPhoneInput,
                                        onValueChange = { guestPhoneInput = it },
                                        label = { Text("Phone Number * • ስልክ ቁጥር") },
                                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("guest_phone_input")
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                } else {
                                    Text(
                                        "Provide delivery address to dispatch order immediately.\n🎁 FREE Delivery to Hawassa, Bishoftu, Shashemene, and Addis Abeba!",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                OutlinedTextField(
                                    value = deliveryAddressInput,
                                    onValueChange = { deliveryAddressInput = it },
                                    label = { Text("Delivery Address / Local Pickup Store • አድራሻ") },
                                    leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                                    singleLine = false,
                                    maxLines = 3,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("delivery_address_input")
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Quantity:", style = MaterialTheme.typography.bodySmall)
                                            Text("${String.format("%.1f", detailQuantity)} ${prod.unitName}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                        }
                                        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Amount Due:", style = MaterialTheme.typography.bodySmall)
                                            Text("${String.format("%.2f", prod.pricePerKg * detailQuantity)} ETB", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        if (currentUser == null && (guestNameInput.isBlank() || guestPhoneInput.isBlank())) {
                                            // Trigger validation error in ViewModel helper
                                            viewModel.checkoutCart(
                                                deliveryAddress = deliveryAddressInput,
                                                guestName = guestNameInput,
                                                guestPhone = guestPhoneInput
                                            )
                                        } else {
                                            viewModel.updateCartQuantity(prod.id, detailQuantity)
                                            if (currentUser != null) {
                                                viewModel.checkoutCart(deliveryAddressInput)
                                            } else {
                                                viewModel.checkoutCart(
                                                    deliveryAddress = deliveryAddressInput,
                                                    guestName = guestNameInput,
                                                    guestPhone = guestPhoneInput
                                                )
                                            }
                                            detailProduct = null
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("submit_direct_purchase"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.VerifiedUser, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Confirm Purchase • እዘዝ")
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                TextButton(
                                    onClick = { isCheckingOutDirectly = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Change Quantity / View Details")
                                }
                            }
                        }
                    }
                )
            }

            // Notification Center Dialog
            if (showNotificationsDialog) {
                AlertDialog(
                    onDismissRequest = {
                        viewModel.markAllNotificationsAsRead()
                        showNotificationsDialog = false
                    },
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Notifications Center",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "የመልዕክት ማዕከል",
                                    style = MaterialTheme.typography.bodySmall.copy(color = FarmGreenPrimary, fontWeight = FontWeight.Bold)
                                )
                            }
                            if (unreadCount > 0) {
                                TextButton(
                                    onClick = { viewModel.markAllNotificationsAsRead() }
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp), tint = FarmGreenPrimary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Mark Read • አንብቤያለሁ", fontSize = 11.sp, color = FarmGreenPrimary, fontWeight = FontWeight.Bold)
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
                            if (notifications.isEmpty()) {
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
                                        text = "All caught up!",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "No order status updates yet.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray
                                    )
                                    Text(
                                        text = "ምንም አዲስ የእንቅስቃሴ መልዕክት የለም።",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(notifications, key = { it.id }) { notification ->
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
                                                    FarmGreenPrimary.copy(alpha = 0.08f)
                                                }
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            border = androidx.compose.foundation.BorderStroke(
                                                width = 1.dp,
                                                color = if (notification.isRead) Color.LightGray.copy(alpha = 0.5f) else FarmGreenPrimary.copy(alpha = 0.3f)
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
                                                                "Confirmed" -> FarmGreenPrimary.copy(alpha = 0.2f)
                                                                "Delivered" -> Color(0xFFE8F5E9)
                                                                "Cancelled" -> MaterialTheme.colorScheme.errorContainer
                                                                else -> MaterialTheme.colorScheme.primaryContainer
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
                                                            "Confirmed" -> FarmGreenPrimary
                                                            "Delivered" -> Color(0xFF2D7D32)
                                                            "Cancelled" -> Color.Red
                                                            else -> MaterialTheme.colorScheme.primary
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
                                                        color = if (notification.isRead) Color.Gray.copy(alpha = 0.8f) else FarmGreenPrimary,
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
                                viewModel.markAllNotificationsAsRead()
                                showNotificationsDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = FarmGreenPrimary)
                        ) {
                            Text("Close / ዝጋ")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ProductShelfCard(
    product: Product,
    onPressPriceTag: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // Visual Header Representing Product (Premium Minimalist Luxury Style)
            PremiumProductBanner(
                id = product.id,
                height = 80.dp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                imageUrl = product.imageUrl
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Category Bubble
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = product.category,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Amharic Naming for all products with explicit size indicators and larger bold font size
            val formattedName = if (product.amharicName.isNotBlank()) {
                "${product.name} (1 ${product.unitName}) • ${product.amharicName}"
            } else {
                "${product.name} (1 ${product.unitName})"
            }
            Text(
                text = formattedName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = product.description,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 2.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Clickable Price Tag and Stock Pill
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .clickable { onPressPriceTag() }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .testTag("price_badge_trigger_${product.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "1 ${product.unitName}: ",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "${String.format("%.0f", product.pricePerKg)} ETB",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "View details",
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (product.stockKg <= 10) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFFE5E5))
                            .padding(vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Muted Stock",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Red
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
fun CustomerOrderCard(order: Order) {
    val dateTimeStamp = remember(order.timestamp) {
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Receipt #ORD-${order.id}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                )
                Text(text = dateTimeStamp, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                PremiumProductBanner(
                    id = order.productId,
                    height = 36.dp,
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    val displayedTitle = if (order.amharicName.isNotBlank()) {
                        "${order.productName} (${order.amharicName})"
                    } else {
                        order.productName
                    }
                    Text(text = displayedTitle, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    Text(text = "Ordered Quantity: ${order.quantityKg} units", style = MaterialTheme.typography.bodySmall)
                }
            }

            Divider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Total Payment", style = MaterialTheme.typography.bodySmall)
                    Text("${String.format("%.2f", order.totalPrice)} ETB", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary))
                }

                // Delivery transit state
                val (color, label) = when (order.status) {
                    "Pending" -> Pair(Color(0xFFE2A108), "Pending Approved • በመጠባበቅ ላይ")
                    "Confirmed" -> Pair(MaterialTheme.colorScheme.primary, "Preparing • በመዘጋጀት ላይ")
                    "Delivered" -> Pair(Color(0xFF2E7D32), "Delivered • ደርሷል")
                    else -> Pair(Color.Gray, order.status)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = 0.12f))
                        .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Utility emojis to beautify visual representation of items without requiring heavy image assets
fun getEmojiForProduct(id: String): String {
    return when (id) {
        "butter" -> "🧈"
        "buttermilk" -> "🥛"
        "cheese" -> "🧀"
        "milk" -> "🥛"
        "yogurt" -> "🍶"
        "egg" -> "🥚"
        "honey" -> "🍯"
        "enjera" -> "🫓"
        "pepper" -> "🫙🌶️"
        "shiro" -> "🥘"
        else -> "🌾"
    }
}

@Composable
fun PhoneCallChip(
    number: String,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Call,
            contentDescription = "Call $number",
            modifier = Modifier.size(16.dp),
            tint = ButterGoldSecondary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.85f)
            )
            Text(
                text = number,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}

@Composable
fun PremiumProductBanner(
    id: String,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    imageUrl: String? = null
) {
    val finalImageUrl = if (!imageUrl.isNullOrBlank()) {
        imageUrl
    } else {
        when (id) {
            "cheese" -> "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRcKuhd5LRb_SK2fTJcsRTeCcdygNHLHA2sH0hKgCxv_w&s=10"
            "butter" -> "https://images.unsplash.com/photo-1589985270826-4b7bb135bc9d?auto=format&fit=crop&q=80&w=600"
            "buttermilk" -> "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTWa4qTD-95LHnF8oir830tm1V6S-KSrlTXnhH_Sdz9L7j1jOc8sGcqQAbu&s=10"
            "milk" -> "https://images.unsplash.com/photo-1563636619-e9143da7973b?auto=format&fit=crop&q=80&w=600"
            "yogurt" -> "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRwPgUX2ePcfMBs5HokGG9C9P999LsPMLhkjsiWv4yYkw&s=10"
            "egg" -> "https://images.unsplash.com/photo-1506976785307-8732e854ad03?auto=format&fit=crop&q=80&w=600"
            "honey" -> "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQ7o42ODyVyA3-BWT0kkCmQwTjtl-vb9i4Kr5ThfTaufg&s=10"
            "enjera" -> "https://i.ytimg.com/vi/g1nGsDUKHFU/sddefault.jpg"
            "shiro" -> "https://kamrach.com/images/thumbnails/288/288/detailed/15/shero_119h-wu.jpg"
            else -> null
        }
    }

    val gradient = when (id) {
        "butter" -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFFFBEB), Color(0xFFFDE68A)))
        "buttermilk" -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFF0FDF4), Color(0xFFBBF7D0)))
        "cheese" -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFFF7ED), Color(0xFFFED7AA)))
        "milk" -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFF0F9FF), Color(0xFFBAE6FD)))
        "yogurt" -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFAF5FF), Color(0xFFE9D5FF)))
        "egg" -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFFFAF0), Color(0xFFF5DEB3)))
        "honey" -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFFF7ED), Color(0xFFD97706)))
        "enjera" -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFAFAF9), Color(0xFFE7E5E4)))
        "shiro" -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFFFF1F2), Color(0xFFFDA4AF)))
        else -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFF1F5F9), Color(0xFFCBD5E1)))
    }

    val monogram = when (id) {
        "butter" -> "KB"
        "buttermilk" -> "AR"
        "cheese" -> "AY"
        "milk" -> "WT"
        "yogurt" -> "EG"
        "egg" -> "EK"
        "honey" -> "MR"
        "enjera" -> "EJ"
        "shiro" -> "SH"
        else -> "AG"
    }

    val brand = when (id) {
        "butter" -> "ESTATE HERB SPICED"
        "buttermilk" -> "REFINED CHURN"
        "cheese" -> "TRADITIONAL SELECT"
        "milk" -> "MOUNTAIN HIGHLAND"
        "yogurt" -> "CLAY SEED COOPERATIVE"
        "egg" -> "ORGANIC SPAN PASTURE"
        "honey" -> "BALE BIOSPHERE RESERVES"
        "enjera" -> "PURE TEFF SOURDOUGH"
        "shiro" -> "GARDEN GROUND SPICE"
        else -> "ANGEL SELECTION"
    }

    val emoji = getEmojiForProduct(id)
    val isCompact = height < 60.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        if (finalImageUrl != null) {
            AsyncImage(
                model = finalImageUrl,
                contentDescription = brand,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Internal luxury border/outline
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isCompact) 3.dp else 6.dp)
                    .border(
                        width = 0.5.dp,
                        color = Color(0x22D97706),
                        shape = RoundedCornerShape(if (isCompact) 4.dp else 6.dp)
                    )
            )

            val textColor = Color(0xFF1E293B)
            val subColor = Color(0xFF64748B)

            if (isCompact) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(2.dp)
                ) {
                    Text(
                        text = monogram,
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp,
                            color = textColor
                        )
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(text = emoji, fontSize = 14.sp)
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = monogram,
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                            fontWeight = FontWeight.Light,
                            fontSize = if (height > 100.dp) 32.sp else 22.sp,
                            letterSpacing = 4.sp,
                            color = textColor
                        )
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(1.dp)
                            .background(Color(0x441E293B))
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = brand,
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (height > 100.dp) 8.sp else 6.sp,
                            letterSpacing = 1.sp,
                            color = subColor
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Small circle emoji badge (only display if no image)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(if (height > 100.dp) 36.dp else 24.dp)
                        .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(50))
                        .border(0.5.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emoji,
                        fontSize = if (height > 100.dp) 18.sp else 12.sp
                    )
                }
            }
        }
    }
}

