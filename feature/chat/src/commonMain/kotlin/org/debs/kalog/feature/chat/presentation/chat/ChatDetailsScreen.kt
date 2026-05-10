package org.debs.kalog.feature.chat.presentation.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.debs.kalog.feature.chat.presentation.platform.hasSoftwareKeyboard
import org.debs.kalog.feature.chat.domain.model.AvatarAccent
import org.debs.kalog.feature.chat.domain.model.AvatarSpec
import org.debs.kalog.feature.chat.domain.model.ChatAttachment
import org.debs.kalog.feature.chat.domain.model.ChatAttachmentKind
import org.debs.kalog.feature.chat.domain.model.ChatMessage
import org.debs.kalog.feature.chat.domain.model.DeliveryStatus
import org.debs.kalog.feature.chat.domain.model.PreparedChatAttachment
import org.debs.kalog.feature.chat.presentation.components.AvatarBadge

@Composable
fun ChatDetailsScreen(
    chatId: String,
    state: ChatDetailsUiState,
    onBack: () -> Unit,
    onDraftChanged: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachFileClick: () -> Unit,
    onPickImageClick: () -> Unit,
    onRecordVoiceClick: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onLoadMoreMessages: () -> Unit,
    onInviteUserClick: () -> Unit,
    onChatInfoClick: () -> Unit = {},
    onAcceptInvitation: () -> Unit = {},
    onDeclineInvitation: () -> Unit = {},
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {
    val listState = rememberLazyListState()
    val defaultSnackbarHostState = remember { SnackbarHostState() }
    val resolvedSnackbarHostState = snackbarHostState ?: defaultSnackbarHostState
    val hasLoadMoreItem = state.hasMoreMessages || state.isLoadingMoreMessages

    val showScrollToBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 2 ||
                (listState.firstVisibleItemIndex > 0 && listState.firstVisibleItemScrollOffset > 0)
        }
    }

    var shouldAutoScroll by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        snapshotFlow { !showScrollToBottom }
            .collect { nearBottom -> shouldAutoScroll = nearBottom }
    }

    LaunchedEffect(state.messages.lastOrNull()?.id) {
        if (shouldAutoScroll) {
            listState.scrollToItem(0)
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore, state.hasMoreMessages, state.isLoadingMoreMessages) {
        if (shouldLoadMore && state.hasMoreMessages && !state.isLoadingMoreMessages) {
            onLoadMoreMessages()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            ChatHeader(
                state = state,
                onBack = onBack,
                onInviteUserClick = onInviteUserClick,
                onChatInfoClick = onChatInfoClick,
            )
        },
        snackbarHost = {
            SnackbarHost(resolvedSnackbarHostState)
        },
        bottomBar = {
            if (state.isPendingInvitation) {
                InvitationBanner(
                    isProcessing = state.isProcessingInvitation,
                    onAccept = onAcceptInvitation,
                    onDecline = onDeclineInvitation,
                )
            } else {
                MessageComposer(
                    draft = state.draft,
                    pendingAttachments = state.pendingAttachments,
                    isPreparingAttachment = state.isPreparingAttachment,
                    canSend = state.canSend,
                    onDraftChanged = onDraftChanged,
                    onSendClick = onSendClick,
                    onAttachFileClick = onAttachFileClick,
                    onPickImageClick = onPickImageClick,
                    onRecordVoiceClick = onRecordVoiceClick,
                    onRemoveAttachment = onRemoveAttachment,
                )
            }
        },
    ) { innerPadding ->
        val coroutineScope = rememberCoroutineScope()

        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFEAF4FB),
                                Color(0xFFF8FBFF),
                            ),
                        ),
                    ),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = state.messages.asReversed(),
                    key = { item -> item.id },
                ) { message ->
                    when (message) {
                        is ChatMessage.Service -> ServiceMessageBubble(message)
                        is ChatMessage.User -> UserMessageBubble(message)
                    }
                }

                if (hasLoadMoreItem) {
                    item(key = "load-more") {
                        LoadMoreMessagesIndicator(
                            isLoading = state.isLoadingMoreMessages,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showScrollToBottom,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                Surface(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable {
                            coroutineScope.launch {
                                listState.animateScrollToItem(0)
                            }
                        },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.96f),
                    shadowElevation = 6.dp,
                    tonalElevation = 2.dp,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Scroll to bottom",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadMoreMessagesIndicator(
    isLoading: Boolean,
) {
    if (!isLoading) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.96f),
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
        ) {
            Text(
                text = "Loading older messages...",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ChatHeader(
    state: ChatDetailsUiState,
    onBack: () -> Unit,
    onInviteUserClick: () -> Unit,
    onChatInfoClick: () -> Unit = {},
) {
    Surface(
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBackIos,
                    contentDescription = null,
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onChatInfoClick)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarBadge(
                    avatar = state.avatar,
                    modifier = Modifier.size(46.dp),
                )
                Column {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (state.canInviteUsers) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(onClick = onInviteUserClick),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "Invite",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceMessageBubble(
    message: ChatMessage.Service,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xFFDDE9F2),
        ) {
            Text(
                text = message.body,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun UserMessageBubble(
    message: ChatMessage.User,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = if (message.isMine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            shape = if (message.isMine) {
                RoundedCornerShape(topStart = 22.dp, topEnd = 8.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
            } else {
                RoundedCornerShape(topStart = 8.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
            },
            color = if (message.isMine) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.White.copy(alpha = 0.98f)
            },
            tonalElevation = if (message.isMine) 0.dp else 2.dp,
            shadowElevation = if (message.isMine) 0.dp else 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!message.isMine) {
                    Text(
                        text = message.sender,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (message.isMine) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = if (message.isMine) {
                        "${message.timestamp}  |  ${message.deliveryStatus.label()}"
                    } else {
                        message.timestamp
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (message.isMine) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun InvitationBanner(
    isProcessing: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Surface(
        modifier = Modifier.imePadding(),
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "You have been invited to this chat.\nConfirm to start messaging.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(enabled = !isProcessing, onClick = onDecline),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = "Decline",
                        modifier = Modifier
                            .padding(vertical = 14.dp)
                            .fillMaxWidth(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(enabled = !isProcessing, onClick = onAccept),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = if (isProcessing) "Processing..." else "Accept",
                        modifier = Modifier
                            .padding(vertical = 14.dp)
                            .fillMaxWidth(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageComposer(
    draft: String,
    pendingAttachments: List<PreparedChatAttachment>,
    isPreparingAttachment: Boolean,
    canSend: Boolean,
    onDraftChanged: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachFileClick: () -> Unit,
    onPickImageClick: () -> Unit,
    onRecordVoiceClick: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var suppressKeyboard by remember { mutableStateOf(hasSoftwareKeyboard) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        modifier = Modifier.imePadding(),
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (pendingAttachments.isNotEmpty()) {
                PendingAttachmentDrafts(
                    attachments = pendingAttachments,
                    onRemoveAttachment = onRemoveAttachment,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                AttachmentActionButton(
                    icon = Icons.Outlined.AttachFile,
                    contentDescription = "Attach file",
                    enabled = !isPreparingAttachment,
                    onClick = onAttachFileClick,
                )
                AttachmentActionButton(
                    icon = Icons.Outlined.PhotoLibrary,
                    contentDescription = "Pick image",
                    enabled = !isPreparingAttachment,
                    onClick = onPickImageClick,
                )
                AttachmentActionButton(
                    icon = Icons.Outlined.Mic,
                    contentDescription = "Record voice",
                    enabled = !isPreparingAttachment,
                    onClick = onRecordVoiceClick,
                )
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    val scrollState = rememberScrollState()
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChanged,
                        readOnly = suppressKeyboard,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        if (event.type == PointerEventType.Press) {
                                            suppressKeyboard = false
                                        }
                                    }
                                }
                            }
                            .onPreviewKeyEvent { event ->
                                if (hasSoftwareKeyboard) return@onPreviewKeyEvent false
                                if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                                    if (event.isCtrlPressed) {
                                        onDraftChanged(draft + "\n")
                                        true
                                    } else {
                                        if (canSend) {
                                            onSendClick()
                                            focusRequester.requestFocus()
                                        }
                                        true
                                    }
                                } else {
                                    false
                                }
                            }
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .verticalScroll(scrollState)
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        decorationBox = { innerTextField ->
                            if (draft.isBlank()) {
                                Text(
                                    text = "Write a message",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        },
                    )
                }
                Surface(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = canSend, onClick = {
                            onSendClick()
                            focusRequester.requestFocus()
                        }),
                    shape = CircleShape,
                    color = if (canSend) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    },
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Send",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.12f else 0.06f),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.45f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun PendingAttachmentDrafts(
    attachments: List<PreparedChatAttachment>,
    onRemoveAttachment: (String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { prepared ->
            val attachment = prepared.attachment
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = attachment.kind.label(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = attachment.displayName(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Remove attachment",
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .clickable { onRemoveAttachment(attachment.id) },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun ChatAttachmentKind.label(): String {
    return when (this) {
        ChatAttachmentKind.File -> "FILE"
        ChatAttachmentKind.Image -> "IMG"
        ChatAttachmentKind.Voice -> "VOICE"
    }
}

private fun ChatAttachment.displayName(): String {
    return when {
        durationMillis != null -> "$name (${durationMillis / 1000}s)"
        sizeBytes != null -> "$name (${sizeBytes.toReadableBytes()})"
        else -> name
    }
}

private fun Long.toReadableBytes(): String {
    if (this < 1024L) return "$this B"
    val kib = this / 1024.0
    if (kib < 1024.0) return "${kib.formatOneDecimal()} KB"
    return "${(kib / 1024.0).formatOneDecimal()} MB"
}

private fun Double.formatOneDecimal(): String {
    val scaled = (this * 10).toInt()
    return "${scaled / 10}.${scaled % 10}"
}

private fun DeliveryStatus.label(): String {
    return when (this) {
        DeliveryStatus.Sending -> "Sending"
        DeliveryStatus.Sent -> "Sent"
        DeliveryStatus.Read -> "Read"
    }
}

@Composable
@Preview
private fun MessagePreview() {
    ChatDetailsScreen(
        chatId = "preview",
        state = ChatDetailsUiState(
            title = "Elena Morozova",
            subtitle = "online",
            avatar = AvatarSpec("EM", AvatarAccent.Rose),
            messages = listOf(
                ChatMessage.User(
                    id = "1",
                    sender = "Elena",
                    body = "The changelog draft looks good.",
                    timestamp = "08:28",
                    isMine = false,
                    deliveryStatus = DeliveryStatus.Read,
                ),
                ChatMessage.User(
                    id = "2",
                    sender = "You",
                    body = "I will send the final version after review.",
                    timestamp = "08:29",
                    isMine = true,
                    deliveryStatus = DeliveryStatus.Sent,
                ),
            ),
        ),
        onBack = {},
        onSendClick = {},
        onDraftChanged = {},
        onAttachFileClick = {},
        onPickImageClick = {},
        onRecordVoiceClick = {},
        onRemoveAttachment = {},
        onLoadMoreMessages = {},
        onInviteUserClick = {},
    )
}
