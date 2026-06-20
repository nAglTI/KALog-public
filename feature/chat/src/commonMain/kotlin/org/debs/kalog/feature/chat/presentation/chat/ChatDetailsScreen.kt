package org.debs.kalog.feature.chat.presentation.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.outlined.FileDownload
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
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.debs.kalog.feature.chat.presentation.platform.hasSoftwareKeyboard
import org.debs.kalog.feature.chat.presentation.platform.ConfigureSystemBars
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
import org.debs.kalog.feature.chat.domain.model.ChatAttachmentLoadState
import org.debs.kalog.feature.chat.domain.model.ChatMessage
import org.debs.kalog.feature.chat.domain.model.DeliveryStatus
import org.debs.kalog.feature.chat.domain.model.PreparedChatAttachment
import mayday_chat.feature.chat.generated.resources.Res
import mayday_chat.feature.chat.generated.resources.*
import org.jetbrains.compose.resources.stringResource
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
    onAttachmentDownloadClick: (String) -> Unit,
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
    var fullScreenAttachmentId by rememberSaveable(chatId) { mutableStateOf<String?>(null) }
    val currentUserDisplayName = stringResource(Res.string.you)
    val fullScreenGallery = remember(state.messages, fullScreenAttachmentId, currentUserDisplayName) {
        val attachmentId = fullScreenAttachmentId ?: return@remember null
        state.messages.firstNotNullOfOrNull { message ->
            val userMessage = message as? ChatMessage.User ?: return@firstNotNullOfOrNull null
            val attachment = userMessage.attachments.firstOrNull { it.id == attachmentId }
                ?: return@firstNotNullOfOrNull null
            if (attachment.isGridMedia()) {
                userMessage.toFullScreenMediaGallery(attachment, currentUserDisplayName)
            } else {
                null
            }
        }
    }
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

    ConfigureSystemBars(fullScreenMediaVisible = fullScreenGallery != null)

    LaunchedEffect(fullScreenAttachmentId, fullScreenGallery) {
        if (fullScreenAttachmentId != null && fullScreenGallery == null) {
            fullScreenAttachmentId = null
        }
    }

    LaunchedEffect(fullScreenGallery != null) {
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
            ChatTopBar(
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
                    isSendingMessage = state.isSendingMessage,
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
            MessageList(
                messages = state.messages,
                listState = listState,
                hasLoadMoreItem = hasLoadMoreItem,
                isLoadingMoreMessages = state.isLoadingMoreMessages,
                showScrollToBottom = showScrollToBottom,
                onScrollToBottom = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                onOpenMedia = { attachment ->
                    fullScreenAttachmentId = attachment.id
                },
                onRequestAttachmentDownload = onAttachmentDownloadClick,
                playingVoiceAttachmentId = playingVoiceAttachmentId,
                voicePlaybackProgress = voicePlaybackProgress,
                onToggleVoicePlayback = onToggleVoicePlayback,
                onSeekVoicePlayback = onSeekVoicePlayback,
                modifier = Modifier.fillMaxSize(),
            )

            if (isFileDragOver) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        text = stringResource(Res.string.drop_files_to_attach),
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
            MediaViewer(
                gallery = gallery,
                onGalleryChanged = { updatedGallery ->
                    fullScreenAttachmentId = updatedGallery.selectedMedia?.attachment?.id
                },
                onDismiss = { fullScreenAttachmentId = null },
            )
        }
    }
}
