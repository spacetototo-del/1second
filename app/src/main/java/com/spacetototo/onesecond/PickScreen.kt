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
                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            }) { Text("영상 선택하기") }
        }
    }
}
