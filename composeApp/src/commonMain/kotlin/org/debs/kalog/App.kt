package org.debs.kalog

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.debs.kalog.app.AppExitManager
import org.debs.kalog.feature.chat.data.preferences.AppThemeMode
import org.debs.kalog.feature.chat.data.preferences.ChatPreferencesDataSource
import org.debs.kalog.feature.chat.domain.usecase.CloseChatUseCase
import org.debs.kalog.feature.chat.localization.chatLocalized
import org.debs.kalog.feature.chat.presentation.koinLifecycleViewModel
import org.debs.kalog.feature.chat.presentation.chat.ChatDetailsRoute
import org.debs.kalog.feature.chat.presentation.chatinfo.ChatInfoRoute
import org.debs.kalog.feature.chat.presentation.chatlist.ChatListRoute
import org.debs.kalog.feature.chat.presentation.onboarding.AccountOnboardingRoute
import org.debs.kalog.feature.chat.presentation.platform.ConfigureSystemBars
import org.debs.kalog.feature.chat.presentation.platform.DesktopAutostartManager
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
fun App(
    protectedDeviceLockAvailable: Boolean = true,
) {
    val chatPreferencesDataSource = koinInject<ChatPreferencesDataSource>()
    val themeMode by chatPreferencesDataSource.observeThemeMode().collectAsState(AppThemeMode.System)
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        AppThemeMode.System -> systemDarkTheme
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }

    KalogTheme(darkTheme = darkTheme) {
        ConfigureSystemBars(fullScreenMediaVisible = false)
        if (!protectedDeviceLockAvailable) {
            ProtectedDeviceLockRequiredScreen()
            return@KalogTheme
        }

        var accountReady by remember { mutableStateOf(false) }
        var currentScreen by rememberSaveable(stateSaver = AppScreenSaver) {
            mutableStateOf<AppScreen>(AppScreen.ChatList)
        }
        var appInstanceId by remember { mutableIntStateOf(0) }

        if (!accountReady) {
            AccountOnboardingRoute(onReady = { accountReady = true })
        } else {
            val desktopAutostartManager = koinInject<DesktopAutostartManager>()
            val appExitManager = koinInject<AppExitManager>()
            val closeChatUseCase = koinInject<CloseChatUseCase>()
            val chatSessionViewModel = koinLifecycleViewModel<ChatSessionViewModel>()

            LaunchedEffect(desktopAutostartManager) {
                if (!desktopAutostartManager.isSupported) return@LaunchedEffect

                runCatching {
                    val enabled = chatPreferencesDataSource.isDesktopAutostartEnabled()
                    val actualEnabled = desktopAutostartManager.isEnabled()
                    when {
                        enabled && !actualEnabled -> desktopAutostartManager.setEnabled(true)
                        !enabled && actualEnabled -> desktopAutostartManager.setEnabled(false)
                    }
                }
            }

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
                            accountReady = false
                            chatSessionViewModel.shutdown()
                            if (!appExitManager.exitApp()) {
                                appInstanceId += 1
                                chatSessionViewModel.restart()
                            }
                        },
                        onAccountImported = {
                            currentScreen = AppScreen.ChatList
                            appInstanceId += 1
                            chatSessionViewModel.restart()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProtectedDeviceLockRequiredScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = chatLocalized(
                        en = "Device lock required",
                        ru = "Нужна блокировка устройства",
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = chatLocalized(
                        en = "Mayday Chat can open, but account creation, backup import, UUID registration, and key generation are disabled until you set up a system PIN, password, or biometric lock.",
                        ru = "Mayday Chat может открыться, но создание аккаунта, импорт резервных копий, регистрация UUID и генерация ключей отключены, пока вы не настроите системный PIN-код, пароль или биометрическую блокировку.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private val AppScreenSaver = Saver<AppScreen, String>(
    save = { screen ->
        when (screen) {
            AppScreen.ChatList -> "chat-list"
            is AppScreen.ChatDetails -> "chat-details:${screen.chatId}"
            is AppScreen.ChatInfo -> "chat-info:${screen.chatId}"
            AppScreen.Settings -> "settings"
        }
    },
    restore = { value ->
        when {
            value.startsWith("chat-details:") -> AppScreen.ChatDetails(value.removePrefix("chat-details:"))
            value.startsWith("chat-info:") -> AppScreen.ChatInfo(value.removePrefix("chat-info:"))
            value == "settings" -> AppScreen.Settings
            else -> AppScreen.ChatList
        }
    },
)
