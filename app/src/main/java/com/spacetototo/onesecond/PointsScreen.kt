@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.spacetototo.onesecond

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.geometry.Size

/** 미리보기 요청 (n 은 같은 구간을 다시 눌러도 다시 재생되게 하는 카운터) */
private data class PreviewReq(val start: Long, val end: Long, val point: Long, val n: Int)

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
    // 위치를 옮길 포인트 (칩을 탭하면 선택된다)
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

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.back() }) { Text("‹ 영상 다시 선택") }
            Text(
                "영상 ${vm.currentVideoIndex + 1} / ${vm.videos.size}  ·  ${formatTime(duration)}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
        }

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

        // 타임라인 + 포인트 마커
        Column {
            Box(
                Modifier.fillMaxWidth().height(10.dp).drawBehind {
                    val pad = 12.dp.toPx()
                    val w = size.width - pad * 2
                    selRange?.let { r ->
                        val x1 = pad + w * r.first / duration.coerceAtLeast(1L)
                        val x2 = pad + w * r.second / duration.coerceAtLeast(1L)
                        drawRect(markerColor.copy(alpha = 0.35f), Offset(x1, 0f), Size(x2 - x1, size.height))
                    }
                    videoPoints.forEach { p ->
                        val x = pad + w * p / duration.coerceAtLeast(1L)
                        drawLine(markerColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
                    }
                }
            )
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
            Text("${formatTime(pos)} / ${formatTime(duration)}", style = MaterialTheme.typography.labelMedium)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { seekBy(-1000) }) { Text("-1초") }
            OutlinedButton(onClick = { seekBy(-100) }) { Text("-0.1") }
            IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "재생/일시정지")
            }
            OutlinedButton(onClick = { seekBy(100) }) { Text("+0.1") }
            OutlinedButton(onClick = { seekBy(1000) }) { Text("+1초") }
        }

        Column {
            Text("클립 길이", style = MaterialTheme.typography.labelLarge)
            LengthSelector(
                selectedMs = vm.clipLenMs,
                onSelect = { vm.setClipLength(it) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            Text(
                "지금부터 찍는 포인트는 앞뒤 ${formatSeconds(vm.clipLenMs / 2)}초씩, 총 ${formatSeconds(vm.clipLenMs)}초를 잘라요. 이미 찍은 포인트는 그대로예요.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Button(
            onClick = {
                player.pause()
                when (vm.addPoint(video.id, pos)) {
                    MainViewModel.AddResult.DUPLICATE ->
                        Toast.makeText(ctx, "이미 같은 지점에 포인트가 있어요", Toast.LENGTH_SHORT).show()
                    MainViewModel.AddResult.OVERLAPPED -> {
                        selected = pos
                        Toast.makeText(ctx, "다른 포인트와 길이가 겹쳐요", Toast.LENGTH_SHORT).show()
                    }
                    // 찍자마자 선택해서, 바로 미리보기 버튼으로 잘리는 구간을 확인할 수 있게 한다
                    MainViewModel.AddResult.ADDED -> selected = pos
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("★ 이 지점을 포인트로 추가 (${formatTime(pos)})") }

        if (selRange != null && sel0 != null) {
            OutlinedButton(
                onClick = {
                    previewReq = PreviewReq(selRange.first, selRange.second, sel0, (previewReq?.n ?: 0) + 1)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(
                    " 잘리는 구간 미리보기 ${formatTime(selRange.first)} ~ ${formatTime(selRange.second)} (${formatSeconds(selRange.second - selRange.first)}초)"
                )
            }
        }

        if (videoPoints.isEmpty()) {
            Text("아직 포인트가 없어요. 영상을 움직여서 원하는 지점에 추가하세요.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("포인트를 탭하면 그 위치로 이동하고, 영상을 움직인 뒤 '옮기기'로 위치를 수정할 수 있어요.", style = MaterialTheme.typography.bodySmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "삭제")
                            }
                        },
                    )
                }
            }
            val sel = selected
            if (sel != null && sel in videoPoints) {
                OutlinedButton(
                    onClick = {
                        player.pause()
                        when (vm.movePoint(video.id, sel, pos)) {
                            MainViewModel.AddResult.DUPLICATE ->
                                Toast.makeText(ctx, "그 위치에는 이미 포인트가 있어요", Toast.LENGTH_SHORT).show()
                            MainViewModel.AddResult.OVERLAPPED -> {
                                selected = pos
                                Toast.makeText(ctx, "다른 포인트와 1초 안쪽으로 겹쳐요", Toast.LENGTH_SHORT).show()
                            }
                            MainViewModel.AddResult.ADDED -> selected = pos
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("선택한 포인트(${formatTime(sel)})를 현재 위치(${formatTime(pos)})로 옮기기") }
            }
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { player.pause(); vm.currentVideoIndex-- },
                enabled = vm.currentVideoIndex > 0,
                modifier = Modifier.weight(1f),
            ) { Text("이전 영상") }
            OutlinedButton(
                onClick = { player.pause(); vm.currentVideoIndex++ },
                enabled = vm.currentVideoIndex < vm.videos.lastIndex,
                modifier = Modifier.weight(1f),
            ) { Text("다음 영상") }
            Button(
                onClick = { player.pause(); vm.goToOrder() },
                enabled = vm.totalPoints > 0,
                modifier = Modifier.weight(1.4f),
            ) { Text("순서 정리 (${vm.totalPoints})") }
        }
    }
}
