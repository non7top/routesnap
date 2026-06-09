package com.routesnap.app.data.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.nio.ByteOrder
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
    private const val MAX_PCM_SAMPLES = 48_000 * 2 * 300

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
        val trackIndex = findAudioTrack(extractor) ?: return Pair(List(barCount) { 0.3f }, 0L)

        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val durationMs =
            if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1000 else 0L

        val pcm = decodePcm(format, extractor)
        extractor.release()

        return Pair(rmsWaveform(pcm, barCount), durationMs)
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? =
        (0 until extractor.trackCount).firstOrNull { i ->
            extractor
                .getTrackFormat(i)
                .getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        }

    private fun decodePcm(
        format: MediaFormat,
        extractor: MediaExtractor,
    ): List<Short> {
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcm = mutableListOf<Short>()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone && pcm.size < MAX_PCM_SAMPLES) {
            feedInput(codec, extractor, inputDone).also { inputDone = it }
            outputDone = drainOutput(codec, info, pcm)
        }

        codec.stop()
        codec.release()
        return pcm
    }

    private fun feedInput(
        codec: MediaCodec,
        extractor: MediaExtractor,
        alreadyDone: Boolean,
    ): Boolean {
        if (alreadyDone) return true
        val idx = codec.dequeueInputBuffer(TIMEOUT_US)
        val done =
            if (idx < 0) {
                false
            } else {
                val buf = codec.getInputBuffer(idx)!!
                val size = extractor.readSampleData(buf, 0)
                if (size < 0) {
                    codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    true
                } else {
                    codec.queueInputBuffer(idx, 0, size, extractor.sampleTime, 0)
                    extractor.advance()
                    false
                }
            }
        return done
    }

    private fun drainOutput(
        codec: MediaCodec,
        info: MediaCodec.BufferInfo,
        pcm: MutableList<Short>,
    ): Boolean {
        val idx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
        if (idx < 0) return false
        val buf = codec.getOutputBuffer(idx)!!
        buf.order(ByteOrder.LITTLE_ENDIAN)
        while (buf.remaining() >= 2) pcm.add(buf.short)
        codec.releaseOutputBuffer(idx, false)
        return info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
    }

    private fun rmsWaveform(
        pcm: List<Short>,
        barCount: Int,
    ): List<Float> {
        if (pcm.isEmpty()) return List(barCount) { 0.3f }
        val chunkSize = (pcm.size / barCount).coerceAtLeast(1)
        val rms =
            (0 until barCount).map { bar ->
                val from = bar * chunkSize
                if (from >= pcm.size) return@map 0f
                val to = minOf(from + chunkSize, pcm.size)
                var sumSq = 0.0
                for (i in from until to) {
                    val s = pcm[i] / 32768.0
                    sumSq += s * s
                }
                sqrt(sumSq / (to - from)).toFloat()
            }
        val peak = rms.max().takeIf { it > 0f } ?: 1f
        return rms.map { it / peak }
    }
}
