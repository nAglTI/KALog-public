package org.debs.kalog.feature.chat.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import org.debs.kalog.feature.chat.domain.model.ChatAttachment

internal expect val hasSoftwareKeyboard: Boolean

@Composable
expect fun ConfigureSystemBars(fullScreenMediaVisible: Boolean)

internal expect suspend fun pickFileAttachment(): ChatAttachment?

internal expect suspend fun pickImageAttachments(): List<ChatAttachment>

internal expect suspend fun takePhotoAttachment(): ChatAttachment?

internal expect fun readClipboardAttachments(): List<ChatAttachment>

internal expect fun readDroppedAttachments(event: DragAndDropEvent): List<ChatAttachment>

internal expect fun openLocalAttachment(localUri: String): Boolean

internal expect fun playLocalAudio(
    localUri: String,
    onProgress: (positionMillis: Long, durationMillis: Long) -> Unit,
    onFinished: () -> Unit,
): Boolean

internal expect fun stopLocalAudio()

internal expect fun seekLocalAudio(positionMillis: Long): Boolean

internal expect fun loadImagePreview(
    localUri: String?,
    contentBytes: ByteArray?,
    maxSidePx: Int,
): ByteArray?

internal expect fun loadVideoThumbnail(localUri: String, maxSidePx: Int): ByteArray?

@Composable
internal expect fun PlatformVideoPlayer(
    localUri: String,
    fileName: String?,
    mimeType: String?,
    modifier: Modifier = Modifier,
)

internal sealed interface VoiceRecordingResult {
    data object Started : VoiceRecordingResult

    data class Finished(val attachment: ChatAttachment) : VoiceRecordingResult

    data object TooShort : VoiceRecordingResult

    data object PermissionDenied : VoiceRecordingResult

    data object Unavailable : VoiceRecordingResult
}

internal expect suspend fun toggleVoiceRecording(): VoiceRecordingResult
