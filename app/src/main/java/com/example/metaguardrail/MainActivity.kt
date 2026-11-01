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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.metaguardrail.data.ActivityEvent
import com.example.metaguardrail.data.ActivityRepository
import com.example.metaguardrail.data.SafetySettings
import com.example.metaguardrail.data.Severity
import com.example.metaguardrail.data.ThemeMode
import com.example.metaguardrail.data.UserPreferencesRepository
import com.example.metaguardrail.monitor.IncidentNotifier
import com.example.metaguardrail.monitor.LiveProtectionState
import com.example.metaguardrail.monitor.VisualContentClassifier
import com.example.metaguardrail.ui.navigation.GuardRailDestination
import com.example.metaguardrail.ui.screens.ActivityScreen
import com.example.metaguardrail.ui.screens.AnalyzeScreen
import com.example.metaguardrail.ui.screens.HomeScreen
import com.example.metaguardrail.ui.screens.RulesScreen
import com.example.metaguardrail.ui.screens.SettingsScreen
import com.example.metaguardrail.ui.theme.GuardRailColors
import com.example.metaguardrail.ui.theme.MetaGuardRailTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val pendingSharedText = mutableStateOf("")
    private var pendingProtectionSettings = SafetySettings()
    private var pendingCloudTextAnalysisEnabled = false
    private var awaitingOverlaySettings = false
    private var awaitingUsageSettings = false
    private var audioPermissionHandledForStart = false
    private var audioPlaybackEnabled = false
    private val pendingIncidentId = mutableStateOf<String?>(null)

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            LiveProtectionState.starting()
            val serviceIntent = Intent(this, com.example.metaguardrail.monitor.ScreenMonitoringService::class.java).apply {
                putExtra(com.example.metaguardrail.monitor.ScreenMonitoringService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(com.example.metaguardrail.monitor.ScreenMonitoringService.EXTRA_RESULT_DATA, data)
                putExtra(
                    com.example.metaguardrail.monitor.ScreenMonitoringService.EXTRA_ENABLED_CATEGORIES,
                    pendingProtectionSettings.enabledCategories.map { it.name }.toTypedArray()
                )
                putExtra(com.example.metaguardrail.monitor.ScreenMonitoringService.EXTRA_AUDIO_ENABLED, audioPlaybackEnabled)
                putExtra(com.example.metaguardrail.monitor.ScreenMonitoringService.EXTRA_SPEECH_LANGUAGE, pendingProtectionSettings.speechLanguage.value)
                putExtra(com.example.metaguardrail.monitor.ScreenMonitoringService.EXTRA_CLOUD_TEXT_ANALYSIS_ENABLED, pendingCloudTextAnalysisEnabled)
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
    ) { continueProtectionSetup() }

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
        handleIncidentIntent(intent)
        val preferencesRepository = UserPreferencesRepository(applicationContext)
        val activityRepository = ActivityRepository(applicationContext)
        IncidentNotifier.ensureChannels(applicationContext)
        setContent {
            val themeMode by preferencesRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val settings by preferencesRepository.settings.collectAsState(initial = SafetySettings())
            val protectionStatus by LiveProtectionState.state.collectAsState()
            val activityEvents by activityRepository.events.collectAsState(initial = emptyList())
            val notifyHighRisk by preferencesRepository.notifyHighRisk.collectAsState(initial = false)
            val notifyWarning by preferencesRepository.notifyWarning.collectAsState(initial = false)
            val cloudTextAnalysisEnabled by preferencesRepository.cloudTextAnalysisEnabled.collectAsState(initial = false)
            val pendingIncident by pendingIncidentId
            val scope = rememberCoroutineScope()
            var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

            val appThemeMode = when (themeMode) {
                ThemeMode.DARK -> com.example.metaguardrail.ui.theme.AppThemeMode.DARK
                ThemeMode.LIGHT -> com.example.metaguardrail.ui.theme.AppThemeMode.LIGHT
                ThemeMode.SYSTEM -> com.example.metaguardrail.ui.theme.AppThemeMode.SYSTEM
            }

            handleIncidentIntentIfNeeded(pendingIncident) { selectedTabIndex = 1 }

            val shared by pendingSharedText
            LaunchedEffect(shared) { if (shared.isNotBlank()) selectedTabIndex = 2 }

            LaunchedEffect(protectionStatus.finding, protectionStatus.isRunning) {
                val f = protectionStatus.finding
                if (protectionStatus.isRunning && !f.isNullOrBlank()) {
                    val last = activityEvents.firstOrNull()
                    val dup = last != null && last.title == "Live: $f" && (System.currentTimeMillis() - last.timestampMs) < 5000
                    if (!dup) activityRepository.addEvent(
                        ActivityEvent(
                            title = "Live: $f",
                            category = f,
                            severity = Severity.WARNING,
                            source = com.example.metaguardrail.data.DetectionSource.LOCAL_RULE,
                            explanation = protectionStatus.message
                        )
                    )
                }
            }

            // Phase 3: Safety incident notifications — priming, dedup, OFF by default
            var isFirstEventsEmission by remember { mutableStateOf(true) }
            var seenIncidentIds by remember { mutableStateOf(setOf<String>()) }
            var notifiedIdsSnapshot by remember { mutableStateOf(setOf<String>()) }

            // Collect notified set for dedup across restarts
            LaunchedEffect(Unit) {
                preferencesRepository.notifiedIncidentIds.collect { notifiedIdsSnapshot = it }
            }
            LaunchedEffect(activityEvents) {
                if (isFirstEventsEmission) {
                    // Prime without notifying historical events
                    seenIncidentIds = activityEvents.map { it.id }.toSet()
                    preferencesRepository.pruneNotifiedIdsToCurrent(activityEvents.map { it.id }.toSet())
                    isFirstEventsEmission = false
                    return@LaunchedEffect
                }
                val newEvents = activityEvents.filter { it.id !in seenIncidentIds }
                seenIncidentIds = activityEvents.map { it.id }.toSet()
                // Prune persisted notified IDs to currently retained events (bounds the set)
                preferencesRepository.pruneNotifiedIdsToCurrent(activityEvents.map { it.id }.toSet())
                if (newEvents.isEmpty()) return@LaunchedEffect
                val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else true
                if (!hasPermission) return@LaunchedEffect
                val toNotify = newEvents.filter { event ->
                    val notAlreadyNotified = event.id !in notifiedIdsSnapshot
                    val isNotifyable = when (event.severity) {
                        Severity.HIGH_RISK -> notifyHighRisk && event.id !in notifiedIdsSnapshot
                        Severity.WARNING -> notifyWarning && event.id !in notifiedIdsSnapshot
                        else -> false
                    }
                    isNotifyable && notAlreadyNotified
                }
                if (toNotify.isEmpty()) return@LaunchedEffect
                for (event in toNotify) {
                    IncidentNotifier.notifyForEvent(applicationContext, event)
                }
                preferencesRepository.addNotifiedIds(toNotify.map { it.id }.toSet())
            }

            MetaGuardRailTheme(themeMode = appThemeMode) {
                val scheme = MaterialTheme.colorScheme
                Scaffold(
                    containerColor = scheme.background,
                    bottomBar = {
                        NavigationBar(containerColor = scheme.surface, tonalElevation = 0.dp) {
                            for ((idx, dest) in GuardRailDestination.entries.withIndex()) {
                                val sel = selectedTabIndex == idx
                                NavigationBarItem(
                                    selected = sel,
                                    onClick = { selectedTabIndex = idx },
                                    icon = { Text(if (sel) "●" else "○", fontSize = 14.sp, color = if (sel) GuardRailColors.Primary else scheme.onSurfaceVariant) },
                                    label = { Text(dest.label, fontSize = 10.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = GuardRailColors.Primary,
                                        selectedTextColor = GuardRailColors.Primary,
                                        indicatorColor = GuardRailColors.PrimaryMuted,
                                        unselectedIconColor = scheme.onSurfaceVariant,
                                        unselectedTextColor = scheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        when (selectedTabIndex) {
                            0 -> HomeScreen(settings = settings, protectionStatus = protectionStatus, activityEvents = activityEvents, onStart = { startProtection(settings, cloudTextAnalysisEnabled) }, onStop = { stopProtection() }, onNavigate = { route -> GuardRailDestination.entries.indexOfFirst { it.route == route }.takeIf { it >= 0 }?.let { selectedTabIndex = it } })
                            1 -> ActivityScreen(events = activityEvents, onClear = { scope.launch { activityRepository.clearAll(); preferencesRepository.clearNotifiedIds() } }, highlightEventId = pendingIncident, onHighlightConsumed = { pendingIncidentId.value = null })
                            2 -> AnalyzeScreen(settings = settings, onAddEvent = { ev -> scope.launch { activityRepository.addEvent(ev) } }, visualClassifierProvider = { try { VisualContentClassifier(applicationContext) } catch (_: Exception) { null } }, sharedText = shared, onSharedTextConsumed = { pendingSharedText.value = "" })
                            3 -> RulesScreen(settings = settings, repository = preferencesRepository, onModeSelected = { m -> scope.launch { preferencesRepository.setMode(m) } }, onCategoryChanged = { c, e -> scope.launch { preferencesRepository.setCategoryEnabled(c, e) } })
                            4 -> SettingsScreen(
                                themeMode = themeMode,
                                onThemeChange = { m -> scope.launch { preferencesRepository.setThemeMode(m) } },
                                settings = settings,
                                protectionStatus = protectionStatus,
                                onManageNotificationPermission = { openNotificationSettings() },
                                onManageOverlayPermission = { openOverlaySettings() },
                                onManageUsagePermission = { openUsageSettings() },
                                onClearActivity = { scope.launch { activityRepository.clearAll(); preferencesRepository.clearNotifiedIds() } },
                                notifyHighRisk = notifyHighRisk,
                                onNotifyHighRiskChange = { v -> scope.launch { preferencesRepository.setNotifyHighRisk(v) } },
                                notifyWarning = notifyWarning,
                                onNotifyWarningChange = { v -> scope.launch { preferencesRepository.setNotifyWarning(v) } },
                                cloudTextAnalysisEnabled = cloudTextAnalysisEnabled,
                                onCloudTextAnalysisChange = { v -> scope.launch { preferencesRepository.setCloudTextAnalysisEnabled(v) } },
                                activityEvents = activityEvents
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleIncidentIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_INCIDENT) {
            val id = intent.getStringExtra(EXTRA_INCIDENT_ID)
            if (!id.isNullOrBlank()) pendingIncidentId.value = id
        }
    }

    @Composable
    private fun handleIncidentIntentIfNeeded(incidentId: String?, onNavigate: () -> Unit) {
        LaunchedEffect(incidentId) {
            if (!incidentId.isNullOrBlank()) onNavigate()
        }
    }

    private fun openNotificationSettings() {
        try {
            val i = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, packageName) } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:$packageName") }
            startActivity(i)
        } catch (_: Exception) {}
    }
    private fun openOverlaySettings() { try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } catch (_: Exception) {} }
    private fun openUsageSettings() { try { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } catch (_: Exception) {} }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); receiveSharedText(intent); handleIncidentIntent(intent) }
    override fun onResume() {
        super.onResume()
        when {
            awaitingOverlaySettings -> { awaitingOverlaySettings = false; if (Settings.canDrawOverlays(this)) continueProtectionSetup() else LiveProtectionState.stopped("Allow display over other apps to show warnings") }
            awaitingUsageSettings -> { awaitingUsageSettings = false; if (hasUsageAccess()) continueProtectionSetup() else LiveProtectionState.stopped("Allow app-usage access to limit checks to Meta apps") }
        }
    }
    private fun startProtection(settings: SafetySettings, cloudTextAnalysisEnabled: Boolean = pendingCloudTextAnalysisEnabled) {
        pendingProtectionSettings = settings
        pendingCloudTextAnalysisEnabled = cloudTextAnalysisEnabled
        audioPermissionHandledForStart = false
        audioPlaybackEnabled = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        LiveProtectionState.starting()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else continueProtectionSetup()
    }
    private fun continueProtectionSetup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !audioPermissionHandledForStart && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO); return }
        if (!Settings.canDrawOverlays(this)) { awaitingOverlaySettings = true; LiveProtectionState.stopped("Allow display over other apps so warnings can appear"); startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return }
        if (!hasUsageAccess()) { awaitingUsageSettings = true; LiveProtectionState.stopped("Allow app-usage access to check only Instagram and Facebook"); startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); return }
        val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(pm.createScreenCaptureIntent())
    }
    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
    }
    private fun stopProtection() { stopService(Intent(this, com.example.metaguardrail.monitor.ScreenMonitoringService::class.java)); LiveProtectionState.stopped() }
    internal fun receiveSharedText(source: Intent?) {
        if (source?.action != Intent.ACTION_SEND || source.type?.startsWith("text/") != true) return
        val raw = source.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        pendingSharedText.value = raw.take(8000).filterNot { it.isISOControl() && it != '\n' }.trim()
    }

    companion object {
        const val ACTION_OPEN_INCIDENT = "com.example.metaguardrail.OPEN_INCIDENT"
        const val EXTRA_INCIDENT_ID = "incident_id"
    }
}
