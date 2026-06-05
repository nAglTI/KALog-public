package org.debs.kalog.feature.chat.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import org.debs.kalog.feature.chat.domain.model.ChatAttachment

internal actual val hasSoftwareKeyboard: Boolean = true

@Composable
actual fun ConfigureSystemBars(fullScreenMediaVisible: Boolean) = Unit

internal actual suspend fun pickFileAttachment(): ChatAttachment? = null

internal actual suspend fun pickImageAttachments(): List<ChatAttachment> = emptyList()

internal actual suspend fun takePhotoAttachment(): ChatAttachment? = null

internal actual fun readClipboardAttachments(): List<ChatAttachment> = emptyList()

internal actual fun readDroppedAttachments(event: DragAndDropEvent): List<ChatAttachment> = emptyList()

internal actual fun openLocalAttachment(localUri: String): Boolean = false

internal actual fun playLocalAudio(
    localUri: String,
    onProgress: (positionMillis: Long, durationMillis: Long) -> Unit,
    onFinished: () -> Unit,
): Boolean = false

internal actual fun stopLocalAudio() = Unit

internal actual fun seekLocalAudio(positionMillis: Long): Boolean = false

internal actual fun loadImagePreview(
    localUri: String?,
    contentBytes: ByteArray?,
    maxSidePx: Int,
): ByteArray? = contentBytes

internal actual fun loadVideoThumbnail(localUri: String, maxSidePx: Int): ByteArray? = null

@Composable
internal actual fun PlatformVideoPlayer(
    localUri: String,
    fileName: String?,
    mimeType: String?,
    modifier: Modifier,
) = Unit

internal actual suspend fun toggleVoiceRecording(): VoiceRecordingResult = VoiceRecordingResult.Unavailable
