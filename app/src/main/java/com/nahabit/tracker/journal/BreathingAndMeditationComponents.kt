package com.nahabit.tracker.journal

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nahabit.tracker.R
import com.nahabit.tracker.core.ui.theme.NahabitCyan
import com.nahabit.tracker.core.ui.theme.NahabitEmerald
import kotlinx.coroutines.delay

enum class BreathingPattern(
    val titleRes: Int,
    val inhaleSec: Int,
    val holdInSec: Int,
    val exhaleSec: Int,
    val holdOutSec: Int
) {
    BOX(R.string.breath_box_title, 4, 4, 4, 4),
    RELAX_478(R.string.breath_478_title, 4, 7, 8, 0),
    COHERENT(R.string.breath_coherent_title, 5, 0, 5, 0)
}

@Composable
fun BreathingAndMeditationSection(
    completedSessionsCount: Int,
    totalMindfulMinutes: Int,
    onSessionCompleted: (String, Int) -> Unit
) {
    var selectedPattern by rememberSaveable { mutableStateOf(BreathingPattern.BOX) }
    var isBreathingActive by rememberSaveable { mutableStateOf(false) }
    var phaseTextRes by remember { mutableIntStateOf(R.string.breath_phase_inhale) }
    var targetScale by remember { mutableFloatStateOf(0.45f) }
    var phaseDurationMs by remember { mutableIntStateOf(4000) }

    var meditationMinutes by rememberSaveable { mutableIntStateOf(5) }
    var isMeditating by rememberSaveable { mutableStateOf(false) }
    var meditationEndRealtime by rememberSaveable { mutableLongStateOf(0L) }
    var meditationSecondsLeft by rememberSaveable { mutableIntStateOf(5 * 60) }

    val animatedCircleScale by animateFloatAsState(
        targetValue = if (isBreathingActive) targetScale else 0.45f,
        animationSpec = tween(durationMillis = phaseDurationMs, easing = LinearEasing),
        label = "breathScale"
    )

    LaunchedEffect(isBreathingActive, selectedPattern) {
        while (isBreathingActive) {
            phaseTextRes = R.string.breath_phase_inhale
            phaseDurationMs = selectedPattern.inhaleSec * 1000
            targetScale = 0.95f
            delay(phaseDurationMs.toLong())

            if (selectedPattern.holdInSec > 0 && isBreathingActive) {
                phaseTextRes = R.string.breath_phase_hold
                phaseDurationMs = selectedPattern.holdInSec * 1000
                targetScale = 0.95f
                delay(phaseDurationMs.toLong())
            }

            if (isBreathingActive) {
                phaseTextRes = R.string.breath_phase_exhale
                phaseDurationMs = selectedPattern.exhaleSec * 1000
                targetScale = 0.40f
                delay(phaseDurationMs.toLong())
            }

            if (selectedPattern.holdOutSec > 0 && isBreathingActive) {
                phaseTextRes = R.string.breath_phase_hold
                phaseDurationMs = selectedPattern.holdOutSec * 1000
                targetScale = 0.40f
                delay(phaseDurationMs.toLong())
            }
        }
    }

    LaunchedEffect(isMeditating, meditationEndRealtime) {
        while (isMeditating) {
            val diff = meditationEndRealtime - SystemClock.elapsedRealtime()
            if (diff <= 0L) {
                meditationSecondsLeft = 0
                isMeditating = false
                playMeditationBell()
                onSessionCompleted("MEDITATION", meditationMinutes)
                break
            } else {
                meditationSecondsLeft = ((diff + 999L) / 1000L).toInt()
            }
            delay(250L)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.affirmation_banner),
                modifier = Modifier.padding(14.dp),
                fontWeight = FontWeight.SemiBold
            )
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BreathingPattern.entries.forEach { pattern ->
                        FilterChip(
                            selected = selectedPattern == pattern,
                            onClick = {
                                selectedPattern = pattern
                                isBreathingActive = false
                            },
                            label = { Text(stringResource(pattern.titleRes)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Box(
                    modifier = Modifier.size(190.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val maxRadius = size.minDimension / 2f
                        drawCircle(
                            color = NahabitCyan.copy(alpha = 0.18f),
                            radius = maxRadius * 0.95f,
                            center = Offset(size.width / 2f, size.height / 2f)
                        )
                        drawCircle(
                            color = NahabitEmerald.copy(alpha = 0.55f),
                            radius = maxRadius * animatedCircleScale,
                            center = Offset(size.width / 2f, size.height / 2f)
                        )
                    }
                    Text(
                        text = if (isBreathingActive) stringResource(phaseTextRes) else stringResource(selectedPattern.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        if (isBreathingActive) {
                            isBreathingActive = false
                            onSessionCompleted(selectedPattern.name, 2)
                        } else {
                            isBreathingActive = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(
                        if (isBreathingActive) stringResource(R.string.breath_stop)
                        else stringResource(R.string.breath_start)
                    )
                }
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.meditation_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${meditationSecondsLeft / 60}m : ${"%02d".format(meditationSecondsLeft % 60)}s",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 10, 15).forEach { mins ->
                        AssistChip(
                            onClick = {
                                isMeditating = false
                                meditationMinutes = mins
                                meditationSecondsLeft = mins * 60
                            },
                            label = { Text("${mins}m") }
                        )
                    }
                }

                Button(
                    onClick = {
                        if (!isMeditating) {
                            playMeditationBell()
                            meditationEndRealtime = SystemClock.elapsedRealtime() + (meditationSecondsLeft * 1000L)
                            isMeditating = true
                        } else {
                            isMeditating = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(if (isMeditating) stringResource(R.string.breath_stop) else "Iniciar Meditación")
                }

                Text(
                    text = stringResource(R.string.meditation_sessions_done, completedSessionsCount, totalMindfulMinutes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun playMeditationBell() {
    runCatching {
        val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        toneGen.startTone(ToneGenerator.TONE_PROP_PROMPT, 600)
    }
}
