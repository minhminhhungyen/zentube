package vn.zenvid.download

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Ghép luồng hình (không tiếng) và luồng tiếng thành một file, dùng MediaMuxer có sẵn của Android
 * – không cần FFmpeg, không mã hoá lại nên rất nhanh và giữ nguyên chất lượng.
 */
object Muxer {
    @SuppressLint("WrongConstant")
    fun mux(video: File, audio: File, out: File, webm: Boolean) {
        val vx = MediaExtractor().apply { setDataSource(video.path) }
        val ax = MediaExtractor().apply { setDataSource(audio.path) }
        val muxer = MediaMuxer(out.path, if (webm) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val vTrack = selectTrack(vx, "video/")
            val aTrack = selectTrack(ax, "audio/")
            val vFormat = vx.getTrackFormat(vTrack)
            val aFormat = ax.getTrackFormat(aTrack)
            val vOut = muxer.addTrack(vFormat)
            val aOut = muxer.addTrack(aFormat)
            muxer.start()

            val size = maxOf(maxInput(vFormat), maxInput(aFormat), 2 * 1024 * 1024)
            val buf = ByteBuffer.allocate(size)
            val info = MediaCodec.BufferInfo()
            var vDone = false
            var aDone = false
            // Ghi xen kẽ theo thời gian để file phát mượt và tua nhanh
            while (!vDone || !aDone) {
                val takeVideo = !vDone && (aDone || vx.sampleTime <= ax.sampleTime)
                val ex = if (takeVideo) vx else ax
                buf.clear()
                val n = ex.readSampleData(buf, 0)
                if (n < 0) {
                    if (takeVideo) vDone = true else aDone = true
                    continue
                }
                info.offset = 0
                info.size = n
                info.presentationTimeUs = ex.sampleTime
                info.flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                muxer.writeSampleData(if (takeVideo) vOut else aOut, buf, info)
                ex.advance()
            }
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            vx.release()
            ax.release()
        }
    }

    private fun selectTrack(ex: MediaExtractor, prefix: String): Int {
        for (i in 0 until ex.trackCount) {
            if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true) {
                ex.selectTrack(i)
                return i
            }
        }
        throw IllegalStateException("Không tìm thấy luồng $prefix trong file tải về")
    }

    private fun maxInput(f: MediaFormat) =
        if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
}
