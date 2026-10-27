package com.example.metaguardrail.monitor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** Local, best-effort nudity/suggestive-image cue classifier. It does not classify violence. */
class VisualContentClassifier(context: Context) : Closeable {
    private val interpreter: Interpreter = Interpreter(loadModel(context))
    private val inputBuffer = ByteBuffer.allocateDirect(1 * IMAGE_SIZE * IMAGE_SIZE * CHANNELS * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
    private val output = Array(1) { FloatArray(CLASS_COUNT) }

    /** Returns a possible adult-image cue when the combined adult class score reaches the threshold. */
    fun possibleAdultImage(bitmap: Bitmap): Boolean {
        fillInput(bitmap)
        interpreter.run(inputBuffer, output)
        val scores = output[0]
        val adultScore = scores[HENTAI_INDEX] + scores[PORN_INDEX] + scores[SEXY_INDEX]
        return adultScore >= ADULT_SCORE_THRESHOLD
    }

    private fun fillInput(bitmap: Bitmap) {
        inputBuffer.rewind()
        val side = minOf(bitmap.width, bitmap.height)
        val left = (bitmap.width - side) / 2
        val top = (bitmap.height - side) / 2
        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        val crop = Bitmap.createBitmap(bitmap, left, top, side, side)
        val resized = Bitmap.createScaledBitmap(crop, IMAGE_SIZE, IMAGE_SIZE, true)
        crop.recycle()
        resized.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)
        resized.recycle()

        pixels.forEach { pixel ->
            inputBuffer.putFloat(Color.red(pixel) / 255f)
            inputBuffer.putFloat(Color.green(pixel) / 255f)
            inputBuffer.putFloat(Color.blue(pixel) / 255f)
        }
        inputBuffer.rewind()
    }

    override fun close() = interpreter.close()

    private fun loadModel(context: Context): java.nio.MappedByteBuffer {
        val modelFile = File(context.codeCacheDir, MODEL_CACHE_FILENAME)
        if (!modelFile.isFile || modelFile.length() < MIN_MODEL_BYTES) {
            modelFile.delete()
            val stagingFile = File(context.codeCacheDir, "$MODEL_CACHE_FILENAME.part")
            stagingFile.delete()
            context.assets.open(MODEL_ASSET).use { asset ->
                InflaterInputStream(asset, Inflater(true)).use { input ->
                    FileOutputStream(stagingFile).use { output -> input.copyTo(output) }
                }
            }
            if (stagingFile.length() < MIN_MODEL_BYTES || !stagingFile.renameTo(modelFile)) {
                stagingFile.delete()
                throw IllegalStateException("Could not prepare the image safety model")
            }
        }
        FileInputStream(modelFile).channel.use { channel ->
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
        }
    }

    companion object {
        private const val MODEL_ASSET = "moderation/nsfw.tflite.deflate"
        private const val MODEL_CACHE_FILENAME = "nsfw-v1.tflite"
        private const val MIN_MODEL_BYTES = 10_000_000L
        private const val IMAGE_SIZE = 224
        private const val CHANNELS = 3
        private const val CLASS_COUNT = 5
        private const val HENTAI_INDEX = 1
        private const val PORN_INDEX = 3
        private const val SEXY_INDEX = 4
        private const val ADULT_SCORE_THRESHOLD = 0.70f
    }
}
