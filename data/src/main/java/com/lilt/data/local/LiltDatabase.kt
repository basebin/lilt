package com.lilt.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "threads")
data class ThreadEntity(
    @PrimaryKey val id: String,
    val name: String,
    val handle: String,
    val initials: String,
    val accentArgb: Long,
    val unreadCount: Int = 0,
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val author: String,
    val body: String,
    val timeLabel: String,
    val deliveryStatus: String,
    val createdAtMillis: Long,
)

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT * FROM messages ORDER BY createdAtMillis ASC")
    suspend fun messages(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY createdAtMillis ASC")
    suspend fun messagesForThread(threadId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE author = :author AND deliveryStatus = :status ORDER BY createdAtMillis ASC")
    suspend fun messagesWithStatus(author: String, status: String): List<MessageEntity>

    @Query("UPDATE messages SET deliveryStatus = :status WHERE id = :messageId")
    suspend fun updateDeliveryStatus(messageId: String, status: String)
}

@Dao
interface ThreadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThreads(threads: List<ThreadEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThread(thread: ThreadEntity)

    @Query("SELECT * FROM threads ORDER BY name ASC")
    suspend fun threads(): List<ThreadEntity>

    @Query("SELECT * FROM threads WHERE id = :threadId LIMIT 1")
    suspend fun thread(threadId: String): ThreadEntity?

    @Query("UPDATE threads SET unreadCount = :unreadCount WHERE id = :threadId")
    suspend fun updateUnreadCount(threadId: String, unreadCount: Int)

    @Query("SELECT COUNT(*) FROM threads")
    suspend fun count(): Int
}

@Database(
    entities = [ThreadEntity::class, MessageEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class LiltDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun threadDao(): ThreadDao
}
