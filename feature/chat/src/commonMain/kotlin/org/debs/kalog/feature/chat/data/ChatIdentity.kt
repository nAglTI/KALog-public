package org.debs.kalog.feature.chat.data

import org.debs.kalog.feature.chat.localization.chatLocalized

internal fun currentUserDisplayName(): String = chatLocalized(
    en = "You",
    ru = "Вы",
)

internal fun isCurrentUserDisplayName(displayName: String?): Boolean {
    return displayName == "You" || displayName == "Вы"
}
