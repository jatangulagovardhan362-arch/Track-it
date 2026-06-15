package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.PriceHistoryEntity
import com.example.data.ProductEntity
import com.example.data.ProductRepository
import com.example.util.NotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URLDecoder
import kotlin.random.Random

// Alert history structure to power the 'Alerts' tab/page
data class AlertEvent(
    val id: Long = System.currentTimeMillis() + Random.nextLong(100),
    val productTitle: String,
    val merchant: String,
    val droppedToPrice: Double,
    val dateText: String,
    val badgeType: String = "LOWEST" // "LOWEST", "ALERT", "STABLE"
)

class ProductViewModel(private val repository: ProductRepository) : ViewModel() {

    // List of active products
    val products = repository.allProducts

    // Alerts state
    private val _alerts = MutableStateFlow<List<AlertEvent>>(emptyList())
    val alerts: StateFlow<List<AlertEvent>> = _alerts.asStateFlow()

    // Loading overlay state
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Newly added product loading state
    private val _isAddingProduct = MutableStateFlow(false)
    val isAddingProduct: StateFlow<Boolean> = _isAddingProduct.asStateFlow()

    // Selected product for the detail panel
    private val _selectedProduct = MutableStateFlow<ProductEntity?>(null)
    val selectedProduct: StateFlow<ProductEntity?> = _selectedProduct.asStateFlow()

    // Price history flow for the selected product
    private val _selectedProductHistory = MutableStateFlow<List<PriceHistoryEntity>>(emptyList())
    val selectedProductHistory: StateFlow<List<PriceHistoryEntity>> = _selectedProductHistory.asStateFlow()

    init {
        // Pre-populate with beautiful default trackers if the list is empty
        viewModelScope.launch {
            val initialList = repository.allProducts.first()
            if (initialList.isEmpty()) {
                seedDefaultProducts()
            } else {
                // Pre-populate some history events for the Alerts tab if there's existing items
                _alerts.value = listOf(
                    AlertEvent(
                        productTitle = "Nike Air Jordan 1",
                        merchant = "Myntra",
                        droppedToPrice = 8499.00,
                        dateText = "Dropped 2 hours ago",
                        badgeType = "LOWEST"
                    ),
                    AlertEvent(
                        productTitle = "Sony WH-1000XM5",
                        merchant = "Amazon",
                        droppedToPrice = 24990.00,
                        dateText = "Dropped Yesterday",
                        badgeType = "DROP"
                    )
                )
            }
        }
    }

    private suspend fun seedDefaultProducts() {
        val p1Id = repository.insertProduct(
            ProductEntity(
                title = "Sony WH-1000XM5",
                url = "https://www.amazon.in/Sony-WH-1000XM5-Wireless-Cancelling/dp/B0B3C99SDG",
                imageUrl = "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?q=80&w=200&auto=format&fit=crop",
                merchant = "Amazon",
                currentPrice = 24990.00,
                lowestPrice = 24990.00,
                highestPrice = 34990.00,
                targetPrice = 25000.00,
                isTracking = true
            )
        ).toInt()

        val p2Id = repository.insertProduct(
            ProductEntity(
                title = "iPhone 15 Pro",
                url = "https://www.flipkart.com/apple-iphone-15-pro-natural-titanium-128-gb/p/itmdb2de759f23fa",
                imageUrl = "https://images.unsplash.com/photo-1510557880182-3d4d3cba35a5?q=80&w=200&auto=format&fit=crop",
                merchant = "Flipkart",
                currentPrice = 112000.00,
                lowestPrice = 112000.00,
                highestPrice = 134900.00,
                targetPrice = 110000.00,
                isTracking = true
            )
        ).toInt()

        val p3Id = repository.insertProduct(
            ProductEntity(
                title = "Nike Air Jordan 1",
                url = "https://www.myntra.com/shoes/nike/nike-men-crimson-air-jordan-1-mid-sneakers/25492102/buy",
                imageUrl = "https://images.unsplash.com/photo-1552346154-21d32810aba3?q=80&w=200&auto=format&fit=crop",
                merchant = "Myntra",
                currentPrice = 8499.00,
                lowestPrice = 8499.00,
                highestPrice = 11495.00,
                targetPrice = 9000.00,
                isTracking = true
            )
        ).toInt()

        // Seed history lists
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L

        // Sony WH-1000XM5: ₹34,990 -> ₹32,000 -> ₹29,990 -> ₹28,500 -> ₹26,990 -> ₹24,990
        val history1 = listOf(34990.0, 32000.0, 29990.0, 28500.0, 26990.0, 24990.0)
        history1.forEachIndexed { i, price ->
            repository.addPriceRecord(p1Id, price, now - (5 - i) * 30 * oneDayMs)
        }

        // iPhone 15 Pro: ₹134,900 -> ₹128,000 -> ₹121,000 -> ₹118,000 -> ₹112,000
        val history2 = listOf(134900.0, 128000.0, 121000.0, 118000.0, 112000.0)
        history2.forEachIndexed { i, price ->
            repository.addPriceRecord(p2Id, price, now - (4 - i) * 30 * oneDayMs)
        }

        // Nike Air Jordan: 11,495 -> 10,900 -> 9,999 -> 9,500 -> 8,499
        val history3 = listOf(11495.0, 10900.0, 9999.0, 9500.0, 8499.0)
        history3.forEachIndexed { i, price ->
            repository.addPriceRecord(p3Id, price, now - (4 - i) * 30 * oneDayMs)
        }

        // Add preloaded alerts matching the preset drop
        _alerts.value = listOf(
            AlertEvent(
                productTitle = "Nike Air Jordan 1",
                merchant = "Myntra",
                droppedToPrice = 8499.00,
                dateText = "Dropped 2 hours ago",
                badgeType = "LOWEST"
            ),
            AlertEvent(
                productTitle = "Sony WH-1000XM5",
                merchant = "Amazon",
                droppedToPrice = 24990.00,
                dateText = "Dropped Yesterday",
                badgeType = "DROP"
            )
        )
    }

    fun selectProduct(product: ProductEntity) {
        _selectedProduct.value = product
        viewModelScope.launch {
            repository.getPriceHistory(product.id).collect { history ->
                _selectedProductHistory.value = history
            }
        }
    }

    fun deselectProduct() {
        _selectedProduct.value = null
        _selectedProductHistory.value = emptyList()
    }

    fun addProduct(
        url: String, 
        customTitle: String = "", 
        customMerchant: String = "",
        customPrice: Double? = null,
        targetPrice: Double? = null
    ) {
        viewModelScope.launch {
            _isAddingProduct.value = true
            delay(1500) // Simulating real scrape processing

            val merchant = when {
                customMerchant.isNotEmpty() -> customMerchant
                url.contains("amazon", ignoreCase = true) -> "Amazon"
                url.contains("flipkart", ignoreCase = true) -> "Flipkart"
                url.contains("myntra", ignoreCase = true) -> "Myntra"
                else -> "Other Shop"
            }

            // Derive realistic product details from URL or fallbacks
            val generatedTitle = when {
                customTitle.isNotEmpty() -> customTitle
                url.contains("wh-1000xm5", ignoreCase = true) || url.contains("headphones", ignoreCase = true) -> "Premium Wireless Headphones"
                url.contains("iphone", ignoreCase = true) -> "Premium iPhone Series"
                url.contains("shoe", ignoreCase = true) || url.contains("jordan", ignoreCase = true) -> "Sport Run Sneaker"
                else -> {
                    // Pull a decent name from URL slug if possible
                    try {
                        val pathSegments = url.split("/")
                        val segment = pathSegments.findLast { s -> s.length > 5 && !s.contains("?") && !s.contains(".") }
                        segment?.replace("-", " ")?.capitalize() ?: "Smart Tech Gadget"
                    } catch (e: Exception) {
                        "Tracked Online Product"
                    }
                }
            }

            val basePrice = customPrice ?: when (merchant) {
                "Amazon" -> Random.nextInt(12000, 39000).toDouble()
                "Flipkart" -> Random.nextInt(40000, 99000).toDouble()
                "Myntra" -> Random.nextInt(3000, 12000).toDouble()
                else -> Random.nextInt(1500, 25000).toDouble()
            }

            val targetVal = targetPrice ?: (basePrice * 0.90).toInt().toDouble()

            val imageUrl = when {
                generatedTitle.contains("phone", ignoreCase = true) || generatedTitle.contains("iphone", ignoreCase = true) -> 
                    "https://images.unsplash.com/photo-1510557880182-3d4d3cba35a5?q=80&w=200"
                generatedTitle.contains("shoe", ignoreCase = true) || generatedTitle.contains("sneaker", ignoreCase = true) -> 
                    "https://images.unsplash.com/photo-1552346154-21d32810aba3?q=80&w=200"
                generatedTitle.contains("headphone", ignoreCase = true) -> 
                    "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?q=80&w=200"
                else -> 
                    "https://images.unsplash.com/photo-1546868871-7041f2a55e12?q=80&w=200" // default smart watch
            }

            val product = ProductEntity(
                title = generatedTitle,
                url = url,
                imageUrl = imageUrl,
                merchant = merchant,
                currentPrice = basePrice,
                lowestPrice = basePrice,
                highestPrice = basePrice * 1.15,
                targetPrice = targetVal,
                isTracking = true
            )

            val newId = repository.insertProduct(product).toInt()

            // Create initial historical points over last 4 months
            val now = System.currentTimeMillis()
            val oneDayMs = 24 * 60 * 60 * 1000L
            val pHistory = listOf(basePrice * 1.15, basePrice * 1.08, basePrice * 1.04, basePrice)
            pHistory.forEachIndexed { i, price ->
                repository.addPriceRecord(newId, price, now - (3 - i) * 30 * oneDayMs)
            }

            _isAddingProduct.value = false
        }
    }

    // Force updates of prices, occasionally simulation drops to test notifications!
    fun simulateRefreshAll(context: Context) {
        viewModelScope.launch {
            _isRefreshing.value = true
            delay(1800) // Beautiful glass refresh progress loading spinner

            val currentProducts = repository.allProducts.first()
            for (p in currentProducts) {
                if (!p.isTracking) continue

                // 40% chance of price dropping, 10% chance of dropping to Lowest Ever
                val roll = Random.nextDouble()
                val changePercent = Random.nextDouble(0.01, 0.15)
                
                when {
                    roll < 0.10 -> {
                        // Drop below lowest ever!
                        val newLowestPrice = p.lowestPrice * 0.90
                        val oldPrice = p.currentPrice
                        
                        val updatedProduct = p.copy(
                            currentPrice = newLowestPrice,
                            lowestPrice = newLowestPrice,
                            highestPrice = maxOf(p.highestPrice, oldPrice)
                        )
                        repository.updateProduct(updatedProduct)
                        repository.addPriceRecord(p.id, newLowestPrice)

                        // Trigger visual alert
                        _alerts.value = listOf(
                            AlertEvent(
                                productTitle = p.title,
                                merchant = p.merchant,
                                droppedToPrice = newLowestPrice,
                                dateText = "Just now",
                                badgeType = "LOWEST"
                            )
                        ) + _alerts.value

                        // Trigger standard system notification helper!
                        NotificationHelper.sendPriceDropNotification(
                            context = context,
                            productId = p.id,
                            title = p.title,
                            merchant = p.merchant,
                            price = newLowestPrice,
                            previousPrice = oldPrice
                        )
                    }
                    roll < 0.40 -> {
                        // Regular slight drops, not lowest ever
                        val newPrice = p.currentPrice * (1.0 - changePercent)
                        val updatedProduct = p.copy(currentPrice = newPrice)
                        repository.updateProduct(updatedProduct)
                        repository.addPriceRecord(p.id, newPrice)

                        // Trigger visual list alert
                        _alerts.value = listOf(
                            AlertEvent(
                                productTitle = p.title,
                                merchant = p.merchant,
                                droppedToPrice = newPrice,
                                dateText = "Just now",
                                badgeType = "DROP"
                            )
                        ) + _alerts.value
                    }
                    roll < 0.65 -> {
                        // Regular price increase/recovery
                        val newPrice = p.currentPrice * (1.0 + changePercent)
                        repository.updateProduct(p.copy(currentPrice = newPrice, highestPrice = maxOf(p.highestPrice, newPrice)))
                        repository.addPriceRecord(p.id, newPrice)
                    }
                }
            }

            // If a product is currently open in detail view, refresh it
            val currentlySelected = _selectedProduct.value
            if (currentlySelected != null) {
                val updated = repository.getProductByIdOneShot(currentlySelected.id)
                if (updated != null) {
                    _selectedProduct.value = updated
                    _selectedProductHistory.value = repository.getPriceHistoryOneShot(updated.id)
                }
            }

            _isRefreshing.value = false
        }
    }

    // Force drop option inside product detail so user can easily manually test and verify price drop alerts!
    fun testForcePriceDrop(context: Context, product: ProductEntity) {
        viewModelScope.launch {
            val oldPrice = product.currentPrice
            val newLowestPrice = product.lowestPrice * 0.85 // Dropped by 15% below previous lowest ever!
            
            val updated = product.copy(
                currentPrice = newLowestPrice,
                lowestPrice = newLowestPrice
            )
            repository.updateProduct(updated)
            repository.addPriceRecord(product.id, newLowestPrice)
            
            // Highlight it in view state
            _selectedProduct.value = updated
            _selectedProductHistory.value = repository.getPriceHistoryOneShot(product.id)
            
            // Add to in-app drop feeds
            _alerts.value = listOf(
                AlertEvent(
                    productTitle = product.title,
                    merchant = product.merchant,
                    droppedToPrice = newLowestPrice,
                    dateText = "Just now",
                    badgeType = "LOWEST"
                )
            ) + _alerts.value
            
            // Trigger actual Android system notification!
            NotificationHelper.sendPriceDropNotification(
                context = context,
                productId = product.id,
                title = product.title,
                merchant = product.merchant,
                price = newLowestPrice,
                previousPrice = oldPrice
            )
        }
    }

    fun deleteProduct(productId: Int) {
        viewModelScope.launch {
            repository.deleteProduct(productId)
            if (_selectedProduct.value?.id == productId) {
                deselectProduct()
            }
        }
    }

    fun toggleTracking(product: ProductEntity) {
        viewModelScope.launch {
            val updated = product.copy(isTracking = !product.isTracking)
            repository.updateProduct(updated)
            if (_selectedProduct.value?.id == product.id) {
                _selectedProduct.value = updated
            }
        }
    }
}

class ProductViewModelFactory(private val repository: ProductRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProductViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProductViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
