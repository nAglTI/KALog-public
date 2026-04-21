package org.debs.kalog.feature.chat.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

private fun AvatarAccent.palette(): Pair<Color, Color> {
    return when (this) {
        AvatarAccent.Sky -> Color(0xFF31A7F3) to Color(0xFF61C7FF)
        AvatarAccent.Emerald -> Color(0xFF1EB980) to Color(0xFF56D6A5)
        AvatarAccent.Amber -> Color(0xFFEE9B28) to Color(0xFFF8BE5C)
        AvatarAccent.Rose -> Color(0xFFE45A84) to Color(0xFFF287A6)
        AvatarAccent.Indigo -> Color(0xFF5067E7) to Color(0xFF7C90FF)
    }
}
