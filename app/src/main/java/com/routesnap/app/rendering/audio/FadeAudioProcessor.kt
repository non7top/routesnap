package com.routesnap.app.rendering.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Applies a linear volume ramp at the start and/or end of a single audio clip.
 *
 * @param fadeInDurationMs  ramp-up duration at clip start; 0 = instant
 * @param fadeOutDurationMs ramp-down duration at clip end; 0 = no tail fade
 * @param clipDurationMs    total clip duration (needed for tail-fade offset); 0 = skip tail fade
 */
@UnstableApi
class FadeAudioProcessor(
    private val fadeInDurationMs: Long,
    private val fadeOutDurationMs: Long,
    private val clipDurationMs: Long,
) : BaseAudioProcessor() {
    private var sampleRate = 0
    private var channelCount = 0
    private var processedFrames = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun onFlush() {
        processedFrames = 0L
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val output = replaceOutputBuffer(remaining)

        val bytesPerFrame = channelCount * 2 // PCM_16BIT
        while (inputBuffer.remaining() >= bytesPerFrame) {
            val currentMs = processedFrames * 1000L / sampleRate
            val gain = calculateGain(currentMs)

            repeat(channelCount) {
                val sample = inputBuffer.short
                output.putShort((sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }
            processedFrames++
        }

        output.flip()
    }

    private fun calculateGain(currentMs: Long): Float {
        val fadeIn =
            if (fadeInDurationMs > 0 && currentMs < fadeInDurationMs) {
                currentMs.toFloat() / fadeInDurationMs
            } else {
                1f
            }

        val fadeOut =
            if (fadeOutDurationMs > 0 && clipDurationMs > 0) {
                val tailStart = clipDurationMs - fadeOutDurationMs
                if (currentMs > tailStart) {
                    1f - (currentMs - tailStart).toFloat() / fadeOutDurationMs
                } else {
                    1f
                }
            } else {
                1f
            }

        return minOf(fadeIn, fadeOut).coerceIn(0f, 1f)
    }
}
