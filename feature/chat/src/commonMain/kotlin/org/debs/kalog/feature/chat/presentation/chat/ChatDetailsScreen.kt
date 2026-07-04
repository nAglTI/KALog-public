package org.debs.kalog.feature.chat.presentation.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mayday_chat.feature.chat.generated.resources.Res
import mayday_chat.feature.chat.generated.resources.drop_files_to_attach
import mayday_chat.feature.chat.generated.resources.you
import org.debs.kalog.feature.chat.domain.model.ChatAttachment
import org.debs.kalog.feature.chat.domain.model.ChatAttachmentKind
import org.debs.kalog.feature.chat.domain.model.ChatMessage
import org.debs.kalog.feature.chat.presentation.platform.*
import org.jetbrains.compose.resources.stringResource

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
