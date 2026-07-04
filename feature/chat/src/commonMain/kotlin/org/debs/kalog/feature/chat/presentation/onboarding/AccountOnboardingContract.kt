package org.debs.kalog.feature.chat.presentation.onboarding

import org.debs.kalog.feature.chat.presentation.text.UiText

data class AccountOnboardingUiState(
    val isChecking: Boolean = true,
    val isReady: Boolean = false,
    val importPassword: String = "",
    val isImporting: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null,
)

sealed interface AccountOnboardingEvent {
    data object CreateNewAccountClicked : AccountOnboardingEvent

    data class ImportPasswordChanged(val password: String) : AccountOnboardingEvent

    data object ImportBackupClicked : AccountOnboardingEvent
}
