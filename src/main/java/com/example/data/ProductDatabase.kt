package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,
    val imageUrl: String,
    val merchant: String,
    val currentPrice: Double,
    val lowestPrice: Double,
    val highestPrice: Double,
    val targetPrice: Double,
    val isTracking: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "price_history")
data class PriceHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val productId: Int,
    val price: Double,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY createdAt DESC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    fun getProductById(id: Int): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductByIdOneShot(id: Int): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProductById(id: Int)

    // Price History
    @Query("SELECT * FROM price_history WHERE productId = :productId ORDER BY timestamp ASC")
    fun getPriceHistoryForProduct(productId: Int): Flow<List<PriceHistoryEntity>>

    @Query("SELECT * FROM price_history WHERE productId = :productId ORDER BY timestamp ASC")
    suspend fun getPriceHistoryForProductOneShot(productId: Int): List<PriceHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceHistory(history: PriceHistoryEntity)

    @Query("DELETE FROM price_history WHERE productId = :productId")
    suspend fun deletePriceHistoryForProduct(productId: Int)
}

@Database(entities = [ProductEntity::class, PriceHistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract val productDao: ProductDao
}

class ProductRepository(private val productDao: ProductDao) {
    val allProducts: Flow<List<ProductEntity>> = productDao.getAllProducts()

    fun getProductById(id: Int): Flow<ProductEntity?> = productDao.getProductById(id)

    suspend fun getProductByIdOneShot(id: Int): ProductEntity? = productDao.getProductByIdOneShot(id)

    suspend fun insertProduct(product: ProductEntity): Long = productDao.insertProduct(product)

    suspend fun updateProduct(product: ProductEntity) = productDao.updateProduct(product)

    suspend fun deleteProduct(id: Int) {
        productDao.deleteProductById(id)
        productDao.deletePriceHistoryForProduct(id)
    }

    fun getPriceHistory(productId: Int): Flow<List<PriceHistoryEntity>> = productDao.getPriceHistoryForProduct(productId)

    suspend fun getPriceHistoryOneShot(productId: Int): List<PriceHistoryEntity> = productDao.getPriceHistoryForProductOneShot(productId)

    suspend fun addPriceRecord(productId: Int, price: Double, timestamp: Long = System.currentTimeMillis()) {
        productDao.insertPriceHistory(PriceHistoryEntity(productId = productId, price = price, timestamp = timestamp))
    }
}
