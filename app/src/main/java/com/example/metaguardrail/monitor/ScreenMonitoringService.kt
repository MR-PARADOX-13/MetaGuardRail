package com.example.metaguardrail.monitor

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.metaguardrail.MainActivity
import com.example.metaguardrail.R
import com.example.metaguardrail.ai.BackendProxyConfig
import com.example.metaguardrail.ai.MuseSparkRepository
import com.example.metaguardrail.data.ContentSafetyAnalyzer
import com.example.metaguardrail.data.SafetyCategory
import com.example.metaguardrail.data.SafetySettings
import com.example.metaguardrail.data.SpeechLanguage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** User-started screen text scan. Frames are processed on-device and are not saved. */
class ScreenMonitoringService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val analyzer = ContentSafetyAnalyzer()
    private var recognizer: TextRecognizer? = null
    private var teluguRecognizer: TeluguTextRecognizer? = null
    private var visualClassifier: VisualContentClassifier? = null
    private var audioMonitor: AudioPlaybackMonitor? = null
    private var settings = SafetySettings()
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    @Volatile private var isProcessingFrame = false
    private var lastAnalysisAt = 0L
    private var cleanFrameCount = 0
    @Volatile private var foregroundPackage: String? = null
    private var usageEventCursor = 0L
    private var warningView: View? = null
    @Volatile private var shieldIsVisible = false
    @Volatile private var shieldSuppressedUntil = 0L
    private var isFinishing = false
    private var textFinding: String? = null
    private var visualFinding: String? = null
    private var audioFinding: String? = null
    private var audioEnabled = false
    private var speechLanguage = SpeechLanguage.ENGLISH
    @Volatile private var cloudTextAnalysisEnabled = false

    private val seenContentHashes = mutableMapOf<String, Long>() // hash -> timestamp
    private val videoFrameSampler = VideoFrameSampler()
    private var lastMuseCallAt = 0L
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var museFinding: String? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            finishSession("Screen sharing was stopped")
        }
    }

    // Note: rotation / config changes trigger new ImageReader size handling — VirtualDisplay is recreated
    // on next startCapture(). Current session continues with existing metrics; rotation while active
    // will be handled on next service restart or by system recreating the projection.

    private fun contentHash(ocrText: String, visualFlag: String?): String {
        return "${ocrText.hashCode()}_${visualFlag}_${ocrText.length}"
    }

    private fun maybeAnalyzeWithMuse(text: String, settings: SafetySettings) {
        if (!cloudTextAnalysisEnabled) return
        if (!BackendProxyConfig.isBackendConfigured()) return
        val now = System.currentTimeMillis()
        if (now - lastMuseCallAt < 10_000) return // throttle 10s
        lastMuseCallAt = now
        serviceScope.launch {
            try {
                val svc = BackendProxyConfig.createMuseService()
                val repo = MuseSparkRepository(svc, analyzer, isCloudTextAnalysisAllowed = { cloudTextAnalysisEnabled })
                val res = repo.analyzeWithMuse(text.take(2000), settings)
                if (res.isSuccess) {
                    val m = res.getOrThrow()
                    // Validate category allowlist already done, check shouldShield
                    if (m.shouldShield || m.severity == "WARNING" || m.severity == "HIGH_RISK") {
                        mainHandler.post { updateFinding("muse", m.category) }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finishSession("Automatic checking is off")
            return START_NOT_STICKY
        }

        if (projection != null) return START_NOT_STICKY

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        @Suppress("DEPRECATION")
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultCode == 0 || resultData == null) {
            finishSession("Screen capture permission was not received")
            return START_NOT_STICKY
        }

        val enabledCategoryNames = intent?.getStringArrayExtra(EXTRA_ENABLED_CATEGORIES).orEmpty()
        settings = SafetySettings(
            enabledCategories = SafetyCategory.entries.filter { category ->
                enabledCategoryNames.contains(category.name)
            }.toSet()
        )
        audioEnabled = intent?.getBooleanExtra(EXTRA_AUDIO_ENABLED, false) == true
        speechLanguage = SpeechLanguage.fromValue(intent?.getStringExtra(EXTRA_SPEECH_LANGUAGE))
        cloudTextAnalysisEnabled = intent?.getBooleanExtra(EXTRA_CLOUD_TEXT_ANALYSIS_ENABLED, false) == true

        startForegroundForCapture()
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, resultData)
            projection?.registerCallback(projectionCallback, mainHandler)
            startCapture()
            LiveProtectionState.running()
        } catch (_: Exception) {
            finishSession("Could not start screen checking. Please try again.")
        }
        return START_NOT_STICKY
    }

    private fun startForegroundForCapture() {
        val channelId = "live_protection"
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(channelId, "Live protection", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val stopIntent = Intent(this, ScreenMonitoringService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Meta Guard Rail protection is active")
            .setContentText("Checking visible Meta app content on this device. Tap Stop to end it.")
            .setOngoing(true)
            .addAction(0, "Stop", stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @Suppress("DEPRECATION")
    private fun startCapture() {
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getRealMetrics(metrics)

        captureThread = HandlerThread("GuardRailScreenContent").also { it.start() }
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        teluguRecognizer = try {
            TeluguTextRecognizer(applicationContext)
        } catch (_: Exception) {
            mainHandler.post {
                LiveProtectionState.running("Telugu OCR is unavailable; checking English text and sampled images")
            }
            null
        }
        visualClassifier = try {
            VisualContentClassifier(applicationContext)
        } catch (_: Exception) {
            mainHandler.post {
                LiveProtectionState.running("Image model unavailable; checking readable text only")
            }
            null
        }
        if (audioEnabled && projection != null) {
            audioMonitor = AudioPlaybackMonitor(
                context = applicationContext,
                projection = projection!!,
                targetPackageNames = AUDIO_CAPTURE_PACKAGES,
                speechLanguage = speechLanguage,
                onTranscript = ::checkSpeechTranscript,
                onStatus = { message -> mainHandler.post { LiveProtectionState.running(message) } }
            ).also { it.start() }
        } else {
            mainHandler.post {
                LiveProtectionState.running("Checking text and sampled images; audio permission is off")
            }
        }
        val captureHandler = Handler(captureThread?.looper ?: Looper.getMainLooper())
        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            processLatestFrame(reader.acquireLatestImage())
        }, captureHandler)

        virtualDisplay = projection?.createVirtualDisplay(
            "Meta Guard Rail content check",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            captureHandler
        )
    }

    private fun processLatestFrame(image: Image?) {
        if (image == null) return
        val now = System.currentTimeMillis()
        if (shieldIsVisible) {
            if (currentForegroundPackage(now) !in SCREEN_MONITORED_PACKAGES) {
                mainHandler.post {
                    textFinding = null
                    visualFinding = null
                    audioFinding = null
                    museFinding = null
                    hideWarning()
                    LiveProtectionState.waitingForSocialApp()
                }
            }
            image.close()
            return
        }
        if (isProcessingFrame || now - lastAnalysisAt < FRAME_INTERVAL_MS) {
            image.close()
            return
        }
        lastAnalysisAt = now
        val packageName = currentForegroundPackage(now)
        if (packageName !in SCREEN_MONITORED_PACKAGES) {
            cleanFrameCount = 0
            videoFrameSampler.reset()
            mainHandler.post {
                textFinding = null
                visualFinding = null
                audioFinding = null
                museFinding = null
                hideWarning()
                LiveProtectionState.waitingForSocialApp()
            }
            image.close()
            return
        }

        isProcessingFrame = true
        var bitmapOwnedByAsync = false
        var asyncBitmap: Bitmap? = null
        try {
            val bitmap = imageToBitmap(image)
            try { image.close() } catch (_: Exception) {}
            if (bitmap == null) {
                isProcessingFrame = false
                return
            }
            asyncBitmap = bitmap
            bitmapOwnedByAsync = true
            // Visual classifier is additionally throttled for video/Reels via VideoFrameSampler (3s).
            // We keep existing FRAME_INTERVAL_MS as primary throttle; VideoFrameSampler provides extra
            // sampling gate if needed for future video-specific logic. Preserving behavior for now.
            val shouldRunVisual = settings.enabledCategories.contains(SafetyCategory.ADULT_CONTENT)
            // Record sampling edge for future video frame gating (no-op if not used)
            // videoFrameSampler.shouldSample(now) could be used to gate heavy inference
            visualFinding = try {
                if (shouldRunVisual && visualClassifier?.possibleAdultImage(bitmap) == true) {
                    "sexual or suggestive imagery"
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
            val imageFindingForFrame = visualFinding
            mainHandler.post {
                updateFinding("image", imageFindingForFrame)
            }
            val textRecognizer = recognizer
            if (textRecognizer == null) {
                try { bitmap.recycle() } catch (_: Exception) {}
                asyncBitmap = null
                bitmapOwnedByAsync = false
                isProcessingFrame = false
                return
            }
            val teluguText = try {
                teluguRecognizer?.recognize(bitmap).orEmpty()
            } catch (_: Exception) {
                ""
            }
            textRecognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { recognized ->
                    val combinedText = listOf(recognized.text, teluguText).filter(String::isNotBlank).joinToString("\n")
                    // --- Deduplication: compute hash after OCR + visual flag ---
                    val hash = contentHash(combinedText, imageFindingForFrame)
                    val nowMs = System.currentTimeMillis()
                    // Prune entries older than 60s
                    val iterator = seenContentHashes.entries.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        if (nowMs - entry.value > 60_000) {
                            iterator.remove()
                        }
                    }
                    // Check duplicate within 5s window: skip duplicate analysis
                    val lastSeen = seenContentHashes[hash]
                    if (lastSeen != null && nowMs - lastSeen < 5000) {
                        // Duplicate frame — skip analysis; recycling handled in onComplete
                        return@addOnSuccessListener
                    }
                    seenContentHashes[hash] = nowMs
                    if (seenContentHashes.size > 100) {
                        // Evict eldest entry (first key) — insertion-order approximation
                        val eldestKey = seenContentHashes.keys.firstOrNull()
                        if (eldestKey != null) seenContentHashes.remove(eldestKey)
                    }

                    // Optional remote Muse analysis (throttled 10s)
                    if (combinedText.isNotBlank()) {
                        maybeAnalyzeWithMuse(combinedText, settings)
                    }

                    val result = analyzer.analyzeContent(combinedText, settings)
                    if (result.isFlagged) {
                        cleanFrameCount = 0
                        val category = result.categoryMatched?.label ?: "safety concern"
                        mainHandler.post {
                            updateFinding("text", category)
                        }
                    } else {
                        if (imageFindingForFrame != null) {
                            cleanFrameCount = 0
                        } else {
                            cleanFrameCount += 1
                            if (cleanFrameCount >= CLEAN_FRAMES_TO_CLEAR) {
                                mainHandler.post {
                                    updateFinding("text", null)
                                    // Also clear muse if visual is also clear — sustained clean frames
                                    if (visualFinding == null) {
                                        updateFinding("muse", null)
                                    }
                                }
                            }
                        }
                    }
                }
                .addOnFailureListener {
                    mainHandler.post {
                        LiveProtectionState.running("Screen is shared, but text could not be read from this frame")
                    }
                }
                .addOnCompleteListener {
                    try { bitmap.recycle() } catch (_: Exception) {}
                    isProcessingFrame = false
                }
            // Async path now owns bitmap and will recycle + reset isProcessingFrame in onComplete
            return
        } catch (_: Exception) {
            try { image.close() } catch (_: Exception) {}
            if (bitmapOwnedByAsync) {
                // async path owns recycle; do not double-recycle here, but ensure flag reset on exception before listener attached
                // If exception occurred before listeners attached, we must cleanup
                try { asyncBitmap?.recycle() } catch (_: Exception) {}
                isProcessingFrame = false
            } else {
                isProcessingFrame = false
            }
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        var padded: Bitmap? = null
        var cropped: Bitmap? = null
        try {
            val paddingPixels = (plane.rowStride - plane.pixelStride * image.width) / plane.pixelStride
            val paddedWidth = image.width + paddingPixels
            padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
            plane.buffer.rewind()
            padded.copyPixelsFromBuffer(plane.buffer)
            cropped = if (paddedWidth == image.width) {
                padded
            } else {
                Bitmap.createBitmap(padded, 0, 0, image.width, image.height).also {
                    try { padded.recycle() } catch (_: Exception) {}
                    padded = null
                }
            }
            val widthForScale = cropped.width
            val scale = minOf(1f, MAX_ANALYSIS_WIDTH.toFloat() / widthForScale)
            if (scale >= 1f) {
                val result = cropped
                // Prevent finally from recycling the returned bitmap
                cropped = null
                padded = null
                return result
            }
            val scaled = Bitmap.createScaledBitmap(
                cropped,
                (cropped.width * scale).toInt().coerceAtLeast(1),
                (cropped.height * scale).toInt().coerceAtLeast(1),
                true
            )
            try { cropped.recycle() } catch (_: Exception) {}
            cropped = null
            padded = null
            return scaled
        } catch (_: Exception) {
            try { cropped?.recycle() } catch (_: Exception) {}
            // Only recycle padded if it's distinct from cropped (already handled)
            if (padded != null && padded != cropped) {
                try { padded.recycle() } catch (_: Exception) {}
            }
            return null
        } finally {
            // Early returns already nullified references to avoid double-recycle.
            // Any remaining non-returned bitmaps that leaked due to exception path are handled above.
        }
    }

    private fun currentForegroundPackage(now: Long): String? {
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val from = if (usageEventCursor == 0L) now - INITIAL_USAGE_LOOKBACK_MS else usageEventCursor
        val events = usageStatsManager.queryEvents(from, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> foregroundPackage = event.packageName

                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    if (foregroundPackage == event.packageName) foregroundPackage = null
                }
            }
        }
        usageEventCursor = now
        return foregroundPackage
    }

    private fun showWarning(category: String) {
        if (!Settings.canDrawOverlays(this)) {
            mainHandler.post { LiveProtectionState.running("Overlay permission revoked — please re-enable display over other apps") }
            return
        }
        if (System.currentTimeMillis() < shieldSuppressedUntil) return
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        if (warningView != null) {
            return
        }
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        val view: View = try {
            WarningOverlayView.create(
                context = this,
                category = category,
                onSnooze = { showScreenTemporarily() },
                onExitHome = { exitToHomeScreen() },
                onOpenSettings = {
                    try {
                        val settingsIntent = Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(settingsIntent)
                    } catch (_: Exception) {
                        showScreenTemporarily()
                    }
                }
            )
        } catch (_: Exception) {
            // Fallback to inline view creation if WarningOverlayView fails
            createFallbackWarningView(category)
        }
        try {
            windowManager.addView(view, params)
            warningView = view
            shieldIsVisible = true
        } catch (_: SecurityException) {
            warningView = null
            shieldIsVisible = false
            mainHandler.post { LiveProtectionState.running("Overlay permission revoked — please re-enable display over other apps") }
        } catch (_: Exception) {
            warningView = null
            shieldIsVisible = false
        }
    }

    private fun createFallbackWarningView(category: String): View {
        val guidance = if (category.contains("self-harm", ignoreCase = true)) {
            "If this may be about you, reach out to someone you trust or local crisis support. If anyone is in immediate danger, contact local emergency services."
        } else {
            "A possible $category was found in Instagram or Facebook. This automated check can be wrong."
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(36), dp(28), dp(36))
            setBackgroundColor(0xF20C1117.toInt())
            isClickable = true
            isFocusable = true

            addView(TextView(context).apply {
                text = "META GUARD RAIL"
                textSize = 13f
                letterSpacing = 0.08f
                setTextColor(0xFF8DD8D0.toInt())
                gravity = Gravity.CENTER
            }, fullWidthWrap())
            addView(TextView(context).apply {
                text = "This content is covered"
                textSize = 26f
                setTextColor(0xFFF2F5F8.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(18), 0, dp(12))
            }, fullWidthWrap())
            addView(TextView(context).apply {
                text = "$guidance\n\nThe cover hides the screen; it does not remove the post or stop its audio."
                textSize = 16f
                setTextColor(0xFFCED7DF.toInt())
                gravity = Gravity.CENTER
                setLineSpacing(dp(4).toFloat(), 1f)
            }, fullWidthWrap())
            addView(Button(context).apply {
                text = "Continue to next item (show for 8 sec)"
                setOnClickListener { showScreenTemporarily() }
            }, fullWidthWrap().apply { topMargin = dp(28) })
            addView(Button(context).apply {
                text = "Exit to home screen"
                setOnClickListener { exitToHomeScreen() }
            }, fullWidthWrap().apply { topMargin = dp(8) })
        }
    }

    private fun fullWidthWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun showScreenTemporarily() {
        shieldSuppressedUntil = System.currentTimeMillis() + SHIELD_SNOOZE_MS
        hideWarning()
    }

    private fun exitToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(homeIntent)
        } catch (_: Exception) {
            showScreenTemporarily()
        }
    }

    private fun hideWarning() {
        warningView?.let { view ->
            try {
                (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view)
            } catch (_: Exception) {
                // The Android system may already have removed it while stopping screen sharing.
            }
        }
        warningView = null
        shieldIsVisible = false
    }

    private fun checkSpeechTranscript(text: String) {
        if (foregroundPackage !in AUDIO_CAPTURE_PACKAGES) return
        val result = analyzer.analyzeContent(text, settings)
        val category = if (result.isFlagged) {
            result.categoryMatched?.label ?: "safety concern"
        } else {
            null
        }
        mainHandler.post { updateFinding("audio", category) }
    }

    /** All displayed finding state is changed on the main thread. */
    private fun updateFinding(source: String, category: String?) {
        when (source) {
            "text" -> textFinding = category
            "image" -> visualFinding = category
            "audio" -> audioFinding = category
            "muse" -> museFinding = category
        }
        val activeFinding = when {
            visualFinding != null -> visualFinding to "image check"
            museFinding != null -> museFinding to "AI review"
            textFinding != null -> textFinding to "visible text"
            audioFinding != null -> audioFinding to "speech transcript"
            else -> null
        }
        if (activeFinding != null) {
            val (label, sourceLabel) = activeFinding
            LiveProtectionState.flagged(label ?: "safety concern", sourceLabel)
            showWarning("$label in $sourceLabel")
        } else {
            LiveProtectionState.clearFinding()
            hideWarning()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun finishSession(message: String) {
        if (isFinishing) return
        isFinishing = true
        hideWarning()
        serviceScope.cancel()
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        audioMonitor?.stop()
        audioMonitor = null
        projection?.unregisterCallback(projectionCallback)
        projection?.stop()
        projection = null
        recognizer?.close()
        recognizer = null
        teluguRecognizer?.close()
        teluguRecognizer = null
        visualClassifier?.close()
        visualClassifier = null
        captureThread?.quitSafely()
        captureThread = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        LiveProtectionState.stopped(message)
        stopSelf()
    }

    override fun onDestroy() {
        if (!isFinishing) finishSession("Automatic checking is off")
        try { serviceScope.cancel() } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.metaguardrail.monitor.STOP"
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_RESULT_DATA = "projection_result_data"
        const val EXTRA_ENABLED_CATEGORIES = "enabled_categories"
        const val EXTRA_AUDIO_ENABLED = "audio_playback_enabled"
        const val EXTRA_SPEECH_LANGUAGE = "speech_language"
        const val EXTRA_CLOUD_TEXT_ANALYSIS_ENABLED = "cloud_text_analysis_enabled"

        private const val NOTIFICATION_ID = 401
        private const val STOP_REQUEST_CODE = 402
        private const val FRAME_INTERVAL_MS = 1_200L
        private const val MAX_ANALYSIS_WIDTH = 1_280
        private const val INITIAL_USAGE_LOOKBACK_MS = 24 * 60 * 60 * 1000L
        private const val CLEAN_FRAMES_TO_CLEAR = 3
        private const val SHIELD_SNOOZE_MS = 8_000L
        private val SCREEN_MONITORED_PACKAGES = setOf(
            "com.instagram.android",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.android.chrome"
        )
        private val AUDIO_CAPTURE_PACKAGES = setOf(
            "com.instagram.android",
            "com.facebook.katana",
            "com.facebook.lite"
        )
    }
}
