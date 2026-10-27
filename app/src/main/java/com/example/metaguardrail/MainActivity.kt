package com.example.metaguardrail

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metaguardrail.data.AnalysisResult
import com.example.metaguardrail.data.ContentSafetyAnalyzer
import com.example.metaguardrail.data.SafetyCategory
import com.example.metaguardrail.data.SafetyMode
import com.example.metaguardrail.data.SafetySettings
import com.example.metaguardrail.data.SpeechLanguage
import com.example.metaguardrail.data.UserPreferencesRepository
import com.example.metaguardrail.monitor.LiveProtectionState
import com.example.metaguardrail.monitor.LiveProtectionStatus
import com.example.metaguardrail.monitor.ScreenMonitoringService
import com.example.metaguardrail.ui.theme.MetaGuardRailTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val pendingSharedText = mutableStateOf("")
    private var pendingProtectionSettings = SafetySettings()
    private var awaitingOverlaySettings = false
    private var awaitingUsageSettings = false
    private var audioPermissionHandledForStart = false
    private var audioPlaybackEnabled = false

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            LiveProtectionState.starting()
            val serviceIntent = Intent(this, ScreenMonitoringService::class.java).apply {
                putExtra(ScreenMonitoringService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenMonitoringService.EXTRA_RESULT_DATA, data)
                putExtra(
                    ScreenMonitoringService.EXTRA_ENABLED_CATEGORIES,
                    pendingProtectionSettings.enabledCategories.map { it.name }.toTypedArray()
                )
                putExtra(ScreenMonitoringService.EXTRA_AUDIO_ENABLED, audioPlaybackEnabled)
                putExtra(ScreenMonitoringService.EXTRA_SPEECH_LANGUAGE, pendingProtectionSettings.speechLanguage.value)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
                else startService(serviceIntent)
            } catch (_: Exception) {
                LiveProtectionState.stopped("Could not start protection. Please try again.")
            }
        } else {
            LiveProtectionState.stopped("Screen sharing was not approved")
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        continueProtectionSetup()
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        audioPermissionHandledForStart = true
        audioPlaybackEnabled = granted
        continueProtectionSetup()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveSharedText(intent)

        val preferencesRepository = UserPreferencesRepository(applicationContext)

        setContent {
            MetaGuardRailTheme {
                val settings by preferencesRepository.settings.collectAsState(
                    initial = SafetySettings()
                )
                val coroutineScope = rememberCoroutineScope()
                val sharedTextValue by pendingSharedText
                val protectionStatus by LiveProtectionState.state.collectAsState()

                GuardRailHome(
                    settings = settings,
                    onModeSelected = { mode ->
                        coroutineScope.launch {
                            preferencesRepository.setMode(mode)
                        }
                    },
                    onCategoryChanged = { category, enabled ->
                        coroutineScope.launch {
                            preferencesRepository.setCategoryEnabled(category, enabled)
                        }
                    },
                    onSpeechLanguageChanged = { language ->
                        coroutineScope.launch {
                            preferencesRepository.setSpeechLanguage(language)
                        }
                    },
                    sharedText = sharedTextValue,
                    onSharedTextConsumed = { pendingSharedText.value = "" },
                    protectionStatus = protectionStatus,
                    onStartProtection = { startProtection(settings) },
                    onStopProtection = { stopProtection() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveSharedText(intent)
    }

    override fun onResume() {
        super.onResume()
        when {
            awaitingOverlaySettings -> {
                awaitingOverlaySettings = false
                if (Settings.canDrawOverlays(this)) continueProtectionSetup()
                else LiveProtectionState.stopped("Allow display over other apps to show warnings")
            }

            awaitingUsageSettings -> {
                awaitingUsageSettings = false
                if (hasUsageAccess()) continueProtectionSetup()
                else LiveProtectionState.stopped("Allow app-usage access to limit checks to Meta apps")
            }
        }
    }

    private fun startProtection(settings: SafetySettings) {
        pendingProtectionSettings = settings
        audioPermissionHandledForStart = false
        audioPlaybackEnabled = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        LiveProtectionState.starting()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            continueProtectionSetup()
        }
    }

    private fun continueProtectionSetup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !audioPermissionHandledForStart &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            awaitingOverlaySettings = true
            LiveProtectionState.stopped("Allow display over other apps so warnings can appear")
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(settingsIntent)
            return
        }
        if (!hasUsageAccess()) {
            awaitingUsageSettings = true
            LiveProtectionState.stopped("Allow app-usage access to check only Instagram and Facebook")
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun stopProtection() {
        stopService(Intent(this, ScreenMonitoringService::class.java))
        LiveProtectionState.stopped()
    }

    internal fun receiveSharedText(source: Intent?) {
        if (source?.action != Intent.ACTION_SEND || source.type?.startsWith("text/") != true) return
        pendingSharedText.value = source.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
    }
}

@Composable
private fun GuardRailHome(
    settings: SafetySettings,
    onModeSelected: (SafetyMode) -> Unit,
    onCategoryChanged: (SafetyCategory, Boolean) -> Unit,
    onSpeechLanguageChanged: (SpeechLanguage) -> Unit,
    sharedText: String,
    onSharedTextConsumed: () -> Unit,
    protectionStatus: LiveProtectionStatus,
    onStartProtection: () -> Unit,
    onStopProtection: () -> Unit
) {
    var testInputText by remember { mutableStateOf("") }
    val analyzer = remember { ContentSafetyAnalyzer() }
    var analysisResult by remember { mutableStateOf<AnalysisResult?>(null) }
    var hideObscuredContent by remember { mutableStateOf(true) }
    var inputNotice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sharedText) {
        if (sharedText.isNotBlank()) {
            testInputText = sharedText
            analysisResult = null
            inputNotice = if (isLinkOnly(sharedText)) {
                "This share contains a link only. Guard Rail does not open links. Paste the post text or caption here to check it."
            } else {
                "Shared text is ready. Review it, then tap Check text."
            }
            hideObscuredContent = true
            onSharedTextConsumed()
        }
    }

    Scaffold(containerColor = Color(0xFF0C1117)) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0C1117))
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Header(isProtectionActive = protectionStatus.isRunning)
            IntroCard()
            LiveProtectionCard(
                status = protectionStatus,
                onStart = onStartProtection,
                onStop = onStopProtection
            )
            ShareFromInstagramCard()

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeading(
                    title = "Safety preferences",
                    subtitle = "Choose speech language and the rules that can trigger the screen cover."
                )
                PreferencesCard(
                    settings = settings,
                    onModeSelected = onModeSelected,
                    onCategoryChanged = onCategoryChanged,
                    onSpeechLanguageChanged = onSpeechLanguageChanged
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeading(
                    title = "Check text",
                    subtitle = "Paste or share text here, then review the result."
                )
                SandboxCard(
                    inputText = testInputText,
                    onInputChanged = { newText ->
                        testInputText = newText
                        analysisResult = null
                        inputNotice = null
                        hideObscuredContent = true
                    },
                    onRunAnalysis = {
                        if (isLinkOnly(testInputText)) {
                            analysisResult = null
                            inputNotice = "This is a link, not the post text. Copy or paste the caption/message to check the text. Links and Instagram images are not opened or analyzed."
                        } else {
                            inputNotice = null
                            analysisResult = analyzer.analyzeContent(testInputText, settings)
                        }
                    },
                    onClear = {
                        testInputText = ""
                        analysisResult = null
                        inputNotice = null
                    },
                    result = analysisResult,
                    inputNotice = inputNotice,
                    hideObscuredContent = hideObscuredContent,
                    onToggleHide = { hideObscuredContent = !hideObscuredContent }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeading(
                    title = "What is checked",
                    subtitle = "What the on-device checks can and cannot detect."
                )
                StatusCard()
            }

            Text(
                text = "Reels are checked as sampled screen frames. English text and Telugu script are checked on-device. Telugu speech recognition is experimental. Violence is checked only when violence-related text is visible; there is no violence-image classifier yet. A detected frame is covered by a full-screen shield, but the post is not removed and audio may continue.",
                color = Color(0xFF9AA6B2),
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun Header(isProtectionActive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(color = Color(0xFF17324A), shape = RoundedCornerShape(16.dp)) {
            Text(
                text = "MG",
                color = Color(0xFF8DD8D0),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "META GUARD RAIL",
                color = Color(0xFFF2F5F8),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp
            )
            Text(
                text = "Personal digital safety",
                color = Color(0xFF9AA6B2),
                style = MaterialTheme.typography.bodySmall
            )
        }
        StatusPill(
            text = if (isProtectionActive) "PROTECTION ON" else "READY",
            color = if (isProtectionActive) Color(0xFF7AE582) else Color(0xFF8BC5FF)
        )
    }
}

@Composable
private fun IntroCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14212C))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF173C43), Color(0xFF182A40), Color(0xFF14212C))),
                    RoundedCornerShape(24.dp)
                )
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusPill(text = "ON-DEVICE CHECKS", color = Color(0xFF7AE582))
            Text(
                text = "Check posts and Reels\nwhile you browse.",
                color = Color(0xFFF3F6F8),
                fontSize = 27.sp,
                lineHeight = 33.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "On-device checks scan visible English and Telugu text, sample images for sexual or suggestive cues, and can check selected-language audio when playback capture is allowed. A possible match covers the screen so you can skip or leave the app.",
                color = Color(0xFFB4C0CB),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun LiveProtectionCard(
    status: LiveProtectionStatus,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (status.finding != null) Color(0xFF2A171B) else Color(0xFF14212C)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Automatic content check",
                        color = Color(0xFFF0F4F7),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        status.message,
                        color = if (status.finding != null) Color(0xFFFFB4B4) else Color(0xFFB4C0CB),
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp
                    )
                }
                StatusPill(
                    text = if (status.isRunning) "ON" else "OFF",
                    color = if (status.isRunning) Color(0xFF7AE582) else Color(0xFFAEB9C4)
                )
            }
            if (status.finding != null) {
                Text(
                    "Possible ${status.finding} flagged by ${status.source ?: "content check"}. Review it; checks can be wrong.",
                    color = Color(0xFFFFD2D2),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
            }
            Text(
                "Android asks to share your screen and allow display-over-apps and app-usage access. Audio checking also needs audio permission. It processes Meta-app playback only, never uses the room microphone, and can be blocked by Android or Meta.",
                color = Color(0xFF9AA6B2),
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp
            )
            if (status.isRunning) {
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop protection")
                }
            } else {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8DD8D0))
                ) {
                    Text("Start protection", color = Color(0xFF0C1117), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ShareFromInstagramCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10231F))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = Color(0xFF1B3A32), shape = RoundedCornerShape(12.dp)) {
                    Text("IG", color = Color(0xFF8DD8D0), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Manual text check", color = Color(0xFFE8F5F1), fontWeight = FontWeight.SemiBold)
                    Text("Or use automatic text check above", color = Color(0xFF9FBDB4), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                text = "You can still share a caption or message to Meta Guard Rail and check it here manually.",
                color = Color(0xFFD1E2DD),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 21.sp
            )
            Text(
                text = "Automatic mode scans visible text, samples frames for sexual or suggestive imagery, and can transcribe supported playback speech. It does not open links or read private app databases.",
                color = Color(0xFF9FBDB4),
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun PreferencesCard(
    settings: SafetySettings,
    onModeSelected: (SafetyMode) -> Unit,
    onCategoryChanged: (SafetyCategory, Boolean) -> Unit,
    onSpeechLanguageChanged: (SpeechLanguage) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151D26))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SafetyMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.mode == mode,
                        onClick = { onModeSelected(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }

            Text(
                text = "Playback speech language",
                color = Color(0xFFE8EDF1),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpeechLanguage.entries.forEach { language ->
                    FilterChip(
                        selected = settings.speechLanguage == language,
                        onClick = { onSpeechLanguageChanged(language) },
                        label = { Text(language.label) }
                    )
                }
            }
            if (settings.speechLanguage == SpeechLanguage.TELUGU) {
                Text(
                    text = "First use downloads about 58 MB for offline Telugu speech checks. The published small-model benchmark is weak, so Telugu audio may miss or mishear speech; use the text and image checks as well.",
                    color = Color(0xFFFFD18A),
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp
                )
            } else {
                Text(
                    text = "First use downloads about 40 MB for offline English speech checks. The model stays on this device; Android or Meta may still block playback capture.",
                    color = Color(0xFF9AA6B2),
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp
                )
            }

            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF293541))
            )

            SafetyCategory.entries.forEachIndexed { index, category ->
                CategoryRow(
                    category = category,
                    enabled = settings.enabledCategories.contains(category),
                    onEnabledChanged = { enabled -> onCategoryChanged(category, enabled) }
                )
                if (index != SafetyCategory.entries.lastIndex) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFF293541))
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: SafetyCategory,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.label,
                color = Color(0xFFE8EDF1),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = category.description,
                color = Color(0xFF9AA6B2),
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 17.sp
            )
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChanged)
    }
}

@Composable
private fun SandboxCard(
    inputText: String,
    onInputChanged: (String) -> Unit,
    onRunAnalysis: () -> Unit,
    onClear: () -> Unit,
    result: AnalysisResult?,
    inputNotice: String?,
    hideObscuredContent: Boolean,
    onToggleHide: () -> Unit
) {
    val shouldObscureSubmittedText = result?.let {
        it.isFlagged && !it.isSupportive && hideObscuredContent
    } ?: false

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151D26))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Enter text or choose a sample to analyze against your enabled categories:",
                color = Color(0xFFE8EDF1),
                style = MaterialTheme.typography.bodyMedium
            )

            // Horizontal chips remain usable on narrow phone screens.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { onInputChanged("Click this link to claim your free crypto reward!") },
                    label = { Text("Scam sample") }
                )
                AssistChip(
                    onClick = { onInputChanged("Let's keep this secret chat between us and don't tell anyone.") },
                    label = { Text("Possible grooming") }
                )
                AssistChip(
                    onClick = { onInputChanged("Education class discussing LGBTQ+ health and mental support.") },
                    label = { Text("Educational context") }
                )
                AssistChip(
                    onClick = { onInputChanged("What the fuck is going on here?") },
                    label = { Text("Vulgar text") }
                )
                AssistChip(
                    onClick = { onInputChanged("This explicit porn video is not suitable for work.") },
                    label = { Text("Adult text") }
                )
                AssistChip(
                    onClick = { onInputChanged("అశ్లీల వీడియో మరియు నగ్న ఫోటోలు") },
                    label = { Text("తెలుగు • adult") }
                )
                AssistChip(
                    onClick = { onInputChanged("మీ అమ్మానాన్నలకు చెప్పొద్దు, రహస్యంగా కలుద్దాం") },
                    label = { Text("తెలుగు • safety") }
                )
                AssistChip(
                    onClick = { onInputChanged("A graphic violence clip shows a stabbing.") },
                    label = { Text("Violence text") }
                )
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChanged,
                placeholder = { Text("Type or paste post / message content here...", color = Color(0xFF6B7A8A)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = if (shouldObscureSubmittedText) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFFF2F5F8),
                    unfocusedTextColor = Color(0xFFF2F5F8),
                    focusedBorderColor = Color(0xFF8DD8D0),
                    unfocusedBorderColor = Color(0xFF293541),
                    focusedContainerColor = Color(0xFF0C1117),
                    unfocusedContainerColor = Color(0xFF0C1117)
                )
            )

            if (inputNotice != null) {
                Text(
                    text = inputNotice,
                    color = Color(0xFFFFD27D),
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2A2417))
                        .padding(12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRunAnalysis,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8DD8D0))
                ) {
                    Text(text = "Check text", color = Color(0xFF0C1117), fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onClear,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293541))
                ) {
                    Text(text = "Clear", color = Color(0xFFE8EDF1))
                }
            }

            if (result != null) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF293541))
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Analysis Result",
                            color = Color(0xFFE8EDF1),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        StatusPill(
                            text = result.riskLabel,
                            color = when {
                                result.isSupportive -> Color(0xFF7AE582)
                                result.isFlagged -> Color(0xFFFF6B6B)
                                else -> Color(0xFF8BC5FF)
                            }
                        )
                    }

                    Text(
                        text = result.explanation,
                        color = if (result.isSupportive) Color(0xFF7AE582) else Color(0xFFB4C0CB),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )

                    if (result.isFlagged && !result.isSupportive) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1215))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Matched content (Obscured)",
                                        color = Color(0xFFFF6B6B),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Button(
                                        onClick = onToggleHide,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A1C22))
                                    ) {
                                        Text(
                                            text = if (hideObscuredContent) "Reveal" else "Hide",
                                            fontSize = 11.sp,
                                            color = Color(0xFFFFB4B4)
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0C1117), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = inputText,
                                        color = Color(0xFFE8EDF1),
                                        modifier = if (hideObscuredContent) Modifier.blur(6.dp) else Modifier
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151D26))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            StatusRow(
                title = "Visible text in Meta apps",
                detail = "English and తెలుగు OCR plus English, Telugu-script, and common Romanized Telugu phrase rules",
                status = "SUPPORTED",
                statusColor = Color(0xFF7AE582)
            )
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF293541))
            )
            StatusRow(
                title = "Photos and sampled Reel frames",
                detail = "Local model looks for sexual or suggestive imagery; may miss content or flag ordinary images",
                status = "LIMITED",
                statusColor = Color(0xFFFFCC80)
            )
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF293541))
            )
            StatusRow(
                title = "Speech in Meta app playback",
                detail = "Choose English or experimental Telugu speech-to-text; first use downloads the model, and playback capture may be blocked",
                status = "LIMITED",
                statusColor = Color(0xFFFFCC80)
            )
            Text(
                text = "Violence in images is not classified yet. Telugu speech has a weak published benchmark and may miss many phrases. Speech checks recognize words, not screams, music, or other sounds.",
                color = Color(0xFFFFCC80),
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 19.sp
            )
            Text(
                text = "A possible match covers the screen after a sampled frame is analyzed; it is not proof a post is harmful. The shield cannot remove content, prevent exposure before detection, or stop audio already playing.",
                color = Color(0xFF9AA6B2),
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun StatusRow(
    title: String,
    detail: String,
    status: String,
    statusColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color(0xFFE8EDF1),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = detail,
                color = Color(0xFF9AA6B2),
                style = MaterialTheme.typography.bodySmall
            )
        }
        StatusPill(text = status, color = statusColor)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            color = Color(0xFFF1F4F7),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            subtitle,
            color = Color(0xFF9AA6B2),
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 18.sp
        )
    }
}

private fun isLinkOnly(value: String): Boolean {
    val candidate = value.trim().removeSurrounding("<", ">")
    return candidate.matches(Regex("(?i)(https?://|www\\.)\\S+")) ||
        candidate.matches(Regex("(?i)instagram\\.com/\\S+"))
}
