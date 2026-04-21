package org.debs.kalog.feature.chat.presentation.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    )
}
