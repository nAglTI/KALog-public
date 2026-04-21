package org.debs.kalog.feature.chat.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.debs.kalog.feature.chat.domain.usecase.RunChatSyncLoopUseCase

class ChatSessionViewModel(
    private val runChatSyncLoopUseCase: RunChatSyncLoopUseCase,
) : ViewModel() {
    private var syncJob: Job? = null

    init {
        restart()
    }

    fun restart() {
        shutdown()
        syncJob = viewModelScope.launch {
            try {
                runChatSyncLoopUseCase()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // The sync loop already retries transient failures on its own.
            }
        }
    }

    fun shutdown() {
        syncJob?.cancel()
        syncJob = null
    }
}
