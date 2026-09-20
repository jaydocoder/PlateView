package com.jaydocoder.plateview.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime

private val beijingZoneId = ZoneId.of("Asia/Shanghai")

@Composable
fun rememberCurrentBeijingTime(): ZonedDateTime {
    val currentTime by produceState(initialValue = ZonedDateTime.now(beijingZoneId)) {
        while (true) {
            val now = ZonedDateTime.now(beijingZoneId)
            val elapsedMillis = now.second * 1_000L + now.nano / 1_000_000L
            delay((60_000L - elapsedMillis).coerceAtLeast(1_000L))
            value = ZonedDateTime.now(beijingZoneId)
        }
    }
    return currentTime
}
