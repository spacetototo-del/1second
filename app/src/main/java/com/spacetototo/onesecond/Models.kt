package com.spacetototo.onesecond

import android.net.Uri
import java.util.Locale

/** 선택 가능한 클립 길이 (ms) */
val CLIP_LENGTHS = listOf(1000L, 1500L, 2000L)
const val DEFAULT_CLIP_MS = 1000L

data class VideoItem(val id: Long, val uri: Uri, val durationMs: Long, val hasAudio: Boolean = true)

/**
 * @param lengthMs 이 클립의 길이
 * @param custom 클립별로 직접 지정했으면 true (기본 길이를 바꿔도 따라가지 않는다)
 */
data class Clip(
    val id: Long,
    val uri: Uri,
    val pointMs: Long,
    val startMs: Long,
    val endMs: Long,
    val lengthMs: Long = DEFAULT_CLIP_MS,
    val custom: Boolean = false,
)

/** 포인트 앞뒤로 길이의 절반씩. 영상 처음/끝에 가까우면 안쪽으로 밀어서 길이를 유지한다. */
fun clipRange(pointMs: Long, durationMs: Long, lengthMs: Long): Pair<Long, Long> {
    if (durationMs <= lengthMs) return 0L to durationMs
    val start = (pointMs - lengthMs / 2).coerceIn(0L, durationMs - lengthMs)
    return start to (start + lengthMs)
}

fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L)
    val m = total / 60000
    val s = (total / 1000) % 60
    val d = (total % 1000) / 100
    return "%d:%02d.%d".format(m, s, d)
}

/** 1000 -> "1", 1500 -> "1.5", 4500 -> "4.5" */
fun formatSeconds(ms: Long): String =
    if (ms % 1000L == 0L) (ms / 1000L).toString() else String.format(Locale.US, "%.1f", ms / 1000.0)
