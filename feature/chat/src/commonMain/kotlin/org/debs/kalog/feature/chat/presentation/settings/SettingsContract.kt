package org.debs.kalog.feature.chat.presentation.settings

data class SettingsUiState(
    val nickname: String = "",
    val savedNickname: String = "",
    val currentUserId: String = "",
    val isClearingData: Boolean = false,
    val debugMode: Boolean = false,
    val nicknameSaved: Boolean = false,
    val nicknameSaveError: Boolean = false,
) {
    val isNicknameChanged: Boolean
        get() = nickname.trim() != savedNickname
}

sealed interface SettingsEvent {
    data class NicknameChanged(val nickname: String) : SettingsEvent

    data object SaveNicknameClicked : SettingsEvent

    data object CopyUserIdClicked : SettingsEvent

    data object ClearAllDataConfirmed : SettingsEvent

    data class DebugModeToggled(val enabled: Boolean) : SettingsEvent
}

sealed interface SettingsEffect {
    data class CopyUserId(val userId: String) : SettingsEffect

    data object AppDataCleared : SettingsEffect
}
