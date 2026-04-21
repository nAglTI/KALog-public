package org.debs.kalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.debs.kalog.app.AppExitManager
import org.debs.kalog.feature.chat.domain.usecase.CloseChatUseCase
import org.debs.kalog.feature.chat.presentation.koinLifecycleViewModel
import org.debs.kalog.feature.chat.presentation.chat.ChatDetailsRoute
import org.debs.kalog.feature.chat.presentation.chatinfo.ChatInfoRoute
import org.debs.kalog.feature.chat.presentation.chatlist.ChatListRoute
import org.debs.kalog.feature.chat.presentation.session.ChatSessionViewModel
import org.debs.kalog.feature.chat.presentation.settings.SettingsRoute
import org.debs.kalog.ui.theme.KalogTheme
import org.koin.compose.koinInject

private sealed interface AppScreen {
    data object ChatList : AppScreen
    data class ChatDetails(val chatId: String) : AppScreen
    data class ChatInfo(val chatId: String) : AppScreen
    data object Settings : AppScreen
}

@Composable
fun App() {
    KalogTheme {
        val chatSessionViewModel = koinLifecycleViewModel<ChatSessionViewModel>()
        val appExitManager = koinInject<AppExitManager>()
        val closeChatUseCase = koinInject<CloseChatUseCase>()
        var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.ChatList) }
        var appInstanceId by remember { mutableIntStateOf(0) }

        LaunchedEffect(currentScreen) {
            if (currentScreen is AppScreen.ChatList) {
                closeChatUseCase()
            }
        }

        when (val screen = currentScreen) {
            is AppScreen.ChatList -> {
                key(appInstanceId) {
                    ChatListRoute(
                        viewModelKey = "chat-list-$appInstanceId",
                        onChatSelected = { currentScreen = AppScreen.ChatDetails(it) },
                        onSettingsClick = { currentScreen = AppScreen.Settings },
                    )
                }
            }
            is AppScreen.ChatDetails -> {
                ChatDetailsRoute(
                    chatId = screen.chatId,
                    onBack = { currentScreen = AppScreen.ChatList },
                    onChatInfoClick = { currentScreen = AppScreen.ChatInfo(screen.chatId) },
                )
            }
            is AppScreen.ChatInfo -> {
                ChatInfoRoute(
                    chatId = screen.chatId,
                    onBack = { currentScreen = AppScreen.ChatDetails(screen.chatId) },
                    onNavigateToChat = { chatId ->
                        currentScreen = AppScreen.ChatDetails(chatId)
                    },
                )
            }
            is AppScreen.Settings -> {
                SettingsRoute(
                    onBack = { currentScreen = AppScreen.ChatList },
                    onAppCleared = {
                        currentScreen = AppScreen.ChatList
                        chatSessionViewModel.shutdown()
                        if (!appExitManager.exitApp()) {
                            appInstanceId += 1
                            chatSessionViewModel.restart()
                        }
                    },
                )
            }
        }
    }
}
