package com.spacetototo.onesecond

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PickScreen(vm: MainViewModel) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris -> if (uris.isNotEmpty()) vm.loadVideos(uris) }

    // 기본 선택 창: 삼성 갤러리, 파일, 구글 포토 등 설치된 앱 중에서 고를 수 있다
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> if (uris.isNotEmpty()) vm.loadVideos(uris) }

    // 파일 앱(내 파일/기기 저장소)에서 직접 고르는 방식
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) vm.loadVideos(uris) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("1초", style = MaterialTheme.typography.displayLarge)
        Text(
            "영상 여러 개를 고르고, 각 영상에서 포인트를 찍으면\n앞뒤 0.5초씩 1초를 잘라 하나의 릴스로 이어 붙여줘요.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (vm.loading) {
            CircularProgressIndicator()
            Text("영상 정보를 읽는 중…")
        } else {
            Button(onClick = {
                galleryLauncher.launch("video/*")
            }) { Text("갤러리에서 선택") }
            OutlinedButton(onClick = {
                fileLauncher.launch(arrayOf("video/*"))
            }) { Text("파일에서 선택 (내 폰 저장소)") }
            OutlinedButton(onClick = {
                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            }) { Text("구글 포토 / 사진 선택기") }
            Text(
                "'갤러리에서 선택'을 누르면 앱 선택 창이 떠요.\n삼성 갤러리(또는 갤러리)를 고르세요.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
