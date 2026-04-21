package org.debs.kalog.feature.chat.data.repository

import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatTimestampFormattingJvmTest {
    @Test
    fun formatsIsoInstantIntoReadableDateAndTime() {
        withDefaultTimeZone("UTC") {
            assertEquals("21 мар, 10:15", "2026-03-21T10:15:30Z".toDisplayTimestamp())
        }
    }

    @Test
    fun convertsOffsetTimestampToSystemTimeZone() {
        withDefaultTimeZone("UTC") {
            assertEquals("21 мар, 10:15", "2026-03-21T13:15:30+03:00".toDisplayTimestamp())
        }
    }

    @Test
    fun keepsShortTimeAsIs() {
        assertEquals("09:12", "09:12".toDisplayTimestamp())
    }

    @Test
    fun preservesUnknownTimestampWhenParsingFails() {
        assertEquals("Yesterday", "Yesterday".toDisplayTimestamp())
    }

    @Test
    fun formatsOlderDatesWithoutFailing() {
        withDefaultTimeZone("UTC") {
            assertEquals("21 мар, 10:15", "2025-03-21T10:15:30Z".toDisplayTimestamp())
        }
    }
}

private fun withDefaultTimeZone(timeZoneId: String, block: () -> Unit) {
    val previous = TimeZone.getDefault()
    TimeZone.setDefault(TimeZone.getTimeZone(timeZoneId))
    try {
        block()
    } finally {
        TimeZone.setDefault(previous)
    }
}
