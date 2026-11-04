package com.example.metaguardrail.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.metaguardrail.BuildConfig
import com.example.metaguardrail.data.ActivityEvent
import com.example.metaguardrail.data.ReportExporter
import com.example.metaguardrail.data.SafetySettings
import com.example.metaguardrail.data.ThemeMode
import com.example.metaguardrail.monitor.LiveProtectionStatus
import com.example.metaguardrail.ui.components.GuardRailCard
import com.example.metaguardrail.ui.components.GuardRailTopBar
import com.example.metaguardrail.ui.components.SectionLabel
import com.example.metaguardrail.ui.theme.GuardRailColors

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    settings: SafetySettings,
    protectionStatus: LiveProtectionStatus,
    onManageNotificationPermission: () -> Unit,
    onManageOverlayPermission: () -> Unit,
    onManageUsagePermission: () -> Unit,
    onClearActivity: (() -> Unit)? = null,
    notifyHighRisk: Boolean = false,
    onNotifyHighRiskChange: ((Boolean) -> Unit)? = null,
    notifyWarning: Boolean = false,
    onNotifyWarningChange: ((Boolean) -> Unit)? = null,
    cloudTextAnalysisEnabled: Boolean = false,
    onCloudTextAnalysisChange: ((Boolean) -> Unit)? = null,
    cloudImageAnalysisEnabled: Boolean = false,
    onCloudImageAnalysisChange: ((Boolean) -> Unit)? = null,
    activityEvents: List<ActivityEvent> = emptyList()
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val packageName = rememberPackageName()
    val appVersion = rememberAppVersion()
    val compileSdk = 37
    val notificationGranted = rememberNotificationGranted()
    val overlayGranted = rememberOverlayGranted()
    val usageGranted = rememberUsageGranted()
    var confirmShareSettings by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val json = ReportExporter.buildReportJson(activityEvents)
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            } catch (_: Exception) {}
        }
    }

    Column(modifier = Modifier.fillMaxWidth().background(scheme.background).verticalScroll(rememberScrollState())) {
        GuardRailTopBar(title = "Settings", subtitle = "Appearance, permissions, and info")
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SectionLabel(text = "Appearance")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Theme", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Choose how the app looks. System follows your device setting.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (mode in ThemeMode.entries) {
                            FilterChip(selected = themeMode == mode, onClick = { onThemeChange(mode) }, label = { Text(text = mode.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) })
                        }
                    }
                }
            }
            SectionLabel(text = "Notifications")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingsRowWithAction(title = "Notifications", subtitle = if (notificationGranted) "Granted" else "Not granted — needed for foreground service on Android 13+", statusOk = notificationGranted, actionLabel = "Manage", onAction = onManageNotificationPermission)
                    if (!notificationGranted) {
                        Text(text = "Incident notifications require notification permission. Enable it via Manage above.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "High-risk alerts", color = scheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "Notify when a new high-risk incident is recorded (highest priority). OFF by default.", color = scheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                        Switch(checked = notifyHighRisk, onCheckedChange = { onNotifyHighRiskChange?.invoke(it) })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "Warning alerts", color = scheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "Notify when a new warning incident is recorded. OFF by default.", color = scheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                        Switch(checked = notifyWarning, onCheckedChange = { onNotifyWarningChange?.invoke(it) })
                    }
                }
            }
            SectionLabel(text = "Permissions")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsRowWithAction(title = "Display over other apps", subtitle = if (overlayGranted) "Granted — shield overlay can appear" else "Not granted — required for shield", statusOk = overlayGranted, actionLabel = "Manage", onAction = onManageOverlayPermission)
                    SettingsRowWithAction(title = "Usage access", subtitle = if (usageGranted) "Granted — checks limited to Instagram & Facebook" else "Not granted — needed to scope checks", statusOk = usageGranted, actionLabel = "Manage", onAction = onManageUsagePermission)
                }
            }
            SectionLabel(text = "Live protection")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Current status", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    StatusDotRow(label = if (protectionStatus.isRunning) "Active" else "Inactive", ok = protectionStatus.isRunning)
                    Text(text = protectionStatus.message, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp)
                    if (!protectionStatus.finding.isNullOrBlank()) Text(text = "Finding: ${protectionStatus.finding}", color = GuardRailColors.Warning, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    if (!protectionStatus.source.isNullOrBlank()) Text(text = "Source: ${protectionStatus.source}", color = scheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
            SectionLabel(text = "Audio")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Speech language", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = settings.speechLanguage.label, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text(text = "Used for live audio checks when playback capture is allowed. Never uses the room microphone; only Meta-app playback. Telugu is experimental.", color = scheme.onSurfaceVariant.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, lineHeight = 14.sp)
                }
            }
            SectionLabel(text = "Privacy")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "On-device processing", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Text, link, and image checks run on-device. Captured screen text and audio transcripts are not stored. No content is uploaded unless you configure a backend.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "Cloud Text Analysis", color = scheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "When ON, OCR-derived text from your screen is sent to the Muse Spark backend for AI safety review. When OFF (default), all checks stay on-device.", color = scheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                        Switch(checked = cloudTextAnalysisEnabled, onCheckedChange = { onCloudTextAnalysisChange?.invoke(it) })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "Cloud Image Analysis", color = scheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "When ON, images may be sent for cloud multimodal review. OFF by default — no images leave device. Independent from Cloud Text Analysis.", color = scheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                        Switch(checked = cloudImageAnalysisEnabled, onCheckedChange = { onCloudImageAnalysisChange?.invoke(it) })
                    }
                }
            }
            SectionLabel(text = "Privacy and Data Controls")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Export & share activity", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Exports contain user-derived text and safety labels from your activity history. Only share with someone you trust.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { exportLauncher.launch(ReportExporter.suggestedFileName()) }, modifier = Modifier.weight(1f)) { Text(text = "Export JSON", fontSize = 12.sp) }
                        OutlinedButton(onClick = { confirmShareSettings = true }, modifier = Modifier.weight(1f)) { Text(text = "Share report", fontSize = 12.sp) }
                    }
                    if (activityEvents.isNotEmpty()) {
                        Text(text = "${activityEvents.size} event(s) available for export.", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, lineHeight = 14.sp)
                    }
                }
            }
            SectionLabel(text = "Data management")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Activity history is stored locally (max 50 events).", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp)
                    if (onClearActivity != null) OutlinedButton(onClick = onClearActivity, modifier = Modifier.fillMaxWidth()) { Text("Clear activity") }
                    else Text(text = "Clear activity from the Activity tab.", color = scheme.onSurfaceVariant.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                }
            }
            SectionLabel(text = "Diagnostics")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Live protection diagnostics", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    InfoRow(label = "Message", value = protectionStatus.message)
                    InfoRow(label = "Finding", value = protectionStatus.finding ?: "None")
                    InfoRow(label = "Source", value = protectionStatus.source ?: "—")
                    InfoRow(label = "App version", value = appVersion)
                    InfoRow(label = "Compile SDK", value = compileSdk.toString())
                }
            }
            SectionLabel(text = "About")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Meta Guard Rail", color = scheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    InfoRow(label = "Package", value = packageName)
                    InfoRow(label = "Version", value = appVersion)
                    InfoRow(label = "Build", value = "compileSdk $compileSdk • minSdk 24 • targetSdk 37")
                    Text(text = "Open-source licenses and third-party notices are available in the app's legal notices (see Settings > About on your device, or check the app's open_source_licenses.html if bundled).", color = scheme.onSurfaceVariant.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, lineHeight = 14.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(scheme.surfaceContainer).padding(horizontal = 10.dp, vertical = 8.dp))
                }
            }
        }
    }
    if (confirmShareSettings) {
        AlertDialog(
            onDismissRequest = { confirmShareSettings = false },
            title = { Text("Share activity report?", fontWeight = FontWeight.SemiBold) },
            text = { Text("This report contains user-derived text and safety labels from your activity history. Only share with someone you trust.", style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmShareSettings = false
                    try {
                        val json = ReportExporter.buildReportJson(activityEvents)
                        val file = ReportExporter.prepareShareCacheFile(context, json)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share report"))
                    } catch (_: Exception) {}
                }) { Text("Share") }
            },
            dismissButton = { TextButton(onClick = { confirmShareSettings = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsRowWithAction(title: String, subtitle: String, statusOk: Boolean, actionLabel: String, onAction: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = title, color = scheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                StatusDot(ok = statusOk)
            }
            Text(text = subtitle, color = scheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 14.sp)
        }
        OutlinedButton(onClick = onAction) { Text(text = actionLabel, fontSize = 12.sp) }
    }
}
@Composable
private fun StatusDot(ok: Boolean) {
    val color = if (ok) GuardRailColors.Safe else GuardRailColors.ProtectionInactive
    androidx.compose.foundation.layout.Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
}
@Composable
private fun StatusDotRow(label: String, ok: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (ok) GuardRailColors.Safe else GuardRailColors.ProtectionInactive))
        Text(text = label, color = scheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
@Composable
private fun InfoRow(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = scheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(text = value, color = scheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 16.dp))
    }
}
@Composable
private fun rememberPackageName(): String { val ctx = LocalContext.current; return ctx.packageName }
@Composable
private fun rememberAppVersion(): String {
    val ctx = LocalContext.current
    return try {
        val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(0))
        else { @Suppress("DEPRECATION") ctx.packageManager.getPackageInfo(ctx.packageName, 0) }
        pi.versionName ?: BuildConfig.VERSION_NAME ?: "1.0"
    } catch (_: Exception) { BuildConfig.VERSION_NAME ?: "1.0" }
}
@Composable
private fun rememberNotificationGranted(): Boolean {
    val ctx = LocalContext.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED else true
}
@Composable
private fun rememberOverlayGranted(): Boolean { val ctx = LocalContext.current; return android.provider.Settings.canDrawOverlays(ctx) }
@Composable
private fun rememberUsageGranted(): Boolean {
    val ctx = LocalContext.current
    return try {
        val appOps = ctx.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        @Suppress("DEPRECATION") appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName) == android.app.AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { false }
}
