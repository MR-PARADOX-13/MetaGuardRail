package com.example.metaguardrail.monitor

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/** Offline Telugu-script OCR. The trained-data file is bundled with the app. */
class TeluguTextRecognizer(context: Context) : AutoCloseable {
    private val tess = TessBaseAPI()

    init {
        val root = File(context.filesDir, "tesseract")
        val data = File(root, "tessdata")
        if (!data.exists() && !data.mkdirs()) {
            throw IllegalStateException("Could not create Telugu OCR data folder")
        }
        val trainedData = File(data, "tel.traineddata")
        if (!trainedData.isFile || trainedData.length() == 0L) {
            context.assets.open(ASSET_PATH).use { input ->
                trainedData.outputStream().use(input::copyTo)
            }
        }
        if (!tess.init(root.absolutePath, "tel")) {
            tess.recycle()
            throw IllegalStateException("Telugu OCR could not load its language data")
        }
    }

    fun recognize(bitmap: Bitmap): String {
        tess.setImage(bitmap)
        return tess.getUTF8Text().orEmpty()
    }

    override fun close() {
        tess.recycle()
    }

    private companion object {
        const val ASSET_PATH = "ocr/tessdata/tel.traineddata"
    }
}
