package org.debs.kalog.feature.chat.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.debs.kalog.feature.chat.domain.model.AvatarAccent
import org.debs.kalog.feature.chat.domain.model.AvatarSpec

@Composable
fun AvatarBadge(
    avatar: AvatarSpec,
    modifier: Modifier = Modifier,
) {
    val palette = avatar.accent.palette()
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(palette.first, palette.second),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = avatar.initials,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
fun SavedMessagesAvatar(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Bookmark,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(28.dp),
        )
    }
}

private fun AvatarAccent.palette(): Pair<Color, Color> {
    return when (this) {
        AvatarAccent.Sky -> Color(0xFF2F6A4B) to Color(0xFF7CBD96)
        AvatarAccent.Emerald -> Color(0xFF4F6D45) to Color(0xFF9FB77A)
        AvatarAccent.Amber -> Color(0xFF9A7527) to Color(0xFFD4A85A)
        AvatarAccent.Rose -> Color(0xFFA4432A) to Color(0xFFD07A5F)
        AvatarAccent.Indigo -> Color(0xFF6B6558) to Color(0xFFAFA896)
    }
}
