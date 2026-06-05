package org.debs.kalog.feature.chat.localization

enum class ChatLanguage {
    English,
    Russian,
}

fun currentChatLanguage(): ChatLanguage {
    return if (currentSystemLanguageCode().equals("ru", ignoreCase = true)) {
        ChatLanguage.Russian
    } else {
        ChatLanguage.English
    }
}

fun chatLocalized(en: String, ru: String): String {
    return when (currentChatLanguage()) {
        ChatLanguage.English -> en
        ChatLanguage.Russian -> ru
    }
}

expect fun currentSystemLanguageCode(): String
