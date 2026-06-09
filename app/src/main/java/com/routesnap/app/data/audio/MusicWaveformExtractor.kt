package com.routesnap.app.data.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes an audio file to a normalized waveform suitable for display.
 * Returns (amplitudes 0..1, trackDurationMs).
 *
 * Streams PCM through RMS accumulators — O(barCount) memory regardless of
 * track length, avoiding OOM on large files.
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
        val trackIndex = findAudioTrack(extractor) ?: return Pair(List(barCount) { 0.3f }, 0L)

        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val durationMs =
            if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1000 else 0L
        val sampleRate =
            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        val channels =
            if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

        val totalSamples = (durationMs * sampleRate / 1000L).coerceAtLeast(1L)
        val samplesPerBar = (totalSamples / barCount).coerceAtLeast(1L)

        val sumSq = DoubleArray(barCount)
        val counts = LongArray(barCount)
        streamDecode(format, extractor, sumSq, counts, barCount, samplesPerBar, channels)
        extractor.release()

        return Pair(normalise(sumSq, counts, barCount), durationMs)
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? =
        (0 until extractor.trackCount).firstOrNull { i ->
            extractor
                .getTrackFormat(i)
                .getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        }

    private fun streamDecode(
        format: MediaFormat,
        extractor: MediaExtractor,
        sumSq: DoubleArray,
        counts: LongArray,
        barCount: Int,
        samplesPerBar: Long,
        channels: Int,
    ) {
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var totalFrames = 0L

        while (!outputDone) {
            inputDone = feedInput(codec, extractor, inputDone)
            val idx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (idx >= 0) {
                val buf = codec.getOutputBuffer(idx)!!
                buf.order(ByteOrder.LITTLE_ENDIAN)
                totalFrames = drainBuffer(buf, channels, totalFrames, samplesPerBar, barCount, sumSq, counts)
                codec.releaseOutputBuffer(idx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }

        codec.stop()
        codec.release()
    }

    private fun drainBuffer(
        buf: ByteBuffer,
        channels: Int,
        startFrame: Long,
        samplesPerBar: Long,
        barCount: Int,
        sumSq: DoubleArray,
        counts: LongArray,
    ): Long {
        var frame = startFrame
        while (buf.remaining() >= channels * 2) {
            accumFrame(buf, channels, frame, samplesPerBar, barCount, sumSq, counts)
            frame++
        }
        return frame
    }

    private fun accumFrame(
        buf: ByteBuffer,
        channels: Int,
        frame: Long,
        samplesPerBar: Long,
        barCount: Int,
        sumSq: DoubleArray,
        counts: LongArray,
    ) {
        var mix = 0.0
        repeat(channels) { mix += buf.short / 32768.0 }
        val s = mix / channels
        val bar = (frame / samplesPerBar).toInt().coerceIn(0, barCount - 1)
        sumSq[bar] += s * s
        counts[bar]++
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

    private fun normalise(
        sumSq: DoubleArray,
        counts: LongArray,
        barCount: Int,
    ): List<Float> {
        val rms = FloatArray(barCount) { i -> if (counts[i] > 0) sqrt(sumSq[i] / counts[i]).toFloat() else 0f }
        val peak = rms.max().takeIf { it > 0f } ?: 1f
        return rms.map { (it / peak).coerceIn(0f, 1f) }
    }
}
