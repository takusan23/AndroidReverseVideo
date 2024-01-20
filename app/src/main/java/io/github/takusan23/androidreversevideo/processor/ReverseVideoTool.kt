package io.github.takusan23.androidreversevideo.processor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import io.github.takusan23.androidreversevideo.processor.video.CanvasVideoProcessor

object ReverseVideoTool {

    @SuppressLint("NewApi") // Android Pie 以降
    suspend fun start(
        context: Context,
        inputVideoUri: Uri
    ) {
        // 一時ファイル置き場
        val tempFolder = context.getExternalFilesDir(null)?.resolve("temp")?.apply { mkdir() }!!
        val reverseVideoFile = tempFolder.resolve("temp_reverse.mp4")

        // 元動画データを取り出すやつ
        val inputVideoMediaMetadataRetriever = MediaMetadataRetriever().apply { setDataSource(context, inputVideoUri) }
        val bitRate = inputVideoMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 3_000_000
        val videoWidth = inputVideoMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
        val videoHeight = inputVideoMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
        val frameRate = inputVideoMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toIntOrNull() ?: 30
        val durationMs = inputVideoMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toInt()!!

        // Canvas で逆から書く
        val paint = Paint()
        val textPaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.WHITE
            textSize = 50f
        }
        CanvasVideoProcessor.start(
            outFile = reverseVideoFile,
            bitRate = bitRate,
            frameRate = frameRate,
            outputVideoWidth = videoWidth,
            outputVideoHeight = videoHeight,
            onCanvasDrawRequest = { currentPositionMs ->
                // ここが Canvas なので、好きなように書く
                // 逆再生したときの、動画のフレームを取り出して、Canvas に書く。
                // getFrameAtTime はマイクロ秒なので注意
                val reverseCurrentPositionMs = durationMs - currentPositionMs
                val bitmap = inputVideoMediaMetadataRetriever.getFrameAtTime(reverseCurrentPositionMs * 1_000, MediaMetadataRetriever.OPTION_CLOSEST)
                drawColor(Color.BLACK)
                if (bitmap != null) {
                    drawBitmap(bitmap, 0f, 0f, paint)
                }
                drawText(currentPositionMs.toString(), 100f, 100f, textPaint)
                currentPositionMs <= durationMs
            }
        )
        println("おわり？")
    }

}