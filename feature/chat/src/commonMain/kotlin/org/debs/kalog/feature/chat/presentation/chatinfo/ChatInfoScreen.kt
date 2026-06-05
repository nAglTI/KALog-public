package org.debs.kalog.feature.chat.presentation.chatinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.debs.kalog.feature.chat.domain.model.AvatarAccent
import org.debs.kalog.feature.chat.domain.model.AvatarSpec
import org.debs.kalog.feature.chat.domain.model.ChatType
import org.debs.kalog.feature.chat.localization.chatLocalized
import org.debs.kalog.feature.chat.presentation.components.AvatarBadge

@Composable
fun ChatInfoScreen(
    state: ChatInfoUiState,
    onBack: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onParticipantClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            ChatInfoHeader(onBack = onBack)
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            item(key = "avatar-header") {
                ChatInfoAvatarSection(
                    state = state,
                    onTitleChanged = onTitleChanged,
                )
            }

            if (state.participants.isNotEmpty()) {
                item(key = "participants-header") {
                    Text(
                        text = chatLocalized(
                            en = "Participants (${state.participants.size})",
                            ru = "Участники (${state.participants.size})",
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }

                items(
                    items = state.participants,
                    key = { it.userId },
                ) { participant ->
                    ParticipantRow(
                        participant = participant,
                        onClick = {
                            if (!participant.isCurrentUser) {
                                onParticipantClick(participant.userId)
                            }
                        },
                        isClickable = !participant.isCurrentUser && !state.isCreatingDirectChat,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatInfoHeader(
    onBack: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBackIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
    Text(
        text = chatLocalized(en = "Chat info", ru = "Информация о чате"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ChatInfoAvatarSection(
    state: ChatInfoUiState,
    onTitleChanged: (String) -> Unit,
) {
    var isEditing by remember { mutableStateOf(false) }
    var editingTitle by remember(state.title) { mutableStateOf(state.title) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AvatarBadge(
            avatar = state.avatar,
            modifier = Modifier.size(80.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (isEditing && state.type == ChatType.Group) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
            ) {
                BasicTextField(
                    value = editingTitle,
                    onValueChange = { editingTitle = it },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    decorationBox = { innerTextField ->
                        if (editingTitle.isBlank()) {
                Text(
                    text = chatLocalized(en = "Chat title", ru = "Название чата"),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        innerTextField()
                    },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            isEditing = false
                            editingTitle = state.title
                        },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
        Text(
            text = chatLocalized(en = "Cancel", ru = "Отмена"),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            if (editingTitle.isNotBlank()) {
                                onTitleChanged(editingTitle)
                            }
                            isEditing = false
                        },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
        Text(
            text = chatLocalized(en = "Save", ru = "Сохранить"),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        } else {
    Text(
        text = state.title.ifBlank {
            chatLocalized(en = "Untitled chat", ru = "Чат без названия")
        },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .then(
                        if (state.type == ChatType.Group) {
                            Modifier.clickable { isEditing = true }
                        } else {
                            Modifier
                        },
                    ),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
    text = when (state.type) {
        ChatType.Group -> chatLocalized(en = "Group chat", ru = "Групповой чат")
        ChatType.Personal -> chatLocalized(en = "Personal chat", ru = "Личный чат")
        ChatType.Unknown -> ""
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ParticipantRow(
    participant: ParticipantUiModel,
    onClick: () -> Unit,
    isClickable: Boolean,
) {
    val clipboardManager = LocalClipboardManager.current
    val canCopyUserId = !participant.isCurrentUser
    var isParticipantMenuVisible by remember { mutableStateOf(false) }
    val initials = participant.displayName
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.take(1).uppercase() }
        .ifBlank { participant.userId.take(2).uppercase() }

    val accent = AvatarAccent.entries.let { accents ->
        val hash = participant.userId.hashCode().toLong().let { if (it < 0) -it else it }
        accents[(hash % accents.size).toInt()]
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isClickable || canCopyUserId) {
                        Modifier
                            .participantContextMenuGestures(
                                enabled = canCopyUserId,
                                onOpen = { isParticipantMenuVisible = true },
                            )
                            .combinedClickable(
                                onClick = {
                                    if (isClickable) {
                                        onClick()
                                    }
                                },
                                onLongClick = {
                                    if (canCopyUserId) {
                                        isParticipantMenuVisible = true
                                    }
                                },
                                onLongClickLabel = chatLocalized(
                                    en = "Copy UUID",
                                    ru = "Копировать UUID",
                                ),
                            )
                    } else {
                        Modifier
                    }
                ),
            color = Color.Transparent,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarBadge(
                    avatar = AvatarSpec(initials = initials, accent = accent),
                    modifier = Modifier.size(44.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (participant.isCurrentUser) {
                            "${participant.displayName} (${chatLocalized(en = "you", ru = "вы")})"
                        } else {
                            participant.displayName
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!participant.isCurrentUser) {
                        Text(
                            text = chatLocalized(en = "Message", ru = "Написать"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        DropdownMenu(
            expanded = isParticipantMenuVisible,
            onDismissRequest = { isParticipantMenuVisible = false },
        ) {
            DropdownMenuItem(
                text = { Text(chatLocalized(en = "Copy UUID", ru = "Копировать UUID")) },
                onClick = {
                    clipboardManager.setText(AnnotatedString(participant.userId))
                    isParticipantMenuVisible = false
                },
            )
        }
    }
}

private fun Modifier.participantContextMenuGestures(
    enabled: Boolean,
    onOpen: () -> Unit,
): Modifier {
    if (!enabled) return this

    return pointerInput(onOpen) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    onOpen()
                }
            }
        }
    }
}
