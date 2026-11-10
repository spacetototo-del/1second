package com.spacetototo.onesecond

import android.net.Uri

const val CLIP_MS = 1000L

data class VideoItem(val id: Long, val uri: Uri, val durationMs: Long)

data class Clip(
    val id: Long,
    val uri: Uri,
    val pointMs: Long,
    val startMs: Long,
    val endMs: Long,
)

/** 포인트 앞뒤 0.5초 = 1초 구간. 영상 처음/끝에 가까우면 안쪽으로 밀어서 1초를 유지한다. */
fun clipRange(pointMs: Long, durationMs: Long): Pair<Long, Long> {
    if (durationMs <= CLIP_MS) return 0L to durationMs
    val start = (pointMs - CLIP_MS / 2).coerceIn(0L, durationMs - CLIP_MS)
    return start to (start + CLIP_MS)
}

fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L)
    val m = total / 60000
    val s = (total / 1000) % 60
    val d = (total % 1000) / 100
    return "%d:%02d.%d".format(m, s, d)
}
