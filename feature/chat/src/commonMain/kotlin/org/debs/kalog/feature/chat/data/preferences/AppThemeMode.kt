package org.debs.kalog.feature.chat.data.preferences

enum class AppThemeMode(val storageValue: String) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun fromStorage(value: String?): AppThemeMode {
            return entries.firstOrNull { it.storageValue == value } ?: System
        }
    }
}
