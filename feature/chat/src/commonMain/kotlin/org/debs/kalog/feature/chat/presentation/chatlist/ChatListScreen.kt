package org.debs.kalog.feature.chat.presentation.chatlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import mayday_chat.feature.chat.generated.resources.Res
import mayday_chat.feature.chat.generated.resources.*
import org.debs.kalog.feature.chat.domain.model.AvatarAccent
import org.debs.kalog.feature.chat.domain.model.AvatarSpec
import org.debs.kalog.feature.chat.presentation.components.AvatarBadge
import org.debs.kalog.feature.chat.presentation.components.SavedMessagesAvatar
import org.debs.kalog.feature.chat.presentation.text.asString
import org.debs.kalog.feature.chat.presentation.text.rawText
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChatListScreen(
    state: ChatListUiState,
    onChatClick: (String) -> Unit,
    onCreateChatClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            ChatListHeader(
                title = state.title,
                onSettingsClick = onSettingsClick,
            )
            val listState = rememberLazyListState()
            var wasAtTop by remember { mutableStateOf(true) }
            LaunchedEffect(listState) {
                snapshotFlow {
                    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                }.collectLatest { atTop -> wasAtTop = atTop }
            }
            LaunchedEffect(state.items.firstOrNull()?.id) {
                if (wasAtTop) {
                    listState.scrollToItem(0)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = state.items,
                    key = ChatListItemUiState::id,
                ) { item ->
                    ChatListRow(
                        item = item,
                        onClick = { onChatClick(item.id) },
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = onCreateChatClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(end = 20.dp, bottom = 20.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringResource(Res.string.create_chat),
            )
        }
    }
}

@Composable
private fun ChatListHeader(
    title: String,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Surface(
            modifier = Modifier
                .size(36.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onSettingsClick),
            shape = MaterialTheme.shapes.medium,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(Res.string.settings),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ChatListRow(
    item: ChatListItemUiState,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (val avatar = item.avatar) {
                is ChatListAvatarUiState.Initials -> AvatarBadge(
                    avatar = avatar.avatar,
                    modifier = Modifier.size(58.dp),
                )
                ChatListAvatarUiState.SavedMessages -> SavedMessagesAvatar(modifier = Modifier.size(58.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = item.previewAuthor?.let { "${it.asString()}: ${item.preview.asString()}" }
                        ?: item.preview.asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.isPinned) {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = item.timestamp,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = item.unreadCount.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
@Preview
private fun ChatListPreview() {
    ChatListScreen(
        state = ChatListUiState(
            title = "Mayday Chat",
            summary = rawText("Encrypted chats: 3"),
            items = listOf(
                ChatListItemUiState(
                    id = "1",
                    title = "Release Channel",
                    avatar = ChatListAvatarUiState.Initials(AvatarSpec("RC", AvatarAccent.Rose)),
                    timestamp = "08:28",
                    preview = rawText("The latest changelog draft is ready."),
                    previewAuthor = rawText("Anna"),
                    unreadCount = 2,
                ),
                ChatListItemUiState(
                    id = "self",
                    title = "Saved Messages",
                    avatar = ChatListAvatarUiState.SavedMessages,
                    timestamp = "",
                    preview = rawText("No messages yet"),
                    previewAuthor = null,
                    unreadCount = 0,
                    isPinned = true,
                ),
            ),
        ),
        onChatClick = {},
        onCreateChatClick = {},
        onSettingsClick = {},
    )
}
