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
import androidx.compose.runtime.mutableLongStateOf
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

    private val prefs = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

    /** 기본 클립 길이 (기본값 1초, 마지막으로 고른 값을 기억한다) */
    var clipLenMs by mutableLongStateOf(
        prefs.getLong("clipLen", DEFAULT_CLIP_MS).takeIf { it in CLIP_LENGTHS } ?: DEFAULT_CLIP_MS
    )
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
                    val (d, audio) = readInfo(uri)
                    if (d > 0) VideoItem(nextId++, uri, d, audio) else null
                }
            }
            loading = false
            if (items.isEmpty()) return@launch
            videos = items
            points = emptyMap()
            pointLengths = emptyMap()
            clips = emptyList()
            currentVideoIndex = 0
            exportState = ExportState.Idle
            stage = Stage.POINTS
        }
    }

    /** (길이 ms, 소리 트랙 유무) */
    private fun readInfo(uri: Uri): Pair<Long, Boolean> {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(getApplication(), uri)
            val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val audio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            d to audio
        } catch (e: Exception) {
            0L to true
        } finally {
            r.release()
        }
    }

    // ---------- 2. 포인트 ----------
    fun pointsOf(videoId: Long): List<Long> = points[videoId].orEmpty()

    enum class AddResult { ADDED, OVERLAPPED, DUPLICATE }

    /** 포인트마다 찍은 시점의 클립 길이를 기억한다 (key = 영상 id to 포인트 ms) */
    private var pointLengths by mutableStateOf<Map<Pair<Long, Long>, Long>>(emptyMap())

    fun lengthOfPoint(videoId: Long, ms: Long): Long = pointLengths[videoId to ms] ?: clipLenMs

    /** 같은 지점이면 추가하지 않고, 클립 길이 안쪽으로 겹치면 추가하되 알려준다. */
    fun addPoint(videoId: Long, ms: Long): AddResult {
        val list = pointsOf(videoId)
        if (list.any { kotlin.math.abs(it - ms) < 50 }) return AddResult.DUPLICATE
        val overlapped = list.any { kotlin.math.abs(it - ms) < clipLenMs }
        points = points + (videoId to (list + ms).sorted())
        pointLengths = pointLengths + ((videoId to ms) to clipLenMs)
        return if (overlapped) AddResult.OVERLAPPED else AddResult.ADDED
    }

    fun removePoint(videoId: Long, ms: Long) {
        points = points + (videoId to pointsOf(videoId).filter { it != ms })
        pointLengths = pointLengths - (videoId to ms)
    }

    /** 이미 찍은 포인트를 새 위치로 옮긴다. 순서 화면의 클립 위치는 그대로 유지한다. */
    fun movePoint(videoId: Long, oldMs: Long, newMs: Long): AddResult {
        val video = videos.firstOrNull { it.id == videoId } ?: return AddResult.DUPLICATE
        val others = pointsOf(videoId).filter { it != oldMs }
        if (others.any { kotlin.math.abs(it - newMs) < 50 }) return AddResult.DUPLICATE
        val overlapped = others.any { kotlin.math.abs(it - newMs) < clipLenMs }
        val keptLength = lengthOfPoint(videoId, oldMs)
        points = points + (videoId to (others + newMs).sorted())
        pointLengths = pointLengths - (videoId to oldMs) + ((videoId to newMs) to keptLength)
        clips = clips.map {
            if (it.uri == video.uri && it.pointMs == oldMs) {
                val r = clipRange(newMs, video.durationMs, it.lengthMs)
                it.copy(pointMs = newMs, startMs = r.first, endMs = r.second)
            } else it
        }
        return if (overlapped) AddResult.OVERLAPPED else AddResult.ADDED
    }

    val totalPoints: Int get() = points.values.sumOf { it.size }

    fun goToOrder() {
        // 기존 순서는 유지하고, 사라진 포인트의 클립은 빼고, 새 포인트는 뒤에 붙인다.
        val wanted = mutableListOf<Triple<VideoItem, Long, Long>>() // 영상, 포인트, 그 포인트의 길이
        for (v in videos) for (p in pointsOf(v.id)) wanted += Triple(v, p, lengthOfPoint(v.id, p))
        val existing = clips.filter { c -> wanted.any { it.first.uri == c.uri && it.second == c.pointMs } }
        val added = wanted
            .filter { w -> existing.none { it.uri == w.first.uri && it.pointMs == w.second } }
            .map { (v, p, len) ->
                val r = clipRange(p, v.durationMs, len)
                Clip(nextId++, v.uri, p, r.first, r.second, lengthMs = len)
            }
        clips = existing + added
        stage = Stage.ORDER
    }

    // ---------- 클립 길이 ----------
    /** 앞으로 새로 찍는 포인트의 기본 길이. 이미 찍은 포인트는 바뀌지 않는다. */
    fun setClipLength(ms: Long) {
        if (ms == clipLenMs) return
        clipLenMs = ms
        prefs.edit().putLong("clipLen", ms).apply()
    }

    /** 순서 화면에서 클립 하나의 길이만 바꾼다. */
    fun setClipLengthFor(id: Long, ms: Long) {
        val c = clips.firstOrNull { it.id == id } ?: return
        val video = videos.firstOrNull { it.uri == c.uri }
        val r = if (video != null) clipRange(c.pointMs, video.durationMs, ms) else c.startMs to c.endMs
        clips = clips.map { if (it.id == id) it.copy(startMs = r.first, endMs = r.second, lengthMs = ms) else it }
        if (video != null) pointLengths = pointLengths + ((video.id to c.pointMs) to ms)
    }

    val totalMs: Long get() = clips.sumOf { it.endMs - it.startMs }

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
        // 백그라운드로 가도 프로세스가 죽지 않도록 포그라운드 서비스를 켠다 (재시도 때는 이미 켜져 있음)
        if (attempt == 0) ExportService.start(ctx)

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
        // 소리 없는 영상이 섞여 있어도 무음 트랙을 채워 넣어 끝까지 이어 붙인다
        val anyAudio = videos.any { it.hasAudio }
        val sequenceBuilder = EditedMediaItemSequence.Builder()
        items.forEach { sequenceBuilder.addItem(it) }
        val sequence = sequenceBuilder.build()
        val composition = Composition.Builder(listOf(sequence))
            .setHdrMode(hdrMode)
            .experimentalSetForceAudioTrack(anyAudio)
            .build()

        val t = Transformer.Builder(ctx)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    exportState = ExportState.Running(1f, saving = true)
                    viewModelScope.launch {
                        try {
                            val uri = saveToGallery(outFile)
                            exportState = ExportState.Done(uri)
                            ExportService.stop(ctx)
                            ExportService.notifyDone(ctx, success = true)
                        } catch (e: Exception) {
                            exportState = ExportState.Error("저장 실패: ${e.message}")
                            ExportService.stop(ctx)
                            ExportService.notifyDone(ctx, success = false)
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
                        ExportService.stop(ctx)
                        ExportService.notifyDone(ctx, success = false)
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
                    ExportService.updateProgress(ctx, holder.progress)
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
        ExportService.stop(getApplication())
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
        pointLengths = emptyMap()
        clips = emptyList()
        currentVideoIndex = 0
        stage = Stage.PICK
    }

    override fun onCleared() {
        polling = false
        transformer?.cancel()
        ExportService.stop(getApplication())
        super.onCleared()
    }

    companion object {
        const val OUT_WIDTH = 1080
        const val OUT_HEIGHT = 1920
    }
}
