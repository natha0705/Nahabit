package com.nahabit.tracker.productivity

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.random.Random

enum class AmbientSoundMode {
    OFF, RAIN, WHITE_NOISE
}

class AmbientSoundGenerator {
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start(mode: AmbientSoundMode) {
        stop()
        if (mode == AmbientSoundMode.OFF) return

        job = scope.launch {
            val sampleRate = 22050
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val buffer = ShortArray(1024)
            var lastOut = 0f

            try {
                audioTrack.play()
                while (isActive) {
                    for (i in buffer.indices) {
                        val white = (Random.nextFloat() * 2f) - 1f
                        val sample = if (mode == AmbientSoundMode.RAIN) {
                            lastOut = (lastOut + (0.08f * (white - lastOut)))
                            (lastOut * 3800f).toInt().toShort()
                        } else {
                            (white * 1600f).toInt().toShort()
                        }
                        buffer[i] = sample
                    }
                    audioTrack.write(buffer, 0, buffer.size)
                }
            } catch (_: Exception) {
            } finally {
                runCatching {
                    audioTrack.stop()
                    audioTrack.release()
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
