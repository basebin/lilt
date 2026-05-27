package com.lilt.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.lilt.core.theme.LiltColors
import com.lilt.domain.chat.ChatThread
import com.lilt.domain.chat.Message
import com.lilt.domain.chat.MessageAuthor
import com.lilt.domain.chat.MessageDeliveryStatus

@Composable
fun ChatRoute(
    profileLabel: String = "You",
    onSignOut: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state
    var showingConversation by remember { mutableStateOf(false) }
    var showingNewChat by remember { mutableStateOf(false) }
    var newChatSubmitted by remember { mutableStateOf(false) }
    val selectedThread = state.selectedThread

    LaunchedEffect(showingNewChat, newChatSubmitted, state.startingThread, state.startThreadError, state.threads.size) {
        if (showingNewChat && newChatSubmitted && !state.startingThread && state.startThreadError == null) {
            showingNewChat = false
            newChatSubmitted = false
        }
    }

    if (showingNewChat) {
        NewChatDialog(
            starting = state.startingThread,
            errorMessage = state.startThreadError,
            onDismiss = {
                showingNewChat = false
                newChatSubmitted = false
                viewModel.clearStartThreadError()
            },
            onStart = { phoneNumber ->
                newChatSubmitted = true
                viewModel.startThread(phoneNumber)
            },
        )
    }

    if (showingConversation && selectedThread != null) {
        ConversationScreen(
            thread = selectedThread,
            onBack = { showingConversation = false },
            onSendMessage = viewModel::sendMessage,
            onRetryQueued = viewModel::retryQueuedMessages,
        )
    } else {
        ChatListScreen(
            profileLabel = profileLabel,
            onSignOut = onSignOut,
            onNewChat = { showingNewChat = true },
            loading = state.loading,
            threads = state.threads,
            onOpenThread = {
                viewModel.selectThread(it.id)
                showingConversation = true
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatListScreen(
    profileLabel: String,
    onSignOut: () -> Unit,
    onNewChat: () -> Unit,
    loading: Boolean,
    threads: List<ChatThread>,
    onOpenThread: (ChatThread) -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = LiltColors.Background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Lilt", color = LiltColors.Ink, fontWeight = FontWeight.Black)
                        Text(
                            "Chats",
                            color = LiltColors.Muted,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onNewChat,
                        colors = ButtonDefaults.textButtonColors(contentColor = LiltColors.Teal),
                    ) {
                        Text("New", fontWeight = FontWeight.Bold)
                    }
                    ProfileChip(
                        profileLabel = profileLabel,
                        onSignOut = onSignOut,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LiltColors.Background),
            )
        },
    ) { padding ->
        when {
            loading -> QuietState(
                text = "Loading chats",
                modifier = Modifier.padding(padding),
            )

            threads.isEmpty() -> QuietState(
                text = "No chats yet",
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(threads, key = { it.id }) { thread ->
                    ChatListItem(thread = thread, onClick = { onOpenThread(thread) })
                }
            }
        }
    }
}

@Composable
private fun NewChatDialog(
    starting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onStart: (String) -> Unit,
) {
    var phoneNumber by remember { mutableStateOf("") }
    val normalizedPhone = phoneNumber.trim().replace(" ", "").replace("%2B", "+")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text("New chat", color = LiltColors.Ink, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("+1 5555550100") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = LiltColors.Field,
                        unfocusedContainerColor = LiltColors.Field,
                        focusedIndicatorColor = LiltColors.Teal,
                        unfocusedIndicatorColor = LiltColors.Line,
                    ),
                )
                errorMessage?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = it,
                        color = LiltColors.Teal,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = normalizedPhone.startsWith("+") && normalizedPhone.length >= 8 && !starting,
                onClick = { onStart(normalizedPhone) },
                colors = ButtonDefaults.textButtonColors(contentColor = LiltColors.Teal),
            ) {
                Text(if (starting) "Starting" else "Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = LiltColors.Muted)
            }
        },
    )
}

@Composable
private fun ProfileChip(
    profileLabel: String,
    onSignOut: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(end = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable(onClick = onSignOut)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(LiltColors.Ink),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = profileLabel.initialLabel(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.width(7.dp))
        Text(
            text = profileLabel,
            color = LiltColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun QuietState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = LiltColors.Muted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ChatListItem(thread: ChatThread, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(thread, 48)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(thread.name, color = LiltColors.Ink, fontWeight = FontWeight.Bold)
            Text(
                text = thread.messages.lastOrNull()?.body.orEmpty(),
                color = LiltColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = thread.messages.lastOrNull()?.timeLabel.orEmpty(),
                color = LiltColors.Muted,
                style = MaterialTheme.typography.labelSmall,
            )
            if (thread.unreadCount > 0) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(LiltColors.Teal),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = thread.unreadCount.coerceAtMost(9).toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationScreen(
    thread: ChatThread,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onRetryQueued: () -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        containerColor = LiltColors.Background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { ConversationTitle(thread) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back", color = LiltColors.Teal)
                    }
                },
                actions = {
                    TextButton(
                        onClick = onRetryQueued,
                        colors = ButtonDefaults.textButtonColors(contentColor = thread.accentColor()),
                    ) {
                        Text("Retry")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
        bottomBar = { MessageComposer(onSendMessage = onSendMessage) },
    ) { padding ->
        MessageList(
            messages = thread.messages,
            accent = thread.accentColor(),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
private fun ConversationTitle(thread: ChatThread) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(thread, 38)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = thread.name,
                color = LiltColors.Ink,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "@${thread.handle} is online",
                color = LiltColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun MessageList(messages: List<Message>, accent: Color, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
    ) {
        items(messages.reversed(), key = { it.id }) { message ->
            MessageBubble(message, accent)
        }
    }
}

@Composable
private fun MessageBubble(message: Message, accent: Color) {
    val isMine = message.author == MessageAuthor.Me
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .clip(
                    RoundedCornerShape(
                        topStart = 8.dp,
                        topEnd = 8.dp,
                        bottomStart = if (isMine) 8.dp else 2.dp,
                        bottomEnd = if (isMine) 2.dp else 8.dp,
                    ),
                )
                .background(if (isMine) LiltColors.Ink else Color.White)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = message.body,
                color = if (isMine) Color.White else LiltColors.Ink,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isMine) {
                    "${message.timeLabel} · ${message.deliveryStatus.label()}"
                } else {
                    message.timeLabel
                },
                color = if (isMine) Color.White.copy(alpha = 0.72f) else accent,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun MessageComposer(onSendMessage: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message") },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = LiltColors.Field,
                unfocusedContainerColor = LiltColors.Field,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        Spacer(Modifier.width(8.dp))
        TextButton(
            onClick = {
                val message = draft.trim().decodeAdbTextInput()
                if (message.isNotEmpty()) {
                    onSendMessage(message)
                    draft = ""
                }
            },
            colors = ButtonDefaults.textButtonColors(contentColor = LiltColors.Teal),
        ) {
            Text("Send", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Avatar(thread: ChatThread, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(thread.accentColor()),
        contentAlignment = Alignment.Center,
    ) {
        Text(thread.initials, color = Color.White, fontWeight = FontWeight.Black)
    }
}

private fun ChatThread.accentColor(): Color = Color(accentArgb)

private fun MessageDeliveryStatus.label(): String = when (this) {
    MessageDeliveryStatus.Sending -> "sending"
    MessageDeliveryStatus.Sent -> "sent"
    MessageDeliveryStatus.Queued -> "queued"
    MessageDeliveryStatus.Failed -> "failed"
}

private fun String.initialLabel(): String =
    trim().firstOrNull()?.uppercaseChar()?.toString() ?: "Y"

private fun String.decodeAdbTextInput(): String = replace("%20", " ").replace("%2B", "+")
