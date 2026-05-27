package com.lilt.domain.chat

import kotlinx.coroutines.flow.Flow

data class ChatThread(
    val id: String,
    val name: String,
    val handle: String,
    val initials: String,
    val accentArgb: Long,
    val unreadCount: Int = 0,
    val messages: List<Message>,
)

data class Message(
    val id: String,
    val threadId: String,
    val author: MessageAuthor,
    val body: String,
    val timeLabel: String,
    val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.Sent,
)

enum class MessageAuthor {
    Me,
    Friend,
}

enum class MessageDeliveryStatus {
    Sending,
    Sent,
    Queued,
    Failed,
}

interface ChatRepository {
    suspend fun threads(): List<ChatThread>
    fun observeThreads(): Flow<List<ChatThread>>
    suspend fun startThread(phoneNumber: String): List<ChatThread>
    suspend fun sendMessage(threadId: String, body: String): List<ChatThread>
    suspend fun receiveMessage(message: IncomingMessage): List<ChatThread>
    suspend fun markThreadRead(threadId: String): List<ChatThread>
    suspend fun retryQueuedMessages(): List<ChatThread>
}

data class IncomingMessage(
    val id: String,
    val threadId: String,
    val senderName: String,
    val body: String,
    val receivedAtMillis: Long,
)
