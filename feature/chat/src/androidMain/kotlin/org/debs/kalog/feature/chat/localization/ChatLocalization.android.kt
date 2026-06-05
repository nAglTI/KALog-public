package org.debs.kalog.feature.chat.localization

import java.util.Locale

actual fun currentSystemLanguageCode(): String = Locale.getDefault().language
