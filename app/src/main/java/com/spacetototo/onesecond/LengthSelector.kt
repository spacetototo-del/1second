package com.spacetototo.onesecond

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** 1초 / 1.5초 / 2초 선택. compact=true 면 순서 화면의 클립 카드용으로 작게 그린다. */
@Composable
fun LengthSelector(
    selectedMs: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(if (compact) 8.dp else 10.dp)
    Row(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(if (compact) 2.dp else 3.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
    ) {
        CLIP_LENGTHS.forEach { ms ->
            val on = ms == selectedMs
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(if (compact) 6.dp else 8.dp))
                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(ms) }
                    .padding(vertical = if (compact) 5.dp else 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${formatSeconds(ms)}초",
                    style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                    color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
