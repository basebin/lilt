package com.lilt.feature.chat

import com.lilt.domain.chat.ChatThread
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatUiStateTest {
    @Test
    fun selectedThreadUsesRequestedThreadWhenPresent() {
        val first = thread("one")
        val second = thread("two")

        val state = ChatUiState(
            threads = listOf(first, second),
            selectedThreadId = "two",
        )

        assertEquals(second, state.selectedThread)
    }

    @Test
    fun selectedThreadFallsBackToFirstThread() {
        val first = thread("one")

        val state = ChatUiState(
            threads = listOf(first),
            selectedThreadId = "missing",
        )

        assertEquals(first, state.selectedThread)
    }

    private fun thread(id: String): ChatThread =
        ChatThread(
            id = id,
            name = id,
            handle = id,
            initials = id.take(2).uppercase(),
            accentArgb = 0xFF2BB3A3,
            messages = emptyList(),
        )
}
