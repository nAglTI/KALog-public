package org.debs.kalog.feature.chat.presentation.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarHostState
import org.debs.kalog.feature.chat.presentation.koinLifecycleViewModel
import org.debs.kalog.feature.chat.presentation.components.UuidInputDialog
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
    val snackbarHostState = remember { SnackbarHostState() }

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
            title = "Invite user to chat",
            value = invitedUserUuid,
            confirmLabel = "Invite",
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
            viewModel.onEvent(ChatDetailsEvent.AttachFileClicked)
        },
        onPickImageClick = {
            viewModel.onEvent(ChatDetailsEvent.PickImageClicked)
        },
        onRecordVoiceClick = {
            viewModel.onEvent(ChatDetailsEvent.RecordVoiceClicked)
        },
        onRemoveAttachment = { attachmentId ->
            viewModel.onEvent(ChatDetailsEvent.RemoveAttachmentDraft(attachmentId))
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
        snackbarHostState = snackbarHostState,
    )
}
