package com.lilt.data.chat

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import com.lilt.data.local.MessageDao
import com.lilt.data.local.MessageEntity
import com.lilt.data.local.ThreadDao
import com.lilt.data.local.ThreadEntity
import com.lilt.domain.chat.ChatRepository
import com.lilt.domain.chat.ChatThread
import com.lilt.domain.chat.IncomingMessage
import com.lilt.domain.chat.Message
import com.lilt.domain.chat.MessageAuthor
import com.lilt.domain.chat.MessageDeliveryStatus
import com.lilt.domain.chat.unreadMessageCount
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Singleton
class FirestoreChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val firebaseMessaging: FirebaseMessaging,
    private val threadDao: ThreadDao,
    private val messageDao: MessageDao,
) : ChatRepository {
    override suspend fun threads(): List<ChatThread> {
        val user = firebaseAuth.currentUser ?: return loadThreads()
        upsertUserProfile(user.uid, user.phoneNumber, user.displayName)
        syncCloudThreads(user.uid)
        seedCloudThreadIfNeeded(user.uid)
        syncCloudThreads(user.uid)
        return loadThreads()
    }

    override fun observeThreads(): Flow<List<ChatThread>> = callbackFlow {
        val uid = firebaseAuth.currentUser?.uid
        if (uid == null) {
            trySend(loadThreads())
            close()
            return@callbackFlow
        }

        val listenerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val messageRegistrations = mutableMapOf<String, ListenerRegistration>()
        val registration = firestore.collection("threads")
            .whereArrayContains("participantIds", uid)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    listenerScope.launch { trySend(loadThreads()) }
                    return@addSnapshotListener
                }

                val threadDocuments = snapshots?.documents.orEmpty()
                val threadEntities = threadDocuments.map { document ->
                    document.toThreadEntity(uid)
                }
                if (threadEntities.isNotEmpty()) {
                    listenerScope.launch {
                        runCatching {
                            threadDao.insertThreads(threadEntities)
                            trySend(loadThreads())
                        }
                    }
                }

                threadDocuments.forEach { threadDocument ->
                    if (messageRegistrations.containsKey(threadDocument.id)) return@forEach
                    firestore.collection("threads")
                        .document(threadDocument.id)
                        .collection("messages")
                        .orderBy("createdAtMillis", Query.Direction.ASCENDING)
                        .addSnapshotListener { messageSnapshots, _ ->
                            listenerScope.launch {
                                runCatching {
                                    val messages = messageSnapshots?.documents.orEmpty().map { messageDocument ->
                                        messageDocument.toMessageEntity(
                                            uid = uid,
                                            threadId = threadDocument.id,
                                        )
                                    }
                                    if (messages.isNotEmpty()) {
                                        messageDao.insertMessages(messages)
                                    }
                                    trySend(loadThreads())
                                }
                            }
                        }
                        .also { messageRegistration ->
                            messageRegistrations[threadDocument.id] = messageRegistration
                        }
                }

                listenerScope.launch { trySend(loadThreads()) }
            }

        awaitClose {
            registration.remove()
            messageRegistrations.values.forEach { it.remove() }
            listenerScope.cancel()
        }
    }

    override suspend fun startThread(phoneNumber: String): List<ChatThread> {
        val user = firebaseAuth.currentUser ?: error("Sign in before starting a chat.")
        val normalizedPhone = phoneNumber.normalizedPhoneNumber()
        val target = firestore.collection("users")
            .whereEqualTo("phoneNumber", normalizedPhone)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?: error("No Lilt account found for that phone number.")
        val targetUid = target.id
        val targetPhone = target.getString("phoneNumber").orEmpty().ifBlank { normalizedPhone }
        val targetName = target.getString("displayName").orEmpty().ifBlank { targetPhone }
        val currentPhone = user.phoneNumber.orEmpty()
        val currentName = user.displayName.orEmpty().ifBlank { currentPhone }
        val participantIds = listOf(user.uid, targetUid).distinct().sorted()
        val threadId = "dm-${participantIds.joinToString("-")}"
        val now = System.currentTimeMillis()
        val threadReference = firestore.collection("threads").document(threadId)

        if (!threadReference.get().await().exists()) {
            threadReference.set(
                mapOf(
                    "name" to targetName,
                    "handle" to targetPhone,
                    "initials" to targetName.initials().ifBlank { targetPhone.takeLast(2) },
                    "accentArgb" to 0xFF2BB3A3,
                    "participantIds" to participantIds,
                    "participantPhones" to mapOf(
                        user.uid to currentPhone,
                        targetUid to targetPhone,
                    ),
                    "participantNames" to mapOf(
                        user.uid to currentName,
                        targetUid to targetName,
                    ),
                    "lastMessage" to "",
                    "lastMessageAtMillis" to now,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastSenderId" to "",
                    "readBy" to mapOf(user.uid to now),
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
                .await()
        }

        syncCloudThreads(user.uid)
        return loadThreads()
    }

    override suspend fun sendMessage(threadId: String, body: String): List<ChatThread> {
        val user = firebaseAuth.currentUser ?: return queueLocalMessage(threadId, body)
        val now = System.currentTimeMillis()
        val messageId = UUID.randomUUID().toString()
        val cleanBody = body.decodeAdbTextInput()
        val message = MessageEntity(
            id = messageId,
            threadId = threadId,
            author = MessageAuthor.Me.name,
            body = cleanBody,
            timeLabel = timeFormatter.format(Date(now)),
            deliveryStatus = MessageDeliveryStatus.Sending.name,
            createdAtMillis = now,
        )
        messageDao.insertMessage(message)

        runCatching {
            firestore.collection("threads")
                .document(threadId)
                .collection("messages")
                .document(messageId)
                .set(
                    mapOf(
                        "id" to messageId,
                        "senderId" to user.uid,
                        "body" to cleanBody,
                        "createdAtMillis" to now,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                )
                .await()

            firestore.collection("threads")
                .document(threadId)
                .update(
                    mapOf(
                        "lastMessage" to cleanBody,
                        "lastMessageAtMillis" to now,
                        "lastMessageAt" to FieldValue.serverTimestamp(),
                        "lastSenderId" to user.uid,
                        "readBy.${user.uid}" to now,
                    ),
                )
                .await()
        }.onSuccess {
            messageDao.updateDeliveryStatus(messageId, MessageDeliveryStatus.Sent.name)
        }.onFailure {
            messageDao.updateDeliveryStatus(messageId, MessageDeliveryStatus.Queued.name)
        }

        syncCloudThreads(user.uid)
        return loadThreads()
    }

    override suspend fun receiveMessage(message: IncomingMessage): List<ChatThread> {
        if (threadDao.thread(message.threadId) == null) {
            threadDao.insertThread(
                ThreadEntity(
                    id = message.threadId,
                    name = message.senderName,
                    handle = message.senderName.lowercase().replace(" ", "."),
                    initials = message.senderName.initials(),
                    accentArgb = 0xFF697789,
                ),
            )
        }
        messageDao.insertMessage(
            MessageEntity(
                id = message.id,
                threadId = message.threadId,
                author = MessageAuthor.Friend.name,
                body = message.body,
                timeLabel = timeFormatter.format(Date(message.receivedAtMillis)),
                deliveryStatus = MessageDeliveryStatus.Sent.name,
                createdAtMillis = message.receivedAtMillis,
            ),
        )
        return loadThreads()
    }

    override suspend fun markThreadRead(threadId: String): List<ChatThread> {
        val user = firebaseAuth.currentUser ?: return loadThreads()
        val now = System.currentTimeMillis()
        runCatching {
            firestore.collection("threads")
                .document(threadId)
                .update("readBy.${user.uid}", now)
                .await()
        }
        threadDao.updateUnreadCount(threadId, unreadCount = 0)
        return loadThreads()
    }

    override suspend fun retryQueuedMessages(): List<ChatThread> {
        val user = firebaseAuth.currentUser ?: return loadThreads()
        val queuedMessages = messageDao.messagesWithStatus(
            author = MessageAuthor.Me.name,
            status = MessageDeliveryStatus.Queued.name,
        )

        queuedMessages.forEach { message ->
            messageDao.updateDeliveryStatus(message.id, MessageDeliveryStatus.Sending.name)
            runCatching {
                firestore.collection("threads")
                    .document(message.threadId)
                    .collection("messages")
                    .document(message.id)
                    .set(
                        mapOf(
                            "id" to message.id,
                            "senderId" to user.uid,
                        "body" to message.body,
                        "createdAtMillis" to message.createdAtMillis,
                        "createdAt" to FieldValue.serverTimestamp(),
                        ),
                    )
                    .await()
                firestore.collection("threads")
                    .document(message.threadId)
                    .update(
                        mapOf(
                            "lastMessage" to message.body,
                            "lastMessageAtMillis" to message.createdAtMillis,
                            "lastMessageAt" to FieldValue.serverTimestamp(),
                            "lastSenderId" to user.uid,
                            "readBy.${user.uid}" to message.createdAtMillis,
                        ),
                    )
                    .await()
            }.onSuccess {
                messageDao.updateDeliveryStatus(message.id, MessageDeliveryStatus.Sent.name)
            }.onFailure {
                messageDao.updateDeliveryStatus(message.id, MessageDeliveryStatus.Queued.name)
            }
        }

        syncCloudThreads(user.uid)
        return loadThreads()
    }

    private suspend fun upsertUserProfile(uid: String, phoneNumber: String?, displayName: String?) {
        val fcmToken = runCatching { firebaseMessaging.token.await() }.getOrNull()
        firestore.collection("users")
            .document(uid)
            .set(
                buildMap {
                    put("id", uid)
                    put("phoneNumber", phoneNumber.orEmpty())
                    put("updatedAt", FieldValue.serverTimestamp())
                    if (!displayName.isNullOrBlank()) {
                        put("displayName", displayName)
                    }
                    if (fcmToken != null) {
                        put("fcmTokens", FieldValue.arrayUnion(fcmToken))
                    }
                },
                com.google.firebase.firestore.SetOptions.merge(),
            )
            .await()
    }

    private suspend fun syncCloudThreads(uid: String) {
        val threadSnapshots = firestore.collection("threads")
            .whereArrayContains("participantIds", uid)
            .get()
            .await()

        val threadEntities = threadSnapshots.documents.map { document ->
            document.toThreadEntity(uid)
        }
        if (threadEntities.isNotEmpty()) {
            threadDao.insertThreads(threadEntities)
        }

        threadSnapshots.documents.forEach { threadDocument ->
            val messages = firestore.collection("threads")
                .document(threadDocument.id)
                .collection("messages")
                .orderBy("createdAtMillis", Query.Direction.ASCENDING)
                .get()
                .await()
                .documents
                .map { messageDocument ->
                    messageDocument.toMessageEntity(uid = uid, threadId = threadDocument.id)
                }
            if (messages.isNotEmpty()) {
                messageDao.insertMessages(messages)
            }
        }
    }

    private suspend fun seedCloudThreadIfNeeded(uid: String) {
        val count = firestore.collection("threads")
            .whereArrayContains("participantIds", uid)
            .limit(1)
            .get()
            .await()
            .size()
        if (count > 0) return

        val now = System.currentTimeMillis()
        val threadId = "welcome-$uid"
        val messageId = "welcome-message-$uid"
        firestore.collection("threads")
            .document(threadId)
            .set(
                mapOf(
                    "name" to "Lilt",
                    "handle" to "lilt",
                    "initials" to "LI",
                    "accentArgb" to 0xFF2BB3A3,
                    "participantIds" to listOf(uid),
                    "lastMessage" to "Your first real chat is ready.",
                    "lastMessageAtMillis" to now,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastSenderId" to "system",
                    "readBy" to mapOf(uid to now),
                ),
            )
            .await()
        firestore.collection("threads")
            .document(threadId)
            .collection("messages")
            .document(messageId)
            .set(
                mapOf(
                    "id" to messageId,
                    "senderId" to "system",
                    "body" to "Your first real chat is ready.",
                    "createdAtMillis" to now,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            .await()
    }

    private suspend fun queueLocalMessage(threadId: String, body: String): List<ChatThread> {
        val now = System.currentTimeMillis()
        messageDao.insertMessage(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                threadId = threadId,
                author = MessageAuthor.Me.name,
                body = body.decodeAdbTextInput(),
                timeLabel = timeFormatter.format(Date(now)),
                deliveryStatus = MessageDeliveryStatus.Queued.name,
                createdAtMillis = now,
            ),
        )
        return loadThreads()
    }

    private suspend fun loadThreads(): List<ChatThread> {
        val messagesByThread = messageDao.messages().groupBy { it.threadId }
        return threadDao.threads().map { thread ->
            thread.toDomain(
                messages = messagesByThread[thread.id].orEmpty().map { it.toDomain() },
            )
        }.sortedByDescending { thread ->
            messagesByThread[thread.id]
                ?.maxOfOrNull { it.createdAtMillis }
                ?: Long.MIN_VALUE
        }
    }

    private companion object {
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    }
}

private fun ThreadEntity.toDomain(messages: List<Message>): ChatThread = ChatThread(
    id = id,
    name = name,
    handle = handle,
    initials = initials,
    accentArgb = accentArgb,
    unreadCount = unreadCount,
    messages = messages,
)

private fun MessageEntity.toDomain(): Message = Message(
    id = id,
    threadId = threadId,
    author = runCatching { MessageAuthor.valueOf(author) }.getOrDefault(MessageAuthor.Friend),
    body = body,
    timeLabel = timeLabel,
    deliveryStatus = runCatching {
        MessageDeliveryStatus.valueOf(deliveryStatus)
    }.getOrDefault(MessageDeliveryStatus.Sent),
)

private fun com.google.firebase.firestore.DocumentSnapshot.toThreadEntity(uid: String): ThreadEntity {
    val participantPhones = get("participantPhones") as? Map<*, *>
    val participantNames = get("participantNames") as? Map<*, *>
    val otherName = participantNames
        ?.entries
        ?.firstOrNull { it.key != uid }
        ?.value
        ?.toString()
    val otherPhone = participantPhones
        ?.entries
        ?.firstOrNull { it.key != uid }
        ?.value
        ?.toString()
    val title = otherName ?: otherPhone ?: getString("name").orEmpty().ifBlank { "Lilt" }
    val readBy = get("readBy") as? Map<*, *>
    val readAtMillis = (readBy?.get(uid) as? Number)?.toLong() ?: 0L
    val lastMessageAtMillis = getLong("lastMessageAtMillis") ?: 0L
    val lastSenderId = getString("lastSenderId").orEmpty()
    val unreadCount = unreadMessageCount(
        lastSenderId = lastSenderId,
        currentUserId = uid,
        lastMessageAtMillis = lastMessageAtMillis,
        readAtMillis = readAtMillis,
    )
    return ThreadEntity(
        id = id,
        name = title,
        handle = otherPhone ?: getString("handle").orEmpty().ifBlank { "lilt" },
        initials = title.initials().ifBlank {
            (otherPhone ?: getString("initials")).orEmpty().takeLast(2).ifBlank { "LI" }
        },
        accentArgb = getLong("accentArgb") ?: 0xFF2BB3A3,
        unreadCount = unreadCount,
    )
}

private fun com.google.firebase.firestore.DocumentSnapshot.toMessageEntity(
    uid: String,
    threadId: String,
): MessageEntity {
    val senderId = getString("senderId").orEmpty()
    val createdAtMillis = getLong("createdAtMillis") ?: 0L
    return MessageEntity(
        id = id,
        threadId = threadId,
        author = if (senderId == uid) MessageAuthor.Me.name else MessageAuthor.Friend.name,
        body = getString("body").orEmpty(),
        timeLabel = FirestoreChatRepositoryTime.format(createdAtMillis),
        deliveryStatus = MessageDeliveryStatus.Sent.name,
        createdAtMillis = createdAtMillis,
    )
}

private object FirestoreChatRepositoryTime {
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun format(createdAtMillis: Long): String = timeFormatter.format(Date(createdAtMillis))
}

private fun String.initials(): String {
    val parts = trim()
        .split(" ")
        .filter { it.isNotBlank() }
    return parts
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .joinToString("")
        .ifBlank { "??" }
}

private fun String.normalizedPhoneNumber(): String = trim().replace(" ", "").replace("%2B", "+")

private fun String.decodeAdbTextInput(): String = replace("%20", " ").replace("%2B", "+")
