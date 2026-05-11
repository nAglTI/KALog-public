package org.debs.kalog.feature.chat.di

import org.debs.kalog.feature.chat.data.crypto.ChatKeyStore
import org.debs.kalog.feature.chat.data.crypto.ChatMessageCipher
import org.debs.kalog.feature.chat.data.crypto.ChatTransportKeyProvider
import org.debs.kalog.feature.chat.data.crypto.SettingsChatKeyStore
import org.debs.kalog.feature.chat.data.cache.ChatAttachmentFileCache
import org.debs.kalog.feature.chat.data.cache.createChatAttachmentFileCache
import org.debs.kalog.feature.chat.data.local.ChatLocalDataSource
import org.debs.kalog.feature.chat.data.local.InMemoryChatLocalDataSource
import org.debs.kalog.feature.chat.data.preferences.ChatPreferencesDataSource
import org.debs.kalog.feature.chat.data.preferences.SettingsChatPreferencesDataSource
import org.debs.kalog.feature.chat.data.remote.ChatRemoteDataSource
import org.debs.kalog.feature.chat.data.remote.KtorChatRemoteDataSource
import org.debs.kalog.feature.chat.data.remote.api.ChatApiService
import org.debs.kalog.feature.chat.data.repository.OfflineFirstChatRepository
import org.debs.kalog.feature.chat.domain.repository.ChatRepository
import org.debs.kalog.feature.chat.domain.usecase.AcceptChatInvitationUseCase
import org.debs.kalog.feature.chat.domain.usecase.BroadcastNicknameUseCase
import org.debs.kalog.feature.chat.domain.usecase.ClearCachedChatAttachmentsUseCase
import org.debs.kalog.feature.chat.domain.usecase.CreateDirectChatUseCase
import org.debs.kalog.feature.chat.domain.usecase.CreateGroupChatUseCase
import org.debs.kalog.feature.chat.domain.usecase.GetChatParticipantsUseCase
import org.debs.kalog.feature.chat.domain.usecase.ClearAllChatDataUseCase
import org.debs.kalog.feature.chat.domain.usecase.CloseChatUseCase
import org.debs.kalog.feature.chat.domain.usecase.ClearOldCachedChatAttachmentsUseCase
import org.debs.kalog.feature.chat.domain.usecase.DeclineChatInvitationUseCase
import org.debs.kalog.feature.chat.domain.usecase.GetCurrentUserIdUseCase
import org.debs.kalog.feature.chat.domain.usecase.InviteUserToChatUseCase
import org.debs.kalog.feature.chat.domain.usecase.LeaveGroupChatUseCase
import org.debs.kalog.feature.chat.domain.usecase.LoadMoreChatMessagesUseCase
import org.debs.kalog.feature.chat.domain.usecase.ObserveChatDetailsUseCase
import org.debs.kalog.feature.chat.domain.usecase.ObserveChatsUseCase
import org.debs.kalog.feature.chat.domain.usecase.OpenChatUseCase
import org.debs.kalog.feature.chat.domain.usecase.PrepareChatAttachmentUseCase
import org.debs.kalog.feature.chat.domain.usecase.RequestChatAttachmentDownloadUseCase
import org.debs.kalog.feature.chat.domain.usecase.RunChatSyncLoopUseCase
import org.debs.kalog.feature.chat.domain.usecase.SendChatMessageUseCase
import org.debs.kalog.feature.chat.domain.usecase.SetGroupChatPublicKeyUseCase
import org.debs.kalog.feature.chat.domain.usecase.StartChatSessionUseCase
import org.debs.kalog.feature.chat.presentation.chat.ChatDetailsViewModel
import org.debs.kalog.feature.chat.presentation.chatinfo.ChatInfoViewModel
import org.debs.kalog.feature.chat.presentation.chatlist.ChatListViewModel
import org.debs.kalog.feature.chat.presentation.session.ChatSessionViewModel
import org.debs.kalog.feature.chat.presentation.settings.SettingsViewModel
import org.debs.kalog.core.network.security.TransportKeyProvider
import org.koin.dsl.module

val chatFeatureModule = module {
    single<ChatPreferencesDataSource> { SettingsChatPreferencesDataSource(get(), get()) }
    single<ChatKeyStore> { SettingsChatKeyStore(get(), get(), get()) }
    single<TransportKeyProvider> { ChatTransportKeyProvider(get()) }
    single { ChatMessageCipher(get(), get()) }
    single<ChatLocalDataSource> { InMemoryChatLocalDataSource() }
    single<ChatAttachmentFileCache> { createChatAttachmentFileCache() }
    single { ChatApiService(get(), get(), get()) }
    single<ChatRemoteDataSource> { KtorChatRemoteDataSource(get()) }
    single<ChatRepository> { OfflineFirstChatRepository(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { AcceptChatInvitationUseCase(get()) }
    factory { BroadcastNicknameUseCase(get()) }
    factory { ClearCachedChatAttachmentsUseCase(get()) }
    factory { ClearAllChatDataUseCase(get()) }
    factory { ClearOldCachedChatAttachmentsUseCase(get()) }
    factory { CloseChatUseCase(get()) }
    factory { CreateDirectChatUseCase(get()) }
    factory { CreateGroupChatUseCase(get()) }
    factory { GetChatParticipantsUseCase(get()) }
    factory { DeclineChatInvitationUseCase(get()) }
    factory { GetCurrentUserIdUseCase(get()) }
    factory { InviteUserToChatUseCase(get()) }
    factory { LeaveGroupChatUseCase(get()) }
    factory { LoadMoreChatMessagesUseCase(get()) }
    factory { RunChatSyncLoopUseCase(get()) }
    factory { StartChatSessionUseCase(get()) }
    factory { ObserveChatsUseCase(get()) }
    factory { ObserveChatDetailsUseCase(get()) }
    factory { OpenChatUseCase(get()) }
    factory { PrepareChatAttachmentUseCase(get()) }
    factory { RequestChatAttachmentDownloadUseCase(get()) }
    factory { SendChatMessageUseCase(get()) }
    factory { SetGroupChatPublicKeyUseCase(get()) }
    factory { ChatSessionViewModel(get()) }
    factory { ChatListViewModel(get(), get(), get(), get()) }
    factory { SettingsViewModel(get(), get(), get(), get(), get(), get()) }
    factory { (chatId: String) -> ChatDetailsViewModel(chatId, get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { (chatId: String) -> ChatInfoViewModel(chatId, get(), get(), get(), get()) }
}
