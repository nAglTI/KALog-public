package org.debs.kalog.feature.chat.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.debs.kalog.core.crypto.EncryptionService
import org.debs.kalog.feature.chat.data.cache.CachedChatAttachment
import org.debs.kalog.feature.chat.data.cache.ChatAttachmentFileCache
import org.debs.kalog.feature.chat.data.cache.EncryptedCachedAttachmentPart
import org.debs.kalog.feature.chat.data.remote.ChatRemoteDataSource
import org.debs.kalog.feature.chat.domain.model.ChatAttachment
import org.debs.kalog.feature.chat.domain.model.ChatAttachmentLoadState
import org.debs.kalog.feature.chat.domain.model.ChatAttachmentPart
import org.debs.kalog.feature.chat.domain.model.ChatMessage
import org.debs.kalog.feature.chat.domain.model.ChatThread

internal class ChatAttachmentDownloadCoordinator(
    private val remoteDataSource: ChatRemoteDataSource,
    private val encryptionService: EncryptionService,
    private val attachmentFileCache: ChatAttachmentFileCache,
) {
    private val mutableCachedAttachmentFiles = MutableStateFlow<Map<String, CachedChatAttachment>>(emptyMap())
    private val mutableAttachmentLoadStates = MutableStateFlow<Map<String, ChatAttachmentLoadState>>(emptyMap())
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val downloadMutex = Mutex()
    private val cacheCheckSemaphore = Semaphore(1)
    private val downloadSemaphore = Semaphore(2)
    private val downloadingAttachmentIds = mutableSetOf<String>()
    private val downloadJobsByChat = mutableMapOf<String, MutableMap<String, Job>>()
    private var activeChatId: String? = null

    val cachedAttachmentFiles: StateFlow<Map<String, CachedChatAttachment>> = mutableCachedAttachmentFiles.asStateFlow()
    val attachmentLoadStates: StateFlow<Map<String, ChatAttachmentLoadState>> = mutableAttachmentLoadStates.asStateFlow()

    suspend fun clearAll() {
        attachmentFileCache.clearAll()
        cancelAll()
        mutableCachedAttachmentFiles.value = emptyMap()
        mutableAttachmentLoadStates.value = emptyMap()
    }

    suspend fun clearCachedAttachments(): Int {
        return attachmentFileCache.clearAll().also { clearedCount ->
            if (clearedCount > 0) {
                mutableCachedAttachmentFiles.value = emptyMap()
                mutableAttachmentLoadStates.value = emptyMap()
            }
        }
    }

    suspend fun clearCachedAttachmentsOlderThan(ageMillis: Long): Int {
        return attachmentFileCache.clearOlderThan(ageMillis).also { clearedCount ->
            if (clearedCount > 0) {
                mutableCachedAttachmentFiles.value = emptyMap()
                mutableAttachmentLoadStates.value = emptyMap()
            }
        }
    }

    fun scheduleAttachmentDownloads(thread: ChatThread) {
        if (activeChatId != thread.id) return
        val knownLoadStates = mutableAttachmentLoadStates.value
        thread.messages
            .asReversed()
            .filterIsInstance<ChatMessage.User>()
            .flatMap { message -> message.attachments }
            .filter { attachment ->
                attachment.localUri == null &&
                    (attachment.decryptionKey != null || attachment.parts.any { part -> part.key != null }) &&
                    attachment.shouldScheduleAutomaticAttachmentLoad(knownLoadStates)
            }
            .take(AUTOMATIC_ATTACHMENT_SCHEDULE_BATCH_SIZE)
            .forEach { attachment ->
                scheduleAttachmentDownload(
                    chatId = thread.id,
                    attachment = attachment,
                    userInitiated = false,
                )
            }
    }

    fun scheduleAttachmentDownload(
        chatId: String,
        attachment: ChatAttachment,
        userInitiated: Boolean,
    ) {
        downloadScope.launch {
            val currentJob = currentCoroutineContext()[Job] ?: return@launch
            val shouldStart = downloadMutex.withLock {
                if (activeChatId != chatId || attachment.id in downloadingAttachmentIds) {
                    false
                } else {
                    downloadingAttachmentIds += attachment.id
                    downloadJobsByChat
                        .getOrPut(chatId) { mutableMapOf() }[attachment.id] = currentJob
                    true
                }
            }
            if (!shouldStart) return@launch

            try {
                val cachedFile = cacheCheckSemaphore.withPermit {
                    updateAttachmentLoadState(attachment.id, ChatAttachmentLoadState.CheckingCache)
                    attachmentFileCache.get(attachment)
                }
                if (cachedFile != null) {
                    rememberCachedAttachmentFile(attachment.id, cachedFile)
                    updateAttachmentLoadState(attachment.id, ChatAttachmentLoadState.Ready)
                    return@launch
                }

                if (!userInitiated && attachment.requiresExplicitDownload()) {
                    updateAttachmentLoadState(attachment.id, ChatAttachmentLoadState.WaitingForTap)
                    return@launch
                }

                downloadSemaphore.withPermit {
                    updateAttachmentLoadState(attachment.id, ChatAttachmentLoadState.Downloading)
                    val cachedFile = downloadAndCacheAttachment(attachment)
                    if (cachedFile != null) {
                        rememberCachedAttachmentFile(attachment.id, cachedFile)
                        updateAttachmentLoadState(attachment.id, ChatAttachmentLoadState.Ready)
                    } else {
                        updateAttachmentLoadState(attachment.id, ChatAttachmentLoadState.Failed)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                updateAttachmentLoadState(attachment.id, ChatAttachmentLoadState.Failed)
            } finally {
                downloadMutex.withLock {
                    downloadingAttachmentIds -= attachment.id
                    downloadJobsByChat[chatId]?.remove(attachment.id)
                    if (downloadJobsByChat[chatId]?.isEmpty() == true) {
                        downloadJobsByChat.remove(chatId)
                    }
                }
            }
        }
    }

    suspend fun activateChat(chatId: String) {
        val jobsToCancel = downloadMutex.withLock {
            if (activeChatId == chatId) return
            activeChatId = chatId
            val inactiveChats = downloadJobsByChat.keys.filter { activeChatId -> activeChatId != chatId }
            inactiveChats.flatMap { inactiveChatId ->
                downloadJobsByChat.remove(inactiveChatId)
                    .orEmpty()
                    .also { jobs -> downloadingAttachmentIds.removeAll(jobs.keys) }
                    .values
            }
        }
        jobsToCancel.forEach(Job::cancel)
    }

    suspend fun cancelAll() {
        val jobsToCancel = downloadMutex.withLock {
            activeChatId = null
            val jobs = downloadJobsByChat.values.flatMap { jobsByAttachment -> jobsByAttachment.values }
            downloadJobsByChat.clear()
            downloadingAttachmentIds.clear()
            jobs
        }
        jobsToCancel.forEach(Job::cancel)
    }

    private fun updateAttachmentLoadState(
        attachmentId: String,
        state: ChatAttachmentLoadState,
    ) {
        mutableAttachmentLoadStates.update { current ->
            if (current[attachmentId] == state) {
                current
            } else {
                current + (attachmentId to state)
            }
        }
    }

    private suspend fun rememberCachedAttachmentFile(
        attachmentId: String,
        cachedFile: CachedChatAttachment,
    ) {
        downloadMutex.withLock {
            mutableCachedAttachmentFiles.value = mutableCachedAttachmentFiles.value + (attachmentId to cachedFile)
        }
    }

    private suspend fun downloadAndCacheAttachment(attachment: ChatAttachment): CachedChatAttachment? {
        val key = attachment.decryptionKey ?: attachment.parts.firstNotNullOfOrNull(ChatAttachmentPart::key) ?: return null
        return if (attachment.parts.isEmpty()) {
            val encryptedBytes = remoteDataSource.downloadAttachment(attachment.id)
            updateAttachmentLoadState(attachment.id, ChatAttachmentLoadState.Downloaded)
            updateAttachmentLoadState(attachment.id, ChatAttachmentLoadState.Decrypting)
            attachmentFileCache.putEncrypted(
                attachmentId = attachment.id,
                fileName = attachment.name,
                mimeType = attachment.mimeType,
                encryptedBytes = encryptedBytes,
                plainSizeBytes = attachment.sizeBytes,
                decryptionKey = key,
            )
        } else {
            attachmentFileCache.putEncryptedFromChunks(
                attachmentId = attachment.id,
                fileName = attachment.name,
                mimeType = attachment.mimeType,
                plainSizeBytes = attachment.sizeBytes,
            ) { append ->
                attachment.parts
                    .sortedBy(ChatAttachmentPart::index)
                    .forEach { part ->
                        updateAttachmentLoadState(attachment.id, ChatAttachmentLoadState.Downloading)
                        val partKey = part.key ?: key
                        val encryptedBytes = remoteDataSource.downloadAttachment(part.id)
                        updateAttachmentLoadState(attachment.id, ChatAttachmentLoadState.Downloaded)
                        updateAttachmentLoadState(attachment.id, ChatAttachmentLoadState.Decrypting)
                        val plainSizeBytes = part.sizeBytes
                            ?: attachment.inferredPartPlainSize(part.index)
                            ?: encryptionService.decryptAttachment(encryptedBytes, partKey).size.toLong()
                        append(
                            EncryptedCachedAttachmentPart(
                                id = part.id,
                                index = part.index,
                                encryptedBytes = encryptedBytes,
                                plainSizeBytes = plainSizeBytes,
                                decryptionKey = partKey,
                            ),
                        )
                    }
            }
        }
    }
}
