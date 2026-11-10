package com.spacetototo.onesecond

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize().systemBarsPadding()) {
                    OneSecondApp(vm)
                }
            }
        }
    }
}

@Composable
fun OneSecondApp(vm: MainViewModel) {
    BackHandler(enabled = vm.stage != Stage.PICK) { vm.back() }
    when (vm.stage) {
        Stage.PICK -> PickScreen(vm)
        Stage.POINTS -> PointsScreen(vm)
        Stage.ORDER -> OrderScreen(vm)
        Stage.EXPORT -> ExportScreen(vm)
    }
}
