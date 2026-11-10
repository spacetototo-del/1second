@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.spacetototo.onesecond

import android.app.Application
import android.content.ContentValues
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Stage { PICK, POINTS, ORDER, EXPORT }

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Float, val saving: Boolean = false) : ExportState
    data class Done(val uri: Uri) : ExportState
    data class Error(val message: String) : ExportState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    var stage by mutableStateOf(Stage.PICK)
        private set
    var loading by mutableStateOf(false)
        private set
    var videos by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var currentVideoIndex by mutableIntStateOf(0)
    var points by mutableStateOf<Map<Long, List<Long>>>(emptyMap())
        private set
    var clips by mutableStateOf<List<Clip>>(emptyList())
        private set
    var exportState by mutableStateOf<ExportState>(ExportState.Idle)
        private set

    private var nextId = 1L
    private var transformer: Transformer? = null
    private var polling = false

    // ---------- 1. 영상 선택 ----------
    fun loadVideos(uris: List<Uri>) {
        viewModelScope.launch {
            loading = true
            val items = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    val d = readDuration(uri)
                    if (d > 0) VideoItem(nextId++, uri, d) else null
                }
            }
            loading = false
            if (items.isEmpty()) return@launch
            videos = items
            points = emptyMap()
            clips = emptyList()
            currentVideoIndex = 0
            exportState = ExportState.Idle
            stage = Stage.POINTS
        }
    }

    private fun readDuration(uri: Uri): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(getApplication(), uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            r.release()
        }
    }

    // ---------- 2. 포인트 ----------
    fun pointsOf(videoId: Long): List<Long> = points[videoId].orEmpty()

    enum class AddResult { ADDED, OVERLAPPED, DUPLICATE }

    /** 같은 지점이면 추가하지 않고, 1초 안쪽으로 겹치면 추가하되 알려준다. */
    fun addPoint(videoId: Long, ms: Long): AddResult {
        val list = pointsOf(videoId)
        if (list.any { kotlin.math.abs(it - ms) < 50 }) return AddResult.DUPLICATE
        val overlapped = list.any { kotlin.math.abs(it - ms) < CLIP_MS }
        points = points + (videoId to (list + ms).sorted())
        return if (overlapped) AddResult.OVERLAPPED else AddResult.ADDED
    }

    fun removePoint(videoId: Long, ms: Long) {
        points = points + (videoId to pointsOf(videoId).filter { it != ms })
    }

    val totalPoints: Int get() = points.values.sumOf { it.size }

    fun goToOrder() {
        // 기존 순서는 유지하고, 사라진 포인트의 클립은 빼고, 새 포인트는 뒤에 붙인다.
        val wanted = mutableListOf<Triple<VideoItem, Long, Pair<Long, Long>>>()
        for (v in videos) for (p in pointsOf(v.id)) wanted += Triple(v, p, clipRange(p, v.durationMs))
        val existing = clips.filter { c -> wanted.any { it.first.uri == c.uri && it.second == c.pointMs } }
        val added = wanted
            .filter { w -> existing.none { it.uri == w.first.uri && it.pointMs == w.second } }
            .map { (v, p, r) -> Clip(nextId++, v.uri, p, r.first, r.second) }
        clips = existing + added
        stage = Stage.ORDER
    }

    // ---------- 3. 순서 ----------
    fun moveClip(from: Int, to: Int) {
        if (from == to || from !in clips.indices || to !in clips.indices) return
        val m = clips.toMutableList()
        m.add(to, m.removeAt(from))
        clips = m
    }

    fun removeClip(id: Long) {
        val c = clips.firstOrNull { it.id == id } ?: return
        clips = clips.filter { it.id != id }
        val v = videos.firstOrNull { it.uri == c.uri } ?: return
        removePoint(v.id, c.pointMs)
    }

    // ---------- 4. 내보내기 ----------
    fun goToExport() {
        if (clips.isEmpty()) return
        if (exportState is ExportState.Done || exportState is ExportState.Error) exportState = ExportState.Idle
        stage = Stage.EXPORT
    }

    /**
     * attempt 0: HDR→SDR(OpenGL) + 9:16 꽉채우기
     * attempt 1: HDR를 SDR로 간주 + 9:16 꽉채우기
     * attempt 2: 효과 없이 원본 크기 그대로 이어붙이기 (최후의 수단)
     */
    fun startExport(attempt: Int = 0) {
        if (clips.isEmpty()) return
        if (attempt == 0 && exportState is ExportState.Running) return
        val ctx = getApplication<Application>()
        val outFile = File(ctx.cacheDir, "export_${System.currentTimeMillis()}.mp4")
        exportState = ExportState.Running(0f)

        val items = clips.map { c ->
            val mediaItem = MediaItem.Builder()
                .setUri(c.uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(c.startMs)
                        .setEndPositionMs(c.endMs)
                        .build()
                )
                .build()
            val fit = Presentation.createForWidthAndHeight(
                OUT_WIDTH, OUT_HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            )
            val builder = EditedMediaItem.Builder(mediaItem)
            if (attempt < 2) builder.setEffects(Effects(emptyList(), listOf(fit)))
            builder.build()
        }
        val hdrMode = when (attempt) {
            0 -> Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
            1 -> Composition.HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR
            else -> Composition.HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR
        }
        val composition = Composition.Builder(listOf(EditedMediaItemSequence(items)))
            .setHdrMode(hdrMode)
            .build()

        val t = Transformer.Builder(ctx)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    exportState = ExportState.Running(1f, saving = true)
                    viewModelScope.launch {
                        try {
                            val uri = saveToGallery(outFile)
                            exportState = ExportState.Done(uri)
                        } catch (e: Exception) {
                            exportState = ExportState.Error("저장 실패: ${e.message}")
                        } finally {
                            polling = false
                        }
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    polling = false
                    outFile.delete()
                    if (attempt < 2) {
                        // 다른 방식으로 자동 재시도
                        startExport(attempt + 1)
                    } else {
                        val cause = generateSequence<Throwable>(exportException) { it.cause }
                            .joinToString(" ← ") { "${it.javaClass.simpleName}: ${it.message}" }
                        exportState = ExportState.Error(
                            "내보내기 실패 [${exportException.errorCodeName}]\n$cause"
                        )
                    }
                }
            })
            .build()
        transformer = t
        t.start(composition, outFile.absolutePath)

        polling = true
        viewModelScope.launch {
            val holder = ProgressHolder()
            while (polling) {
                val state = exportState
                if (state is ExportState.Running && !state.saving &&
                    t.getProgress(holder) != Transformer.PROGRESS_STATE_NOT_STARTED
                ) {
                    exportState = ExportState.Running(holder.progress / 100f)
                }
                delay(300)
            }
        }
    }

    private suspend fun saveToGallery(file: File): Uri = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "1second_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/OneSecond")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert 실패")
        resolver.openOutputStream(uri)!!.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        file.delete()
        uri
    }

    fun cancelExport() {
        polling = false
        transformer?.cancel()
        transformer = null
        exportState = ExportState.Idle
    }

    // ---------- 공통 ----------
    fun back() {
        when (stage) {
            Stage.PICK -> Unit
            Stage.POINTS -> stage = Stage.PICK
            Stage.ORDER -> stage = Stage.POINTS
            Stage.EXPORT -> {
                if (exportState is ExportState.Running) cancelExport()
                stage = Stage.ORDER
            }
        }
    }

    fun reset() {
        cancelExport()
        videos = emptyList()
        points = emptyMap()
        clips = emptyList()
        currentVideoIndex = 0
        stage = Stage.PICK
    }

    override fun onCleared() {
        polling = false
        transformer?.cancel()
        super.onCleared()
    }

    companion object {
        const val OUT_WIDTH = 1080
        const val OUT_HEIGHT = 1920
    }
}
