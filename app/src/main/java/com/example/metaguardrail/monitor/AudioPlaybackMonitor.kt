package com.example.metaguardrail.monitor

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import com.example.metaguardrail.data.SpeechLanguage
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** Transcribes capturable Meta-app playback locally; does not use the room microphone. */
class AudioPlaybackMonitor(
    private val context: Context,
    private val projection: MediaProjection,
    private val targetPackageNames: Set<String>,
    private val speechLanguage: SpeechLanguage,
    private val onTranscript: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {
    @Volatile private var active = true
    @Volatile private var recorder: AudioRecord? = null
    private var worker: Thread? = null

    fun start() {
        worker = Thread(::captureAndTranscribe, "GuardRailMetaAudio").also { it.start() }
    }

    fun stop() {
        active = false
        try {
            recorder?.stop()
        } catch (_: Exception) {
            // The recorder may not have started yet.
        }
        worker?.interrupt()
    }

    private fun captureAndTranscribe() {
        var model: Model? = null
        var recognizer: Recognizer? = null
        var audioRecord: AudioRecord? = null
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                onStatus("Audio playback checks need Android 10 or newer")
                return
            }

            val packageUids = targetPackageNames.mapNotNull { packageName ->
                try {
                    @Suppress("DEPRECATION")
                    context.packageManager.getApplicationInfo(packageName, 0).uid
                } catch (_: Exception) {
                    null
                }
            }.distinct()
            if (packageUids.isEmpty()) {
                onStatus("No supported Instagram or Facebook app is installed for audio checks")
                return
            }

            val languageLabel = if (speechLanguage == SpeechLanguage.TELUGU) "Telugu" else "English"
            onStatus("Loading local $languageLabel speech recognition; it may use about 300 MB of memory")
            val modelDirectory = prepareModelDirectory(speechLanguage)
            if (!active) return
            model = Model(modelDirectory.absolutePath)
            recognizer = Recognizer(model, SAMPLE_RATE.toFloat())

            val captureBuilder = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            packageUids.forEach(captureBuilder::addMatchingUid)
            val captureConfiguration = captureBuilder.build()
            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            val minimumBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minimumBufferSize <= 0) throw IllegalStateException("Playback audio format is unavailable")

            audioRecord = AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(minimumBufferSize * 2)
                .setAudioPlaybackCaptureConfig(captureConfiguration)
                .build()
            recorder = audioRecord
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("Playback audio capture could not be initialized")
            }

            audioRecord.startRecording()
            onStatus("Checking $languageLabel speech in Meta app playback when the app permits capture")
            val buffer = ByteArray(maxOf(BUFFER_BYTES, minimumBufferSize))
            var lastPartial = ""
            while (active) {
                val count = audioRecord.read(buffer, 0, buffer.size)
                if (count < 0) throw IllegalStateException("Playback audio read failed: $count")
                if (count == 0) continue
                val speechRecognizer = recognizer ?: break
                if (speechRecognizer.acceptWaveForm(buffer, count)) {
                    emitTranscript(speechRecognizer.getResult())
                    lastPartial = ""
                } else {
                    val partial = JSONObject(speechRecognizer.getPartialResult()).optString("partial").trim()
                    if (partial.isNotBlank() && partial != lastPartial) {
                        lastPartial = partial
                        onTranscript(partial)
                    }
                }
            }
        } catch (error: Exception) {
            if (active) {
                Log.w(TAG, "Playback speech capture unavailable", error)
                onStatus(
                    "Audio speech checking could not start. Check the one-time model download and whether Meta allows playback capture; text and image checks continue."
                )
            }
        } finally {
            try {
                audioRecord?.stop()
            } catch (_: Exception) {
                // Already stopped.
            }
            audioRecord?.release()
            recorder = null
            recognizer?.close()
            model?.close()
        }
    }

    private fun emitTranscript(json: String) {
        val text = JSONObject(json).optString("text").trim()
        if (text.isNotBlank()) onTranscript(text)
    }

    private fun prepareModelDirectory(language: SpeechLanguage): File {
        val modelName = if (language == SpeechLanguage.TELUGU) TELUGU_MODEL_DIRECTORY_NAME else ENGLISH_MODEL_DIRECTORY_NAME
        val root = File(context.filesDir, modelName)
        if (File(root, "am/final.mdl").isFile) return root

        val staging = File(context.filesDir, "$modelName.installing")
        staging.deleteRecursively()
        if (!staging.mkdirs()) throw IllegalStateException("Cannot prepare the speech model folder")
        val stagingPath = staging.canonicalPath + File.separator
        try {
            val archive = downloadSpeechModel(language)
            try {
                archive.inputStream().use { input -> extractModelZip(input, staging, stagingPath) }
            } finally {
                archive.delete()
            }
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
        val extractedModel = File(staging, modelName)
        if (!File(extractedModel, "am/final.mdl").isFile) {
            staging.deleteRecursively()
            throw IllegalStateException("The offline speech model could not be extracted")
        }
        if (root.exists()) root.deleteRecursively()
        if (!extractedModel.renameTo(root)) {
            staging.deleteRecursively()
            throw IllegalStateException("The offline speech model could not be installed")
        }
        staging.deleteRecursively()
        return root
    }

    private fun extractModelZip(input: java.io.InputStream, staging: File, stagingPath: String) {
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val outputFile = File(staging, entry.name).canonicalFile
                if (!outputFile.path.startsWith(stagingPath)) {
                    throw IllegalStateException("Invalid file path in speech model")
                }
                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    FileOutputStream(outputFile).use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
            }
        }
    }

    private fun downloadSpeechModel(language: SpeechLanguage): File {
        val modelName = if (language == SpeechLanguage.TELUGU) {
            TELUGU_MODEL_DIRECTORY_NAME
        } else {
            ENGLISH_MODEL_DIRECTORY_NAME
        }
        val (languageName, sizeLabel) = if (language == SpeechLanguage.TELUGU) {
            "Telugu" to "about 58 MB"
        } else {
            "English" to "about 40 MB"
        }
        val modelUrl = if (language == SpeechLanguage.TELUGU) TELUGU_MODEL_URL else ENGLISH_MODEL_URL
        val archive = File(context.cacheDir, "$modelName.zip.part")
        if (archive.exists()) archive.delete()
        onStatus("Downloading the $sizeLabel $languageName speech model once; it stays on this device")
        val connection = (URL(modelUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("$languageName speech model download failed (${connection.responseCode})")
            }
            connection.inputStream.use { input ->
                FileOutputStream(archive).use { output -> input.copyTo(output, bufferSize = 64 * 1024) }
            }
            if (archive.length() < MIN_SPEECH_ARCHIVE_BYTES) {
                throw IllegalStateException("${language.label} speech model download was incomplete")
            }
            val readyArchive = File(context.cacheDir, "$modelName.zip")
            if (readyArchive.exists()) readyArchive.delete()
            if (!archive.renameTo(readyArchive)) {
                throw IllegalStateException("Could not save the $languageName speech model")
            }
            return readyArchive
        } catch (error: Exception) {
            archive.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "MetaGuardRailAudio"
        private const val SAMPLE_RATE = 16_000
        private const val BUFFER_BYTES = 8_192
        private const val ENGLISH_MODEL_DIRECTORY_NAME = "vosk-model-small-en-us-0.15"
        private const val TELUGU_MODEL_DIRECTORY_NAME = "vosk-model-small-te-0.42"
        private const val ENGLISH_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        private const val TELUGU_MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-te-0.42.zip"
        private const val MIN_SPEECH_ARCHIVE_BYTES = 10_000_000L
    }
}
