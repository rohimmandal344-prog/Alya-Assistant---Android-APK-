package com.example.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val timestamp: Long,
    val status: String,
    val actionType: String? = null,
    val actionTarget: String? = null,
    val actionSuccess: Boolean? = null,
    val actionVerified: Boolean? = null,
    val actionDetails: String? = null
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val memoryKey: String,
    val category: String,
    val content: String,
    val updatedAt: Long
)

@Entity(tableName = "action_audit_logs")
data class ActionAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: String,
    val target: String?,
    val success: Boolean,
    val verified: Boolean,
    val message: String,
    val details: String,
    val timestamp: Long
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY timestamp ASC")
    fun getAllMessagesFlow(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<ConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationEntity): Long

    @Query("UPDATE conversations SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("DELETE FROM conversations")
    suspend fun clearAll()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun getAllMemoriesFlow(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    suspend fun getAllMemoriesList(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE memoryKey = :key LIMIT 1")
    suspend fun getMemory(key: String): MemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE memoryKey = :key")
    suspend fun deleteMemory(key: String)

    @Query("DELETE FROM memories")
    suspend fun clearAll()
}

@Dao
interface ActionAuditDao {
    @Query("SELECT * FROM action_audit_logs ORDER BY timestamp DESC LIMIT 200")
    fun getRecentAuditLogsFlow(): Flow<List<ActionAuditEntity>>

    @Query("SELECT * FROM action_audit_logs WHERE success = 1 ORDER BY timestamp DESC LIMIT 200")
    fun getSuccessfulAuditLogsFlow(): Flow<List<ActionAuditEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ActionAuditEntity): Long

    @Query("DELETE FROM action_audit_logs")
    suspend fun clearAuditLogs()
}

@Database(
    entities = [ConversationEntity::class, MemoryEntity::class, ActionAuditEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AlyaDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun memoryDao(): MemoryDao
    abstract fun actionAuditDao(): ActionAuditDao

    companion object {
        @Volatile
        private var INSTANCE: AlyaDatabase? = null

        fun getDatabase(context: Context): AlyaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AlyaDatabase::class.java,
                    "alya_assistant.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
