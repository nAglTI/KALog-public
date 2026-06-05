package org.debs.kalog.feature.chat.presentation.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.launch
import org.debs.kalog.feature.chat.localization.chatLocalized
import org.debs.kalog.feature.chat.presentation.koinLifecycleViewModel
import org.debs.kalog.feature.chat.presentation.components.UuidInputDialog
import org.debs.kalog.feature.chat.presentation.platform.VoiceRecordingResult
import org.debs.kalog.feature.chat.presentation.platform.pickFileAttachment
import org.debs.kalog.feature.chat.presentation.platform.pickImageAttachments
import org.debs.kalog.feature.chat.presentation.platform.readClipboardAttachments
import org.debs.kalog.feature.chat.presentation.platform.takePhotoAttachment
import org.debs.kalog.feature.chat.presentation.platform.toggleVoiceRecording
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChatDetailsRoute(
    chatId: String,
    onBack: () -> Unit,
    onChatInfoClick: () -> Unit = {},
) {
    val viewModel = koinLifecycleViewModel<ChatDetailsViewModel>(
        key = chatId,
        parameters = { parametersOf(chatId) },
    )
    val state by viewModel.state.collectAsState()
    var isInviteDialogVisible by rememberSaveable { mutableStateOf(false) }
    var invitedUserUuid by rememberSaveable { mutableStateOf("") }
    var isRecordingVoice by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ChatDetailsEffect.InviteUserCompleted -> {
                    isInviteDialogVisible = false
                    invitedUserUuid = ""
                }
                ChatDetailsEffect.InvitationDeclined -> {
                    onBack()
                }
                is ChatDetailsEffect.ShowError -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is ChatDetailsEffect.ShowMessage -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    LaunchedEffect(state.canInviteUsers) {
        if (!state.canInviteUsers) {
            isInviteDialogVisible = false
            invitedUserUuid = ""
        }
    }

    if (isInviteDialogVisible && state.canInviteUsers) {
        UuidInputDialog(
            title = chatLocalized(
                en = "Invite user to chat",
                ru = "Пригласить пользователя в чат",
            ),
            value = invitedUserUuid,
            confirmLabel = chatLocalized(en = "Invite", ru = "Пригласить"),
            onValueChange = { invitedUserUuid = it },
            onDismiss = {
                isInviteDialogVisible = false
                invitedUserUuid = ""
            },
            onConfirm = {
                viewModel.onEvent(ChatDetailsEvent.InviteUserConfirmed(invitedUserUuid))
            },
            isProcessing = state.isInvitingUser,
        )
    }

    BackHandler(onBack = onBack)

    ChatDetailsScreen(
        chatId = chatId,
        state = state,
        onBack = onBack,
        onDraftChanged = { value ->
            viewModel.onEvent(ChatDetailsEvent.DraftChanged(value))
        },
        onSendClick = {
            viewModel.onEvent(ChatDetailsEvent.SendClicked)
        },
        onAttachFileClick = {
            coroutineScope.launch {
                val attachment = pickFileAttachment()
                if (attachment != null) {
                    viewModel.onEvent(ChatDetailsEvent.AttachmentDraftSelected(attachment))
                } else {
                    viewModel.onEvent(ChatDetailsEvent.AttachFileClicked)
                }
            }
        },
        onPickImageClick = {
            coroutineScope.launch {
                val attachments = pickImageAttachments()
                if (attachments.isNotEmpty()) {
                    viewModel.onEvent(ChatDetailsEvent.AttachmentDraftsSelected(attachments))
                } else {
                    viewModel.onEvent(ChatDetailsEvent.PickImageClicked)
                }
            }
        },
        onTakePhotoClick = {
            coroutineScope.launch {
                val attachment = takePhotoAttachment()
                if (attachment != null) {
                    viewModel.onEvent(ChatDetailsEvent.AttachmentDraftSelected(attachment))
                } else {
                    snackbarHostState.showSnackbar(
                        chatLocalized(
                            en = "Camera access was denied or the action was cancelled.",
                            ru = "Доступ к камере отклонён или действие отменено.",
                        ),
                    )
                }
            }
        },
        onRecordVoiceClick = {
            coroutineScope.launch {
                when (val result = toggleVoiceRecording()) {
                    VoiceRecordingResult.Started -> {
                        isRecordingVoice = true
                    }
                    is VoiceRecordingResult.Finished -> {
                        isRecordingVoice = false
                        viewModel.onEvent(ChatDetailsEvent.AttachmentDraftSelected(result.attachment))
                    }
                    VoiceRecordingResult.TooShort -> {
                        isRecordingVoice = false
                        snackbarHostState.showSnackbar(
                            chatLocalized(
                                en = "Voice recording is too short.",
                                ru = "Голосовая запись слишком короткая.",
                            ),
                        )
                    }
                    VoiceRecordingResult.PermissionDenied -> {
                        isRecordingVoice = false
                        snackbarHostState.showSnackbar(
                            chatLocalized(
                                en = "Microphone access is required.",
                                ru = "Нужен доступ к микрофону.",
                            ),
                        )
                    }
                    VoiceRecordingResult.Unavailable -> {
                        isRecordingVoice = false
                        snackbarHostState.showSnackbar(
                            chatLocalized(
                                en = "Voice recording is unavailable.",
                                ru = "Запись голоса недоступна.",
                            ),
                        )
                    }
                }
            }
        },
        onPasteClipboardAttachments = {
            val attachments = readClipboardAttachments()
            if (attachments.isNotEmpty()) {
                viewModel.onEvent(ChatDetailsEvent.AttachmentDraftsSelected(attachments))
                true
            } else {
                false
            }
        },
        onAttachmentsDropped = { attachments ->
            viewModel.onEvent(ChatDetailsEvent.AttachmentDraftsSelected(attachments))
        },
        onRemoveAttachment = { attachmentId ->
            viewModel.onEvent(ChatDetailsEvent.RemoveAttachmentDraft(attachmentId))
        },
        onAttachmentDownloadClick = { attachmentId ->
            viewModel.onEvent(ChatDetailsEvent.AttachmentDownloadClicked(attachmentId))
        },
        onLoadMoreMessages = {
            viewModel.onEvent(ChatDetailsEvent.LoadMoreMessagesClicked)
        },
        onInviteUserClick = {
            if (state.canInviteUsers) {
                isInviteDialogVisible = true
            }
        },
        onChatInfoClick = onChatInfoClick,
        onAcceptInvitation = {
            viewModel.onEvent(ChatDetailsEvent.AcceptInvitationClicked)
        },
        onDeclineInvitation = {
            viewModel.onEvent(ChatDetailsEvent.DeclineInvitationClicked)
        },
        isRecordingVoice = isRecordingVoice,
        snackbarHostState = snackbarHostState,
    )
}
