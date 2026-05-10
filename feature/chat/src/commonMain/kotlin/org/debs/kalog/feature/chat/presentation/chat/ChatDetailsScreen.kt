package org.debs.kalog.feature.chat.presentation.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.debs.kalog.feature.chat.presentation.platform.hasSoftwareKeyboard
import org.debs.kalog.feature.chat.presentation.platform.loadImagePreview
import org.debs.kalog.feature.chat.presentation.platform.loadVideoThumbnail
import org.debs.kalog.feature.chat.presentation.platform.openLocalAttachment
import org.debs.kalog.feature.chat.presentation.platform.PlatformVideoPlayer
import org.debs.kalog.feature.chat.presentation.platform.playLocalAudio
import org.debs.kalog.feature.chat.presentation.platform.readDroppedAttachments
import org.debs.kalog.feature.chat.presentation.platform.seekLocalAudio
import org.debs.kalog.feature.chat.presentation.platform.stopLocalAudio
import org.debs.kalog.feature.chat.domain.model.AvatarAccent
import org.debs.kalog.feature.chat.domain.model.AvatarSpec
import org.debs.kalog.feature.chat.domain.model.ChatAttachment
import org.debs.kalog.feature.chat.domain.model.ChatAttachmentKind
import org.debs.kalog.feature.chat.domain.model.ChatMessage
import org.debs.kalog.feature.chat.domain.model.DeliveryStatus
import org.debs.kalog.feature.chat.domain.model.PreparedChatAttachment
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sqrt
import org.debs.kalog.feature.chat.presentation.components.AvatarBadge

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChatDetailsScreen(
    chatId: String,
    state: ChatDetailsUiState,
    onBack: () -> Unit,
    onDraftChanged: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachFileClick: () -> Unit,
    onPickImageClick: () -> Unit,
    onTakePhotoClick: () -> Unit,
    onRecordVoiceClick: () -> Unit,
    onPasteClipboardAttachments: () -> Boolean,
    onAttachmentsDropped: (List<ChatAttachment>) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onLoadMoreMessages: () -> Unit,
    onInviteUserClick: () -> Unit,
    onChatInfoClick: () -> Unit = {},
    onAcceptInvitation: () -> Unit = {},
    onDeclineInvitation: () -> Unit = {},
    isRecordingVoice: Boolean = false,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {
    val listState = rememberLazyListState()
    val defaultSnackbarHostState = remember { SnackbarHostState() }
    val resolvedSnackbarHostState = snackbarHostState ?: defaultSnackbarHostState
    val hasLoadMoreItem = state.hasMoreMessages || state.isLoadingMoreMessages
    val coroutineScope = rememberCoroutineScope()
    var fullScreenGallery by remember { mutableStateOf<FullScreenMediaGallery?>(null) }
    var isFileDragOver by remember { mutableStateOf(false) }
    var playingVoiceAttachmentId by remember { mutableStateOf<String?>(null) }
    var voicePlaybackProgress by remember { mutableStateOf<Map<String, VoicePlaybackProgress>>(emptyMap()) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

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

    LaunchedEffect(fullScreenGallery) {
        if (fullScreenGallery != null) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopLocalAudio()
        }
    }

    fun updatePlaybackProgress(
        attachment: ChatAttachment,
        positionMillis: Long,
        durationMillis: Long,
    ) {
        voicePlaybackProgress = voicePlaybackProgress + (
            attachment.id to VoicePlaybackProgress(
                positionMillis = positionMillis,
                durationMillis = durationMillis,
            )
        )
    }

    fun startAudioPlayback(
        attachment: ChatAttachment,
        startAtMillis: Long = 0L,
    ) {
        val localUri = attachment.localUri ?: return
        val started = playLocalAudio(
            localUri = localUri,
            onProgress = { positionMillis, durationMillis ->
                coroutineScope.launch {
                    updatePlaybackProgress(attachment, positionMillis, durationMillis)
                }
            },
            onFinished = {
                coroutineScope.launch {
                    val nextVoice = if (attachment.kind == ChatAttachmentKind.Voice) {
                        state.messages.nextVoiceFromSameSender(attachment.id)
                    } else {
                        null
                    }
                    if (nextVoice?.localUri != null) {
                        startAudioPlayback(nextVoice)
                    } else {
                        playingVoiceAttachmentId = null
                    }
                }
            },
        )
        if (started) {
            playingVoiceAttachmentId = attachment.id
            val startPosition = startAtMillis.coerceAtLeast(0L)
            updatePlaybackProgress(
                attachment = attachment,
                positionMillis = startPosition,
                durationMillis = attachment.durationMillis ?: 0L,
            )
            if (startPosition > 0L) {
                seekLocalAudio(startPosition)
            }
        }
    }

    val onToggleVoicePlayback: (ChatAttachment) -> Unit = toggleVoice@ { attachment ->
        if (attachment.localUri == null) return@toggleVoice
        if (playingVoiceAttachmentId == attachment.id) {
            stopLocalAudio()
            playingVoiceAttachmentId = null
            return@toggleVoice
        }
        startAudioPlayback(attachment)
    }

    val onSeekVoicePlayback: (ChatAttachment, Long) -> Unit = seekVoice@ { attachment, positionMillis ->
        if (attachment.localUri == null) return@seekVoice
        val safePosition = positionMillis.coerceAtLeast(0L)
        if (playingVoiceAttachmentId == attachment.id) {
            if (seekLocalAudio(safePosition)) {
                updatePlaybackProgress(
                    attachment = attachment,
                    positionMillis = safePosition,
                    durationMillis = voicePlaybackProgress[attachment.id]?.durationMillis
                        ?: attachment.durationMillis
                        ?: 0L,
                )
            }
        } else {
            startAudioPlayback(attachment, safePosition)
        }
    }
    val dropTarget = remember(onAttachmentsDropped) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                isFileDragOver = false
                val attachments = readDroppedAttachments(event)
                if (attachments.isEmpty()) return false
                onAttachmentsDropped(attachments)
                return true
            }

            override fun onEntered(event: DragAndDropEvent) {
                isFileDragOver = true
            }

            override fun onExited(event: DragAndDropEvent) {
                isFileDragOver = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                isFileDragOver = false
            }
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.V &&
                    (event.isCtrlPressed || event.isMetaPressed)
                ) {
                    onPasteClipboardAttachments()
                } else {
                    false
                }
            }
            .dragAndDropTarget(
                shouldStartDragAndDrop = { true },
                target = dropTarget,
            ),
    ) {
        Scaffold(
        modifier = Modifier.fillMaxSize(),
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
                    attachmentUploadProgress = state.attachmentUploadProgress,
                    isPreparingAttachment = state.isPreparingAttachment,
                    canSend = state.canSend,
                    onDraftChanged = onDraftChanged,
                    onSendClick = onSendClick,
                    onAttachFileClick = onAttachFileClick,
                    onPickImageClick = onPickImageClick,
                    onTakePhotoClick = onTakePhotoClick,
                    onRecordVoiceClick = onRecordVoiceClick,
                    onPasteClipboardAttachments = onPasteClipboardAttachments,
                    onRemoveAttachment = onRemoveAttachment,
                    isRecordingVoice = isRecordingVoice,
                )
            }
        },
    ) { innerPadding ->
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
                        is ChatMessage.User -> UserMessageBubble(
                            message = message,
                            onOpenMedia = { attachment ->
                                fullScreenGallery = message.toFullScreenMediaGallery(attachment)
                            },
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

            if (isFileDragOver) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.94f),
                    shadowElevation = 8.dp,
                ) {
                    Text(
                        text = "Drop files to attach",
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        }

        fullScreenGallery?.let { gallery ->
            FullScreenMediaGalleryViewer(
                gallery = gallery,
                onGalleryChanged = { updatedGallery -> fullScreenGallery = updatedGallery },
                onDismiss = { fullScreenGallery = null },
            )
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
    onOpenMedia: (ChatAttachment) -> Unit,
    playingVoiceAttachmentId: String?,
    voicePlaybackProgress: Map<String, VoicePlaybackProgress>,
    onToggleVoicePlayback: (ChatAttachment) -> Unit,
    onSeekVoicePlayback: (ChatAttachment, Long) -> Unit,
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
                if (message.body.isNotBlank()) {
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
                val mediaAttachments = message.attachments.filter { attachment -> attachment.isGridMedia() }
                val otherAttachments = message.attachments.filterNot { attachment -> attachment.isGridMedia() }
                mediaAttachments.chunked(MAX_MEDIA_GRID_ITEMS).forEach { mediaGroup ->
                    if (mediaGroup.size == 1) {
                        MessageAttachmentChip(
                            attachment = mediaGroup.first(),
                            isMine = message.isMine,
                            onOpenMedia = onOpenMedia,
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
                        )
                    }
                }
                otherAttachments.forEach { attachment ->
                    MessageAttachmentChip(
                        attachment = attachment,
                        isMine = message.isMine,
                        onOpenMedia = onOpenMedia,
                        isVoicePlaying = playingVoiceAttachmentId == attachment.id,
                        voicePlaybackProgress = voicePlaybackProgress[attachment.id],
                        onToggleVoicePlayback = onToggleVoicePlayback,
                        onSeekVoicePlayback = onSeekVoicePlayback,
                    )
                }
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
private fun MediaAttachmentGrid(
    attachments: List<ChatAttachment>,
    isMine: Boolean,
    onOpenMedia: (ChatAttachment) -> Unit,
) {
    val media = attachments.take(MAX_MEDIA_GRID_ITEMS)
    Column(
        modifier = Modifier.widthIn(max = 320.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (media.size) {
            2 -> MediaGridRow(media, isMine, onOpenMedia, height = 154.dp)
            3 -> {
                MediaGridRow(media.take(1), isMine, onOpenMedia, height = 172.dp)
                MediaGridRow(media.drop(1), isMine, onOpenMedia, height = 104.dp)
            }
            4 -> {
                MediaGridRow(media.take(2), isMine, onOpenMedia, height = 154.dp)
                MediaGridRow(media.drop(2), isMine, onOpenMedia, height = 154.dp)
            }
            5 -> {
                MediaGridRow(media.take(1), isMine, onOpenMedia, height = 164.dp)
                MediaGridRow(media.drop(1).take(2), isMine, onOpenMedia, height = 98.dp)
                MediaGridRow(media.drop(3), isMine, onOpenMedia, height = 98.dp)
            }
            6 -> {
                MediaGridRow(media.take(3), isMine, onOpenMedia, height = 104.dp)
                MediaGridRow(media.drop(3), isMine, onOpenMedia, height = 104.dp)
            }
            7 -> {
                MediaGridRow(media.take(1), isMine, onOpenMedia, height = 154.dp)
                MediaGridRow(media.drop(1).take(3), isMine, onOpenMedia, height = 90.dp)
                MediaGridRow(media.drop(4), isMine, onOpenMedia, height = 90.dp)
            }
            8 -> {
                MediaGridRow(media.take(2), isMine, onOpenMedia, height = 126.dp)
                MediaGridRow(media.drop(2).take(3), isMine, onOpenMedia, height = 90.dp)
                MediaGridRow(media.drop(5), isMine, onOpenMedia, height = 90.dp)
            }
            9 -> {
                MediaGridRow(media.take(3), isMine, onOpenMedia, height = 88.dp)
                MediaGridRow(media.drop(3).take(3), isMine, onOpenMedia, height = 88.dp)
                MediaGridRow(media.drop(6), isMine, onOpenMedia, height = 88.dp)
            }
            else -> {
                MediaGridRow(media.take(2), isMine, onOpenMedia, height = 122.dp)
                MediaGridRow(media.drop(2).take(4), isMine, onOpenMedia, height = 72.dp)
                MediaGridRow(media.drop(6).take(4), isMine, onOpenMedia, height = 72.dp)
            }
        }
    }
}

@Composable
private fun MediaGridRow(
    attachments: List<ChatAttachment>,
    isMine: Boolean,
    onOpenMedia: (ChatAttachment) -> Unit,
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
    modifier: Modifier = Modifier,
) {
    val imageBitmap = rememberAttachmentImagePreviewBitmap(attachment)
    val thumbnailBitmap = rememberVideoThumbnailBitmap(attachment)
    val canOpen = when (attachment.kind) {
        ChatAttachmentKind.Image -> imageBitmap != null
        ChatAttachmentKind.Video -> attachment.localUri != null
        else -> false
    }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = canOpen) {
                onOpenMedia(attachment)
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
                else -> MediaLoadingPlaceholderContent()
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
                            contentDescription = "Play video",
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
    isVoicePlaying: Boolean,
    voicePlaybackProgress: VoicePlaybackProgress?,
    onToggleVoicePlayback: (ChatAttachment) -> Unit,
    onSeekVoicePlayback: (ChatAttachment, Long) -> Unit,
) {
    when (attachment.kind) {
        ChatAttachmentKind.Image -> {
            ImageAttachmentPreview(attachment, isMine, onOpenMedia)
            return
        }
        ChatAttachmentKind.Video -> {
            VideoAttachmentPreview(
                attachment = attachment,
                isMine = isMine,
                onOpenVideo = onOpenMedia,
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
                onSeekPlayback = onSeekVoicePlayback,
            )
            return
        }
        ChatAttachmentKind.File -> Unit
    }

    Surface(
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
                text = attachment.kind.label(),
                style = MaterialTheme.typography.labelMedium,
                color = if (isMine) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = attachment.displayName(),
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
) {
    val imageBitmap = rememberAttachmentImagePreviewBitmap(attachment)

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = imageBitmap != null) {
                onOpenImage(attachment)
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
                title = "Image",
            )
        }
    }
}

@Composable
private fun FullScreenMediaGalleryViewer(
    gallery: FullScreenMediaGallery,
    onGalleryChanged: (FullScreenMediaGallery) -> Unit,
    onDismiss: () -> Unit,
) {
    val media = gallery.selectedMedia ?: return
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

    when (media.attachment.kind) {
        ChatAttachmentKind.Image -> FullScreenImageViewer(
            media = media,
            previousMedia = gallery.items.getOrNull(gallery.selectedIndex - 1),
            nextMedia = gallery.items.getOrNull(gallery.selectedIndex + 1),
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            positionLabel = positionLabel,
            onPrevious = onPrevious,
            onNext = onNext,
            onDismiss = onDismiss,
        )
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
    hasPrevious: Boolean,
    hasNext: Boolean,
    positionLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
) {
    val attachment = media.attachment
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val imageBitmap = rememberAttachmentImageBitmap(attachment)
    val previousImageBitmap = rememberOptionalAttachmentImageBitmap(
        previousMedia?.attachment?.takeIf { item -> item.kind == ChatAttachmentKind.Image },
    )
    val nextImageBitmap = rememberOptionalAttachmentImageBitmap(
        nextMedia?.attachment?.takeIf { item -> item.kind == ChatAttachmentKind.Image },
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
                    if (hasPrevious) animateGallerySlide(direction = 1, navigate = onPrevious)
                }
                fun navigateToNext() {
                    if (hasNext) animateGallerySlide(direction = -1, navigate = onNext)
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
                        contentDescription = "Previous media",
                        onClick = onPrevious,
                    )
                }
                FullScreenImageControl(
                    icon = Icons.Outlined.Remove,
                    contentDescription = "Zoom out",
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
                    contentDescription = "Zoom in",
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
                        contentDescription = "Next media",
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
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 0.dp, vertical = 86.dp)
                .fullScreenGallerySwipe(
                    enabled = hasPrevious || hasNext,
                    onPrevious = onPrevious,
                    onNext = onNext,
                ),
        )
        FullScreenMediaHeader(
            media = media,
            onDismiss = onDismiss,
        )
        FullScreenMediaFooter(
            media = media,
            centerContent = {
                FullScreenGalleryNavigation(
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    positionLabel = positionLabel,
                    onPrevious = onPrevious,
                    onNext = onNext,
                )
            },
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
                    contentDescription = "Close media",
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
private fun FullScreenImageControl(
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
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

@Composable
private fun RowScope.FullScreenGalleryNavigation(
    hasPrevious: Boolean,
    hasNext: Boolean,
    positionLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    if (positionLabel.isBlank()) return
    FullScreenImageControl(
        enabled = hasPrevious,
        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
        contentDescription = "Previous media",
        onClick = onPrevious,
    )
    Text(
        text = positionLabel,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White.copy(alpha = 0.84f),
        fontWeight = FontWeight.SemiBold,
    )
    FullScreenImageControl(
        enabled = hasNext,
        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = "Next media",
        onClick = onNext,
    )
}

private fun Modifier.fullScreenGallerySwipe(
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(onPrevious, onNext) {
        var totalDrag = 0f
        detectHorizontalDragGestures(
            onDragStart = { totalDrag = 0f },
            onHorizontalDrag = { change, dragAmount ->
                totalDrag += dragAmount
                change.consume()
            },
            onDragEnd = {
                if (abs(totalDrag) >= FULL_SCREEN_SWIPE_THRESHOLD_PX) {
                    if (totalDrag > 0f) {
                        onPrevious()
                    } else {
                        onNext()
                    }
                }
            },
        )
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
) {
    val thumbnailBitmap = rememberVideoThumbnailBitmap(attachment)

    BoxWithConstraints {
        val previewWidth = (maxWidth.value * 0.58f).dp.coerceAtMost(240.dp)
        Surface(
            modifier = Modifier
                .width(previewWidth)
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = attachment.localUri != null) {
                    onOpenVideo(attachment)
                },
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF111827),
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
                            .background(Color(0xFF1F2937)),
                    ) {
                        MediaLoadingPlaceholderContent()
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
                                contentDescription = "Play video",
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
                    .clickable(enabled = attachment.localUri != null) {
                        onTogglePlayback(attachment)
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
                        imageVector = if (isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                        contentDescription = if (isPlaying) "Stop voice message" else "Play voice message",
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
                    text = if (isPlaying) "Playing voice" else "Voice message",
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
                    .clickable(enabled = attachment.localUri != null) {
                        onTogglePlayback(attachment)
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
                        imageVector = if (isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                        contentDescription = if (isPlaying) "Stop audio" else "Play audio",
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
                    text = attachment.displayName(),
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
                attachment.localUri?.let(::openLocalAttachment)
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
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Loading ${attachment.displayName()}",
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
private fun MediaLoadingPlaceholderContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1F2937)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.dp,
            color = Color.White,
        )
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
                        contentDescription = "Attach",
                        enabled = !isPreparingAttachment,
                        onClick = { isAttachmentMenuVisible = true },
                    )
                    DropdownMenu(
                        expanded = isAttachmentMenuVisible,
                        onDismissRequest = { isAttachmentMenuVisible = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("File") },
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
                            text = { Text("Photo / video") },
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
                            text = { Text("Camera") },
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
                            text = { Text("Paste") },
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
                            .heightIn(min = 48.dp, max = 180.dp)
                            .verticalScroll(scrollState)
                            .padding(horizontal = 18.dp, vertical = 13.dp),
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
                                else -> Icons.Outlined.Send
                            },
                            contentDescription = when {
                                isRecordingVoice -> "Stop recording"
                                shouldRecordVoice -> "Record voice"
                                else -> "Send message"
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
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
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
                    text = if (progress == null) "Preparing attachment..." else "Uploading ${progress.fileName}",
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
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer,
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
                text = "Recording voice... tap stop to attach",
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
        ChatAttachmentKind.Video -> "VIDEO"
        ChatAttachmentKind.Audio -> "AUDIO"
        ChatAttachmentKind.Voice -> "VOICE"
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
        value = withContext(Dispatchers.Default) {
            loadImagePreview(
                localUri = localUri,
                contentBytes = contentBytes,
                maxSidePx = CHAT_IMAGE_PREVIEW_MAX_SIDE_PX,
            )?.decodeToImageBitmapOrNull()
        }?.also { bitmap -> MediaPreviewMemoryCache.put(cacheKey, bitmap) }
            ?.let { bitmap -> KeyedImageBitmap(cacheKey, bitmap) }
    }
    return imageBitmap?.takeIf { keyed -> keyed.key == cacheKey }?.bitmap
}

@Composable
private fun rememberAttachmentImageBitmap(
    attachment: ChatAttachment,
): ImageBitmap? {
    val contentBytes = attachment.contentBytes
    val localUri = attachment.localUri
    val bitmapKey = attachment.previewCacheKey(
        prefix = "full-image",
        maxSidePx = CHAT_FULL_SCREEN_IMAGE_MAX_SIDE_PX,
    )
    val imageBitmap by produceState<KeyedImageBitmap?>(
        initialValue = FullScreenImageMemoryCache.get(bitmapKey)?.let { bitmap ->
            KeyedImageBitmap(bitmapKey, bitmap)
        },
        key1 = attachment.id,
        key2 = localUri,
        key3 = contentBytes,
    ) {
        FullScreenImageMemoryCache.get(bitmapKey)?.let { cached ->
            value = KeyedImageBitmap(bitmapKey, cached)
            return@produceState
        }
        value = null
        if (contentBytes == null && localUri == null) return@produceState
        value = withContext(Dispatchers.Default) {
            loadImagePreview(
                localUri = localUri,
                contentBytes = contentBytes,
                maxSidePx = CHAT_FULL_SCREEN_IMAGE_MAX_SIDE_PX,
            )?.decodeToImageBitmapOrNull()
        }?.also { bitmap -> FullScreenImageMemoryCache.put(bitmapKey, bitmap) }
            ?.let { bitmap -> KeyedImageBitmap(bitmapKey, bitmap) }
    }
    return imageBitmap?.takeIf { keyed -> keyed.key == bitmapKey }?.bitmap
}

@Composable
private fun rememberOptionalAttachmentImageBitmap(
    attachment: ChatAttachment?,
): ImageBitmap? {
    val contentBytes = attachment?.contentBytes
    val localUri = attachment?.localUri
    val bitmapKey = attachment?.previewCacheKey(
        prefix = "full-image",
        maxSidePx = CHAT_FULL_SCREEN_IMAGE_MAX_SIDE_PX,
    ).orEmpty()
    val imageBitmap by produceState<KeyedImageBitmap?>(
        initialValue = FullScreenImageMemoryCache.get(bitmapKey)?.let { bitmap ->
            KeyedImageBitmap(bitmapKey, bitmap)
        },
        key1 = attachment?.id,
        key2 = localUri,
        key3 = contentBytes,
    ) {
        FullScreenImageMemoryCache.get(bitmapKey)?.let { cached ->
            value = KeyedImageBitmap(bitmapKey, cached)
            return@produceState
        }
        value = null
        if (attachment == null || (contentBytes == null && localUri == null)) return@produceState
        value = withContext(Dispatchers.Default) {
            loadImagePreview(
                localUri = localUri,
                contentBytes = contentBytes,
                maxSidePx = CHAT_FULL_SCREEN_IMAGE_MAX_SIDE_PX,
            )?.decodeToImageBitmapOrNull()
        }?.also { bitmap -> FullScreenImageMemoryCache.put(bitmapKey, bitmap) }
            ?.let { bitmap -> KeyedImageBitmap(bitmapKey, bitmap) }
    }
    return imageBitmap?.takeIf { keyed -> keyed.key == bitmapKey }?.bitmap
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
        value = withContext(Dispatchers.Default) {
            loadVideoThumbnail(localUri, CHAT_VIDEO_THUMBNAIL_MAX_SIDE_PX)?.decodeToImageBitmapOrNull()
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

private fun ChatAttachment.isGridMedia(): Boolean {
    return kind == ChatAttachmentKind.Image || kind == ChatAttachmentKind.Video
}

private fun Offset.coerceToBounds(maxX: Float, maxY: Float): Offset {
    return Offset(
        x = x.coerceIn(-maxX.coerceAtLeast(0f), maxX.coerceAtLeast(0f)),
        y = y.coerceIn(-maxY.coerceAtLeast(0f), maxY.coerceAtLeast(0f)),
    )
}

private const val MAX_MEDIA_GRID_ITEMS = 10
private const val FULL_SCREEN_SWIPE_THRESHOLD_PX = 80f
private const val FULL_SCREEN_GALLERY_SLIDE_DURATION_MS = 220
private const val FULL_SCREEN_IMAGE_SETTLE_DURATION_MS = 180
private const val FULL_SCREEN_GALLERY_SLIDE_MARGIN_PX = 48f
private const val CHAT_IMAGE_PREVIEW_MAX_SIDE_PX = 720
private const val CHAT_FULL_SCREEN_IMAGE_MAX_SIDE_PX = 2560
private const val CHAT_VIDEO_THUMBNAIL_MAX_SIDE_PX = 480

private data class VoicePlaybackProgress(
    val positionMillis: Long,
    val durationMillis: Long,
)

private data class FullScreenMedia(
    val attachment: ChatAttachment,
    val sender: String,
    val timestamp: String,
    val description: String,
)

private data class FullScreenMediaGallery(
    val items: List<FullScreenMedia>,
    val selectedIndex: Int,
) {
    val selectedMedia: FullScreenMedia?
        get() = items.getOrNull(selectedIndex)
}

private fun ChatMessage.User.toFullScreenMediaGallery(attachment: ChatAttachment): FullScreenMediaGallery {
    val mediaAttachments = attachments.filter { item -> item.isGridMedia() }
    val selectedIndex = mediaAttachments.indexOfFirst { item -> item.id == attachment.id }
        .takeIf { index -> index >= 0 }
        ?: 0
    return FullScreenMediaGallery(
        items = mediaAttachments.map { mediaAttachment ->
            FullScreenMedia(
                attachment = mediaAttachment,
                sender = if (isMine) "You" else sender,
                timestamp = timestamp,
                description = body.ifBlank { mediaAttachment.displayName() },
            )
        },
        selectedIndex = selectedIndex,
    )
}

private fun List<ChatMessage>.nextVoiceFromSameSender(currentAttachmentId: String): ChatAttachment? {
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
        onTakePhotoClick = {},
        onRecordVoiceClick = {},
        onPasteClipboardAttachments = { false },
        onAttachmentsDropped = {},
        onRemoveAttachment = {},
        onLoadMoreMessages = {},
        onInviteUserClick = {},
    )
}
