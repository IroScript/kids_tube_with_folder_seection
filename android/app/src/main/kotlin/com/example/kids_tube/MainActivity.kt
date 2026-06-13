package com.example.kids_tube

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.example.kids_tube/thumbnail"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "getVideoThumbnail") {
                val path = call.argument<String>("path")
                if (path != null) {
                    thread {
                        val bytes = getVideoThumbnail(path)
                        runOnUiThread {
                            if (bytes != null) {
                                result.success(bytes)
                            } else {
                                result.error("ERROR", "Failed to generate thumbnail", null)
                            }
                        }
                    }
                } else {
                    result.error("BAD_ARGUMENT", "Path is null", null)
                }
            } else {
                result.notImplemented()
            }
        }
    }

    private fun getVideoThumbnail(path: String): ByteArray? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null) {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
                return stream.toByteArray()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }
        return null
    }
}

