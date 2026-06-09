package com.routesnap.app.data.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes an audio file to a normalized waveform suitable for display.
 * Returns (amplitudes 0..1, trackDurationMs).
 */
object MusicWaveformExtractor {
    private const val TAG = "MusicWaveformExtractor"
    private const val TIMEOUT_US = 10_000L

    suspend fun extract(
        context: Context,
        uri: Uri,
        barCount: Int = 300,
    ): Pair<List<Float>, Long> =
        withContext(Dispatchers.IO) {
            try {
                decode(context, uri, barCount)
            } catch (e: Exception) {
                Log.w(TAG, "Waveform extraction failed, using flat placeholder", e)
                Pair(List(barCount) { 0.3f }, 0L)
            }
        }

    private fun decode(
        context: Context,
        uri: Uri,
        barCount: Int,
    ): Pair<List<Float>, Long> {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        val trackIndex =
            (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return Pair(List(barCount) { 0.3f }, 0L)

        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        val durationMs = durationUs / 1000

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmSamples = mutableListOf<Short>()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuf = codec.getInputBuffer(inputIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuf, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outputIndex >= 0) {
                val outputBuf = codec.getOutputBuffer(outputIndex)!!
                outputBuf.order(ByteOrder.LITTLE_ENDIAN)
                while (outputBuf.remaining() >= 2) {
                    pcmSamples.add(outputBuf.short)
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
            }

            // Safety: stop if we have enough samples (avoid OOM for very long tracks)
            if (pcmSamples.size > 48_000 * 2 * 300) break
        }

        codec.stop()
        codec.release()
        extractor.release()

        if (pcmSamples.isEmpty()) return Pair(List(barCount) { 0.3f }, durationMs)

        val chunkSize = (pcmSamples.size / barCount).coerceAtLeast(1)
        val rms =
            (0 until barCount).map { bar ->
                val from = bar * chunkSize
                val to = minOf(from + chunkSize, pcmSamples.size)
                if (from >= pcmSamples.size) return@map 0f
                var sumSq = 0.0
                for (i in from until to) {
                    val s = pcmSamples[i] / 32768.0
                    sumSq += s * s
                }
                sqrt(sumSq / (to - from)).toFloat()
            }

        val peak = rms.max().takeIf { it > 0f } ?: 1f
        return Pair(rms.map { it / peak }, durationMs)
    }
}
