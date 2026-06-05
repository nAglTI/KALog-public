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
import android.media.MicrophoneDirection
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import android.view.Window
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
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
import org.debs.kalog.feature.chat.data.account.ACCOUNT_BACKUP_MIME_TYPE
import org.debs.kalog.feature.chat.data.account.AccountBackupFileRef
import org.debs.kalog.feature.chat.data.cache.SecureAndroidAttachmentStore
import org.debs.kalog.feature.chat.domain.model.ChatAttachment
import org.debs.kalog.feature.chat.domain.model.ChatAttachmentKind

internal actual val hasSoftwareKeyboard: Boolean = true

@Composable
actual fun ConfigureSystemBars(fullScreenMediaVisible: Boolean) {
    val view = LocalView.current
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    DisposableEffect(view, fullScreenMediaVisible, darkTheme) {
        val window = AndroidChatPlatformBridge.currentWindow()
        if (window != null) {
            configureSystemBars(window, fullScreenMediaVisible, darkTheme)
        }
        onDispose {
            window?.let { configureSystemBars(it, fullScreenMediaVisible = false, darkTheme = darkTheme) }
        }
    }
}

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
    fileName: String?,
    mimeType: String?,
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

@Suppress("DEPRECATION")
private fun configureSystemBars(
    window: Window,
    fullScreenMediaVisible: Boolean,
    darkTheme: Boolean,
) {
    val useLightSystemBarAppearance = !fullScreenMediaVisible && !darkTheme
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.isAppearanceLightStatusBars = useLightSystemBarAppearance
    controller.isAppearanceLightNavigationBars = useLightSystemBarAppearance
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isStatusBarContrastEnforced = !fullScreenMediaVisible
        window.isNavigationBarContrastEnforced = !fullScreenMediaVisible
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
    private var accountBackupPickerLauncher: ActivityResultLauncher<Array<String>>? = null
    private var pendingPickedFile = CompletableDeferred<ChatAttachment?>()
    private var pendingPickedImages = CompletableDeferred<List<ChatAttachment>>()
    private var pendingPhoto = CompletableDeferred<ChatAttachment?>()
    private var pendingMicrophonePermission = CompletableDeferred<Boolean>()
    private var pendingCameraPermission = CompletableDeferred<Boolean>()
    private var pendingAccountBackupBytes = CompletableDeferred<ByteArray?>()
    private var activeRecorder: ActiveVoiceRecorder? = null
    private var activeAudioPlayer: MediaPlayer? = null
    private val audioProgressHandler = Handler(Looper.getMainLooper())
    private var audioProgressRunnable: Runnable? = null

    fun requireContext(): Context {
        return checkNotNull(activity) { "Android chat platform is not registered." }
    }

    fun currentWindow(): Window? = activity?.window

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
        accountBackupPickerLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            Thread {
                val bytes = uri?.let { selectedUri ->
                    runCatching {
                        activity.contentResolver.openInputStream(selectedUri)?.use { input -> input.readBytes() }
                    }.getOrNull()
                }
                pendingAccountBackupBytes.complete(bytes)
            }.start()
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
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
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
        val recorder = createVoiceRecorder(currentActivity, file) ?: return VoiceRecordingResult.Unavailable

        activeRecorder = recorder
        return VoiceRecordingResult.Started
    }

    internal suspend fun saveAccountBackupFile(fileName: String, bytes: ByteArray): AccountBackupFileRef? {
        val currentActivity = activity ?: return null
        val directory = File(currentActivity.cacheDir, "account-backups").apply { mkdirs() }
        val file = File(directory, fileName)
        return runCatching {
            file.writeBytes(bytes)
            AccountBackupFileRef(fileName = file.name, platformRef = file.absolutePath)
        }.getOrNull()
    }

    internal suspend fun shareAccountBackupFile(file: AccountBackupFileRef): Boolean {
        val currentActivity = activity ?: return false
        val backupFile = File(file.platformRef)
        if (!backupFile.isFile) return false
        val uri = FileProvider.getUriForFile(
            currentActivity,
            "${currentActivity.packageName}.fileprovider",
            backupFile,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = ACCOUNT_BACKUP_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            currentActivity.startActivity(Intent.createChooser(intent, "Share account backup"))
            true
        }.getOrDefault(false)
    }

    internal suspend fun pickAccountBackupFileBytes(): ByteArray? {
        val launcher = accountBackupPickerLauncher ?: return null
        pendingAccountBackupBytes = CompletableDeferred()
        launcher.launch(arrayOf(ACCOUNT_BACKUP_MIME_TYPE, "*/*"))
        return pendingAccountBackupBytes.await()
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

    private fun createVoiceRecorder(
        context: Context,
        file: File,
    ): ActiveVoiceRecorder? {
        VOICE_RECORDING_CONFIGS.forEach { config ->
            file.delete()
            val state = VoiceRecorderRuntimeState(config)
            val mediaRecorder = createAndroidMediaRecorder(context)
            val started = runCatching {
                mediaRecorder.setAudioSource(config.audioSource)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    mediaRecorder.setPrivacySensitive(true)
                }
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                mediaRecorder.setAudioChannels(VOICE_RECORDING_CHANNELS)
                mediaRecorder.setAudioSamplingRate(config.sampleRateHz)
                mediaRecorder.setAudioEncodingBitRate(config.bitRateBps)
                mediaRecorder.setMaxDuration(MAX_VOICE_RECORDING_MILLIS)
                mediaRecorder.setMaxFileSize(MAX_VOICE_RECORDING_BYTES)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    mediaRecorder.setPreferredMicrophoneDirection(MicrophoneDirection.MIC_DIRECTION_TOWARDS_USER)
                }
                mediaRecorder.setOnErrorListener { _, what, extra ->
                    state.failed = true
                    Log.w(VOICE_RECORDER_TAG, "Recorder error source=${config.label} what=$what extra=$extra")
                }
                mediaRecorder.setOnInfoListener { _, what, extra ->
                    if (
                        what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                            what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED
                    ) {
                        state.limitReached = true
                    }
                    Log.d(VOICE_RECORDER_TAG, "Recorder info source=${config.label} what=$what extra=$extra")
                }
                mediaRecorder.setOutputFile(file.absolutePath)
                mediaRecorder.prepare()
                mediaRecorder.start()
            }.onFailure { error ->
                Log.w(VOICE_RECORDER_TAG, "Failed to start recorder with ${config.label}", error)
                runCatching { mediaRecorder.release() }
                file.delete()
            }.isSuccess

            if (started) {
                Log.d(VOICE_RECORDER_TAG, "Started voice recorder with ${config.label}")
                return ActiveVoiceRecorder(
                    file = file,
                    recorder = mediaRecorder,
                    state = state,
                    startedAtElapsedMillis = SystemClock.elapsedRealtime(),
                )
            }
        }
        return null
    }

    private fun createAndroidMediaRecorder(context: Context): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }
}

private class ActiveVoiceRecorder(
    private val file: File,
    private val recorder: MediaRecorder,
    private val state: VoiceRecorderRuntimeState,
    private val startedAtElapsedMillis: Long,
) {
    fun stop(): VoiceRecordingResult {
        val stopped = runCatching { recorder.stop() }
            .onFailure { error ->
                state.failed = true
                Log.w(VOICE_RECORDER_TAG, "Failed to stop recorder source=${state.config.label}", error)
            }
            .isSuccess
        runCatching { recorder.release() }
        if (!file.isFile || file.length() < MIN_VOICE_RECORDING_BYTES) {
            file.delete()
            return if (stopped) VoiceRecordingResult.TooShort else VoiceRecordingResult.Unavailable
        }
        val elapsedMillis = (SystemClock.elapsedRealtime() - startedAtElapsedMillis).coerceAtLeast(0L)
        val durationMillis = file.audioDurationMillis() ?: elapsedMillis
        if (durationMillis < MIN_VOICE_RECORDING_MILLIS) {
            file.delete()
            return VoiceRecordingResult.TooShort
        }
        if (!stopped && durationMillis <= 0L) {
            file.delete()
            return VoiceRecordingResult.Unavailable
        }
        val bytes = runCatching { file.readBytes() }
            .getOrElse {
                file.delete()
                return VoiceRecordingResult.Unavailable
            }
        file.delete()
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

private data class VoiceRecorderConfig(
    val audioSource: Int,
    val sampleRateHz: Int,
    val bitRateBps: Int,
    val label: String,
)

private class VoiceRecorderRuntimeState(
    val config: VoiceRecorderConfig,
) {
    @Volatile
    var failed: Boolean = false

    @Volatile
    var limitReached: Boolean = false
}

private fun File.audioDurationMillis(): Long? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(absolutePath)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { retriever.release() }
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
private const val VOICE_RECORDER_TAG = "KALogVoiceRecorder"
private const val VOICE_RECORDING_CHANNELS = 1
private const val VOICE_RECORDING_BIT_RATE_BPS = 64_000
private const val MIN_VOICE_RECORDING_MILLIS = 700L
private const val MIN_VOICE_RECORDING_BYTES = 1_024L
private const val MAX_VOICE_RECORDING_MILLIS = 5 * 60 * 1000
private const val MAX_VOICE_RECORDING_BYTES = 12L * 1024L * 1024L
private val VOICE_RECORDING_CONFIGS = listOf(
    VoiceRecorderConfig(
        audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        sampleRateHz = 48_000,
        bitRateBps = VOICE_RECORDING_BIT_RATE_BPS,
        label = "voice_communication/48k",
    ),
    VoiceRecorderConfig(
        audioSource = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        sampleRateHz = 44_100,
        bitRateBps = VOICE_RECORDING_BIT_RATE_BPS,
        label = "voice_communication/44.1k",
    ),
    VoiceRecorderConfig(
        audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION,
        sampleRateHz = 48_000,
        bitRateBps = VOICE_RECORDING_BIT_RATE_BPS,
        label = "voice_recognition/48k",
    ),
    VoiceRecorderConfig(
        audioSource = MediaRecorder.AudioSource.MIC,
        sampleRateHz = 48_000,
        bitRateBps = VOICE_RECORDING_BIT_RATE_BPS,
        label = "mic/48k",
    ),
    VoiceRecorderConfig(
        audioSource = MediaRecorder.AudioSource.MIC,
        sampleRateHz = 44_100,
        bitRateBps = VOICE_RECORDING_BIT_RATE_BPS,
        label = "mic/44.1k",
    ),
)
