package org.debs.kalog.feature.chat.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import org.debs.kalog.feature.chat.presentation.koinLifecycleViewModel
import org.debs.kalog.feature.chat.presentation.components.ConfirmActionDialog

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onAppCleared: () -> Unit,
) {
    val viewModel = koinLifecycleViewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var isClearDataDialogVisible by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SettingsEffect.CopyUserId -> {
                    clipboardManager.setText(AnnotatedString(effect.userId))
                }
                SettingsEffect.AppDataCleared -> {
                    isClearDataDialogVisible = false
                    onAppCleared()
                }
            }
        }
    }

    if (isClearDataDialogVisible) {
        ConfirmActionDialog(
            title = "Clear all app data",
            message = "This will delete local chats cache, keys, preferences and secure storage. The app will be closed when possible.",
            confirmLabel = "Continue",
            onDismiss = {
                if (!state.isClearingData) {
                    isClearDataDialogVisible = false
                }
            },
            onConfirm = {
                viewModel.onEvent(SettingsEvent.ClearAllDataConfirmed)
            },
            isProcessing = state.isClearingData,
        )
    }

    SettingsScreen(
        state = state,
        onBackClick = onBack,
        onNicknameChange = { viewModel.onEvent(SettingsEvent.NicknameChanged(it)) },
        onSaveNicknameClick = { viewModel.onEvent(SettingsEvent.SaveNicknameClicked) },
        onCopyUserIdClick = { viewModel.onEvent(SettingsEvent.CopyUserIdClicked) },
        onClearDataClick = { isClearDataDialogVisible = true },
        onDebugModeToggle = { viewModel.onEvent(SettingsEvent.DebugModeToggled(it)) },
        onClearMediaCacheClick = { viewModel.onEvent(SettingsEvent.ClearMediaCacheClicked) },
        onMediaCacheRetentionDaysChange = {
            viewModel.onEvent(SettingsEvent.MediaCacheRetentionDaysChanged(it))
        },
        onClearOldMediaCacheClick = { viewModel.onEvent(SettingsEvent.ClearOldMediaCacheClicked) },
    )
}
