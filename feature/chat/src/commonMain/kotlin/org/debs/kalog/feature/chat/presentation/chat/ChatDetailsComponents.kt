package org.debs.kalog.feature.chat.presentation.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import mayday_chat.feature.chat.generated.resources.*
import org.debs.kalog.feature.chat.domain.model.*
import org.debs.kalog.feature.chat.presentation.components.AvatarBadge
import org.debs.kalog.feature.chat.presentation.components.SavedMessagesAvatar
import org.debs.kalog.feature.chat.presentation.platform.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sqrt

@Composable
internal fun MessageList(
    messages: List<ChatMessage>,
    listState: LazyListState,
    hasLoadMoreItem: Boolean,
    isLoadingMoreMessages: Boolean,
    showScrollToBottom: Boolean,
    onScrollToBottom: () -> Unit,
    onOpenMedia: (ChatAttachment) -> Unit,
    onRequestAttachmentDownload: (String) -> Unit,
    playingVoiceAttachmentId: String?,
    voicePlaybackProgress: Map<String, VoicePlaybackProgress>,
    onToggleVoicePlayback: (ChatAttachment) -> Unit,
    onSeekVoicePlayback: (ChatAttachment, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                items = messages.asReversed(),
                key = { item -> item.id },
            ) { message ->
                when (message) {
                    is ChatMessage.Service -> ServiceMessageBubble(message)
                    is ChatMessage.User -> UserMessageBubble(
                        message = message,
                        onOpenMedia = onOpenMedia,
                        onRequestAttachmentDownload = onRequestAttachmentDownload,
                        playingVoiceAttachmentId = playingVoiceAttachmentId,
                        voicePlaybackProgress = voicePlaybackProgress,
                        onToggleVoicePlayback = onToggleVoicePlayback,
                        onSeekVoicePlayback = onSeekVoicePlayback,
                    )
                }
            }

            if (hasLoadMoreItem) {
                item(key = "load-more") {
                    LoadMoreMessagesIndicator(
                        isLoading = isLoadingMoreMessages,
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
                    .clickable(onClick = onScrollToBottom),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(Res.string.scroll_to_bottom),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
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
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Text(
                text = stringResource(Res.string.loading_older_messages),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun ChatTopBar(
    state: ChatDetailsUiState,
    onBack: () -> Unit,
    onInviteUserClick: () -> Unit,
    onChatInfoClick: () -> Unit = {},
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(onClick = onBack),
                shape = MaterialTheme.shapes.medium,
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBackIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
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
                if (state.type == ChatType.Self) {
                    SavedMessagesAvatar(modifier = Modifier.size(46.dp))
                } else {
                    AvatarBadge(
                        avatar = state.avatar,
                        modifier = Modifier.size(46.dp),
                    )
                }
                Column {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.subtitle.isNotBlank()) {
                        Text(
                            text = state.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (state.canInviteUsers) {
                Surface(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(onClick = onInviteUserClick),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        text = stringResource(Res.string.invite),
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
internal fun ServiceMessageBubble(
    message: ChatMessage.Service,
) {
    val clipboardManager = LocalClipboardManager.current
    var isCopyMenuVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box {
            Surface(
                modifier = Modifier.messageContextMenuGestures(
                    enabled = message.body.isNotBlank(),
                    onOpen = { isCopyMenuVisible = true },
                ),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            ) {
                MessageSelectionContainer(isMine = false) {
                    Text(
                        text = message.body,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            MessageCopyDropdownMenu(
                expanded = isCopyMenuVisible,
                onDismiss = { isCopyMenuVisible = false },
                onCopy = {
                    clipboardManager.setText(AnnotatedString(message.body))
                },
            )
        }
    }
}

@Composable
internal fun UserMessageBubble(
    message: ChatMessage.User,
    onOpenMedia: (ChatAttachment) -> Unit,
    onRequestAttachmentDownload: (String) -> Unit,
    playingVoiceAttachmentId: String?,
    voicePlaybackProgress: Map<String, VoicePlaybackProgress>,
    onToggleVoicePlayback: (ChatAttachment) -> Unit,
    onSeekVoicePlayback: (ChatAttachment, Long) -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    var isCopyMenuVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = if (message.isMine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            modifier = Modifier.messageContextMenuGestures(
                enabled = message.body.isNotBlank(),
                onOpen = { isCopyMenuVisible = true },
            ),
            shape = if (message.isMine) {
                RoundedCornerShape(topStart = 14.dp, topEnd = 6.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
            } else {
                RoundedCornerShape(topStart = 6.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
            },
            color = if (message.isMine) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surface
            },
            border = if (message.isMine) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                if (message.body.isNotBlank()) {
                    MessageSelectionContainer(isMine = message.isMine) {
                        Text(
                            text = message.body,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (message.isMine) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
                val mediaAttachments = message.attachments.filter { attachment -> attachment.isGridMedia() }
                val otherAttachments = message.attachments.filterNot { attachment -> attachment.isGridMedia() }
                mediaAttachments.chunked(MAX_MEDIA_GRID_ITEMS).forEach { mediaGroup ->
                    if (mediaGroup.size == 1) {
                        MessageAttachmentChip(
                            attachment = mediaGroup.first(),
                            isMine = message.isMine,
                            onOpenMedia = onOpenMedia,
                            onRequestAttachmentDownload = onRequestAttachmentDownload,
                            isVoicePlaying = playingVoiceAttachmentId == mediaGroup.first().id,
                            voicePlaybackProgress = voicePlaybackProgress[mediaGroup.first().id],
                            onToggleVoicePlayback = onToggleVoicePlayback,
                            onSeekVoicePlayback = onSeekVoicePlayback,
                        )
                    } else {
                        MediaAttachmentGrid(
                            attachments = mediaGroup,
                            isMine = message.isMine,
                            onOpenMedia = onOpenMedia,
                            onRequestAttachmentDownload = onRequestAttachmentDownload,
                        )
                    }
                }
                otherAttachments.forEach { attachment ->
                    MessageAttachmentChip(
                        attachment = attachment,
                        isMine = message.isMine,
                        onOpenMedia = onOpenMedia,
                        onRequestAttachmentDownload = onRequestAttachmentDownload,
                        isVoicePlaying = playingVoiceAttachmentId == attachment.id,
                        voicePlaybackProgress = voicePlaybackProgress[attachment.id],
                        onToggleVoicePlayback = onToggleVoicePlayback,
                        onSeekVoicePlayback = onSeekVoicePlayback,
                    )
                }
                MessageFooter(message)
            }
        }
        MessageCopyDropdownMenu(
            expanded = isCopyMenuVisible,
            onDismiss = { isCopyMenuVisible = false },
            onCopy = {
                clipboardManager.setText(AnnotatedString(message.body))
            },
        )
    }
}

@Composable
private fun MessageFooter(message: ChatMessage.User) {
    val footerColor = if (message.isMine) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    if (message.isMine) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message.timestamp,
                style = MaterialTheme.typography.labelMedium,
                color = footerColor,
            )
            DeliveryStatusIndicator(
                status = message.deliveryStatus,
                tint = footerColor,
            )
        }
    } else {
        Text(
            text = message.timestamp,
            style = MaterialTheme.typography.labelMedium,
            color = footerColor,
        )
    }
}

@Composable
private fun DeliveryStatusIndicator(
    status: DeliveryStatus,
    tint: Color,
) {
    when (status) {
        DeliveryStatus.Sending -> {
            CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                strokeWidth = 1.5.dp,
                color = tint,
            )
        }
        DeliveryStatus.Sent -> {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = stringResource(Res.string.delivery_sent),
                tint = tint,
                modifier = Modifier.size(15.dp),
            )
        }
        DeliveryStatus.Failed -> {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = status.label(),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun MessageSelectionContainer(
    isMine: Boolean,
    content: @Composable () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val selectionColors = if (isMine) {
        val textColor = colorScheme.onPrimary
        val selectionBackgroundColor = if (textColor.luminance() > 0.5f) {
            Color.Black.copy(alpha = 0.28f)
        } else {
            Color.White.copy(alpha = 0.36f)
        }
        TextSelectionColors(
            handleColor = textColor,
            backgroundColor = selectionBackgroundColor,
        )
    } else {
        TextSelectionColors(
            handleColor = colorScheme.primary,
            backgroundColor = colorScheme.primary.copy(alpha = 0.28f),
        )
    }

    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        SelectionContainer(content = content)
    }
}

@Composable
private fun MessageCopyDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.copy_text)) },
            onClick = {
                onCopy()
                onDismiss()
            },
        )
    }
}

private fun Modifier.messageContextMenuGestures(
    enabled: Boolean,
    onOpen: () -> Unit,
): Modifier {
    if (!enabled) return this

    return pointerInput(onOpen) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    onOpen()
                    continue
                }

                val down = event.changes.firstOrNull { change ->
                    change.pressed && !change.previousPressed
                } ?: continue
                val pointerId = down.id
                val startPosition = down.position
                var canceled = false

                val completedBeforeTimeout = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val nextEvent = awaitPointerEvent(PointerEventPass.Initial)
                        if (
                            nextEvent.type == PointerEventType.Press &&
                            nextEvent.buttons.isSecondaryPressed
                        ) {
                            onOpen()
                            canceled = true
                            break
                        }

                        val change = nextEvent.changes.firstOrNull { it.id == pointerId }
                        if (change == null || !change.pressed) {
                            canceled = true
                            break
                        }

                        val delta = change.position - startPosition
                        val distance = sqrt(delta.x * delta.x + delta.y * delta.y)
                        if (distance > viewConfiguration.touchSlop) {
                            canceled = true
                            break
                        }
                    }
                    true
                }

                if (completedBeforeTimeout == null && !canceled) {
                    onOpen()
                }
            }
        }
    }
}

@Composable
private fun MediaAttachmentGrid(
    attachments: List<ChatAttachment>,
    isMine: Boolean,
    onOpenMedia: (ChatAttachment) -> Unit,
    onRequestAttachmentDownload: (String) -> Unit,
) {
    val media = attachments.take(MAX_MEDIA_GRID_ITEMS)
    Column(
        modifier = Modifier.widthIn(max = 320.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (media.size) {
            2 -> MediaGridRow(media, isMine, onOpenMedia, onRequestAttachmentDownload, height = 154.dp)
            3 -> {
                MediaGridRow(media.take(1), isMine, onOpenMedia, onRequestAttachmentDownload, height = 172.dp)
                MediaGridRow(media.drop(1), isMine, onOpenMedia, onRequestAttachmentDownload, height = 104.dp)
            }
            4 -> {
                MediaGridRow(media.take(2), isMine, onOpenMedia, onRequestAttachmentDownload, height = 154.dp)
                MediaGridRow(media.drop(2), isMine, onOpenMedia, onRequestAttachmentDownload, height = 154.dp)
            }
            5 -> {
                MediaGridRow(media.take(1), isMine, onOpenMedia, onRequestAttachmentDownload, height = 164.dp)
                MediaGridRow(media.drop(1).take(2), isMine, onOpenMedia, onRequestAttachmentDownload, height = 98.dp)
                MediaGridRow(media.drop(3), isMine, onOpenMedia, onRequestAttachmentDownload, height = 98.dp)
            }
            6 -> {
                MediaGridRow(media.take(3), isMine, onOpenMedia, onRequestAttachmentDownload, height = 104.dp)
                MediaGridRow(media.drop(3), isMine, onOpenMedia, onRequestAttachmentDownload, height = 104.dp)
            }
            7 -> {
                MediaGridRow(media.take(1), isMine, onOpenMedia, onRequestAttachmentDownload, height = 154.dp)
                MediaGridRow(media.drop(1).take(3), isMine, onOpenMedia, onRequestAttachmentDownload, height = 90.dp)
                MediaGridRow(media.drop(4), isMine, onOpenMedia, onRequestAttachmentDownload, height = 90.dp)
            }
            8 -> {
                MediaGridRow(media.take(2), isMine, onOpenMedia, onRequestAttachmentDownload, height = 126.dp)
                MediaGridRow(media.drop(2).take(3), isMine, onOpenMedia, onRequestAttachmentDownload, height = 90.dp)
                MediaGridRow(media.drop(5), isMine, onOpenMedia, onRequestAttachmentDownload, height = 90.dp)
            }
            9 -> {
                MediaGridRow(media.take(3), isMine, onOpenMedia, onRequestAttachmentDownload, height = 88.dp)
                MediaGridRow(media.drop(3).take(3), isMine, onOpenMedia, onRequestAttachmentDownload, height = 88.dp)
                MediaGridRow(media.drop(6), isMine, onOpenMedia, onRequestAttachmentDownload, height = 88.dp)
            }
            else -> {
                MediaGridRow(media.take(2), isMine, onOpenMedia, onRequestAttachmentDownload, height = 122.dp)
                MediaGridRow(media.drop(2).take(4), isMine, onOpenMedia, onRequestAttachmentDownload, height = 72.dp)
                MediaGridRow(media.drop(6).take(4), isMine, onOpenMedia, onRequestAttachmentDownload, height = 72.dp)
            }
        }
    }
}

@Composable
private fun MediaGridRow(
    attachments: List<ChatAttachment>,
    isMine: Boolean,
    onOpenMedia: (ChatAttachment) -> Unit,
    onRequestAttachmentDownload: (String) -> Unit,
    height: Dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        attachments.forEach { attachment ->
            MediaGridCell(
                attachment = attachment,
                isMine = isMine,
                onOpenMedia = onOpenMedia,
                onRequestAttachmentDownload = onRequestAttachmentDownload,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun MediaGridCell(
    attachment: ChatAttachment,
    isMine: Boolean,
    onOpenMedia: (ChatAttachment) -> Unit,
    onRequestAttachmentDownload: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageBitmap = rememberAttachmentImagePreviewBitmap(attachment)
    val thumbnailBitmap = rememberVideoThumbnailBitmap(attachment)
    val canOpen = when (attachment.kind) {
        ChatAttachmentKind.Image -> imageBitmap != null
        ChatAttachmentKind.Video -> attachment.localUri != null
        else -> false
    }
    val canRequestDownload = attachment.localUri == null && attachment.loadState.canRequestDownload()
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = canOpen || canRequestDownload) {
                if (canOpen) {
                    onOpenMedia(attachment)
                } else {
                    onRequestAttachmentDownload(attachment.id)
                }
            },
        shape = RoundedCornerShape(8.dp),
        color = if (isMine) {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                imageBitmap != null -> Image(
                    bitmap = imageBitmap,
                    contentDescription = attachment.displayName(),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                thumbnailBitmap != null -> Image(
                    bitmap = thumbnailBitmap,
                    contentDescription = attachment.displayName(),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                else -> MediaLoadingPlaceholderContent(attachment.loadState)
            }
            if (attachment.kind == ChatAttachmentKind.Video && attachment.localUri != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(38.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.58f),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = stringResource(Res.string.play_video),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageAttachmentChip(
    attachment: ChatAttachment,
    isMine: Boolean,
    onOpenMedia: (ChatAttachment) -> Unit,
    onRequestAttachmentDownload: (String) -> Unit,
    isVoicePlaying: Boolean,
    voicePlaybackProgress: VoicePlaybackProgress?,
    onToggleVoicePlayback: (ChatAttachment) -> Unit,
    onSeekVoicePlayback: (ChatAttachment, Long) -> Unit,
) {
    when (attachment.kind) {
        ChatAttachmentKind.Image -> {
            ImageAttachmentPreview(attachment, isMine, onOpenMedia, onRequestAttachmentDownload)
            return
        }
        ChatAttachmentKind.Video -> {
            VideoAttachmentPreview(
                attachment = attachment,
                isMine = isMine,
                onOpenVideo = onOpenMedia,
                onRequestAttachmentDownload = onRequestAttachmentDownload,
            )
            return
        }
        ChatAttachmentKind.Voice -> {
            VoiceAttachmentPreview(
                attachment = attachment,
                isMine = isMine,
                isPlaying = isVoicePlaying,
                progress = voicePlaybackProgress,
                onTogglePlayback = onToggleVoicePlayback,
                onRequestAttachmentDownload = onRequestAttachmentDownload,
                onSeekPlayback = onSeekVoicePlayback,
            )
            return
        }
        ChatAttachmentKind.Audio -> {
            AudioAttachmentPreview(
                attachment = attachment,
                isMine = isMine,
                isPlaying = isVoicePlaying,
                progress = voicePlaybackProgress,
                onTogglePlayback = onToggleVoicePlayback,
                onRequestAttachmentDownload = onRequestAttachmentDownload,
                onSeekPlayback = onSeekVoicePlayback,
            )
            return
        }
        ChatAttachmentKind.File -> Unit
    }

    val canRequestDownload = attachment.localUri == null && attachment.loadState.canRequestDownload()
    val canOpen = attachment.localUri != null
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = canOpen || canRequestDownload) {
                if (canOpen) {
                    openLocalAttachment(attachment)
                } else {
                    onRequestAttachmentDownload(attachment.id)
                }
            },
        shape = RoundedCornerShape(12.dp),
        color = if (isMine) {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (attachment.localUri == null) attachment.loadState.shortLabel() else attachment.kind.label(),
                style = MaterialTheme.typography.labelMedium,
                color = if (isMine) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = attachment.displayNameWithLoadState(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isMine) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ImageAttachmentPreview(
    attachment: ChatAttachment,
    isMine: Boolean,
    onOpenImage: (ChatAttachment) -> Unit,
    onRequestAttachmentDownload: (String) -> Unit,
) {
    val imageBitmap = rememberAttachmentImagePreviewBitmap(attachment)
    val canRequestDownload = attachment.localUri == null && attachment.loadState.canRequestDownload()

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = imageBitmap != null || canRequestDownload) {
                if (imageBitmap != null) {
                    onOpenImage(attachment)
                } else {
                    onRequestAttachmentDownload(attachment.id)
                }
            },
        shape = RoundedCornerShape(14.dp),
        color = if (isMine) {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        },
    ) {
        if (imageBitmap != null) {
            BoxWithConstraints {
            val maxPreviewSide = (maxWidth.value * 0.58f).dp.coerceAtMost(220.dp)
            val ratio = imageBitmap.width.toFloat() / imageBitmap.height.toFloat()
            val previewWidth = if (ratio >= 1f) {
                maxPreviewSide
            } else {
                (maxPreviewSide.value * ratio).dp
            }
            val previewHeight = if (ratio >= 1f) {
                (maxPreviewSide.value / ratio).dp
            } else {
                maxPreviewSide
            }
            Image(
                bitmap = imageBitmap,
                contentDescription = attachment.displayName(),
                modifier = Modifier
                    .width(previewWidth)
                    .height(previewHeight),
                contentScale = ContentScale.Fit,
            )
            }
        } else {
            MediaLoadingPlaceholder(
                attachment = attachment,
                isMine = isMine,
                title = stringResource(Res.string.image),
                onRequestDownload = { onRequestAttachmentDownload(attachment.id) },
            )
        }
    }
}

@Composable
internal fun MediaViewer(
    gallery: FullScreenMediaGallery,
    onGalleryChanged: (FullScreenMediaGallery) -> Unit,
    onDismiss: () -> Unit,
) {
    val media = gallery.selectedMedia ?: return
    var galleryImageBitmaps by remember(gallery.items) { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    val hasPrevious = gallery.selectedIndex > 0
    val hasNext = gallery.selectedIndex < gallery.items.lastIndex
    val positionLabel = if (gallery.items.size > 1) {
        "${gallery.selectedIndex + 1}/${gallery.items.size}"
    } else {
        ""
    }
    val onPrevious = {
        if (hasPrevious) {
            onGalleryChanged(gallery.copy(selectedIndex = gallery.selectedIndex - 1))
        }
    }
    val onNext = {
        if (hasNext) {
            onGalleryChanged(gallery.copy(selectedIndex = gallery.selectedIndex + 1))
        }
    }
    fun rememberGalleryImageBitmap(attachmentId: String, bitmap: ImageBitmap) {
        if (galleryImageBitmaps[attachmentId] === bitmap) return
        FullScreenAttachmentImageMemoryCache.put(attachmentId, bitmap)
        val nextBitmaps = LinkedHashMap(galleryImageBitmaps)
        nextBitmaps.remove(attachmentId)
        nextBitmaps[attachmentId] = bitmap
        while (nextBitmaps.size > FULL_SCREEN_GALLERY_BITMAP_CACHE_ITEMS) {
            val oldestKey = nextBitmaps.keys.firstOrNull() ?: break
            nextBitmaps.remove(oldestKey)
        }
        galleryImageBitmaps = nextBitmaps
    }

    when (media.attachment.kind) {
        ChatAttachmentKind.Image -> key(media.attachment.id) {
            FullScreenImageViewer(
                media = media,
                previousMedia = gallery.items.getOrNull(gallery.selectedIndex - 1),
                nextMedia = gallery.items.getOrNull(gallery.selectedIndex + 1),
                imageBitmapSeed = galleryImageBitmaps[media.attachment.id],
                previousImageBitmapSeed = gallery.items.getOrNull(gallery.selectedIndex - 1)
                    ?.attachment
                    ?.id
                    ?.let(galleryImageBitmaps::get),
                nextImageBitmapSeed = gallery.items.getOrNull(gallery.selectedIndex + 1)
                    ?.attachment
                    ?.id
                    ?.let(galleryImageBitmaps::get),
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                positionLabel = positionLabel,
                onPrevious = onPrevious,
                onNext = onNext,
                onDismiss = onDismiss,
                onImageBitmapReady = ::rememberGalleryImageBitmap,
            )
        }
        ChatAttachmentKind.Video -> FullScreenVideoViewer(
            media = media,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            positionLabel = positionLabel,
            onPrevious = onPrevious,
            onNext = onNext,
            onDismiss = onDismiss,
        )
        else -> Unit
    }
}

@Composable
private fun FullScreenImageViewer(
    media: FullScreenMedia,
    previousMedia: FullScreenMedia?,
    nextMedia: FullScreenMedia?,
    imageBitmapSeed: ImageBitmap?,
    previousImageBitmapSeed: ImageBitmap?,
    nextImageBitmapSeed: ImageBitmap?,
    hasPrevious: Boolean,
    hasNext: Boolean,
    positionLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    onImageBitmapReady: (String, ImageBitmap) -> Unit,
) {
    val attachment = media.attachment
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val imageBitmap = rememberAttachmentImageBitmap(
        attachment = attachment,
        imageBitmapSeed = imageBitmapSeed,
        onImageBitmapReady = onImageBitmapReady,
    )
    val previousImageBitmap = rememberOptionalAttachmentImageBitmap(
        attachment = previousMedia?.attachment?.takeIf { item -> item.kind == ChatAttachmentKind.Image },
        imageBitmapSeed = previousImageBitmapSeed,
        onImageBitmapReady = onImageBitmapReady,
    )
    val nextImageBitmap = rememberOptionalAttachmentImageBitmap(
        attachment = nextMedia?.attachment?.takeIf { item -> item.kind == ChatAttachmentKind.Image },
        imageBitmapSeed = nextImageBitmapSeed,
        onImageBitmapReady = onImageBitmapReady,
    )
    var scale by remember(attachment.id) { mutableStateOf(1f) }
    var offset by remember(attachment.id) { mutableStateOf(Offset.Zero) }
    val gallerySlideOffset = remember(attachment.id) { Animatable(0f) }
    var gallerySlideDirection by remember(attachment.id) { mutableStateOf(0) }
    var isGallerySlideActive by remember(attachment.id) { mutableStateOf(false) }
    var settleOffsetJob by remember(attachment.id) { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    Box(
        modifier = fullScreenMediaModifier(onDismiss),
    ) {
        if (imageBitmap != null) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 86.dp),
                contentAlignment = Alignment.Center,
            ) {
                val viewportWidthPx = with(density) { maxWidth.toPx() }
                val viewportHeightPx = with(density) { maxHeight.toPx() }
                fun panBounds(nextScale: Float): Offset {
                    val safeScale = nextScale.coerceAtLeast(1f)
                    return Offset(
                        x = viewportWidthPx * (safeScale - 1f) / 2f,
                        y = viewportHeightPx * (safeScale - 1f) / 2f,
                    )
                }
                fun applyZoom(zoomChange: Float) {
                    settleOffsetJob?.cancel()
                    val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
                    val bounds = panBounds(nextScale)
                    scale = nextScale
                    offset = if (nextScale <= 1.01f) {
                        Offset.Zero
                    } else {
                        offset.coerceToBounds(bounds.x, bounds.y)
                    }
                }
                fun applyPan(panChange: Offset) {
                    settleOffsetJob?.cancel()
                    val bounds = panBounds(scale)
                    val fallbackOverscroll = viewportWidthPx * 0.14f
                    val nextExtra = if (hasNext) viewportWidthPx else fallbackOverscroll
                    val previousExtra = if (hasPrevious) viewportWidthPx else fallbackOverscroll
                    val nextOffset = offset + panChange
                    offset = Offset(
                        x = nextOffset.x.coerceIn(
                            minimumValue = -bounds.x - nextExtra,
                            maximumValue = bounds.x + previousExtra,
                        ),
                        y = if (scale <= 1.01f) {
                            0f
                        } else {
                            nextOffset.y.coerceIn(-bounds.y, bounds.y)
                        },
                    )
                }
                fun animateOffsetTo(targetOffset: Offset) {
                    settleOffsetJob?.cancel()
                    val startOffset = offset
                    if ((startOffset - targetOffset).distance() < 0.5f) {
                        offset = targetOffset
                        return
                    }
                    settleOffsetJob = coroutineScope.launch {
                        val progress = Animatable(0f)
                        progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = FULL_SCREEN_IMAGE_SETTLE_DURATION_MS),
                        ) {
                            offset = Offset(
                                x = startOffset.x + (targetOffset.x - startOffset.x) * value,
                                y = startOffset.y + (targetOffset.y - startOffset.y) * value,
                            )
                        }
                        offset = targetOffset
                        settleOffsetJob = null
                    }
                }
                fun animateGallerySlide(direction: Int, navigate: () -> Unit) {
                    if (isGallerySlideActive) return
                    settleOffsetJob?.cancel()
                    gallerySlideDirection = direction
                    isGallerySlideActive = true
                    val slideDistance = viewportWidthPx * scale.coerceAtLeast(1f) + FULL_SCREEN_GALLERY_SLIDE_MARGIN_PX
                    val targetSlideOffset = direction * slideDistance - offset.x
                    coroutineScope.launch {
                        gallerySlideOffset.snapTo(0f)
                        gallerySlideOffset.animateTo(
                            targetValue = targetSlideOffset,
                            animationSpec = tween(durationMillis = FULL_SCREEN_GALLERY_SLIDE_DURATION_MS),
                        )
                        navigate()
                    }
                }
                fun navigateToPrevious() {
                    if (hasPrevious) {
                        previousMedia?.attachment?.id?.let { attachmentId ->
                            previousImageBitmap?.let { bitmap ->
                                FullScreenAttachmentImageMemoryCache.put(attachmentId, bitmap)
                                onImageBitmapReady(attachmentId, bitmap)
                            }
                        }
                        animateGallerySlide(direction = 1, navigate = onPrevious)
                    }
                }
                fun navigateToNext() {
                    if (hasNext) {
                        nextMedia?.attachment?.id?.let { attachmentId ->
                            nextImageBitmap?.let { bitmap ->
                                FullScreenAttachmentImageMemoryCache.put(attachmentId, bitmap)
                                onImageBitmapReady(attachmentId, bitmap)
                            }
                        }
                        animateGallerySlide(direction = -1, navigate = onNext)
                    }
                }
                fun settlePanOrNavigate() {
                    val bounds = panBounds(scale)
                    val swipeThreshold = viewportWidthPx * 0.5f
                    when {
                        offset.x <= -bounds.x - swipeThreshold && hasNext -> navigateToNext()
                        offset.x >= bounds.x + swipeThreshold && hasPrevious -> navigateToPrevious()
                        scale <= 1.01f -> animateOffsetTo(Offset.Zero)
                        else -> animateOffsetTo(offset.coerceToBounds(bounds.x, bounds.y))
                    }
                }
                val totalSlideX = offset.x + gallerySlideOffset.value
                val slideDistance = viewportWidthPx * scale.coerceAtLeast(1f) + FULL_SCREEN_GALLERY_SLIDE_MARGIN_PX
                val incomingBitmap = when {
                    gallerySlideDirection < 0 -> nextImageBitmap
                    gallerySlideDirection > 0 -> previousImageBitmap
                    else -> null
                }
                val incomingTranslationX = totalSlideX - gallerySlideDirection * slideDistance

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(
                            attachment.id,
                            hasPrevious,
                            hasNext,
                            viewportWidthPx,
                            isGallerySlideActive,
                        ) {
                            if (!isGallerySlideActive) {
                                detectFullScreenImageGestures(
                                    onZoom = ::applyZoom,
                                    onPan = ::applyPan,
                                    onGestureEnd = ::settlePanOrNavigate,
                                )
                            }
                        }
                        .pointerInput(attachment.id) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (!isGallerySlideActive) {
                                        if (scale > 1.01f) {
                                            scale = 1f
                                            offset = Offset.Zero
                                        } else {
                                            scale = 2f
                                            offset = Offset.Zero
                                        }
                                        offset = Offset.Zero
                                    }
                                },
                            )
                        },
                ) {
                    if (incomingBitmap != null && gallerySlideDirection != 0) {
                        Image(
                            bitmap = incomingBitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    translationX = incomingTranslationX,
                                    alpha = 0.96f,
                                ),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = attachment.displayName(),
                        modifier = Modifier
                            .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = totalSlideX,
                            translationY = offset.y,
                        ),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 86.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
        FullScreenMediaHeader(
            media = media,
            onDismiss = onDismiss,
        )
        FullScreenMediaFooter(
            media = media,
            centerContent = {
                if (positionLabel.isNotBlank()) {
                    FullScreenImageControl(
                        enabled = hasPrevious,
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(Res.string.previous_media),
                        onClick = onPrevious,
                    )
                }
                FullScreenImageControl(
                    icon = Icons.Outlined.Remove,
                    contentDescription = stringResource(Res.string.zoom_out),
                    onClick = {
                        scale = (scale - 0.5f).coerceAtLeast(1f)
                        if (scale <= 1.01f) offset = Offset.Zero
                    },
                )
                Text(
                    text = "${(scale * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                FullScreenImageControl(
                    icon = Icons.Outlined.Add,
                    contentDescription = stringResource(Res.string.zoom_in),
                    onClick = { scale = (scale + 0.5f).coerceAtMost(5f) },
                )
                if (positionLabel.isNotBlank()) {
                    Text(
                        text = positionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.84f),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (positionLabel.isNotBlank()) {
                    FullScreenImageControl(
                        enabled = hasNext,
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(Res.string.next_media),
                        onClick = onNext,
                    )
                }
            },
        )
    }
}

@Composable
private fun FullScreenVideoViewer(
    media: FullScreenMedia,
    hasPrevious: Boolean,
    hasNext: Boolean,
    positionLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
) {
    val localUri = media.attachment.localUri ?: return
    Box(
        modifier = fullScreenMediaModifier(onDismiss),
    ) {
        PlatformVideoPlayer(
            localUri = localUri,
            fileName = media.attachment.name,
            mimeType = media.attachment.mimeType,
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(top = 86.dp),
        )
        FullScreenMediaHeader(
            media = media,
            onDismiss = onDismiss,
        )
        FullScreenSideNavigation(
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            onPrevious = onPrevious,
            onNext = onNext,
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun fullScreenMediaModifier(
    onDismiss: () -> Unit,
): Modifier {
    val focusRequester = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    return Modifier
        .fillMaxSize()
        .background(Color.Black)
        .focusRequester(focusRequester)
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                onDismiss()
                true
            } else {
                false
            }
        }
        .focusable()
}

@Composable
private fun BoxScope.FullScreenMediaHeader(
    media: FullScreenMedia,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .statusBarsPadding()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = media.sender,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = media.timestamp,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.14f),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(Res.string.close_media),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.FullScreenMediaFooter(
    media: FullScreenMedia,
    centerContent: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(Color.Black.copy(alpha = 0.74f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (media.description.isNotBlank()) {
            Text(
                text = media.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = centerContent,
        )
    }
}

@Composable
private fun BoxScope.FullScreenSideNavigation(
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    if (hasPrevious) {
        FullScreenImageControl(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(Res.string.previous_media),
            onClick = onPrevious,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp),
        )
    }
    if (hasNext) {
        FullScreenImageControl(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(Res.string.next_media),
            onClick = onNext,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
        )
    }
}

@Composable
private fun FullScreenImageControl(
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = Color.Black.copy(alpha = if (enabled) 0.54f else 0.24f),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = if (enabled) 1f else 0.42f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private suspend fun PointerInputScope.detectFullScreenImageGestures(
    onZoom: (Float) -> Unit,
    onPan: (Offset) -> Unit,
    onGestureEnd: () -> Unit,
) {
    awaitPointerEventScope {
        while (true) {
            var sawPointer = false

            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Scroll) {
                    val scrollY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (scrollY != 0f) {
                        onZoom(if (scrollY < 0f) 1.12f else 0.88f)
                        event.changes.forEach { change -> change.consume() }
                    }
                    continue
                }

                val pressedChanges = event.changes.filter { change -> change.pressed }
                if (pressedChanges.isEmpty()) {
                    if (sawPointer) break
                    continue
                }
                sawPointer = true

                val activeChanges = event.changes.filter { change ->
                    change.pressed && change.previousPressed
                }
                if (pressedChanges.size >= 2 && activeChanges.size >= 2) {
                    val previousCentroid = activeChanges.centroid(usePrevious = true)
                    val currentCentroid = activeChanges.centroid(usePrevious = false)
                    val previousDistance = activeChanges.averageDistanceFrom(previousCentroid, usePrevious = true)
                    val currentDistance = activeChanges.averageDistanceFrom(currentCentroid, usePrevious = false)
                    val zoomChange = if (previousDistance > 0f) {
                        currentDistance / previousDistance
                    } else {
                        1f
                    }
                    val panChange = currentCentroid - previousCentroid

                    if (abs(zoomChange - 1f) > 0.001f || panChange.distance() > 0.1f) {
                        onZoom(zoomChange)
                        onPan(panChange)
                    }
                    event.changes.forEach { change -> change.consume() }
                } else {
                    val change = pressedChanges.firstOrNull { item -> item.previousPressed } ?: continue
                    val delta = change.positionChange()
                    if (delta == Offset.Zero) continue

                    onPan(delta)
                    change.consume()
                }
            }

            if (sawPointer) onGestureEnd()
        }
    }
}

private fun List<PointerInputChange>.centroid(usePrevious: Boolean): Offset {
    if (isEmpty()) return Offset.Zero
    var x = 0f
    var y = 0f
    forEach { change ->
        val position = change.positionFor(usePrevious)
        x += position.x
        y += position.y
    }
    return Offset(x / size, y / size)
}

private fun List<PointerInputChange>.averageDistanceFrom(
    center: Offset,
    usePrevious: Boolean,
): Float {
    if (isEmpty()) return 0f
    var distance = 0f
    forEach { change ->
        distance += (change.positionFor(usePrevious) - center).distance()
    }
    return distance / size
}

private fun PointerInputChange.positionFor(usePrevious: Boolean): Offset {
    return if (usePrevious) previousPosition else position
}

private fun Offset.distance(): Float = sqrt(x * x + y * y)

@Composable
private fun VideoAttachmentPreview(
    attachment: ChatAttachment,
    isMine: Boolean,
    onOpenVideo: (ChatAttachment) -> Unit,
    onRequestAttachmentDownload: (String) -> Unit,
) {
    val thumbnailBitmap = rememberVideoThumbnailBitmap(attachment)
    val canRequestDownload = attachment.localUri == null && attachment.loadState.canRequestDownload()

    BoxWithConstraints {
        val previewWidth = (maxWidth.value * 0.58f).dp.coerceAtMost(240.dp)
        Surface(
            modifier = Modifier
                .width(previewWidth)
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = attachment.localUri != null || canRequestDownload) {
                    if (attachment.localUri != null) {
                        onOpenVideo(attachment)
                    } else {
                        onRequestAttachmentDownload(attachment.id)
                    }
                },
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1B1A17),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap,
                        contentDescription = attachment.displayName(),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF2A2924)),
                    ) {
                        MediaLoadingPlaceholderContent(attachment.loadState)
                    }
                }
                if (attachment.localUri != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(54.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.58f),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PlayArrow,
                                contentDescription = stringResource(Res.string.play_video),
                                tint = Color.White,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                    color = Color.Black.copy(alpha = if (isMine) 0.46f else 0.54f),
                ) {
                    Text(
                        text = attachment.displayName(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceAttachmentPreview(
    attachment: ChatAttachment,
    isMine: Boolean,
    isPlaying: Boolean,
    progress: VoicePlaybackProgress?,
    onTogglePlayback: (ChatAttachment) -> Unit,
    onRequestAttachmentDownload: (String) -> Unit,
    onSeekPlayback: (ChatAttachment, Long) -> Unit,
) {
    val durationMillis = (progress?.durationMillis ?: attachment.durationMillis ?: 0L).coerceAtLeast(0L)
    val positionMillis = if (isPlaying) {
        (progress?.positionMillis ?: 0L).coerceIn(0L, durationMillis.takeIf { it > 0L } ?: Long.MAX_VALUE)
    } else {
        0L
    }
    val progressFraction = if (durationMillis > 0L) {
        positionMillis.toFloat() / durationMillis.toFloat()
    } else {
        0f
    }.coerceIn(0f, 1f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = if (isMine) {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable(enabled = attachment.localUri != null || attachment.loadState.canRequestDownload()) {
                        if (attachment.localUri != null) {
                            onTogglePlayback(attachment)
                        } else {
                            onRequestAttachmentDownload(attachment.id)
                        }
                    },
                shape = CircleShape,
                color = if (isMine) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                },
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (attachment.localUri == null) {
                            Icons.Outlined.FileDownload
                        } else if (isPlaying) {
                            Icons.Outlined.Stop
                        } else {
                            Icons.Outlined.PlayArrow
                        },
                        contentDescription = if (isPlaying) {
                            stringResource(Res.string.stop_voice_message)
                        } else {
                            stringResource(Res.string.play_voice_message)
                        },
                        tint = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = if (attachment.localUri == null) {
                        attachment.loadState.statusText()
                    } else if (isPlaying) {
                        stringResource(Res.string.playing_voice)
                    } else {
                        stringResource(Res.string.voice_message)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                VoiceWaveform(
                    progressFraction = progressFraction,
                    isMine = isMine,
                    durationMillis = durationMillis,
                    onSeek = { positionMillis -> onSeekPlayback(attachment, positionMillis) },
                )
            }
            Text(
                text = formatVoiceProgress(positionMillis, durationMillis, isPlaying),
                style = MaterialTheme.typography.labelMedium,
                color = if (isMine) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.84f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AudioAttachmentPreview(
    attachment: ChatAttachment,
    isMine: Boolean,
    isPlaying: Boolean,
    progress: VoicePlaybackProgress?,
    onTogglePlayback: (ChatAttachment) -> Unit,
    onRequestAttachmentDownload: (String) -> Unit,
    onSeekPlayback: (ChatAttachment, Long) -> Unit,
) {
    val durationMillis = (progress?.durationMillis ?: attachment.durationMillis ?: 0L).coerceAtLeast(0L)
    val positionMillis = if (isPlaying) {
        (progress?.positionMillis ?: 0L).coerceIn(0L, durationMillis.takeIf { it > 0L } ?: Long.MAX_VALUE)
    } else {
        0L
    }
    val progressFraction = if (durationMillis > 0L) {
        positionMillis.toFloat() / durationMillis.toFloat()
    } else {
        0f
    }.coerceIn(0f, 1f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = if (isMine) {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable(enabled = attachment.localUri != null || attachment.loadState.canRequestDownload()) {
                        if (attachment.localUri != null) {
                            onTogglePlayback(attachment)
                        } else {
                            onRequestAttachmentDownload(attachment.id)
                        }
                    },
                shape = CircleShape,
                color = if (isMine) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                },
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (attachment.localUri == null) {
                            Icons.Outlined.FileDownload
                        } else if (isPlaying) {
                            Icons.Outlined.Stop
                        } else {
                            Icons.Outlined.PlayArrow
                        },
                        contentDescription = if (isPlaying) {
                            stringResource(Res.string.stop_audio)
                        } else {
                            stringResource(Res.string.play_audio)
                        },
                        tint = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = attachment.displayNameWithLoadState(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AudioProgressBar(
                    progressFraction = progressFraction,
                    isMine = isMine,
                    durationMillis = durationMillis,
                    onSeek = { positionMillis -> onSeekPlayback(attachment, positionMillis) },
                )
            }
            Text(
                text = formatVoiceProgress(positionMillis, durationMillis, isPlaying),
                style = MaterialTheme.typography.labelMedium,
                color = if (isMine) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.84f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AudioProgressBar(
    progressFraction: Float,
    isMine: Boolean,
    durationMillis: Long,
    onSeek: (Long) -> Unit,
) {
    val activeColor = if (isMine) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(activeColor.copy(alpha = 0.24f))
            .seekablePlaybackProgress(durationMillis, onSeek),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                .height(5.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(activeColor.copy(alpha = 0.95f)),
        )
    }
}

@Composable
private fun VoiceWaveform(
    progressFraction: Float,
    isMine: Boolean,
    durationMillis: Long,
    onSeek: (Long) -> Unit,
) {
    val heights = remember {
        listOf(12, 20, 15, 28, 18, 10, 24, 31, 14, 22, 30, 17, 11, 25, 20, 13, 29, 16, 21, 12)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .seekablePlaybackProgress(durationMillis, onSeek),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        heights.forEachIndexed { index, height ->
            val barProgress = (index + 1).toFloat() / heights.size.toFloat()
            val isPlayed = progressFraction >= barProgress
            val color = if (isMine) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.primary
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = if (isPlayed) 0.95f else 0.28f)),
            )
        }
    }
}

private fun Modifier.seekablePlaybackProgress(
    durationMillis: Long,
    onSeek: (Long) -> Unit,
): Modifier {
    if (durationMillis <= 0L) return this
    fun Float.toSeekMillis(widthPx: Int): Long {
        if (widthPx <= 0) return 0L
        return ((coerceIn(0f, widthPx.toFloat()) / widthPx.toFloat()) * durationMillis)
            .roundToLong()
            .coerceIn(0L, durationMillis)
    }
    return this
        .pointerInput(durationMillis, onSeek) {
            detectTapGestures { offset ->
                onSeek(offset.x.toSeekMillis(size.width))
            }
        }
        .pointerInput(durationMillis, onSeek) {
            detectDragGestures { change, _ ->
                change.consume()
                onSeek(change.position.x.toSeekMillis(size.width))
            }
        }
}

@Composable
private fun MediaAttachmentPreview(
    attachment: ChatAttachment,
    isMine: Boolean,
    title: String,
    actionLabel: String,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = attachment.localUri != null) {
                openLocalAttachment(attachment)
            },
        shape = RoundedCornerShape(14.dp),
        color = if (isMine) {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        },
    ) {
        AttachmentTextPreview(
            attachment = attachment,
            isMine = isMine,
            title = title,
            actionLabel = actionLabel,
        )
    }
}

@Composable
private fun AttachmentTextPreview(
    attachment: ChatAttachment,
    isMine: Boolean,
    title: String,
    actionLabel: String,
) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = attachment.displayName(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isMine) {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            },
        ) {
            Text(
                text = actionLabel,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MediaLoadingPlaceholder(
    attachment: ChatAttachment,
    isMine: Boolean,
    title: String,
    onRequestDownload: () -> Unit,
) {
    val canRequestDownload = attachment.localUri == null && attachment.loadState.canRequestDownload()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = canRequestDownload, onClick = onRequestDownload),
        shape = RoundedCornerShape(14.dp),
        color = if (isMine) {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AttachmentLoadStateIcon(attachment.loadState, isMine)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (attachment.localUri == null) attachment.loadState.statusText() else title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = attachment.displayNameWithLoadState(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isMine) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MediaLoadingPlaceholderContent(
    loadState: ChatAttachmentLoadState = ChatAttachmentLoadState.NotStarted,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2A2924)),
        contentAlignment = Alignment.Center,
    ) {
        if (loadState.canRequestDownload()) {
            Icon(
                imageVector = Icons.Outlined.FileDownload,
                contentDescription = stringResource(Res.string.download_attachment),
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun AttachmentLoadStateIcon(
    loadState: ChatAttachmentLoadState,
    isMine: Boolean,
) {
    val tint = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    if (loadState.canRequestDownload()) {
        Icon(
            imageVector = Icons.Outlined.FileDownload,
            contentDescription = stringResource(Res.string.download_attachment),
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    } else {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = tint,
        )
    }
}

@Composable
internal fun InvitationBanner(
    isProcessing: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Surface(
        modifier = Modifier.imePadding(),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                text = stringResource(Res.string.invitation_banner),
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
                        .clip(CircleShape)
                        .clickable(enabled = !isProcessing, onClick = onDecline),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        text = stringResource(Res.string.decline),
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
                        .clip(CircleShape)
                        .clickable(enabled = !isProcessing, onClick = onAccept),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = if (isProcessing) {
                            stringResource(Res.string.processing)
                        } else {
                            stringResource(Res.string.accept)
                        },
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
internal fun MessageComposer(
    draft: String,
    pendingAttachments: List<PreparedChatAttachment>,
    attachmentUploadProgress: AttachmentUploadProgress?,
    isPreparingAttachment: Boolean,
    canSend: Boolean,
    onDraftChanged: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachFileClick: () -> Unit,
    onPickImageClick: () -> Unit,
    onTakePhotoClick: () -> Unit,
    onRecordVoiceClick: () -> Unit,
    onPasteClipboardAttachments: () -> Boolean,
    onRemoveAttachment: (String) -> Unit,
    isRecordingVoice: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    var suppressKeyboard by remember { mutableStateOf(hasSoftwareKeyboard) }
    var isAttachmentMenuVisible by remember { mutableStateOf(false) }
    val shouldRecordVoice = draft.isBlank() && pendingAttachments.isEmpty()
    val shouldUseVoiceAction = isRecordingVoice || shouldRecordVoice
    val isPrimaryEnabled = if (shouldUseVoiceAction) {
        !isPreparingAttachment
    } else {
        canSend && !isPreparingAttachment
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        modifier = Modifier.imePadding(),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
            if (isPreparingAttachment) {
                AttachmentUploadProgressBanner(attachmentUploadProgress)
            }
            if (isRecordingVoice) {
                RecordingVoiceBanner()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box {
                    AttachmentActionButton(
                        icon = Icons.Outlined.AttachFile,
                        contentDescription = stringResource(Res.string.attach),
                        enabled = !isPreparingAttachment,
                        onClick = { isAttachmentMenuVisible = true },
                    )
                    DropdownMenu(
                        expanded = isAttachmentMenuVisible,
                        onDismissRequest = { isAttachmentMenuVisible = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.file)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.AttachFile,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                isAttachmentMenuVisible = false
                                onAttachFileClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.photo_video)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoLibrary,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                isAttachmentMenuVisible = false
                                onPickImageClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.camera)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoCamera,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                isAttachmentMenuVisible = false
                                onTakePhotoClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.paste)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.ContentPaste,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                isAttachmentMenuVisible = false
                                onPasteClipboardAttachments()
                            },
                        )
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                            .heightIn(min = 48.dp, max = 180.dp)
                            .verticalScroll(scrollState)
                            .padding(horizontal = 18.dp, vertical = 13.dp),
                        decorationBox = { innerTextField ->
                            if (draft.isBlank()) {
                                Text(
                                    text = stringResource(Res.string.write_message),
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
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(enabled = isPrimaryEnabled, onClick = {
                            if (shouldUseVoiceAction) {
                                onRecordVoiceClick()
                            } else {
                                onSendClick()
                            }
                            focusRequester.requestFocus()
                        }),
                    shape = CircleShape,
                    color = if (isRecordingVoice) {
                        MaterialTheme.colorScheme.error
                    } else if (isPrimaryEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    },
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                                imageVector = when {
                                    isRecordingVoice -> Icons.Outlined.Stop
                                    shouldRecordVoice -> Icons.Outlined.Mic
                                    else -> Icons.AutoMirrored.Outlined.Send
                                },
                            contentDescription = when {
                                isRecordingVoice -> stringResource(Res.string.stop_recording)
                                shouldRecordVoice -> stringResource(Res.string.record_voice)
                                else -> stringResource(Res.string.send_message)
                            },
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentUploadProgressBanner(
    progress: AttachmentUploadProgress?,
) {
    val totalBytes = progress?.totalBytes?.takeIf { it > 0L } ?: 0L
    val bytesSent = progress?.bytesSent?.coerceAtLeast(0L) ?: 0L
    val fraction = if (totalBytes > 0L) {
        (bytesSent.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val percent = (fraction * 100f).toInt().coerceIn(0, 100)

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (progress == null) {
                        stringResource(Res.string.preparing_attachment)
                    } else {
                        stringResource(Res.string.uploading_file, progress.fileName)
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (totalBytes > 0L) "$percent%" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceAtLeast(if (totalBytes > 0L) 0.02f else 0.18f))
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun RecordingVoiceBanner() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
            )
            Text(
                text = stringResource(Res.string.recording_voice_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                        contentDescription = stringResource(Res.string.remove_attachment),
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

@Composable
private fun ChatAttachmentKind.label(): String {
    return when (this) {
        ChatAttachmentKind.File -> stringResource(Res.string.attachment_kind_file_short)
        ChatAttachmentKind.Image -> stringResource(Res.string.attachment_kind_image_short)
        ChatAttachmentKind.Video -> stringResource(Res.string.attachment_kind_video_short)
        ChatAttachmentKind.Audio -> stringResource(Res.string.attachment_kind_audio_short)
        ChatAttachmentKind.Voice -> stringResource(Res.string.attachment_kind_voice_short)
    }
}

@Composable
private fun rememberAttachmentImagePreviewBitmap(
    attachment: ChatAttachment,
): ImageBitmap? {
    val contentBytes = attachment.contentBytes
    val localUri = attachment.localUri
    val cacheKey = attachment.previewCacheKey(prefix = "image", maxSidePx = CHAT_IMAGE_PREVIEW_MAX_SIDE_PX)
    val imageBitmap by produceState<KeyedImageBitmap?>(
        initialValue = MediaPreviewMemoryCache.get(cacheKey)?.let { bitmap ->
            KeyedImageBitmap(cacheKey, bitmap)
        },
        key1 = attachment.id,
        key2 = localUri,
        key3 = contentBytes,
    ) {
        MediaPreviewMemoryCache.get(cacheKey)?.let { cached ->
            value = KeyedImageBitmap(cacheKey, cached)
            return@produceState
        }
        value = null
        value = MediaPreviewDecodeLimiter.withPermit {
            withContext(Dispatchers.Default) {
                loadImagePreview(
                    localUri = localUri,
                    contentBytes = contentBytes,
                    maxSidePx = CHAT_IMAGE_PREVIEW_MAX_SIDE_PX,
                )?.decodeToImageBitmapOrNull()
            }
        }?.also { bitmap -> MediaPreviewMemoryCache.put(cacheKey, bitmap) }
            ?.let { bitmap -> KeyedImageBitmap(cacheKey, bitmap) }
    }
    return imageBitmap?.takeIf { keyed -> keyed.key == cacheKey }?.bitmap
}

@Composable
private fun rememberAttachmentImageBitmap(
    attachment: ChatAttachment,
    imageBitmapSeed: ImageBitmap?,
    onImageBitmapReady: (String, ImageBitmap) -> Unit,
): ImageBitmap? {
    val contentBytes = attachment.contentBytes
    val localUri = attachment.localUri
    val bitmapKey = attachment.previewCacheKey(
        prefix = "full-image",
        maxSidePx = CHAT_FULL_SCREEN_IMAGE_MAX_SIDE_PX,
    )
    val previewKey = attachment.previewCacheKey(
        prefix = "image",
        maxSidePx = CHAT_IMAGE_PREVIEW_MAX_SIDE_PX,
    )
    val initialBitmap = imageBitmapSeed?.let { bitmap ->
        KeyedImageBitmap(bitmapKey, bitmap)
    } ?: FullScreenImageMemoryCache.get(bitmapKey)?.let { bitmap ->
        KeyedImageBitmap(bitmapKey, bitmap)
    } ?: FullScreenAttachmentImageMemoryCache.get(attachment.id)?.let { bitmap ->
        KeyedImageBitmap(bitmapKey, bitmap)
    } ?: MediaPreviewMemoryCache.get(previewKey)?.let { bitmap ->
        KeyedImageBitmap(previewKey, bitmap)
    }
    val imageBitmap by produceState<KeyedImageBitmap?>(
        initialBitmap,
        attachment.id,
        localUri,
        contentBytes,
        imageBitmapSeed,
    ) {
        if (value?.key != bitmapKey && value?.key != previewKey) {
            value = initialBitmap
        }
        imageBitmapSeed?.let { seeded ->
            FullScreenAttachmentImageMemoryCache.put(attachment.id, seeded)
            FullScreenImageMemoryCache.put(bitmapKey, seeded)
            value = KeyedImageBitmap(bitmapKey, seeded)
            return@produceState
        }
        FullScreenImageMemoryCache.get(bitmapKey)?.let { cached ->
            FullScreenAttachmentImageMemoryCache.put(attachment.id, cached)
            value = KeyedImageBitmap(bitmapKey, cached)
            onImageBitmapReady(attachment.id, cached)
            return@produceState
        }
        FullScreenAttachmentImageMemoryCache.get(attachment.id)?.let { cached ->
            FullScreenImageMemoryCache.put(bitmapKey, cached)
            value = KeyedImageBitmap(bitmapKey, cached)
            onImageBitmapReady(attachment.id, cached)
            return@produceState
        }
        MediaPreviewMemoryCache.get(previewKey)?.let { preview ->
            value = KeyedImageBitmap(previewKey, preview)
        } ?: run {
            value = null
        }
        if (contentBytes == null && localUri == null) return@produceState
        val decodedFullBitmap = FullScreenImageDecodeLimiter.withPermit {
            withContext(Dispatchers.Default) {
                loadImagePreview(
                    localUri = localUri,
                    contentBytes = contentBytes,
                    maxSidePx = CHAT_FULL_SCREEN_IMAGE_MAX_SIDE_PX,
                )?.decodeToImageBitmapOrNull()
            }
        }
        if (decodedFullBitmap != null) {
            FullScreenAttachmentImageMemoryCache.put(attachment.id, decodedFullBitmap)
            FullScreenImageMemoryCache.put(bitmapKey, decodedFullBitmap)
            value = KeyedImageBitmap(bitmapKey, decodedFullBitmap)
            onImageBitmapReady(attachment.id, decodedFullBitmap)
        }
    }
    return imageBitmap
        ?.takeIf { keyed -> keyed.key == bitmapKey || keyed.key == previewKey }
        ?.bitmap
        ?: initialBitmap?.bitmap
}

@Composable
private fun rememberOptionalAttachmentImageBitmap(
    attachment: ChatAttachment?,
    imageBitmapSeed: ImageBitmap?,
    onImageBitmapReady: (String, ImageBitmap) -> Unit,
): ImageBitmap? {
    val contentBytes = attachment?.contentBytes
    val localUri = attachment?.localUri
    val bitmapKey = attachment?.previewCacheKey(
        prefix = "full-image",
        maxSidePx = CHAT_FULL_SCREEN_IMAGE_MAX_SIDE_PX,
    ).orEmpty()
    val previewKey = attachment?.previewCacheKey(
        prefix = "image",
        maxSidePx = CHAT_IMAGE_PREVIEW_MAX_SIDE_PX,
    ).orEmpty()
    val initialBitmap = imageBitmapSeed?.takeIf { attachment != null }?.let { bitmap ->
        KeyedImageBitmap(bitmapKey, bitmap)
    } ?: FullScreenImageMemoryCache.get(bitmapKey)?.let { bitmap ->
        KeyedImageBitmap(bitmapKey, bitmap)
    } ?: attachment?.id?.let(FullScreenAttachmentImageMemoryCache::get)?.let { bitmap ->
        KeyedImageBitmap(bitmapKey, bitmap)
    } ?: MediaPreviewMemoryCache.get(previewKey)?.let { bitmap ->
        KeyedImageBitmap(previewKey, bitmap)
    }
    val imageBitmap by produceState<KeyedImageBitmap?>(
        initialBitmap,
        attachment?.id,
        localUri,
        contentBytes,
        imageBitmapSeed,
    ) {
        if (attachment == null) {
            value = null
            return@produceState
        }
        if (value?.key != bitmapKey && value?.key != previewKey) {
            value = initialBitmap
        }
        imageBitmapSeed?.let { seeded ->
            FullScreenAttachmentImageMemoryCache.put(attachment.id, seeded)
            FullScreenImageMemoryCache.put(bitmapKey, seeded)
            value = KeyedImageBitmap(bitmapKey, seeded)
            return@produceState
        }
        FullScreenImageMemoryCache.get(bitmapKey)?.let { cached ->
            FullScreenAttachmentImageMemoryCache.put(attachment.id, cached)
            value = KeyedImageBitmap(bitmapKey, cached)
            onImageBitmapReady(attachment.id, cached)
            return@produceState
        }
        FullScreenAttachmentImageMemoryCache.get(attachment.id)?.let { cached ->
            FullScreenImageMemoryCache.put(bitmapKey, cached)
            value = KeyedImageBitmap(bitmapKey, cached)
            onImageBitmapReady(attachment.id, cached)
            return@produceState
        }
        MediaPreviewMemoryCache.get(previewKey)?.let { preview ->
            value = KeyedImageBitmap(previewKey, preview)
        } ?: run {
            value = null
        }
        if (contentBytes == null && localUri == null) return@produceState
        val decodedFullBitmap = FullScreenImageDecodeLimiter.withPermit {
            withContext(Dispatchers.Default) {
                loadImagePreview(
                    localUri = localUri,
                    contentBytes = contentBytes,
                    maxSidePx = CHAT_FULL_SCREEN_IMAGE_MAX_SIDE_PX,
                )?.decodeToImageBitmapOrNull()
            }
        }
        if (decodedFullBitmap != null) {
            FullScreenAttachmentImageMemoryCache.put(attachment.id, decodedFullBitmap)
            FullScreenImageMemoryCache.put(bitmapKey, decodedFullBitmap)
            value = KeyedImageBitmap(bitmapKey, decodedFullBitmap)
            onImageBitmapReady(attachment.id, decodedFullBitmap)
        }
    }
    return imageBitmap
        ?.takeIf { keyed -> keyed.key == bitmapKey || keyed.key == previewKey }
        ?.bitmap
        ?: initialBitmap?.bitmap
}

@Composable
private fun rememberVideoThumbnailBitmap(
    attachment: ChatAttachment,
): ImageBitmap? {
    val localUri = attachment.localUri
    val cacheKey = attachment.previewCacheKey(prefix = "video", maxSidePx = CHAT_VIDEO_THUMBNAIL_MAX_SIDE_PX)
    val imageBitmap by produceState<KeyedImageBitmap?>(
        initialValue = MediaPreviewMemoryCache.get(cacheKey)?.let { bitmap ->
            KeyedImageBitmap(cacheKey, bitmap)
        },
        key1 = attachment.id,
        key2 = localUri,
    ) {
        MediaPreviewMemoryCache.get(cacheKey)?.let { cached ->
            value = KeyedImageBitmap(cacheKey, cached)
            return@produceState
        }
        value = null
        if (attachment.kind != ChatAttachmentKind.Video || localUri == null) return@produceState
        value = MediaPreviewDecodeLimiter.withPermit {
            withContext(Dispatchers.Default) {
                loadVideoThumbnail(localUri, CHAT_VIDEO_THUMBNAIL_MAX_SIDE_PX)?.decodeToImageBitmapOrNull()
            }
        }?.also { bitmap -> MediaPreviewMemoryCache.put(cacheKey, bitmap) }
            ?.let { bitmap -> KeyedImageBitmap(cacheKey, bitmap) }
    }
    return imageBitmap?.takeIf { keyed -> keyed.key == cacheKey }?.bitmap
}

private fun ByteArray.decodeToImageBitmapOrNull(): ImageBitmap? {
    return runCatching { decodeToImageBitmap() }.getOrNull()
}

private fun ChatAttachment.previewCacheKey(prefix: String, maxSidePx: Int): String {
    return listOf(
        prefix,
        id,
        localUri.orEmpty(),
        contentBytes?.size?.toString().orEmpty(),
        maxSidePx.toString(),
    ).joinToString(separator = "|")
}

private data class KeyedImageBitmap(
    val key: String,
    val bitmap: ImageBitmap,
)

private object FullScreenImageMemoryCache {
    private const val MAX_ITEMS = 6
    private val cache = LinkedHashMap<String, ImageBitmap>()

    fun get(key: String): ImageBitmap? = cache[key]

    fun put(key: String, bitmap: ImageBitmap) {
        if (key.isBlank()) return
        cache.remove(key)
        cache[key] = bitmap
        while (cache.size > MAX_ITEMS) {
            val oldestKey = cache.keys.firstOrNull() ?: break
            cache.remove(oldestKey)
        }
    }
}

private object FullScreenAttachmentImageMemoryCache {
    private const val MAX_ITEMS = 12
    private val cache = LinkedHashMap<String, ImageBitmap>()

    fun get(attachmentId: String): ImageBitmap? = cache[attachmentId]

    fun put(attachmentId: String, bitmap: ImageBitmap) {
        if (attachmentId.isBlank()) return
        cache.remove(attachmentId)
        cache[attachmentId] = bitmap
        while (cache.size > MAX_ITEMS) {
            val oldestKey = cache.keys.firstOrNull() ?: break
            cache.remove(oldestKey)
        }
    }
}

private object MediaPreviewMemoryCache {
    private const val MAX_ITEMS = 160
    private val cache = LinkedHashMap<String, ImageBitmap>()

    fun get(key: String): ImageBitmap? = cache[key]

    fun put(key: String, bitmap: ImageBitmap) {
        if (key.isBlank()) return
        cache.remove(key)
        cache[key] = bitmap
        while (cache.size > MAX_ITEMS) {
            val oldestKey = cache.keys.firstOrNull() ?: break
            cache.remove(oldestKey)
        }
    }
}

private object MediaPreviewDecodeLimiter {
    private val semaphore = Semaphore(CHAT_MEDIA_PREVIEW_DECODE_PARALLELISM)

    suspend fun <T> withPermit(block: suspend () -> T): T {
        return semaphore.withPermit { block() }
    }
}

private object FullScreenImageDecodeLimiter {
    private val semaphore = Semaphore(CHAT_FULL_SCREEN_IMAGE_DECODE_PARALLELISM)

    suspend fun <T> withPermit(block: suspend () -> T): T {
        return semaphore.withPermit { block() }
    }
}

internal fun ChatAttachment.isGridMedia(): Boolean {
    return kind == ChatAttachmentKind.Image || kind == ChatAttachmentKind.Video
}

private fun Offset.coerceToBounds(maxX: Float, maxY: Float): Offset {
    return Offset(
        x = x.coerceIn(-maxX.coerceAtLeast(0f), maxX.coerceAtLeast(0f)),
        y = y.coerceIn(-maxY.coerceAtLeast(0f), maxY.coerceAtLeast(0f)),
    )
}

private const val MAX_MEDIA_GRID_ITEMS = 10
private const val FULL_SCREEN_GALLERY_BITMAP_CACHE_ITEMS = 8
private const val FULL_SCREEN_GALLERY_SLIDE_DURATION_MS = 220
private const val FULL_SCREEN_IMAGE_SETTLE_DURATION_MS = 180
private const val FULL_SCREEN_GALLERY_SLIDE_MARGIN_PX = 48f
private const val CHAT_IMAGE_PREVIEW_MAX_SIDE_PX = 720
private const val CHAT_FULL_SCREEN_IMAGE_MAX_SIDE_PX = 2560
private const val CHAT_VIDEO_THUMBNAIL_MAX_SIDE_PX = 480
private const val CHAT_MEDIA_PREVIEW_DECODE_PARALLELISM = 1
private const val CHAT_FULL_SCREEN_IMAGE_DECODE_PARALLELISM = 1

internal data class VoicePlaybackProgress(
    val positionMillis: Long,
    val durationMillis: Long,
)

internal data class FullScreenMedia(
    val attachment: ChatAttachment,
    val sender: String,
    val timestamp: String,
    val description: String,
)

internal data class FullScreenMediaGallery(
    val items: List<FullScreenMedia>,
    val selectedIndex: Int,
) {
    val selectedMedia: FullScreenMedia?
        get() = items.getOrNull(selectedIndex)
}

internal fun ChatMessage.User.toFullScreenMediaGallery(
    attachment: ChatAttachment,
    currentUserDisplayName: String,
): FullScreenMediaGallery {
    val mediaAttachments = attachments.filter { item -> item.isGridMedia() }
    val selectedIndex = mediaAttachments.indexOfFirst { item -> item.id == attachment.id }
        .takeIf { index -> index >= 0 }
        ?: 0
    return FullScreenMediaGallery(
        items = mediaAttachments.map { mediaAttachment ->
            FullScreenMedia(
                attachment = mediaAttachment,
                sender = if (isMine) currentUserDisplayName else sender,
                timestamp = timestamp,
                description = body.ifBlank { mediaAttachment.name },
            )
        },
        selectedIndex = selectedIndex,
    )
}

internal fun List<ChatMessage>.nextVoiceFromSameSender(currentAttachmentId: String): ChatAttachment? {
    val currentMessageIndex = indexOfFirst { message ->
        message is ChatMessage.User &&
            message.attachments.any { attachment -> attachment.id == currentAttachmentId }
    }
    if (currentMessageIndex < 0) return null

    val currentMessage = this[currentMessageIndex] as? ChatMessage.User ?: return null
    val nextMessage = getOrNull(currentMessageIndex + 1) as? ChatMessage.User ?: return null
    if (nextMessage.isMine != currentMessage.isMine || nextMessage.sender != currentMessage.sender) return null

    return nextMessage.attachments.firstOrNull { attachment ->
        attachment.kind == ChatAttachmentKind.Voice && attachment.localUri != null
    }
}

private fun formatVoiceProgress(
    positionMillis: Long,
    durationMillis: Long,
    isPlaying: Boolean,
): String {
    return if (isPlaying && durationMillis > 0L) {
        "${positionMillis.toVoiceTimestamp()} / ${durationMillis.toVoiceTimestamp()}"
    } else {
        durationMillis.toVoiceTimestamp()
    }
}

private fun Long.toVoiceTimestamp(): String {
    val totalSeconds = (this / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun ChatAttachment.displayName(): String {
    return when {
        durationMillis != null -> "$name (${durationMillis / 1000}${stringResource(Res.string.duration_seconds_suffix)})"
        sizeBytes != null -> "$name (${sizeBytes.toReadableBytes()})"
        else -> name
    }
}

@Composable
private fun ChatAttachment.displayNameWithLoadState(): String {
    return if (localUri == null && loadState != ChatAttachmentLoadState.Ready) {
        "${displayName()} - ${loadState.detailText()}"
    } else {
        displayName()
    }
}

private fun ChatAttachmentLoadState.canRequestDownload(): Boolean {
    return this == ChatAttachmentLoadState.NotStarted ||
        this == ChatAttachmentLoadState.WaitingForTap ||
        this == ChatAttachmentLoadState.Failed
}

@Composable
private fun ChatAttachmentLoadState.shortLabel(): String {
    return when (this) {
        ChatAttachmentLoadState.NotStarted,
        ChatAttachmentLoadState.WaitingForTap -> stringResource(Res.string.attachment_load_short_download)
        ChatAttachmentLoadState.CheckingCache -> stringResource(Res.string.attachment_load_short_cache)
        ChatAttachmentLoadState.Downloading -> stringResource(Res.string.attachment_load_short_loading)
        ChatAttachmentLoadState.Downloaded -> stringResource(Res.string.attachment_load_short_done)
        ChatAttachmentLoadState.Decrypting -> stringResource(Res.string.attachment_load_short_decrypting)
        ChatAttachmentLoadState.Ready -> stringResource(Res.string.attachment_load_short_file)
        ChatAttachmentLoadState.Failed -> stringResource(Res.string.attachment_load_short_error)
    }
}

@Composable
private fun ChatAttachmentLoadState.statusText(): String {
    return when (this) {
        ChatAttachmentLoadState.NotStarted,
        ChatAttachmentLoadState.WaitingForTap -> stringResource(Res.string.attachment_status_tap_to_download)
        ChatAttachmentLoadState.CheckingCache -> stringResource(Res.string.attachment_status_checking_cache)
        ChatAttachmentLoadState.Downloading -> stringResource(Res.string.attachment_status_downloading)
        ChatAttachmentLoadState.Downloaded -> stringResource(Res.string.attachment_status_downloaded)
        ChatAttachmentLoadState.Decrypting -> stringResource(Res.string.attachment_status_downloaded_decrypting)
        ChatAttachmentLoadState.Ready -> stringResource(Res.string.attachment_status_ready)
        ChatAttachmentLoadState.Failed -> stringResource(Res.string.attachment_status_download_failed)
    }
}

@Composable
private fun ChatAttachmentLoadState.detailText(): String {
    return when (this) {
        ChatAttachmentLoadState.NotStarted,
        ChatAttachmentLoadState.WaitingForTap -> stringResource(Res.string.attachment_detail_waiting_for_tap)
        ChatAttachmentLoadState.CheckingCache -> stringResource(Res.string.attachment_detail_checking_cache)
        ChatAttachmentLoadState.Downloading -> stringResource(Res.string.attachment_detail_downloading)
        ChatAttachmentLoadState.Downloaded -> stringResource(Res.string.attachment_detail_downloaded)
        ChatAttachmentLoadState.Decrypting -> stringResource(Res.string.attachment_detail_decrypting)
        ChatAttachmentLoadState.Ready -> stringResource(Res.string.attachment_detail_ready)
        ChatAttachmentLoadState.Failed -> stringResource(Res.string.attachment_detail_error)
    }
}

@Composable
private fun Long.toReadableBytes(): String {
    if (this < 1024L) return "$this ${stringResource(Res.string.bytes_unit_b)}"
    val kib = this / 1024.0
    if (kib < 1024.0) return "${kib.formatOneDecimal()} ${stringResource(Res.string.bytes_unit_kb)}"
    return "${(kib / 1024.0).formatOneDecimal()} ${stringResource(Res.string.bytes_unit_mb)}"
}

private fun Double.formatOneDecimal(): String {
    val scaled = (this * 10).toInt()
    return "${scaled / 10}.${scaled % 10}"
}

@Composable
private fun DeliveryStatus.label(): String {
    return when (this) {
        DeliveryStatus.Sending -> stringResource(Res.string.delivery_sending)
        DeliveryStatus.Sent -> stringResource(Res.string.delivery_sent)
        DeliveryStatus.Failed -> stringResource(Res.string.delivery_failed)
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
                    deliveryStatus = DeliveryStatus.Sent,
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
        onTakePhotoClick = {},
        onRecordVoiceClick = {},
        onPasteClipboardAttachments = { false },
        onAttachmentsDropped = {},
        onRemoveAttachment = {},
        onAttachmentDownloadClick = {},
        onLoadMoreMessages = {},
        onInviteUserClick = {},
    )
}
