package com.lilt.domain.chat

fun unreadMessageCount(
    lastSenderId: String,
    currentUserId: String,
    lastMessageAtMillis: Long,
    readAtMillis: Long,
): Int =
    if (lastSenderId.isNotBlank() && lastSenderId != currentUserId && lastMessageAtMillis > readAtMillis) {
        1
    } else {
        0
    }
