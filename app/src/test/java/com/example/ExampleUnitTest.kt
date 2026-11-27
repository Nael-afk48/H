package com.example

import com.example.util.formatRelativeTime
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date
import java.util.concurrent.TimeUnit

class ExampleUnitTest {
    @Test
    fun formatRelativeTime_formatsCalmHumanReadableIntervals() {
        val nowMillis = 1_760_000_000_000L

        assertEquals("just now", formatRelativeTime(null, nowMillis))
        assertEquals(
            "just now",
            formatRelativeTime(Timestamp(Date(nowMillis - TimeUnit.SECONDS.toMillis(25))), nowMillis)
        )
        assertEquals(
            "2m ago",
            formatRelativeTime(Timestamp(Date(nowMillis - TimeUnit.MINUTES.toMillis(2))), nowMillis)
        )
        assertEquals(
            "5h ago",
            formatRelativeTime(Timestamp(Date(nowMillis - TimeUnit.HOURS.toMillis(5))), nowMillis)
        )
        assertEquals(
            "yesterday",
            formatRelativeTime(Timestamp(Date(nowMillis - TimeUnit.DAYS.toMillis(1))), nowMillis)
        )
        assertEquals(
            "4d ago",
            formatRelativeTime(Timestamp(Date(nowMillis - TimeUnit.DAYS.toMillis(4))), nowMillis)
        )
        assertEquals(
            "2w ago",
            formatRelativeTime(Timestamp(Date(nowMillis - TimeUnit.DAYS.toMillis(14))), nowMillis)
        )
    }
}
