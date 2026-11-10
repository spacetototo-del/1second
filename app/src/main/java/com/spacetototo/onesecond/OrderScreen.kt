@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.spacetototo.onesecond

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun OrderScreen(vm: MainViewModel) {
    val clips = vm.clips
    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    var preview by remember { mutableStateOf<Clip?>(null) }

    fun onDrag(id: Long) {
        val info = listState.layoutInfo.visibleItemsInfo
        val curInfo = info.firstOrNull { it.key == id } ?: return
        val cur = vm.clips.indexOfFirst { it.id == id }
        val center = curInfo.offset + curInfo.size / 2f + dragDelta
        val target = info.firstOrNull { it.key != id && center >= it.offset && center <= it.offset + it.size }
        if (target != null) {
            vm.moveClip(cur, target.index)
            dragDelta -= (target.offset - curInfo.offset)
        }
        // 화면 가장자리로 끌면 자동 스크롤
        val start = listState.layoutInfo.viewportStartOffset
        val end = listState.layoutInfo.viewportEndOffset
        if (center < start + 80) dragDelta += listState.dispatchRawDelta(-30f)
        else if (center > end - 80) dragDelta += listState.dispatchRawDelta(30f)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.back() }) { Text("‹ 포인트 수정") }
            Text(
                "클립 ${clips.size}개 · 총 ${clips.size}초",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "오른쪽 ≡ 를 길게 잡고 위아래로 끌어 순서를 바꾸세요. 썸네일을 누르면 1초 미리보기가 나와요.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(clips, key = { _, c -> c.id }) { index, clip ->
                val isDragging = draggingId == clip.id
                Card(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) dragDelta else 0f },
                ) {
                    Row(
                        Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("${index + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.size(28.dp))
                        Thumb(clip.uri, clip.startMs, Modifier.size(width = 56.dp, height = 100.dp).clickable { preview = clip })
                        Column(Modifier.weight(1f)) {
                            Text("${formatTime(clip.startMs)} ~ ${formatTime(clip.endMs)}")
                            Text("포인트 ${formatTime(clip.pointMs)}", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { vm.removeClip(clip.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "클립 삭제")
                        }
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = "순서 변경",
                            modifier = Modifier
                                .size(40.dp)
                                .padding(6.dp)
                                .pointerInput(clip.id) {
                                    detectDragGestures(
                                        onDragStart = { draggingId = clip.id; dragDelta = 0f },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragDelta += amount.y
                                            onDrag(clip.id)
                                        },
                                        onDragEnd = { draggingId = null; dragDelta = 0f },
                                        onDragCancel = { draggingId = null; dragDelta = 0f },
                                    )
                                },
                        )
                    }
                }
            }
        }

        Button(
            onClick = { vm.goToExport() },
            enabled = clips.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) { Text("내보내기 (${clips.size}초)") }
    }

    preview?.let { clip -> ClipPreviewDialog(clip) { preview = null } }
}

@Composable
private fun Thumb(uri: Uri, timeMs: Long, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uri, timeMs) {
        value = withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, uri)
                fun meta(k: Int) = r.extractMetadata(k)?.toIntOrNull() ?: 0
                var w = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                var h = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val rot = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
                val frame: Bitmap? = if (w > 0 && h > 0) {
                    val tw = 240
                    r.getScaledFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST, tw, tw * h / w)
                } else {
                    r.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                }
                frame?.asImageBitmap()
            } catch (e: Exception) {
                null
            } finally {
                r.release()
            }
        }
    }
    Box(modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant)) {
        bmp?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ClipPreviewDialog(clip: Clip, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(
                MediaItem.Builder()
                    .setUri(clip.uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.startMs)
                            .setEndPositionMs(clip.endMs)
                            .build()
                    )
                    .build()
            )
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        text = {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(420.dp),
                factory = { c ->
                    PlayerView(c).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = player
                    }
                },
            )
        },
    )
}
