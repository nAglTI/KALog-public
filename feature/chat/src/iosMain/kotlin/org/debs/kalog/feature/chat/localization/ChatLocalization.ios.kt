package org.debs.kalog.feature.chat.localization

import platform.Foundation.NSLocale

actual fun currentSystemLanguageCode(): String {
    return NSLocale.currentLocale.languageCode ?: "en"
}
