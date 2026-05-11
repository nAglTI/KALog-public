package org.debs.kalog.feature.chat.presentation.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import org.debs.kalog.feature.chat.data.cache.SecureAndroidAttachmentStore
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
@OptIn(UnstableApi::class)
internal actual fun PlatformVideoPlayer(
    localUri: String,
    modifier: Modifier,
) {
    val player = remember(localUri) {
        val context = AndroidChatPlatformBridge.requireContext()
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(ChatVideoMediaCodecSelector)
        ExoPlayer.Builder(context, renderersFactory).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            val mediaItem = MediaItem.fromUri(Uri.parse(localUri))
            if (SecureAndroidAttachmentStore.isSecureUri(localUri)) {
                setMediaSource(
                    ProgressiveMediaSource.Factory(SecureAttachmentExoDataSourceFactory())
                        .createMediaSource(mediaItem),
                )
            } else {
                setMediaItem(mediaItem)
            }
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player, localUri) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(
                    CHAT_VIDEO_PLAYER_TAG,
                    "state=${playbackState.toPlayerStateName()} playWhenReady=${player.playWhenReady} uri=$localUri",
                )
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(CHAT_VIDEO_PLAYER_TAG, "isPlaying=$isPlaying uri=$localUri")
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(CHAT_VIDEO_PLAYER_TAG, "Playback failed uri=$localUri", error)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
                keepScreenOn = true
                useController = true
                controllerAutoShow = true
                controllerHideOnTouch = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setControllerShowTimeoutMs(3_000)
                setEnableComposeSurfaceSyncWorkaround(true)
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
        },
    )
}

private object ChatVideoMediaCodecSelector : MediaCodecSelector {
    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean,
    ) = if (mimeType == MimeTypes.VIDEO_DOLBY_VISION) {
        Log.d(CHAT_VIDEO_PLAYER_TAG, "Prefer HEVC base-layer fallback for Dolby Vision video.")
        emptyList()
    } else {
        MediaCodecSelector.DEFAULT.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder,
        )
    }
}

private class SecureAttachmentExoDataSourceFactory : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return SecureAttachmentExoDataSource()
    }
}

private class SecureAttachmentExoDataSource : BaseDataSource(false) {
    private var localUri: String? = null
    private var reader: org.debs.kalog.feature.chat.data.cache.SecureAndroidAttachmentReader? = null
    private var position: Long = 0L
    private var bytesRemaining: Long = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        val uriString = dataSpec.uri.toString()
        val entryReader = SecureAndroidAttachmentStore.openReader(uriString)
            ?: throw IllegalStateException("Secure attachment is not registered: $uriString")
        localUri = uriString
        reader = entryReader
        position = dataSpec.position
        val totalSize = SecureAndroidAttachmentStore.sizeBytes(uriString)
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            ((totalSize ?: Long.MAX_VALUE) - position).coerceAtLeast(0L)
        } else {
            dataSpec.length
        }
        transferInitializing(dataSpec)
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val maxRead = minOf(length.toLong(), bytesRemaining).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val read = reader?.readAt(position, buffer, offset, maxRead) ?: return C.RESULT_END_OF_INPUT
        if (read <= 0) return C.RESULT_END_OF_INPUT
        position += read
        if (bytesRemaining != Long.MAX_VALUE) {
            bytesRemaining -= read
        }
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = localUri?.let(Uri::parse)

    override fun close() {
        localUri = null
        reader = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}

private class SecureAttachmentMediaDataSource(
    localUri: String,
) : MediaDataSource() {
    private val reader = SecureAndroidAttachmentStore.openReader(localUri)
    private val sizeBytes = SecureAndroidAttachmentStore.sizeBytes(localUri) ?: 0L

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        return reader?.readAt(position, buffer, offset, size) ?: -1
    }

    override fun getSize(): Long = sizeBytes

    override fun close() = Unit
}

private fun Int.toPlayerStateName(): String {
    return when (this) {
        Player.STATE_IDLE -> "idle"
        Player.STATE_BUFFERING -> "buffering"
        Player.STATE_READY -> "ready"
        Player.STATE_ENDED -> "ended"
        else -> "unknown($this)"
    }
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

    fun requireContext(): Context {
        return checkNotNull(activity) { "Android chat platform is not registered." }
    }

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

        val recordingDirectory = File(currentActivity.noBackupFilesDir, "kalog-active-recordings").apply { mkdirs() }
        val file = File(recordingDirectory, "kalog-voice-${UUID.randomUUID()}.m4a")
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
                if (SecureAndroidAttachmentStore.isSecureUri(localUri)) {
                    setDataSource(SecureAttachmentMediaDataSource(localUri))
                } else {
                    setDataSource(currentActivity, Uri.parse(localUri))
                }
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
            val decodedSourceBytes = contentBytes ?: localUri
                ?.takeIf { uri -> SecureAndroidAttachmentStore.isSecureUri(uri) }
                ?.let(SecureAndroidAttachmentStore::readAll)
            val decodedSourceUri = localUri.takeIf { decodedSourceBytes == null }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            decodeImage(decodedSourceBytes, decodedSourceUri, currentActivity, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateBitmapSampleSize(bounds.outWidth, bounds.outHeight, safeMaxSide)
            }
            val decoded = decodeImage(decodedSourceBytes, decodedSourceUri, currentActivity, decodeOptions) ?: return@runCatching null
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
                if (SecureAndroidAttachmentStore.isSecureUri(localUri)) {
                    retriever.setDataSource(SecureAttachmentMediaDataSource(localUri))
                } else {
                    retriever.setDataSource(currentActivity, Uri.parse(localUri))
                }
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
        val bytes = file.readBytes()
        file.delete()
        val durationMillis = (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L)
        return VoiceRecordingResult.Finished(
            ChatAttachment(
                id = UUID.randomUUID().toString(),
                kind = ChatAttachmentKind.Voice,
                name = "voice.m4a",
                mimeType = "audio/mp4",
                sizeBytes = bytes.size.toLong(),
                localUri = null,
                contentBytes = bytes,
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
    runCatching {
        contentResolver.takePersistableUriPermission(this, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val fileSize = metadata?.sizeBytes
    val contentBytes = fileSize
        ?.takeIf { size -> size in 1..INLINE_ATTACHMENT_BYTES_LIMIT }
        ?.let {
            contentResolver.openInputStream(this)?.use { input -> input.readBytes() }
        }

    return ChatAttachment(
        id = UUID.randomUUID().toString(),
        kind = mimeType.toAttachmentKind(),
        name = fileName,
        mimeType = mimeType,
        sizeBytes = fileSize ?: contentBytes?.size?.toLong(),
        localUri = toString(),
        contentBytes = contentBytes,
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
    if (SecureAndroidAttachmentStore.isSecureUri(localUri)) {
        val bytes = SecureAndroidAttachmentStore.readAll(localUri) ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
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
    return ChatAttachment(
        id = UUID.randomUUID().toString(),
        kind = ChatAttachmentKind.Image,
        name = "photo.jpg",
        mimeType = "image/jpeg",
        sizeBytes = bytes.size.toLong(),
        localUri = null,
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

private const val INLINE_ATTACHMENT_BYTES_LIMIT = 16L * 1024L * 1024L
private const val CHAT_VIDEO_PLAYER_TAG = "KALogVideoPlayer"
