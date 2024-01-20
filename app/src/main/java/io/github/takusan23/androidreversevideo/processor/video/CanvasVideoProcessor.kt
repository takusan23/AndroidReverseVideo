package io.github.takusan23.androidreversevideo.processor.video

import android.content.Context
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

object CanvasVideoProcessor {

    /** タイムアウト */
    private const val TIMEOUT_US = 10000L

    /**
     * 処理を開始する
     *
     * @param outFile 出力先
     * @param codecName コーデック名
     * @param containerFormat コンテナフォーマット
     * @param bitRate ビットレート
     * @param frameRate フレームレート
     * @param outputVideoWidth 動画の高さ
     * @param outputVideoHeight 動画の幅
     * @param onCanvasDrawRequest Canvasの描画が必要になったら呼び出される。trueを返している間、動画を作成する
     */
    suspend fun start(
        outFile: File,
        bitRate: Int = 1_000_000,
        frameRate: Int = 30,
        outputVideoWidth: Int = 1280,
        outputVideoHeight: Int = 720,
        codecName: String = MediaFormat.MIMETYPE_VIDEO_AVC,
        containerFormat: Int = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        onCanvasDrawRequest: Canvas.(positionMs: Long) -> Boolean,
    ) = withContext(Dispatchers.Default) {
        val encodeMediaCodec = MediaCodec.createEncoderByType(codecName).apply {
            // エンコーダーにセットするMediaFormat
            // コーデックが指定されていればそっちを使う
            val videoMediaFormat = MediaFormat.createVideoFormat(codecName, outputVideoWidth, outputVideoHeight).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            }
            configure(videoMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }

        // エンコーダーのSurfaceを取得して、OpenGLを利用してCanvasを重ねます
        val canvasInputSurface = CanvasInputSurface(
            encodeMediaCodec.createInputSurface(),
            TextureRenderer(
                outputVideoWidth = outputVideoWidth,
                outputVideoHeight = outputVideoHeight
            )
        )
        // OpenGL
        canvasInputSurface.makeCurrent()
        encodeMediaCodec.start()
        canvasInputSurface.createRender()

        // マルチプレクサ
        var videoTrackIndex = -1
        val mediaMuxer = MediaMuxer(outFile.path, containerFormat)

        // メタデータ格納用
        val bufferInfo = MediaCodec.BufferInfo()
        var outputDone = false
        val startMs = System.currentTimeMillis()

        try {
            while (!outputDone) {

                // コルーチンキャンセル時は強制終了
                if (!isActive) break

                // Surface経由でデータを貰って保存する
                val encoderStatus = encodeMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (encoderStatus >= 0) {
                    val encodedData = encodeMediaCodec.getOutputBuffer(encoderStatus)!!
                    if (bufferInfo.size > 1) {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            // MediaMuxer へ addTrack した後
                            mediaMuxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                        }
                    }
                    encodeMediaCodec.releaseOutputBuffer(encoderStatus, false)
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // MediaMuxerへ映像トラックを追加するのはこのタイミングで行う
                    // このタイミングでやると固有のパラメーターがセットされたMediaFormatが手に入る(csd-0 とか)
                    // 映像がぶっ壊れている場合（緑で塗りつぶされてるとか）は多分このあたりが怪しい
                    val newFormat = encodeMediaCodec.outputFormat
                    videoTrackIndex = mediaMuxer.addTrack(newFormat)
                    mediaMuxer.start()
                }
                if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    continue
                }
                // OpenGLで描画する
                // Canvas の入力をする
                val presentationTimeUs = (System.currentTimeMillis() - startMs) * 1000
                var isRunning = false
                canvasInputSurface.drawCanvas { canvas ->
                    isRunning = onCanvasDrawRequest(canvas, presentationTimeUs / 1000L)
                }
                canvasInputSurface.setPresentationTime(presentationTimeUs * 1000)
                canvasInputSurface.swapBuffers()
                if (!isRunning) {
                    outputDone = true
                    encodeMediaCodec.signalEndOfInputStream()
                }
            }
        } finally {
            // OpenGL開放
            canvasInputSurface.release()
            // エンコーダー終了
            encodeMediaCodec.stop()
            encodeMediaCodec.release()
            // MediaMuxerも終了
            mediaMuxer.stop()
            mediaMuxer.release()
        }
    }
}