package org.debs.kalog.feature.chat.presentation.chatlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import mayday_chat.feature.chat.generated.resources.Res
import mayday_chat.feature.chat.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.debs.kalog.feature.chat.presentation.components.ChatTypeSelectionDialog
import org.debs.kalog.feature.chat.presentation.components.UuidInputDialog
import org.debs.kalog.feature.chat.presentation.koinLifecycleViewModel

@Composable
fun ChatListRoute(
    viewModelKey: String,
    onChatSelected: (String) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val viewModel = koinLifecycleViewModel<ChatListViewModel>(key = viewModelKey)
    val state by viewModel.state.collectAsState()
    var isCreateTypeDialogVisible by rememberSaveable { mutableStateOf(false) }
    var isCreatePersonalChatDialogVisible by rememberSaveable { mutableStateOf(false) }
    var targetUserUuid by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ChatListEffect.NavigateToChat -> {
                    isCreateTypeDialogVisible = false
                    isCreatePersonalChatDialogVisible = false
                    targetUserUuid = ""
                    onChatSelected(effect.chatId)
                }
            }
        }
    }

    if (isCreateTypeDialogVisible) {
        ChatTypeSelectionDialog(
            onDismiss = { isCreateTypeDialogVisible = false },
            onCreatePersonalChat = {
                isCreateTypeDialogVisible = false
                isCreatePersonalChatDialogVisible = true
            },
            onCreateGroupChat = {
                isCreateTypeDialogVisible = false
                viewModel.onEvent(ChatListEvent.CreateGroupChatClicked)
            },
            isProcessing = state.isCreatingChat,
        )
    }

    if (isCreatePersonalChatDialogVisible) {
        UuidInputDialog(
            title = stringResource(Res.string.create_personal_chat),
            value = targetUserUuid,
            confirmLabel = stringResource(Res.string.create_chat),
            onValueChange = { targetUserUuid = it },
            onDismiss = {
                isCreatePersonalChatDialogVisible = false
                targetUserUuid = ""
            },
            onConfirm = {
                viewModel.onEvent(ChatListEvent.CreateChatConfirmed(targetUserUuid))
            },
            isProcessing = state.isCreatingChat,
        )
    }

    ChatListScreen(
        state = state,
        onChatClick = { chatId ->
            viewModel.onEvent(ChatListEvent.ChatClicked(chatId))
        },
        onCreateChatClick = {
            isCreateTypeDialogVisible = true
        },
        onSettingsClick = onSettingsClick,
    )
}
