package com.lilt.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadReadStateTest {
    @Test
    fun unreadMessageCountReturnsOneForUnreadMessageFromAnotherUser() {
        assertEquals(
            1,
            unreadMessageCount(
                lastSenderId = "friend",
                currentUserId = "me",
                lastMessageAtMillis = 200,
                readAtMillis = 100,
            ),
        )
    }

    @Test
    fun unreadMessageCountReturnsZeroForOwnMessage() {
        assertEquals(
            0,
            unreadMessageCount(
                lastSenderId = "me",
                currentUserId = "me",
                lastMessageAtMillis = 200,
                readAtMillis = 100,
            ),
        )
    }

    @Test
    fun unreadMessageCountReturnsZeroWhenThreadHasBeenRead() {
        assertEquals(
            0,
            unreadMessageCount(
                lastSenderId = "friend",
                currentUserId = "me",
                lastMessageAtMillis = 200,
                readAtMillis = 200,
            ),
        )
    }
}
