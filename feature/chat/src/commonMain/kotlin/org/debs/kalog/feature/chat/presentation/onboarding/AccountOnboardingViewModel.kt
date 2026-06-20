package org.debs.kalog.feature.chat.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.debs.kalog.feature.chat.data.account.AccountBackupManager
import org.debs.kalog.feature.chat.data.crypto.ChatKeyStore
import org.debs.kalog.feature.chat.data.preferences.ChatPreferencesDataSource
import org.debs.kalog.feature.chat.domain.usecase.ClearAllChatDataUseCase
import org.debs.kalog.feature.chat.domain.usecase.GetCurrentUserIdUseCase
import org.debs.kalog.feature.chat.presentation.text.uiText
import mayday_chat.feature.chat.generated.resources.Res
import mayday_chat.feature.chat.generated.resources.*

class AccountOnboardingViewModel(
    private val chatPreferencesDataSource: ChatPreferencesDataSource,
    private val accountBackupManager: AccountBackupManager,
    private val clearAllChatDataUseCase: ClearAllChatDataUseCase,
    private val getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    private val chatKeyStore: ChatKeyStore,
) : ViewModel() {
    private val _state = MutableStateFlow(AccountOnboardingUiState())
    val state = _state.asStateFlow()

    init {
        checkAccount()
    }

    fun onEvent(event: AccountOnboardingEvent) {
        when (event) {
            AccountOnboardingEvent.CreateNewAccountClicked -> createNewAccount()
            AccountOnboardingEvent.ImportBackupClicked -> importBackup()
            is AccountOnboardingEvent.ImportPasswordChanged -> {
                _state.update {
                    it.copy(
                        importPassword = event.password,
                        error = null,
                        message = null,
                    )
                }
            }
        }
    }

    private fun checkAccount() {
        viewModelScope.launch {
            try {
                val completed = chatPreferencesDataSource.isAccountOnboardingCompleted()
                val hasLocalAccount = completed && hasLocalAccount()
                if (completed && !hasLocalAccount) {
                    chatPreferencesDataSource.setAccountOnboardingCompleted(false)
                }
                _state.update {
                    it.copy(
                        isChecking = false,
                        isReady = completed && hasLocalAccount,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _state.update { it.copy(isChecking = false, isReady = false) }
            }
        }
    }

    private fun createNewAccount() {
        viewModelScope.launch {
            try {
                clearAllChatDataUseCase()
                val userId = getCurrentUserIdUseCase()?.ifBlank { null }
                check(userId != null && hasLocalAccount())
                chatPreferencesDataSource.setAccountOnboardingCompleted(true)
                _state.update { it.copy(isReady = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        error = uiText(Res.string.could_not_prepare_new_account),
                    )
                }
            }
        }
    }

    private fun importBackup() {
        val password = state.value.importPassword
        if (password.isBlank() || state.value.isImporting) return

        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, error = null, message = null) }
            try {
                val result = accountBackupManager.importAccountFromPicker(password)
                if (result == null) {
                    _state.update {
                        it.copy(
                            isImporting = false,
                            message = uiText(Res.string.import_cancelled),
                        )
                    }
                    return@launch
                }
                chatPreferencesDataSource.setAccountOnboardingCompleted(true)
                _state.update {
                    it.copy(
                        isImporting = false,
                        isReady = true,
                        message = if (result.deviceBoundPrivateKeyCount > 0) {
                            uiText(Res.string.imported_account_with_device_bound_warning, result.userId)
                        } else {
                            uiText(Res.string.imported_account, result.userId)
                        },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        isImporting = false,
                        error = importBackupFailedMessage(),
                    )
                }
            }
        }
    }

    private suspend fun hasLocalAccount(): Boolean {
        val userId = chatKeyStore.currentUserId()?.ifBlank { null } ?: return false
        val publicKey = chatKeyStore.currentUserPublicKey()?.ifBlank { null } ?: return false
        val privateKeyRef = chatKeyStore.currentUserPrivateKeyRef() ?: return false
        return userId.isNotBlank() && publicKey.isNotBlank() && privateKeyRef.serialize().isNotBlank()
    }

    private companion object {
        private fun importBackupFailedMessage() = uiText(Res.string.could_not_import_backup)
    }
}
