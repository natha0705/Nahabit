package com.nahabit.tracker.stats

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.nahabit.tracker.core.ui.theme.NahabitCyan
import com.nahabit.tracker.core.ui.theme.NahabitEmerald

@Composable
fun AnimatedWeeklyBarChart(
    rates: List<Float>,
    modifier: Modifier = Modifier
) {
    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
        label = "barsAnim"
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val labels = listOf("L", "M", "X", "J", "V", "S", "D")

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(horizontal = 8.dp)
        ) {
            if (rates.isEmpty()) return@Canvas
            val count = rates.size
            val slotWidth = size.width / count
            val barWidth = slotWidth * 0.52f
            val maxBarHeight = size.height

            rates.forEachIndexed { index, value ->
                val x = index * slotWidth + (slotWidth - barWidth) / 2f
                val barHeight = (value.coerceIn(0.06f, 1f) * maxBarHeight) * animProgress

                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, maxBarHeight),
                    cornerRadius = CornerRadius(12f, 12f)
                )

                drawRoundRect(
                    color = if (value >= 0.85f) NahabitEmerald else NahabitCyan,
                    topLeft = Offset(x, maxBarHeight - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(12f, 12f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            labels.forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ContributionHeatmap90Days(
    intensities: List<Float>,
    modifier: Modifier = Modifier
) {
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        val cols = 13
        val rows = 7
        val gap = 4.dp.toPx()
        val cellWidth = (size.width - (gap * (cols - 1))) / cols
        val cellHeight = (size.height - (gap * (rows - 1))) / rows
        val cellSize = minOf(cellWidth, cellHeight)

        intensities.take(cols * rows).forEachIndexed { idx, level ->
            val col = idx / rows
            val row = idx % rows
            val x = col * (cellSize + gap)
            val y = row * (cellSize + gap)

            val cellColor = when {
                level <= 0.01f -> emptyColor
                level < 0.35f -> NahabitEmerald.copy(alpha = 0.35f)
                level < 0.70f -> NahabitEmerald.copy(alpha = 0.65f)
                else -> NahabitEmerald
            }

            drawRoundRect(
                color = cellColor,
                topLeft = Offset(x, y),
                size = Size(cellSize, cellSize),
                cornerRadius = CornerRadius(6f, 6f)
            )
        }
    }
}
