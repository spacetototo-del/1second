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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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

        Button(
            onClick = {
                player.pause()
                val overlapped = vm.addPoint(video.id, pos)
                if (overlapped) Toast.makeText(ctx, "다른 포인트와 1초 안쪽으로 겹쳐요", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("★ 이 지점을 포인트로 추가 (${formatTime(pos)})") }

        if (videoPoints.isEmpty()) {
            Text("아직 포인트가 없어요. 영상을 움직여서 원하는 지점에 추가하세요.", style = MaterialTheme.typography.bodySmall)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(videoPoints, key = { it }) { p ->
                    AssistChip(
                        onClick = {
                            player.pause()
                            player.seekTo(p)
                            pos = p
                        },
                        label = { Text(formatTime(p)) },
                        trailingIcon = {
                            IconButton(onClick = { vm.removePoint(video.id, p) }, modifier = Modifier.height(20.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "삭제")
                            }
                        },
                    )
                }
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
