package org.debs.kalog.feature.chat.presentation.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import org.debs.kalog.feature.chat.domain.model.ChatAttachment
import org.debs.kalog.feature.chat.domain.model.ChatAttachmentKind

internal actual val hasSoftwareKeyboard: Boolean = true

internal actual suspend fun pickFileAttachment(): ChatAttachment? = AndroidChatPlatformBridge.pickFileAttachment()

internal actual suspend fun pickImageAttachments(): List<ChatAttachment> = AndroidChatPlatformBridge.pickImageAttachments()

internal actual suspend fun takePhotoAttachment(): ChatAttachment? = AndroidChatPlatformBridge.takePhotoAttachment()

internal actual fun readClipboardAttachments(): List<ChatAttachment> = emptyList()

internal actual fun readDroppedAttachments(event: DragAndDropEvent): List<ChatAttachment> = emptyList()

internal actual fun openLocalAttachment(localUri: String): Boolean = false

internal actual fun playLocalAudio(
    localUri: String,
    onProgress: (positionMillis: Long, durationMillis: Long) -> Unit,
    onFinished: () -> Unit,
): Boolean {
    return AndroidChatPlatformBridge.playLocalAudio(localUri, onProgress, onFinished)
}

internal actual fun stopLocalAudio() {
    AndroidChatPlatformBridge.stopLocalAudio()
}

internal actual fun seekLocalAudio(positionMillis: Long): Boolean {
    return AndroidChatPlatformBridge.seekLocalAudio(positionMillis)
}

internal actual fun loadImagePreview(
    localUri: String?,
    contentBytes: ByteArray?,
    maxSidePx: Int,
): ByteArray? {
    return AndroidChatPlatformBridge.loadImagePreview(localUri, contentBytes, maxSidePx)
}

internal actual fun loadVideoThumbnail(localUri: String, maxSidePx: Int): ByteArray? {
    return AndroidChatPlatformBridge.loadVideoThumbnail(localUri, maxSidePx)
}

@Composable
internal actual fun PlatformVideoPlayer(
    localUri: String,
    modifier: Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            VideoView(context).apply {
                val controller = MediaController(context)
                controller.setAnchorView(this)
                setMediaController(controller)
                setVideoURI(Uri.parse(localUri))
                setOnPreparedListener { player ->
                    player.isLooping = false
                    start()
                }
            }
        },
        update = { view ->
            if (view.tag != localUri) {
                view.tag = localUri
                view.setVideoURI(Uri.parse(localUri))
                view.start()
            }
        },
    )
}

internal actual suspend fun toggleVoiceRecording(): VoiceRecordingResult {
    return AndroidChatPlatformBridge.toggleVoiceRecording()
}

object AndroidChatPlatformBridge {
    private var activity: ComponentActivity? = null
    private var filePickerLauncher: ActivityResultLauncher<Array<String>>? = null
    private var imagePickerLauncher: ActivityResultLauncher<PickVisualMediaRequest>? = null
    private var cameraPreviewLauncher: ActivityResultLauncher<Void?>? = null
    private var microphonePermissionLauncher: ActivityResultLauncher<String>? = null
    private var cameraPermissionLauncher: ActivityResultLauncher<String>? = null
    private var pendingPickedFile = CompletableDeferred<ChatAttachment?>()
    private var pendingPickedImages = CompletableDeferred<List<ChatAttachment>>()
    private var pendingPhoto = CompletableDeferred<ChatAttachment?>()
    private var pendingMicrophonePermission = CompletableDeferred<Boolean>()
    private var pendingCameraPermission = CompletableDeferred<Boolean>()
    private var activeRecorder: ActiveVoiceRecorder? = null
    private var activeAudioPlayer: MediaPlayer? = null
    private val audioProgressHandler = Handler(Looper.getMainLooper())
    private var audioProgressRunnable: Runnable? = null

    fun register(activity: ComponentActivity) {
        this.activity = activity
        filePickerLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            Thread {
                pendingPickedFile.complete(uri?.toChatAttachment(activity))
            }.start()
        }
        imagePickerLauncher = activity.registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            Thread {
                pendingPickedImages.complete(uris.mapNotNull { uri -> uri.toChatAttachment(activity) })
            }.start()
        }
        cameraPreviewLauncher = activity.registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            pendingPhoto.complete(bitmap?.toPhotoAttachment(activity))
        }
        microphonePermissionLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingMicrophonePermission.complete(granted)
        }
        cameraPermissionLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingCameraPermission.complete(granted)
        }
    }

    internal suspend fun pickFileAttachment(): ChatAttachment? {
        val launcher = filePickerLauncher ?: return null
        pendingPickedFile = CompletableDeferred()
        launcher.launch(arrayOf("*/*"))
        return pendingPickedFile.await()
    }

    internal suspend fun pickImageAttachments(): List<ChatAttachment> {
        val launcher = imagePickerLauncher ?: return emptyList()
        pendingPickedImages = CompletableDeferred()
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        return pendingPickedImages.await()
    }

    internal suspend fun takePhotoAttachment(): ChatAttachment? {
        val currentActivity = activity ?: return null
        if (!ensureCameraPermission(currentActivity)) return null
        val launcher = cameraPreviewLauncher ?: return null
        pendingPhoto = CompletableDeferred()
        launcher.launch(null)
        return pendingPhoto.await()
    }

    internal suspend fun toggleVoiceRecording(): VoiceRecordingResult {
        val currentActivity = activity ?: return VoiceRecordingResult.Unavailable
        activeRecorder?.let { recorder ->
            activeRecorder = null
            return recorder.stop()
        }

        if (!ensureMicrophonePermission(currentActivity)) {
            return VoiceRecordingResult.PermissionDenied
        }

        val file = File(currentActivity.cacheDir, "kalog-voice-${UUID.randomUUID()}.m4a")
        val recorder = runCatching {
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(currentActivity)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
            ActiveVoiceRecorder(file, mediaRecorder, startedAtMillis = System.currentTimeMillis())
        }.getOrNull() ?: return VoiceRecordingResult.Unavailable

        activeRecorder = recorder
        return VoiceRecordingResult.Started
    }

    internal fun playLocalAudio(
        localUri: String,
        onProgress: (positionMillis: Long, durationMillis: Long) -> Unit,
        onFinished: () -> Unit,
    ): Boolean {
        val currentActivity = activity ?: return false
        stopLocalAudio()

        val player = runCatching {
            MediaPlayer().apply {
                setDataSource(currentActivity, Uri.parse(localUri))
                setOnCompletionListener { completedPlayer ->
                    publishFinalAudioProgress(completedPlayer, onProgress)
                    releaseAudioPlayer(completedPlayer)
                    onFinished()
                }
                setOnErrorListener { failedPlayer, _, _ ->
                    releaseAudioPlayer(failedPlayer)
                    onFinished()
                    true
                }
                prepare()
                start()
            }
        }.getOrNull() ?: return false

        activeAudioPlayer = player
        startAudioProgress(player, onProgress)
        return true
    }

    internal fun stopLocalAudio() {
        activeAudioPlayer?.let { player ->
            releaseAudioPlayer(player)
        }
    }

    internal fun seekLocalAudio(positionMillis: Long): Boolean {
        val player = activeAudioPlayer ?: return false
        return runCatching {
            val target = positionMillis.coerceAtLeast(0L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                player.seekTo(target, MediaPlayer.SEEK_CLOSEST)
            } else {
                @Suppress("DEPRECATION")
                player.seekTo(target.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            }
            true
        }.getOrDefault(false)
    }

    internal fun loadImagePreview(
        localUri: String?,
        contentBytes: ByteArray?,
        maxSidePx: Int,
    ): ByteArray? {
        val currentActivity = activity
        val safeMaxSide = maxSidePx.coerceAtLeast(128)
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            decodeImage(contentBytes, localUri, currentActivity, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateBitmapSampleSize(bounds.outWidth, bounds.outHeight, safeMaxSide)
            }
            val decoded = decodeImage(contentBytes, localUri, currentActivity, decodeOptions) ?: return@runCatching null
            val preview = decoded.scaleToMaxSide(safeMaxSide)
            ByteArrayOutputStream().use { stream ->
                preview.compress(Bitmap.CompressFormat.JPEG, 84, stream)
                if (preview != decoded) preview.recycle()
                decoded.recycle()
                stream.toByteArray()
            }
        }.getOrNull()
    }

    internal fun loadVideoThumbnail(localUri: String, maxSidePx: Int): ByteArray? {
        val currentActivity = activity ?: return null
        val safeMaxSide = maxSidePx.coerceAtLeast(128)
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(currentActivity, Uri.parse(localUri))
                val targetSize = retriever.scaledVideoFrameSize(safeMaxSide)
                val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        0L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        targetSize.first,
                        targetSize.second,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } ?: return@runCatching null
                val bitmap = decoded.scaleToMaxSide(safeMaxSide)
                ByteArrayOutputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream)
                    if (bitmap != decoded) bitmap.recycle()
                    decoded.recycle()
                    stream.toByteArray()
                }
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }

    private suspend fun ensureMicrophonePermission(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        val launcher = microphonePermissionLauncher ?: return false
        pendingMicrophonePermission = CompletableDeferred()
        launcher.launch(Manifest.permission.RECORD_AUDIO)
        return pendingMicrophonePermission.await()
    }

    private suspend fun ensureCameraPermission(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        val launcher = cameraPermissionLauncher ?: return false
        pendingCameraPermission = CompletableDeferred()
        launcher.launch(Manifest.permission.CAMERA)
        return pendingCameraPermission.await()
    }

    private fun startAudioProgress(
        player: MediaPlayer,
        onProgress: (positionMillis: Long, durationMillis: Long) -> Unit,
    ) {
        val runnable = object : Runnable {
            override fun run() {
                if (activeAudioPlayer != player) return
                val positionMillis = runCatching { player.currentPosition.toLong() }.getOrDefault(0L)
                val durationMillis = runCatching { player.duration.toLong() }.getOrDefault(0L)
                onProgress(positionMillis, durationMillis)
                audioProgressHandler.postDelayed(this, 150L)
            }
        }
        audioProgressRunnable = runnable
        audioProgressHandler.post(runnable)
    }

    private fun publishFinalAudioProgress(
        player: MediaPlayer,
        onProgress: (positionMillis: Long, durationMillis: Long) -> Unit,
    ) {
        val durationMillis = runCatching { player.duration.toLong() }.getOrDefault(0L)
        if (durationMillis > 0L) {
            onProgress(durationMillis, durationMillis)
        }
    }

    private fun releaseAudioPlayer(player: MediaPlayer) {
        audioProgressRunnable?.let(audioProgressHandler::removeCallbacks)
        audioProgressRunnable = null
        if (activeAudioPlayer == player) {
            activeAudioPlayer = null
        }
        runCatching {
            if (player.isPlaying) {
                player.stop()
            }
        }
        runCatching { player.release() }
    }
}

private class ActiveVoiceRecorder(
    private val file: File,
    private val recorder: MediaRecorder,
    private val startedAtMillis: Long,
) {
    fun stop(): VoiceRecordingResult {
        runCatching { recorder.stop() }
        runCatching { recorder.release() }
        if (!file.isFile || file.length() == 0L) {
            file.delete()
            return VoiceRecordingResult.Unavailable
        }
        val durationMillis = (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L)
        return VoiceRecordingResult.Finished(
            ChatAttachment(
                id = UUID.randomUUID().toString(),
                kind = ChatAttachmentKind.Voice,
                name = "voice.m4a",
                mimeType = "audio/mp4",
                sizeBytes = file.length(),
                localUri = file.toURI().toString(),
                contentBytes = file.readBytes(),
                durationMillis = durationMillis,
            ),
        )
    }
}

private fun Uri.toChatAttachment(context: Context): ChatAttachment? {
    val contentResolver = context.contentResolver
    val mimeType = contentResolver.getType(this)
    val metadata = contentResolver.query(this, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            AttachmentMetadata(
                name = nameIndex.takeIf { it >= 0 }?.let(cursor::getString),
                sizeBytes = sizeIndex.takeIf { it >= 0 }?.let(cursor::getLong),
            )
        } else {
            null
        }
    }
    val fileName = metadata?.name ?: lastPathSegment ?: "attachment"
    val stagedFile = File(
        context.cacheDir,
        "kalog-attachment-${UUID.randomUUID()}${fileName.extensionOrEmpty(mimeType)}",
    )
    contentResolver.openInputStream(this)?.use { input ->
        stagedFile.outputStream().use { output ->
            input.copyTo(output)
        }
    } ?: return null
    if (!stagedFile.isFile || stagedFile.length() == 0L) return null

    val fileSize = stagedFile.length()

    return ChatAttachment(
        id = UUID.randomUUID().toString(),
        kind = mimeType.toAttachmentKind(),
        name = fileName,
        mimeType = mimeType,
        sizeBytes = metadata?.sizeBytes ?: fileSize,
        localUri = stagedFile.toURI().toString(),
        contentBytes = if (fileSize <= INLINE_ATTACHMENT_BYTES_LIMIT) stagedFile.readBytes() else null,
    )
}

private fun decodeImage(
    contentBytes: ByteArray?,
    localUri: String?,
    context: Context?,
    options: BitmapFactory.Options,
): Bitmap? {
    if (contentBytes != null) {
        return BitmapFactory.decodeByteArray(contentBytes, 0, contentBytes.size, options)
    }
    if (localUri == null) return null
    val uri = Uri.parse(localUri)
    if (uri.scheme == "file") {
        return BitmapFactory.decodeFile(File(URI(localUri)).absolutePath, options)
    }
    val currentContext = context ?: return null
    return currentContext.contentResolver.openInputStream(uri)?.use { input ->
        input.decodeBitmap(options)
    }
}

private fun InputStream.decodeBitmap(options: BitmapFactory.Options): Bitmap? {
    return BitmapFactory.decodeStream(this, null, options)
}

private fun calculateBitmapSampleSize(
    width: Int,
    height: Int,
    maxSidePx: Int,
): Int {
    var sampleSize = 1
    while (width / sampleSize > maxSidePx * 2 || height / sampleSize > maxSidePx * 2) {
        sampleSize *= 2
    }
    return sampleSize
}

private fun MediaMetadataRetriever.scaledVideoFrameSize(maxSidePx: Int): Pair<Int, Int> {
    var width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
    var height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
    val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
    if (rotation == 90 || rotation == 270) {
        val originalWidth = width
        width = height
        height = originalWidth
    }
    if (width <= 0 || height <= 0) return maxSidePx to maxSidePx
    val scale = (maxSidePx.toFloat() / maxOf(width, height).toFloat()).coerceAtMost(1f)
    return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
}

private fun Bitmap.scaleToMaxSide(maxSidePx: Int): Bitmap {
    val largestSide = maxOf(width, height)
    if (largestSide <= maxSidePx) return this
    val scale = maxSidePx.toFloat() / largestSide.toFloat()
    val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
    val scaledHeight = (height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true)
}

private fun Bitmap.toPhotoAttachment(context: Context): ChatAttachment? {
    val bytes = ByteArrayOutputStream().use { stream ->
        compress(Bitmap.CompressFormat.JPEG, 92, stream)
        stream.toByteArray()
    }
    if (bytes.isEmpty()) return null
    val file = File(context.cacheDir, "kalog-photo-${UUID.randomUUID()}.jpg")
    file.writeBytes(bytes)
    return ChatAttachment(
        id = UUID.randomUUID().toString(),
        kind = ChatAttachmentKind.Image,
        name = "photo.jpg",
        mimeType = "image/jpeg",
        sizeBytes = bytes.size.toLong(),
        localUri = file.toURI().toString(),
        contentBytes = bytes,
    )
}

private data class AttachmentMetadata(
    val name: String?,
    val sizeBytes: Long?,
)

private fun String?.toAttachmentKind(): ChatAttachmentKind {
    return when {
        this?.startsWith("image/") == true -> ChatAttachmentKind.Image
        this?.startsWith("video/") == true -> ChatAttachmentKind.Video
        this?.startsWith("audio/") == true -> ChatAttachmentKind.Audio
        else -> ChatAttachmentKind.File
    }
}

private fun String?.extensionOrEmpty(mimeType: String?): String {
    val explicitExtension = this
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.takeIf { extension -> extension.isNotBlank() && extension.length <= 8 }
    if (explicitExtension != null) return ".$explicitExtension"

    return when (mimeType?.lowercase()) {
        "image/jpeg" -> ".jpg"
        "image/png" -> ".png"
        "image/gif" -> ".gif"
        "image/webp" -> ".webp"
        "video/mp4" -> ".mp4"
        "video/webm" -> ".webm"
        "audio/mpeg" -> ".mp3"
        "audio/mp4" -> ".m4a"
        "audio/ogg" -> ".ogg"
        else -> ""
    }
}

private const val INLINE_ATTACHMENT_BYTES_LIMIT = 16L * 1024L * 1024L
