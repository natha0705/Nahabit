package com.nahabit.tracker.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nahabit.tracker.R
import com.nahabit.tracker.core.ui.theme.LocalCardSpacing
import com.nahabit.tracker.core.ui.theme.NahabitAmber
import com.nahabit.tracker.core.ui.theme.NahabitCyan
import com.nahabit.tracker.core.ui.theme.NahabitEmerald
import com.nahabit.tracker.habits.HabitWithStreakUiModel
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DualProgressRings(
    habitsProgress: Float,
    waterProgress: Float,
    modifier: Modifier = Modifier,
    ringSize: Dp = 140.dp
) {
    val animatedHabits by animateFloatAsState(
        targetValue = habitsProgress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "habitsRing"
    )
    val animatedWater by animateFloatAsState(
        targetValue = waterProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "waterRing"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val outerStroke = 14.dp.toPx()
            val innerStroke = 12.dp.toPx()
            val gap = 8.dp.toPx()

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = outerStroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = NahabitEmerald,
                startAngle = -90f,
                sweepAngle = 360f * animatedHabits,
                useCenter = false,
                style = Stroke(width = outerStroke, cap = StrokeCap.Round)
            )

            val inset = outerStroke + gap
            val innerDiameter = size.minDimension - (inset * 2)
            if (innerDiameter > 0f) {
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(innerDiameter, innerDiameter),
                    style = Stroke(width = innerStroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = NahabitCyan,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedWater,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(innerDiameter, innerDiameter),
                    style = Stroke(width = innerStroke, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(animatedHabits * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = stringResource(R.string.today_ring_habits),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CelebrationConfettiBurst(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val infiniteTransition = rememberInfiniteTransition(label = "confetti")
    val burstProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particles"
    )

    val colors = remember {
        listOf(NahabitEmerald, NahabitCyan, NahabitAmber, Color(0xFFA855F7))
    }

    Canvas(modifier = modifier.fillMaxWidth().height(48.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val particleCount = 18
        for (i in 0 until particleCount) {
            val angle = (i * (360f / particleCount)) * (Math.PI / 180.0)
            val maxRadius = size.width * 0.42f
            val distance = maxRadius * burstProgress
            val x = center.x + (cos(angle) * distance).toFloat()
            val y = center.y + (sin(angle) * distance * 0.35f).toFloat()
            val alpha = (1f - burstProgress).coerceIn(0f, 1f)

            drawCircle(
                color = colors[i % colors.size].copy(alpha = alpha),
                radius = 5.dp.toPx() * (1f - burstProgress * 0.4f),
                center = Offset(x, y)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableHabitCard(
    item: HabitWithStreakUiModel,
    onIncrement: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    val spacing = LocalCardSpacing.current

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onIncrement()
            }
            false
        }
    )

    val containerColor by animateColorAsState(
        targetValue = if (item.completedToday) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "habitCardBg"
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(NahabitEmerald.copy(alpha = 0.25f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = NahabitEmerald
                )
            }
        }
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = containerColor)
        ) {
            Row(
                modifier = Modifier
                    .padding(spacing)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${item.definition.iconEmoji} ${item.definition.title}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = NahabitAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(
                                R.string.habits_streak_label,
                                item.currentStreak,
                                item.bestStreak
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onIncrement()
                        },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics {
                                contentDescription = item.definition.title
                            }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${item.todayValue}/${item.definition.targetValue}")
                    }

                    if (onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete)
                            )
                        }
                    }
                }
            }
        }
    }
}
