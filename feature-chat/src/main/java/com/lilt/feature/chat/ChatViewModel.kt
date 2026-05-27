package com.lilt.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lilt.domain.chat.ChatRepository
import com.lilt.domain.chat.ChatThread
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

data class ChatUiState(
    val threads: List<ChatThread> = emptyList(),
    val selectedThreadId: String? = null,
    val loading: Boolean = true,
    val startingThread: Boolean = false,
    val startThreadError: String? = null,
) {
    val selectedThread: ChatThread?
        get() = threads.firstOrNull { it.id == selectedThreadId } ?: threads.firstOrNull()
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
) : ViewModel() {
    var state = androidx.compose.runtime.mutableStateOf(ChatUiState())
        private set

    init {
        viewModelScope.launch {
            val initialThreads = repository.threads()
            state.value = ChatUiState(
                threads = initialThreads,
                selectedThreadId = initialThreads.firstOrNull()?.id,
                loading = false,
            )
            val retriedThreads = repository.retryQueuedMessages()
            state.value = state.value.copy(threads = retriedThreads)
        }
        viewModelScope.launch {
            repository.observeThreads().collect { threads ->
                state.value = state.value.copy(
                    threads = threads,
                    selectedThreadId = state.value.selectedThreadId ?: threads.firstOrNull()?.id,
                    loading = false,
                )
            }
        }
    }

    fun selectThread(threadId: String) {
        state.value = state.value.copy(selectedThreadId = threadId)
        viewModelScope.launch {
            state.value = state.value.copy(
                threads = repository.markThreadRead(threadId),
                selectedThreadId = threadId,
            )
        }
    }

    fun sendMessage(body: String) {
        val threadId = state.value.selectedThread?.id ?: return
        viewModelScope.launch {
            state.value = state.value.copy(
                threads = repository.sendMessage(threadId, body),
                selectedThreadId = threadId,
            )
        }
    }

    fun startThread(phoneNumber: String) {
        viewModelScope.launch {
            state.value = state.value.copy(startingThread = true, startThreadError = null)
            runCatching {
                repository.startThread(phoneNumber)
            }.onSuccess { threads ->
                state.value = state.value.copy(
                    threads = threads,
                    selectedThreadId = threads.firstOrNull()?.id,
                    startingThread = false,
                )
            }.onFailure { throwable ->
                state.value = state.value.copy(
                    startingThread = false,
                    startThreadError = throwable.message ?: "Could not start that chat.",
                )
            }
        }
    }

    fun clearStartThreadError() {
        state.value = state.value.copy(startThreadError = null)
    }

    fun retryQueuedMessages() {
        viewModelScope.launch {
            state.value = state.value.copy(
                threads = repository.retryQueuedMessages(),
            )
        }
    }
}
