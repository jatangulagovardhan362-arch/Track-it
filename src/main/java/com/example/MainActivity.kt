package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.shadow
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import coil.compose.AsyncImage
import com.example.data.AppDatabase
import com.example.data.PriceHistoryEntity
import com.example.data.ProductEntity
import com.example.data.ProductRepository
import com.example.ui.AlertEvent
import com.example.ui.ProductViewModel
import com.example.ui.ProductViewModelFactory
import com.example.ui.theme.MyApplicationTheme
import com.example.util.NotificationHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val db by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "product_tracker_database"
        ).fallbackToDestructiveMigration().build()
    }

    private val repository by lazy { ProductRepository(db.productDao) }
    private val viewModel: ProductViewModel by viewModels { ProductViewModelFactory(repository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create Channel for price drop alerts
        NotificationHelper.createNotificationChannel(this)

        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel)
            }
        }
    }
}

// Sealed class for App Navigation State (inside the activity/single-view scaffold)
sealed class TabScreen(val label: String, val icon: ImageVector) {
    object Home : TabScreen("Home", Icons.Default.TrendingDown)
    object Add : TabScreen("Add Tracking", Icons.Default.AddCircleOutline)
    object Alerts : TabScreen("Alerts Hub", Icons.Default.Notifications)
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainAppScreen(viewModel: ProductViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf<TabScreen>(TabScreen.Home) }
    val products by viewModel.products.collectAsStateWithLifecycle(initialValue = emptyList())
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isAddingProduct by viewModel.isAddingProduct.collectAsStateWithLifecycle()
    val selectedProduct by viewModel.selectedProduct.collectAsStateWithLifecycle()
    val selectedProductHistory by viewModel.selectedProductHistory.collectAsStateWithLifecycle()

    // Request post notifications permission for Android 13+
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "Notifications enabled for price drops!", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Liquid glass backdrop drawing: floating blurred organic gradient circles
                drawRect(color = Color(0xFFF0F5FF)) // base clean background

                // Glow blob 1 (Top right)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x3B60A5FA), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.15f),
                        radius = size.width * 0.7f
                    ),
                    center = Offset(size.width * 0.85f, size.height * 0.15f),
                    radius = size.width * 0.7f
                )

                // Glow blob 2 (Middle left - Purple/Indigo)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x2D8B5CF6), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.55f),
                        radius = size.width * 0.8f
                    ),
                    center = Offset(size.width * 0.15f, size.height * 0.55f),
                    radius = size.width * 0.8f
                )

                // Glow blob 3 (Bottom)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x25EC4899), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.9f),
                        radius = size.width * 0.6f
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.9f),
                    radius = size.width * 0.6f
                )
            }
    ) {
        Scaffold(
            containerColor = Color.Transparent, // fully dynamic backdrop transparent
            bottomBar = {
                LiquidGlassBottomBar(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Horizontal state swapper
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        slideInHorizontally(
                            initialOffsetX = { x -> if (targetState.ordinal() > initialState.ordinal()) x else -x },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                        ) with slideOutHorizontally(
                            targetOffsetX = { x -> if (targetState.ordinal() > initialState.ordinal()) -x else x },
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                ) { screen ->
                    when (screen) {
                        is TabScreen.Home -> HomeScreen(
                            products = products,
                            viewModel = viewModel,
                            onProductSelect = { viewModel.selectProduct(it) }
                        )
                        is TabScreen.Add -> AddProductScreen(
                            viewModel = viewModel,
                            onProductAdded = { currentTab = TabScreen.Home }
                        )
                        is TabScreen.Alerts -> AlertsHubScreen(
                            alerts = alerts
                        )
                    }
                }

                // Global interactive glass scanning overlay
                if (isRefreshing) {
                    ScanningOverlayGlass()
                }

                // Simple slide up dialog for product details (iOS card style)
                AnimatedVisibility(
                    visible = selectedProduct != null,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
                    ) + fadeIn(),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                    ) + fadeOut()
                ) {
                    selectedProduct?.let { product ->
                        ProductDetailDialog(
                            product = product,
                            history = selectedProductHistory,
                            onDismiss = { viewModel.deselectProduct() },
                            onDelete = {
                                viewModel.deleteProduct(product.id)
                            },
                            onToggleAlert = {
                                viewModel.toggleTracking(product)
                            },
                            onForceDrop = {
                                viewModel.testForcePriceDrop(context, product)
                                Toast.makeText(context, "⚡ Price drop simulated!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

// Extension to get index representation for swipe animation sorting
private fun TabScreen.ordinal(): Int = when (this) {
    is TabScreen.Home -> 0
    is TabScreen.Add -> 1
    is TabScreen.Alerts -> 2
}

@Composable
fun HomeScreen(
    products: List<ProductEntity>,
    viewModel: ProductViewModel,
    onProductSelect: (ProductEntity) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Price Pulse",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = (-1.5).sp,
                            color = Color(0xFF1E293B)
                        )
                    )
                    Text(
                        text = "MARKET RADAR WATCH",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    )
                }

                // Apple/iOS style Refresh Radar button
                GlassCircleButton(
                    icon = Icons.Default.Radar,
                    onClick = {
                        viewModel.simulateRefreshAll(context)
                        Toast.makeText(context, "Scanning retail price APIs...", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        if (products.isEmpty()) {
            item {
                GlassMorphismCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingDown,
                            contentDescription = "Empty Watchlist",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Products Tracked yet",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155),
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Click 'Add Tracking' at the bottom tab to paste a URL from Amazon, Flipkart or Myntra to begin watching price history cuts!",
                            textAlign = TextAlign.Center,
                            color = Color(0xFF64748B),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        } else {
            // Hero Spotlight Card (The lowest ever or highest drop item)
            val spot = products.find { it.currentPrice <= it.lowestPrice } ?: products.first()
            item {
                Text(
                    text = "FEATURED OPPORTUNITY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF475569),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                SpotlightProductCard(
                    product = spot,
                    onClick = { onProductSelect(spot) }
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "WATCHING NOW",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF475569),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Active: ${products.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3B82F6)
                    )
                }
            }

            items(products) { product ->
                StandardProductGlassRow(
                    product = product,
                    onClick = { onProductSelect(product) }
                )
            }
        }

        // Home page elegant Watermark signature
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // watermark with styled glass pill container
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0x33FFFFFF))
                        .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(32.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "made by ⚡GOPI⚡",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF64748B),
                        letterSpacing = 1.2.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Price History Tracker Engine v1.0",
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun SpotlightProductCard(
    product: ProductEntity,
    onClick: () -> Unit
) {
    val dropPercent = if (product.highestPrice > 0) {
        ((product.highestPrice - product.currentPrice) / product.highestPrice * 100).toInt()
    } else 0

    GlassMorphismCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("spotlight_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .padding(2.dp)
                    ) {
                        AsyncImage(
                            model = product.imageUrl,
                            contentDescription = product.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column {
                        val merchantColor = getMerchantColor(product.merchant)
                        Text(
                            text = "${product.merchant} Deal",
                            color = merchantColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = product.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1E293B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Auto-scanning active",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // Apple-style drop rating pill or Glowing Lowest Price badge
                if (product.currentPrice <= product.lowestPrice) {
                    GlowingLowestPriceBadge(text = "LOWEST EVER")
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFDCFCE7))
                            .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "BIG OFFER",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF15803D),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Large price indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "₹${formatPrice(product.currentPrice)}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "₹${formatPrice(product.highestPrice)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF94A3B8),
                    textDecoration = TextDecoration.LineThrough,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                if (dropPercent > 0) {
                    Text(
                        text = "-$dropPercent%",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF22C55E),
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mini historical sparks simulation underlay (6 bars)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Build heights proportional
                    val prices = listOf(1.0f, 0.85f, 0.70f, 0.73f, 0.58f, 0.45f)
                    prices.forEachIndexed { index, heightFactor ->
                        val isLast = index == prices.size - 1
                        val barColor = if (isLast) Color(0xFF3B82F6) else Color(0x1F3B82F6)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(heightFactor)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(barColor)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("JAN", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                Text("FEB", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                Text("MAR", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                Text("APR", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                Text("MAY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                Text("NOW", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF3B82F6))
            }
        }
    }
}

@Composable
fun StandardProductGlassRow(
    product: ProductEntity,
    onClick: () -> Unit
) {
    val dropPercent = if (product.highestPrice > 0) {
        ((product.highestPrice - product.currentPrice) / product.highestPrice * 100).toInt()
    } else 0
    val isLowest = product.currentPrice <= product.lowestPrice

    GlassMorphismCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("product_row_${product.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Product Brand Badge Circle (Glass styling)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF1F3F6))
            ) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Small corner merchant shortcut icon
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.BottomEnd)
                        .background(getMerchantColor(product.merchant), RoundedCornerShape(topStart = 4.dp))
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${product.merchant} • Tracking Enabled",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹${formatPrice(product.currentPrice)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0F172A)
                )

                if (isLowest) {
                    GlowingLowestPriceBadge(
                        text = "LOWEST EVER",
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "STABLE",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductScreen(
    viewModel: ProductViewModel,
    onProductAdded: () -> Unit
) {
    var rawUrl by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var customMerchant by remember { mutableStateOf("Amazon") }
    var currentPrice by remember { mutableStateOf("") }
    var targetPrice by remember { mutableStateOf("") }
    val isAddingProduct by viewModel.isAddingProduct.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val quickDemoUrls = listOf(
        Triple("https://www.amazon.in/Sony-WH-1000XM5", "Sony WH-1000XM5 Headphones", "Amazon"),
        Triple("https://www.flipkart.com/iphone-15-pro", "iPhone 15 Pro Titanium", "Flipkart"),
        Triple("https://www.myntra.com/shoes/nike", "Nike Air Jordan Sneaker", "Myntra")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Add Tracker",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = (-1.5).sp,
                    color = Color(0xFF1E293B)
                )
            )
            Text(
                text = "MICRO-SCAN RETAIL PIPELINES",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            )
        }

        item {
            GlassMorphismCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Paste Live URL link below",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )

                    OutlinedTextField(
                        value = rawUrl,
                        onValueChange = { rawUrl = it },
                        placeholder = { Text("https://www.amazon.in/dp/...", fontSize = 13.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("url_input_field"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0x4D3B82F6)
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Customize Tracker Overrides (Optional)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF64748B),
                        letterSpacing = 0.5.sp
                    )

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Product Label", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = currentPrice,
                            onValueChange = { currentPrice = it },
                            label = { Text("Current Price (₹)", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = targetPrice,
                            onValueChange = { targetPrice = it },
                            label = { Text("Alert Limit (₹)", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isAddingProduct) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF3B82F6))
                        }
                    } else {
                        Button(
                            onClick = {
                                if (rawUrl.isEmpty()) {
                                    return@Button
                                }
                                val cPrice = currentPrice.toDoubleOrNull()
                                val tPrice = targetPrice.toDoubleOrNull()
                                viewModel.addProduct(rawUrl, title, customMerchant, cPrice, tPrice)
                                onProductAdded()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .shadow(8.dp, RoundedCornerShape(16.dp))
                                .testTag("validate_add_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Add, null)
                                Text("Engage Glass Scraper", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Demo Shortcut pills
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                Text(
                    text = "TAP PRESETS TO FILL DEMO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF475569),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickDemoUrls.forEach { (url, label, brand) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x3BFFFFFF))
                                .border(1.dp, Color(0x3B3B82F6), RoundedCornerShape(12.dp))
                                .clickable {
                                    rawUrl = url
                                    title = label
                                    customMerchant = brand
                                    currentPrice = when (brand) {
                                        "Amazon" -> "24990"
                                        "Flipkart" -> "112000"
                                        else -> "8499"
                                    }
                                    targetPrice = when (brand) {
                                        "Amazon" -> "24000"
                                        "Flipkart" -> "110000"
                                        else -> "8000"
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = brand,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = getMerchantColor(brand)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlertsHubScreen(alerts: List<AlertEvent>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Alerts Hub",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = (-1.5).sp,
                    color = Color(0xFF1E293B)
                )
            )
            Text(
                text = "HISTORICAL NOTIFICATION FEED",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            )
        }

        if (alerts.isEmpty()) {
            item {
                GlassMorphismCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsNone,
                            contentDescription = "No alerts",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No Notifications yet",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "When an auto-scan discovers a new price drop that hits a lowest-ever barrier, a neon ticket drops here!",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(alerts) { alert ->
                GlassMorphismCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Alert indicator
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEF2F2))
                                .border(1.dp, Color(0xFFFCA5A5), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingDown,
                                contentDescription = "Drop Alert",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = alert.merchant,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = getMerchantColor(alert.merchant)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .background(Color(0xFF94A3B8), CircleShape)
                                )
                                Text(
                                    text = alert.dateText,
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            Text(
                                text = alert.productTitle,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "Reached historic lowest price: ₹${formatPrice(alert.droppedToPrice)}!",
                                fontSize = 11.sp,
                                color = Color(0xFF16A34A),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// Full iOS slide-up detailed sheet with Canvas Chart
@Composable
fun ProductDetailDialog(
    product: ProductEntity,
    history: List<PriceHistoryEntity>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onToggleAlert: () -> Unit,
    onForceDrop: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x9A0F172A)) // translucent iOS dark dim underlay
            .clickable(
                onClick = onDismiss,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        // High polish Glass bottom sheet card
        GlassMorphismCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false, onClick = {}) // absorb clicks
                .border(2.dp, Color(0xEEFFFFFF), RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
            backgroundColor = Color(0xEAFFFFFF), // heavier white frost
            cornerRadius = 32.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Dimple top line
                Box(
                    modifier = Modifier
                        .size(40.dp, 5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFCBD5E1))
                        .align(Alignment.CenterHorizontally)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = product.merchant.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = getMerchantColor(product.merchant),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = product.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Close indicator
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(Color(0xFFE2E8F0), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                    }
                }

                // Info Rows
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoBlock(
                        title = "CURRENT PRICE",
                        value = "₹${formatPrice(product.currentPrice)}",
                        valueColor = Color(0xFF0F172A),
                        modifier = Modifier.weight(1f)
                    )

                    InfoBlock(
                        title = "LOWEST RECORD",
                        value = "₹${formatPrice(product.lowestPrice)}",
                        valueColor = Color(0xFF16A34A),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Dynamic Canvas Chart
                Text(
                    text = "HISTORICAL PRICE FLUCTUATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF64748B),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x243B82F6))
                        .border(1.dp, Color(0x3B3B82F6), RoundedCornerShape(16.dp))
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    if (history.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Assembling historical point records...", fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                    } else {
                        PriceLineChart(history = history)
                    }
                }

                // Mini stats layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Peak: ₹${formatPrice(product.highestPrice)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = "Alert threshold: ₹${formatPrice(product.targetPrice)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3B82F6)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Alert switch row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x1F3B82F6))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Background Monitoring",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = "Receive notifications on drops",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    Switch(
                        checked = product.isTracking,
                        onCheckedChange = { onToggleAlert() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF10B981)
                        )
                    )
                }

                // Interactive iOS Actions Hub
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                uriHandler.openUri(product.url)
                            } catch (e: Exception) {
                                // fallback search
                                uriHandler.openUri("https://www.google.com/search?q=${product.title}")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                            Text("Launch Official App / Store", fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // SIMULATE PRICE DROP trigger (to test background notifications!)
                        Button(
                            onClick = onForceDrop,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Bolt, null, modifier = Modifier.size(16.dp))
                                Text("Simulate Drop", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        // Remove Tracker button
                        OutlinedButton(
                            onClick = {
                                onDelete()
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF64748B)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(16.dp))
                                Text("Delete Alert", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoBlock(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = valueColor)
        }
    }
}

// Pure Responsive Custom Vector line chart inside Android Canvas
@Composable
fun PriceLineChart(history: List<PriceHistoryEntity>) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .testTag("price_history_canvas")
    ) {
        val sizeWidth = size.width
        val sizeHeight = size.height

        val prices = history.map { it.price }
        if (prices.size < 2) return@Canvas

        val maxPrice = prices.maxOrNull() ?: 1.0
        val minPrice = prices.minOrNull() ?: 0.0
        val priceDiff = if (maxPrice - minPrice == 0.0) 1.0 else (maxPrice - minPrice)

        val paddingX = 20f
        val paddingY = 20f
        val chartWidth = sizeWidth - 2 * paddingX
        val chartHeight = sizeHeight - 2 * paddingY

        val points = history.mapIndexed { idx: Int, entity: PriceHistoryEntity ->
            val x = paddingX + idx.toFloat() / (history.size - 1) * chartWidth
            // inverse coordinate mapping (high price at top)
            val proportion = (entity.price - minPrice) / priceDiff
            val y = paddingY + chartHeight - (proportion.toFloat() * chartHeight)
            Offset(x, y)
        }

        // 1. Draw smooth fill gradient area underneath line
        val fillPath = Path().apply {
            moveTo(points.first().x, sizeHeight)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, sizeHeight)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x663B82F6), Color.Transparent)
            )
        )

        // 2. Draw actual trend stroke path
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val pPrev = points[i - 1]
                val pCur = points[i]
                // Draw cubic bezier curve for ultra premium curvy trends
                val controlX1 = pPrev.x + (pCur.x - pPrev.x) / 2f
                val controlY1 = pPrev.y
                val controlX2 = pPrev.x + (pCur.x - pPrev.x) / 2f
                val controlY2 = pCur.y
                cubicTo(controlX1, controlY1, controlX2, controlY2, pCur.x, pCur.y)
            }
        }

        drawPath(
            path = path,
            color = Color(0xFF3B82F6),
            style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 3. Highlight point circles
        points.forEachIndexed { i, pt ->
            val isMin = prices[i] == minPrice
            val isMax = prices[i] == maxPrice
            val circleColor = when {
                isMin -> Color(0xFF22C55E) // Green for Lowest
                isMax -> Color(0xFFEF4444) // Red for peak
                else -> Color(0xFF3B82F6)
            }
            drawCircle(
                color = Color.White,
                radius = 12f,
                center = pt
            )
            drawCircle(
                color = circleColor,
                radius = 7f,
                center = pt
            )
        }
    }
}

// Liquid Glass custom styled Bottom Dock
@Composable
fun LiquidGlassBottomBar(
    currentTab: TabScreen,
    onTabSelected: (TabScreen) -> Unit
) {
    val items = listOf(TabScreen.Home, TabScreen.Add, TabScreen.Alerts)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding() // Prevent system bar overlap!
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Frost panel
        GlassMorphismCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .shadow(12.dp, RoundedCornerShape(32.dp)),
            backgroundColor = Color(0xB5FFFFFF), // Translucent high-opacity white frost
            cornerRadius = 32.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { tab ->
                    val selected = currentTab == tab
                    val scale by animateFloatAsState(if (selected) 1.2f else 1.0f)
                    val tintColor by animateColorAsState(if (selected) Color(0xFF2563EB) else Color(0x7F1E293B))

                    Column(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onTabSelected(tab) }
                            )
                            .padding(8.dp)
                            .testTag("nav_tab_${tab.label.replace(" ", "_")}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = tintColor,
                            modifier = Modifier
                                .size(24.dp)
                                .drawBehind {
                                    // Glow behind active nav tab
                                    if (selected) {
                                        drawCircle(
                                            color = Color(0x3B2563EB),
                                            radius = size.width * 0.9f
                                        )
                                    }
                                }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tab.label.uppercase(),
                            fontSize = 8.sp,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                            color = tintColor,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

// Circular custom glass floating button
@Composable
fun GlassCircleButton(
    icon: ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color(0x66FFFFFF))
            .border(1.dp, Color(0x99FFFFFF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF1E293B),
            modifier = Modifier.size(20.dp)
        )
    }
}

// Gorgeous radar scanner overlay
@Composable
fun ScanningOverlayGlass() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x76000000)) // dim background
            .clickable(enabled = false) {}, // block input
        contentAlignment = Alignment.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition()
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )

        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.7f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        // Frost Panel
        GlassMorphismCard(
            modifier = Modifier
                .size(260.dp)
                .border(2.dp, Color(0xEFFFFFFF), RoundedCornerShape(24.dp)),
            backgroundColor = Color(0xF2FFFFFF),
            cornerRadius = 24.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing backdrop light
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                            .clip(CircleShape)
                            .background(Color(0x3B3B82F6))
                    )

                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = "Radar Scan Active",
                        tint = Color(0xFF2563EB),
                        modifier = Modifier
                            .size(60.dp)
                            .graphicsLayer(rotationZ = rotation)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Price Scan Radar",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1E293B)
                )
                Text(
                    text = "Micro-Scraping Online Storefronts...",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// Master Glass Card composable component (Frosted effect fallback helper)
@Composable
fun GlassMorphismCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0x4DFFFFFF), // default classic 30% translucent white
    borderStrokeColor: Color = Color(0x66FFFFFF), // default classic border reflection
    cornerRadius: Dp = 24.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .border(1.dp, borderStrokeColor, RoundedCornerShape(cornerRadius))
    ) {
        content()
    }
}

// Helper styling constants represent merchants
fun getMerchantColor(merchant: String): Color = when (merchant.uppercase()) {
    "AMAZON" -> Color(0xFFEA580C)  // Deep Orange
    "FLIPKART" -> Color(0xFF1D4ED8) // Deep Blue
    "MYNTRA" -> Color(0xFFDB2777)   // Gorgeous Rose/Pink
    else -> Color(0xFF64748B)       // Cool Slate
}

// Helper formats large numbers with thousand commas
fun formatPrice(price: Double): String {
    return String.format("%,.2f", price)
}

@Composable
fun GlowingLowestPriceBadge(
    modifier: Modifier = Modifier,
    text: String = "LOWEST EVER"
) {
    val infiniteTransition = rememberInfiniteTransition(label = "badge_glow")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha_glow"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x3B10B981)) // semi-transparent rich emerald green
            .drawBehind {
                // Drawing an outer glowing soft aura behind the badge
                drawRoundRect(
                    color = Color(0x34D399).copy(alpha = alphaAnim * 0.25f),
                    size = size,
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                    style = Stroke(width = 4.dp.toPx())
                )
            }
            .border(
                width = 1.2.dp,
                color = Color(0xFF10B981).copy(alpha = alphaAnim),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Heartbeat/pulser green radar dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981))
            )
            
            Text(
                text = text,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF065F46), // iOS style deep emerald green for readability
                letterSpacing = 0.6.sp
            )
        }
    }
}
