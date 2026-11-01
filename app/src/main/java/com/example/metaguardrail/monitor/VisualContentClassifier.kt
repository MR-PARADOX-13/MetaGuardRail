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

/**
 * Local, best-effort nudity/suggestive-image cue classifier.
 *
 * Uses a bundled TFLite model (`moderation/nsfw.tflite.deflate`, ~10 MB) to score
 * 224x224 center-cropped frames for adult classes (hentai, porn, sexy).
 *
 * **Limitations — false positives / false negatives:**
 * - The model is trained on a limited dataset and can misclassify clothed people,
 *   medical/educational imagery, art, or low-quality frames as adult content (false positives).
 * - Conversely it can miss explicit content, especially under unusual lighting, heavy blur,
 *   extreme crops, or content types it was not trained on (false negatives).
 * - It does **not** classify violence, gore, self-harm imagery, or non-sexual harassment —
 *   only sexual/suggestive nudity cues. Absence of a flag is not a guarantee of safety.
 * - Scores are thresholded at [ADULT_SCORE_THRESHOLD] (0.70) sum across adult classes;
 *   tuning this trades recall vs. precision. Callers needing different sensitivity should
 *   use [decodeOutputForTest] with custom thresholds.
 * - Inference runs on CPU via TFLite; keep calls off the main thread.
 *
 * All processing is on-device; frames are not uploaded.
 */
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
        return decodeOutputForTest(scores)
    }

    /**
     * Test hook: produces the normalized float input that would be fed to the TFLite model
     * for [bitmap]. Mirrors [fillInput] logic (center square crop → 224x224 resize → RGB / 255f)
     * but returns a new [FloatArray] instead of writing to the shared [inputBuffer], so it is
     * safe to call from tests without disturbing inference state.
     *
     * @return FloatArray of size [IMAGE_SIZE] * [IMAGE_SIZE] * [CHANNELS] in RGB order.
     */
    fun preprocessForTest(bitmap: Bitmap): FloatArray {
        val side = minOf(bitmap.width, bitmap.height)
        val left = (bitmap.width - side) / 2
        val top = (bitmap.height - side) / 2
        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        val crop = Bitmap.createBitmap(bitmap, left, top, side, side)
        val resized = Bitmap.createScaledBitmap(crop, IMAGE_SIZE, IMAGE_SIZE, true)
        crop.recycle()
        resized.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)
        resized.recycle()

        val out = FloatArray(IMAGE_SIZE * IMAGE_SIZE * CHANNELS)
        var idx = 0
        for (pixel in pixels) {
            out[idx++] = Color.red(pixel) / 255f
            out[idx++] = Color.green(pixel) / 255f
            out[idx++] = Color.blue(pixel) / 255f
        }
        return out
    }

    /**
     * Test hook: decodes raw model output scores into the adult-image decision.
     *
     * Mirrors the threshold logic in [possibleAdultImage]: sums scores at
     * [HENTAI_INDEX], [PORN_INDEX], and [SEXY_INDEX] and compares against
     * [ADULT_SCORE_THRESHOLD]. Exposed so tests can verify behavior with synthetic
     * score vectors without needing a real model or bitmap.
     *
     * @param scores FloatArray of length [CLASS_COUNT] as produced by the model.
     * @return true if the summed adult score meets the threshold.
     */
    fun decodeOutputForTest(scores: FloatArray): Boolean {
        require(scores.size == CLASS_COUNT) {
            "scores must have size $CLASS_COUNT, was ${scores.size}"
        }
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
