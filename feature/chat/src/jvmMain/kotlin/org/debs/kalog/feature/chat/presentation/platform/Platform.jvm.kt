package org.debs.kalog.feature.chat.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import java.awt.BorderLayout
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Desktop
import java.awt.Image
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilenameFilter
import java.net.URI
import java.nio.file.Files
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import javax.swing.JPanel
import java.util.UUID
import javax.imageio.ImageIO
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.debs.kalog.feature.chat.data.cache.SecureJvmAttachmentStore
import org.debs.kalog.feature.chat.domain.model.ChatAttachment
import org.debs.kalog.feature.chat.domain.model.ChatAttachmentKind

internal actual val hasSoftwareKeyboard: Boolean = false

@Composable
actual fun ConfigureSystemBars(fullScreenMediaVisible: Boolean) = Unit

internal actual suspend fun pickFileAttachment(): ChatAttachment? = withContext(Dispatchers.IO) {
    if (GraphicsEnvironment.isHeadless()) return@withContext null

    val dialog = FileDialog(null as Frame?, "Attach file", FileDialog.LOAD)
    dialog.isVisible = true

    val selectedFileName = dialog.file ?: return@withContext null
    val selectedDirectory = dialog.directory ?: return@withContext null
    val file = File(selectedDirectory, selectedFileName)
    if (!file.isFile) return@withContext null

    file.toChatAttachment()
}

internal actual suspend fun pickImageAttachments(): List<ChatAttachment> = withContext(Dispatchers.IO) {
    if (GraphicsEnvironment.isHeadless()) return@withContext emptyList()

    val dialog = FileDialog(null as Frame?, "Attach images", FileDialog.LOAD).apply {
        isMultipleMode = true
        filenameFilter = FilenameFilter { _, name -> name.hasImageExtension() }
    }
    dialog.isVisible = true

    dialog.files
        .orEmpty()
        .filter(File::isFile)
        .mapNotNull(File::toChatAttachment)
        .filter { attachment -> attachment.kind == ChatAttachmentKind.Image }
}

internal actual suspend fun takePhotoAttachment(): ChatAttachment? = null

internal actual fun readClipboardAttachments(): List<ChatAttachment> {
    if (GraphicsEnvironment.isHeadless()) return emptyList()
    return runCatching {
        val transferable = Toolkit.getDefaultToolkit()
            .systemClipboard
            .getContents(null)
            ?: return emptyList()
        transferable.toChatAttachments()
    }.getOrDefault(emptyList())
}

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun readDroppedAttachments(event: DragAndDropEvent): List<ChatAttachment> {
    return runCatching {
        when (val dragData = event.dragData()) {
            is DragData.FilesList -> dragData.readFiles().mapNotNull { uri ->
                runCatching { File(URI(uri)).toChatAttachment() }.getOrNull()
            }
            else -> emptyList()
        }
    }.getOrDefault(emptyList())
}

private fun String?.toAttachmentKind(): ChatAttachmentKind {
    return when {
        this?.startsWith("image/") == true -> ChatAttachmentKind.Image
        this?.startsWith("video/") == true -> ChatAttachmentKind.Video
        this?.startsWith("audio/") == true -> ChatAttachmentKind.Audio
        else -> ChatAttachmentKind.File
    }
}

private fun Transferable.toChatAttachments(): List<ChatAttachment> {
    return when {
        isDataFlavorSupported(DataFlavor.javaFileListFlavor) -> {
            val files = getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
            files.orEmpty()
                .filterIsInstance<File>()
                .mapNotNull { file -> file.toChatAttachment() }
        }
        isDataFlavorSupported(DataFlavor.imageFlavor) -> {
            val image = getTransferData(DataFlavor.imageFlavor) as? Image
            listOfNotNull(image?.toClipboardImageAttachment())
        }
        else -> emptyList()
    }
}

private fun File.toChatAttachment(): ChatAttachment? {
    if (!isFile) return null
    val mimeType = runCatching { Files.probeContentType(toPath()) }.getOrNull()
    val fileSize = length()
    return ChatAttachment(
        id = UUID.randomUUID().toString(),
        kind = mimeType.toAttachmentKind(),
        name = name,
        mimeType = mimeType,
        sizeBytes = fileSize,
        localUri = toURI().toString(),
        contentBytes = if (fileSize <= INLINE_ATTACHMENT_BYTES_LIMIT) readBytes() else null,
    )
}

private fun Image.toClipboardImageAttachment(): ChatAttachment? {
    val bufferedImage = toBufferedImage()
    val bytes = ByteArrayOutputStream().use { stream ->
        ImageIO.write(bufferedImage, "png", stream)
        stream.toByteArray()
    }
    if (bytes.isEmpty()) return null
    return ChatAttachment(
        id = UUID.randomUUID().toString(),
        kind = ChatAttachmentKind.Image,
        name = "clipboard-image.png",
        mimeType = "image/png",
        sizeBytes = bytes.size.toLong(),
        localUri = null,
        contentBytes = bytes,
    )
}

private fun Image.toBufferedImage(): BufferedImage {
    if (this is BufferedImage) return this
    val bufferedImage = BufferedImage(getWidth(null), getHeight(null), BufferedImage.TYPE_INT_ARGB)
    val graphics = bufferedImage.createGraphics()
    try {
        graphics.drawImage(this, 0, 0, null)
    } finally {
        graphics.dispose()
    }
    return bufferedImage
}

private fun String.hasImageExtension(): Boolean {
    val extension = substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return extension in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
}

internal actual fun openLocalAttachment(localUri: String): Boolean {
    if (SecureJvmAttachmentStore.isSecureUri(localUri)) return false
    return runCatching {
        if (!Desktop.isDesktopSupported()) return false
        val file = File(URI(localUri))
        if (!file.isFile) return false
        Desktop.getDesktop().open(file)
        true
    }.getOrDefault(false)
}

internal actual fun playLocalAudio(
    localUri: String,
    onProgress: (positionMillis: Long, durationMillis: Long) -> Unit,
    onFinished: () -> Unit,
): Boolean {
    return DesktopAudioPlayer.play(localUri, onProgress, onFinished)
}

internal actual fun stopLocalAudio() {
    DesktopAudioPlayer.stop()
}

internal actual fun seekLocalAudio(positionMillis: Long): Boolean {
    return DesktopAudioPlayer.seekTo(positionMillis)
}

internal actual fun loadImagePreview(
    localUri: String?,
    contentBytes: ByteArray?,
    maxSidePx: Int,
): ByteArray? {
    return runCatching {
        val source = when {
            contentBytes != null -> ByteArrayInputStream(contentBytes).use(ImageIO::read)
            localUri != null && SecureJvmAttachmentStore.isSecureUri(localUri) ->
                SecureJvmAttachmentStore.readAll(localUri)?.let { bytes ->
                    ByteArrayInputStream(bytes).use(ImageIO::read)
                }
            localUri != null -> ImageIO.read(File(URI(localUri)))
            else -> null
        } ?: return@runCatching null
        val largestSide = maxOf(source.width, source.height)
        val safeMaxSide = maxSidePx.coerceAtLeast(128)
        val scale = (safeMaxSide.toDouble() / largestSide.toDouble()).coerceAtMost(1.0)
        val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
        val preview = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        val graphics = preview.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.color = java.awt.Color.WHITE
            graphics.fillRect(0, 0, targetWidth, targetHeight)
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null)
        } finally {
            graphics.dispose()
        }
        ByteArrayOutputStream().use { stream ->
            ImageIO.write(preview, "jpg", stream)
            stream.toByteArray()
        }
    }.getOrNull()
}

internal actual fun loadVideoThumbnail(localUri: String, maxSidePx: Int): ByteArray? = null

@Composable
internal actual fun PlatformVideoPlayer(
    localUri: String,
    fileName: String?,
    mimeType: String?,
    modifier: Modifier,
) {
    val playbackFile = remember(localUri, fileName, mimeType) {
        localUri.toJvmVideoPlaybackFile(fileName = fileName, mimeType = mimeType)
    } ?: return
    val deletePlaybackFile = SecureJvmAttachmentStore.isSecureUri(localUri)
    var mediaPlayer: MediaPlayer? = null
    SwingPanel(
        modifier = modifier,
        factory = {
            JPanel(BorderLayout()).apply {
                val jfxPanel = JFXPanel()
                add(jfxPanel, BorderLayout.CENTER)
                javafx.application.Platform.runLater {
                    val player = MediaPlayer(Media(playbackFile.toURI().toString())).apply {
                        isAutoPlay = true
                    }
                    mediaPlayer = player
                    val mediaView = MediaView(player).apply {
                        isPreserveRatio = true
                        fitWidth = 1280.0
                        fitHeight = 720.0
                    }
                    val root = StackPane(mediaView)
                    jfxPanel.scene = Scene(root, 1280.0, 720.0)
                }
            }
        },
    )
    DisposableEffect(localUri) {
        onDispose {
            javafx.application.Platform.runLater {
                mediaPlayer?.stop()
                mediaPlayer?.dispose()
            }
            if (deletePlaybackFile) {
                runCatching { playbackFile.delete() }
            }
        }
    }
}

private fun String.toJvmVideoPlaybackFile(
    fileName: String?,
    mimeType: String?,
): File? {
    return if (SecureJvmAttachmentStore.isSecureUri(this)) {
        val bytes = SecureJvmAttachmentStore.readAll(this) ?: return null
        val extension = fileName?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
            ?: mimeType.toVideoExtension()
        Files.createTempFile("kalog-video-", ".$extension").toFile().apply {
            writeBytes(bytes)
            deleteOnExit()
        }
    } else {
        runCatching { File(URI(this)).takeIf(File::isFile) }.getOrNull()
    }
}

private fun String?.toVideoExtension(): String {
    return when (this) {
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        "video/x-matroska" -> "mkv"
        else -> "mp4"
    }
}

internal actual suspend fun toggleVoiceRecording(): VoiceRecordingResult = withContext(Dispatchers.IO) {
    DesktopVoiceRecorder.toggle()
}

private const val INLINE_ATTACHMENT_BYTES_LIMIT = 16L * 1024L * 1024L

private object DesktopAudioPlayer {
    @Volatile
    private var activeClip: Clip? = null

    fun play(
        localUri: String,
        onProgress: (positionMillis: Long, durationMillis: Long) -> Unit,
        onFinished: () -> Unit,
    ): Boolean {
        stop()
        val clip = runCatching {
            val audioInputStream = if (SecureJvmAttachmentStore.isSecureUri(localUri)) {
                val bytes = SecureJvmAttachmentStore.readAll(localUri) ?: return false
                AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes))
            } else {
                val file = File(URI(localUri))
                AudioSystem.getAudioInputStream(file)
            }
            AudioSystem.getClip().apply {
                open(audioInputStream)
                start()
            }
        }.getOrElse {
            val opened = openLocalAttachment(localUri)
            onFinished()
            return opened
        }

        activeClip = clip
        Thread {
            try {
                while (activeClip == clip && clip.isOpen && clip.isRunning) {
                    onProgress(
                        clip.microsecondPosition / 1000L,
                        clip.microsecondLength / 1000L,
                    )
                    Thread.sleep(150L)
                }
                if (activeClip == clip && clip.isOpen) {
                    onProgress(clip.microsecondLength / 1000L, clip.microsecondLength / 1000L)
                    activeClip = null
                    clip.close()
                    onFinished()
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }.apply {
            isDaemon = true
            name = "KALog audio progress"
            start()
        }
        return true
    }

    fun stop() {
        activeClip?.let { clip ->
            activeClip = null
            runCatching { clip.stop() }
            runCatching { clip.close() }
        }
    }

    fun seekTo(positionMillis: Long): Boolean {
        val clip = activeClip ?: return false
        return runCatching {
            clip.microsecondPosition = positionMillis.coerceAtLeast(0L) * 1000L
            true
        }.getOrDefault(false)
    }
}

private object DesktopVoiceRecorder {
    private val format = AudioFormat(44_100f, 16, 1, true, false)

    @Volatile
    private var activeRecorder: ActiveDesktopVoiceRecorder? = null

    fun toggle(): VoiceRecordingResult {
        activeRecorder?.let { recorder ->
            activeRecorder = null
            return recorder.stop()
        }

        val recorder = runCatching {
            val info = DataLine.Info(TargetDataLine::class.java, format)
            val line = AudioSystem.getLine(info) as TargetDataLine
            line.open(format)
            ActiveDesktopVoiceRecorder(line, format)
        }.getOrNull() ?: return VoiceRecordingResult.Unavailable

        activeRecorder = recorder
        recorder.start()
        return VoiceRecordingResult.Started
    }
}

private class ActiveDesktopVoiceRecorder(
    private val line: TargetDataLine,
    private val format: AudioFormat,
) {
    private val rawAudio = ByteArrayOutputStream()
    private val startedAtMillis = System.currentTimeMillis()

    @Volatile
    private var isRecording = false

    private val captureThread = Thread {
        val buffer = ByteArray(4096)
        while (isRecording) {
            val read = runCatching { line.read(buffer, 0, buffer.size) }.getOrDefault(-1)
            if (read > 0) {
                synchronized(rawAudio) {
                    rawAudio.write(buffer, 0, read)
                }
            }
        }
    }.apply {
        isDaemon = true
        name = "KALog voice recorder"
    }

    fun start() {
        isRecording = true
        line.start()
        captureThread.start()
    }

    fun stop(): VoiceRecordingResult {
        isRecording = false
        runCatching { line.stop() }
        runCatching { line.close() }
        runCatching { captureThread.join(800L) }

        val pcmBytes = synchronized(rawAudio) { rawAudio.toByteArray() }
        if (pcmBytes.isEmpty()) {
            return VoiceRecordingResult.Unavailable
        }

        val wavBytes = pcmBytes.toWavBytes(format)
        val durationMillis = (System.currentTimeMillis() - startedAtMillis).coerceAtLeast(0L)

        return VoiceRecordingResult.Finished(
            ChatAttachment(
                id = UUID.randomUUID().toString(),
                kind = ChatAttachmentKind.Voice,
                name = "voice.wav",
                mimeType = "audio/wav",
                sizeBytes = wavBytes.size.toLong(),
                localUri = null,
                contentBytes = wavBytes,
                durationMillis = durationMillis,
            ),
        )
    }
}

private fun ByteArray.toWavBytes(format: AudioFormat): ByteArray {
    val wav = ByteArrayOutputStream()
    val byteRate = (format.sampleRate * format.frameSize).toInt()
    val dataSize = size
    wav.writeAscii("RIFF")
    wav.writeIntLe(36 + dataSize)
    wav.writeAscii("WAVE")
    wav.writeAscii("fmt ")
    wav.writeIntLe(16)
    wav.writeShortLe(1)
    wav.writeShortLe(format.channels)
    wav.writeIntLe(format.sampleRate.toInt())
    wav.writeIntLe(byteRate)
    wav.writeShortLe(format.frameSize)
    wav.writeShortLe(format.sampleSizeInBits)
    wav.writeAscii("data")
    wav.writeIntLe(dataSize)
    wav.write(this)
    return wav.toByteArray()
}

private fun ByteArrayOutputStream.writeAscii(value: String) {
    write(value.encodeToByteArray())
}

private fun ByteArrayOutputStream.writeIntLe(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
    write((value ushr 16) and 0xFF)
    write((value ushr 24) and 0xFF)
}

private fun ByteArrayOutputStream.writeShortLe(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
}
