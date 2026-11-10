package com.spacetototo.onesecond

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ExportScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val state = vm.exportState
    val view = LocalView.current
    val running = state is ExportState.Running

    // Android 13+ 에서는 진행 알림을 보이려면 알림 권한이 필요하다 (거부해도 내보내기는 진행됨)
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.startExport()
    }
    fun begin() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.startExport()
        }
    }

    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            ExportState.Idle -> {
                Text("내보내기", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "클립 ${vm.clips.size}개 · 약 ${vm.clips.size}초\n" +
                        "${MainViewModel.OUT_WIDTH}×${MainViewModel.OUT_HEIGHT} (9:16 릴스) · 원본 소리 유지",
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { begin() }, modifier = Modifier.fillMaxWidth()) { Text("영상 만들기") }
                OutlinedButton(onClick = { vm.back() }, modifier = Modifier.fillMaxWidth()) { Text("‹ 순서로 돌아가기") }
            }

            is ExportState.Running -> {
                Text(if (state.saving) "갤러리에 저장하는 중…" else "영상을 만드는 중…", style = MaterialTheme.typography.titleLarge)
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                Text("${(state.progress * 100).toInt()}%")
                Text("다른 앱으로 이동해도 계속 진행돼요. 알림에서 진행 상황을 볼 수 있어요.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                OutlinedButton(onClick = { vm.cancelExport() }) { Text("취소") }
            }

            is ExportState.Done -> {
                Text("완성!", style = MaterialTheme.typography.headlineMedium)
                Text("갤러리의 Movies/OneSecond 폴더에 저장됐어요.", textAlign = TextAlign.Center)
                Button(onClick = { shareToInstagram(ctx, state.uri) }, modifier = Modifier.fillMaxWidth()) {
                    Text("인스타그램에 공유")
                }
                OutlinedButton(onClick = { share(ctx, state.uri) }, modifier = Modifier.fillMaxWidth()) {
                    Text("다른 앱으로 공유")
                }
                OutlinedButton(onClick = { vm.back() }, modifier = Modifier.fillMaxWidth()) { Text("‹ 순서 다시 편집") }
                OutlinedButton(onClick = { vm.reset() }, modifier = Modifier.fillMaxWidth()) { Text("처음으로") }
            }

            is ExportState.Error -> {
                Text("문제가 생겼어요", style = MaterialTheme.typography.headlineMedium)
                Text(state.message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { begin() }, modifier = Modifier.fillMaxWidth()) { Text("다시 시도") }
                OutlinedButton(onClick = { vm.back() }, modifier = Modifier.fillMaxWidth()) { Text("‹ 순서로 돌아가기") }
            }
        }
    }
}

private fun sendIntent(uri: Uri) = Intent(Intent.ACTION_SEND).apply {
    type = "video/mp4"
    putExtra(Intent.EXTRA_STREAM, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

private fun share(ctx: Context, uri: Uri) {
    ctx.startActivity(Intent.createChooser(sendIntent(uri), "공유"))
}

private fun shareToInstagram(ctx: Context, uri: Uri) {
    try {
        ctx.startActivity(sendIntent(uri).setPackage("com.instagram.android"))
    } catch (e: ActivityNotFoundException) {
        share(ctx, uri)
    }
}
