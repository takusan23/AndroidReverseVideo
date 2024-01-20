package io.github.takusan23.androidreversevideo.processor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.contentValuesOf
import io.github.takusan23.androidreversevideo.processor.audio.ReverseAudioProcessor
import io.github.takusan23.androidreversevideo.processor.tool.MediaExtractorTool
import io.github.takusan23.androidreversevideo.processor.tool.MediaMuxerTool
import io.github.takusan23.androidreversevideo.processor.tool.MediaStoreTool
import io.github.takusan23.androidreversevideo.processor.video.CanvasVideoProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object ReverseVideoTool {

    @SuppressLint("NewApi") // Android Pie 以降
    suspend fun start(
        context: Context,
        inputVideoUri: Uri
    ) = withContext(Dispatchers.Default) {
        // 一時ファイル置き場
        val tempFolder = context.getExternalFilesDir(null)?.resolve("temp")?.apply { mkdir() }!!
        val reverseVideoFile = tempFolder.resolve("temp_video_reverse.mp4")
        val reverseAudioFile = tempFolder.resolve("temp_audio_reverse.mp4")
        val resultFile = tempFolder.resolve("android_reverse_video_${System.currentTimeMillis()}.mp4")

        // 元動画データを取り出すやつ
        val inputVideoMediaMetadataRetriever = MediaMetadataRetriever().apply { setDataSource(context, inputVideoUri) }
        val bitRate = inputVideoMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 3_000_000
        val videoWidth = inputVideoMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
        val videoHeight = inputVideoMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
        val frameRate = inputVideoMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toIntOrNull() ?: 30
        val durationMs = inputVideoMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt()!!

        // 並列処理
        listOf(
            launch {
                // 音声を逆にする処理
                ReverseAudioProcessor.start(
                    context = context,
                    inFile = inputVideoUri,
                    outFile = reverseAudioFile,
                    tempFolder = tempFolder
                )
            },
            launch {
                // 映像を逆にする処理
                val paint = Paint()
                // Canvas で逆から書く
                CanvasVideoProcessor.start(
                    outFile = reverseVideoFile,
                    bitRate = bitRate,
                    frameRate = frameRate,
                    outputVideoWidth = videoWidth,
                    outputVideoHeight = videoHeight,
                    onCanvasDrawRequest = { currentPositionMs ->
                        println(currentPositionMs)
                        // ここが Canvas なので、好きなように書く
                        // 逆再生したときの、動画のフレームを取り出して、Canvas に書く。
                        // getFrameAtTime はマイクロ秒なので注意
                        val reverseCurrentPositionMs = durationMs - currentPositionMs
                        val bitmap = inputVideoMediaMetadataRetriever.getFrameAtTime(reverseCurrentPositionMs * 1_000, MediaMetadataRetriever.OPTION_CLOSEST)
                        if (bitmap != null) {
                            drawBitmap(bitmap, 0f, 0f, paint)
                        }
                        currentPositionMs <= durationMs
                    }
                )
            }
        ).joinAll()

        // 音声トラックと映像トラックを合わせる
        MediaMuxerTool.mixAvTrack(
            audioTrackFile = reverseAudioFile,
            videoTrackFile = reverseVideoFile,
            resultFile = resultFile
        )

        // 保存する
        MediaStoreTool.saveToVideoFolder(
            context = context,
            file = resultFile
        )

        // 要らないファイルを消す
        tempFolder.deleteRecursively()
    }

}