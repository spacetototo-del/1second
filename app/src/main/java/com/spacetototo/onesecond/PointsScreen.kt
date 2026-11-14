@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.spacetototo.onesecond

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** 미리보기 요청 (n 은 같은 구간을 다시 눌러도 다시 재생되게 하는 카운터) */
private data class PreviewReq(val start: Long, val end: Long, val point: Long, val n: Int)

/** 영상이 화면의 대부분을 쓰도록 컨트롤을 작게 만든 버튼 */
@Composable
private fun SmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = false,
) {
    val pad = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    val m = modifier.height(36.dp)
    if (filled) {
        Button(onClick = onClick, modifier = m, enabled = enabled, contentPadding = pad) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = m, enabled = enabled, contentPadding = pad) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun PointsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val video = vm.videos[vm.currentVideoIndex]
    val duration = video.durationMs
    val videoPoints = vm.pointsOf(video.id)

    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setSeekParameters(SeekParameters.EXACT)
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    LaunchedEffect(video.id) {
        player.setMediaItem(MediaItem.fromUri(video.uri))
        player.prepare()
        player.playWhenReady = false
    }

    var pos by remember(video.id) { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    // 위치를 옮기거나 미리볼 포인트 (칩을 탭하거나 포인트를 찍으면 선택된다)
    var selected by remember(video.id) { mutableStateOf<Long?>(null) }
    var previewReq by remember(video.id) { mutableStateOf<PreviewReq?>(null) }

    // 잘리는 구간만 재생하고 끝나면 멈춘 뒤, 포인트 위치로 돌아와서 계속 조절할 수 있게 한다
    LaunchedEffect(previewReq) {
        val req = previewReq ?: return@LaunchedEffect
        player.seekTo(req.start)
        player.play()
        var waited = 0L
        val limit = (req.end - req.start) + 3000L
        while (isActive && player.currentPosition < req.end && waited < limit) {
            delay(20)
            waited += 20
        }
        player.pause()
        player.seekTo(req.point)
    }

    LaunchedEffect(video.id) {
        while (true) {
            if (!scrubbing) pos = player.currentPosition.coerceIn(0L, duration)
            delay(50)
        }
    }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    fun seekBy(deltaMs: Long) {
        player.pause()
        val t = (player.currentPosition + deltaMs).coerceIn(0L, duration)
        player.seekTo(t)
        pos = t
    }

    val markerColor = MaterialTheme.colorScheme.tertiary

    // 선택된 포인트와, 그 포인트가 실제로 잘리는 구간
    val sel0 = selected?.takeIf { it in videoPoints }
    val selRange = sel0?.let { clipRange(it, duration, vm.lengthOfPoint(video.id, it)) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 머리글 한 줄: 돌아가기 · 영상 번호 · 현재 시간
        Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { vm.back() },
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) { Text("‹ 다시 선택", style = MaterialTheme.typography.labelLarge) }
            Text(
                "영상 ${vm.currentVideoIndex + 1}/${vm.videos.size}",
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            Text("${formatTime(pos)} / ${formatTime(duration)}", style = MaterialTheme.typography.labelLarge)
        }

        // 영상: 남는 공간을 모두 차지한다
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    PlayerView(c).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = player
                    }
                },
            )
        }

        // 슬라이더 위에 포인트 마커와 잘리는 구간 띠를 겹쳐서 그린다
        Box(
            Modifier.fillMaxWidth().height(40.dp).drawBehind {
                val pad = 12.dp.toPx()
                val w = size.width - pad * 2
                val d = duration.coerceAtLeast(1L)
                val cy = size.height / 2
                selRange?.let { r ->
                    val x1 = pad + w * r.first / d
                    val x2 = pad + w * r.second / d
                    drawRect(markerColor.copy(alpha = 0.35f), Offset(x1, cy - 12.dp.toPx()), Size(x2 - x1, 24.dp.toPx()))
                }
                videoPoints.forEach { p ->
                    val x = pad + w * p / d
                    drawLine(markerColor, Offset(x, cy - 12.dp.toPx()), Offset(x, cy + 12.dp.toPx()), strokeWidth = 3.dp.toPx())
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Slider(
                value = pos.toFloat(),
                onValueChange = {
                    scrubbing = true
                    pos = it.toLong()
                    player.pause()
                    player.seekTo(pos)
                },
                onValueChangeFinished = { scrubbing = false },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            )
        }

        // 이동 버튼 한 줄
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallButton("-1초", { seekBy(-1000) }, Modifier.weight(1f))
            SmallButton("-0.1", { seekBy(-100) }, Modifier.weight(1f))
            IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }, modifier = Modifier.height(36.dp)) {
                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "재생/일시정지")
            }
            SmallButton("+0.1", { seekBy(100) }, Modifier.weight(1f))
            SmallButton("+1초", { seekBy(1000) }, Modifier.weight(1f))
        }

        // 클립 길이: 라벨과 선택을 한 줄에
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("클립 길이", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
            LengthSelector(
                selectedMs = vm.clipLenMs,
                onSelect = { vm.setClipLength(it) },
                compact = true,
                modifier = Modifier.weight(1f),
            )
        }

        // 포인트 추가 + 잘리는 구간 미리보기를 한 줄에
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallButton(
                "★ 포인트 추가 (${formatTime(pos)})",
                onClick = {
                    player.pause()
                    when (vm.addPoint(video.id, pos)) {
                        MainViewModel.AddResult.DUPLICATE ->
                            Toast.makeText(ctx, "이미 같은 지점에 포인트가 있어요", Toast.LENGTH_SHORT).show()
                        MainViewModel.AddResult.OVERLAPPED -> {
                            selected = pos
                            Toast.makeText(ctx, "다른 포인트와 길이가 겹쳐요", Toast.LENGTH_SHORT).show()
                        }
                        // 찍자마자 선택해서, 바로 미리보기로 잘리는 구간을 확인할 수 있게 한다
                        MainViewModel.AddResult.ADDED -> selected = pos
                    }
                },
                modifier = Modifier.weight(1.5f),
                filled = true,
            )
            SmallButton(
                "▶ 구간 미리보기",
                onClick = {
                    val r = selRange
                    val p = sel0
                    if (r != null && p != null) previewReq = PreviewReq(r.first, r.second, p, (previewReq?.n ?: 0) + 1)
                },
                modifier = Modifier.weight(1f),
                enabled = selRange != null,
            )
        }

        // 찍은 포인트 칩 + 선택한 포인트를 현재 위치로 옮기기
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (videoPoints.isEmpty()) {
                Text(
                    "영상을 움직여 원하는 지점에서 ★ 를 눌러 포인트를 추가하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                )
            } else {
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(videoPoints, key = { it }) { p ->
                        FilterChip(
                            selected = p == selected,
                            onClick = {
                                player.pause()
                                player.seekTo(p)
                                pos = p
                                selected = p
                            },
                            label = { Text("${formatTime(p)} · ${formatSeconds(vm.lengthOfPoint(video.id, p))}초") },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        vm.removePoint(video.id, p)
                                        if (selected == p) selected = null
                                    },
                                    modifier = Modifier.height(20.dp),
                                ) { Icon(Icons.Filled.Close, contentDescription = "삭제") }
                            },
                        )
                    }
                }
                if (sel0 != null) {
                    SmallButton(
                        "여기로 옮기기",
                        onClick = {
                            player.pause()
                            when (vm.movePoint(video.id, sel0, pos)) {
                                MainViewModel.AddResult.DUPLICATE ->
                                    Toast.makeText(ctx, "그 위치에는 이미 포인트가 있어요", Toast.LENGTH_SHORT).show()
                                MainViewModel.AddResult.OVERLAPPED -> {
                                    selected = pos
                                    Toast.makeText(ctx, "다른 포인트와 길이가 겹쳐요", Toast.LENGTH_SHORT).show()
                                }
                                MainViewModel.AddResult.ADDED -> selected = pos
                            }
                        },
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallButton(
                "이전 영상",
                onClick = { player.pause(); vm.currentVideoIndex-- },
                modifier = Modifier.weight(1f),
                enabled = vm.currentVideoIndex > 0,
            )
            SmallButton(
                "다음 영상",
                onClick = { player.pause(); vm.currentVideoIndex++ },
                modifier = Modifier.weight(1f),
                enabled = vm.currentVideoIndex < vm.videos.lastIndex,
            )
            SmallButton(
                "순서 정리 (${vm.totalPoints})",
                onClick = { player.pause(); vm.goToOrder() },
                modifier = Modifier.weight(1.3f),
                enabled = vm.totalPoints > 0,
                filled = true,
            )
        }
    }
}
