package com.example.data.local.cache

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "translation_cache",
    indices = [Index(value = ["sourceText", "sourceLang", "targetLang"], unique = true)]
)
data class TranslationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceText: String,
    val targetText: String,
    val sourceLang: String = "zh",
    val targetLang: String = "ar",
    val provider: String,
    val timestamp: Long = System.currentTimeMillis(),
    val hitCount: Int = 1
)

@Dao
interface TranslationDao {
    @Query("SELECT * FROM translation_cache WHERE sourceText = :sourceText AND sourceLang = :sourceLang AND targetLang = :targetLang LIMIT 1")
    suspend fun getTranslation(sourceText: String, sourceLang: String, targetLang: String): TranslationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslation(entity: TranslationEntity): Long

    @Query("UPDATE translation_cache SET hitCount = hitCount + 1, timestamp = :now WHERE id = :id")
    suspend fun updateHit(id: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM translation_cache ORDER BY timestamp DESC LIMIT 100")
    fun getRecentTranslations(): Flow<List<TranslationEntity>>

    @Query("SELECT COUNT(*) FROM translation_cache")
    fun getCacheCount(): Flow<Int>

    @Query("DELETE FROM translation_cache WHERE timestamp < :expireThreshold")
    suspend fun evictStaleTranslations(expireThreshold: Long)

    @Query("DELETE FROM translation_cache WHERE id IN (SELECT id FROM translation_cache ORDER BY timestamp ASC LIMIT :count)")
    suspend fun evictOldest(count: Int)

    @Query("DELETE FROM translation_cache")
    suspend fun clearAll()
}

@Database(entities = [TranslationEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun translationDao(): TranslationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "onscreen_translator.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
