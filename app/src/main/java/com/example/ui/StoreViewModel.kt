package com.example.ui

import android.app.Application
import android.util.Log
import com.example.data.Recipe
import com.example.data.SuggestedProduct
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface Screen {
    object Onboarding : Screen
    object Home : Screen
    data class StoreDetail(val shopId: Long) : Screen
    object AIChat : Screen
    object Cart : Screen
    data class OrderTracker(val orderId: Long) : Screen
    data class VendorDashboard(val shopId: Long) : Screen
    object Settings : Screen
}

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isUser: Boolean,
    val intent: String = "",
    val recipe: Recipe? = null,
    val quickReplies: List<String> = emptyList(),
    val matchedProducts: List<ProductEntity> = emptyList(),
    val productReasons: Map<Long, String> = emptyMap()
)

class StoreViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "StoreViewModel"
    private val database = AppDatabase.getDatabase(application)
    private val repository = StoreRepository(database.storeDao())

    // UI Navigation State
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Onboarding)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // Screen navigation stack
    private val screenStack = mutableListOf<Screen>()

    // Core Data Flows from SQLite
    val userState: StateFlow<UserEntity?> = repository.userFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Coordinates of user (latitude, longitude)
    private val _userCoordinates = MutableStateFlow<Pair<Double, Double>?>(null)
    val userCoordinates: StateFlow<Pair<Double, Double>?> = _userCoordinates.asStateFlow()

    fun updateUserCoordinates(latitude: Double, longitude: Double) {
        _userCoordinates.value = Pair(latitude, longitude)
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Radius of the earth in km
        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)
        val a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val distance = r * c
        return ((distance * 10).toInt() / 10.0) // Round to 1 decimal place
    }

    val shopsState: StateFlow<List<ShopEntity>> = combine(
        repository.allShops,
        _userCoordinates
    ) { shops, coords ->
        // For demo purposes, we want stores and products to be available on all locations.
        // We simulate that the shops are always nearby regardless of the selected coordinates.
        shops.mapIndexed { index, shop ->
            // Stable simulated distance between 0.5km and 2.5km based on shop ID
            var simulatedDist = ((shop.id % 10) + 1) * 0.4
            simulatedDist = Math.round(simulatedDist * 10.0) / 10.0 // Round to 1 decimal place
            val deliveryTime = (simulatedDist * 12 + 5).toInt().coerceIn(5, 45)
            shop.copy(distanceKm = simulatedDist, deliveryTimeMin = deliveryTime)
        }.sortedBy { it.distanceKm }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val productsState: StateFlow<List<ProductEntity>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cartState: StateFlow<List<CartItemEntity>> = repository.cartItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ordersState: StateFlow<List<OrderEntity>> = repository.allOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // AI Concierge Chat State
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    init {
        // Observe user to direct to Home if logged in, or Onboarding if null
        viewModelScope.launch {
            userState.collect { user ->
                if (user != null && _currentScreen.value is Screen.Onboarding) {
                    navigateTo(Screen.Home)
                } else if (user == null) {
                    navigateTo(Screen.Onboarding)
                }
            }
        }
    }

    fun navigateTo(screen: Screen) {
        if (_currentScreen.value != screen) {
            screenStack.add(_currentScreen.value)
            _currentScreen.value = screen
        }
    }

    fun navigateBack() {
        if (screenStack.isNotEmpty()) {
            _currentScreen.value = screenStack.removeAt(screenStack.size - 1)
        } else {
            _currentScreen.value = Screen.Home
        }
    }

    fun login(name: String, phone: String, ageBracket: String, language: String, location: String) {
        viewModelScope.launch {
            val tier = when (ageBracket) {
                "Under 25" -> "Modern"
                "25–40" -> "Modern"
                "41–55" -> "Standard"
                "56+" -> "Simplified"
                else -> "Standard"
            }
            val user = UserEntity(
                id = "current_user",
                name = name.ifEmpty { "Ramesh Kumar" },
                phone = phone.ifEmpty { "9876543210" },
                ageBracket = ageBracket,
                language = language,
                selectedLocation = location.ifEmpty { "Rampur Main Town, UP" },
                uiTier = tier
            )
            repository.saveUser(user)
            navigateTo(Screen.Home)
        }
    }

    fun updateUiTier(tier: String) {
        viewModelScope.launch {
            val user = userState.value ?: return@launch
            repository.saveUser(user.copy(uiTier = tier))
        }
    }

    fun updateLanguage(lang: String) {
        viewModelScope.launch {
            val user = userState.value ?: return@launch
            repository.saveUser(user.copy(language = lang))
        }
    }

    fun updateLocation(loc: String) {
        viewModelScope.launch {
            val user = userState.value ?: return@launch
            repository.saveUser(user.copy(selectedLocation = loc))

            // Re-calculate / randomize distances to local shops to simulate realistic physical distance tracking!
            val shops = shopsState.value
            for (shop in shops) {
                val newDistance = ((0.1 + Math.random() * 3.4) * 10).toInt() / 10.0
                val newTime = (newDistance * 12 + 5).toInt().coerceIn(5, 45)
                database.storeDao().insertShop(shop.copy(distanceKm = newDistance, deliveryTimeMin = newTime))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            // In Room, we can clear table or just remove the current_user row
            database.storeDao().clearCart()
            database.storeDao().insertUser(UserEntity(name = "", phone = "", ageBracket = "", language = "", selectedLocation = "", uiTier = "Standard"))
            _currentScreen.value = Screen.Onboarding
            _chatMessages.value = emptyList()
            screenStack.clear()
        }
    }

    // --- Cart Actions ---

    fun addToCart(productId: Long, shopId: Long, quantity: Int = 1) {
        viewModelScope.launch {
            val existing = cartState.value.find { it.productId == productId }
            if (existing != null) {
                repository.updateCartQuantity(existing.id, existing.quantity + quantity)
            } else {
                repository.addToCart(productId, shopId, quantity)
            }
        }
    }

    fun decreaseCartQuantity(productId: Long) {
        viewModelScope.launch {
            val item = cartState.value.find { it.productId == productId } ?: return@launch
            if (item.quantity > 1) {
                repository.updateCartQuantity(item.id, item.quantity - 1)
            } else {
                repository.removeFromCart(productId)
            }
        }
    }

    fun removeFromCart(productId: Long) {
        viewModelScope.launch {
            repository.removeFromCart(productId)
        }
    }

    fun clearCart() {
        viewModelScope.launch {
            repository.clearCart()
        }
    }

    // --- Order Actions ---

    fun checkout(paymentMethod: String, address: String) {
        val currentCart = cartState.value
        if (currentCart.isEmpty()) return

        viewModelScope.launch {
            // Multi-store cart handling: group by shopId so each shop gets its own sub-order
            val groupedByShop = currentCart.groupBy { it.shopId }
            val allProducts = productsState.value
            
            var lastOrderId = -1L
            for ((shopId, items) in groupedByShop) {
                val shop = repository.getShopById(shopId)
                val shopName = shop?.name ?: "Local Shop"
                
                // Calculate total
                val total = items.sumOf { item ->
                    val prod = allProducts.find { it.id == item.productId }
                    (prod?.price ?: 0.0) * item.quantity
                }

                val order = OrderEntity(
                    shopId = shopId,
                    shopName = shopName,
                    status = "Placed",
                    timestamp = System.currentTimeMillis(),
                    paymentMethod = paymentMethod,
                    deliveryAddress = address,
                    totalAmount = total
                )

                lastOrderId = repository.placeOrder(order, items) { id ->
                    repository.getProductById(id)
                }
            }

            // Navigate to Order Tracker of the last order
            if (lastOrderId != -1L) {
                navigateTo(Screen.OrderTracker(lastOrderId))
            }
        }
    }

    fun updateOrderStatus(orderId: Long, status: String) {
        viewModelScope.launch {
            repository.updateOrderStatus(orderId, status)
        }
    }

    fun reorder(orderId: Long) {
        viewModelScope.launch {
            // Get order items and put them in cart
            repository.getOrderItems(orderId).firstOrNull()?.let { orderItems ->
                val allShops = shopsState.value
                val allProducts = productsState.value
                val order = ordersState.value.find { it.id == orderId } ?: return@launch

                for (item in orderItems) {
                    val matchingProduct = allProducts.find { it.name == item.productName && it.shopId == order.shopId }
                    if (matchingProduct != null) {
                        addToCart(matchingProduct.id, order.shopId, item.quantity)
                    }
                }
                navigateTo(Screen.Cart)
            }
        }
    }

    // --- AI Shopping Concierge Chat Actions ---

    fun sendMessageToAI(text: String) {
        if (text.trim().isEmpty()) return

        // Capture previous conversation history before adding the new message
        val priorHistory = _chatMessages.value.map { Pair(it.text, it.isUser) }

        // 1. Add User Message
        val userMsg = ChatMessage(text = text, isUser = true)
        _chatMessages.value = _chatMessages.value + userMsg
        _isAiLoading.value = true

        viewModelScope.launch {
            try {
                val products = productsState.value
                val shopsMap = shopsState.value.associateBy { it.id }
                val user = userState.value
                val language = user?.language ?: "English"
                val ageBracket = user?.ageBracket ?: "25–40"

                // Query Gemini API with the full local product catalog and chat history passed!
                val aiResponse = GeminiService.getShoppingAdvice(
                    userPrompt = text,
                    catalogProducts = products,
                    shopsMap = shopsMap,
                    language = language,
                    ageBracket = ageBracket,
                    chatHistory = priorHistory
                )

                // Match product Entities from returned IDs
                val matchedEntities = products.filter { p -> aiResponse.suggestedProducts.any { it.productId == p.id } }.toMutableList()
                val reasonsMap = aiResponse.suggestedProducts.associate { it.productId to it.reason }

                // Proactively scan the AI message text to map any mentioned products dynamically in both English and Hindi
                val textLower = aiResponse.message.lowercase()
                for (prod in products) {
                    val prodNameLower = prod.name.lowercase()
                    
                    val hasMatch = when {
                        prodNameLower.contains("saree") && (textLower.contains("saree") || textLower.contains("साड़ी")) -> true
                        prodNameLower.contains("teddy") && (textLower.contains("teddy") || textLower.contains("टेडी")) -> true
                        prodNameLower.contains("chocolate") && (textLower.contains("chocolate") || textLower.contains("चॉकलेट")) -> true
                        prodNameLower.contains("candle") && (textLower.contains("candle") || textLower.contains("मोमबत्ती")) -> true
                        prodNameLower.contains("frame") && (textLower.contains("frame") || textLower.contains("फ्रेम")) -> true
                        prodNameLower.contains("mug") && (textLower.contains("mug") || textLower.contains("मग")) -> true
                        prodNameLower.contains("paneer") && (textLower.contains("paneer") || textLower.contains("पनीर")) -> true
                        prodNameLower.contains("katli") && (textLower.contains("katli") || textLower.contains("कतली")) -> true
                        prodNameLower.contains("samosa") && (textLower.contains("samosa") || textLower.contains("समोसा")) -> true
                        prodNameLower.contains("gulab jamun") && (textLower.contains("gulab jamun") || textLower.contains("गुलाब जामुन")) -> true
                        prodNameLower.contains("laddu") && (textLower.contains("laddu") || textLower.contains("लड्डू")) -> true
                        prodNameLower.contains("atta") && (textLower.contains("atta") || textLower.contains("आटा")) -> true
                        prodNameLower.contains("oil") && (textLower.contains("oil") || textLower.contains("तेल")) -> true
                        prodNameLower.contains("salt") && (textLower.contains("salt") || textLower.contains("नमक")) -> true
                        prodNameLower.contains("potato") && (textLower.contains("potato") || textLower.contains("aloo") || textLower.contains("आलू")) -> true
                        prodNameLower.contains("onion") && (textLower.contains("onion") || textLower.contains("pyaj") || textLower.contains("प्याज")) -> true
                        prodNameLower.contains("tomato") && (textLower.contains("tomato") || textLower.contains("tamatar") || textLower.contains("टमाटर")) -> true
                        prodNameLower.contains("peas") && (textLower.contains("peas") || textLower.contains("matar") || textLower.contains("मटर")) -> true
                        else -> {
                            prodNameLower.split(" ", "-", "(", ")", "/").any { word ->
                                word.length > 3 && textLower.contains(word)
                            }
                        }
                    }
                    
                    if (hasMatch && matchedEntities.none { it.id == prod.id }) {
                        matchedEntities.add(prod)
                    }
                }

                // 2. Add Assistant Message
                val assistantMsg = ChatMessage(
                    text = aiResponse.message,
                    isUser = false,
                    intent = aiResponse.intent,
                    recipe = aiResponse.recipe,
                    quickReplies = aiResponse.quickReplies,
                    matchedProducts = matchedEntities.distinctBy { it.id },
                    productReasons = reasonsMap
                )

                _chatMessages.value = _chatMessages.value + assistantMsg
            } catch (e: Exception) {
                Log.e(TAG, "Error matching AI query: ${e.message}", e)
                // Add fallback message
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    text = "I'm sorry, I'm having trouble connecting to the network right now. Try checking your internet connection or try again.",
                    isUser = false
                )
            } finally {
                _isAiLoading.value = false
            }
        }
    }

    // --- Vendor Management Actions ---

    fun toggleProductStock(productId: Long, inStock: Boolean) {
        viewModelScope.launch {
            val product = repository.getProductById(productId) ?: return@launch
            repository.updateProduct(product.copy(inStock = inStock))
        }
    }

    fun updateProductPrice(productId: Long, newPrice: Double) {
        viewModelScope.launch {
            val product = repository.getProductById(productId) ?: return@launch
            repository.updateProduct(product.copy(price = newPrice))
        }
    }

    fun addNewProduct(shopId: Long, name: String, price: Double, unit: String, category: String) {
        viewModelScope.launch {
            val newProduct = ProductEntity(
                shopId = shopId,
                name = name,
                price = price,
                unit = unit,
                inStock = true,
                subCategory = category,
                imageUrl = "general"
            )
            repository.saveProduct(newProduct)
        }
    }
}
