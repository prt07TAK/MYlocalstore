package com.example.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// --- Entities ---

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String = "current_user",
    val name: String,
    val phone: String,
    val ageBracket: String, // "Under 25", "25–40", "41–55", "56+"
    val language: String, // "English", "Hindi"
    val selectedLocation: String, // e.g. "Pipariya Main Town, MP"
    val uiTier: String // "Modern", "Standard", "Simplified"
)

@Entity(tableName = "shops")
data class ShopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val ownerPhone: String,
    val category: String, // "Kirana", "Sabji", "Sweet Shop", "Gift Shop", "Medicine Store", "Clothing Store", "Stationery Shop"
    val rating: Double,
    val distanceKm: Double,
    val deliveryTimeMin: Int,
    val address: String,
    val hours: String,
    val isVerified: Boolean = true,
    val latitude: Double = 22.76,
    val longitude: Double = 78.35
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopId: Long,
    val name: String,
    val price: Double,
    val unit: String, // e.g. "kg", "packet", "piece", "g", "Litre"
    val inStock: Boolean,
    val subCategory: String,
    val imageUrl: String // descriptive label for drawing placeholders
)

@Entity(tableName = "cart_items")
data class CartItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val shopId: Long,
    val quantity: Int
)

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shopId: Long,
    val shopName: String,
    val status: String, // "Placed", "Accepted", "Packed", "Out for Delivery", "Delivered"
    val timestamp: Long,
    val paymentMethod: String, // "UPI", "Cash on Delivery", "Card"
    val deliveryAddress: String,
    val totalAmount: Double
)

@Entity(tableName = "order_items")
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val productName: String,
    val price: Double,
    val quantity: Int,
    val imageUrl: String
)

// --- DAO ---

@Dao
interface StoreDao {
    // User Queries
    @Query("SELECT * FROM users LIMIT 1")
    fun getUserFlow(): Flow<UserEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getUserSync(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    // Shop Queries
    @Query("SELECT * FROM shops")
    fun getAllShops(): Flow<List<ShopEntity>>

    @Query("SELECT * FROM shops WHERE category = :category")
    fun getShopsByCategory(category: String): Flow<List<ShopEntity>>

    @Query("SELECT * FROM shops WHERE id = :shopId LIMIT 1")
    suspend fun getShopById(shopId: Long): ShopEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShop(shop: ShopEntity): Long

    // Product Queries
    @Query("SELECT * FROM products")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE shopId = :shopId")
    fun getProductsByShop(shopId: Long): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    suspend fun getProductById(productId: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR subCategory LIKE '%' || :query || '%'")
    fun searchProducts(query: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR subCategory LIKE '%' || :query || '%'")
    suspend fun searchProductsSync(query: String): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    // Cart Queries
    @Query("SELECT * FROM cart_items")
    fun getCartItems(): Flow<List<CartItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToCart(item: CartItemEntity)

    @Query("DELETE FROM cart_items WHERE productId = :productId")
    suspend fun removeCartItemByProductId(productId: Long)

    @Query("UPDATE cart_items SET quantity = :quantity WHERE id = :cartItemId")
    suspend fun updateCartQuantity(cartItemId: Long, quantity: Int)

    @Query("DELETE FROM cart_items")
    suspend fun clearCart()

    // Order Queries
    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    fun getAllOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE shopId = :shopId ORDER BY timestamp DESC")
    fun getOrdersByShop(shopId: Long): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE id = :orderId LIMIT 1")
    suspend fun getOrderById(orderId: Long): OrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity): Long

    @Query("UPDATE orders SET status = :status WHERE id = :orderId")
    suspend fun updateOrderStatus(orderId: Long, status: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItem(item: OrderItemEntity)

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    fun getOrderItems(orderId: Long): Flow<List<OrderItemEntity>>
}

// --- Database Class ---

@Database(
    entities = [
        UserEntity::class,
        ShopEntity::class,
        ProductEntity::class,
        CartItemEntity::class,
        OrderEntity::class,
        OrderItemEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "my_local_store_db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseInitializerCallback(context))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- Database Initializer ---

class DatabaseInitializerCallback(private val context: Context) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // Seeding database in IO Thread
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(context)
            val dao = database.storeDao()
            seedDatabase(dao)
        }
    }

    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
        super.onDestructiveMigration(db)
        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(context)
            val dao = database.storeDao()
            seedDatabase(dao)
        }
    }

    private suspend fun seedDatabase(dao: StoreDao) {
        // 1. Initial Default User (so they are signed in by default if they don't do onboarding)
        val defaultUser = UserEntity(
            id = "current_user",
            name = "Ramesh Kumar",
            phone = "9876543210",
            ageBracket = "25–40",
            language = "English",
            selectedLocation = "Pipariya Town Hall, Pipariya",
            uiTier = "Standard"
        )
        dao.insertUser(defaultUser)

        // 2. Seeding Shops
        val shops = listOf(
            ShopEntity(1, "Gupta Kirana Store", "9112233441", "Kirana", 4.8, 0.4, 15, "Pipariya Main Bazar, Ward 5", "8:00 AM - 9:00 PM", true, 22.762, 78.352),
            ShopEntity(2, "Saini Fresh Sabji Mandi", "9112233442", "Sabji", 4.6, 0.2, 10, "Subhash Chowk, Opp. State Bank", "7:00 AM - 8:00 PM", true, 22.758, 78.354),
            ShopEntity(3, "Verma Mithai Bhandar", "9112233443", "Sweet Shop", 4.9, 0.6, 20, "Station Road, Near Railway Crossing", "9:00 AM - 10:00 PM", true, 22.765, 78.348),
            ShopEntity(4, "Raju Gift & Toy Corner", "9112233444", "Gift Shop", 4.5, 0.9, 25, "College Road, Pipariya", "10:00 AM - 8:30 PM", true, 22.759, 78.361),
            ShopEntity(5, "Arogya Medical & Wellness", "9112233445", "Medicine Store", 4.7, 0.5, 12, "Hospital Road, Near Civil Hospital", "24 Hours", true, 22.761, 78.349),
            ShopEntity(6, "Shyam Stationery & Books", "9112233446", "Stationery Shop", 4.4, 0.7, 18, "School Road, Pipariya", "9:00 AM - 8:00 PM", true, 22.764, 78.355),
            ShopEntity(7, "Milan Garments & Saree Palace", "9112233447", "Clothing Store", 4.6, 1.1, 30, "Panchayat Market, Shop 12", "10:00 AM - 9:00 PM", true, 22.766, 78.359),
            ShopEntity(8, "TechHub Electronics", "9112233448", "Electronics", 4.7, 1.5, 35, "Main Road, Near Bus Stand", "10:00 AM - 9:30 PM", true, 22.760, 78.350),
            ShopEntity(9, "Sunrise Bakery", "9112233449", "Bakery", 4.8, 0.8, 15, "New Market Area", "8:00 AM - 10:00 PM", true, 22.768, 78.360),
            ShopEntity(10, "Champion Sports", "9112233450", "Sports", 4.5, 1.2, 20, "Stadium Road", "9:00 AM - 8:30 PM", true, 22.755, 78.365)
        )
        for (shop in shops) {
            dao.insertShop(shop)
        }

        // 3. Seeding Products
        val products = listOf(
            // Gupta Kirana (shopId = 1)
            ProductEntity(0, 1, "Tata Salt (1 kg)", 28.0, "packet", true, "Spices & Salt", "salt"),
            ProductEntity(0, 1, "Fortune Mustard Oil (1 L)", 175.0, "Litre", true, "Oils & Ghee", "oil"),
            ProductEntity(0, 1, "Ashirvaad Shudh Chakki Atta (5 kg)", 240.0, "packet", true, "Atta & Flours", "atta"),
            ProductEntity(0, 1, "Maggi 2-Minute Noodles (120g)", 14.0, "packet", true, "Snacks", "noodles"),
            ProductEntity(0, 1, "Amul Butter (100g)", 56.0, "piece", true, "Dairy", "butter"),
            ProductEntity(0, 1, "Brooke Bond Red Label Tea (250g)", 110.0, "packet", true, "Tea & Coffee", "tea"),
            ProductEntity(0, 1, "Sugar / Chini (1 kg)", 44.0, "kg", true, "Groceries", "sugar"),
            ProductEntity(0, 1, "Haldi / Turmeric Powder (200g)", 50.0, "packet", true, "Spices", "haldi"),
            ProductEntity(0, 1, "Red Chili Powder (200g)", 70.0, "packet", true, "Spices", "chili"),
            ProductEntity(0, 1, "Garam Masala Powder (100g)", 45.0, "packet", true, "Spices", "masala"),

            // Saini Fresh Sabji (shopId = 2)
            ProductEntity(0, 2, "Fresh Potato (Aloo) (1 kg)", 25.0, "kg", true, "Vegetables", "potato"),
            ProductEntity(0, 2, "Fresh Onion (Pyaj) (1 kg)", 40.0, "kg", true, "Vegetables", "onion"),
            ProductEntity(0, 2, "Fresh Tomato (Tamatar) (1 kg)", 30.0, "kg", true, "Vegetables", "tomato"),
            ProductEntity(0, 2, "Fresh Paneer (Dairy) (250g)", 90.0, "g", true, "Vegetables & Dairy", "paneer"),
            ProductEntity(0, 2, "Green Peas (Matar) (500g)", 60.0, "g", true, "Vegetables", "peas"),
            ProductEntity(0, 2, "Fresh Ginger & Garlic Mix (100g)", 20.0, "packet", true, "Vegetables", "ginger_garlic"),
            ProductEntity(0, 2, "Green Chilies (Hari Mirch) (100g)", 10.0, "g", true, "Vegetables", "green_chili"),
            ProductEntity(0, 2, "Fresh Coriander (Hara Dhaniya) (100g)", 12.0, "g", true, "Vegetables", "coriander"),

            // Verma Mithai Bhandar (shopId = 3)
            ProductEntity(0, 3, "Premium Kaju Katli (250g)", 250.0, "packet", true, "Sweets", "kaju_katli"),
            ProductEntity(0, 3, "Special Gulab Jamun (4 pcs)", 60.0, "plate", true, "Sweets", "gulab_jamun"),
            ProductEntity(0, 3, "Motichoor Laddu (500g)", 160.0, "packet", true, "Sweets", "laddu"),
            ProductEntity(0, 3, "Fresh Crispy Samosa (1 pc)", 15.0, "piece", true, "Snacks", "samosa"),
            ProductEntity(0, 3, "Khaman Dhokla (250g)", 50.0, "packet", true, "Snacks", "dhokla"),
            ProductEntity(0, 3, "Rasgulla Sweet (4 pcs)", 60.0, "plate", true, "Sweets", "rasgulla"),

            // Raju Gift & Toy Corner (shopId = 4)
            ProductEntity(0, 4, "Beautiful Photo Frame (Medium)", 150.0, "piece", true, "Home Decor", "frame"),
            ProductEntity(0, 4, "Coffee Mug \"Best Mom Ever\"", 120.0, "piece", true, "Mugs & Gifts", "mug"),
            ProductEntity(0, 4, "Scented Candle Set (4 pack)", 180.0, "packet", true, "Home Decor", "candle"),
            ProductEntity(0, 4, "Teddy Bear (Pink, Soft)", 220.0, "piece", true, "Toys", "teddy"),
            ProductEntity(0, 4, "Luxury Chocolate Box", 300.0, "box", true, "Gifts", "chocolate"),
            ProductEntity(0, 4, "Gold-plated Puja Thali", 450.0, "piece", true, "Religious Gifts", "thali"),

            // Arogya Medical (shopId = 5)
            ProductEntity(0, 5, "Paracetamol 650mg Tablets", 30.0, "strip", true, "Medicines", "paracetamol"),
            ProductEntity(0, 5, "Vicks Vaporub (25g)", 85.0, "piece", true, "Medicines", "vicks"),
            ProductEntity(0, 5, "ORS Powder Orange (1 pack)", 20.0, "packet", true, "Wellness", "ors"),
            ProductEntity(0, 5, "Hand Sanitizer Gel (100ml)", 50.0, "piece", true, "Wellness", "sanitizer"),
            ProductEntity(0, 5, "Digene Acidity Relief Liquid", 140.0, "bottle", true, "Medicines", "digene"),

            // Shyam Stationery (shopId = 6)
            ProductEntity(0, 6, "Classmate Notebook (120 pages)", 40.0, "piece", true, "Notebooks", "notebook"),
            ProductEntity(0, 6, "Reynolds Gel Pen (Blue, pack of 5)", 50.0, "packet", true, "Pens", "pens"),
            ProductEntity(0, 6, "Nataraj Pencil Box", 60.0, "box", true, "Pencils", "pencils"),
            ProductEntity(0, 6, "Drawing Sheet set (10 sheets)", 30.0, "packet", true, "Art Supplies", "drawing_sheets"),

            // Milan Garments (shopId = 7)
            ProductEntity(0, 7, "Cotton Saree (Red & Gold)", 450.0, "piece", true, "Women's Wear", "saree"),
            ProductEntity(0, 7, "Casual T-Shirt (Blue, Medium)", 250.0, "piece", true, "Men's Wear", "tshirt"),
            ProductEntity(0, 7, "Kid's Frock (Multicolor)", 350.0, "piece", true, "Kids' Wear", "frock"),
            ProductEntity(0, 7, "Warm Shawl (Woolen)", 499.0, "piece", true, "Winter Wear", "shawl"),

            // TechHub Electronics (shopId = 8)
            ProductEntity(0, 8, "Wireless Earbuds", 1499.0, "piece", true, "Audio", "earbuds"),
            ProductEntity(0, 8, "Fast Charging Power Bank 10000mAh", 999.0, "piece", true, "Accessories", "power_bank"),
            ProductEntity(0, 8, "USB-C Fast Charging Cable", 299.0, "piece", true, "Accessories", "cable"),
            ProductEntity(0, 8, "Smart Fitness Band", 1999.0, "piece", true, "Wearables", "smart_band"),

            // Sunrise Bakery (shopId = 9)
            ProductEntity(0, 9, "Fresh Black Forest Cake (500g)", 350.0, "piece", true, "Cakes", "cake"),
            ProductEntity(0, 9, "Butter Cookies (250g)", 120.0, "packet", true, "Cookies", "cookies"),
            ProductEntity(0, 9, "Whole Wheat Bread", 40.0, "packet", true, "Bread", "bread"),
            ProductEntity(0, 9, "Chocolate Brownie", 60.0, "piece", true, "Desserts", "brownie"),

            // Champion Sports (shopId = 10)
            ProductEntity(0, 10, "Cricket Bat (Kashmir Willow)", 1200.0, "piece", true, "Cricket", "bat"),
            ProductEntity(0, 10, "Football (Size 5)", 450.0, "piece", true, "Football", "football"),
            ProductEntity(0, 10, "Badminton Racket Set", 650.0, "set", true, "Badminton", "racket"),
            ProductEntity(0, 10, "Yoga Mat (6mm)", 350.0, "piece", true, "Fitness", "yoga_mat")
        )
        for (prod in products) {
            dao.insertProduct(prod)
        }
    }
}

// --- Repository Pattern ---

class StoreRepository(private val dao: StoreDao) {
    val userFlow: Flow<UserEntity?> = dao.getUserFlow()
    suspend fun getUserSync(): UserEntity? = dao.getUserSync()
    suspend fun saveUser(user: UserEntity) = dao.insertUser(user)

    val allShops: Flow<List<ShopEntity>> = dao.getAllShops()
    fun getShopsByCategory(category: String): Flow<List<ShopEntity>> = dao.getShopsByCategory(category)
    suspend fun getShopById(shopId: Long): ShopEntity? = dao.getShopById(shopId)
    suspend fun saveShop(shop: ShopEntity): Long = dao.insertShop(shop)

    val allProducts: Flow<List<ProductEntity>> = dao.getAllProducts()
    fun getProductsByShop(shopId: Long): Flow<List<ProductEntity>> = dao.getProductsByShop(shopId)
    suspend fun getProductById(productId: Long): ProductEntity? = dao.getProductById(productId)
    fun searchProducts(query: String): Flow<List<ProductEntity>> = dao.searchProducts(query)
    suspend fun searchProductsSync(query: String): List<ProductEntity> = dao.searchProductsSync(query)
    suspend fun saveProduct(product: ProductEntity): Long = dao.insertProduct(product)
    suspend fun updateProduct(product: ProductEntity) = dao.updateProduct(product)

    val cartItems: Flow<List<CartItemEntity>> = dao.getCartItems()
    suspend fun addToCart(productId: Long, shopId: Long, quantity: Int) {
        dao.addToCart(CartItemEntity(productId = productId, shopId = shopId, quantity = quantity))
    }
    suspend fun removeFromCart(productId: Long) = dao.removeCartItemByProductId(productId)
    suspend fun updateCartQuantity(cartItemId: Long, quantity: Int) = dao.updateCartQuantity(cartItemId, quantity)
    suspend fun clearCart() = dao.clearCart()

    val allOrders: Flow<List<OrderEntity>> = dao.getAllOrders()
    fun getOrdersByShop(shopId: Long): Flow<List<OrderEntity>> = dao.getOrdersByShop(shopId)
    suspend fun getOrderById(orderId: Long): OrderEntity? = dao.getOrderById(orderId)
    suspend fun placeOrder(order: OrderEntity, items: List<CartItemEntity>, productGetter: suspend (Long) -> ProductEntity?): Long {
        val orderId = dao.insertOrder(order)
        for (item in items) {
            val product = productGetter(item.productId)
            if (product != null) {
                dao.insertOrderItem(
                    OrderItemEntity(
                        orderId = orderId,
                        productName = product.name,
                        price = product.price,
                        quantity = item.quantity,
                        imageUrl = product.imageUrl
                    )
                )
            }
        }
        dao.clearCart()
        return orderId
    }
    suspend fun updateOrderStatus(orderId: Long, status: String) = dao.updateOrderStatus(orderId, status)
    fun getOrderItems(orderId: Long): Flow<List<OrderItemEntity>> = dao.getOrderItems(orderId)
}
