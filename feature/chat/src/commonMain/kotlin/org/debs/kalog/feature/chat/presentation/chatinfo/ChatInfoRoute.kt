package org.debs.kalog.feature.chat.presentation.chatinfo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import org.debs.kalog.feature.chat.presentation.koinLifecycleViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChatInfoRoute(
    chatId: String,
    onBack: () -> Unit,
    onNavigateToChat: (String) -> Unit,
) {
    val viewModel = koinLifecycleViewModel<ChatInfoViewModel>(
        key = "chat-info-$chatId",
        parameters = { parametersOf(chatId) },
    )
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ChatInfoEffect.NavigateToChat -> onNavigateToChat(effect.chatId)
            }
        }
    }

    BackHandler(onBack = onBack)

    ChatInfoScreen(
        state = state,
        onBack = onBack,
        onTitleChanged = { newTitle ->
            viewModel.onEvent(ChatInfoEvent.TitleChanged(newTitle))
        },
        onParticipantClick = { userId ->
            viewModel.onEvent(ChatInfoEvent.ParticipantClicked(userId))
        },
    )
}
