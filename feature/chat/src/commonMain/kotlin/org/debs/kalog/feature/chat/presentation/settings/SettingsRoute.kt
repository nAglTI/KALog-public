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
import org.debs.kalog.feature.chat.localization.chatLocalized
import org.debs.kalog.feature.chat.presentation.koinLifecycleViewModel
import org.debs.kalog.feature.chat.presentation.components.ConfirmActionDialog

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onAppCleared: () -> Unit,
    onAccountImported: () -> Unit,
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
                SettingsEffect.AccountImported -> {
                    onAccountImported()
                }
            }
        }
    }

    if (isClearDataDialogVisible) {
        ConfirmActionDialog(
            title = chatLocalized(
                en = "Clear all app data",
                ru = "Очистить все данные приложения",
            ),
            message = chatLocalized(
                en = "Local chat cache, keys, settings, and secure storage will be deleted. The app will close if possible.",
                ru = "Будут удалены локальный кэш чатов, ключи, настройки и защищённое хранилище. Приложение закроется, если это возможно.",
            ),
            confirmLabel = chatLocalized(en = "Continue", ru = "Продолжить"),
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
        onThemeModeChange = { viewModel.onEvent(SettingsEvent.ThemeModeSelected(it)) },
        onDesktopAutostartToggle = { viewModel.onEvent(SettingsEvent.DesktopAutostartToggled(it)) },
        onClearMediaCacheClick = { viewModel.onEvent(SettingsEvent.ClearMediaCacheClicked) },
        onMediaCacheRetentionDaysChange = {
            viewModel.onEvent(SettingsEvent.MediaCacheRetentionDaysChanged(it))
        },
        onClearOldMediaCacheClick = { viewModel.onEvent(SettingsEvent.ClearOldMediaCacheClicked) },
        onBackupPasswordChange = { viewModel.onEvent(SettingsEvent.BackupPasswordChanged(it)) },
        onBackupPasswordConfirmationChange = {
            viewModel.onEvent(SettingsEvent.BackupPasswordConfirmationChanged(it))
        },
        onExportAccountBackupClick = { viewModel.onEvent(SettingsEvent.ExportAccountBackupClicked) },
        onShareAccountBackupClick = { viewModel.onEvent(SettingsEvent.ShareAccountBackupClicked) },
        onImportBackupPasswordChange = {
            viewModel.onEvent(SettingsEvent.ImportBackupPasswordChanged(it))
        },
        onImportAccountBackupClick = { viewModel.onEvent(SettingsEvent.ImportAccountBackupClicked) },
    )
}
